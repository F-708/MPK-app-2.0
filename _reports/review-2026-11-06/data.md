# Аудит домена «Данные» — «Мой Политех» (Room v11)

Дата: 2026-11-06 · Ветка: `v2.4-stable` · Режим: read-only, код не менялся
Объект: `data/local/**`, `data/repository/**`, `worker/**`, `content/**`, `dataeditor/**`, `util/BackupManager.kt`, `res/xml/*backup*`, `app/proguard-rules.pro`, `app/schemas/**`, тесты `app/src/test/java/com/politex/**`

## Вердикт

| Вопрос | Вердикт |
|---|---|
| Миграции 1→11 | **Покрытие полное и согласовано со схемами.** Все 10 шагов написаны и зарегистрированы (`MpkDatabase.kt:285`), на каждый есть тест (`MigrationV1ToV2Test` … `MigrationV10ToV11Test`), схемы 4–11 экспортированы, `entity`-аннотации совпадают с SQL миграций (проверено по `11.json`, включая `defaultValue`: `contentHash` = `DEFAULT ''`, `weightsEnabled` = `DEFAULT 1`, `roundingMode` = `DEFAULT '0.1'`). Пропущенного шага нет, «дырки» в пути апгрейда нет. |
| Защита от потери данных | **Отсутствует.** `fallbackToDestructiveMigration()` остаётся включённым, поэтому единственный реальный сценарий тихой потери — не «апгрейд», а *отсутствие пути* (downgrade/восстановление более новой БД/будущий bump без миграции): Room удалит `mpk_schedule.db` и создаст пустую базу без сообщения. |
| Риск тихой потери ДЗ | **Средний (P1), не «сейчас», а «при событии».** В обычной эксплуатации (обновление v10→v11) ДЗ переживает миграцию — это покрыто тестами (`MigrationV10ToV11Test`, «задания пережили миграцию»). Потеря происходит: (1) при destructive fallback (весь `student_tasks` исчезает разом, UI покажет пустой список как норму); (2) при восстановлении JSON-бэкапа из-за дедупликации (`BackupManager.kt:378`) и нетранзакционного импорта; (3) автоматический Android-бэкап не является страховкой: в правилах есть только `mpk_schedule.db` без `-wal`, а половина настроек не бэкапится вообще. |
| Общая оценка слоя | Схема и кэш расписания спроектированы аккуратно (погрупповая замена в `@Transaction`, защита от неполного дня, дедуп воркеров через `enqueueUniquePeriodicWork`), но **целостности мешают**: отсутствие FK/каскадов, отсутствие индексов у `student_tasks`, нетранзакционный restore, дефектный прунинг `teacher_lessons`, `runBlocking` в виджетах. |

Находок: **P1 — 3, P2 — 7, P3 — 8.** P0 (потеря данных в штатном сценарии текущей сборки) не выявлено.

---

## Находки

### [P1] Destructive fallback включён рядом с полной цепочкой миграций / Где: `app/src/main/java/com/politex/data/local/MpkDatabase.kt:286`
**Симптом.** При любом событии, для которого Room не найдёт путь миграции (установка более старого APK поверх более нового, восстановление БД с `user_version` выше текущего — например, из облачного бэкапа устройства другой сборки, будущий `version = 12` без миграции), база `mpk_schedule.db` молча удаляется и создаётся пустой. Пользователь теряет ДЗ, оценки, материалы, аннотации, правки преподавателя. Никакого сообщения, диалога или автоснимка нет; данные не восстановимы, потому что собственный бэкап (`BackupManager`) создаётся только вручную.
**Root cause.** `fallbackToDestructiveMigration()` — глобальный выключатель «на все версии»; использован дефолтный вариант, а не `fallbackToDestructiveMigrationFrom(...)` с явным перечнем, и не обёрнут проверкой версии. При этом миграции 1→11 закрыты, поэтому destruct-путь не нужен вовсе: он страховал период разработки, а в релиз ушёл как штатный режим.
**Фикс.** Убрать `fallbackToDestructiveMigration()` из релизной конфигурации: для неизвестного пути падать в явный `IllegalStateException`, ловить в `Application` и показывать экран «данные повреждены → восстановить из бэкапа/сбросить» вместо тихого стирания. Если destruct нужен для конкретных legacy-версий — только `fallbackToDestructiveMigrationFrom(listOf(/* явные */))`. Дополнительно: перед открытием БД, если `user_version` в файле > `MpkDatabase.version`, писать автоснимок `buildBackup()` в `filesDir` и предупреждать пользователя.
**Тест.** Нет ни одного (`Select-String "fallbackToDestructiveMigration"` по `app/src/test` → 0). Нужен тест: подложить БД с `PRAGMA user_version = 12`, открыть `MpkDatabase` и ожидать `IllegalStateException` (а не пустую БД); плюс Robolectric-тест конфигурации, что destructive-путь выключен.

