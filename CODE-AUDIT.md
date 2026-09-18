# Код-аудит проекта «Мой Политех» (MPK-app-2.1)

**Что проверено:** 61 файл Kotlin в `app/src/main` (17 165 строк, из них 14 303 строки кода), XML-ресурсы, манифест, Gradle-конфиг, 4 виджета, 31 тестовый файл (2 023 строки).
**Снимок состояния:** рабочее дерево на 18.09.2026, 23:09 (коммит `2e80352` + незакоммиченные правки).
**Ничего в коде не менялось** — только чтение и анализ.

> ⚠️ **Важно про снимок.** Во время аудита в репозитории параллельно шли правки (незакоммиченные): удаление подсистемы активации (`ActivationScreen`, `AppCodeScreen`, `ActivationStore`, `UnlockToken`, `UnlockTokenTest`), правки дизайна (`SettingsScreen`, `CollegeScreen`, `CollegeMapScreen`, `CalendarArchiveDialog`, `GroupSelectionDialog`, `MainScreen`) и чистка `backup_rules.xml` / `data_extraction_rules.xml`. Номера строк даны на момент снимка; для каждой находки приведён **текст-якорь**, по которому её можно найти заново:
>
> ```bash
> grep -rn "<якорь>" app/src/main/java
> ```

**Как считалось:** анализ объявлений (функция/класс/поле/константа) и подсчёт вхождений по всему `app/src`; отдельно считались использования во `main` и в тестах (чтобы отличать «мёртвое» от «живого только в тестах»); комментарии считались построчно (KDoc + `//` + блочные).

---

## 1. Мёртвый код

### 1.1. Не вызывается нигде (подтверждено: единственное вхождение — само объявление; в тестах тоже нет)

| Файл:строка | Что | Чем подтверждается | Что делать |
|---|---|---|---|
| `data/local/dao/LessonDao.kt:44` | `suspend fun insertLesson(lesson): Long` | ни одного вызова во всём `app/src` (используется пакетный `insertLessons`) | удалить |
| `data/local/dao/LessonDao.kt:53` | `suspend fun deleteLessonsForDay(group, dayOfWeek)` | ни одного вызова (живой — `deleteLessonsForDate`) | удалить |
| `data/local/dao/TaskDao.kt:50` | `suspend fun clearTasksForGroup(group)` | ни одного вызова; `TaskRepository` его тоже не оборачивает | удалить |
| `data/local/entity/LessonEntity.kt:48` | `val officialSubjectName` (`@delegate:Ignore`) | ни одного обращения | удалить |
| `data/local/entity/LessonEntity.kt:56` | `val timeRangeDisplay` (`@delegate:Ignore`) | ни одного обращения (в UI время склеивается вручную: `"${lesson.timeStart} – ${lesson.timeEnd}"`) | удалить |
| `data/model/CollegeMap.kt:45` | `fun looksLikeRoomNumber(room)` | ни одного вызова | удалить |
| `data/network/MpkScheduleParser.kt:83` | `private val DOC_LINK_REGEX` | не используется: в `extractDocumentUrls` рядом стоят четыре inline-`Regex(...)`, а эта константа забыта | удалить (или переиспользовать в п.4 метода) |
| `data/repository/ScheduleRepository.kt:69` | `fun searchLessons(group, query)` | ни одного вызова; сам `LessonDao.searchLessonsForGroup` тоже не используется | удалить цепочку (репозиторий + DAO + `FTS`-подобный SQL) |
| `data/repository/ScheduleRepository.kt:176` | `suspend fun saveLessons(lessons)` | ни одного вызова | удалить |
| `data/repository/ScheduleRepository.kt:180` | `suspend fun replaceSchedule(group, lessons)` | ни одного вызова; в живых — `replaceSyncedLessons` | удалить (вместе с `LessonDao.replaceScheduleForGroup`, который вызывает только он) |
| `data/repository/ScheduleRepository.kt:184` | `suspend fun clearSchedule(group)` | ни одного вызова; `LessonDao.clearLessonsForGroup` вызывается только отсюда и из мёртвого `replaceScheduleForGroup` → при удалении обоих становится мёртвым и он | удалить (вместе с `LessonDao.clearLessonsForGroup`) |
| `data/repository/TeachersRepository.kt:66` | `fun toggleFavorite(name)` | ни одного вызова — функция «Избранное» из UI удалена | удалить вместе с `favorites()` и prefs `teachers_favorites` |
| `data/repository/TeachersRepository.kt:72` | `fun favorites(): Set<String>` | ни одного вызова | удалить |
| `ui/components/GroupBadge.kt` (весь файл, 115 строк) | `fun GroupBadge(...)` | единственное вхождение — объявление. Внутри — прибитая гвоздями группа `"41О"` (строка 83: `groupName.ifBlank { "41О" }`) | **удалить файл целиком** |
| `ui/components/IntroSplash.kt` (весь файл, 175 строк) | `fun IntroSplash(...)` + `fun IntroSplashOverlay(...)` | `IntroSplashOverlay` не вызывается нигде; `IntroSplash` вызывается только из него | **удалить файл целиком** (заставка в приложение не подключена) |
| `ui/components/MonthGrid.kt:142` | `val MONTH_NAMES_GENITIVE` | ни одного обращения (в UI живёт `MONTH_NAMES_NOMINATIVE` и своя `MONTH_NAMES_CAPS` в архиве) | удалить |
| `ui/components/SimpleDatePickerDialog.kt:202` | `internal fun isoFromParts(...)` | ни одного вызова (даты собираются через `String.format` на месте) | удалить |
| `ui/screens/CollegeScreen.kt:1695` | `private fun SubPageHeader(title, onBack)` | ни одного вызова — все под-экраны рисуют шапку вручную; рядом живая `SubPageBackButton` | удалить |
| `ui/theme/AppTheme.kt:80` | `val currentAppTheme` | ни одного обращения (текущая тема хранится в состоянии экрана настроек) | удалить |
| `util/BellCountdownNotifier.kt:209` | `fun refresh(context)` | ни одного вызова (есть `show`/`cancel`/`setEnabled`) | удалить |
| `util/SubjectFormatter.kt:534` | `private fun shortenAlgorithmic(text, maxLength)` | приватная, не вызывается изнутри файла | удалить |
| `util/BellTimerService.kt:31,61-65` | `private var widgetRefreshCounter` + `if (widgetRefreshCounter >= 1)` | счётчик инкрементируется и обнуляется, но условие **всегда истинно**, т.е. это `while(true) { updateAll() }` в трёх строках | упростить до прямого вызова, счётчик удалить |
| `util/WidgetUpdateHelper.kt:26` | `private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` | не используется; вместе с ним мертвы импорты `SupervisorJob`, `Dispatchers`, `CoroutineScope`, `launch`, `AppWidgetManager`, `ComponentName` (строки 3-10) | удалить; см. также риск 5.3 (хелпер на самом деле блокирует поток) |
| `widget/BellCountdownWidgetProvider.kt:215` | `CountdownMode.remove(context, id)` | ни одного вызова | либо удалить, либо (правильнее) вызывать из `onDeleted` — см. 4.4 |
| `widget/BellCountdownWidgetProvider.kt:264` | `WidgetStyle.remove(context, id)` | ни одного вызова | то же |
| `ui/screens/TasksScreen.kt:518` | `var description by remember { mutableStateOf("") }` | переменная пишется только в `:694` (`description.trim()` → всегда `""`), **поля ввода для неё в диалоге нет** — поле «описание» из UI убрали, state остался | удалить state и параметр; либо вернуть поле ввода |
| `ui/components/CalendarArchiveDialog.kt:237-251` | ветка `if (room == null) { … return@Column }` внутри `RoomPanel` | `RoomPanel` вызывается только под `if (targetRoom != null)` (строка 208 экрана), а параметр объявлен nullable → **ветка недостижима** | сделать параметр non-null и убрать ветку |

