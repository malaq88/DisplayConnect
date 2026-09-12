#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

esp_err_t touch_init(void);
/** Poll touch. Returns true if pressed; writes panel coordinates. */
bool touch_read(uint16_t *x, uint16_t *y);
