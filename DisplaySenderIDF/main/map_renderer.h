#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "nav_types.h"
#include "ui.h"

void map_renderer_draw(ui_t *ui, const nav_state_t *state);
void show_status_screen(ui_t *ui, const char *title, const char *line2, const char *line3);
void show_waiting_for_app_screen(ui_t *ui);

/** Theme switch hit-test in panel coordinates. */
bool map_renderer_theme_switch_hit(uint16_t x, uint16_t y);
/** Redraw only the theme switch (partial flush). */
void map_renderer_draw_theme_switch(ui_t *ui);
