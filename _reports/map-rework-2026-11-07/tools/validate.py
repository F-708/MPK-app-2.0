"""Проверка целостности векторного слоя и сводка покрытия."""

import json
import pathlib

ROOT = pathlib.Path(r"E:\MPK app\MPK-app-2.1")
W, H = 1376, 768


def main():
    semantic = json.loads((ROOT / "app" / "src" / "main" / "assets" / "college_map.json").read_text(encoding="utf-8"))
    traced = json.loads((ROOT / "_reports" / "map-rework-2026-11-07" / "data" / "map_walls.json").read_text(encoding="utf-8"))

    problems = []
    total_rooms = 0
    total_real = 0
    total_doors = 0
    total_walls = 0
    total_stairs = 0
    wall_px = 0

    for floor_id, floor in traced["floors"].items():
        rooms = semantic["floors"][floor_id]["rooms"]
        ids = {r["id"] for r in rooms}
        total_rooms += len(rooms)
        real = [r for r in rooms if r.get("status") != "nonexistent"]
        total_real += len(real)

        for door in floor["doors"]:
            if door["roomId"] not in ids:
                problems.append("дверь ссылается на неизвестный кабинет: %s" % door["roomId"])
        with_door = {d["roomId"] for d in floor["doors"]}
        total_doors += len(with_door)

        flat = floor["walls"]
        for index in range(0, len(flat), 4):
            x0, y0, x1, y1 = flat[index:index + 4]
            if not (0 <= x0 < x1 <= W and 0 <= y0 < y1 <= H):
                problems.append("прямоугольник вне холста на этаже %s: %s" % (floor_id, [x0, y0, x1, y1]))
            wall_px += (x1 - x0) * (y1 - y0)
        total_walls += len(flat) // 4
        total_stairs += len(floor["stairs"])

        # кабинеты без собственной двери в данных
        no_door = [r["number"] for r in real if r["id"] not in with_door]
        print(
            "этаж %-4s кабинетов=%-3d реальных=%-3d со створкой=%-3d без двери=%-2d стен=%-4d лестниц=%d"
            % (floor_id, len(rooms), len(real), len(with_door), len(no_door), len(flat) // 4, len(floor["stairs"]))
        )
        if no_door:
            print("        без двери:", ", ".join(no_door[:12]))

    size = (ROOT / "_reports" / "map-rework-2026-11-07" / "data" / "map_walls.json").stat().st_size
    web = pathlib.Path(r"E:\MPK app\emulator-web-map")
    print()
    print("итого кабинетов: %d (реальных %d), стен: %d, дверей: %d, маршей: %d" % (total_rooms, total_real, total_walls, total_doors, total_stairs))
    print("площадь стен: %d px (%d полных квадратов, доля холста %.1f%%)" % (wall_px, wall_px, 100.0 * wall_px / (W * H)))
    print("map_walls.json: %d КБ" % (size // 1024))
    for name in ("index.html", "map-data.js"):
        path = web / name
        print("%-14s %d КБ" % (name, path.stat().st_size // 1024))

    if problems:
        print("\nПРОБЛЕМЫ:")
        for p in problems[:20]:
            print("  ", p)
        return 1
    print("\nцелостность в порядке")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())