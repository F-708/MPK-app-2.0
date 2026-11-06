# Архитектурный аудит: слои, связность, техдолг

**Проект:** `E:\MPK app\MPK-app-2.1`, пакет `com.politex`, ветка `v2.4-stable` (HEAD `415be8c`).
**Режим:** read-only. Код не менялся, gradle/тесты не запускались. Все цифры получены чтением файлов и подсчётом строк.
**Фокус:** что мешает безопасно починить 4 текущих бага (ДЗ, приём файлов, расписание, темы) и что даёт максимум эффекта при минимуме риска.

## 0. Сводка в цифрах

| Метрика | Значение |
|---|---|
| `app/src/main` | 178 `.kt`, 44 764 строки |
| из них `ui/` | 75 файлов, **26 445 строк (59 %)** |
| из них `ui/screens/` | 22 файла, **19 137 строк** (в среднем 870 строк на файл) |
| `data/` | 36 файлов, 6 242 строки |
| `util/` | 30 файлов, 5 151 строка |
| `widget/` | 12 файлов, 3 159 строк |
| `update/` | 9 файлов, 1 631 строка |
| `content/` | 6 файлов, 1 225 строк |
| `worker/` | 7 файлов, 586 строк |
| `ui/viewmodel/` | **1 файл, 318 строк** на всё приложение |
| Прочие source set'ы | `adminplus` 11 файлов / 2 278 строк, `owner` 6 / 654; **`admin` и `teacher` — 0 файлов** |
| Тесты | 228 файлов, 28 683 строки |
| `runCatching` в main | **182** вызова; `getOrDefault` — 36 |
| `Calendar.getInstance()` в main | **50** вызовов, из них **19** — в `data/` и `util/` |
| `TODO / FIXME / HACK / XXX` | **0** |

Последняя строка — не хорошая новость: в проекте нет ни одной пометки о риске. Весь риск «спрятан» в `runCatching` и в `CoroutineExceptionHandler { _, _ -> }`, то есть в местах, которые компилятор и линт считают нормой.

---

## 1. Карта слоёв

### 1.1 Как есть

```
                    ┌──────────────────────────────────────────────┐
                    │ MainActivity (204 стр.) — композиционный корень│
                    │ applyAppTheme, 2 канала уведомлений,          │
                    │ BellTimerService.sync, 5-6 WorkManager-цепочек│
                    │ по BuildConfig.FLAVOR, What's New, intake      │
                    └───────────────┬──────────────────────────────┘
                                    │ setContent
                    ┌───────────────▼──────────────────────────────┐
                    │ MainScreen (520) ── NavHost, bottom bar,      │
                    │ 10 маршрутов, 6 экранов получают repository   │
                    │ напрямую из AppViewModel                      │
                    └───┬───────────┬───────────┬──────────┬────────┘
                        │           │           │          │
        ┌───────────────▼──┐ ┌──────▼─────┐ ┌───▼──────┐ ┌─▼─────────────┐
        │ ScheduleScreen   │ │TasksScreen │ │Grades    │ │CollegeScreen  │
        │ 2126 стр.        │ │1541        │ │1285      │ │2131           │
        │ 1 composable =   │ │31 var      │ │DAO в     │ │OwnerOverrides │
        │ 691 строка       │ │+ raw DAO   │ │экране    │ │Store в экране │
        └───┬──────────┬───┘ └──┬─────────┘ └───┬──────┘ └───┬───────────┘
            │          │        │               │            │
   ┌────────▼───┐  ┌───▼────────▼───────────────▼────────────▼─────────┐
   │AppViewModel│  │        ГЛОБАЛЬНЫЕ СИНГЛТОНЫ (мимо слоёв)          │
   │318 стр.    │  │ AppGraph (lateinit) · ExternalMaterialIntake      │
   │6 flow + 6  │  │ paletteState / darkModeState · WidgetUpdateHelper │
   │функций     │  │ .widgetScope · OwnerOverridesStore.revision       │
   └────┬───────┘  │ ViewerMode · GradeDetailMode · FlavorOverlay      │
        │          │ CuratorStore · TeacherModeStore (revision)        │
        │          └───────────────┬───────────────────────────────────┘
        │                          │
   ┌────▼──────────────────────────▼───────────────────────────────────┐
   │ data/: ScheduleRepository (699), MpkNetworkClient,                │
   │        MpkScheduleParser (1516), TaskRepository (56), DAO, Room 11│
   └───────────────────────────┬──────────────────────────────────────┘
                               │
   ┌───────────────────────────▼───────────────────────────────────────┐
   │ Android: SharedPreferences (11 файлов), files/owner_overrides,     │
   │          files/materials, assets content-pack, WorkManager,        │
   │          AlarmManager, widgets (RemoteViews)                      │
   └───────────────────────────────────────────────────────────────────┘
```

Читать эту схему надо так: **слой представления — 59 % кода, слой приложения — 0,7 %**. Приложения как слоя фактически нет: один `AppViewModel` на 318 строк, который покрывает только расписание/ДЗ и отдаёт наружу `scheduleRepository` и `TaskRepository`. Остальные 9 экранов берут данные сами — из `AppGraph`, из `MpkDatabase`, из DAO, из статических store'ов.

### 1.2 Нарушения слоёв (все проверены по импортам и вызовам)

