#pragma once

#include <stdint.h>
#include <stdbool.h>

bool touchInit();
/** Returns true if pressed; writes screen coordinates (0..SCR_W/H). */
bool touchRead(uint16_t& x, uint16_t& y);
