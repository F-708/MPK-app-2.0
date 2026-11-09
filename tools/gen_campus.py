"""Раскладка кампуса вокруг вписанного плана.

План занимает центральный прямоугольник (0,0)-(1376,768). Окрестности живут в
расширенном мире вокруг него и рисуются тем же трансформом, что и план, поэтому
стадион, авеню, гаражи и мастерская остаются на своих местах при любом этаже.

Запуск: python tools/gen_campus.py
Пишет app/src/main/assets/campus.json (version 2, UTF-8).
"""

import io
import json
import sys

ASSET = r"E:\MPK app\MPK-app-2.1\app\src\main\assets\campus.json"

PLAN_W = 1376.0
PLAN_H = 768.0
WORLD_L = -780.0
WORLD_T = -1300.0
WORLD_R = 2160.0
WORLD_B = 2200.0


def rect(x0, y0, x1, y1):
    return [[x0, y0], [x1, y0], [x1, y1], [x0, y1]]


def shape(ident, kind, poly, **extra):
    out = {"id": ident, "kind": kind, "polygon": poly}
    out.update(extra)
    return out


def layer_lawn():
    return [shape("lawn", "lawn", rect(WORLD_L, WORLD_T, WORLD_R, WORLD_B))]


def layer_distant():
    out = []
    # Академия наук: серая ступенчатая структура на дальнем плане.
    ax, ay = 520.0, 1760.0
    steps = [
        (0.0, 40.0, 560.0, 150.0),
        (60.0, 0.0, 440.0, 60.0),
        (140.0, 150.0, 280.0, 60.0),
        (90.0, 210.0, 380.0, 50.0),
    ]
    for i, (dx, dy, w, h) in enumerate(steps):
        out.append(
            shape(
                f"academy-{i}",
                "distant",
                rect(ax + dx, ay + dy, ax + dx + w, ay + dy + h),
            )
        )
    far = [
        ("far-1", -700.0, 1860.0, 210.0, 130.0),
        ("far-2", -400.0, 1900.0, 180.0, 110.0),
        ("far-3", 1200.0, 1840.0, 220.0, 140.0),
        ("far-4", 1520.0, 1890.0, 190.0, 120.0),
        ("far-5", 1820.0, 1860.0, 240.0, 150.0),
        ("far-6", 60.0, 1950.0, 200.0, 120.0),
    ]
    for ident, x, y, w, h in far:
        out.append(shape(ident, "distant", rect(x, y, x + w, y + h)))
    return out


def layer_paths():
    return [
        shape("path-stadium", "path", rect(455.0, -200.0, 490.0, 0.0)),
        shape("path-north-yard", "path", rect(300.0, -200.0, 455.0, -170.0)),
        shape("path-north-yard-2", "path", rect(300.0, -760.0, 330.0, -170.0)),
        shape("path-east", "path", rect(1376.0, 390.0, 1420.0, 418.0)),
        shape("path-east-2", "path", rect(1420.0, 600.0, 1450.0, 780.0)),
        shape("path-south-yard", "path", rect(200.0, 768.0, 230.0, 810.0)),
        shape("path-south-yard-2", "path", rect(-420.0, 760.0, 200.0, 790.0)),
        shape("path-west", "path", rect(-420.0, 380.0, -60.0, 410.0)),
        shape("path-west-2", "path", rect(-60.0, 410.0, -30.0, 760.0)),
        shape("path-parking", "path", rect(40.0, 700.0, 70.0, 810.0)),
    ]


def layer_road():
    return [
        shape("avenue", "road", rect(WORLD_L, 1000.0, WORLD_R, 1110.0)),
        shape("driveway", "road", rect(540.0, 768.0, 610.0, 1000.0)),
    ]


def layer_sidewalk():
    return [
        shape("walk-north", "sidewalk", rect(WORLD_L, 976.0, WORLD_R, 1000.0)),
        shape("walk-south", "sidewalk", rect(WORLD_L, 1110.0, WORLD_R, 1134.0)),
        shape("walk-driveway-w", "sidewalk", rect(516.0, 768.0, 540.0, 976.0)),
        shape("walk-driveway-e", "sidewalk", rect(610.0, 768.0, 634.0, 976.0)),
    ]


def layer_markings():
    out = []
    dash_y0, dash_y1 = 1052.0, 1057.0
    x = WORLD_L + 20.0
    i = 1
    while x < WORLD_R - 40.0:
        out.append(shape(f"dash-{i}", "dash", rect(x, dash_y0, x + 60.0, dash_y1)))
        x += 120.0
        i += 1
    # Разметка стояночных мест.
    sx = 90.0
    j = 1
    while sx < 500.0:
        out.append(shape(f"stall-{j}", "stall", rect(sx, 860.0, sx + 4.0, 1000.0)))
        sx += 70.0
        j += 1
    # Зебра через авеню у проезда.
    k = 0
    while k < 6:
        out.append(
            shape(
                f"zebra-{k}",
                "stall",
                rect(548.0 + k * 12.0, 1010.0, 554.0 + k * 12.0, 1100.0),
            )
        )
        k += 1
    return out


