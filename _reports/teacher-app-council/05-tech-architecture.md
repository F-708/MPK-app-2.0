# 05 — Техническая архитектура: приложение для преподавателей (консилиум, фаза 1)

Автор: ТЕХНИЧЕСКИЙ АРХИТЕКТОР. Дата: 2026-10-07.
База: `D:\MPK app\MPK-app-2.1`, Android/Kotlin/Compose, minSdk 24, targetSdk 36, Room v9, 2 flavor (standard/admin), ~33k LOC main-исходников [VERIFIED: wc -l app/src/main/java], 784 теста [дано].

---

## 1. ФОРМА ПОСТАВКИ

### Как устроен admin flavor сегодня (прецедент)

- `productFlavors { standard (isDefault), admin }` в dimension "version"; admin получает `applicationIdSuffix = ".admin"` и `versionNameSuffix = "-admin"` [VERIFIED: app/build.gradle.kts:18-29].
- Source set `app/src/admin/` = **только assets/students.csv + res/ (иконки mipmap + drawable)** [VERIFIED: ls app/src/admin]. Манифест-оверлея у admin НЕТ — иконка переопределяется ресурсами, label общий из `main/res/values/strings.xml` ("Мой Политех").
- Код-гейты: `BuildConfig.FLAVOR == "admin"` ровно в 2 местах [VERIFIED: CollegeScreen.kt:233, SettingsScreen.kt:721]. Данные-гейт: `StudentsRepository` при отсутствии актива молча возвращает `emptyList()` [VERIFIED: StudentsRepository.kt:25-35] — т.е. standard не падает без students.csv.
- Подпись: единый release signingConfig из `keystore.properties`/env для всех flavor [VERIFIED: app/build.gradle.kts:51-71].

### Вариант A — третий flavor «teacher» в текущем проекте

Механика: `create("teacher") { applicationIdSuffix = ".teacher"; versionNameSuffix = "-teacher" }` + `src/teacher/res` (иконка, `app_name` «Мой Политех Преподавателю») + ОДНА точка ветвления в `MainActivity`: `if (BuildConfig.FLAVOR == "teacher") TeacherRoot() else MainScreen()` — вместо расползания FLAVOR-гейтов по экранам.

| Плюсы | Минусы |
|---|---|
| Прецедент admin уже proves весь паттерн (suffix, res-оверлей, гейт активов) | Матрица сборки ×1.5 (3 flavor × debug/release), дольше CI |
| Общий код (парсеры, Room, тема, карта, звонки) — одна копия, багфиксы в одном месте; история правок расписания-парсера плотная (КРИТ1, СРЕД10, М23…) — форк эту ленту удвоит | Соблазн писать `if (FLAVOR==...)` повсюду → комбинаторный ад (лечится единой точкой ветвления в MainActivity) |
| R8: `BuildConfig.FLAVOR` — константа, ученические ветки (Students, Tasks, Grades, Materials) вырезаются как недостижимые [ASSUMPTION — стандартное поведение R8 на final-константе] | Общая версия БД: новая таблица преподавателя bump'ит version и для учеников |
| Отдельный applicationId → отдельное приложение в Play, раздельные песочницы prefs/БД на устройстве | |

Трудозатраты: **MVP 1–1.5 дня, полный вариант 5–7 дней** [ASSUMPTION, детализация в §7].

### Вариант B — отдельный модуль/проект с копией общего кода

| Плюсы | Минусы |
|---|---|
| Полная изоляция релизов | Форк ~33k LOC + 784 теста: каждый фикс парсера/URL/дат — в двух местах |
| Можно чистить от ученического кода | Градл-инфра, keystore, CI, roborazzi-скриншоты — дублировать |
| | Расхождение версий Room-схемы и миграций почти гарантировано |
| | Общий код НЕ модуляризован (один `app`, нет :core) — выделение модулей само по себе 5–8 дней рефакторинга [ASSUMPTION] |

Трудозатраты: **12–18 дней на старте + постоянный налог двойной поддержки** [ASSUMPTION]. Отклонить.

