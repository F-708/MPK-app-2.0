# Ревью: тесты и CI-гейты — «Мой Политех» (v2.4-stable)

Дата: 2026-11-06. Режим: read-only (чтение кода, git, конфигов). Gradle/CI не запускались.
Ревьюер: «Тесты и CI».

**Снимок цифрами (факты из дерева):**

| Метрика | Значение |
|---|---|
| Тестовых `.kt` файлов | **228** |
| Всего `@Test` | **1677** |
| Классов на `RobolectricTestRunner` | **95** |
| Чисто-JVM классов (без Robolectric) | **133** |
| Классов, снимающих PNG через Roborazzi | **5** |
| Классов в корневом пакете `com.politex` | **169 из 228** |
| Классов с именами волн/PR (`Step3…`, `X5X34…`) | **22** |
| Классов, работающих с файловым синглтоном БД | **21** |
| Классов на in-memory БД | **13** |
| Workflow-файлов | **1** (`build-apk.yml`) |
| `robolectric.properties` | **отсутствует** |
| Голденов Roborazzi в git | **0** |

---

## Карта тестов

| Пакет | Файлов | Что покрывают |
|---|---|---|
| `com.politex` (корень) | **169** | Всё сразу: расписание (`Schedule*`, `Sync*`, `DaySlotsTest`, `DateRestampTest`), преподаватели (`Teacher*`), звонки (`Bell*`), виджеты (`Widget*`), обновления (`Update*`, `update/`), ДЗ и оценки (`Homework*`, `Grade*`, `Subject*`, `Task*`), карта (`Map*`, `Route*`, `Room*`, `Gg89RoomsTest`), документы (`Doc*`, `Docx*`, `Viewer*`, `BinaryTextExtractTest`), навигация (`Nav*`, `N15*`, `N1N4N18*`, `T8*`), темы (`AppTheme*`, `DarkThemeTest`, `PlumThemeTest`, `ThemeTokensContrastTest`), миграции (`MigrationV1ToV2` … `V10ToV11`), бэкап (`Backup*`), шаги волн (`Step1LogicTest` … `Step7BannerAndIconTest`) |
| `widget` | 10 | Секундомеры, минутный/секундный тик, `AlarmManager`-цепочки, `RemoteViews`-совместимость, предпочтения, очистка, `TimeChangedReceiver`, подгруппы |
| `update` | 13 | Проверка версий, каналы stable/preview, `ETag`-мета, валидация пакета, прогресс, состояние установки, Worker-уведомление, формула versionCode из тега |
| `content` | 10 | Контент-пакет: валидатор, резолвер ассетов, переключение store, предикат Worker'а, стартовая проверка, owner-overrides, ресайз фото |
| `data/model` | 8 | Карта колледжа: поиск маршрута, многоэтажность, ортогональность, упрощение, масштаб номеров |
| `util` | 4 | Debounce стартовой проверки, интервалы каналов обновления, pending-store, тестовый `WorkManager` |
| `promo` | 3 | Roborazzi-снимки экранов и логотипа для промо-видео |
| `teacher` | 3 | Teacher-расписание, строки лидов, стиль звонков, снимки |
| `ui/changelog` | 3 | Окно «Что нового»: store, UI, подключение к запуску |
| `ui/screens` | 2 | Построение маршрута, резолв источника маршрута |
| `data/repository` | 2 | `ScheduleArchiveTest` (`isStaleDate`), `SyncDateFilterTest` (фильтр дат синка) |
| `dataeditor` | 1 | Логика редактора преподавателей |

**Вывод по карте:** покрытие по «железу» приложения широкое (расписание, виджеты, апдейтер, карта, миграции), но **плоское**: 74 % файлов лежат в одном пакете, доменного деления нет. Домены «ДЗ на расписании» и «темы» представлены отдельными точечными классами, а не набором сценариев.

---

## Находки

### [P0] CI не запускается ни на рабочей ветке, ни на PR / `.github/workflows/build-apk.yml:5-7`
**Проблема.** Триггер объявлен как `on.push.branches: [ main, master ]` + `workflow_dispatch`. Рабочая ветка репозитория — `v2.4-stable` (`git branch --show-current`). `pull_request` не объявлен вообще. Значит единственный тестовый гейт в проекте не выполняется **ни на одном** реальном изменении: ни на push в рабочую ветку, ни на PR.
**Фикс.** Добавить рабочую ветку в `push` и включить PR:
```yaml
on:
  push:
    branches: [ v2.4-stable, main, master ]
  pull_request:
  workflow_dispatch:
```
**Как проверить.** `git branch --show-current` → `v2.4-stable`. В GitHub Actions `gh run list --limit 20` — ни одного run'а, привязанного к push в эту ветку.

---

### [P0] Roborazzi-«тесты» ничего не проверяют: 5 классов снимают PNG, сравнения с эталоном нет / `app/build.gradle.kts:7,258-261`, `promo/PromoShotsTest.kt:621`, `MapScreenShotTest.kt:35`, `promo/CollegeLogoShotTest.kt:84,101`, `promo/UpdateShotsTest.kt:141,158`, `teacher/TeacherShotsTest.kt:216`
**Проблема.** Плагин `roborazzi` подключён, три зависимости на месте, `captureRoboImage(...)` вызывается в 5 классах — но:
- ни `roborazzi { outputDir = ... }`, ни `roborazzi.record`/`roborazzi.verify` не сконфигурированы (`grep -i roborazzi gradle.properties app/build.gradle.kts build.gradle.kts` даёт только объявления плагина и зависимостей);
- гейт `:app:testStandardDebugUnitTest` запускается без `-Proborazzi.test.record/verify`, а без этих флагов `captureRoboImage` — no-op;
- эталонов в git нет: `git ls-files "app/src/test/**/*.png"` пуст; единственный `app/src/test/screenshots/greeting.png` — untracked остаток демо-примера.

