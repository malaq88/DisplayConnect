# DisplayConnect

**Português (Brasil):** [README.pt-BR.md](README.pt-BR.md)

**DisplayConnect** mirrors your phone’s GPS navigation screen onto a cheap, bike-mounted **ESP32 CYD** (Cheap Yellow Display) over Wi-Fi. While you ride, turn-by-turn directions from Google Maps stay visible on a dedicated 2.4″ TFT without taking your phone out of a pocket or bag.

The phone captures the screen, compresses it to JPEG, and streams frames to the ESP32. The CYD decodes and draws each frame in real time—giving you a lightweight, weather-resistant navigation display for bicycle tours.

---

## Why use an ESP32 CYD for bike navigation?

Cycling with a phone as the only display has practical drawbacks:

| Problem on the phone | How DisplayConnect helps |
|----------------------|--------------------------|
| Screen hard to read in sunlight or rain | Mount the CYD on the handlebar; keep the phone protected |
| Battery drain from bright GPS + LTE | Phone can stay dim or in a pocket; CYD draws only the mirrored map |
| Touch interaction while riding is unsafe | Glance at the CYD; control navigation on the phone before you start |
| Dedicated bike GPS units are expensive | ESP32-2432S028 boards cost a few dollars and reuse Google Maps routing |

The **ESP32-2432S028** (often called **CYD** or **Cheap Yellow Display**) combines an ESP32, a 240×320 ILI9341 TFT, and touch in one board. DisplayConnect targets that resolution natively: frames are scaled with **letterboxing** so map proportions stay correct on the portrait display.

---

## How it works

```
┌─────────────────────┐         Wi-Fi (WebSocket)         ┌──────────────────────┐
│   Android phone     │  ─── JPEG frames (240×320) ───►  │  ESP32 CYD           │
│                     │                                   │  240×320 TFT         │
│  Google Maps        │  ◄── heartbeat / reconnect ────  │  WebSocket server    │
│  MediaProjection    │                                   │  TJpg_Decoder        │
│  JPEG encode        │                                   │  TFT_eSPI            │
└─────────────────────┘                                   └──────────────────────┘
```

1. You connect the app to the ESP32 (default `192.168.4.1:81` in AP mode, or your LAN IP in STA mode).
2. You open **Google Maps** and start navigation on the phone.
3. You tap **Start Transmission**; the app requests **MediaProjection** permission and runs a foreground service.
4. Each frame is cropped (optional), scaled to 240×320 with letterbox, compressed to JPEG, and sent over WebSocket.
5. The ESP32 receives the frame, decodes JPEG, and draws it on the TFT.

---

## Technologies

### Android app

| Technology | Role |
|------------|------|
| **Kotlin** | Primary language |
| **Jetpack Compose** | Modern declarative UI (Material 3) |
| **MVVM** | `MainViewModel`, `SettingsViewModel` separate UI from logic |
| **Navigation Compose** | Main screen ↔ Settings |
| **DataStore Preferences** | Persistent settings (IP, FPS, quality, crop, etc.) |
| **MediaProjection API** | Captures the full screen (including Google Maps) |
| **VirtualDisplay + ImageReader** | Efficient frame pipeline from the projection |
| **Foreground Service** (`mediaProjection` type) | Keeps capture alive while navigating |
| **OkHttp WebSocket** | Binary frame transport, ping, auto-reconnect |
| **Coroutines + StateFlow** | Async networking and reactive UI state |

**Image pipeline (app side)**

- **ImageProcessor** — crop margins, letterbox scale, rotation, JPEG compression (with optional battery-saver quality reduction).
- **FrameDiffer** — optional “transmit only on changes” to save bandwidth and CPU.
- **StatsTracker** — FPS, bitrate, frames sent/skipped.
- **DisplaySocketClient** — 4-byte big-endian size header + JPEG payload; `0,0,0,0` heartbeat every 15 s.

**Requirements:** Android 7.0+ (API 24), target API 36.

### ESP32 firmware (`DisplaySender/`)

| Technology | Role |
|------------|------|
| **ESP32** (ESP32-2432S028) | Wi-Fi + JPEG decode + display |
| **Arduino framework** | Sketch `DisplaySender.ino` |
| **TFT_eSPI** (Bodmer) | ILI9341 driver, CYD pin setup via `Setup_User.h` |
| **TJpg_Decoder** (Bodmer) | Hardware-friendly JPEG decode to TFT |
| **WebSockets_Generic** (Khoi Hoang) | WebSocket server on port 81 |
| **Wi-Fi STA / AP** | Join phone hotspot or create `DisplayConnect-CYD` AP |

**Display:** 240×320 portrait, ILI9341_2, gamma tuned for CYD boards (CYDAlbumPlayer palette for status screens).

---

## Wire protocol

Binary WebSocket messages from app → ESP32:

