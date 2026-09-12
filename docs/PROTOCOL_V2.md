# DisplayConnect v2 — Protocol (JSON navigation over BLE)

The phone computes navigation and map geometry; the CYD only **draws** what it receives. All geographic work happens on Android.

---

## Architecture

```
Android                              ESP32 CYD
────────                             ─────────
GPS (LocationTracker)
  ↓
OSRM route (OsrmRouteProvider)
Overpass streets (OverpassStreetProvider)  [cached, refreshed on movement]
  ↓
MapProjector / StreetContextProjector
  lat/lon → screen pixels (240×232)
  ↓
NavMessage.toJson()
  ↓
BleNavClient.sendNavMessage()  ──BLE UART (NUS)──►  line buffer
                                                     parseNavJson()
                                                     MapRenderer.draw()
```

**Design principle:** `route`, `streets`, `user_x`, and `user_y` are **screen coordinates**, not WGS84. The ESP32 does not need a map projection library.

---

## Transport: BLE UART (Nordic NUS)

| Property | Value |
|----------|-------|
| Link | Bluetooth Low Energy GATT |
| Device name | `DisplayConnect-CYD` |
| Service UUID | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| RX (phone → CYD) | `6E400002-...` — Write / Write Without Response |
| TX (CYD → phone) | `6E400003-...` — Notify |
| Framing | Each JSON message ends with `\n` |
| Chunking | Large payloads split to MTU−3 bytes; ESP reassembles until `\n` |
| Requested MTU | 256 (firmware) |
| Heartbeat | `{"type":"heartbeat"}\n` every 15 s |
| On connect | CYD shows loading UI (from `loop()`) and notifies `OK\n` |

The phone still uses **Wi‑Fi / mobile data** for OSRM, Nominatim, and Overpass. Only the **phone ↔ CYD** link is BLE.

Wi‑Fi WebSocket from early v2 is **no longer used** for CYD updates.

### ESP32 receive path (stable)

NimBLE callbacks run on a small BLE task stack. Touching the TFT or allocating large JSON documents there can **reboot the CYD**.

Current firmware:

1. `onWrite` → push bytes into an RX ring buffer (no TFT / no JSON)
2. `onConnect` / `onDisconnect` → set flags only
3. `loop()` → drain RX queue, reassemble lines, `parseNavJson` / `MapRenderer`, update loading/waiting screens, restart advertising

Do not move drawing or ArduinoJson back into the BLE callbacks.

---

## Message types

### Heartbeat

```json
{"type":"heartbeat"}
```

No drawing update. Keeps the connection alive. Sent as a line over BLE RX.

### Loading

```json
{"type":"loading"}
```

Shows a loading screen on the CYD (spinner + “Getting directions…”) until the next `nav` message. Sent when the app starts navigation while OSRM/GPS data is still being prepared. Also shown automatically when a BLE client connects. Colors follow the local light/dark theme (not sent in JSON).

### Navigation (`nav`)