### [P1] Авто-бэкап Android покрывает половину настроек и снимает живую WAL-базу / Где: `app/src/main/res/xml/data_extraction_rules.xml:10-15`, `res/xml/backup_rules.xml:8-9`
**Симптом.** После переноса на новое устройство или переустановки восстанавливаются только виджет-настройки и расписание; теряются: тема оформления, режим преподавателя (имя), масштаб UI, канал обновлений, режим отображения расписания, переименования предметов, кураторы, флаги подсказок, избранные преподаватели, профиль преподавателя. Пользователь видит «приложение как после установки», хотя `allowBackup="true"` (`AndroidManifest.xml:17`).
**Root cause.** В правилах ровно два `include`: `sharedpref/mpk_widget_prefs.xml` и `database/mpk_schedule.db`. Все остальные хранилища не перечислены: `mpk_ui_prefs` (`AppThemeStore.kt:10`, `TeacherModeStore.kt:7`, `UiScaleStore.kt:17`, `ScheduleDisplayPrefs.kt:7`, `UpdateChannelStore.kt:51`), `mpk_subject_renames` (`SubjectRenameStore.kt:9`), `mpk_curators` (`CuratorStore.kt:9`), `mpk_hints` (`HintFlags.kt:6`), `teachers_favorites` (`TeachersRepository.kt:77`), `mpk_teacher_profile` (`TeacherScheduleWorker.kt:67`), `content_pack_prefs` (`content/ContentPackStore.kt`). Второй дефект: снимок `mpk_schedule.db` делается без `mpk_schedule.db-wal` / `-shm`, а Room по умолчанию работает в WAL — незачекпойнченные транзакции (свежие ДЗ/оценки) в бэкап не попадают, восстановление даёт состояние на момент последнего чекпойнта.
**Фикс.** Перечислить все prefs-файлы (или `domain="sharedpref" path="."` с исключением служебных: `notified_*`/`checked_*` живут в `mpk_widget_prefs`, их придётся фильтровать иначе — вынести служебные ключи в отдельный файл `mpk_service_prefs`). Для БД: включить `path="."` в домене `database` (заберёт wal/shm) либо кастомный `BackupAgent` с `wal_checkpoint(TRUNCATE)` перед снимком. Дополнительно рассмотреть `android:backupInForeground="true"`.
**Тест.** Нет. Нужен юнит-тест, который парсит `data_extraction_rules.xml`/`backup_rules.xml` и сверяет список `include` с фактическим набором имён prefs, встречающихся в коде (константы `PREFS*`).

### [P1] Собственный JSON-бэкап теряет настройку оценок, правки преподавателя и прогресс чтения / Где: `app/src/main/java/com/politex/util/BackupManager.kt:29-38`, `:53-135`
**Симптом.** Единственный «ручной» способ переноса данных не содержит: `grade_config` (приоритеты категорий, веса 25/25/50/100, режим округления — часы пользовательской настройки), `teacher_overrides` (правки преподавателя: «кабинет другой», «пары нет», «заменяет другой»), `material_bookmarks` + `material_annotations` + `lastPage` (закладки, рисунки на страницах, прогресс чтения). После восстановления оценки считаются по дефолтной формуле, все пометки в материалах пропали, страницы не открываются там, где остановился.
**Root cause.** `BackupSection` = `{SCHEDULE_HISTORY, TASKS, GRADES, SUBJECT_NAMES, SETTINGS}`, где `GRADES` покрывает только `grade_entries`, а `SCHEDULE_HISTORY` — только `lessons`. Новые таблицы (v8 `material_annotations`, v10 `teacher_overrides`, v7→v8 поля `grade_config`) добавлялись без расширения секций; версия формата при этом поднималась (`FORMAT_VERSION = 2`), но состав не сверялся со схемой БД.
**Фикс.** Добавить секции `GRADE_CONFIG`, `TEACHER_OVERRIDES`, `MATERIALS_META` (bookmarks/annotations/lastPage/rotationDeg/subjectName/archived) и поднять `FORMAT_VERSION` до 3 с обратной совместимостью при импорте (неизвестные секции — предупреждение, а не ошибка; сейчас так и есть — `BackupManager.kt:169`). Заодно добавить `SUBGROUP`, `HINTS`, `CURATOR`.
**Тест.** `BackupSectionsTest` (7 тестов) и `BackupRoundTripTest` (5 тестов) проверяют только существующие секции; теста «полный экспорт содержит все таблицы БД» нет — именно поэтому пробел не был замечен.

