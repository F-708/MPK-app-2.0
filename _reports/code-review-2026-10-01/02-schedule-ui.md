# Ревью: Расписание — UI
_Дата: 2026-10-01. Область: schedule-ui_

## Резюме и вердикт

UI-слой расписания в целом написан грамотно: формат даты в БД (`dd.MM.yyyy`) совпадает с форматом, который UI подставляет в запросы; сравнения «точная дата vs шаблон дня недели» везде идут через `==`/`isBlank()` по строке одинакового формата (лексикографического сравнения дат нет — главный подозреваемый НЕ подтверждён); сортировка по датам в DAO сделана корректно через `substr(...)` по полям, а дедупликация — через `HomeworkMarkers.dateToMillis` (epoch). Это сильно.

НО есть три реальные проблемы, которые напрямую усиливают жалобу заказчика «сегодня 1 октября, расписание сломано, не синхронизируется»:

1. **«Сегодня» вычисляется через `remember { Calendar.getInstance() }` без ключа даты** — если композиция переживает полночь (приложение в фоне, процесс жив), экран продолжает показывать вчерашний день с заголовком «СЕГОДНЯ». Это ровно симптом «сегодня 1-е, а расписание не то».
2. **Пустое состояние не различает «нет данных» и «синхронизация упала с сетевой ошибкой»** — `hasSyncError` есть в `uiState`, но в ScheduleScreen не пробрасывается. При настоящем сетевом root cause пользователь видит «Расписание ещё не опубликовано», что уводит диагностику не туда.
3. **Подсветка «текущая пара» и `schoolDayEnded` залипают** — их `remember`-ключи не включают текущее время; при открытом экране они не обновляются на границе урока.

Вердикт: **данные в БД UI показывает корректно** (формат и логика выбора дня верны), но **«сегодня» определяется ненадёжно, а ошибки синхронизации маскируются под «не опубликовано»**. Область требует 2 исправлений уровня high и нескольких medium.

---

## Критичные баги

### SUI-01 — critical — «Сегодня»/«завтра» не обновляются после полуночи (remember Calendar без ключа даты)
- **file:line:** `ui/screens/ScheduleScreen.kt:147`, `:204`; `ui/screens/GroupScheduleScreen.kt:179`, `:198`; `ui/components/CalendarArchiveDialog.kt:97`; `ui/components/SimpleDatePickerDialog.kt:56`
- **root cause:** `remember { Calendar.getInstance() }` вычисляется один раз на время жизни композиции. Ключа, привязанного к календарному дню, нет. Если композиция экрана переживает смену даты (телефон в фоне ночью, процесс не убит, утром открыли приложение — вкладка «Расписание» активна), `realNow`, `realDayOfWeek`, `tomorrow`, `daySlots`, `leftIsToday`, `rightIsTomorrow` остаются вчерашними. Экран показывает вчерашний день, подписывая его «СЕГОДНЯ», и запрашивает уроки за вчерашнюю `activeDateString`.
- **доказательство:**
```kotlin
// ScheduleScreen.kt:147
val realNow = remember { Calendar.getInstance() }
val realDayOfWeek = remember { when (realNow.get(Calendar.DAY_OF_WEEK)) { ... } }
// :187  ключ realDayOfWeek тоже залипший → слоты вчерашние
val daySlots = remember(groupInfo.canonicalName, saturdayIsSchoolDay, realDayOfWeek) { ... }
// :209
val leftIsToday = sameDay(leftCal, realNow)   // realNow вчерашний → leftIsToday=false ИЛИ левый слот = вчера
```
`daySlots` строит `left` из `Calendar.getInstance()`, но только внутри `remember`, чей ключ `realDayOfWeek` не меняется → не пересчитывается. В GroupScheduleScreen та же картина (:179, :186).
- **влияние на жалобу:** прямой усилитель. Заказчик 1 октября при «тёплом» открытии видит 30.09 как «сегодня» и «не то расписание», даже если сеть и БД в порядке.
- **фикс:** ввести наблюдаемый «ключ дня». Минимально — пересчитывать `realNow` при `ON_RESUME`/смене дня. Рабочий вариант без новых зависимостей: хранить в ViewModel `StateFlow<String>` с `dd.MM.yyyy`, обновляемый по `ACTION_DATE_CHANGED`/`TIME_TICK` (или пересчитываемый в `onResume` MainActivity), и прокидывать его как ключ: `val todayKey by viewModel.todayKey.collectAsState(); val realNow = remember(todayKey) { Calendar.getInstance() }`. Тогда смена дня инвалидирует все зависимые `remember`. `MainActivity.onResume` уже существует (`MainActivity.kt:63`) — точка входа есть.