```json
{
  "type": "nav",
  "lat": -23.5505,
  "lon": -46.6333,
  "bearing": 127.5,
  "instruction": "Turn right",
  "distance_m": 200,
  "remaining_m": 5400,
  "remaining_s": 980,
  "off_route": false,
  "lang": "pt-BR",
  "gps_weak": false,
  "street": "Av. Paulista",
  "route": [[120, 80], [125, 90], [130, 100]],
  "streets": [[10, 20, 50, 40], [100, 30, 140, 60]],
  "user_x": 120,
  "user_y": 150,
  "html": "<div>optional snippet</div>"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Must be `"nav"` |
| `lat`, `lon` | number | Current WGS84 position (for logging / future use) |
| `bearing` | number | Heading in degrees (0 = north, clockwise) |
| `instruction` | string | Maneuver text (e.g. "Turn right") |
| `distance_m` | int | Distance to next maneuver in meters |
| `street` | string | Street name for current step (optional) |
| `route` | array | Polyline `[[x,y], ...]` in **map pixels** (max 64 points on ESP) |
| `streets` | array | Optional road segments `[[x0,y0,x1,y1], ...]` (max **128** on CYD) |
| `user_x`, `user_y` | int | Current position on map in pixels |
| `html` | string | Optional HTML fragment (max ~480 chars); ESP strips tags and draws plain text |
| `remaining_m` | int | Remaining route distance in meters; `-1` if unknown |
| `remaining_s` | int | Remaining duration in seconds; `-1` if unknown |
| `off_route` | bool | Position is off the route |
| `lang` | string | `pt-BR` or `en` |
| `gps_weak` | bool | GPS is stale/imprecise; firmware may keep the last good puck |

`[-1,-1]` in `route` marks a gap: the renderer must **not** connect the previous point to the next (off-screen excursion).

### Config

```json
{"type":"config","lang":"pt-BR"}
```

Sent when BLE connects or the user changes language. The CYD persists the preference.

### Map pixel space

- Map area: **240 × 232** pixels (top of the 240×320 display)
- Origin: top-left `(0, 0)`
- Center of map = current GPS position
- Scale: `mapScaleMeters` is the **vertical** half-range; both axes use the same meters/pixel (roads are not stretched)
- Bottom **88 px** overlay: maneuver, remaining time/distance, GPS-weak hint + on-device **light/dark theme switch**

Theme preference is stored on the CYD in NVS and does not affect the JSON schema.

---

## Android pipeline (per update)

1. `LocationTracker` emits GPS fixes; `GpsFilter` rejects stale, imprecise, and implausible jumps.
2. `NavigationEngine` loads OSRM route (FOSSGIS car/bike/foot) and remaining distance/time.
3. `MapProjector` + `ScreenGeometry` project with equal axes and clip segments (gaps as `[-1,-1]`).
4. `OfflineMapRepository` supplies Overpass streets along the whole corridor (persisted).
5. `StreetContextProjector` distributes up to **128** visible segments across the screen.
6. `NavMessage` is built and serialized with `org.json`.
7. `BleNavClient` keeps only the latest frame, then writes with GATT ACKs (chunked to MTU).

Typical payload size: **under 2 KB**.

---

## ESP32 render order

`MapRenderer::draw()`:

1. Clear map area (theme land color; soft park blobs in light mode)
2. Draw `streets` — road casing then fill
3. Draw `route` polyline — blue with light edge (two passes)
4. Draw user puck + bearing arrow
5. Draw bottom sheet overlay: distance, instruction, street (or HTML text) + theme switch

---

## Route profiles (Android → OSRM)

| UI label | Graph | Notes |
|----------|-------|-------|
| Car | `https://routing.openstreetmap.de/routed-car/route/v1/driving/` | Default |
| Motorcycle | same car graph | No dedicated motorcycle graph |
| Bike | `.../routed-bike/route/v1/cycling/` | Separate FOSSGIS bike graph |
| Walking | `.../routed-foot/route/v1/walking/` | Separate FOSSGIS foot graph |

Changing only `/v1/{profile}` on `router.project-osrm.org` does **not** switch the routing graph. v3.0 uses the FOSSGIS hosts above.

---

## External APIs (Android only)

| API | Purpose | API key |
|-----|---------|---------|
| OSRM FOSSGIS (`routing.openstreetmap.de`) | Turn-by-turn route + geometry | No |
| Nominatim (`nominatim.openstreetmap.org`) | Address / place search | No (respect usage policy) |
| Photon (`photon.komoot.io`) | Complementary search | No |
| Overpass (`overpass-api.de`) | Road geometry along the route | No (rate limit) |

The ESP32 does not call these APIs and does not need Wi‑Fi for navigation display.

---

## Maps browser mode

Optional flow:

1. App opens Google Maps in a **WebView** (`MapsBrowserActivity`).
2. JavaScript extracts visible navigation text every ~2 s.
3. Snippet stored in `MapsHtmlHolder` and attached to each `nav` message as `html`.
4. Map polyline still comes from GPS + OSRM on the phone.

The ESP32 does **not** render HTML/CSS — `html_renderer` strips simple tags and draws text lines.

---

## Libraries

### ESP32

- **TFT_eSPI** — display driver and drawing primitives
- **NimBLE-Arduino** v2.x — BLE GATT UART server (callbacks with `NimBLEConnInfo`)
- **ArduinoJson** v7+ — JSON parse (only from `loop()`)
- **XPT2046_Touchscreen** — resistive touch for the on-device theme switch
- **Preferences** (ESP32 Arduino core) — persist light/dark theme in NVS

Firmware UI modules: `map_renderer`, `maps_theme`, `touch_cyd`, `loading_screen`, `html_renderer`, `utf8_text`.

### Android

- **Android BLE APIs** (`BluetoothLeScanner`, `BluetoothGatt`) — NUS client
- **Play Services Location** — GPS
- **org.json** — `NavMessage` serialization
- **OkHttp** — HTTP for OSRM (FOSSGIS), Nominatim, Photon, Overpass (internet, not CYD link)

---

## Legacy transports

| Tag / era | Transport |
|-----------|-----------|
| v1.0 | Binary WebSocket + JPEG |
| Early v2.0 | Text WebSocket JSON (`ws://IP:81`) |
| Current | **BLE UART** + newline-delimited JSON (**v3.0**: remaining time, `gps_weak`, `lang`, 128 street segments) |

v3.0 protocol extensions were adapted from [PLZ-1/DisplayConnectV2.1](https://github.com/PLZ-1/DisplayConnectV2.1) for the CYD 240×232 map (not the 480×232 ST7796S layout).
