# DisplayConnect

**English:** [README.md](README.md)

**DisplayConnect** espelha a tela de navegação GPS do seu celular em um **ESP32 CYD** (Cheap Yellow Display) barato, montado na bicicleta, via Wi-Fi. Durante o pedal, as instruções passo a passo do Google Maps ficam visíveis em um TFT de 2,4″ dedicado, sem precisar tirar o telefone do bolso ou da mochila.

O celular captura a tela, comprime em JPEG e envia os quadros para o ESP32. O CYD decodifica e desenha cada frame em tempo real — um display de navegação leve e adequado para passeios de bicicleta.

---

## Por que usar um ESP32 CYD para navegação na bike?

Pedalar usando só o celular como display tem desvantagens práticas:

| Problema no celular | Como o DisplayConnect ajuda |
|---------------------|----------------------------|
| Tela difícil de ler no sol ou na chuva | Monte o CYD no guidão; mantenha o celular protegido |
| Bateria consumida pelo GPS + LTE com tela ligada | O celular pode ficar com brilho baixo ou no bolso; o CYD mostra só o mapa espelhado |
| Tocar na tela pedalando é inseguro | Olhe o CYD; configure a rota no celular antes de sair |
| GPS dedicado para bike é caro | Placas ESP32-2432S028 custam poucos dólares e reutilizam o roteamento do Google Maps |

O **ESP32-2432S028** (também chamado de **CYD** ou **Cheap Yellow Display**) reúne ESP32, TFT ILI9341 240×320 e touch em uma única placa. O DisplayConnect usa essa resolução nativamente: os quadros são redimensionados com **letterbox** para manter as proporções corretas do mapa no display em retrato.

---

## Como funciona

```
┌─────────────────────┐         Wi-Fi (WebSocket)         ┌──────────────────────┐
│   Celular Android   │  ─── quadros JPEG (240×320) ───►  │  ESP32 CYD           │
│                     │                                   │  TFT 240×320         │
│  Google Maps        │  ◄── heartbeat / reconexão ─────  │  servidor WebSocket  │
│  MediaProjection    │                                   │  TJpg_Decoder        │
│  codificação JPEG   │                                   │  TFT_eSPI            │
└─────────────────────┘                                   └──────────────────────┘
```

1. Você conecta o app ao ESP32 (padrão `192.168.4.1:81` no modo AP, ou o IP da rede no modo STA).
2. Abre o **Google Maps** e inicia a navegação no celular.
3. Toca em **Start Transmission**; o app solicita permissão de **MediaProjection** e inicia um serviço em primeiro plano.
4. Cada quadro é recortado (opcional), redimensionado para 240×320 com letterbox, comprimido em JPEG e enviado via WebSocket.
5. O ESP32 recebe o quadro, decodifica o JPEG e desenha no TFT.

---

## Tecnologias

### App Android

| Tecnologia | Função |
|------------|--------|
| **Kotlin** | Linguagem principal |
| **Jetpack Compose** | UI declarativa moderna (Material 3) |
| **MVVM** | `MainViewModel` e `SettingsViewModel` separam UI da lógica |
| **Navigation Compose** | Tela principal ↔ Configurações |
| **DataStore Preferences** | Configurações persistentes (IP, FPS, qualidade, crop, etc.) |
| **MediaProjection API** | Captura a tela inteira (incluindo Google Maps) |
| **VirtualDisplay + ImageReader** | Pipeline eficiente de quadros a partir da projeção |
| **Foreground Service** (tipo `mediaProjection`) | Mantém a captura ativa durante a navegação |
| **OkHttp WebSocket** | Transporte binário de quadros, ping, reconexão automática |
| **Coroutines + StateFlow** | Rede assíncrona e estado reativo na UI |

**Pipeline de imagem (lado do app)**

- **ImageProcessor** — margens de crop, escala com letterbox, rotação, compressão JPEG (com redução opcional de qualidade no modo economia de bateria).
- **FrameDiffer** — opção “transmitir só em mudanças” para economizar banda e CPU.
- **StatsTracker** — FPS, taxa de transferência, quadros enviados/ignorados.
- **DisplaySocketClient** — cabeçalho de 4 bytes big-endian + payload JPEG; heartbeat `0,0,0,0` a cada 15 s.

**Requisitos:** Android 7.0+ (API 24), target API 36.

### Firmware ESP32 (`DisplaySender/`)

| Tecnologia | Função |
|------------|--------|
| **ESP32** (ESP32-2432S028) | Wi-Fi + decodificação JPEG + display |
| **Arduino framework** | Sketch `DisplaySender.ino` |
| **TFT_eSPI** (Bodmer) | Driver ILI9341, pinos CYD via `Setup_User.h` |
| **TJpg_Decoder** (Bodmer) | Decodificação JPEG otimizada para o TFT |
| **WebSockets_Generic** (Khoi Hoang) | Servidor WebSocket na porta 81 |
| **Wi-Fi STA / AP** | Conecta ao hotspot do celular ou cria o AP `DisplayConnect-CYD` |

**Display:** 240×320 em retrato, ILI9341_2, gamma ajustado para placas CYD (paleta CYDAlbumPlayer nas telas de status).

---

## Protocolo de comunicação

Mensagens WebSocket binárias do app → ESP32:

| Mensagem | Conteúdo |
|----------|----------|
| **Cabeçalho** | 4 bytes, big-endian `uint32` = tamanho do JPEG em bytes |
| **Corpo** | `N` bytes de dados JPEG |
| **Heartbeat** | Só cabeçalho: `0x00 0x00 0x00 0x00` (ignorado) |

