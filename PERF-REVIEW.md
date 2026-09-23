# PERF-REVIEW — аудит производительности / кэширования / состояния

Репо: `D:\MPK app\MPK-app-2.1` (Kotlin + Compose, пакет `com.politex`, flavors `standard`/`admin`).
Метод: адресное чтение кода (Grep + Read offset/limit). Проект не собирался. Код не менялся.

Легенда: **file:line** — точное место. «Мин. фикс» — минимальный диф, без перепроектирования.

---

## 1. Диагноз по жалобам

### Жалоба 1. Холодный старт: расписание появляется ~1 секунду

Цепочка загрузки (полная, с блокировками):

```
MainActivity.onCreate            MainActivity.kt:33
  └ enableEdgeToEdge, applyAppTheme (SharedPreferences read, sync)   MainActivity.kt:35-37
  └ NotificationHelper / BellChannel / BellTimerService.start        MainActivity.kt:39-44
  └ MpkWorkManagerHelper.setupPeriodicScheduleCheck (WorkManager)    MainActivity.kt:45
  └ setContent { viewModel() }                                       MainActivity.kt:47-48
        └ AppViewModel.init → MpkDatabase.getInstance (sync, lazy)   AppViewModel.kt:51
        └ AppViewModel.init → syncSchedule(isAutoSync = true)        AppViewModel.kt:71-73
              └ ScheduleRepository.syncScheduleFromWeb (IO)          AppViewModel.kt:161
                    └ Room counts для today/tomorrow                 ScheduleRepository.kt:79-80
                    └ OkHttp: до 3 кандидатов URL × 8s timeout       MpkNetworkClient.kt:120-168
        └ MainScreen(viewModel)                                      MainScreen.kt:55
              └ MainTopBar / NavigationBar (эмодзи-free, ok)         MainScreen.kt:89,98
              └ ScheduleScreen(...)                                  MainScreen.kt:169
                    └ 9 (девять!) независимых Room-Flow                 ScheduleScreen.kt:142-258
```

**Корневые причины (по порядку вклада):**

**1a. `isInitialLoad` сериализует UI за первым Room-эмитом, а не за кэшем.**
`ScheduleScreen.kt:233-237`:

```kotlin
val lessonsFromDbState: List<LessonEntity>? by scheduleRepository
    .getLessonsForDateOrDay(groupInfo.canonicalName, activeDateString, activeDayOfWeek)
    .collectAsState(initial = null)          // ← initial = null = «данных ещё нет»
val lessonsFromDb = lessonsFromDbState ?: emptyList()
val isInitialLoad = lessonsFromDbState == null
```

`initial = null` заставляет первый кадр показать `CircularProgressIndicator` (`ScheduleScreen.kt:510-524`), даже если в Room уже лежат строки. Room-Flow отдаёт первый эмит только после постановки query на executor + inflate базы (`MpkDatabase.kt:33-39` — `databaseBuilder(...).build()` без `setQueryExecutor`, без `createFromAsset`), т.е. сотни мс на холодном старте. **Это и есть «~1 секунда».**

**Мин. фикс:** не менять сигнатуру Flow, а сделать начальное значение из дешёвого синхронного кэша группы. Минимально — `initial = null` → `initial = emptyList()` и `isInitialLoad` считать по отдельному флагу «первый эмит пришёл»:

```kotlin
var firstEmit by remember(groupInfo.canonicalName) { mutableStateOf(false) }
LaunchedEffect(lessonsFromDbState) { if (lessonsFromDbState != null) firstEmit = true }
val isInitialLoad = !firstEmit
```
Это не убирает ожидание первого эмита, но убирает спиннер в пользу заглушки на «нет данных» (жалоба 2). **Настоящий фикс жалобы 1 — п. 1b.**

**1b. `syncSchedule(isAutoSync = true)` в `AppViewModel.init` (AppViewModel.kt:71-73) на каждом создании VM.**
`syncScheduleFromWeb(force = false)` внутри делает 2 синхронных `COUNT(*)` (`ScheduleRepository.kt:79-80`) и, если `needTomorrow` (а `tomorrowCached == 0` почти всегда для вечерних/выходных запусков), **сразу уходит в сеть** (`ScheduleRepository.kt:91-95`) с таймаутом 8s × 3 кандидата × 2 попытки. Это фоновый `viewModelScope.launch`, UI он не блокирует напрямую, но: `_uiState.update { copy(isSyncing = true) }` → `MainScreen.kt:174` → рекомпозиция `ScheduleScreen`, а `MainActivity.onResume` дополнительно бьёт `WidgetUpdateHelper.updateAllWidgets` (`MainActivity.kt:97-100`), что читает БД через `runBlocking` (см. 4a).

**Мин. фикс:** в `init` синхронизировать не безусловно, а только если в базе нет ни сегодня, ни завтра:
```kotlin
init {
    if (!uiState.value.showGroupSelection) {
        viewModelScope.launch {
            if (!scheduleRepository.hasSchedule(uiState.value.currentGroupName)) {
                syncSchedule(isAutoSync = true)
            }
        }
    }
}
```
`hasSchedule` уже есть — `ScheduleRepository.kt:159-161`. Дополнительно: убрать `fetchScheduleForGroup(tomorrow)` из «холодного» пути (`ScheduleRepository.kt:91-95`) — tomorrow нужен только виджетам, а не первому экрану.

