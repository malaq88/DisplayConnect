#include "loading_screen.h"
#include "nav_types.h"

#include <math.h>

#ifndef DEG_TO_RAD
#define DEG_TO_RAD 0.01745329251f
#endif

static const uint16_t COL_MAP_BG = 0x2945;
static const uint16_t COL_OVERLAY_BG = 0x3186;
static const uint16_t COL_SPINNER = 0x5BF7;
static const uint16_t COL_SPINNER_DIM = 0x3186;
static const uint16_t COL_TEXT_DIM = 0x7BEF;

static const int16_t SPINNER_CX = SCR_W / 2;
static const int16_t SPINNER_CY = MAP_AREA_H / 2;
static const int16_t SPINNER_R = 22;
static const uint8_t SPINNER_DOTS = 8;

static uint8_t s_lastSpinnerFrame = 255;

static void drawSpinnerAt(TFT_eSPI& tft, uint8_t activeDot) {
  for (uint8_t i = 0; i < SPINNER_DOTS; i++) {
    const float angle = (static_cast<float>(i) / SPINNER_DOTS) * 6.2831853f - 1.5707963f;
    const int16_t x = SPINNER_CX + static_cast<int16_t>(cosf(angle) * SPINNER_R);
    const int16_t y = SPINNER_CY + static_cast<int16_t>(sinf(angle) * SPINNER_R);
    const uint8_t radius = (i == activeDot) ? 4 : 3;
    const uint16_t color = (i == activeDot) ? COL_SPINNER : COL_SPINNER_DIM;
    tft.fillCircle(x, y, radius, color);
  }
}

void showMapLoadingScreen(TFT_eSPI& tft) {
  s_lastSpinnerFrame = 255;

  tft.fillRect(0, 0, SCR_W, MAP_AREA_H, COL_MAP_BG);

  tft.fillRect(0, MAP_AREA_H, SCR_W, OVERLAY_H, COL_OVERLAY_BG);
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(TFT_WHITE, COL_OVERLAY_BG);
  tft.drawString(F("Loading map..."), SCR_W / 2, MAP_AREA_H + 28, 2);
  tft.setTextColor(COL_TEXT_DIM, COL_OVERLAY_BG);
  tft.drawString(F("Waiting for route data"), SCR_W / 2, MAP_AREA_H + 52, 2);

  drawSpinnerAt(tft, 0);
}

void updateMapLoadingAnimation(TFT_eSPI& tft, uint32_t nowMs) {
  const uint8_t frame = static_cast<uint8_t>((nowMs / 180U) % SPINNER_DOTS);
  if (frame == s_lastSpinnerFrame) {
    return;
  }
  s_lastSpinnerFrame = frame;

  tft.fillRect(
    SPINNER_CX - SPINNER_R - 8,
    SPINNER_CY - SPINNER_R - 8,
    (SPINNER_R + 8) * 2,
    (SPINNER_R + 8) * 2,
    COL_MAP_BG
  );
  drawSpinnerAt(tft, frame);
}
