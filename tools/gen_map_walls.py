"""Пересборка стен плана из кабинетов исходной схемы.

Источник истины — `college_map.json` (те же прямоугольники кабинетов, что рисует
экран), а не подготовленные отрезки: каждое ребро каждого кабинета становится стеной,
проёмы дверей вычитаются, коллинеарные смежные участки сливаются, а прямоугольник
стены расширяется на полтолщины с обоих концов, чтобы на стыках не оставалось щелей.

Двери берутся из прежнего map_walls.json (там они размечены по сторонам и покрывают
все кабинеты) и пересаживаются на новые кромки с сохранением относительного положения.

Запуск: python tools/gen_map_walls.py
Пишет app/src/main/assets/map_walls.json (version 2, UTF-8).
"""

import io
import json
import sys

ASSET = r"E:\MPK app\MPK-app-2.1\app\src\main\assets\map_walls.json"
SOURCE = r"E:\MPK app\MPK-app-2.1\app\src\main\assets\college_map.json"
PLAN_W = 1376.0
PLAN_H = 768.0
THICKNESS = 8
HALF = THICKNESS / 2.0
DOOR_WIDTH = 20.0
EPS = 0.01


def add(store, orient, line, a, b):
    if abs(b - a) <= EPS:
        return
    store.setdefault((orient, round(line, 2)), []).append((min(a, b), max(a, b)))


def merge(intervals):
    out = []
    for a, b in sorted(intervals):
        if out and a <= out[-1][1] + EPS:
            out[-1][1] = max(out[-1][1], b)
        else:
            out.append([a, b])
    return [(a, b) for a, b in out]


def subtract(intervals, cuts):
    out = list(intervals)
    for ca, cb in cuts:
        nxt = []
        for a, b in out:
            if cb <= a + EPS or ca >= b - EPS:
                nxt.append((a, b))
                continue
            if ca > a + EPS:
                nxt.append((a, ca))
            if cb < b - EPS:
                nxt.append((cb, b))
        out = nxt
    return out


def outline_edges(outlines):
    for poly in outlines:
        for i in range(len(poly)):
            ax, ay = poly[i][0], poly[i][1]
            bx, by = poly[(i + 1) % len(poly)][0], poly[(i + 1) % len(poly)][1]
            if abs(ay - by) < EPS and abs(ax - bx) > EPS:
                yield ("h", ay, ax, bx)
            elif abs(ax - bx) < EPS and abs(ay - by) > EPS:
                yield ("v", ax, ay, by)


def snap_edges(rooms, outlines, tol=3.0):
    """Сводит почти совпадающие кромки кабинетов и контуров к одной линии.

    После округления нормированных координат соседние кабинеты и контур корпуса дают
    кромки врозь на 1-3 пикселя: получаются две параллельные стены, и дверной проём
    вырезается только из той, на которую попал. Общая линия убирает и двойные стены,
    и глухие створы.
    """
    def mapping(values):
        uniq = sorted(set(values))
        groups = []
        for v in uniq:
            if groups and v - groups[-1][-1] <= tol:
                groups[-1].append(v)
            else:
                groups.append([v])
        out = {}
        for group in groups:
            target = round(sum(group) / len(group))
            for v in group:
                out[v] = target
        return out

    xs = mapping([r["x0"] for r in rooms] + [r["x1"] for r in rooms] +
                 [p[0] for poly in outlines for p in poly])
    ys = mapping([r["y0"] for r in rooms] + [r["y1"] for r in rooms] +
                 [p[1] for poly in outlines for p in poly])
    for r in rooms:
        r["x0"] = xs[r["x0"]]
        r["x1"] = xs[r["x1"]]
        r["y0"] = ys[r["y0"]]
        r["y1"] = ys[r["y1"]]
    snapped = [[[xs[p[0]], ys[p[1]]] for p in poly] for poly in outlines]
    return rooms, snapped


