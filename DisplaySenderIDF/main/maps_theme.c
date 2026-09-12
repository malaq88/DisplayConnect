#include "maps_theme.h"

#include "esp_log.h"
#include "nvs.h"
#include "nvs_flash.h"

static const char *TAG = "theme";
static maps_theme_t s_theme = MAPS_THEME_LIGHT;

void maps_theme_init(void)
{
    nvs_handle_t h;
    if (nvs_open("ui", NVS_READONLY, &h) == ESP_OK) {
        uint8_t v = 0;
        if (nvs_get_u8(h, "theme", &v) == ESP_OK && v <= MAPS_THEME_DARK) {
            s_theme = (maps_theme_t)v;
        }
        nvs_close(h);
    }
    ESP_LOGI(TAG, "theme=%s", s_theme == MAPS_THEME_DARK ? "dark" : "light");
}

maps_theme_t maps_theme_get(void)
{
    return s_theme;
}

bool maps_theme_is_dark(void)
{
    return s_theme == MAPS_THEME_DARK;
}

void maps_theme_set(maps_theme_t theme)
{
    s_theme = theme;
    nvs_handle_t h;
    if (nvs_open("ui", NVS_READWRITE, &h) == ESP_OK) {
        nvs_set_u8(h, "theme", (uint8_t)theme);
        nvs_commit(h);
        nvs_close(h);
    }
}

bool maps_theme_toggle(void)
{
    maps_theme_set(s_theme == MAPS_THEME_DARK ? MAPS_THEME_LIGHT : MAPS_THEME_DARK);
    return maps_theme_is_dark();
}

uint16_t maps_col_land(void)
{
    /* Light: near-white Maps land. Dark: elevated charcoal (not pure black). */
    return maps_theme_is_dark() ? ui_rgb(48, 52, 58) : ui_rgb(248, 249, 251);
}

uint16_t maps_col_park(void)
{
    return maps_theme_is_dark() ? ui_rgb(58, 78, 62) : ui_rgb(220, 238, 210);
}

uint16_t maps_col_road_edge(void)
{
    return maps_theme_is_dark() ? ui_rgb(90, 96, 104) : ui_rgb(210, 214, 220);
}

uint16_t maps_col_road_fill(void)
{
    return maps_theme_is_dark() ? ui_rgb(72, 76, 84) : ui_rgb(255, 255, 255);
}

uint16_t maps_col_route_edge(void)
{
    return maps_theme_is_dark() ? ui_rgb(32, 36, 42) : ui_rgb(255, 255, 255);
}

uint16_t maps_col_route(void)
{
    return ui_rgb(66, 133, 244);
}

uint16_t maps_col_route_deep(void)
{
    return ui_rgb(26, 115, 232);
}

uint16_t maps_col_card(void)
{
    return maps_theme_is_dark() ? ui_rgb(58, 62, 70) : ui_rgb(255, 255, 255);
}

uint16_t maps_col_card_shadow(void)
{
    return maps_theme_is_dark() ? ui_rgb(36, 38, 44) : ui_rgb(200, 206, 214);
}

uint16_t maps_col_text(void)
{
    return maps_theme_is_dark() ? ui_rgb(240, 242, 245) : ui_rgb(32, 33, 36);
}

uint16_t maps_col_muted(void)
{
    return maps_theme_is_dark() ? ui_rgb(160, 168, 176) : ui_rgb(95, 99, 104);
}

uint16_t maps_col_accent(void)
{
    return maps_theme_is_dark() ? ui_rgb(138, 180, 248) : ui_rgb(26, 115, 232);
}

uint16_t maps_col_white(void)
{
    return ui_rgb(255, 255, 255);
}

uint16_t maps_col_switch_track(void)
{
    return maps_theme_is_dark() ? ui_rgb(66, 133, 244) : ui_rgb(200, 206, 214);
}

uint16_t maps_col_switch_knob(void)
{
    return ui_rgb(255, 255, 255);
}
