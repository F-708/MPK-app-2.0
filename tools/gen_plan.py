"""Генератор плана этажей для карты.

Источник — `college_map.json`: там у каждого кабинета есть прямоугольник и точка двери,
у этажа — осевые линии коридоров и точки входов/лестниц. Из этого собираем готовый
ассет `map_walls.json`:

  rooms     — прямоугольники кабинетов (пиксели холста 1376x768)
  doors     — проём: сторона, начало вдоль стороны, ширина
  windows   — отрезки окон на наружных стенах
  stairs    — лестничные клетки
  corridors — осевые линии коридоров и их ширина

Стены отдельно не генерируются: их роль играют кромки кабинетов, поэтому линия
всегда прямая и точно совпадает с прямоугольником кабинета.
"""

import json
import math
import os

PLAN_W = 1376.0
PLAN_H = 768.0

DOOR_W = 18.0          # ширина проёма по умолчанию
DOOR_MIN = 12.0
DOOR_MAX = 28.0
EDGE_TOL = 2.0         # допуск «кромки совпадают»
CORRIDOR_PAD = 2.0     # запас, с которым кромка считается обращённой в коридор
WINDOW_LEN = 30.0      # длина одного окна
WINDOW_STEP = 58.0     # шаг окон вдоль стены
WINDOW_INSET = 3.0     # отступ линии окна внутрь кабинета

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "assets", "college_map.json")
DST = os.path.join(HERE, "..", "app", "src", "main", "assets", "map_walls.json")


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def room_rect(room):
    x = room["x"] * PLAN_W
    y = room["y"] * PLAN_H
    w = room["w"] * PLAN_W
    h = room["h"] * PLAN_H
    return [round(x - w / 2, 1), round(y - h / 2, 1), round(x + w / 2, 1), round(y + h / 2, 1)]


def door_of(room, rect):
    """Сторона проёма: точка двери лежит на кромке прямоугольника."""
    if not room.get("door"):
        return None
    dx = room["door"][0] * PLAN_W
    dy = room["door"][1] * PLAN_H
    x0, y0, x1, y1 = rect
    edges = {
        "w": abs(dx - x0),
        "e": abs(dx - x1),
        "n": abs(dy - y0),
        "s": abs(dy - y1),
    }
    side = min(edges, key=edges.get)
    if edges[side] > 4.0:
        return None
    if side in ("n", "s"):
        span = x1 - x0
        at = dx - span / 2
        limit = span
    else:
        span = y1 - y0
        at = dy - span / 2
        limit = span
    width = min(DOOR_W, limit * 0.7)
    width = max(4.0, width)
    width = min(width, limit)
    # Проём не должен вылезать за угол кабинета.
    half = width / 2
    center = at
    center = max(-limit / 2 + half, min(limit / 2 - half, center))
    return {
        "side": side,
        "center": round(center, 1),
        "width": round(width, 1),
    }


def seg_point_distance(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    denom = vx * vx + vy * vy
    t = 0.0 if denom <= 1e-9 else max(0.0, min(1.0, (wx * vx + wy * vy) / denom))
    cx, cy = ax + t * vx, ay + t * vy
    return math.hypot(px - cx, py - cy)


def corridor_segments(floor):
    out = []
    for cor in floor.get("corridors") or []:
        pts = [(p[0] * PLAN_W, p[1] * PLAN_H) for p in cor.get("points") or []]
        width = max(6.0, (cor.get("width") or 0.0) * PLAN_W)
        for i in range(len(pts) - 1):
            out.append((pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], width))
    return out


def is_open_edge(rect, side, others, corridors):
    """Кромка «внутренняя», если к ней примыкает другой кабинет или идёт коридор."""
    x0, y0, x1, y1 = rect
    if side in ("n", "s"):
        y = y0 if side == "n" else y1
        samples = [(x0 + (x1 - x0) * f, y) for f in (0.2, 0.5, 0.8)]
        normal = (0.0, -1.0 if side == "n" else 1.0)
        length = x1 - x0
    else:
        x = x0 if side == "w" else x1
        samples = [(x, y0 + (y1 - y0) * f) for f in (0.2, 0.5, 0.8)]
        normal = (-1.0 if side == "w" else 1.0, 0.0)
        length = y1 - y0
    if length <= 0.5:
        return True
    hits = 0
    for sx, sy in samples:
        px = sx + normal[0] * (EDGE_TOL + 1.5)
        py = sy + normal[1] * (EDGE_TOL + 1.5)
        for other in others:
            ox0, oy0, ox1, oy1 = other
            if ox0 - EDGE_TOL <= px <= ox1 + EDGE_TOL and oy0 - EDGE_TOL <= py <= oy1 + EDGE_TOL:
                hits += 1
                break
        else:
            for cx0, cy0, cx1, cy1, cw in corridors:
                if seg_point_distance(sx, sy, cx0, cy0, cx1, cy1) <= cw / 2 + CORRIDOR_PAD:
                    hits += 1
                    break
    return hits >= 2


