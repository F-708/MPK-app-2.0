# Ревью: Слой данных (Room, DAO, сущности, миграции, утилиты данных)

_Дата: 2026-10-01. Область: data-layer. Ревьюер: босс (лично)._

## Резюме и вердикт

Слой данных в целом здоров: миграция v1→v2 явная и протестирована, транзакции replace-операций на месте, сортировка дат в SQL сделана умно (substr по dd.MM.yyyy). Главная проблема — **отсутствие индексов на `lessons`** при том, что таблица теперь держит кэш ВСЕХ групп и читается виджетом каждую минуту (DAT-01). Второе — потенциальное накопление дублей недельных шаблонов (DAT-02).

## Критичные баги

### DAT-01 [high/performance] У таблицы `lessons` нет ни одного индекса — full scan каждую минуту
- **Файл**: `data/local/entity/LessonEntity.kt:8` (`@Entity(tableName="lessons")` без `indices`), `data/local/dao/LessonDao.kt` (все запросы фильтруют groupName/dateString/dayOfWeek/room).
- **Root cause**: после Б3 таблица хранит расписание ВСЕХ групп каталога (сотни групп × ~12 уроков × несколько дат = тысячи-десятки тысяч строк). Каждый запрос (`getLessonsForDaySync` из виджета — КАЖДУЮ МИНУТУ, `getLessonsForDate` из UI, `getLessonsForRoomOnDate` из карты, `getAllDistinctDatesSync` из prune) — полное сканирование таблицы. На учительском `teacher_lessons` индекс ЕСТЬ (в миграции), на `lessons` — нет.
- **Фикс**: `@Entity(tableName="lessons", indices=[Index("groupName","dayOfWeek"), Index("groupName","dateString"), Index("dateString"), Index("roomFirst"), Index("roomSecond")])` + **MIGRATION_2_3** с `CREATE INDEX IF NOT EXISTS` для каждого (имена Room: `index_lessons_groupName_dayOfWeek` и т.д. — должны совпадать побайтово, иначе Room упадёт на валидации схемы при открытии). Тест миграции v2→v3 по образцу MigrationV1ToV2Test. Это же устраняет батарейный слив WID-04 частично.

### DAT-02 [high/bug] Недельные шаблоны (dateString="") вставляются без удаления — накопление дублей
- **Файл**: `data/repository/ScheduleRepository.kt:502-513` (`replaceSyncedLessons`): при `dateString.isBlank()` — только `insertLessons(dayLessons)`, без delete; `data/local/dao/LessonDao.kt:58-59` (`@Insert(onConflict=REPLACE)` с autoGenerate PK — REPLACE никогда не срабатывает, id каждый раз новый).
- **Root cause**: каждый повторный синк, возвращающий строки с пустой dateString (старый формат/недельный шаблон), ДОБАВЛЯЕТ дубли вместо замены. `pruneOldLessons` их не удаляет (пустая dateString не матчится DATE-регуляризацией → `isStaleDate=false` → живёт вечно). UI-запросы `getLessonsForDateOrDay` OR-веткой `(dateString='' AND dayOfWeek=...)` вытаскивают ВСЕ дубли → в расписании пары повторяются 2-3 раза.
- **Проверить перед фиксом**: производит ли текущий парсер вообще строки с пустой dateString (applyHeaderDate перештамповывает даты — возможно, ветка мертва). Если да — это «пары-дубликаты» в UI.
- **Фикс**: в `replaceSyncedLessons` для пустой dateString — `lessonDao.replaceWeeklyTemplate(groupName, dayLessons)` (DELETE WHERE groupName=:g AND dateString='' + INSERT, одной транзакцией). + `pruneOldLessons` периодически чистить недельные шаблоны групп, которых нет в каталоге.

## Баги «через раз» / плавающие

### DAT-03 [medium/architecture] `exportSchema=false` — миграции не валидируются Room
- **Файл**: `data/local/MpkDatabase.kt:23`.
- **Root cause**: без экспорта схемы `MigrationTestHelper` не может автоматически проверить, что ручная SQL в MIGRATION_1_2 даёт ТОЧНУЮ схему, ожидаемую Room (индексы, nullability, default-значения). Сейчас спасает самописный MigrationV1ToV2Test через боевой getInstance (Room валидирует схему при открытии — падает при расхождении). Работает, но хрупко: добавление колонки/индекса в v3 без точного совпадения имён = crash при обновлении у всех пользователей.
- **Фикс**: `exportSchema=true` + `room.schemaLocation` в ksp-аргументы; схемы в git. Дёшево, страховка дорогая.

### DAT-04 [medium/bug] Смешение форматов дат: lessons=dd.MM.yyyy, student_tasks.deadlineDate=ISO
- **Файлы**: `LessonEntity.dateString` (dd.MM.yyyy), `TasksScreen.kt:816` (`dueDate` = `isoDate(millis)` = yyyy-MM-dd), `TaskDao:15` (ORDER BY deadlineDate — лексикографически, но для ISO это ХРОНОЛОГИЧЕСКИ верно), `HomeworkMarkers.normalizeIso:129-130` (мост `-`→`.`).
- **Статус**: НЕ баг сегодня (ISO сортируется лексикографически правильно; `sameIsoDay` нормализует). Но конвенция проекта «в БД только дд.ММ.гггг» фактически нарушена вторым форматом, и любой новый разработчик/агент, применив `parseDotDate` к ISO-строке, получит null→0. Задокументировать в mpk-dev скилле: «lessons — dd.MM.yyyy, student_tasks.deadlineDate — ISO yyyy-MM-dd, мост — HomeworkMarkers.normalizeIso».