**1c. Мёртвая инициализация до первого кадра.**
`IntroSplash` / `IntroSplashOverlay` (`IntroSplash.kt:42`, `149`) — **не вызывается нигде** (Grep по `app/src/main` даёт единственные хиты: определения). `ic_splash.png` 13.8 КБ лежит мёртвым грузом. Не влияет на старт, но это 13.8 КБ в APK.

**1d. `BellTimerService.start` в `onCreate` (MainActivity.kt:42-44).**
`startForegroundService` → `onCreate` → `onStartCommand` → `startTicking` (`BellTimerService.kt:32-45`). Сервис поднимает coroutine-scope и в первом же тике дергает `startForeground` + `maybeCelebrate`, который делает `runBlocking` в Room (`ConfettiController.kt:201-206`). На холодном старте это ещё один конкурент за поток БД. **Не блокирует первый кадр** (сервис стартует асинхронно), но добавляет нагрузку на тот же период.

---

### Жалоба 2. «Сегодня»↔«Завтра»: до секунды висит заглушка «расписание ещё не опубликовано»

**Корневая причина 2a (главная): ключ Flow меняется на каждый переключатель, `collectAsState` стартует с `null`, старые данные выбрасываются.**

`ScheduleScreen.kt:233-235` — Flow зависит от `activeDateString` и `activeDayOfWeek`, а оба пересчитываются от `selectedSlot` (`ScheduleScreen.kt:188-205`). При тапе по ячейке «ЗАВТРА» (`ScheduleScreen.kt:466-469`: `selectedSlot = 1; selectedDateString = ""`):

1. `activeCal` → `rightCal` → `activeDateString` меняется на `dd.MM.yyyy` завтра → **новый ключ Flow**;
2. `collectAsState(initial = null)` в Compose **перезапускает** коллекцию с `initial` (это документированное поведение: при смене ключа `remember` в `collectAsState` пересоздаётся) → `lessonsFromDbState == null` → `isInitialLoad == true`;
3. UI рендерит спиннер (`ScheduleScreen.kt:510-524`) → потом, если за завтра строк в базе нет, `lessons.isEmpty()` → **заглушка «Расписание ещё не опубликовано»** (`ScheduleScreen.kt:525-563`, текст на `:556`).

**Точное место, где UI решает «данных нет»:** `ScheduleScreen.kt:525` — `} else if (lessons.isEmpty()) {`. Единственный источник этого решения — `lessons`, который целиком зависит от разового эмита выдающего Flow. **Stale-while-revalidate отсутствует полностью:** предыдущий непустой список нигде не сохраняется между сменами ключа.

**Вторичная причина 2b: данные «завтра» физически могут отсутствовать.**
`syncScheduleFromWeb` (`ScheduleRepository.kt:70-116`) при `needToday == false && needTomorrow == false` вообще не ходит в сеть (`:85-88`), а при `needTomorrow == true` кладёт fetched в БД **только внутри ветки `needToday`** (`:97-115`). Если `needToday == false`, а `needTomorrow == true`, ветка `else` (`:107-115`) пишет `fetched` — ок. Но `replaceSyncedLessons` (`:140-145`) сначала `DELETE` по датам, потом `INSERT` — между ними Room эмитит **пустой список** для этих дат (`Room` инвалидирует таблицу на каждую транзакцию; `@Transaction` тут не защищает Flow от промежуточной эмиссии, инвалидация приходит после COMMIT, но `deleteLessonsForDate` + `insertLessons` идут отдельными запросами вне транзакции — `ScheduleRepository.kt:141-144`). Пока идёт синк, UI может получить пустую эмиссию → та же заглушка.

**Мин. фиксы (в порядке дешевизны):**

**Фикс 1 (одна строка, главный):** не переключать ключ, а фильтровать в памяти. `getLessonsForDateOrDay` уже отдаёт и точные даты, и шаблон дня недели — вместо нового Flow на каждый слот держать один Flow по группе и фильтровать `lessons` через `remember`:
```kotlin
// вместо :233-243 — один Flow по группе, фильтр на клиенте
val allGroupLessons by scheduleRepository.getAllLessonsForGroup(groupInfo.canonicalName)
    .collectAsState(initial = emptyList())
val lessons = remember(allGroupLessons, activeDateString, activeDayOfWeek) {
    val exact = allGroupLessons.filter { it.dateString == activeDateString }
    if (exact.isNotEmpty()) exact
    else allGroupLessons.filter { it.dateString.isBlank() && it.dayOfWeek == activeDayOfWeek }
}
```
Это заодно убирает 5 лишних Flow (`:249-258`) и `saturdayLessons` (`:142-144`) — один запрос вместо девяти.

**Фикс 2 (защита от «мигания» при синке):** обернуть `replaceSyncedLessons` в `@Transaction` целиком, а не двумя запросами:
```kotlin
// ScheduleRepository.kt:140-145 — добавить в LessonDao @Transaction suspend fun
@Transaction
suspend fun replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>) {
    deleteLessonsForDate(groupName, dateString); insertLessons(lessons)
}
```

**Фикс 3 (если фикс 1 неприемлем):** сохранять последний непустой `lessons` в `remember` и показывать его, пока новый Flow не отдал первый эмит (классический SWR, ~6 строк).

---

### Жалоба 3. Возврат на «Расписание» с «Заданий»/«Звонков»: экран перезагружается с нуля, состояние теряется

**Корневая причина: `when (uiState.currentTab)` уничтожает поддерево, а всё состояние `ScheduleScreen` — `remember` без ключей сохранения.**

