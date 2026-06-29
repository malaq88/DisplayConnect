# DisplayConnect v2 — Lista de tarefas

Objetivo: substituir espelhamento JPEG por **navegação via JSON** com **mapa desenhado na CYD** (rota + posição + manobra).

Escopo inicial: **nível A** (mapa vetorial). Fases B e C são opcionais.

---

## Fase 0 — Definição e preparação

- [ ] **0.1** Definir schema JSON v1 (`type`, `lat`, `lon`, `bearing`, `instruction`, `distance_m`, `route[][]`)
- [ ] **0.2** Documentar protocolo WebSocket (texto JSON + heartbeat; manter binário só se híbrido)
- [ ] **0.3** Decidir provedor de rota: OSRM (grátis) vs Google Routes API (billing)
- [ ] **0.4** Criar branch `feature/nav-json` no repositório
- [ ] **0.5** Marcar modo atual (JPEG mirror) como **legacy** ou removível depois da v2

---

## Fase 1 — Protocolo e firmware mínimo (ESP32)

- [ ] **1.1** Adicionar biblioteca **ArduinoJson** ao sketch
- [ ] **1.2** Criar `NavProtocol.h/.cpp` — parse JSON, validação, dispatch por `type`
- [ ] **1.3** Tratar mensagens WebSocket **texto** em `webSocketEvent` (`WStype_TEXT`)
- [ ] **1.4** Criar `MapRenderer.h/.cpp` — limpar área do mapa, desenhar polilinha, marcador, seta (`bearing`)
- [ ] **1.5** Criar `NavOverlay.h/.cpp` — faixa inferior com instrução + distância (TFT_eSPI ou LVGL)
- [ ] **1.6** Implementar heartbeat / timeout de conexão (sem frames JPEG)
- [ ] **1.7** Tela de teste: receber JSON fixo (Serial ou WebSocket) e desenhar rota estática
- [ ] **1.8** Testar em hardware CYD 240×320 (sem Android)

**Entregável:** CYD desenha rota + texto ao receber JSON de teste.

---

## Fase 2 — Android: envio de JSON (sem rota ainda)

- [ ] **2.1** Adicionar dependência **Play Services Location** (`FusedLocationProviderClient`)
- [ ] **2.2** Permissões no `AndroidManifest`: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- [ ] **2.3** Fluxo de permissão de localização na UI (MainScreen)
- [ ] **2.4** Criar pacote `navigation/` com `LocationTracker.kt` (updates 1–2 Hz)
- [ ] **2.5** Criar `protocol/NavMessage.kt` + serialização JSON
- [ ] **2.6** Estender `DisplaySocketClient` — `sendNavMessage(json: String)` via WebSocket texto
- [ ] **2.7** Criar `NavigationForegroundService` (tipo `location`, sem MediaProjection)
- [ ] **2.8** Enviar JSON com `lat`, `lon`, `bearing` (rota vazia ou mock)
- [ ] **2.9** Atualizar `MainScreen`: botão **Start Navigation** / **Stop Navigation**
- [ ] **2.10** Atualizar `TransmissionHub` / stats para bytes/s e updates/s (não FPS JPEG)

**Entregável:** celular envia posição em tempo real; CYD mostra marcador se mover.

---

## Fase 3 — Rota e projeção no mapa

- [ ] **3.1** Criar `routing/RouteProvider.kt` (interface) + implementação OSRM ou Routes API
- [ ] **3.2** UI para definir destino (campo de texto / mapa / “usar localização salva”)
- [ ] **3.3** Criar `map/MapProjector.kt` — lat/lon → pixels 240×320 (Web Mercator simplificado)
- [ ] **3.4** Centralizar mapa na posição atual com margem de zoom configurável
- [ ] **3.5** Simplificar polilinha (Douglas-Peucker ou amostragem) para caber no JSON
- [ ] **3.6** Incluir `route` em coordenadas de tela no JSON
- [ ] **3.7** ESP: redesenhar rota + posição a cada update
- [ ] **3.8** Recalcular rota ao desviar (threshold de distância à polilinha)

**Entregável:** navegação A→B com linha de rota desenhada na CYD.

---

## Fase 4 — Manobras e UX de navegação

- [ ] **4.1** Extrair próxima manobra da rota (step seguinte do OSRM/Routes)
- [ ] **4.2** Campos JSON: `instruction`, `distance_m`, `street` (opcional), `icon` (left/right/straight)
- [ ] **4.3** ESP: ícones de seta na faixa de manobra
- [ ] **4.4** Android: `bearing` suavizado (filtro) para seta estável na bike
- [ ] **4.5** Configurações: intervalo de update (Hz), zoom do mapa
- [ ] **4.6** Notificação foreground com próxima manobra
- [ ] **4.7** Teste com tela bloqueada + otimização de bateria desativada

**Entregável:** experiência usável em passeio de bike.

---

## Fase 5 — Limpeza e modo legacy

- [ ] **5.1** Remover ou isolar pacote `capture/` (MediaProjection, JPEG, wake locks)
- [ ] **5.2** Remover permissões e service `mediaProjection` do manifest
- [ ] **5.3** Remover settings obsoletos (FPS JPEG, crop, frame diff) do DataStore
- [ ] **5.4** Atualizar `README.md` e `README.pt-BR.md` com protocolo v2
- [ ] **5.5** (Opcional) manter modo espelhamento como flag experimental

**Entregável:** codebase focada na v2; documentação alinhada.

---

## Fase 6 — Opcional: mapa com ruas (tiles OSM)

- [ ] **6.1** Android envia `zoom` + bbox ou tile indices no JSON
- [ ] **6.2** ESP: `HTTPClient` + cache de tiles (SPIFFS/LittleFS)
- [ ] **6.3** Decode PNG/JPEG de tile (PNGdec ou provedor JPEG)
- [ ] **6.4** Compor 2–4 tiles em 240×320; desenhar rota por cima
- [ ] **6.5** Respeitar política de uso OSM (User-Agent, rate limit, cache)
- [ ] **6.6** Validar RAM/PSRAM na CYD

**Entregável:** mapa com ruas reais na placa.

---

## Fase 7 — Opcional: híbrido (snapshot + JSON)

- [ ] **7.1** Tipos de mensagem: `nav` (texto) + `map` (binário JPEG baixa frequência)
- [ ] **7.2** Android gera snapshot 240×320 (SDK ou static map API) a cada N segundos
- [ ] **7.3** ESP: JPEG de fundo + overlay LVGL para manobra
- [ ] **7.4** Sincronizar timestamp para evitar overlay desalinhado

**Entregável:** mapa mais rico sem stream contínuo.

---

## Ordem recomendada

```
Fase 0 → Fase 1 → Fase 2 → Fase 3 → Fase 4 → Fase 5
                              ↓
                    Fase 6 ou 7 (se quiser mapa com ruas/imagem)
```

## Estimativa de esforço (referência)

| Fase | Complexidade |
|------|----------------|
| 0–1  | Baixa–média (ESP + protocolo) |
| 2–3  | Média (Android location + rota) |
| 4    | Média (polish navegação) |
| 5    | Baixa (limpeza) |
| 6–7  | Alta (tiles / híbrido) |

## Critérios de pronto (v2 MVP)

- [ ] Conectar CYD via Wi-Fi WebSocket
- [ ] Definir destino no app Android
- [ ] CYD exibe rota, posição atual e próxima manobra
- [ ] Updates ≥ 1 Hz com payload JSON pequeno (< 2 KB)
- [ ] Funciona com tela do celular bloqueada (serviço `location`)
- [ ] Sem MediaProjection / sem espelhamento JPEG
