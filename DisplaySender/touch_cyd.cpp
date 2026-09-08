#include "touch_cyd.h"
#include "nav_types.h"

#include <Arduino.h>
#include <SPI.h>
#include <XPT2046_Touchscreen.h>

/* ESP32-2432S028 (CYD) — touch on its own SPI bus (TFT uses HSPI). */
static const uint8_t TOUCH_CS = 33;
static const uint8_t TOUCH_IRQ = 36;
static const uint8_t TOUCH_MOSI = 32;
static const uint8_t TOUCH_MISO = 39;
static const uint8_t TOUCH_CLK = 25;

/* Calibration for portrait (tft.setRotation(0)). Tuned for common CYD panels. */
static const int RAW_X_MIN = 200;
static const int RAW_X_MAX = 3800;
static const int RAW_Y_MIN = 200;
static const int RAW_Y_MAX = 3800;

/* No IRQ pin (255): poll by pressure. IRQ gating often blocks all touches on CYD. */
static SPIClass s_touchSpi(VSPI);
static XPT2046_Touchscreen s_ts(TOUCH_CS, 255);
static bool s_ok = false;
static uint32_t s_lastLogMs = 0;

bool touchInit() {
  pinMode(TOUCH_CS, OUTPUT);
  digitalWrite(TOUCH_CS, HIGH);
  /* Keep IRQ as input so the controller can settle; we don't gate on it. */
  pinMode(TOUCH_IRQ, INPUT);

  s_touchSpi.begin(TOUCH_CLK, TOUCH_MISO, TOUCH_MOSI, TOUCH_CS);
  if (!s_ts.begin(s_touchSpi)) {
    Serial.println(F("Touch begin failed"));
    return false;
  }
  /* Match DisplaySender.ino tft.setRotation(0) */
  s_ts.setRotation(0);
  s_ok = true;
  Serial.println(F("Touch XPT2046 ready (poll mode)"));
  return true;
}

bool touchRead(uint16_t& x, uint16_t& y) {
  if (!s_ok) {
    return false;
  }

  if (!s_ts.touched()) {
    return false;
  }

  const TS_Point p = s_ts.getPoint();
  if (p.z < 200) {
    return false;
  }

  /* Library rotation 0 already swaps axes into display-oriented raw space. */
  int16_t px = map(p.x, RAW_X_MIN, RAW_X_MAX, 0, SCR_W - 1);
  int16_t py = map(p.y, RAW_Y_MIN, RAW_Y_MAX, 0, SCR_H - 1);

  if (px < 0) px = 0;
  if (py < 0) py = 0;
  if (px >= SCR_W) px = SCR_W - 1;
  if (py >= SCR_H) py = SCR_H - 1;

  x = static_cast<uint16_t>(px);
  y = static_cast<uint16_t>(py);

  const uint32_t now = millis();
  if (now - s_lastLogMs > 300) {
    s_lastLogMs = now;
    Serial.printf("touch raw=%d,%d z=%d -> %u,%u\n", p.x, p.y, p.z, x, y);
  }
  return true;
}
