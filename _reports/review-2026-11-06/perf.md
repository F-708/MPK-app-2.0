# Ревью домена «Производительность и Compose» — «Мой Политех» v2.5

Дата: 2026-11-06 · Ревьюер: rev-perf · Ветка: `v2.4-stable` (versionCode 20014) · minSdk 24 / targetSdk 36
Режим: **read-only** — только чтение/grep. Gradle, тесты, профилирование и Macrobenchmark **не запускались** (гейты у Лида).

Все находки привязаны к `путь:строка`. Где эффект не измерен, а выведен из кода — помечено
**«гипотеза, требует замера»**. Цифр «на глаз» в отчёте нет: только количество вхождений в коде,
константы из кода и линейки Android, которые можно перепроверить.

Пути указаны от корня репозитория `MPK-app-2.1/`.

> Патч-готовый бэклог по P1/P2 (фрагменты «было → стало», эффект, риск, измерение, волны применения) —
> в соседнем файле [`perf-fixes-backlog.md`](perf-fixes-backlog.md).

---

## 0. Сводка

| Приоритет | Кол-во | Суть |
|---|---|---|
| **P1** | 4 | PdfRenderer на главном потоке; 1 Hz-тик виджетов и alarm-цепочка; отсутствие lifecycle-aware сбора состояния; двойной декод .doc + префетч 6 документов |
| **P2** | 7 | Парсинг JSON карты в композиции; `verticalScroll` над всем документом; списки без `key` (15 мест); веер `updateAllWidgets`; кэш `ByteArray` в синглтоне без дискового кэша; стартовая цепочка в `onCreate`; отсутствие профиля запуска приложения |
| **P3** | 4 | Бесконечная анимация в bottom bar; аллокации в `bottomBar`; `SimpleDateFormat`/`String.format` в горячих местах; инвалидация кэша преподавателей на каждый resume |

**Важно:** кодовая база в части Compose **заметно аккуратнее ожидаемого** — `remember(key)` расставлен
почти везде (ScheduleScreen, GradesScreen, TasksScreen, MaterialBaseScreen, CollegeMapScreen),
regex в `MpkScheduleParser` предкомпилированы, виджеты не используют `setImageViewBitmap`,
`BellTimerService` умеет останавливать себя. Поэтому в отчёте нет «микрооптимизаций ради галочки»:
ниже — только то, что реально видно пользователю (фриз, джанк, батарея, память) или системе (вакуум пробуждений).

---

## P1

### [P1] PdfRenderer открывается на главном потоке / `ui/screens/materials/PdfViewer.kt:205-213`
**Симптом:** тап по PDF-материалу → «залипание» на 100–600 мс (на сканах и многомегабайтных PDF — больше),
пропущенные кадры анимации перехода; на слабых устройствах — до ANR при открытии тяжёлого файла.
**Root cause:** открытие идёт в `produceState`, а его блок выполняется в корутине композиции, т.е. на
**главном** потоке:
```kotlin
val handleState by produceState<Result<Pair<ParcelFileDescriptor, PdfRenderer>>?>(null, key1 = file) {
    value = runCatching {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) // disk IO
        pfd to PdfRenderer(pfd)                                                        // разбор xref/trailer
    }
}
```
Ни `withContext(Dispatchers.IO)`, ни предварительного открытия в `LaunchedEffect` нет. Рендер самих страниц
при этом сделан правильно (`PdfViewer.kt:886-901`: `withContext(Dispatchers.Default)` + `Mutex`), т.е. проблема
локальна и в одном месте.
**Фикс:** обернуть тело в `withContext(Dispatchers.IO) { ... }` (можно `produceState` целиком); для больших
файлов дополнительно показывать плейсхолдер и открывать `PdfRenderer` в `Init`-фазе через `remember`-корутину.
`DisposableEffect` на закрытие (`PdfViewer.kt:216-223`) уже корректен — его не трогать.
**Как измерить:** Perfetto/`Trace` — длительность main-thread slice между «тап по элементу» и первым
`Choreographer#doFrame` после открытия; `JankStats` (`FrameTimingMetric`) на сценарии «открыть PDF 20 стр.».
Ожидаемый эффект: −150…500 мс фриза (гипотеза, требует замера).