### [P2] Дедупликация при импорте бэкапа молча схлопывает разные задания и не возвращает отметки о выполнении / Где: `app/src/main/java/com/politex/util/BackupManager.kt:378`, `:251`, `:339`
**Симптом.** Два разных задания одного предмета с одинаковым заголовком и дедлайном (типовой случай: заголовок по умолчанию «Домашнее задание», одна дата) при восстановлении превращаются в одно; пользователь видит «Уже есть, пропущено: 1» и считает, что всё на месте. Отдельно: для уже существующего задания `isCompleted` из бэкапа **не применяется** (запись отбрасывается как дубль) — восстановление не возвращает галочки «сделано», хотя это ключевое состояние ДЗ.
**Root cause.** `dedupeKey()` = `groupName|subjectName|title|deadlineDate` — без `taskType`, `createdAt`, `id`; в JSON `id` не пишется вовсе (`taskToJson`, `BackupManager.kt:136-147`). Стратегия импорта — «только добавить, никогда не обновлять».
**Фикс.** Писать `id` и `taskType` в JSON; для записей с `id` — upsert по `id` (сохраняет и `isCompleted`); для legacy-файлов без `id` — включить `taskType` и `createdAt` в ключ, а совпадение по ключу трактовать как «обновить существующее, если в бэкапе `isCompleted`/`title` новее», а не «пропустить».
**Тест.** Нет теста на два задания с одинаковым `title`+`deadline` и нет теста на сохранение `isCompleted` при восстановлении на непустую БД.

### [P2] Восстановление бэкапа не транзакционно / Где: `app/src/main/java/com/politex/util/BackupManager.kt:161-375`
**Симптом.** Исключение на середине файла (битый элемент массива, `OutOfMemoryError` на большом `strokesJson`, убийство процесса) оставляет БД в полу-восстановленном состоянии: часть заданий/оценок уже вставлена, а вызов возвращает `Result.failure(e)`. Повторный импорт того же файла не дозальёт пропущенное: ранние записи попадут под «Уже есть, пропущено» (см. находку выше). Отдельно: задача и её напоминания пишутся разными вызовами (`:255` и `:266`) — задача без напоминаний остаётся навсегда, чинить вручную.
**Root cause.** Импорт — последовательность одиночных DAO-вызовов без `db.withTransaction { }`; частичный откат не предусмотрен.
**Фикс.** Обернуть весь `restoreBackup` в `db.withTransaction { ... }`; результат (`ImportResult`) собирать внутри и возвращать после успешного коммита.
**Тест.** `BackupRoundTripTest`/`BackupSectionsTest` проверяют только успешный путь. Нужен тест с инъекцией исключения на середине массива: после падения БД должна быть в исходном состоянии.

### [P2] У `student_tasks` нет ни одного индекса под реальные запросы / Где: `app/src/main/java/com/politex/data/local/entity/StudentTaskEntity.kt:9-21`, `data/local/dao/TaskDao.kt:12-25`
**Симптом.** Все три экранных запроса фильтруют по `groupName` и сортируют по `isCompleted, deadlineDate`, а `getPendingTasksForGroup` — по `groupName, isCompleted = 0, deadlineDate`. План выполнения — `SCAN student_tasks` + temp B-tree на сортировку; на каждом открытии списка ДЗ и в каждом обновлении виджета. При накопленной истории (4 курса, 200+ заданий) это заметно, при 1–2 тыс. — секунды.
**Root cause.** В `@Entity` не объявлено `indices`; миграции тоже не создают ни одного индекса для этой таблицы (в `11.json` `student_tasks` без `indices`).
**Фикс.** `@Entity(tableName = "student_tasks", indices = [Index("groupName", "isCompleted", "deadlineDate")])` + миграция **11→12** (не забыть! см. P3 про отсутствие проверки схем). Для `deadlineDate` как TEXT в формате `dd.MM.yyyy` сортировка лексикографическая и совпадает с календарной только внутри одного года — при индексе это станет заметнее; надёжнее добавить `deadlineEpoch: Long`.
**Тест.** `TaskUpsertTest`, `TaskUndoRoundTripTest` не проверяют план. Добавить тест с `EXPLAIN QUERY PLAN` (или `Room.inMemoryDatabaseBuilder` + `query("EXPLAIN QUERY PLAN ...")`) и ассертом на отсутствие `SCAN`.

### [P2] `teacher_lessons` растёт без ограничения — прунинг идёт по датам из `lessons` / Где: `app/src/main/java/com/politex/data/repository/ScheduleRepository.kt:658-664`
**Симптом.** `pruneOldLessons` вычисляет `staleDates` из `lessonDao.getAllDistinctDatesSync()` и тем же списком удаляет `teacher_lessons`. Даты, для которых синкнулось только расписание преподавателей (группового документа нет, или он уже вычищен), не удаляются **никогда**: таблица копит ~40 строк на преподавателя в день бесконечно. Через год — десятки тысяч мёртвых строк, деградация `getTeachersOnDate`/`getPresentDates`.
**Root cause.** Один источник дат для двух независимых таблиц; у `teacher_lessons` нет собственного прунинга, хотя `TeacherScheduleWorker` пишет её каждые 15 минут в окне 6:30–21:00 и на завтрашние даты.
**Фикс.** Добавить в `TeacherLessonDao` собственный `@Query("DELETE FROM teacher_lessons WHERE dateString != '' AND <граница>")` — сравнение по вычисленной границе в SQL (`substr`/переданный список), а не по списку из `lessons`. Второй вариант: отдельный `SELECT DISTINCT dateString FROM teacher_lessons`, фильтрация в Kotlin, `deleteForDates`.
**Тест.** `TeacherScheduleSyncChainTest` (12 тестов) покрывает идемпотентность, «пустой ответ не стирает кэш», «мусорный разбор не стирает кэш», однофамильцев — но **прунинга не касается вообще**. Нужен тест «дата старше 60 дней без группового расписания → строки преподавателей удалены».

