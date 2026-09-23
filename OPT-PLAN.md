# OPT-PLAN.md — план работ по хотелкам заказчика O1..O9

**Проект:** `D:\MPK app\MPK-app-2.1`, пакет `com.politex`, flavors `standard` / `admin`.
**Статус на момент составления:** ветка `v2.4-stable`, HEAD `f90e11e` («Волна A: конфетти удалено, O2 центрирование, O5/версия убраны, O9 размер APK»). Рабочее дерево чистое.
**Критерий приёмки всего плана:** `sh ./gradlew :app:testStandardDebugUnitTest` — зелёный; ручные сценарии O1/O3/O6/O7 — по чеклистам ниже.

Легенда: `[x]` сделано, `[ ]` не начато, `[~]` в работе.

---

## 0. Контекст и конвенции (кратко)

1. **Room без изменения схемы.** `MpkDatabase.kt:17` `version = 1` + `fallbackToDestructiveMigration()`. Разрешены только **аддитивные** DAO-запросы; новые колонки в сущности — только с осознанным решением (см. §4).
2. **Даты в БД — только `dd.MM.yyyy`.** Никаких лексикографических сравнений; сравнение — только через `Calendar` / epoch (`CalendarArchiveDialog`, `HomeworkMarkers`, виджеты). ISO нормализуется на границе (`MpkScheduleParser.parseDotDate`).
3. **`students.csv` — только `app/src/admin/assets/`.** Код `standard` никогда не ссылается на `StudentsRepository`. Проверка релиза: `unzip -l app-standard-release.apk | grep -c students.csv` → `0`.
4. **UI — «колледжный» стиль:** радиус 2–3 dp, `ColorBgMain/ColorBrandBlue/ColorBorderLight`, хитбоксы ≥44–48 dp, `bouncyClickable`.
5. **Чтение кода — адресное.** Файлы > 500 строк не читать целиком.
6. **Диагнозы ниже даны по HEAD `f90e11e`.** Часть хотелок уже закрыта волной A — по ним план содержит только **остаточный** объём, помеченный явно.

### 0.1. Что уже закрыто волной A (`f90e11e`) — переделывать НЕ надо

| Задача | Факт | Проверка в коде |
|---|---|---|
| O2 (центрирование кнопок «Звонки») | `[x]` | `BellsScreen.kt:243` — `Box(fillMaxSize, contentAlignment = Alignment.Center)` вокруг `Text` |
| O4 (конфетти полностью) | `[x]` | `grep -rn "Confetti\|confetti" app/src/main` → 0 совпадений; 6 файлов удалены (`ConfettiOverlay`, `ConfettiRenderer`, `WidgetConfetti`, `ConfettiController`, 2 теста); `iv_confetti_overlay` нет ни в одном из 4 layout |
| O4a (версия приложения) | `[x]` | `grep "VERSION_NAME" SettingsScreen.kt` → пусто |
| O5 (ползунок «Закрепить в шторке») | `[~]` — UI убран, **осталось мёртвое API** | `BellCountdownNotifier.kt:101-107` (`isPinned`/`setPinned`), `:166` (`setOngoing(isPinned(context))`) — вызывающих из UI больше нет |
| O9 (часть) | `[~]` | фото преподавателей 5.6 МБ → 1.7 МБ, `.jpeg/.png` → `.jpg` + правка `teachers.csv`; убраны firebase-bom/ai/appcheck, retrofit, converter-moshi, moshi-kotlin, logging-interceptor; `app/build.gradle.kts:76-80` — `isMinifyEnabled`/`isShrinkResources`/`isCrunchPngs` = true |
| Аудит O1 | `[x]` (только отчёт) | `PERF-REVIEW.md` (438 строк) — диагнозы жалоб 1–4 с точными строками |

**Отсюда: остаток работ — O1 (реализация), O3 (a/b/c/d), O6, O7, O8, O9-остаток (карты в WebP, замер APK), O5-остаток (мёртвое API).**

### 0.2. Baseline размеров (измерено)

| Артефакт | Размер |
|---|---|
| `D:\MPK app\MPK-app-2.1\moy-polytech-2.0-release.apk` / `-admin` | 23.6 МБ / 23.6 МБ |
| `moy-polytech-2.3-ux-release.apk` / `-admin` | 23.7 МБ / 23.7 МБ |
| `moy-polytech-v2.9-debug.apk` / `-admin-debug` | 29.6 МБ / 29.7 МБ |
| `res/drawable` итого | 862 КБ (3 карты этажей `map_floor1/2/34.jpg` = 824 КБ, 1376×768) |
| `assets/teachers_photos` | 2.1 МБ на диске (99 файлов; было 5.6 МБ до волны A) |
| `assets/teachers.csv` | 30.7 КБ |
| `assets/college_map.json` | 65.6 КБ |
| `admin/assets/students.csv` | 1578 строк, 3 колонки (`ФИО;группа;курс;…`) |

> Релизные APK после волны A **не собирались** (не проверялось, что R8/шринк не ломает runtime) — это отдельный пункт приёмки O9.

### 0.3. Координация (важно перед стартом волны)

- Волна A (`f90e11e`) закрыла часть хотелок. Перед началом любой волны **сверить HEAD и `git status`** — параллельные агенты могли уже взять файл.
- Правило владения: **один файл — один агент в волне**; если файл нужен двум агентам (см. `MpkNetworkClient.kt` в OW1-D и OW2-B) — правки разнесены по разным волнам, старт второй волны только после зелёных тестов первой.
- Перед каждым изменением — проверять `git log --oneline -3` и `git status --short`; при неожиданных правках в «своём» файле — не мержить вручную, а сообщать координатору.
- **На момент написания плана OW1-A и OW1-C уже в работе** (незакоммиченные правки в `MainActivity.kt`, `MainScreen.kt`, `ScheduleScreen.kt`, `AppViewModel.kt`, `BellCountdownNotifier.kt`, `BellCountdownWidgetProvider.kt`, `NowNextWidgetProvider.kt`, `WidgetUpdateHelper.kt`, `BellTimerService.kt`) — это в точности их файлы из §Волны. Перед стартом OW1-B/D перепроверить `git status`, чтобы не пересечься.

---

## O1. Производительность и состояние UI (КРИТИЧНО)

**Жалобы:** (1) холодный старт ~1 с; (2) «Сегодня»↔«Завтра» — до секунды заглушка «Расписание ещё не опубликовано» при наличии данных; (3) возврат с «Заданий»/«Звонков» перезагружает экран; (4) виджеты перезагружаются.

### Диагноз (подтверждён по PERF-REVIEW + проверен по коду)

**O1-a. Смена дня = новый Flow = сброс в `initial = null`.**
`ScheduleScreen.kt:233-237`:
```kotlin
val lessonsFromDbState … by scheduleRepository
    .getLessonsForDateOrDay(groupInfo.canonicalName, activeDateString, activeDayOfWeek)
    .collectAsState(initial = null)
val isInitialLoad = lessonsFromDbState == null
```
Ключ Flow зависит от `activeDateString`/`activeDayOfWeek`, которые пересчитываются от `selectedSlot` (`:188-205`). Тап по «ЗАВТРА» (`:466-469`) → новый ключ → `collectAsState` пересоздаётся → `isInitialLoad = true` → спиннер (`:510-524`), затем `lessons.isEmpty()` → заглушка (`:525`, текст `:556`). **Stale-while-revalidate отсутствует полностью.**

**O1-b. Данные «завтра» могут писаться транзакционно-рвано.**
`ScheduleRepository.kt:140-145` — `replaceSyncedLessons`: сначала цикл `deleteLessonsForDate`, затем `insertLessons` — двумя отдельными запросами вне `@Transaction`. Пока идёт синк, Room успевает эмитить пустой список → та же заглушка.

**O1-c. Возврат на вкладку теряет состояние.**
`MainScreen.kt:168-194` — `when (uiState.currentTab)`: уход из ветки уничтожает всё composition-поддерево. Над `when` вынесен только `listState` (`:160-165`, `rememberSaveable`). Внутри `ScheduleScreen` на голом `remember`: `selectedSlot` (`:184`), `selectedDateString` (`:185`), `didColdStartScroll` (`:292`), 9 Flow (`:142-144`, `:233-243`, `:249-258`).

**O1-d. Повторный парсинг CSV в композиции.**
`ScheduleScreen.kt:118-127` — `isTeacherKnown`: `TeachersRepository(context).loadTeachers()` + `HashSet` из ~99 фамилий **на главном потоке** на каждый вход на вкладку (`loadTeachers` не кэширован — `TeachersRepository.kt:22-24`, чтение ассета + split построчно).

