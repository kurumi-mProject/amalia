# Амалия — автономный голосовой ассистент для Android

> Этап: **скелет → MVP**. Сейчас в проекте: голосовой экран с живым орбом,
> конечный автомат состояний (слушаю → думаю → говорю) и демо-режим «эхо»,
> который прогоняет полный цикл без внешних API — чтобы проверить UX.
> Стриминговые шлюзы (STT → LLM → TTS) подключаются следующим этапом.

## Стек

| Компонент | Версия |
|---|---|
| Gradle | 9.7.1 |
| Android Gradle Plugin | 9.4.0 (built-in Kotlin 2.2.10) |
| Compose BOM | 2026.08.00 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| JDK | 17 |

Важно: AGP 9 использует **built-in Kotlin** — плагин `org.jetbrains.kotlin.android`
больше не нужен. Для Compose подключается только
`org.jetbrains.kotlin.plugin.compose` версии, совпадающей со встроенным Kotlin.

## Сборка в облаке

Проект готов к сборке через GitHub Actions (см. `.github/workflows/build.yml`):
залил в репозиторий → workflow собирает `app-debug.apk` и отдаёт артефактом.

Локально:

```bash
export ANDROID_HOME=/path/to/sdk   # CI делает это сам
./gradlew assembleDebug
```

JDK должен быть **17+** (Gradle 9 и AGP 9 требуют 17).

## Структура

```
app/src/main/java/com/my/amali/
├── MainActivity.kt              # точка входа, edge-to-edge
└── ui/
    ├── assistant/
    │   ├── AssistantScreen.kt   # главный экран (орб + транскрипт + микрофон)
    │   ├── AssistantViewModel.kt# конечный автомат IDLE→LISTENING→THINKING→SPEAKING
    │   └── OrbIndicator.kt      # живой орб-индикатор состояний
    └── theme/
        ├── Color.kt             # палитра «Аурора»
        ├── Theme.kt             # тёмная тема + Material You
        └── Type.kt              # типографика
```