Итог: 5 классов (≈25 снимков) в гейте не проверяют пиксели вообще. Регрессия вёрстки (в т.ч. по темам и по бейджу ДЗ) гейтом не ловится.
**Фикс.** Явно задать вывод и включить верификацию отдельной задачей:
```kotlin
roborazzi { outputDir.set(file("src/test/screenshots")) }
```
```yaml
- name: Roborazzi verify
  run: ./gradlew :app:verifyRoborazziStandardDebugUnitTest -Proborazzi.test.verify=true
```
Эталоны закоммитить. Снимки, которые нужны как *артефакты для промо* (`promo/shots`), вынести из тестового гейта в отдельную задачу.
**Как проверить.** Поменять один цвет в `AppTheme.COLLEGE`, прогнать `verifyRoborazzi` — сейчас прогон зелёный (неверно), после фикса — красный.

---

### [P0] `fallbackToDestructiveMigration()` в проде и ни одного теста-стража / `app/src/main/java/com/politex/data/local/MpkDatabase.kt:286`
**Проблема.** БД объявлена `version = 11`, все 10 миграций зарегистрированы (строка 285), но следом стоит `.fallbackToDestructiveMigration()` (286). Это прямо нарушает инвариант проекта («`fallbackToDestructiveMigration` тихо стирает данные»). В тестах **нет ни одной проверки**, запрещающей этот вызов: `grep fallbackToDestructive app/src/test` — 0 совпадений. 11 миграционных тестов руками собирают DDL и проверяют данные, но при удалении миграции упадут не с «миграция отсутствует», а с `assertEquals("задания пережили миграцию", 1, tasks.size)` → `0` — диагностика обманывает.
**Фикс.** Тест-страж, читающий билдер: `DatabaseMigrationGuardTest` — проверяет, что для каждой пары версий `1→…→11` в `addMigrations(...)` есть `Migration`, и что `fallbackToDestructiveMigration` в исходнике отсутствует (или что `openHelper` без миграции бросает `IllegalStateException`, а не пересоздаёт БД).
**Как проверить.** Убрать `migration9To10` из `addMigrations` → страж падает с внятным сообщением; сейчас падает `MigrationV9ToV10Test` с «заданий 0».

---

### [P1] Нет `robolectric.properties`: три разных SDK в одном прогоне, 36 классов проверяют не тот SDK, что поставляется
**Где.** `app/src/test/resources/` — файла `robolectric.properties` нет. Фактическое распределение `@Config(sdk=…)`:

| `sdk` | Классов | Следствие |
|---|---|---|
| `[35]` | 36 | SDK 35, приложение собирается с `targetSdk = 36` |
| `[30, 31, 33, 35]` | 6 | каждая `@Test` выполняется **4 раза** (`WidgetSecondsTickTest`, `TeacherModeWidgetsTest`, `TimeChangedReceiverTest`, `WidgetPrefsClearTest`, `WidgetRemoteViewsCompatTest`, `NowNextEmptyStateRecoveryTest`) |
| не указан | 53 | неявный `targetSdk` = 36 |

**Проблема.** Три разных поведения Android в одном гейте; 36 классов валидируют не тот API-уровень, который уходит в релиз; 6 классов дают ×4 прогонов и ×4 инициализации sandbox'а. Единой точки правки нет — смена SDK требует правки 42 файлов.
**Фикс.** `app/src/test/resources/robolectric.properties`:
```properties
sdk=36
qualifiers=w411dp-h891dp-xxhdpi
```
`@Config(sdk = [30, 31, 33, 35])` оставить только у 6 виджет-классов, с комментарием «проверяем совместимость AlarmManager/RemoteViews на старых API»; у остальных `@Config` удалить.
**Как проверить.** `./gradlew :app:testStandardDebugUnitTest --tests "com.politex.WidgetSecondsTickTest"` — в отчёте 29 тестов × 4 конфигурации; после фикса — 29 × 1 + отдельный матричный класс.

---

