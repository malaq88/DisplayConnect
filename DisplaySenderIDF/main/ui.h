#pragma once

#include <stdint.h>
#include <stdbool.h>
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "board.h"

typedef struct {
    uint16_t *fb;
    esp_lcd_panel_handle_t panel;
} ui_t;

void ui_init(ui_t *ui);
bool ui_color_trans_done(esp_lcd_panel_io_handle_t io,
                         esp_lcd_panel_io_event_data_t *edata,
                         void *user_ctx);

uint16_t ui_rgb(uint8_t r, uint8_t g, uint8_t b);

void ui_fill(ui_t *ui, uint16_t color);
void ui_rect(ui_t *ui, int x, int y, int w, int h, uint16_t color);
void ui_pixel(ui_t *ui, int x, int y, uint16_t color);
void ui_line(ui_t *ui, int x0, int y0, int x1, int y1, uint16_t color);
void ui_thick_line(ui_t *ui, int x0, int y0, int x1, int y1, uint16_t color, int width);
void ui_fill_circle(ui_t *ui, int cx, int cy, int r, uint16_t color);
void ui_fill_triangle(ui_t *ui, int x0, int y0, int x1, int y1, int x2, int y2, uint16_t color);
void ui_text(ui_t *ui, int x, int y, const char *s, int scale, uint16_t color);
void ui_text_centered(ui_t *ui, int cx, int y, const char *s, int scale, uint16_t color);
int ui_text_width(const char *s, int scale);

void ui_flush(ui_t *ui);
/** Flush only rows that intersect [y, y+h). Faster than a full-screen flush. */
void ui_flush_rect(ui_t *ui, int x, int y, int w, int h);
