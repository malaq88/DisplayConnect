# DisplayConnect v2 — Lista de tarefas

Objetivo: substituir espelhamento JPEG por **navegação via JSON** com **mapa desenhado na CYD** (rota + posição + manobra).

**Status atual:** MVP v2 concluído no `master`. Melhorias pós-v2.0 também implementadas.

---

## Fase 0 — Definição e preparação

- [x] **0.1** Definir schema JSON v1 (`type`, `lat`, `lon`, `bearing`, `instruction`, `distance_m`, `route[][]`)
- [x] **0.2** Documentar protocolo (JSON + heartbeat; transporte atual = BLE UART)
- [x] **0.3** Provedor de rota: **OSRM** (grátis)
- [x] **0.4** Releases git `v1.0` / `v2.0`
- [x] **0.5** Modo JPEG (v1) preservado em tag; fluxo principal é v2

---

## Fase 1 — Protocolo e firmware mínimo (ESP32)

- [x] **1.1** Biblioteca **ArduinoJson**
- [x] **1.2** `nav_protocol.cpp` — parse JSON
- [x] **1.3** Transporte BLE NUS + fila RX (substituiu WebSocket texto)
- [x] **1.4** `map_renderer.cpp` — polilinha, marcador, seta (`bearing`)
- [x] **1.5** Overlay inferior com instrução + distância (+ `html_renderer` para modo Maps)
- [x] **1.6** Heartbeat JSON
- [x] **1.7** Teste em hardware CYD
- [x] **1.8** CYD desenha rota + texto ao receber JSON
- [x] **1.9** BLE estável: TFT/JSON só no `loop()` (evitar reboot no connect)

---

## Fase 2 — Android: envio de JSON

- [x] **2.1** Play Services Location
- [x] **2.2** Permissões `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`
- [x] **2.3** Fluxo de permissão na UI
- [x] **2.4** `navigation/LocationTracker.kt`
- [x] **2.5** `protocol/NavMessage.kt`
- [x] **2.6** `BleNavClient.sendNavMessage(json)` (BLE UART; replaced WebSocket)
- [x] **2.7** `NavigationForegroundService` (tipo `location`)
- [x] **2.8** JSON com `lat`, `lon`, `bearing`, rota
- [x] **2.9** Botões **Start Navigation** / **Stop Navigation**
- [x] **2.10** Stats em updates/s e bytes/s

---

## Fase 3 — Rota e projeção no mapa

- [x] **3.1** `routing/OsrmRouteProvider.kt`
- [x] **3.2** UI para destino (coordenadas + busca por nome — ver Fase 8)
- [x] **3.3** `map/MapProjector.kt`
- [x] **3.4** Mapa centralizado na posição atual (`mapScaleMeters`)
- [x] **3.5** Simplificação da polilinha (amostragem, máx. 64 pontos)
- [x] **3.6** `route` em pixels no JSON
- [x] **3.7** ESP redesenha rota + posição
- [ ] **3.8** Recalcular rota ao desviar (threshold à polilinha)

---

## Fase 4 — Manobras e UX de navegação

- [x] **4.1** Próxima manobra via steps OSRM
- [x] **4.2** Campos `instruction`, `distance_m`, `street`
- [ ] **4.3** Ícones de seta na faixa de manobra (hoje só texto)
- [x] **4.4** `bearing` do GPS ou calculado pela rota
- [x] **4.5** Configurações: Hz de update, raio do mapa
- [ ] **4.6** Notificação com próxima manobra no texto
- [x] **4.7** Funciona com tela bloqueada (serviço `location`)

**Extra (v2.0):** Modo Maps browser — WebView + campo `html` no JSON.

---

## Fase 5 — Limpeza e modo legacy

- [x] **5.1** Fluxo principal sem `capture/` (código v1 ainda no repo, não usado)
- [x] **5.2** Manifest sem `mediaProjection` no fluxo v2
- [x] **5.3** Settings v2 (Hz, map scale, destino, perfil de rota)
- [x] **5.4** `README.md`, `README.pt-BR.md`, `PROTOCOL_V2.md` atualizados
- [x] **5.5** v1 preservado em tag `v1.0`

---

## Fase 8 — Melhorias pós-v2.0 (concluídas)

- [x] **8.1** Busca de destino por endereço/nome (`NominatimGeocoder`)
- [x] **8.2** Perfis de rota: carro, moto, bike, a pé (`RouteProfile` + OSRM)
- [x] **8.3** Ruas de contexto no mapa (`OverpassStreetProvider` + campo `streets` no JSON)
- [x] **8.4** Linhas mais grossas na CYD (ruas 2 px, rota 3 px)
- [x] **8.5** Documentação alinhada com arquitetura JSON + BLE
- [x] **8.6** Transporte BLE (NimBLE v2) + connect estável (callback sem TFT)

---

## Fase 6 — Opcional: mapa com tiles OSM na ESP

- [ ] **6.1** Android envia `zoom` + bbox ou tile indices
- [ ] **6.2** ESP: `HTTPClient` + cache de tiles
- [ ] **6.3** Decode PNG/JPEG de tile
- [ ] **6.4** Compor tiles; desenhar rota por cima
- [ ] **6.5** Política de uso OSM na ESP
- [ ] **6.6** Validar RAM/PSRAM

**Nota:** Fase 8 usa Overpass no **Android** e envia segmentos já projetados — alternativa mais leve.

---

## Fase 7 — Opcional: híbrido (snapshot + JSON)

- [ ] **7.1** Tipos `nav` + `map` (JPEG baixa frequência)
- [ ] **7.2** Snapshot 240×320 a cada N segundos
- [ ] **7.3** JPEG de fundo + overlay
- [ ] **7.4** Sincronização timestamp

---

## Ordem recomendada (próximos passos)

```
Concluído: Fase 0–5 + Fase 8
Pendente útil: 3.8 (reroute), 4.3 (ícones), 4.6 (notificação rica)
Opcional: Fase 6 ou 7
```

## Critérios de pronto (v2 MVP) — atendidos

- [x] Conectar CYD via **BLE UART** (NUS; substituiu Wi‑Fi WebSocket)
- [x] Definir destino no app Android (coordenadas + busca)
- [x] CYD exibe rota, posição e próxima manobra
- [x] Updates ≥ 1 Hz com payload JSON pequeno (< 2 KB)
- [x] Funciona com tela do celular bloqueada
- [x] Sem MediaProjection / sem espelhamento JPEG no fluxo principal
