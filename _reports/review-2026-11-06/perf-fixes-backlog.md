# Бэклог правок производительности — patch-ready

Источник: [`perf.md`](perf.md) (ревью домена «Производительность и Compose», 2026-11-06).
Этот файл — исполняемый бэклог: для каждого пункта есть точный фрагмент **было → стало**, эффект, риск,
способ измерения и привязка к тестам. Код здесь **не менялся**, кроме помеченного «СДЕЛАНО».

Условные обозначения:
- **Приоритет**: P1/P2 — как в `perf.md`.
- **Write-scope**: кто может править файлы. `rev-perf` — только `PdfViewer.kt`; `agent-schedule` —
  `MpkNetworkClient.kt`, `MpkScheduleParser.kt`, `ScheduleRepository.kt`, `ScheduleScreen.kt`;
  `agent-palette` — `res/layout/widget_*.xml`, `BellCountdownWidgetProvider.kt` (палитра виджетов),
  `BellTimerService.kt`; «свободно» — заявку может взять любой агент.
- Все пути — от корня репозитория `MPK-app-2.1/`, ссылки вида `файл:строка` актуальны на момент ревью.

> **Внимание (состояние дерева).** На момент написания бэклога `:app:compileStandardDebugKotlin` **красный
> не из-за этих пунктов**: ошибки в `data/repository/ScheduleRepository.kt:260,654,658,663`
> (`FetchStamps` в public API, `noteRejectedUrl` не резолвится) и `util/MaterialFormats.kt:65`
> (битый KDoc, лавина `Expecting member declaration`) — это незакоммиченные правки параллельных агентов.
> `PdfViewer.kt` в списке ошибок отсутствует. Перед гейтом дерево должно позеленеть.

---

## B1. Секундный отсчёт виджета: окно «у звонка» + `RemoteViews.setChronometer` — **P1, документ**

**Где:** `util/BellTimerService.kt:100-130` (цикл), `widget/WidgetUpdateHelper.kt:33-37` (`needSecondTick`),
`widget/BellCountdownWidgetProvider.kt:73-77` (`updateSecondsWidgets`), `object WidgetAlarm` в том же файле
(`shouldScheduleSecondTick`, `scheduleSecondTick`, `nextSecondTrigger`), `class WidgetAlarmReceiver`
(`ACTION_SECOND_TICK` → `handleSecondTick`).
**Write-scope:** `BellTimerService.kt`, `BellCountdownWidgetProvider.kt` — **agent-palette** (палитра виджетов);
`res/layout/widget_bell_countdown.xml` — тоже он. Здесь только документ.
**Эффект:** снимается 1 Hz-нагрузка (сборка `RemoteViews` + IPC в лончер + чтения prefs каждую секунду) и
самоперевзводящаяся alarm-цепочка `setExactAndAllowWhileIdle(RTC, now+1000)` в окне 7:00–21:00;
ожидаемо — десятки тысяч пробуждений/сутки → сотни (гипотеза, требует замера `dumpsys alarm`/`batterystats`).
**Риск:** средний — меняется виджет-UI и удаляется код, покрытый `WidgetSecondsTickTest` (454 строки).

### B1a. Минимальный шаг (без правки layout): сузить окно секунд

```kotlin
// БЫЛО — widget/WidgetUpdateHelper.kt:33-37
fun needSecondTick(
    secondsWidgetEnabled: Boolean,
    notificationStyle: com.politex.util.BellCountdownNotifier.Style,
): Boolean = secondsWidgetEnabled || notificationStyle == com.politex.util.BellCountdownNotifier.Style.WITH_SECONDS
```
```kotlin
// СТАЛО — секунды только в окне ±SECONDS_WINDOW_MIN вокруг границы звонка
private const val SECONDS_WINDOW_MIN = 3

fun needSecondTick(
    secondsWidgetEnabled: Boolean,
    notificationStyle: com.politex.util.BellCountdownNotifier.Style,
    minutesOfDay: Int = WidgetData.minuteOfDay(WidgetData.nowCalendar()),
): Boolean {
    val wants = secondsWidgetEnabled ||
        notificationStyle == com.politex.util.BellCountdownNotifier.Style.WITH_SECONDS
    return wants && WidgetAlarm.inSchoolWindow(minutesOfDay) &&
        IsNearBellBoundary(minutesOfDay) // ±SECONDS_WINDOW_MIN от start/end любого звонка дня
}
```
`IsNearBellBoundary` строится из `CollegeBellSchedule.getBellsForDay(dow)` (уже используется в
`BellCountdownWidgetProvider.countdownForMinute`). В `BellTimerService.startTicking()`
(`BellTimerService.kt:105-107`) вызов получает третий аргумент:
`WidgetUpdateHelper.needSecondTick(secondsWidgets, style, WidgetData.minuteOfDay(WidgetData.nowCalendar()))`.