| № | Нарушение | Доказательство |
|---|---|---|
| Н1 | Экран читает БД напрямую, минуя repository/VM | `ScheduleScreen.kt:174-179` (`getAllLessonsForGroup().collectAsState`), `:289-297` (собирает `TaskRepository` из `MpkDatabase` внутри композиции), `TasksScreen.kt:137` (`taskReminderDao()` из БД рядом с `taskRepository` из параметра), `MaterialBaseScreen.kt:186`, `GradesScreen.kt:128` |
| Н2 | Экран сам вызывает `sync*` репозитория и держит свои `isSyncing/syncError` | `GroupScheduleScreen.kt:278-305` — полный дубль логики синка `AppViewModel.syncSchedule` внутри экрана, со своим `scope` и своими текстами ошибок |
| Н3 | UI импортирует content-слой | `CollegeScreen.kt:57-59` — `ContentAssets`, `ContentPackStore`, `OwnerOverridesStore` прямо в экране |
| Н4 | Глобальный сервис-локатор вместо DI: UI достаёт репозиторий из `AppGraph` | `CollegeMapScreen.kt:151`, `CollegeScreen.kt:1770`, `TeacherNav.kt:194,204,214,243` |
| Н5 | Данные приложения хранятся в prefs виджетов | `AppViewModel.kt:110-112` читает текущую группу из `WidgetUpdateHelper.getSelectedGroup` → prefs-файл `mpk_widget_prefs` (`WidgetUpdateHelper.kt:21,58-60`). «Текущая группа» — свойство приложения, а не виджета |
| Н6 | Навигационные константы живут в файле ViewModel | `AppViewModel.kt:47-57` (`ROUTE_*`) + `MainScreen.kt:92-157` (строители маршрутов, `backAction`, `ownerTabIndex`) — графа навигации как отдельной сущности нет |
| Н7 | Второй навигационный стек руками | `TeacherNav.kt:175-238` — `when (subPage)` вместо `NavHost`; back-стек, deep links и restore состояния не работают, `MainScreen` и `TeacherNav` дублируют bottom bar (`MainScreen.kt:225-281` vs `TeacherNav.kt:124-170`, вплоть до одинаковых `10.sp` и цветов) |
| Н8 | Логика с системными часами внутри data-слоя | `ScheduleRepository.kt:325` (`Calendar.getInstance()` в синке), `:650-656` (`parseCalendarDate`), `:125-138` (`isStaleDate`) — бизнес-правило «что сегодня» нельзя подменить в тесте |

### 1.3 Где на самом деле god object

`AppViewModel` — **не** god object по размеру: 318 строк, 6 публичных flow, 6 функций и 3 публичные зависимости (`scheduleRepository`, `taskRepository`, `diagnosticInfo`); зависит от него ровно один экран — `MainScreen` (`MainActivity.kt:91,108`). Проблема обратная: он **недо-объект**. Он отдаёт наружу `scheduleRepository`/`taskRepository` (`AppViewModel.kt:102-103`), и `MainScreen` раздаёт репозиторий шести маршрутам (`MainScreen.kt:342,380,409,430,482,505`), а три файла берут его из `AppGraph` вообще без VM.

God object здесь — **`MainScreen` + сами экраны**: 234 объявления локального состояния (`mutableStateOf/mutableIntStateOf`) в `ui/screens`, из них 32 в `SettingsScreen`, 31 в `TasksScreen`, 27 в `CollegeMapScreen`, 25 в `CollegeScreen`. Состояние приложения живёт в 22 файлах экранов, а не в слое состояния. Поэтому любой из 4 багов «плавает»: нет одного места, где можно посмотреть и починить.

---

## 2. Топ-10 техдолга