### [P1] Секундный отсчёт: FGS-цикл 1 Hz + самоперевзводящаяся alarm-цепочка / `util/BellTimerService.kt:100-130`, `widget/BellCountdownWidgetProvider.kt:73-77`, `widget/BellCountdownWidgetProvider.kt:472+ (object WidgetAlarm)`
**Симптом:** батарея. При включённом виджете «До звонка» с секундами (и/или строке отсчёта со стилем
`WITH_SECONDS`) приложение держит 1 обновление в секунду; если foreground-сервис не может работать —
1 точный alarm в секунду в школьном окне.
**Root cause (два независимых механизма):**
1. `BellTimerService.startTicking()` (`BellTimerService.kt:100-130`) при `secondMode` считает
   `delay(1000L - nowMs % 1000L)` и каждую секунду: читает 2–3 SharedPreferences
   (`BellCountdownNotifier.isEnabled`, `WidgetUpdateHelper.hasSecondsWidgets`, `getStyle`),
   затем `BellCountdownWidgetProvider.updateSecondsWidgets` — а это на каждый виджет
   `getAppWidgetIds` + `WidgetOptions.seconds` (prefs) + сборка `RemoteViews` + IPC в лончер
   (`BellCountdownWidgetProvider.kt:73-77`, `buildViews` там же).
2. Если FGS не запускается (`shouldScheduleSecondTick` = `hasSecondsWidgets && !serviceRunning && inSchoolWindow`,
   `WidgetAlarm` в том же файле), планируется `setExactAndAllowWhileIdle(RTC, now + 1000 мс)`; приёмник
   `WidgetAlarmReceiver.ACTION_SECOND_TICK` → `handleSecondTick` → `updateSecondsWidgets` **и снова**
   `scheduleNextSecond` (`widget/BellCountdownWidgetProvider.kt`, `class WidgetAlarmReceiver`).
   Окно — 7:00–21:00 (`SCHOOL_START/SCHOOL_END` в `object WidgetAlarm`), т.е. до 14 ч/сутки.
   Процесс при этом может быть уже убит → каждый тик = холодный старт процесса
   (`MpkApplication.onCreate` → `AppGraph.init` → открытие Room).
**Фикс (по возрастанию риска):**
- Секунды показывать **только** в 2–3 минутах до/после звонка (сейчас крутятся весь учебный день) — это
  сразу снимает основную массу тиков; окно вычисляется из `CollegeBellSchedule` (в `countdownForMinute` уже есть).
- Заменить построение `RemoteViews` в секундном тике на `RemoteViews.setChronometer(...)`/`setChronometerCountDown`
  (API 24+): секунды идут на стороне лончера, IPC — один раз на смену состояния. Тогда `BellTimerService`
  нужен 1 раз в минуту, а секундная alarm-цепочка не нужна вообще.
- Для fallback-alarm: `setWindow(...)` вместо `setExactAndAllowWhileIdle` (и/или не перевзводить цепочку,
  когда экран выключен: `PowerManager.isInteractive`).
**Как измерить:** `adb shell dumpsys alarm | grep -A3 politex` (число `+1s` алармов), `dumpsys batterystats`
(раздел `Alarm Stats` и `Foreground Service`), Battery Historian; кол-во `secondsWidgets`-обновлений — счётчик
в `updateSecondsWidgets`. Ожидаемый эффект: десятки тысяч пробуждений/сутки → единицы (гипотеза, требует замера
на устройстве с виджетом секунд).

### [P1] Нигде не используется lifecycle-aware сбор состояния / `collectAsState` без Lifecycle (30+ мест)
**Симптом:** батарея/нагрузка в фоне. Свёрнутое приложение продолжает подписки на Room-запросы и
рекомпозицию при каждом изменении таблиц (синк в WorkManager пишет в БД → экран перерисовывается в фоне).
**Root cause:** `collectAsStateWithLifecycle` не встречается **ни в одном** файле (grep: 0 совпадений),
при этом `collectAsState` — более 30 раз, в т.ч. на долгоживущих экранах:
- `ui/screens/MainScreen.kt:168` (`uiState` всего NavHost), `:170`, `:322-323`;
- `ui/screens/GradesScreen.kt:182-183`, `:414`, `:417`, `:847`;
- `ui/screens/CollegeScreen.kt:984-988`, `:1239`;
- `ui/screens/materials/PdfViewer.kt:257`, `:303`; `materials/ImageViewer.kt:131`;
- `ui/screens/MaterialBaseScreen.kt:189`; `ui/components/CalendarArchiveDialog.kt:111-112`.

`stateIn(..., SharingStarted.WhileSubscribed(5_000))` в `AppViewModel` (`:153`, `:169`, `:180`) помогает только
для VM-флоу и не отписывается при уходе приложения в фон: композиция жива → подписка жива.
**Фикс:** заменить на `collectAsStateWithLifecycle()` — зависимость уже подключена
(`libs.androidx.lifecycle.runtime.compose` в `app/build.gradle.kts`), API-правок не требуется. Это
механическая, безопасная замена 1:1.
**Как измерить:** Perfetto-трейс 30 с при свёрнутом приложении с параллельным синком: до/после — наличие
кадров `Choreographer` и потоков `pool-*-thread-*` (Room). Также `dumpsys gfxinfo com.politex framestats`
до/после.

