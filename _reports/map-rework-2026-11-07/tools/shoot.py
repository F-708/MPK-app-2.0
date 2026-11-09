"""Снимки прототипа карты в headless-браузере + проверка консоли."""

import pathlib
import sys

from playwright.sync_api import sync_playwright

BASE = pathlib.Path(r"E:\MPK app\MPK-app-2.1\_reports\map-rework-2026-11-07")
OUT = BASE / "screens"
URL = pathlib.Path(r"E:\MPK app\emulator-web-map\index.html").as_uri()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    problems = []

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        page = browser.new_page(viewport={"width": 1500, "height": 940}, device_scale_factor=2)
        page.on("console", lambda m: problems.append("console.%s: %s" % (m.type, m.text)) if m.type == "error" else None)
        page.on("pageerror", lambda e: problems.append("pageerror: %s" % e))

        page.goto(URL)
        page.wait_for_timeout(1200)

        stats = page.evaluate(
            """() => ({
                rooms: document.querySelectorAll('#layer-rooms rect').length,
                nums: document.querySelectorAll('#layer-roomnums text').length,
                stairs: document.querySelectorAll('.stair-hatch').length,
                doors: document.querySelectorAll('#layer-doors g').length,
                route: document.querySelectorAll('.route-line').length,
                badge: document.querySelector('#badge').innerText
            })"""
        )
        print("стартовое состояние:", stats)
        page.screenshot(path=str(OUT / "01-floor1-light.png"))

        page.click("#theme")
        page.wait_for_timeout(400)
        page.screenshot(path=str(OUT / "02-floor1-dark.png"))
        page.click("#theme")
        page.wait_for_timeout(300)

        page.fill("#q", "112")
        page.wait_for_timeout(500)
        page.screenshot(path=str(OUT / "03-search.png"))
        print("поиск:", page.evaluate("() => document.querySelector('#q-hint').innerText"))

        page.click("#zoom-fit")
        page.fill("#from", "101")
        page.fill("#to", "112")
        page.click("#go")
        page.wait_for_timeout(500)
        page.screenshot(path=str(OUT / "04-route-same-floor.png"))
        print("маршрут по этажу:", page.evaluate("() => document.querySelector('#route-out').innerText").replace("\n", " | "))

        page.fill("#to", "213")
        page.click("#go")
        page.wait_for_timeout(600)
        page.screenshot(path=str(OUT / "05-route-multifloor.png"))
        print("межэтажный:", page.evaluate("() => document.querySelector('#route-out').innerText").replace("\n", " | "))

        page.click("#floors button:nth-child(3)")
        page.wait_for_timeout(500)
        page.fill("#from", "301")
        page.fill("#to", "307")
        page.click("#go")
        page.wait_for_timeout(500)
        page.screenshot(path=str(OUT / "06-floor34-route.png"))
        print("этаж 3-4:", page.evaluate("() => document.querySelector('#route-out').innerText").replace("\n", " | "))

        page.click("#zoom-in")
        page.click("#zoom-in")
        page.wait_for_timeout(500)
        page.screenshot(path=str(OUT / "07-zoom.png"))

        browser.close()

    if problems:
        print("\nПРОБЛЕМЫ В КОНСОЛИ:")
        for p in problems[:15]:
            print("  ", p)
        return 1
    print("\nконсоль чистая, ошибок нет")
    for f in sorted(OUT.glob("*.png")):
        print("  %-32s %d КБ" % (f.name, f.stat().st_size // 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())