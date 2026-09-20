#!/usr/bin/env python3
"""
Аудит геометрии семьи иконок Амалии.

## Зачем это существует

Иконки рисуются вручную координатами, а глазом их проверить нельзя —
поэтому проверка делается числами. Главный риск ручной отрисовки: часть
глифа уезжает за рабочее поле (тогда в 21dp он выглядит обрезанным) или
глиф оказывается слишком мелким относительно соседей в навбаре.

## Что проверяется

1. **Поле.** Все абсолютные координаты и конечные точки относительных
   команд обязаны лежать в [1.7, 22.3]: половина штриха (0.85) плюс
   такой же воздух. Иначе штрих вылезает за край сетки.
2. **Плотность.** Длина всех штрихов. Глиф, у которого она вдвое меньше
   соседей, будет казаться бледнее в навбаре — это визуальный дефект,
   который числа ловят, а случайный взгляд нет.
3. **Центр.** Центр габаритной рамки. Если он уехал больше чем на 1dp
   от (12, 12), глиф висит в ячейке криво.

Запуск:  python3 tools/AuditIcons.py
Код возврата: 0 — всё в порядке, 1 — есть отклонения.
"""

import math
import os
import re
import sys

RAW = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "res", "raw",
)

FIELD = 1.7
CENTER = 12.0
CENTER_TOLERANCE = 1.0

NUM = r"-?\d+(?:\.\d+)?"


def parse_paths(body: str):
    """Возвращает список (points, length) для каждого <path>."""
    out = []
    for d in re.findall(r'd="([^"]+)"', body):
        points = []
        length = 0.0
        x = y = 0.0
        # Абсолютные и относительные команды; поддерживаются M L H V A Z.
        for cmd, args in re.findall(rf"([MLHVAZmlhvaz])([^MLHVAZmlhvaz]*)", d):
            vals = [float(v) for v in re.findall(NUM, args)]
            if cmd in "ML":
                for i in range(0, len(vals) - 1, 2):
                    dx, dy = vals[i], vals[i + 1]
                    nx, ny = dx, dy
                    length += math.hypot(nx - x, ny - y)
                    x, y = nx, ny
                    points.append((x, y))
            elif cmd in "ml":
                for i in range(0, len(vals) - 1, 2):
                    nx, ny = x + vals[i], y + vals[i + 1]
                    length += math.hypot(vals[i], vals[i + 1])
                    x, y = nx, ny
                    points.append((x, y))
            elif cmd == "H":
                for v in vals:
                    length += abs(v - x)
                    x = v
                    points.append((x, y))
            elif cmd == "h":
                for v in vals:
                    length += abs(v)
                    x += v
                    points.append((x, y))
            elif cmd == "V":
                for v in vals:
                    length += abs(v - y)
                    y = v
                    points.append((x, y))
            elif cmd == "v":
                for v in vals:
                    length += abs(v)
                    y += v
                    points.append((x, y))
            elif cmd.lower() == "a" and len(vals) >= 7:
                for i in range(0, len(vals) - 6, 7):
                    dx, dy = vals[i + 5], vals[i + 6]
                    nx, ny = (x + dx, y + dy) if cmd == "a" else (dx, dy)
                    length += math.hypot(nx - x, ny - y)
                    x, y = nx, ny
                    points.append((x, y))
        out.append((points, length))
    return out


def main() -> int:
    problems = []
    rows = []

    for name in sorted(os.listdir(RAW)):
        if not name.endswith(".svg"):
            continue
        body = open(os.path.join(RAW, name), encoding="utf-8").read()
        paths = parse_paths(body)
        if not paths:
            problems.append(f"{name}: не найдено ни одного пути")
            continue

        pts = [p for points, _ in paths for p in points]
        length = sum(l for _, l in paths)
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        lo = min(min(xs), min(ys))
        hi = max(max(xs), max(ys))
        cx = (min(xs) + max(xs)) / 2
        cy = (min(ys) + max(ys)) / 2

        field_ok = lo >= FIELD - 0.05 and hi <= 24.0 - FIELD + 0.05
        center_ok = abs(cx - CENTER) <= CENTER_TOLERANCE and abs(cy - CENTER) <= CENTER_TOLERANCE

        rows.append((name, lo, hi, length, cx, cy, field_ok, center_ok))
        if not field_ok:
            problems.append(f"{name}: выходит за поле — край {lo:.2f}/{hi:.2f}")
        if not center_ok:
            problems.append(f"{name}: центр уехал — ({cx:.2f}, {cy:.2f})")

    print(f"{'файл':<28}{'поле':>14}{'длина':>9}{'центр':>16}")
    for name, lo, hi, length, cx, cy, fok, cok in rows:
        print(
            f"{name:<28}{lo:>6.2f}..{hi:<6.2f}{length:>7.1f}"
            f"{cx:>8.2f},{cy:<7.2f}"
            f"  {'OK' if fok and cok else 'BAD'}"
        )

    if rows:
        lengths = [r[3] for r in rows]
        print(
            f"\nдлина штрихов: мин {min(lengths):.1f}, "
            f"макс {max(lengths):.1f}, "
            f"разброс {max(lengths) / max(min(lengths), 0.1):.1f}x"
        )
        if max(lengths) / max(min(lengths), 0.1) > 3.0:
            problems.append("разброс плотности больше 3x — глифы будут разной «силы»")

    if problems:
        print("\nПРОБЛЕМЫ:")
        for p in problems:
            print("  -", p)
        return 1

    print("\nВсё в порядке: поле, центры и плотность сбалансированы.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