| # | Что | Где | Почему болит | Цена фикса | Приоритет |
|---|---|---|---|---|---|
| 1 | Защёлка приёма файлов `active` без владельца + молчаливые отказы | `ExternalMaterialIntake.kt:20-64`, `MaterialBaseScreen.kt:235-272`, `LessonsHubScreen.kt:64-78`, `MainActivity.kt:167-183,201-203` | `active` снимается только в 4 из 7 веток; уход с вкладки/смерть композиции оставляет защёлку навсегда → `claim()` больше не срабатывает, шаринг «прилипает». `fromIntent` глотает **все** исключения (`catch (_: Exception)`) и отдаёт `null` без сообщения. `handledIntakeKey` статичен и выставляется **до** проверки режима преподавателя | 1-2 дня: владелец + `reset()` + лог + один тест на «застрявший» сценарий | **P0** |
| 2 | `fallbackToDestructiveMigration()` на БД v11 | `MpkDatabase.kt:277-291` | Нарушен инвариант проекта (CLAUDE.md: «fallback тихо стирает данные»). Любое изменение схемы ДЗ/материалов без миграции = молчаливая потеря всех заданий, оценок, материалов, правок преподавателей | 30 минут: убрать строку 286; 10 migration-тестов уже есть | **P0** |
| 3 | `syncScheduleDetailed` — 155 строк, 5 mutable-флагов, глотание отмены | `ScheduleRepository.kt:320-475` | Внутри одной функции: probe, fetch, валидация «полный ли день», запись, teacher-sync, prune, решение об исходе. `runCatching` на `:395-402` съедает `CancellationException` (в соседнем месте `:453-455` её корректно перебрасывают — то есть правило есть, но не соблюдается). Блок `:412-424` дословно повторён в `:431-443`. Диагностика бага «расписание» = чтение 155 строк с пятью флагами | 2-3 дня: разрезать на `probe → fetch → decide → persist` с трассировкой; без смены поведения | **P0** |
| 4 | Тема — ambient-состояние процесса, а не значение | `AppTheme.kt:185-204`, `Color.kt:166-225`, `AppThemeStore.kt:14-18,29-33`, `SettingsScreen.kt:208-212,597,677`, `TeacherAppearance.kt:97` | 50 геттеров-обёрток над одним `var paletteState`; `AppThemeStore.load()` — **мутатор** (вызывает `applyDarkMode`), и его вызывают из `remember {}` в композиции, из виджетов (`BellCountdownWidgetProvider.kt:452`) и из уведомлений (`BellCountdownNotifier.kt:307`) на IO-потоке. Две локальные копии истины (`currentTheme` и `darkTheme`) синхронизируются руками | 1-2 дня: `ThemeController` + один источник; `load()` → чистый `read()` | **P1** |
| 5 | Два способа получить данные, нет single source of truth | `ScheduleScreen.kt:174-179,289-297`, `GroupScheduleScreen.kt:243-247`, `BellsScreen.kt:133`, `TasksScreen.kt:137-147` | Один и тот же экран читает данные то из VM, то из репозитория, то из DAO (fallback-ветки). Поведение в проде и в тесте идёт по разным путям. Любой фикс расписания надо применять в 3 местах | 2 дня: убрать fallback-ветки, оставить один путь | **P1** |
| 6 | `AppGraph` — `lateinit` сервис-локатор, доступный из UI | `MpkApplication.kt:12-33`, `CollegeMapScreen.kt:151`, `TeacherNav.kt:194-243` | `lateinit` без инициализации = `UninitializedPropertyAccessException` вместо понятной ошибки; экран нельзя отрисовать без `Application`. Отсюда все Compose-тесты идут через Robolectric | 1 день: передавать зависимости сверху, `AppGraph` оставить только точке входа | **P1** |
| 7 | `MainActivity.onCreate` — композиционный корень на 90 строк с ветвлением по строкам `FLAVOR` | `MainActivity.kt:53-133` | ~14 вызовов запуска подсистем в одном методе; `admin` и `teacher` — пустые source set'ы (0 файлов), вся разница в `if (BuildConfig.FLAVOR == "...")` (`MainActivity.kt:69,95,101,167,173`, `SettingsScreen.kt:390,424`, `CollegeScreen.kt:257`). Добавить flavor-поведение = добавить ещё одну строку в `if` | 1-2 дня: `AppStartup` + `FlavorPolicy` интерфейс | **P1** |
| 8 | `SettingsScreen` 1612 строк / 32 состояния / 16 приватных композаблов | `SettingsScreen.kt:151-445,591-732` | Тема, звонки, обновления, бэкап, карта, преподаватель, «сброс» — в одном файле. Блок темы уже держит две копии глобального состояния. Новая настройка темы = ещё один `remember` + ещё одна строка в `BackupManager` | 0,5 дня **только** на вынос темы (см. §4) | **P1** |
| 9 | Два «преподавательских» механизма и два навигатора | `BuildConfig.FLAVOR=="teacher"` vs `TeacherModeStore` (`TeacherModeStore.kt`), `TeacherNav.kt` vs `MainScreen.kt` | Режим преподавателя существует и как отдельный APK (пустой source set), и как рантайм-флаг внутри standard. Ветки расходятся: `tabsForMode` (`AppViewModel.kt:72`), `acceptIntake` (`MainActivity.kt:37-40`), `showSubgroupCard` (`SettingsScreen.kt:1608`). Фикс «приём файлов» обязан проверяться в обоих | 3-5 дней (не сейчас) | **P2** |
| 10 | Ошибки глотаются системно, логирования нет | 182 `runCatching`, 36 `getOrDefault`, `WidgetUpdateHelper.kt:28-30` (`CoroutineExceptionHandler { _, _ -> }`), `OwnerOverridesStore.kt:341-354,371-373` | `OwnerOverridesStore.load()` при битом `manifest.json` отдаёт `emptyMap()` и **кеширует пустоту** — все правки преподавателей исчезают до перезапуска, без следа в логах. То же в `persistLocked` (`:371`): `catch (_: Exception) { false }` → `upsertTeacher` вернёт `null`, UI промолчит | 1 день: один `AppLog` + замена `getOrDefault` на `onFailure{log}` в 4 критичных местах | **P1** |

---

## 3. Находки

### [P0] Приём файлов: защёлка `active` и молчаливые отказы
**Где:** `ExternalMaterialIntake.kt:14-64, 99-131`; `MainActivity.kt:167-183, 201-203`; `LessonsHubScreen.kt:64-78`; `MaterialBaseScreen.kt:235-272, 569-590`
**Проблема:** передача файла — трёхсторонний протокол через мутируемый синглтон: `offer()` в Activity → `claim()` в `LessonsHubScreen` → `finish()` в `MaterialBaseScreen`. `claim()` заблокирован, пока `active != null` (`:37`), а `finish()` вызывается только в ветках «слишком большой», «уже есть», «импортировано», «отмена диалога». Ветка «пользователь ушёл с вкладки, пока диалог выбора предмета открыт» или «композиция уничтожена при повороте/уходе в фон» не закрывает защёлку — и все последующие шаринги молча лежат в `pending`, при этом `hasWork=true` заставляет хаб прыгать на «Материалы» (`LessonsHubScreen.kt:64-78`) с пустым результатом. Плюс `fromIntent` (`:53-64`) ловит `Exception` и возвращает `null` — пользователь не узнает, почему файл не принят (APK? нет `content://`? битый URI? — все три случая дают одинаковую тишину). `handledIntakeKey` (`MainActivity.kt:202`) выставляется **до** `acceptIntake` (`:170-181`), то есть отказ в режиме преподавателя навсегда «съедает» этот файл.
**Root cause:** у процесса нет владельца. Состояние живёт в `object` без жизненного цикла, вызывающая сторона не обязана его разблокировать, а ошибки классифицируются как «нет материала» вместо «есть причина».
**Фикс:** (1) один владелец — `IntakeQueue` с явным `claim(ownerId)`/`release(ownerId)` и `DisposableEffect`-гарантией освобождения; (2) `sealed class IntakeResult { Accepted, Rejected(reason) }` вместо `null`; (3) ключ дедупликации выставлять после успешного `offer`, а не до проверки режима; (4) `Log.w` в местах отказа.
**Риск:** низкий — правка локальная (1 файл + 2 вызова). Основной риск — сломать существующие 2 теста (`ExternalMaterialIntakeTest`, `LessonsHubIntakeTest`), которые как раз вручную «сливают» очередь (`:50-53` и `:70-73`) — это и есть признак, что протокол держится на честном слове.