**O1-e. Холодный старт.** `AppViewModel.kt:69-74` — в `init` безусловный `syncSchedule(isAutoSync = true)`; `ScheduleRepository.kt:82-88` при `force=false` и непустом кэше в сеть не идёт, но перед этим — 2 запроса `getLessonCountForDate`; сам экран ещё до этого поднимает 9 Flow.

**O1-f. Виджеты.** `MainActivity.kt:64-67` — `onResume` → `updateAllWidgets` (все 4 провайдера). `BellTimerService.kt:38-68` — минутный тик, на каждом тике `startForeground` + `BellCountdownWidgetProvider.updateAll`. Плюс отдельный `WidgetAlarm` (`BellCountdownWidgetProvider.kt:393-418`) — **два независимых минутных таймера**. `NowNextWidgetProvider`/`BellCountdownWidgetProvider` делают `runBlocking` в БД на главном потоке хоста виджета (`PERF-REVIEW.md` §Жалоба 4).

### План

- [ ] **1.1** Один Flow на группу вместо Flow-на-день. Ввести чистую функцию выбора дня (тестируемую, не в composable):
  `ScheduleRepository.selectDayLessons(all: List<LessonEntity>, date: String, dow: Int): List<LessonEntity>` — приоритет точной даты, фолбэк на пустой `dateString` + `dayOfWeek`. В `ScheduleScreen` заменить `:233-243` на `getAllLessonsForGroup(...).collectAsState(initial = emptyList())` + `remember(allGroupLessons, activeDateString, activeDayOfWeek) { selectDayLessons(...) }`. Заодно удалить 6 отдельных Flow (`:142-144` saturdayLessons, `:249-258` mon..fri) — они питают `weeklyLessonsByDow` (`:260-270`) и `saturdayIsSchoolDay` (`:145`): пересчитать из одного списка.
  **Важно:** `isInitialLoad` после этого должен означать «первая эмиссия Room ещё не пришла», а не «сменился день» — ввести `var firstEmission by remember { mutableStateOf(false) }`, выставлять в `LaunchedEffect`/через `produceState`.
- [ ] **1.2** Транзакция на перезапись дня: добавить в `LessonDao` (аддитивно, схема не меняется):
  ```kotlin
  @Transaction suspend fun replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>) {
      deleteLessonsForDate(groupName, dateString); insertLessons(lessons)
  }
  ```
  и вызвать её из `ScheduleRepository.replaceSyncedLessons` (`:140-145`) вместо цикла.
- [ ] **1.3** Поднять состояние дня над `when`: `MainScreen.kt` рядом с `scheduleListState` (`:160`) — `var scheduleSlot by rememberSaveable { mutableIntStateOf(0) }`, `var scheduleDate by rememberSaveable { mutableStateOf("") }`; передать в `ScheduleScreen(slot=…, onSlotChange=…, date=…, onDateChange=…)`. `didColdStartScroll` (`ScheduleScreen.kt:292`) → `rememberSaveable`.
- [ ] **1.4** Убрать парсинг CSV из композиции: `ScheduleScreen.kt:118-127` — перенести в `TeachersRepository` как ленивый кэш (`private val knownSurnames by lazy { … }`) или в `AppViewModel`; в composable передавать готовый предикат.
- [ ] **1.5** Холодный старт: в `AppViewModel.init` (`:69-74`) не звать синк, если расписание в БД уже есть (`scheduleRepository.hasSchedule(group)` — уже есть, `:159-161`); сеть — только при пустом кэше либо по кнопке/воркеру.
- [ ] **1.6** Виджеты: (a) минутный кэш `todayLessons`/`lastGroupLessonForToday` в памяти процесса (perf-review фикс 4a); (b) не дёргать `updateAllWidgets` из `MainActivity.onResume` (`:64-67`) — только `updateOne`/по необходимости; (c) на минутном тике обновлять только «минутные» провайдеры (`BellCountdown`, `NowNext`), `TodaySchedule`/`Bells` — по смене урока/раз в 10 минут; (d) оставить **один** минутный источник: если `BellTimerService` запущен — `WidgetAlarm.scheduleNext` не планировать (и наоборот).
- [ ] **1.7** `ScheduleScreen.kt:202` (`MainScreen.kt`) — двойная подписка на `uiState` (`CollegeTab`) → передавать уже собранное состояние.

### Критерии приёмки
- Переключение «Сегодня»↔«Завтра» при наличии данных в БД — **без** спиннера и **без** заглушки: список меняется мгновенно (`UiRenderSmokeTest` + ручной сценарий).
- Возврат «Задания»→«Расписание»: сохранены выбранный день, позиция скролла, отсутствие повторной автопрокрутки.
- Холодный старт при заполненной БД: без сетевого вызова (проверяется по `SyncDiagnosticInfo.lastSyncTime` — не меняется).
- Число `runBlocking` в БД из виджетов за минуту: ≤1 на провайдер.

### Юнит-тесты (чистая логика)
- `ScheduleDaySelectTest`: `selectDayLessons` — точная дата приоритетнее, фолбэк по `dayOfWeek` при пустом `dateString`, пустой результат, не-перемешивание дней (использовать существующий `RoomScheduleLogicTest` как шаблон, `:38-54`).
- `replaceForDate`: две строки → одна транзакция, промежуточной пустой эмиссии нет (Robolectric + in-memory Room; шаблон — `ScheduleArchiveTest`).
- `TeachersRepository`: ленивый кэш возвращает тот же экземпляр, CSV читается 1 раз.

### Риски
- Смена модели «один Flow + фильтр в памяти» затрагивает автопрокрутку (`:305-336`) и `HomeworkMarkers` (`:272-286`) — ключи `remember` придётся перепроверить; риск ложных срабатываний автопрокрутки.
- `rememberSaveable` для `selectedSlot` — сохранение переживёт смерть процесса, но `groupInfo` может смениться: ключ сохранения уже есть (`selectedSlot` сейчас `remember(groupInfo.canonicalName)`), при подъёме ключ потеряется → **сбрасывать слот при смене группы** явным `LaunchedEffect(groupInfo.canonicalName)`.
- Виджеты: правки затрагивают 4 провайдера + 2 таймера; ошибка → «виджет замер». Нужен ручной smoke каждого типа виджета.

---

## O2. Центрирование кнопок дней недели («Звонки») — `[x]` (волна A)

**Диагноз (был):** `Surface` в M3 рисует контент от `Alignment.TopStart` — `Text` с `padding(vertical = 8.dp)` липнул к верху при `heightIn(min = 44.dp)`.
**Сделано:** `BellsScreen.kt:243` — `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)` вокруг `Text` (`:238-254`).
**Приёмка:** текст ПН-ПТ / ЧЕТВЕРГ / СУББОТА по центру при системном шрифте 100–130 %.
**Остаток:** ничего. При регрессии — тест `UiRenderSmokeTest` (Roborazzi) на снимок ряда кнопок.

---

## O3. Карта колледжа (вкладка «Другое»)

Файлы: `ui/screens/CollegeMapScreen.kt` (1114 строк), `ui/components/RoomScheduleDialog.kt` (365), `data/local/dao/LessonDao.kt`, `data/repository/ScheduleRepository.kt`, `data/network/MpkScheduleParser.kt`, `data/network/MpkNetworkClient.kt`, `util/GroupParser.kt`.

### O3-a. Цифры-номера кабинетов: «жирный курсив, сливаются»

**Диагноз.** Курсива в коде нет — `grep -rn "fontStyle\|FontStyle\|Italic" app/src/main` → **0 совпадений**. Ощущение «жирного курсива» даёт связка:
- `CollegeMapScreen.kt:846-860` — единый базовый размер `baseFontPx = 22f`, `fontWeight = FontWeight.Normal` (уже нормальный вес);
- `:1053-1082` — расчёт `fit` с «демпфированием» относительно медианы и клампом `coerceIn(medianFit * 0.70f, medianFit * 1.35f)`: мелкие кабинеты получают `fit ≈ 0.70·median` → на 240 dpi цифра «249» рисуется ~7–9 px и сливается;
- `:1094-1105` — белый **ореол** `Stroke(width = layout.size.height * 0.22f, join = StrokeJoin.Round)` + заполнение: при малом размере ореол съедает просветы между штрихами → слипание выглядит как «жирный курсив».

**План**
- [ ] **3a.1** Ввести минимальный размер отрисовки: заменить нижний кламп `medianFit * 0.70f` на абсолютный минимум в screen-px (например `minScreenPx = 13f` при плотности 1 — параметр уже передаётся как `minScreenPxPerDp`, `:1019`, но **не используется** — мёртвый параметр).
- [ ] **3a.2** Уменьшить ореол до `0.14f·height` и/или отключать ореол, когда `fit < 0.8` (мелкие цифры получают чистый контур без «жира»).
- [ ] **3a.3** Для кабинетов, где расчётный размер всё равно < минимума, — **не рисовать номер** (или рисовать только при zoom ≥ порога, `zoom` уже приходит в `drawMap`).
- [ ] **3a.4** Удалить мёртвый `baseFontPx`-путь, если он не нужен после 3a.1 (см. `:847, :944, :1021, :944`).

