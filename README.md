# DisplayConnect

**Português (Brasil):** [README.pt-BR.md](README.pt-BR.md)

**DisplayConnect** sends **bike navigation data** from your Android phone to a cheap **ESP32 CYD** (Cheap Yellow Display) over **Bluetooth Low Energy**. The phone computes GPS, routing, and map geometry; the CYD **draws a vector map** (route, nearby streets, your position, and turn instructions) on a dedicated 2.4″ TFT.

**Current version (`master`):** navigation via **JSON over BLE UART** — the CYD no longer receives a mirror of your phone screen, and no longer needs Wi‑Fi for the phone↔display link.

---

## v1.0 vs v2.0 — screen mirroring replaced by JSON routes

Starting with **version 2.0**, DisplayConnect **stopped replicating the phone screen**. Navigation is sent as structured data; the CYD draws the map itself.

| | **v1.0** (tag [`v1.0`](https://github.com/malaq88/DisplayConnect/releases/tag/v1.0)) | **v2.0+** (tag [`v2.0`](https://github.com/malaq88/DisplayConnect/releases/tag/v2.0), current `master`) |
|---|--|--|
| **Method** | Screen mirroring | JSON navigation |
| **Phone** | Captures the display with **MediaProjection**, encodes **JPEG** | Uses **GPS + OSRM** to compute the route; projects geometry to screen pixels |
| **Link to CYD** | Binary WebSocket (JPEG) | Early v2: text WebSocket JSON → **now BLE UART** |
| **CYD** | Decodes JPEG with **TJpg_Decoder** and shows a copy of the phone screen | Parses JSON with **ArduinoJson** and **draws** route, streets, position, and maneuver text with **TFT_eSPI** |
| **Google Maps** | Required on screen (mirrored) | Not required for core mode (OSRM + OpenStreetMap on the phone) |
| **Screen locked** | Problematic (MediaProjection) | Supported (`location` foreground service) |

**v2.0 introduced:** JSON protocol, vector map on the CYD, OSRM routing, and optional Google Maps browser mode (HTML text snippet only — not full screen mirroring).

**After v2.0** (`master` today): place search (Nominatim), route profiles (car / motorcycle / bike / walking), nearby street context (Overpass), thicker map lines, and **BLE transport** (replacing Wi‑Fi WebSocket to the CYD).

The v1.0 JPEG mirroring code remains in git tag `v1.0` for reference; it is **not** the active flow on `master`.

---

## Why use an ESP32 CYD for bike navigation?

| Problem on the phone | How DisplayConnect helps |
|----------------------|--------------------------|
| Screen hard to read in sunlight or rain | Mount the CYD on the handlebar; keep the phone protected |
| Battery drain from bright GPS + LTE | Phone can stay dim or locked; CYD only draws lightweight graphics |
| Touch interaction while riding is unsafe | Glance at the CYD; set destination before you start |
| Dedicated bike GPS units are expensive | ESP32-2432S028 boards cost a few dollars |

The **ESP32-2432S028** (CYD) combines ESP32, 240×320 ILI9341 TFT, and touch. The map area is **240×232 pixels**; the bottom strip shows maneuver text.

---

## How it works (v2.0+)

Since **v2.0**, the app does **not** capture or stream the phone screen. It sends a **navigation JSON packet** on each GPS update (1–5 Hz).

The phone is the **brain**; the CYD is the **display**. No Google Maps SDK or API key is required for core navigation (OSRM + OpenStreetMap).

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android phone                            │
│  GPS (Fused Location)                                           │
│  OSRM route (car / bike / walk)  — needs internet               │
│  Nominatim search + Overpass street geometry (OSM)              │
│  MapProjector: lat/lon → screen pixels (240×232)                │
│  NavMessage → JSON                                              │
│  BleNavClient (BLE UART / Nordic NUS)                           │
└────────────────────────────┬────────────────────────────────────┘
                             │  BLE  (newline-delimited JSON)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ESP32 CYD                                │
│  NimBLE NUS server                                              │
│  ArduinoJson parse → NavState                                   │
│  MapRenderer (TFT_eSPI): streets → route → user marker → text │
└─────────────────────────────────────────────────────────────────┘
```

### Update loop (1–5 Hz, configurable)

1. **Scan / Connect** the app to the CYD over Bluetooth (`DisplayConnect-CYD`).
2. **Search** a destination by address/place name (Nominatim) or enter coordinates.
3. Choose a **route profile**: car, motorcycle, bike, or walking.
4. Tap **Start Navigation (OSRM)** — a foreground `location` service starts.
5. On each GPS update, the app:
   - Fetches or reuses the OSRM route for the selected profile
   - Optionally loads nearby OSM streets (Overpass, cached while you ride)
   - Projects route, streets, and position to **screen coordinates**
   - Sends a JSON `nav` message over **BLE**
6. The CYD parses JSON and redraws the map (gray street context, green route, cyan position arrow, maneuver overlay).

Optional: **Open Maps in browser** mode keeps a WebView open, extracts navigation text as a small HTML snippet, and includes it in each JSON message. The map polyline still comes from GPS + OSRM on the phone.

Full protocol details: [docs/PROTOCOL_V2.md](docs/PROTOCOL_V2.md)

---

## Technologies

### Android app (Kotlin)

- **Jetpack Compose + Material 3** -> UI (BLE scan, route profiles, search)
- **MVVM + StateFlow** -> `MainViewModel`, reactive UI state
- **DataStore** -> BLE device address/name, route profile, saved destination
- **Navigation Compose** -> Main screen and Settings
- **Play Services Location** -> Real-time GPS
- **Foreground Service** (`location`) -> Navigation while the screen is locked
- **BleNavClient** (Android BLE / NUS) -> Sends JSON to the CYD
- **Coroutines** -> Async GPS, routing, streets, and transmission
- **OSRM** (`router.project-osrm.org`) -> Car, bike, and walking routes
- **Nominatim** (OpenStreetMap) -> Address and place search
- **Overpass API** (OpenStreetMap) -> Nearby street geometry
- **org.json** (`JSONObject`) -> Builds the `NavMessage` protocol
- **OkHttp** -> HTTP for OSRM / OSM APIs (internet only)
- **WebView** (Maps browser mode) -> Extracts Google Maps navigation text (optional)

**Key modules**

- `navigation/` -> `LocationTracker`, `NavigationEngine`, `NavigationForegroundService`
- `routing/` -> `OsrmRouteProvider`, `NominatimGeocoder`, `OverpassStreetProvider`, `RouteProfile`
- `map/` -> `MapProjector`, `StreetContextProjector` (lat/lon -> pixels)
- `protocol/` -> `NavMessage` JSON builder
- `network/` -> `BleNavClient`
- `maps/` -> Maps browser WebView + HTML extractor (optional)

**Requirements:** Android 7.0+ (API 24), target API 36. BLE required.

### ESP32 CYD firmware (Arduino)

- **ESP32-2432S028** (CYD) -> BLE + 240×320 TFT
- **TFT_eSPI** -> ILI9341 driver; draws lines, circles, and text
- **NimBLE-Arduino** (h2zero) **v2.x** -> BLE GATT UART (NUS) server
- **ArduinoJson** v7+ -> JSON parsing (on the main `loop()` task only)

**Firmware modules:** `nav_protocol.cpp`, `map_renderer.cpp`, `html_renderer.cpp`, `loading_screen.cpp`.

The CYD does **not** need Wi‑Fi for navigation display. It only draws vectors and text from BLE JSON.

**Important (CYD stability):** NimBLE callbacks must **not** call TFT or ArduinoJson. They only queue RX bytes / UI flags; `loop()` draws and parses. Drawing from a BLE callback can reboot the board (stack overflow).

---

## Wire protocol (summary)

- **Transport:** BLE UART (Nordic NUS), newline-delimited JSON
- **Device name:** `DisplayConnect-CYD`
- **Service / RX / TX UUIDs:** Nordic NUS (`6E400001` / `6E400002` / `6E400003` …)
- **MTU:** negotiated (firmware requests 256)
- **Heartbeat:** `{"type":"heartbeat"}\n` every 15 s
- **Navigation:** `{"type":"nav", ...}\n` — see [docs/PROTOCOL_V2.md](docs/PROTOCOL_V2.md)

Important: `route`, `streets`, `user_x`, and `user_y` are already in **screen pixels** (240×232 map area). The ESP32 does not perform geographic projection.

---

## Project structure

```
DisplayConnect/
├── app/                              # Android application
│   └── src/main/java/com/example/displayconnect/
│       ├── navigation/                 # GPS, NavigationEngine, foreground service
│       ├── routing/                    # OSRM, Nominatim, Overpass, RouteProfile
│       ├── map/                        # MapProjector, StreetContextProjector
│       ├── protocol/                   # NavMessage JSON
│       ├── network/                    # BleNavClient (BLE UART)
│       ├── maps/                       # Optional Maps browser WebView
│       ├── storage/                    # SettingsRepository (DataStore)
│       ├── ui/                         # Compose screens and components
│       ├── viewmodel/                  # MainViewModel, SettingsViewModel
│       ├── models/                     # AppSettings, ConnectionState, stats
│       └── utils/                      # TransmissionHub, StatsTracker
├── DisplaySender/                    # ESP32 firmware
│   ├── DisplaySender.ino
│   ├── map_renderer.cpp / .h
│   ├── nav_protocol.cpp / .h
│   ├── html_renderer.cpp / .h
│   └── config.h.example
├── docs/
│   ├── PROTOCOL_V2.md                  # JSON protocol reference
│   └── NAV_V2_TASKS.md                 # Development task list
├── README.md
└── README.pt-BR.md
```

Legacy JPEG capture code may still exist under `app/.../capture/` from v1 but is **not used** by the current app flow.

---

## Hardware

- **Board:** ESP32-2432S028 (Cheap Yellow Display), 240×320 TFT
- **Phone:** Android 7+ with BLE, GPS, and internet (for OSRM / OSM APIs)
- **Link:** Bluetooth Low Energy between phone and CYD (no shared Wi‑Fi required for the display)

**TFT_eSPI setup:** Configure `Arduino/libraries/TFT_eSPI/Setup_User.h` for your CYD (ILI9341_2, `TFT_RGB`, `USE_HSPI_PORT`).

---

## Getting started

### 1. Flash the ESP32

1. Install Arduino libraries: **TFT_eSPI**, **NimBLE-Arduino** (v2.x), **ArduinoJson** v7+.
2. Copy `DisplaySender/config.h.example` to `DisplaySender/config.h`.
3. Upload `DisplaySender.ino`.
4. On boot, the CYD shows **Waiting for app** / **Bluetooth LE** / `DisplayConnect-CYD`.

### 2. Build and install the Android app

1. Open the project in **Android Studio**.
2. Build and install (`minSdk 24`).
3. Grant **Bluetooth**, **location**, and **notifications** when prompted.

### 3. Ride workflow

1. Power the CYD (it advertises `DisplayConnect-CYD`).
2. In DisplayConnect: **Scan devices** → select the CYD → **Connect** (loading screen on the CYD).
3. Select **route type** (car / motorcycle / bike / walking).
4. **Search** a destination or enter coordinates.
5. Tap **Start Navigation (OSRM)** and mount the CYD on your handlebar.
6. Adjust **Settings** if needed (update rate, map radius).

If the CYD reboots when connecting, make sure you have the current firmware: TFT updates must run in `loop()`, not in BLE callbacks.

---

## Settings reference

| Setting | Default | Description |
|---------|---------|-------------|
| BLE device | — | Saved CYD address / name after scan |
| Route type | Car | OSRM profile: driving / cycling / walking |
| Update rate | 2 Hz | Navigation JSON updates per second |
| Map radius | 400 m | Visible half-width around your position |
| Destination | — | Saved query, label, and coordinates |

---

## Known limitations

- **No native second screen:** The CYD is not an Android external display; it only renders what the app sends in JSON.
- **BLE range:** Keep phone and CYD within typical BLE distance (~10 m open air; less with pockets/bags).
- **OSRM public server:** Shared free tier; motorcycle uses the `driving` profile (no dedicated moto profile on public OSRM).
- **OSM rate limits:** Nominatim and Overpass should be used sparingly (search on user action; street cache refreshes on movement).
- **Maps browser mode:** Requires the WebView activity to stay open; ESP renders stripped text, not full HTML/CSS.
- **Single BLE client:** One GATT connection at a time.

---

## Version history

| Tag | Description |
|-----|-------------|
| **v1.0** | **Screen mirroring** — MediaProjection + JPEG over binary WebSocket |
| **v2.0** | **JSON navigation** — OSRM vector map; initially over Wi‑Fi WebSocket. See [docs/PROTOCOL_V2.md](docs/PROTOCOL_V2.md) |
| **master** (current) | Place search, route profiles, street context, thicker lines, **BLE UART** to the CYD (NimBLE v2; stable connect / deferred TFT) |

---

## Development

- **License:** MIT — see [LICENSE](LICENSE).
- **Default package:** `com.example.displayconnect`
- **Internet cleartext:** Enabled for HTTP APIs (OSRM/OSM); CYD link is BLE, not `ws://`.

---

## Summary

DisplayConnect turns an inexpensive **ESP32 CYD** into a **dedicated bike navigation display**. Since **v2.0**, the Android app no longer mirrors the screen: it uses **GPS + OSRM + OpenStreetMap**, sends **lightweight JSON** over **BLE**, and the CYD **draws the route and map** with TFT_eSPI. The phone can stay locked; the handlebar display shows turn-by-turn navigation without JPEG streaming or a Wi‑Fi hotspot between phone and CYD.
