# DisplayConnect

**English:** [README.md](README.md)

**DisplayConnect** envia **dados de navegação para bike** do seu celular Android para um **ESP32 CYD** (Cheap Yellow Display) barato via **Bluetooth Low Energy**. O celular calcula GPS, rota e geometria do mapa; a CYD **desenha um mapa vetorial** (rota, ruas próximas, sua posição e instruções de curva) em um TFT de 2,4″.

**Versão atual (`master`):** navegação via **JSON over BLE UART** — a CYD **não** recebe mais espelhamento da tela nem precisa de Wi‑Fi no link celular↔display.

---

## v1.0 vs v2.0 — da replicação de tela ao envio da rota em JSON

A partir da **versão 2.0**, o DisplayConnect **deixou de replicar a tela do celular**. A navegação é enviada como dados estruturados; a CYD desenha o mapa localmente.

| | **v1.0** | **v2.0+** (`master`) |
|---|--|--|
| **Método** | Espelhamento de tela | Navegação JSON |
| **Celular** | MediaProjection + JPEG | GPS + OSRM → pixels |
| **Link com a CYD** | WebSocket binário (JPEG) | v2 inicial: WebSocket texto → **agora BLE UART** |
| **CYD** | TJpg_Decoder | ArduinoJson + TFT_eSPI (mapa vetorial) |
| **Google Maps** | Necessário na tela | Opcional (OSRM + OSM no celular) |
| **Tela bloqueada** | Problemático | Suportado (serviço `location`) |

**Depois da v2.0:** busca Nominatim, perfis de rota, ruas Overpass, linhas mais grossas e **transporte BLE** (NimBLE v2).

---

## Como funciona (v2.0+)

O celular é o **cérebro**; a CYD é o **display**. Internet no celular só para OSRM/OSM. O link com a CYD é **BLE**.

```
Celular: GPS + OSRM + OSM → JSON → BleNavClient (NUS)
                │
                │ BLE (JSON + \n)
                ▼
CYD: NimBLE server → fila RX → loop() → ArduinoJson → MapRenderer
```

1. **Scan / Connect** Bluetooth (`DisplayConnect-CYD`)
2. Busque destino (Nominatim) ou coordenadas
3. Escolha perfil de rota
4. **Start Navigation** — serviço `location`
5. Updates JSON via BLE; a CYD redesenha o mapa

Protocolo: [docs/PROTOCOL_V2.md](docs/PROTOCOL_V2.md)

---

## Tecnologias

### App Android (Kotlin)

- **Jetpack Compose + Material 3** -> UI (scan BLE, rotas, busca)
- **MVVM + StateFlow** -> estado da UI
- **DataStore** -> endereço BLE, perfil, destino
- **Play Services Location** -> GPS
- **Foreground Service** (`location`) -> navegação com tela bloqueada
- **BleNavClient** -> JSON para a CYD via BLE UART
- **OSRM / Nominatim / Overpass** -> rota e mapa (internet)
- **OkHttp** -> HTTP das APIs (não é o link com a CYD)

### Firmware ESP32

- **NimBLE-Arduino v2.x** -> servidor GATT UART (NUS)
- **TFT_eSPI** + **ArduinoJson** -> desenho e parse
- Sem Wi‑Fi obrigatório na CYD para navegação
- **Callbacks BLE** só enfileiram dados; o **`loop()`** desenha e faz parse (evitar reboot por stack overflow)

---

## Primeiros passos

1. Flash: bibliotecas **TFT_eSPI**, **NimBLE-Arduino (v2.x)**, **ArduinoJson** v7+
2. Copie `config.h.example` → `config.h` e envie `DisplaySender.ino`
3. No app: conceda Bluetooth + localização → **Scan** → Connect → navegue

Se a CYD reiniciar ao conectar, use o firmware atual (TFT apenas no `loop()`, nunca no callback BLE).

---

## Limitações

- BLE tem alcance limitado (~10 m)
- Um cliente BLE por vez
- APIs OSM/OSRM no celular (internet)

---

## Resumo

Desde a **v2.0** o app envia **JSON de navegação** (não espelha a tela). No `master` atual o transporte para a CYD é **BLE UART**, e a placa desenha o mapa com TFT_eSPI.