### [P1] Двойной декод .doc при поиске файла + параллельный префетч полных документов / `data/network/MpkNetworkClient.kt:363`, `:250-254`, `data/network/MpkScheduleParser.kt:349-376`
**Симптом:** долгий синк (секунды), расход мобильного трафика; на слабом устройстве — заметное потепление
корпуса во время обновления расписания. Плюс пик памяти при парсинге.
**Root cause (три эффекта в одном тракте):**
1. **Двойной декод каждого кандидата.** Для каждого скачанного документа сначала
   `MpkScheduleParser.documentMatchesDate(docBytes, dateDotStr)` (`MpkNetworkClient.kt:363`), внутри —
   `extractHeaderText` (`MpkScheduleParser.kt:544-560`), который заново прогоняет
   `extractUtf16LeRuns(bytes)` **и** `String(bytes, CP1251)`; только потом `parseFile(...)` декодирует документ
   ещё раз. Т.е. тело документа обрабатывается минимум дважды до полезной работы.
2. **`extractDocTexts` материализует 4 полные копии документа сразу** (`MpkScheduleParser.kt:349-376`):
   `utf16Text` (join по runs), `cp1251Text` (join по runs), `utf8Text`, `cp1251RawText` — независимо от того,
   какая кодировка окажется рабочей. Для .doc в 300 КБ это ~1.2–1.5 МБ char-данных + списки runs, поверх
   исходного `ByteArray`.
3. **Префетч качает полные документы пачками по 6** (`PREFETCH_BATCH = 6`, `MpkNetworkClient.kt:75`, `:250-254`):
   `prefetch(batch) { async { downloadFileBytes(it) } }` — до 6 полных .doc параллельно, чтобы затем отбросить
   те, у которых не совпала дата в шапке. При первом синке без `urlMemo` это до 6 документов на один вызов
   (`generateCandidateDirectUrls` даёт 24 кандидата: 2 месяца × 2 формата даты × 3 суффикса × 2 расширения).
**Фикс:**
- `documentMatchesDate` должен работать по первым N КБ (шапка «РАСПИСАНИЕ …» всегда в начале) либо
  возвращать `DocTexts`, который переиспользуется в `parseFileRawTexts` — один декод вместо двух.
- `extractDocTexts`: сделать ленивым (`by lazy`/`let` по требованию) — сначала UTF-16, только при неудаче
  считать cp1251/utf8.
- Префетч: ограничить `PROBE`-подобной проверкой (HEAD/first-bytes) до полной загрузки; `PREFETCH_BATCH`
  снизить до 2–3 или качать последовательно с ранним выходом.
**Как измерить:** `SyncDiagnosticInfo.receivedBytes` (уже пишется в `MpkNetworkClient.fetchScheduleForGroup`)
до/после; время `syncSchedule` из лога; Perfetto — длительность `extractDocTexts` на IO-потоке + peak heap
(Android Studio Memory Profiler, «холодный» синк без кэша). Ожидаемый эффект: −30…50 % трафика на первый синк,
−1 проход декода (гипотеза, требует замера).

---

## P2

### [P2] JSON карты читается и парсится в композиции / `ui/screens/CollegeMapScreen.kt:149`, `data/model/CollegeMapData.kt` (`fun load`, `fun parse`)
**Симптом:** подлагивание при первом входе на экран карты; повторяется при **каждом** новом входе
(`remember` живёт в рамках композиции экрана).
**Root cause:** `val mapData = remember(context, contentRevision) { CollegeMapData.load(context) }` —
`load` = `ContentAssets.open("college_map.json").bufferedReader().readText()` + полный `JSONObject`-разбор
с обходом всех этажей/комнат/коридоров/узлов, **синхронно на главном потоке** в момент композиции.
Ассет ~70 КБ (`app/src/main/assets`), т.е. десятки миллисекунд на слабом устройстве.
**Фикс:** грузить через `produceState` + `withContext(Dispatchers.IO)` (как сделано в
`KnownTeachers.kt:TeachersCache.load`), либо кэшировать результат в синглтоне с инвалидацией по
`contentRevision` — экран открывают часто, данные меняются редко.
**Как измерить:** Perfetto: main-thread slice `CollegeMapData.load` при первом фрейме экрана карты;
время до первого кадра (`ActivityReportFullyDrawn`-подобный маркер навигации).

