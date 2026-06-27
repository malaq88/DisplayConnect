#pragma once

// =============================================================================
// Wi-Fi — preencha para conectar à rede do celular.
// Se vazio, a ESP32 inicia um ponto de acesso em 192.168.4.1
// =============================================================================
#define WIFI_SSID "VOO_ANTONIO"
#define WIFI_PASSWORD "ml0118@@9211"

#define AP_SSID     "DisplayConnect-CYD"
#define AP_PASSWORD "display123"

#define WS_PORT 81

#define WIFI_CONNECT_TIMEOUT_MS 15000
#define JPEG_MAX_SIZE 65536

// =============================================================================
// Display TFT — configurado em Arduino/libraries/TFT_eSPI/Setup_User.h
// (CYDAlbumPlayer). User_Setup_Select.h inclui Setup_User.h.
// =============================================================================
