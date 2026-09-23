<div align="center">

<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="140" alt="Amalia"/>

# Amalia

### Open-Source AI Voice Assistant for Android

*Feels alive. Responds instantly. Lives on your phone.*

🌐 **[mirabel.tech](https://mirabel.tech)** — official website: download the APK, live pipeline demo, FAQ

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](../LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.08-blue.svg)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/Min%20SDK-API%2026-orange.svg)](https://android.com)

[🌍 Other languages](../README.md)

<br/>

> Amalia is not just an assistant — she has a personality.
> 18 years old, confident, sharp, and direct.
> She doesn't ask unnecessary questions. She acts.

<br/>

<img src="../icon-preview/1_dark_sheet.png" width="680" alt="Amalia UI Preview"/>

</div>

---

## 📖 About the Project

Amalia is a fully open-source Android voice assistant built from scratch with modern Kotlin and Jetpack Compose. The project was created to explore the limits of on-device and cloud AI pipelines on mobile — how fast, how natural, and how smart a voice assistant can be in 2026.

The entire pipeline — from the moment you press the mic button to the moment you hear the response — takes **under 1 second** in typical conditions.

Everything is transparent: the source code is open, the architecture is documented, and you are free to fork, modify, and build on top of it. The only ask: **keep a reference to the original author**.

---

## ✨ Features

### 🎙️ Voice Recognition
- **Groq Whisper Large v3 Turbo** — state-of-the-art speech-to-text, ~220ms average latency
- **Silero VAD** — on-device voice activity detection, works fully offline
- Automatic end-of-speech detection: 600ms silence threshold
- No streaming socket — clean HTTP request after speech ends, no dropped words
- Supports Russian, English, and auto-detection mode

### 🧠 AI Brain
- **Qwen3 (27B)** running on **Groq LPU** hardware — ~150ms to first token
- JSON-mode responses for reliable tool calling
- Rich system prompt — Amalia has a defined character, not a generic chatbot
- Tool use: can call device functions, query system state, set alarms, etc.
- Single API key for both STT and LLM (Groq)

### 🔊 Voice Synthesis
- **Fish Audio `drama-3-preview`** — the most expressive TTS model available
- Raw PCM 24kHz streaming — no MP3 decoding overhead
- First audio chunk in ~448–826ms
- `USAGE_ASSISTANT` AudioTrack — correct audio routing on Android
- Independent `playJob` — TTS is never interrupted by the next conversation cycle

### 🌗 Circadian UI
- Interface color temperature shifts automatically throughout the day
- Cool blue tones in the morning, warm amber in the evening
- Based on a custom `CircadianEngine` — no third-party library needed
- Smooth animated transitions between states: Idle → Listening → Thinking → Speaking

### 🛠️ Device Control
Amalia can control your phone through voice:

| Command | What it does |
|---|---|
| "Turn on Wi-Fi" | Toggles Wi-Fi |
| "Bluetooth off" | Disables Bluetooth |
| "Brightness 70%" | Sets screen brightness |
| "Volume up" | Increases media volume |
| "Flashlight on" | Turns on torch |
| "Set alarm for 7am" | Creates system alarm |
| "Timer 10 minutes" | Starts a countdown timer |
| "What's the battery?" | Reports battery level |
| "Is it charging?" | Checks charging state |

### 🔒 Privacy & Security
- API keys stored in `local.properties` — never committed to git
- In-app API key settings screen — no need to rebuild
- No analytics, no telemetry, no data collection
- All VAD processing is on-device

---

## ⚡ Performance

```
🎙️ You stop speaking
        ↓  Silero VAD detects 600ms of silence
        ↓
  Groq Whisper v3 Turbo    ≈ 220ms   (speech → text)
        ↓
  Qwen3 27B on Groq LPU   ≈ 150ms   (text → response JSON)
        ↓
  Fish Audio drama-3       ≈ 500ms   (text → first PCM chunk)
        ↓
🔊 You hear Amalia speak
────────────────────────────────────
  Total end-to-end latency:  ~870ms
```

All three steps run sequentially but are pipeline-optimized: TTS starts streaming before LLM finishes generating, and AudioTrack plays before all audio is received.

---

## 🏗️ Architecture

```
app/src/main/java/com/my/amali/
│
├── core/
│   ├── di/ServiceLocator.kt          — dependency wiring (no DI framework)
│   └── navigation/                   — NavHost + BottomNavBar
│
├── data/
│   ├── ai/
│   │   ├── AIOrchestrator.kt         — STT → LLM → TTS pipeline coordinator
│   │   ├── GroqLLM.kt                — LLM client, JSON mode, tool calling
│   │   ├── GroqWhisperStt.kt         — STT engine, VAD loop, Whisper HTTP
│   │   ├── GroqSttClient.kt          — multipart POST to Groq transcription API
│   │   ├── SpeechGate.kt             — Silero VAD wrapper
│   │   ├── VoiceRecorder.kt          — AudioRecord lifecycle management
│   │   ├── FishAudioTTS.kt           — TTS engine, PCM streaming
│   │   ├── AudioPlayer.kt            — AudioTrack PCM playback
│   │   ├── AmaliaLog.kt              — centralized logger (tag: AMALIA)
│   │   ├── AIEngineInterfaces.kt     — SttEvent, AiResponse, EngineOptions
│   │   └── ModelCatalog.kt           — model registry per provider
│   │
│   ├── model/
│   │   ├── Conversation.kt           — conversation data model
│   │   └── DeviceStatus.kt           — battery, volume, charging state
│   │
│   └── repository/
│       ├── ConversationRepository.kt — conversation history persistence
│       └── SettingsRepository.kt     — DataStore-backed settings
│
├── domain/entity/
│   ├── Settings.kt                   — app settings model
│   └── AppLanguage.kt                — supported UI languages
│
├── system/
│   └── SystemControllerHub.kt        — device control (Wi-Fi, BT, volume…)
│
└── ui/
    ├── assistant/
    │   ├── AssistantScreen.kt        — main voice screen
    │   ├── AssistantViewModel.kt     — state machine: Idle/Listening/Thinking/Speaking
    │   ├── GreetingHero.kt           — animated greeting on idle screen
    │   └── GreetingPhrases.kt        — time-aware greeting phrases
    │
    ├── settings/                     — all settings screens
    ├── history/                      — conversation history
    ├── onboarding/                   — first-launch flow
    ├── components/                   — shared UI components
    ├── icons/AmaliaIcons.kt          — custom SVG icon set
    └── theme/
        ├── CircadianEngine.kt        — time-based color temperature
        └── Type.kt                   — typography
```



---

## 🏗️ Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Kotlin | 2.2 |
| UI Framework | Jetpack Compose | BOM 2026.08.00 |
| Design System | Material 3 | latest |
| Architecture | MVVM + StateFlow | — |
| Async | Kotlin Coroutines + Flow | — |
| Persistence | DataStore Preferences | — |
| HTTP | OkHttp | 4.x |
| STT | Groq Whisper Large v3 Turbo | — |
| VAD | Silero VAD (android-vad) | 2.0.9 |
| LLM | Qwen3-27B via Groq LPU | — |
| TTS | Fish Audio drama-3-preview | — |
| Audio | Android AudioRecord + AudioTrack | — |
| Build | Gradle | 9.7.1 |
| AGP | Android Gradle Plugin | 9.4.0 |
| Min SDK | Android 8.0 | API 26 |
| Target SDK | Android 16 | API 36 |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Meerkat or newer
- JDK 17+
- Android device or emulator running Android 8.0+

### Step 1 — Clone

```bash
git clone https://github.com/kurumi-mProject/amalia.git
cd amalia
```

### Step 2 — API Keys

You need two API keys:

**Groq** (STT + LLM) — [console.groq.com](https://console.groq.com)
- Free tier: 14,400 STT seconds/day + generous LLM tokens
- One key handles both Whisper and Qwen3

**Fish Audio** (TTS) — [fish.audio](https://fish.audio)
- Free tier: 6M characters/month via Startup Program
- Required for Amalia's voice

Create `local.properties` in the project root:

```properties
GROQ_API_KEY=gsk_your_key_here
FISH_AUDIO_API_KEY=sk-fish-your_key_here
```

> ⚠️ `local.properties` is in `.gitignore` — your keys will never be committed.

You can also enter keys directly in the app: **Settings → API & Models**.

### Step 3 — Build

```bash
./gradlew assembleDebug
```

Or open in Android Studio and press **Run**.

### Step 4 — Permissions

On first launch, Amalia will ask for:
- **Microphone** — required for voice input
- **Modify audio settings** — required for volume control tool

---

## 🔧 Configuration

All settings are available in the app UI. No need to recompile.

| Setting | Where |
|---|---|
| Groq API key | Settings → API & Models |
| Fish Audio key | Settings → API & Models |
| STT language | Settings → API & Models |
| LLM model | Settings → API & Models |
| TTS voice | Settings → Voice |
| Waveform style | Settings → Appearance → Waveform |
| Circadian theme | Settings → Appearance |

---

## 🏗️ Building for Release

The project includes a full CI/CD pipeline via GitHub Actions.

### Debug build
Triggered on every push. Downloads `amalia-debug.apk` from Actions artifacts.

### Release build (AAB + APK)
Triggered by pushing a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

This will:
1. Build a signed AAB for Google Play
2. Build a signed APK for sideloading
3. Create a GitHub Release with both files attached

Required GitHub Secrets for release builds:

| Secret | Description |
|---|---|
| `GROQ_API_KEY` | Groq API key |
| `FISH_AUDIO_API_KEY` | Fish Audio API key |
| `AMALIA_KEYSTORE_BASE64` | Base64-encoded `.jks` keystore |
| `AMALIA_KEYSTORE_PASSWORD` | Keystore password |
| `AMALIA_KEY_ALIAS` | Key alias inside keystore |
| `AMALIA_KEY_PASSWORD` | Key password |

---

## 🐛 Debugging

All pipeline steps emit structured logs under the `AMALIA` tag:

```bash
adb logcat -s AMALIA
```

Module prefixes:

| Prefix | Module |
|---|---|
| `[STT]` | Voice recognition |
| `[PCM]` | Audio streaming |
| `[ORC]` | Pipeline orchestrator |
| `[VM]` | ViewModel state machine |
| `[TTS]` | Fish Audio TTS |
| `[TOOL]` | Device tool calls |

---

## 🙏 Sponsors

<div align="center">

### Voice powered by

<a href="https://fish.audio">
  <img src="https://img.shields.io/badge/Fish%20Audio-TTS%20Sponsor-FF6B35?style=for-the-badge&logoColor=white" alt="Fish Audio"/>
</a>

**[Fish Audio](https://fish.audio)** sponsors the voice synthesis technology behind Amalia.

Their `drama-3-preview` model delivers strikingly natural, expressive speech with PCM streaming at 24kHz and first-chunk latency under 500ms. Without Fish Audio's Startup Program, Amalia wouldn't sound the way she does.

If you're building voice applications — Fish Audio is where to start.

</div>

---

## 📜 Open Source & License

Amalia is released under the **MIT License**.

```
MIT License — Copyright (c) 2026 kurumi-mProject
```

**What this means:**
- ✅ You can use this code freely — personal, commercial, anywhere
- ✅ You can modify it however you want
- ✅ You can distribute your modified version
- ✅ You can include it in your own projects
- ✅ You can sell products built on top of it

**One ask:**
> Please keep a reference to the original author — **kurumi-mProject** — in your README, about screen, or documentation. It's not required by the license, but it's appreciated.

See [LICENSE](../LICENSE) for the full text.

---

## 🤝 Contributing

Contributions are welcome. If you find a bug, have a feature idea, or want to improve something — open an issue or a pull request.

There are no formal contribution guidelines yet. Be reasonable, keep the code style consistent, and describe what your PR does.

---

## 📬 Contact

GitHub: [@kurumi-mProject](https://github.com/kurumi-mProject)

---

<div align="center">

*Built with care · Powered by [Groq](https://groq.com) + [Fish Audio](https://fish.audio)*

</div>
