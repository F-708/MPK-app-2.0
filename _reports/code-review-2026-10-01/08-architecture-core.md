# Ревью: Архитектура, навигация, ViewModel, тема, общий стиль

_Дата: 2026-10-01. Область: architecture-core. Ревьюер: босс (лично)._

## Резюме и вердикт

Приложение **не имеет формальной архитектуры** — нет DI, нет Navigation-компонента, слои (UI/VM/Repo/Data) присутствуют, но протыкаются насквозь (UI создаёт репозитории напрямую через `remember{}`, один экран читает CSV на main-thread). Это типичный результат генерации кода: каждый экран писался самодостаточным, общая структура не выдержана. Работает, но хрупко и дорого в поддержке. **Вердикт: нужен архитектурный фундамент перед крупными фичами** — иначе каждый баг-фикс будет порождать два новых (что и наблюдается: карта «третий клик» — прямое следствие `when()`-навигации).

## Критичные проблемы

### ARC-01 [high/architecture] Навигация — самодельный `when(tab)`, уничтожающий поддерево = корень бага карты
- **Файл**: `ui/screens/MainScreen.kt:175-177` (`when (uiState.currentTab)` + комментарий «when() уничтожает поддерево вкладки»), `ui/viewmodel/AppViewModel.kt:88-99` (C3: расписание пришлось поднять в VM именно потому, что collectAsState внутри экрана умирал при смене вкладки).
- **Root cause**: нет `NavHost`/`androidx.navigation`. Переключение вкладки = ветка `when` → Compose выбрасывает старое поддерево и строит новое. Любой `remember{}` внутри экрана (состояние карты, выбранный кабинет, позиция скролла) ТЕРЯЕТСЯ при уходе и возвращается «с нуля». Именно это порождает:
  - баг карты «после 2 выборов 3-й не нажимается» (см. 03-map) — `CollegeMapScreen` пересоздаётся, remember-состояние `initialRoom/selectedRoom` рассинхронизируется;
  - костыль C3 (поднятие `groupLessons`/`pendingTasks` в VM со `stateIn WhileSubscribed(5000)`) — чтобы данные переживали смерть вкладки;
  - `returnTab`/`pendingRoom`/`pendingTeacherName`/`scrollTarget` в `AppUiState` — самодельная замена Navigation-аргументам и backstack.
- **Фикс** (стратегический, большая волна): мигрировать на Navigation-Compose. `AppUiState.pending*` → nav-аргументы; `returnTab` → backstack; `scrollTarget` → SavedStateHandle. Это устраняет целый класс багов «состояние теряется при навигации» одной мерой. **Не делать в одну волну с баг-фиксами** — сначала точечно починить карту (03-map), миграцию Navigation планировать отдельно.

### ARC-02 [high/architecture] Нет DI — репозитории создаются в 6+ местах с разным набором зависимостей
- **Файлы**: `ScheduleRepository(...)` создаётся в `AppViewModel:62` (с teacherLessonDao+catalog), `CollegeMapScreen:140` (без teacherLessonDao), `CollegeScreen:211,1439,1548` (3 разных!), `ScheduleCheckWorker:92`. `TeachersRepository(context)` в `CollegeScreen:840,1118`, `StudentsScreens:436`. `MpkDatabase.getInstance` — синглтон, но репозитории — нет.
- **Root cause**: каждый вызов `remember{Repository(...)}` создаёт НОВЫЙ экземпляр с ДРУГИМИ параметрами. Следствия: (а) кэш `ScheduleRepository.cachedCatalog` не шарится между экземплярами — ассет читается повторно; (б) в CollegeMapScreen репозиторий БЕЗ teacherLessonDao — офлайн-преподаватели там не работают, хотя в AppViewModel работают (неконсистентность); (в) `@Volatile`-кэши в провайдерах (WidgetData, Provider) — отдельные от VM.
- **Фикс**: минимальный — один `AppContainer`/`ServiceLocator` (object) в Application, отдающий ШАРИРУЮЩИЕ репозитории; UI берёт из него вместо `remember{Repository()}`. Полный — Hilt/Koin (см. android-skills:koin). Для старта достаточно `object AppGraph { val scheduleRepo by lazy {...} }` в Application-классе (которого сейчас НЕТ — проверить; MainActivity делает всё в onCreate).