### [P1] 21 класс работает с файловым синглтоном БД — изоляции нет, миграционные тесты сбрасывают его рефлексией
**Где.** `MpkDatabase.getInstance(context)` в 21 классе (`BackupRoundTripTest`, `BackupSectionsTest`, `LessonsHubIntakeTest`, `LocaleRootDateTest`, `MaterialTextRoutingTest`, `MigrationV3ToV4Test` … `MigrationV10ToV11Test`, `TeacherModeBackupTest`, `TeacherModeComposeTest`, `UiRenderSmokeTest`, `W16PdfReaderModeTest`, `promo/PromoShotsTest`, `teacher/TeacherShotsTest`, `NowNextEmptyStateRecoveryTest`, `TeacherModeWidgetsTest`). 13 классов используют `inMemoryDatabaseBuilder` — единой стратегии нет.
**Проблема.** `instance` — статическое поле; sandbox-загрузчик Robolectric переиспользуется между классами с одинаковым `@Config`, поэтому открытая БД переживает границу тест-класса. Отсюда `resetSingleton()` в `MigrationV10ToV11Test:229-242`, который **глотает все исключения** (`catch (_: Exception) {}`): если рефлексия не сработала, тест продолжит работать со старой БД и «пройдёт».
**Фикс.** Один `@Rule` (`TestDbRule`) с `inMemoryDatabaseBuilder` + `close()`; в migration-тестах — `MigrationTestHelper` + сверка с `app/schemas/*.json` вместо ручного DDL. Запретить `getInstance` в тестах grep-гейтом в CI.
**Как проверить.** Прогнать `MigrationV10ToV11Test` дважды в одном форке / прогнать классы в обратном алфавитном порядке — сейчас результат зависит от порядка.

---

### [P1] Реальное время вместо инъектируемых часов; `TodayProvider` в main не используется ни разу
**Где.** `app/src/main/java/com/politex/ui/util/TodayProvider.kt` (есть, но экспортирует только чистые `dayKeyOf`/`minutesOfDay`, часов не даёт). В main — **0 вхождений** `TodayProvider` (grep по `app/src/main` пуст), он используется только из `TodayProviderTest.kt`. При этом:
- `ScheduleRepository.syncScheduleDetailed` — `val today = Calendar.getInstance()` (`ScheduleRepository.kt:325`);
- `HomeworkMarkers.lessonPassed` / границы дня — `Calendar.getInstance()` (`HomeworkMarkers.kt:106,130`);
- `SyncCacheFirstTest.kt:180,202,218,233`, `SyncDateFilterTest.kt:135,178` — `Calendar.getInstance()` в самих тестах.

Тесты обходят это точечно: `WidgetData.calendarOverride` (только виджеты) и подмена `TimeZone.setDefault` на анонимный подкласс с `delta`-смещением (`promo/PromoShotsTest.kt:133-173`) — то есть глобальная мутация `TimeZone` на весь JVM.
**Проблема.** Любой тест «сегодня/устарело» зависит от дня запуска, дня недели и момента полуночи. Это корень флаков расписания и невозможности писать детерминированные тесты под фикс №3.
**Фикс.** Ввести `interface Clock { fun now(): Calendar }` (или `TimeSource`) и протянуть в `ScheduleRepository`, `HomeworkMarkers`, `WidgetData`; в тестах — `FixedClock(2026, 10, 5, 10, 30)`. `PromoShotsTest.freezeAtTuesday1030` после этого удаляется целиком.
**Как проверить.** `grep -c "Calendar.getInstance()" app/src/main/java/com/politex` — сейчас >0 в 6 модулях; цель — только внутри `SystemClock`.

---

### [P1] Гейт покрывает только flavor `standard`; `admin` / `adminplus` / `teacher` не тестируются
**Где.** `build-apk.yml:36` — `:app:testStandardDebugUnitTest`. При этом `app/build.gradle.kts` объявляет 4 flavor'а и подключает `src/testOwner/java` к `testAdmin`/`testAdminplus` (строки ~166-172), а `src/owner/java` (AccessCode, gate) и `src/admin/java` (база учащихся) в `standard` вообще не входят.
**Проблема.** Код доступа admin, гейт и база учащихся не покрыты ничем; `testOwner`-исходники подключены, но их прогон никем не инициируется.
**Фикс.** Минимум — второй шаг `:app:testAdminplusDebugUnitTest` (самый полный sourceSet) в CI; в идеале матрица `[standard, adminplus, teacher]`.
**Как проверить.** `./gradlew tasks --all | grep -i "test.*UnitTest"` — 8 задач, в CI вызывается 1.

---

### [P2] 169 из 228 файлов — в корневом пакете `com.politex`
**Где.** `app/src/test/java/com/politex/*.kt`.
**Проблема.** `ScheduleScreen`-тесты, `Widget*`, `Update*`, `Teacher*`, миграции и темы лежат в одной куче. Найти «все тесты расписания» нельзя ни по имени пакета, ни по каталогу; новые тесты по инерции кладутся туда же.
**Фикс.** Переносить по домену при каждой правке (`schedule/`, `homework/`, `theme/`, `widget/`, `migration/`); новые классы сразу в доменный пакет.
**Как проверить.** `Get-ChildItem app/src/test/java/com/politex -Filter *.kt | Measure-Object` — 169.

---

### [P2] 22 класса названы по волнам/PR, а не по поведению
**Где.** `Step1LogicTest`, `Step2UiLogicTest`, `Step3NetworkArchiveTest`, `Step4BugfixTest`, `Step5OfficialDocTest`, `Step6WidgetsAndWorkerTest`, `Step7BannerAndIconTest`, `R3WidgetFixesTest`, `W1LiveFixtureDiagnosticsTest`, `W1SyncTargetDateTest`, `W16PdfReaderModeTest`, `X1DocumentDateTest`, `X5X34AnnotationToolsTest`, `Jj1SubjectsMatchTrapTest`, `K4GroupOnboardingUiTest`, `K4PickerLettersScrollTest`, `Ll1GroupConfirmTest`, `Ll1GroupDialogUiTest`, `N15BackStackTest`, `N1N4N18LogicTest`, `T8NavigationScrollTest`.
**Проблема.** `Step4BugfixTest` не говорит ничего; при падении нельзя понять, что сломалось. Часть классов — солянка из разнородных кейсов (по имени волны).
**Фикс.** Переименовывать при касании: `Step7BannerAndIconTest` → `BannerThemeTest` + `LauncherIconTest`. Новые — только по поведению.
**Как проверить.** `Get-ChildItem … | Where BaseName -match '^(Step\d|R\d|W\d|X\d|Jj|K\d|Ll|N\d|T\d)'` — 22.