⚠ **Сломается** `test/.../BellSecondsTest.kt:55-70` — 8 ассертов на текущую двухаргументную семантику.
Правится в том же коммите: сигнатура с дефолтным третьим аргументом + отдельные кейсы «вне окна → false».
⚠ Продуктовое решение: вне окна звонка пользователь видит `MM:SS` без тиканья — согласовать.

### B1b. Полный шаг: убрать периодику вовсе (`Chronometer` в `RemoteViews`)

1. `res/layout/widget_bell_countdown.xml`: добавить рядом с `tv_countdown_value`
   (`@+id/tv_countdown_value`, `@+id/widget_countdown_root`, `@+id/tv_countdown_label` уже есть):
   ```xml
   <Chronometer
       android:id="@+id/chrono_countdown"
       android:layout_width="wrap_content"
       android:layout_height="wrap_content"
       android:countDown="true"
       android:visibility="gone" />
   ```
2. `BellCountdownWidgetProvider.buildViews(...)` — вместо `setTextViewText(R.id.tv_countdown_value, valueText(...))`:
   ```kotlin
   // БЫЛО: текст, который надо перерисовывать каждую секунду
   views.setTextViewText(R.id.tv_countdown_value, valueText(minutesLeft))
   // СТАЛО: обратный отсчёт считает сам лончер, IPC — один раз
   views.setChronometer(
       R.id.chrono_countdown,
       android.os.SystemClock.elapsedRealtime() + minutesLeft * 60_000L - secondNow * 1_000L,
       null,
       true,
   )
   views.setChronometerCountDown(R.id.chrono_countdown, true) // API 24+, minSdk = 24 ✔
   views.setViewVisibility(R.id.chrono_countdown, android.view.View.VISIBLE)
   views.setViewVisibility(R.id.tv_countdown_value, android.view.View.GONE)
   ```
3. После этого `updateSecondsWidgets()` (и `needSecondTick`) не нужны: `BellTimerService` переходит на
   60-секундный тик, `WidgetAlarm.scheduleNextSecond` / `ACTION_SECOND_TICK` / `handleSecondTick` **удаляются**
   вместе с alarm-цепочкой; `onDestroy`/`onDisabled`/`Boot`-ветки перестают звать `scheduleNextSecond`.

⚠ Удаляемое покрыто `test/.../WidgetSecondsTickTest.kt:112-206` (`shouldScheduleSecondTick`,
`nextSecondTrigger`, `scheduleSecondTick`) — тесты удаляются/переписываются на проверку `setChronometer`-пути
(или на «секундных алармов больше нет»).
**Измерить:** `adb shell dumpsys alarm | grep -c politex` и `dumpsys batterystats --charged com.politex | grep -A5 "Alarm Stats"`
за 30 мин с включённым секундным виджетом; до/после. UI-проверка — Roborazzi-шот виджета
(`PromoShotsTest` уже рендерит шоты).

---

## B2. `collectAsState` → `collectAsStateWithLifecycle` (35 мест) — **P1, свободно**