### ARC-03 [critical/performance] Блокирующий IO на main-thread: 800 КБ CSV в `remember{}`
- **Файл**: `ui/screens/StudentsScreens.kt:88` (`val students = remember { StudentsRepository(context).loadStudents() }`), `:436` (аналогично teachers), `CollegeScreen:840,1118`.
- **Root cause**: `loadStudents()` — suspend НЕ может быть внутри `remember{}`, значит это БЛОКИРУЮЩАЯ функция: синхронное чтение+парсинг 800 КБ students.csv (admin) прямо в композиции на UI-потоке. Первый вход на экран учащихся = подвисание/ANR на слабых устройствах. Каждый recomposition с новым ключом — повтор.
- **Доказательство**: `remember { ... loadStudents() }` — синхронный вызов, не `produceState`/`LaunchedEffect`.
- **Фикс**: `produceState(initialValue = emptyList()) { value = withContext(IO){ repo.loadStudents() } }` или поднять загрузку в VM как StateFlow. Кэш результата в репозитории (сейчас каждый `remember` — новый парсинг).

## Проблемы уровня medium

### ARC-04 [medium/architecture] `CollegeScreen.kt` — god-объект на 1901 строку
- **Файл**: `ui/screens/CollegeScreen.kt` (1901 строка): карточки преподавателей, вкладки, расписание преподавателя, список, детали, интеграция карты — всё в одном файле. `SettingsScreen` 1007, `TasksScreen` 879, `ScheduleScreen` 1210 — тоже крупные.
- **Root cause**: отсутствие разделения на composable-компоненты/экраны. Правка одного экрана рискует задеть соседний (общие приватные composable в файле).
- **Фикс**: декомпозиция по экранам/вкладкам в отдельные файлы (`college/TeacherCardScreen.kt`, `college/TeacherListScreen.kt`, ...). Механическая, но снижает риск при баг-фиксах. Приоритет — после критичных багов.

### ARC-05 [medium/architecture] Нет Application-класса — инициализация в MainActivity.onCreate
- **Файл**: `MainActivity.kt:29-37` (applyAppTheme, createChannel×2, BellTimerService.start, setupPeriodicScheduleCheck — всё в onCreate активити).
- **Root cause**: инициализация приложения привязана к активити, а не к `Application`. Процесс может быть поднят воркером/ресивером/виджетом БЕЗ MainActivity (например, WidgetAlarmReceiver) — тогда каналы уведомлений не созданы, тема не применена. `BellCountdownNotifier.createChannel` дублируется и в BellTimerService:26 (частичная защита), но не системно.
- **Фикс**: завести `class MpkApplication : Application()`, вынести туда инициализацию (каналы, WorkManager, AppGraph из ARC-02), зарегистрировать в манифесте `android:name=".MpkApplication"`. Это же — место для DI-контейнера.

### ARC-06 [medium/architecture] `AppUiState` — смешение навигации, данных и одноразовых событий
- **Файл**: `AppViewModel.kt:38-57`. В одном data class: `currentTab`/`returnTab`/`pendingTeacherName`/`pendingRoom`/`pendingFromRoom`/`scrollTarget` (навигация + события) и `isSyncing`/`hasSyncError`/`groupInfo` (состояние данных). `pending*`+`consume*` — самодельный паттерн «одноразового события» поверх StateFlow (гонки при быстрой recomposition, потеря события при смерти вкладки).
- **Фикс**: навигационные `pending*` уйдут с Navigation (ARC-01). Одноразовые события (toast, scroll) — `Channel`/`SharedFlow`, не StateFlow (`_toastEvent` уже SharedFlow — правильно; `scrollTarget` в StateFlow — неправильно, дублировать consume-паттерн не надо).

## Стиль и консистентность (заказчик: «подогнать под один стиль»)

