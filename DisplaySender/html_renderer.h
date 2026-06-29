#pragma once

#include <TFT_eSPI.h>

/** Renders a minimal HTML fragment (plain tags) as text lines on the TFT. */
class HtmlRenderer {
public:
  explicit HtmlRenderer(TFT_eSPI& display);

  /** Draw up to [maxLines] at yStart; returns number of lines drawn. */
  int drawLines(const char* html, int16_t x, int16_t yStart, int16_t lineHeight, int maxLines);

private:
  TFT_eSPI& tft;

  static void stripTag(char* text);
  static bool nextLine(const char*& cursor, char* out, size_t outSize);
};