---

### [P2] `ScheduleParseBenchTest` — бенчмарк без единого `assert` в тестовом гейте
**Где.** `app/src/test/java/com/politex/ScheduleParseBenchTest.kt:54-93`.
**Проблема.** Один `@Test`, ноль проверок, ноль `assert*`. Гоняет `parseFileAllGroups` ×3 по каталогу групп (57+ позиций), `parseFileRawTexts` ×3, `TeacherScheduleParser.parse` ×3 на реальных `.doc` из фикстур и пишет `build/bench-parse.txt`. В гейте это чистые потерянные минуты CI и zero signal.
**Фикс.** `@Ignore("бенчмарк, запускается вручную")` либо перенос в отдельный sourceSet `src/bench`; результат — в артефакт, не в гейт.
**Как проверить.** В XML-отчёте у теста нет ни одного `failure`, но и ни одного утверждения — только `system-out` с таблицей `avg/best ms`.

---

### [P2] `UiRenderSmokeTest` — 6 тестов без `assert`
**Где.** `app/src/test/java/com/politex/UiRenderSmokeTest.kt:32-96`.
**Проблема.** Все шесть методов — `setContent { … }; waitForIdle()`. Проверяется только «не бросило исключение». Для краш-детектора это оправдано, но: (а) ни один экран не проверяет контент, (б) используется файловая БД-синглтон (см. P1), (в) `ScheduleRepository(lessonDao)` создан без сетевого клиента — половина экрана не задействована.
**Фикс.** Добавить по 1–2 `onNodeWithText/onNodeWithTag(...).assertExists()` на ключевой узел каждого экрана; тяжёлые экраны перевести на Roborazzi-голдены.
**Как проверить.** `Select-String UiRenderSmokeTest.kt -Pattern "assert"` — 0 совпадений.

---

### [P2] Скриншот-тесты пишут в отслеживаемый git каталог `promo/shots`
**Где.** `promo/PromoShotsTest.kt:110-114` (`File(root, "promo/shots")`), `promo/CollegeLogoShotTest.kt`, `promo/UpdateShotsTest.kt`, `teacher/TeacherShotsTest.kt` — тот же `outDir`.
**Проблема.** `git ls-files promo/shots` возвращает закоммиченные PNG (`logo_vector_light.png`, `shot_bells.png`, …). Прогон гейта перезаписывает ассеты в рабочем дереве: `git status` после тестов грязный, а в CI каждый run порождает diff. `MapScreenShotTest` пишет в `build/screenshots` (untracked) — третья, несогласованная стратегия.
**Фикс.** Все снимки — в `build/shots` или в `roborazzi.outputDir`; в git — только эталоны для `verify`.
**Как проверить.** `git status --porcelain` после прогона: сейчас содержит изменённые `promo/shots/*.png`.

---

### [P2] Ожидание через `Thread.sleep` + `System.nanoTime` в 9 классах
**Где.** `PromoShotsTest.kt:624-642` (`waitForText`/`waitForTag`: цикл `Thread.sleep(50)` + `mainClock.advanceTimeByFrame()` до 20 000 мс), `UpdateShotsTest.kt:170-173`, `teacher/TeacherShotsTest.kt:222-243`, `UpdateSettingsComposeTest.kt:77`, `UpdateInstallButtonComposeTest.kt:209-227`, `ContentStartupCheckTest.kt:42,85`, `update/AppUpdateStartupCheckTest.kt:42,85`, `update/UpdateInstallControllerTest.kt:56,149-166`, `update/AppUpdateWorkerNotifyTest.kt:72`.
**Проблема.** В Compose-тестах `mainClock.autoAdvance = false`, значит «настоящее» время теста и виртуальные часы Compose расходятся; цикл с `Thread.sleep` крутит виртуальные часы вручную и зависит от загрузки машины. На CI-раннере с 1 vCPU 20 с может не хватить → таймаут → `AssertionError("Текст … не появился")` при полностью исправном коде. Дополнительно: `System.nanoTime()` в тесте — единственный источник «дедлайна», то есть тест меряет реальный wall-clock.
**Фикс.** `composeRule.waitUntil(timeoutMillis = 5_000) { … }` (работает с виртуальными часами и корректно их двигает); для корутин — `runTest` + `advanceUntilIdle()`/`advanceTimeBy()` вместо `Thread.sleep`.
**Как проверить.** Запустить `PromoShotsTest` под нагрузкой (`--max-workers=1`, параллельно с компиляцией) — падения по таймауту воспроизводятся.

---

