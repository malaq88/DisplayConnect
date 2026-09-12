#include "touch.h"
#include "board.h"

#include "driver/i2c_master.h"
#include "esp_check.h"
#include "esp_lcd_axs15231b.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_touch.h"
#include "esp_log.h"

static const char *TAG = "touch";
static esp_lcd_touch_handle_t s_tp;

esp_err_t touch_init(void)
{
    i2c_master_bus_handle_t i2c_bus = NULL;
    const i2c_master_bus_config_t i2c_cfg = {
        .i2c_port = I2C_NUM_0,
        .sda_io_num = PIN_TOUCH_SDA,
        .scl_io_num = PIN_TOUCH_SCL,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    ESP_RETURN_ON_ERROR(i2c_new_master_bus(&i2c_cfg, &i2c_bus), TAG, "i2c bus");

    esp_lcd_panel_io_handle_t tp_io = NULL;
    esp_lcd_panel_io_i2c_config_t tp_io_cfg = ESP_LCD_TOUCH_IO_I2C_AXS15231B_CONFIG();
    ESP_RETURN_ON_ERROR(esp_lcd_new_panel_io_i2c(i2c_bus, &tp_io_cfg, &tp_io), TAG, "tp io");

    const esp_lcd_touch_config_t tp_cfg = {
        .x_max = LCD_H_RES,
        .y_max = LCD_V_RES,
        .rst_gpio_num = -1,
        .int_gpio_num = -1,
        .levels = {
            .reset = 0,
            .interrupt = 0,
        },
        .flags = {
            .swap_xy = 0,
            .mirror_x = 0,
            .mirror_y = 0,
        },
    };
    ESP_RETURN_ON_ERROR(esp_lcd_touch_new_i2c_axs15231b(tp_io, &tp_cfg, &s_tp), TAG, "tp");
    ESP_LOGI(TAG, "Touch ready (AXS15231B)");
    return ESP_OK;
}

bool touch_read(uint16_t *x, uint16_t *y)
{
    if (!s_tp || !x || !y) {
        return false;
    }
    if (esp_lcd_touch_read_data(s_tp) != ESP_OK) {
        return false;
    }

    esp_lcd_touch_point_data_t pt[1] = {0};
    uint8_t n = 0;
    if (esp_lcd_touch_get_data(s_tp, pt, &n, 1) != ESP_OK || n == 0) {
        return false;
    }
    *x = pt[0].x;
    *y = pt[0].y;
    return true;
}