O cliente pode enviar cabeçalho e corpo em **dois frames WebSocket binários separados** ou em **um único frame combinado** (cabeçalho + JPEG). O firmware trata os dois casos.

Ao conectar, o ESP32 pode responder com o texto `OK`.

---

## Estrutura do projeto

```
DisplayConnect/
├── app/                          # Aplicativo Android
│   └── src/main/java/com/example/displayconnect/
│       ├── capture/                # MediaProjection, ImageProcessor, FrameDiffer, service
│       ├── network/                # DisplaySocketClient (WebSocket)
│       ├── storage/                # SettingsRepository (DataStore)
│       ├── ui/                     # Telas e componentes Compose
│       ├── viewmodel/              # MainViewModel, SettingsViewModel
│       ├── models/                 # AppSettings, ConnectionState, estatísticas
│       └── utils/                  # TransmissionHub, StatsTracker, IntentUtils
├── DisplaySender/
│   ├── DisplaySender.ino           # Firmware ESP32
│   ├── config.h.example            # Modelo Wi-Fi (copiar para config.h)
│   └── config.h                    # Credenciais locais (gitignored)
├── README.md                       # Documentação em inglês
└── README.pt-BR.md                 # Esta documentação
```

---

## Hardware

- **Placa:** ESP32-2432S028 (Cheap Yellow Display), TFT 240×320
- **Celular:** Android com Google Maps (ou qualquer app que você queira espelhar)
- **Rede:** Celular e ESP32 na mesma Wi-Fi, **ou** modo AP do ESP32 (`192.168.4.1`) com o celular conectado em `DisplayConnect-CYD`

**Configuração TFT_eSPI:** Ajuste `Arduino/libraries/TFT_eSPI/Setup_User.h` para o seu CYD (ILI9341_2, `TFT_RGB`, `USE_HSPI_PORT`) e garanta que `User_Setup_Select.h` o inclua.

---

## Primeiros passos

### 1. Gravar o firmware na ESP32

1. Instale as bibliotecas Arduino: **TFT_eSPI**, **TJpg_Decoder**, **WebSockets_Generic**.
2. Copie `DisplaySender/config.h.example` para `DisplaySender/config.h`.
3. Opcionalmente defina `WIFI_SSID` / `WIFI_PASSWORD` para entrar no hotspot do celular. Deixe vazio para usar o modo AP (`192.168.4.1`).
4. Envie `DisplaySender.ino` para a placa.
5. Na inicialização, o CYD exibe **Waiting for app** com IP e porta.

### 2. Compilar e instalar o app Android

1. Abra o projeto no **Android Studio**.
2. Compile e instale no celular (`minSdk 24`).
3. Conceda **notificações** (Android 13+) e **captura de tela** quando solicitado.

### 3. Fluxo no passeio de bike

1. Ligue o CYD e anote o IP (ex.: `192.168.4.1`, porta `81`).
2. No DisplayConnect, informe IP/porta → **Connect**.
3. **Abra o Google Maps**, defina a rota e inicie a navegação.
4. Toque em **Start Transmission** e monte o CYD no guidão.
5. Ajuste **Settings** se necessário (FPS, qualidade JPEG, crop, modo só em mudanças).

---

## Referência de configurações

| Configuração | Padrão | Descrição |
|--------------|--------|-----------|
| IP / Porta ESP | `192.168.4.1` / `81` | Endpoint WebSocket |
| FPS | 15 | Taxa alvo de captura |
| Qualidade JPEG | 70 | Compressão (menor = quadros menores) |
| Resolução | 240×320 | Tamanho de saída (igual ao CYD) |
| Rotação | 0° | Gira o quadro codificado |
| Economia de bateria | Desligado | Reduz qualidade JPEG durante a transmissão |
| Transmitir só em mudanças | Ligado | Ignora quadros similares (FrameDiffer) |
| Margens de crop | Topo 4%, base 6% | Remove barras de status / chrome da navegação |
| Sensibilidade a mudanças | 2% | Limiar do comparador de quadros |

---

## Limitações conhecidas

- **Google Maps e captura de tela:** O Maps usa `FLAG_SECURE` em algumas telas, o que pode gerar **quadros pretos** ou captura instável quando o Maps está em primeiro plano. É uma restrição do Android/Google, não um problema de Wi-Fi ou ESP32. Uma alternativa de longo prazo é embutir o **Google Maps SDK** no DisplayConnect em vez de espelhar o app Maps.
- **Latência:** O streaming JPEG via Wi-Fi adiciona um pequeno atraso; adequado para consultar instruções de rota, não para jogos ou uso que exija resposta instantânea.
- **Cliente único:** O firmware atende um cliente WebSocket por vez.
- **WebSocket sem criptografia:** Apenas `ws://` local; adequado para rede privada na bike, não para a internet pública.

---

## Desenvolvimento

- **Licença:** MIT — veja [LICENSE](LICENSE).
- **Pacote padrão:** `com.example.displayconnect` (altere o `applicationId` antes de publicar).
- **Tráfego cleartext:** Habilitado no manifest para `ws://` local com o ESP32.

---

## Resumo

O DisplayConnect transforma um **ESP32 CYD** barato em um **display de navegação dedicado para bicicleta**, transmitindo capturas de tela comprimidas do seu Android. Você mantém o roteamento completo do Google Maps no celular enquanto a tela no guidão mostra o mesmo mapa — pensado para **ciclismo**: legível, montável no guidão e sem precisar segurar ou desbloquear o celular a cada curva.
