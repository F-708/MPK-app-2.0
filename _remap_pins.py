# -*- coding: utf-8 -*-
"""Переносит разметку кабинетов со старых планов на новые.

Новые планы перерисованы нейросетью: холст 16:9 вместо 4:3, здание стоит
в другом месте. Прямо пропорционально переносить нельзя — получается
систематический сдвиг.

Как считаем преобразование:
1. Выделяем контур здания на старом и новом плане (крупнейшая связная
   область линий — так надписи и таблицы в углах не мешают).
2. Грубо накладываем контуры друг на друга.
3. Уточняем масштаб и сдвиг перебором: берём вариант, где контуры
   совпадают лучше всего (максимум пересечения).
4. Переносим рамки кабинетов и прижимаем их к стенам нового плана.
"""
import io
import re
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

PAIRS = [
    ("floor1",  "FIRST",        "app/src/main/res/drawable/map_floor1.jpg",  r"c:\Users\F708\Downloads\1 floor.jpg"),
    ("floor2",  "SECOND",       "app/src/main/res/drawable/map_floor2.jpg",  r"c:\Users\F708\Downloads\2 floor.jpg"),
    ("floor34", "THIRD_FOURTH", "app/src/main/res/drawable/map_floor34.jpg", r"c:\Users\F708\Downloads\3 floor.jpg"),
]

SEARCH = 18
MIN_COVERAGE = 0.42
MIN_SIDE = 5
DOWN = 4          # во сколько раз уменьшаем картинку при подборе


def dark_mask(img):
    a = np.asarray(img.convert("RGB")).astype(np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    lum = (r + g + b) / 3
    return (lum < 145) & (abs(r - g) < 60) & (abs(r - b) < 60)


def biggest_component(mask, dilate):
    grown = ndimage.binary_dilation(mask, np.ones((dilate, dilate), bool))
    labels, count = ndimage.label(grown)
    if count == 0:
        return np.zeros_like(mask), None
    sizes = ndimage.sum(grown, labels, range(1, count + 1))
    biggest = int(np.argmax(sizes)) + 1
    comp = mask & (labels == biggest)
    ys, xs = np.where(comp)
    if len(xs) == 0:
        return comp, None
    return comp, (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()))


def to_img(arr):
    return Image.fromarray((arr * 255).astype("uint8"))


