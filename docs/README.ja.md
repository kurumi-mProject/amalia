<div align="center">

<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Amalia"/>

# Amalia

### Android AI 音声アシスタント

*生きている。即座に応答する。あなたのスマホに宿る。*

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

[🌍 他の言語](../README.md)

</div>

---

Amalia は Kotlin と Jetpack Compose で構築されたオープンソースの Android AI 音声アシスタントです。18歳、自信があり直接的な個性を持ちます。最先進の AI モデルにより、1秒以内に音声コマンドに応答します。

## ✨ 機能

- 🎙️ **即座の音声認識** — Groq Whisper Large v3 Turbo + デバイス上の Silero VAD
- 🧠 **スマート AI** — Groq LPU 上の Qwen3、最初のトークンまで約 150ms
- 🔊 **自然な音声** — Fish Audio `drama-3-preview`、24kHz PCM ストリーミング
- 🌗 **サーカディアン UI** — 時刻に応じて色温度が自動的に変化
- 🛠️ **デバイス制御** — Wi-Fi、Bluetooth、輝度、音量、懐中電灯、アラーム、タイマー
- 📱 **オフライン VAD** — 音声検出はデバイス上で完結、ネット不要
- 🔒 **プライバシー保護** — API キーはローカルに保存、第三者に送信しない

## 🏗️ 技術スタック

| レイヤー | 技術 |
|---|---|
| 言語 | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| アーキテクチャ | MVVM + StateFlow |
| STT | Groq Whisper Large v3 Turbo |
| VAD | Silero VAD（オフライン・デバイス上） |
| LLM | Qwen3（Groq LPU） |
| TTS | Fish Audio `drama-3-preview` |
| 最低 SDK | Android 8.0（API 26） |

## ⚡ パイプライン

```
🎙️ マイク
      ↓
 Silero VAD（デバイス上）
      ↓  600ms の無音 → 発話終了
 Groq Whisper v3 Turbo  ~220ms
      ↓
 Qwen3 on Groq LPU      ~150ms
      ↓
 Fish Audio drama-3     ~500ms 最初のチャンク
      ↓
🔊 AudioTrack PCM 24kHz
```

**合計レイテンシ：~900ms**（発話終了から最初の音声まで）

## 🚀 はじめ方

1. リポジトリをクローン
2. API キーを取得：
   - [Groq](https://console.groq.com) — 無料枠あり
   - [Fish Audio](https://fish.audio) — 無料枠あり
3. `local.properties` に追加：
```
GROQ_API_KEY=あなたのキー
FISH_AUDIO_API_KEY=あなたのキー
```
4. Android Studio でビルド、または `./gradlew assembleDebug`

## 🙏 スポンサー

<div align="center">

#### 音声合成技術提供

**[Fish Audio](https://fish.audio)** — Amalia の自然な音声を支えるスポンサーです。`drama-3-preview` モデルは 500ms 未満のレイテンシで超リアルな音声を提供します。

[![Fish Audio](https://img.shields.io/badge/Fish%20Audio-TTSスポンサー-orange?style=for-the-badge)](https://fish.audio)

</div>

## 📄 ライセンス

Apache 2.0 — [LICENSE](../LICENSE) を参照