### DAT-05 [low/bug] `GroupCatalog.load` глотает исключение → пустой каталог → тихая деградация
- **Файл**: `util/GroupCatalog.kt:32-37` (catch → emptyList), `ScheduleRepository.catalog():80-81`.
- **Root cause**: если ассет повреждён/не найден, репозиторий молча уходит в одногрупповой режим (по памяти проекта — известная деградация), офлайн-кэш чужих групп и кабинетов тихо умирает. Лога нет.
- **Фикс**: логировать catch; в диагностике синка (SyncDiagnosticInfo) отражать «каталог пуст».

## Архитектура и ООП

- `ScheduleRepository` создаётся в **6 местах** (AppViewModel, CollegeMapScreen, CollegeScreen×3, ScheduleCheckWorker) с РАЗНЫМ набором параметров (где-то teacherLessonDao=null, где-то есть; catalogProvider везде свой). `ponytail`-комментарий в репозитории признаёт это. Единая фабрика `ScheduleRepository.create(context)` (или DI-контейнер) — кандидат №1 при архитектурной волне (см. 08-architecture).
- `TaskRepository` — чистая обёртка над DAO без логики: допустимо, но слой пустой; если не появится логика — можно звать DAO напрямую из VM (меньше слоёв). Не срочно.
- `BackupManager` — хорош: version-гейт, dedupeKey, warnings. Замечание: `restoreBackup` вставляет задачи по одной без транзакции (100 задач = 100 транзакций) — обернуть в `db.withTransaction`.
- `HomeworkMarkers` — образцовый: pure-функции, даты через Calendar/epoch, ISO-мост в одном месте. НЕ ЛОМАТЬ.
- `MpkCurriculum` (810 строк хардкода программ) — данные в коде. Работает, но при изменении curriculum — пересборка APK. Кандидат на assets-файл (низкий приоритет).

## Производительность

- DAT-01 (индексы) — главное.
- `LessonEntity` lazy-делегаты (`shortSubjectName` и др.) — `@delegate:Ignore`, Room не трогает; lazy на data class, который создаётся тысячами из курсора, — каждый делегат = объект Lazy. Дёшево, но на больших списках заметно; не трогать без замеров.
- `getDistinctDatesForGroup:73-78` — ORDER BY substr()×3 без индекса: после DAT-01 индекс на (groupName, dateString) ускорит и его.

## Безопасность

- `student_tasks` — пользовательские данные, защищены только миграционной конвенцией (см. память: забытая миграция = тихое стирание ДЗ). DAT-03 снижает этот риск.
- Backup JSON содержит только задачи/настройки, без ФИО-персональных данных учащихся — ок. Файл пишется через SAF (проверить в UI-отчёте).

## Стиль

- DAO-запросы читаемые, транзакции документированы (O7/O1).
- `LessonEntity.roomSecond/teacherSecond/isSplit/dateString` имеют дефолты — новые колонки добавлялись аддитивно; ок.
- BOM (`﻿package`) в HomeworkMarkers/MpkCurriculum — убрать (косметика, иногда ломает diff-инструменты).

## Пробелы в тестах

- НЕТ тестов на: DAT-02 (дубли недельных шаблонов при повторном insertLessons), DAT-05 (пустой каталог → деградация), BackupManager round-trip (build→restore→dedupe), TaskDao-сортировку смешанных ISO/пустых deadlineDate.
- MigrationV1ToV2Test есть — образец для v2→v3 (DAT-01).

## Что работает хорошо (не ломать)

- `replaceForDate`/`replaceScheduleForGroup` — @Transaction (O7: без мелькания пустого UI).
- `MIGRATION_1_2` + `MigrationV1ToV2Test` через боевой getInstance — правильная схема.
- SQL-сортировка dd.MM.yyyy через substr(7,4)/substr(4,2)/substr(1,2) (LessonDao:76) — хронологически верно без лексикографической ловушки.
- `pruneOldLessons` через epoch (`isStaleDate`), не строками.
- Индекс (surnameKey, dateString) на teacher_lessons в миграции — совпадает с аннотацией сущности (иначе Room упал бы на валидации — тест это гарантирует).
- HomeworkMarkers — эталон работы с датами.

## Рекомендуемый порядок исправлений

1. **DAT-01** — MIGRATION_2_3 + индексы (тест миграции обязателен; без теста НЕ ДЕЛАТЬ — расхождение имён = crash у всех).
2. **DAT-02** — сначала диагностика (есть ли строки с dateString="" в живой БД/парсере), потом replaceWeeklyTemplate.
3. **DAT-03** — exportSchema=true (одна строка + ksp-аргумент + git).
4. **DAT-04** — документация конвенции форматов (без кода).
5. **DAT-05** — лог + диагностика.
6. BackupManager-транзакция — попутно.
