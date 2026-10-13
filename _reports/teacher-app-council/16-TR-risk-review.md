# 16 — TR РИСК-РЕВЬЮ переделки teacher-приложения (критик-инженер)

Дата: 2026-10-13. Read-only анализ кода (gradle не гонял — делит параллельная сессия).
Бриф: MASTER-PLAN.md:898-925 (ВОЛНА TR, TR1-TR10). Отмены: `_reports/teacher-app-council/DECISIONS.md:46-57`.
Каждое утверждение помечено [VERIFIED: файл:строки] или [ASSUMPTION].

---

## 0. КАРТА ТЕКУЩЕГО teacher-КОДА (что переделываем)

UI-код преподавателя живёт в **main** source set (`com.politex.ui.teacher.*`), гейтится единственным
FLAVOR-branch в MainActivity [VERIFIED: DECISIONS.md:21-26; MainActivity.kt:74-84]. `src/teacher/java` — **пуст**
(0 файлов) [VERIFIED: `find app/src/teacher/java -type f` → 0]. В `src/teacher` только res (иконка, app_name) + манифест-overlay.

| Слой | Файлы | Строк |
|---|---|---|
| UI | ui/teacher/{Root,Nav,Home,Week,More,GroupLessons,Onboarding,Theme,ProfileStore,DayViewModel,WeekLogic,OnboardingLogic}.kt | ~2800 |
| Данные | data/repository/TeacherDayLoader.kt (508), TeacherInsights.kt, network/TeacherScheduleParser.kt | — |
| Воркер | worker/TeacherScheduleWorker.kt (103) | — |
| Уведомления | util/TeacherNotifier.kt (489) + TeacherNotifierLogic.kt (207) | 696 |
| Виджеты | widget/Teacher{Next,Today}WidgetProvider, TeacherWidget{Data,Logic} | ~680 |
| Room | entity/TeacherOverrideEntity (26), dao/TeacherOverrideDao (30), MpkDatabase v10 migration9To10 | — |
| Тесты | 19 файлов Teacher*Test.kt + teacher/TeacherShotsTest.kt | ~191 @Test |