**Где:** все точки ниже; `collectAsStateWithLifecycle` в проекте **не используется ни разу**.
Зависимость уже есть (`implementation(libs.androidx.lifecycle.runtime.compose)` в `app/build.gradle.kts`),
новых зависимостей не нужно.
**Эффект:** в фоне (свёрнутое приложение) прекращаются подписки на Room-флоу и рекомпозиция при записи в БД
(синк в WorkManager пишет уроки → экран перерисовывался в фоне). Ожидаемо: минус кадры и Room-запросы в
фоновом трейсе; эффект на батарею — гипотеза, требует замера.
**Риск:** низкий (API-совместимая замена 1:1), кроме двух файлов, где текущий вызов **зафиксирован тестами
как строка исходника** — см. предупреждение ниже.

Полный список (файл:строка, актуально после правки PdfViewer в этой сессии):

| Файл | Строки | Кол-во | Волна |
|---|---|---|---|
| `ui/screens/GradesScreen.kt` | 182, 183, 414, 417, 847 | 5 | 1 |
| `ui/screens/StudentsScreens.kt` | 353, 362, 365, 370, 379 | 5 | 1 |
| `ui/screens/CollegeScreen.kt` | 988, 1239 | 2 | 1 |
| `ui/screens/ScheduleScreen.kt` | 178, 297 | 2 | 1 |
| `ui/components/CalendarArchiveDialog.kt` | 111, 112 | 2 | 1 |
| `ui/screens/materials/PdfViewer.kt` | 283, 329 | 2 | 1 (scope `rev-perf`) |
| `ui/screens/BellsScreen.kt` | 172 | 1 | 1 |
| `ui/screens/GroupScheduleScreen.kt` | 247 | 1 | 1 |
| `ui/screens/MaterialBaseScreen.kt` | 192 | 1 | 1 |
| `ui/screens/TasksScreen.kt` | 147 | 1 | 1 |
| `ui/screens/materials/ImageViewer.kt` | 131 | 1 | 1 |
| `ui/screens/materials/MaterialWebViewer.kt` | 85 | 1 | 1 |
| `ui/screens/MainScreen.kt` | 168, 170, 322, 323, 519 | 5 | **2** |
| `ui/screens/SettingsScreen.kt` | 881, 882 | 2 | **2** |
| `ui/components/UpdateInstallUi.kt` | 118, 119 | 2 | 2 |
| `ui/teacher/TeacherHomeScreen.kt` | 90 | 1 | 2 |
| `ui/teacher/TeacherNav.kt` | 97 | 1 | 2 |
| **Итого** | | **35** | |

Паттерн правки (однотипный, менять только имя функции и импорт):
```kotlin
// БЫЛО
import androidx.compose.runtime.collectAsState
val entries by dao.allEntries().collectAsState(initial = emptyList())
// СТАЛО
import androidx.lifecycle.compose.collectAsStateWithLifecycle
val entries by dao.allEntries().collectAsStateWithLifecycle(initialValue = emptyList())
```
Аргумент `initial` переименовывается в `initialValue` (позиционно можно не указывать имя).

⚠ **Волна 2 блокирована двумя тестами, которые проверяют текст исходника:**
`test/.../UpdateTabPulseTest.kt:99` и `test/.../ContentUpdateSilenceTest.kt:58` ассертят
`src.contains("UpdateInstallController.foundUpdate.collectAsState()")`. Они затрагивают
`MainScreen.kt:170` и `SettingsScreen.kt:881` — в том же коммите строку в тесте нужно обновить на
`collectAsStateWithLifecycle()`, иначе гейт покраснеет. Поэтому волна 2 — отдельным коммитом.
**Измерить:** Perfetto 30 c при `home` + активном `ScheduleCheckWorker`: до — кадры `Choreographer#doFrame`
и потоки Room; после — тишина. Плюс `dumpsys gfxinfo com.politex framestats` до/после.

---

## B3. Двойной декод .doc + `PREFETCH_BATCH = 6` — **P1, документ (файлы `agent-schedule`)**