**Приёмка:** скриншот этажа 1/2/3-4 при `restZoom()` — номера читаемы, не сливаются; сравнение «до/после» через `MapScreenShotTest` (Roborazzi, уже есть `app/src/test/java/com/politex/MapScreenShotTest.kt`).
**Тест:** чистая функция `mapNumberScale(roomW, roomH, layoutH, layoutW, zoom, isVertical, minPx)` → вынести расчёт `fit` из `drawMap` и покрыть кейсами «узкий вертикальный кабинет», «мелкий кабинет», «крупный кабинет».
**Риски:** уменьшение шрифта → обратная жалоба «мелко». Компромисс: минимум + отсутствие ореола + zoom по тапу.

### O3-b. «Подробнее — расписание» должно показывать кабинет по ВСЕМ группам

**Диагноз (корневой, важный).** Диалог уже читает **все** строки кабинета без фильтра по группе: `LessonDao.kt:32-41` `getLessonsForRoomOnDate` — `WHERE (roomFirst = :room OR roomSecond = :room) AND (date… OR dayOfWeek…)`, без `groupName`. То есть фильтрация «только моя группа» происходит **не в диалоге, а в данных**: в БД лежат уроки **только выбранной группы** — `ScheduleRepository.syncScheduleFromWeb(groupName)` → `MpkNetworkClient.fetchScheduleForGroup(targetGroup)` → `MpkScheduleParser.parseFile(bytes, targetGroup)` (`MpkScheduleParser.kt:214-231`), парсер **отбрасывает чужие группы** (`:598`, `:632`, `:785-794` — поиск колонки `targetGroup`). Вывод: **без синхронизации чужих групп требование O3b невыполнимо никакими правками UI.**

Дополнительно: `RoomScheduleDialog.kt:59-69` (`filterRoomLessonsForDate`) и `:76-81` (`currentRoomLessonNumber`) корректны; `CollegeMapScreen.kt:178-194` грузит данные кабинета `produceState(key1 = roomScheduleFor)` — ок.

**План (выбрана схема «догрузка дня по всем группам из того же документа»)**
- [ ] **3b.1** `MpkScheduleParser`: новая функция `parseFileAllGroups(bytes: ByteArray, targetDate: String): List<LessonEntity>` — разбор **всех** групповых колонок. Реализовать минимум на стратегии `cells` (`parseCellRunsSchedule`, `:753-802`) + `grid` (`parseTableGridSchedule`): колонки уже сгруппированы (`block.groups`, `:761-772`), нужно лишь не отбрасывать не-целевые колонки, а штамповать `groupName` каждой. Для стратегии `line` — вернуть пусто и зафиксировать в `lastParseStats.strategy`.
- [ ] **3b.2** `ScheduleRepository`: `suspend fun syncDayAllGroups(date: String, dow: Int): Result<Int>` — скачивает те же кандидаты, что и обычный синк (`MpkNetworkClient.downloadFileBytes` + `generateCandidateDirectUrls` + портал, `:120-140`), парсит `parseFileAllGroups`, пишет `replaceSyncedLessons(groupName=…)` **по каждой группе** (перезапись строго по дате — ключ уже правильный после O1.1.2). Не трогать выбранную `currentGroupName` из настроек.
- [ ] **3b.3** `CollegeMapScreen`: перед открытием `RoomScheduleDialog` (`:283-292`) — если для кабинета на сегодня в БД есть строки **менее чем от 2 групп** и сеть доступна, показать строку прогресса «Загружаем расписание кабинета…» и вызвать `syncDayAllGroups`; по завершении — перечитать Flow. **НЕ** блокировать открытие диалога при ошибке сети: показываем то, что есть (честно).
- [ ] **3b.4** `RoomScheduleDialog`: текущий урок выделять синим «СЕЙЧАС» — механика уже есть (`:259-271`, `room_lesson_current_<n>`), но `currentLessonNumber` считается один раз (`CollegeMapScreen.kt:192-194`, `remember(roomScheduleFor)`), т.е. **не обновляется** — добавить минутный тикер (`LaunchedEffect` с `delay(60_000)`) или пересчёт при открытии.
- [ ] **3b.5** Сортировка/группировка в диалоге: сейчас `filterRoomLessonsForDate` сортирует по `lessonNumber` (`:68`) — при нескольких группах в одной паре карточки идут подряд (по подписи группы в `:295-300` уже видно). Добавить визуальный разделитель по номеру пары (заголовок «1–2 урок»), если в паре >1 группы.

**Приёмка (кейс заказчика):** кабинет 249, день: 31-я — 1–2 урок, 22-я — 3–4, 41-я — 5–6 → диалог показывает 6 карточек, три разные группы, текущая пара — с синей рамкой «СЕЙЧАС».
**Тесты:** `RoomDayScheduleTest` — `filterRoomLessonsForDate` на списке из 3 групп × 2 пары (все проходят, порядок по уроку); `currentRoomLessonNumber` — границы звонков, инфочас четверга (`lessonNumber = 0` не считается парой).
**Риски:** (1) `parseFileAllGroups` зависит от реального формата `.doc` — **проверить на сохранённых фикстурах** (`app/src/test/java/com/politex/Step5OfficialDocTest.kt`, `RealSiteDocParserTest.kt` — там есть образцы); (2) объём БД вырастет в ~66 раз на день (66 групп × 6 пар ≈ 400 строк/день) — при `ARCHIVE_KEEP_DAYS = 60` (`ScheduleRepository.kt:14`) это до 24k строк, приемлемо; (3) `pruneOldLessons` (`:118-123`) чистит по дате без группы — не мешает.

### O3-c. Вкладка «Группа»: поисковая строка с полным списком групп

**Диагноз.** Сейчас ввод + выпадашка «группы с уроками сегодня» (`CollegeMapScreen.kt:158-162`, `:704-733`: `DropdownMenuItem` из `todayGroups`) — заказчик называет это «скудным выбором» (видит 4 варианта). Поиск уже принимает любой регистр: `searchGroupNow` (`:600-631`) → `GroupParser.cleanRawGroupName` (`GroupParser.kt:96-98`) + `GroupParser.matchesGroup` — латиница/кириллица нормируются (`:7-20, :100-103`). Не хватает **полного списка существующих групп** как подсказки/валидатора.

**План**
- [ ] **3c.1** Источник списка: новый ассет `app/src/main/assets/group_codes.csv` — **только коды групп**, без ФИО (безопасность: `standard` не должен видеть персональные данные). Источник — O8 (полная выгрузка). Формат: одна колонка, 66 значений, кириллица: `11А, 11Г, …, 42Э`.
- [ ] **3c.2** Загрузчик `util/GroupCatalog.kt` (новый файл, создаётся в OW1-B вместе с O8): `fun all(): List<String>`, `fun find(input: String): String?` — нормирует регистр/латиницу через `GroupParser.normalizeSpecialtyChar`, возвращает канонический код или `null`.
- [ ] **3c.3** `GroupSearchField` (`:633-770`): убрать `DropdownMenu` (`:704-733`), оставить `BasicTextField` + (опционально) компактные inline-подсказки совпадений под полем (максимум 6, фильтр по подстроке) — **без** выпадающего списка, как просил заказчик.
- [ ] **3c.4** `searchGroupNow`: если `GroupCatalog.find(group) == null` → сообщение «Группа «X» не найдена — проверьте код»; иначе искать по каноническому коду. Порядок приоритета ответов сохранить (`:624-630`).
- [ ] **3c.5** Не забыть: `GroupParser.generateCommonGroups()` (`:105-141`) — синтетический список (курс × код специальности), он неполон (нет 2-й группы для части кодов, нет 3-й); **не** использовать его как источник для карты. Судьба функции: используется `GroupSelectionDialog.kt:80` и `GroupScheduleScreen.kt:607` — **не трогать в этой волне** (отдельное решение, §4).

**Приёмка:** ввод `41о` / `41O` / `41o` → находится `41О`; ввод `99Х` → «не найдена»; ввод группы без уроков сегодня → «У группы «…» нет уроков сегодня».
**Тесты:** `GroupCatalogTest` — регистр/латиница, несуществующий код, пустая строка; чтение ассета через Robolectric (`isIncludeAndroidResources = true` уже включён, `app/build.gradle.kts:92`).
**Риски:** ассет попадает в **оба** флейвора — это допустимо (только коды групп). Проверить размер (≈400 байт).

### O3-d. Тап по кабинету на карте = как ввод в поиск