| Message | Content |
|---------|---------|
| **Header** | 4 bytes, big-endian `uint32` = JPEG size in bytes |
| **Body** | `N` bytes of JPEG data |
| **Heartbeat** | Header only: `0x00 0x00 0x00 0x00` (ignored) |

The client may send header and body as **two separate WebSocket binary frames** or as a **single combined frame** (header + JPEG). The firmware handles both.

On connect, the ESP32 may reply with the text `OK`.

---

## Project structure

```
DisplayConnect/
├── app/                          # Android application
│   └── src/main/java/com/example/displayconnect/
│       ├── capture/                # MediaProjection, ImageProcessor, FrameDiffer, service
│       ├── network/                # DisplaySocketClient (WebSocket)
│       ├── storage/                # SettingsRepository (DataStore)
│       ├── ui/                     # Compose screens and components
│       ├── viewmodel/              # MainViewModel, SettingsViewModel
│       ├── models/                 # AppSettings, ConnectionState, stats
│       └── utils/                  # TransmissionHub, StatsTracker, IntentUtils
├── DisplaySender/
│   ├── DisplaySender.ino           # ESP32 firmware
│   ├── config.h.example            # Wi-Fi template (copy to config.h)
│   └── config.h                    # Local credentials (gitignored)
├── README.md                       # English documentation
└── README.pt-BR.md                 # Portuguese (Brazil) documentation
```

---

## Hardware

- **Board:** ESP32-2432S028 (Cheap Yellow Display), 240×320 TFT
- **Phone:** Android device with Google Maps (or any app you want mirrored)
- **Network:** Phone and ESP32 on the same Wi-Fi, **or** ESP32 AP mode (`192.168.4.1`) with phone connected to `DisplayConnect-CYD`

**TFT_eSPI setup:** Configure `Arduino/libraries/TFT_eSPI/Setup_User.h` for your CYD (ILI9341_2, `TFT_RGB`, `USE_HSPI_PORT`), and ensure `User_Setup_Select.h` includes it.

---

## Getting started

### 1. Flash the ESP32

1. Install Arduino libraries: **TFT_eSPI**, **TJpg_Decoder**, **WebSockets_Generic**.
2. Copy `DisplaySender/config.h.example` to `DisplaySender/config.h`.
3. Optionally set `WIFI_SSID` / `WIFI_PASSWORD` to join your phone’s hotspot. Leave empty to use AP mode (`192.168.4.1`).
4. Upload `DisplaySender.ino` to the board.
5. On boot, the CYD shows **Waiting for app** with its IP and port.

### 2. Build and install the Android app

1. Open the project in **Android Studio**.
2. Build and install on your phone (`minSdk 24`).
3. Grant **notifications** (Android 13+) and **screen capture** when prompted.

### 3. Ride workflow

1. Power the CYD and note the IP (e.g. `192.168.4.1`, port `81`).
2. In DisplayConnect, enter IP/port → **Connect**.
3. **Open Google Maps**, set your route, start navigation.
4. **Start Transmission** and mount the CYD on your handlebar.
5. Adjust **Settings** if needed (FPS, JPEG quality, crop, change-only mode).

---

## Settings reference

| Setting | Default | Description |
|---------|---------|-------------|
| ESP IP / Port | `192.168.4.1` / `81` | WebSocket endpoint |
| FPS | 15 | Target capture rate |
| JPEG quality | 70 | Compression (lower = smaller frames) |
| Resolution | 240×320 | Output size (match CYD) |
| Rotation | 0° | Rotate encoded frame |
| Battery saver | Off | Reduces JPEG quality while transmitting |
| Transmit only on changes | On | Skips similar frames (FrameDiffer) |
| Crop margins | Top 4%, bottom 6% | Trims status bars / nav chrome |
| Change sensitivity | 2% | Threshold for frame differ |

---

## Known limitations

- **Google Maps and screen capture:** Maps uses `FLAG_SECURE` on some screens, which can produce **black frames** or unstable capture when Maps is in the foreground. This is an Android/Google restriction, not a Wi-Fi or ESP32 issue. A long-term alternative is embedding the **Google Maps SDK** inside DisplayConnect instead of mirroring the Maps app.
- **Latency:** Wi-Fi JPEG streaming adds a small delay; fine for turn-by-turn glances, not for fast gaming.
- **Single client:** The firmware serves one WebSocket client at a time.
- **Cleartext WebSocket:** Local `ws://` only; suitable for a private bike LAN, not the public internet.

---

## Development

- **License:** MIT — see [LICENSE](LICENSE).
- **Default package:** `com.example.displayconnect` (change `applicationId` before publishing).
- **Cleartext traffic:** Enabled in the manifest for local `ws://` to the ESP32.

---

## Summary

DisplayConnect turns an inexpensive **ESP32 CYD** into a **dedicated bike navigation display** by streaming compressed screenshots from your Android phone. You keep full Google Maps routing on the phone while the handlebar screen shows the same map—optimized for **cycling**: readable, mountable, and independent of holding or unlocking the phone on every turn.