### [P2] Перезапись дня не защищена от неполного разбора для одной группы / Где: `app/src/main/java/com/politex/data/repository/ScheduleRepository.kt:668-679`, `LessonDao.replaceForDate`
**Симптом.** Защита от неполного документа есть только на уровне «сколько групп разобрано» (`isFullDayDocument`, `:34-37`, порог `catalogSize+1)/2`) — но не на уровне «сколько уроков у конкретной группы». Если парсер после изменения вёрстки документа вернёт для группы 3 урока вместо 6, `deleteLessonsForDate` + `insertLessons` внутри транзакции заменит 6 на 3: пользователь увидит обрезанное расписание, старые данные не восстановятся, а статус синка будет «успешно загружено (3)».
**Root cause.** `replaceForDate` — безусловный `delete + insert` per `(group, date)`; до записи не сравнивается ни количество уроков, ни «покрытие» номеров пар с предыдущим кэшем.
**Фикс.** Перед заменой: `oldCount = getLessonCountForDate(group, date)`; если `new.size < oldCount && new.size < oldCount/2 + 1` — не перезаписывать, писать диагностику (`SyncDiagnosticInfo`) и оставлять кэш (как уже сделано для `teacher_lessons` при пустом/мусорном разборе). Либо мержить по `lessonNumber` (обновлять совпавшие слоты, не удаляя отсутствующие).
**Тест.** Нет. `ScheduleLessonMergeTest` (37 тестов) — про *отображение* (склейка соседних пар), не про запись в кэш. Нужен тест: «кэш 6 уроков + синк с 3 → кэш не деградировал (или деградировал с диагностикой)».

### [P2] Виджеты открывают БД синхронно на main thread (`runBlocking`) / Где: `app/src/main/java/com/politex/widget/TeacherWidgetData.kt:100`, `widget/NowNextWidgetProvider.kt:268`, `widget/BellCountdownWidgetProvider.kt:220`
**Симптом.** `AppWidgetProvider.onUpdate`/`BroadcastReceiver.onReceive` вызывают `runBlocking { snapshot(context) }`, который читает Room. На «холодном» старте после перезагрузки это первое открытие БД, и в него попадает **миграция 1→11** — на устройстве, где год не обновлялись, это сотни миллисекунд, которых достаточно для ANR (`BroadcastReceiver` — 10 с, виджет-апдейт в лаунчере — заметный лаг).
**Root cause.** Синхронный API виджетов + `runBlocking` как «мостик» к suspend-репозиторию. `AppGraph.init` (`MpkApplication.kt:18-27`) вызывается из `onCreate` и строит объект БД, но `Room.databaseBuilder(...).build()` **не открывает файл** — открытие и миграции откладываются до первого запроса, и этим первым запросом как раз оказывается `runBlocking` в приёмнике.
**Фикс.** Настоящий прогрев: в `MpkApplication.onCreate` — `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { MpkDatabase.getInstance(this@…).openHelper.writableDatabase }` (любой реальный запрос, не только `getInstance`), чтобы миграция прошла вне критического пути. В приёмниках — `goAsync()` + корутина, `runBlocking` убрать. `allowMainThreadQueries` в проекте не включён (проверено, нигде нет) — обычные запросы на main не пройдут, но `runBlocking` этот предохранитель обходит.
**Тест.** Нет. Минимум — статический чек в тестах-линтерах (`runBlocking` в `widget/**` запрещён).

### [P2] `teacher_overrides` не привязаны к преподавателю / Где: `app/src/main/java/com/politex/data/local/entity/TeacherOverrideEntity.kt` (unique index `dateString`+`lessonNumber`), `data/local/dao/TeacherOverrideDao.kt`
**Симптом.** Ключ правки — `(дата, номер пары)`, без преподавателя. При смене преподавателя в owner-режиме (или если на устройстве поработали два преподавателя) правки первого молча применяются ко второму, а следующая правка того же слота через `upsert` с `OnConflictStrategy.REPLACE` затирает предыдущую без следа. Пользователь видит «чужой» кабинет/отмену пары и не понимает, откуда.
**Root cause.** Модель предполагает «один преподаватель на устройство», но это нигде не зафиксировано инвариантом: `TeacherModeStore.setTeacher` (`util/TeacherModeStore.kt`) не чистит таблицу при смене профиля.
**Фикс.** Либо добавить `teacherSurnameKey` в индекс/ключ (миграция), либо при смене профиля очищать `teacher_overrides` (и предупреждать пользователя). Сейчас — как минимум задокументировать инвариант и очищать при `setTeacher`.
**Тест.** `TeacherInsightsOverrideTest` проверяет матчинг правки к уроку, но не смену профиля. Нужен тест «смена преподавателя → overrides предыдущего не видны новому».