### [P3] `AppThemeTest` мутирует глобальную тему без восстановления
**Где.** `AppThemeTest.kt:68-84` — цикл `applyAppTheme(theme)` с восстановлением `COLLEGE` **только в последней строке метода**, без `@After`. Если любой `assert` в середине цикла упадёт, глобальные `ColorBrandFill`/`ColorTopBar` останутся от чужой темы и повлияют на последующие тесты в том же sandbox'е.
**Фикс.** `@After { applyAppTheme(AppTheme.COLLEGE) }` (или try/finally).
**Как проверить.** Временно сломать один `assert` в середине цикла и посмотреть на результат следующего теста класса.

---

### [P3] `ScheduleArchiveTest` содержит тавтологию вместо проверки
**Где.** `app/src/test/java/com/politex/data/repository/ScheduleArchiveTest.kt:47-50`:
```kotlin
assertNotEquals(past, current)
assertTrue(past < current)
assertFalse("08.09.2026" == "15.09.2026")
```
**Проблема.** Третья строка сравнивает два строковых литерала — не код. Первые две проверяют арифметику `Calendar`, а не `isStaleDate`. Тест называется «прошедший тот же день недели не равен текущей неделе», но `isStaleDate` в нём не вызывается.
**Фикс.** Заменить на реальные кейсы вокруг `isStaleDate`: граница cutoff включительно/исключительно, формат `2025-09-01` и `1.9.2025` (сейчас только «не удаляется»), пустая строка.
**Как проверить.** Удалить тело `isStaleDate` (вернуть `false`) — тест останется зелёным.

---

### [P3] `app/schemas` неполон: есть 4…11, нет 1…3
**Где.** `app/schemas/` — `4.json` … `11.json`. При этом существуют `MigrationV1ToV2Test`, `V2ToV3Test`, `V3ToV4Test`.
**Проблема.** Ранние миграции не сверяются с экспортированной схемой; расхождение DDL в тесте и в проде для v1→v4 не будет замечено.
**Фикс.** Либо догенерировать схемы 1–3, либо явно зафиксировать в комментарии, что v1→v4 не поддержаны схемой.
**Как проверить.** `Get-ChildItem app/schemas` — 8 файлов, минимальный `4.json`.

---

## План новых тестов под 4 фикса

> Общие требования: время — только через фиксированный `Clock`/`Calendar`, БД — только in-memory, ожидание — только `composeRule.waitUntil`. Ни один новый класс не должен содержать `Thread.sleep`, `System.currentTimeMillis()` или `Calendar.getInstance()` в теле теста.

### Фикс 1. Приём только поддерживаемых файлов

Состояние кода: `ExternalMaterialIntake` (`ui/screens/materials/ExternalMaterialIntake.kt`) отбрасывает **только APK** — по MIME `application/vnd.android.package-archive` (строка 18, 103) и по `.apk` в имени (104, 112). Белого списка поддерживаемых типов нет: `.exe`, `.zip`, `.7z`, `.iso`, `.apk`-в-архиве и любой `application/octet-stream` с неизвестным именем проходят дальше. `MaterialImporter.PLAIN_TEXT_EXT` / `IMAGE_EXT` (строки 70, 72) определяют лишь способ чтения, не приём. Существующий `ExternalMaterialIntakeTest` (22 теста) проверяет разбор intent'ов и отказ APK — **ни одного теста на «поддерживаемый/неподдерживаемый тип»**.

**1. `IntakeSupportedTypesTest`** (Robolectric) — 6 кейсов
- `pdf принимается и по MIME, и по расширению`
- `doc и docx принимаются при MIME application/octet-stream`
- `изображения jpg/png/webp/heic/heif дают PendingMaterial.Images`
- `exe, zip, 7z, iso отклоняются по расширению при нейтральном MIME`
- `MIME application/x-msdownload отклоняется без расширения в имени`
- `неизвестное расширение без MIME отклоняется (fail-closed, а не fail-open)`

**2. `IntakeRejectionFeedbackTest`** (Robolectric) — 4 кейса
- `отказ даёт текст причины, а не тихий null`
- `SEND_MULTIPLE pdf+exe: exe отброшен, pdf принят, warning выставлен`
- `все файлы отклонены → hasWork == false, экран «Уроки» не открывается`
- `consumeWarning сбрасывает предупреждение ровно один раз`

**3. `MaterialImporterWhitelistTest`** (Robolectric) — 5 кейсов
- `extFromMime для поддерживаемых MIME даёт ожидаемое расширение`
- `имя без расширения + поддерживаемый MIME получает расширение`
- `чужое расширение при поддерживающем MIME нормализуется`
- `copyToStorage не создаёт файл для отклонённого типа`
- `sanitize сохраняет расширение и режет длину до MAX_FILE_NAME_LENGTH`

**4. `IntakeRegressionE2ETest`** (Robolectric, `Robolectric.buildActivity`) — 4 кейса
- `ACTION_VIEW .pdf → материал появляется в списке и открывается viewer`
- `ACTION_SEND .apk → активити не стартует, тост/снек с причиной`
- `ACTION_SEND_MULTIPLE 25 изображений → 20 приняты, предупреждение`
- `повторный шеринг того же файла (contentHash) не создаёт дубль`

---

### Фикс 2. Бейдж ДЗ на всех нужных уроках

