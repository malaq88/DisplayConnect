#pragma once

#include <stdint.h>
#include <stdbool.h>

enum MapsTheme : uint8_t {
  MAPS_THEME_LIGHT = 0,
  MAPS_THEME_DARK = 1,
};

void mapsThemeInit();
MapsTheme mapsThemeGet();
bool mapsThemeIsDark();
void mapsThemeSet(MapsTheme theme);
bool mapsThemeToggle(); /* returns new isDark */

uint16_t mapsColLand();
uint16_t mapsColPark();
uint16_t mapsColRoadEdge();
uint16_t mapsColRoadFill();
uint16_t mapsColRouteEdge();
uint16_t mapsColRoute();
uint16_t mapsColRouteDeep();
uint16_t mapsColCard();
uint16_t mapsColCardShadow();
uint16_t mapsColText();
uint16_t mapsColMuted();
uint16_t mapsColAccent();
uint16_t mapsColWhite();
uint16_t mapsColSwitchTrack();
uint16_t mapsColSwitchKnob();
