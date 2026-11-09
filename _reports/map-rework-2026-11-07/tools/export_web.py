"""Сборка данных для веб-прототипа карты.

Источники:
  app/src/main/assets/college_map.json        — кабинеты, коридоры, входы, подписи
  _reports/.../data/map_walls.json            — стены, лестницы, двери (трассировка)

Результат: emulator-web-map/map-data.js  (window.MAPDATA, координаты в пикселях растра)
"""

import json
import pathlib

ROOT = pathlib.Path(r"E:\MPK app\MPK-app-2.1")
WEB = pathlib.Path(r"E:\MPK app\emulator-web-map")
W, H = 1376, 768

FLOOR_TITLES = {
    "1": "1 этаж",
    "2": "2 этаж",
    "3-4": "3 и 4 этажи",
}


def load():
    semantic = json.loads((ROOT / "app" / "src" / "main" / "assets" / "college_map.json").read_text(encoding="utf-8"))
    traced = json.loads((ROOT / "_reports" / "map-rework-2026-11-07" / "data" / "map_walls.json").read_text(encoding="utf-8"))
    return semantic, traced


def stair_groups(semantic):
    """Лестницы разных этажей связываются по близости координат (допуск 0.02)."""
    groups = []
    for floor_id, floor in semantic["floors"].items():
        for entrance in floor.get("entrances", []):
            if entrance.get("kind") != "stairs":
                continue
            hit = None
            for group in groups:
                if abs(group["x"] - entrance["x"]) < 0.02 and abs(group["y"] - entrance["y"]) < 0.02:
                    hit = group
                    break
            if hit is None:
                hit = {"x": entrance["x"], "y": entrance["y"], "floors": {}}
                groups.append(hit)
            hit["floors"][floor_id] = entrance["id"]
    for index, group in enumerate(groups):
        group["id"] = "st%d" % (index + 1)
    return groups


def build_floor(floor_id, semantic_floor, traced_floor, groups, titles):
    rooms = []
    for room in semantic_floor.get("rooms", []):
        door = room.get("door")
        rooms.append(
            {
                "id": room["id"],
                "n": room["number"],
                "x": round(room["x"], 5),
                "y": round(room["y"], 5),
                "w": round(room["w"], 5),
                "h": round(room["h"], 5),
                "d": [round(door[0], 5), round(door[1], 5)] if door else None,
                "s": room.get("status", "normal"),
                "t": titles.get(room["id"], ""),
                "sc": room.get("numScale", 1),
            }
        )

    walls = list(traced_floor["walls"])

    labels = [
        {"x": round(label["x"], 5), "y": round(label["y"], 5), "t": label["text"], "size": label.get("size", 16)}
        for label in semantic_floor.get("labels", [])
        if label.get("text")
    ]

    stairs = [[s["x"], s["y"], s["w"], s["h"]] for s in traced_floor["stairs"]]

    # проёмы дверей измерены по растру: gap = границы проёма в пикселях
    doors = [
        {
            "x": d["x"],
            "y": d["y"],
            "side": d["side"],
            "g": d.get("gap"),
            "room": d["roomId"],
        }
        for d in traced_floor.get("doors", [])
    ]

    entrances = []
    for entrance in semantic_floor.get("entrances", []):
        group = next(
            (g for g in groups if g["floors"].get(floor_id) == entrance["id"]),
            None,
        )
        entrances.append(
            {
                "id": entrance["id"],
                "x": round(entrance["x"], 5),
                "y": round(entrance["y"], 5),
                "kind": entrance.get("kind", "backup"),
                "title": entrance.get("title", ""),
                "usable": entrance.get("usable", True),
                "st": group["id"] if group else None,
            }
        )

    corridors = [
        [[round(p[0], 5), round(p[1], 5)] for p in corridor.get("points", [])]
        for corridor in semantic_floor.get("corridors", [])
        if len(corridor.get("points", [])) >= 2
    ]

    return {
        "rooms": rooms,
        "walls": walls,
        "labels": labels,
        "stairs": stairs,
        "doors": doors,
        "entrances": entrances,
        "corridors": corridors,
        "alignment": traced_floor["alignment"],
    }


def main():
    semantic, traced = load()
    groups = stair_groups(semantic)

    titles = {}
    for floor in semantic["floors"].values():
        for callout in floor.get("callouts", []):
            text = (callout.get("text") or "").strip()
            if text and callout.get("targetId"):
                titles.setdefault(callout["targetId"], text)

    floors = {}
    for floor_id in ("1", "2", "3-4"):
        floors[floor_id] = build_floor(
            floor_id,
            semantic["floors"][floor_id],
            traced["floors"][floor_id],
            groups,
            titles,
        )

    data = {
        "w": W,
        "h": H,
        "version": 1,
        "floors": floors,
        "order": ["1", "2", "3-4"],
        "titles": FLOOR_TITLES,
        "stairs": [{"id": g["id"], "x": round(g["x"], 5), "y": round(g["y"], 5), "floors": g["floors"]} for g in groups],
    }

    WEB.mkdir(parents=True, exist_ok=True)
    body = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    (WEB / "map-data.js").write_text("window.MAPDATA=" + body + ";\n", encoding="utf-8")

    total_rooms = sum(len(f["rooms"]) for f in floors.values())
    total_walls = sum(len(f["walls"]) // 4 for f in floors.values())
    print("этажей: 3, кабинетов: %d, прямоугольников стен: %d" % (total_rooms, total_walls))
    print("лестничных групп: %d" % len(groups))
    print("map-data.js: %d КБ" % ((WEB / "map-data.js").stat().st_size // 1024))
    for floor_id, floor in floors.items():
        print(
            "  этаж %-4s кабинетов=%-3d стен=%-4d лестниц(маршей)=%-2d входов=%d коридоров=%d"
            % (
                floor_id,
                len(floor["rooms"]),
                len(floor["walls"]) // 4,
                len(floor["stairs"]),
                len(floor["entrances"]),
                len(floor["corridors"]),
            )
        )


if __name__ == "__main__":
    main()