**Где:** `data/network/MpkNetworkClient.kt:363` (вызов `documentMatchesDate`), `:250-254` (`prefetch`),
`:71,75` (константы), `data/network/MpkScheduleParser.kt:349-376` (`extractDocTexts`), `:544-560`
(`extractHeaderText`), `:567-574` (`documentMatchesDate`), `:240-247` (`parseFile`).
**Write-scope:** `MpkNetworkClient.kt`, `MpkScheduleParser.kt` — **agent-schedule**. Здесь только документ.
**Эффект:** сейчас на каждый документ-кандидат: `documentMatchesDate` (полный декод шапки: `extractUtf16LeRuns`
+ `String(bytes, CP1251)`) → затем `parseFile` (ещё один полный декод) → плюс `extractDocTexts` материализует
4 строковые копии документа. Ожидаемо: −1 полный проход декода на документ, −30…50 % трафика на первый синк
(префетч 6 полных .doc), ниже пик heap (гипотеза, требует замера `ScheduleParseBenchTest`).

### B3a. Один декод на документ (главный выигрыш, низкий риск)

```kotlin
// БЫЛО — MpkNetworkClient.kt:361-372 (внутри forEachDocumentUrl)
} else if (!MpkScheduleParser.documentMatchesDate(docBytes, dateDotStr)) {
    false
} else {
    totalBytesReceived += docBytes.size
    lastHttpCode = 200
    rememberResponsePreview(docBytes)
    val parsed = MpkScheduleParser.parseFile(docBytes, targetGroup, dateDotStr)
```
```kotlin
// СТАЛО — декодируем один раз и переиспользуем
} else {
    val texts = MpkScheduleParser.extractDocTexts(docBytes)
    if (!MpkScheduleParser.documentMatchesDate(texts, dateDotStr)) {
        false
    } else {
        totalBytesReceived += docBytes.size
        lastHttpCode = 200
        rememberResponsePreview(docBytes)
        val parsed = MpkScheduleParser.parseFileRawTexts(texts, targetGroup, dateDotStr)
```
В парсере — перегрузка на `DocTexts` (старую на `ByteArray` оставить и выразить через неё, чтобы не ломать
`X1DocumentDateTest`, `SyncOutcomeStatusTest`, `AllGroupsDiscoveryTest`):
```kotlin
// СТАЛО к MpkScheduleParser.kt:567-574
fun documentMatchesDate(texts: DocTexts, dateDot: String): Boolean {
    val headerDate = extractHeaderDate(headerTextOf(texts)) ?: return true
    /* сравнение дат — без изменений */
}

fun documentMatchesDate(bytes: ByteArray, dateDot: String): Boolean =
    documentMatchesDate(extractDocTexts(bytes), dateDot)
```
⚠ Учесть поведение `applyHeaderDate` (вызывается из `parseFile`, `:246`): `parseFileRaw` её не применяет —
если она нужна для датировки уроков, вызывать её явно над результатом `parseFileRawTexts`.

### B3b. Ленивые копии в `DocTexts` (средний риск: `data class` → `class`)

```kotlin
// БЫЛО — MpkScheduleParser.kt:364-375: все 4 представления строятся всегда
utf16Text = utf16Runs.joinToString("\n"),
cp1251Runs = cp1251Runs,
cp1251Text = cp1251Runs.joinToString("\n"),
utf8Text = String(bytes, Charsets.UTF_8),
cp1251RawText = String(bytes, CP1251),
```
```kotlin
// СТАЛО — строки по требованию
internal class DocTexts(
    val wasHtml: Boolean,
    val isZip: Boolean,
    val docxXml: String?,
    val isOle: Boolean,
    val utf16Runs: List<String>,
    private val bytes: ByteArray,
) {
    val utf16Text: String by lazy { utf16Runs.joinToString("\n") }
    val cp1251Runs: List<String> by lazy { extractCp1251Runs(bytes) }
    val cp1251Text: String by lazy { cp1251Runs.joinToString("\n") }
    val utf8Text: String by lazy { String(bytes, Charsets.UTF_8) }
    val cp1251RawText: String by lazy { String(bytes, CP1251) }
}
```
⚠ `AllGroupsDiscoveryTest.kt:87` и `ScheduleParseBenchTest.kt:61-75` используют `extractDocTexts` —
перегрузки `parseFileRawTexts`/`documentMatchesDate(texts, …)` должны сохранить их зелёными.
Комментарием зафиксировать: «`DocTexts` держит ссылку на `bytes` — не кэшировать дольше одного синка».

### B3c. Префетч: не качать 6 полных документов «вслепую»

