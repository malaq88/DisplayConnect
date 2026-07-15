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

Shows a loading screen on the CYD (spinner + “Loading map…”) until the next `nav` message. Sent when the app starts navigation while OSRM/GPS data is still being prepared. Also shown automatically when a BLE client connects.

### Navigation (`nav`)

```json
{
  "type": "nav",
  "lat": -23.5505,
  "lon": -46.6333,
  "bearing": 127.5,
  "instruction": "Turn right",
  "distance_m": 200,
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
| `streets` | array | Optional road segments `[[x0,y0,x1,y1], ...]` for map context (max 56 on ESP) |
| `user_x`, `user_y` | int | Current position on map in pixels |
| `html` | string | Optional HTML fragment (max ~480 chars); ESP strips tags and draws plain text |

### Map pixel space

- Map area: **240 × 232** pixels (top of the 240×320 display)
- Origin: top-left `(0, 0)`
- Center of map = current GPS position
- Scale controlled by Android setting `mapScaleMeters` (half-width of visible area in meters)

---

## Android pipeline (per update)

1. `LocationTracker` emits GPS fix at 1–5 Hz.
2. `NavigationEngine` loads OSRM route for destination and selected `RouteProfile`.
3. `MapProjector.projectRoute()` converts route lat/lon → pixels, centered on user.
4. `OverpassStreetProvider` fetches OSM ways when user moves ~35% of map width (cached).
5. `StreetContextProjector.projectSegments()` converts street ways → line segments in pixels.
6. `NavMessage` is built and serialized with `org.json`.
7. `BleNavClient` writes the string + `\n` over BLE UART RX (chunked to MTU).

Typical payload size: **under 2 KB**.

---

## ESP32 render order

`MapRenderer::draw()`:

1. Clear map area (black)
2. Draw `streets` segments — dark gray, 2 px thick
3. Draw `route` polyline — green, 3 px thick
4. Draw user marker + bearing arrow — cyan
5. Draw overlay strip (bottom 88 px): distance, instruction, street name (or HTML text)

---

## Route profiles (Android → OSRM)

| UI label | OSRM profile | Notes |
|----------|--------------|-------|
| Car | `driving` | Default |
| Motorcycle | `driving` | Public OSRM has no dedicated motorcycle profile |
| Bike | `cycling` | Bike paths where OSM/OSRM data exists |
| Walking | `walking` | Pedestrian paths |

URL pattern: `https://router.project-osrm.org/route/v1/{profile}/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson&steps=true`

---

## External APIs (Android only)

| API | Purpose | API key |
|-----|---------|---------|
| OSRM (`router.project-osrm.org`) | Turn-by-turn route + geometry | No |
| Nominatim (`nominatim.openstreetmap.org`) | Address / place search | No (respect usage policy) |
| Overpass (`overpass-api.de`) | Nearby road geometry | No (rate limit) |

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

### Android

- **Android BLE APIs** (`BluetoothLeScanner`, `BluetoothGatt`) — NUS client
- **Play Services Location** — GPS
- **org.json** — `NavMessage` serialization
- **OkHttp** — HTTP for OSRM, Nominatim, Overpass (internet, not CYD link)

---

## Legacy transports

| Tag / era | Transport |
|-----------|-----------|
| v1.0 | Binary WebSocket + JPEG |
| Early v2.0 | Text WebSocket JSON (`ws://IP:81`) |
| Current | **BLE UART** + newline-delimited JSON |
