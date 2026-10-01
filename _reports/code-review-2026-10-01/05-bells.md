# Ревью: Звонки и уведомления

_Дата: 2026-10-01. Область: bells. Ревьюер: босс (лично)._

## Резюме и вердикт

Логика отсчёта чистая и хорошо покрыта (`countdownForMinute`, `isQuietWindow`, `tomorrowFirstLessonLabel` — pure-функции, есть тесты BellCutoffTest/NotificationQuietWindowTest). НО корень жалобы «звонки глючат» — **архитектурный, а не в коде**: расписание звонков ЗАХАРДКОЖЕНО в `CollegeBellSchedule` (STANDARD/THURSDAY/SATURDAY), и любой нестандартный день (сокращённые уроки, жара, праздник, перенос) даёт неверный отсчёт. Сам DISCLAIMER (строка 36-37) это признаёт: «Сокращённые звонки не синхронизируются автоматически». Второй источник глюков — точные будильники на Android 14+ (см. BEL-02, пересекается с виджетами).

## Критичные баги

### BEL-01 [high/bug] Расписание звонков — хардкод, рассинхрон с реальным днём = «звонки глючат»
- **Файл**: `data/model/CollegeBellSchedule.kt:39-79` (STANDARD_BELLS/THURSDAY_BELLS/SATURDAY_BELLS), `getTypeForDay:83-89`.
- **Root cause**: приложение знает только три фиксированных шаблона (Пн-Ср+Пт / Чт с инфочасом / Сб). Реальный колледж публикует сокращённые дни (последний звонок, экзамены, жара, 1 сентября и т.п.). В такие дни отсчёт «до звонка» и момент вибрации/уведомления НЕ совпадают с реальным звонком → пользователь видит «глюк».
- **Доказательство**: DISCLAIMER:36 прямо говорит «Сокращённые звонки не синхронизируются автоматически». Данные о сокращениях на сайте ЕСТЬ (в документе расписания), но не подтягиваются.
- **Фикс** (по возрастанию сложности):
  1. _Минимальный_: при синке расписания (ScheduleRepository) парсить время пар из документа и переопределять `start/end`/`breakAfterMinutes` на текущую дату; `CollegeBellSchedule` becomes fallback, если данных на дату нет. Хранить override в Room (колонка/таблица `bell_overrides(dateString, lessonNumber, start, end)`).
  2. _Полный_: отдельный источник «расписание звонков на дату» с сайта (`raspisanie-zvonkov/` — ссылка есть на портале, см. живой grep), кэш в БД.
- **Вопрос заказчику**: стоит ли игра свеч — сокращённые дни редки. Если да → вариант 1 закрывает 90%.