`MainScreen.kt:168-194`:
```kotlin
when (uiState.currentTab) {
    AppTab.SCHEDULE -> ScheduleScreen(...)   // :169
    AppTab.TASKS -> TasksScreen(...)         // :180
    ...
}
```
В Compose уход из ветки `when` = **полная потеря composition-поддерева**: все `remember` сбрасываются. В `ScheduleScreen` на голом `remember` живут:

| Состояние | Строка | Переживает смену вкладки? |
|---|---|---|
| `listState` (позиция скролла) | `MainScreen.kt:160-162` | **да** — вынесен над `when` через `rememberSaveable`; авторы это уже починили (комментарий 8.1) |
| `selectedSlot` | `ScheduleScreen.kt:184` | **нет** — `remember(groupInfo.canonicalName)` |
| `selectedDateString` | `ScheduleScreen.kt:185` | **нет** |
| `showArchiveDialog` | `ScheduleScreen.kt:186` | нет (приемлемо) |
| `didColdStartScroll` | `ScheduleScreen.kt:292` | **нет** → автопрокрутка к «сейчас» повторяется при каждом возврате |
| `realNow` / `realDayOfWeek` | `ScheduleScreen.kt:129-140` | **нет** — пересчитывается, но это ок |
| `saturdayLessons` + 5 Flow | `ScheduleScreen.kt:142-258` | **нет** — все 9 запросов к Room выполняются заново |

Отдельно: `isTeacherKnown` (`ScheduleScreen.kt:118-127`) на **каждом** возврате на вкладку заново читает `assets/teachers.csv` и строит `HashSet` из ~99+ имён — **на главном потоке**, внутри `remember(context)` (composable-вычисление, не IO-диспетчер).

**Мин. фиксы:**

**(a) Поднять `selectedSlot`/`selectedDateString` над `when`, как уже сделано для скролла** (`MainScreen.kt:157-165` — там даже комментарий-обоснование). Ровно тот же приём:
```kotlin
// MainScreen.kt, рядом со scheduleListState (:160)
var scheduleSlot by rememberSaveable { mutableIntStateOf(0) }
var scheduleDate by rememberSaveable { mutableStateOf("") }
// ...передать в ScheduleScreen(slot = scheduleSlot, onSlotChange = { scheduleSlot = it }, ...)
```
Это ~8 строк и закрывает «состояние теряется».

**(b) `remember` → `rememberSaveable` для `didColdStartScroll`** — иначе автопрокрутка срабатывает повторно (заметный «прыжок» при возврате). Одна строка, `ScheduleScreen.kt:292`.

**(c) Убрать повторный парсинг CSV:** `ScheduleScreen.kt:118-127`. Ленивый `by lazy` в companion/объекте `TeachersRepository` — один греп + одна строка. Ещё лучше — не тянуть `isTeacherKnown` в композицию вовсе, а отдавать из VM (там же, где `diagnosticInfo`). Сейчас это O(N_csv) на каждый вход на вкладку.

**(d) Опционально, «правильный» фикс жалобы 3:** не `when`, а держать все 4 вкладки в composition с `Modifier.alpha`/`.zIndex`, либо `SaveableStateHolder` + `rememberSaveableStateHolder()`. **Против:** перепроектирование, 4 экрана одновременно в памяти. `(a)` покрывает 95% жалобы за 8 строк — делать его, `(d)` только если заказчик жалуется на остаточное.

---

### Жалоба 4. Виджеты перезагружаются (пропадают и заново подгружаются)

**Корневая причина: полная пересборка `RemoteViews` + `removeAllViews` на каждом тике, до 4 раз в минуту, плюс bitmap-кадры конфетти.**

**4a. `runBlocking` в БД из виджетов.** `NowNextWidgetProvider.kt:148-161` (`WidgetData.todayLessons`) и `BellCountdownWidgetProvider.kt:185-194` (`lastGroupLessonForToday`), `ConfettiController.kt:201-206`. Это вызывается из `AppWidgetProvider.onUpdate` / `updateAll` — т.е. **на главном потоке** процесса-хоста виджета. Внутри — открытие Room и полный `SELECT * WHERE groupName AND dayOfWeek` с последующей сортировкой и `parseDotDate` на каждой строке (`NowNextWidgetProvider.kt:152-160`). Пока это выполняется, `updateAppWidget` не отрисовывает — визуально «виджет пропал».

**4b. `removeAllViews` + пересборка всех строк на каждый тик.** `TodayScheduleWidgetProvider.kt:56-72`: `views.removeAllViews(R.id.today_rows)` затем `views.addView(...)` на каждую из до 12 строк, каждая строка — новый `RemoteViews` (`:97`). Это O(N) IPC-объектов на каждое обновление. Частота обновлений:

- `WidgetAlarm.scheduleNext` (`BellCountdownWidgetProvider.kt:393-418`) — **каждую минуту** в окне 07:00–21:00 (`:399-400`: `System.currentTimeMillis() + 60_000L`), `setExactAndAllowWhileIdle`. Вне окна — один раз в сутки на 07:00.
- `WidgetAlarmReceiver.ACTION_TICK` (`:426-434`) → `WidgetUpdateHelper.updateAllWidgets` → **все 4 провайдера** (`WidgetUpdateHelper.kt:45-50`), каждый пересобирает все свои инстансы.
- `BellTimerService.startTicking` (`BellTimerService.kt:38-67`) — параллельно, каждую минуту, `BellCountdownWidgetProvider.updateAll` (`:65`).
- `MainActivity.onResume` (`MainActivity.kt:97-100`) → `updateAllWidgets` — на каждый резюм, включая возврат с любой вкладки.