Состояние кода: `HomeworkMarkers.markerLessonTypes(...)` → `Map<Int, List<TaskType>>` (`HomeworkMarkers.kt:35-65`), вызывается из `ScheduleScreen.kt:310-327` и доезжает до карточек через `hasHomework`/`homeworkTypes` (строки 856-919, 935). Есть тесты на сопоставление предметов (`HomeworkSubjectsMatchTest`, `Jj1SubjectsMatchTrapTest`) и на экран заданий — **но ни одного теста на то, что маркер появляется на каждом нужном уроке**: покрытие по дням недели 1..6, поведение на merged-run, на split-парах и на пустых слотах не проверяется.

**5. `HomeworkBadgeCoverageTest`** (plain JUnit) — 6 кейсов
- `маркер ставится на каждый урок недели 1..6 для предмета с дедлайном`
- `день без пары этого предмета маркера не получает`
- `два задания по одному предмету дают два типа на одном номере`
- `дедлайн в прошлом не маркирует будущие пары`
- `перенос дедлайна даёт маркер на следующем вхождении предмета`
- `subjectName с префиксом/скобками матчится на пороге FUZZY_MATCH_THRESHOLD = 0.8`

**6. `HomeworkBadgeMergedRunTest`** (plain JUnit) — 5 кейсов
- `merged run из 3 уроков даёт один бейдж на общем заголовке`
- `бейдж не дублируется на каждом номере run`
- `split-пара маркирует обе подгруппы`
- `дырка в нумерации не переносит бейдж на ScheduleLessonSlot.Empty`
- `teacher-режим маркирует по группе, а не по преподавателю`

**7. `ScheduleHomeworkBadgeRenderTest`** (Robolectric + Compose) — 5 кейсов
- `бейдж виден на одиночном уроке с заданием` → `onNodeWithTag("hw_badge_2").assertExists()`
- `на уроке без задания бейджа нет` → `assertDoesNotExist()`
- `на merged-карточке бейдж есть, если задание хотя бы у одного урока`
- `клик по бейджу вызывает onHomeworkClick`
- `на ScheduleLessonSlot.Empty бейдж не рисуется`

**8. `HomeworkBadgeRoborazziTest`** (Roborazzi, `@GraphicsMode(NATIVE)`) — 4 снимка
- `hw_badge_single_light`, `hw_badge_merged_light`, `hw_badge_split_light`, `hw_badge_none_light`
- (+ те же 4 в dark)

---

### Фикс 3. Баги расписания (устаревшее / сегодня)

Состояние кода: `ScheduleRepository.isStaleDate` (125), `syncScheduleDetailed` (320, `Calendar.getInstance()` 325), `nextDayCandidates` (161/178), очистка `deleteLessonsByDates` (660-663). Тесты есть (`SyncDateFilterTest`, `SyncCacheFirstTest`, `ScheduleArchiveTest`, `NextSchoolDayFallbackTest`, `W1SyncTargetDateTest`, `TodayProviderTest`), но **4 из них берут реальный `Calendar.getInstance()`**, а `isStaleDate` покрыт одним классом с фиксированным cutoff `2026-01-01` и без границы «сегодня/вчера».

**9. `ScheduleTodayBoundaryTest`** (plain JUnit, фиксированный `Calendar`) — 6 кейсов
- `урок, начавшийся 5 минут назад, ещё «сегодня»`
- `урок, закончившийся минуту назад, помечается прошедшим, но остаётся в сегодня`
- `в 00:00:00 вчерашние уроки уходят в архив, сегодняшние встают`
- `суббота без пар → следующая учебная дата = понедельник`
- `воскресенье → понедельник, а не «сегодня»`
- `переход на зимнее время не сдвигает границу суток (Europe/Minsk)`

**10. `ScheduleStaleDateTest`** (plain JUnit) — 5 кейсов
- `дата ровно на cutoff не устаревает (граница включительно)`
- `дата за день до cutoff устаревает`
- `'' и не-точечные форматы не трогаются`
- `удаляются только устаревшие даты, свежие остаются`
- `teacher_lessons чистятся тем же набором дат`

**11. `ScheduleSyncTodayTargetTest`** (plain JUnit, fake dao + fake client) — 6 кейсов
- `пустой кэш → синк берёт сегодня и завтра`
- `сегодняшний кэш непустой → сеть для сегодня не вызывается`
- `force игнорирует кэш для обеих дат`
- `пятница → кандидат понедельник, а не суббота`
- `суббота со schoolDay=true → кандидат суббота`
- `SyncOutcome.Ok несёт число уроков и дату-источник`

**12. `ScheduleMidnightRolloverTest`** (Robolectric, фиксированный clock) — 4 кейса
- `экран, открытый в 23:59, после полуночи показывает новый день`
- `виджет после полуночи не показывает вчерашние уроки`
- `«до звонка» после полуночи считает до первого звонка нового дня`
- `кэш «завтра» не переиспользуется как «сегодня»`

---

### Фикс 4. Настраиваемые темы

Состояние кода: `AppTheme` — 8 тем, у каждой `palette` + `darkPalette` (`AppTheme.kt:7-190`), `applyAppTheme` (193), `AppThemeStore`. Тесты: `AppThemeTest` (7), `AppThemeStoreDarkTest` (6), `ThemeTokensContrastTest` (2 — 11 пар токенов), `DarkThemeTest` (7), `PlumThemeTest`, `BellCountdownThemeTest`, `WidgetStyleThemeTest`. **Дырки:** нет теста персистентности (id → prefs → чтение), нет теста неизвестного/устаревшего id в prefs, нет теста, что виджеты и `RemoteViews` следуют теме, контраст проверен по 11 парам, а не по всем токенам, Roborazzi-сетки тем нет.