**Диагноз.** Сейчас карта обрабатывает только жесты (`CollegeMapScreen.kt:896-911` — `detectTransformGestures`); попадания по кабинету нет, `target` меняется только извне (`:172`, `:200-202`). Геометрия доступна: `MapRoom(x, y, w, h)` нормализованы (`CollegeMapData.kt:9-19`), обратное преобразование уже есть логикой `drawMap` (`:1026-1029`).

**План**
- [ ] **3d.1** Чистая функция `hitTestRoom(floor: FloorMap, xNorm: Float, yNorm: Float): MapRoom?` (порядок: сначала точное попадание в прямоугольник, затем — ближайший кабинет в радиусе ~0.02 норм. единиц; приоритет — меньший кабинет).
- [ ] **3d.2** В `MapCanvas` добавить `detectTapGestures` **через** `awaitPointerEventScope`/`pointerInput` рядом с `detectTransformGestures` (не взамен — иначе сломается зум/пан): экранные координаты → нормализованные с учётом `scale`/`translationX/Y` (обратное к `graphicsLayer`, `:913-922`).
- [ ] **3d.3** Прокинуть `onRoomTap: (MapRoom) -> Unit` в `MapCanvas` → в `CollegeMapScreen` установить `target = room.number` (тот же путь, что у поиска: `:222-228`), `searchFailed = false`, панель `RoomPanel` (`:274-279`) станет доступна, кнопка «ПОДРОБНЕЕ — РАСПИСАНИЕ КАБИНЕТА» (`:369-395`) заработает через `roomScheduleFor` (`:277`).

**Приёмка:** тап по кабинету на карте → тот же результат, что ручной ввод номера: карта зумится к кабинету (анимация `:874-894`), панель показывает номер/название, «Подробнее» открывает расписание (с учётом O3-b).
**Тесты:** `MapHitTestTest` — попадание в центр/на границу/мимо; приоритет меньшего кабинета при перекрытии; тап при `scale = restZoom()` и при `scale = 8f` (нормализация координат).
**Риски:** конфликт тап/пан (тап по концу пан-жеста) — использовать `detectTapGestures` с порогом `touchSlop`, порядок модификаторов: тапы **после** `detectTransformGestures` внутри одного `pointerInput` (иначе жест «съедается»).

---

## O4. Убрать конфетти полностью — `[x]` (волна A)

**Проверено (3 независимые проверки):**
1. `grep -rn "Confetti\|confetti" app/src/main` → **0 совпадений**;
2. нет `iv_confetti_overlay` ни в одном из 4 layout виджетов (`widget_bells`, `widget_bell_countdown`, `widget_now_next`, `widget_today_schedule` — блок удалён);
3. удалены файлы: `ui/components/ConfettiOverlay.kt`, `util/ConfettiRenderer.kt`, `widget/WidgetConfetti.kt`, `util/ConfettiController.kt`, `test/ConfettiPhysicsTest.kt`, `test/ConfettiTriggerTest.kt`; убраны вызовы в `MainActivity.kt`, `BellTimerService.kt`, `ScheduleCheckWorker.kt`, `ScheduleScreen.kt`, 4 провайдерах, `SettingsScreen.kt` (debug-карточка); убрана строка версии приложения.

**Остаток:** `[ ]` уборка данных в `SharedPreferences` — ключи `confetti_celebrated_date`, `confetti_tomorrow_pending_<date>` у пользователей с обновлением останутся мусором (безвредно, но ~единицы записей). Опционально — игнор, чистить не нужно (§4).
**Приёмка:** в релизном APK нет ресурса `iv_confetti_overlay`; `grep -r confetti app/src` → 0.

---

## O5. Ползунок «Закрепление в шторке» — `[x]` UI / `[ ]` остаток

**Сделано:** UI-переключатель удалён из `SettingsScreen.kt` (проверено: в файле нет `isPinned`/`setPinned`/`testTag("bell_notification_pinned_switch")`).
**Остаток (мёртвое API):**
- [ ] **5.1** `BellCountdownNotifier.kt:101-107` — удалить `isPinned`/`setPinned`; `:30` — `PREF_PINNED`; `:166` — `.setOngoing(isPinned(context))` → `.setOngoing(true)` (уведомление FGS всё равно держится системой, `BellTimerService.kt:28` `startForeground`; `buildHiddenNotification` уже `.setOngoing(true)`, `:218`).
**Приёмка:** компиляция без предупреждений об unused; уведомление ведёт себя как раньше (свайп снимает — это и есть вердикт заказчика: не чиним).

---

## O6. Уведомление состояния: тишина после уроков, возобновление в 6:00

### Диагноз

Механика «тишины» уже реализована и работает, но граница — **07:00**, не 06:00:
- `BellCountdownWidgetProvider.kt:35` — `const val SHOW_FROM_MINUTE = 7 * 60`; используется в `:93` (`tomorrowFirstLessonLabel`) и `:132` (`countdownForMinute`);
- `countdownForMinute` (`:124-166`): `MY_LESSONS` (`GROUP_LESSONS`) — если `lastGroupLesson == null` → `null` весь день (`:136`); если минуты > конца последнего урока группы → `null` (`:140`). `ALL_BELLS` — `null` после последнего звонка (цикл ничего не находит, `:143-165` → `null`).
- `BellCountdownNotifier.buildNotification` (`:136-201`): `info == null || info.first < 0` → `buildHiddenNotification` (`:148`) — «тихая заглушка» вместо строки. Комментарий `:146-147` это фиксирует.
- `BellTimerService` (`:38-68`) каждую минуту делает `startForeground` (демоут), т.е. **полный «убрать уведомление» невозможен** — что и указано заказчиком как допустимое.

**Что не соответствует требованию:**
1. Возобновление в **07:00** вместо **06:00**.
2. `ALL_BELLS`: после конца дня `countdownForMinute` возвращает `null` (тишина — верно), но **условие возобновления «в этот день есть уроки/звонки»** не проверяется явно: `getBellsForDay(dow)` пуст → `null` (`:131`), `dow == 7` → `null` (`:62`, `:131`). Для субботы звонки есть только у «СУББОТА»-графика — надо убедиться, что `CollegeBellSchedule.getBellsForDay(6)` непуст только когда суббота действительно учебная (сейчас — отдельная проверка `getBellsForType`, `BellsScreen.kt:138`).
3. `MY_LESSONS` в 06:00 должен возобновиться, **если у группы есть уроки сегодня**: `lastGroupLessonForToday` (`:172-198`) берёт строки с датой (приоритет) либо шаблон `dayOfWeek` — работает, но содержит `runBlocking` (`:185`); после O1.6 это останется единственным чтением на минуту.

### План
- [ ] **6.1** `SHOW_FROM_MINUTE = 6 * 60` (`BellCountdownWidgetProvider.kt:35`) — одно значение, тянет за собой и виджет, и уведомление (общая константа).
- [ ] **6.2** Проверить/зафиксировать предикат «день с занятиями»:
  - `MY_LESSONS` — `lastGroupLessonForToday(context, cal) != null` (уже есть);
  - `ALL_BELLS` — `CollegeBellSchedule.getBellsForDay(dow).isNotEmpty()` (сейчас неявно через `:131`); вынести в явную функцию `hasBellsForDay(dow)` и покрыть тестом для Сб/Вс.
- [ ] **6.3** В `BellCountdownNotifier` не плодить логику: уведомление по-прежнему читает `countdownInfo(context, mode)` (`:144`) — правка границы и предиката автоматически лечит и уведомление, и виджет.
- [ ] **6.4** Явно задокументировать в `buildHiddenNotification` (`:203-225`): «Android не позволяет удалить уведомление работающего FGS — используем демоут» (заказчик это санкционировал).
- [ ] **6.5** Опционально (только если заказчик захочет «совсем без строки»): останавливать сервис после конца уроков и поднимать по `AlarmManager` в 06:00 — **отложено**, см. §4.

### Критерии приёмки
- 05:59 — заглушка (строка без текста); 06:00 — если у группы есть уроки сегодня, строка возобновляется; если нет — заглушка весь день.
- `ALL_BELLS`: конец последнего звонка → тишина → 06:00 следующего учебного дня возобновление; в воскресенье — тишина весь день.
- Проверка на четверг (инфочас, `lessonNumber = 0`) — не считается парой (`RoomScheduleDialog.kt:76-81` — эталон).

### Юнит-тесты (расширить `app/src/test/java/com/politex/BellCutoffTest.kt`, 204 строки, шаблон уже есть)
- `countdownForMinute(minutes=359 /*05:59*/, dow=3, bells, ALL_BELLS, …)` → `null`;
- `minutes=360 /*06:00*/` → не `null` при наличии звонков и уроков группы;
- `GROUP_LESSONS` + `lastGroupLesson = null` → `null` весь день (в т.ч. в 10:00);
- `ALL_BELLS`, `dow = 7` → `null`;
- `ALL_BELLS`, день без звонков (Сб без «СУББОТА»-графика) → `null`;
- граница «минута окончания последнего урока» — `null` со следующей минуты.