### 1.2. Живое только в тестах (в проде не вызывается)

| Файл:строка | Что | Подтверждение |
|---|---|---|
| `data/model/CollegeBellSchedule.kt:95` | `val BELLS = STANDARD_BELLS` — комментарий обещает «обратную совместимость для существующего кода» | в проде **ни одного** обращения, только `Step2UiLogicTest:17` |
| `data/model/CollegeBellSchedule.kt:129` | `fun getCurrentSlot(...)` | только тест (`Step2UiLogicTest:38`); живые экраны ищут слот сами через `firstOrNull { minutes in it.startMinutes..it.endMinutes }` (4 дублирующих места) |
| `data/model/CollegeBellSchedule.kt:147` | `fun getTimeForNumber(...)` | только тесты (6 вызовов); прод использует `getTimeForLessonNumber` |
| `data/model/CollegeBellSchedule.kt:166` | `fun getBellForNumber(...)` | только тесты |
| `data/model/CollegeBellSchedule.kt:46` | `const val DISCLAIMER` | только тест; в UI не выводится |
| `data/model/GroupInfo.kt:19,25,31` | `hasSaturdayClasses`, `canHaveCourseWork`, `canHaveDiploma` | только тесты. При этом логика продублирована в проде: `ScheduleScreen:149` (`groupInfo.course == 1 || saturdayLessons.isNotEmpty()`), `TaskType.getAvailableForCourse`, `TasksScreen` |
| `data/network/MpkNetworkClient.kt:25` | `const val BROWSER_USER_AGENT` (синоним `USER_AGENT`) | только тест |
| `data/network/MpkNetworkClient.kt:65` | `fun generateCandidateEventUrls(...)` | только тест; в проде используется `generateCandidateDirectUrls` |
| `data/network/MpkScheduleParser.kt:102` + `SchedulePostLink` (строка 19) | `fun findSchedulePostLinks(html)` и весь «Шаг А» | только тест `Step4BugfixTest:28`. В проде ссылки ищет `extractDocumentUrls` («Шаг Б»), а `POST_LINK_REGEX` обслуживает только мёртвый метод |
| `data/repository/TeacherInsights.kt:41` | `fun lastSeenDate(name)` (+ поле `TeacherFacts.lastDate`, которое считается только для неё) | только тест `TeacherInsightsTest` |

**Решение по 1.2 — за заказчиком:** либо удалить (тогда тесты придётся убрать/переписать), либо, что честнее, **свести дубли**: вызывать `getCurrentSlot`/`getTimeForNumber` из прод-кода вместо 4-5 самописных копий той же логики в экранах и виджетах. Сейчас это мёртвый публичный API рядом с дублями.

### 1.3. Мёртвые ресурсы

| Файл | Что | Подтверждение |
|---|---|---|
| `res/drawable/bg_widget_card.xml` | весь drawable | 0 ссылок в коде и XML; виджеты красят фон программно (`setInt(..., "setBackgroundColor", style.backgroundColor)`) |
| `res/values/colors.xml` | `widget_primary`, `widget_primary_container`, `widget_primary_dark`, `widget_text_title`, `widget_text_secondary`, `widget_text_tertiary`, `widget_accent_green`, `widget_accent_green_bg`, `widget_accent_orange`, `widget_accent_orange_bg`, `widget_row_bg`, `widget_subgroup_1`, `widget_subgroup_2`, `purple_200`, `teal_200`, `teal_700`, `black` — **17 цветов** | 0 ссылок (проверено по всем `@color/…`); `widget_bg_light`/`widget_bg_border` используются только мёртвым `bg_widget_card.xml` → фактически тоже мертвы |
| все `xml/*_widget_info.xml:10` | `android:previewImage="@drawable/widget_preview_countdown"` | **одна и та же** картинка-превью у всех четырёх виджетов (у «Звонков», «Сейчас/дальше» и «Расписания» превью не соответствует содержимому) |
| `res/values/strings.xml` | `bell_countdown_widget_description` | живая, но описывает только один из четырёх виджетов; у остальных `android:description` нет вовсе |

### 1.4. Мёртвые токены темы и стили (остались от удалённой шторки-меню)

Подтверждение — 0 обращений во всём `app/src` (в тестах тоже):

- `ui/theme/Color.kt:115` `ColorActiveFill`, `:117` `ColorMenuBg`, `:118` `ColorMenuSubBg`, `:122` `ColorMenuIcon`, `:125` `ColorSurfaceLight`, `:138` `ColorSuccessBorder`, `:140` `ColorWarning`, `:142` `ColorErrorText` — **8 токенов**.
- `ui/theme/Type.kt:110` `TextStyleTopDateBar`, `:128` `TextStyleMenuBarTitle`, `:164` `TextStyleMenuItem`, `:173` `TextStyleMenuSubItem` — **4 стиля**.
- `ColorMenuBorder` живёт только через мёртвый `GroupBadge.kt`, `ColorMenuText`/`ColorMenuSubtext` — только через мёртвые стили из `Type.kt`. Удаление 1.4 потянет за собой эти два-три поля `MpkPalette` (`menuBg`, `menuSubBg`, `menuIcon`, `activeFill`, `successBorder`, `warning`, `errorText`).

### 1.5. Мёртвые импорты — 40 штук в 14 файлах

| Файл | Кол-во | Импорты (строки) |
|---|---|---|
| `ui/components/CalendarArchiveDialog.kt` | 9 | `fadeIn`, `fadeOut`, `heightIn`, `width`, `HorizontalDivider`, `TextOverflow`, `ColorTextDisabled`, `ColorTextWeekdays`, `ColorBrandFill` |
| `ui/screens/SettingsScreen.kt` | 5 | `wrapContentWidth`, `Icons.Default.Widgets`, `ColorDividerLight`, `ColorTopBar`, `TextStylePageTitle` |
| `ui/screens/StudentsScreens.kt` | 5 | `MpkDatabase`, `GroupInfo`, `Teacher`, `ColorBrandFill`, `ColorTopBar` |
| `ui/screens/CollegeMapScreen.kt` | 4 | `clickable`, `mutableFloatStateOf`, `drawscope.translate`, `ColorSurfaceHighlight` |
| `ui/screens/CollegeScreen.kt` | 4 | `Icons.Default.Star`, `Icons.Outlined.StarBorder`, `IconButton`, `StudentsRepository` (последние два — следы удалённого «Избранного») |
| `widget/WidgetUpdateHelper.kt` | 3 | `AppWidgetManager`, `ComponentName`, `launch` |
| `ui/components/MonthGrid.kt` | 2 | `Arrangement`, `ColorBrandBlue` |
| `ui/screens/BellsScreen.kt` | 2 | `testTag`, `ColorTextTitle` |
| `ui/screens/TasksScreen.kt` | 2 | `TextButton`, `ColorActiveBlue` |
| `MainActivity.kt`, `ui/components/GroupSelectionDialog.kt`, `ui/components/IntroSplash.kt`, `ui/screens/ScheduleScreen.kt` | по 1 | `Box`; `ColorActiveBlue`; `width`; `Icons.Default.EventNote` |

*(Импорты `androidx.compose.runtime.getValue/setValue` в списки не включались — они нужны для делегатов `by`.)*

### 1.6. Мёртвые ветки и недостижимая логика