- **Смешение полных qualifies и импортов**: `java.util.Calendar`, `com.politex.data.local.MpkDatabase`, `com.politex.util.GroupCatalog` пишутся полностью внутри кода в одних файлах и импортом в других. Причина — генерация кода разными «заходами». Правило: импорты сверху, полный qualify — только при конфликте имён. Прогнать ktlint/spotless (сейчас не подключены — проверить build.gradle).
- **dow-конвертация Calendar→1..7 продублирована 8+ раз** (WidgetData, Provider×3, Notifier×2, ScheduleCheckWorker×2, CollegeBellSchedule, ScheduleRepository). Вынести в ОДИН `MpkScheduleParser.dowOfCalendar(cal)` (уже существует) или `util/DowExt.kt`, переиспользовать везде. Это самый частый дубль в проекте.
- **prefs-обёртки** (`WidgetStyle.load/save/remove`, `CountdownMode.load/save/remove`, `AppThemeStore`) — один и тот же паттерн 4+ раза. Обобщить `enumPref<T>()`.
- **`@delegate:Ignore lazy`** в сущностях (LessonEntity, StudentTaskEntity) — консистентно, ок.
- **BOM в начале файлов** (`﻿package`) — HomeworkMarkers, MpkCurriculum, build.gradle.kts. Убрать.
- **Комментарии с историей решений** (T1-T14, O1-O9, Б3/Б7/Б12/Б13, F1-F6, W1-W8, C3) — отличная практика, СОХРАНИТЬ, но они же показывают, что код многократно латался точечно (каждый баг = свой шифр). Признак отсутствия изначальной архитектуры.

## Производительность (архитектурный срез)

- ARC-03 (main-thread CSV) — критично.
- `stateIn(WhileSubscribed(5000))` в AppViewModel — корректный паттерн для выживания данных при смерти вкладки; НЕ ломать при миграции Navigation (или заменить на nav-scoped VM).
- Нет baseline profile (проверить build-отчёт 09) — холодный старт без AOT-компиляции горячих путей.
- Отсутствие DI (ARC-02) = повторное чтение ассетов/создание репозиториев.

## Безопасность

- `students.csv` изолирован flavor'ом (admin) + `StudentsRepository` в try/catch→emptyList (по отчёту 06-teachers) — у standard-пользователей crash исключён, экран гейтится `isAdmin`. Хорошо.
- ARC-05: инициализация каналов уведомлений в активити — если процесс поднят без активити, `notify()` в канал без канала = тихий drop (Android 8+). Не секьюрити, но надёжность.

## Пробелы в тестах

- НЕТ тестов на: AppViewModel (state-переходы, consume-паттерны, syncSchedule), навигацию (pending*/returnTab), блокирующий IO (ARC-03 поймался бы Robolectric-тестом «экран учащихся не падает/не висит»). Есть UiRenderSmokeTest/Step2UiLogicTest — проверить, покрывают ли VM.

## Что работает хорошо (не ломать)

- `stateIn(WhileSubscribed(5000))` + `flatMapLatest` для groupLessons/pendingTasks — правильный реактивный паттерн, переживает смерть вкладки (C3).
- `_toastEvent: MutableSharedFlow` для одноразовых событий — корректно (не StateFlow).
- `AppUiState.update{copy()}` — иммутабельные обновления без гонок.
- Комментарии-решения (история T/O/Б/F/W/C) — бесценны для понимания «почему так».
- `enableEdgeToEdge()` + applyAppTheme — современный подход к теме.
- PendingIntent CLEAR_TOP|SINGLE_TOP + onNewIntent — тёплый тап виджета не плодит активити (MainActivity:57-61, верно).

## Рекомендуемый порядок исправлений

_Архитектура — ПОСЛЕ критичных багов функциональности. Сначала остановить кровь (расписание/карта/контакты/виджеты), потом фундамент._

1. **ARC-05** (Application-класс) — дешёво, разблокирует ARC-02, чинит инициализацию без активити.
2. **ARC-03** (IO на main → produceState/VM) — быстрый фикс ANR, низкий риск.
3. **ARC-02** (AppGraph-синглтон репозиториев) — после ARC-05; устраняет неконсистентность teacherLessonDao + повторное чтение ассетов.
4. **dow-конверсия + prefs-обёртки** — вынести дубли (стиль, низкий риск, механика).
5. **ARC-01** (Navigation-Compose) — БОЛЬШАЯ отдельная волна, устраняет класс багов «состояние теряется» (включая корень карты). Только после стабилизации остального.
6. **ARC-04** (декомпозиция god-экранов) — попутно с волной Navigation.
7. **ARC-06** (events → Channel) — с Navigation.

## Вопрос для плана

Подогнать «под один стиль» = подключить **spotless/ktlint** в build.gradle (автоформат + rule «импорты вместо полных qualify», «без BOM»). Одна настройка → весь код приводится автоматически. См. 09-build-security-perf.
