/**
 * DisplayConnect — Firmware para ESP32-2432S028 (Cheap Yellow Display)
 *
 * Servidor WebSocket que recebe quadros JPEG do app Android DisplayConnect
 * e exibe no TFT 240×320.
 *
 * Protocolo (compatível com DisplaySocketClient):
 *   Mensagem 1: 4 bytes big-endian = tamanho do JPEG (0 = heartbeat, ignorar)
 *   Mensagem 2: N bytes = dados JPEG
 *
 * Bibliotecas necessárias (já instaladas em Documentos/Arduino/libraries):
 *   - TFT_eSPI           (Bodmer)
 *   - TJpg_Decoder       (Bodmer)
 *   - WebSockets_Generic (Khoi Hoang — fork do arduinoWebSockets)
 */

#include "config.h"

#include <WiFi.h>
#include <WebSocketsServer_Generic.h>
#include <SPI.h>
#include <TFT_eSPI.h>
#include <TJpg_Decoder.h>

// ---------------------------------------------------------------------------
// Objetos globais
// ---------------------------------------------------------------------------

TFT_eSPI tft;
WebSocketsServer webSocket(WS_PORT);

// ── Cores (CYDAlbumPlayer) ──────────────────────────────────────────────────
#define COL_BG       TFT_BLACK
#define COL_TEXT     TFT_WHITE
#define COL_DIM      tft.color565(120, 120, 120)
#define COL_ACCENT   tft.color565(50, 205, 50)
#define COL_BTN      tft.color565(30, 30, 30)
#define COL_BTN_ACT  tft.color565(70, 70, 70)
#define COL_DIR      tft.color565(35, 45, 70)

static const int SCR_W = 240;
static const int SCR_H = 320;

static inline uint16_t colInfoCyan() { return tft.color565(88, 190, 245); }

static uint32_t pendingFrameSize = 0;
static bool awaitingFrameBody = false;

static bool clientConnected = false;
static uint32_t framesReceived = 0;
static uint32_t lastFpsMillis = 0;
static uint16_t fpsCounter = 0;
static float currentFps = 0.0f;

// ---------------------------------------------------------------------------
// Protótipos
// ---------------------------------------------------------------------------

bool tftJpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap);
bool initDisplay();
bool initWiFi();
void showStatusScreen(const char* title, const char* line2 = nullptr, const char* line3 = nullptr);
void showWaitingForAppScreen();
void processBinaryMessage(uint8_t clientNum, uint8_t* payload, size_t length);
bool decodeAndDrawJpeg(const uint8_t* data, size_t length);
void webSocketEvent(const uint8_t& num, const WStype_t& type, uint8_t* payload, const size_t& length);
void updateFpsCounter();

// ---------------------------------------------------------------------------
// setup / loop
// ---------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println(F("=== DisplayConnect CYD Firmware ==="));

  if (!initDisplay()) {
    Serial.println(F("Failed to initialize display."));
    while (true) { delay(1000); }
  }

  showStatusScreen("DisplayConnect", "Starting Wi-Fi...");

  if (!initWiFi()) {
    showStatusScreen("Wi-Fi Error", "Restart the board.");
    while (true) { delay(1000); }
  }

  TJpgDec.setJpgScale(1);
  TJpgDec.setSwapBytes(true);
  TJpgDec.setCallback(tftJpegOutput);

  webSocket.begin();
  webSocket.onEvent(webSocketEvent);

  IPAddress addr = WiFi.getMode() == WIFI_AP ? WiFi.softAPIP() : WiFi.localIP();
  Serial.printf("WebSocket at ws://%d.%d.%d.%d:%d\n",
                addr[0], addr[1], addr[2], addr[3], WS_PORT);
  showWaitingForAppScreen();
}

void loop() {
  webSocket.loop();
  updateFpsCounter();
}

// ---------------------------------------------------------------------------
// Display
// ---------------------------------------------------------------------------

bool initDisplay() {
  pinMode(TFT_BL, OUTPUT);
  digitalWrite(TFT_BL, HIGH);

  tft.init();

  // Ajuste de gamma para ILI9341_2 em placas CYD (CYDAlbumPlayer)
  tft.writecommand(0x26);
  tft.writedata(2);
  delay(120);
  tft.writecommand(0x26);
  tft.writedata(1);

  tft.setRotation(0);
  tft.fillScreen(COL_BG);
  return true;
}

void showStatusScreen(const char* title, const char* line2, const char* line3) {
  tft.fillScreen(COL_BG);
  tft.setTextDatum(MC_DATUM);

  tft.fillRect(0, 0, SCR_W, 36, COL_BTN);
  tft.setTextColor(COL_TEXT, COL_BTN);
  tft.drawString(title, SCR_W / 2, 18, 2);

  if (line2 != nullptr) {
    tft.setTextColor(colInfoCyan(), COL_BG);
    tft.drawString(line2, SCR_W / 2, 120, 2);
  }
  if (line3 != nullptr) {
    tft.setTextColor(COL_DIM, COL_BG);
    tft.drawString(line3, SCR_W / 2, 150, 2);
  }

  tft.setTextColor(COL_DIM, COL_BG);
  tft.drawString(F("DisplayConnect v1.0"), SCR_W / 2, SCR_H - 20, 2);
}

