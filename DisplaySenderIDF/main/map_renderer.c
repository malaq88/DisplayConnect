#include "map_renderer.h"
#include "config.h"
#include "maps_theme.h"
#include "utf8_text.h"

#include <ctype.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#ifndef DEG_TO_RAD
#define DEG_TO_RAD 0.01745329251f
#endif

/* Slimmer strokes = much faster redraw on 320x480 */
static const uint8_t STREET_EDGE_W = 5;
static const uint8_t STREET_FILL_W = 3;
static const uint8_t ROUTE_EDGE_W = 8;
static const uint8_t ROUTE_FILL_W = 5;

/* Theme switch in bottom sheet (top-right) */
#define SW_W 56
#define SW_H 28
#define SW_X (SCR_W - OVERLAY_MARGIN - SW_W - 4)
#define SW_Y (MAP_AREA_H + 14)

static inline int16_t scale_x(int16_t x)
{
    if (x < 0) {
        return x;
    }
    return (int16_t)((x * SCR_W) / PROTO_MAP_W);
}

static inline int16_t scale_y(int16_t y)
{
    if (y < 0) {
        return y;
    }
    return (int16_t)((y * MAP_AREA_H) / PROTO_MAP_H);
}

static void strip_tag(char *text)
{
    if (!text) {
        return;
    }
    char *write = text;
    bool in_tag = false;
    for (const char *read = text; *read; ++read) {
        if (*read == '<') {
            in_tag = true;
            continue;
        }
        if (*read == '>') {
            in_tag = false;
            continue;
        }
        if (!in_tag) {
            *write++ = *read;
        }
    }
    *write = '\0';

    char *dst = text;
    bool space = true;
    for (const char *src = text; *src; ++src) {
        if (isspace((unsigned char)*src)) {
            if (!space) {
                *dst++ = ' ';
                space = true;
            }
        } else {
            *dst++ = *src;
            space = false;
        }
    }
    if (dst > text && *(dst - 1) == ' ') {
        --dst;
    }
    *dst = '\0';
}

static bool next_html_line(const char **cursor, char *out, size_t out_size)
{
    if (!cursor || !*cursor || !out || out_size == 0) {
        return false;
    }
    while (**cursor == '\n' || **cursor == '\r') {
        (*cursor)++;
    }
    if (**cursor == '\0') {
        return false;
    }
    size_t i = 0;
    while (**cursor && **cursor != '\n' && **cursor != '\r' && i < out_size - 1) {
        out[i++] = *(*cursor)++;
    }
    out[i] = '\0';
    strip_tag(out);
    return out[0] != '\0';
}

static void draw_theme_switch_at(ui_t *ui, int sx, int sy);

void map_renderer_draw_theme_switch(ui_t *ui)
{
    draw_theme_switch_at(ui, SW_X, SW_Y);
    ui_flush_rect(ui, SW_X - 2, SW_Y - 2, SW_W + 4, SW_H + 4);
}

bool map_renderer_theme_switch_hit(uint16_t x, uint16_t y)
{
    /* Nav bottom-sheet switch */
    if (x >= (uint16_t)(SW_X - 8) && x < (uint16_t)(SW_X + SW_W + 8) &&
        y >= (uint16_t)(SW_Y - 8) && y < (uint16_t)(SW_Y + SW_H + 8)) {
        return true;
    }
    /* Waiting-screen switch (bottom-right) */
    const int wx = SCR_W - 24 - SW_W;
    const int wy = SCR_H - 56;
    return x >= (uint16_t)(wx - 8) && x < (uint16_t)(wx + SW_W + 8) &&
           y >= (uint16_t)(wy - 8) && y < (uint16_t)(wy + SW_H + 8);
}

static void draw_map_background(ui_t *ui)
{
    ui_rect(ui, 0, 0, SCR_W, MAP_AREA_H, maps_col_land());
    /* One soft park blob — cheap depth without darkening the map */
    if (!maps_theme_is_dark()) {
        ui_fill_circle(ui, SCR_W - 48, 90, 44, maps_col_park());
        ui_fill_circle(ui, 36, MAP_AREA_H - 80, 36, maps_col_park());
    }
}

static void draw_streets(ui_t *ui, const nav_state_t *state)
{
    for (int i = 0; i < state->street_segment_count; i++) {
        int16_t x0 = scale_x(state->street_x0[i]);
        int16_t y0 = scale_y(state->street_y0[i]);
        int16_t x1 = scale_x(state->street_x1[i]);
        int16_t y1 = scale_y(state->street_y1[i]);
        ui_thick_line(ui, x0, y0, x1, y1, maps_col_road_edge(), STREET_EDGE_W);
    }
    for (int i = 0; i < state->street_segment_count; i++) {
        int16_t x0 = scale_x(state->street_x0[i]);
        int16_t y0 = scale_y(state->street_y0[i]);
        int16_t x1 = scale_x(state->street_x1[i]);
        int16_t y1 = scale_y(state->street_y1[i]);
        ui_thick_line(ui, x0, y0, x1, y1, maps_col_road_fill(), STREET_FILL_W);
    }
}

