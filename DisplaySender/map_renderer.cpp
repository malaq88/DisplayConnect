#include "map_renderer.h"
#include "html_renderer.h"
#include <math.h>

#ifndef DEG_TO_RAD
#define DEG_TO_RAD 0.01745329251f
#endif

static const uint16_t COL_ROUTE = 0x07E0;   // green
static const uint16_t COL_USER = 0x5BF7;    // cyan
static const uint16_t COL_OVERLAY_BG = 0x3186;
static const uint16_t COL_DIM = 0x7BEF;

MapRenderer::MapRenderer(TFT_eSPI& display) : tft(display) {}

void MapRenderer::clearMapArea() {
  tft.fillRect(0, 0, SCR_W, MAP_AREA_H, TFT_BLACK);
}

void MapRenderer::draw(const NavState& state) {
  clearMapArea();
  drawRoute(state);
  if (state.hasUserPosition) {
    drawUserMarker(state.userX, state.userY, state.bearing);
  }
  drawOverlay(state);
}

void MapRenderer::drawRoute(const NavState& state) {
  if (state.routeCount < 2) {
    return;
  }

  for (int i = 0; i < state.routeCount - 1; i++) {
    const int16_t x0 = state.routeX[i];
    const int16_t y0 = state.routeY[i];
    const int16_t x1 = state.routeX[i + 1];
    const int16_t y1 = state.routeY[i + 1];
    if (x0 < 0 || y0 < 0 || x1 < 0 || y1 < 0) {
      continue;
    }
    tft.drawLine(x0, y0, x1, y1, COL_ROUTE);
  }
}

void MapRenderer::drawUserMarker(int16_t x, int16_t y, float bearingDeg) {
  if (x < 0 || y < 0 || x >= SCR_W || y >= MAP_AREA_H) {
    return;
  }
  drawArrowIcon(x, y, bearingDeg, COL_USER);
  tft.fillCircle(x, y, 3, COL_USER);
}

void MapRenderer::drawArrowIcon(int16_t cx, int16_t cy, float bearingDeg, uint16_t color) {
  const float rad = bearingDeg * DEG_TO_RAD;
  const int16_t tipX = cx + static_cast<int16_t>(sinf(rad) * 14.0f);
  const int16_t tipY = cy - static_cast<int16_t>(cosf(rad) * 14.0f);
  const float leftRad = rad + 2.4f;
  const float rightRad = rad - 2.4f;
  const int16_t leftX = cx + static_cast<int16_t>(sinf(leftRad) * 8.0f);
  const int16_t leftY = cy - static_cast<int16_t>(cosf(leftRad) * 8.0f);
  const int16_t rightX = cx + static_cast<int16_t>(sinf(rightRad) * 8.0f);
  const int16_t rightY = cy - static_cast<int16_t>(cosf(rightRad) * 8.0f);
  tft.fillTriangle(tipX, tipY, leftX, leftY, rightX, rightY, color);
}

void MapRenderer::drawOverlay(const NavState& state) {
  tft.fillRect(0, MAP_AREA_H, SCR_W, OVERLAY_H, COL_OVERLAY_BG);
  tft.setTextDatum(TL_DATUM);

  if (state.hasHtml && state.html[0] != '\0') {
    HtmlRenderer htmlRenderer(tft);
    tft.setTextColor(TFT_WHITE, COL_OVERLAY_BG);
    htmlRenderer.drawLines(state.html, 8, MAP_AREA_H + 8, 22, 3);
    return;
  }

  tft.setTextColor(TFT_WHITE, COL_OVERLAY_BG);

  char distLine[24];
  if (state.distanceM >= 1000) {
    snprintf(distLine, sizeof(distLine), "%.1f km", state.distanceM / 1000.0f);
  } else {
    snprintf(distLine, sizeof(distLine), "%d m", state.distanceM);
  }

  tft.drawString(distLine, 8, MAP_AREA_H + 8, 2);

  if (state.instruction[0] != '\0') {
    tft.drawString(state.instruction, 8, MAP_AREA_H + 30, 2);
  }
  if (state.street[0] != '\0') {
    tft.setTextColor(COL_DIM, COL_OVERLAY_BG);
    tft.drawString(state.street, 8, MAP_AREA_H + 52, 2);
  }
}
