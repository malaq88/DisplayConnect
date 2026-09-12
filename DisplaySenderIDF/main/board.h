/*
 * Pin map — Guition JC3248W535EN (ESP32-S3).
 * Verified against NorthernMan54/JC3248W535EN and GlomarGadaffi/jc3248-display-driver.
 */
#pragma once

#include "driver/gpio.h"
#include "driver/spi_master.h"

/* Display: AXS15231B, 320x480 IPS, QSPI */
#define LCD_H_RES           320
#define LCD_V_RES           480
#define PIN_LCD_CS          GPIO_NUM_45
#define PIN_LCD_SCK         GPIO_NUM_47
#define PIN_LCD_D0          GPIO_NUM_21
#define PIN_LCD_D1          GPIO_NUM_48
#define PIN_LCD_D2          GPIO_NUM_40
#define PIN_LCD_D3          GPIO_NUM_39
#define PIN_LCD_BL          GPIO_NUM_1
#define LCD_HOST            SPI2_HOST
#define LCD_QSPI_PCLK_HZ    (40 * 1000 * 1000)  /* 40 MHz stable ceiling */

/* Touch I2C (AXS15231B) */
#define PIN_TOUCH_SCL       GPIO_NUM_8
#define PIN_TOUCH_SDA       GPIO_NUM_4
