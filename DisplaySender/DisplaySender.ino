/**
 * DisplayConnect v2 — ESP32-2432S028 (CYD)
 *
 * BLE UART (Nordic NUS) server receives JSON navigation updates from the
 * Android app and renders a vector map (route + position + maneuver overlay).
 *
 * IMPORTANT: Never call TFT / ArduinoJson from NimBLE callbacks — they run on
 * a small BLE task stack and will reboot the CYD. Callbacks only queue bytes /
 * flags; loop() does drawing and parsing.
 *
 * Libraries:
 *   - TFT_eSPI (Bodmer)
 *   - NimBLE-Arduino (h2zero) v2.x
 *   - ArduinoJson (Benoit Blanchon) v7+
 */

#include "config.h"

#include <NimBLEDevice.h>
#include <SPI.h>
#include <TFT_eSPI.h>

#include "nav_types.h"
#include "map_renderer.h"
#include "nav_protocol.h"
#include "loading_screen.h"

TFT_eSPI tft;
MapRenderer mapRenderer(tft);

static volatile bool clientConnected = false;
static volatile bool awaitingFirstNav = false;
static volatile bool uiShowLoading = false;
static volatile bool uiShowWaiting = false;
static volatile bool sendOkPending = false;

static uint32_t navUpdatesReceived = 0;
static uint32_t lastStatsMillis = 0;
static uint16_t updatesPerSecond = 0;

static const size_t LINE_BUF_SIZE = 3072;
static char lineBuf[LINE_BUF_SIZE];
static size_t lineLen = 0;

// Byte queue filled in BLE write callback, drained in loop()
static const size_t RX_QUEUE_SIZE = 4096;
static uint8_t rxQueue[RX_QUEUE_SIZE];
static volatile size_t rxHead = 0;
static volatile size_t rxTail = 0;

static NimBLEServer* bleServer = nullptr;
static NimBLECharacteristic* txCharacteristic = nullptr;

#define COL_BTN      tft.color565(30, 30, 30)

static inline uint16_t colInfoCyan() { return tft.color565(88, 190, 245); }
static inline uint16_t colDim() { return tft.color565(120, 120, 120); }

bool initDisplay();
bool initBle();
void showStatusScreen(const char* title, const char* line2 = nullptr, const char* line3 = nullptr);
void showWaitingForAppScreen();
void processTextMessage(const char* payload, size_t length);
void drainRxQueue();
void appendRxByte(char c);
void updateStatsCounter();
void handleUiFlags();

static bool rxQueuePush(uint8_t b) {
  const size_t next = (rxHead + 1) % RX_QUEUE_SIZE;
  if (next == rxTail) {
    return false; // full
  }
  rxQueue[rxHead] = b;
  rxHead = next;
  return true;
}

static bool rxQueuePop(uint8_t& b) {
  if (rxTail == rxHead) {
    return false;
  }
  b = rxQueue[rxTail];
  rxTail = (rxTail + 1) % RX_QUEUE_SIZE;
  return true;
}

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* /*server*/, NimBLEConnInfo& /*connInfo*/) override {
    clientConnected = true;
    awaitingFirstNav = true;
    navUpdatesReceived = 0;
    updatesPerSecond = 0;
    lastStatsMillis = millis();
    lineLen = 0;
    uiShowLoading = true;
    sendOkPending = true;
    // Do NOT draw TFT or notify heavily here — defer to loop()
  }

  void onDisconnect(NimBLEServer* /*server*/, NimBLEConnInfo& /*connInfo*/, int /*reason*/) override {
    clientConnected = false;
    awaitingFirstNav = false;
    navUpdatesReceived = 0;
    lineLen = 0;
    rxHead = 0;
    rxTail = 0;
    uiShowWaiting = true;
    // Advertising restart deferred to loop() — safer than here
  }
};

class RxCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo& /*connInfo*/) override {
    const NimBLEAttValue& value = characteristic->getValue();
    const size_t len = value.length();
    const uint8_t* data = value.data();
    for (size_t i = 0; i < len; i++) {
      if (!rxQueuePush(data[i])) {
        break; // drop overflow rather than crash
      }
    }
  }
};

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println(F("=== DisplayConnect CYD v2 (BLE) ==="));

  if (!initDisplay()) {
    Serial.println(F("Failed to initialize display."));
    while (true) { delay(1000); }
  }

  showStatusScreen("DisplayConnect", "Starting BLE...");

  if (!initBle()) {
    showStatusScreen("BLE Error", "Restart the board.");
    while (true) { delay(1000); }
  }

  showWaitingForAppScreen();
}

void loop() {
  handleUiFlags();
  drainRxQueue();

  if (clientConnected && awaitingFirstNav) {
    updateMapLoadingAnimation(tft, millis());
  }

  updateStatsCounter();
  delay(5);
}

void handleUiFlags() {
  if (uiShowLoading) {
    uiShowLoading = false;
    showMapLoadingScreen(tft);
  }

  if (uiShowWaiting) {
    uiShowWaiting = false;
    showWaitingForAppScreen();
    if (bleServer != nullptr) {
      bleServer->startAdvertising();
    }
  }

  if (sendOkPending && clientConnected && txCharacteristic != nullptr) {
    sendOkPending = false;
    txCharacteristic->setValue("OK\n");
    txCharacteristic->notify();
  }
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

bool initBle() {
  // Keep NimBLE lean — CYD has limited RAM with TFT_eSPI
  NimBLEDevice::init(BLE_DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P3);
  NimBLEDevice::setMTU(BLE_MTU);

  bleServer = NimBLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());
  bleServer->advertiseOnDisconnect(false); // we restart advertising from loop()

  NimBLEService* service = bleServer->createService(NUS_SERVICE_UUID);
  NimBLECharacteristic* rx = service->createCharacteristic(
      NUS_RX_UUID,
      NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR
  );
  rx->setCallbacks(new RxCallbacks());

  txCharacteristic = service->createCharacteristic(
      NUS_TX_UUID,
      NIMBLE_PROPERTY::NOTIFY | NIMBLE_PROPERTY::READ
  );

  service->start();

  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(NUS_SERVICE_UUID);
  advertising->start();

  Serial.printf("BLE advertising as \"%s\"\n", BLE_DEVICE_NAME);
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
  tft.drawString(F("DisplayConnect v2 BLE"), SCR_W / 2, SCR_H - 20, 2);
}

void showWaitingForAppScreen() {
  showStatusScreen("Waiting for app", "Bluetooth LE", BLE_DEVICE_NAME);
}

void appendRxByte(char c) {
  if (c == '\n' || c == '\r') {
    if (lineLen > 0) {
      lineBuf[lineLen] = '\0';
      processTextMessage(lineBuf, lineLen);
      lineLen = 0;
    }
    return;
  }
  if (lineLen + 1 >= LINE_BUF_SIZE) {
    Serial.println(F("BLE line buffer overflow — reset"));
    lineLen = 0;
    return;
  }
  lineBuf[lineLen++] = c;
}

void drainRxQueue() {
  uint8_t b;
  // Process a bounded number per loop to keep UI responsive
  for (int i = 0; i < 256; i++) {
    if (!rxQueuePop(b)) {
      break;
    }
    appendRxByte(static_cast<char>(b));
  }
}

void processTextMessage(const char* payload, size_t length) {
  if (isLoadingJson(payload, length)) {
    awaitingFirstNav = true;
    showMapLoadingScreen(tft);
    return;
  }

  NavState state;
  if (!parseNavJson(payload, length, state)) {
    return;
  }

  mapRenderer.draw(state);
  awaitingFirstNav = false;
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