def source_rooms(fid, src):
    """Прямоугольники кабинетов из college_map.json, округлённые до пикселя."""
    out = []
    for r in src["floors"][fid]["rooms"]:
        w = r.get("w") or 0.0
        h = r.get("h") or 0.0
        if w <= 0 or h <= 0:
            continue
        x0 = round((r["x"] - w / 2.0) * PLAN_W)
        x1 = round((r["x"] + w / 2.0) * PLAN_W)
        y0 = round((r["y"] - h / 2.0) * PLAN_H)
        y1 = round((r["y"] + h / 2.0) * PLAN_H)
        if x1 - x0 < 4 or y1 - y0 < 4:
            continue
        out.append({
            "number": r.get("number", ""),
            "status": r.get("status", "normal"),
            "x0": x0, "y0": y0, "x1": x1, "y1": y1,
        })
    return [r for r in out if r["x1"] - r["x0"] >= 4 and r["y1"] - r["y0"] >= 4]


def remap_doors(old_rooms, new_rooms, doors):
    """Пересаживает проёмы со старых прямоугольников на новые кромки."""
    old_by = {r["number"]: r for r in old_rooms}
    new_by = {}
    for r in new_rooms:
        new_by.setdefault(r["number"], r)
    out = []
    for d in doors:
        number = d.get("roomId")
        old = old_by.get(number)
        new = new_by.get(number)
        side = d.get("side")
        if old is None or new is None or side not in ("n", "s", "e", "w"):
            continue
        gap = d.get("gap") or [0, 0]
        if side in ("n", "s"):
            old_lo, old_hi = old["x0"], old["x1"]
            new_lo, new_hi = new["x0"], new["x1"]
            line = new["y1"] if side == "s" else new["y0"]
        else:
            old_lo, old_hi = old["y0"], old["y1"]
            new_lo, new_hi = new["y0"], new["y1"]
            line = new["x1"] if side == "e" else new["x0"]
        old_len = float(old_hi - old_lo)
        new_len = float(new_hi - new_lo)
        if old_len <= 0 or new_len <= 0:
            continue
        width = float(gap[1] - gap[0])
        t = ((gap[0] + gap[1]) / 2.0 - old_lo) / old_len
        center = new_lo + t * new_len
        lo = max(new_lo, min(new_hi - 2.0, center - width / 2.0))
        hi = min(new_hi, lo + width)
        if hi - lo < 6.0:
            continue
        out.append({
            "roomId": number,
            "side": side,
            "gap": [round(lo, 1), round(hi, 1)],
            "x": int(round(line)) if side in ("e", "w") else int(round(new["x0"] if side == "e" else new["x0"])),
            "y": int(round(line)) if side in ("n", "s") else int(round(new["y0"])),
            "width": round(hi - lo, 1),
        })
        d = out[-1]
        if side in ("n", "s"):
            d["x"] = int(round((new["x0"] + new["x1"]) / 2.0))
            d["y"] = int(round(line))
        else:
            d["x"] = int(round(line))
            d["y"] = int(round((new["y0"] + new["y1"]) / 2.0))
    return out


def door_cuts(doors, store):
    """Проёмы по линиям стен.

    Кромка кабинета и линия стены могут разойтись на пару пикселей (округление
    нормированных координат), поэтому проём вырезается из всех стен в пределах
    4 px от линии двери — это одна и та же физическая стена.
    """
    cuts = {}
    for d in doors:
        side = d.get("side")
        gap = d.get("gap") or [0, 0]
        if side in ("n", "s"):
            orient, line = "h", float(d.get("y", 0))
        elif side in ("e", "w"):
            orient, line = "v", float(d.get("x", 0))
        else:
            continue
        for key in store:
            if key[0] != orient or abs(key[1] - line) > 4.0:
                continue
            cuts.setdefault(key, []).append((float(gap[0]), float(gap[1])))
    return cuts