Т.е. в худшем случае 2 разных минутных источника (`WidgetAlarm` + `BellTimerService`) + `onResume` бьют по всем виджетам. Два минутных таймера — избыточность: `BellTimerService` живёт только если `BellCountdownNotifier.isEnabled` (`MainActivity.kt:42`), `WidgetAlarm` — если `hasWork` (`:381-391`), и оба делают одно и то же.

**4c. Конфетти — 16 bitmap-кадров 420×300 ARGB_8888.** `WidgetConfetti.kt:12-13`, `:31-51`: `TOTAL_FRAMES = 16`, `FRAME_MS = 90` → ~1.5 с анимации. На каждом кадре:
- `ConfettiRenderer.drawFrame(420, 300, frame, TOTAL_FRAMES)` (`:38`) — **новая** `Bitmap.createBitmap(420,300,ARGB_8888)` (`ConfettiRenderer.kt:35`) = 420×300×4 = **504 КБ на кадр**, 16 кадров = ~8 МБ аллокаций за анимацию, + 42 частицы × Path/Paint на кадр (Paint/Path переиспользуются — `ConfettiRenderer.kt:42-43`, это уже оптимизировано);
- `WidgetUpdateHelper.updateAllWidgets(context)` (`:42`) — **все 4 виджета пересобираются 16 раз**, каждый со своим `runBlocking` в БД.

Итого конфетти = 16 полных пересборок всех виджетов + 8 МБ bitmap-мусора. `setImageViewBitmap` передаёт bitmap через Binder (лимит транзакции ~1 МБ на современных Android; 504 КБ проходит, но впритык, на низких API — риск `TransactionTooLargeException`).

**4d. Мелочь:** `WidgetAlarm.hasWork` (`:381-391`) на каждом `scheduleNext` делает `getAppWidgetIds` 4 раза → 4 binder-вызова.

**Мин. фиксы:**

**(a) Кэшировать строки виджета в памяти процесса.** `WidgetData.todayLessons` вызывается в `buildViews` каждого провайдера (`TodayScheduleWidgetProvider.kt:42`, `NowNextWidgetProvider.kt:47`) и не зависит от `appWidgetId`. Обернуть в простой кэш на минуту:
```kotlin
// NowNextWidgetProvider.kt:143
private var cacheMinute = -1
private var cacheLessons: List<LessonEntity> = emptyList()
fun todayLessons(context: Context): List<LessonEntity> {
    val m = minuteOfDay(context)
    if (m == cacheMinute) return cacheLessons
    ... // существующее тело
    cacheMinute = m; cacheLessons = result; return result
}
```
Одна переменная, закрывает 4× `runBlocking` за кадр.

**(b) Обновлять только видимые виджеты при тике.** `WidgetUpdateHelper.updateAllWidgets` (`:45-50`) → заменить на per-kind вызов только если `ids.isNotEmpty()` (уже так в `updateAll`) + не пересобирать `TodaySchedule`/`Bells` каждую минуту: их содержимое меняется раз в урок, а не раз в минуту. **Минимально:** в `WidgetAlarmReceiver.onReceive` (`:429-434`) дёргать только `BellCountdownWidgetProvider` + `NowNextWidgetProvider` (те, у кого минутный отсчёт), а `TodaySchedule`/`Bells` — раз в 10 минут или по факту смены урока.

**(c) Убрать двойной минутный таймер.** Либо `WidgetAlarm.scheduleNext` не планировать, если `BellTimerService` уже запущен (`hasWork` → проверить `BellCountdownNotifier.isEnabled` и не планировать будильник, полагаясь на сервис), либо наоборот. `BellCountdownWidgetProvider.kt:381-391` — одна ветка.

**(d) Конфетти: уменьшить кадр и убрать пересборку.** `WidgetConfetti.kt:12-13`, `:38`, `:42`:
- `drawFrame(420, 300, ...)` → `drawFrame(180, 130, ...)` — в 5.4 раза меньше пикселей (bitmap 504 КБ → 94 КБ), на плотности виджетов разница незаметна;
- `TOTAL_FRAMES` 16 → 8, `FRAME_MS` 90 → 140 — та же длительность, вдвое меньше кадров;
- убрать `WidgetUpdateHelper.updateAllWidgets` из `step()` — вместо этого инвалидировать **только один** виджет-хост конфетти (передавать `appWidgetId` в `play`), либо (лучше) не рисовать конфетти в виджете вообще: `ConfettiOverlay` в приложении (`MainActivity.kt:80-85`) уже это делает.
- `Bitmap.recycle()` предыдущего кадра (`:36-41`) — сейчас GC-pressure на 8 МБ.

**(e) `runBlocking` → `goAsync()`/coroutine в `onUpdate`.** Правильный фикс — `AppWidgetProvider` не поддерживает suspend, но можно `goAsync()` + `CoroutineScope` с `Dispatchers.IO` и `provider.onUpdate` как тонкой обёрткой. Дороже по дифу; `(a)` даёт 90% эффекта за 4 строки. **Рекомендую (a), не (e).**

---

## 2. Инвентаризация размера

### APK в `D:\MPK app\` (корень, вне репо)

