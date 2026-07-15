#pragma once

#include <stddef.h>
#include "nav_types.h"

/** Parse JSON nav message into NavState. Requires ArduinoJson library. */
bool parseNavJson(const char* json, size_t length, NavState& state);

/** True when message is {"type":"loading"} (show loading UI on CYD). */
bool isLoadingJson(const char* json, size_t length);