```kotlin
// БЫЛО — MpkNetworkClient.kt:250-254
suspend fun prefetch(urls: List<String>) {
    val fresh = urls.filterNot { it in tried }
    if (fresh.isEmpty()) return
    coroutineScope { fresh.map { async { downloadFileBytes(it) } }.awaitAll() }
}
```
```kotlin
// СТАЛО — сначала дешёвая проверка даты по шапке (первые N КБ), качаем только совпавшие
suspend fun prefetch(urls: List<String>) {
    val fresh = urls.filterNot { it in tried }
    if (fresh.isEmpty()) return
    coroutineScope {
        fresh.chunked(PREFETCH_BATCH).forEach { batch ->
            batch.map { url -> async { downloadHeadBytes(url, HEAD_PROBE_BYTES) } }.awaitAll()
                .forEach { probe -> probe?.let { rememberUrlMemo(/* … */) } }
        }
    }
}
```
(минимальный вариант без нового кода: `PREFETCH_BATCH = 6 → 2`, `MpkNetworkClient.kt:75`).
**Измерить:** готовый харнесс — `test/.../ScheduleParseBenchTest.kt:61-75` уже бенчивает
`extractDocTexts`, `documentMatchesDate`, `parseFileRawTexts`; прогнать до/после и записать медианы.
Плюс `SyncDiagnosticInfo.receivedBytes` (`fetchScheduleForGroup` уже заполняет) на «холодном» синке.

---

## B4. `verticalScroll` над всем документом → `LazyColumn` — **P2, свободно**

**Где:** `ui/screens/materials/DocTextViewer.kt:337-356` (`Box` + `Modifier.verticalScroll(scrollState)` +
`Column { repeat(sheets) { … } }`), данные — `DocTextViewer.kt:576-592` (`loadContent`).
**Эффект:** вход в длинный .docx/.txt перестаёт композировать/измерять все сотни абзацев сразу;
прокрутка становится ленивой. Ожидаемо: снятие фриза входа (гипотеза, требует `JankStats`).
**Риск:** средний — ломается визуал «бумажных листов» (`sheetCount`, `pageHeightFromWidth`,
`DOC_SHEET_GAP_DP`), нужен Roborazzi-record + vision-проверка.
**Было → стало (эскиз):**
```kotlin
// БЫЛО
Box(modifier = Modifier.fillMaxSize().graphicsLayer { /* zoom */ }.verticalScroll(scrollState)) {
    Column(modifier = Modifier.fillMaxWidth()) { repeat(sheets) { i -> Surface(/* лист */) { … } } }
}
// СТАЛО
LazyColumn(state = listState, modifier = Modifier.fillMaxSize().graphicsLayer { /* zoom */ }) {
    items(count = sheets, key = { it }) { i ->
        Surface(/* лист */) { /* текст листа i — только для видимых */ }
    }
}
```
Требует предварительно нарезать абзацы на листы (`sheetCount` уже считает количество, но не разбиение) —
поэтому это не однострочная правка; в отчёте оценено как «средний риск».
**Измерить:** `JankStats`-сценарий «открыть .docx 50 КБ → проскроллить до конца», метрики
`frameDurationCpuMs` P90, число фреймов > 16 мс; RSS до/после.

---

## B5. `key` в 15 `items(...)` — **P2, свободно**

**Где (все без `key =`):** `ui/screens/CollegeScreen.kt:632, 744, 1039, 1406, 1618, 1916`;
`ui/screens/GradesScreen.kt:231`; `ui/screens/StudentsScreens.kt:221, 639, 693, 777, 828`;
`ui/screens/TasksScreen.kt:932, 1341, 1510`.
**Эффект:** при вставке/удалении элемента перестаёт «прыгать» позиция и сбрасываться состояние строк;
меньше рекомпозиций видимых элементов.
**Риск:** низкий.
**Было → стало (пример, `GradesScreen.kt:231`):**
```kotlin
items(subjects) { subject -> … }                       // БЫЛО
items(subjects, key = { it }) { subject -> … }         // СТАЛО
items(rows) { row -> … }                               // БЫЛО (CollegeScreen.kt:1039, TeacherRow)
items(rows, key = { it.key() }) { row -> … }           // СТАЛО — добавить стабильный key() в sealed TeacherRow
```
Для `StudentsScreens.kt:693` (`subjects`) — `key = { it }`; для `:221` (`filtered` — студенты) — `key = { it.id }`;
для `TasksScreen.kt:1341` (`presets`) — `key = { it.id }`/`it.name`.
**Измерить:** Layout Inspector → recomposition counts при добавлении/удалении элемента; визуально — «прыжок»