### Вариант C — режим «Я преподаватель» внутри ученического приложения

Переключатель в Settings → корневой экран заменяется на TeacherRoot, ученические табы скрываются. Прецедент замены корня есть (NavHost со startDestination) [VERIFIED: MainScreen.kt:383-385].

| Плюсы | Минусы |
|---|---|
| Дешевле всех: 2–3 дня [ASSUMPTION] | **Заказчик хочет ОТДЕЛЬНОЕ приложение** — не удовлетворяет требованию |
| Ноль новой сборки | Одна Play-страница на две аудитории → путаница в отзывах/скриншотах |
| | Ученики видят入口 преподавательских фич; обратная миграция UX болезненна |

### РЕКОМЕНДАЦИЯ: **A — третий flavor «teacher»**

Обоснование: (1) заказчик требует отдельное приложение → C отпадает как финальная форма; (2) admin flavor уже демонстрирует ровно нужный механизм (applicationIdSuffix + res-оверлей + данные-гейт через assets), т.е. A — это повторение proven-паттерна, а не новая архитектура; (3) ядро ценности teacher-приложения (парсинг дока преподавателей, кэш teacher_lessons, UI сегодня/завтра/календарь) уже живёт в общем коде — вариант B заставил бы платить двойную цену за поддержку самого хрупкого места (парсер .doc с сайта). C допустим как тактический stop-gap на 2 дня, если дедлайн горит, с последующей конверсией в A (код TeacherRoot переносится как есть).

---

## 2. Карта переиспользования

Легенда: **as-is** — берём без правок; **adapt** — нужны изменения; **cut** — в teacher-сборку не тянем.