| Файл | Размер |
|---|---|
| `moy-polytech-v2.0-nav-admin-release.apk` | 23.6 M |
| `moy-polytech-v2.0-nav-release.apk` | 23.6 M |
| `moy-polytech-v2.1-admin-release.apk` | 23.6 M |
| `moy-polytech-v2.1-release.apk` | 23.6 M |
| `moy-polytech-v2.2-admin-release.apk` | 23.7 M |
| `moy-polytech-v2.2-release.apk` | 23.7 M |
| `moy-polytech-v2.3-ux-admin-release.apk` | 23.7 M |
| `moy-polytech-v2.3-ux-release.apk` | 23.7 M |
| `moy-polytech-2.0-admin-release.apk` / `moy-polytech-2.0-release.apk` | 23.6 M ×2 |
| `moy-polytech-v2.6-debug.apk` | 22.8 M |
| `moy-polytech-v2.7-debug.apk` | 22.7 M |
| `moy-polytech-v2.8-debug.apk` | 22.7 M |
| `moy-polytech-v2.9-debug.apk` | 29.6 M |
| `moy-polytech-v2.9-admin-debug.apk` | 29.7 M |
| `mpk-schedule-v2.5.1-debug.apk` | 22.6 M |

**Вывод:** 16 APK, суммарно ~390 МБ на диске. **Ни один не в репо-корне `MPK-app-2.1`** — там только `moy-polytech-v2.6…v2.9-debug.apk` + `mpk-schedule-v2.5.1-debug.apk` (6 файлов, ~150 МБ). `admin`-вариант стабильно на **+0.1–0.2 МБ** тяжелее (students.csv 349 КБ → сжатие). Скачок v2.8→v2.9: **22.7 → 29.7 МБ (+7 МБ!)** — надо проверить, что добавилось (вероятно `firebase-ai`/AppCheck, см. ниже).

### Ресурсы `app/src/main/res` — итого 1.4 МБ

| Папка | Размер | Примечание |
|---|---|---|
| `res/drawable/` | **862 K** | доминируют 3 карты колледжа |
| `res/mipmap-xxxhdpi/` | 204 K | `ic_launcher_foreground.png` 104.2 K |
| `res/mipmap-xxhdpi/` | 116 K | foreground 58.2 K |
| `res/mipmap-xhdpi/` | 52 K | foreground 26.1 K |
| `res/mipmap-hdpi/` | 32 K | foreground 14.8 K |
| `res/mipmap-mdpi/` | 16 K | foreground 6.8 K |
| `res/layout/` | 32 K | 6 widget-лейаутов (1.2–4.2 K) |
| `res/mipmap-anydpi-v26/` | 2.0 K | `ic_launcher.xml`, `ic_launcher_round.xml` |
| `res/values/` + `values-v31/` + `xml/` | 23 K | |

Детально `res/drawable/`:

| Файл | Размер |
|---|---|
| `map_floor2.jpg` | 285.1 K |
| `map_floor34.jpg` | 271.0 K |
| `map_floor1.jpg` | 268.3 K |
| `ic_splash.png` | **13.8 K — МЁРТВЫЙ** (`IntroSplash` не вызывается) |
| `ic_mpk_emblem.xml` | 2.5 K |
| `widget_preview_countdown.png` | 1.5 K |
| `bg_widget_card.xml` / `ic_launcher_background.xml` | 336 B / 134 B |

**Вывод по drawable:** три JPEG-карты — **824 КБ, 96% всей папки drawable**. Они уже `.jpg`, но в PNG-хостинге Android ресурсов `.jpg` упаковывается как есть. Лежат в `drawable/` без density-квалификаторов — значит, **не масштабируются** (ок), но при `isCrunchPngs = false` (см. ниже) не пережимаются. Кандидат: WebP quality 80 → ~40-50% экономии (~350 КБ), либо вынести в `assets/` и грузить с диска. `ic_splash.png` — удалить (−13.8 КБ).

### `app/src/main/assets` — **7.1 МБ осн. вес**

| Путь | Размер |
|---|---|
| `assets/teachers_photos/` | **7.1 M** (99 JPEG, 31–59 КБ каждый) |
| `assets/college_map.json` | 65.6 K |
| `assets/teachers.csv` | 30.7 K |

**Вывод:** `teachers_photos/` — **самая тяжёлая часть APK после зависимостей**. 99 файлов по ~40 КБ. Если превью используется в списке (`CollegeScreen.kt` через `coil.compose.AsyncImage` — `CollegeScreen.kt:1068`, `:1259`), достаточно 200×200 WebP q75 → ~8-12 КБ каждый → **7.1 МБ → ~1 МБ**. Это крупнейшая единичная экономия в проекте.

### `app/src/admin/assets`

| Файл | Размер |
|---|---|
| `students.csv` | **349.3 K** |

**Вывод:** admin-flavor добавляет ровно 349 КБ (→ +0.1–0.2 МБ в APK, объясняет разницу standard/admin). Отдельная папка flavor — **правильное решение**, в standard не попадает.

### `app/build.gradle.kts` — release-сборка **не минифицируется и не шринкается**