### [P0] Room: деструктивный fallback при версии 11
**Где:** `MpkDatabase.kt:44` (`version = 11`), `:277-291` (`.fallbackToDestructiveMigration()`), `:66-275` (10 миграций), тесты `MigrationV1ToV2Test … MigrationV10ToV11Test`
**Проблема:** при любом расхождении схемы (забыли миграцию, поменяли тип поля, добавили колонку в сущность ДЗ) Room не падает, а **удаляет базу** и создаёт пустую. В ней 11 сущностей (`:29-42`): `LessonEntity`, `StudentTaskEntity`, `TeacherLessonEntity`, `TaskReminderEntity`, `MaterialFolderEntity`, `MaterialItemEntity`, `MaterialBookmarkEntity`, `MaterialAnnotationEntity`, `GradeEntryEntity`, `GradeConfigEntity`, `TeacherOverrideEntity`. То есть фикс бага ДЗ, требующий поля в `StudentTaskEntity`, при ошибке в миграции стирает ДЗ у всех пользователей — и это не будет замечено ни в CI (тесты миграций проверяют только написанные миграции), ни в проде (никаких отчётов).
**Root cause:** страховка, оставленная на время разработки, дожила до релиза; инвариант проекта («явная Migration + MigrationVNToVN+1Test») кодом не защищён.
**Фикс:** удалить `.fallbackToDestructiveMigration()`. 10 migration-тестов уже написаны — падение будет громким и в тестах, и в проде.
**Риск:** минимальный по объёму, высокий по последствиям при бездействии. Единственный сценарий, где fallback сейчас «спасает», — пользователь с промежуточной сборкой; такие сборки внутренние.

### [P0] `syncScheduleDetailed`: пять флагов, проглоченная отмена, дублированный fetch
**Где:** `ScheduleRepository.kt:320-475`; вспомогательные эвристики `:28-73`; кэш неполных дней `:244-258`
**Проблема:** одна функция совмещает: выбор дат (`:332`), кэш-хит (`:335-347`), сетевой probe (`:354-366`), решение «какие дни качать» (`:370-383`), ранний выход по «известному неполному дню» (`:385-389`), загрузку по каталогу или по группе (`:391-424`), повторную загрузку, если группы нет в документе (`:430-443`), запись в БД (`:449-457`), teacher-sync (`:452-455`), prune (`:456`) и вычисление исхода (`:459-474`). Внутри — 5 изменяемых флагов (`probed`, `probeNetworkError`, `documentFound`, `targetDocumentFound`, `fetchFailed`) и аккумулятор `fetched`. `runCatching` на `:395-402` превращает **любое** исключение (включая `CancellationException` при уходе с экрана) в `fetchFailed = true` и продолжает писать частичные данные; в `:453-455` отмена корректно перебрасывается — то есть в одном файле два противоположных правила. Блок загрузки `:412-424` дословно повторён в `:431-443`.
**Root cause:** инкрементальная эвристика «день считается загруженным, если разобрано ≥ половины каталога» (`isFullDayDocument`, `:34-37`) вросла в середину сетевого метода. Она намеренная и покрыта тестом (`ScheduleCacheGateTest.kt:54-57` — «частичный документ не принимается»), но именно она решает, увидит ли пользователь расписание: если сайт публикует день в два приёма (сначала 1-2 курса), приложение честно скажет «расписания нет» и закэширует это как «неполный день».
**Фикс:** разрезать на 4 чистые ступени, каждая — отдельная функция с явным результатом: `probeDates()`, `fetchDays()`, `mergeFetch()` (вся политика «полный/неполный день» — здесь, с явным `SyncTrace`), `persistAndOutcome()`. Отмену не глотать. Дублированный блок свести в один вызов.
**Риск:** средний (поведение сети и кэша), но именно он делает баг «расписание» воспроизводимым и починяемым: сейчас диагностика требует чтения 155 строк, после разреза — 4 функции по 20-40 строк с тестами на каждую.

### [P1] Тема: ambient-состояние процесса + мутирующий `load()`
**Где:** `AppTheme.kt:185-204`, `Color.kt:166-225`, `AppThemeStore.kt:14-18,29-33`, `MainActivity.kt:57-61`, `SettingsScreen.kt:208-212,597,677`, `TeacherAppearance.kt:97`, `BellCountdownWidgetProvider.kt:452`, `BellCountdownNotifier.kt:307`, `CollegeLogo.kt:91`
**Проблема:** тема — не значение, а процесс-глобал: `private var appliedTheme`, `darkModeState`, `internal var paletteState` + **50** геттеров `val ColorX: Color get() = paletteState.x` (`Color.kt:168-225`). Следствия, все наблюдаемые в коде:
1. `AppThemeStore.load(context)` (`:14-18`) — не чтение, а **запись**: она вызывает `applyDarkMode`, то есть меняет глобальную палитру. Её вызывают из `remember {}` в композиции (`SettingsScreen.kt:208`, `TeacherAppearance.kt:97`), из провайдера виджета (`BellCountdownWidgetProvider.kt:452`) и из построения уведомления (`BellCountdownNotifier.kt:307`) — то есть Compose-состояние пишется из фонового потока виджет-ресивера.
2. В `SettingsScreen` две независимые копии истины: `currentTheme` (`:208`) и `darkTheme` внутри `AppearanceCard` (`:597`), синхронизируются руками (`:211`, `:658-659`). Новая настройка темы = третья копия.
3. `ThemeSwatch` рисует превью из данных (`theme.palette`, `:706,713`), а рамку — из глобала (`ColorBgMain`, `ColorBorderLight`, `:684,687`). Во время переключения карточка показывает смесь двух тем.
4. Проверить тему в тесте можно только через `applyAppTheme` (`AppThemeTest.kt:71`, `PromoShotsTest.kt:581-582`) — то есть тест меняет глобальное состояние всего процесса.
**Root cause:** тема была сделана «один раз при старте Activity»; `applyAppTheme` вызывается в `MainActivity.onCreate` до `setContent` (`MainActivity.kt:57-61`) и больше нигде, кроме `save`. Как только тему понадобилось менять на лету и рисовать в виджетах, глобал стал узким местом.
**Фикс:** ввести `ThemeController` (один `StateFlow<ThemeState>`), `AppThemeStore.load()` → чистый `read()`, а `apply` — отдельный вызов только из точки старта; `CompositionLocal` вместо 50 геттеров. Поэтапно: сначала `read()`/`apply()` разделить (полдня, без изменения поведения), потом `CompositionLocal`.
**Риск:** средний. Геттеры используются в том числе вне композиции (уведомления, виджеты, RemoteViews) — их нельзя просто перевести на `CompositionLocal`; нужен `paletteOf(theme, dark)` как чистая функция и два адаптера (Compose + RemoteViews).