static void draw_route(ui_t *ui, const nav_state_t *state)
{
    if (state->route_count < 2) {
        return;
    }
    for (int pass = 0; pass < 2; pass++) {
        uint16_t color = (pass == 0) ? maps_col_route_edge() : maps_col_route();
        uint8_t width = (pass == 0) ? ROUTE_EDGE_W : ROUTE_FILL_W;
        for (int i = 0; i < state->route_count - 1; i++) {
            int16_t x0 = state->route_x[i];
            int16_t y0 = state->route_y[i];
            int16_t x1 = state->route_x[i + 1];
            int16_t y1 = state->route_y[i + 1];
            if (x0 < 0 || y0 < 0 || x1 < 0 || y1 < 0) {
                continue;
            }
            ui_thick_line(ui, scale_x(x0), scale_y(y0), scale_x(x1), scale_y(y1), color, width);
        }
    }
}

static void draw_user_puck(ui_t *ui, int16_t cx, int16_t cy, float bearing_deg)
{
    float rad = bearing_deg * DEG_TO_RAD;

    ui_fill_circle(ui, cx, cy, 11, maps_col_white());
    ui_fill_circle(ui, cx, cy, 8, maps_col_route());
    ui_fill_circle(ui, cx, cy, 3, maps_col_white());

    int16_t tip_x = cx + (int16_t)(sinf(rad) * 17.0f);
    int16_t tip_y = cy - (int16_t)(cosf(rad) * 17.0f);
    float left_rad = rad + 2.55f;
    float right_rad = rad - 2.55f;
    int16_t left_x = cx + (int16_t)(sinf(left_rad) * 10.0f);
    int16_t left_y = cy - (int16_t)(cosf(left_rad) * 10.0f);
    int16_t right_x = cx + (int16_t)(sinf(right_rad) * 10.0f);
    int16_t right_y = cy - (int16_t)(cosf(right_rad) * 10.0f);
    ui_fill_triangle(ui, tip_x, tip_y, left_x, left_y, right_x, right_y, maps_col_white());

    tip_x = cx + (int16_t)(sinf(rad) * 15.0f);
    tip_y = cy - (int16_t)(cosf(rad) * 15.0f);
    left_x = cx + (int16_t)(sinf(left_rad) * 8.0f);
    left_y = cy - (int16_t)(cosf(left_rad) * 8.0f);
    right_x = cx + (int16_t)(sinf(right_rad) * 8.0f);
    right_y = cy - (int16_t)(cosf(right_rad) * 8.0f);
    ui_fill_triangle(ui, tip_x, tip_y, left_x, left_y, right_x, right_y, maps_col_route_deep());
}

static void draw_bottom_sheet(ui_t *ui)
{
    const int top = MAP_AREA_H;
    ui_rect(ui, 0, top - 4, SCR_W, 4, maps_col_card_shadow());
    ui_rect(ui, 0, top, SCR_W, OVERLAY_H, maps_col_card());

    const int r = 12;
    for (int i = 0; i < r; i++) {
        int inset = r - i;
        ui_rect(ui, 0, top + i, inset, 1, maps_col_land());
        ui_rect(ui, SCR_W - inset, top + i, inset, 1, maps_col_land());
    }
    ui_rect(ui, SCR_W / 2 - 16, top + 8, 32, 3, maps_col_muted());
}

static void draw_theme_switch_at(ui_t *ui, int sx, int sy)
{
    const bool dark = maps_theme_is_dark();
    ui_rect(ui, sx, sy, SW_W, SW_H, maps_col_switch_track());
    ui_fill_circle(ui, sx + SW_H / 2, sy + SW_H / 2, SW_H / 2, maps_col_switch_track());
    ui_fill_circle(ui, sx + SW_W - SW_H / 2, sy + SW_H / 2, SW_H / 2, maps_col_switch_track());
    const int knob_cx = dark ? (sx + SW_W - SW_H / 2) : (sx + SW_H / 2);
    ui_fill_circle(ui, knob_cx, sy + SW_H / 2, SW_H / 2 - 3, maps_col_switch_knob());
    if (dark) {
        ui_text(ui, sx + 8, sy + 10, "D", 1, maps_col_white());
    } else {
        ui_text(ui, sx + SW_W - 16, sy + 10, "L", 1, maps_col_muted());
    }
}