| Файл:строка | Что |
|---|---|
| `data/network/TeacherScheduleParser.kt:119-120` | `when { … value.uppercase().contains("КУРС") -> cell.subject = value; else -> cell.subject = value }` — **две ветки делают одно и то же**, ветка про «1 КУРС» бесполезна |
| `ui/screens/ScheduleScreen.kt:190-246` | `selectedDateString` присваивается только `""` (строки 341, 354) — **ветка «выбранная дата» недостижима**: подписи `"… • $selectedDateString"`, фильтрация `bySpecificDate` и `.replace("-", ".")`-сравнения никогда не выполняются. Причина: `CalendarArchiveDialog` вызывается без `onDateSelected` (строка 251) |
| `ui/screens/CollegeMapScreen.kt:237-251` | см. 1.1 (ветка `room == null`) |
| `ui/components/GroupBadge.kt:104-113` | `if (showDialog)` — внутри мёртвого компонента |
| `ui/components/CalendarArchiveDialog.kt:430` | `val hasData = savedDates.contains(dateStr) \|\| allGroupLessons.isNotEmpty()` — `dateStr` тут в ISO (`%04d-%02d-%02d`), а `savedDates` приходят из БД в формате `дд.ММ.гггг` → **сравнение не срабатывает никогда**, точка-индикатор фактически означает «у группы есть хоть один урок». Левая половина условия мёртвая (см. 2.3) |
| `util/BellTimerService.kt:61-65` | см. 1.1 |
| `data/network/MpkScheduleParser.kt:662` | `?: "2026"` — захардкоженный год (см. раздел 2) |

---

## 2. Время и даты

### 2.1. 🔴 Главная мина: захардкоженный 2026 год в парсере

`data/network/MpkScheduleParser.kt:662` (якорь `ifBlank { "2026" }`):

```kotlin
val y = textMonthMatch.groupValues.getOrNull(3)?.ifBlank { "2026" } ?: "2026"
detectedDate = "$d.$m.$y"
```

Документы МГПК в шапке часто пишут «5 сентября» **без года** — тогда год подставляется константой 2026. Последствия каскадом:

1. С 01.01.2027 все уроки, разобранные из такого документа, получают дату **2026**.
2. `ScheduleRepository.pruneOldLessons()` (`:140`, `ARCHIVE_KEEP_DAYS = 60`, строка 19) считает даты старше 60 дней устаревшими и **удаляет их** (`isStaleDate` → `deleteLessonsByDate`).
3. Итог: расписание перестанет появляться в приложении, а диагностика будет показывать «загружено N уроков» — данные будут тут же уходить в мусор. Это не косметика, а отказ основного сценария.

**Что сделать:** брать год из `targetDate` (он всегда передаётся сверху — `parsePlainTextSchedule(text, targetGroup, targetDate)`) или из текущего учебного года, а не из константы. Учебный год считать как «сентябрь–август»: месяц < 8 → текущий год, иначе текущий+1.

### 2.2. Формат дат: `YYYY-MM-DD` в документации, `дд.ММ.гггг` в реальности

- `data/local/entity/LessonEntity.kt:26` — комментарий **врёт**: `// Календарная дата в формате YYYY-MM-DD (для точечных изменений)`.
- Фактический формат — `дд.ММ.гггг`: это прямо задокументировано в `ScheduleRepository.kt:12` и подтверждается SQL (`LessonDao.kt:55-61`: `ORDER BY substr(dateString, 7, 4) … substr(dateString, 4, 2) … substr(dateString, 1, 2)`) и записью даты в парсере (`"$d.$m.$y"`) и в `formatDate()` (`"%02d.%02d.%04d"`).
- Отсюда — «страховки от старого формата», которые сегодня ничего не делают (no-op), но создают ложное впечатление поддержки двух форматов:
  - `ui/screens/ScheduleScreen.kt:238,239,243` — `it.dateString.replace("-", ".")`
  - `ui/components/CalendarArchiveDialog.kt:127` — то же в фильтре по дате
  - `widget/BellCountdownWidgetProvider.kt:113,115` — то же в подсчёте последнего урока
  - `data/repository/TeacherInsights.kt:74` — `val dateKey = lesson.dateString.replace("-", ".")`
  - `ui/screens/CollegeScreen.kt:1449` — `sdf.parse(dateStr.replace("-", "."))` (там разбирается ISO-строка из календаря — единственное место, где `replace` осмыслен)

**Что сделать:** привести комментарий к `дд.ММ.гггг`, убрать 6 no-op `replace("-", ".")`, а в `CalendarArchiveDialog` сравнивать в одном формате (см. 1.6) — например, приводить ISO к `дд.ММ.гггг` при сравнении с `savedDates`.

Разные места нормализуют дату **по-разному**: `WidgetData.todayLessons` (`NowNextWidgetProvider.kt:154`) берёт `maxOfOrNull { it.dateString }` без замены, а `BellCountdownWidgetProvider.kt:113` — с заменой. Сегодня результат совпадает, но это два источника истины об одном и том же.

### 2.3. Академические/календарные константы и подписи

| Где | Что не так |
|---|---|
| `data/model/CollegeBellSchedule.kt:42,47` | «утверждено на 2026 учебный год» и текст `DISCLAIMER` «…на 2026 г.» — дата в пользовательском тексте (в UI не показывается, но константа живая, её проверяет тест) |
| `ui/screens/SettingsScreen.kt:355` (якорь `Версия приложения`) | **`value = "2.11"` — прибито гвоздями, при этом `versionName = "2.25"` в `app/build.gradle.kts`**. Пользователь видит в диагностике неверную версию; расхождение будет расти с каждым релизом |
| `ui/screens/CollegeScreen.kt:1596` | `CollegeBellSchedule.getBellsForDay(1)` — время урока преподавателя **всегда считается по понедельнику**, независимо от выбранной даты (в расписании преподавателя 4-й пары в четверг время другое — 14:45 против 14:15, и инфочас). Нужен день недели выбранной даты |
| `data/network/TeacherScheduleParser.kt:111` + `.kt` doc `1..10` | жёсткий предел «10 уроков», при том что `CollegeBellSchedule` описывает **12** уроков. Уроки 11-12 теряются |
| `data/network/MpkScheduleParser.kt:1026` | `Regex("^(10\|[1-9])\\s*(.*)$")` — «11 Эк…» разберётся как **урок 1** (регекс съест первую «1»), «12 …» — как урок 1. В `detectLessonNumber:598` предел другой (`1..12`). Расхождение внутри одного файла |
| `data/network/MpkScheduleParser.kt:96,643` | `(202[0-9])` в регексах дат — после 2029 года даты перестанут распознаваться молча (без ошибки). Мелочь, но дешево починить: `(20[0-9]{2})` |
| `ui/components/MonthGrid.kt:147` | комментарий «Названия месяцев — «Сентябрь 2026»» — пример с годом, который устареет |
| `ui/components/CalendarArchiveDialog.kt:91-98` | KDoc-«спецификация» с «СЕНТЯБРЬ 2026», «#111827, Bold 15px» — техзадание заказчика; полезно, но год в примере лучше обезличить |
| `ui/components/MainHeaderBanner.kt:30` | `…/wp-content/uploads/**2024/11**/cropped-cropped-cropped-logo-na-sajt.png` — прямая ссылка на файл заказчика в папке 2024 года. Если сайт почистят uploads — шапка останется без баннера (есть фолбэк-портик, так что не падение) |

### 2.4. Отладочное время (`DebugClock`) — в релизном коде, без гейта

`util/DebugClock.kt` целиком:

- **Нет проверки `BuildConfig.DEBUG`** — подмена времени работает в release-APK (`isMinifyEnabled = false`, так что и код, и строки на месте).
- UI-доступ: `SettingsScreen.DebugTimeSection()` (якорь `ДЕБАГ-ВРЕМЯ`) показывается только при включённом тумблере «Дебаг», а **сам тумблер не сохраняется** (`rememberSaveable { mutableStateOf(false) }`, строка 106) — после перезапуска он снова выключен, а подмена в SharedPreferences **остаётся включённой**.
- Итог: студент (или заказчик), однажды выставивший «дебаг-время», получает приложение, живущее в прошлом (звонки, подсветка урока, виджеты), и **не видит в интерфейсе, где это выключить**, пока снова не найдёт тумблер «Дебаг» в диагностике.
- Consumed by: `BellsScreen`, `ScheduleScreen`, `CollegeScreen` (расписание преподавателя), `BellCountdownWidgetProvider`, `NowNextWidgetProvider`, `SettingsScreen`.
- **Не учитывают** дебаг-время: `ScheduleCheckWorker` (там `Calendar.getInstance()`), `MpkNetworkClient.calculateTargetCalendar`, `StudentsScreens.kt:323` (`DebugClock.now()` **без context** → всегда системное время, хотя KDoc `DebugClock` обещает, что override видят все экраны). Это внутренние расхождения: половина приложения живёт по подменённому времени, половина — по настоящему, и понять это в UI невозможно.

**Что сделать (решение заказчика):** либо обернуть весь блок в `if (BuildConfig.DEBUG)`, либо сохранять флаг «дебаг» и показывать в UI явный индикатор «подменено время» с кнопкой сброса. Оставлять как есть — риск получить «приложение сломалось» от одного нажатия.

---

## 3. Комментарии

### 3.1. Сколько их

**1 539 строк комментариев в 61 файле `main` (~9% от 17 165 строк)**; в тестах — ещё 195 строк.

| Файл | Комментариев | Строк | Доля |
|---|---|---|---|
| `data/network/MpkScheduleParser.kt` | 166 | 1 408 | 12% |
| `ui/screens/ScheduleScreen.kt` | 95 | 992 | 10% |
| `ui/screens/CollegeScreen.kt` | 86 | 1 784 | 5% |
| `ui/screens/CollegeMapScreen.kt` | 69 | 880 | 8% |
| `util/SubjectFormatter.kt` | 51 | 616 | 8% |
| `util/GroupParser.kt` | 48 | 189 | **25%** |
| `data/network/TeacherScheduleParser.kt` | 45 | 224 | **20%** |
| `ui/screens/TasksScreen.kt` | 42 | 778 | 5% |
| `ui/components/CalendarArchiveDialog.kt` | 42 | 613 | 7% |
| `data/repository/ScheduleRepository.kt` | 41 | 193 | **21%** |
| `util/BackupManager.kt` | 40 | 200 | **20%** |
| `ui/screens/SettingsScreen.kt` | 38 | 1 311 | 3% |
| `widget/BellCountdownWidgetProvider.kt` | 37 | 353 | 10% |
| `util/BellCountdownNotifier.kt` | 37 | 249 | 15% |
| `data/model/CollegeBellSchedule.kt` | 36 | 183 | **20%** |
| `ui/screens/BellsScreen.kt` | 35 | 547 | 6% |
| `data/repository/TeacherInsights.kt` | 32 | 163 | **20%** |
| `ui/screens/StudentsScreens.kt` | 31 | 935 | 3% |
| `ui/viewmodel/AppViewModel.kt` | 27 | 166 | 16% |
| `ui/theme/Color.kt` | 24 | 151 | 16% |
| `widget/WidgetUpdateHelper.kt` | 21 | 82 | **26%** |
| `worker/ScheduleCheckWorker.kt` | 18 | 148 | 12% |

Плотность комментариев в целом **здоровая**: это не «комментарии ради комментариев», а объяснения форматов документов, разметки карты и истории решений. Ниже — только те, что **врут, устарели или дублируют код**.

### 3.2. Врут (описывают не то, что делает код) — 14 штук

| Файл:строка | Комментарий говорит | Код делает |
|---|---|---|
| `data/local/entity/LessonEntity.kt:26` | «Календарная дата в формате **YYYY-MM-DD**» | формат `дд.ММ.гггг` (см. 2.2) |
| `widget/BellCountdownWidgetProvider.kt:16-18` | «**Единственный виджет** приложения „Мой Политех“ — „До звонка“» | в приложении **4** виджета (там же, в файле, лежат `WidgetAlarm` и `WidgetAlarmReceiver` для всех четырёх) |
| `worker/ScheduleCheckWorker.kt:22` | «Запускается только в интервале публикации документов (**14:00 – 21:30**)» | код проверяет **10:00–21:00** (строки 43-44), и это же окно написано пользователю в настройках (`SettingsScreen`: «Включены (10:00 – 21:00)») |
| `worker/ScheduleCheckWorker.kt:19-25` | заголовок «ПОЛИТИКА НУЛЕВОГО СПАМА … 3. Отправляет РОВНО ОДИН пуш на день» | по факту ключ дедупликации — `notified_${group}_${targetDate}` (строка 88), т.е. один пуш на **группу и дату**, а не на день; остальное совпадает |
| `widget/NowNextWidgetProvider.kt:106` | ««08:15–09:00 • каб. 214»; **кабинет не пишем, если его нет**» | дубль-комментарий; следующей строкой идёт актуальный вариант («кабинет — если он есть и включён в настройках»). Первый остался от прежней реализации `detailsOf` |
| `widget/NowNextWidgetProvider.kt:16` | «Виджет „Сейчас и дальше“ (**2x3**)» | конфиг виджета — `targetCellWidth=3`, `targetCellHeight=2` → **3x2** |
| `ui/components/SimpleDatePickerDialog.kt:51-54` | «Сетка [MonthGrid] … **уже работает в архиве расписания** — используем её же» | архив (`CalendarArchiveDialog`) **не использует** `MonthGrid`: там своя сетка `LazyVerticalGrid`/`calculateMonthDays`. Общим остался только `calculateMonthDays` |
| `ui/screens/CollegeMapScreen.kt:206-207` | «Табличку снизу убрали: если кабинета нет, об этом говорит само поле поиска» | табличка (`RoomPanel`) осталась, вместе с недостижимой веткой «нет кабинета» |
| `ui/screens/CollegeMapScreen.kt:562-564` | «Раньше здесь стояли 1376 и 768 — номера выходили вчетверо крупнее» (описывает удалённый код) | объясняет удалённое; ценности не несёт, ночью только путает — при этом рядом живая логика (сравнение с `imgW/imgH`) |
| `data/repository/TeachersRepository.kt:27` | «**Избранное** хранится в SharedPreferences» | функции избранного мертвы (1.1), UI избранного нет |
| `ui/screens/CollegeScreen.kt:298` | пункт меню «Преподаватели — поиск по базе, **избранное** и фотографии» | «избранного» в приложении нет: `toggleFavorite`/`favorites` не вызываются, звёздочек в UI тоже нет (импорты `Star`/`StarBorder` мертвы) |
| `ui/theme/Color.kt:11-14` | «Архитектура: **палитра (светлая/тёмная)** + живые геттеры» | тёмной палитры нет (это же признаёт строка 105: «только светлая — тёмная тема удалена») |
| `util/WidgetUpdateHelper.kt:18` | «**Не блокирует UI поток лаунчера**» | блокирует: `updateAllWidgets` → `BellCountdownWidgetProvider.updateAll` → `runBlocking { dao.getLessonsForDaySync(...) }` (см. 5.1) |
| `util/BellTimerService.kt:60-65` | «Виджет обновляем тем же тактом» + счётчик `widgetRefreshCounter >= 1` | такт действительно есть, но условие всегда истинно — комментарий маскирует мёртвый счётчик |
| `data/network/MpkScheduleParser.kt:1025` и `TeacherScheduleParser.kt:11` | «нумеруют уроками **1..10**» | `CollegeBellSchedule` описывает 12 уроков, а `detectLessonNumber` принимает `1..12` — внутреннее противоречие |

