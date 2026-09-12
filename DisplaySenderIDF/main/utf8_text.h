#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "ui.h"

#define UTF8_FONT_H 20

bool display_english(void);
void set_display_language(bool english);
void init_display_language(void);

int utf8_text_width(const char *text);
void utf8_text_draw(ui_t *ui, const char *text, int x, int y, int max_width, uint16_t color);
void utf8_text_draw_centered(ui_t *ui, int cx, int y, const char *text, uint16_t color);