Ученическое (эталон «копии»): ScheduleScreen (1394), BellsScreen (705), CollegeScreen (1852),
SettingsScreen (1495), MainScreen (602), AppViewModel (167), components/*, theme/{Color,Tokens,Type,Theme,AppTheme}.

Всего тестов в проекте: **919** [VERIFIED: сумма @Test по app/src/test].

---

## 1. ГЛАВНЫЙ АРХИТЕКТУРНЫЙ ВОПРОС — как переиспользовать ученические экраны

### 1.1 Реально ли «взять ScheduleScreen 1-в-1»? НЕТ.

`ScheduleScreen(groupInfo: GroupInfo, scheduleRepository, groupLessonsFromVm: List<LessonEntity>?,
pendingTasksFromVm: List<StudentTaskEntity>?, onTeacherClick, onRoomClick, ...)`
[VERIFIED: ScheduleScreen.kt:122-146]. Экран насквозь групповой:

- источник данных — `scheduleRepository.getAllLessonsForGroup(groupInfo.canonicalName)` [VERIFIED: :169];
  у преподавателя группы НЕТ, есть `teacher_lessons` через `TeacherLessonDao.getForSurnameOnDate(surnameKey, date)`
  [VERIFIED: TeacherLessonDao.kt:13-23].
- рендерит `LessonEntity` (teacherFirst/teacherSecond/roomFirst/roomSecond/isSplit) [VERIFIED: LessonEntity.kt:19-33].
  Teacher-данные — `TeacherDayLesson(lessonNumber, subject, groups: List<String>, room, start, end, ...)`
  [VERIFIED: TeacherDayLoader.kt:73-86]. **Разные типы, разная форма.**
- карточка урока `LessonCard` показывает `TeacherChip(lesson.teacherFirst)` = ФИО преподавателя
  [VERIFIED: ScheduleScreen.kt:1010-1021, 1069-1139]. TR3 требует **ГРУППУ вместо ФИО** — прямо противоположное.
- ДЗ-бейджи (`HomeworkBadge`, `homeworkByLesson`, `pendingTasks`) [VERIFIED: :291-336, 1338-1394] — у преподавателя заданий нет.
- split-подгруппы (`SubgroupPane`, `RouteStartResolver`, диалог «Откуда вести маршрут?») [VERIFIED: :459-493, 949-986, 1141-1254] — teacher-строка не split.
- календарь-архив `CalendarArchiveDialog(groupName = groupInfo.canonicalName, ...)` [VERIFIED: :432-456; CalendarArchiveDialog.kt:91-113] — групповой.
- `daySlots(...)` требует `hasScheduleForDate` по группе [VERIFIED: :180-187; DaySlots.kt:19-31].

Вывод: «взять 1-в-1» технически невозможно без группы. Но **ВИЗУАЛЬНО** экран = колонка
(заголовок «Расписание» + subtitle + кнопка sync) → строка (2×`DaySlotCell` + кнопка-календарь `AppCard`) →
`AppDivider` → `AnimatedContent`{ loader | empty-state | `LazyColumn` карточек }.
Эту **обёртку** и **лист-компоненты** можно переиспользовать; тело карточки — нельзя (ФИО→группа).

### 1.2 Что УЖЕ переиспользуется 1-в-1 (без группы)

- **BellsScreen** — принимает `groupInfo`, но группа нужна ТОЛЬКО для синей рельсы «до конца моих уроков»
  (`getLessonsForDay(groupInfo.canonicalName)` → `lastLessonToday` → `blueEndNum`) [VERIFIED: BellsScreen.kt:130-190].
  Список звонков — `CollegeBellSchedule.getBellsForType` (общий хардкод, от группы не зависит) [VERIFIED: CollegeBellSchedule.kt:37-100].
  **Пердаём blank-GroupInfo → рельса серая, звонки рисуются.** Экран пригоден как есть (v1); «своя» рельса — опционально позже.
- **CollegeMapScreen** — `initialRoom, onBack, onOpenTeacher, fromRoom, showRoute` [VERIFIED: CollegeMapScreen.kt:131-142],
  группа не нужна. Уже вызывается из TeacherNav [VERIFIED: TeacherNav.kt:182-186]. **Реюз as-is.**
- **GroupScheduleScreen/Route** — «найти группу» (TR5) — `scheduleRepository, selectedGroupName, ownGroupName`
  [VERIFIED: GroupScheduleScreen.kt:203-217]. `ownGroupName` можно blank. Реюз.
- Лист-компоненты: `DaySlotCell` [components/DaySlotCell.kt], `AppCard`, `NoLessonCard`, `CenteredLoader`,
  `SearchBar`, `SubPageHeader`, `SectionCaption`, `MainHeaderBanner`, `bouncyClickable`, tokens/colors.
- `TeacherCard`/`TeacherAvatar` (список преподавателей) — НО private в CollegeScreen [VERIFIED: CollegeScreen.kt:813-957].

### 1.3 Три стратегии — честная оценка

| | A: параметризовать живые экраны | B: собрать из тех же компонентов | C: копировать экраны целиком |
|---|---|---|---|
| Суть | `teacherMode: Boolean` + источник данных в ScheduleScreen/CollegeScreen/SettingsScreen | реюз лист-компонентов/токенов + тонкие teacher-контейнеры; тело карточки — near-copy | дубль 1394+705+1852+1495 строк в ui/teacher |
| Стоимость | 4-6 дн | **6-8 дн** | 5-7 дн |
| Регрессия ученического | **ВЫСОКАЯ** — ветвление в живых экранах, каждый студенческий коммит теперь думает про teacher | НИЗКАЯ (если карточку near-copy, а не извлекать) | **НУЛЬ** |
| Нарушение «ноль FLAVOR в общем коде» [DECISIONS.md:22-26] | **ДА** (teacherMode-ветки в общем UI) | нет (teacher-контейнеры в ui/teacher) | нет |
| Drift/поддержка | средняя | низкая | **ВЫСОКАЯ** — багфиксы ×2 навсегда |
| Мёртвый студенческий код в teacher | нет | нет | **ДА** (homework/split/group после вырезания) |

### 1.4 РЕКОМЕНДАЦИЯ: **B (гибрид)**

Заказчик хочет «полностью как у учеников» **визуально**, не тем же файлом [MASTER-PLAN.md:899-905].
B даёт пиксель-в-пиксель переиспользуя токены/цвета/лист-компоненты, не внося teacherMode-веток в живые
студенческие экраны (A — регрессия) и не плодя дубли 4 экранов с мёртвым кодом (C — drift).

Конкретно:
- **Расписание (tab1)**: тонкий `TeacherScheduleScreen` = near-copy обёртки ScheduleScreen (заголовок/subtitle/sync,
  2×`DaySlotCell`, `AppDivider`, `AnimatedContent`, `LazyColumn`, empty-state, `CenteredLoader`, `NoLessonCard`),
  данные из `TeacherDayLoader.loadDay` → `TeacherDayLesson`. Карточка = **`TeacherLessonCard`** (near-copy `LessonCard`
  ~200 строк: та же Surface/RoundedCornerShape(2.dp)/BorderStroke/padding 12dp/номер-бейдж 24dp/время/предмет/room-чип),
  но `TeacherChip`→**`GroupChip`** (группа вместо ФИО), **БЕЗ** «Править»/⚑/ДЗ/split (TR3/TR4).
  Room-чип — тот же `AppCard`+`LocationOn`+`bouncyClickable` (heightIn(min=44.dp)) = «кликабельна как в оригинале» (TR3).
  Кнопка календаря — near-copy `CalendarArchiveDialog` на teacher-датах ИЛИ упрощённый выбор даты (архив преподавателя = `getPresentDates`).
- **График (tab2)**: СМ. §1.5 — вероятно `BellsScreen` (реюз as-is).
- **Ещё (tab3)**: `MenuCard`-подобные секции (near-copy OtherMenu), но свой набор: расписание звонков, карта, найти группу,
  преподаватели, настройки [TR5]. `TeachersPage` — своя (near-copy), **БЕЗ «ВЕДУТ У ГРУППЫ»/isActive-инсайтов** [TR5; CollegeScreen.kt:744,739] → реюз `TeacherCard`/`TeacherAvatar`/`SearchBar`.
- **Настройки**: тонкий teacher-экран (сменить преподавателя + масштаб + тема), реюз `AppCard`/`SectionCaption`/`SettingsSwitchRow`-паттерн + theme LazyRow (`AppTheme.entries`) [SettingsScreen.kt:616]. **SettingsScreen целиком НЕ реюзим** (1495 строк, groupInfo/backup/grades/subgroup — учителю не нужны) [VERIFIED: SettingsScreen.kt:106-108].

Извлечение `LessonCard`-тела в общий параметризованный компонент (вариант B′) — **НЕ делать**: это правит живой
студенческий ScheduleScreen (регрессия). Near-copy карточки в ui/teacher безопаснее и дешевле, чем защита студентов от регресса.

### 1.5 ⚠️ БЛОКИРУЮЩАЯ НЕЯСНОСТЬ: что такое вкладка «График»?

TR5: 3 вкладки «Расписание / График / Ещё»; при этом «Ещё» уже содержит «расписание звонков» [MASTER-PLAN.md:911-913].
DECISIONS.md:50 отменяет экраны «Мой день»/«Неделя»/«Нагрузка» собственной разработки. Значит «График» — НЕ тяжёлая неделя/нагрузка.

Две трактовки [ASSUMPTION]:
1. **График звонков** = `BellsScreen` (совпадает с виджетом «график звонков» TR8; студент-аналог — таб «Звонки»). Реюз почти бесплатный.
2. **График работы** = недельная сетка пар преподавателя (облегчённая замена отменённой «Недели»).

Это **вопрос №1 заказчику** (блокирует tab2). Безопасный дефолт до ответа: tab2 = `BellsScreen` (трактовка 1),
т.к. она переиспользуется as-is и согласуется с TR8. Если заказчик имел в виду неделю — замена контейнера, компоненты те же.

---

## 2. УДАЛЕНИЕ override (TR4 — «правки/замены удалить полностью»)

### 2.1 Точный список на удаление (grep всех вызовов)

**Код (main):**
- `TeacherDayLoader.kt`: `syncDayWithOverrideDiff` (397-432), `applyOverrides`-вызов в `loadDay` (375-382),
  `overridesToPrune` (379), `diffOverrideResets` (196-229), `resetMessage`/`dayLabel` (240-256),
  типы `OverrideReset`/`OverrideResetReason` (98-108), поля `TeacherDay.resets` (95), `TeacherDayLesson.overridden`/`isReplacement` (83-85).
  → `loadDay` упрощается: `loadTeacherDay(... fromDatabase, sync = repository.syncTeacherScheduleForDate)` напрямую.
- `TeacherDayViewModel.kt`: `saveOverride` (83-108), `cancelOverride` (110-117), `dismissOverrideResets` (119),
  `recordResets` (128-131), поле `overrideResets` (36), `syncDayWithOverrideDiff`-вызов в `refresh` (74-77), импорт TeacherOverrideEntity.
- `TeacherHomeScreen.kt`: `editing` state (104), `TeacherOverrideSheet` (527-619), `OverrideSheetItem` (621-646),
  `floorRoomSuggestions` (649-666), плашка `overrideResets` (218-240), маркер «✎ ваша правка» (481-487),
  кнопка «Править» (489-498), `StatusLabel("ЗАМЕНА")`/isReplacement (410-413). *(Экран и так переписывается под B.)*
- `TeacherWeekScreen.kt`: `syncDayWithOverrideDiff`-вызов (142).
- `TeacherNav.kt`: `markOverridesPlate` (100, 199-221), `EXTRA_MARK_OVERRIDES` (68, 107-110).
- `TeacherNotifier.kt`: `sendOverrideResets` (420-438), override-оверлей в `dayLessons` (128-136). *(Файл удаляется целиком — см. §3.)*
- `TeacherWidgetLogic.kt`: `applyOverrides` (56-75), `overridesHash` (112-113), `overrideHash` в `cacheKey` (105-110), поле `replaced` (20).
- `TeacherWidgetData.kt`: `db.teacherOverrideDao().all()` (86), `applyOverrides`-вызовы (97-100, 141-144), overrides в `scanNextDays`.
- `TeacherNotifierLogic.kt`: `resetKey`/`resetNotificationText` (174-183), isReplacement-фильтр в `reminderCandidates` (190). *(Файл удаляется — §3.)*
- `worker/TeacherScheduleWorker.kt`: `syncDayWithOverrideDiff`-вызов (64-65) → plain `syncTeacherScheduleForDate`.

**Room (см. 2.2):** `TeacherOverrideEntity.kt`, `TeacherOverrideDao.kt`, `MpkDatabase` migration9To10.

### 2.2 Таблица `teacher_overrides` в Room v10 — удалить vs dormant

Сейчас: `MpkDatabase version=10`, `migration9To10` создаёт `teacher_overrides` + уникальный индекс
[VERIFIED: MpkDatabase.kt:42, 251-266], `fallbackToDestructiveMigration()` включён [VERIFIED: :285, комментарий :268-273].
**MpkDatabase ОДИН на все flavor'ы** (main source set) — student_tasks (ДЗ студентов) живут в той же БД.

Критично: Room валидирует **identity hash** схемы при открытии. Если убрать `TeacherOverrideEntity` из `@Database(entities=[...])`
[:29-41], но оставить version=10 → hash разойдётся с уже установленной v10 → при апгрейде без явной миграции
`fallbackToDestructiveMigration` **ТИХО сотрёт всю БД, включая student_tasks у всех студентов**. Это катастрофа для ученического приложения.

Варианты:
- **(a) Удалить таблицу честно**: v10→v11, `migration10To11 = DROP TABLE teacher_overrides`, убрать entity из списка,
  поднять version, добавить `MigrationV10ToV11Test`. Стоимость ~0.5 дн + риск (ошибка в миграции → destructive-wipe студентов).
- **(b) DORMANT — РЕКОМЕНДАЦИЯ**: **MpkDatabase.kt НЕ ТРОГАТЬ ВООБЩЕ.** Entity+dao+migration9To10 остаются (Room-схема
  неизменна → version=10 → hash совпадает → ноль миграций, ноль риска студентам). Таблица пустая навсегда (0 строк, 0 байт, 0 runtime —
  никто не читает/пишет после §2.1). Стоимость **0**. Единственный «долг» — ~56 строк мёртвого entity+dao.

**РЕКОМЕНДАЦИЯ: (b) dormant.** Пустая таблица бесплатна; удаление = миграция v10→v11 + тест + реальный риск
silent-wipe student_tasks у студентов при малейшей ошибке. Мёртвые `TeacherOverrideEntity.kt`/`TeacherOverrideDao.kt`
оставить как есть (или удалить ТОЛЬКО dao-abstract-метод `teacherOverrideDao()`, оставив entity в списке — но и это не нужно).
Если заказчик принципиально хочет «чтобы таблицы не было» — тогда (a), но только отдельным безопасным коммитом с MigrationV10ToV11Test.

---

## 3. УДАЛЕНИЕ уведомлений (сводка/diff/reminder) — TR6

### 3.1 Что удалить

- **`util/TeacherNotifier.kt` (489 строк) — ЦЕЛИКОМ**, включая `TeacherReminderReceiver` (470-489).
- **`util/TeacherNotifierLogic.kt` (207 строк) — ЦЕЛИКОМ.**
- `MainActivity.kt:51` — убрать `TeacherNotifier.createChannels(this)` (оставить `setupTeacherSync` :52).
- `worker/TeacherScheduleWorker.kt` — убрать `snapshotRaw`/`before` (51-52), `onSyncCompleted`-блок (86-95);
  sync через plain `syncTeacherScheduleForDate`. Остаётся: окна синка (`shouldTeacherSync`), `lastSyncEpoch`, `TeacherWidgetUpdater.updateAll`.
- `TeacherMoreScreen.kt` — убрать `NotifSection` (180-232), `ToggleRow` (234-263), ключи notif_* (67-71). *(Экран переписывается под B.)*
- `src/teacher/AndroidManifest.xml:13-19` — удалить `<receiver TeacherReminderReceiver>`.
- Орфанные prefs-ключи в `mpk_teacher_profile` (summary_sent_*, diff_notified_*, tomorrow_published_*, pending_tomorrow_published,
  override_reset_*, lesson_reminded_*, notif_summary/changes/reminder/reminder_minutes) — **не трогать** (безвредные сироты; чистка не обязательна).
- Каналы teacher_day_summary/teacher_schedule_changes/teacher_lesson_reminder — не создаются после удаления createChannels (безвредно).

### 3.2 ⚠️ НЕ ТЕРЯЕМ ЛИ «ДО ЗВОНКА» В ШТОРКЕ (TR7)? — НЕТ, но это ДРУГАЯ система, и она СЕЙЧАС НЕ ДОСТУПНА В TEACHER

«До звонка» = **`BellCountdownNotifier` (общий, НЕ teacher)** + `BellTimerService` [VERIFIED: util/BellCountdownNotifier.kt, util/BellTimerService.kt].
Удаление TeacherNotifier его **не задевает** (разные файлы, разные каналы). НО проверка кода показывает, что «до звонка»
в teacher-сборке **фактически выключено** — это и есть дыра в брифе:

1. **Канал создаётся** для всех flavor: `MpkApplication.onCreate:38` + `MainActivity.onCreate:40-42` (до flavor-branch) ✅.
2. **Сервис стартует** если `isEnabled` [MainActivity.kt:43-48] — тоже до flavor-branch ✅. **НО** `isEnabled` = pref
   `bell_notification_enabled`, **default false** [VERIFIED: BellCountdownNotifier.kt:85].
3. **Включить pref может ТОЛЬКО `BellCountdownNotificationCard`** — private-компонент внутри **SettingsScreen (студенческий)**
   [VERIFIED: SettingsScreen.kt:893-934, switch :1013-1039 `setEnabled(context,true)`]. В **TeacherMoreScreen этого тоггла НЕТ**
   [VERIFIED: TeacherMoreScreen.kt — только notif_summary/changes/reminder]. → **У преподавателя НЕТ UI включить «до звонка».** ДЫРА №1.
4. **Watchdog в teacher НЕ планируется**: teacher-ветка MainActivity зовёт `setupTeacherSync`, но НЕ `setupBellServiceWatchdog`
   (он в else-ветке) [VERIFIED: MainActivity.kt:49-57]. `BellServiceWatchdogWorker` — то, что перезапускает убитый OEM'ом
   `BellTimerService` + ставит fallback-alarm [VERIFIED: BellServiceWatchdogWorker.kt:16-23]. В teacher его нет → если FGS убьют,
   поднимут только BOOT/MY_PACKAGE_REPLACED [BellCountdownWidgetProvider.kt:534] и TimeChangedReceiver [:42], но не mid-life watchdog. ДЫРА №2.
5. **Scope-мина**: `BellCountdownNotifier.Scope.MY_LESSONS` (default) считает «до конца уроков МОЕЙ ГРУППЫ» через
   `lastGroupLessonForToday` → `WidgetUpdateHelper.getSelectedGroup(context)`, которая в teacher **blank** → `return null`
   [VERIFIED: BellCountdownNotifier.kt:192-205; BellCountdownWidgetProvider.kt:198-201 `if group.isBlank() return null`].
   При `lastGroupLesson==null` и `countdownForMinute` и `tomorrowFirstLessonLabel` в GROUP_LESSONS-режиме дают null
   [VERIFIED: BellCountdownWidgetProvider.kt:105-110, 160-162] → info=null → нотификация «Уроков нет»/скрытая.
   **`Scope.COLLEGE`** использует `bells.lastOrNull{...}.endMinutes` (общий `CollegeBellSchedule`, от группы не зависит)
   [VERIFIED: :199, 108-109] → **работает в teacher корректно.** ДЫРА №3: teacher обязан использовать COLLEGE-scope.

**ВЫВОД по TR7**: «до звонка» в teacher НЕ потеряется от удаления TeacherNotifier (это общая система), но её **надо ВКЛЮЧИТЬ**,
чего сейчас нет. Работы: (а) добавить в teacher-настройки строку «до звонка» **только минуты/секунды** (TR7) — реюз
`Style.COMPACT`/`WITH_SECONDS` [BellCountdownNotifier.kt:64-83], ВЫБРОСИВ выбор темы/scope из UI (или жёстко зафиксировав
`Scope.COLLEGE`); (б) `setEnabled(true)` должен стартовать `BellTimerService`; (в) добавить `setupBellServiceWatchdog` в teacher-ветку
MainActivity (или teacher-эквивалент); (г) принудительно `Scope.COLLEGE` в teacher (guard, НЕ меняющий студенческий default MY_LESSONS).
Формат «только минуты/секунды» уже есть: `formatMinutes` vs `formatMinutesSeconds`/`formatSeconds` [BellCountdownNotifier.kt:277-281, 419-422].

---

## 4. МАСШТАБ 75/100/125% (TR6) — технические ловушки

Существующего механизма НЕТ [VERIFIED: grep fontScale/DisplayMetrics/scaledDensity/Configuration() по app/src/main → пусто].
`TeacherTheme`-метрики 48dp/13sp (LocalTeacherMetrics) ОТМЕНЕНЫ [DECISIONS.md:51] — вместо них масштаб.

### 4.1 Три механизма, честно

| Механизм | Что ломает |
|---|---|
| **LocalDensity с density×scale** (всё: dp+sp) | dp-геометрия плывёт: навбар 72dp→90dp [TeacherNav.kt:130], `DaySlotCell` height 44dp→55dp [DaySlotCell.kt:47], бейдж `.size(24.dp)`, hit-таргеты; **px-математика карты** `with(density){dp.toPx()}` [CollegeMapScreen.kt:981-1002, 1128-1153] → метки комнат съезжают; **Roborazzi** снимает другой размер. Именно этого боится заказчик («пересечения»). |
| **LocalDensity с fontScale×scale** (только sp) | dp-геометрия **пиксель-в-пиксель как у студентов** (100% «копия» сохраняется). Риск — текст переполнит ФИКСИР-высоту: навбар-label 10sp [TeacherNav.kt:161], DaySlotCell 12sp/10sp в 44dp [DaySlotCell.kt:62,73], номер-бейдж 13sp в 24dp. Все — `heightIn(min=...)` или Center → растут/влезут, кроме жёстких `.height()`. Перечислимо. |
| **Своя типографика ×множитель** | Невозможно дёшево: код использует инлайн `fontSize = 13.sp` в СОТНЯХ `Text`, не `Typography` [VERIFIED: Type.kt существует, но экраны пишут inline]. Трогать каждый Text — нереально. |

### 4.2 БЕЗОПАСНЫЙ механизм — РЕКОМЕНДАЦИЯ: fontScale-only через LocalDensity, ТОЛЬКО в teacher-поддереве

```
// TeacherRoot/TeacherNav: обернуть контент
val scale = ScaleStore.get(context)               // 0.75f / 1.0f / 1.25f
val d = LocalDensity.current
CompositionLocalProvider(
    LocalDensity provides Density(d.density, d.fontScale * scale)
) { ... }
```
- **Масштабируется**: весь sp-текст (через LocalDensity.fontScale) в пределах teacher-поддерева.
- **НЕ масштабируется (автоматически)**: иконки (`Modifier.size(N.dp)`), рамки/BorderStroke, dp-отступы, навбар 72dp,
  hit-таргеты, **карта** (её px-математика на `density`, не на `fontScale` — не трогаем), **виджеты** (RemoteViews, вне Compose-дерева).
- Студенческое приложение не затрагивается (провайдер только в TeacherRoot).
- Хранить pref в `mpk_ui_prefs` (рядом с AppThemeStore) или `mpk_teacher_profile`; читать в TeacherRoot, реактивно (State) чтобы менять без рестарта.

Если заказчик захочет масштабировать и геометрию (не только текст) — тогда density×scale, но ОБЯЗАТЕЛЬНО вывести
`CollegeMapScreen` из-под провайдера (рендерить с оригинальным LocalDensity) и зафиксировать навбар/DaySlotCell.
Это дороже и рискованнее → не рекомендую для v1.

### 4.3 Где реально полезут «пересечения» (аудит фикс-высот)

- Навбар `Modifier.height(72.dp)` + label 10sp [TeacherNav.kt:130,161] — при fontScale×1.25 label=12.5sp, в 72dp влезет (иконка 24dp + текст). Проверить vision'ом.
- `DaySlotCell` `.height(44.dp)`, title 12sp + subtitle 10sp [DaySlotCell.kt:47,62,73] — при 1.25 (15sp+12.5sp) в 44dp **впритык**; рассмотреть `heightIn(min=44.dp)` вместо `height`.
- Номер-бейдж `.size(24.dp)` + 13sp [ScheduleScreen.kt:911-921] — цифра в 24dp, при 1.25 (16sp) может обрезаться → `min`/без фикс.
- `Tokens.HitTarget/ButtonHeight = 44.dp` [Tokens.kt:8-10] — dp, не масштабируются (ок, hit-зона стабильна).
- Чипы с `heightIn(min=...)` — растут безопасно.
- `TextStylePageTitle = 32.sp` [Type.kt] → при 1.25 = 40sp, «Расписание» по ширине — проверить.

### 4.4 Как тестировать

- Roborazzi: 3 масштаба × ключевые экраны (Расписание+карточка, График, Ещё, Настройки). Т.к. масштаб — in-app pref
  (не системный fontScale), драйвить его в тесте через `ScaleStore.set(...)` перед `captureRoboImage`, НЕ через `@Config(qualifiers="fontScale...")`.
- Паттерн — копия `TeacherShotsTest` (стоящие часы, фикс TimeZone) [VERIFIED: teacher/TeacherShotsTest.kt; 14-shots-vision.md:11-18].
- После записи — **vision-сабагент** (правило проекта: Roborazzi record + vision после визуальных правок) [mpk-build SKILL.md].
- Юнит-тест `ScaleStore` (75/100/125 round-trip, default 100).

---

## 5. ВИДЖЕТЫ (TR8) — 4 виджета под преподавателя

Текущий teacher-манифест: зарегистрированы `TeacherNextWidgetProvider` («Следующая пара» 4×1/2×2) и
`TeacherTodayWidgetProvider` («Моё сегодня» 4×2); **студенческие вырезаны** `tools:node="remove"`:
BellCountdown, NowNext, TodaySchedule, Bells, WidgetConfigActivity [VERIFIED: src/teacher/AndroidManifest.xml:22-53].

Разбор 4 требуемых виджетов:

| TR8 | Провайдер | От чего считает | Решение |
|---|---|---|---|
| **«график звонков»** | `BellsWidgetProvider` (общий) | ТОЛЬКО `CollegeBellSchedule.getBellsForDay` — **группы нет вообще** [VERIFIED: BellsWidgetProvider.kt:34-81] | **ВЕРНУТЬ в teacher-манифест as-is** (убрать `tools:node="remove"` :52). Работает без изменений. 0 кода. |
| **«до звонка»** | `BellCountdownWidgetProvider` (общий) | `CountdownMode.GROUP_LESSONS` → `getSelectedGroup` → blank в teacher → «Уроков нет» [VERIFIED: BellCountdownWidgetProvider.kt:198-201, 281-285]; `ALL_BELLS` → `CollegeBellSchedule` → работает | **ВЕРНУТЬ** (убрать remove :49) **+ форсировать `ALL_BELLS`** когда группа blank (иначе мёртвый «Уроков нет»). Минимально: в `CountdownMode.load` default→ALL_BELLS при blank-группе ИЛИ teacher-guard. |
| **«мои уроки»** | `TeacherTodayWidgetProvider` (уже есть) | `TeacherWidgetData.snapshot` → teacher_lessons [VERIFIED: TeacherWidgetData.kt:73-124] | **ОСТАВИТЬ**, но вычистить override-оверлей (§2.1: `applyOverrides`, `teacherOverrideDao().all()`). |
| **«сейчас и дальше»** | `TeacherNextWidgetProvider` (уже есть) | тот же snapshot [VERIFIED: TeacherNextWidgetProvider.kt:96-112] | **ОСТАВИТЬ**, вычистить overrides (`replaced`-бейдж, overrideHash в cacheKey). |

Студенческие `NowNextWidgetProvider`/`TodayScheduleWidgetProvider` (групповые) — **НЕ возвращать** (у teacher свои эквиваленты;
вернули бы → «Выберите группу в приложении» [VERIFIED: NowNextWidgetProvider.kt:97-104]).

### 5.1 Точный план манифеста (`src/teacher/AndroidManifest.xml`)

- Убрать `tools:node="remove"` с: `BellCountdownWidgetProvider` (:49), `BellsWidgetProvider` (:52), `WidgetConfigActivity` (:53).
  - **WidgetConfigActivity ОБЯЗАТЕЛЕН**: `bell_countdown_widget_info.xml` и `bells_widget_info.xml` ссылаются
    `android:configure="com.politex.widget.WidgetConfigActivity"` [VERIFIED: xml/bell_countdown_widget_info.xml, xml/bells_widget_info.xml].
    Без activity добавление виджета упадёт/пропустит настройку. Activity обрабатывает COUNTDOWN/BELLS/NOW_NEXT/TODAY
    [WidgetConfigActivity.kt:243-284] — в teacher инстанцируются только COUNTDOWN/BELLS.
  - Мелочь: в config для COUNTDOWN предлагается `CountdownMode.GROUP_LESSONS` («до конца моих уроков») — в teacher бессмысленно;
    можно скрыть опцию GROUP_LESSONS при blank-группе (низкий приоритет).
- Оставить `tools:node="remove"` для `NowNextWidgetProvider` (:50), `TodayScheduleWidgetProvider` (:51).
- Удалить `<receiver TeacherReminderReceiver>` (:13-19) — см. §3.
- `WidgetAlarmReceiver`/BOOT/`TimeChangedReceiver` — в main-манифесте, остаются (минутный тик уже обновляет teacher-виджеты
  через `TeacherWidgetUpdater.updateAllNow` [VERIFIED: BellCountdownWidgetProvider.kt:520; hasWork включает teacher-провайдеры :455-456]).

⚠️ Краш-мина (уже закрыта в reliz-ревью): тап по чипу кабинета студенческого виджета шлёт EXTRA_ROOM →
`MainActivity.handleIntent` → `navController.navigate` на контроллере БЕЗ графа (в teacher NavHost не ставится) → crash
[VERIFIED: 12-release-review-tech.md:94; MainActivity.kt:64-71,100-114]. Сейчас закрыто FLAVOR-гейтом handleIntent (:67)
и вырезанием студенческих виджетов. **При возврате BellCountdown/Bells в teacher**: их тап шлёт `openAppIntent(..., "BELLS", scrollTarget="CURRENT_BELL")`
[VERIFIED: BellsWidgetProvider.kt:83-89; BellCountdownWidgetProvider.kt:300-305] → EXTRA_OPEN_TAB/EXTRA_SCROLL_TARGET, НЕ EXTRA_ROOM.
В teacher `handleIntent` всё равно под FLAVOR-гейтом (:67) и не вызывается → экстра игнорируется, краша нет. НО: teacher-навигация
должна сама читать EXTRA_OPEN_TAB и маппить «BELLS»→tab График. Текущий `TeacherNavLogic.tabForExtra` понимает только
"week"/"more"/"today" [VERIFIED: TeacherNav.kt:70-75] — «BELLS»/«SCHEDULE» упадут в default TODAY. Доработать маппинг (§7 Wave3).

---

## 6. Регресс-риски ученического приложения

| Место | Риск | Митигция |
|---|---|---|
| **MpkDatabase** (общая, student_tasks) | **КРИТИЧНО** при удалении teacher_overrides | §2.2: **dormant** — MpkDatabase НЕ трогать → version=10, hash совпадает, ноль миграций, ноль wipe-риска |
| Shared-компоненты (DaySlotCell, AppCard, TeacherCard, tokens/colors) | LOW — только чтение | реюз read-only; НЕ менять сигнатуры/токены |
| `LessonCard` (ScheduleScreen) | MED если извлекать тело в общий компонент | §1.4: near-copy `TeacherLessonCard` в ui/teacher, студенческий LessonCard НЕ трогать |
| `TeachersPage`/`CollegeScreen` «ВЕДУТ У ВАС» | MED если параметризовать | своя teacher-копия страницы (реюз TeacherCard/Avatar), OtherMenu/TeachersPage (private) не трогаем |
| `SettingsScreen` (общий, 1495) | MED если добавлять teacher-строки | свой тонкий teacher-settings; SettingsScreen не трогаем |
| `BellsScreen` (общий) | LOW — передаём blank GroupInfo | рельса станет серой у teacher (не баг); студент не затронут |
| `BellCountdownNotifier` (общий) | MED — teacher guard на Scope/watchdog | guard ТОЛЬКО в teacher-ветке; студенческий default MY_LESSONS + watchdog не менять |
| `MainActivity` flavor-branch | LOW — уже изолирован | правки только внутри `if (FLAVOR=="teacher")` |
| `src/teacher/AndroidManifest.xml` | НОЛЬ для студентов | overlay сливается ТОЛЬКО в teacher-сборку [VERIFIED: manifest comment :2-5] |
| `Tokens`/`Color`/`Type`/`Theme` | HIGH если править | **НЕ ТРОГАТЬ** (общие на все flavor) |

### 6.1 Минимальный набор проверок, чтобы поймать регресс студентов

1. **После каждой волны**: `:app:compileStandardDebugKotlin` (быстро, «дерево не сломано») [mpk-build SKILL].
2. **Гейт**: `:app:testStandardDebugUnitTest` — 919 тестов зелёные (или явный список удалённых teacher-тестов). Известный флакер `LiveNetworkSmokeTest` — 1 ретрай [mpk-build SKILL].
3. **Roborazzi студента** (`PromoShotsTest` — снимает студенческие экраны): если ВОЛНА тронула общий компонент (LessonCard/Tokens/DaySlotCell) —
   record до/после + vision-diff shot_schedule_today/tomorrow, shot_settings. Если общие НЕ тронуты (стратегия B) — пропускаем.
4. **Room-гейт**: если MpkDatabase не тронут (dormant) — миграционные тесты не нужны. Если всё же v10→v11 — `MigrationV10ToV11Test` ОБЯЗАТЕЛЕН [mpk-build SKILL, CLAUDE.md Room v9/v10 инвариант].
5. **Security-гейт** (если собирается teacher/standard release): `unzip -l app-standard-release.apk | grep -c students.csv` = 0 [CLAUDE.md].

---

## 7. Порядок работ (волны ≤3 агентов; gradle делится с параллельной сессией)

### 7.1 Как делить gradle (TR10)

Gradle-сборка ОДНА на машину, делится с параллельной сессией (promo/). Worktree НЕ решает: каждый worktree поднимает
свой daemon + build dir, но всё равно contends на `~/.gradle` lock; на Windows тяжело. **РЕКОМЕНДАЦИЯ: последовательные
блоки с ЕДИНСТВЕННЫМ gradle-владельцем на блок.** Агенты волны пишут код БЕЗ gradle (≤3 параллельно, правило bun-segfault),
затем **оркестратор** один раз гоняет `compileStandardDebugKotlin` + `testStandardDebugUnitTest` между волнами.
Полные сборки (`assembleTeacherRelease`) — только в финале, по договорённости с параллельной сессией (окно, когда promo не собирает).
Координация через SendMessage с promo-сессией перед каждым gradle-прогоном (namespace не пересекается: мы в app/src, они в promo/).

### 7.2 Волны

**Wave 0 — блокирующие вопросы (0 кода, 0 gradle).** Задать заказчику:
- (Q1) «График» (tab2) = график звонков (BellsScreen) или недельная сетка пар? (§1.5)
- (Q2) «до звонка» в teacher: до последнего звонка колледжа (Scope.COLLEGE, работает сейчас) или до конца МОИХ пар (нужен teacher-источник)? (§3.2)
- (Q3) таблица teacher_overrides: dormant (0 риска) или принципиально удалить (v10→v11)? (§2.2)
Дефолты до ответа: Q1=BellsScreen, Q2=COLLEGE, Q3=dormant.

**Wave 1 — МИНИМАЛЬНЫЙ ВИДИМЫЙ РЕЗУЛЬТАТ (2-3 дн, ≤3 агента без gradle).**
Что делать ПЕРВЫМ: переписать teacher-оболочку под «копию оригинала».
- А1: `TeacherNav` 3 таба (Расписание/График/Ещё), стиль = студенческий навбар (72dp, те же иконки/цвета) [эталон MainScreen.kt:287-337].
- А2: `TeacherScheduleScreen` + `TeacherLessonCard` (near-copy обёртки ScheduleScreen + карточка с GroupChip, БЕЗ Править/⚑/ДЗ) [TR2/TR3].
- А3: tab2 = `BellsScreen`(blank GroupInfo); tab3-заглушка. Удалить override-UI из Home/Nav (§2.1 частично).
- Точка проверки: `compileStandardDebugKotlin` + `testStandardDebugUnitTest`; Roborazzi teacher (Расписание) + vision.
- **Это то, что заказчик увидит первым**: «интерфейс = копия, в карточке группа, нет Править/⚑».

**Wave 2 — Ещё/преподаватели/настройки + чистка уведомлений (2 дн).**
- А1: `TeacherMoreScreen` = «Другое»-меню (звонки/карта/найти группу/преподаватели/настройки), реюз MenuCard-паттерн [TR5].
- А2: `TeachersPage`-teacher (БЕЗ «ведёт у вас»), реюз TeacherCard/TeacherAvatar/SearchBar; `GroupScheduleScreen` для «найти группу».
- А3: teacher-Settings (сменить преподавателя + тема) + удалить TeacherNotifier/TeacherNotifierLogic + вызовы (§3.1) + receiver из манифеста.
- Точка проверки: compile + test; удалены TeacherNotifierLogicTest (31), правки в worker.

**Wave 3 — виджеты + «до звонка» (2 дн).**
- А1: манифест — вернуть Bells/BellCountdown/WidgetConfigActivity (§5.1), форс ALL_BELLS при blank-группе.
- А2: teacher-виджеты — вычистить overrides (§2.1); `TeacherNavLogic.tabForExtra` маппинг "BELLS"/"SCHEDULE" (§5.1).
- А3: teacher-настройки «до звонка» (только минуты/секунды, TR7) + `setEnabled`→`BellTimerService.start` + `setupBellServiceWatchdog` в teacher-ветку + guard Scope.COLLEGE (§3.2).
- Точка проверки: compile + test; ручная приёмка виджетов (48ч Xiaomi/Samsung — руки заказчика, как в v2.6 [MASTER-PLAN.md:893-894]).

**Wave 4 — масштаб + иконка + override-dormant-чистка данных (1.5 дн).**
- А1: механизм масштаба fontScale-only (§4.2) + ScaleStore + settings-строка 75/100/125.
- А2: Roborazzi 3 масштаба × ключевые экраны + vision (§4.4).
- А3: иконка — уменьшить шапку (scale путей к центру 54,54 в ic_launcher_foreground_teacher.xml) [TR9; §8 ниже]; удалить teacher_overrides-чтение из widget/loader (финальная чистка §2.1).
- Точка проверки: compile + test; record Roborazzi + vision.

**Финал (окно без promo-сборки):** `assembleTeacherRelease` + `assembleStandardRelease` (паровоз),
гейты: students.csv=0, versionCode bump, dexdump teacher=0 StudentsRepository (долг чести №10 [12-release-review-tech.md:28,108]),
LiveDoc 200/OLE2. **~8-10 рабочих дней** суммарно (совпадает с оценкой BRIEF:13).

---

## 8. Тесты

### 8.1 Какие существующие teacher-тесты умрут

Всего teacher-тестов: ~191 @Test в 20 файлах [VERIFIED: сумма @Test].

| Файл | @Test | Судьба |
|---|---|---|
| `TeacherNotifierLogicTest.kt` | 31 | **УДАЛИТЬ целиком** ( notifications отменены, §3) |
| `TeacherOverrideLogicTest.kt` | 14 | **УДАЛИТЬ целиком** (override отменён, §2) |
| `teacher/TeacherShotsTest.kt` | 6 | **ПЕРЕПИСАТЬ**: shotHomeOverrideSheet — удалить; остальные — под новые экраны (Wave1-4) |
| `TeacherWidgetLogicTest.kt` | 23 | **ПРАВИТЬ**: override-тесты (~5-6: applyOverrides/overridesHash/cacheKey-overrideHash, строки 40-90,184-190) — удалить; nextState/findNextLesson/labels — оставить |
| `MigrationV9ToV10Test.kt` | 4 | **ОСТАВИТЬ** при dormant (§2.2b); заменить на `MigrationV10ToV11Test` ТОЛЬКО если удаляем таблицу (§2.2a) |
| `TeacherDayLogicTest.kt` | 9 | **ОСТАВИТЬ** (statuses/matchRows — НЕ override) [VERIFIED: @Test-имена :24-123 без override] |
| `TeacherInsightsOverrideTest.kt` | 9 | **ОСТАВИТЬ** — это про «ведёт у вас»-инсайты (LeadsMarks), НЕ про teacher_overrides [VERIFIED: :34-123]. Умрёт только если выпилим TeacherInsights.isActive из teacher — но он нужен студентам. Не трогаем. |
| `TeacherScheduleWorkerTest.kt` | 7 | **ОСТАВИТЬ** (тестирует `shouldTeacherSync`-окна, не override) [VERIFIED: :12-45] |
| остальные (Parser/Cache/E2E/LiveDoc/RowMatch/NavLogic/Onboarding/GroupLogic/WeekLogic/ScheduleDayPick/CsvParse) | ~88 | WeekLogicTest (10) — под вопросом если tab2 «График»≠неделя (§1.5); NavLogicTest (3) — править tabForExtra (§5.1); Onboarding — усилить fuzzy (TR1); прочие — оставить |

Итого guaranteed-смерть: **~45-51 @Test** (31 notifier + 14 override + 1 shots + ~5-6 widget-override).
Чистая цифра после Wave2-3: ~919 − ~50 + новые.

### 8.2 Обязательные НОВЫЕ тесты

1. **Масштаб** (§4): `ScaleStoreTest` (75/100/125 round-trip, default 100) + Roborazzi 3 масштаба × ключевые экраны (§4.4) + vision-аудит «нет пересечений».
2. **Выбор преподавателя с ГРУБЫМИ опечатками** (TR1): `TeacherOnboardingLogicTest` расширить — `FuzzySearch.damerauLevenshtein`
   терпит MAX_DISTANCE=2 [VERIFIED: FuzzySearch.kt:9,46], но «показывает ВСЕХ подходящих» (TR1) — тест что
   `filterTeachers` при опечатке («Лебедьева» вместо «Лебедева», транслитерация, пропуск буквы) возвращает >1 кандидата
   и `confirmCandidates` показывает всех однофамильцев [VERIFIED: TeacherOnboardingLogic.kt:9-35]. Кейсы: distance=2, ё/е, перестановка, инициалы.
3. **Виджеты на teacher-данных** (§5): `TeacherWidgetLogicTest` — nextState/findNext/emptyText БЕЗ overrides (после чистки);
   `BellsWidget`-реюз (группа не нужна) — смоук; `BellCountdown` ALL_BELLS при blank-группе (новый guard).
4. **«Группа вместо ФИО» в карточке** (TR3): тест что `TeacherLessonCard` рендерит `lesson.groups` (чип группы) и НЕ рендерит ФИО/Править/⚑ —
   через Roborazzi + testTag (`teacher_group_chip_*` уже есть [TeacherHomeScreen.kt:447], переиспользовать в новой карточке) ИЛИ
   чистый тест маппинга `TeacherDayLesson.groups` → chip-модель.
5. **«до звонка» в teacher** (§3.2): тест что `BellCountdownNotifier.buildNotification` при blank-группе + Scope.COLLEGE
   даёт валидный countdown (не «Уроков нет»); что `setEnabled(true)` планирует сервис; что teacher-ветка зовёт watchdog.
6. **Манифест-гейт** (§5.1): если возможен robolectric-тест merged-manifest — проверить что в teacher присутствуют
   Bells/BellCountdown/WidgetConfig и отсутствуют NowNext/TodaySchedule/TeacherReminderReceiver.

---

## 9. Иконка (TR9) — «шапка слишком большая, уменьшить»

`ic_launcher_foreground_teacher.xml` — вектор 108×108, шапка занимает x:21→87 (66 юнитов = 61% ширины),
y:33→79 [VERIFIED: файл:5-13]. Adaptive-icon safe zone = центр 66% → шапка почти впритык по ширине.
«Уменьшить» = масштабировать пути к центру (54,54) на ~0.75-0.8 (например `android:scaleX/scaleY` на `<group>` или пересчитать координаты).
Пропорции «как у оригинальной иконки» — сравнить с студенческой (PNG mipmap ic_launcher_foreground) [VERIFIED: main mipmap-anydpi-v26/ic_launcher.xml].
Чистая правка вектора, **0 риск** для кода/студентов, 0.5 дн. Roborazzi иконку не снимает — проверить визуально на launcher (руками).

---

## 10. ТОП-5 ЛОВУШЕК (сводка для финального сообщения)

1. **ДЫРА «до звонка» (TR7) — ПОДТВЕРЖДЕНА.** `BellCountdownNotifier` — общая система, от удаления TeacherNotifier НЕ пострадает,
   НО в teacher-сборке сейчас **недоступна**: (а) нет UI-тоггла (он private в студенческом SettingsScreen, в TeacherMoreScreen его нет),
   (б) `bell_notification_enabled` default false, (в) `BellServiceWatchdog` в teacher-ветке MainActivity НЕ планируется,
   (г) default `Scope.MY_LESSONS` при blank-группе даёт «Уроков нет» — нужен `Scope.COLLEGE`.
   [VERIFIED: MainActivity.kt:43-57; BellCountdownNotifier.kt:85,192-205; SettingsScreen.kt:893; TeacherMoreScreen.kt]. Работы — §3.2.
2. **Удаление teacher_overrides = миграция v10→v11 + риск silent-wipe student_tasks у ВСЕХ студентов** (MpkDatabase общая,
   fallbackToDestructiveMigration). РЕШЕНИЕ: **dormant** — MpkDatabase не трогать, удалить только вызовы. 0 риска, 0 стоимости.
   [VERIFIED: MpkDatabase.kt:42,251-285].
3. **ScheduleScreen/CollegeScreen/SettingsScreen НЕ реюзятся 1-в-1** (групповые насквозь: GroupInfo/getAllLessonsForGroup/
   homework/split/«ведёт у вас»/backup). Стратегия **B**: реюз лист-компонентов + BellsScreen/CollegeMapScreen/GroupScheduleScreen as-is +
   near-copy карточки (GroupChip вместо TeacherChip) и тонкие контейнеры. [VERIFIED: ScheduleScreen.kt:122-146,1010-1021; CollegeScreen.kt:701-811].
4. **Масштаб density×scale ломает px-математику карты + Roborazzi + dp-геометрию** («пересечения» заказчика).
   РЕШЕНИЕ: **fontScale-only** через LocalDensity в teacher-поддереве (dp/иконки/карта/навбар не трогаются) + аудит фикс-высот
   (навбар 72dp, DaySlotCell 44dp, бейдж 24dp). [VERIFIED: CollegeMapScreen.kt:981-1002; DaySlotCell.kt:47; TeacherNav.kt:130].
5. **Возврат виджетов «до звонка»/«график звонков» в teacher** требует: убрать `tools:node="remove"` с BellCountdown+Bells+**WidgetConfigActivity**
   (на неё ссылаются widget-info XML), форс `ALL_BELLS` при blank-группе, и доработать `TeacherNavLogic.tabForExtra` (сейчас «BELLS»/«SCHEDULE»
   падают в default TODAY → тап по виджету откроет не ту вкладку). [VERIFIED: src/teacher/AndroidManifest.xml:49-53; xml/bells_widget_info.xml; TeacherNav.kt:70-75].

**Блокирующий вопрос заказчику (Wave 0):** что такое вкладка «График» — график звонков (BellsScreen) или недельная сетка пар? (§1.5)