### [P2] Весь документ в одном `verticalScroll` вместо ленивого списка / `ui/screens/materials/DocTextViewer.kt:337-356`
**Симптом:** открытие длинного .docx/.txt (1000+ абзацев) — фриз на входе, джанк при прокрутке, высокий RSS;
прокрутка «догоняет» палец.
**Root cause:** `Column` внутри `Modifier.verticalScroll(scrollState)` со всеми абзацами
(`text.split('\n').map { DocxParagraph(...) }`, `DocTextViewer.kt:576-592`) — все `Text`-узлы композируются,
измеряются и удерживаются одновременно; плюс `onSizeChanged` → `sheetCount` → `repeat(sheets)` поверх того же
`Column`. Для документа в 800 абзацев это ~800 текстовых узлов в одном measure/layout проходе.
**Фикс:** `LazyColumn` с `items(paragraphs, key = { index })`; «бумажные листы» рисовать фоном-слоем
(`drawBehind` по вычисленной высоте) или разбивать абзацы на страницы по `pageHeightPx` (логика `sheetCount`
в `DocTextViewer.kt:96-100` уже есть) и рендерить `items(pages)`.
**Как измерить:** `JankStats` на сценарии «открыть .docx на 50 КБ и проскроллить до конца»; число фреймов
> 16 мс; RSS до/после (Memory Profiler). Ожидаемо: снятие фриза входа и стабильные 60 fps при прокрутке
(гипотеза, требует замера).

### [P2] `LazyColumn` без `key` в 15 местах / см. список
**Симптом:** при вставке/удалении элемента (задача, оценка, преподаватель) список теряет состояние элементов:
сбрасывается анимация, «прыгает» позиция; при переупорядочивании — лишние рекомпозиции всех видимых элементов.
**Root cause / где:** `items(list)` без `key =`:
`ui/screens/CollegeScreen.kt:632, 744, 1039, 1406, 1618, 1916`;
`ui/screens/GradesScreen.kt:231`; `ui/screens/StudentsScreens.kt:221, 639, 693, 777, 828`;
`ui/screens/TasksScreen.kt:932, 1341, 1510`.
Для сравнения, в «сложных» списках ключи **есть** и сделаны правильно:
`ScheduleScreen.kt:799`, `GroupScheduleScreen.kt:736`, `TasksScreen.kt:368`, `PdfViewer.kt:538`,
`MaterialBaseScreen.kt:736, 923, 1000`.
**Фикс:** добавить `key` (у сущностей с id — `it.id`; у строк-заголовков — стабильная строка-константа).
В `StudentsScreens.kt:693` (`subjects`) — `key = { it }`.
**Как измерить:** Layout Inspector → Recomposition counts при добавлении/удалении элемента; визуально —
«подпрыгивание» списка. Правка низкорисковая.

### [P2] Веер `updateAllWidgets()` по 6 провайдерам на каждое событие / `widget/WidgetUpdateHelper.kt:80-86` + 7 вызывающих
**Симптом:** микро-нагрузка на батарею/CPU, лишние IPC в лончер; заметно на устройствах с несколькими
экземплярами виджетов.
**Root cause:** `updateAllWidgets` последовательно дергает `BellCountdownWidgetProvider`, `NowNextWidgetProvider`,
`TodayScheduleWidgetProvider`, `BellsWidgetProvider` (каждый — перебор id + полная сборка `RemoteViews`).
Вызывается из: `worker/BellServiceWatchdogWorker.kt:30` (**каждые 15 минут**, без изменения данных),
`worker/ScheduleCheckWorker.kt:118, 213`, `widget/TimeChangedReceiver.kt:37`, `util/AppThemeStore.kt:32`,
`widget/WidgetSyncWorker.kt:23`, `WidgetUpdateHelper.kt:68` (смена группы). Watchdog-воркер для виджетов
не должен обновлять их вовсе — его задача — поднять сервис.
**Фикс:** из `BellServiceWatchdogWorker` убрать `updateAllWidgets` (оставить `BellTimerService.sync`);
в остальных местах обновлять только затронутые провайдеры (`updateMinuteWidgets` уже есть в хелпере).
**Как измерить:** число `AppWidgetProvider`-обновлений в `dumpsys appwidget`; Perfetto — slices
`buildViews` при холостом прогоне 15-минутного цикла.