### [P3] Enum-ы в SharedPreferences и Room защищены `-keep`, но `TaskType` хранится как русская строка с fallback в HOMEWORK / Где: `app/proguard-rules.pro` (блок `-keepclassmembers enum *`), `data/model/TaskType.kt:16-20`
**Симптом (по вопросу 7, R8).** Риск переименования закрыт: при `isMinifyEnabled = true` (`app/build.gradle.kts:138`) правило `-keepclassmembers enum * { values(); valueOf(); entries(); <fields>; }` сохраняет имена констант, поэтому `SubgroupPref.name` (`SubgroupSettings.kt`), `GradeCategory.*.name` (`GradeAggregator.kt`), `BellCountdownNotifier.Style/Theme/Scope` (`.from(...)` ищет по `name`), `AppTheme.id`, `MaterialKind.name` (`MaterialItemEntity.kt:48-49`) переживают R8. Проверять дополнительно нечего.
**Остаточный риск.** `student_tasks.taskType` хранит **displayName** («Домашнее задание» и т.п.), а `TaskType.fromDisplayName` при неудаче молча возвращает `HOMEWORK`. Любое переименование подписи в будущей версии превратит все сохранённые лабораторные/курсовые в «Домашнее задание» — тихо и необратимо. Это не R8, а отсутствие маппинга.
**Фикс.** Хранить `name()` (`HOMEWORK`/`LAB_WORK`/…), миграция 11→12 с `UPDATE student_tasks SET taskType = CASE …`; `fromDisplayName` оставить только для чтения legacy-значений с явной ветвью «не распознано → предупреждение», а не fallback.

### [P3] `MaterialImporter.rollback` оставляет сиротские аннотации / Где: `app/src/main/java/com/politex/util/MaterialImporter.kt:510-520`
**Симптом.** Откат неудавшегося импорта удаляет файлы, `material_bookmarks` и `material_items`, но не `material_annotations`. Строки остаются навсегда (FK нет), занимают место (JSON-штрихи по страницам), а при совпадении `id` в будущем могут «всплыть» на чужом материале.
**Root cause.** Дублирование логики каскада: в `MaterialDao.deleteItemWithBookmarks` (`@Transaction`) удаляются bookmarks + annotations + item, а в `rollback` каскад написан вручную и без annotations.
**Фикс.** Вызывать `dao.deleteItemWithBookmarks(itemId)` вместо ручной последовательности (или добавить `dao.deleteAnnotationsForItem(itemId)` перед `deleteItemById`).
**Тест.** `MaterialDaoTest` (8.2 KB) проверяет `deleteItemWithBookmarks`; откат импорта — нет.

### [P3] Нет внешних ключей и каскадов: возможны сироты в 3 таблицах / Где: `data/local/entity/TaskReminderEntity.kt`, `MaterialBookmarkEntity.kt`, `MaterialAnnotationEntity.kt`
**Симптом.** `task_reminders.taskId`, `material_bookmarks.itemId`, `material_annotations.itemId`, `material_items.folderId` — «логические» FK без `ForeignKey`-аннотаций и без `ON DELETE CASCADE`. Сейчас все удаления аккуратные (репозиторий задач удаляет напоминания, DAO материалов — bookmarks/annotations), но инвариант держится на дисциплине вызовов, а не на схеме.
**Конкретные дыры.** `TaskDao.clearTasksForGroup` (`TaskDao.kt:53`) не удаляет напоминания (сейчас вызовов нет — мёртвый код, но ловушка); `LessonDao.replaceScheduleForGroup`/`ScheduleRepository.clearSchedule` (`ScheduleRepository.kt:692-694`) чистит **все** даты группы — при появлении вызова это затрёт историю (сейчас тоже без вызовов).
**Фикс.** Либо `@ForeignKey(onDelete = CASCADE)` (потребует переноса таблиц в миграции — дорого), либо задокументировать инвариант и добавить периодическую «сборку мусора» (`DELETE FROM task_reminders WHERE taskId NOT IN (SELECT id FROM student_tasks)` в `ReminderCheckWorker`). Мёртвые API (`clearTasksForGroup`, `replaceSchedule`) — удалить или пометить `@Deprecated` с предупреждением в KDoc.