```kotlin
release {                              // app/build.gradle.kts:74-81
  isCrunchPngs = false                 // :76  ← PNG не пережимаются
  isMinifyEnabled = false              // :77  ← R8/ProGuard ВЫКЛЮЧЕН
  isDebuggable = false
  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

**Корневая находка:** `isMinifyEnabled = false` + отсутствие `isShrinkResources = true`. `proguardFiles` объявлены, но не применяются — мёртвая строка. Это объясняет 23.7 МБ. R8 убирает неиспользуемый код (а тут его много, см. ниже) — типичный выигрыш 15-25% + `shrinkResources` 5-15%.

**Мин. фикс:**
```kotlin
release {
  isCrunchPngs = true
  isMinifyEnabled = true          // was false
  isShrinkResources = true        // отсутствовало
  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  signingConfig = signingConfigs.getByName("release")
}
```
Риск: R8 может поломать reflection (Moshi `@JsonClass(generateAdapter=true)` — кодоген, безопасно; Room — кодоген, безопасно; OkHttp/Retrofit — есть consumer-rules). **Требуется smoke-тест.** Это даст, по опыту, **23.7 МБ → ~16-18 МБ**.

### `gradle/libs.versions.toml` — тяжёлые/неиспользуемые зависимости

Подключены в `app/build.gradle.kts:109-172`:

| Зависимость | Строка build.gradle | Используется в коде? | Оценка веса |
|---|---|---|---|
| `firebase-ai` | `:135` | **Grep по `app/src/main`: 0 хитов** | ~3-5 МБ + транзитивно `firebase-common`, `firebase-annotations` |
| `firebase-appcheck-recaptcha` | `:145` | **0 хитов** | ~1-2 МБ + GMS |
| `firebase-appcheck-debug` | `:146` | **0 хитов** | small, но тянет `firebase-appcheck` |
| `platform(firebase-bom)` | `:111` | ради неиспользуемых | версии 34.17.0 |
| `converter-moshi` / `moshi-kotlin` / `moshi-kotlin-codegen` | `:134`, `:150`, `:171` | **0 хитов** (Retrofit не используется — `MpkNetworkClient` на голом OkHttp) | ~1 МБ + KSP-время |
| `retrofit` | `:153` | **0 хитов** | ~0.5 МБ |
| `logging-interceptor` | `:149` | **0 хитов** | ~50 КБ |
| `coil-compose` | `:133` | **да** — `CollegeScreen.kt:1068`, `:1259`, `MainHeaderBanner.kt:24` | ~1.5 МБ |
| `androidx-compose-material-icons-extended` | `:119` | да, но: full-набор иконок | ~**10+ МБ** несжатого, R8 срежет при minify |
| `androidx-work-runtime-ktx` | `:132` | да — `MpkWorkManagerHelper` | ~300 КБ |
| `androidx-room-*` | `:130-131`, `:170` | да | ~500 КБ |
| `roborazzi` (+ compose/junit/rule) | `:160-162` | только тесты | test-only, в APK не попадает |

**Grep-подтверждение неиспользуемости** (по `app/src/main`): единственные хиты на `coil|firebase|Firebase|retrofit2|Retrofit|moshi|Moshi` — 3 вызова `coil.compose.AsyncImage`/`SubcomposeAsyncImage` и импорт `coil.request.ImageRequest`. **`firebase`, `retrofit2`, `moshi` — нулевые.**

**Мин. фикс (самый жирный выигрыш без риска для кода):** удалить из `app/build.gradle.kts` строки `:111` (bom), `:134`, `:135`, `:145`, `:146`, `:149`, `:150`, `:153`, `:171` (ksp moshi) + соответствующие записи из `libs.versions.toml` (или оставить в toml, но не подключать — минимальный диф). Ожидаемо **−8-12 МБ** и заметно быстрее `ksp`/dex на CI.

**Важно:** проверить `proguard-rules.pro` и `google-services.json` на ссылки перед удалением (в `app/` не проверял — вне задачи; отметить как «не проверено»).

---

## 3. Прочие находки

### 3.1. Девять Room-Flow на одном экране расписания
`ScheduleScreen.kt:142-258` — полный список:
- `:142-144` `getLessonsForDay(group, 6)` — суббота
- `:233-235` `getLessonsForDateOrDay(...)` — активный день
- `:249-250`, `:251-252`, `:253-254`, `:255-256`, `:257-258` — пн/вт/ср/чт/пт

**7 отдельных подписок на таблицу `lessons`** (+ `getPendingTasksForGroup` на `tasks` = 8). Каждая инвалидация `lessons` (любой синк, любая правка) вызывает 7 перезапросов + 7 рекомпозиций. Все 5 дневных Flow нужны только для `weeklyLessonsByDow` (`:260-270`), который используется исключительно в `homeworkMarkerNumbers` (`:272-286`). **Это можно посчитать одним запросом** `getAllLessonsForGroup` + группировка в памяти, или вообще один раз по группе.

**Мин. фикс:** заменить 5 дневных Flow на один `getAllLessonsForGroup` (`LessonDao.kt:43-44`, уже есть), а `weeklyLessonsByDow` считать через `groupBy { it.dayOfWeek }` в `remember`. −5 подписок.

### 3.2. `flowOn(Dispatchers.IO)` × 7 в репозитории — избыточно, но безопасно
`ScheduleRepository.kt:36, 40, 44, 48, 52, 56, 61` — каждый метод оборачивает Room-Flow в `.flowOn(Dispatchers.IO)`. Room уже выполняет запросы на своём executor (`RoomDatabase` по умолчанию — `ArchTaskExecutor`/`IO`). Двойное переключение контекста на каждый эмит. **Не баг**, но при 7 Flow × частые эмиссии — это лишние coroutine-dispatch'и. Удалить можно одной строкой на метод, но эффект мал — **не рекомендую трогать** без профилирования.

### 3.3. `HomeworkMarkers.dedupeByLatestDate` внутри `remember` — O(N log N) на каждый эмит
`ScheduleScreen.kt:269`: `.mapValues { (_, list) -> HomeworkMarkers.dedupeByLatestDate(list) }`. Ключи `remember` (`:261`) — 6 списков; при эмиссии одного из них пересчитываются все 6. Дёшево по абсолютным числам, но `markerLessonNumbers` (`:279-285`) внутри `remember` (`:272`) требует ещё и `pendingTasks` — каждый тап по заданию в другой вкладке это пересчёт. Не критично, отметить.

### 3.4. `AppUiState` собирает `groupInfo` через `GroupParser.parse` в конструкторе VM
`AppViewModel.kt:57-63` — `GroupParser.parse(...)` вызывается **дважды**: на `:60` для `groupInfo` и на `:59` для `currentGroupName` (через `WidgetUpdateHelper.getSelectedGroup(application)` 2 раза → 2 `getSharedPreferences`). Мелочь, но при холодном старте. Мин. фикс: один локальный `val group = ...getSelectedGroup(application)` перед `MutableStateFlow`.

### 3.5. `MainScreen.kt:202` — двойная подписка на `uiState`
```kotlin
val uiState by viewModel.uiState.collectAsState()          // :61
...
val pendingTeacher by viewModel.uiState.collectAsState()   // :202 (в CollegeTab)
```
`CollegeTab` переподписывается на **тот же** `StateFlow`, из-за чего любое изменение `isSyncing`/`scrollTarget` рекомпозирует и `CollegeTab`. Мин. фикс: передавать `uiState` параметром (он уже есть в `MainScreen`) вместо нового `collectAsState`.

### 3.6. `BellsScreen` — `collectAsState` + локальный 30-секундный таймер
`BellsScreen.kt:109-111` — Flow по `(group, todayDow)`, `:123-129` — `while(true) { delay(30_000) }` для `currentTimeMinutes`. Таймер крутится, пока вкладка в composition (а она теряется при переключении — см. жалобу 3, т.е. пересоздаётся). `delay(30_000)` без привязки к минуте → сдвиг обновления до 30 с. Не жалоба заказчика, но: при каждом входе на «Звонки» таймер стартует с 0 → первое обновление через 30 с. Ок.

### 3.7. `LessonEntity` — `by lazy` на `@delegate:Ignore`
`LessonEntity.kt:26-44` — 4 ленивых вычисления (`shortSubjectName`, `subjectAcronym`, `officialSubjectName`, `timeRangeDisplay`) с `SubjectFormatter` внутри. `@delegate:Ignore` корректно исключает из Room. `by lazy` потокобезопасен по умолчанию (`SYNCHRONIZED`) → синхронизация на каждом первом обращении к полю в UI-потоке. Для `SubjectFormatter.getShortName` это regex-работа (`SubjectFormatter` не читал детально — **не проверено**). Если вызывается в LazyColumn для каждой карточки при скролле — стоит проверить. Пометить как возможную микро-находку.

### 3.8. `circularProgressIndicator` внутри `AnimatedContent`
`ScheduleScreen.kt:501-524`: спиннер `isInitialLoad` находится **внутри** `AnimatedContent`, чей `targetState = selectedSlot to selectedDateString` (`:505`). При переключении «Сегодня»→«Завтра» `AnimatedContent` запускает fade, а внутри **уже** новый контент со спиннером → пользователь видит «спиннер → заглушка». Комментарий на `:502-504` объясняет, почему ключ анимации — день, а не данные (правильно), но не отменяет того, что **внутрь анимации попадает `isInitialLoad == true`**. Если применить фикс 2a (сохранение последних данных), это перестанет мигать автоматически.

### 3.9. Двойной период синхронизации расписания
- `MpkWorkManagerHelper.setupPeriodicScheduleCheck` — `PeriodicWorkRequest` 15 мин (`MpkWorkManagerHelper.kt:14, 25-28`), окно 10:00–21:00 внутри воркера (`ScheduleCheckWorker.kt:34`).
- `WidgetAlarm` — 1 мин, окно 07:00–21:00 (`BellCountdownWidgetProvider.kt:399-400`).

Оба источника могут писать в БД. `ScheduleCheckWorker` делает полный сетевой fetch + `replaceSyncedLessons` (`ScheduleCheckWorker.kt:83, 89`) → `DELETE`+`INSERT` вне транзакции (см. фикс 2b) → возможна пустая эмиссия в UI как раз в момент, когда пользователь смотрит расписание. **Это правдоподобный механизм для жалобы 2**, если заглушка появляется не при переключении, а сама по себе.

### 3.10. `MainActivity.onResume` → `updateAllWidgets`
`MainActivity.kt:97-100`. На каждый резюм (включая возврат из фона, диалогов, поворот) — 4 провайдера × все инстансы × `runBlocking` в БД. Для `TodaySchedule`/`Bells` данные не меняются — пересборка бессмысленна. Мин. фикс: убрать вызов из `onResume` вовсе (тик виджета и так раз в минуту) или оставить только `BellCountdown` + `NowNext`.

### 3.11. `AndroidManifest.xml` подтверждает: Firebase/AppCheck мертвы
`app/src/main/AndroidManifest.xml` — **ни одного** `<provider>`/`<meta-data>` для Firebase, AppCheck, GMS, `google-services`. Нет кастомного `android:name` у `<application>` (используется дефолтный `android.app.Application`) → **инициализации Firebase в старте процесса нет вообще**; `MpkDatabase.getInstance` ленив (`MpkDatabase.kt:31-43`) и вызывается впервые из `AppViewModel.init` (`AppViewModel.kt:51`). Компоненты манифеста: 4 widget-receiver'а, `WidgetConfigActivity`, `BellTimerService` (FGS `specialUse`), `WidgetAlarmReceiver`. → **firebase-bom / firebase-ai / appcheck-recaptcha / appcheck-debug можно удалять без правок манифеста** (пункт 3 сводки подтверждён).

Права: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` — лишних нет.

