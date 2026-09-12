/**
 * DisplayConnect v2 — JC3248W535EN (ESP32-S3 + AXS15231B)
 *
 * IMPORTANT: Never call display / cJSON from BLE callbacks — they only
 * enqueue bytes / set flags; the app task does drawing and parsing.
 *
 * Prefer draining BLE RX over screen flushes: a full QSPI flush can take
 * long enough to overflow the RX queue and drop nav JSON.
 */

#include <stdio.h>
#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "nvs_flash.h"

#include "ble_nus.h"
#include "config.h"
#include "display.h"
#include "loading_screen.h"
#include "map_renderer.h"
#include "maps_theme.h"
#include "nav_protocol.h"
#include "nav_types.h"
#include "touch.h"
#include "ui.h"
#include "utf8_text.h"

static const char *TAG = "main";

static ui_t s_ui;

static volatile bool s_awaiting_first_nav = false;
static volatile bool s_ui_show_loading = false;
static volatile bool s_ui_show_waiting = false;
static volatile bool s_send_ok_pending = false;
static volatile uint32_t s_rx_dropped = 0;

static uint32_t s_nav_updates = 0;
static uint32_t s_last_stats_ms = 0;
static uint16_t s_updates_per_sec = 0;

static nav_state_t s_last_nav;
static bool s_has_nav = false;
static bool s_showing_waiting = true;
static bool s_touch_was_down = false;
static uint32_t s_last_theme_toggle_ms = 0;

#define LINE_BUF_SIZE 8192
static char s_line_buf[LINE_BUF_SIZE];
static size_t s_line_len = 0;
static bool s_discard_line = false;
static volatile bool s_rx_overflow = false;

#define RX_QUEUE_LEN 16384
static QueueHandle_t s_rx_queue;

static void on_ble_rx(const uint8_t *data, size_t len, void *ctx)
{
    (void)ctx;
    for (size_t i = 0; i < len; i++) {
        if (xQueueSend(s_rx_queue, &data[i], 0) != pdTRUE) {
            s_rx_dropped++;
            s_rx_overflow = true;
            break;
        }
    }
}

static void on_ble_conn(bool connected, void *ctx)
{
    (void)ctx;
    if (connected) {
        s_awaiting_first_nav = true;
        s_nav_updates = 0;
        s_updates_per_sec = 0;
        s_last_stats_ms = (uint32_t)(esp_timer_get_time() / 1000);
        s_line_len = 0;
        s_discard_line = false;
        s_rx_dropped = 0;
        s_rx_overflow = false;
        s_has_nav = false;
        s_showing_waiting = false;
        s_ui_show_loading = true;
        s_send_ok_pending = true;
        ESP_LOGI(TAG, "BLE connected — waiting for nav JSON");
    } else {
        s_awaiting_first_nav = false;
        s_nav_updates = 0;
        s_line_len = 0;
        s_discard_line = false;
        s_rx_overflow = false;
        s_has_nav = false;
        uint8_t drain;
        while (xQueueReceive(s_rx_queue, &drain, 0) == pdTRUE) {
        }
        s_ui_show_waiting = true;
        ESP_LOGI(TAG, "BLE disconnected");
    }
}

static void process_text_message(const char *payload, size_t length)
{
    ESP_LOGI(TAG, "BLE line %u bytes: %.48s%s",
             (unsigned)length, payload, length > 48 ? "..." : "");

    bool english = false;
    if (parse_config_json(payload, length, &english)) {
        set_display_language(english);
        if (s_has_nav) {
            s_last_nav.english = display_english();
            map_renderer_draw(&s_ui, &s_last_nav);
        } else if (s_showing_waiting) {
            show_waiting_for_app_screen(&s_ui);
        } else {
            show_map_loading_screen(&s_ui);
        }
        return;
    }

    if (is_loading_json(payload, length)) {
        s_awaiting_first_nav = true;
        s_has_nav = false;
        s_showing_waiting = false;
        show_map_loading_screen(&s_ui);
        return;
    }

    nav_state_t state;
    if (!parse_nav_json(payload, length, &state)) {
        return;
    }

    set_display_language(state.english);
    ESP_LOGI(TAG, "Nav OK: route=%d streets=%d dist=%dm",
             state.route_count, state.street_segment_count, state.distance_m);
    s_last_nav = state;
    s_has_nav = true;
    s_showing_waiting = false;
    map_renderer_draw(&s_ui, &s_last_nav);
    s_awaiting_first_nav = false;
    s_nav_updates++;
    s_updates_per_sec++;
}

static void append_rx_byte(char c)
{
    if (c == '\n' || c == '\r') {
        if (s_line_len > 0 && !s_discard_line) {
            s_line_buf[s_line_len] = '\0';
            process_text_message(s_line_buf, s_line_len);
        }
        s_line_len = 0;
        s_discard_line = false;
        return;
    }
    if (s_discard_line) {
        return;
    }
    if (s_line_len + 1 >= LINE_BUF_SIZE) {
        ESP_LOGW(TAG, "BLE line buffer overflow (%u) — reset", (unsigned)LINE_BUF_SIZE);
        s_line_len = 0;
        s_discard_line = true;
        return;
    }
    s_line_buf[s_line_len++] = c;
}