### [P2] Кэш документов держит полные `ByteArray` в синглтоне; нет HTTP-дискового кэша / `data/network/MpkNetworkClient.kt:113, 71, 80-89`
**Симптом:** память: до 16 полных .doc одновременно; повторный синк после смерти процесса — снова полная
загрузка.
**Root cause:** `docCache: ConcurrentHashMap<String, DocCacheEntry>` хранит `bytes: ByteArray?` с TTL 60 с и
лимитом `DOC_CACHE_MAX_ENTRIES = 16` — до нескольких МБ удерживаемого heap в **companion/singleton** живущего
объекта. `OkHttpClient` собран без `.cache(Cache(...))` (`:80-89`), т.е. дискового кэша нет: после рестарта
процесса документ качается заново (спасает только `urlMemo` на 6 ч, и он in-memory).
**Фикс:** оставить в памяти 1–2 последних документа (TTL 60 с достаточно для одного синка), добавить
`OkHttp` `Cache` в `cacheDir` (например 10 МБ) с `Cache-Control`-совместимым чтением; `.doc` с сайта —
неизменяемый ресурс, `ETag`/`If-Modified-Since` дадут бесплатный повтор.
**Как измерить:** Memory Profiler (retained size после синка); количество сетевых запросов после «холодного»
старта — Network Inspector / `receivedBytes` в диагностике.

### [P2] Стартовая цепочка в `onCreate` до первого кадра / `MainActivity.kt:57-88`, `MpkApplication.kt:33-42`, `ui/viewmodel/AppViewModel.kt:107-116`
**Симптом:** холодный старт и первый кадр — дольше, чем нужно; на бюджетных устройствах заметный «белый экран».
**Root cause:** до `setContent` (`MainActivity.kt:90`) синхронно выполняется:
`AppThemeStore.load` (`:57-61`, prefs), `NotificationHelper.createNotificationChannel` + `BellCountdownNotifier.createChannel`
(`:63-66`), `BellTimerService.sync` (`:67-68`, потенциальный `startForegroundService`), `initUiScale`,
3–4 × `MpkWorkManagerHelper.setup*` (`:71-88`, каждый — `WorkManager.getInstance` + БД WorkManager),
`TeacherModeStore.isTeacher` (`:80`), `AppUpdateWorker.schedule/checkOnStartup` (`:83-84`), `WhatsNewStore.onLaunch`
(`:85`), `ContentPackWorker.schedule/checkOnStartup` (`:87-88`). Сверху `MpkApplication.onCreate`
→ `AppGraph.init` → `MpkDatabase.getInstance` (`MpkApplication.kt:23`) + 3 репозитория. И `AppViewModel` в
конструкторе читает prefs 5 раз (`AppViewModel.kt:110-114`).
Итого к первому кадру прикасаемся к **5 разным** prefs-файлам (`mpk_ui_prefs`, `mpk_widget_prefs`,
`mpk_update_prefs`, `content_pack_prefs`, `mpk_whats_new_prefs`) — первый `getSharedPreferences` для каждого
делает синхронный разбор XML с диска. Сами проверки дебаунсятся (`StartupCheckDebounceTest`,
`ContentStartupCheckTest`), но `WorkManager.getInstance` в 3–4 местах — это открытие своей БД/инициализация.
**Фикс:** разнести по фазам: (1) в `onCreate` — только тема (нужна до первого кадра) и `setContent`;
(2) всё остальное — в `LaunchedEffect(Unit)` или `lifecycleScope.launch { }` после первого кадра;
(3) `MpkWorkManagerHelper` — один метод `setupAll(context)` вместо 3–4 вызовов;
(4) `AppViewModel` — читать prefs один раз (`getPrefs` уже кэширует объект) и собирать `AppUiState` из
одного снимка.
**Как измерить:** Macrobenchmark `StartupTimingMetric` (cold/warm) + `Trace`-секции в `onCreate`;
Perfetto: длительность `Activity#onCreate` до `Choreographer#doFrame`; StrictMode в debug-сборке на
diskRead/diskWrite (покажет и prefs, и files).

### [P2] Нет профиля запуска приложения (baseline/startup profile) / `app/` (файла нет), `app/build.gradle.kts`
**Симптом:** стабильно более медленный холодный старт и первые кадры расписания на всех устройствах, кроме
тех, где ART успел профилировать приложение.
**Root cause:** в `app/src/main` нет `baseline-prof.txt`, плагин `androidx.baselineprofile` не подключён
(в `app/build.gradle.kts` нет ни `baselineProfile`, ни `profileinstaller`). В `app/build/intermediates/binary_art_profile/*`
лежат только **слитые профили библиотек** (Compose/AndroidX), т.е. профиля именно под наши экраны
(`MainScreen`, `ScheduleScreen`, `AppGraph`) нет.
**Фикс:** подключить `androidx.baselineprofile` + `profileinstaller` и сгенерировать профиль по джорнеям
«холодный старт → расписание → карта» (у проекта уже есть Roborazzi/тестовая инфраструктура, джорнеи
придётся писать на `UiAutomator`, т.к. baseline-profile генератор требует `androidTest`).
**Как измерить:** Macrobenchmark cold start (median/P90) до/после; `adb shell cmd package compile -m speed-profile -f com.politex`
для контроля эффекта профиля.