### Риски
- Одна константа на виджет и уведомление: сдвиг на 06:00 изменит и виджет (заказчик жаловался на виджеты в O1 — правка совпадает с его ожиданием).
- `runBlocking` в `lastGroupLessonForToday` вызывается из FGS-тика (не UI-поток) — риск ANR ниже, но кэш из O1.6 обязателен.

---

## O7. Расписание преподавателей: только реальные данные, никогда фолбэк

### Диагноз

Проверено по коду:
1. **Источник — единственный и честный:** `CollegeScreen.kt:1395-1456` `TeacherScheduleTab` → `client.fetchTeacherSchedule(targetCalendar)` (`:1430`); кэш — in-memory `mutableMapOf<dateKey, …>` (`:1407`), живёт в `remember` вкладки, при уходе теряется (связано с O1-c).
2. **Сеть:** `MpkNetworkClient.fetchTeacherSchedule` (`:250-272`) — URL строго по дате: `…/uploads/$yyyy/$mm/$ddMmYyyy-raspisanie-prepodavatelej.doc`; при `null`/пустом ответе — `Result.failure(IOException("Документ не найден: $url"))` (`:260-262`); при пустом разборе — `failure` (`:264-267`). **Фолбэка на «текущую неделю» в сети НЕТ.** Совпадает с жалобой заказчика, что колледж удаляет файлы через ~2 дня: старые даты → 404 → честная ошибка.
3. **404-путь:** `downloadFileBytes` (`:240-248`) глотает исключение и возвращает `null` → `failure("Документ не найден")`. Не логируется код ответа (404 vs 500 неразличимы) — мелочь для диагностики.
4. **UI-сообщение:** `CollegeScreen.kt:1434-1440` — при `e.message?.contains("не найден")` → «Расписание на <дата> ещё не опубликовано»; иначе «Не удалось загрузить (проверьте интернет)». **Здесь и есть главный дефект против требования:** «не опубликовано» показывается и когда данных нет **в архиве БД**, и когда нет сети — пользователь не отличает.
5. **Архива преподавателей в БД нет вообще:** `grep -rn "prepodavatel\|teacherArchive" app/src/main/java` → только строка URL (`MpkNetworkClient.kt:256`). Т.е. «показывает хоть за неделю» может быть только следствием **повторного успешного скачивания** (файл ещё лежал на сайте), а не выдумкой. **Выдуманных данных в коде не найдено** — это ключевой вывод: требование выполняется уже сейчас, но нет (а) явного различения состояний и (б) архива, из-за чего после удаления файлов история исчезает.
6. **Календарь преподавателя:** `CollegeScreen.kt:1459-1475` — `CalendarArchiveDialog(groupName = "", …)`; в нём `groupName=""` → список дат для подсветки строится из уроков группы (пусто) → календарь не знает, за какие даты расписание есть. Выбор даты парсится корректно (`MpkScheduleParser.parseDotDate`, `:1467-1471`).
7. **Парсер:** `TeacherScheduleParser.parsePlainText` (`:63-127`) — `dayOfWeek` берётся из заголовка «1 НЕДЕЛЯ ║ СРЕДА 16.09.2026» (`:73`, `dowFromHeaderText` `:30-41`); если заголовка нет — `0`. Слоты метятся днём (`TeacherSlot.dayOfWeek`, `:8-15, :123`). Фильтрация по выбранному дню — `CollegeScreen.kt:1449-1455`: `docDow == 0 → показать все`; **`docDow == 0` — источник возможной путаницы**: документ без заголовка покажет все дни подряд под выбранной датой.

### План
- [ ] **7.1** Различать 4 состояния вместо 2 (`CollegeScreen.kt:1434-1440` + новая модель):
  `NotPublished` (HTTP 404/нет файла) → «Расписание на <дд.ММ.гггг> не опубликовано (файла нет на сайте)»;
  `NoNetwork` → «Нет связи с сайтом колледжа»;
  `ParseFailed` → «Файл получен, но не распознан»;
  `Empty` (документ есть, но сотрудника в нём нет) → «Преподаватель не найден в документе на эту дату».
  Для этого `MpkNetworkClient.fetchTeacherSchedule` (`:250-272`) должен возвращать типизированный результат: добавить `sealed class TeacherFetchError` (или вернуть `Pair<Int /*httpCode*/, …>`) — код ответа уже доступен внутри `downloadFileBytes` (`:243` `response.isSuccessful`), но теряется.
- [ ] **7.2** Явно запретить фолбэк: в `CollegeScreen.kt` не подставлять данные другой даты. Проверить `:1444-1455`: `docDow == 0` → **не показывать слоты** (вместо «показать все»), с сообщением «Не удалось определить день недели в документе». Это и есть требуемое «никогда не выдуманные данные».
- [ ] **7.3** Архив (по желанию заказчика — «должны показываться только реальные данные, локальный архив БД, синканый ранее»): добавить в `LessonDao` **аддитивный** запрос под таблицу `lessons`? Нет — таблица `lessons` не подходит (нет преподавателя как владельца дня). Вариант: кэш скачанных документов **на диск** (`context.filesDir/teacher_sched/<dd.MM.yyyy>.doc`) — не требует изменения схемы Room, работает как архив: при 404 берём файл с диска, если он есть. **Решение по умолчанию — делать (см. §4)**, диф ~40 строк в `CollegeScreen.kt` + `MpkNetworkClient`.
- [ ] **7.4** `CalendarArchiveDialog` (`:1459-1475`): передавать реальные даты, за которые документ доступен (из дискового архива 7.3), чтобы календарь подсвечивал доступные дни, а не пустой список.
- [ ] **7.5** Диагностика: логировать HTTP-код в `SyncDiagnosticInfo` (`MpkNetworkClient.kt:47-48`) — для отлова «404 vs 500».

### Критерии приёмки
- Дата, файл которой удалён: если документ есть в дисковом архиве — расписание показывается; если нет — «не опубликовано», **никогда** не данные другой даты.
- Дата 10 дней назад без архива → «не опубликовано», никаких слотов.
- Документ без заголовка дня → пустой список + сообщение (не «все дни»).

### Тесты
- `TeacherScheduleParserTest` (есть, 4.3 КБ): `dowFromHeaderText` для всех шести дней + вариант без заголовка → `0`; слоты помечены днём.
- Новый `TeacherScheduleSourceTest`: логика выбора источника (`archive → network → error`) — вынести в чистую функцию `resolveTeacherScheduleSource(filesDirExists: Boolean, networkOk: Boolean): Source`.
- `TeacherInsights.surnameOf` на именах из документа (существующий `TeacherInsightsTest` как шаблон).

### Риски
- Дисковый архив растёт (≈50–200 КБ × дни) — ограничить 60 днями по аналогии с `ARCHIVE_KEEP_DAYS` (`ScheduleRepository.kt:14`).
- Изменение сигнатуры `fetchTeacherSchedule` затрагивает `CollegeScreen.kt` и тесты — один файл/один агент, но правку парсера и клиента делает один агент (OW1-D).

---

## O8. Обновить базу студентов (расширенный формат, 18 колонок)

### Диагноз

**Текущий файл:** `app/src/admin/assets/students.csv` — 1578 строк, формат `ФИО;группа;курс;специальностьCode;специальность;…` (см. парсер).
**Парсер:** `StudentsRepository.kt:22-54` — `line.split(';')`, индексы **жёстко зашиты** (`0=ФИО, 1=группа, 2=курс, 3=код, 4=специальность, 5=бюджет, 6=общежитие, 7=куратор, 8=кабинет`), `course = p[2].filter { it.isDigit() }`, `courseMismatch` через `MpkCurriculum.getSpecialty(letter).subjectsByCourse` (`:32-48`). Новая выгрузка даст **другой порядок колонок** → парсер вернёт мусор (группа попадёт в поле «Отчество» и т.д.).

