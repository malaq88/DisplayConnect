#include "html_renderer.h"
#include <ctype.h>
#include <string.h>

HtmlRenderer::HtmlRenderer(TFT_eSPI& display) : tft(display) {}

void HtmlRenderer::stripTag(char* text) {
  if (text == nullptr) {
    return;
  }
  char* write = text;
  bool inTag = false;
  for (const char* read = text; *read != '\0'; ++read) {
    if (*read == '<') {
      inTag = true;
      continue;
    }
    if (*read == '>') {
      inTag = false;
      continue;
    }
    if (!inTag) {
      *write++ = *read;
    }
  }
  *write = '\0';

  // Trim leading / collapse whitespace
  char* dst = text;
  bool space = true;
  for (const char* src = text; *src != '\0'; ++src) {
    if (isspace(static_cast<unsigned char>(*src))) {
      if (!space) {
        *dst++ = ' ';
        space = true;
      }
    } else {
      *dst++ = *src;
      space = false;
    }
  }
  if (dst > text && *(dst - 1) == ' ') {
    --dst;
  }
  *dst = '\0';
}

bool HtmlRenderer::nextLine(const char*& cursor, char* out, size_t outSize) {
  if (cursor == nullptr || out == nullptr || outSize == 0) {
    return false;
  }
  while (*cursor == '\n' || *cursor == '\r') {
    ++cursor;
  }
  if (*cursor == '\0') {
    return false;
  }

  size_t i = 0;
  while (*cursor != '\0' && *cursor != '\n' && *cursor != '\r' && i < outSize - 1) {
    out[i++] = *cursor++;
  }
  out[i] = '\0';
  stripTag(out);
  return out[0] != '\0';
}

int HtmlRenderer::drawLines(const char* html, int16_t x, int16_t yStart, int16_t lineHeight, int maxLines) {
  if (html == nullptr || html[0] == '\0' || maxLines <= 0) {
    return 0;
  }

  const char* cursor = html;
  char line[96];
  int drawn = 0;
  int16_t y = yStart;

  tft.setTextDatum(TL_DATUM);
  while (drawn < maxLines && nextLine(cursor, line, sizeof(line))) {
    tft.drawString(line, x, y, 2);
    y += lineHeight;
    ++drawn;
  }
  return drawn;
}
