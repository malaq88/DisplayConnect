#pragma once

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#include "esp_err.h"

typedef void (*ble_rx_bytes_cb_t)(const uint8_t *data, size_t len, void *ctx);
typedef void (*ble_conn_cb_t)(bool connected, void *ctx);

typedef struct {
    ble_rx_bytes_cb_t on_rx;
    ble_conn_cb_t on_conn;
    void *ctx;
} ble_nus_callbacks_t;

esp_err_t ble_nus_start(const ble_nus_callbacks_t *cbs);
esp_err_t ble_nus_notify(const char *text);
void ble_nus_restart_advertising(void);
bool ble_nus_is_connected(void);