### [P1] UI напрямую ходит в данные — два пути к одному и тому же
**Где:** `ScheduleScreen.kt:174-179, 289-297`; `GroupScheduleScreen.kt:243-247`; `TasksScreen.kt:137, 143-147`; `MaterialBaseScreen.kt:186`; `GradesScreen.kt:128`; `CollegeMapScreen.kt:151`; `CollegeScreen.kt:1770`
**Проблема:** `ScheduleScreen` принимает `groupLessonsFromVm: List<LessonEntity>?` и, если он `null`, сам подписывается на репозиторий (`:174-179`); то же для `pendingTasksFromVm` — иначе собирает `TaskRepository` из `MpkDatabase` прямо в композиции (`:289-297`). Это два разных пути исполнения в проде и в тестах: `UiRenderSmokeTest.kt:47` и `PromoShotsTest.kt:343` идут «без VM», реальный `MainScreen` — «с VM». Баг воспроизводится на одном пути и не воспроизводится на другом.
**Root cause:** экраны писались как самодостаточные (чтобы рендерились в тестах отдельно), а VM приделали сверху позже.
**Фикс:** оставить один вход — параметр обязателен, fallback удалить; тесты передают фейковый `StateFlow`, а не «ничего».
**Риск:** низкий технически, но придётся поправить 3-4 теста и промо-шоты. Делать **вместе** с фиксом расписания, иначе экран снова обрастёт ветками.

### [P1] Ошибки глотаются: 182 `runCatching`, 36 `getOrDefault`, нет логов
**Где:** `OwnerOverridesStore.kt:341-354` (`load` → `getOrDefault("")` → `emptyMap()` → кеш пустоты), `:356-373` (`persistLocked` → `catch (_: Exception) { false }`), `:317-321` (три `runCatching{}` подряд без реакции); `ExternalMaterialIntake.kt:53-64`; `WidgetUpdateHelper.kt:28-30` (`CoroutineExceptionHandler { _, _ -> }`); `MpkScheduleParser` — 18 `runCatching`
**Проблема:** самый дорогой случай — `OwnerOverridesStore`: битый `manifest.json` (обрыв записи, кончилось место) даёт `emptyMap()`, **кешируется** и правки владельца исчезают до перезапуска, без единой строки в логе; `upsertTeacher` в этом состоянии вернёт `null`, а UI (см. `CollegeScreen`/adminplus) просто ничего не покажет. Такую жалобу («я правил, а оно пропало») невозможно диагностировать по логам — их нет.
**Root cause:** `runCatching` выбран как универсальный способ «не падать», без различения «нет данных» и «сломалось».
**Фикс:** минимальный `AppLog` (обёртка над `android.util.Log`, `BuildConfig.DEBUG`-гейт для verbose) + правило: `runCatching` допустим только там, где вызывающий различает «пусто» и «ошибка». В `OwnerOverridesStore` — сохранять бэкап битого манифеста и не кешировать пустоту при ошибке парсинга.
**Риск:** низкий. Это подготовительный шаг: без него любой из 4 фиксов будет чиниться «на глаз».

### [P1] Общий «текущий контекст» размазан по 11 prefs-файлам
**Где:** `mpk_ui_prefs` пишут 5 разных объектов: `AppThemeStore.kt:10`, `ScheduleDisplayPrefs.kt:7`, `TeacherModeStore.kt:7`, `UiScaleStore.kt:17`, `UpdateChannelStore.kt:51`; `mpk_update_prefs` — 3: `AppUpdateWorker.kt:54`, `UpdateCheckController.kt:47`, `UpdatePendingStore.kt:8`; группа — в `mpk_widget_prefs` (`WidgetUpdateHelper.kt:21`); у `CollegeScreen.kt:1285` свой `mpk_prefs`; `BackupManager.kt:64-76` вручную перечисляет ключи настроек
**Проблема:** нет реестра настроек. Добавление одной настройки = новый объект + новый ключ + правка `BackupManager` (иначе настройка не переживает бэкап) + тест. Пять объектов делят один файл, но не знают друг о друге; любая миграция ключей — ручная. Для темы это уже привело к дублированию: `appTheme`/`appDark` лежат в `mpk_ui_prefs`, а `BellCountdownNotifier` держит свои `Style/Theme/Scope` отдельно.
**Root cause:** настройки добавлялись по одной, каждая — свой `object`.
**Фикс:** не переписывать всё. Ввести `Settings` с типизированными ключами и одним местом регистрации; новые настройки (в том числе темы) — только через него; `BackupManager` собирает ключи из реестра, а не руками.
**Риск:** низкий, если не трогать существующие ключи (иначе потеря настроек у пользователей).