static void draw_overlay_content(ui_t *ui, const nav_state_t *state)
{
    const int text_x = OVERLAY_MARGIN + 4;
    const int y0 = MAP_AREA_H + 16;
    const int text_max_w = SW_X - text_x - 8;
    const bool english = state->english || display_english();

    if (state->has_html && state->html[0] != '\0') {
        const char *cursor = state->html;
        char line[96];
        int drawn = 0;
        int y = y0;
        while (drawn < 3 && next_html_line(&cursor, line, sizeof(line))) {
            utf8_text_draw(ui, line, text_x, y, text_max_w,
                           drawn == 0 ? maps_col_accent() : maps_col_text());
            y += UTF8_FONT_H;
            drawn++;
        }
    } else {
        char dist_line[24];
        if (state->distance_m >= 1000) {
            snprintf(dist_line, sizeof(dist_line), "%.1f km", state->distance_m / 1000.0f);
        } else {
            snprintf(dist_line, sizeof(dist_line), "%d m", state->distance_m);
        }

        utf8_text_draw(ui, state->gps_weak ? (english ? "Weak GPS" : "GPS fraco") : dist_line,
                       text_x, y0, text_max_w, maps_col_accent());

        if (state->instruction[0]) {
            utf8_text_draw(ui, state->instruction, text_x, y0 + 20, text_max_w, maps_col_text());
        }
        if (state->street[0]) {
            utf8_text_draw(ui, state->street, text_x, y0 + 40, text_max_w, maps_col_muted());
        }

        char remaining[40] = "";
        if (state->off_route) {
            snprintf(remaining, sizeof(remaining), "%s", english ? "Off route" : "Fora da rota");
        } else if (state->remaining_m >= 0 || state->remaining_s >= 0) {
            char km[16] = "--";
            char mins[16] = "--";
            if (state->remaining_m >= 0) {
                snprintf(km, sizeof(km), state->remaining_m < 1000 ? "%.2f km" : "%.1f km",
                         state->remaining_m / 1000.0);
            }
            if (state->remaining_s >= 0) {
                int minutes = state->remaining_s / 60 + (state->remaining_s % 60 != 0);
                if (minutes >= 60) {
                    snprintf(mins, sizeof(mins), "%dh%02d", minutes / 60, minutes % 60);
                } else {
                    snprintf(mins, sizeof(mins), "%d min", minutes);
                }
            }
            snprintf(remaining, sizeof(remaining), "%s · %s", km, mins);
        }
        if (remaining[0]) {
            utf8_text_draw(ui, remaining, text_x, y0 + 62, text_max_w, maps_col_muted());
        }
    }

    draw_theme_switch_at(ui, SW_X, SW_Y);
}

void map_renderer_draw(ui_t *ui, const nav_state_t *state)
{
    draw_map_background(ui);
    draw_streets(ui, state);
    draw_route(ui, state);

    if (state->has_user_position) {
        int16_t ux = scale_x(state->user_x);
        int16_t uy = scale_y(state->user_y);
        if (ux >= 0 && uy >= 0 && ux < SCR_W && uy < MAP_AREA_H) {
            draw_user_puck(ui, ux, uy, state->bearing);
        }
    }

    draw_bottom_sheet(ui);
    draw_overlay_content(ui, state);
    ui_flush(ui);
}

void show_status_screen(ui_t *ui, const char *title, const char *line2, const char *line3)
{
    ui_fill(ui, maps_col_land());
    if (!maps_theme_is_dark()) {
        ui_fill_circle(ui, SCR_W - 40, 100, 50, maps_col_park());
    }

    ui_rect(ui, 0, 0, SCR_W, 48, maps_col_card());
    ui_rect(ui, 0, 48, SCR_W, 2, maps_col_card_shadow());
    utf8_text_draw_centered(ui, SCR_W / 2, 14, title, maps_col_text());

    const int card_y = SCR_H / 2 - 50;
    ui_rect(ui, 24, card_y, SCR_W - 48, 100, maps_col_card());
    ui_rect(ui, 24, card_y, SCR_W - 48, 3, maps_col_route());

    if (line2) {
        utf8_text_draw_centered(ui, SCR_W / 2, card_y + 28, line2, maps_col_accent());
    }
    if (line3) {
        utf8_text_draw_centered(ui, SCR_W / 2, card_y + 56, line3, maps_col_muted());
    }

    draw_theme_switch_at(ui, SCR_W - 24 - SW_W, SCR_H - 56);
    ui_text_centered(ui, SCR_W / 2, SCR_H - 22, "Toque: claro / escuro", 1, maps_col_muted());

    ui_flush(ui);
}

void show_waiting_for_app_screen(ui_t *ui)
{
    show_status_screen(ui,
                       display_english() ? "Ready to navigate" : "Pronto para navegar",
                       "Bluetooth LE", BLE_DEVICE_NAME);
}