### [P3] `cachedCatalog` — не `@Volatile`, читается из воркера и UI / Где: `app/src/main/java/com/politex/data/repository/ScheduleRepository.kt:242`
**Симптом.** Поле кэша каталога групп (`List<String>?`) пишется лениво из разных потоков (`syncScheduleDetailed` в UI-корутине и `ScheduleCheckWorker`), без `@Volatile`/`synchronized`. Практический эффект — лишний пересчёт каталога (гонка «последний выиграл»), но формально это data race; соседний `incompleteDayCache` (`:244`) сделан корректно (`ConcurrentHashMap`), т.е. решение уже известно.
**Фикс.** `@Volatile private var cachedCatalog` (или `AtomicReference`).

### [P3] `incompleteDayCache` — «день неполный» живёт до перезапуска процесса / Где: `app/src/main/java/com/politex/data/repository/ScheduleRepository.kt:244`, `:385`
**Симптом.** Если документ дня разобрался меньше чем на половину каталога, дата помечается неполной и `syncScheduleDetailed` для неё вообще не идёт в сеть (`:385`) — пока дата не «дополнится» в рамках того же процесса. Для пользователя это «расписание не опубликовано», хотя оно уже выложено; помогает только ручное обновление (`force`) или перезапуск. Для виджетов не страшно — они ходят через `syncScheduleForDate`, где кэша нет, поэтому состояние самовосстанавливается.
**Фикс.** Добавить TTL (например, 30 минут) или хранить в prefs с меткой времени; при `force` — сбрасывать.

### [P3] Сортировка дат через `substr` в SQL — индекс не используется / Где: `data/local/dao/LessonDao.kt` (`getDistinctDatesForGroup`)
**Симптом.** `ORDER BY substr(dateString,7,4) DESC, substr(dateString,4,2) DESC, substr(dateString,1,2) DESC` — сортировка по вычисляемым выражениям: индекс `index_lessons_groupName_dateString` используется только на фильтр, сортировка идёт temp B-tree. Экран выбора даты пересчитывает это при каждом изменении таблицы (Flow).
**Фикс.** Хранить дату в ISO (`YYYY-MM-DD`, лексикографическая сортировка = календарная) или отдельное поле `dateEpoch: Long` с индексом. Сейчас терпимо (объём на группу — сотни строк), но это тот же корень, что и в P2 про `deadlineDate`.

### [P3] Отсутствуют схемы Room 1–3 → `MigrationTestHelper` неприменим к первым шагам / Где: `app/schemas/com.politex.data.local.MpkDatabase/` (есть `4.json`…`11.json`, нет `1.json`, `2.json`, `3.json`), `MigrationV1ToV2Test.kt:23-60`, `MigrationV2ToV3Test.kt`
**Симптом.** Тесты 1→2 и 2→3 создают старую БД **сырым SQL, написанным от руки** (`createV1Database`/`createV2Database` через `FrameworkSQLiteOpenHelperFactory`), т.е. тест проверяет миграцию против собственного предположения о старой схеме, а не против реальной. Если историческая v1-схема отличалась (порядок/наличие колонки), тест останется зелёным, а у пользователя апгрейд упадёт на валидации Room.
**Дополнительно.** Ни один тест не вызывает `MigrationTestHelper`/`runMigrationsAndValidate` (проверено по всем `MigrationV*Test`) — валидацию конечной схемы делает только открытие `MpkDatabase.getInstance()`, и при расхождении Room бросит `IllegalStateException` без указания, какой именно шаг виноват. `MigrationV2ToV3Test` — единственный без сквозного `getInstance()`, т.е. цепочка 2→11 целиком не проверена.
**Фикс.** Восстановить `1.json`–`3.json` (сгенерировать из исторического тега или из `CREATE TABLE` в тестах) и перевести тесты на `MigrationTestHelper.runMigrationsAndValidate`; добавить один параметризованный тест «каждая версия N из 1..11 → 11 → схема валидна, данные живы».

### [P3] Стиль миграций: `IF NOT EXISTS` маскирует расхождения структуры / Где: `MpkDatabase.kt:71,84,94-98,107,126-170,181-199,224-242,256-266,273`
**Симптом.** Все `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` в миграциях. Если объект уже существует с другой структурой, миграция «успешна», а падение произойдёт позже — на валидации схемы Room, с невнятным сообщением и без указания шага. Для свежих миграций `IF NOT EXISTS` не нужен (Room гарантирует однократность).
**Фикс.** В новых миграциях — без `IF NOT EXISTS`; для существующих — оставить, но добавить тест-валидацию после каждого шага (см. предыдущую находку).

---

## Карта таблиц

