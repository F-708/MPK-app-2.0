"""Трассировка растровых планов этажей в векторные слои.

Вход:  app/src/main/res/drawable/map_floor1.jpg, map_floor2.jpg, map_floor34.jpg
Выход: data/map_walls.json  (стены, лестницы, двери, привязка к сетке)

Стены в исходнике — тёмно-синие линии толщиной 3-5 px на светлом фоне,
план строго ортогональный. Поэтому маска стен раскладывается на прямоугольники
(слияние одинаковых горизонтальных ранов по вертикали), а не на контуры:
так сохраняются реальные проёмы дверей и получается компактный результат.
"""

import json
import math
import pathlib
import sys

import cv2
import numpy as np

ROOT = pathlib.Path(r"E:\MPK app\MPK-app-2.1")
OUT = ROOT / "_reports" / "map-rework-2026-11-07" / "data"
DEBUG = ROOT / "_reports" / "map-rework-2026-11-07" / "debug"

FLOORS = {
    "1": "map_floor1.jpg",
    "2": "map_floor2.jpg",
    "3-4": "map_floor34.jpg",
}

WALL_LUM = 110  # порог: стены < 110, заливки кабинетов ~ 230


def imread(path):
    buf = np.fromfile(str(path), dtype=np.uint8)
    return cv2.imdecode(buf, cv2.IMREAD_COLOR)


def imwrite(path, img):
    ok, buf = cv2.imencode(path.suffix, img)
    if ok:
        buf.tofile(str(path))