---

## P3

### [P3] Бесконечная анимация в нижней навигации при доступном обновлении / `ui/screens/MainScreen.kt:248` → `ui/screens/ScheduleScreen.kt:2036-2061`
**Симптом:** при наличии обновления (`updatePending != null`) на кнопке «Другое» постоянно крутится
`rememberInfiniteTransition` с `tween(1000, Reverse)` → кадры генерируются непрерывно, пока экран виден:
повышенный расход батареи и нагрев в простое, невозможность «уснуть» GPU при статичном UI.
**Root cause:** `pulseHalo(active)` создаёт бесконечный transition в `drawBehind`; пока флаг активен,
Compose обязан перерисовывать каждый кадр. Флаг не гаснет сам (сбрасывается только при установке/скрытии).
**Фикс:** ограничить число пульсаций (например 3 раза через `Animatable` + `repeatable(iterations = 3)`),
либо переключиться на «статичный» бейдж после первого проигрывания.
**Как измерить:** `dumpsys gfxinfo com.politex framestats` / Perfetto на 10 с простоя с `updatePending`:
число кадров должно упасть с ~600 до ~0.

### [P3] Аллокации в `bottomBar` на каждую рекомпозицию / `ui/screens/MainScreen.kt:231-239`
**Симптом:** микро-джанк при переключении вкладок; мусор для GC.
**Root cause:** `tabsForMode(uiState.isTeacherMode).map { Triple(tab, icon, icon) }` создаёт новый список и
4 `Triple` при каждой рекомпозиции `MainScreen` (а `uiState` меняется на каждом синке — `isSyncing`).
**Фикс:** вынести в `remember(uiState.isTeacherMode)`, иконки — в статическую таблицу.
**Как измерить:** Layout Inspector (recomposition count `MainScreen`), allocation profiler.

### [P3] `SimpleDateFormat` и `String.format` в горячих путях / `data/network/MpkNetworkClient.kt:56-63, 183-190`, `widget/BellCountdownWidgetProvider.kt` (`formatSeconds`), `ui/screens/ScheduleScreen.kt:256`
**Симптом:** лишние аллокации при синке и в секундном тике виджета (24 форматтера на вызов
`generateCandidateDirectUrls`/`buildDirectScheduleUrl`).
**Root cause:** `SimpleDateFormat(...)` создаётся на каждый вызов (не `ThreadLocal`, не `DateFormats`);
`String.format(Locale.US, "%d:%02d")` — в секундном тике виджета (`formatSeconds`).
**Фикс:** один `ThreadLocal<SimpleDateFormat>`/`java.time`-форматтеры; в `formatSeconds` — ручная сборка
строки (`"$m:${sec.toString().padStart(2,'0')}"`).
**Как измерить:** allocation profiler на `syncSchedule` и на секундном тике.

### [P3] Кэш преподавателей инвалидируется на каждый `ON_RESUME` / `ui/util/KnownTeachers.kt:35-56`
**Симптом:** каждый возврат в приложение заново читается `teachers.csv` (assets, ~50 КБ) и пересобирается
`HashSet` фамилий; на экране расписания — повторный проход `produceState` (на IO, так что это не фриз,
но лишний IO/CPU на каждый resume).
**Root cause:** `rememberTeachers()` в `DisposableEffect` на `ON_RESUME` (кроме первого) делает
`TeachersCache.invalidate(); resumeTick++` — безусловно, хотя данные меняются только контент-паком.
**Фикс:** инвалидировать по `ContentPackStore`-ревизии (механизм уже есть —
`OwnerOverridesStore.revision` читается там же строкой ниже), а не по каждому resume.
**Как измерить:** Perfetto — slices `TeachersCache.load` на resume; счётчик чтений ассета.

---

## Что проверено и **не** является проблемой (чтобы не тратить гейты на «оптимизацию»)

- **Рекомпозиция списков расписания.** `ScheduleScreen` мемоизирует всё тяжёлое:
  `lessons` (`:276-279`), `slots` (`:337-340`), `weeklyLessonsByDow` (`:299-308`),
  `homeworkByLesson` (`:310-333`), `daySlots` (`:187-194`), `schoolDayEnded` (`:400-413`).
  `mergedLessonSlots` — один `associateBy` + линейный проход (`:1961-1979`), не O(n²).
