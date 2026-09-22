<div align="center">

<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Amalia"/>

# Amalia

### KI-Sprachassistent für Android

*Lebendig. Antwortet sofort. Lebt auf deinem Handy.*

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

[🌍 Andere Sprachen](../README.md)

</div>

---

Amalia ist ein Open-Source KI-Sprachassistent für Android, gebaut mit Kotlin und Jetpack Compose. Sie hat eine Persönlichkeit — 18 Jahre alt, selbstbewusst und direkt. Angetrieben von modernsten KI-Modellen antwortet Amalia auf Sprachbefehle in unter einer Sekunde.

## ✨ Funktionen

- 🎙️ **Sofortige Spracherkennung** — Groq Whisper Large v3 Turbo + Silero VAD auf dem Gerät
- 🧠 **Smarte KI** — Qwen3 auf Groq LPU, ~150ms bis zum ersten Token
- 🔊 **Natürliche Stimme** — Fish Audio `drama-3-preview`, 24kHz PCM-Streaming
- 🌗 **Zirkadianes UI** — Farbtemperatur passt sich automatisch der Tageszeit an
- 🛠️ **Gerätesteuerung** — WLAN, Bluetooth, Helligkeit, Lautstärke, Taschenlampe, Wecker, Timer
- 📱 **Offline-VAD** — Spracherkennung läuft komplett auf dem Gerät, kein Internet nötig
- 🔒 **Datenschutz** — API-Schlüssel lokal gespeichert, nichts an Dritte gesendet

## 🏗️ Tech-Stack

| Ebene | Technologie |
|---|---|
| Sprache | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Architektur | MVVM + StateFlow |
| STT | Groq Whisper Large v3 Turbo |
| VAD | Silero VAD (offline, auf dem Gerät) |
| LLM | Qwen3 über Groq LPU |
| TTS | Fish Audio `drama-3-preview` |
| Min SDK | Android 8.0 (API 26) |

## ⚡ Pipeline

```
🎙️ Mikrofon
      ↓
 Silero VAD (auf dem Gerät)
      ↓  600ms Stille → Sprache beendet
 Groq Whisper v3 Turbo  ~220ms
      ↓
 Qwen3 on Groq LPU      ~150ms
      ↓
 Fish Audio drama-3     ~500ms erstes Chunk
      ↓
🔊 AudioTrack PCM 24kHz
```

**Gesamtlatenz: ~900ms** vom Ende der Sprache bis zum ersten Ton

## 🚀 Schnellstart

1. Repository klonen
2. API-Schlüssel besorgen:
   - [Groq](https://console.groq.com) — kostenloses Kontingent verfügbar
   - [Fish Audio](https://fish.audio) — kostenloses Kontingent verfügbar
3. In `local.properties` eintragen:
```
GROQ_API_KEY=dein_schlüssel
FISH_AUDIO_API_KEY=dein_schlüssel
```
4. Mit Android Studio oder `./gradlew assembleDebug` bauen

## 🙏 Sponsoren

<div align="center">

#### Sprachsynthese unterstützt von

**[Fish Audio](https://fish.audio)** — Sponsor der Sprachsynthese-Technologie von Amalia. Das `drama-3-preview`-Modell liefert ultrarealistisches PCM-Audio-Streaming mit unter 500ms Latenz.

[![Fish Audio](https://img.shields.io/badge/Fish%20Audio-TTS%20Sponsor-orange?style=for-the-badge)](https://fish.audio)

</div>

## 📄 Lizenz

Apache 2.0 — siehe [LICENSE](../LICENSE)