### 3.3. Дублируют очевидное из кода (можно убрать без потерь)

- `ui/screens/ScheduleScreen.kt:263-264` — два одинаковых разделителя `// =====` подряд.
- `ui/screens/ScheduleScreen.kt:792-794` — **осиротевший KDoc**: «Панель подгруппы (50% ширины карточки) с микро-бейджем «1» или «2»» стоит **над** `TeacherChip` (док старой `SubgroupPane`, уехал при вставке нового компонента). Читается как описание не той функции.
- `ui/screens/CollegeMapScreen.kt:376-379` — то же: KDoc «Поле поиска кабинета. Своя реализация вместо стандартного поля…» приклеен к функции `findRoomEverywhere` (поиск кабинета по этажам), а не к `SearchField`. Ложное описание.
- `ui/screens/CollegeMapScreen.kt:46-47`, `:49-51` и подобные — пересказ того, что уже написано в `data class MapRoom` (`x`, `y` — доли от размера плана).
- `data/local/entity/LessonEntity.kt:16-25` — построчные комментарии `// 1 = Понедельник .. 6 = Суббота`, `// Например "08:30"` при говорящих именах; пользы мало, но и вреда нет (не трогать).
- `ui/screens/ScheduleScreen.kt:88-98`, `TasksScreen.kt:87-95`, `CalendarArchiveDialog.kt:89-99` — «спецификации» дизайн-системы (кегли/цвета) в шапке: полезны как ссылка на ТЗ заказчика, но дублируют код (`TextStyle`, `Color`) и **устаревают молча** (в архиве размеры уже разошлись с кодом: `fontSize 16.sp` вместо 15, `TextStyleCalendarWeekdays` вместо 13sp Bold).

### 3.4. Что НЕ трогать (ценные объяснения «почему»)

Это комментарии, которые несут знание, не выводимое из кода, — их следует сохранить при любой чистке:

- `data/network/MpkScheduleParser.kt:265-268,289-290` — почему сначала UTF-16LE, потом CP1251, и почему статистику UTF-16 нельзя затирать.
- `data/network/MpkScheduleParser.kt:674-685` — изоляция стратегий разбора (падение одной не убивает разбор).
- `data/util/GroupParser.kt:74-78, 53-55` — почему ручной разбор вместо регекса и почему кириллица задана явным диапазоном `Ѐ-ӿ` (разное поведение JVM/ICU).
- `data/repository/ScheduleRepository.kt:15-25, 134-138` — смысл чистки архива и почему «не похоже на дату» = не устарело.
- `ui/components/CalendarArchiveDialog.kt:592` + `MonthGrid.kt:33-42` — почему своя сетка вместо библиотечного `DatePicker`.
- `util/BellTimerService.kt:16-26` — почему служба, а не `AlarmManager` (Doze у производителей).
- `data/network/MpkScheduleParser.kt:228-231,233-234` — почему нет смысла пробовать текстовые фолбэки для настоящего OLE2.
- `worker/MpkWorkManagerHelper.kt:17-25,43-45` — почему 15 минут и почему `UPDATE`, а не `KEEP`.
- `ui/screens/TasksScreen.kt:741-745,758-763` — UTC-семантика дат срока и почему старый произвольный текст показывается как есть.
- `ui/screens/CollegeMapScreen.kt:461-463` — почему в кнопке нельзя `fillMaxSize`.
- `util/BackupManager.kt:72-74, 90-97` — почему `id` не сохраняется и почему дубликаты пропускаются.
- `widget/TodayScheduleWidgetProvider.kt:19-20` — почему строки добавляются через `addView`, а не через `RemoteViewsService`.

---

## 4. Виджеты

Проверены все шесть файлов `app/src/main/java/com/example/widget/*` + четыре `xml/*_widget_info.xml` + четыре layout-файла + регистрация в манифесте.

### 4.1. Регистрация и обновление — что в порядке

- Все **4 провайдера** зарегистрированы в манифесте с `APPWIDGET_UPDATE` и своим `appwidget-provider` meta-data: `BellCountdownWidgetProvider`, `NowNextWidgetProvider`, `TodayScheduleWidgetProvider`, `BellsWidgetProvider`.
- `WidgetConfigActivity` зарегистрирован с `APPWIDGET_CONFIGURE`, включён как `android:configure` во всех четырёх `*_widget_info.xml`, определяет виджет через `WidgetKind.of(...)` и корректно возвращает `RESULT_OK` с `EXTRA_APPWIDGET_ID`.
- `WidgetAlarmReceiver` — **не отдельный файл**: объявлен в конце `BellCountdownWidgetProvider.kt:332`, в манифесте на него ссылается `.widget.WidgetAlarmReceiver` (строка 109) → ссылка валидна, `ClassNotFoundException` не будет. Обрабатывает `ACTION_TICK` и `BOOT_COMPLETED`, после перезагрузки поднимает будильник и (если включено) службу строки «До звонка» — корректно.
- Обновление вызывается из: `MainActivity.onResume` → `updateAllWidgets`, `WidgetUpdateHelper.setSelectedGroup` → `updateAllWidgets`, `ScheduleCheckWorker` после успешной синхронизации, `WidgetConfigActivity` после настройки (`updateOne`), `BellTimerService` (раз в минуту), `WidgetAlarmReceiver` при тике, `SettingsScreen` после смены дебаг-времени.
- `WidgetAlarm` грамотно деградирует: `setExactAndAllowWhileIdle` при разрешении, иначе `setAndAllowWhileIdle`, `SecurityException` перехвачен, ночью будильник спит до 07:00, есть проверка `hasWork` (любой виджет или включённая строка «До звонка»).

### 4.2. 🔴 Минутный тик обновляет только один виджет из четырёх

Манифест обещает (строка 107): «**Минутный тик виджета** + перезапуск будильника после перезагрузки». Фактически `WidgetAlarmReceiver.onReceive` при `ACTION_TICK` делает ровно:

```kotlin
BellCountdownWidgetProvider.updateAll(context)   // только «До звонка»
WidgetAlarm.scheduleNext(context)
```

`NowNextWidgetProvider`, `TodayScheduleWidgetProvider`, `BellsWidgetProvider` при этом **не обновляются**. А у всех трёх в конфиге `updatePeriodMillis="0"` (системный период отключён) → они обновляются только когда: пользователь открыл приложение (`onResume`), сменил группу, отработал воркер (раз в 15 минут, и только при включённых уведомлениях) или система пересоздала виджет.

**Что это значит для пользователя:** «Сейчас и дальше» и «Расписание на сегодня» не переключают подсветку текущего урока в течение дня, а «Звонки» не подсвечивают текущую пару, пока не откроешь приложение. Минутный тик для них не работает, хотя будильник тикает.

**Что сделать:** в `ACTION_TICK` вызывать `WidgetUpdateHelper.updateAllWidgets(context)` (одна строка) — тогда все четыре виджета живут по минуте, как и задумано.

### 4.3. Размеры: конфиг и комментарии расходятся

