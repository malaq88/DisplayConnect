#pragma once

#include "esp_err.h"
#include "esp_lcd_panel_ops.h"
#include "ui.h"

esp_err_t display_init(ui_t *ui);
void display_set_brightness(int percent);
