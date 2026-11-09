"""Быстрая проверка сгенерированного плана: PNG без Android.

Рисует то же, что должен нарисовать слой карты: подложка, коридоры, кромки
кабинетов, проёмы, окна и лестницы. Нужен, чтобы увидеть ошибки геометрии
до сборки приложения.
"""

import json
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "assets", "map_walls.json")
OUT = os.path.join(HERE, "..", "_reports", "wave11-final", "shots")
SCALE = 1.0

PAPER = (255, 255, 255)
CORRIDOR = (238, 240, 244)
WALL = (55, 62, 72)
WINDOW = (120, 165, 215)
STAIR = (150, 158, 168)
LABEL = (40, 46, 56)


def main():
    data = json.load(open(SRC, encoding="utf-8"))
    w = int(data["plan"][0] * SCALE)
    h = int(data["plan"][1] * SCALE)
    os.makedirs(OUT, exist_ok=True)
    for fid, floor in data["floors"].items():
        img = Image.new("RGB", (w, h), PAPER)
        d = ImageDraw.Draw(img)

        # коридоры
        for cor in floor["corridors"]:
            pts = [(p[0] * SCALE, p[1] * SCALE) for p in cor["pts"]]
            cw = max(2, int(cor["w"] * SCALE))
            if len(pts) >= 2:
                d.line(pts, fill=CORRIDOR, width=cw, joint="curve")
                for p in pts:
                    d.ellipse([p[0] - cw / 2, p[1] - cw / 2, p[0] + cw / 2, p[1] + cw / 2], fill=CORRIDOR)

        # кромки кабинетов с вырезанными проёмами
        doors = {}
        for door in floor["doors"]:
            doors.setdefault(door["n"], []).append(door)
        for room in floor["rooms"]:
            x0, y0, x1, y1 = [v * SCALE for v in room["r"]]
            gaps = doors.get(room["n"], [])
            for side in ("n", "s", "w", "e"):
                segs = []
                if side in ("n", "s"):
                    y = y0 if side == "n" else y1
                    a, b = x0, x1
                    cuts = []
                    for g in gaps:
                        if g["side"] != side:
                            continue
                        mid = (x0 + x1) / 2 + g["c"] * SCALE
                        cuts.append((mid - g["w"] * SCALE / 2, mid + g["w"] * SCALE / 2))
                    cuts.sort()
                    cur = a
                    for c0, c1 in cuts:
                        if c0 > cur:
                            segs.append((cur, y, c0, y))
                        cur = max(cur, c1)
                    if cur < b:
                        segs.append((cur, y, b, y))
                else:
                    x = x0 if side == "w" else x1
                    a, b = y0, y1
                    cuts = []
                    for g in gaps:
                        if g["side"] != side:
                            continue
                        mid = (y0 + y1) / 2 + g["c"] * SCALE
                        cuts.append((mid - g["w"] * SCALE / 2, mid + g["w"] * SCALE / 2))
                    cuts.sort()
                    cur = a
                    for c0, c1 in cuts:
                        if c0 > cur:
                            segs.append((x, cur, x, c0))
                        cur = max(cur, c1)
                    if cur < b:
                        segs.append((x, cur, x, b))
                for s in segs:
                    d.line(s, fill=WALL, width=2)

        # окна
        for win in floor["windows"]:
            d.line([v * SCALE for v in win], fill=WINDOW, width=3)

        # лестницы
        for st in floor["stairs"]:
            x0, y0 = st["x"] * SCALE, st["y"] * SCALE
            x1, y1 = x0 + st["w"] * SCALE, y0 + st["h"] * SCALE
            d.rectangle([x0, y0, x1, y1], outline=STAIR, width=2)
            steps = 5
            for i in range(1, steps):
                if st["dir"] in (0, 180):
                    y = y0 + (y1 - y0) * i / steps
                    d.line([x0, y, x1, y], fill=STAIR, width=1)
                else:
                    x = x0 + (x1 - x0) * i / steps
                    d.line([x, y0, x, y1], fill=STAIR, width=1)

        # номера
        for room in floor["rooms"]:
            x0, y0, x1, y1 = [v * SCALE for v in room["r"]]
            label = room["n"]
            tw = d.textlength(label)
            d.text(((x0 + x1) / 2 - tw / 2, (y0 + y1) / 2 - 5), label, fill=LABEL)

        path = os.path.join(OUT, "plan-check-%s.png" % fid.replace("-", "_"))
        img.save(path)
        print("saved", path)


if __name__ == "__main__":
    main()