### [P1] `SettingsScreen` 1612 строк: разбивать или нет
**Где:** `SettingsScreen.kt:151-1612` — 16 приватных композаблов (и 3 приватных функции-хелпера), 32 состояния, карточки: `TeacherModeCard:447`, `ScheduleSettingsCard:494`, `AppearanceCard:592`, `MapSettingsCard:735`, `ResetActionCard:767`, `AboutCard:820`, `UpdatesCard:869`, `BellCountdownSection:1063`, `BackupSectionsDialog:1392`
**Проблема:** файл держит 9 несвязанных доменов. Он же — единственное место, где тема меняется в standard-флейворе, и он уже содержит две копии состояния темы. При этом главный `SettingsScreen(...)` — 295 строк, `BellCountdownSection` — 200, `UpdatesCard` — 167.
**Root cause:** «ещё одна карточка» добавлялась в конец существующего файла 8 раз.
**Фикс:** **не** разбивать целиком (высокий риск конфликтов с параллельными правками). Вынести только то, что трогается: `AppearanceCard` + `ThemeSwatch` (`:591-732`) → `ui/settings/AppearanceSection.kt` с `ThemeController` вместо двух локальных копий. Это ~140 строк переноса без изменения поведения.
**Риск:** минимальный. Полная декомпозиция — отдельная задача на 2-3 дня и не нужна для 4 багов.

### [P1] `AppGraph` + `lateinit`: экран нельзя отрисовать без `Application`
**Где:** `MpkApplication.kt:12-33`; потребители в UI — `CollegeMapScreen.kt:151` (`remember { AppGraph.scheduleRepository }`), `CollegeScreen.kt:1770`, `TeacherNav.kt:194,204,214,243`
**Проблема:** `lateinit var scheduleRepository` без инициализации бросает `UninitializedPropertyAccessException` в произвольной точке композиции. Промах `AppGraph.init` (или тест без `Application`) даёт не «нет данных», а падение. Именно поэтому 228 тестов идут через Robolectric, а `WorkManagerTestApplication.kt` существует отдельно.
**Root cause:** сервис-локатор выбран вместо конструкторного внедрения; ViewModel его хотя бы инкапсулирует, а экраны достают напрямую.
**Фикс:** запретить `AppGraph` в `ui/` (детект — грепом в CI: `import com.politex.AppGraph` в `ui/` = падение сборки); репозиторий передаётся параметром, как уже сделано для 6 маршрутов.
**Риск:** низкий; 2 файла + 6 вызовов.

### [P2] Два экрана расписания дублируют конвейер «день/дата/слоты»
**Где:** `ScheduleScreen.kt:181-272` (saturdayLessons → daySlots → tomorrow → sameDay → leftIsToday → activeCal → subtitle → currentLessonNumber) и `GroupScheduleScreen.kt:248-341` — тот же конвейер почти строка в строку; таблицы дней `ScheduleScreen.kt:242` vs `GroupScheduleScreen.kt:713`; третий вариант в `BellsScreen.kt`
**Проблема:** ~90 строк логики «какой сегодня день и какой урок идёт» существует в двух копиях (плюс `BellsScreen` считает своё). Уже разошлись: `schoolDayEnded` есть только в `ScheduleScreen` (`:400-413`), `pulseLessonNumber` считается по-разному (`:284-287` — через `pulseTargetLesson`, `GroupScheduleScreen.kt:338-341` — «первый урок с кабинетом»). Фикс «не тот день/не та пара» надо делать дважды.
**Root cause:** `GroupScheduleScreen` писался копированием `ScheduleScreen` под чужую группу.
**Фикс:** вынести в `ScheduleDayState` (`@Composable fun rememberScheduleDay(lessons, groupInfo)`), обе площадки используют его.
**Риск:** средний (визуальные регрессии), поэтому — после фикса расписания, а не до.

### [P2] Две системы «преподавателя» и второй навигатор
**Где:** `AppViewModel.kt:72` (`tabsForMode`), `MainActivity.kt:37-40,69,95,101,167` (`acceptIntake`, `FLAVOR == "teacher"`), `TeacherNav.kt:124-238`, `TeacherModeStore.kt`, пустые source set'ы `app/src/admin`, `app/src/teacher`
**Проблема:** «преподаватель» — одновременно отдельный APK (source set пуст, вся разница — строковые проверки в `main`) и рантайм-флаг внутри standard (`TeacherModeStore`, `revision`). Видимость разделов задаётся тремя разными способами: `tabsForMode`, `showSubgroupCard/showTeacherModeExit` (`SettingsScreen.kt:1608-1610`), `acceptIntake`. Навигация в teacher-флейворе — ручной `when(subPage)` без back-стека.
**Root cause:** teacher-приложение влили в тот же модуль, не выделив его в отдельный граф.
**Фикс:** (позже) явный `FlavorPolicy` (интерфейс, 2 реализации) вместо строковых `if`; навигацию teacher перевести на `NavHost`.
**Риск:** высокий. Не трогать в рамках 4 багов, но помнить: любой фикс приёма файлов затрагивает `acceptIntake`, то есть оба механизма.