---

## B6. `updateAllWidgets` из watchdog-воркера — **P2, свободно**

**Где:** `worker/BellServiceWatchdogWorker.kt:30` (вызывается каждые 15 минут, `CHECK_INTERVAL_MINUTES = 15L`)
→ `widget/WidgetUpdateHelper.kt:80-86` (веер по 4 провайдерам).
**Эффект:** каждые 15 минут без изменения данных пересобираются `RemoteViews` всех виджетов (IPC в лончер,
чтение prefs). После правки — обновление только по реальному событию (минутный тик/синк).
**Риск:** низкий: воркер уже делает `ensureTodaySchedule` (`:28`) и `WidgetAlarm.scheduleNext` (`:26`).
**Было → стало:**
```kotlin
// БЫЛО — BellServiceWatchdogWorker.kt:28-31
runCatching { WidgetUpdateHelper.ensureTodaySchedule(ctx) }
WidgetUpdateHelper.updateAllWidgets(ctx)
return Result.success()
// СТАЛО
runCatching { WidgetUpdateHelper.ensureTodaySchedule(ctx) }
runCatching { WidgetUpdateHelper.updateMinuteWidgets(ctx) } // только минута, без полного веера
return Result.success()
```
**Измерить:** `dumpsys appwidget` (кол-во обновлений), Perfetto: отсутствие `buildViews` в 15-минутном цикле.

---

## B7. `CollegeMapData.load` в композиции — **P2, свободно**

**Где:** `ui/screens/CollegeMapScreen.kt:149` → `data/model/CollegeMapData.kt` (`fun load`: `readText()` + `JSONObject`-разбор).
**Эффект:** чтение ассета (~70 КБ) и полный разбор JSON уходят с главного потока; при повторном входе на карту
данные берутся из кэша.
**Риск:** низкий.
```kotlin
// БЫЛО — CollegeMapScreen.kt:149
val mapData = remember(context, contentRevision) { CollegeMapData.load(context) }
// СТАЛО — с IO + кэш на процесс
val mapData by produceState(initialValue = CollegeMapData(emptyMap()), key1 = contentRevision) {
    value = withContext(Dispatchers.IO) { CollegeMapDataCache.load(context, contentRevision) }
}
```
`CollegeMapDataCache` — `@Volatile`-кэш по `contentRevision` (по образцу `TeachersCache` в
`ui/util/KnownTeachers.kt:20-32`). Пока `emptyMap()` — карта рисует пустое состояние; если это заметно,
оставить `remember` + `LaunchedEffect` с явным «loading».
**Измерить:** Perfetto — отсутствие main-thread slice `CollegeMapData.load` на первом кадре экрана карты.

---

## B8. Стартовая цепочка `onCreate` — **P2, свободно**

