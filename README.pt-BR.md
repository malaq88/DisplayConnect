# DisplayConnect

**English:** [README.md](README.md)

**DisplayConnect** envia **dados de navegação para bike** do seu celular Android para um **ESP32 CYD** (Cheap Yellow Display) barato via **Bluetooth Low Energy**. O celular calcula GPS, rota e geometria do mapa; a CYD **desenha um mapa vetorial** (rota, ruas próximas, sua posição e instruções de curva) em um TFT de 2,4″.

**Versão atual:** **v3.0** (tag [`v3.0`](https://github.com/malaq88/DisplayConnect/releases/tag/v3.0)) — JSON via BLE UART, mapa sem eixos esticados, filtro de GPS, ruas offline e OSRM FOSSGIS. Grave **app e firmware da CYD juntos**.

---

## v1.0 vs v2.0 — da replicação de tela ao envio da rota em JSON

A partir da **versão 2.0**, o DisplayConnect **deixou de replicar a tela do celular**. A navegação é enviada como dados estruturados; a CYD desenha o mapa localmente.

| | **v1.0** | **v2.0+** (`v2.0`, **`v3.0`**) |
|---|--|--|
| **Método** | Espelhamento de tela | Navegação JSON |
| **Celular** | MediaProjection + JPEG | GPS + OSRM → pixels |
| **Link com a CYD** | WebSocket binário (JPEG) | v2 inicial: WebSocket texto → **agora BLE UART** |
| **CYD** | TJpg_Decoder | ArduinoJson + TFT_eSPI (mapa vetorial) |
| **Google Maps** | Necessário na tela | Opcional (OSRM + OSM no celular) |
| **Tela bloqueada** | Problemático | Suportado (serviço `location`) |

**Depois da v2.0:** busca Nominatim, perfis de rota, ruas Overpass, **transporte BLE** (NimBLE v2) e **UI claro/escuro** com switch touch na CYD.

**v3.0** traz de volta para a **CYD 240×320** (`DisplaySender/`) as melhorias de navegação do [PLZ-1/DisplayConnectV2.1](https://github.com/PLZ-1/DisplayConnectV2.1): projeção sem esticar eixos, recorte de ruas, filtro de GPS, tempo/distância restantes, OSRM bike/a pé nos grafos FOSSGIS, busca Nominatim+Photon, PT/EN, download offline de ruas e BLE mais estável. O hardware daquele repositório (LOLIN32 Lite + ST7796S 480×320) **não** entra nesta versão.

---

## Como funciona (v2.0+)

O celular é o **cérebro**; a CYD é o **display**. Internet no celular só para OSRM/OSM. O link com a CYD é **BLE**.

```
Celular: GPS + OSRM + OSM → JSON → BleNavClient (NUS)
                │
                │ BLE (JSON + \n)
                ▼
CYD: NimBLE → fila RX → loop() → ArduinoJson → MapRenderer
     Touch XPT2046 → switch L/D (tema salvo na NVS)
```

1. **Scan / Connect** Bluetooth (`DisplayConnect-CYD`)
2. Busque destino (Nominatim) ou coordenadas
3. Escolha perfil de rota
4. **Start Navigation** — serviço `location`
5. Updates JSON via BLE; a CYD redesenha o mapa (ruas, rota azul, puck, bottom sheet)

### UI na CYD

- Tema **claro** por padrão (estilo Maps); tema **escuro** opcional
- Toque no **switch L/D** (canto inferior direito na espera, ou lado direito do bottom sheet na navegação)
- Preferência gravada na NVS (`Preferences`: `ui` / `theme`)

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
- **OSRM / Nominatim / Photon / Overpass** -> rota e mapa (internet; ruas podem ficar salvas no celular)
- **OkHttp** -> HTTP das APIs (não é o link com a CYD)

### Firmware ESP32 (CYD / Arduino)

- **NimBLE-Arduino v2.x** -> servidor GATT UART (NUS)
- **TFT_eSPI** + **ArduinoJson** -> desenho e parse
- **XPT2046_Touchscreen** -> toque para o switch de tema
- **Preferences** -> persistência claro/escuro
- Módulos: `map_renderer`, `maps_theme`, `touch_cyd`, `nav_protocol`, `loading_screen`, `html_renderer`, `utf8_text` (acentos)
- Sem Wi‑Fi obrigatório na CYD para navegação
- **Callbacks BLE** só enfileiram dados; o **`loop()`** desenha e faz parse (evitar reboot por stack overflow)

---

## Primeiros passos

1. Flash: bibliotecas **TFT_eSPI**, **NimBLE-Arduino (v2.x)**, **ArduinoJson** v7+, **XPT2046_Touchscreen**
2. Copie `config.h.example` → `config.h` e envie `DisplaySender.ino`
3. No boot: tela **Ready to navigate** + switch de tema no canto
4. No app: conceda Bluetooth + localização → **Scan** → Connect → navegue

Se a CYD reiniciar ao conectar, use o firmware atual (TFT apenas no `loop()`, nunca no callback BLE).

---

## Limitações

- BLE tem alcance limitado (~10 m)
- Um cliente BLE por vez
- APIs OSM/OSRM no celular (internet)
- Touch resistivo da CYD é impreciso; a área de toque do switch é generosa de propósito

---

## Créditos

- Projeto original: **Antonio Malaquias** — [malaq88/DisplayConnect](https://github.com/malaq88/DisplayConnect)
- As melhorias de navegação da **v3.0** foram adaptadas de **[PLZ-1/DisplayConnectV2.1](https://github.com/PLZ-1/DisplayConnectV2.1)** (LOLIN32 Lite + ST7796S) para a CYD ESP32-2432S028 e para a JC3248W535EN. Aquele repositório não é um lançamento oficial do DisplayConnect.

Dados de mapa: **© colaboradores OpenStreetMap**. Roteamento: OSRM/FOSSGIS. Busca: Nominatim e Photon.

---

## Resumo

Desde a **v2.0** o app envia **JSON de navegação** (não espelha a tela). A **v3.0** (com crédito a [PLZ-1/DisplayConnectV2.1](https://github.com/PLZ-1/DisplayConnectV2.1)) deixa o mapa e o GPS mais estáveis na **CYD original**: transporte **BLE UART**, desenho local estilo Maps, tema claro/escuro por toque.
