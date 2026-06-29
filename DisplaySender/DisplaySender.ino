/**
 * DisplayConnect v2 — ESP32-2432S028 (CYD)
 *
 * WebSocket server receives JSON navigation updates from the Android app
 * and renders a vector map (route + position + maneuver overlay).
 *
 * Protocol (JSON text over WebSocket):
 *   {"type":"heartbeat"}
 *   {"type":"nav","lat":...,"lon":...,"bearing":...,"instruction":"...",
 *    "distance_m":200,"street":"...","route":[[x,y],...],"user_x":120,"user_y":80}
 *
 * Libraries:
 *   - TFT_eSPI (Bodmer)
 *   - WebSockets_Generic (Khoi Hoang)
 *   - ArduinoJson (Benoit Blanchon) v7+
 */

#include "config.h"

#include <WiFi.h>
#include <WebSocketsServer_Generic.h>
#include <SPI.h>
#include <TFT_eSPI.h>

#include "nav_types.h"
#include "map_renderer.h"
#include "nav_protocol.h"

TFT_eSPI tft;
WebSocketsServer webSocket(WS_PORT);
MapRenderer mapRenderer(tft);

static bool clientConnected = false;
static uint32_t navUpdatesReceived = 0;
static uint32_t lastStatsMillis = 0;
static uint16_t updatesPerSecond = 0;

#define COL_BTN      tft.color565(30, 30, 30)

static inline uint16_t colInfoCyan() { return tft.color565(88, 190, 245); }
static inline uint16_t colDim() { return tft.color565(120, 120, 120); }

bool initDisplay();
bool initWiFi();
void showStatusScreen(const char* title, const char* line2 = nullptr, const char* line3 = nullptr);
void showWaitingForAppScreen();
void processTextMessage(const char* payload, size_t length);
void webSocketEvent(const uint8_t& num, const WStype_t& type, uint8_t* payload, const size_t& length);
void updateStatsCounter();

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println(F("=== DisplayConnect CYD v2 ==="));

  if (!initDisplay()) {
    Serial.println(F("Failed to initialize display."));
    while (true) { delay(1000); }
  }

  showStatusScreen("DisplayConnect", "Starting Wi-Fi...");

  if (!initWiFi()) {
    showStatusScreen("Wi-Fi Error", "Restart the board.");
    while (true) { delay(1000); }
  }

  webSocket.begin();
  webSocket.onEvent(webSocketEvent);

  IPAddress addr = WiFi.getMode() == WIFI_AP ? WiFi.softAPIP() : WiFi.localIP();
  Serial.printf("WebSocket at ws://%d.%d.%d.%d:%d\n",
                addr[0], addr[1], addr[2], addr[3], WS_PORT);
  showWaitingForAppScreen();
}

void loop() {
  webSocket.loop();
  updateStatsCounter();
}

bool initDisplay() {
  pinMode(TFT_BL, OUTPUT);
  digitalWrite(TFT_BL, HIGH);

  tft.init();
  tft.writecommand(0x26);
  tft.writedata(2);
  delay(120);
  tft.writecommand(0x26);
  tft.writedata(1);

  tft.setRotation(0);
  tft.fillScreen(TFT_BLACK);
  return true;
}

void showStatusScreen(const char* title, const char* line2, const char* line3) {
  tft.fillScreen(TFT_BLACK);
  tft.setTextDatum(MC_DATUM);

  tft.fillRect(0, 0, SCR_W, 36, COL_BTN);
  tft.setTextColor(TFT_WHITE, COL_BTN);
  tft.drawString(title, SCR_W / 2, 18, 2);

  if (line2 != nullptr) {
    tft.setTextColor(colInfoCyan(), TFT_BLACK);
    tft.drawString(line2, SCR_W / 2, 120, 2);
  }
  if (line3 != nullptr) {
    tft.setTextColor(colDim(), TFT_BLACK);
    tft.drawString(line3, SCR_W / 2, 150, 2);
  }

  tft.setTextColor(colDim(), TFT_BLACK);
  tft.drawString(F("DisplayConnect v2.0"), SCR_W / 2, SCR_H - 20, 2);
}

void showWaitingForAppScreen() {
  static char ipLine[24];
  static char portLine[16];

  const IPAddress addr = WiFi.getMode() == WIFI_AP ? WiFi.softAPIP() : WiFi.localIP();
  snprintf(ipLine, sizeof(ipLine), "IP: %d.%d.%d.%d", addr[0], addr[1], addr[2], addr[3]);
  snprintf(portLine, sizeof(portLine), "Port: %d", WS_PORT);

  showStatusScreen("Waiting for app", ipLine, portLine);
}

bool initWiFi() {
  WiFi.persistent(false);
  WiFi.setSleep(false);

  if (strlen(WIFI_SSID) > 0) {
    Serial.printf("Connecting to \"%s\"...\n", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    const uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED && (millis() - start) < WIFI_CONNECT_TIMEOUT_MS) {
      delay(250);
      Serial.print('.');
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
      Serial.print(F("Wi-Fi STA: "));
      Serial.println(WiFi.localIP());
      return true;
    }

    Serial.println(F("STA failed, starting AP..."));
    WiFi.disconnect(true);
    delay(100);
  } else {
    Serial.println(F("WIFI_SSID empty — AP mode."));
  }

  WiFi.mode(WIFI_AP);
  if (!WiFi.softAP(AP_SSID, AP_PASSWORD)) {
    Serial.println(F("Failed to create AP."));
    return false;
  }

  Serial.print(F("Wi-Fi AP: "));
  Serial.println(WiFi.softAPIP());
  Serial.printf("SSID: %s  Password: %s\n", AP_SSID, AP_PASSWORD);
  return true;
}

void processTextMessage(const char* payload, size_t length) {
  NavState state;
  if (!parseNavJson(payload, length, state)) {
    return;
  }

  mapRenderer.draw(state);
  navUpdatesReceived++;
  updatesPerSecond++;
}

void updateStatsCounter() {
  const uint32_t now = millis();
  if (now - lastStatsMillis >= 1000) {
  if (clientConnected && navUpdatesReceived > 0) {
      Serial.printf("Nav updates/s: %u  Total: %lu\n",
                    updatesPerSecond,
                    static_cast<unsigned long>(navUpdatesReceived));
    }
    updatesPerSecond = 0;
    lastStatsMillis = now;
  }
}

void webSocketEvent(const uint8_t& num, const WStype_t& type, uint8_t* payload, const size_t& length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.printf("[%u] Disconnected\n", num);
      clientConnected = false;
      navUpdatesReceived = 0;
      showWaitingForAppScreen();
      break;

    case WStype_CONNECTED: {
      IPAddress ip = webSocket.remoteIP(num);
      Serial.printf("[%u] Connected from %s\n", num, ip.toString().c_str());
      clientConnected = true;
      navUpdatesReceived = 0;
      updatesPerSecond = 0;
      lastStatsMillis = millis();
      tft.fillScreen(TFT_BLACK);
      webSocket.sendTXT(num, "OK");
      break;
    }

    case WStype_TEXT:
      if (payload != nullptr && length > 0) {
        processTextMessage(reinterpret_cast<const char*>(payload), length);
      }
      break;

    case WStype_BIN:
      Serial.println(F("Binary messages ignored in v2 (use JSON)."));
      break;

    case WStype_ERROR:
      Serial.printf("[%u] WebSocket error\n", num);
      break;

    case WStype_PING:
    case WStype_PONG:
      break;

    default:
      break;
  }
}