**Где:** `MainActivity.kt:57-88` (тема, каналы, `BellTimerService.sync`, 3–4 × `MpkWorkManagerHelper.setup*`,
`AppUpdateWorker.schedule/checkOnStartup`, `WhatsNewStore.onLaunch`, `ContentPackWorker.schedule/checkOnStartup`),
`MpkApplication.kt:33-42` (`AppGraph.init` → `MpkDatabase.getInstance` + 3 репозитория),
`ui/viewmodel/AppViewModel.kt:107-116` (5 чтений prefs в конструкторе).
**Эффект:** меньше работы до первого кадра: снимается синхронный разбор 5 prefs-файлов и лишние инициализации
`WorkManager`. Ожидаемо −20…80 мс холодного старта (гипотеза, требует `StartupTimingMetric`).
**Риск:** средний — порядок инициализаций влияет на виджеты/уведомления; делать после B1/B6, чтобы не
конфликтовать по смыслу.
```kotlin
// БЫЛО — MainActivity.kt:63-88 (всё синхронно до setContent)
NotificationHelper.createNotificationChannel(this)
BellTimerService.sync(this)
… MpkWorkManagerHelper.setupPeriodicScheduleCheck(this) … AppUpdateWorker.checkOnStartup(this) …
// СТАЛО — после первого кадра
setContent { … }
lifecycleScope.launch {
    yield() // дать первому кадру пройти
    NotificationHelper.createNotificationChannel(this@MainActivity)
    BellTimerService.sync(this@MainActivity)
    MpkWorkManagerHelper.setupAll(this@MainActivity)  // один вход вместо 3-4
    AppUpdateWorker.checkOnStartup(this@MainActivity)
    ContentPackWorker.checkOnStartup(this@MainActivity)
}
```
Тема (`AppThemeStore.load`, `:57-61`) **остаётся** в `onCreate` — она нужна до `setContent`.
**Измерить:** Macrobenchmark `StartupTimingMetric` (cold/warm, 10 итераций, ABBA); Perfetto — длительность
`Activity#onCreate`; StrictMode (`diskRead`) в debug до правки — покажет все prefs-чтения.

### B8b. Профиль запуска приложения — **P2, свободно**

**Где:** `app/` — нет `baseline-prof.txt`; в `app/build.gradle.kts` нет плагина `androidx.baselineprofile`
и `profileinstaller`. В `app/build/intermediates/binary_art_profile/*` лежат только слитые профили библиотек.
**Эффект:** стабильно быстрее холодный старт и первые кадры на всех устройствах (не только после ART-профилирования).
**Риск:** средний (новый генератор + androidTest-джорней), но правки рантайм-кода не требует.
**Шаги:** (1) `id("androidx.baselineprofile")` + `implementation(libs.androidx.profileinstaller)`;
(2) `baselineProfile { }`-модуль/джорней «холодный старт → расписание → карта → звонки»;
(3) `./gradlew :app:generateStandardReleaseBaselineProfile`; (4) проверить `baseline-prof.txt` в APK.
**Измерить:** Macrobenchmark cold start до/после; контроль — `adb shell cmd package compile -m speed-profile -f com.politex`.

---

## B9. Кэш документов: память + отсутствие дискового кэша — **P2, документ (`agent-schedule`)**

**Где:** `data/network/MpkNetworkClient.kt:113` (`docCache`), `:71` (`DOC_CACHE_MAX_ENTRIES = 16`),
`:80-89` (`OkHttpClient` без `.cache(...)`).
**Эффект:** минус удерживаемый heap (сейчас до 16 полных `.doc` в `ByteArray` с TTL 60 с) и минус повторная
загрузка того же документа после смерти процесса.
**Риск:** низкий для дискового кэша; для сужения `docCache` — проверить, что ретраи внутри одного синка
(`maxRetries = 2`) не начнут качать заново.
```kotlin
// БЫЛО — MpkNetworkClient.kt:80-89
OkHttpClient.Builder().connectTimeout(8000, …).readTimeout(8000, …)…build()
// СТАЛО
OkHttpClient.Builder()
    .cache(Cache(java.io.File(context.cacheDir, "http"), 10L * 1024 * 1024))
    .connectTimeout(8000, …)…
```
⚠ `client` — статический `companion object`, а `cacheDir` требует `Context`: либо инициализировать клиент из
`AppGraph.init(context)` (там уже есть `applicationContext`), либо оставить `lazy` и передать `Context`
в `downloadFileBytes`. Второй вариант — минимальная правка; первый — чище.
Плюс: `DOC_CACHE_MAX_ENTRIES = 16 → 2` (одного синка достаточно).
**Измерить:** Memory Profiler (retained size после синка); Network Inspector / `SyncDiagnosticInfo.receivedBytes`
на повторном «холодном» синке.

---

## B10. СДЕЛАНО в этой сессии — `ui/screens/materials/PdfViewer.kt` (scope `rev-perf`)

