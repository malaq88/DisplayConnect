#pragma once

#include <stddef.h>
#include <stdbool.h>
#include "nav_types.h"

bool is_loading_json(const char *json, size_t length);
bool parse_nav_json(const char *json, size_t length, nav_state_t *state);