| Таблица | Ключ | Объём роста | Индексы | Кто пишет / читает |
|---|---|---|---|---|
| `lessons` | PK `id` (autogen); логический `(groupName, dateString, lessonNumber)` | +`N_групп`×~7 уроков на каждый синканный день (пишутся **все** группы каталога), TTL 60 дней (`pruneOldLessons`). Строки с `dateString=''` (недельная сетка) **не прунятся никогда** → потенциально безгранично | 5: `(groupName,dayOfWeek)`, `(groupName,dateString)`, `dateString`, `roomFirst`, `roomSecond`; **уникального нет** | ScheduleRepository, ScheduleCheckWorker, MpkScheduleParser, BackupManager → LessonDao, TeacherInsights, виджеты, UI |
| `student_tasks` | PK `id`; логический `(groupName, subjectName, title, deadlineDate)` | растёт с вводом пользователя, **не чистится никогда** | **нет ни одного** | TaskRepository, BackupManager → TaskDao, виджеты, экраны ДЗ |
| `task_reminders` | PK `id`; `taskId` (без FK) | 1–3 на задание; удаление только явное в `TaskRepository` | `taskId` | TaskRepository/ReminderCheckWorker |
| `teacher_lessons` | PK `id`; логический `(surnameKey, dateString, lessonNumber)` | ~40 строк/день на преподавателя, каждые 15 мин в окне 6:30–21:00; **прунинг дефектный** (см. P2) | `(surnameKey,dateString)`; уникального нет | ScheduleRepository.syncTeacherScheduleForDate → TeacherLessonDao, ScheduleCheckWorker, виджеты |
| `teacher_overrides` | PK `id`; unique `(dateString, lessonNumber)` | число ручных правок; не прунится; **не привязан к преподавателю** | unique `(dateString,lessonNumber)` | dataeditor/OwnerOverrides UI → TeacherOverrideDao |
| `material_folders` | PK `id` | **мёртвая** (W11: папки убраны из UI), не пополняется | нет | deprecated-код MaterialDao только |
| `material_items` | PK `id`; `folderId` (без FK, deprecated) | материалы пользователя + файлы в `filesDir/materials`; не чистится; `archived=1` копится | `folderId`; **`contentHash` без индекса** (`WHERE contentHash=?`) | MaterialImporter, экраны материалов → MaterialDao |
| `material_bookmarks` | PK `id`; `itemId` (без FK) | закладки; сироты при `rollback` | `itemId` | MaterialDao |
| `material_annotations` | PK `id`; unique `(itemId,pageIndex)` | JSON-штрихи по страницам, растёт с рисованием; сироты при `rollback`; не прунится | unique `(itemId,pageIndex)` | MaterialDao |
| `grade_entries` | PK `id` | число оценок; чистится `deleteBySubject` | **нет** (даже по `subjectName`, хотя есть `entriesForSubject`/`deleteBySubject`) | GradeDao, GradeAggregator, BackupManager |
| `grade_config` | PK `subjectName` | по числу предметов | PK | GradeDao; JSON-строки (`priorityJson`, `weightsJson`) без схемной валидации — битый JSON = потеря настройки |

Всего 11 таблиц, 7 DAO, ни одного `ForeignKey`, ни одного `@TypeConverter` (enum-ы и JSON живут как `String`).

## Матрица миграций 1→11

| Шаг | Что делает | Тест | Риск |
|---|---|---|---|
| 1→2 | `CREATE TABLE teacher_lessons` + индекс `(surnameKey,dateString)` | `MigrationV1ToV2Test` (2 теста, включая сквозной 1→11 и «задание переживает миграцию») | Низкий. Схема v1 написана в тесте от руки (нет `1.json`) — тест проверяет сам себя |
| 2→3 | 5 индексов на `lessons` | `MigrationV2ToV3Test` | Низкий. **Единственный тест без сквозного `getInstance()`** → 2→11 целиком не проверен |
| 3→4 | `CREATE TABLE task_reminders` + индекс `taskId` | `MigrationV3ToV4Test` | Низкий |
| 4→5 | `material_folders`, `material_items`, `material_bookmarks` + 2 индекса | `MigrationV4ToV5Test` | Низкий |
| 5→6 | `grade_entries`, `grade_config` (PK `subjectName`) | `MigrationV5ToV6Test` | Низкий |
| 6→7 | `ALTER material_items ADD subjectName TEXT NOT NULL DEFAULT ''`, `archived INTEGER NOT NULL DEFAULT 0` + `UPDATE` c коррелированным подзапросом по `folderId` | `MigrationV6ToV7Test` | Средний: `NOT NULL` + `DEFAULT` корректны и совпадают с `@ColumnInfo`; `UPDATE` заполняет из `material_folders` (если папка удалена — `COALESCE ''`). Ок |
| 7→8 | `material_annotations` + unique-индекс, `material_items.textEdited`, `grade_config` (`weightsJson`, `roundingMode '0.1'`, `roundUp`) | `MigrationV7ToV8Test` | Средний (самый широкий шаг — 5 DDL за раз). Значения `DEFAULT` совпадают со схемой `8.json`/`11.json` |
| 8→9 | `grade_config.weightsEnabled INTEGER NOT NULL DEFAULT 1` | `MigrationV8ToV9Test` | Низкий |
| 9→10 | `teacher_overrides` + unique `(dateString,lessonNumber)` | `MigrationV9ToV10Test` | Низкий |
| 10→11 | `material_items.contentHash TEXT NOT NULL DEFAULT ''` | `MigrationV10ToV11Test` (4 теста: колонка есть, старые строки получают `''`, поиск по `''` не матчит, ДЗ живы) | Низкий |

