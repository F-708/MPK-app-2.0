# -*- coding: utf-8 -*-
"""Прижимает размеченные рамки кабинетов к чёрным линиям стен на плане.

Разметка сделана на глаз, поэтому рамки стоят примерно. Скрипт ищет рядом
с каждой гранью линию стены (тёмные, не красные пиксели) и сдвигает грань
на неё. Если рядом сильной линии нет — грань остаётся как была.

Результат: исправленный код Kotlin + картинка-превью, чтобы глазами проверить.
"""
import io
import re
import numpy as np
from PIL import Image, ImageDraw

FLOORS = {
    "FIRST": ("app/src/main/res/drawable/map_floor1.jpg", "preview_floor1.png"),
    "SECOND": ("app/src/main/res/drawable/map_floor2.jpg", "preview_floor2.png"),
    "THIRD_FOURTH": ("app/src/main/res/drawable/map_floor34.jpg", "preview_floor34.png"),
}

# Насколько далеко от нарисованной грани искать настоящую стену (в пикселях)
SEARCH = 16
# Какая доля грани должна быть тёмной, чтобы считать это стеной, а не буквой
MIN_COVERAGE = 0.45
# Минимальный размер кабинета в пикселях — защита от вырожденных рамок
MIN_SIDE = 5


def wall_mask(path):
    img = Image.open(path).convert("RGB")
    a = np.asarray(img).astype(np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    # Стены и подписи чёрные; красные надписи и линии — не стены
    dark = (r < 140) & (g < 140) & (b < 140) & (abs(r - g) < 55) & (abs(r - b) < 55)
    return dark, img


def snap_axis(mask, fixed_lo, fixed_hi, guess, horizontal):
    """Ищет ближайшую сильную линию вдоль одной оси.

    horizontal=False — двигаем вертикальную грань (меняется x),
    horizontal=True  — двигаем горизонтальную грань (меняется y).
    """
    size = mask.shape[1] if horizontal else mask.shape[0]  # длина грани
    lo = max(0, guess - SEARCH)
    hi = min(mask.shape[0] if horizontal else mask.shape[1], guess + SEARCH + 1)

    best_pos, best_score = guess, 0
    for pos in range(lo, hi):
        if horizontal:
            line = mask[pos, fixed_lo:fixed_hi]
        else:
            line = mask[fixed_lo:fixed_hi, pos]
        score = int(line.sum())
        # Ближе к исходной грани — приоритетнее при равном счёте
        better = score > best_score or (score == best_score and abs(pos - guess) < abs(best_pos - guess))
        if better:
            best_pos, best_score = pos, score

    span = max(1, fixed_hi - fixed_lo)
    if best_score < MIN_COVERAGE * span:
        return guess, False
    return best_pos, True


def process(pins, floor_key):
    path, preview = FLOORS[floor_key]
    mask, img = wall_mask(path)
    H, W = mask.shape

    fixed = []
    for room, x, y, w, h in pins:
        x0 = int(round((x - w / 2) * W))
        x1 = int(round((x + w / 2) * W))
        y0 = int(round((y - h / 2) * H))
        y1 = int(round((y + h / 2) * H))

        # Сначала вертикальные стены — ищем их вдоль исходного диапазона по y
        nx0, ok_l = snap_axis(mask, max(0, y0), min(H, y1), x0, horizontal=False)
        nx1, ok_r = snap_axis(mask, max(0, y0), min(H, y1), x1, horizontal=False)
        # Затем горизонтальные — вдоль уточнённых x
        lo, hi = max(0, min(nx0, nx1)), min(W, max(nx0, nx1))
        ny0, ok_t = snap_axis(mask, lo, hi, y0, horizontal=True)
        ny1, ok_b = snap_axis(mask, lo, hi, y1, horizontal=True)

        # Если грань не прижалась — оставляем исходную
        if not ok_l: nx0 = x0
        if not ok_r: nx1 = x1
        if not ok_t: ny0 = y0
        if not ok_b: ny1 = y1

        if nx1 - nx0 < MIN_SIDE:
            nx0, nx1 = x0, x1
        if ny1 - ny0 < MIN_SIDE:
            ny0, ny1 = y0, y1

        cx = (nx0 + nx1) / 2 / W
        cy = (ny0 + ny1) / 2 / H
        nw = (nx1 - nx0) / W
        nh = (ny1 - ny0) / H

        snapped = sum([ok_l, ok_r, ok_t, ok_b])
        fixed.append((room, cx, cy, nw, nh, snapped))

    # Превью: рамки поверх плана
    prev = img.copy()
    d = ImageDraw.Draw(prev)
    for room, cx, cy, w, h, _ in fixed:
        px, py = cx * W, cy * H
        pw, ph = w * W, h * H
        d.rectangle([px - pw / 2, py - ph / 2, px + pw / 2, py + ph / 2], outline=(0, 114, 206), width=2)
    prev.save(preview)

    return fixed


# ---------------------------------------------------------------------------
# Разбор присланного кода
# ---------------------------------------------------------------------------
SRC = io.open("tools/incoming_pins.txt", encoding="utf-8").read()
LINE = re.compile(
    r'put\("([^"]+)",\s*RoomPin\(Floor\.(\w+),\s*([\d.]+)f,\s*([\d.]+)f,\s*([\d.]+)f,\s*([\d.]+)f\)\)'
)

by_floor = {}
for m in LINE.finditer(SRC):
    room, fl, x, y, w, h = m.group(1), m.group(2), *map(float, m.groups()[2:])
    by_floor.setdefault(fl, []).append((room, x, y, w, h))

# ---------------------------------------------------------------------------
out = []
snapped_total = 0
pins_total = 0

for fl in ["FIRST", "SECOND", "THIRD_FOURTH"]:
    pins = by_floor.get(fl, [])
    if not pins:
        continue
    fixed = process(pins, fl)
    snapped_total += sum(1 for p in fixed if p[5] > 0)
    pins_total += len(fixed)

    out.append("        // --- %s ---" % fl)
    for room, cx, cy, w, h, _ in fixed:
        out.append(
            '        put("%s", RoomPin(Floor.%s, %.4ff, %.4ff, %.4ff, %.4ff))'
            % (room, fl, cx, cy, w, h)
        )
    out.append("")

io.open("tools/corrected_pins.txt", "w", encoding="utf-8").write("\n".join(out))
print("кабинетов:", pins_total)
print("прижато хотя бы одной гранью:", snapped_total)
print("превью сохранены: preview_floor1.png, preview_floor2.png, preview_floor34.png")