**13. `AppThemePersistenceTest`** (Robolectric) — 6 кейсов
- `выбранная тема переживает пересоздание store`
- `неизвестный id в prefs откатывается на COLLEGE без падения`
- `id темы не зависит от локали (ru / en / ROOT)`
- `смена темы инкрементит revision — виджеты получают сигнал`
- `тёмный режим хранится отдельно от id темы`
- `сброс настроек возвращает COLLEGE + light`

**14. `AppThemeAllTokensTest`** (plain JUnit) — 5 кейсов
- `каждая из 8 тем в light проходит WCAG AA по всем парам токенов`
- `каждая из 8 тем в dark проходит WCAG AA по тем же парам`
- `тёмная палитра не совпадает со светлой ни в одной теме`
- `все 8 тем имеют различные topBar и brandBlue`
- `контраст textPrimary/bgMain >= 7 во всех темах`

**15. `AppThemeWidgetSyncTest`** (Robolectric) — 4 кейса
- `RemoteViews виджета получает цвета активной темы`
- `смена темы без перезапуска обновляет все виджеты`
- `тёмная тема не даёт белый текст на белом фоне виджета`
- `тема не сбрасывается после onUpdate`

**16. `AppThemeRoborazziTest`** (Roborazzi, `@GraphicsMode(NATIVE)`) — 4 снимка
- `theme_grid_light` (8 тем в одной сетке), `theme_grid_dark`
- `settings_theme_picker`, `settings_theme_picker_dark`

**Итого 16 новых классов, ~82 кейса.** Приоритет при нехватке времени: 1, 5, 9, 14 (по одному на фикс) — они ловят регресс фикса, остальные добавляются по мере правок.

---

## CI-гейты: что есть / чего нет

| Гейт | Есть | Где | Комментарий |
|---|---|---|---|
| Unit-тесты | **да** | `build-apk.yml:36` | `:app:testStandardDebugUnitTest --no-daemon` — единственный тестовый гейт |
| Сборка APK | **да** | `build-apk.yml:39` | `assembleDebug`, debug-вариант, R8 не задействован |
| Триггер на рабочую ветку | **нет** | `on.push.branches: [main, master]` | рабочая — `v2.4-stable` |
| Триггер на `pull_request` | **нет** | — | PR'ы не проверяются вообще |
| `--continue` (все падения за прогон) | **нет** | — | первый упавший тест рвёт прогон |
| Публикация отчётов тестов | **нет** | — | нет `upload-artifact` для `app/build/reports/tests` |
| Адекватный таймаут | **нет** | `timeout-minutes: 15` | 1677 тестов, 95 Robolectric-классов, `maxParallelForks = 1`, cold-start скачивания android-all для 5 SDK |
| Кэш Gradle | **частично** | `gradle/actions/setup-gradle@v4` | кэш есть по умолчанию, но без `cache-read-only` и без ключа по версии зависимостей |
| JDK как в разработке | **нет** | `java-version: '17'`, `temurin` | локально `JBR 21.0.10`; AGP 9.1.1 + Gradle 9.3.1 требуют 17+, но расхождение не проверено |
| Roborazzi verify | **нет** | — | 5 классов снимают PNG, сравнения с эталоном нет, эталонов в git нет |
| spotless / ktlint | **нет** | root `build.gradle.kts:10-20` | плагин настроен (`ktlint 1.5.0`), в CI не вызывается |
| Android Lint | **нет** | — | — |
| Матрица flavor'ов | **нет** | — | `standard` тестируется, `admin` / `adminplus` / `teacher` — нет |
| Миграции vs `app/schemas` | **нет** | — | 11 ручных DDL-тестов, сверки со схемой нет; `schemas` неполон (нет 1–3) |
| Страж «нет `fallbackToDestructiveMigration`» | **нет** | — | вызов присутствует в `MpkDatabase.kt:286` |
| Гейт «нет `students.csv` в standard APK» | **нет** | — | инвариант из `CLAUDE.md` не автоматизирован |
| Секреты в CI | **не требуются** | `.env.example` | `secrets`-плагин падает на `.env.example`, `.env` не нужен — риска нет |

### Минимальные правки workflow

`build-apk.yml` — четыре правки, дающие максимум эффекта:

```yaml
on:
  push:
    branches: [ v2.4-stable, main, master ]   # + рабочая ветка
  pull_request:                                # + PR
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45                        # было 15

    steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { java-version: '21', distribution: 'temurin' }   # как локально

    - uses: gradle/actions/setup-gradle@v4
      with: { cache-read-only: false }

    - name: Unit tests
      run: ./gradlew :app:testStandardDebugUnitTest --continue --no-daemon

    - name: Upload test reports
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: unit-test-reports
        path: app/build/reports/tests/
        retention-days: 7
```

Отдельным PR (после стабилизации флаков) — второй job:

```yaml
  verify:
    needs: build
    steps:
      - run: ./gradlew spotlessCheck
      - run: ./gradlew :app:verifyRoborazziStandardDebugUnitTest -Proborazzi.test.verify=true
```

---

## Флаки