def build_floor(fid, fl, src):
    rooms = source_rooms(fid, src)
    outlines = [[[float(p[0]), float(p[1])] for p in poly] for poly in (fl.get("outlines") or [])]
    rooms, outlines = snap_edges(rooms, outlines)
    doors = remap_doors(fl.get("rooms") or [], rooms, fl.get("doors") or []) or list(fl.get("doors") or [])

    store = {}
    for room in rooms:
        add(store, "h", room["y0"], room["x0"], room["x1"])
        add(store, "h", room["y1"], room["x0"], room["x1"])
        add(store, "v", room["x0"], room["y0"], room["y1"])
        add(store, "v", room["x1"], room["y0"], room["y1"])
    rooms_only = sum(len(merge(v)) for v in store.values())

    for orient, line, a, b in outline_edges(outlines):
        add(store, orient, line, a, b)
    with_outline = sum(len(merge(v)) for v in store.values())

    cuts = door_cuts(doors, store)

    segments = []
    for (orient, line), intervals in sorted(store.items()):
        merged = merge(intervals)
        kept = subtract(merged, cuts.get((orient, line), []))
        for a, b in kept:
            if b - a <= EPS:
                continue
            segments.append({
                "orient": orient,
                "line": line,
                "a": a,
                "b": b,
                "doorEnd": [False, False],
            })

    for seg in segments:
        for d in doors:
            side = d.get("side")
            gap = d.get("gap") or [0, 0]
            if side in ("n", "s"):
                orient, line = "h", float(d.get("y", 0))
            elif side in ("e", "w"):
                orient, line = "v", float(d.get("x", 0))
            else:
                continue
            if seg["orient"] != orient or abs(seg["line"] - line) > 0.51:
                continue
            if abs(seg["b"] - float(gap[0])) < 0.6:
                seg["doorEnd"][1] = True
            if abs(seg["a"] - float(gap[1])) < 0.6:
                seg["doorEnd"][0] = True

    rects = []
    segs_out = []
    for seg in segments:
        t = THICKNESS
        left_grow = 0.0 if seg["doorEnd"][0] else HALF
        right_grow = 0.0 if seg["doorEnd"][1] else HALF
        if seg["orient"] == "h":
            x0 = seg["a"] - left_grow
            x1 = seg["b"] + right_grow
            y0 = seg["line"] - HALF
            y1 = seg["line"] + HALF
        else:
            x0 = seg["line"] - HALF
            x1 = seg["line"] + HALF
            y0 = seg["a"] - left_grow
            y1 = seg["b"] + right_grow
        rects.append([round(x0, 1), round(y0, 1), round(x1, 1), round(y1, 1)])
        segs_out.append({
            "x0": round(seg["a"], 1) if seg["orient"] == "h" else round(seg["line"], 1),
            "y0": round(seg["line"], 1) if seg["orient"] == "h" else round(seg["a"], 1),
            "x1": round(seg["b"], 1) if seg["orient"] == "h" else round(seg["line"], 1),
            "y1": round(seg["line"], 1) if seg["orient"] == "h" else round(seg["b"], 1),
            "t": t,
        })

    flat = [c for r in rects for c in r]
    fl["walls"] = flat
    fl["wallSegments"] = segs_out
    fl["doors"] = doors
    fl["rooms"] = rooms
    fl["outlines"] = outlines
    meta = fl.get("meta") or {}
    meta["rooms"] = len(rooms)
    meta["doors"] = len(doors)
    meta["wallSegments"] = len(segs_out)
    meta["wallRects"] = len(rects)
    meta["roomsOnly"] = rooms_only
    meta["withOutline"] = with_outline
    meta["thickness"] = THICKNESS
    fl["meta"] = meta
    return len(segs_out), len(rooms)


def main():
    with io.open(ASSET, encoding="utf-8") as f:
        data = json.load(f)
    with io.open(SOURCE, encoding="utf-8") as f:
        src = json.load(f)
    data["version"] = 2
    data["source"] = "analytic: college_map.json (кабинеты, двери, контуры), сетка 1 px"
    for fid, fl in data["floors"].items():
        segs, rooms = build_floor(fid, fl, src)
        print(f"floor {fid}: rooms {rooms}, segments {segs}")
    with io.open(ASSET, "w", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print("written", ASSET)
    return 0


if __name__ == "__main__":
    sys.exit(main())