### [P2] Пятьдесят `Calendar.getInstance()` и отсутствие часов
**Где:** 50 вызовов; вне UI — `ScheduleRepository.kt:325,650-656`, `util/HomeworkMarkers.kt` (2), `util/BellCountdownNotifier.kt` (5), `widget/*` (9); в UI — `TodayProvider.kt:20,24,33,37,46`
**Проблема:** «сегодня» вычисляется из системных часов в 50 точках, включая data-слой и виджеты. Тест «ночью перед праздником» или «в 00:01» невозможен без Robolectric/подмены таймзоны; поэтому граничные баги расписания и звонков ловятся только руками. `TodayProvider` (`rememberToday`/`rememberNowMinutes`) — шаг в правильную сторону, но это UI-утилита: у data-слоя доступа к ней нет, и каждый экран заводит свой минутный таймер (`:30-37, 47-54`).
**Root cause:** часы никогда не были абстракцией; `java.util.Calendar` течёт из модели в UI.
**Фикс:** `interface Clock { fun today(): LocalDate; fun nowMinutes(): Int }` с `SystemClock` и `FixedClock`; прокинуть в `ScheduleRepository` и `HomeworkMarkers`. Не переписывать все 50 — начать с `data/` (8 вызовов).
**Риск:** низкий, эффект для тестируемости расписания — высокий.

### [P2] 228 тестов, но состояние между ними общее
**Где:** `ExternalMaterialIntakeTest.kt:50-53` и `LessonsHubIntakeTest.kt:70-73` — ручной дренаж очереди перед тестом; `AppThemeTest.kt:83`, `DarkThemeTest.kt:20,133`, `PromoShotsTest.kt:586` — ручной сброс темы; `AppThemeStoreDarkTest.kt:23-24` — сброс и палитры, и prefs
**Проблема:** тесты вынуждены вручную возвращать глобальные синглтоны в исходное состояние. Это работает, пока тесты не идут параллельно и пока никто не забыл сброс; цена — хрупкость и «flaky по порядку». Именно поэтому баг приёма файлов и темы трудно закрыть тестом-регрессией: тест должен воспроизвести «застрявшую» защёлку, а не просто пройти happy-path.
**Root cause:** статические глобалы (см. Н4, P1-находки 4 и 6).
**Фикс:** не переписывать 228 тестов. Ввести `@Before fun resetAppState()` в общий базовый класс для 4 затронутых областей (intake/theme/schedule/tasks) — один файл, явный список глобалов.
**Риск:** низкий.

### [P3] Мелкое, но показательное
- `TeacherNav.kt:193` — `bellsStubGroup`: экран `GroupScheduleRoute` требует `GroupInfo`, который ему не нужен; в teacher-навигации передаётся заглушка.
- `ExternalMaterialIntake.kt:99-113` — определение APK тремя способами (mime, расширение URI, `OpenableColumns`) — логика верная, но дублируется в двух ветках `parse` (`:74`, `:84`).
- `LessonEntity.kt:36-47` и `SubjectDisplayName.kt:10` — два пути к короткому имени предмета; `SubjectFormatter` при этом единственный источник формул (это хорошо).
- `MaterialImporter.kt:244-250` — дедуп по `contentHash`, а колонка добавлена в миграции 10→11 со `DEFAULT ''` (`MpkDatabase.kt:270-275`): у материалов, импортированных до v11, хеш пустой, поэтому «уже есть такой файл» для них не срабатывает — повторный шаринг создаёт дубликат.
- `CollegeScreen.kt:1285` — приватный `PREFS = "mpk_prefs"` внутри экрана на 2131 строку: UI владеет своим хранилищем.
- `FlavorOverlay.kt:5-11` — `@Volatile var` с `@Composable`-лямбдами, которые выставляют `OwnerApplication`/`AdminPlusApplication`; статическая точка расширения UI, невидимая для графа зависимостей.
- Ноль `TODO/FIXME`: единственный «маркер» риска в проекте — `runCatching`.

---

## 4. Что сделать перед 4 фиксами

Порядок важен: первые три пункта — общие, они окупаются на всех четырёх багах.

**Шаг 0. Убрать деструктивный fallback БД (30 минут, P0).**
`MpkDatabase.kt:286` — удалить `.fallbackToDestructiveMigration()`. Прогнать `testStandardDebugUnitTest`: 10 migration-тестов уже есть. Без этого любой фикс ДЗ с изменением схемы — лотерея на данных пользователя. Делать **до** ветки с фиксом ДЗ, отдельным коммитом.

**Шаг 1. Дать себе возможность видеть ошибки (0,5 дня).**
Один `AppLog` + `onFailure { log(...) }` в 4 местах, где сейчас глухо: `ExternalMaterialIntake.fromIntent` (`:53-64`), `OwnerOverridesStore.load` (`:341-354`), `ScheduleRepository` `:395-402`, `MaterialBaseScreen` `:251-253`. Плюс правило: `runCatching` не глотает `CancellationException`. Это не рефакторинг, это приборная панель — без неё следующие четыре фикса будут проверяться глазами.

**Шаг 2. Зафиксировать контракты четырёх областей тестами-«характеристиками» (1 день).**
Не новые фичи, а фиксация текущего поведения, чтобы фикс было видно в диффе:
- приём файлов: сценарий «пользователь ушёл с вкладки, пока открыт диалог предмета» → сейчас защёлка не снимается (падающий тест станет зелёным после фикса);
- ДЗ: `TaskRepository` + `reminderDao` как одна операция (сейчас `TasksScreen.kt:137` и `:191-204` пишут в два места без транзакции);
- расписание: 4 сценария `syncScheduleDetailed` по датам/каталогу — на чистом `Clock` и фейковом `MpkNetworkClient` (сейчас невозможно: `:325`);
- темы: `applyAppTheme` → палитра, и **отдельно** `AppThemeStore.load()` не должен менять палитру.

**Шаг 3. По каждому багу — минимальный «шов», без смены поведения.**