def windows_of(rect, others, corridors):
    """Окна на наружных кромках: короткие отрезки, отступающие внутрь кабинета."""
    x0, y0, x1, y1 = rect
    out = []
    for side in ("n", "s", "w", "e"):
        if not is_open_edge(rect, side, others, corridors):
            continue
        if side in ("n", "s"):
            start, end = x0, x1
            length = end - start
            if length < 14:
                continue
            count = max(1, int(round(length / WINDOW_STEP)))
            win = min(WINDOW_LEN, max(10.0, length / count - 8.0))
            for i in range(count):
                center = start + length * (i + 0.5) / count
                a = center - win / 2
                b = center + win / 2
                y = y0 + WINDOW_INSET if side == "n" else y1 - WINDOW_INSET
                out.append([round(a, 1), round(y, 1), round(b, 1), round(y, 1)])
        else:
            start, end = y0, y1
            length = end - start
            if length < 14:
                continue
            count = max(1, int(round(length / WINDOW_STEP)))
            win = min(WINDOW_LEN, max(10.0, length / count - 8.0))
            for i in range(count):
                center = start + length * (i + 0.5) / count
                a = center - win / 2
                b = center + win / 2
                x = x0 + WINDOW_INSET if side == "w" else x1 - WINDOW_INSET
                out.append([round(x, 1), round(a, 1), round(x, 1), round(b, 1)])
    return out


def stairs_of(floor):
    out = []
    for ent in floor.get("entrances") or []:
        if ent.get("kind") != "stairs":
            continue
        x = ent["x"] * PLAN_W
        y = ent["y"] * PLAN_H
        direction = int(ent.get("dir") or 0)
        w, h = (26.0, 44.0) if direction in (0, 180) else (44.0, 26.0)
        out.append(
            {
                "x": round(x - w / 2, 1),
                "y": round(y - h / 2, 1),
                "w": w,
                "h": h,
                "dir": direction,
                "near": ent.get("nearRoom") or "",
            }
        )
    return out


def nearest_corridor_door(rect, corridors):
    """Кабинет без точки двери: ставим проём на кромку, ближайшую к коридору."""
    x0, y0, x1, y1 = rect
    best = None
    for (cx0, cy0, cx1, cy1, cw) in corridors:
        for side in ("n", "s", "w", "e"):
            if side in ("n", "s"):
                y = y0 if side == "n" else y1
                length = x1 - x0
                points = [(x0 + length * f, y) for f in (0.25, 0.5, 0.75)]
            else:
                x = x0 if side == "w" else x1
                length = y1 - y0
                points = [(x, y0 + length * f) for f in (0.25, 0.5, 0.75)]
            if length < DOOR_MIN * 2:
                continue
            for px, py in points:
                d = seg_point_distance(px, py, cx0, cy0, cx1, cy1) - cw / 2
                if best is None or d < best[0]:
                    if side in ("n", "s"):
                        center = (px - (x0 + x1) / 2)
                    else:
                        center = (py - (y0 + y1) / 2)
                    best = (d, side, center, length)
    if best is None:
        return None
    _, side, center, length = best
    width = min(DOOR_W, length * 0.6)
    width = max(4.0, width)
    width = min(width, length)
    half = width / 2
    center = max(-length / 2 + half, min(length / 2 - half, center))
    return {"side": side, "center": round(center, 1), "width": round(width, 1)}


GRID = 4.0            # клетка растра для контура корпуса


