# promo/video — Remotion-трейлер «Мой Политех» v1.0

30с (900 кадров, 30fps), сценарий: `promo/SCRIPT_LOCKED_v1.0.md` (вариант А, v0.4, 10 сцен A1–A10).

## Запуск

```bash
npm install
npm run sync      # скопировать promo/shots/* → public/shots/ + записать shots.json
npm run studio    # превью
npm run render    # Promo9x16 → out/promo_a_9x16.mp4 (H.264, CRF 18)
npm run render:16x9
npm run qa        # стиллы ключевых кадров в out/qa/
```

`npm run sync` встроен в studio/render/qa — просто кидайте кадры в `promo/shots/`.

## Ожидаемые имена кадров (promo/shots/, png или mp4)

| Имя | Сцена |
|---|---|
| `a1_schedule_today` | A1 hook |
| `a2_map_route` | A2 карта (поверх — оверлей шевронов) |
| `a2_map_constructor` | A2 врезка конструктора |
| `a3_lessons` | A3 уроки, слоты Сегодня/Завтра |
| `a4_bells_timeline` | A4 звонки, таймлайн (поверх — бегущая точка) |
| `a5_teachers_list` | A5 список преподавателей |
| `a5_teacher_card` | A5 карточка «ведёт у вас» |
| `a6_grades` | A6 оценки, нампад + средний |
| `a7_task_dialog` | A7 диалог задания |
| `a8_materials_list` | A8 материалы, список |
| `a8_material_pdf` | A8 открытый PDF |
| `icon` | A10 финал (иначе заглушка-эмблема «П», TODO) |

Отсутствующий кадр → серая заглушка с именем ( Shot.tsx ). Если рядом лежит `.mp4`
с тем же именем — возьмётся он (OffthreadVideo). A3-виджет, A4-б/в (шторка «до звонка»,
виджет), A7-пуш — нарисованные реплики системного хрома, кадры не нужны.

## Раскладка

- `src/Root.tsx` — композиции Promo9x16 (1080×1920) и Promo16x9 (1920×1080); `calculateMetadata`
  грузит манифест `shots.json` (fetch(staticFile) — работает и в Studio, и в рендере).
- `src/Video.tsx` — `<Series>` A1..A10: 60/120/120/120/90/90/90/90/60/60 = 900 + `<Audio>` teaser_v1.wav.
- `src/scenes/S01..S10` — по сцене на файл; `src/components/` — мокап, титр, выноски,
  Ken Burns, шевроны/точка (параметры как в приложении: 56px шаг, 12px/с, пульс 900мс Reverse),
  реплики хрома Android (виджеты по widget_*.xml, WidgetStyle.LIGHT).
