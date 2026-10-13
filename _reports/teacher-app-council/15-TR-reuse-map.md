# 15 — КАРТА ПЕРЕИСПОЛЬЗОВАНИЯ ОРИГИНАЛА (волна TR)

Роль: UX-архитектор консилиума. Режим: read-only (чтение/grep, gradle не трогал).
Дата: 2026-10-08. Все пути от `D:\MPK app\MPK-app-2.1\app\src\main\java\com\politex\` (если не указано).

Главный вывод: **оригинал уже содержит ~90% нужного UI в `main` source set**, а teacher-код
(пакет `ui/teacher/*`) — это почти entirely собственная разработка, которую заказчик велел убрать.
Стратегия волны TR: teacher-экраны **перестают быть своими** и начинают **вызывать студенческие
composable из `main` напрямую** (они не flavor-гейчены, компилируются в standard-прогоне — см.
DECISIONS.md «teacher-экраны в MAIN source set»). Данные — из `teacher_lessons` через
`TeacherDayLoader`/`TeacherWidgetData`; UI — 1-в-1 студенческий.

Ключевой технический факт, подтверждающий всю карту: **flavor-ветвление — ЕДИНСТВЕННАЯ точка в
`MainActivity.kt:74`** (`if (BuildConfig.FLAVOR == "teacher") TeacherRoot() else MainScreen()`).
Всё остальное общее. Значит переиспользование = «TeacherRoot рисует студенческие компоненты,
кормя их teacher-данными», а не «пишем параллельные teacher-версии».

---

## 1. Таблица «экран/компонент оригинала → файл:строки → как берём → что меняется»

| Экран/компонент оригинала | Файл:строки (сигнатура) | Как берём в teacher | Что меняется для преподавателя |
|---|---|---|---|
| `DaySlotCell(title,subtitle,selected,onClick,modifier)` | `ui/components/DaySlotCell.kt:34` | **1-в-1** (импорт из components) | Ничего. Подписи: title=«СЕГОДНЯ»/«ЗАВТРА», subtitle=`dd.MM` **без дня недели** |
| Ряд Сегодня/Завтра + кнопка календаря | `ui/screens/ScheduleScreen.kt:566-621` | **копия блока** в новый `TeacherScheduleScreen` | Кнопка календаря: `AppCard(size=Tokens.HitTarget=44dp)` + `Icon(DateRange, 20dp, ColorBrandBlue)` — берём как есть |
| `LessonCard(lesson,isCurrent,hasHomework,homeworkTypes,onHomeworkClick,onTeacherClick,isTeacherKnown,onRoomClick,pulse,focusAlpha,modifier)` | `ui/screens/ScheduleScreen.kt:866` | **копия с правками** (см. §2) | Вместо `TeacherChip`→`GroupChip`; `hasHomework/homeworkTypes/onHomeworkClick`→выкл (нет дз); `isSplit`/`SubgroupPane`→не нужен (у преподавателя пары не делятся на подгруппы в его разрезе) |
| `NoLessonCard(lessonNumber)` | `ui/components/NoLessonCard.kt:31` | **1-в-1** | Ничего («Урока нет») |
| Пустое состояние + кнопка «СИНХРОНИЗИРОВАТЬ С САЙТОМ» | `ScheduleScreen.kt:635-762` | **копия с правками** | Убрать упоминание группы; `onSyncRequest`→`TeacherDayViewModel.refresh()` |
| Шапка «Расписание» + подзаголовок | `ScheduleScreen.kt:501-527` | **копия** | title «Расписание»; subtitle — «Сегодня, dd.MM» / имя учителя НЕ показывать здесь (он в профиле) |
| `CalendarArchiveDialog(groupName,scheduleRepository,onDismiss,onDateSelected,onOpenRoom,onOpenTeacher,isTeacherKnown,modifier)` | `ui/components/CalendarArchiveDialog.kt:91` | **копия с правками** | Источник дат — `teacherLessonDao.getPresentDates`/`getTeachersOnDate` вместо group; поиск — по группам/предметам/кабинету учителя. `MonthGrid` (`ui/components/MonthGrid.kt:32`) — **1-в-1** |
| `MonthGrid(year,month,selectedDateIso,onSelect,modifier)` + `calculateMonthDays` | `ui/components/MonthGrid.kt:32,113` | **1-в-1** | Ничего |
| `BellsScreen(groupInfo,scheduleRepository,scrollState,scrollToCurrentRequested,onScrollToCurrentConsumed,modifier)` | `ui/screens/BellsScreen.kt:130` | **1-в-1** (таймлайн-рельс + `LessonBellCard:495` + `BreakRow:664` + `NowContextCard:423`) | Убрать зависимость от `groupInfo`/`scheduleRepository.getLessonsForDay(group)` (определяет «синий рельс до последнего урока группы»); для teacher `blueEnd` = номер последней пары учителя из `TeacherWidgetData.snapshot()`, либо `null` (рельс серый). Чипы типа (ПН-ПТ/ЧЕТВЕРГ/СУББОТА) — как есть |
| `NavigationBar` (4 вкладки) | `ui/screens/MainScreen.kt:287-338` | **копия стиля** (высота 72dp, `NavigationBarItem` colors selected=ColorTopBar / indicator=ColorSurfaceHighlight / unselected=ColorTextMuted, label 10sp) | 3 вкладки вместо 4 (см. §3). Иконки: Расписание=`CalendarMonth`, График=`AccessTime`/`CalendarMonth`, Ещё=`Apps` |
| `OtherMenu` («Другое») | `ui/screens/CollegeScreen.kt:223-301` | **копия с правками** (см. §3) | Порядок/состав строк — см. §3 |
| `MenuCard(icon,title,subtitle,onClick)` | `ui/screens/CollegeScreen.kt:304` | **1-в-1** (AppCard + icon-box 36dp ColorBrandFill + title 15sp bold + subtitle 12sp + ChevronRight 20dp) | Ничего |
| `TeachersPage(groupInfo,scheduleRepository,onBack,onOpenTeacher)` | `ui/screens/CollegeScreen.kt:701` | **копия с правками** + параметр `showLeadsBadge=false` (см. §3) | Вырезать «ВЕДУТ У ГРУППЫ»/«ВЕДЁТ У ВАС» параметром, не форком |
| `TeacherCard(teacher,isActive,roomsFromSchedule,onClick)` | `ui/screens/CollegeScreen.kt:814` | **1-в-1** + `showBadge:Boolean` | Шильд «ВЕДЁТ У ВАС» (`:838-856`) — под `if (isActive && showLeadsBadge)` |
| `TeacherCardPage` / `LeadsChip` | `CollegeScreen.kt:1069,1191` | **1-в-1** + `showLeadsChip=false` | `LeadsChip` (`:1164-1170`) скрыть параметром |
| `SettingsScreen(groupInfo,onGroupChanged,modifier)` | `ui/screens/SettingsScreen.kt:106` | **НЕ копируем целиком** (заточен под группу: дз/оценки/карта-маршруты/подгруппа). Берём **отдельные блоки**: theme-swatches, `SettingsSwitchRow:1337`, `SectionCaption`, `BellCountdownNotificationCard:934` | Новый `TeacherSettingsScreen` из этих блоков (см. §5, §6) |
| Тема: `AppTheme` enum (7 палитр) + `applyAppTheme` + `AppThemeStore` | `ui/theme/AppTheme.kt`, `util/AppThemeStore.kt` | **1-в-1, уже работает** (см. §5) | Ничего — механизм общий, `MainActivity:33` вызывает `applyAppTheme` для всех flavor |
| `Tokens` (44dp hit, 12sp/11sp тексты) | `ui/theme/Tokens.kt` | **1-в-1** | Заменяет `TeacherMetrics` (48dp) — удалить метрики |
| Палитра `MpkPalette`/`paletteState`/`ColorXxx` getters | `ui/theme/Color.kt` | **1-в-1** | Ничего |
| `MyApplicationTheme(content)` | `ui/theme/Theme.kt:9` | **1-в-1** | Ничего |
| `SearchBar(value,onValueChange,placeholder,modifier,leadingIcon,testTag,keyboardType)` | `ui/components/SearchBar.kt:39` | **1-в-1** | Ничего (уже используется в онбординге) |
| `SubPageHeader(title,onBack,subtitle,...)` / `BackButton` / `AppCard` / `AppDivider` / `SectionCaption` / `CenteredLoader` | `ui/components/*.kt` | **1-в-1** | Ничего |
| `FuzzySearch.score/normalize/wordsOf/damerauLevenshtein` | `util/FuzzySearch.kt` | **1-в-1** | Усилить допуск под TR1 (см. §10, п.4) |
| Виджеты: `BellCountdownWidgetProvider`/`NowNextWidgetProvider`/`TodayScheduleWidgetProvider`/`BellsWidgetProvider` | `widget/*.kt` + `res/layout/widget_*.xml` | **копия логики + те же layout XML** (см. §7) | Источник данных → `TeacherWidgetData.snapshot()` вместо `WidgetData.todayLessons(group)` |
| Уведомление «до звонка»: `BellCountdownNotifier` + `notification_bell_countdown.xml` | `util/BellCountdownNotifier.kt`, `res/layout/notification_bell_countdown.xml` | **копия с правками** (см. §6) | Оставить 2 стиля (минуты/секунды); выбор темы — dropdown |
| Иконка | `teacher/res/drawable/ic_launcher_foreground_teacher.xml` | **правка координат** (см. §8) | Уменьшить шапку |

---

## 2. Карточка пары преподавателя — ТОЧНО

### 2.1 Что переиспользовать из студенческой `LessonCard` (ScheduleScreen.kt:866-1066)

Берём **дословно** эти куски (номера строк оригинала):
- **Surface-обёртка** (`:882-891`): `RoundedCornerShape(2.dp)`, `color=ColorBgMain`, `border=BorderStroke(1.dp, if(isCurrent) ColorActiveBlue else ColorBorderLight)`, `shadowElevation=0.dp`, `testTag("lesson_card_$n")`.
- **Колонка + padding(12.dp)** (`:893-901`), `drawBehind` для `focusAlpha` — оставить (подсветка текущей).
- **Верхний Row: бейдж номера + время** (`:902-945`):
  - бейдж: `Surface(RoundedCornerShape(2.dp), ColorBrandFill, size(24.dp))` + `Text("$lessonNumber", 13sp Bold White)` (`:908-924`)
  - `Spacer(width=8.dp)` + `Text("${timeStart} – ${timeEnd}", 13sp SemiBold ColorTextBody)` (`:925-934`)
  - блок `if(hasHomework) HomeworkBadge(...)` (`:937-944`) — **УДАЛИТЬ** (у преподавателя нет дз).
- **Spacer(height=8.dp)** (`:947`).
- **Предмет** (`:988-1001`): `Text(subject.ifBlank{"Предмет уточняется"}, if blank 11sp Normal ColorTextMuted else 15sp Bold ColorTextTitle, maxLines=2)`. Для teacher: `subject = TeacherDayLesson.subject` (уже есть в лоадере), пустой → «Пара» (как сейчас в TeacherLessonCard) или «Предмет уточняется» (как в оригинале) — **рекомендую оригинальное «Предмет уточняется»** для 1-в-1.
- **Нижний Row (преподаватель/кабинет)** (`:1005-1062`) — см. 2.2 и 2.3.

**НЕ берём**: ветку `if(lesson.isSplit){ SubgroupPane... }` (`:949-986`) — у преподавателя одна строка
`teacher_lessons` уже его; подгруппы не нужны.

### 2.2 Замена «преподаватель» → «группа»

Данные: `TeacherDayLesson.groups: List<String>` (`data/repository/TeacherDayLoader.kt:76`), получается
из `TeacherLessonEntity.groups: String` split по `", "` (`splitGroups`, `:291`; вызов в `toLesson:301`).
Т.е. в БД `teacher_lessons.groups` — строка вида `"ИС-21, ИС-22"`.

В оригинальном нижнем Row (`:1010-1021`) стоит:
```
if (lesson.teacherFirst.isNotBlank()) {
    TeacherChip(name = lesson.teacherFirst, onClick = if(isTeacherKnown(...)) {...} else null, pulse)
}
```
**Заменяем на** `GroupChip` — визуальный клон `TeacherChip` (`:1069-1139`), но:
- вместо `Icons.Default.Person` → `Icons.Default.Groups` (или `School`);
- `name` → `groups.joinToString(", ")`;
- `onClick` → `onGroupClick(group)` (открывает «мои пары у группы» — `TeacherGroupLessonsScreen`, уже есть); если групп несколько — либо некликабельно, либо открывает первую. **Рекомендация:** кликабельно только при 1 группе (иначе неоднозначно), иначе `onClick=null` (плоский текст как в оригинальном `TeacherChip` при `onClick==null`, `:1076-1088`).
- contentDescription «Открыть карточку преподавателя» → «Мои пары у этой группы».

Размеры `GroupChip` = дословно `TeacherChip`: `Surface(RoundedCornerShape(2.dp), border=1.dp ColorBrandBlue@40%, color=ColorSurfaceHighlight)`, Row padding(start=5,end=2,top=2,bottom=2), icon 12dp, text 12sp SemiBold ColorBrandBlue, ChevronRight 14dp, внешний Box `heightIn(min=44.dp)` bouncyClickable(scaleDown=0.97).

### 2.3 Кнопка кабинета — «чуть больше» + кликабельна как в оригинале

**Оригинальная кнопка кабинета** (`ScheduleScreen.kt:1023-1061`):
```
Box(Modifier.heightIn(min = 44.dp).bouncyClickable(scaleDown=0.97f){ onRoomClick(roomFirst) }, Center) {
  AppCard(Modifier.pulseHalo(pulse).wrapContentWidth(), color=ColorSurfaceHighlight) {
    Row(CenterVertically, padding(horizontal=6.dp, vertical=2.dp)) {
      Icon(LocationOn, tint=ColorBrandBlue, size=12.dp)
      Spacer(width=2.dp)
      Text("каб. $roomFirst", 11sp Bold ColorBrandBlue)
    }
  }
}
```
Т.е. оригинальный тап по кабинету = **`onRoomClick(room)`**. В `MainScreen.kt:436-437` он ведёт:
`navController.navigate(buildMapRoute(room, fromRoom, showRoute=false))` → **`CollegeMapScreen`
(карта колледжа с открытым кабинетом)**. `RoomScheduleDialog` НЕ используется на этом пути (он
вызывается только из `CollegeMapScreen.kt:525`). **Вывод: оригинальное поведение тапа по кабинету =
открыть КАРТУ на этом кабинете.**

**Что делает teacher сейчас** (`TeacherHomeScreen.kt:457-474`): кабинет 30sp + отдельная квадратная
кнопка-флаг `Icons.Default.Flag` 48dp → `onRoomClick` → `TeacherNav` открывает `CollegeMapScreen(room)`.
Поведение то же (карта), но вид НЕ как в оригинале.

**Требуемая дельта (TR3: «чуть больше» + «как в оригинальном уроке»):**
Берём оригинальный блок 2.3 дословно и **увеличиваем на конкретную дельту** (заказчик: «ЧУТЬ больше»):
- внешний `Box.heightIn(min = 44.dp)` → **`48.dp`** (+4dp, = старый teacher hitTarget, но без ломающих пропорций);
- `Icon(LocationOn).size(12.dp)` → **`14.dp`**;
- `Text("каб. $room").fontSize 11.sp` → **`13.sp`**;
- `Row.padding(horizontal=6.dp, vertical=2.dp)` → **`horizontal=10.dp, vertical=4.dp`**;
- `AppCard.color = ColorSurfaceHighlight` — оставить; скругление 2dp — оставить.

Получается тот же оригинальный чип «каб. N» с иконкой, но физически крупнее (48dp hit, 13sp текст).
**Клик — ТОТ же: `onRoomClick(room)` → `CollegeMapScreen(initialRoom=room)`.** В `TeacherNav` уже есть
`TeacherSubPage.Map(room)` → `CollegeMapScreen` (`TeacherNav.kt:182-186`) — оставляем, просто убираем
отдельную кнопку-флаг.

### 2.4 Подтверждение: НЕТ «Править», НЕТ ⚑

- Кнопка **«Править»** сейчас: `TeacherHomeScreen.kt:488-499` (`Surface...Text("Править")`) + `onEdit`→
  `TeacherOverrideSheet` (`:527-619`). **УДАЛИТЬ полностью** (TR4).
- Маркер **«✎ ваша правка»**: `TeacherHomeScreen.kt:481-487`. **УДАЛИТЬ**.
- **Флаг ⚑** (`Icons.Default.Flag`, кнопка «как дойти»): `TeacherHomeScreen.kt:470`. **УДАЛИТЬ** — его
  заменяет кликабельный чип кабинета из 2.3.
- Бейдж **«ЗАМЕНА»** (`StatusLabel`, `:410-413`, `:412` ColorWarningText): **УДАЛИТЬ** (часть override-механики).
- Плашка **overrideResets** («Правка сброшена»): `TeacherHomeScreen.kt:218-240`. **УДАЛИТЬ**.
- Плашка **markOverridesPlate** в `TeacherNav.kt:199-221`. **УДАЛИТЬ**.

Итог: в карточке пары преподавателя остаются только: номер+время, предмет, чип группы, кнопка кабинета.
Никаких «Править»/⚑/✎/ЗАМЕНА. Подтверждаю.

---

## 3. Вкладки: «График» и состав «Ещё»

### 3.1 Три вкладки (TR5)

Стиль навигации — копия `MainScreen.kt:287-338` (NavigationBar height 72dp, те же `NavigationBarItemDefaults.colors`).
Уже почти есть в `TeacherNav.kt:126-177` — оставляем оболочку, меняем набор/подписи вкладок:

| # | Подпись | Иконка (filled/outlined) | Контент |
|---|---|---|---|
| 1 | **Расписание** | `CalendarMonth` | Новый `TeacherScheduleScreen` (§1, §2): Сегодня/Завтра + календарь + `LessonCard`-копия |
| 2 | **График** | `AccessTime` | **см. 3.2 — рекомендация: `BellsScreen` (таймлайн звонков)** |
| 3 | **Ещё** | `Apps` | Меню-копия `OtherMenu` (§3.3) |

`TeacherTab` enum (`TeacherNav.kt:59`) переименовать: `TODAY/WEEK/MORE` → `SCHEDULE/BELLS/MORE`
(или оставить имена, поменять подписи и контент). `TeacherNavLogic.tabForExtra` (`:70`) обновить под
новые строки deep-link.

### 3.2 «График» — ЧТО это (главная двусмысленность, см. §10)

По коду оригинала **«график» = `BellsScreen`** (`ui/screens/BellsScreen.kt:130`) — единственная
студенческая страница, которая буквально является графиком: вертикальный **таймлайн-рельс**
(`LessonBellCard:495` рисует рельс `drawLine`/`drawCircle` справа, `BreakRow:664` — перемены),
чипы типа дня (ПН-ПТ / ЧЕТВЕРГ / СУББОТА, `:279-337`), `NowContextCard:423` («сейчас урок/перемена»).

**Рекомендация: «График» = `BellsScreen` 1-в-1** (с минимальной правкой: убрать зависимость
`blueEnd` от группы — для teacher либо `null`, либо номер последней пары учителя из снапшота).

**Обоснование:**
1. Это **копия оригинального экрана** (прямое требование TR2/TR5), а не собственная разработка.
2. `BellsScreen` **группо-независим** по сути (звонки общие для колледжа) — работает у преподавателя без группы.
3. Альтернатива («недельный график пар» = старый `TeacherWeekScreen`) **прямо отменена** DECISIONS.md
   (`«экраны „Мой день"/„Неделя"/„Нагрузка" собственной разработки» — убрать`). Брать её = нарушить решение.

**Конфликт с §3.3:** если «График» = звонки, то строка «Расписание звонков» в «Ещё» дублирует вкладку.
**Разрешение (документируем, заказчик недоступен):** вкладка «График» = `BellsScreen`; из меню «Ещё»
строку «Расписание звонков» **убираем** (она теперь верхнеуровневая вкладка). Это唯一 не-дублирующая
трактовка, согласованная с «Ещё = Другое оригинала минус звонки». См. §10 п.1 — помечено как решение
консилиума, подлежит подтверждению.

> Fallback-трактовка (если заказчик имел в виду «мой недельный график пар»): «График» = недельная
> сетка Пн–Сб из `teacher_lessons`, собранная ИЗ ОРИГИНАЛЬНЫХ компонентов (DaySlotCell-подобные
> чипы дней + `LessonCard`-копии), БЕЗ旧 TeacherWeekScreen-метрик. Тогда «Расписание звонков»
> возвращается в «Ещё». Этот вариант дороже (нет готового оригинала для недельной сетки) — потому
> и не основной.

### 3.3 «Ещё» — состав строка-в-строку как оригинальное «Другое»

Оригинал `OtherMenu` (`CollegeScreen.kt:223-301`): шапка `Header(title="Другое", subtitle=...)` +
`LazyColumn(spacedBy 12.dp, padding 16.dp)` из `MenuCard`. Копируем оболочку, шапку переименовываем
в «Ещё». Строки (порядок оригинала, с поправкой на teacher):

| # | Иконка | Title | Subtitle | onClick |
|---|---|---|---|---|
| 1 | `LocationOn` | Карта колледжа | «Планы этажей и поиск кабинета» | `CollegeMapScreen` |
| 2 | `DateRange` | Найти группу | «Расписание любой группы колледжа» | `GroupScheduleScreen` (есть в `main`, `:204`) |
| 3 | `Person` | Преподаватели | «Поиск по базе, избранное и фотографии» | `TeachersPage` **без «ведёт у вас»** (§3.4) |
| 4 | `Settings` | Настройки | «Профиль, уведомления, масштаб и тема» | `TeacherSettingsScreen` (§5, §6) |

Отличия от оригинала:
- **УБРАТЬ** строку «Моя специальность» (`CollegeScreen.kt:258-265`, `School`) — у преподавателя нет своей группы/специальности.
- **УБРАТЬ** строку «Расписание звонков» — стала вкладкой «График» (§3.2).
- **УБРАТЬ** admin-строку «База данных учащихся» (`:243-256`) — teacher flavor не admin.
- **ДОБАВИТЬ/ОСТАВИТЬ** строку «Профиль: <ФИО> — сменить» (из текущего `TeacherMoreScreen.kt:96-103`)
  вверху, как `MenuCard` с иконкой `Person`/`AccountCircle`. Это требование TR6 («сменить преподавателя»).
- Порядок оригинала: специальность→расписание группы→карта→преподаватели→настройки. У нас после
  удаления: **Профиль → Карта → Найти группу → Преподаватели → Настройки** (профиль первым, т.к. замена
  «Моя специальность» как «якорь личности»; остальные — в оригинальном относительном порядке).

**Что ВЫРЕЗАТЬ из текущего `TeacherMoreScreen.kt`** (§9 детально): `NotifSection` (`:181` — сводка/изменения/напоминание), строка «Не нашли себя или заметили неточность?» (`:105-111`), блок «СТАБИЛЬНОСТЬ/OEM» (`:143-154`, `TeacherOemHelpPage:378`) — оставить только если заказчик подтвердит (по брифу не требуется; **рекомендую удалить** как «своё изобретение»). `TeacherBellsPage:310` — заменить на вкладку-`BellsScreen`.

### 3.4 «Преподаватели» БЕЗ «ведёт у вас» — точное место и как вырезать параметром

Шильдик «ВЕДЁТ У ВАС» рендерится в **двух** местах:
1. **Список** `TeacherCard` — `CollegeScreen.kt:838-856`:
   ```
   if (isActive) { Surface(...ColorActiveBlue...) { Text("ВЕДЁТ У ВАС", 9sp Bold White) } }
   ```
2. **Заголовки-секции** в `TeachersPage` — `CollegeScreen.kt:744` (`TeacherRow.Header("ВЕДУТ У ГРУППЫ $group (...)")`) и подзаголовок `:759` (`"$n ведут у $group"`).
3. **Карточка** `TeacherCardPage` → `LeadsChip` — `CollegeScreen.kt:1164-1170,1191-1238` («ВЕДЁТ У ВАС»/«+ ВЕДЁТ У ВАС»/«ОТМЕТКА СНЯТА»).

**Как вырезать БЕЗ форка экрана — добавить параметр `showLeads: Boolean = true`:**
- `TeachersPage(groupInfo, scheduleRepository, onBack, onOpenTeacher, showLeads=true)` — при
  `showLeads=false`: (а) не строить `TeacherInsights`/не сплитить на active/rest (весь список =
  «ВСЕ ПРЕПОДАВАТЕЛИ», `:739-753` → просто `teachers.sortedBy{name}`); (б) подзаголовок `:759` =
  `"$n сотрудников"` без «ведут у»; (в) `TeacherCard(isActive=false, showBadge=false)`.
- `TeacherCard(teacher, isActive, roomsFromSchedule, onClick, showBadge=true)` — `if(isActive && showBadge){...}` на `:838`.
- `TeacherCardPage(..., showLeadsChip=true)` — `if(showLeadsChip) item{ LeadsChip(...) }` на `:1164`.
- `TeachersRoute`/`TeacherCardRoute` (`:130,147`) — пробросить параметр.

Teacher вызывает `TeachersRoute(..., showLeads=false)`. **Нулевой форк, один параметр** — стандартный
паттерн проекта (ср. `pulse`, `isTeacherKnown` уже параметризованы).

Данные `TeacherInsights` (`data/repository/TeacherInsights.kt:7`) в teacher-ветке не нужны (нет
`allGroupLessons`) — при `showLeads=false` не загружаем `scheduleRepository.getAllLessonsForGroup`,
экономим запрос.

---

## 4. МАСШТАБ 75/100/125% — механизм

### 4.1 Есть ли уже что-то подобное?
**НЕТ.** `grep fontScale|LocalDensity|Density(|scaleFactor|uiScale` по всему `main/java/com/politex`
→ **0 совпадений** (единственное `scaleDown` — параметр анимации `bouncyClickable`, не масштаб).
Текущий `TeacherMetrics` (`ui/teacher/TeacherTheme.kt:12-19`: hitTarget=48dp, chipText=13sp,
body=16sp, buttonText=14sp) — **не масштаб, а фикс-метрики**, и заказчик велел их убрать
(DECISIONS.md:51). Значит механизм строим с нуля.

### 4.2 Варианты и выбор

| Вариант | Суть | Плюсы | Минусы |
|---|---|---|---|
| A. `LocalDensity` override (uniform zoom) | `CompositionLocalProvider(LocalDensity provides Density(base.density*k, base.fontScale))` вокруг teacher-контента | **Масштабирует dp И sp ЕДИНООБРАЗНО** → вся раскладка идентична, только крупнее/мельче. Пересечений нет по построению. 1 строка + store. Как системный «Размер экрана» Android | При k>1 фикс-высоты без скролла могут клипаться вертикально (митигируется: оригинал весь в LazyColumn/verticalScroll) |
| B. Только `fontScale` | `Density(base.density, base.fontScale*k)` | Текст растёт, коробки на месте | Текст **налезает** на фикс-коробки (12sp→15sp в бейдже 24dp) — ровно то, чего боится заказчик |
| C. CompositionLocal со scale-множителем для sp/dp вручную | Свой `LocalUiScale`, каждый размер `*scale` | Точечный контроль | Нужно править **каждую** sp/dp-литералу (их сотни) — огромный диф, источник багов. Противоречит «копия 1-в-1» |
| D. Обёртка темы с токенами | Все размеры через `Tokens`/метрики | Системно | Оригинал использует inline-литералы (`13.sp`, `12.dp`) повсеместно — не токенизирован. Переписывать = не «копия» |

### 4.3 РЕШЕНИЕ: **Вариант A — uniform `LocalDensity` override.**

**Обоснование (лестница: rung 4 «нативная фича платформы»):** это буквально как Android Display Size /
`Configuration.densityDpi`, только внутри приложения и без перезапуска Activity. Математически:
`dp.toPx = value*density*k`, `sp.toPx = value*fontScale*density*k` — **обе единицы умножаются на один
`k`**, поэтому относительные пропорции раскладки НЕ меняются вовсе. Это единственная опция, где
«пересечения/налезания» невозможны в принципе (всё масштабируется вместе, включая отступы и размеры
коробок) — прямой ответ на страх заказчика.

**Реализация (минимум кода):**
```kotlin
// util/UiScaleStore.kt  (клон AppThemeStore — тот же prefs "mpk_ui_prefs")
object UiScaleStore {
    private const val KEY = "ui_scale"
    fun load(c: Context): Float = c.getSharedPreferences("mpk_ui_prefs", MODE_PRIVATE).getFloat(KEY, 1f)
    fun save(c: Context, k: Float) { c.getSharedPreferences("mpk_ui_prefs", MODE_PRIVATE).edit().putFloat(KEY, k).apply() }
}

// В TeacherRoot (или MainActivity под teacher-веткой):
val k = UiScaleStore.load(context)
val base = LocalDensity.current
CompositionLocalProvider(LocalDensity provides Density(base.density * k, base.fontScale)) {
    TeacherNav(...)
}
```
`k ∈ {0.75f, 1.0f, 1.25f}`. При смене в настройках — `save` + recompose (чтение `k` как `state`).
**Важно:** применять `LocalDensity` ТОЛЬКО внутри teacher-поддерева (не глобально), чтобы не задеть
виджеты/уведомления (RemoteViews живут вне Compose, на системный density).

### 4.4 Риск «пересечений» и как проверить

Риск A **минимален** (uniform). Остаточные риски и митигация:
1. **Горизонтальный overflow фикс-строк без скролла** при 125%: ряд Сегодня/Завтра (2×`weight(1f)`+44dp
   календарь, `ScheduleScreen.kt:566-621`) — `weight` поглотит, ОК. Чипы типа звонков (`BellsScreen:279`,
   `weight(1f)`) — ОК. `DaySlotCell` title `softWrap=false,maxLines=1` — при 125% «СЕГОДНЯ» 12sp→15sp
   в ~1/3 экрана: проверить, не обрезается ли. Митигация: `weight` уже там; при нужде уменьшить `letterSpacing`.
2. **Вертикальный клип фикс-высот**: `Tokens.ButtonHeight=44dp`, `NavigationBar.height(72.dp)`
   (`MainScreen:291`) — при 125% контент внутри растёт, но высота фикс. → **рекомендация:** высоту
   нижней навигации и кнопок НЕ хардкодить под scale (оставить 44/72dp как «пол» через `heightIn(min=)`),
   чтобы текст влез. В оригинале часть уже `heightIn(min=44.dp)` (не фикс) — хорошо.
3. **Theme-swatches LazyRow** (`SettingsScreen:615`, 92dp карточки) — LazyRow скроллится, ОК.

**Проверка (roborazzi на 3 масштабах):** прогнать record для teacher-скринов при `k=0.75/1.0/1.25` на
самых плотных экранах: (а) ряд Сегодня/Завтра+календарь, (б) `LessonCard` с длинной группой «ИС-21, ИС-22»
+ чип кабинета, (в) чипы типа звонков, (г) theme-swatches, (д) меню «Ещё». Сравнить с vision-агентом:
нет ли обрезки текста/налезания. Существующая инфра: `_reports/teacher-app-council/shots/` (8 png),
`PromoShotsTest`, `MapScreenShotTest.kt` — добавить `TeacherScaleShotTest` (3 кадра × 5 экранов).
**Гейт:** при 125% ни один `Text(maxLines=1)` не обрезан по ширине; при 75% hit-зоны ≥ 44dp*0.75=33dp
(минимально приемлемо; ниже 40dp — предупредить, что 75% снижает доступность тапа).

> `ponytail:` uniform-scale — потолок: если заказчик захочет «только текст крупнее, отступы те же»
> (вариант B), это другой механизм; A покрывает «общий масштаб интерфейса», что и просит бриф.

---

## 5. Тема приложения — механизм оригинала и включение в teacher

### 5.1 Как устроено в оригинале
- **7 палитр**: `enum AppTheme` (`ui/theme/AppTheme.kt:5-84`): COLLEGE(«Фирменная»), EMERALD, BORDEAUX,
  GRAPHITE, INDIGO, AMBER, STEEL. Каждая = `id/title/hint/palette:MpkPalette`. Базовая `LightPalette`
  (`ui/theme/Color.kt:46`), остальные — `paletteWithAccents(topBar,brand,active)` (`:86`).
- **Хранение**: `util/AppThemeStore.kt` — SharedPreferences `"mpk_ui_prefs"`, ключ `"app_theme"` (id строкой). `save()` сразу зовёт `applyAppTheme()`.
- **Применение**: `applyAppTheme(theme)` (`AppTheme.kt:99`) пишет `paletteState = theme.palette`.
  `paletteState` (`Color.kt:85`, `mutableStateOf`) — источник всех `ColorXxx` getters (`:87-126`),
  они `get() = paletteState.xxx` → **смена палитры = recompose всего UI без пересоздания Activity**.
- **Инициализация**: `MainActivity.kt:33-37` — `applyAppTheme(AppThemeStore.load(this))` **для ВСЕХ flavor** (не под гейтом).
- **UI выбора**: `SettingsScreen.kt:613-668` — `LazyRow` swatches (`items(AppTheme.entries)`), каждая
  карточка 92dp с мини-превью (topBar-бокс 36dp + brandBlue-полоса), title 11sp, `bouncyClickable{onThemePicked}`.
  `onThemePicked` (`:146`) = `AppThemeStore.save` + обновить `currentTheme`.

### 5.2 Включение в teacher — ТОТ ЖЕ КОД, ТЕ ЖЕ ПАЛИТРЫ
**Механизм уже активен в teacher**: `MainActivity:33` применяет тему до ветвления flavor; `TeacherRoot`
→ `TeacherTheme` (`ui/teacher/TeacherTheme.kt:22`) → `MyApplicationTheme`. Все teacher-экраны читают
`ColorBrandBlue`/`ColorTopBar`/... из той же `paletteState`. **Значит смена темы УЖЕ работает** — не
хватает только UI-выбора.

**Что сделать (минимум):**
1. В новый `TeacherSettingsScreen` (§6) **внизу** (TR6: «тема приложения (внизу)») вставить блок
   swatches — **скопировать 1-в-1** `SettingsScreen.kt:610-668` (`SectionCaption("ВНЕШНИЙ ВИД")` +
   `LazyRow(items(AppTheme.entries))`). Тот же код, те же 7 палитр, тот же `AppThemeStore`.
2. `currentTheme = AppThemeStore.load(context)` + `onThemePicked` — дословно из `SettingsScreen:145-149`.
3. **Удалить** `TeacherTheme`-обёртку как источник метрик; оставить только `MyApplicationTheme`
   (+ `LocalDensity` из §4). Т.е. `TeacherTheme(content)` сводится к `CompositionLocalProvider(LocalDensity...){ MyApplicationTheme(content) }`.

**Ноль новых палитр, ноль нового кода хранения** — чистое переиспользование. Тема и масштаб живут в
одном prefs-файле `"mpk_ui_prefs"` (ключи `app_theme` / `ui_scale`).

---

## 6. Уведомление «до звонка» — TR7

### 6.1 Оригинал (`util/BellCountdownNotifier.kt`)
- **Style enum** (`:64-83`): `COMPACT`, `WITH_APP_NAME`, `WITH_LESSON`, `WITH_PROGRESS`, `WITH_SECONDS`.
- **Theme enum** (`:47-62`): SYSTEM, BRAND, SKY, DARK (accent + colorize).
- **Scope enum** (`:33-45`): MY_LESSONS / COLLEGE.
- Рендер: `buildVisibleNotification:269` → layout `res/layout/notification_bell_countdown.xml`
  (`nbell_value` 26sp Bold + `nbell_unit` 13sp + `nbell_label` 12sp + `nbell_app` + `nbell_progress`).
  `WITH_SECONDS` → `formatMinutesSeconds` (`:277`), иначе `formatMinutes`. `WITH_PROGRESS` → ProgressBar.
  `WITH_LESSON` → дописывает номер урока в label (`:338`).
- **UI выбора сейчас**: `SettingsScreen.kt:1062-1180` — СТИЛЬ (radio-список `Style.entries` минус
  `WITH_APP_NAME`, `:1074`) + ЦВЕТОВАЯ ТЕМА (список `Theme.entries` с accent-кружком, `:1135`).
- Звук: канал `setSound(null,null)` (`:159`) + `setSilent(true)` — **беззвучное**. «Темы звонка» как
  звука НЕТ (нет `R.raw`, нет `RingtoneManager` — grep подтвердил: единственный `setSound` = null).

### 6.2 Что оставить / что выкинуть (TR7: «ТОЛЬКО минуты/секунды»)
- **ОСТАВИТЬ** 2 стиля: `COMPACT` (минуты) и `WITH_SECONDS` (секунды). Это ровно «в минутах / в секундах».
- **ВЫКИНУТЬ** из `Style`: `WITH_APP_NAME` (уже мёртв — `from()` мапит его в COMPACT, `:80`),
  `WITH_LESSON`, `WITH_PROGRESS`. → В teacher-копии enum оставляем только 2 значения; `WITH_PROGRESS`-блок
  (`:331-337`) и `WITH_LESSON`-блок (`:338`) в рендере удалить; `nbell_progress`/`nbell_app` в layout — GONE навсегда.
- **Scope** (MY_LESSONS/COLLEGE): для teacher оставить оба, но MY_LESSONS = «до конца МОИХ пар»
  (через `TeacherWidgetData.snapshot()` номер последней пары), COLLEGE = «до последнего звонка».
  Либо упростить до COLLEGE (звонки общие). **Рекомендация:** оставить MY_LESSONS по умолчанию
  (преподавателю важно «до конца моих пар»), как в `CountdownMode.GROUP_LESSONS`.

### 6.3 «Менее замысловатый выбор темы — выпадающий список»
Сейчас тема = радио-список из 4 карточек 44dp (`SettingsScreen:1135-1180`) — заказчик считает это
«замысловатым». **Заменить на выпадающий список.** Готовый компонент в проекте ЕСТЬ:
- `androidx.compose.material3.ExposedDropdownMenuBox` + `ExposedDropdownMenu` + `DropdownMenuItem` —
  используется в `ui/screens/materials/MaterialDialogs.kt:196-215` (готовый образец с `trailingIcon`).
- Простой `DropdownMenu`+`DropdownMenuItem` — `ui/screens/StudentsScreens.kt:254-268`.

**План:** в teacher-настройках, блок «Уведомление до звонка»:
1. `SettingsSwitchRow`-клон — вкл/выкл строки (как `SettingsScreen:972`, `bell_notification_switch`).
2. **Стиль**: вместо 5 радио — `ExposedDropdownMenuBox` с 2 пунктами («В минутах» / «С секундами»).
3. **Тема**: вместо 4 радио-карточек — `ExposedDropdownMenuBox` с 4 пунктами (SYSTEM/BRAND/SKY/DARK),
   текст = `theme.title`. Никаких accent-кружков/превью — «чтобы не отвлекались».
4. Хранение — то же: `BellCountdownNotifier.setStyle/setTheme` (`:120,108`) в те же prefs.

**Файлы:** teacher-копия нотификатора (либо переиспользовать `BellCountdownNotifier` как есть — он
не flavor-гейчен, но `Scope.MY_LESSONS` зовёт `lastGroupLessonForToday(group)` — для teacher нужен
teacher-вариант). **Рекомендация:** НЕ форкать `BellCountdownNotifier`; добавить в него ветку «если
профиль учителя — last lesson из teacher_lessons» ИЛИ сделать тонкую teacher-обёртку. UI dropdown —
новый блок в `TeacherSettingsScreen`.

### 6.4 УДАЛИТЬ остальные teacher-уведомления (TR6)
`TeacherNotifier` (сводка/изменения/напоминание/override-reset) — **удалить целиком** (§9).

---

## 7. Виджеты — TR8 (4 штуки, адаптация оригинальных)

Оригинальные 4 виджета сейчас **вырезаны из teacher-манифеста** (`teacher/AndroidManifest.xml:49-53`,
`tools:node="remove"`) + `WidgetConfigActivity` тоже. Teacher имеет свои 2 (`TeacherNextWidgetProvider`,
`TeacherTodayWidgetProvider`). TR8 требует 4: «до звонка», «мои уроки», «график звонков», «сейчас и дальше».

**Общий принцип:** teacher-версии = **копия студенческой логики провайдера + ТОТ ЖЕ layout XML**, но
источник данных — `TeacherWidgetData.snapshot()` (`widget/TeacherWidgetData.kt:79`) вместо
`WidgetData.todayLessons(group)`. Layout XML **переиспользуем те же** (`res/layout/widget_*.xml`) — не
копируем, т.к. они group-независимы по разметке (меняются только `setTextViewText`).

| TR8 виджет | Оригинал-донор | Layout (переиспользуем) | Что берём 1-в-1 | Что заменяем на teacher-данные |
|---|---|---|---|---|
| **До звонка** | `BellCountdownWidgetProvider` (`widget/BellCountdownWidgetProvider.kt:230` buildViews) | `res/layout/widget_bell_countdown.xml` (3×1; id: `widget_countdown_root`, `tv_countdown_label`, `tv_countdown_value`, `iv_idle`) | Вся логика `countdownInfo/formatMinutes/formatSeconds/tomorrowFirstLessonLabel/nightFirstLessonLabel`, `CountdownMode`, `WidgetStyle` | `lastGroupLessonForToday(group)` (`:198`) → `lastTeacherLessonForToday()` из `snapshot().lessons.maxOf{lessonNumber}`. `CountdownMode.GROUP_LESSONS`→«до конца моих пар» |
| **Мои уроки** | `TodayScheduleWidgetProvider` (`widget/TodayScheduleWidgetProvider.kt:46`) | `res/layout/widget_today_schedule.xml` (5×3; `today_root`, `tv_today_header`, `today_rows`, `tv_today_empty`, `iv_idle`) + row `res/layout/widget_row_list.xml` (`row_number/row_time/row_subject/row_room/row_room2`) | `buildRow`/`buildMoreRow` структура, `MAX_ROWS`, `WidgetStyle`, `showIdle` | `WidgetData.todayLessons(group)` → `snapshot().lessons`; header `"СЕГОДНЯ • $group"`→`"МОЁ СЕГОДНЯ"`; `row_subject` = `subject • groups`; `row_room` = кабинет; **убрать** `RouteStartResolver`/`SubgroupSettings` (подгруппы) и `row_room2` ЗАМЕНА |
| **График звонков** | `BellsWidgetProvider` (`widget/BellsWidgetProvider.kt:11`) | `res/layout/widget_bells.xml` (2×4; `bells_root`, `tv_bells_header`, `bells_rows`) + row `res/layout/widget_row_bell.xml` | **1-в-1 ЦЕЛИКОМ** — использует только `CollegeBellSchedule.getBellsForDay(dow)` (группо-независим!) | Ничего (данные общие). Только тап `openAppIntent(...,"BELLS")` → teacher deep-link на вкладку «График» |
| **Сейчас и дальше** | `NowNextWidgetProvider` (`widget/NowNextWidgetProvider.kt:51`) | `res/layout/widget_now_next.xml` (4×2; `now_next_root`, `ll_now_block`, `tv_now_*`, next-блок, `iv_idle`) | Структура двух блоков (сейчас/дальше), `chooseRooms`→упростить, `WidgetStyle`, `detailsOf`, `minuteOfDay` | `todayLessons(group)` → `snapshot().lessons`; вместо `roomFirst/roomSecond` (подгруппы) — один `room` учителя; `teacherFirst`→`groups`. **Убрать** `isSplitStyle`/`SubgroupPref` |

**Регистрация (манифест):** в `teacher/AndroidManifest.xml` снять `tools:node="remove"` с нужных
провайдеров ИЛИ (чистее) объявить **teacher-специфичные классы** `TeacherBellCountdownWidgetProvider`
и т.д. (как уже сделано для Next/Today), каждый со своим `*_info.xml` и своим label. **Рекомендация:**
4 новых teacher-провайдера-обёртки, переиспользующие layout XML и `WidgetStyle`/`CountdownMode`, но
читающие `TeacherWidgetData`. Существующие `TeacherNext`/`TeacherToday` — оставить/переименовать под
«сейчас и дальше»/«мои уроки» (они уже teacher-данные используют).

**WidgetConfigActivity**: сейчас удалён из teacher. Для настройки стиля/секунд/режима — либо вернуть
(с teacher-данными), либо упростить: конфиг не обязателен (дефолт ALL_BELLS/секунды-выкл). **Рекомендация:**
вернуть `WidgetConfigActivity` в teacher-манифест (он уже group-независим по UI стиля), но убрать из
него выбор группы. `updatePeriodMillis=1800000` + `WidgetAlarm` минутный тик — уже общие
(`BellCountdownWidgetProvider.hasWork():446` УЧиТЫВАЕТ teacher-провайдеры `:455-456`) — оставляем.

**Размеры** (из `res/xml/*_info.xml`): до звонка 3×1 (180×40dp), мои уроки 5×3 (320×180dp), график
звонов 2×4 (110×180dp), сейчас и дальше 4×2 (250×110dp). Копируем `*_info.xml` как есть (меняем
`initialLayout`/`configure`/`previewImage` при необходимости).

---

## 8. Иконка — TR9 (точные координаты)

### 8.1 Текущий teacher-вектор (`teacher/res/drawable/ic_launcher_foreground_teacher.xml`, viewport 108×108)
- **Шапка (доска)**: `M54,33 L87,48.5 L54,64 L21,48.5 Z` → ширина **66** (x21→87), высота 31 (y33→64).
- **Тулья (чаша)**: `M39,57 L54,64 L69,57 L69,65 C69,74 62,79 54,79 C46,79 39,74 39,65 Z` → x39→69 (30), y57→79.
- **Кисточка**: линия `M85,50 L85,68` (strokeWidth 3) + круг `M85,68 m-3.5,0 a3.5,3.5...` (r3.5) → x85, y50→71.5.
- Полный glyph bbox: x21→87 (**66**), y33→79 (**46**). Центр ≈ (54, 54).

### 8.2 Оригинал (`main/res/mipmap-anydpi-v26/ic_launcher.xml` → `@mipmap/ic_launcher_foreground`, PNG)
- Измеренный ink-bbox PNG (432×432): **x73→357 = 284px**, что в 108-viewport = **284/432*108 ≈ 71** ширина.
  Высота та же ≈ 71 (y73→357). Т.е. оригинальный герб (здание, `ic_mpk_emblem.xml` viewport 108:
  x17→91=**74**, y20→87=**67**) заполняет ~68-71/108 ≈ **66% ширины**, центр (54, 53.5).
- **Safe-zone**: адаптивная иконка 108dp, гарантированно видимый внутренний круг **66dp** (радиус 33 от
  центра 54 → x21→87). Оригинальное здание (74 wide) чуть ВЫХОДИТ за 66-круг (x17→91), но это здание —
  углы обрезаются круглой маской незаметно (низкие ступени). **Сплошная** navy-шапка при 66 wide
  упирается ровно в границу круга → визуально «слишком большая/тяжёлая».

### 8.3 ДИАГНОЗ «шапка слишком большая»
У teacher сплошная доска шириной 66dp = **100% диаметра safe-круга (66dp)**, при этом ОНА ПЛОТНАЯ
(заливка `#001737`) → оптическая масса выше, чем у «воздушного» здания-оригинала той же ширины.
Плюс доска (66 wide × 31 tall) доминирует над тульей (30 wide) — нарушение внутренней пропорции.

### 8.4 ТОЧНЫЕ новые координаты (масштаб 0.80 от центра (54,54))
Уменьшаем весь glyph в 0.80×, центр оставляем (54,54). Результат: шапка **52** wide (было 66), поля
**28dp** с каждой стороны (больше, чем у оригинала 17dp) — намеренно, чтобы сплошная шапка читалась
того же «веса», что воздушное здание-оригинал. Новые пути (drop-in замена):
```xml
<!-- Шапка (доска): M54,37 L80,50 L54,62 L28,50 Z   (width 52, height 25, center 54,49.5) -->
<path android:fillColor="#001737"
    android:pathData="M54,37 L80,50 L54,62 L28,50 Z" />
<!-- Тулья (чаша): сжата и сдвинута под новую доску -->
<path android:fillColor="#001737"
    android:pathData="M42,56 L54,62 L66,56 L66,63 C66,70 60,74 54,74 C48,74 42,70 42,63 Z" />
<!-- Кисточка: линия -->
<path android:strokeColor="#001737" android:strokeWidth="3"
    android:strokeLineCap="round"
    android:pathData="M79,51 L79,65" />
<!-- Кисточка: шарик (r2.8) -->
<path android:fillColor="#001737"
    android:pathData="M79,65 m-2.8,0 a2.8,2.8 0 1,0 5.6,0 a2.8,2.8 0 1,0 -5.6,0" />
```
Новый bbox: x28→79 (**51**), y37→74 (**37**). Шапка/тулья пропорция = 52/24 ≈ 2.2 (было 66/30=2.2 —
сохранена форма доски, уменьшен абсолютный размер).

**Регулировочная ручка (калибровка):** если после рендера шапка покажется МЕЛКОЙ относительно
оригинала (заполнение 47% vs 68%) — поднять масштаб с 0.80 до **0.88–0.90** (пересчитать те же 4 точки:
`new = 54 + (old-54)*s`). Диапазон s: 0.80 (заметно меньше, много поля) → 0.90 (ближе к fill
оригинала). **Рекомендую начать с 0.80** (прямой ответ на «слишком большая»), при необходимости +0.05.

**Проверка:** (а) отрендерить `ic_launcher.xml` (anydpi-v26) в preview; (б) сравнить ink-bbox с
оригиналом скриптом (PIL, как в этом анализе): целевое заполнение ~50-55% ширины (между «слишком
большим» 61% текущего и «воздушным» 68% оригинала); (в) на круглой/квадратной маске шапка не касается
края (поля 28dp > 21dp запаса до границы круга). Обновить также legacy-PNG `teacher/res/mipmap-*/ic_launcher.png`
(сейчас ink-bbox 37→157 = 120 wide из 192) — перегенерировать из нового вектора, чтобы legacy и
adaptive совпали. `ic_launcher_round.xml` (`teacher/res/mipmap-anydpi-v26/`) ссылается на тот же
foreground — правится автоматически.

---

## 9. Что УДАЛИТЬ / что СОХРАНИТЬ в текущем teacher-коде

### 9.1 УДАЛИТЬ файл целиком
| Файл | Почему |
|---|---|
| `ui/teacher/TeacherWeekScreen.kt` | Экран «Неделя» собственной разработки (DECISIONS.md:50 — убрать). Заменяется вкладкой «График»=`BellsScreen` (§3.2) |
| `ui/teacher/TeacherWeekLogic.kt` | Логика «Неделя»/«Нагрузка» (`aggregateLoad`, `WeekLoad`). Нагрузка не в брифе. Юнит-тест `TeacherWeekLogicTest` — удалить вместе |
| `util/TeacherNotifier.kt` | Сводка/изменения/напоминание/override-reset пуш (TR6: удалить утреннюю сводку, изменения расписания, напоминание до пары). `TeacherReminderReceiver` внутри — тоже |
| `util/TeacherNotifierLogic.kt` | Чистая логика вышеперечисленных уведомлений (`summaryText`/`diffMessages`/`reminderCandidates`/`resetKey`). Тесты — удалить |

### 9.2 УДАЛИТЬ блоки (файл остаётся)
| Файл | Блок (строки) | Почему |
|---|---|---|
| `ui/teacher/TeacherHomeScreen.kt` | `TeacherOverrideSheet:527-619`, `OverrideSheetItem:621-646`, `floorRoomSuggestions:648-666` | override bottom sheet (TR4) |
| ″ | `editing` state `:104`, `editing?.let{...}:344-357` | вызов sheet |
| ″ | overrideResets-плашка `:218-240` | пуш/плашка о сбросе (TR4) |
| ″ | notif-prompt card «Уведомлять об изменениях?» `:242-275` | «изменения расписания» удалены (TR6) |
| ″ | в `TeacherLessonCard:399`: StatusLabel ЗАМЕНА `:410-413`, кнопка Flag `:463-472`, «✎ ваша правка» `:481-487`, кнопка «Править» `:488-499`, кабинет 30sp `:461` | TR3/TR4: нет Править/⚑/✎/ЗАМЕНА; кабинет — чип как в оригинале (§2.3). **Фактически `TeacherLessonCard` заменить на копию студенческой `LessonCard` (§2)** |
| ″ | `TeacherDaySlotCell:361-395` (со своим weekday-subtitle `slotSubtitle:520`) | заменить на `DaySlotCell` из components (§1), subtitle `dd.MM` без дня недели |
| `ui/teacher/TeacherNav.kt` | `markOverridesPlate:100,107-110,199-221` | override (TR4) |
| ″ | `TeacherSubPage.OemHelp:83`, его вызов `:189,245` | OEM-справка — «своё», не в брифе (удаляю; вернуть при подтверждении) |
| ″ | `TeacherTab.WEEK` → переименовать в `BELLS`, контент `TeacherWeekScreen` → `BellsScreen` | §3.1 |
| `ui/teacher/TeacherMoreScreen.kt` | `NotifSection:181-232`, `ToggleRow:234-263` | сводка/изменения/напоминание (TR6) |
| ″ | строка «Не нашли себя или заметили неточность?» `:105-111` | TR6 удалить |
| ″ | `TeacherBellsPage:310-362`, `TeacherOemHelpPage:378-433`, `OemCard:435` | bells→вкладка `BellsScreen`; OEM→удалить |
| ″ | весь `TeacherMoreScreen` → **переписать** как `OtherMenu`-клон (§3.3): Профиль/Карта/Найти группу/Преподаватели/Настройки | TR5 |
| `ui/teacher/TeacherOnboardingScreen.kt` | «Не нашли себя? Напишите нам» `:232-249` | TR6 (тот же пункт) |
| `ui/teacher/TeacherDayViewModel.kt` | `saveOverride:83-108`, `cancelOverride:110-117`, `dismissOverrideResets:119`, `recordResets:128-131`, `overrideResets` в state `:36` | override (TR4) |
| `data/repository/TeacherDayLoader.kt` | `OverrideReset/OverrideResetReason:98-108`, `TeacherDayLogic.applyOverrides:146-162`, `diffOverrideResets` (см. `:216`), `overridesToPrune:168`, `syncDayWithOverrideDiff:397-432` (→ заменить на простой `syncTeacherScheduleForDate`), override-чтение в `loadDay:375-380` | override (TR4). `TeacherDayLesson.overridden/isReplacement:83-85` — поля удалить |
| `util/BellCountdownNotifier.kt` (если форкаем под teacher) | `Style.WITH_APP_NAME/WITH_LESSON/WITH_PROGRESS` (`:69-71`), их рендер (`:331-340`) | TR7: только минуты/секунды |
| `widget/TeacherWidgetData.kt` / `TeacherWidgetLogic.kt` | `applyOverrides` (`TeacherWidgetLogic:56`), `overridesHash:112`, `TeacherWidgetLesson.replaced:20`, чтение `teacherOverrideDao` | override (TR4) |
| `teacher/AndroidManifest.xml` | `<receiver TeacherReminderReceiver>:13-19` | напоминание удалено |

### 9.3 СОХРАНИТЬ (нужно, работает)
| Файл/блок | Почему оставляем |
|---|---|
| `worker/TeacherScheduleWorker.kt` + `shouldTeacherSync` | Окна синка (DECISIONS.md:54 — СОХРАНЕНО). **Правка:** убрать вызовы `syncDayWithOverrideDiff`→простой sync, `TeacherNotifier.snapshotRaw/onSyncCompleted` (строки `:52,64-65,86-95`) — иначе не скомпилируется после удаления TeacherNotifier |
| `worker/MpkWorkManagerHelper.setupTeacherSync` | Планирование воркера |
| `data/repository/TeacherDayLoader.kt`: `loadDay:344`, `toLesson:293`, `readMatchedRows`, `TeacherDayLogic.statuses:123`, `teacherTargetCalendar:21`, `defaultTeacherDayOffset:30`, `findNextLesson:450`, `readLastSyncEpoch/markSyncedNow` | Ядро загрузки дня. Убрать только override-обвязку (§9.2) |
| `ui/teacher/TeacherProfileStore.kt` + `TeacherProfile(name,surnameKey)` | Профиль (СОХРАНЕНО). **surnameKey-фикс** — общий, чинит и ученическое (DECISIONS.md:54) |
| `ui/teacher/TeacherOnboardingScreen.kt` + `TeacherOnboardingLogic.kt` | Онбординг «найди себя» (СОХРАНЕНО, усилить fuzzy — TR1) |
| `util/FuzzySearch.kt` | Общий нечёткий поиск (усилить, не удалять) |
| `widget/TeacherNextWidgetProvider.kt`, `TeacherTodayWidgetProvider.kt`, `TeacherWidgetData.kt`, `TeacherWidgetLogic.kt` | Виджет-логика (СОХРАНЕНО). Убрать override-поля, добавить 2 новых виджета (§7) |
| `data/local/entity/TeacherLessonEntity.kt` + `TeacherLessonDao.kt` | Источник данных. **НЕ трогаем** (Room) |
| `ui/teacher/TeacherGroupLessonsScreen.kt` | «Мои пары у группы» — цель клика по чипу группы (§2.2). Оставить |
| `ui/teacher/TeacherRoot.kt` | Оболочка. Правка: `TeacherTheme`→`MyApplicationTheme`+`LocalDensity` (§4, §5) |
| `ui/teacher/TeacherDayViewModel.kt` (ядро) | `load/refresh/setProfile/selectDay` — оставить |

### 9.4 Оставить DORMANT (нельзя удалить без миграции)
**Таблица `teacher_overrides` в Room v10** (`data/local/MpkDatabase.kt:40,42`, `version=10`;
`TeacherOverrideEntity`, `TeacherOverrideDao`, `MIGRATION_9_10` `:255`).

**Проблема:** удалить `TeacherOverrideEntity` из `@Database(entities=[...])` = изменить схему = нужна
`MIGRATION_10_11` (DROP TABLE) + `MigrationV10ToV11Test` (инвариант CLAUDE.md: «Room v9+: любое
изменение схемы = явная Migration + тест»). `fallbackToDestructiveMigration` тихо сотрёт ВСЕ данные
ученика (расписание/оценки/дз) — НЕДОПУСТИМО.

**РЕШЕНИЕ — оставить dormant (спящей):**
1. `TeacherOverrideEntity`, `TeacherOverrideDao`, таблица в схеме, `MIGRATION_9_10` — **ОСТАВИТЬ как есть**.
2. **Удалить только CALLERS** (§9.2): UI-sheet, `applyOverrides`, `diffOverrideResets`, `saveOverride`/
   `cancelOverride`, `TeacherWidgetLogic.applyOverrides`, чтение `overrideDao.forDate` в `loadDay`.
3. Таблица остаётся в БД, но **пустая и не читается** (новый код её не трогает). У существующих
   пользователей v2.6-teacher в ней могут быть строки — они просто игнорируются (безопасно: «лучше
   промолчать, чем соврать»).
4. Схема НЕ меняется → `version=10` остаётся → **миграция v11 НЕ нужна** → риск `fallbackToDestructive`
   = ноль.

**Обоснование риска:** dormant-таблица стоит ~0 (несколько пустых строк у ранних пользователей). Реальный
DROP TABLE отложить на будущую волну, когда будет другой повод для v11 (не гонять миграцию ради мусора).
`TeacherOverrideDao`/`Entity` — удалить из *кода* можно будет ТОЛЬКО вместе с `MIGRATION_10_11`. Пока —
dormant. **Гейт:** `MigrationV9ToV10Test` остаётся зелёным (схему не трогаем); добавить проверку, что
`loadDay` больше не зовёт `overrideDao` (юнит-тест на «чистый» день).

---

## 10. Открытые вопросы / двусмысленности брифа (решаем сами, документируем)

**п.1 — «График» (вкладка 2): что именно?** (§3.2)
Двусмысленность: (а) `BellsScreen` (таймлайн звонков) vs (б) недельный график пар преподавателя.
**РЕШЕНИЕ консилиума: (а) `BellsScreen` 1-в-1.** Основание: единственный оригинальный экран-«график»;
недельная сетка = собственная разработка, отменённая DECISIONS.md:50; «копия оригинала» (TR2) → берём
готовое. **Следствие:** строку «Расписание звонков» из меню «Ещё» убираем (иначе дубль). Риск: если
заказчик имел в виду (б) — переделать вкладку на недельную сетку из оригинальных компонентов (дороже).
**Помечено для подтверждения.**

**п.2 — «число листа» (неразобранная фраза заказчика).**
В коде/планах фраза не найдена (grep по MASTER-PLAN, _reports, docs — 0 точных совпадений). Наиболее
вероятные прочтения: (а) «число» = **день месяца на кнопках Сегодня/Завтра** (subtitle `dd.MM`) —
фраза-обрывок про то, что на кнопках должно быть только число (дата), БЕЗ дня недели → **совпадает с
TR3 «БЕЗ надписей дней недели»**; (б) «лист» = страница/экран (опечатка «число листа» = «число и месяц»?).
**РЕШЕНИЕ:** трактуем как (а) — на кнопках Сегодня/Завтра оставляем оригинальный subtitle `dd.MM`
(`ScheduleScreen.kt:576-581`) и **убираем** день недели, который сейчас добавляет teacher `slotSubtitle`
(`TeacherHomeScreen.kt:520-523` = `"dd.MM, пн"`). Это дословно выполняет TR3. **Помечено: уточнить у заказчика.**

**п.3 — «Найти группу» в «Ещё» (TR5): какой экран?**
**РЕШЕНИЕ:** `GroupScheduleScreen` (`ui/screens/GroupScheduleScreen.kt:204`) — «расписание любой группы
колледжа», в оригинале вызывается из «Другое»→«Расписание группы» (`CollegeScreen.kt:266-273`). Берём
1-в-1 (он в `main`, не flavor-гейчен). Подпись строки — «Найти группу» (как в брифе) вместо «Расписание группы».

**п.4 — TR1 «fuzzy терпит ДАЖЕ ГРУБЫЕ ошибки» — насколько грубые?**
Текущий `FuzzySearch`: `MAX_DISTANCE=2` (Дamerau-Левенштейн), `MIN_FUZZY_WORD_LENGTH=4`
(`util/FuzzySearch.kt:7-9`) — терпит ≤2 опечаток в слове ≥4 букв. «Грубые ошибки» (3+) сейчас НЕ ловит.
**РЕШЕНИЕ:** для teacher-онбординга **увеличить допуск**: `MAX_DISTANCE=3` ИЛИ добавить отдельный
«широкий» режим (`score` с `MAX_DISTANCE=3`) ТОЛЬКО в `TeacherOnboardingLogic.filterTeachers`
(не трогая глобальный `FuzzySearch`, чтобы не сломать ученический поиск/`FuzzySearchTest`). Также:
показывать **ВСЕХ** подходящих (не топ-N) — сейчас `filterTeachers` уже возвращает всех с `score>0`
(`TeacherOnboardingLogic.kt:15-21`), сортировка по score — оставить. **Порог:** считать «грубой ошибкой»
≤3 edits; при >3 — «ничего не найдено» + подсказка. Тест: `filterTeachers("Иванвоа")` → находит «Иванова» (2 перестановки+опечатка).

**п.5 — OEM-справка («как запретить системе убивать приложение»)** (`TeacherMoreScreen.kt:378`).
В брифе TR5 состав «Ещё» = звонки/карта/группа/преподаватели/настройки. OEM-справки НЕТ. Но она решает
реальную проблему (Xiaomi/Samsung убивают фоновый синк → расписание не обновляется). **РЕШЕНИЕ: удалить
из «Ещё»** (строгое следование брифу), НО оставить минимальную подсказку в настройках («если обновления
не приходят — разрешите автозапуск») одной строкой, без отдельного экрана. **Помечено: подтвердить** —
если заказчик увидит «расписание не обновляется», OEM-справку вернуть.

**п.6 — Профиль/смена преподавателя: в «Ещё» или в «Настройках»?**
TR6 говорит «Настройки: сменить преподавателя». TR5 состав «Ещё» — без явного «профиль». **РЕШЕНИЕ:**
строка «Профиль: <ФИО> — сменить» в «Ещё» (быстрый доступ, как «Моя специальность» в оригинале была
якорем личности) И дубль «Сменить преподавателя» внутри `TeacherSettingsScreen` (как в оригинале
«СМЕНИТЬ» группу в шапке настроек, `SettingsScreen.kt:289-313`). Оба ведут в один `onPickProfile`
(сброс профиля → онбординг-поиск). Не конфликтует.

**п.7 — Уведомление «до звонка»: звук/«тема звонка».**
TR7: «выбор темы уведомления и звонка — менее замысловатый». В коде «звонка» как звука НЕТ (§6.1:
`setSound(null)`, `R.raw` отсутствует). **«Тема звонка» = цветовая тема уведомления** (`BellCountdownNotifier.Theme`),
а «звонок» = сами bell-уведомления. **РЕШЕНИЕ:** трактуем «тема уведомления И звонка» = ОДИН dropdown
цветовой темы (SYSTEM/BRAND/SKY/DARK) + dropdown стиля (минуты/секунды). Никакого выбора звука не
добавляем (его нет и бриф «менее замысловатый» = убрать, не добавить). **Помечено: если заказчик хотел
выбор рингтона — это новая фича, не копия; уточнить.**

---

## Сводка: главные решения карты
1. **UI = вызов студенческих composable из `main`** (они не flavor-гейчены). Единственная flavor-точка — `MainActivity:74`.
2. **`LessonCard` (`ScheduleScreen.kt:866`)** — копируем, `TeacherChip`→`GroupChip` (данные `TeacherDayLesson.groups`), кнопка кабинета = оригинальный чип «каб. N» +4dp/+2sp, клик → `CollegeMapScreen` (как в оригинале). Нет Править/⚑/✎/ЗАМЕНА.
3. **Сегодня/Завтра** — `DaySlotCell` из components 1-в-1, subtitle `dd.MM` без дня недели; календарь — `CalendarArchiveDialog`+`MonthGrid` 1-в-1.
4. **Вкладки**: Расписание (новый `TeacherScheduleScreen`) / График = **`BellsScreen` 1-в-1** / Ещё = клон `OtherMenu` (Профиль→Карта→Найти группу→Преподаватели→Настройки).
5. **«Преподаватели» без «ведёт у вас»** — параметр `showLeads=false` в `TeachersPage`/`TeacherCard`/`TeacherCardPage` (вырез в 3 точках: `:744,838,1164`), без форка.
6. **Масштаб 75/100/125%** — uniform `LocalDensity` override (`Density(base.density*k, base.fontScale)`), 1 строка + `UiScaleStore` (клон `AppThemeStore`). Пересечений нет по построению. Проверка roborazzi×3.
7. **Тема** — механизм УЖЕ работает в teacher (`MainActivity:33`+`paletteState`); добавить только swatches-UI (копия `SettingsScreen:610-668`) ВНИЗ настроек. Те же 7 палитр.
8. **Уведомление «до звонка»** — оставить COMPACT+WITH_SECONDS, выкинуть WITH_APP_NAME/WITH_LESSON/WITH_PROGRESS; выбор темы+стиля = `ExposedDropdownMenuBox` (готов в `MaterialDialogs.kt:196`).
9. **Виджеты (4)** — переиспользуем те же layout XML (`widget_bell_countdown/today_schedule/bells/now_next`) + `WidgetStyle`; данные → `TeacherWidgetData.snapshot()`. «График звонков» = `BellsWidgetProvider` вообще 1-в-1 (группо-независим).
10. **Удалить**: `TeacherWeekScreen/Logic`, `TeacherNotifier(+Logic)`, override-механику (sheet/diff/пуш/«Править»/⚑), «Не нашли себя», OEM-экран, notif-секцию (сводка/изменения/напоминание). **Сохранить**: воркер, лоадер, профиль+surnameKey, онбординг+fuzzy, виджет-логику.
11. **`teacher_overrides` (Room v10)** — оставить DORMANT (таблица+DAO+Entity в схеме, удалить только callers). Миграция v11 НЕ нужна → риск `fallbackToDestructive` = 0.
12. **Иконка** — масштаб 0.80 от центра (54,54): шапка 66→52dp (точные пути в §8.4), поля 28dp. Ручка калибровки 0.80→0.90.
