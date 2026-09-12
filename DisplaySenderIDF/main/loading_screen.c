#include "loading_screen.h"
#include "nav_types.h"
#include "maps_theme.h"

#include <math.h>

#ifndef DEG_TO_RAD
#define DEG_TO_RAD 0.01745329251f
#endif

static const int16_t SPINNER_CX = SCR_W / 2;
static const int16_t SPINNER_CY = MAP_AREA_H / 2;
static const int16_t SPINNER_R = 28;
static const uint8_t SPINNER_DOTS = 8;

static uint8_t s_last_spinner_frame = 255;

static void draw_spinner(ui_t *ui, uint8_t active_dot)
{
    for (uint8_t i = 0; i < SPINNER_DOTS; i++) {
        float angle = ((float)i / SPINNER_DOTS) * 6.2831853f - 1.5707963f;
        int16_t x = SPINNER_CX + (int16_t)(cosf(angle) * SPINNER_R);
        int16_t y = SPINNER_CY + (int16_t)(sinf(angle) * SPINNER_R);
        int radius = (i == active_dot) ? 5 : 3;
        uint16_t color = (i == active_dot) ? maps_col_route() : maps_col_muted();
        ui_fill_circle(ui, x, y, radius, color);
    }
}

void show_map_loading_screen(ui_t *ui)
{
    s_last_spinner_frame = 255;

    ui_rect(ui, 0, 0, SCR_W, MAP_AREA_H, maps_col_land());
    ui_fill_circle(ui, 40, 70, 50, maps_col_park());
    ui_fill_circle(ui, SCR_W - 55, 140, 58, maps_col_park());

    /* Bottom sheet preview */
    ui_rect(ui, 0, MAP_AREA_H - 6, SCR_W, 6, maps_col_card_shadow());
    ui_rect(ui, 0, MAP_AREA_H, SCR_W, OVERLAY_H, maps_col_card());
    ui_rect(ui, SCR_W / 2 - 18, MAP_AREA_H + 8, 36, 4, maps_col_muted());

    ui_text_centered(ui, SCR_W / 2, MAP_AREA_H + 28, "Getting directions...", 2, maps_col_text());
    ui_text_centered(ui, SCR_W / 2, MAP_AREA_H + 58, "Loading map data", 1, maps_col_muted());

    draw_spinner(ui, 0);
    ui_flush(ui);
}

void update_map_loading_animation(ui_t *ui, uint32_t now_ms)
{
    uint8_t frame = (uint8_t)((now_ms / 180U) % SPINNER_DOTS);
    if (frame == s_last_spinner_frame) {
        return;
    }
    s_last_spinner_frame = frame;

    const int box = (SPINNER_R + 10) * 2;
    const int bx = SPINNER_CX - SPINNER_R - 10;
    const int by = SPINNER_CY - SPINNER_R - 10;
    ui_rect(ui, bx, by, box, box, maps_col_land());
    draw_spinner(ui, frame);
    ui_flush_rect(ui, bx, by, box, box);
}
