#include "utf8_text.h"
#include "latin_font.h"
#include <Preferences.h>

static bool englishLanguage = false;
bool displayEnglish() { return englishLanguage; }
void initDisplayLanguage() {
  Preferences preferences;
  preferences.begin("display-lang", true);
  englishLanguage = preferences.getBool("en", false);
  preferences.end();
}
void setDisplayLanguage(bool english) {
  if (english == englishLanguage) return;
  englishLanguage = english;
  Preferences preferences;
  preferences.begin("display-lang", false);
  preferences.putBool("en", english);
  preferences.end();
}

static uint16_t nextCodepoint(const char*& cursor) {
  uint8_t first = static_cast<uint8_t>(*cursor++);
  if (first < 128) return first;
  int remaining = (first & 0xE0) == 0xC0 ? 1 : (first & 0xF0) == 0xE0 ? 2 : (first & 0xF8) == 0xF0 ? 3 : 0;
  if (!remaining) return '?';
  uint32_t value = first & (remaining == 1 ? 0x1F : remaining == 2 ? 0x0F : 0x07);
  for (int i = 0; i < remaining; ++i) {
    uint8_t next = static_cast<uint8_t>(*cursor);
    if (!next || (next & 0xC0) != 0x80) return '?';
    ++cursor;
    value = (value << 6) | (next & 0x3F);
  }
  if (value == 0x2013 || value == 0x2014) return '-';
  if (value == 0x2018 || value == 0x2019) return '\'';
  return value >= 32 && value <= 255 ? value : '?';
}

static LatinGlyph glyphFor(uint16_t code) {
  LatinGlyph glyph;
  if (code < 32 || code > 255) code = '?';
  memcpy_P(&glyph, &latinGlyphs[code - 32], sizeof(glyph));
  return glyph;
}

int utf8TextWidth(const char* text) {
  int width = 0;
  if (text) while (*text) width += glyphFor(nextCodepoint(text)).width;
  return width;
}

void drawUtf8Text(TFT_eSPI& tft, const char* text, int x, int y, int maxWidth, uint16_t color, uint16_t background) {
  if (!text) return;
  const int right = x + maxWidth;
  while (*text) {
    const LatinGlyph glyph = glyphFor(nextCodepoint(text));
    if (x + glyph.width > right) break;
    tft.fillRect(x, y, glyph.width, 20, background);
    tft.drawBitmap(x, y, latinPixels + glyph.offset, glyph.width, 20, color);
    x += glyph.width;
  }
}