void showWaitingForAppScreen() {
  static char ipLine[24];
  static char portLine[16];

  const IPAddress addr = WiFi.getMode() == WIFI_AP ? WiFi.softAPIP() : WiFi.localIP();
  snprintf(ipLine, sizeof(ipLine), "IP: %d.%d.%d.%d", addr[0], addr[1], addr[2], addr[3]);
  snprintf(portLine, sizeof(portLine), "Port: %d", WS_PORT);

  showStatusScreen("Waiting for app", ipLine, portLine);
}

bool tftJpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap) {
  if (y >= tft.height()) {
    return false;
  }
  tft.pushImage(x, y, w, h, bitmap);
  return true;
}

// ---------------------------------------------------------------------------
// Wi-Fi
// ---------------------------------------------------------------------------

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
  const bool apOk = WiFi.softAP(AP_SSID, AP_PASSWORD);
  if (!apOk) {
    Serial.println(F("Failed to create AP."));
    return false;
  }

  Serial.print(F("Wi-Fi AP: "));
  Serial.println(WiFi.softAPIP());
  Serial.printf("SSID: %s  Password: %s\n", AP_SSID, AP_PASSWORD);
  return true;
}

// ---------------------------------------------------------------------------
// Protocolo JPEG
// ---------------------------------------------------------------------------

static uint32_t readUInt32BigEndian(const uint8_t* data) {
  return (static_cast<uint32_t>(data[0]) << 24) |
         (static_cast<uint32_t>(data[1]) << 16) |
         (static_cast<uint32_t>(data[2]) << 8)  |
          static_cast<uint32_t>(data[3]);
}

bool decodeAndDrawJpeg(const uint8_t* data, size_t length) {
  if (data == nullptr || length < 4) {
    return false;
  }

  const JRESULT result = TJpgDec.drawJpg(0, 0, data, length);
  if (result != JDR_OK) {
    Serial.printf("JPEG error: %d\n", result);
    return false;
  }

  framesReceived++;
  fpsCounter++;
  return true;
}

void processBinaryMessage(uint8_t clientNum, uint8_t* payload, size_t length) {
  if (length == 0) {
    return;
  }

  // Mensagem única: cabeçalho + corpo
  if (length > 4 && !awaitingFrameBody) {
    const uint32_t declared = readUInt32BigEndian(payload);
    const size_t bodyLen = length - 4;

    if (declared == 0) {
      return; // heartbeat embutido
    }

    if (declared == bodyLen && declared <= JPEG_MAX_SIZE) {
      if (decodeAndDrawJpeg(payload + 4, bodyLen)) {
        pendingFrameSize = 0;
        awaitingFrameBody = false;
      }
      return;
    }
  }

  // Cabeçalho de tamanho (4 bytes)
  if (length == 4) {
    const uint32_t frameSize = readUInt32BigEndian(payload);

    if (frameSize == 0) {
      return; // heartbeat do DisplaySocketClient
    }

    if (frameSize > JPEG_MAX_SIZE) {
      Serial.printf("Invalid size: %u\n", frameSize);
      awaitingFrameBody = false;
      pendingFrameSize = 0;
      return;
    }

    pendingFrameSize = frameSize;
    awaitingFrameBody = true;
    return;
  }

  // Corpo JPEG — decodifica direto do payload (sem cópia extra)
  if (awaitingFrameBody && length == pendingFrameSize) {
    decodeAndDrawJpeg(payload, length);
    awaitingFrameBody = false;
    pendingFrameSize = 0;
    return;
  }

  // Pacote inesperado — resincroniza
  if (awaitingFrameBody) {
    Serial.printf("Out-of-sync packet: expected %u, got %u\n",
                  pendingFrameSize, static_cast<unsigned>(length));
    awaitingFrameBody = false;
    pendingFrameSize = 0;
  }
}

void updateFpsCounter() {
  const uint32_t now = millis();
  if (now - lastFpsMillis >= 1000) {
    currentFps = fpsCounter;
    fpsCounter = 0;
    lastFpsMillis = now;

    if (clientConnected && framesReceived > 0) {
      Serial.printf("FPS: %.0f  Frames: %lu\n", currentFps,
                    static_cast<unsigned long>(framesReceived));
    }
  }
}

// ---------------------------------------------------------------------------
// WebSocket
// ---------------------------------------------------------------------------

void webSocketEvent(const uint8_t& num, const WStype_t& type, uint8_t* payload, const size_t& length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.printf("[%u] Disconnected\n", num);
      clientConnected = false;
      awaitingFrameBody = false;
      pendingFrameSize = 0;
      framesReceived = 0;
      showWaitingForAppScreen();
      break;

    case WStype_CONNECTED: {
      IPAddress ip = webSocket.remoteIP(num);
      Serial.printf("[%u] Connected from %s\n", num, ip.toString().c_str());
      clientConnected = true;
      framesReceived = 0;
      fpsCounter = 0;
      lastFpsMillis = millis();
      awaitingFrameBody = false;
      pendingFrameSize = 0;
      tft.fillScreen(COL_BG);
      webSocket.sendTXT(num, "OK");
      break;
    }

    case WStype_BIN:
      processBinaryMessage(num, payload, length);
      break;

    case WStype_TEXT:
      Serial.printf("[%u] Text: %s\n", num, payload);
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
