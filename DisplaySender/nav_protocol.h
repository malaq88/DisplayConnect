#pragma once

#include <stddef.h>
#include "nav_types.h"

/** Parse JSON nav message into NavState. Requires ArduinoJson library. */
bool parseNavJson(const char* json, size_t length, NavState& state);
