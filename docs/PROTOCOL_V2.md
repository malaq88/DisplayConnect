# DisplayConnect v2 — Protocol (JSON navigation)

## WebSocket

- **Transport:** text frames (UTF-8 JSON)
- **Port:** 81 (default)
- **Heartbeat:** `{"type":"heartbeat"}` every 15 s

## Navigation message

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
  "user_y": 150
}
```

| Field | Description |
|-------|-------------|
| `route` | Polyline in **screen pixels** (240×232 map area) |
| `streets` | Optional nearby road segments `[[x0,y0,x1,y1],...]` drawn as map context (gray) |
| `user_x`, `user_y` | Current position on map |
| `bearing` | Heading in degrees (0 = north, clockwise) |
| `html` | Optional HTML fragment from Maps WebView (max ~480 chars); ESP strips tags and draws text lines |

## Maps browser mode

The app opens Google Maps in a **WebView**, extracts a small HTML snippet from visible navigation text via JavaScript, and includes it in each `nav` message. The map polyline still comes from GPS + OSRM on the phone.

**Note:** The ESP32 does not render full HTML/CSS — only plain text from simple tags (`<div>`, etc.).

## ESP32 libraries (v2)

- TFT_eSPI
- WebSockets_Generic
- **ArduinoJson** v7+

## Android

- GPS + **OSRM** routing (no Google Maps SDK)
- Foreground service type: `location`
