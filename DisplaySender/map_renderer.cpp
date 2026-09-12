#include "map_renderer.h"
#include "html_renderer.h"
#include "maps_theme.h"
#include "utf8_text.h"
#include <math.h>
#include <stdlib.h>
#include <string.h>

#ifndef DEG_TO_RAD
#define DEG_TO_RAD 0.01745329251f
#endif

static const uint8_t STREET_EDGE_W = 4;
static const uint8_t STREET_FILL_W = 2;
static const uint8_t ROUTE_EDGE_W = 6;
static const uint8_t ROUTE_FILL_W = 3;

#define SW_W 48
#define SW_H 24
#define SW_X (SCR_W - 8 - SW_W)
#define SW_Y (MAP_AREA_H + 10)

MapRenderer::MapRenderer(TFT_eSPI& display) : tft(display) {}

static void drawThickLine(TFT_eSPI& tft, int16_t x0, int16_t y0, int16_t x1, int16_t y1,
                          uint16_t color, uint8_t width) {
  if (width <= 1) {
    tft.drawLine(x0, y0, x1, y1, color);
    return;
  }
  const int8_t half = static_cast<int8_t>(width / 2);
  const int adx = abs(x1 - x0);
  const int ady = abs(y1 - y0);
  if (adx >= ady) {
    for (int8_t o = -half; o <= half; o++) {
      tft.drawLine(x0, y0 + o, x1, y1 + o, color);
    }
  } else {
    for (int8_t o = -half; o <= half; o++) {
      tft.drawLine(x0 + o, y0, x1 + o, y1, color);
    }
  }
}

void MapRenderer::clearMapArea() {
  tft.fillRect(0, 0, SCR_W, MAP_AREA_H, mapsColLand());
  if (!mapsThemeIsDark()) {
    tft.fillCircle(SCR_W - 36, 70, 32, mapsColPark());
    tft.fillCircle(28, MAP_AREA_H - 60, 26, mapsColPark());
  }
}

void MapRenderer::drawThemeSwitchAt(int sx, int sy) {
  const bool dark = mapsThemeIsDark();
  tft.fillRoundRect(sx, sy, SW_W, SW_H, SW_H / 2, mapsColSwitchTrack());
  const int knob = dark ? (sx + SW_W - SW_H / 2) : (sx + SW_H / 2);
  tft.fillCircle(knob, sy + SW_H / 2, SW_H / 2 - 3, mapsColSwitchKnob());
  tft.setTextDatum(MC_DATUM);
  if (dark) {
    tft.setTextColor(mapsColWhite(), mapsColSwitchTrack());
    tft.drawString("D", sx + 12, sy + SW_H / 2, 1);
  } else {
    tft.setTextColor(mapsColMuted(), mapsColSwitchTrack());
    tft.drawString("L", sx + SW_W - 12, sy + SW_H / 2, 1);
  }
  tft.setTextDatum(TL_DATUM);
}

void MapRenderer::drawThemeSwitch() {
  drawThemeSwitchAt(SW_X, SW_Y);
}

void MapRenderer::drawThemeSwitchWaiting() {
  drawThemeSwitchAt(SCR_W - 16 - SW_W, SCR_H - 44);
}

bool MapRenderer::themeSwitchHit(uint16_t x, uint16_t y) {
  /* Generous hit zones — resistive touch on CYD is imprecise. */

  /* Nav overlay: right side of bottom sheet */
  if (y >= static_cast<uint16_t>(MAP_AREA_H) &&
      x >= static_cast<uint16_t>(SCR_W - 90)) {
    return true;
  }
  /* Waiting / status: bottom-right corner */
  if (y >= static_cast<uint16_t>(SCR_H - 70) &&
      x >= static_cast<uint16_t>(SCR_W - 90)) {
    return true;
  }
  return false;
}

void MapRenderer::draw(const NavState& state) {
  clearMapArea();
  drawStreets(state);
  drawRoute(state);
  if (state.hasUserPosition) {
    drawUserMarker(state.userX, state.userY, state.bearing);
  }
  drawOverlay(state);
}

void MapRenderer::drawStreets(const NavState& state) {
  for (int i = 0; i < state.streetSegmentCount; i++) {
    drawThickLine(tft, state.streetX0[i], state.streetY0[i],
                  state.streetX1[i], state.streetY1[i],
                  mapsColRoadEdge(), STREET_EDGE_W);
  }
  for (int i = 0; i < state.streetSegmentCount; i++) {
    drawThickLine(tft, state.streetX0[i], state.streetY0[i],
                  state.streetX1[i], state.streetY1[i],
                  mapsColRoadFill(), STREET_FILL_W);
  }
}

void MapRenderer::drawRoute(const NavState& state) {
  if (state.routeCount < 2) {
    return;
  }
  for (int pass = 0; pass < 2; pass++) {
    const uint16_t color = (pass == 0) ? mapsColRouteEdge() : mapsColRoute();
    const uint8_t width = (pass == 0) ? ROUTE_EDGE_W : ROUTE_FILL_W;
    for (int i = 0; i < state.routeCount - 1; i++) {
      const int16_t x0 = state.routeX[i];
      const int16_t y0 = state.routeY[i];
      const int16_t x1 = state.routeX[i + 1];
      const int16_t y1 = state.routeY[i + 1];
      if (x0 < 0 || y0 < 0 || x1 < 0 || y1 < 0) {
        continue;
      }
      drawThickLine(tft, x0, y0, x1, y1, color, width);
    }
  }
}