| Компонент | Файл(ы), строки | Использование в teacher-варианте |
|---|---|---|
| Парсер расписания преподавателей (таблицы `║/│`, .doc и .docx) | `data/network/TeacherScheduleParser.kt:67-163, 194-210` | **as-is** — ядро продукта |
| Загрузка дока преподавателей (`nameBase="raspisanie-prepodavatelej"`, проверка даты в доке) | `data/network/MpkNetworkClient.kt:353-387, 147-174, 129-135` | **as-is** |
| Кэш-запись teacher_lessons (rows + distinctBy + replaceForDate даже пустыми) | `data/repository/ScheduleRepository.kt:208-235, 577-610` | **as-is** |
| Чтение дня преподавателя из кэша | `ScheduleRepository.getTeacherLessonsForDate:612-618`, `TeacherLessonDao:20-23` | **as-is** (но см. §3 про surnameKey) |
| Таблица teacher_lessons | `entity/TeacherLessonEntity.kt:12-26`, `dao/TeacherLessonDao.kt` | **as-is** |
| Логика «кэш-первым, сеть один раз, ошибка→текст» | `CollegeScreen.loadTeacherDay:1442-1460`, `teacherTargetCalendar:1412-1417`, `defaultTeacherDayOffset:1419-1421` (вс 404 → сразу пн), `teacherSyncErrorMessage:1423-1433` | **adapt** — вынести из CollegeScreen в отдельный файл (напр. `data/repository/TeacherDayLoader.kt`), использовать из обоих приложений |
| UI дня: чипы СЕГОДНЯ/ЗАВТРА, календарь, карточки слотов со временем звонков | `CollegeScreen.TeacherScheduleTab:1468-1730+` | **adapt** — превратить из вкладки карточки в корневой экран TeacherHomeScreen (90% кода переносится) |
| Календарь-архив | `ui/components/CalendarArchiveDialog.kt` (уже вызывается с `groupName=""` для преподавателя: CollegeScreen:1536-1551) | **as-is** |
| Звонки (время слотов, экран) | `data/model/CollegeBellSchedule.kt` (getBellsForDay используется в слотах: CollegeScreen:1702-1714), `ui/screens/BellsScreen.kt:131,149` | звонки **as-is**; BellsScreen **adapt** (сейчас тянет lessons группы — для преподавателя показывать только сетку звонков) |
| Справочник преподавателей + фото | `data/repository/TeachersRepository.kt:7-59`, `assets/teachers.csv` (~100 чел., `;`-разделитель, 10 колонок), `assets/teachers_photos/`, `TeacherAvatar` CollegeScreen:917-957 | **as-is** — источник для онбординга «выберите себя» и профиля |
| Кэш/загрузка CSV преподавателей в Compose | `ui/util/KnownTeachers.kt:26-77` (TeachersCache, rememberTeachers) | **as-is** |
| Поиск преподавателя для онбординга | `CollegeScreen.TeachersPage:700-810` + `util/FuzzySearch.kt` | **adapt** — переиспользовать паттерн поиска/списка в TeacherOnboardingScreen |
| Матчинг ФИО (фамилия-ключ, инициалы) | `data/repository/TeacherInsights.surnameOf:167-175`, `initialsOf:177-183`, `matchTeacher:125-156`, `splitTeacherNames:162-165` | **as-is** для сопоставления; `buildFacts/insights` (:55-111) — **cut** (требует кэш уроков ВСЕХ групп; в teacher-сборке факты брать из teacher_lessons: subject/room/groups там уже есть) |
| Карточка преподавателя (About) | `CollegeScreen.TeacherCardPage:1069-1189`, `AboutTeacherTab:1240-1410` | **adapt** — убрать insights-блоки (комнаты/предметы из расписания групп), оставить CSV-поля (должность, стаж, категория, контакты) |
| Карта + маршруты | `ui/screens/CollegeMapScreen.kt`, `data/model/CollegeRouteFinder.kt`, `CollegeMapData.kt`, `assets/college_map.json` | **as-is** (преподавателю: «в какой кабинет идти») |
| DI-контейнер | `MpkApplication.kt:11-41` (AppGraph: scheduleRepository с teacherLessonDao, teachersRepository, studentsRepository) | **adapt** — минимально: studentsRepository в teacher-сборке безвреден (assets нет → empty), можно оставить as-is |
| База Room | `data/local/MpkDatabase.kt:26-41` (version 9, 10 таблиц), миграции :59-244, fallbackToDestructive :263 | **as-is** (общая схема, см. §4) |
| Тема/токены | `ui/theme/{AppTheme,Color,Theme,Tokens,Type}.kt` | **as-is** |
| Навигация | `MainScreen.kt:383-590` (NavHost, табы SCHEDULE/TASKS/BELLS/COLLEGE :45-54), `AppViewModel.kt:64-166` (весь вокруг группы) | **cut/adapt** — teacher-сборке нужен СВОЙ корневой NavHost (Расписание/Звонки/Карта/Профиль) и лёгкий TeacherViewModel; AppViewModel не переиспользуем (он group-centric) |
| Воркер синхронизации | `worker/ScheduleCheckWorker.kt:17-156` (group-driven), `MpkWorkManagerHelper.kt:12-67` | **adapt** — teacher-ветка/отдельный воркер (§5); helper as-is + новый setup |
| Виджеты | `widget/{BellCountdown,NowNext,TodaySchedule,Bells}WidgetProvider`, `WidgetUpdateHelper.kt:19-172` | Bells **as-is**; NowNext/TodaySchedule/BellCountdown **adapt** — читают lessons выбранной группы (NowNextWidgetProvider:97,233; TodayScheduleWidgetProvider:60; BellCountdownWidgetProvider.lastGroupLessonForToday:198-215) → teacher-источник teacher_lessons. BellCountdownNotifier.Scope.COLLEGE group-независим [VERIFIED: BellCountdownNotifier.kt:33-43,199] — дефолт для teacher |
| Будильники/служба звонков | `util/BellTimerService.kt`, `worker/BellServiceAlarmReceiver.kt`, `widget/TimeChangedReceiver.kt`, манифест :103-147 | **as-is** |
| Напоминания о ДЗ | `worker/ReminderCheckWorker.kt`, TaskRepository/TaskDao | **cut** |
| ДЗ/оценки/материалы/студенты/группы | TasksScreen, GradesScreen, MaterialBaseScreen, StudentsScreens, GroupScheduleScreen, ScheduleScreen, `util/{GroupCatalog,GroupParser,SubgroupSettings,HomeworkMarkers}` | **cut** в MVP (GroupScheduleScreen — кандидат на возврат позже: «кто у меня в группе X», но требует тяжёлый групповой док) |
| Утилиты дат/формата | `util/{Dow,DateFormats,SubjectFormatter,Csv}.kt` | **as-is** |
| Бэкап | `util/BackupManager.kt` | **cut** в MVP (экспортирует группу/задачи) |
| R8-правила | `proguard-rules.pro:3-22` (keep виджетов/receiver'ов, enum-имён в prefs, Room) | **as-is** |

### Что УЖЕ готово для расписания преподавателя

Полный рабочий цикл: скачивание отдельного дока преподавателей → парсинг → кэш в Room → UI «сегодня/завтра/календарь» со временем звонков и группами, кэш-первым с однократной сетью [VERIFIED: MpkNetworkClient:353-387 → ScheduleRepository:577-610 → CollegeScreen:1468-1730]. Покрыто тестами: TeacherScheduleParserTest, TeacherScheduleCacheTest, TeacherScheduleE2ETest, TeacherScheduleLiveDocTest, TeacherScheduleDayPickTest, TeacherInsights*Test (7 файлов).

### Чего НЕ хватает

1. **Профиль «я — преподаватель»**: выбор себя из teachers.csv + хранение (достаточно SharedPreferences, см. §4) + онбординг первого запуска.
2. **Корневой экран/навигация** teacher-сборки (сейчас расписание преподавателя — вкладка внутри карточки преподавателя внутри «Другое»).
3. **Список «моих групп + предметов» / недельная нагрузка**: teacher_lessons заполняется по одному дню; для недели надо синкнуть 6 доков (пн–сб) и агрегировать distinct(groups×subject). Хранимого нет — только вычисляемое.
4. **Замены/локальные правки**: никаких сущностей нет; кэш-первым гейт (`getPresentDates`, ScheduleRepository:584) блокирует перечитывание уже скачанного дня — внутридневная замена на сайте не подхватится без `force=true`.
5. **Teacher-воркер и уведомление** («завтра у вас N пар») — текущий воркер group-only.
6. **Виджеты** на teacher_lessons.

---

## 3. Данные: источник истины и качество ФИО

**Источник — ОТДЕЛЬНЫЙ документ расписания преподавателей**, а не фильтрация группового дока. Это важно: в задании предполагалось «один документ = все группы → фильтрация по teacherFirst/teacherSecond», но в коде реализовано иначе и лучше:

- Групповой док (`…-raspisanie-uchashhihsya.doc`, один файл на все группы [VERIFIED: ScheduleRepository.fetchAllGroupsForDate:537-564]) даёт `lessons.teacherFirst/teacherSecond` [VERIFIED: LessonEntity.kt:29,31] — из них `TeacherInsights.buildFacts` считает факты (кабинеты/предметы/активность) [VERIFIED: TeacherInsights.kt:55-111].
- Док преподавателей (`…-raspisanie-prepodavatelej.doc(-2)`) качается `fetchTeacherSchedule` [VERIFIED: MpkNetworkClient.kt:353-387], парсится `TeacherScheduleParser.parse` в `Map<ФИО, List<TeacherSlot>>` и пишется в `teacher_lessons` через `teacherRows` [VERIFIED: ScheduleRepository.kt:208-235, 577-610].

Teacher-приложению нужен **только второй док** — он лёгкий (один файл/день) и уже содержит пары преподавателя с группами, предметом и кабинетом. Групповой док (тяжёлый, все группы) можно не качать вообще.

**Как матчится ФИО сейчас:**
- В доке формат «Фамилия И.О.» [VERIFIED: TeacherScheduleParserTest.kt:122 фикстура «Автушко Н.А.»], в teachers.csv — полное «Фамилия Имя Отчество» [VERIFIED: teachers.csv строка 1 «Шатько Александр Викторович;…»].
- Ключ — `surnameOf()`: первый токен, lowercased, цифры вырезаны [VERIFIED: TeacherInsights.kt:167-175]. Пишется в `teacher_lessons.surnameKey` [VERIFIED: ScheduleRepository.kt:215-216], читается по нему же [VERIFIED: TeacherLessonDao:20-23, CollegeScreen:1488-1514].
- Двусмысленность разруливается в `matchTeacher`: при >1 кандидате на фамилию — сравнение инициалов `initialsOf().startsWith` [VERIFIED: TeacherInsights.kt:125-156].

**Грабли:**
1. **Однофамильцы**: в teachers.csv 2 пары дублей (Ерёменко ×2, Савицкая ×2 из ~100) [VERIFIED: awk-подсчёт по teachers.csv]. Запрос `getForSurnameOnDate(surnameKey)` их **сливает в один список** — matchTeacher применяется к фактам из групповых lessons, но НЕ к выборке из teacher_lessons. В teacher-приложении пользователь «выбирает себя» из CSV — значит, показывать чужие пары однофамильца = критический баг продукта. Mitigation: после выборки по surnameKey фильтровать строки через `TeacherInsights.matchTeacher(row.teacherName) == выбранный` (teacherName в доке хранит «Фамилия И.О.» сырьём [VERIFIED: ScheduleRepository.kt:220-221]) — одна строка кода, без миграции.
2. **«Иванов И.И.» vs полное ФИО**: матчится через surnameOf+initialsOf — работает, но `initialsOf` берёт первые буквы токенов ПОСЛЕ первого [VERIFIED: TeacherInsights.kt:177-183]; если в доке порядок «И.И. Иванов» (инициалы впереди) — ключ сломается. В фикстурах порядок «Фамилия И.О.» [VERIFIED: TeacherScheduleParserTest.kt:122], риск низкий [ASSUMPTION].
3. **Замены в документе**: док публикуется на конкретную дату, парсер проверяет дату в шапке (`matchesRequestedDate`, иначе «Документ за X вместо Y» [VERIFIED: MpkNetworkClient.kt:368-379]). Внутридневная замена = пере-публикация дока, но наш гейт `getPresentDates` не перечитывает уже скачанный день без force [VERIFIED: ScheduleRepository.kt:584-587]. Для teacher-приложения: pull-to-refresh с `force=true` (параметр уже есть [VERIFIED: ScheduleRepository.kt:579]) — дёшево и честно.
4. **Ячейка с двумя преподавателями** («Иванов/Петров» при замене): `teacherRows` НЕ сплитит по «/» (в отличие от splitTeacherNames для групповых lessons) — слот достанется только surnameKey первой фамилии [VERIFIED: ScheduleRepository.kt:213-232 vs TeacherInsights.kt:162-165]. Проверить на живых доках; если встречается — сплитить в teacherRows (3 строки).
5. **Воскресенье**: дока нет (404), дефолт — сразу понедельник [VERIFIED: CollegeScreen.kt:1419-1421] — as-is.

---

## 4. Room: новые сущности, миграции, версия

**Принцип «лестницы»: минимум новых таблиц.**

1. **Профиль преподавателя — НЕ таблица.** Имя/фамилия-ключ/должность — 3 строки в SharedPreferences (паттерн `WidgetUpdateHelper.getSelectedGroup` [VERIFIED: WidgetUpdateHelper.kt:20-21,55-66] или отдельный prefs-файл `teacher_profile`). Source of truth для карточки — teachers.csv в assets, он уже версионируется с приложением. Room тут = лишняя миграция ради статичных данных.
2. **Локальные правки/замены — одна таблица `teacher_overrides`** (единственная реальная новая сущность):
   `id PK autoGenerate, dateString, lessonNumber, op (TEXT: CANCEL|REPLACE|MOVE|NOTE), subject, room, groups, note, updatedAt`; индекс `(dateString, lessonNumber)`. Применяется оверлеем при рендере дня после `getTeacherLessonsForDate`. Версия БД **9 → 10**: `migration9To10` в `addMigrations` + `MigrationV9ToV10Test` — жёсткая конвенция проекта (fallbackToDestructiveMigration тихо стирает данные при забытой миграции [VERIFIED: MpkDatabase.kt:246-263, комментарий R1-СРЕД15]).
3. **Нагрузка — вычисляемое, НЕ хранение.** Недельная нагрузка = sync пн–сб (6 лёгких доков, `syncTeacherScheduleForDate` уже умеет任意 дату) → `COUNT DISTINCT (день, lessonNumber)` и агрегат groups×subject из teacher_lessons. Отдельная таблица устареет мгновенно (кэш чистится prune через 60 дней [VERIFIED: ScheduleRepository.kt:673-682]) и ничего не даёт сверх SELECT'а.
4. **База — ОБЩАЯ** (`mpk_schedule.db`, одна `MpkDatabase` на модуль). Раздельная база для flavor бессмысленна: flavor'ы не ставятся на одно устройство обычно, а даже если оба стоят — applicationId разные ⇒ песочницы и так раздельные [VERIFIED: applicationIdSuffix в build.gradle.kts:26]. Общая схема = одна линия миграций 1→10, один MigrationTest-набор.

---

## 5. Синхронизация (воркер)

Текущий `ScheduleCheckWorker` **group-driven**: выбранная группа из prefs → probe → `syncScheduleForDate(groupName, …)` [VERIFIED: ScheduleCheckWorker.kt:35,76], окно 10:00–21:00 [:31], дедуп-метки `notified_/checked_` в prefs [:43-55,72-74]. Важно: этот путь **не заполняет teacher_lessons** — `syncScheduleForDate` не вызывает teacher-синк [VERIFIED: ScheduleRepository.kt:503-535]; teacher-кэш наполняется только из `syncScheduleDetailed` (ученический полный синк, [:463]) или из UI преподавателя (CollegeScreen:1526).

Для teacher-сборки — **отдельный лёгкий воркер `TeacherScheduleWorker`** (не ветвление внутри существующего: файлы не пересекаются, групповая логика не мешает):

1. Целевые даты = сегодня + следующий учебный день (`nextDayCandidates` — уже есть [VERIFIED: ScheduleRepository.kt:185-197]).
2. `repository.syncTeacherScheduleForDate(cal)` на каждую — качает ТОЛЬКО док преподавателей (один файл, не все группы) [VERIFIED: ScheduleRepository.kt:577-610]. Ответ на вопрос «своё или то же самое»: **то же самое не нужно** — групповой док teacher-сборке не требуется вовсе; teacher-док и так содержит всех преподавателей, «своё» получается фильтрацией при чтении (§3.1).
3. Дедуп-метки — тот же паттерн `notified_/checked_` + `staleMarkKeys` (переиспользовать как есть [VERIFIED: ScheduleCheckWorker.kt:141-154]).
4. Уведомление «Расписание на <день> опубликовано (N пар)» — адаптация `NotificationHelper.showScheduleReleaseNotification` [VERIFIED: NotificationHelper.kt:46-50].
5. `WidgetUpdateHelper.updateAllWidgets` после успеха [VERIFIED: ScheduleCheckWorker.kt:81].
6. Регистрация: `MpkWorkManagerHelper.setupTeacherSync()` (15 мин период, NetworkType.CONNECTED — паттерн :15-41), вызов из MainActivity только для teacher-flavor [VERIFIED: MainActivity.kt:49-53 — точка уже есть].

Опционально (не MVP): при force-режиме дня — перечитывать док для подхвата внутридневных замен (§3.3).

---

## 6. Сборка / релиз

- **applicationId**: `com.aistudio.mpkschedule.vbyqzt.teacher` через `applicationIdSuffix = ".teacher"` [VERIFIED паттерн: build.gradle.kts:16,26]. Отдельная запись в Play, отдельная песочница.
- **versionName/Code**: `versionNameSuffix = "-teacher"`, versionCode общий счётчик (20014+) — простое решение; независимая нумерация не нужна, пока релизы синхронны [ASSUMPTION].
- **Подпись**: тот же release signingConfig (keystore.properties / env CI) [VERIFIED: build.gradle.kts:51-71] — никаких новых ключей; отдельный upload-ключ нужен только если отдельная Play-консоль [ASSUMPTION].
- **R8**: `isMinifyEnabled/isShrinkResources` уже на [VERIFIED: build.gradle.kts:78-80]; proguard-rules.pro общий и покрывает виджеты/receiver'ы/enum-имена в prefs [VERIFIED: proguard-rules.pro:3-17]. Новый teacher-enum (op в teacher_overrides, если enum) хранить в БД как TEXT-строки — правило keep enum уже есть, но проще не завязываться.
- **students.csv гейт**: teacher source set НЕ включает admin-ассеты (механика flavor source sets), плюс релизный чек-лист: `unzip -l app-teacher-release.apk | grep -c students.csv` = 0 — та же процедура, что для standard (конвенция SKILL.md п.3). Второй гейт: `grep -c admin` в списке ассетов не нужен — код Students* в APK попадёт, но без актива возвращает empty [VERIFIED: StudentsRepository.kt:25-35]; R8, вероятно, вырежет недостижимое из-за FLAVOR-константы [ASSUMPTION].
- **Иконка/название**: `src/teacher/res/mipmap-*` (оверлей как у admin [VERIFIED: ls app/src/admin/res]) + `src/teacher/res/values/strings.xml` с `app_name` «Мой Политех Преподавателю» — манифест-оверлей НЕ нужен (admin обошёлся без него).
- **CI-гейты**: `:app:testTeacherDebugUnitTest` (все 784 теста прогоняются и на teacher — общий код) + `compileTeacherDebugKotlin` после каждой волны.

---

## 7. План работ волнами (2–3 агента, файлы не пересекаются)

После каждой волны — сразу `compileStandardDebugKotlin && compileTeacherDebugKotlin` (конвенция оркестрации: bun падает на 4+ агентах).

**Волна 0 — каркас (1 агент, 0.5 дня).**
`app/build.gradle.kts` (flavor teacher), `src/teacher/res/**` (иконка-заглушка, app_name), `MainActivity.kt` (единственная ветка FLAVOR→TeacherRoot), новый `ui/teacher/TeacherRoot.kt` (заглушка). Никто больше ничего не трогает.

**Волна 1 — ядро расписания = MVP (2 агента, 1 день).**
- A: `ui/teacher/TeacherHomeScreen.kt` + `ui/teacher/TeacherViewModel.kt` — портированный TeacherScheduleTab (сегодня/завтра/календарь, force-refresh) И вынос `loadTeacherDay/teacherTargetCalendar/defaultTeacherDayOffset/teacherSyncErrorMessage/TeacherDayLoad` из `CollegeScreen.kt` в `data/repository/TeacherDayLoader.kt` (CollegeScreen правит ТОЛЬКО агент A).
- B: `ui/teacher/TeacherOnboardingScreen.kt` (выбор себя: поиск по teachers.csv, паттерн TeachersPage+FuzzySearch) + `util/TeacherProfileStore.kt` (prefs).
**Здесь уже выкатывается MVP за ~1 день** (плюс фикс однофамильцев из §3.1 — одна строка в фильтре выборки, делает A).

**Волна 2 — фон и оболочка (3 агента, 1.5 дня).**
- A: `worker/TeacherScheduleWorker.kt` + правка `MpkWorkManagerHelper.kt` (setupTeacherSync) + адаптация NotificationHelper-вызова.
- B: `ui/teacher/TeacherNav.kt` — NavHost teacher-сборки (Расписание/Звонки/Карта/Профиль): реюз BellsScreen (adapt), CollegeMapScreen (as-is), AboutTeacherTab без insights; + `ui/screens/BellsScreen.kt` (минимальная adapt-правка — только B).
- C: `widget/*` — teacher-источник для TodaySchedule/NowNext/BellCountdown (lastGroupLessonForToday → teacher-аналог), дефолт Scope.COLLEGE для BellCountdownNotifier.

**Волна 3 — данные+ (2 агента, 1.5 дня).**
- A: Room v10 — `entity/TeacherOverrideEntity.kt`, `dao/TeacherOverrideDao.kt`, `MIGRATION_9_10`, `MigrationV9ToV10Test`, оверлей в TeacherDayLoader; UI правок в TeacherHomeScreen (слот «отменён/замена»).
- B: `ui/teacher/TeacherWeekScreen.kt` — «моя неделя»: синк пн–сб, нагрузка (пар/нед), список групп×предметов (вычисляемое).

**Волна 4 — релиз (1 агент, 0.5–1 дня).**
Финальная иконка/название, `assembleTeacherRelease`, гейты (students.csv=0, установка на устройство, smoke «сегодня/завтра/календарь», R8-лог без warnings про новое), Play-метаданные, прогон всех юнит-тестов на 3 flavor.

**Итого: MVP — 1–1.5 дня (волны 0–1); полноценное приложение — ~5–6 рабочих дней** [ASSUMPTION по всем оценкам дней]. За неделю реально: MVP + воркер + уведомление + виджет + звонки/карта + релиз-гейт; неделя+2 дня — замены и недельная нагрузка.

---

## 8. Технические риски (топ-5)

| # | Риск | Вероятность/ущерб | Mitigation |
|---|---|---|---|
| 1 | **Однофамильцы сливаются** через surnameKey-выборку teacher_lessons (Ерёменко, Савицкая ×2 в CSV) — преподаватель видит чужие пары | средняя / высокий (доверие к продукту) | Фильтр строк через `matchTeacher(row.teacherName)==selected` при чтении (§3.1); тест на фикстуре с двумя «Савицкая С.И./Савицкая Н.П.» |
| 2 | **Формат дока преподавателей дрейфнет** (новая вёрстка таблицы, порядок «И.О. Фамилия», ячейки-замены «Иванов/Петров») — единственный парсер на оба приложения | средняя / высокий | Живые тесты уже есть (TeacherScheduleLiveDocTest, RealSiteDocParserTest); добавить teacherRows-сплит по «/»; `matchesRequestedDate`-гейт уже защищает от подмены даты [VERIFIED: MpkNetworkClient:368-379] |
| 3 | **Забытая миграция 9→10** при общих таблицах: fallbackToDestructiveMigration тихо стирает student_tasks у УЧЕНИКОВ (уже была такая конвенция-ловушка) | низкая / критический | Обязательный `MigrationV9ToV10Test` + `room.schemaLocation` уже версионируется [VERIFIED: build.gradle.kts:112-114]; CI-гейт `:app:testStandardDebugUnitTest` на каждой волне |
| 4 | **Внутридневные замены не видны** (кэш-гейт getPresentDates не перечитывает день) | высокая / средний | Pull-to-refresh с `force=true` (параметр уже есть); в воркере — force для сегодняшней даты в окне публикации (14:00–18:00) [ASSUMPTION про окно] |
| 5 | **FLAVOR-гейты расползаются** по общим экранам (сейчас 2 точки у admin; teacher соблазняет добавить ещё десяток) → общий код превращается в if-лапшу, риск развала | средняя / средний (долгосрочный) | Правило: teacher-ветвление ТОЛЬКО в MainActivity (одна точка) + свой NavHost в `ui/teacher/`; общие экраны не знают о flavor'е (никаких `if FLAVOR=="teacher"` внутри CollegeScreen/BellsScreen — только параметры); code-review-чек на каждый PR |

Вне топ-5, но отметить: 784 теста прогоняются и для teacher-flavor бесплатно (общий код) — регрессия ученического приложения от teacher-работ маловероятна при соблюдении правила из риска 5.
