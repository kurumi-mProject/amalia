<div align="center">

<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Amalia"/>

# Амалия

### ИИ-голосовой ассистент для Android

*Живая. Отвечает мгновенно. Живёт на твоём телефоне.*

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

[🌍 Другие языки](../README.md)

</div>

---

Амалия — голосовой ИИ-ассистент с открытым исходным кодом для Android на Kotlin и Jetpack Compose. У неё есть характер — 18 лет, уверенная и прямая. На современных AI-моделях отвечает на голосовые команды менее чем за секунду.

## ✨ Возможности

- 🎙️ **Мгновенное распознавание** — Groq Whisper Large v3 Turbo + Silero VAD на устройстве
- 🧠 **Умный мозг** — Qwen3 на Groq LPU, ~150мс до первого токена
- 🔊 **Живой голос** — Fish Audio `drama-3-preview`, PCM-стриминг 24кГц
- 🌗 **Циркадный UI** — интерфейс меняет цветовую температуру по времени суток
- 🛠️ **Управление устройством** — Wi-Fi, Bluetooth, яркость, громкость, фонарик, будильники, таймеры
- 📱 **VAD офлайн** — детекция речи полностью на устройстве, без интернета
- 🔒 **Ваши ключи — ваши данные** — API-ключи хранятся локально

## 🏗️ Стек

| Слой | Технология |
|---|---|
| Язык | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Архитектура | MVVM + StateFlow |
| STT | Groq Whisper Large v3 Turbo |
| VAD | Silero VAD (офлайн, на устройстве) |
| LLM | Qwen3 через Groq LPU |
| TTS | Fish Audio `drama-3-preview` |
| Min SDK | Android 8.0 (API 26) |

## ⚡ Пайплайн

```
🎙️ Микрофон
      ↓
 Silero VAD (на устройстве)
      ↓  600мс тишины → конец фразы
 Groq Whisper v3 Turbo  ~220мс
      ↓
 Qwen3 на Groq LPU      ~150мс
      ↓
 Fish Audio drama-3     ~500мс первый чанк
      ↓
🔊 AudioTrack PCM 24кГц
```

**Полная задержка: ~900мс** от конца речи до первого звука

## 🚀 Запуск

1. Клонировать репозиторий
2. Получить API-ключи:
   - [Groq](https://console.groq.com) — есть бесплатный тариф
   - [Fish Audio](https://fish.audio) — есть бесплатный тариф
3. Добавить в `local.properties`:
```
GROQ_API_KEY=твой_ключ
FISH_AUDIO_API_KEY=твой_ключ
```
4. Собрать в Android Studio или `./gradlew assembleDebug`

## 🙏 Спонсоры

<div align="center">

#### Голос Амалии — это

**[Fish Audio](https://fish.audio)** — спонсор голосового синтеза. Модель `drama-3-preview` обеспечивает ультрареалистичную речь с задержкой менее 500мс.

[![Fish Audio](https://img.shields.io/badge/Fish%20Audio-Спонсор%20TTS-orange?style=for-the-badge)](https://fish.audio)

</div>

## 📄 Лицензия

Apache 2.0 — см. [LICENSE](../LICENSE)
