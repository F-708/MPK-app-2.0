# REVIEW-PLAN — план исправлений по итогам полного ревью (2026-10-01)

> Источник истины по исправлениям. Детали — в `_reports/code-review-2026-10-01/` (01…09 + 00-SUMMARY).
> Режим: автономный менеджер, волны по 2-3 задачи на НЕпересекающихся файлах, после каждой волны
> `sh ./gradlew :app:compileStandardDebugKotlin` + `:app:testStandardDebugUnitTest`, коммит на блок.
> Скиллы: см. `.claude/skills/mpk-dev` (маршрутизация) и `mpk-build` (гейты).

## Принципы волн

1. **Сначала остановить кровь** (критичные баги функциональности), потом фундамент (архитектура), потом косметика.
2. **Одна волна = 2-3 задачи на непересекающихся файлах** (bun-ограничение, [[mpk-agent-orchestration-limits]]).
3. Каждая волна: компиляция → тесты → коммит. Не копить незакоммиченное.
4. Баг-фикс = root cause в общей функции, не патч в каждом вызывающем (ponytail).
5. **Навигацию (ARC-01) и обновление Compose (BSP-02) — отдельными крупными волнами**, не мешать с баг-фиксами (меняют слишком много).

## Статус жалоб заказчика (5 штук)

| # | Жалоба | Root cause | Отчёт | Волна |
|---|---|---|---|---|
| 1 | Расписание на 1 октября не синхронизируется | URL-кандидаты не учитывают месяц ЗАГРУЗКИ + суффикс `-1`; event-страницы с iframe не используются; `generateCandidateEventUrls` мёртв | 01 SCH-01 (ДОКАЗАНО curl) | **A** |
| 2 | Карта: после 2 выборов кабинета 3-й не нажимается | `when()`-навигация пересоздаёт поддерево + `target=remember(initialTarget)` петля `onRoomChanged→mapRoom→initialRoom`; RoomPanel меняет viewH → рестарт pointerInput | 03 MAP (агент дописывает) + 08 ARC-01 | **B** |
| 3 | У всех преподавателей прочерки в контактах | В `teachers.csv` буквально «—» (U+2014) в 89/100 строк; `isNotBlank()` пропускает; шильдик рисуется | 06 TEA-01 (ПОДТВЕРЖДЕНО) | **B** |
| 4 | Виджеты редко обновляются / глючат | Exact-alarm не выдан на 14+ → inexact; FGS убивают OEM; виджет «СЕГОДНЯ» показывает последнюю дату, не сегодня | 04 WID-01/02 | **A** |
| 5 | Звонки глючат | Хардкод CollegeBellSchedule (сокращённые дни не синхронизируются); exact-alarm/FGS (общее с виджетами) | 05 BEL-01/02/03 | **C** |

---

## ВОЛНА A — критичные баги функциональности (остановить кровь)