---

## Баги «через раз» / плавающие

### SUI-02 — high — Пустое состояние маскирует сетевую ошибку синхронизации под «не опубликовано»
- **file:line:** `ui/screens/ScheduleScreen.kt:588-635` (empty state), `ui/screens/MainScreen.kt:183-199` (вызов), `ui/viewmodel/AppViewModel.kt:43,222-227` (`hasSyncError`)
- **root cause:** `AppViewModel` различает успех и сетевой сбой (`hasSyncError = true` при `onFailure`), но `MainScreen` не передаёт этот флаг в `ScheduleScreen`. Empty state всегда показывает «Расписание ещё не опубликовано» / «пар пока нет в базе колледжа», независимо от того, упал ли sync. Кнопка ручного обновления есть (`empty_state_sync_btn`, :653-683 — хорошо), но текст вводит в заблуждение при сетевой ошибке.
- **доказательство:** grep `hasSyncError` по `MainScreen.kt` и `ScheduleScreen.kt` — пусто (не проброшен). В empty state только ветки `selectedDateString.isNotBlank()` / else:
```kotlin
// ScheduleScreen.kt:631
text = if (selectedDateString.isNotBlank()) { "Нет данных за $selectedDateString..." }
       else { "Для группы ${groupInfo.canonicalName} на этот день пар пока нет в базе колледжа." }
```
- **влияние:** при настоящем root cause (сеть/URL) пользователь и ревьюер видят «не опубликовано» и думают на данные/сайт, а не на сбой загрузки. GroupScheduleScreen в этом плане лучше — у него есть `syncNotice` с текстом «Не удалось загрузить расписание: проверьте интернет» (:159) и «ОБНОВИТЬ» (:531).
- **фикс:** прокинуть `hasSyncError` из `uiState` в `ScheduleScreen` и в empty state при `hasSyncError` показывать «Не удалось обновить расписание — проверьте интернет» (по образцу GroupScheduleScreen `syncNotice`).

### SUI-03 — medium — «Текущая пара» и `schoolDayEnded` залипают: remember-ключи не включают время
- **file:line:** `ui/screens/ScheduleScreen.kt:260-268` (`currentLessonNumber`), `:360-372` (`schoolDayEnded`); `ui/screens/GroupScheduleScreen.kt:210-218` (`currentLessonNumber`)
- **root cause:** оба значения читают `Calendar.getInstance()` (текущие минуты) внутри `remember`, но ключ `remember` — `(activeDayOfWeek, selectedSlot, activeDateString)`, время в ключ не входит. Пока композиция жива, значение не пересчитывается на границе урока. Итог: подсветка «идёт сейчас» остаётся на паре, которая уже закончилась; `schoolDayEnded` залипает в `false` после конца занятий.
- **доказательство:**
```kotlin
// ScheduleScreen.kt:260
val currentLessonNumber = remember(activeDayOfWeek, selectedSlot, activeDateString) {
    if (selectedSlot == 0 && leftIsToday) {
        val cal = Calendar.getInstance()               // читается здесь...
        val minutes = cal.get(HOUR_OF_DAY)*60 + cal.get(MINUTE)
        CollegeBellSchedule.getBellsForDay(activeDayOfWeek)
            .firstOrNull { minutes in it.startMinutes..it.endMinutes }?.lessonNumber ...
    } else null                                        // ...но ключ времени не содержит → не обновится
}
```
`schoolDayEnded` (:360) — та же проблема, ключ `(activeDayOfWeek, selectedSlot, activeDateString, lessons)`.
- **взаимодействие:** из-за залипания `currentLessonNumber` `LaunchedEffect(scrollToCurrentRequested, isInitialLoad, ...)` и автопрокрутка к «сейчас» (:343) используют устаревший номер; `schoolDayEnded==false` не даёт переключиться на «завтра» после уроков (:398).
- **фикс:** привязать к тикающему ключу. Минимально — раз в минуту обновлять `nowMillis` в ViewModel (`StateFlow<Long>`, `TIME_TICK`) и добавить его (округлённым до минуты) в ключ `remember`. Либо вынести расчёт в `snapshotFlow`/`LaunchedEffect` с периодическим триггером. Тот же ключ дня из SUI-01 частично помогает (пересчёт на новую дату), но не решает смену урока внутри дня.

