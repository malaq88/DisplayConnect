#pragma once

#include <TFT_eSPI.h>
#include <stdint.h>

/** Full-screen layout shown while waiting for the first nav JSON. */
void showMapLoadingScreen(TFT_eSPI& tft);

/** Animate spinner in the map area; call from loop() while loading. */
void updateMapLoadingAnimation(TFT_eSPI& tft, uint32_t nowMs);