Непересекающиеся файлы: сеть (MpkNetworkClient+ScheduleRepository+Parser) / виджеты (widget/*) — разные поддеревья.

### A1. Расписание: ScheduleUrlResolver + event-страницы [SCH-01, SCH-02]
**Файлы**: `data/network/MpkNetworkClient.kt`, `data/network/MpkScheduleParser.kt` (extractDocumentUrls уже умеет iframe), `data/repository/ScheduleRepository.kt` (fetchAllGroupsForDate, fetchScheduleForGroup, fetchTeacherSchedule).
**Что делать**:
1. Новый `generateCandidateDirectUrls`: месяц даты И предыдущий месяц × суффиксы {«», «-1», «-2»} × {.doc, .docx}.
2. Если прямые 404: скачать event-страницы из `generateCandidateEventUrls(calendar)` (оживить мёртвый код) → `extractDocumentUrls(eventHtml)` → скачать `.doc`.
3. Портал-фолбэк: из HTML портала брать event-ссылки `/raspisanie/<dd-mm-yyyy>-raspisanie-uchashhihsya/?occurrence=`, для каждой — шаг 2.
4. Вынести всё в `ScheduleUrlResolver` (один объект, 3 вызывающих) — закрывает и SCH-02 (преподаватели).
**Тест**: `ScheduleEventUrlTest` — фикстура event-HTML с iframe → ожидаемый `.doc`. Прогнать `LiveNetworkSmokeTest` вживую как приёмку (сегодня 01.10 — идеальный кейс).
**Приёмка**: curl-проверка из отчёта 01: реальный док `/2026/09/01.10.2026-...-1.doc` найден и разобран; 1 октября показывает расписание.

### A2. Виджет «СЕГОДНЯ»: показывать сегодня, не последнюю дату [WID-01]
**Файлы**: `widget/NowNextWidgetProvider.kt` (`WidgetData.todayLessons`), `widget/TodayScheduleWidgetProvider.kt`.
**Что делать**: в `todayLessons` фильтровать `dateString == todayString ИЛИ dateString.isBlank()`, НЕ `== latestMillis`. Нет данных на сегодня → пустой список (честное «Уроков нет / обновите»), не чужой день.
**Тест**: `TodayLessonsDateTest` — в БД только прошлая дата → todayLessons возвращает пусто.

### A3. Воркер не сдаётся на весь день [WID-03]
**Файлы**: `worker/ScheduleCheckWorker.kt`, `data/repository/ScheduleRepository.kt` (протащить documentFound в результат syncScheduleForDate).
**Что делать**: `checkedKey` ставить ТОЛЬКО при documentFound=true. Пустой результат из-за «документ не найден» ≠ «группы нет в документе».
**Зависимость**: A3 зависит от A1 (после A1 documentFound станет точным). Делать A3 после/вместе с A1.
**Тест**: `ScheduleCheckWorkerTest` — documentFound=false → checkedKey НЕ ставится.

> Волна A — 3 задачи, но A1 и A3 пересекаются по ScheduleRepository → **A1+A3 один агент (сеть+воркер), A2 другой агент (виджеты)**. Итого 2 агента, файлы не пересекаются.

---

## ВОЛНА B — жалоба 2 и 3 (карта + контакты), low-risk

### B1. Контакты: нормализация прочерка в парсере [TEA-01, TEA-03, TEA-04]
**Файлы**: `data/repository/TeachersRepository.kt` (парсер). UI НЕ трогаем — `hasContacts`(CollegeScreen:1369) заработает сам.
**Что делать**: одна функция `cell(raw): String = raw.trim().let{ if(it in setOf("—","-","–","")) "" else it }` применить к room/phone/email при парсинге. НЕ трогать дефисы внутри телефонов (`338-33-42` — только ячейка ЦЕЛИКОМ равна «—» → пусто).
**Тест**: `TeachersCsvParseTest` — строка с «—» → phone/email пустые; `hasContacts` false.

### B2. teachers.csv: строки 48, 93 с 11 полями [TEA-02]
**Файлы**: `data/repository/TeachersRepository.kt` (тот же парсер — объединить с B1 в одного агента).
**Что делать**: 99 строк = 10 полей, 2 строки (48, 93) = 11 полей (лишняя `;`). Парсер наивный split(';') → колонки съезжают (стаж/категория = мусор). Либо починить данные в CSV (убрать лишнюю `;`), либо парсер брать фиксированные индексы 0-5 и игнорировать хвост. **Lazy-вариант: починить CSV** (2 строки) + тест-гейт «все строки = 10 полей».
**Проверить**: что реально в колонках 6-9 этих строк (см. 06 TEA-02).

### B3. Карта: фикс «третьего клика» [MAP-02 + MAP-03, отчёт 03 готов]
**Файлы**: `ui/screens/CollegeMapScreen.kt`.
**Root cause (отчёт 03, ранжировано)**:
- **MAP-02 (высокая уверенность)**: hit-test тапа (:1076-1078) читает `scale.value`/`offsetX.value` ВО ВРЕМЯ зум-анимации (LaunchedEffect :1047: zoom-in 420мс → delay 600 → zoom-out 540мс = ~1.5с полёта). Тап в середине анимации → координаты мапятся неверно → промах. Фикс: hit-test по `scale.targetValue`/`offset.targetValue` (3-5 строк).
- **MAP-03 (средняя)**: RoomPanel появляется/меняет высоту → viewH у MapCanvas(weight(1f)) меняется → `pointerInput(floorId, viewW, viewH)` рестартует детектор → тап в момент рестарта теряется. Фикс: фиксированная высота RoomPanel ИЛИ убрать viewW/viewH из ключей pointerInput.
- **MAP-01 (фон)**: binding-петля `target`↔`mapRoom` (:196-197 ↔ CollegeScreen:227,231) — `remember(initialTarget)` пересоздаёт state на каждое эхо. Фикс: target — единственный источник, initialRoom читать только при входе.
**Приёмка**: 3+ последовательных тапа по разным кабинетам в быстром темпе — каждый открывается. Расширить MapScreenShotTest / проверить на устройстве.
**ВНИМАНИЕ**: системный фикс = Navigation-миграция (E4), здесь — тактика.

### B4. UI расписания: «сегодня» после полуночи + честные ошибки синка [SUI-01, SUI-02, SUI-03]
**Файлы**: `ui/screens/ScheduleScreen.kt`, `ui/screens/GroupScheduleScreen.kt`, `ui/components/CalendarArchiveDialog.kt`, `ui/components/SimpleDatePickerDialog.kt`, `ui/screens/MainScreen.kt` (проброс hasSyncError).
**Что делать**:
1. SUI-01: `realNow`/`tomorrow` — `remember { Calendar.getInstance() }` без ключа дня → после полуночи показывается вчерашний день. Фикс-паттерн: ключ = epochDay, обновление при ON_RESUME (lifecycle). Аналог в CollegeMapScreen:147,214 (MAP-05) чинит агент B3 тем же паттерном — файлы не пересекаем.
2. SUI-02: hasSyncError из AppViewModel не проброшен в UI → сетевой сбой выглядит как «не опубликовано». Пробросить + различать тексты.
3. SUI-03: currentLessonNumber remember-ключ без времени — метка «идёт урок» залипает.
**Пересечение**: ScheduleScreen правит и волна A? НЕТ — A не трогает UI-экраны. B4 НЕ пересекается с B1-B3 (те в CollegeMapScreen/TeachersRepository).

> Волна B: B1+B2 — один агент (TeachersRepository+CSV), B3+B4 — ВТОРОЙ и ТРЕТИЙ агенты (CollegeMapScreen / ScheduleScreen+диалоги — разные файлы). Не пересекаются.

---

## ВОЛНА C — надёжность звонков/виджетов (общая проблема exact-alarm + FGS)

### C1. WorkManager-watchdog + рестарт FGS [BEL-03, WID-02]
**Файлы**: `worker/MpkWorkManagerHelper.kt`, новый `worker/WidgetWatchdogWorker.kt`, `widget/BellCountdownWidgetProvider.kt` (WidgetAlarmReceiver).
**Что делать**: периодический воркер (15 мин): виджеты на экране && `isEnabled && !BellTimerService.isRunning` → рестарт. В `WidgetAlarmReceiver`: при `isEnabled` обновлять countdown, если `!isRunning` (одна строка, сейчас обновляет только NowNext).

### C2. `updatePeriodMillis` как бесплатная страховка [WID-02]
**Файлы**: `app/src/main/res/xml/today_schedule_widget_info.xml`, `bells_widget_info.xml`, `now_next_widget_info.xml`.
**Что делать**: поднять `updatePeriodMillis` с 0 до 1800000 (30 мин) у Today/Bells — система сама дёрнет onUpdate, они не «мигают» (меняются раз в урок).

### C3. Экран «включите точные будильники» [BEL-02, WID-02]
**Файлы**: `ui/screens/SettingsScreen.kt` (+ возможно новый диалог).
**Что делать**: при `!canScheduleExactAlarms()` (Android 12+) — строка в настройках с deep-link `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Без этого пользователь не понимает, почему отсчёт «плавает».
**Вопрос заказчику**: добавлять ли `USE_EXACT_ALARM` (не отзывается, но Play-политика) — для будильника допустимо.

### C4. Дневной кэш lastGroupLesson [BEL-04, WID-04]
**Файлы**: `widget/BellCountdownWidgetProvider.kt` (`lastGroupLessonForToday`).
**Что делать**: убрать `HH:mm` из cacheKey → ключ (группа|dow|дата). Один Room-запрос в день вместо 1440.
**Пересечение**: C1 и C4 оба в BellCountdownWidgetProvider → C1+C4 один агент.

> Волна C: C1+C4 (BellCountdownWidgetProvider+worker) / C2 (xml) / C3 (SettingsScreen) — 3 агента, не пересекаются.

---

## ВОЛНА D — слой данных (индексы + миграция)

### D1. Индексы на `lessons` + MIGRATION_2_3 [DAT-01]
**Файлы**: `data/local/entity/LessonEntity.kt` (@Entity indices), `data/local/MpkDatabase.kt` (version=3, MIGRATION_2_3), `data/local/dao/LessonDao.kt` (не меняется, но проверить запросы).
**Что делать**: индексы (groupName,dayOfWeek), (groupName,dateString), (dateString), (roomFirst), (roomSecond). MIGRATION_2_3 с `CREATE INDEX IF NOT EXISTS` — имена ПОБАЙТОВО как Room ожидает (`index_lessons_groupName_dayOfWeek`).
**ОБЯЗАТЕЛЬНО**: `MigrationV2ToV3Test` по образцу MigrationV1ToV2Test. **БЕЗ ТЕСТА НЕ ДЕЛАТЬ** — расхождение имён = crash при обновлении у всех пользователей (fallbackToDestructiveMigration сотрёт student_tasks).

### D2. Недельные шаблоны: replace вместо insert [DAT-02]
**Файлы**: `data/local/dao/LessonDao.kt` (новый replaceWeeklyTemplate), `data/repository/ScheduleRepository.kt` (replaceSyncedLessons).
**Сначала диагностика**: есть ли вообще строки dateString="" в живой БД/парсере (applyHeaderDate перештамповывает даты). Если ветка мертва — закрыть как «не баг».
**Пересечение**: D2 и A1/A3 оба в ScheduleRepository → НЕ в одну волну с A. D2 отдельно, после A.

### D3. exportSchema=true [DAT-03]
**Файлы**: `data/local/MpkDatabase.kt`, `app/build.gradle.kts` (ksp room.schemaLocation), схемы в git.
**Что делать**: включить экспорт схемы — MigrationTestHelper сможет валидировать миграции автоматически. Страховка для D1.

---

## ВОЛНА E — архитектура (фундамент, ПОСЛЕ стабилизации багов)

### E1. Application-класс + AppGraph [ARC-05, ARC-02]
**Файлы**: новый `MpkApplication.kt`, `AndroidManifest.xml` (android:name), все места `remember{Repository(...)}` → AppGraph.
**Что делать**: `class MpkApplication : Application()` — инициализация (каналы, WorkManager, тема). `object AppGraph` — шареные репозитории (ScheduleRepository с ЕДИНЫМ набором deps: catalogProvider + teacherLessonDao). Устраняет неконсистентность (CollegeMapScreen без teacherLessonDao) + повторное чтение ассетов.

### E2. IO на main → produceState [ARC-03]
**Файлы**: `ui/screens/StudentsScreens.kt`, `ui/screens/CollegeScreen.kt` (TeachersRepository в remember).
**Что делать**: `produceState{ value = withContext(IO){ repo.load() } }` вместо блокирующего `remember{ repo.loadStudents() }` (800 КБ CSV на main = ANR). Кэш в репозитории.

### E3. dow-конверсия + prefs-обёртки: убрать дубли [стиль]
**Файлы**: 12 файлов с `Calendar.MONDAY -> 1` (18 дублей) → `MpkScheduleParser.dowOfCalendar` (уже есть!) или `util/DowExt.kt`. prefs-обёртки → `enumPref<T>()`.
**Что делать**: механическая замена дублей на существующую функцию. Низкий риск.

### E4. Navigation-Compose миграция [ARC-01] — БОЛЬШАЯ отдельная волна
**Файлы**: `ui/screens/MainScreen.kt`, `ui/viewmodel/AppViewModel.kt`, все экраны.
**Что делать**: заменить `when(tab)` + `pending*`/`returnTab`/`scrollTarget` на NavHost + nav-аргументы + SavedStateHandle. `navigationCompose` уже в libs.versions.toml (2.8.9, закомментирован — подключить). **Устраняет корень карты «третий клик» и класс багов «состояние теряется».**
**Риск**: высокий, меняет всё. Только после A-E3, отдельной волной, со скриншот-тестами Roborazzi как приёмкой.

### E5. Декомпозиция god-экранов [ARC-04] — попутно с E4
**Файлы**: CollegeScreen (1901), ScheduleScreen (1210), SettingsScreen (1007), TasksScreen (879).

---

## ВОЛНА F — сборка/стиль/косметика

### F1. okhttp 4.12 + versionCode [BSP-01, BSP-03]
**Файлы**: `gradle/libs.versions.toml`, `app/build.gradle.kts`. okhttp→4.12.0 (CVE); versionCode/versionName синхронизировать с v3.3.

### F2. backup rules [BSP-05]
**Файлы**: `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`. Явные include (prefs, тема, student_tasks), exclude кэш.

### F3. spotless/ktlint [BSP-07]
**Файлы**: `build.gradle.kts`, `app/build.gradle.kts`. Автоформат + правило «импорты вместо qualify», «без BOM». **Отдельным коммитом** «форматирование, без изменения логики».

### F4. composeBom update [BSP-02] — отдельной волной со скриншот-тестами.

### F5. baseline profile [BSP-04] — когда появится эмулятор/CI.

### Косметика (низкий приоритет, одним коммитом): BEL-05 (прогресс-бар 45 мин), BEL-06/DAT-05 (молчаливые фолбэки → лог), BOM в файлах, полные qualify.

---

## Порядок выполнения (итог)

```
A (сеть+виджеты, критично)  →  B (контакты+карта, low-risk)  →  C (надёжность звонков/виджетов)
   →  D (БД: индексы+миграция)  →  E (архитектура, E4 Navigation отдельно)  →  F (сборка/стиль)
```

После каждой волны: compileStandardDebugKotlin → testStandardDebugUnitTest → (для standard-release: grep students.csv = 0) → коммит.

## Блокирующие вопросы к заказчику

- **Q1 (BEL-01)**: нужна ли синхронизация СОКРАЩЁННЫХ звонков с сайта, или хардкод CollegeBellSchedule приемлем (сокращённые дни редки)?
- **Q2 (SCH-04)**: неделя в МГПК — пятидневка или шестидневка? (nextSchoolDay пропускает только воскресенье.)
- **Q3 (C3/BEL-02)**: добавлять `USE_EXACT_ALARM` в манифест (не отзывается пользователем, но ограничен Play-политикой для будильников) или только deep-link на `SCHEDULE_EXACT_ALARM`?
- **Q4 (E4)**: готовы ли к большой волне миграции на Navigation-Compose (риск, но устраняет класс багов)? Или ограничиться тактическими фиксами?
