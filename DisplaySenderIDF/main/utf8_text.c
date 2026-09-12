#include "utf8_text.h"
#include "latin_font.h"

#include <string.h>

#include "esp_log.h"
#include "nvs.h"

static const char *TAG = "utf8";
static bool s_english = false;

bool display_english(void)
{
    return s_english;
}

void init_display_language(void)
{
    nvs_handle_t h;
    if (nvs_open("display-lang", NVS_READONLY, &h) == ESP_OK) {
        uint8_t v = 0;
        if (nvs_get_u8(h, "en", &v) == ESP_OK) {
            s_english = v != 0;
        }
        nvs_close(h);
    }
    ESP_LOGI(TAG, "lang=%s", s_english ? "en" : "pt-BR");
}

void set_display_language(bool english)
{
    if (english == s_english) {
        return;
    }
    s_english = english;
    nvs_handle_t h;
    if (nvs_open("display-lang", NVS_READWRITE, &h) == ESP_OK) {
        nvs_set_u8(h, "en", english ? 1 : 0);
        nvs_commit(h);
        nvs_close(h);
    }
}

static uint16_t next_codepoint(const char **cursor)
{
    uint8_t first = (uint8_t)(*(*cursor)++);
    if (first < 128) {
        return first;
    }
    int remaining = (first & 0xE0) == 0xC0 ? 1
                  : (first & 0xF0) == 0xE0 ? 2
                  : (first & 0xF8) == 0xF0 ? 3 : 0;
    if (!remaining) {
        return '?';
    }
    uint32_t value = first & (remaining == 1 ? 0x1F : remaining == 2 ? 0x0F : 0x07);
    for (int i = 0; i < remaining; ++i) {
        uint8_t next = (uint8_t)(**cursor);
        if (!next || (next & 0xC0) != 0x80) {
            return '?';
        }
        (*cursor)++;
        value = (value << 6) | (next & 0x3F);
    }
    if (value == 0x2013 || value == 0x2014) {
        return '-';
    }
    if (value == 0x2018 || value == 0x2019) {
        return '\'';
    }
    return (value >= 32 && value <= 255) ? (uint16_t)value : '?';
}

static LatinGlyph glyph_for(uint16_t code)
{
    if (code < 32 || code > 255) {
        code = '?';
    }
    return latinGlyphs[code - 32];
}

static void draw_bitmap_1bpp(ui_t *ui, int x, int y, const uint8_t *bitmap, int w, int h, uint16_t color)
{
    const int byte_width = (w + 7) / 8;
    for (int row = 0; row < h; row++) {
        const int py = y + row;
        if ((unsigned)py >= (unsigned)LCD_V_RES) {
            continue;
        }
        const uint8_t *src = bitmap + row * byte_width;
        for (int col = 0; col < w; col++) {
            if (src[col / 8] & (uint8_t)(0x80 >> (col & 7))) {
                ui_pixel(ui, x + col, py, color);
            }
        }
    }
}

int utf8_text_width(const char *text)
{
    int width = 0;
    if (!text) {
        return 0;
    }
    while (*text) {
        width += glyph_for(next_codepoint(&text)).width;
    }
    return width;
}

void utf8_text_draw(ui_t *ui, const char *text, int x, int y, int max_width, uint16_t color)
{
    if (!ui || !text) {
        return;
    }
    const int right = max_width > 0 ? (x + max_width) : LCD_H_RES;
    while (*text) {
        const LatinGlyph glyph = glyph_for(next_codepoint(&text));
        if (x + glyph.width > right) {
            break;
        }
        draw_bitmap_1bpp(ui, x, y, latinPixels + glyph.offset, glyph.width, UTF8_FONT_H, color);
        x += glyph.width;
    }
}

void utf8_text_draw_centered(ui_t *ui, int cx, int y, const char *text, uint16_t color)
{
    if (!text) {
        return;
    }
    const int w = utf8_text_width(text);
    utf8_text_draw(ui, text, cx - w / 2, y, w + 1, color);
}