### BEL-02 [high/bug] Точные будильники не гарантированы на Android 14+ → отсчёт/звонки «плавают»
- **Файл**: `widget/BellCountdownWidgetProvider.kt:464-470` (`WidgetAlarm.scheduleNext`), пересекается с BellTimerService.
- **Root cause**: targetSdk 36. `SCHEDULE_EXACT_ALARM` (манифест:9) на Android 14+ **не выдаётся по умолчанию** — пользователь должен включить вручную. Код честно проверяет `canScheduleExactAlarms()` и при false деградирует до `setAndAllowWhileIdle` (неточный). В doze неточный будильник срабатывает с задержкой до ~15 мин → уведомление «до звонка 5 мин» приходит когда звонок уже прошёл.
- **Доказательство**: манифест декларирует только `SCHEDULE_EXACT_ALARM` (отзываемое), НЕ `USE_EXACT_ALARM` (для будильников/календаря, не отзывается, но Play-политика ограничивает категорию).
- **Фикс**:
  1. Главный таймер — `BellTimerService` (foreground, START_STICKY, тикает по `delay` до границы минуты, drift-free — код корректен, BellTimerService.kt:44-46). Он НЕ зависит от exact alarm. Убедиться, что он реально стартует и живёт (см. BEL-03).
  2. Будильник — только страховка. Добавить в настройки экран «разрешите точные будильники» с deep-link `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, если `!canScheduleExactAlarms()` — иначе пользователь не знает, почему «плавает».
  3. `scheduleNext` при неточном будильнике не даёт минутной точности — это ожидаемо, но UI должен честно показывать «для точного отсчёта включите строку уведомления» (FGS).

### BEL-03 [medium/bug] BellTimerService убивается battery-оптимизацией, перезапуск не гарантирован
- **Файл**: `util/BellTimerService.kt:33-37` (onStartCommand → START_STICKY), манифест:102-108 (foregroundServiceType=specialUse + PROPERTY_SPECIAL_USE_FGS_SUBTYPE — КОРРЕКТНО для targetSdk 34+).
- **Root cause**: START_STICKY перезапускает сервис, но на агрессивных OEM (Xiaomi/Samsung/Huawei) FGS `specialUse` убивается вместе с приложением в doze, а перезапуск не гарантирован без exact alarm или WorkManager-страховки. `specialUse` — самый слабый приоритет FGS.
- **Фикс**: добавить периодический `WorkManager` (15 мин) как watchdog — если `isEnabled && !BellTimerService.isRunning` → `BellTimerService.start()`. Не полагаться только на START_STICKY. (См. также отчёт 04-widgets — там та же проблема с будильником.)

## Баги «через раз» / плавающие

### BEL-04 [medium/bug] `runBlocking` + Room-запрос каждую минуту в фоновом тике
- **Файл**: `BellCountdownWidgetProvider.kt:218-235` (`lastGroupLessonForToday`), вызывается из `BellCountdownNotifier.buildNotification` → `BellTimerService.startTicking` каждый тик (минута).
- **Root cause**: `runBlocking { MpkDatabase...getLessonsForDaySync }` на `Dispatchers.Default` каждую минуту. Кэш по (группа|dow|дата|HH:mm) сбрасывается КАЖДУЮ минуту (ключ включает минуты), то есть фактически кэш не помогает между тиками — БД читается раз в минуту всё время работы сервиса. Не main-thread, но лишняя батарея/IO.
- **Фикс**: кэшировать по (группа|dow|дата) БЕЗ минут — «последний урок группы сегодня» не меняется в течение дня. Один запрос в день вместо 1440.

### BEL-05 [low/bug] Полоса прогресса считает все уроки по 45 минут
- **Файл**: `BellCountdownNotifier.kt:357` — `((45 - minutesLeft).coerceIn(0,45) * 100 / 45)`.
- **Root cause**: прогресс-бар предполагает 45-минутный урок. На перемене «до N урока» (left может быть до 25 на большой перемене) прогресс почти полный сразу; для сокращённых уроков — неверно. Косметика, но читается как глюк.
- **Фикс**: считать прогресс от реальной длительности текущего слота (`bell.endMinutes - bell.startMinutes`), а не константы 45.

### BEL-06 [low/bug] `getTimeForNumber`/`getTimeForLessonNumber` — молчаливый фолбэк на чужие времена
- **Файл**: `CollegeBellSchedule.kt:134` (`return Pair("08:15","09:55")`), `:146` (`"08:15","09:00"`).
- **Root cause**: при отсутствии урока с номером возвращает захардкоженное время вместо null/ошибки — вызывающий не узнает, что данных нет, и покажет неверное время. Низкий риск (номера 1-12 всегда есть).

## Архитектура и ООП

- Логика отсчёта вынесена в pure-функции (`countdownForMinute`, `nightFirstLessonLabel`, `tomorrowFirstLessonLabel`, `isQuietWindow`) — ХОРОШО, тестируемо, не ломать.
- `BellCountdownWidgetProvider.countdownInfo` и `buildViews` дублируют dow-конвертацию (Calendar→1..7) в 4 местах (Notifier:170-178, 205-212; Provider:55-63, 207-210, 248-255). Вынести в `CollegeBellSchedule.dowOf(cal)` — один источник (частично уже есть `dowOfCalendar` в MpkScheduleParser — переиспользовать!).
- `BellItem.startMinutes/endMinutes` парсят строку «HH:mm» на КАЖДОЕ обращение (getter, `:23-31`) — в цикле `bells.forEachIndexed` это ~12 парсингов на тик. Мелочь: вычислить Int один раз при создании списка.

## Производительность

- BEL-04 (runBlocking каждую минуту) — главный расход.
- `startForeground` вызывается каждую минуту (BellTimerService:52) с полной пересборкой уведомления — ожидаемо для живого отсчёта, но `buildNotification` тянет `countdownInfo` → `lastGroupLessonForToday` (BEL-04). Оптимизация BEL-04 снимет и это.

## Безопасность

- Персональных данных нет. POST_NOTIFICATIONS (манифест:7) обрабатывается через `canPost` (Notifier:327) — корректно. `notify` обёрнут в try/catch SecurityException — хорошо.

## Стиль

- Комментарии отличные (история T10/O6/W3 прослеживается). Много полного qualifies `java.util.Calendar` рядом с импортом — привести к одному виду (та же претензия, что в 01-schedule-core).

## Пробелы в тестах

- Есть: BellCutoffTest, NotificationQuietWindowTest, TomorrowLessonWidgetTest — чистые функции покрыты.
- НЕТ: тест на override звонков из документа (появится с фиксом BEL-01); тест `getTimeForNumber` на отсутствующий номер (BEL-06); интеграционный тест жизненного цикла BellTimerService (сложно, Robolectric).

## Что работает хорошо (не ломать)

- Pure-функции отсчёта + quiet window + ночное/вечернее окна — продуманы, покрыты тестами.
- `BellTimerService` тик без drift (пересчёт до границы минуты каждую итерацию, :44-46).
- Манифест: `foregroundServiceType=specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` — корректно для targetSdk 34+ (без property FGS падает на Android 14).
- `setOnlyAlertOnce(true)` + IMPORTANCE_LOW + setSilent — строка не «ding-ает» каждую минуту. Правильно.

## Рекомендуемый порядок исправлений

1. **BEL-02/BEL-03** (вместе с виджетами, общая проблема exact-alarm/FGS-выживания): WorkManager-watchdog + экран «включите точные будильники». Закрывает «звонки плавают/виджет не обновляется» одной мерой.
2. **BEL-04** — кэш по дню, а не по минуте (быстрая победа, снимает 1440 запросов/день).
3. **BEL-01** — override звонков из документа (требует решения заказчика по объёму).
4. **BEL-05/BEL-06** — косметика.