### SUI-04 — medium — GroupScheduleScreen: гонка при быстром переключении группы (общий isSyncing + чтение selectedGroupName внутри корутины)
- **file:line:** `ui/screens/GroupScheduleScreen.kt:147-167`
- **root cause:** (а) `syncForeignSchedule` читает `selectedGroupName` ВНУТРИ `scope.launch` (делегат `rememberSaveable`), а не фиксирует в момент нажатия; при смене группы между запуском и выполнением корутины синхронизируется уже другая группа. (б) общий флаг `isSyncing` + `if (isSyncing) return`: если запущена синхронизация группы A и пользователь переключился на B, `LaunchedEffect(B)` вызовет `syncForeignSchedule`, но упрётся в `isSyncing==true` → группа B не синхронизируется, пока идёт A (и notice может прийти по A, а список уже B).
- **доказательство:**
```kotlin
fun syncForeignSchedule(force: Boolean) {
    if (selectedGroupName.isBlank() || isSyncing) return   // (б) блокирует вторую группу
    isSyncing = true
    scope.launch {
        val result = scheduleRepository.syncScheduleFromWeb(selectedGroupName, force = force) // (а) читает ТЕКУЩУЮ группу
        ...
    }
}
LaunchedEffect(selectedGroupName) { if (selectedGroupName.isNotBlank()) syncForeignSchedule(force = false) }
```
- **фикс:** захватить группу в локальную `val group = selectedGroupName` до `launch` и синхронизировать именно её; `isSyncing`/`syncNotice` привязать к конкретной группе (или хранить `Map<group, State>`), чтобы переключение не блокировалось и notice не «прилетал» чужой группе. Либо отменять предыдущий `Job` при смене группы (`job?.cancel()`).

---

## Архитектура и ООП

### SUI-05 — low — Дублирование логики «сегодня/следующий учебный день/формат даты» между двумя экранами
- **file:line:** `ui/screens/ScheduleScreen.kt:175-207` (`calendarDayOfWeek`, `isSchoolDay`, `daySlots`, `sameDay`, `%02d.%02d.%04d`) vs `ui/screens/GroupScheduleScreen.kt:81-108` (`groupScheduleDayOfWeek`, `groupScheduleIsSchoolDay`, `groupScheduleNextSchoolDay`, `groupScheduleDbDate`)
- **root cause:** GroupScheduleScreen вынес чистые функции в `internal` (тестируемо — хорошо), но ScheduleScreen держит те же алгоритмы инлайн, с локальными `fun` внутри композабла. Две реализации одного и того же (`calendarDayOfWeek` == `groupScheduleDayOfWeek`, `sameDay` и пр.). Риск расхождения: правка «следующего учебного дня» в одном экране не дойдёт до второго.
- **фикс:** переиспользовать `internal`-хелперы GroupScheduleScreen (или поднять их в общий `util`) из ScheduleScreen; удалить локальные дубликаты. `groupScheduleDbDate` уже есть — заменить им `%02d.%02d.%04d`-форматирование в `activeDateString` (ScheduleScreen:237, :509, :522).

### SUI-06 — low — Две разные стратегии выборки «день по дате/шаблону»: in-memory (ScheduleScreen) vs DAO (GroupScheduleScreen)
- **file:line:** `ui/screens/ScheduleScreen.kt:283-286,1202-1210` (`lessonsForDay` по всему `allGroupLessons`) vs `ui/screens/GroupScheduleScreen.kt:221-227` (`getLessonsForDateOrDay` из DAO + `pickForeignGroupLessons`)
- **root cause:** ScheduleScreen тянет весь список группы одним Flow (это осознанная оптимизация O1/A — хорошо) и фильтрует в памяти; GroupScheduleScreen идёт в DAO на каждый день. Обе дают одинаковую семантику (точная дата приоритетна), но реализованы по-разному. Не баг, а несогласованность: `pickForeignGroupLessons` и `lessonsForDay` — почти близнецы с разной подписью.
- **фикс:** оставить как есть (у ScheduleScreen один Flow — обосновано), но свести фильтрацию к одной общей чистой функции, чтобы не разъезжалась.

---

## Производительность (рекомпозиции)