def layer_parking():
    return [shape("park-south", "parking", rect(60.0, 820.0, 520.0, 1000.0))]


def layer_buildings():
    return [
        {"id": "hostel", "kind": "building", "roofTone": "red",
         "polygon": rect(1450.0, 60.0, 1690.0, 220.0)},
        {"id": "gym", "kind": "building", "roofTone": "red",
         "polygon": rect(1450.0, 520.0, 1680.0, 660.0)},
        {"id": "annex-west", "kind": "building", "roofTone": "red",
         "polygon": rect(-680.0, 260.0, -420.0, 440.0)},
        {"id": "annex-south", "kind": "building", "roofTone": "red",
         "polygon": rect(700.0, 1400.0, 900.0, 1540.0)},
        {"id": "house-north-1", "kind": "building", "roofTone": "gray",
         "polygon": rect(250.0, -1180.0, 420.0, -1020.0)},
        {"id": "house-north-2", "kind": "building", "roofTone": "gray",
         "polygon": rect(980.0, -1160.0, 1180.0, -1000.0)},
        {"id": "house-west", "kind": "building", "roofTone": "gray",
         "polygon": rect(-720.0, 900.0, -540.0, 1040.0)},
        {"id": "house-south", "kind": "building", "roofTone": "gray",
         "polygon": rect(1100.0, 1260.0, 1290.0, 1400.0)},
    ]


def layer_garages():
    out = []
    for i in range(8):
        x = 1430.0 + i * 36.0
        out.append(shape(f"garage-{i}", "garage", rect(x, 760.0, x + 28.0, 804.0)))
    return out


def layer_workshop():
    return [
        shape("workshop-main", "workshop", rect(1420.0, 300.0, 1600.0, 400.0)),
        shape("workshop-shed", "workshop", rect(1420.0, 400.0, 1500.0, 460.0)),
    ]


def layer_trees():
    spots = [
        # ряд вдоль северного газона и стадиона
        (150.0, -640.0), (240.0, -700.0), (330.0, -640.0), (880.0, -640.0),
        (960.0, -700.0), (1050.0, -640.0), (120.0, -900.0), (400.0, -960.0),
        (860.0, -980.0), (1290.0, -880.0), (1300.0, -600.0), (60.0, -480.0),
        # западная сторона
        (-640.0, 60.0), (-520.0, 120.0), (-700.0, 560.0), (-560.0, 620.0),
        (-420.0, 0.0), (-460.0, 700.0), (-300.0, -60.0), (-180.0, 140.0),
        # восточная сторона
        (1800.0, 120.0), (1900.0, 320.0), (1780.0, 460.0), (1900.0, 640.0),
        (1760.0, 900.0), (1880.0, 1160.0), (1600.0, 960.0), (1380.0, 120.0),
        # южная сторона и парковка
        (-240.0, 1180.0), (-60.0, 1220.0), (140.0, 1180.0), (640.0, 1200.0),
        (820.0, 1180.0), (1000.0, 1240.0), (1400.0, 1200.0), (1700.0, 1520.0),
        (300.0, 1600.0), (60.0, 1620.0), (1180.0, 1620.0), (1500.0, 1700.0),
    ]
    out = []
    for i, (x, y) in enumerate(spots):
        r = 34.0 + (i * 7) % 22
        kind = "tree-dark" if i % 3 == 0 else "tree-light"
        out.append({"x": x, "y": y, "r": float(r), "kind": kind})
    return out


def main():
    data = {
        "version": 2,
        "canvas": [PLAN_W, PLAN_H],
        "world": [WORLD_L, WORLD_T, WORLD_R, WORLD_B],
        "lawn": layer_lawn(),
        "distant": layer_distant(),
        "paths": layer_paths(),
        "road": layer_road(),
        "sidewalk": layer_sidewalk(),
        "markings": layer_markings(),
        "parking": layer_parking(),
        "buildings": layer_buildings(),
        "garages": layer_garages(),
        "workshop": layer_workshop(),
        "stadium": {
            "ellipse": [520.0, -370.0, 300.0, 170.0],
            "innerField": [520.0, -370.0, 238.0, 118.0],
        },
        "trees": layer_trees(),
        "bed": rect(0.0, 0.0, PLAN_W, PLAN_H),
    }
    with io.open(ASSET, "w", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print("campus.json:", len(data["trees"]), "деревьев,", len(data["buildings"]), "зданий")
    return 0


if __name__ == "__main__":
    sys.exit(main())