**Источник (уточнено координатором):**
- `D:\MPK app\students_mpk_full_1703 (2).csv` — **1704 строки** (заголовок + 1703), 18 колонок, `;`, все поля в кавычках, BOM UTF-8, дата 2026-09-16. Проверено: **все 1704 строки имеют ровно 18 полей**, вложенных `;` внутри кавычек нет (проверка через `csv.reader`).
- `C:\Users\F708\Downloads\students_mpk_full_30.csv` — фрагмент только по `41С` (30 строк, из них 29 данных; дата 2026-09-23). Данные совпадают с 1703-файлом → **основной источник = 1703**, `_30` — контрольная выборка.
- Колонка «Когорта (Актуальный/Архив)»: **все 1703 строки = «Текущие учащиеся (1-4 курс)»** → архива в выгрузке нет; фильтр по архиву пока не активируется, но поле надо сохранить в модели (появится в следующей выгрузке).
- Полный список групп — **66** (против 58 в текущем `students.csv`); новые: `41Д`, `41М`, `42Д`, `42М`. Коды: `11А 11Г 11Д 11М 11Н 11О 11П 11С 11Т 11Э 12Т 12Э 13Д 13М 13О 13Э 21Г 21Д 21М 21Н 21О 21П 21Т 21Э 22Д 22М 22Н 22О 22Т 22Э 23Э 31Г 31Д 31М 31Н 31О 31П 31Т 31Э 32Д 32М 32Н 32О 32Т 32Э 33М 33О 33Э 41А 41Г 41Д 41М 41Н 41О 41П 41С 41Т 41Э 42Д 42М 42О 42Э` (все 3-символьные, курс+номер+буква).
- Старый текстовый файл со специальностями **не найден** (глоб по `C:\Users\F708\Downloads` и `D:\MPK app`: `*.txt/*.md` с «специал/группа/mpk» — только CSV/ZIP/XLSX) → **CSV самодостаточен** (колонки «Код специальности», «Специальность» есть).

**Важно для `GroupParser`:** `GroupParser.parse` (`:53-90`) требует `course in 1..4`, `groupNumber in 1..9`, длину `i == s.length - 1`, и `MpkCurriculum.isValidSpecialtyCode` (`:76`). Для новых групп `41Д/41М/42Д/42М` код `Д`/`М` в `MpkCurriculum.SPECIALTIES` есть (`:44 'Д'`, `:758 'М'`) — проверить `subjectsByCourse.keys.max()` (влияет на `courseMismatch`, `StudentsRepository.kt:46-47`: для нового 4 курса по `Д` возможен ложный `courseMismatch`, если в curriculum у `Д` нет 4 курса).

### План
- [ ] **8.1** Заменить `app/src/admin/assets/students.csv` содержимым `students_mpk_full_1703 (2).csv` **с сохранением расширенного формата** (18 колонок). Файл — только `admin`-флейвор.
- [ ] **8.2** Переписать разбор `StudentsRepository.kt:22-54` под новые колонки: 0 `№`, 1 `ФИО`, 2 `Фамилия`, 3 `Имя`, 4 `Отчество`, 5 `Курс`, 6 `Группа`, 7 `Когорта`, 8 `Год набора`, 9 `Базовое образование`, 10 `Код специальности`, 11 `Специальность`, 12 `Основа обучения`, 13 `Общежитие`, 14 `Куратор`, 15 `Студент-куратор`, 16 `Кабинет`, 17 `Источник приказа`.
  - снимать кавычки (`"`), курс — `Regex("\\d+").find(p[5])` (`"4 курс"` → `4`);
  - `Student` (`:6-18`) расширить полями `cohort`, `admissionYear`, `baseEducation`, `curator`, `studentCurator`, `nameFirst/nameLast/nameMiddle` (ФИО уже есть) — **не удалять** существующие поля, иначе поедут `StudentsScreens.kt`;
  - **заголовок не парсить как строку данных** (сейчас определяется эвристикой `filter { it.fullName.isNotBlank() }` — заголовок пройдёт фильтр!). Добавить пропуск первой строки по маркеру `№`/`"№"`.
- [ ] **8.3** Извлечь список групп: уникальные значения колонки `Группа` (66) → записать в **новый** `app/src/main/assets/group_codes.csv` (O3-c.1). Генерировать скриптом/вручную из выгрузки — **без ФИО**.
- [ ] **8.4** Проверить `courseMismatch` для новых групп через `MpkCurriculum` (лог/тест): если `getSpecialty('Д').subjectsByCourse.keys.max() < 4` — ослабить проверку (не помечать `4Д` как mismatch).
- [ ] **8.5** БЕЗОПАСНОСТЬ: `StudentsRepository` остаётся `admin`-only. Проверка: `grep -rn "StudentsRepository" app/src/main/java` — единственные вызывающие `StudentsScreens.kt:88` и `CollegeScreen.kt:267` (экран `STUDENTS`), который открывается только по `isAdmin = BuildConfig.FLAVOR == "admin"` (`CollegeScreen.kt:301-311`). **Убедиться, что новый `group_codes.csv` (standard) не содержит ФИО** — только коды.
- [ ] **8.6** `StudentsScreens.kt` (`:88-102`, `:179-182`, `:319-336`): добавить фильтр по «Когорта» (пока no-op, но модель готова) и подпись курса из нового поля; не ломать существующие фильтры группа/курс/поиск.

**Приёмка:**
- `:app:testStandardDebugUnitTest` — зелёный;
- `unzip -l app-standard-release.apk | grep -c students.csv` → **0**;
- в admin-сборке: 1703 студента, 66 групп в фильтре, поиск «Фамилия» работает, карточка студента открывается (расписание группы — `StudentsScreens.kt:378-421`, там синк чужой группы, работает и сейчас).

**Тесты:** `StudentsCsvParseTest` (новый, Robolectric):
- строка `"1";"Абражевич Елизавета Владиславовна";…;"41Д";…` → `fullName`, `group = "41Д"`, `course = "4"`, `specialtyCode = "5-04-0414-01"`, `cohort = "Текущие учащиеся (1-4 курс)"`;
- заголовок строки не попадает в результат;
- строка с пустыми полями не падает;
- `distinctGroups()` = 66; в списке есть `41Д`, `42М`.
**Риски:** (1) смещение колонок — тест обязателен; (2) размер файла в APK admin (~800 КБ → 2 МБ: 1703 строки × ~400 байт UTF-8 ≈ 700 КБ, приемлемо); (3) `students_mpk_full_1703 (2).csv` содержит все 1703 строки «Текущие» — если заказчик ждёт архив, его в выгрузке нет (зафиксировано).

---

## O9. Оптимизация размера APK — остаток

**Сделано (волна A):** фото преподавателей 5.6 → 1.7 МБ (480 px, q78, `.jpeg/.png` → `.jpg` + правка `teachers.csv`, 2 битые ссылки на `.svg` → фолбэк-аватар); убраны firebase-bom/ai/appcheck, retrofit, converter-moshi, moshi-kotlin, logging-interceptor (**проверено: 0 ссылок** — `grep -ril` по `app/src/main/java`); `app/build.gradle.kts:76-80` → `isCrunchPngs = true`, `isMinifyEnabled = true`, `isShrinkResources = true`; написан `proguard-rules.pro`.

### Остаток