- **Тики времени.** `rememberNowMinutes`/`rememberToday` (`ui/util/TodayProvider.kt:18-56`) тикают 1 раз
  в минуту (выровнено по границе минуты), а не каждую секунду. Всё, что зависит от `nowMinutes`,
  пересчитывается 1 раз/мин — это норма.
- **Тяжёлая арифметика на главном потоке.** Парсинг расписания идёт в `withContext(Dispatchers.IO)`
  (`MpkNetworkClient.kt:305+`, `:472+`), рендер страниц PDF — в `Dispatchers.Default` под `Mutex`
  (`PdfViewer.kt:886-901`), загрузка содержимого .doc — в `Dispatchers.IO` (`DocTextViewer.kt:113-116`),
  импорт материалов — `Dispatchers.IO` (`util/MaterialImporter.kt`).
- **Regex в парсере.** Все «горячие» шаблоны — предкомпилированные `private val` (`MpkScheduleParser.kt:66-116`);
  inline `Regex(...)` есть только в `parseDotDate`-подобных путях (`:119`, `:126`, `:177`) — не в цикле по
  документу.
- **Утечки WebView/Context.** `MaterialWebViewer.kt` использует `AndroidView` с `onRelease`, где вызываются
  `removeView` + `destroy()` + закрытие порта; `remember(item.id)` вокруг `WebView`-ссылок. Утечек не видно.
- **Bitmap без `inSampleSize`.** `BitmapFactory` встречается только в `content/OwnerPhotoResizer.kt`, и там
  `inSampleSize` **есть**. Виджеты не используют `setImageViewBitmap` вовсе (только текст) — RemoteViews дешёвые.
- **`collectAsState` в `MainScreen` на `uiState`** — читается один раз в корне, но `uiState` — единый
  data-класс на всё приложение (`AppUiState`, `AppViewModel.kt:87-95`), поэтому любое поле (например
  `isSyncing`) рекомпозирует корень `MainScreen`/`Scaffold`. Это P3-кандидат на дробление состояния,
  но измеренного эффекта у меня нет — **гипотеза, требует замера** (Layout Inspector: recomposition count
  `MainScreen` при `syncSchedule`).

---

## Гипотезы для Macrobenchmark / Perfetto

Порядок — по ожидаемому соотношению «эффект / стоимость настройки». Тесты помечены `[нужен замер]`.

1. **Cold start (Baseline)**: `StartupTimingMetric`, 10 итераций, ABBA-сравнение до/после правок P2 «стартовая
   цепочка» и «baseline profile». Метрики: `timeToInitialDisplayMs`, `timeToFullDisplayMs`
   (нужен `reportFullyDrawn()` после первого кадра расписания).
2. **Открытие PDF (P1)**: `FrameTimingMetric` + `StartupTimingMetric` на джорнее «Материалы → PDF 10 МБ».
   Ожидание: main-thread slice < 16 мс после переноса `PdfRenderer` в IO `[нужен замер]`.
3. **Вход на карту (P2)**: `FrameTimingMetric` на навигации «Другое → Карта»; проверить отсутствие
   main-thread `CollegeMapData.load` в трейсе Perfetto.
4. **Длинный .docx (P2)**: `FrameTimingMetric` + `Trace` на «открыть документ 50 КБ, проскроллить до конца»;
   целевые метрики: `frameDurationCpuMs` P90 ≤ 16, `frameOverrunMs` ≈ 0.
5. **Фоновая активность (P1 lifecycle)**: Perfetto 60 с при `home` + запущенном `ScheduleCheckWorker`:
   до правки — кадры `Choreographer#doFrame` и Room-запросы в фоне; после — тишина. Метрика:
   суммарное `CPU time` процесса за окно.
6. **Батарея виджета секунд (P1)**: не Macrobenchmark, а `dumpsys batterystats` + `dumpsys alarm` за 30 минут
   с включённым секундным виджетом; считать `Alarm`-срабатывания и `wakeups`. Сравнить с вариантом на
   `setChronometer` `[нужен замер]`.
7. **Синк расписания (P1 декод)**: `Trace`-секции вокруг `downloadFileBytes` / `documentMatchesDate` /
   `parseFile` + счётчик `SyncDiagnosticInfo.receivedBytes`; цель — один декод на документ.
8. **Jank в расписании при скролле (контроль)**: `FrameTimingMetric` на `ScrollSchedule` — ожидаемо уже
   в порядке; нужен как baseline, чтобы правки P2 не сделали хуже.
