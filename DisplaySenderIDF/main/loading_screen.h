#pragma once

#include <stdint.h>
#include "ui.h"

void show_map_loading_screen(ui_t *ui);
void update_map_loading_animation(ui_t *ui, uint32_t now_ms);
