#pragma once
#include <TFT_eSPI.h>

int utf8TextWidth(const char* text);
void drawUtf8Text(TFT_eSPI& tft, const char* text, int x, int y, int maxWidth, uint16_t color, uint16_t background);
bool displayEnglish();
void setDisplayLanguage(bool english);
void initDisplayLanguage();
