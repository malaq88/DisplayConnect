#pragma once

#include <TFT_eSPI.h>
#include "nav_types.h"

class MapRenderer {
public:
  explicit MapRenderer(TFT_eSPI& display);

  void draw(const NavState& state);
  void clearMapArea();
  void drawThemeSwitch();
  void drawThemeSwitchWaiting();
  static bool themeSwitchHit(uint16_t x, uint16_t y);

private:
  TFT_eSPI& tft;

  void drawRoute(const NavState& state);
  void drawStreets(const NavState& state);
  void drawUserMarker(int16_t x, int16_t y, float bearingDeg);
  void drawOverlay(const NavState& state);
  void drawArrowIcon(int16_t cx, int16_t cy, float bearingDeg);
  void drawThemeSwitchAt(int sx, int sy);
};