### 3.12. `proguard-rules.pro` — пустая болванка
`app/proguard-rules.pro` содержит **только закомментированный шаблон** (22 строки, ноль активных `-keep` правил). Это важно: перед включением `isMinifyEnabled = true` (пункт 1 сводки) нужно понимать, что защищать придётся «с нуля»:
- Room — кодоген + consumer-rules, обычно достаточно, но DAO лямбды/`suspend` через KSP безопасны;
- Moshi `@JsonClass(generateAdapter = true)` — кодоген, безопасно (но зависимость всё равно удаляется, п. 3);
- OkHttp — consumer-rules есть;
- **Реальный риск:** `resources`-reflection в `RemoteViews` (`setInt(id, "setBackgroundColor", ...)`, `views.addView`, `setTextViewText`) — это **id-based, не reflection**, R8 не ломает. Ок.
- **Настоящий риск:** `isShrinkResources = true` может срезать layout'ы виджетов (`widget_*.xml`), на которые ссылаются только через `R.layout.*` в Kotlin — они **достижимы** из кода, должны сохраниться. Но `roborazzi`/`robolectric`-тесты, ищущие ресурсы по имени, и `WidgetLayoutsTest.kt` — прогнать после включения.
Вывод: **включение R8 ожидаемо безопасно, но требует прогона тестов и ручного smoke-теста виджетов** (4 виджета + конфетти).

