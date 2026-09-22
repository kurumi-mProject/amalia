<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Amalia"/>

# Amalia

### AI Voice Assistant for Android

*Feels alive. Responds instantly. Lives on your phone.*

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

[English](#english) · [Русский](#русский) · [中文](#中文) · [日本語](#日本語) · [Deutsch](#deutsch) · [Español](#español)

---

<img src="icon-preview/1_dark_sheet.png" width="600" alt="Amalia UI Preview"/>

</div>

---

## English

Amalia is an open-source AI voice assistant for Android built with Kotlin and Jetpack Compose. She has a personality — 18 years old, confident, and direct. Powered by cutting-edge AI models, Amalia responds to voice commands in under a second.

### ✨ Features

- 🎙️ **Instant voice recognition** — Groq Whisper Large v3 Turbo with on-device Silero VAD
- 🧠 **Smart AI brain** — Qwen3 running on Groq LPU, ~150ms first token
- 🔊 **Natural voice** — Fish Audio `drama-3-preview` model, PCM streaming at 24kHz
- 🌗 **Circadian UI** — interface adapts color temperature to time of day
- 🛠️ **Device control** — Wi-Fi, Bluetooth, brightness, volume, flashlight, alarms, timers
- 📱 **Works offline for VAD** — speech detection happens entirely on-device
- 🔒 **Your keys, your data** — API keys stored locally, nothing sent to third parties

### 🏗️ Tech Stack

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

### 🚀 Getting Started

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

### 🙏 Sponsors

<div align="center">

#### TTS powered by

[![Fish Audio](https://img.shields.io/badge/Fish%20Audio-Sponsor-orange?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0id2hpdGUiIGQ9Ik0xMiAyQzYuNDggMiAyIDYuNDggMiAxMnM0LjQ4IDEwIDEwIDEwIDEwLTQuNDggMTAtMTBTMTcuNTIgMiAxMiAyem0tMiAxNGwtNC00IDEuNDEtMS40MUwxMCAxMy4xN2w2LjU5LTYuNTlMMTggOGwtOCA4eiIvPjwvc3ZnPg==)](https://fish.audio)

**[Fish Audio](https://fish.audio)** provides ultra-realistic voice synthesis technology that powers Amalia's natural speech. Their `drama-3-preview` model delivers PCM audio streaming with under 500ms latency.

</div>

---

## Русский

Amalia — голосовой ИИ-ассистент с открытым исходным кодом для Android на Kotlin и Jetpack Compose. У неё есть характер — 18 лет, уверенная и прямая. На современных AI-моделях Амалия отвечает на голосовые команды менее чем за секунду.

### ✨ Возможности

- 🎙️ **Мгновенное распознавание** — Groq Whisper Large v3 Turbo + Silero VAD на устройстве
- 🧠 **Умный мозг** — Qwen3 на Groq LPU, ~150мс до первого токена
- 🔊 **Живой голос** — Fish Audio `drama-3-preview`, PCM-стриминг 24кГц
- 🌗 **Циркадный UI** — интерфейс меняет цветовую температуру по времени суток
- 🛠️ **Управление устройством** — Wi-Fi, Bluetooth, яркость, громкость, фонарик, будильники
- 📱 **VAD офлайн** — детекция речи полностью на устройстве
- 🔒 **Ваши ключи — ваши данные** — API-ключи хранятся локально

### 🙏 Спонсоры

#### Голос Амалии — это

**[Fish Audio](https://fish.audio)** — спонсор голосового синтеза. Модель `drama-3-preview` обеспечивает ультрареалистичную речь с задержкой менее 500мс.

---

## 中文

Amalia 是一款基于 Kotlin 和 Jetpack Compose 构建的开源 Android AI 语音助手。她有自己的个性——18岁，自信，直接。基于最先进的 AI 模型，Amalia 能在不到一秒内响应语音指令。

### ✨ 功能特色

- 🎙️ **即时语音识别** — Groq Whisper Large v3 Turbo + 设备端 Silero VAD
- 🧠 **智能 AI 大脑** — Qwen3 运行在 Groq LPU，首个 token 约 150ms
- 🔊 **自然语音** — Fish Audio `drama-3-preview`，24kHz PCM 流式传输
- 🌗 **昼夜节律 UI** — 界面颜色温度随时间自动调整
- 🛠️ **设备控制** — Wi-Fi、蓝牙、亮度、音量、手电筒、闹钟、定时器
- 🔒 **数据安全** — API 密钥本地存储，不发送给第三方

### 🙏 赞助商

**[Fish Audio](https://fish.audio)** 提供超逼真的语音合成技术，驱动 Amalia 的自然语音。

---

## 日本語

Amalia は Kotlin と Jetpack Compose で構築されたオープンソースの Android AI 音声アシスタントです。18歳、自信があり直接的な個性を持ちます。最先進の AI モデルにより、1秒以内に音声コマンドに応答します。

### ✨ 機能

- 🎙️ **即座の音声認識** — Groq Whisper Large v3 Turbo + デバイス上の Silero VAD
- 🧠 **スマート AI** — Groq LPU 上の Qwen3、最初のトークンまで約 150ms
- 🔊 **自然な音声** — Fish Audio `drama-3-preview`、24kHz PCM ストリーミング
- 🌗 **サーカディアン UI** — 時刻に応じて色温度が変化
- 🛠️ **デバイス制御** — Wi-Fi、Bluetooth、輝度、音量、懐中電灯、アラーム
- 🔒 **プライバシー保護** — API キーはローカルに保存

### 🙏 スポンサー

**[Fish Audio](https://fish.audio)** — Amalia の音声合成技術を提供するスポンサーです。

---

## Deutsch

Amalia ist ein Open-Source KI-Sprachassistent für Android, gebaut mit Kotlin und Jetpack Compose. Sie hat Persönlichkeit — 18 Jahre alt, selbstbewusst und direkt. Angetrieben von modernsten KI-Modellen antwortet Amalia auf Sprachbefehle in unter einer Sekunde.

### ✨ Funktionen

- 🎙️ **Sofortige Spracherkennung** — Groq Whisper Large v3 Turbo + Silero VAD on-device
- 🧠 **Smarte KI** — Qwen3 auf Groq LPU, ~150ms bis zum ersten Token
- 🔊 **Natürliche Stimme** — Fish Audio `drama-3-preview`, 24kHz PCM-Streaming
- 🌗 **Zirkadianes UI** — Farbtemperatur passt sich der Tageszeit an
- 🛠️ **Gerätesteuerung** — WLAN, Bluetooth, Helligkeit, Lautstärke, Taschenlampe
- 🔒 **Datenschutz** — API-Schlüssel lokal gespeichert

### 🙏 Sponsoren

**[Fish Audio](https://fish.audio)** — Sponsor der Sprachsynthese-Technologie von Amalia.

---

## Español

Amalia es un asistente de voz con IA de código abierto para Android, construido con Kotlin y Jetpack Compose. Tiene personalidad — 18 años, segura y directa. Con los modelos de IA más avanzados, Amalia responde a comandos de voz en menos de un segundo.

### ✨ Características

- 🎙️ **Reconocimiento de voz instantáneo** — Groq Whisper Large v3 Turbo + Silero VAD en dispositivo
- 🧠 **IA inteligente** — Qwen3 en Groq LPU, ~150ms hasta el primer token
- 🔊 **Voz natural** — Fish Audio `drama-3-preview`, streaming PCM a 24kHz
- 🌗 **UI circadiana** — la temperatura de color se adapta a la hora del día
- 🛠️ **Control del dispositivo** — Wi-Fi, Bluetooth, brillo, volumen, linterna, alarmas
- 🔒 **Privacidad** — las claves API se almacenan localmente

### 🙏 Patrocinadores

**[Fish Audio](https://fish.audio)** — patrocinador de la tecnología de síntesis de voz de Amalia.

---

<div align="center">

## Pipeline

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

---

## License

Apache 2.0 — see [LICENSE](LICENSE)

---

*Made with ❤️ · TTS sponsored by [Fish Audio](https://fish.audio)*

</div>