- [ ] **9.1** Карты этажей: `res/drawable/map_floor1.jpg` 268 КБ, `map_floor2.jpg` 285 КБ, `map_floor34.jpg` 270 КБ (1376×768 каждый, суммарно **824 КБ**). Заменить на WebP q80 (ожидаемо −250…−350 КБ суммарно) — изображение плоское, потерь не видно; `mapRes()` (`CollegeMapScreen.kt:1110-1114`) уже ссылается на `R.drawable.map_floor*`, имена сохранить.
- [ ] **9.2** Мёртвые ресурсы (проверено grep'ом):
  - `IntroSplash.kt` (161 строка) — **0 вызывающих** (`grep -rn "IntroSplash" app/src/main/java` → только сам файл) + `res/drawable/ic_splash.png` (13.8 КБ) — только он на него и ссылался → удалить оба;
  - `GroupBadge.kt` (107 строк, 3.9 КБ) — **0 вызывающих** → удалить;
  - `MainHeaderBanner.kt` — **используется** (обёртка `MainTopBar.kt:9` → `MainScreen.kt:89` `topBar`) → **НЕ удалять**. `TopBar` оставить как есть.
  **Итого экономия:** ~15 КБ ресурсов + ~11 КБ dex (R8 вырежет и так — ценность в чистоте кода).
- [ ] **9.3** `widget_preview_countdown.png` (1.5 КБ) используется как превью всех 4 виджетов (`res/xml/*_widget_info.xml:10`) — оставить.
- [ ] **9.4** Прогнать релизные сборки и **замерить**: `:app:assembleStandardRelease`, `:app:assembleAdminRelease`. Зафиксировать в этом файле таблицу «до/после». Ожидание по perf-review: 23.7 МБ → ~15–17 МБ.
- [ ] **9.5** **Smoke прошивки R8** (обязательно, т.к. `isMinifyEnabled` включён впервые): протестировать на устройстве — запуск, синк, виджеты всех 4 типов, уведомление «До звонка», настройка темы, `Room` (схема `lessons/tasks`), чтение ассетов (`teachers.csv`, `college_map.json`). При падении — правило в `proguard-rules.pro`, а не откат шринка.
- [ ] **9.6** Проверить `isShrinkResources` не вырезал ресурсы, на которые есть только `string`-ссылки/`xml` (`res/xml/*_widget_info.xml` hold через `android:previewImage` — обычно safe, но виджеты часто ломаются именно тут).

**Приёмка:** APK standard/admin собираются; размер ≤ 18 МБ; `unzip -l app-standard-release.apk | grep -c students.csv` → 0; smoke-сценарий 9.5 пройден; все 4 виджета рисуются.
**Тесты:** `WidgetLayoutsTest`, `UiRenderSmokeTest` (Roborazzi) — прогон как регрессия после шринка ресурсов.
**Риски:** WebP на `minSdk = 24` — поддерживается (API 14+ для lossy); `isCrunchPngs` уже включён и на `.jpg` не влияет.

---

## Волны исполнения

**Правило:** один файл — один агент в волне. Файлы между волнами передаются «из рук в руки», интерфейсы фиксируются в начале волны.

### OW1 — 4 параллельных агента (независимые подсистемы)

**Агент OW1-A (O1: состояние и кэш UI).** Владеет файлами:
- `app/src/main/java/com/politex/ui/screens/MainScreen.kt`
- `app/src/main/java/com/politex/ui/screens/ScheduleScreen.kt`
- `app/src/main/java/com/politex/ui/viewmodel/AppViewModel.kt`
- `app/src/main/java/com/politex/MainActivity.kt`
- `app/src/main/java/com/politex/data/repository/ScheduleRepository.kt`
- `app/src/main/java/com/politex/data/local/dao/LessonDao.kt`
- `app/src/main/java/com/politex/data/repository/TeachersRepository.kt`
- `app/src/main/java/com/politex/widget/WidgetUpdateHelper.kt`
- `app/src/main/java/com/politex/widget/NowNextWidgetProvider.kt`
- `app/src/main/java/com/politex/widget/TodayScheduleWidgetProvider.kt`
- `app/src/test/java/com/politex/ScheduleDaySelectTest.kt` (новый)
- `app/src/test/java/com/politex/RoomScheduleLogicTest.kt` (**только** если нужна правка под новую сигнатуру — иначе не трогать)

Обязательства перед другими агентами (замороженный интерфейс):
```
ScheduleRepository.selectDayLessons(all, dateString, dayOfWeek): List<LessonEntity>   // чистая, companion
LessonDao.replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>)  // @Transaction
```

**Агент OW1-B (O8 + источник групп для O3-c).** Владеет файлами:
- `app/src/admin/assets/students.csv` (замена содержимого)
- `app/src/main/java/com/politex/data/repository/StudentsRepository.kt`
- `app/src/main/java/com/politex/ui/screens/StudentsScreens.kt`
- `app/src/main/assets/group_codes.csv` (новый)
- `app/src/main/java/com/politex/util/GroupCatalog.kt` (новый)
- `app/src/main/java/com/politex/util/GroupParser.kt` (только если нужен доступ к `normalizeSpecialtyChar` — уже публичный, `:100-103`; в идеале **не трогать**)
- `app/src/test/java/com/politex/StudentsCsvParseTest.kt` (новый)
- `app/src/test/java/com/politex/GroupCatalogTest.kt` (новый)

Замороженный интерфейс для OW2-A:
```
object GroupCatalog {
    fun all(): List<String>
    fun find(input: String): String?      // канонический код или null
}
```

**Агент OW1-C (O6: граница 06:00 + предикаты).** Владеет файлами:
- `app/src/main/java/com/politex/widget/BellCountdownWidgetProvider.kt`
- `app/src/main/java/com/politex/util/BellCountdownNotifier.kt` (в т.ч. остаток O5: удаление `isPinned`/`setPinned`/`PREF_PINNED`, `setOngoing(true)`)
- `app/src/main/java/com/politex/util/BellTimerService.kt` (только если понадобится уточнить демоут)
- `app/src/test/java/com/politex/BellCutoffTest.kt` (расширение)

**Агент OW1-D (O7: преподаватели — только реальные данные).** Владеет файлами:
- `app/src/main/java/com/politex/ui/screens/CollegeScreen.kt` (**только** `TeacherScheduleTab`, `:1395-1475` и импорты)
- `app/src/main/java/com/politex/data/network/MpkNetworkClient.kt` (только `fetchTeacherSchedule`, `downloadFileBytes`)
- `app/src/main/java/com/politex/data/network/TeacherScheduleParser.kt`
- `app/src/test/java/com/politex/TeacherScheduleParserTest.kt`
- `app/src/test/java/com/politex/TeacherScheduleSourceTest.kt` (новый)

> **Конфликт, который нужно соблюсти:** `MpkNetworkClient.kt` в OW1-D и в OW2-B (O3-b). Разделение — по **разным функциям**, но правки в одном файле в разных волнах. Правило: OW1-D завершает и коммитит первым; OW2-B стартует после зелёных тестов OW1.

### OW2 — 3 параллельных агента (после OW1)

**Агент OW2-A (O3-a, O3-c, O3-d — вся карта).** Владеет файлами:
- `app/src/main/java/com/politex/ui/screens/CollegeMapScreen.kt`
- `app/src/test/java/com/politex/MapHitTestTest.kt` (новый)
- `app/src/test/java/com/politex/MapScreenShotTest.kt` (обновление эталонов Roborazzi)
- `app/src/test/java/com/politex/MapNumberScaleTest.kt` (новый)

Потребляет: `GroupCatalog` (OW1-B), `ScheduleRepository.syncDayAllGroups` + `RoomScheduleDialog` (OW2-B — сигнатура заморожена ниже).

**Агент OW2-B (O3-b: кабинет по всем группам).** Владеет файлами:
- `app/src/main/java/com/politex/data/network/MpkScheduleParser.kt` (`parseFileAllGroups`)
- `app/src/main/java/com/politex/data/network/MpkNetworkClient.kt` (`fetchAllGroupsScheduleForDay` — после OW1-D)
- `app/src/main/java/com/politex/data/repository/ScheduleRepository.kt` (`syncDayAllGroups` — после OW1-A)
- `app/src/main/java/com/politex/ui/components/RoomScheduleDialog.kt` (минутный тикер «СЕЙЧАС», разделители пар)
- `app/src/test/java/com/politex/RoomDayScheduleTest.kt` (новый)

Замороженный интерфейс для OW2-A:
```
ScheduleRepository.syncDayAllGroups(dateString: String, dayOfWeek: Int): Result<Int>
RoomScheduleDialog(roomNumber, dateTitle, lessons, currentLessonNumber, onTeacherClick, onDismissRequest, modifier)  // сигнатура НЕ меняется
```

**Агент OW2-C (O9-остаток + уборка мёртвых файлов).** Владеет файлами:
- `app/src/main/res/drawable/map_floor1.webp`, `map_floor2.webp`, `map_floor34.webp` (новые) + удаление соответствующих `.jpg`
- `app/src/main/res/drawable/ic_splash.png` (удаление)
- `app/src/main/java/com/politex/ui/components/IntroSplash.kt` (удаление)
- `app/src/main/java/com/politex/ui/components/GroupBadge.kt` (удаление)
  (`MainHeaderBanner.kt` **не** удаляется — вызывается из `MainTopBar.kt:9`)
- `app/build.gradle.kts`, `app/proguard-rules.pro` (только если smoke R8 выявит пробел)

### OW3 — верификация (1 агент, последовательно)

- [ ] `sh ./gradlew :app:testStandardDebugUnitTest` — зелёный;
- [ ] `:app:assembleStandardRelease` + `:app:assembleAdminRelease`;
- [ ] `unzip -l app-standard-release.apk | grep -c students.csv` = **0**; в admin — **1**;
- [ ] `grep -rn "Confetti\|confetti" app/src` = **0**;
- [ ] `grep -rn "StudentsRepository" app/src/main/java` — только `StudentsScreens.kt` + `CollegeScreen.kt`;
- [ ] размеры APK — вписать в §0.2;
- [ ] ручные сценарии: O1 (переключение дня / возврат с вкладки), O3b (кабинет 249, 3 группы), O3c (`41о` латиницей), O3d (тап по кабинету), O6 (05:59/06:00), O7 (дата без файла — «не опубликовано»).

---

## Решения по умолчанию (автономный режим)

1. **Что переделывать из волны A — ничего.** O2/O4/O5-UI/O9-часть закрыты коммитом `f90e11e`; просмотрел код и подтвердил. В план вошли только остатки (O5-мёртвое-API, O9-webp/R8-smoke, O6-граница).
2. **O3-b: схема «догрузка дня по всем группам»**, а не фоновый синк всего колледжа. Причина: данные уже содержатся в том же документе, который приложение и так качает; минимальный диф — новый парсер-режим + один метод репозитория. **Не** делаю: изменение схемы Room, отдельную таблицу «кабинеты», воркер на все группы.
3. **O3-b: сеть — по требованию (тап по «Подробнее»), с честным «показываем то, что есть» при ошибке.** Не блокирую UI, не показываю выдуманное.
4. **O3-c: список групп — в `main/assets` (оба флейвора), только коды.** Персональных данных нет → ограничение «только admin» на него не распространяется. `GroupParser.generateCommonGroups()` не трогаю (используется в `GroupSelectionDialog`/`GroupScheduleScreen` — отдельная задача, не входит в O1..O9).
5. **O3-a: курсив в коде отсутствует** — лечу реальную причину (мелкий `fit` + толстый белый ореол), а не «меняю `fontStyle`». Диапазон: минимальный размер + тонкий ореол; если заказчику покажется мелко — смотреть zoom, не вес шрифта.
6. **O5: ползунок убран, API вычищаю.** Вердикт «не чинить» относится к функциональности `setOngoing`; мёртвый публичный API удаляю, `setOngoing(true)` фиксирую (иначе поведение неявно зависит от pref-значения по умолчанию `true`, `:102`).
7. **O6: демоут, а не остановка сервиса.** Заказчик прямо разрешил оба варианта; демоут — минимальный диф и не ломает `START_STICKY` (`BellTimerService.kt:35`). Отдельно: сдвиг `SHOW_FROM_MINUTE` 07:00→06:00 действует и на виджеты — считаю это соответствующим ожиданию заказчика (он жаловался на виджеты в O1), а не побочным эффектом.
8. **O7: дисковый архив документов преподавателей — делаю**, потому что требование «локальный архив, синканый ранее» без него не выполняется после удаления файлов с сайта. Схему Room не трогаю (файлы в `filesDir`), TTL 60 дней по аналогии с `ARCHIVE_KEEP_DAYS`.
9. **O8: источник — `students_mpk_full_1703 (2).csv`** (1703 строки, 66 групп, 18 колонок), `_30` — контрольная выборка по `41С`. Колонку «Когорта» сохраняю в модели, но фильтр не включаю (архива в выгрузке нет). Текстовый файл со специальностями не найден → CSV самодостаточен.
10. **O8: файл `students.csv` остаётся в расширенном формате 18 колонок** (не ужимаю до 10) — так следующее обновление выгрузки не потребует правок парсера. Размер +~0.5 МБ только в admin.
11. **O4: `SharedPreferences`-ключи конфетти не чищу** — мусор безвреден, код для очистки = лишний диф. При следующей смене формата prefs — уйдёт сам.
12. **`fallbackToDestructiveMigration` не трогаю; схему не меняю.** Все новые DAO-методы — только `@Transaction`-обёртки и `SELECT`'ы.
13. **`GroupParser` в OW1-B не правлю без нужды** — иначе OW2-A получит рассинхрон; `normalizeSpecialtyChar` уже публичен.
14. **Мёртвые `IntroSplash`/`MainHeaderBanner`/`GroupBadge`** — удаляю только после подтверждения нуля вызывающих (`grep` уже показал 0; агент подтвердит ещё раз). Если найдётся вызывающий — файл остаётся, задача отмечается как «требует уточнения».

### Требует уточнения (зафиксировано, не блокирует)
- Реальный формат `.doc` расписания дня: покрывает ли `parseFileAllGroups` все 66 групп в проде — проверяется только на живом файле/фикстуре (`Step5OfficialDocTest`, `RealSiteDocParserTest`).
- Суббота: набор звонков для «СУББОТА»-графика (`CollegeBellSchedule.getBellsForDay(6)`) — нужно ли считать субботу учебным днём для `ALL_BELLS` (влияет на O6.2).
- Нужен ли заказчику дисковый архив преподавателей «навсегда» (сейчас default — 60 дней).

---

## Карта файлов → задач

| Файл | Задачи | Волна |
|---|---|---|
| `app/src/main/java/com/politex/MainActivity.kt` | O1-f | OW1-A |
| `app/src/main/java/com/politex/ui/screens/MainScreen.kt` | O1-c, O1.7 | OW1-A |
| `app/src/main/java/com/politex/ui/screens/ScheduleScreen.kt` | O1-a, O1-c, O1-d | OW1-A |
| `app/src/main/java/com/politex/ui/viewmodel/AppViewModel.kt` | O1-e | OW1-A |
| `app/src/main/java/com/politex/data/repository/ScheduleRepository.kt` | O1-b, O3-b (`syncDayAllGroups`) | OW1-A → OW2-B |
| `app/src/main/java/com/politex/data/local/dao/LessonDao.kt` | O1-b (`replaceForDate`) | OW1-A |
| `app/src/main/java/com/politex/data/repository/TeachersRepository.kt` | O1-d (кэш) | OW1-A |
| `app/src/main/java/com/politex/widget/WidgetUpdateHelper.kt` | O1-f | OW1-A |
| `app/src/main/java/com/politex/widget/NowNextWidgetProvider.kt` | O1-f | OW1-A |
| `app/src/main/java/com/politex/widget/TodayScheduleWidgetProvider.kt` | O1-f | OW1-A |
| `app/src/main/java/com/politex/widget/BellCountdownWidgetProvider.kt` | O6.1, O6.2, O1-f | OW1-C |
| `app/src/main/java/com/politex/util/BellCountdownNotifier.kt` | O6, O5.1 | OW1-C |
| `app/src/main/java/com/politex/util/BellTimerService.kt` | O6 (демоут), O1-f | OW1-C |
| `app/src/main/java/com/politex/ui/screens/CollegeScreen.kt` | O7.1–O7.5 (только `TeacherScheduleTab`) | OW1-D |
| `app/src/main/java/com/politex/data/network/MpkNetworkClient.kt` | O7.1, O7.5 → затем O3-b | OW1-D → OW2-B |
| `app/src/main/java/com/politex/data/network/TeacherScheduleParser.kt` | O7 | OW1-D |
| `app/src/admin/assets/students.csv` | O8.1 | OW1-B |
| `app/src/main/java/com/politex/data/repository/StudentsRepository.kt` | O8.2 | OW1-B |
| `app/src/main/java/com/politex/ui/screens/StudentsScreens.kt` | O8.6 | OW1-B |
| `app/src/main/assets/group_codes.csv` (новый) | O8.3, O3-c.1 | OW1-B |
| `app/src/main/java/com/politex/util/GroupCatalog.kt` (новый) | O3-c.2, O8.3 | OW1-B |
| `app/src/main/java/com/politex/ui/screens/CollegeMapScreen.kt` | O3-a, O3-c, O3-d, O3-b.3, O3-b.4 | OW2-A |
| `app/src/main/java/com/politex/ui/components/RoomScheduleDialog.kt` | O3-b.4, O3-b.5 | OW2-B |
| `app/src/main/java/com/politex/data/network/MpkScheduleParser.kt` | O3-b.1 | OW2-B |
| `app/src/main/res/drawable/map_floor{1,2,34}.webp` | O9.1 | OW2-C |
| `app/src/main/res/drawable/ic_splash.png` (удаление) | O9.2 | OW2-C |
| `app/src/main/java/com/politex/ui/components/IntroSplash.kt` (удаление) | O9.2 | OW2-C |
| `app/src/main/java/com/politex/ui/components/GroupBadge.kt` (удаление) | O9.2 | OW2-C |
| `app/build.gradle.kts`, `app/proguard-rules.pro` | O9.4, O9.5 | OW2-C |
| `app/src/test/java/com/politex/BellCutoffTest.kt` | O6 | OW1-C |
| `app/src/test/java/com/politex/ScheduleDaySelectTest.kt` (новый) | O1.1 | OW1-A |
| `app/src/test/java/com/politex/StudentsCsvParseTest.kt` (новый) | O8 | OW1-B |
| `app/src/test/java/com/politex/GroupCatalogTest.kt` (новый) | O3-c | OW1-B |
| `app/src/test/java/com/politex/TeacherScheduleParserTest.kt` | O7 | OW1-D |
| `app/src/test/java/com/politex/TeacherScheduleSourceTest.kt` (новый) | O7 | OW1-D |
| `app/src/test/java/com/politex/RoomDayScheduleTest.kt` (новый) | O3-b | OW2-B |
| `app/src/test/java/com/politex/MapHitTestTest.kt` (новый) | O3-d | OW2-A |
| `app/src/test/java/com/politex/MapNumberScaleTest.kt` (новый) | O3-a | OW2-A |
| `app/src/test/java/com/politex/MapScreenShotTest.kt` | O3-a | OW2-A |

**Файлы, которые не должен трогать никто:** `app/src/main/java/com/politex/data/local/MpkDatabase.kt` (схема), `app/src/main/java/com/politex/data/local/entity/LessonEntity.kt` (схема), `app/src/main/res/xml/*_widget_info.xml`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/politex/util/MpkCurriculum.kt`, `app/src/main/java/com/politex/util/SubjectFormatter.kt`.