### SUI-07 — low — Мелочи рекомпозиции в ScheduleScreen
- **file:line:** `ui/screens/ScheduleScreen.kt:173` (`saturdayIsSchoolDay` без remember), `:279` (`LaunchedEffect(allGroupLessons)` с ключом-List)
- **root cause:** (а) `val saturdayIsSchoolDay = groupInfo.course == 1 || saturdayLessons.isNotEmpty()` считается на каждой рекомпозиции (дёшево, но это ключ `remember(daySlots)` — лишние пересчёты). (б) `LaunchedEffect(allGroupLessons) { firstEmit = true }` — ключ `List`; `List.equals` по содержимому, так что на каждый НОВЫЙ по составу эмит Room эффект перезапускается (присваивание `true` — дёшево, но семантически ключ слишком широкий; достаточно было `Unit`/одноразового флага).
- **фикс:** обернуть `saturdayIsSchoolDay` в `remember(groupInfo.course, saturdayLessons)`; для `firstEmit` использовать `LaunchedEffect(Unit)` внутри ветки «нет данных из VM» или `snapshotFlow` по факту первого эмита.
- **что уже хорошо (не ломать):** один Flow на группу вместо семи (O1/A/F, :160-172, :298-305), `AnimatedContent` с ключом по дню, а не по данным (:561-571), `remember` на `lessons`/`slots`/`fromRoom`/`weeklyLessonsByDow` — это правильные оптимизации.

---

## Стиль и консистентность

### SUI-08 — low — CalendarArchiveDialog: точка «есть данные» рисуется для weekly-шаблона на любых днях (в т.ч. будущих/прошедших)
- **file:line:** `ui/components/CalendarArchiveDialog.kt:120-123`, `:428-431`
- **root cause:** `hasData` = `savedDatesDot.contains(dotStr) || (dow этого дня in weeklyLessonDows)`. Вторая ветка помечает точкой КАЖДЫЙ день, чей день недели есть в недельном шаблоне, независимо от того, синхронизированы ли реальные данные на эту дату. В архиве это показывает «данные есть» для даты, которой в БД нет (только шаблон). При сетевом баге календарь выглядит «заполненным», хотя точных дат нет — вводит в заблуждение.
- **фикс:** различать визуально «точная дата в БД» (filled-точка) и «только weekly-шаблон на этот dow» (пустая/бледная точка) либо показывать точку только для `savedDatesDot`.

### SUI-09 — low — Архивный weekly-фолбэк показывает «ПАРЫ НА <прошедшую дату>», которых не было
- **file:line:** `ui/components/CalendarArchiveDialog.kt:136-149`, заголовок `:504-505`
- **root cause:** при выборе архивной даты, на которую точных строк нет, `selectedDateLessons` падает в weekly-шаблон по dow (`else ... filter { dateString.isBlank() && dayOfWeek == dow }`). Заголовок при этом пишет «ПАРЫ НА 01.10.2026 (5)», хотя это шаблон недели, а не реально синхронизированный архив за эту дату. Семантика «архива» смешивается с «обычным недельным расписанием».
- **фикс:** в режиме архива (выбор конкретной прошедшей даты) показывать только точные `dateString == selectedDateDot`; при их отсутствии — «На этот день в архиве нет занятий» (текст уже есть, :533). Weekly-фолбэк оставить для основного экрана, но не подписывать его конкретной датой как «пары на дату».

### SUI-10 — low — Мелочи
- `ui/screens/ScheduleScreen.kt:330` — `didColdStartScroll` это `rememberSaveable` БЕЗ ключа группы: при смене группы автопрокрутка к «сейчас» уже не сработает (флаг остался `true` от прошлой группы). Низкая значимость (редкий сценарий), но логично добавить `groupInfo.canonicalName` в ключ, раз `selectedSlot`/`selectedDate` при смене группы сбрасываются (:217-223).
- `ui/components/SimpleDatePickerDialog.kt:189` — `isoFromParts` объявлена, но в самом файле не используется (MonthGrid строит ISO инлайн, :72/:79). Либо переиспользовать её в MonthGrid, либо убрать дублирование форматирования.
- `ui/components/GroupSelectionDialog.kt:125-137` — крестик обёрнут и в `IconButton(onClick=onDismissRequest)`, и во внутренний `bouncyClickable(onClick=onDismissRequest)` → двойной обработчик/двойная зона. Достаточно одного `bouncyClickable`.

---

## Пробелы в тестах

Чистые функции UI покрыты хорошо: `GroupScheduleLogicTest` тестирует `groupScheduleDayOfWeek`, `groupScheduleIsSchoolDay`, `groupScheduleNextSchoolDay`, формат `dd.MM.yyyy`, `pickForeignGroupLessons` (точная дата приоритетнее шаблона); `T8NavigationScrollTest` — `autoScrollLessonIndex`. Это правильно: спорная логика вынесена из `remember` в чистые функции ради тестируемости.