def wall_mask(bgr):
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    mask = (gray < WALL_LUM).astype(np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    return mask


def runs_of_row(row):
    d = np.diff(np.concatenate(([0], row, [0])))
    starts = np.where(d == 1)[0]
    ends = np.where(d == -1)[0]
    return list(zip(starts.tolist(), ends.tolist()))


def rle_rects(mask):
    """Прямоугольники: одинаковые раны в соседних строках сливаются по вертикали."""
    height = mask.shape[0]
    rects = []
    opened = {}
    for y in range(height):
        current = set()
        for x0, x1 in runs_of_row(mask[y]):
            key = (x0, x1)
            current.add(key)
            opened.setdefault(key, y)
        for key in [k for k in opened if k not in current]:
            rects.append([key[0], opened.pop(key), key[1], y])
    for key, y0 in opened.items():
        rects.append([key[0], y0, key[1], height])
    return rects


def merge_horizontal(rects):
    """Слияние прямоугольников с общими вертикальными границами в длинные участки."""
    rects = sorted(rects, key=lambda r: (r[1], r[3], r[0]))
    out = []
    for r in rects:
        if out:
            p = out[-1]
            if p[1] == r[1] and p[3] == r[3] and p[2] == r[0]:
                p[2] = r[2]
                continue
        out.append(list(r))
    return out


def drop_small(rects, min_side=2, min_area=8):
    out = []
    for x0, y0, x1, y1 in rects:
        if x1 - x0 < min_side and y1 - y0 < min_side:
            continue
        if (x1 - x0) * (y1 - y0) < min_area:
            continue
        out.append([x0, y0, x1, y1])
    return out


def stair_boxes(bgr):
    """Лестничные марши: серая штриховка (низкая насыщенность, средняя яркость)."""
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    grey = ((gray > 140) & (gray < 212) & (hsv[:, :, 1] < 26)).astype(np.uint8)
    grey = cv2.morphologyEx(grey, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    count, _, stats, _ = cv2.connectedComponentsWithStats(grey, 8)
    out = []
    for i in range(1, count):
        x, y, w, h, area = (int(v) for v in stats[i][:5])
        if area < 260 or w < 17 or h < 17:
            continue
        if max(w, h) / float(min(w, h)) > 4.5:
            continue
        fill = area / float(w * h)
        if not 0.15 < fill < 0.92:
            continue
        out.append({"x": x, "y": y, "w": w, "h": h, "area": area, "fill": round(fill, 2)})
    return out


def door_gap(mask, px, py, horizontal, limit=44):
    """Реальный проём: ищем ближайшие простенки слева/справа (или сверху/снизу) от точки двери."""
    height, width = mask.shape
    if horizontal:
        lo = hi = None
        for step in range(1, limit):
            x = px - step
            if x < 0 or mask[py, x] != 0:
                lo = x + 1
                break
        for step in range(1, limit):
            x = px + step
            if x >= width or mask[py, x] != 0:
                hi = x - 1
                break
        if lo is None or hi is None or hi <= lo:
            return None
        return lo, hi, py
    lo = hi = None
    for step in range(1, limit):
        y = py - step
        if y < 0 or mask[y, px] != 0:
            lo = y + 1
            break
    for step in range(1, limit):
        y = py + step
        if y >= height or mask[y, px] != 0:
            hi = y - 1
            break
    if lo is None or hi is None or hi <= lo:
        return None
    return lo, hi, px


def stairs_from_entrances(semantic_floor, detected, radius=34.0):
    """Лестницы привязываются к входам kind=stairs из college_map.json.

    Штриховка даёт габариты марша, но сама по себе ненадёжна: на 2 этаже
    диагональная штриховка есть и в кабинетах (204, 205, 206, 210), а реальные
    марши местами залиты цветом пола. Позиции входов — единственный источник,
    на который опирается маршрутизация, поэтому берём их.
    """
    out = []
    used = set()
    for entrance in semantic_floor.get("entrances", []):
        if entrance.get("kind") != "stairs":
            continue
        ex = entrance["x"] * W
        ey = entrance["y"] * H
        best = None
        best_d = radius
        for index, stair in enumerate(detected):
            cx = stair["x"] + stair["w"] / 2.0
            cy = stair["y"] + stair["h"] / 2.0
            d = math.hypot(cx - ex, cy - ey)
            if d < best_d:
                best_d = d
                best = index
        if best is not None:
            used.add(best)
            stair = dict(detected[best])
            stair["entranceId"] = entrance["id"]
            stair["source"] = "hatch"
            stair["approx"] = False
            out.append(stair)
        else:
            w, h = 30, 34
            out.append({
                "x": int(round(ex - w / 2.0)),
                "y": int(round(ey - h / 2.0)),
                "w": w,
                "h": h,
                "area": 0,
                "fill": 0.0,
                "entranceId": entrance["id"],
                "source": "entrance",
                "approx": True,
            })
    leftovers = [detected[i] for i in range(len(detected)) if i not in used]
    return out, leftovers


def side_of(room, px, py):
    """К какой грани кабинета ближе точка двери: n/s/w/e (в пикселях)."""
    x0 = (room["x"] - room["w"] / 2.0) * W
    x1 = (room["x"] + room["w"] / 2.0) * W
    y0 = (room["y"] - room["h"] / 2.0) * H
    y1 = (room["y"] + room["h"] / 2.0) * H
    return min(
        (abs(py - y0), "n"),
        (abs(py - y1), "s"),
        (abs(px - x0), "w"),
        (abs(px - x1), "e"),
    )[1]


def path_from_rects(rects):
    """Один path: каждый прямоугольник — замкнутый подпуть (M/h/v/h/Z)."""
    parts = []
    for x0, y0, x1, y1 in rects:
        parts.append("M%d %dh%dv%dh%dZ" % (x0, y0, x1 - x0, y1 - y0, x0 - x1))
    return "".join(parts)


def alignment(rooms, fill, wall):
    """Совпадение прямоугольников кабинетов с растром.

    Критерий: кромки прямоугольника должны лежать на стенах (простенки и общие
    стены соседних кабинетов), а внутри — не быть стен. Проёмы дверей дают
    разрывы, поэтому порог по кромке ниже единицы.
    """
    total = 0
    good = 0
    edge_hits = []
    worst = []
    for room in rooms:
        x0 = int((room["x"] - room["w"] / 2.0) * W)
        y0 = int((room["y"] - room["h"] / 2.0) * H)
        x1 = int((room["x"] + room["w"] / 2.0) * W)
        y1 = int((room["y"] + room["h"] / 2.0) * H)
        if x1 - x0 < 4 or y1 - y0 < 4:
            continue
        total += 1
        area = float((x1 - x0) * (y1 - y0))
        blocked = float(wall[y0:y1, x0:x1].sum()) / area
        edges = (
            wall[y0, x0:x1].sum()
            + wall[max(y0, y1 - 1), x0:x1].sum()
            + wall[y0:y1, x0].sum()
            + wall[y0:y1, max(x0, x1 - 1)].sum()
        )
        perimeter = 2.0 * ((x1 - x0) + (y1 - y0))
        on_wall = float(edges) / perimeter
        edge_hits.append(on_wall)
        # прямоугольники проведены по осям стен: полоса стены внутри кабинета
        # даёт blocked 0.08-0.25 при корректной привязке
        ok = on_wall >= 0.35 and blocked <= 0.30
        if ok:
            good += 1
        else:
            worst.append((round(on_wall, 2), round(blocked, 2), room.get("number") or room["id"]))
    worst.sort(key=lambda t: t[0])
    edge_hits.sort()
    return {
        "rooms": total,
        "matched": good,
        "ratio": round(good / total, 3) if total else 0.0,
        "edge_on_wall_median": round(edge_hits[len(edge_hits) // 2], 3) if edge_hits else 0.0,
        "edge_on_wall_min": round(edge_hits[0], 3) if edge_hits else 0.0,
        "suspect": [w[2] for w in worst if w[0] < 0.35][:40],
        "worst": worst[:10],
    }


def process(floor_id, filename, map_data):
    bgr = imread(ROOT / "app" / "src" / "main" / "res" / "drawable" / filename)
    if bgr is None:
        raise SystemExit("не читается " + filename)
    bgr = cv2.resize(bgr, (W, H), interpolation=cv2.INTER_AREA)

    mask = wall_mask(bgr)
    rects = drop_small(merge_horizontal(rle_rects(mask)))
    rects.sort(key=lambda r: (r[1], r[0]))

    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blue, _, red = cv2.split(bgr)
    fill = ((gray > 200) & (blue.astype(np.int16) - red.astype(np.int16) > 6)).astype(np.uint8)

    rooms = map_data["floors"][floor_id].get("rooms", [])
    stairs, stair_leftovers = stairs_from_entrances(map_data["floors"][floor_id], stair_boxes(bgr))
    doors = []
    for room in rooms:
        door = room.get("door")
        if not door:
            continue
        px = int(round(door[0] * W))
        py = int(round(door[1] * H))
        side = side_of(room, px, py)
        gap = door_gap(mask, px, py, side in ("n", "s"))
        entry = {
            "roomId": room["id"],
            "side": side,
            "x": px,
            "y": py,
            "approx": True,
        }
        if gap:
            lo, hi, fixed = gap
            entry["gap"] = [lo, hi] if side in ("n", "s") else [lo, hi]
            entry["width"] = int(hi - lo)
        doors.append(entry)

    aligned = alignment(rooms, fill, mask)

    debug = bgr.copy()
    for x0, y0, x1, y1 in rects:
        cv2.rectangle(debug, (x0, y0), (x1 - 1, y1 - 1), (0, 0, 255), 1)
    for s in stairs:
        cv2.rectangle(debug, (s["x"], s["y"]), (s["x"] + s["w"], s["y"] + s["h"]), (0, 200, 0), 2)
    for d in doors:
        cv2.circle(debug, (d["x"], d["y"]), 3, (255, 0, 0), -1)
    DEBUG.mkdir(parents=True, exist_ok=True)
    imwrite(DEBUG / ("floor%s.jpg" % floor_id.replace("-", "_")), debug)

    return {
        "walls": [value for rect in rects for value in rect],
        "wallPath": path_from_rects(rects),
        "stairs": stairs,
        "stairLeftovers": stair_leftovers,
        "doors": doors,
        "alignment": aligned,
    }, len(rects)


W, H = 1376, 768


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    map_data = json.loads((ROOT / "app" / "src" / "main" / "assets" / "college_map.json").read_text(encoding="utf-8"))
    result = {"version": 1, "viewBox": [0, 0, W, H], "source": "raster-trace", "floors": {}}
    for floor_id, filename in FLOORS.items():
        data, count = process(floor_id, filename, map_data)
        result["floors"][floor_id] = data
        print(
            "floor %-4s walls=%-5d stairs=%-2d doors=%-3d align=%s"
            % (floor_id, count, len(data["stairs"]), len(data["doors"]), data["alignment"]["ratio"])
        )
    (OUT / "map_walls.json").write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("размер:", (OUT / "map_walls.json").stat().st_size // 1024, "КБ")


if __name__ == "__main__":
    sys.exit(main())