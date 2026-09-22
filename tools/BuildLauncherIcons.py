#!/usr/bin/env python3
"""
Сборка семьи лончер-иконок Амалии из исходного арта.

## Зачем это существует

Адаптивная иконка Android — это канва 108×108dp, у которой система
маскирует края под форму лончера (круг, скруглённый квадрат, капля).
Гарантированно видимая зона — круг 66dp в центре. Раньше весь арт лежал
в слое `background` на весь канвас, а `foreground` был пустым: маска
круга срезала края арта — «иконку видно не полностью».

Теперь наоборот: `background` — чистый чёрный (#000000, совпадает с фоном
арта), `foreground` — арт, вписанный содержимым в safe-круг. Светящиеся
элементы всегда внутри маски, чёрные углы арта растворяются в фоне.

Дополнительно:
  - чёрный фон арта переводится в прозрачность (мягкий люма-ключ):
    так на сплэше (#07070B) не проступает силуэт чёрного квадрата;
  - legacy-иконки (mipmap webp) — чёрный скруглённый квадрат с артом
    по центру: для лончеров без поддержки адаптивных иконок;
  - `drawable-nodpi/amalia_logo.png` — вырезанный по содержимому арт
    для онбординга (круг-орб на главном слайде).

Запуск:  python3 tools/BuildLauncherIcons.py <путь-к-арту.png>
"""

import os
import sys

from PIL import Image
import numpy as np

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(PROJECT, "app", "src", "main", "res")

# Плотности адаптивного канвы: 108dp → px.
ADAPTIVE_DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# Legacy-иконка 48dp → px.
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

CANVAS_DP = 108.0
SAFE_RADIUS_DP = 31.0     # содержимое вписывается в круг 62dp из гарантированных 66dp
LEGACY_CONTENT_FRAC = 0.31  # половина-диагональ контента от стороны legacy-иконки

# Мягкий люма-ключ: ниже LOW арт прозрачный, выше HIGH — полностью плотный.
KEY_LOW = 6.0
KEY_HIGH = 26.0


def content_bbox(rgb: np.ndarray):
    """Габариты светящегося содержимого: всё, что заметно чёрного фона."""
    lum = rgb.mean(axis=2)
    ys, xs = np.where(lum > 20)
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def keyed(art: Image.Image) -> Image.Image:
    """Чёрный фон → прозрачность, с плавной кромкой у свечения."""
    rgb = np.asarray(art.convert("RGB")).astype(np.float32)
    lum = rgb.mean(axis=2)
    alpha = np.clip((lum - KEY_LOW) / (KEY_HIGH - KEY_LOW), 0.0, 1.0)
    rgba = np.dstack([rgb, alpha * 255.0]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def fit_into_circle(art: Image.Image, bbox, radius_px: float):
    """Масштаб, при котором половина-диагональ bbox равна radius_px."""
    x0, y0, x1, y1 = bbox
    cx = (x0 + x1) / 2.0
    cy = (y0 + y1) / 2.0
    half_diag = np.hypot((x1 - x0) / 2.0, (y1 - y0) / 2.0)
    scale = radius_px / half_diag
    return scale, cx, cy


def build_adaptive(art_opaque: Image.Image, art_keyed: Image.Image, bbox):
    """foreground: арт в safe-круге; фон слоя — чёрный (задаётся цветом в XML)."""
    for density, scale_px in ADAPTIVE_DENSITIES.items():
        size = int(round(CANVAS_DP * scale_px))
        safe_radius = SAFE_RADIUS_DP * scale_px
        ratio, cx, cy = fit_into_circle(art_opaque, bbox, safe_radius)

        w = int(round(art_opaque.width * ratio))
        h = int(round(art_opaque.height * ratio))
        scaled = art_keyed.resize((w, h), Image.LANCZOS)

        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        # Центрируем не картинку, а СОДЕРЖИМОЕ: центр bbox попадает в центр канвы.
        px = size / 2.0 - cx * ratio
        py = size / 2.0 - cy * ratio
        canvas.paste(scaled, (int(round(px)), int(round(py))), scaled)

        directory = os.path.join(RES, f"drawable-{density}")
        os.makedirs(directory, exist_ok=True)
        canvas.save(os.path.join(directory, "ic_launcher_foreground.png"))
        print(f"drawable-{density}/ic_launcher_foreground.png  {size}x{size}")


def build_legacy(art_opaque: Image.Image, art_keyed: Image.Image, bbox):
    """webp-иконки для лончеров без адаптивных иконок: чёрный скруглённый квадрат."""
    for density, side in LEGACY_SIZES.items():
        scale_px = side / CANVAS_DP
        safe_radius = SAFE_RADIUS_DP * scale_px * 1.06  # legacy-маска мягче
        ratio, cx, cy = fit_into_circle(art_opaque, bbox, safe_radius)

        w = int(round(art_opaque.width * ratio))
        h = int(round(art_opaque.height * ratio))
        scaled = art_keyed.resize((w, h), Image.LANCZOS)

        canvas = Image.new("RGBA", (side, side), (0, 0, 0, 255))
        # Скругление — как у системных иконок (≈20% стороны).
        mask = Image.new("L", (side * 4, side * 4), 0)
        from PIL import ImageDraw

        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, side * 4, side * 4], radius=side * 4 * 0.2, fill=255
        )
        mask = mask.resize((side, side), Image.LANCZOS)
        canvas.putalpha(mask)

        px = side / 2.0 - cx * ratio
        py = side / 2.0 - cy * ratio
        canvas.paste(scaled, (int(round(px)), int(round(py))), scaled)

        directory = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(directory, exist_ok=True)
        canvas.save(os.path.join(directory, "ic_launcher.webp"), lossless=False, quality=92)
        canvas.save(os.path.join(directory, "ic_launcher_round.webp"), lossless=False, quality=92)
        print(f"mipmap-{density}/ic_launcher(.round).webp  {side}x{side}")