def footprint_rects(rooms, corridors):
    """Контур корпуса: всё, что не достижимо снаружи плана.

    Заливаем растр кабинетами и коридорами, затем заливкой от краёв помечаем
    «улицу». Остаток — корпус: кабинеты, стены и глухие участки крыши между
    ними. Именно этим заливается бумага, поэтому газон не просвечивает
    через крышу (кабинеты 231/233/240 на втором этаже).
    """
    w = int(PLAN_W // GRID)
    h = int(PLAN_H // GRID)
    solid = [[False] * w for _ in range(h)]

    def mark(x0, y0, x1, y1):
        c0 = max(0, int(x0 // GRID))
        c1 = min(w - 1, int(x1 // GRID) - 1)
        r0 = max(0, int(y0 // GRID))
        r1 = min(h - 1, int(y1 // GRID) - 1)
        for r in range(r0, r1 + 1):
            row = solid[r]
            for c in range(c0, c1 + 1):
                row[c] = True

    for rect in rooms:
        mark(rect[0], rect[1], rect[2], rect[3])
    for cor in corridors:
        pts = cor["pts"]
        cw = cor["w"]
        for i in range(len(pts) - 1):
            ax, ay = pts[i]
            bx, by = pts[i + 1]
            steps = int(max(abs(bx - ax), abs(by - ay)) // (GRID / 2)) + 1
            for s in range(steps + 1):
                t = s / steps
                cx = ax + (bx - ax) * t
                cy = ay + (by - ay) * t
                mark(cx - cw / 2, cy - cw / 2, cx + cw / 2, cy + cw / 2)

    outside = [[False] * w for _ in range(h)]
    stack = []
    for c in range(w):
        for r in (0, h - 1):
            if not solid[r][c] and not outside[r][c]:
                outside[r][c] = True
                stack.append((r, c))
    for r in range(h):
        for c in (0, w - 1):
            if not solid[r][c] and not outside[r][c]:
                outside[r][c] = True
                stack.append((r, c))
    while stack:
        r, c = stack.pop()
        for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nr, nc = r + dr, c + dc
            if 0 <= nr < h and 0 <= nc < w and not solid[nr][nc] and not outside[nr][nc]:
                outside[nr][nc] = True
                stack.append((nr, nc))

    # Склеиваем оставшиеся клетки в горизонтальные полосы и объединяем по вертикали.
    open_rects = {}
    rects = []
    for r in range(h):
        spans = []
        c = 0
        while c < w:
            if not outside[r][c]:
                start = c
                while c < w and not outside[r][c]:
                    c += 1
                spans.append((start, c - 1))
            else:
                c += 1
        nxt = {}
        for span in spans:
            if span in open_rects:
                nxt[span] = open_rects[span]
            else:
                nxt[span] = r
        for span, r0 in open_rects.items():
            if span not in nxt:
                rects.append(
                    [
                        float(span[0]) * GRID,
                        float(r0) * GRID,
                        float(span[1] + 1) * GRID,
                        float(r) * GRID,
                    ]
                )
        open_rects = nxt
    for span, r0 in open_rects.items():
        rects.append(
            [
                float(span[0]) * GRID,
                float(r0) * GRID,
                float(span[1] + 1) * GRID,
                float(h) * GRID,
            ]
        )
    return [[round(v, 1) for v in rect] for rect in rects]


def main():
    data = load(SRC)
    floors = {}
    for fid, floor in data["floors"].items():
        src_rooms = [r for r in floor.get("rooms") or [] if r.get("number")]
        rects = [(r, room_rect(r)) for r in src_rooms]
        all_rects = [rect for _, rect in rects]
        corridors = corridor_segments(floor)

        rooms, doors, windows = [], [], []
        for room, rect in rects:
            rooms.append(
                {
                    "n": room["number"],
                    "s": room.get("status") or "normal",
                    "r": rect,
                }
            )
            door = door_of(room, rect)
            if door is None:
                door = nearest_corridor_door(rect, corridors)
            if door:
                doors.append(
                    {
                        "n": room["number"],
                        "side": door["side"],
                        "c": door["center"],
                        "w": door["width"],
                    }
                )
            others = [r for r in all_rects if r is not rect]
            windows.extend(windows_of(rect, others, corridors))

        floors[fid] = {
            "rooms": rooms,
            "doors": doors,
            "windows": windows,
            "stairs": stairs_of(floor),
            "footprint": footprint_rects(
                [r["r"] for r in rooms],
                [
                    {
                        "pts": [
                            [round(p[0] * PLAN_W, 1), round(p[1] * PLAN_H, 1)]
                            for p in (cor.get("points") or [])
                        ],
                        "w": round(max(6.0, (cor.get("width") or 0.0) * PLAN_W), 1),
                    }
                    for cor in (floor.get("corridors") or [])
                ],
            ),
            "corridors": [
                {
                    "pts": [
                        [round(p[0] * PLAN_W, 1), round(p[1] * PLAN_H, 1)]
                        for p in (cor.get("points") or [])
                    ],
                    "w": round(max(6.0, (cor.get("width") or 0.0) * PLAN_W), 1),
                }
                for cor in (floor.get("corridors") or [])
            ],
        }
        print(
            fid,
            "rooms", len(rooms),
            "doors", len(doors),
            "windows", len(windows),
            "stairs", len(floors[fid]["stairs"]),
            "corridors", len(floors[fid]["corridors"]),
            "footprint", len(floors[fid]["footprint"]),
        )

    out = {
        "version": 3,
        "plan": [PLAN_W, PLAN_H],
        "floors": floors,
    }
    with open(DST, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, separators=(",", ":"))
    print("written", DST, os.path.getsize(DST), "bytes")


if __name__ == "__main__":
    main()