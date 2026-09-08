#include "maps_theme.h"

#include <Preferences.h>

static Preferences s_prefs;
static MapsTheme s_theme = MAPS_THEME_LIGHT;

static inline uint16_t rgb565(uint8_t r, uint8_t g, uint8_t b) {
  return static_cast<uint16_t>(((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3));
}

void mapsThemeInit() {
  if (s_prefs.begin("ui", true)) {
    const uint8_t v = s_prefs.getUChar("theme", MAPS_THEME_LIGHT);
    if (v <= MAPS_THEME_DARK) {
      s_theme = static_cast<MapsTheme>(v);
    }
    s_prefs.end();
  }
}

MapsTheme mapsThemeGet() {
  return s_theme;
}

bool mapsThemeIsDark() {
  return s_theme == MAPS_THEME_DARK;
}

void mapsThemeSet(MapsTheme theme) {
  s_theme = theme;
  if (s_prefs.begin("ui", false)) {
    s_prefs.putUChar("theme", static_cast<uint8_t>(theme));
    s_prefs.end();
  }
}

bool mapsThemeToggle() {
  mapsThemeSet(s_theme == MAPS_THEME_DARK ? MAPS_THEME_LIGHT : MAPS_THEME_DARK);
  return mapsThemeIsDark();
}

uint16_t mapsColLand() {
  return mapsThemeIsDark() ? rgb565(48, 52, 58) : rgb565(248, 249, 251);
}

uint16_t mapsColPark() {
  return mapsThemeIsDark() ? rgb565(58, 78, 62) : rgb565(220, 238, 210);
}

uint16_t mapsColRoadEdge() {
  return mapsThemeIsDark() ? rgb565(90, 96, 104) : rgb565(210, 214, 220);
}

uint16_t mapsColRoadFill() {
  return mapsThemeIsDark() ? rgb565(72, 76, 84) : rgb565(255, 255, 255);
}

uint16_t mapsColRouteEdge() {
  return mapsThemeIsDark() ? rgb565(32, 36, 42) : rgb565(255, 255, 255);
}

uint16_t mapsColRoute() {
  return rgb565(66, 133, 244);
}

uint16_t mapsColRouteDeep() {
  return rgb565(26, 115, 232);
}

uint16_t mapsColCard() {
  return mapsThemeIsDark() ? rgb565(58, 62, 70) : rgb565(255, 255, 255);
}

uint16_t mapsColCardShadow() {
  return mapsThemeIsDark() ? rgb565(36, 38, 44) : rgb565(200, 206, 214);
}

uint16_t mapsColText() {
  return mapsThemeIsDark() ? rgb565(240, 242, 245) : rgb565(32, 33, 36);
}

uint16_t mapsColMuted() {
  return mapsThemeIsDark() ? rgb565(160, 168, 176) : rgb565(95, 99, 104);
}

uint16_t mapsColAccent() {
  return mapsThemeIsDark() ? rgb565(138, 180, 248) : rgb565(26, 115, 232);
}

uint16_t mapsColWhite() {
  return rgb565(255, 255, 255);
}

uint16_t mapsColSwitchTrack() {
  return mapsThemeIsDark() ? rgb565(66, 133, 244) : rgb565(200, 206, 214);
}

uint16_t mapsColSwitchKnob() {
  return rgb565(255, 255, 255);
}
