#pragma once

#define BLE_DEVICE_NAME     "DisplayConnect-S3"

/* Nordic UART Service (NUS) — same UUIDs as Android BleNavClient */
#define NUS_SERVICE_UUID    "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_RX_UUID         "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_TX_UUID         "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

#define BLE_PREFERRED_MTU   256

/* Android MapProjector still emits 240×232 map pixels; we scale on device. */
#define PROTO_MAP_W         240
#define PROTO_MAP_H         232