| Виджет | `*_widget_info.xml` (`targetCell*`) | Комментарий в манифесте | Комментарий в коде |
|---|---|---|---|
| «До звонка» | **3x1** (`minWidth 180dp`, `minHeight 40dp`, `minResizeHeight 40dp`) | «(2x1)» (строка 35) | «Размер 2x1, растягивается до 3x1» |
| «Сейчас / дальше» | **3x2** (`minWidth 110dp`, `minHeight 110dp`) | «(3x2)» ✓ | «(2x3)» ✗ |
| «Расписание на сегодня» | **4x3** (`minWidth 250dp`, `minHeight 180dp`) | «(4x4)» ✗ | «Широкий (4–5 клеток)» |
| «Звонки» | **3x4** (`minWidth 180dp`, `minHeight 180dp`) | «(2x4)» ✗ | — |

Плюс `minWidth 110dp` у «Сейчас/дальше» соответствует 2 клеткам по старой формуле (`(size+30)/70`), а `targetCellWidth=3` — трём: на разных версиях Android виджет получит разную ширину.

**Что сделать:** выбрать эталонные размеры (заказчику), привести к ним `targetCell*`, `min*` и все три вида комментариев. Отдельно: **`minResizeHeight 40dp` у «До звонка» меньше высоты содержимого** (две строки текста 12sp + 26sp и padding 10dp ≈ 75dp) — при растягивании в минимум текст обрежется; поднять до ~70dp.

### 4.4. Обработчики: нет `onDeleted` и `onDisabled`

- **Ни у одного из четырёх провайдеров нет `onDeleted`.** При удалении виджета с рабочего стола его настройки в SharedPreferences (`widget_style_<id>`, `countdown_mode_<id>`, `widget_show_room_<id>`) остаются **навсегда** — id виджетов не переиспользуются, так что записи копятся мусором. Функции `WidgetStyle.remove` и `CountdownMode.remove` для этого написаны, но **не вызываются** (1.1) — обработчик просто не подключили. Первый виджет нового пользователя получает «случайный» стиль из мусора предыдущего.
- `onDisabled` (полное удаление всех виджетов) есть **только** у `BellCountdownWidgetProvider` (строки 37-39, `WidgetAlarm.cancel`). У трёх остальных его нет — будильник остаётся висеть, но самоизлечивается на следующем тике: `scheduleNext` проверяет `hasWork()` и не переставляет будильник. То есть это не утечка, а лишний холостой тик; тем не менее обработчики стоит добавить для симметрии.
- У «Сейчас/дальше» и «Расписания на сегодня» обработчик нажатия — единый `openIntent` на весь виджет (`setOnClickPendingIntent(R.id.*_root, ...)`), при этом внутри есть по-настоящему кликабельные смысловые элементы (`tv_next_subject`, строки уроков) — они не кликабельны и ведут только «в приложение». Замечание, а не дефект.

### 4.5. Данные виджетов: блокирующий запрос к БД в момент отрисовки

`WidgetData.todayLessons` (`NowNextWidgetProvider.kt:145-163`) и `BellCountdownWidgetProvider.countdownInfo` (строки 103-121) читают Room через `kotlinx.coroutines.runBlocking { … getLessonsForDaySync(...) }`. Вызовы приходят **на потоке вызывающего**:

- `AppWidgetProvider.onUpdate` → главный поток;
- `MainActivity.onResume` → главный поток;
- `WidgetUpdateHelper.setSelectedGroup` → вызывается из Compose-обработчиков (главный поток);
- `WidgetConfigActivity` → главный поток.

Итог: при открытии приложения и при смене группы главный поток ждёт обращения к диску (десятки миллисекунд, на слабом устройстве — больше). Само чтение всегда отдаёт данные (метод `suspend`, не падает), но лаунчер в этот момент может показать «Application Not Responding», если диск занят. Комментарий `WidgetUpdateHelper:18` («не блокирует UI поток») вводит в заблуждение — рядом даже лежит неиспользованный `widgetScope` (1.1), оставшийся от задуманного асинхронного варианта.

**Что сделать:** в `WidgetUpdateHelper` перейти на `widgetScope.launch { … }` с построением `RemoteViews` уже на IO-потоке (в `buildViews` читается только БД и prefs, `AppWidgetManager.updateAppWidget` можно звать из любого потока).

### 4.6. Мелочи

- `WidgetUpdateHelper.updateOne(context, appWidgetId)` фактически обновляет **все** виджеты этого типа (`updateAll`), а не один — имя и KDoc обещают обратное.
- `TodayScheduleWidgetProvider.MAX_ROWS = 12` — соответствует 12 урокам в расписании звонков; ограничение осознанное (комментарий на месте).
- Все четыре виджета используют один и тот же `previewImage` (1.3) — в галерее виджетов они выглядят одинаково.
- У «Звонков» в воскресенье показывается понедельничный график — осознанное решение с комментарием (строки 50-52), претензий нет.

---

## 5. Риски и падения

### 5.1. Блокирующие вызовы на главном потоке

| Где | Что блокируется |
|---|---|
| `widget/NowNextWidgetProvider.kt:150` + `widget/BellCountdownWidgetProvider.kt:104` | `runBlocking { … }` вокруг запроса к Room; вызывается из главного потока (см. 4.5) |
| `ui/screens/StudentsScreens.kt:92` | `StudentsRepository(context).loadStudents()` — чтение и разбор **`students.csv` (30 КБ, 1703 записи)** прямо в композиции (`remember { … }`), т.е. на главном потоке при первом показе экрана |
| `ui/screens/CollegeMapScreen.kt:128` | `CollegeMapData.load(context)` — чтение и `org.json`-разбор **`college_map.json` (65 КБ)** на главном потоке |
| `ui/screens/ScheduleScreen.kt:120`, `ui/screens/CollegeScreen.kt:135,787,1075`, `ui/screens/StudentsScreens.kt:403` | `TeachersRepository.loadTeachers()` — чтение и разбор `teachers.csv` (30 КБ, 101 запись) в композиции; на экране расписания это происходит **до** первой отрисовки списка |
| `ui/screens/CollegeScreen.kt:1442` | `ScheduleRepository(MpkDatabase.getInstance(context).lessonDao())` создаётся **прямо в композиции** при каждом показе календаря |

### 5.2. Возможные исключения и некорректные данные

