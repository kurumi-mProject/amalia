#!/usr/bin/env python3
"""Добавляет три новые строки главного экрана во все локали Амалии.

Зачем скриптом: в проекте девять файлов strings.xml, и ручная правка
гарантированно разъезжается — где-то строка есть, где-то нет, и экран
показывает английский текст в арабской локали.

Строки:
  assistant_ready              — состояние покоя («Готова»), подпись над орбом;
  assistant_conversations_count — счётчик разговоров в шапке (нейтральная
                                  формулировка «разговоров: N», чтобы не
                                  заводить plurals в девяти языках);
  assistant_welcome_chips      — заголовок ленты подсказок в приветствии.

Скрипт идемпотентен: повторный запуск ничего не дублирует.
"""

import io
import os
import re

BASE = "app/src/main/res"

# locale -> {key: value}
T = {
    "values": {
        "assistant_ready": "Готова",
        "assistant_conversations_count": "разговоров: %1$d",
        "assistant_welcome_chips": "Попробуй сказать",
    },
    "values-en": {
        "assistant_ready": "Ready",
        "assistant_conversations_count": "conversations: %1$d",
        "assistant_welcome_chips": "Try saying",
    },
    "values-es": {
        "assistant_ready": "Lista",
        "assistant_conversations_count": "conversaciones: %1$d",
        "assistant_welcome_chips": "Prueba a decir",
    },
    "values-de": {
        "assistant_ready": "Bereit",
        "assistant_conversations_count": "Gespräche: %1$d",
        "assistant_welcome_chips": "Sag zum Beispiel",
    },
    "values-fr": {
        "assistant_ready": "Prête",
        "assistant_conversations_count": "conversations : %1$d",
        "assistant_welcome_chips": "Essaie de dire",
    },
    "values-hi": {
        "assistant_ready": "तैयार",
        "assistant_conversations_count": "बातचीत: %1$d",
        "assistant_welcome_chips": "कहकर देखिए",
    },
    "values-ja": {
        "assistant_ready": "準備完了",
        "assistant_conversations_count": "会話: %1$d",
        "assistant_welcome_chips": "話しかけてみて",
    },
    "values-zh": {
        "assistant_ready": "准备好了",
        "assistant_conversations_count": "对话: %1$d",
        "assistant_welcome_chips": "试着说",
    },
    "values-ar": {
        "assistant_ready": "جاهزة",
        "assistant_conversations_count": "المحادثات: %1$d",
        "assistant_welcome_chips": "جرّب أن تقول",
    },
}

# Куда вставлять: сразу после этой строки, чтобы новые ключи лежали
# рядом с остальными строками ассистента, а не в конце файла.
ANCHOR = "assistant_welcome_desc"


def add_strings(path: str, strings: dict) -> list:
    """Вставляет отсутствующие строки в XML. Возвращает список добавленных."""
    with io.open(path, encoding="utf-8") as fh:
        xml = fh.read()

    added = []
    for key, value in strings.items():
        if re.search(r'name="%s"' % re.escape(key), xml):
            continue
        line = '    <string name="%s">%s</string>\n' % (key, value)
        anchor_match = re.search(r'^.*name="%s".*$' % re.escape(ANCHOR), xml, re.M)
        if anchor_match:
            insert_at = anchor_match.end() + 1
            xml = xml[:insert_at] + line + xml[insert_at:]
        else:
            close = xml.rindex("</resources>")
            xml = xml[:close] + line + xml[close:]
        added.append(key)

    if added:
        with io.open(path, "w", encoding="utf-8") as fh:
            fh.write(xml)
    return added


def main() -> None:
    total = 0
    for locale, strings in T.items():
        path = os.path.join(BASE, locale, "strings.xml")
        if not os.path.exists(path):
            print("SKIP (нет файла): %s" % path)
            continue
        added = add_strings(path, strings)
        total += len(added)
        print("%-12s добавлено: %s" % (locale, ", ".join(added) if added else "— уже есть"))
    print("Всего новых строк: %d" % total)


if __name__ == "__main__":
    main()
