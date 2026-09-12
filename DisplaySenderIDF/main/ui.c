#include "ui.h"
#include "font8x8.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "esp_err.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

static const char *TAG_UI = "ui";
#define BAND_ROWS 40

static uint16_t *s_bounce[2];
static SemaphoreHandle_t s_done;

bool ui_color_trans_done(esp_lcd_panel_io_handle_t io,
                         esp_lcd_panel_io_event_data_t *edata,
                         void *user_ctx)
{
    (void)io;
    (void)edata;
    (void)user_ctx;
    BaseType_t hp = pdFALSE;
    if (s_done) {
        xSemaphoreGiveFromISR(s_done, &hp);
    }
    return hp == pdTRUE;
}

void ui_init(ui_t *ui)
{
    (void)ui;
    if (!s_done) {
        s_done = xSemaphoreCreateCounting(2, 0);
    }
    for (int i = 0; i < 2; i++) {
        if (!s_bounce[i]) {
            s_bounce[i] = heap_caps_malloc(LCD_H_RES * BAND_ROWS * sizeof(uint16_t),
                                           MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
        }
    }
    if (!s_done || !s_bounce[0] || !s_bounce[1]) {
        ESP_LOGE(TAG_UI, "bounce/sem alloc failed");
        ESP_ERROR_CHECK(ESP_ERR_NO_MEM);
    }
}

uint16_t ui_rgb(uint8_t r, uint8_t g, uint8_t b)
{
    uint16_t v = (uint16_t)(((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3));
    return (uint16_t)((v >> 8) | (v << 8)); /* big-endian RGB565 for AXS15231B */
}

void ui_fill(ui_t *ui, uint16_t color)
{
    if (!ui || !ui->fb) {
        return;
    }
    uint16_t *row0 = ui->fb;
    for (int x = 0; x < LCD_H_RES; x++) {
        row0[x] = color;
    }
    for (int y = 1; y < LCD_V_RES; y++) {
        memcpy(&ui->fb[y * LCD_H_RES], row0, LCD_H_RES * sizeof(uint16_t));
    }
}

void ui_rect(ui_t *ui, int x, int y, int w, int h, uint16_t color)
{
    if (x < 0) {
        w += x;
        x = 0;
    }
    if (y < 0) {
        h += y;
        y = 0;
    }
    if (x + w > LCD_H_RES) {
        w = LCD_H_RES - x;
    }
    if (y + h > LCD_V_RES) {
        h = LCD_V_RES - y;
    }
    if (w <= 0 || h <= 0) {
        return;
    }
    for (int yy = y; yy < y + h; yy++) {
        uint16_t *row = &ui->fb[yy * LCD_H_RES + x];
        if (w == LCD_H_RES && x == 0) {
            for (int xx = 0; xx < w; xx++) {
                row[xx] = color;
            }
        } else {
            for (int xx = 0; xx < w; xx++) {
                row[xx] = color;
            }
        }
    }
}

void ui_pixel(ui_t *ui, int x, int y, uint16_t color)
{
    if ((unsigned)x < (unsigned)LCD_H_RES && (unsigned)y < (unsigned)LCD_V_RES) {
        ui->fb[y * LCD_H_RES + x] = color;
    }
}

void ui_line(ui_t *ui, int x0, int y0, int x1, int y1, uint16_t color)
{
    int dx = abs(x1 - x0);
    int sx = x0 < x1 ? 1 : -1;
    int dy = -abs(y1 - y0);
    int sy = y0 < y1 ? 1 : -1;
    int err = dx + dy;
    for (;;) {
        ui_pixel(ui, x0, y0, color);
        if (x0 == x1 && y0 == y1) {
            break;
        }
        int e2 = 2 * err;
        if (e2 >= dy) {
            err += dy;
            x0 += sx;
        }
        if (e2 <= dx) {
            err += dx;
            y0 += sy;
        }
    }
}

void ui_thick_line(ui_t *ui, int x0, int y0, int x1, int y1, uint16_t color, int width)
{
    if (width <= 1) {
        ui_line(ui, x0, y0, x1, y1, color);
        return;
    }
    /* Dominant-axis offsets only — ~2x faster than dual-axis casing. */
    int half = width / 2;
    const int adx = abs(x1 - x0);
    const int ady = abs(y1 - y0);
    if (adx >= ady) {
        for (int o = -half; o <= half; o++) {
            ui_line(ui, x0, y0 + o, x1, y1 + o, color);
        }
    } else {
        for (int o = -half; o <= half; o++) {
            ui_line(ui, x0 + o, y0, x1 + o, y1, color);
        }
    }
}

void ui_fill_circle(ui_t *ui, int cx, int cy, int r, uint16_t color)
{
    if (r < 0) {
        return;
    }
    for (int y = -r; y <= r; y++) {
        /* Scanline fill — fewer pixel tests */
        int span = (int)sqrtf((float)(r * r - y * y));
        int x0 = cx - span;
        int x1 = cx + span;
        if (x0 < 0) {
            x0 = 0;
        }
        if (x1 >= LCD_H_RES) {
            x1 = LCD_H_RES - 1;
        }
        int py = cy + y;
        if ((unsigned)py >= (unsigned)LCD_V_RES) {
            continue;
        }
        uint16_t *row = &ui->fb[py * LCD_H_RES];
        for (int x = x0; x <= x1; x++) {
            row[x] = color;
        }
    }
}

static void fill_flat_triangle(ui_t *ui, int x0, int y0, int x1, int y1, int x2, int y2, uint16_t color)
{
    /* Sorted by y: y0 <= y1 <= y2 */
    if (y1 < y0) {
        int t;
        t = y0; y0 = y1; y1 = t;
        t = x0; x0 = x1; x1 = t;
    }
    if (y2 < y0) {
        int t;
        t = y0; y0 = y2; y2 = t;
        t = x0; x0 = x2; x2 = t;
    }
    if (y2 < y1) {
        int t;
        t = y1; y1 = y2; y2 = t;
        t = x1; x1 = x2; x2 = t;
    }

    if (y0 == y2) {
        int minx = x0 < x1 ? (x0 < x2 ? x0 : x2) : (x1 < x2 ? x1 : x2);
        int maxx = x0 > x1 ? (x0 > x2 ? x0 : x2) : (x1 > x2 ? x1 : x2);
        ui_line(ui, minx, y0, maxx, y0, color);
        return;
    }

    for (int y = y0; y <= y2; y++) {
        int xa, xb;
        if (y < y1) {
            float tA = (y2 == y0) ? 0.f : (float)(y - y0) / (float)(y2 - y0);
            float tB = (y1 == y0) ? 0.f : (float)(y - y0) / (float)(y1 - y0);
            xa = x0 + (int)((x2 - x0) * tA);
            xb = x0 + (int)((x1 - x0) * tB);
        } else {
            float tA = (y2 == y0) ? 0.f : (float)(y - y0) / (float)(y2 - y0);
            float tB = (y2 == y1) ? 0.f : (float)(y - y1) / (float)(y2 - y1);
            xa = x0 + (int)((x2 - x0) * tA);
            xb = x1 + (int)((x2 - x1) * tB);
        }
        if (xa > xb) {
            int t = xa;
            xa = xb;
            xb = t;
        }
        ui_line(ui, xa, y, xb, y, color);
    }
}

void ui_fill_triangle(ui_t *ui, int x0, int y0, int x1, int y1, int x2, int y2, uint16_t color)
{
    fill_flat_triangle(ui, x0, y0, x1, y1, x2, y2, color);
}

void ui_text(ui_t *ui, int x, int y, const char *s, int scale, uint16_t color)
{
    if (!s || scale < 1) {
        if (scale < 1) {
            scale = 1;
        }
        if (!s) {
            return;
        }
    }
    /* Clip vertically — avoid drawing past the bottom of the panel. */
    if (y >= LCD_V_RES || y + 8 * scale <= 0) {
        return;
    }
    int cx = x;
    for (const char *p = s; *p; p++) {
        if (cx >= LCD_H_RES) {
            break;
        }
        if (cx + 8 * scale <= 0) {
            cx += 8 * scale;
            continue;
        }
        unsigned char ch = (unsigned char)*p;
        if (ch >= 128) {
            ch = '?';
        }
        const unsigned char *g = font8x8_basic[ch];
        for (int row = 0; row < 8; row++) {
            int py = y + row * scale;
            if (py >= LCD_V_RES || py + scale <= 0) {
                continue;
            }
            unsigned char bits = g[row];
            for (int col = 0; col < 8; col++) {
                /* font8x8_basic: LSB = leftmost pixel */
                if (bits & (1u << col)) {
                    int px = cx + col * scale;
                    if (px >= 0 && px < LCD_H_RES) {
                        ui_rect(ui, px, py, scale, scale, color);
                    }
                }
            }
        }
        cx += 8 * scale;
    }
}

int ui_text_width(const char *s, int scale)
{
    if (scale < 1) {
        scale = 1;
    }
    return (int)strlen(s) * 8 * scale;
}

void ui_text_centered(ui_t *ui, int cx, int y, const char *s, int scale, uint16_t color)
{
    int w = ui_text_width(s, scale);
    ui_text(ui, cx - w / 2, y, s, scale, color);
}

void ui_flush(ui_t *ui)
{
    ui_flush_rect(ui, 0, 0, LCD_H_RES, LCD_V_RES);
}

void ui_flush_rect(ui_t *ui, int x, int y, int w, int h)
{
    (void)x;
    (void)w;
    if (!ui || !s_bounce[0] || !s_bounce[1] || !s_done) {
        return;
    }
    if (y < 0) {
        h += y;
        y = 0;
    }
    if (y + h > LCD_V_RES) {
        h = LCD_V_RES - y;
    }
    if (h <= 0) {
        return;
    }

    /* Align to BAND_ROWS — QSPI driver expects full-width bands. */
    int y0 = (y / BAND_ROWS) * BAND_ROWS;
    int y1 = y + h;
    if (y1 % BAND_ROWS) {
        y1 += BAND_ROWS - (y1 % BAND_ROWS);
    }
    if (y1 > LCD_V_RES) {
        y1 = LCD_V_RES;
    }

    int inflight = 0;
    int band = 0;
    for (int yy = y0; yy < y1; yy += BAND_ROWS, band++) {
        if (inflight == 2) {
            xSemaphoreTake(s_done, portMAX_DELAY);
            inflight--;
        }
        uint16_t *buf = s_bounce[band & 1];
        memcpy(buf, &ui->fb[yy * LCD_H_RES], LCD_H_RES * BAND_ROWS * sizeof(uint16_t));
        if (esp_lcd_panel_draw_bitmap(ui->panel, 0, yy, LCD_H_RES, yy + BAND_ROWS, buf) == ESP_OK) {
            inflight++;
        }
    }
    while (inflight > 0) {
        xSemaphoreTake(s_done, portMAX_DELAY);
        inflight--;
    }
}