1. **`data/model/CollegeBellSchedule.kt:29-37`** — `BellItem.startMinutes/endMinutes` делают `start.split(":")[0].toInt() * 60 + parts[1].toInt()` без проверок: строка без `:` или с буквами → `IndexOutOfBoundsException`/`NumberFormatException`. Сейчас данные приходят только из констант, поэтому не стреляет, но рядом в `NowNextWidgetProvider.kt:178-187` живёт **оборонительный** `parseHhMm()` (возвращает `-1`), которым пользуются виджеты. Два разных контракта на одно и то же поле — при первом же импорте данных извне одна из веток упадёт.
2. **`ui/screens/CollegeScreen.kt:180-184, 221-227`** — запись состояния **во время композиции** (`page = OtherPage.TEACHERS` прямо в теле `when`), плюс ветка `STUDENT_CARD` при `student == null` рендерит `OtherMenu` целиком копией. Это не падение, но Compose такие записи не любит (лишние рекомпозиции, предупреждение линтера); правильнее — `LaunchedEffect`/`SideEffect`.
3. **`ui/screens/TasksScreen.kt:390-394, 489-493`, `ui/components/GroupSelectionDialog.kt:124-127`** — на `IconButton` навешан **второй** обработчик через `bouncyClickable`: `IconButton(onClick = onToggleCompleted, modifier = Modifier.size(32.dp).bouncyClickable(onClick = onToggleCompleted))`. Два `clickable` в одной цепочке — лишний код; какой из них сработает, зависит от порядка модификаторов (сейчас — внутренний, от `IconButton`). Для «удалить задание» это означает, что поведение кнопки держится на порядке модификаторов, а не на явном контракте. Оставить один обработчик.
4. **`ui/components/CalendarArchiveDialog.kt:435-458`** — при выборе дня `parseDateSafely(dateStr)` может вернуть `null` (если строка не распознана); тогда `when (cal?.get(...))` уходит в `else -> 1`, т.е. молча считается «понедельник» вместо ошибки/ничего. Сегодня строка всегда генерируется тут же в ISO, так что не стреляет, но защита мнимая.
5. **`data/repository/ScheduleRepository.kt:26-36`** — `isStaleDate` строит `Calendar` из подстрок без проверки валидности (проверка регексом есть, но `31.02.2026` пройдёт и «нормализуется» в март). В сочетании с 2.1 может удалить живые данные; безопаснее сравнивать как строку ISO (`yyyyMMdd`), а не через `Calendar`.
6. **`data/local/MpkDatabase.kt:45`** — `fallbackToDestructiveMigration()` (осознанное решение «ZERO CRASH» из комментария). Напоминание: при **любом** несовместимом изменении схемы **все задания студента будут стёрты**. Митигация — резервная копия в `BackupManager`, но она ручная; стоит добавить хотя бы предупреждение в UI после миграции.
7. **`worker/ScheduleCheckWorker.kt:84`** — `SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())`: в локалях с не-григорианским календарём (например, тайский) дата в ключе дедупликации и в тексте пуша разойдётся с остальным приложением (везде `Locale.ROOT`). Плюс воркер **не знает про `DebugClock`** — при включённом дебаг-времени пуши продолжают считаться по системному.
8. **`MainActivity.kt:47-49, 60-63, 71-76`** — `handleIntent` вызывается только в `LaunchedEffect(intent)` на создании Activity и читает **только** `EXTRA_OPEN_TAB`. При этом `NotificationHelper:63-65` кладёт в интент `EXTRA_DAY_OF_WEEK` и `EXTRA_TARGET_DATE`, **которые никто не читает** (0 вхождений в проекте). Значит: (а) нажатие на пуш не открывает расписание на нужный день; (б) если приложение уже запущено, `onNewIntent` не приводит к смене вкладки вообще (интент обновляется, но композиция об этом не узнаёт). Два параметра `showScheduleReleaseNotification` (`targetDayOfWeek`, `targetDate`) работают только на текст пуша.
9. **`data/network/MpkScheduleParser.kt:60-61`** — `var lastParseStats` — глобальное изменяемое состояние парсера. При одновременной синхронизации (воркер + ручное обновление) диагностика перемешается: `groupFound` может быть от чужого разбора. Не портит уроки, но путает следствие.
10. **`util/BellTimerService.kt:39`** — `startForeground(id, buildNotification(this))` в `onCreate` без `try`: если построение уведомления когда-нибудь бросит исключение, служба упадёт при старте (сейчас `countdownInfo` внутри всё глушит `catch`, так что риск низкий).
11. **`ui/screens/CollegeScreen.kt:1400-1416`** — кэш `cache = remember { mutableMapOf<...>() }` не переживает пересоздание и не ограничен по размеру (в рамках сессии — не проблема).

### 5.3. Порядок реагирования

1. Захардкоженный `2026` (2.1) — отказ основного сценария с 01.01.2027.
2. Минутный тик только для одного виджета (4.2) — виджеты показывают устаревшее состояние.
3. Блокирующие вызовы на главном потоке (5.1) — лаги/ANR при открытии и смене группы.
4. `hasData` в архиве (1.6) — индикатор «есть расписание» показывает точки на всех днях.
5. Пуш не открывает нужный день (5.2.8) — пользователь не понимает, зачем нажал.
6. Мёртвый `"41О"` в `GroupBadge` (1.1) — при первом же использовании компонента подставит чужую группу.
7. `DebugClock` без гейта (2.4) — «приложение сломалось».

---

## 6. Эффективность

| Где | Проблема | Что сделать |
|---|---|---|
| `ui/screens/ScheduleScreen.kt:292-298` | `rememberInfiniteTransition` + `animateFloat(0f → 360f)` запускается **всегда**, даже когда `isSyncing == false` — бесконечная анимация крутит кадры, пока экран открыт (а экран открыт почти всегда). Значение используется только в условии `if (isSyncing)` | включать анимацию только при синхронизации (`animateFloatAsState(targetValue = if (isSyncing) 1f else 0f)` или `if (isSyncing) rememberInfiniteTransition(...)`) |
| `ui/screens/StudentsScreens.kt:92`, `CollegeMapScreen.kt:128`, `ScheduleScreen.kt:119-128`, `CollegeScreen.kt:787,1075`, `StudentsScreens.kt:403` | Чтение и разбор ассетов (30 + 30 + 65 КБ) в композиции, на главном потоке | вынести в `produceState`/`LaunchedEffect(Dispatchers.IO)` c состоянием загрузки, либо кэшировать в объекте-синглтоне |
| `ui/screens/CollegeMapScreen.kt:128` | `CollegeMapData.load()` не кэшируется между заходами на карту (парсинг JSON каждый раз) | кэш по образцу `MpkDatabase.getInstance` |
| `util/SubjectFormatter.kt` (`normalize` → цикл `calculateSimilarity` по всем официальным предметам) | Вызывается из `LessonEntity.shortSubjectName`/`subjectAcronym` (`by lazy` — кэш **на экземпляре**). Room на каждое изменение отдаёт **новые** экземпляры → для списка из 10 пар нормализация прогоняется заново, в том числе из композиции (`ScheduleScreen.LessonCard`) | сделать кэш `SubjectFormatter` по `subjectRaw` (`ConcurrentHashMap`), либо просить у Room `@Ignore`-поля заранее посчитанными |
| `util/GroupParser.kt:62-68` | `matchesGroup` на каждый вызов компилирует `"\\s+".toRegex()` и `createGroupSearchRegex(...)`; вызывается в цикле по блокам/ячейкам парсера | вынести оба регекса в `private val` / кэшировать по группе (`mutableMapOf<String, Regex>`) |
| `ui/components/CalendarArchiveDialog.kt:150-164` | Поиск фильтрует весь список уроков группы на **каждое нажатие клавиши**, без debounce и на главном потоке | `derivedStateOf` + debounce 150-250 мс, либо `snapshotFlow` |
| `widget/WidgetUpdateHelper.kt:62-67` → `BellCountdownWidgetProvider.updateWidgets` | Для каждого экземпляра виджета — свой запрос к БД и свой `runBlocking`; при 3 виджетах на минуту приходится 3 запроса | один запрос на тип виджета, затем раздать в `RemoteViews` по id |
| `widget/BellCountdownWidgetProvider.kt:287-297` | `WidgetAlarm.hasWork()` дёргает `AppWidgetManager.getAppWidgetIds` 4 раза на каждый тик/планирование | считать один раз и кэшировать в prefs-флаге `есть виджеты` (обновлять в `onEnabled`/`onDisabled`/`onDeleted`) |
| `ui/screens/CollegeScreen.kt:794-796, 1080`, `StudentsScreens.kt:404-406` | `TeacherInsights` (построение карт по всем урокам группы) пересобирается при каждом изменении потока уроков | пометить `remember(teachers, allLessons)` уже есть — ок; но на экране преподавателя это происходит дважды за сессию (список + карточка). Держать один инстанс на экран |
| `ui/screens/ScheduleScreen.kt:198, 151-161` | `dayNamesNominative` (аллокация списка) и локальные функции создаются на каждую рекомпозицию | вынести список в `private val` файла |
| `ui/screens/CollegeMapScreen.kt:801-830` | `drawMap` рисует и измеряет номера **всех** кабинетов на каждый кадр анимации зума | часть уже кэширована (`numberLayouts`/`remember(floorId, floor, density)`), но `drawText` вызывается для всех комнат: отсекать по видимой области (`clipRect`) |
| `ui/screens/CollegeScreen.kt:1440-1445` | `ScheduleRepository(...)` и `MpkDatabase.getInstance(...)` в композиции + запросы `getDistinctDates("")`/`getAllLessonsForGroup("")` по пустой группе (заведомо пустой результат) | `remember { ScheduleRepository(...) }`; либо не открывать архив расписания в карточке преподавателя вовсе (он там показывает пустой календарь) |