9. **Компиляция/размер**: `assembleStandardRelease` + `apkanalyzer apk file-size` — ассеты
   (`assets`: 106 jpg ≈ 2.05 МБ, pdf.js-часть: 2 × .wasm 0.34 МБ, 3 × .mjs 1.87 МБ, 168 × .bcmap 1.11 МБ)
   уже под `isCrunchPngs/isShrinkResources`; **не** является проблемой производительности, но влияет на
   время установки/первого запуска `[гипотеза, требует замера на устройстве]`.

---

## Топ-5 по соотношению «эффект / риск»

| # | Что | Эффект | Риск | Почему в топе |
|---|---|---|---|---|
| 1 | **`collectAsState` → `collectAsStateWithLifecycle`** (30+ мест, `MainScreen.kt:168` и др.) | Фоновая нагрузка/батарея, меньше рекомпозиций | **Очень низкий**: API-совместимая замена 1:1, зависимость уже есть | Механическая правка, покрывается существующими тестами UI |
| 2 | **`PdfRenderer` в `withContext(IO)`** (`PdfViewer.kt:205-213`) | −150…500 мс фриза на открытии PDF | **Низкий**: одна обёртка, `DisposableEffect` уже закрывает ресурсы | Самый заметный пользователю фриз из найденных |
| 3 | **Секунды виджета: окно «у звонка» + `setChronometer`** (`BellTimerService.kt:100-130`, `BellCountdownWidgetProvider.kt`) | Снятие 1 Hz-нагрузки и alarm-цепочки → батарея | **Средний**: меняется поведение UI виджета, нужны `WidgetSecondsTickTest` + ручная проверка | Самый большой эффект на батарею, но требует аккуратной проверки |
| 4 | **`verticalScroll` → `LazyColumn` в `DocTextViewer`** (`:337-356`) | Снятие фриза входа и джанка прокрутки больших .doc | **Средний**: ломается визуал «бумажных листов», нужен Roborazzi-record | Сильный эффект, но заметная работа по вёрстке |
| 5 | **`key` в 15 `items(...)` + убрать `updateAllWidgets` из watchdog-воркера** | Корректность состояния списков, минус лишние обновления виджетов каждые 15 мин | **Низкий**: локальные правки | Дешёво, безопасно, закрывает классы ошибок на будущее |

Отдельно, **вне топ-5 из-за стоимости проверки**: двойной декод .doc (P1) — потенциально самый большой выигрыш
по времени синка, но требует переделки контракта `documentMatchesDate`/`parseFileRawTexts`; делать после
появления метрики «время синка» в тестах (`ScheduleFetchSpeedupTest` — хорошая точка входа).

---

### Приложение: как воспроизводился поиск

- Список файлов и размеров: `Get-ChildItem` по `app/src/main/java/com/politex` (топ-40 по строкам).
- Паттерны главного потока/IO: `readText()`, `readBytes()`, `openInputStream`, `contentResolver.query`,
  `runBlocking`, `Dispatchers.Main`, `withContext(Dispatchers.IO)`, `BitmapFactory`, `inSampleSize`,
  `setImageViewBitmap`, `RemoteViews`, `AlarmManager`.
- Compose-паттерны: `collectAsState`/`collectAsStateWithLifecycle` (35 / 0), `remember(`, `derivedStateOf`,
  `verticalScroll`, `items(`/`itemsIndexed(`/`key =`.
- Разбор кода: `MainActivity.kt`, `MpkApplication.kt`, `AppViewModel.kt`, `MainScreen.kt`,
  `ScheduleScreen.kt` (фрагменты 150–440, 1820–1980), `MpkNetworkClient.kt` (целиком),
  `MpkScheduleParser.kt` (349–500, 544–600, 860–980), `PdfViewer.kt` (190–370, 850–1040),
  `DocTextViewer.kt` (95–240, 330–365, 560–600), `MaterialWebViewer.kt` (55–255), `ImageViewer.kt`,
  `BellTimerService.kt`, `WidgetUpdateHelper.kt`, `BellCountdownWidgetProvider.kt` (целиком),
  `MpkWorkManagerHelper.kt`, `CollegeMapData.kt`, `CollegeScreen.kt` (930–1040), `StudentsScreens.kt` (668–707),
  `uri/util/KnownTeachers.kt`, `util/AppThemeStore.kt`, `content/ContentPackWorker.kt`,
  `update/AppUpdateWorker.kt`, `app/build.gradle.kts`, `AndroidManifest.xml`.
- Не запускалось: gradle, unit/instrumentation-тесты, Macrobenchmark, Perfetto, `dumpsys` на устройстве.