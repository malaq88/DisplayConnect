#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "ui.h"

typedef enum {
    MAPS_THEME_LIGHT = 0,
    MAPS_THEME_DARK = 1,
} maps_theme_t;

void maps_theme_init(void);
maps_theme_t maps_theme_get(void);
bool maps_theme_is_dark(void);
void maps_theme_set(maps_theme_t theme);
bool maps_theme_toggle(void); /* returns new is_dark */

uint16_t maps_col_land(void);
uint16_t maps_col_park(void);
uint16_t maps_col_road_edge(void);
uint16_t maps_col_road_fill(void);
uint16_t maps_col_route_edge(void);
uint16_t maps_col_route(void);
uint16_t maps_col_route_deep(void);
uint16_t maps_col_card(void);
uint16_t maps_col_card_shadow(void);
uint16_t maps_col_text(void);
uint16_t maps_col_muted(void);
uint16_t maps_col_accent(void);
uint16_t maps_col_white(void);
uint16_t maps_col_switch_track(void);
uint16_t maps_col_switch_knob(void);