---

## 7. Что удалить безопасно

Порядок: сначала удаление, потом (отдельно) правки логики. Всё перечисленное проверено на отсутствие вызовов (включая тесты).

**Целые файлы (3):**
1. `app/src/main/java/com/example/ui/components/GroupBadge.kt` (115 строк)
2. `app/src/main/java/com/example/ui/components/IntroSplash.kt` (175 строк)
3. `app/src/main/res/drawable/bg_widget_card.xml`

**Функции/поля (26):**
`LessonDao.insertLesson`, `LessonDao.deleteLessonsForDay`, `TaskDao.clearTasksForGroup`, `LessonEntity.officialSubjectName`, `LessonEntity.timeRangeDisplay`, `CollegeMap.looksLikeRoomNumber`, `MpkScheduleParser.DOC_LINK_REGEX`, `ScheduleRepository.searchLessons` (+ `LessonDao.searchLessonsForGroup`), `ScheduleRepository.saveLessons`, `ScheduleRepository.replaceSchedule` (+ `LessonDao.replaceScheduleForGroup`), `ScheduleRepository.clearSchedule`, `TeachersRepository.toggleFavorite`, `TeachersRepository.favorites`, `MonthGrid.MONTH_NAMES_GENITIVE`, `SimpleDatePickerDialog.isoFromParts`, `CollegeScreen.SubPageHeader`, `AppTheme.currentAppTheme`, `BellCountdownNotifier.refresh`, `SubjectFormatter.shortenAlgorithmic`, `BellTimerService.widgetRefreshCounter` (заменить на прямой вызов), `WidgetUpdateHelper.widgetScope`, `TasksScreen.description` (state), `CalendarArchiveDialog.RoomPanel`-ветка `room == null`, `TeacherScheduleParser` дублирующая ветка `contains("КУРС")`, `BackupManager.KEY_APP` (пишется, не читается).

**Ресурсы:** 17 неиспользуемых цветов + 4 стиля текста + 8 мёртвых токенов палитры (1.3, 1.4). При удалении стилей/токенов придётся убрать и поля `MpkPalette`, которые используются только ими (`menuBg`, `menuSubBg`, `menuIcon`, `activeFill`, `successBorder`, `warning`, `errorText`).

**Импорты:** 40 неиспользуемых (1.5) — удаляются IDE одним действием.

**Мусор в рабочей папке (не код):** 38 APK-файлов (`moy-polytech-v2.*.apk`, `mpk-schedule-v2.5.1-debug.apk`, суммарно 883 МБ) в корне проекта; они в `.gitignore`, но занимают место и путают («какой из них последний?»). Последний релиз — `moy-polytech-v2.25-release.apk`. Также в корне лежат одноразовые артефакты чужого аудита: `_fix_audit.py`.

**Условно безопасно (решение заказчика):** test-only API из 1.2 — удалять только вместе с тестами или сначала «оживить», убрав дубли.

---

## 8. Что требует решения заказчика

1. **Судьба подсистемы активации.** Из рабочего дерева удалены `ActivationScreen`, `AppCodeScreen`, `ActivationStore`, `UnlockToken`, `UnlockTokenTest` (и правки в `MainActivity`, `MainScreen`, `AppViewModel`, `CollegeScreen`), но **не закоммичены**. Нужно решить: это осознанный отказ от активации по коду навсегда (тогда коммитим и больше не возвращаемся) или временная мера. Пока решения нет, репозиторий в состоянии «полураздетой» функции: комментарии в `DECISIONS.md`/`ROADMAP.md` могут противоречить коду.
2. **Дебаг-время в релизе** (2.4): убирать под `BuildConfig.DEBUG` или оставлять в UI с явным индикатором и кнопкой сброса?
3. **Формат дат** (2.2): закрепляем `дд.ММ.гггг` как единственный (и убираем все `replace("-", ".")` / правим комментарии) или переходим на ISO (`yyyy-MM-dd`) в БД? Второе — миграция схемы, поэтому решение дорогое; рекомендуется первое.
4. **Эталонные размеры виджетов** (4.3) — какие клетки считать правильными для каждого из четырёх?
5. **`fallbackToDestructiveMigration`** (5.2.6): оставляем «стирание при смене схемы» или заводим настоящие миграции? Пользовательские задания — это данные, которые нельзя терять молча.
6. **`DebugClock` + воркер** (5.2.7): должен ли фоновый воркер уважать подменённое время? От этого зависит, как тестировать уведомления.
7. **Пуш-уведомления** (5.2.8): должен ли тап открывать расписание на конкретный день (для этого уже передаются `EXTRA_DAY_OF_WEEK`/`EXTRA_TARGET_DATE`), или достаточно открыть приложение?
8. **Превью виджетов** (1.3): заказывать отдельные картинки-превью для трёх виджетов или оставить одно?
9. **`ROADMAP.md` / `DECISIONS.md` / `IDEAS.md` / `GITHUB-HOSTING.md` / `STUDENTS-AUDIT.md` / `DESIGN-AUDIT.md`** — рядом с отчётом лежат ещё 5-6 документов (в том числе созданных в параллель). Стоит решить, какой из них «источник истины», иначе через месяц будет непонятно, чему верить.

---

## Приложение: методика и как перепроверить

- **Мёртвый код** — по каждому объявлению (регекс по `fun|class|object|val|var|typealias` с модификаторами) считалось число вхождений имени по всем `*.kt` проекта (включая тесты), отдельно — в `main` и в `test`. «Мёртвое» = только собственное объявление; «test-only» = 1 вхождение в `main` + ≥1 в тесте.
- **Комментарии** — построчный разбор: `//`, строки внутри `/* … */`, строки, начинающиеся с `*` (KDoc). Итог: 1 539 строк в 61 файле `main` при 17 165 строках, то есть ~9%.
- **Ресурсы** — по всем `@color/`, `@drawable/`, `@string/`, `@layout/`, `@style/`, `@xml/`, `@mipmap/` в `*.kt` и `*.xml` (включая `res/`, манифест, `values-v31`).
- **Импорты** — по каждому `import` проверялось вхождение последнего сегмента в тело файла (строки `import` из поиска исключены; `getValue`/`setValue` исключены как нужные для делегатов `by`).
- **Виджеты** — сверка `AndroidManifest.xml` ↔ `xml/*_widget_info.xml` ↔ `layout/widget_*.xml` ↔ четыре провайдера; отдельно прослежены все точки вызова `updateAllWidgets`/`updateOne`/`WidgetAlarm.scheduleNext`.

Точки перепроверки ключевых находок:

```bash
grep -rn 'ifBlank { "2026" }' app/src/main/java                     # 2.1
grep -rn 'ACTION_TICK' app/src/main/java                            # 4.2
grep -rn 'runBlocking' app/src/main/java                            # 4.5 / 5.1
grep -rn 'hasData = savedDates' app/src/main/java                   # 1.6
grep -rn 'EXTRA_DAY_OF_WEEK\|EXTRA_TARGET_DATE' app/src/main/java   # 5.2.8
grep -rn 'DebugClock' app/src/main/java                             # 2.4
grep -rn 'replace("-", ".")' app/src/main/java                      # 2.2
```
