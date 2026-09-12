#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "board.h"
#include "config.h"

#define NAV_MAX_ROUTE_POINTS      64
#define NAV_MAX_STREET_SEGMENTS   128
#define SCR_W                     LCD_H_RES
#define SCR_H                     LCD_V_RES
/* Larger map on 480h panel; bottom sheet ~110px like Google Maps nav card */
#define MAP_AREA_H                370
#define OVERLAY_H                 (SCR_H - MAP_AREA_H)
#define NAV_HTML_MAX              480
#define OVERLAY_MARGIN            10
#define OVERLAY_RADIUS            14

typedef struct {
    bool valid;
    double lat;
    double lon;
    float bearing;
    int distance_m;
    int remaining_m;
    int remaining_s;
    bool off_route;
    bool english;
    bool gps_weak;
    char instruction[128];
    char street[96];
    char html[NAV_HTML_MAX];
    bool has_html;
    int route_count;
    int16_t route_x[NAV_MAX_ROUTE_POINTS];
    int16_t route_y[NAV_MAX_ROUTE_POINTS];
    int street_segment_count;
    int16_t street_x0[NAV_MAX_STREET_SEGMENTS];
    int16_t street_y0[NAV_MAX_STREET_SEGMENTS];
    int16_t street_x1[NAV_MAX_STREET_SEGMENTS];
    int16_t street_y1[NAV_MAX_STREET_SEGMENTS];
    int16_t user_x;
    int16_t user_y;
    bool has_user_position;
} nav_state_t;
