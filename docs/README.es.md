<div align="center">

<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Amalia"/>

# Amalia

### Asistente de Voz IA para Android

*Con vida propia. Responde al instante. Vive en tu teléfono.*

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

[🌍 Otros idiomas](../README.md)

</div>

---

Amalia es un asistente de voz con IA de código abierto para Android, construido con Kotlin y Jetpack Compose. Tiene personalidad — 18 años, segura y directa. Con los modelos de IA más avanzados, responde a comandos de voz en menos de un segundo.

## ✨ Características

- 🎙️ **Reconocimiento de voz instantáneo** — Groq Whisper Large v3 Turbo + Silero VAD en dispositivo
- 🧠 **IA inteligente** — Qwen3 en Groq LPU, ~150ms hasta el primer token
- 🔊 **Voz natural** — Fish Audio `drama-3-preview`, streaming PCM a 24kHz
- 🌗 **UI circadiana** — la temperatura de color se adapta automáticamente a la hora del día
- 🛠️ **Control del dispositivo** — Wi-Fi, Bluetooth, brillo, volumen, linterna, alarmas, temporizadores
- 📱 **VAD sin conexión** — la detección de voz ocurre completamente en el dispositivo
- 🔒 **Privacidad total** — las claves API se almacenan localmente, nada se envía a terceros

## 🏗️ Stack Tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM + StateFlow |
| STT | Groq Whisper Large v3 Turbo |
| VAD | Silero VAD (sin conexión, en dispositivo) |
| LLM | Qwen3 via Groq LPU |
| TTS | Fish Audio `drama-3-preview` |
| SDK mínimo | Android 8.0 (API 26) |

## ⚡ Pipeline

```
🎙️ Micrófono
      ↓
 Silero VAD (en dispositivo)
      ↓  600ms de silencio → fin del habla
 Groq Whisper v3 Turbo  ~220ms
      ↓
 Qwen3 on Groq LPU      ~150ms
      ↓
 Fish Audio drama-3     ~500ms primer chunk
      ↓
🔊 AudioTrack PCM 24kHz
```

**Latencia total: ~900ms** desde el fin del habla hasta el primer audio

## 🚀 Cómo Empezar

1. Clona el repositorio
2. Obtén tus claves API:
   - [Groq](https://console.groq.com) — nivel gratuito disponible
   - [Fish Audio](https://fish.audio) — nivel gratuito disponible
3. Añade a `local.properties`:
```
GROQ_API_KEY=tu_clave
FISH_AUDIO_API_KEY=tu_clave
```
4. Compila con Android Studio o `./gradlew assembleDebug`

## 🙏 Patrocinadores

<div align="center">

#### Síntesis de voz patrocinada por

**[Fish Audio](https://fish.audio)** patrocina la tecnología de síntesis de voz de Amalia. Su modelo `drama-3-preview` ofrece streaming de audio PCM ultrarrealista con menos de 500ms de latencia.

[![Fish Audio](https://img.shields.io/badge/Fish%20Audio-Patrocinador%20TTS-orange?style=for-the-badge)](https://fish.audio)

</div>

## 📄 Licencia

Apache 2.0 — ver [LICENSE](../LICENSE)