def build_logo(art_opaque: Image.Image, art_keyed: Image.Image, bbox):
    """Лого для онбординга: плотный кроп по содержимому, без чёрных полей."""
    x0, y0, x1, y1 = bbox
    pad = int((x1 - x0) * 0.02)
    x0 = max(0, x0 - pad)
    y0 = max(0, y0 - pad)
    x1 = min(art_keyed.width, x1 + pad)
    y1 = min(art_keyed.height, y1 + pad)

    crop = art_keyed.crop((x0, y0, x1 + 1, y1 + 1))
    # 512px по длинной стороне достаточно для орба 168dp на любом экране.
    long_side = max(crop.size)
    ratio = 512.0 / long_side
    crop = crop.resize((int(crop.width * ratio), int(crop.height * ratio)), Image.LANCZOS)

    directory = os.path.join(RES, "drawable-nodpi")
    os.makedirs(directory, exist_ok=True)
    crop.save(os.path.join(directory, "amalia_logo.png"))
    print(f"drawable-nodpi/amalia_logo.png  {crop.size[0]}x{crop.size[1]}")


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Использование: python3 tools/BuildLauncherIcons.py <арт.png>")
    source = sys.argv[1]

    art_opaque = Image.open(source).convert("RGB")
    bbox = content_bbox(np.asarray(art_opaque).astype(np.float32))
    print("bbox содержимого:", bbox)

    art_keyed = keyed(art_opaque)
    build_adaptive(art_opaque, art_keyed, bbox)
    build_legacy(art_opaque, art_keyed, bbox)
    build_logo(art_opaque, art_keyed, bbox)

    # Старый арт больше не нужен ни в одном из density-каталогов:
    # фон иконки задаётся цветом, а не картинкой.
    for density in ADAPTIVE_DENSITIES:
        stale = os.path.join(RES, f"drawable-{density}", "ic_launcher_background.png")
        if os.path.exists(stale):
            os.remove(stale)
            print(f"удалён {density}/ic_launcher_background.png")


if __name__ == "__main__":
    main()