Покрытие: **10/10 шагов имеют тест.** Схемы согласованы: все `defaultValue` из `entity` (`''`, `'0.1'`, `0`, `1`) буквально совпадают с SQL миграций и с `11.json`. Отсутствие шага (гипотетический bump без миграции) приведёт не к падению, а к **тихому удалению БД** — см. P1.

## Топ-риски потери данных (по убыванию)

1. **Destructive fallback + отсутствие автоснимка** (`MpkDatabase.kt:286`). Один «нештатный» путь миграции = вся база исчезает без сообщения. Вероятность низкая (все шаги закрыты), ущерб максимальный и необратимый. Единственный сценарий, где пользователь узнаёт о потере — «всё пропало».
2. **Android-бэкап не является страховкой**: снимается живая WAL-база без `-wal` (свежие записи теряются) и половина настроек не бэкапится вовсе (`mpk_ui_prefs`, `mpk_subject_renames`, `mpk_curators`, `mpk_hints`, `teachers_favorites`, `mpk_teacher_profile`). При переезде на новое устройство «как было» не восстановится.
3. **Импорт JSON-бэкапа**: нетранзакционность + дедуп по `title|deadline` схлопывает задания и не возвращает `isCompleted`. Пользователь считает, что восстановил всё.
4. **Собственный бэкап не содержит `grade_config`/`teacher_overrides`/аннотаций** — настройка оценок и прогресс чтения теряются безвозвратно при любом переносе.
5. **Неполный разбор документа затирает день** для группы (`replaceForDate` без порога «сколько уроков было»): обрезанное расписание вместо отказа от записи.
6. **`TaskType` как русская строка + fallback `HOMEWORK`**: переименование подписи = тихая потеря типа у всех заданий.
7. **Правки преподавателя без привязки к преподавателю** — взаимное затирание при смене профиля.

## Чего не хватает в тестах

**Целостность / БД**
- Нет теста на конфигурацию БД: destructive fallback не включён / неизвестный `user_version` даёт исключение, а не пустую БД (0 упоминаний `fallbackToDestructiveMigration` в тестах).
- Нет теста «все таблицы БД покрыты экспортом» (сравнение ключей `BackupSection` со списком `@Entity`) — из-за этого пропущены `grade_config`, `teacher_overrides`, `material_bookmarks`/`annotations`.
- Нет теста на отсутствие сирот: удаление задания/материала → нет строк в `task_reminders`/`material_annotations`.
- Нет теста на план запроса (`EXPLAIN QUERY PLAN`) ни для `student_tasks`, ни для `grade_entries`, ни для `getDistinctDatesForGroup`.

**Бэкап**
- Нет теста на два задания с одинаковым `title`+`deadline` (схлопывание при импорте).
- Нет теста на сохранение `isCompleted` при восстановлении на непустую БД.
- Нет теста на частичный сбой импорта (атомарность).
- Нет теста совместимости: импорт файла `FORMAT_VERSION = 1` (проверяется только предупреждение о «более новой» версии).
- Нет теста «правила `data_extraction_rules.xml` покрывают все prefs-файлы кода».

**Кэш расписания**
- `SyncCacheFirstTest` (4 теста: кэш-first, `FoundNoGroup` из кэша, `force` в сеть, честный статус при пустом кэше) и `TeacherScheduleSyncChainTest` (12 тестов: идемпотентность, «пустой/мусорный ответ не стирает кэш», чужой день, однофамильцы) — сильный набор, но **только для расписания преподавателей**.
- Нет аналога «пустой/неполный ответ не стирает кэш» для **группового** расписания (там защита только по числу групп, не по числу уроков).
- Нет теста прунинга (`pruneOldLessons`): ни TTL 60 дней, ни дефект «даты только из `lessons`».
- Нет теста на строки с `dateString=''` (недельная сетка): не прунятся, дублируются ли при повторной вставке.
- Нет теста на «двух воркеров одновременно» (ScheduleCheckWorker + TeacherScheduleWorker + импорт материалов) — гонки не воспроизводятся.
- Нет теста на `incompleteDayCache` (подавление сети без TTL).

**Миграции**
- `MigrationV2ToV3Test` — единственный без сквозного прогона до v11.
- Ни один тест не использует `MigrationTestHelper.runMigrationsAndValidate` (валидация конечной схемы делегирована открытию Room, сообщение при расхождении невнятное).
- Нет параметризованного теста «любая версия 1..11 → 11: схема валидна + данные живы» — сейчас 10 отдельных тестов, каждый со своей рукописной старой схемой.
- Схемы `1.json`–`3.json` отсутствуют — переход на `MigrationTestHelper` для первых шагов невозможен без их восстановления.