Что НЕ покрыто (и где риск):
- **`lessonsForDay` (ScheduleScreen.kt:1202)** — публичная, вынесена «ради юнит-теста» (комментарий :1199), но grep по тестам не находит ни одного вызова. Близнец `pickForeignGroupLessons` (та покрыта). Добавить тест: точная дата приоритетна; при отсутствии точной — weekly-шаблон по dow; пустой результат.
- **`autoScrollLessonIndex` + `currentLessonNumber`-залипание (SUI-03)** — нет теста на «время в ключе»; логика «идёт урок / уроки кончились» покрыта, но связь с реальным временем — нет.
- **Построение сетки месяца `calculateMonthDays` (MonthGrid.kt:109)** — нет юнит-теста на offset первого дня (ПН=0) и переход через границы года/високос. Именно здесь живёт риск «сетка месяца съехала».
- **`daySlots`/next-school-day логика Saturday** — покрыта для GroupScheduleScreen (`groupScheduleNextSchoolDay`), но ScheduleScreen использует свой инлайн-вариант (:187-200), который тестами не зафиксирован (см. SUI-05).
- **SUI-01 (полночь)** — нет теста/проверки, что «сегодня» пересчитывается при смене дня. Сложно для юнит-теста, но это самый опасный незакрытый сценарий.

---

## Что работает хорошо (не ломать)

- **Формат даты согласован по всей цепочке.** UI подставляет `"%02d.%02d.%04d"` (ScheduleScreen:237, GroupScheduleScreen `groupScheduleDbDate`), парсер пишет `SimpleDateFormat("dd.MM.yyyy")` (MpkScheduleParser:75,120), репозиторий `formatDate` — тот же формат (ScheduleRepository:495). Совпадает с конвенцией «дд.ММ.гггг». Расхождений нет.
- **Лексикографического сравнения дат в UI нет.** Все сравнения — `==`/`isBlank()` по строке единого формата, либо через `HomeworkMarkers.dateToMillis` (epoch) в `dedupeByLatestDate`, либо DAO-сортировка через `substr(dateString,...)` по полям год/месяц/день (LessonDao:76). Классический баг строк «01.10.2026 < 02.09.2026» здесь не воспроизводится.
- **Один Flow на группу** вместо семи (O1/A/F) — правильная оптимизация, снимает лишние перезапросы к Room.
- **Кнопка ручного force-sync есть в обоих экранах** (`empty_state_sync_btn` :653, `group_schedule_retry_button` :522) и в шапке (`sync_button` :473) — пункт «force sync» закрыт.
- **C3-паттерн**: данные группы живут в `AppViewModel` (`StateFlow`), переживают пересоздание поддерева вкладки → нет ложного спиннера/«не опубликовано» при возврате на вкладку. Обосновано и работает.
- **`AnimatedContent` ключуется по дню, а не по данным** (:561-571) — эмиссии Room не дёргают fade. Верно.
- **GroupScheduleScreen empty state честный**: различает «загружаем» / `syncNotice` (в т.ч. сетевая ошибка) / «не синхронизировано» (:499-504) — образец, на который стоит ровнять ScheduleScreen (SUI-02).

---

## Рекомендуемый порядок исправлений

1. **SUI-01 (critical)** — «сегодня» после полуночи. Прямо усиливает жалобу «1 октября — не то расписание». Ввести ключ дня (StateFlow `dd.MM.yyyy` в VM, обновление по `onResume`/`TIME_TICK`) и прокинуть в `remember(...)` всех дат. Самый большой эффект на симптом заказчика.
2. **SUI-02 (high)** — пробросить `hasSyncError` в ScheduleScreen и показывать сетевую ошибку вместо «не опубликовано». Без этого настоящий сетевой root-cause невидим пользователю и маскируется под «нет данных».
3. **SUI-03 (medium)** — тикающий ключ для `currentLessonNumber`/`schoolDayEnded`, чтобы подсветка «сейчас» и переключение на «завтра» после уроков работали при открытом экране.
4. **SUI-04 (medium)** — гонка переключения группы в GroupScheduleScreen: фиксировать группу до `launch`, не блокировать вторую группу общим `isSyncing`.
5. **SUI-08/SUI-09 (low)** — архив: не подсвечивать weekly-шаблон как «данные есть» и не подписывать его конкретной прошедшей датой; иначе архив выглядит заполненным при пустой БД точных дат.
6. **SUI-05/SUI-06 (low)** — свести дубли today/next-school-day/формата даты к общим `internal`-хелперам (уже есть в GroupScheduleScreen), убрать инлайн-дубликаты из ScheduleScreen.
7. **SUI-07/SUI-10 (low)** — мелкие рекомпозиции и чистка (двойной onClick крестика, неиспользуемый `isoFromParts`).
8. **Тесты** — добавить `lessonsForDay`, `calculateMonthDays` (offset ПН, високос, переход года); это закрывает два самых незащищённых чистых алгоритма области.