static void drain_rx_queue(void)
{
    uint8_t b;
    if (s_rx_overflow) {
        s_rx_overflow = false;
        while (xQueueReceive(s_rx_queue, &b, 0) == pdTRUE) {
        }
        s_line_len = 0;
        s_discard_line = true;
        ESP_LOGW(TAG, "BLE overflow: discarded incomplete frame");
        return;
    }
    while (xQueueReceive(s_rx_queue, &b, 0) == pdTRUE) {
        append_rx_byte((char)b);
    }
}

static UBaseType_t rx_queue_waiting(void)
{
    return s_rx_queue ? uxQueueMessagesWaiting(s_rx_queue) : 0;
}

static void handle_ui_flags(void)
{
    if (s_ui_show_loading) {
        s_ui_show_loading = false;
        s_showing_waiting = false;
        show_map_loading_screen(&s_ui);
    }

    if (s_ui_show_waiting) {
        s_ui_show_waiting = false;
        s_showing_waiting = true;
        show_waiting_for_app_screen(&s_ui);
        ble_nus_restart_advertising();
    }

    if (s_send_ok_pending && ble_nus_is_connected()) {
        s_send_ok_pending = false;
        ble_nus_notify("OK\n");
    }
}

static void redraw_after_theme_change(void)
{
    if (s_has_nav && ble_nus_is_connected()) {
        map_renderer_draw(&s_ui, &s_last_nav);
        s_showing_waiting = false;
    } else if (ble_nus_is_connected() && s_awaiting_first_nav) {
        show_map_loading_screen(&s_ui);
        s_showing_waiting = false;
    } else {
        show_waiting_for_app_screen(&s_ui);
        s_showing_waiting = true;
    }
}

static void poll_theme_switch(void)
{
    uint16_t x = 0;
    uint16_t y = 0;
    const bool down = touch_read(&x, &y);
    const uint32_t now = (uint32_t)(esp_timer_get_time() / 1000);

    if (down && !s_touch_was_down) {
        if (map_renderer_theme_switch_hit(x, y) &&
            (now - s_last_theme_toggle_ms) > 350) {
            const bool dark = maps_theme_toggle();
            s_last_theme_toggle_ms = now;
            ESP_LOGI(TAG, "theme -> %s (touch %u,%u)", dark ? "dark" : "light", x, y);
            redraw_after_theme_change();
        }
    }
    s_touch_was_down = down;
}

static void update_stats(void)
{
    uint32_t now = (uint32_t)(esp_timer_get_time() / 1000);
    if (now - s_last_stats_ms >= 1000) {
        if (ble_nus_is_connected()) {
            ESP_LOGI(TAG, "Nav/s=%u total=%lu q=%u drop=%lu line=%u",
                     s_updates_per_sec,
                     (unsigned long)s_nav_updates,
                     (unsigned)rx_queue_waiting(),
                     (unsigned long)s_rx_dropped,
                     (unsigned)s_line_len);
        }
        s_updates_per_sec = 0;
        s_last_stats_ms = now;
    }
}

void app_main(void)
{
    ESP_LOGI(TAG, "=== DisplayConnect S3 (JC3248W535EN / ESP-IDF) ===");

    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    maps_theme_init();
    init_display_language();

    s_rx_queue = xQueueCreate(RX_QUEUE_LEN, sizeof(uint8_t));
    ESP_ERROR_CHECK(s_rx_queue ? ESP_OK : ESP_ERR_NO_MEM);

    ESP_ERROR_CHECK(display_init(&s_ui));
    if (touch_init() != ESP_OK) {
        ESP_LOGW(TAG, "Touch init failed — theme switch disabled");
    }

    show_status_screen(&s_ui, "DisplayConnect", "Starting BLE...", NULL);

    ble_nus_callbacks_t cbs = {
        .on_rx = on_ble_rx,
        .on_conn = on_ble_conn,
        .ctx = NULL,
    };
    ESP_ERROR_CHECK(ble_nus_start(&cbs));
    show_waiting_for_app_screen(&s_ui);
    s_showing_waiting = true;

    while (1) {
        /* Always drain BLE before any slow display work. */
        drain_rx_queue();
        handle_ui_flags();
        drain_rx_queue();

        poll_theme_switch();
        drain_rx_queue();

        if (ble_nus_is_connected() && s_awaiting_first_nav && rx_queue_waiting() == 0 && s_line_len == 0) {
            update_map_loading_animation(&s_ui, (uint32_t)(esp_timer_get_time() / 1000));
        }

        update_stats();
        vTaskDelay(pdMS_TO_TICKS(2));
    }
}