void MapRenderer::drawUserMarker(int16_t x, int16_t y, float bearingDeg) {
  if (x < 0 || y < 0 || x >= SCR_W || y >= MAP_AREA_H) {
    return;
  }
  tft.fillCircle(x, y, 9, mapsColWhite());
  tft.fillCircle(x, y, 6, mapsColRoute());
  tft.fillCircle(x, y, 2, mapsColWhite());
  drawArrowIcon(x, y, bearingDeg);
}

void MapRenderer::drawArrowIcon(int16_t cx, int16_t cy, float bearingDeg) {
  const float rad = bearingDeg * DEG_TO_RAD;
  const int16_t tipX = cx + static_cast<int16_t>(sinf(rad) * 14.0f);
  const int16_t tipY = cy - static_cast<int16_t>(cosf(rad) * 14.0f);
  const float leftRad = rad + 2.5f;
  const float rightRad = rad - 2.5f;
  const int16_t leftX = cx + static_cast<int16_t>(sinf(leftRad) * 8.0f);
  const int16_t leftY = cy - static_cast<int16_t>(cosf(leftRad) * 8.0f);
  const int16_t rightX = cx + static_cast<int16_t>(sinf(rightRad) * 8.0f);
  const int16_t rightY = cy - static_cast<int16_t>(cosf(rightRad) * 8.0f);
  tft.fillTriangle(tipX, tipY, leftX, leftY, rightX, rightY, mapsColWhite());

  const int16_t tipX2 = cx + static_cast<int16_t>(sinf(rad) * 12.0f);
  const int16_t tipY2 = cy - static_cast<int16_t>(cosf(rad) * 12.0f);
  const int16_t leftX2 = cx + static_cast<int16_t>(sinf(leftRad) * 6.0f);
  const int16_t leftY2 = cy - static_cast<int16_t>(cosf(leftRad) * 6.0f);
  const int16_t rightX2 = cx + static_cast<int16_t>(sinf(rightRad) * 6.0f);
  const int16_t rightY2 = cy - static_cast<int16_t>(cosf(rightRad) * 6.0f);
  tft.fillTriangle(tipX2, tipY2, leftX2, leftY2, rightX2, rightY2, mapsColRouteDeep());
}

void MapRenderer::drawOverlay(const NavState& state) {
  tft.fillRect(0, MAP_AREA_H - 3, SCR_W, 3, mapsColCardShadow());
  tft.fillRect(0, MAP_AREA_H, SCR_W, OVERLAY_H, mapsColCard());
  tft.fillRoundRect(SCR_W / 2 - 14, MAP_AREA_H + 6, 28, 3, 1, mapsColMuted());
  tft.setTextDatum(TL_DATUM);

  const int textMaxW = SW_X - 16;

  if (state.hasHtml && state.html[0] != '\0') {
    HtmlRenderer htmlRenderer(tft);
    tft.setTextColor(mapsColAccent(), mapsColCard());
    htmlRenderer.drawLines(state.html, 8, MAP_AREA_H + 16, 20, 3);
    drawThemeSwitchAt(SW_X, SW_Y);
    return;
  }

  char distLine[24];
  if (state.distanceM >= 1000) {
    snprintf(distLine, sizeof(distLine), "%.1f km", state.distanceM / 1000.0f);
  } else {
    snprintf(distLine, sizeof(distLine), "%d m", state.distanceM);
  }

  const char* distOrGps = state.gpsWeak
      ? (state.english ? "Weak GPS" : "GPS fraco")
      : distLine;
  drawUtf8Text(tft, distOrGps, 8, MAP_AREA_H + 10, textMaxW, mapsColAccent(), mapsColCard());

  if (state.instruction[0] != '\0') {
    drawUtf8Text(tft, state.instruction, 8, MAP_AREA_H + 30, textMaxW, mapsColText(), mapsColCard());
  }
  if (state.street[0] != '\0') {
    drawUtf8Text(tft, state.street, 8, MAP_AREA_H + 50, textMaxW, mapsColMuted(), mapsColCard());
  }

  char remaining[40] = "";
  if (state.offRoute) {
    snprintf(remaining, sizeof(remaining), "%s", state.english ? "Off route" : "Fora da rota");
  } else if (state.remainingM >= 0 || state.remainingS >= 0) {
    char km[16] = "--";
    char mins[16] = "--";
    if (state.remainingM >= 0) {
      snprintf(km, sizeof(km), state.remainingM < 1000 ? "%.2f km" : "%.1f km", state.remainingM / 1000.0);
    }
    if (state.remainingS >= 0) {
      const int minutes = state.remainingS / 60 + (state.remainingS % 60 != 0);
      if (minutes >= 60) {
        snprintf(mins, sizeof(mins), "%dh%02d", minutes / 60, minutes % 60);
      } else {
        snprintf(mins, sizeof(mins), "%d min", minutes);
      }
    }
    snprintf(remaining, sizeof(remaining), "%s · %s", km, mins);
  }
  if (remaining[0] != '\0') {
    tft.setTextColor(mapsColMuted(), mapsColCard());
    tft.drawString(remaining, 8, MAP_AREA_H + 72, 1);
  }

  drawThemeSwitchAt(SW_X, SW_Y);
}