| # | Класс / место | Причина | Как стабилизировать |
|---|---|---|---|
| 1 | `LiveNetworkSmokeTest` (`fetchScheduleForGroup_41О_…`) | Реальная сеть: `MpkNetworkClient().fetchScheduleForGroup("41О")` + `InetAddress.getByName("guo-mpk.by")`. `assumeTrue` только на `CI` и на DNS — DNS резолвится и для мёртвого хоста. `assertTrue(lessons.isNotEmpty())` требует, чтобы сайт отдал документ **на сегодня** → падает в выходные, в праздники, вечером до публикации, при смене имени документа. Уже требует обхода (коммит `bb2c9c0`). | Вынести из `src/test` в `src/liveTest` + отдельная ручная задача; в гейте — только `MockWebServer` с записанным ответом (`W1LiveFixtureDiagnosticsTest` уже доказывает, что фикстуры достаточно). |
| 2 | `promo/PromoShotsTest` — все 23 теста | 20-секундные ожидания на `Thread.sleep(50)` + `mainClock.advanceTimeByFrame()` в цикле (`:624-642`); глобальная подмена `TimeZone.setDefault` на анонимный подкласс (`:133-173`) — утечка за пределы класса; файловая БД-синглтон; запись в **отслеживаемый** `promo/shots`. | `composeRule.waitUntil`; фиксированный `Clock` вместо `TimeZone`-хака; in-memory БД; вывод в `build/shots`. |
| 3 | `promo/UpdateShotsTest`, `teacher/TeacherShotsTest`, `promo/CollegeLogoShotTest` | Тот же polling-паттерн (`UpdateShotsTest:170-173`, `TeacherShotsTest:222-243`), `DateFormats.dotDate(Calendar.getInstance())` — реальная дата внутри снимка; `promo/shots` перезаписывается. | `waitUntil` + фиксированные часы; снимки — артефакты, не эталоны в git. |
| 4 | `SyncCacheFirstTest` (4 теста) | `val today = Calendar.getInstance()` в каждом тесте (`:180,202,218,233`); ожидание `SyncOutcome.Ok(5, …)` и `SyncOutcome.NotFound(targetDate)` — зависит от дня недели, субботы и момента запуска. Прогон 23:59:59 может пересечь полночь между `today` и `fmt(nextDayCandidates(...))`. | Тот же `FixedClock`; все даты — константы (`2026-10-05`, понедельник). |
| 5 | `data/repository/SyncDateFilterTest` | `tomorrowStr(today = Calendar.getInstance())` (`:135`), `val today = Calendar.getInstance()` (`:178`). | Фиксированная дата в параметре. |
| 6 | `content/ContentStartupCheckTest`, `update/AppUpdateStartupCheckTest` | `Thread.sleep(50)` / `Thread.sleep(300)` + `assertTrue(System.currentTimeMillis() - mark < 60_000L)` — абсолютные дельты от реальных часов. | `runTest` + `TestCoroutineScheduler`; сравнивать не «сколько прошло», а значение сохранённой метки. |
| 7 | `update/UpdateInstallControllerTest`, `UpdateInstallButtonComposeTest`, `UpdateSettingsComposeTest`, `update/AppUpdateWorkerNotifyTest` | Циклы ожидания с дедлайнами 5–10 с и `Thread.sleep(20/25/50)`. | `ComposeTestRule.waitUntil` + `advanceTimeBy`; для контроллера — `runTest`/`advanceUntilIdle`. |
| 8 | `widget/WidgetSecondsTickTest` + 5 виджет-классов с `@Config(sdk = [30,31,33,35])` | ×4 прогона каждого теста (риск таймаута CI), глобальный мутабельный `WidgetData.calendarOverride`, `System.currentTimeMillis()` в `tickDiagnostics()` (только сообщение, но попадает в отчёт). | Оставить матрицу только там, где реально различается поведение `AlarmManager`/`RemoteViews`; остальным — `sdk=36` из `robolectric.properties`. |
| 9 | `ScheduleParseBenchTest` | Не флак по assert'ам (их нет), но нестабилен по времени: 10 бенчмарков на реальных `.doc`, включая `parseFileAllGroups` ×3 по 57 группам — при загруженном CI это десятки секунд. | `@Ignore` / отдельный sourceSet. |
| 10 | Любой тест расписания/ДЗ/виджетов | Общая причина всех пунктов 4–8: **нет инъектируемых часов**. `TodayProvider` в main не используется, `Calendar.getInstance()` зашит в `ScheduleRepository:325` и `HomeworkMarkers:106,130`. | Ввести `Clock` и протянуть в 3 модуля — снимает причины 2, 4, 5, 6, 8 разом. |

---

## Что сделать в первую очередь

1. **P0:** включить CI на `v2.4-stable` и на `pull_request` (5 строк YAML) — иначе остальное не имеет значения.
2. **P0:** настроить Roborazzi `outputDir` + `verify` и закоммитить эталоны — иначе 5 классов снимков мертвы.
3. **P0:** тест-страж против `fallbackToDestructiveMigration`.
4. **P1:** `robolectric.properties` с `sdk=36` — убирает рассинхрон 36/6/53 классов одним файлом.
5. **P1:** `Clock` в `ScheduleRepository`/`HomeworkMarkers` — разблокирует детерминированные тесты под фиксы 2 и 3 и снимает половину флаков.