def refine(old_comp, obox, new_comp, nbox):
    """Подбирает масштаб и сдвиг так, чтобы контуры здания совпали."""
    ox0, oy0, ox1, oy1 = obox
    nx0, ny0, nx1, ny1 = nbox
    oc = old_comp[oy0:oy1, ox0:ox1]
    tw, th = nx1 - nx0, ny1 - ny0

    tws, ths = max(1, tw // DOWN), max(1, th // DOWN)
    nm = np.asarray(
        to_img(new_comp[ny0:ny1, nx0:nx1]).resize((tws, ths), Image.BILINEAR)
    ) > 100

    oc_img = to_img(oc)
    best = (0, 0, 1.0, 1.0, -1.0)

    for dsx in (0.94, 0.96, 0.98, 1.0, 1.02, 1.04, 1.06):
        for dsy in (0.94, 0.96, 0.98, 1.0, 1.02, 1.04, 1.06):
            w = max(1, int(tws * dsx))
            h = max(1, int(ths * dsy))
            r = np.asarray(oc_img.resize((w, h), Image.BILINEAR)) > 100
            for dx in range(-8, 9, 2):
                for dy in range(-8, 9, 2):
                    x0, y0 = max(0, dx), max(0, dy)
                    x1, y1 = min(tws, dx + w), min(ths, dy + h)
                    if x1 <= x0 or y1 <= y0:
                        continue
                    canvas = np.zeros((ths, tws), bool)
                    canvas[y0:y1, x0:x1] = r[y0 - dy:y1 - dy, x0 - dx:x1 - dx]
                    inter = np.logical_and(canvas, nm).sum()
                    union = np.logical_or(canvas, nm).sum()
                    score = inter / union if union else 0.0
                    if score > best[4]:
                        best = (dx, dy, dsx, dsy, score)
    return best


def snap_axis(mask, lo, hi, guess, horizontal):
    H, W = mask.shape
    a = max(0, guess - SEARCH)
    b = min(H if horizontal else W, guess + SEARCH + 1)
    best_pos, best_score = guess, 0
    for pos in range(a, b):
        line = mask[pos, lo:hi] if horizontal else mask[lo:hi, pos]
        score = int(line.sum())
        if score > best_score or (score == best_score and abs(pos - guess) < abs(best_pos - guess)):
            best_pos, best_score = pos, score
    span = max(1, hi - lo)
    return (best_pos, True) if best_score >= MIN_COVERAGE * span else (guess, False)


SRC = io.open("tools/corrected_pins.txt", encoding="utf-8").read()
LINE = re.compile(
    r'put\("([^"]+)",\s*RoomPin\(Floor\.(\w+),\s*([\d.]+)f,\s*([\d.]+)f,\s*([\d.]+)f,\s*([\d.]+)f\)\)'
)
by_floor = {}
for m in LINE.finditer(SRC):
    by_floor.setdefault(m.group(2), []).append((m.group(1), *map(float, m.groups()[2:])))

out_all = []

for key, fl_key, old_path, new_path in PAIRS:
    old_img, new_img = Image.open(old_path), Image.open(new_path)
    OW, OH = old_img.size
    NW, NH = new_img.size

    old_mask = dark_mask(old_img)
    new_mask = dark_mask(new_img)
    old_comp, obox = biggest_component(old_mask, dilate=3)
    new_comp, nbox = biggest_component(new_mask, dilate=31)
    if obox is None or nbox is None:
        print(key, "— контур здания не найден")
        continue

    ox0, oy0, ox1, oy1 = obox
    nx0, ny0, nx1, ny1 = nbox

    dx, dy, dsx, dsy, score = refine(old_comp, obox, new_comp, nbox)
    print("%-8s контуры совпали на %.3f (масштаб %.2f x %.2f, сдвиг %d, %d)"
          % (key, score, dsx, dsy, dx * DOWN, dy * DOWN))

    sx = ((nx1 - nx0) / max(1, ox1 - ox0)) * dsx
    sy = ((ny1 - ny0) / max(1, oy1 - oy0)) * dsy
    tx = nx0 + dx * DOWN
    ty = ny0 + dy * DOWN

    out_pins = []
    for number, x, y, w, h in by_floor.get(fl_key, []):
        px, py, pw, ph = x * OW, y * OH, w * OW, h * OH
        cx = tx + (px - ox0) * sx
        cy = ty + (py - oy0) * sy
        cw, ch = pw * sx, ph * sy

        x0, x1 = int(round(cx - cw / 2)), int(round(cx + cw / 2))
        y0, y1 = int(round(cy - ch / 2)), int(round(cy + ch / 2))

        bx0, ok_l = snap_axis(new_mask, max(0, y0), min(NH, y1), x0, False)
        bx1, ok_r = snap_axis(new_mask, max(0, y0), min(NH, y1), x1, False)
        lo, hi = max(0, min(bx0, bx1)), min(NW, max(bx0, bx1))
        by0, ok_t = snap_axis(new_mask, lo, hi, y0, True)
        by1, ok_b = snap_axis(new_mask, lo, hi, y1, True)

        if not ok_l: bx0 = x0
        if not ok_r: bx1 = x1
        if not ok_t: by0 = y0
        if not ok_b: by1 = y1
        if bx1 - bx0 < MIN_SIDE: bx0, bx1 = x0, x1
        if by1 - by0 < MIN_SIDE: by0, by1 = y0, y1

        out_pins.append((number,
                         (bx0 + bx1) / 2 / NW, (by0 + by1) / 2 / NH,
                         (bx1 - bx0) / NW, (by1 - by0) / NH))

    prev = new_img.convert("RGB").copy()
    d = ImageDraw.Draw(prev)
    for number, cx, cy, w, h in out_pins:
        px, py = cx * NW, cy * NH
        pw, ph = w * NW, h * NH
        d.rectangle([px - pw / 2, py - ph / 2, px + pw / 2, py + ph / 2],
                    outline=(0, 114, 206), width=2)
    prev.save("preview_new_%s.png" % key)

    out_all.append("        // --- %s ---" % key)
    out_all.append("\n".join(
        '        put("%s", RoomPin(Floor.%s, %.4ff, %.4ff, %.4ff, %.4ff))'
        % (n, fl_key, cx, cy, w, h) for n, cx, cy, w, h in out_pins))
    out_all.append("")
    print("         кабинетов: %d" % len(out_pins))

io.open("tools/new_pins.txt", "w", encoding="utf-8").write("\n".join(out_all))
print("\nзаписано в tools/new_pins.txt")