| Баг | Что подготовить | Файлы | Объём |
|---|---|---|---|
| Приём файлов | `IntakeResult` (Accepted/Rejected(reason)) + владелец с `release()` + перенос ключа дедупликации | `ExternalMaterialIntake.kt`, `MainActivity.kt:167-183`, `MaterialBaseScreen.kt:235-272` | 1-2 дня |
| ДЗ | один транзакционный метод «сохранить задание + напоминания»; один владелец `TaskRepository` (сейчас 2 экземпляра: `AppViewModel.kt:103` и `ScheduleScreen.kt:291-296`) | `TaskRepository.kt`, `TasksScreen.kt:137,191-204`, `AppViewModel.kt:103` | 1 день |
| Расписание | разрезать `syncScheduleDetailed` на `probe/fetch/decide/persist` + `Clock` в конструкторе репозитория | `ScheduleRepository.kt:320-475`, `:125-138`, `:650-656` | 2-3 дня |
| Темы | `AppThemeStore.load()` → чистый `read()`; `apply*` только из точки старта; вынести `AppearanceCard`+`ThemeSwatch` в отдельный файл с `ThemeController` | `AppThemeStore.kt`, `AppTheme.kt:193-204`, `SettingsScreen.kt:208-212,591-732`, `TeacherAppearance.kt:97`, `BellCountdownWidgetProvider.kt:452`, `BellCountdownNotifier.kt:307` | 1-2 дня |

**Чего НЕ делать в рамках этих четырёх фиксов:** не переписывать `ScheduleScreen`/`CollegeScreen`, не сливать `TeacherNav` с `MainScreen`, не вводить DI-фреймворк, не дробить `SettingsScreen` целиком, не трогать парсер (`MpkScheduleParser`, 1516 строк) — он самый протестированный кусок (18 `runCatching`, но и десяток тестов разбора: `AllGroupsParseTest`, `ParserAnchorTest`, `RealSiteDocParserTest`, `X1DocumentDateTest`) и не является причиной ни одного из 4 багов.

---

## 5. Что не трогать

| Область | Почему |
|---|---|
| `data/network/MpkScheduleParser.kt` (1516 строк) | Самая покрытая тестами часть (`AllGroupsParseTest`, `ParserAnchorTest`, `RealSiteDocParserTest`, `X1DocumentDateTest`, `Step5OfficialDocTest`, `ScheduleParseBenchTest`). Читается плохо из-за размера, но это чистые функции без состояния. Любой рефакторинг здесь — риск сломать разбор живого сайта без выигрыша для 4 багов |
| 10 Room-миграций (`MpkDatabase.kt:66-275`) | Парные тесты `MigrationV1ToV2Test … MigrationV10ToV11Test` — редкий пример дисциплины в этом проекте. Единственное, что нужно, — убрать fallback (шаг 0), а не переписывать миграции |
| `SubjectFormatter` + `LessonEntity.shortSubjectName` | Единственный источник сокращений предмета, используется парсером, виджетами, уведомлениями, ДЗ (`HomeworkMarkers.kt:73-96`). `SubjectDisplayName.kt` — тонкая обёртка, дублированием не является |
| `widget/` (3159 строк, 7 провайдеров) | Отдельный контур (RemoteViews, свои prefs, свои таймеры). Трогать только в части чтения темы (`BellCountdownWidgetProvider.kt:452`) — не переписывать |
| `CollegeRouteFinder` + `CollegeMapData` (835 + 187 строк) | Чистая геометрия/граф, покрыта десятком тестов маршрутов (`CollegeRouteFinderTest`, `RealMapMultiFloorRouteTest`, `RouteOrthogonalityTest`, `SimplifyRouteTest` и др.). Не имеет отношения к 4 багам |
| `update/` (1631 строка) | Самодостаточный контур обновлений со своими контроллерами и тестами. Трогать только если фикс темы требует `UpdateCheckController` — не требует |
| `BackupManager` | Работает и покрыт (`BackupRoundTripTest`, `BackupSectionsTest`). Менять только при добавлении новой настройки темы — и тогда одной строкой в реестре ключей, а не рефакторингом |
| Промо/Remotion (`promo/`), `_reports/*` других агентов | Живая параллельная сессия и чужие отчёты; в этом аудите не читались и не изменялись |

---

## Приложение: чем мерил

- Строки — построчный подсчёт файлов `app/src/main/java/com/politex/**/*.kt` (178 файлов, 44 764 строки); цифры в тексте — фактические, включая файлы >700 строк (`CollegeScreen` 2131, `ScheduleScreen` 2126, `SettingsScreen` 1612, `CollegeMapScreen` 1609, `TasksScreen` 1541, `MpkScheduleParser` 1516, `GradesScreen` 1285, `MaterialBaseScreen` 1221, `PdfViewer` 1198, `GroupScheduleScreen` 1047).
- Размеры композаблов — по номерам строк объявлений: `ScheduleScreen(...)` `:129-820` (691 строка), `GroupScheduleScreen(...)` `:198-714` (516), `TasksScreen(...)` `:126-516` (390), `MySpecialtyPage` `:502-811` (310), `TeacherScheduleTab` `:1703-2015` (312), `SettingsScreen(...)` `:151-445` (295).
- Нарушения слоёв — по фактическим `import com.politex.data.*` / `content.*` в `ui/` и по вызовам `MpkDatabase.getInstance`, `AppGraph.*`.
- Глобалы — по объявлениям `object`/`var`/`mutableStateOf` вне `data`: `ExternalMaterialIntake`, `paletteState`, `darkModeState`, `appliedTheme`, `WidgetUpdateHelper.widgetScope`, `OwnerOverridesStore.revision`, `ViewerMode`, `GradeDetailMode`, `FlavorOverlay`, `AppGraph`.
- `TODO/FIXME/HACK/XXX` — поиск по `app/src/main`: 0 совпадений.