### 3.13. Не проверено (вне адресного чтения)
- `SubjectFormatter.kt` — стоимость regex внутри `LessonEntity.shortSubjectName` (`LessonEntity.kt:27-29`, `by lazy SYNCHRONIZED`).
- `BackupManager.kt`, `NotificationHelper.kt`, `BellCountdownNotifier.kt` — не читались.
- `TasksScreen.kt` целиком (только Grep) — 1 Flow на группу (`:104-107`) + `remember`-фильтры, выглядит нормально.
- `CollegeScreen.kt` (1000+ строк) — только точки `AsyncImage` (`:1068`, `:1259`). Возможны собственные `runBlocking`/тяжёлые вычисления на главном потоке — **приоритетный кандидат на дообследование**.
- `CollegeMapScreen.kt`, `GroupScheduleScreen.kt`, `SettingsScreen.kt` — не читались.
- Соответствие 99 JPEG в `assets/teachers_photos/` записям `teachers.csv` — не сверялось.

---

## 4. Сводка приоритетов

| # | Проблема | Жалоба | Мин. фикс | Диф | Эффект |
|---|---|---|---|---|---|
| 1 | `isMinifyEnabled = false`, нет `shrinkResources` | — | `app/build.gradle.kts:76-77` | 2 строки | **APK 23.7 → ~17 МБ** |
| 2 | `teachers_photos/` 7.1 МБ JPEG | — | конвертация в WebP 200×200 | разово | **APK −6 МБ** |
| 3 | Неиспользуемые firebase-ai / appcheck / retrofit / moshi | — | `app/build.gradle.kts:111,134,135,145,146,149,150,153,171` | 9 строк | **APK −8-12 МБ**, быстрее CI |
| 4 | `initial = null` + новый Flow на каждый слот | 2 | `ScheduleScreen.kt:233-243` → один Flow + фильтр в памяти | ~10 строк | Убирает мигание заглушки |
| 5 | 9 Room-Flow на экране | 1, 2 | 5 дневных → один `getAllLessonsForGroup` | ~15 строк | −5 подписок, −7 перезапросов |
| 6 | `selectedSlot`/`selectedDateString` на голом `remember` | 3 | поднять в `MainScreen` над `when` (+`rememberSaveable`) | ~8 строк | Состояние переживает вкладки |
| 7 | `isTeacherKnown` парсит CSV в композиции | 3 | `by lazy` или вынести в VM | 1-3 строки | Быстрее возврат на вкладку |
| 8 | Виджеты: `runBlocking` + `removeAllViews` + 2 таймера | 4 | кэш `todayLessons`, убрать лишние провайдеры из тика | ~15 строк | Убирает «пропадание» |
| 9 | Конфетти 16×504 КБ bitmap + 16× updateAllWidgets | 4 | 180×130 / 8 кадров / только один виджет | ~6 строк | −8 МБ аллокаций, −15 пересборок |
| 10 | `syncSchedule(force=false)` в `init` всегда | 1 | гейт по `hasSchedule()` | ~4 строки | Быстрее холодный старт |
| 11 | `replaceSyncedLessons` DELETE+INSERT вне транзакции | 2, 4 | `@Transaction` в DAO | ~5 строк | Убирает пустые эмиссии |
| 12 | `map_floor*.jpg` 824 КБ | — | WebP q80 | разово | −350 КБ |
| 13 | Мёртвый `ic_splash.png` + `IntroSplash` | — | удалить | 2 файла | −13.8 КБ |

**Правило для всех пунктов 4-11: изменения локальные, перепроектирование не требуется.** Пункты 1-3 (размер) дают наибольший абсолютный эффект при наименьшем риске, но пункт 1 требует smoke-теста R8.

---

*Отчёт подготовлен по адресному чтению кода. Строки указаны по состоянию файлов на момент ревью. Проект не собирался.*