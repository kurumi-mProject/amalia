<div align="center">

<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Amalia"/>

# Amalia

### AI Voice Assistant for Android

*Feels alive. Responds instantly. Lives on your phone.*

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

[🌍 Other languages](../README.md)

</div>

---

Amalia is an open-source AI voice assistant for Android built with Kotlin and Jetpack Compose. She has a personality — 18 years old, confident, and direct. Powered by cutting-edge AI models, Amalia responds to voice commands in under a second.

## ✨ Features

- 🎙️ **Instant voice recognition** — Groq Whisper Large v3 Turbo with on-device Silero VAD
- 🧠 **Smart AI brain** — Qwen3 running on Groq LPU, ~150ms first token
- 🔊 **Natural voice** — Fish Audio `drama-3-preview` model, PCM streaming at 24kHz
- 🌗 **Circadian UI** — interface adapts color temperature to time of day
- 🛠️ **Device control** — Wi-Fi, Bluetooth, brightness, volume, flashlight, alarms, timers
- 📱 **Works offline for VAD** — speech detection happens entirely on-device
- 🔒 **Your keys, your data** — API keys stored locally, nothing sent to third parties

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + StateFlow |
| STT | Groq Whisper Large v3 Turbo |
| VAD | Silero VAD (on-device, offline) |
| LLM | Qwen3 via Groq LPU |
| TTS | Fish Audio `drama-3-preview` |
| Min SDK | Android 8.0 (API 26) |

## ⚡ Pipeline

```
🎙️ Microphone
      ↓
 Silero VAD (on-device)
      ↓  600ms silence → end of speech
 Groq Whisper v3 Turbo  ~220ms
      ↓
 Qwen3 on Groq LPU      ~150ms
      ↓
 Fish Audio drama-3     ~500ms first chunk
      ↓
🔊 AudioTrack PCM 24kHz
```

**Total latency: ~900ms** from end of speech to first audio

## 🚀 Getting Started

1. Clone the repo
2. Get your API keys:
   - [Groq](https://console.groq.com) — free tier available
   - [Fish Audio](https://fish.audio) — free tier available
3. Add to `local.properties`:
```
GROQ_API_KEY=your_key
FISH_AUDIO_API_KEY=your_key
```
4. Build with Android Studio or `./gradlew assembleDebug`

## 🙏 Sponsors

<div align="center">

#### TTS powered by

**[Fish Audio](https://fish.audio)** provides ultra-realistic voice synthesis technology that powers Amalia's natural speech. Their `drama-3-preview` model delivers PCM audio streaming with under 500ms latency.

[![Fish Audio](https://img.shields.io/badge/Fish%20Audio-TTS%20Sponsor-orange?style=for-the-badge)](https://fish.audio)

</div>

## 📄 License

Apache 2.0 — see [LICENSE](../LICENSE)
