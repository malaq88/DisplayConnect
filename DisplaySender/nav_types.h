#pragma once

#include <stdint.h>

#define NAV_MAX_ROUTE_POINTS 64
#define NAV_MAX_STREET_SEGMENTS 56
#define SCR_W 240
#define SCR_H 320
#define MAP_AREA_H 232
#define OVERLAY_H (SCR_H - MAP_AREA_H)
#define NAV_HTML_MAX 480

struct NavState {
  bool valid = false;
  double lat = 0.0;
  double lon = 0.0;
  float bearing = 0.0f;
  int distanceM = 0;
  char instruction[64] = {};
  char street[48] = {};
  char html[NAV_HTML_MAX] = {};
  bool hasHtml = false;
  int routeCount = 0;
  int16_t routeX[NAV_MAX_ROUTE_POINTS] = {};
  int16_t routeY[NAV_MAX_ROUTE_POINTS] = {};
  int streetSegmentCount = 0;
  int16_t streetX0[NAV_MAX_STREET_SEGMENTS] = {};
  int16_t streetY0[NAV_MAX_STREET_SEGMENTS] = {};
  int16_t streetX1[NAV_MAX_STREET_SEGMENTS] = {};
  int16_t streetY1[NAV_MAX_STREET_SEGMENTS] = {};
  int16_t userX = 0;
  int16_t userY = 0;
  bool hasUserPosition = false;
};