1. **`PdfRenderer` открывался на главном потоке** (`produceState` без диспетчера) → вынесено в
   `withContext(Dispatchers.IO + NonCancellable)`; добавлены `openPdfHandle`/`closePdfHandle`,
   дескриптор закрывается на всех путях: ошибка `PdfRenderer` (ветка «битый PDF» раньше **текла fd**),
   отмена корутины при уходе с экрана, `onDispose`.
2. **JSON разметки парсился на главном потоке** (`Stroke.parse` в `LaunchedEffect(annotations)`) →
   `withContext(Dispatchers.Default)`, запись в `pageStrokes` возвращается на главный поток.
Эффект: снятие фриза 150…500 мс на открытии PDF и джанка при открытии размеченного файла
**(гипотеза, требует замера)**. Риск: низкий — поведение не менялось.
**Измерить:** Perfetto/JankStats на сценарии «Материалы → PDF 10 МБ» и «PDF с разметкой на 20 страниц».

---

## B11. Мелкое (P3) — одним коммитом «гигиена», свободно

| Где | Было → стало | Эффект |
|---|---|---|
| `ui/screens/ScheduleScreen.kt:2036-2061` (`pulseHalo`) по вызову `ui/screens/MainScreen.kt:248` | бесконечный `rememberInfiniteTransition(tween(1000, Reverse))` → `repeatable(iterations = 3)` или один проигрыш | нет постоянной генерации кадров, пока висит бейдж обновления |
| `ui/screens/MainScreen.kt:231-239` | `tabsForMode(uiState.isTeacherMode).map { Triple(...) }` на каждую рекомпозицию → `remember(isTeacherMode)` | минус аллокации на каждом `isSyncing` |
| `ui/util/KnownTeachers.kt:35-56` | инвалидация кэша на каждом `ON_RESUME` → только по `OwnerOverridesStore.revision`/контент-паку | минус чтение `teachers.csv` на каждый возврат в приложение |
| `data/network/MpkNetworkClient.kt:56-63, 183-190`, `widget/BellCountdownWidgetProvider.kt` (`formatSeconds`), `ui/screens/ScheduleScreen.kt:256` | `SimpleDateFormat(...)`/`String.format` на вызов → `ThreadLocal`-форматтеры / ручная сборка строки | минус аллокации в синке и секундном тике |

---

## Порядок применения (волны)

1. **Волна A (сразу, минимальный риск):** B10 (сделано), B2 → волна 1 (16 файлов, без `MainScreen`/`SettingsScreen`),
   B5 (`key`), B6 (watchdog), B7 (карта).
2. **Волна B:** B2 → волна 2 (`MainScreen`, `SettingsScreen`, `UpdateInstallUi`, teacher-экраны) **вместе** с
   правкой `UpdateTabPulseTest.kt:99` и `ContentUpdateSilenceTest.kt:58`.
3. **Волна C (нужны гейты и решения):** B1a → затем B1b (виджеты; `agent-palette`), B3a → B3b → B3c
   (`agent-schedule`), B4 (`DocTextViewer` + Roborazzi-record), B8/B8b (старт + профиль), B9.

После каждой волны: `:app:compileStandardDebugKotlin` → `:app:testStandardDebugUnitTest`; для UI-правок —
Roborazzi record + vision-проверка (правило проекта).

## Что обязательно проверить в гейте

- `BellSecondsTest.kt:55-70` — правка `needSecondTick` (B1a).
- `WidgetSecondsTickTest.kt:112-206` — удаление секундной alarm-цепочки (B1b).
- `UpdateTabPulseTest.kt:99`, `ContentUpdateSilenceTest.kt:58` — ассертят текст исходника
  `UpdateInstallController.foundUpdate.collectAsState()`; ломаются от B2.
- `ScheduleParseBenchTest.kt:61-75`, `X1DocumentDateTest`, `SyncOutcomeStatusTest`, `AllGroupsDiscoveryTest` —
  контракт `documentMatchesDate` / `extractDocTexts` / `parseFileRawTexts` (B3).
- `ScheduleFetchSpeedupTest`, `SyncCacheFirstTest` — регресс по времени/кэшу синка (B3, B9).