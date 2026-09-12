# DisplaySenderIDF

Firmware **ESP-IDF** do DisplayConnect para a placa **Guition JC3248W535EN** (ESP32-S3 + tela AXS15231B QSPI 320×480).

É a porta do firmware Arduino `DisplaySender/` (CYD 240×320) para ESP-IDF nativo, mantendo o mesmo protocolo BLE Nordic UART (NUS) e JSON de navegação do app Android.

## Hardware

| Item | Valor |
|------|--------|
| MCU | ESP32-S3 (16 MB flash, 8 MB OPI PSRAM) |
| Display | AXS15231B QSPI, 320×480 IPS |
| Touch | AXS15231B I2C (SDA=4, SCL=8) — switch de tema |
| BLE name | `DisplayConnect-S3` |
| Referência | [NorthernMan54/JC3248W535EN](https://github.com/NorthernMan54/JC3248W535EN) |

Pinos QSPI (já na placa): CS=45, CLK=47, D0=21, D1=48, D2=40, D3=39, BL=1.

**Gotchas (tela preta):**
1. Usar a tabela de init **Guition/NorthernMan54** — o default do componente Espressif termina com `0x22` (All Pixels Off).
2. `espressif/esp_lcd_axs15231b` ainda inverte `disp_on_off`: passar **`false`** envia `DISPON` (igual ao demo oficial da placa).

## UI

Mesmo visual e comportamento do `DisplaySender/` (tema claro padrão / escuro opcional), na resolução **320×480** da JC3248W535EN:

- Ruas com casing, rota azul, puck, bottom sheet
- Overlay UTF-8 com fonte Latin-1 (acentos em ruas/instruções), tempo/distância restantes, GPS fraco, fora da rota
- Idioma PT/EN via JSON `config`/`lang` (persistido em NVS `display-lang`)
- Switch L/D por toque capacitivo; preferência em NVS (`ui` / `theme`)
- Overflow BLE descarta o frame incompleto (igual à CYD), em vez de concatenar lixo

## Pré-requisitos

- [ESP-IDF](https://docs.espressif.com/projects/esp-idf/en/latest/esp32s3/get-started/) **≥ 5.1** (testado com o target `esp32s3`)
- Cabo USB na porta da placa

## Build & flash

No ESP-IDF PowerShell / export:

```bash
cd DisplaySenderIDF
idf.py set-target esp32s3
idf.py build
idf.py -p COMx flash monitor
```

Na primeira build o Component Manager baixa `espressif/esp_lcd_axs15231b` (e `esp_lcd_touch`).

Se você já tinha um `sdkconfig` do template hello_world, reaplique os defaults da placa:

```bash
idf.py fullclean
del sdkconfig
idf.py set-target esp32s3
idf.py build
```

## App Android

1. No app, escaneie BLE e conecte em **DisplayConnect-S3** (mesmo NUS / framing `\n` do CYD).
2. O Android projeta o mapa em **240×232**; este firmware **escala** para 320×370 (área de mapa) + overlay. O JSON é o mesmo da CYD.
3. A partir da **v3.0** o overlay mostra tempo/distância restantes, GPS fraco, fora da rota e texto UTF-8 (igual ao `DisplaySender/`).

Protocolo: [docs/PROTOCOL_V2.md](../docs/PROTOCOL_V2.md). Melhorias de navegação da v3.0 adaptadas de [PLZ-1/DisplayConnectV2.1](https://github.com/PLZ-1/DisplayConnectV2.1).

## Estrutura

```
DisplaySenderIDF/
├── main/
│   ├── main.c            # loop: flags UI + drain RX + touch/tema + draw
│   ├── ble_nus.c         # servidor NimBLE NUS
│   ├── display.c         # AXS15231B QSPI + backlight PWM
│   ├── ui.c              # framebuffer PSRAM + primitives
│   ├── touch.c           # touch I2C AXS15231B
│   ├── maps_theme.c      # paleta claro/escuro + NVS
│   ├── nav_protocol.c    # parse JSON (cJSON) + config/lang
│   ├── map_renderer.c    # ruas / rota / puck / overlay / switch
│   ├── utf8_text.c       # fonte Latin-1 + idioma NVS
│   ├── latin_font.h      # glifos 20px (ASCII + Latin-1)
│   └── loading_screen.c
├── sdkconfig.defaults    # PSRAM, flash 16MB, NimBLE
└── partitions.csv
```

Callbacks BLE **só enfileiram bytes**; desenho e `cJSON` rodam na task principal (mesmo cuidado do reboot da CYD).

## Notas

- QSPI a **40 MHz** (80 MHz costuma gerar artefatos).
- Firmware Arduino CYD permanece em `DisplaySender/` para a ESP32-2432S028.
- Protocolo: [docs/PROTOCOL_V2.md](../docs/PROTOCOL_V2.md)
