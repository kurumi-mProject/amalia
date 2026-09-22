<div align="center">

<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Amalia"/>

# Amalia

### Android AI 语音助手

*有生命力。即时响应。住在你的手机里。*

[![Build](https://github.com/kurumi-mProject/amalia/actions/workflows/build.yml/badge.svg)](https://github.com/kurumi-mProject/amalia/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

[🌍 其他语言](../README.md)

</div>

---

Amalia 是一款基于 Kotlin 和 Jetpack Compose 构建的开源 Android AI 语音助手。她有自己的个性——18岁，自信，直接。基于最先进的 AI 模型，能在不到一秒内响应语音指令。

## ✨ 功能特色

- 🎙️ **即时语音识别** — Groq Whisper Large v3 Turbo + 设备端 Silero VAD
- 🧠 **智能 AI 大脑** — Qwen3 运行在 Groq LPU，首个 token 约 150ms
- 🔊 **自然语音** — Fish Audio `drama-3-preview`，24kHz PCM 流式传输
- 🌗 **昼夜节律 UI** — 界面颜色温度随时间自动调整
- 🛠️ **设备控制** — Wi-Fi、蓝牙、亮度、音量、手电筒、闹钟、定时器
- 📱 **离线 VAD** — 语音检测完全在设备上运行，无需网络
- 🔒 **数据安全** — API 密钥本地存储，不发送给第三方

## 🏗️ 技术栈

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + StateFlow |
| STT | Groq Whisper Large v3 Turbo |
| VAD | Silero VAD（离线，设备端） |
| LLM | Qwen3（Groq LPU） |
| TTS | Fish Audio `drama-3-preview` |
| 最低 SDK | Android 8.0（API 26） |

## ⚡ 处理流程

```
🎙️ 麦克风
      ↓
 Silero VAD（设备端）
      ↓  600ms 静音 → 说话结束
 Groq Whisper v3 Turbo  ~220ms
      ↓
 Qwen3 on Groq LPU      ~150ms
      ↓
 Fish Audio drama-3     ~500ms 首个音频块
      ↓
🔊 AudioTrack PCM 24kHz
```

**总延迟：~900ms**（从说话结束到第一个音频）

## 🚀 快速开始

1. 克隆仓库
2. 获取 API 密钥：
   - [Groq](https://console.groq.com) — 有免费套餐
   - [Fish Audio](https://fish.audio) — 有免费套餐
3. 在 `local.properties` 中添加：
```
GROQ_API_KEY=你的密钥
FISH_AUDIO_API_KEY=你的密钥
```
4. 用 Android Studio 构建或运行 `./gradlew assembleDebug`

## 🙏 赞助商

<div align="center">

#### 语音合成由以下提供支持

**[Fish Audio](https://fish.audio)** 提供超逼真的语音合成技术，驱动 Amalia 的自然语音。`drama-3-preview` 模型延迟低于 500ms。

[![Fish Audio](https://img.shields.io/badge/Fish%20Audio-TTS%20赞助商-orange?style=for-the-badge)](https://fish.audio)

</div>

## 📄 许可证

Apache 2.0 — 参见 [LICENSE](../LICENSE)
