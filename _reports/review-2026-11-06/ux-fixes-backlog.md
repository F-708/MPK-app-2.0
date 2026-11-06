# UX/a11y — патч-готовый бэклог

Основание: `ux-a11y.md` (ревью от 2026-11-06). Здесь только P0/P1 с готовыми фрагментами кода.
Приоритеты: **P0** — действие недоступно части пользователей; **P1** — серьёзный барьер/ошибка.

Актуальность якорей: 2026-11-06, 17:5x. **Файлы в дереве двигаются параллельно** (в рабочей копии
уже есть чужие правки: `TasksScreen` — long-press удаления, `AppTheme`/`Color` — navSelected,
`MaterialBaseScreen`, `ScheduleScreen`, `MaterialFormats.kt` — новый файл). Поэтому все патчи
привязаны к **уникальным строкам-якорям**, а номера строк — справочные; перед применением `grep`.

---

## 0. Статус: что уже в дереве

| ID | Тема | Статус | Кто |
|---|---|---|---|
| FIX-1 | Токены тач-таргетов 44→48dp | ✅ **сделано** (Tokens.kt) | rev-ux-a11y |
| FIX-2 | ripple + role/onClickLabel + min-48dp в `bouncyClickable` | ✅ **сделано** (BouncyClick.kt) | rev-ux-a11y |
| FIX-3 | Удаление задания без свайпа | 🟡 **наполовину**: long-press + диалог уже есть в `TasksScreen` (кто-то из агентов); не хватает семантики, ripple, custom action | — |
| FIX-4 | `navSelected = brand` (контраст навигации) | ✅ **сделано** (`AppTheme.kt:184`, `Color.kt:170`) | FIX-4 (другой агент) |
| FIX-5 | Вес предмета: семантика `ProgressBarRangeInfo` | ⬜ открыт (P0) | — |
| FIX-6 | Удаление оценки доступным способом | ⬜ открыт (P0) | — |
| FIX-7 | Контраст бейджей типов заданий | ⬜ открыт (P1) | — |
| FIX-8 | Тач-таргеты в компактном расписании | ⬜ открыт (P1) | — |
| FIX-9 | Технические тексты ошибок (4 текста / 5 мест) | ⬜ открыт (P1) | — |
| FIX-10 | Чекбокс «выполнено» — роль и состояние | ⬜ открыт (P1, дешёвый) | — |

### Блокер сборки, не мой (сообщено Лиду)

Сейчас `:app:compileStandardDebugKotlin` падает **не на моих файлах**:

```
e: …/data/repository/ScheduleRepository.kt:260:5 'public' function exposes its 'internal' parameter type 'FetchStamps'.
e: …/util/MaterialFormats.kt:65:85 Syntax error: Expecting member declaration. (и далее 65:86..65:106)
```

Причина второго — `*/` внутри KDoc: `` /** ... (без `*/*` и octet-stream). */ `` — комментарий
закрывается на `*/*`, хвост становится мусором. Фикс: убрать обратные кавычки вокруг `*/*`
(например: `без '*' '/' и octet-stream`). Файл не мой — не правил.
В моих файлах (`Tokens.kt`, `BouncyClick.kt`) компилятор ошибок не выдал: семантическая проверка
модуля отработала (`ScheduleRepository` — это ошибка чекера, а не парсера).

---

## 1. Порядок применения

| Волна | Состав | Почему так |
|---|---|---|
| A (сделано) | FIX-1, FIX-2, FIX-4 | Токены и клик-модификатор — фундамент: от них зависят остальные правки. navSelected — отдельный агент. |
| B (P0) | FIX-5, FIX-6, добить FIX-3 | Это «нельзя выполнить действие», а не «неудобно». Требуют TalkBack-прогона. |
| C (P1) | FIX-7, FIX-8, FIX-9, FIX-10 | Локальные правки, каждая независима. |
| D (P2) | настройки, терминология, back-поведение, шапка с группой | См. §5 — без диффов, нужны решения по продукту. |

---

## 2. FIX-5 (P0) — Вес предмета: доступный слайдер

**Файл:** `app/src/main/java/com/politex/ui/screens/GradesScreen.kt` · **якорь:** `private fun WeightSlider(`

**Проблема:** значение меняется только через `pointerInput` на `Canvas`; в семантике узла нет ни роли,
ни `progressBarRangeInfo`, ни `setProgress` → TalkBack выставляет вес предмета **никак**, а от веса
зависит средний балл.

**Было** (сокращённо):

```kotlin
Canvas(
    modifier =
    modifier
        .height(32.dp)
        .pointerInput(enabled) { … detectTapGestures { onValueChange(snap(it.x, size.width.toFloat())) } }
        .pointerInput(enabled) { … detectHorizontalDragGestures { … } },
) { /* отрисовка */ }
```

**Стало:**

```kotlin
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.roundToInt

@Composable
private fun WeightSlider(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange = rememberUpdatedState(onValueChange)
    val snap: (Float, Float) -> Int = { x, w ->
        GradeAggregator.snapWeight((x / w).coerceIn(0f, 1f))
    }
    Canvas(
        modifier =
        modifier
            .height(Tokens.HitTarget) // было 32.dp: 32dp — не площадь для пальца
            .semantics {
                contentDescription = "Вес в процентах"
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        current = value / 100f,
                        range = 0f..1f,
                        steps = 0,
                    )
                if (enabled) {
                    setProgress { target ->
                        onChange.value((target * 100f).roundToInt().let(GradeAggregator::snapWeight))
                        true
                    }
                }
            }
            .pointerInput(enabled) { /* без изменений */ }
            .pointerInput(enabled) { /* без изменений */ },
    ) { /* отрисовка без изменений */ }
}
```

Проверить сигнатуру `GradeAggregator.snapWeight` — если она принимает `Float`, заменить
`(target * 100).roundToInt().let(GradeAggregator::snapWeight)` на
`GradeAggregator.snapWeight(target)` (передав долю 0..1) и оставить `value = snap(...)`.

**Риск:** низкий. Визуал не меняется, высота Canvas растёт с 32 до 48dp — в панели настроек предмета
это одна строка, места хватает. `setProgress` без `progressBarRangeInfo` — молча игнорируется,
поэтому оба шага обязательны.

**Проверка:**
1. Compose-тест (`app/src/test/java/com/politex/WeightSliderA11yTest.kt`):

```kotlin
@Test
fun `weight slider reports progress and is adjustable`() {
    composeRule.setContent { WeightSlider(value = 40, enabled = true, onValueChange = { changed = it }) }
    composeRule.onNodeWithContentDescription("Вес в процентах")
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
    assertEquals(50, changed)
}
```
2. TalkBack: фокус на слайдере → «Вес в процентах, 40 процентов, ползунок»; свайп вверх/вниз меняет значение.

---

## 3. FIX-6 (P0) — Удаление оценки доступным способом

**Файл:** `app/src/main/java/com/politex/ui/screens/GradesScreen.kt`
**Якоря:** `var armedId by remember` (машина состояний, ~`:465`) и `.testTag("grades_recent_${e.id}")` (чип, ~`:632`).

**Проблема:** удаление = второе нажатие в окне 500 мс (`DELETE_ARM_TIMEOUT_MS`). TalkBack физически не
успевает (навигация между фокусами + пауза), тремор — тоже. «Взведено» показывается **только цветом**
(заливка `ColorDangerFill`).

**Минимальный патч (визуал не меняется, кроме размера 44→48dp):**

```kotlin
// 1) В GradeSubjectScreen рядом с onChipTap объявить прямое удаление:
val deleteEntry: (GradeEntryEntity) -> Unit = { e ->
    armedId = null
    deleteGrade(e)
}

// 2) Чип оценки:
Box(
    modifier =
    Modifier
        .size(Tokens.HitTarget) // было 44.dp
        .testTag("grades_recent_${e.id}")
        .semantics {
            if (armed) stateDescription = "Готово к удалению. Нажмите ещё раз."
            customActions =
                listOf(
                    CustomAccessibilityAction("Удалить оценку") { deleteEntry(e); true },
                )
        }
        .bouncyClickable(
            onClickLabel = "Удалить оценку",
        ) { onChipTap(e) },
    contentAlignment = Alignment.Center,
) { /* Surface без изменений */ }
```

Импорты: `androidx.compose.ui.semantics.CustomAccessibilityAction`,
`…semantics.customActions`, `…stateDescription`, `…contentDescription`.

**Опционально (рекомендуется, но меняет вид):** в armed-состоянии показывать не только красную заливку,
но и `Icon(Icons.Default.Delete, size 12.dp)` внутри чипа — тогда состояние читается не только цветом
(WCAG 1.4.1 Use of Color).

**Риск:** низкий. `customActions` не влияет на обычные тапы. Существующее undo (`grades_undo_row`)
остаётся страховкой.

**Проверка:**
- TalkBack: чип → «Действия» → «Удалить оценку» удаляет за одно действие, появляется строка «Удалена
  оценка «4» / ОТМЕНИТЬ».
- Compose-тест: `onNodeWithTag("grades_recent_<id>").performSemanticsAction(SemanticsActions.CustomActions)`.
- Юнит-тест `DeleteArmState` (`gradesDeleteAction`) не меняется — таймер остаётся для обычных пользователей.

---

## 4. FIX-3 (P0, добить) — Удаление задания без свайпа

**Файл:** `app/src/main/java/com/politex/ui/screens/TasksScreen.kt`
**Уже есть:** `TaskItemCard(… onLongPressDelete)` через `combinedClickable`, диалог «Удалить задание?»
и undo-снэкбар. **Значит основной барьер снят** — остались три шероховатости.

### 4.1 `indication = null` у карточки задания + подписи действий

**Якорь:** `onClick = onEdit,` внутри `.combinedClickable(` (`~:543-548`).

```kotlin
.combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = LocalIndication.current,          // было null: карточка не отзывалась
    role = Role.Button,
    onClickLabel = "Открыть задание",
    onLongClickLabel = "Удалить задание",
    onClick = onEdit,
    onLongClick = onLongPressDelete,
)
```

Импорты: `androidx.compose.foundation.LocalIndication`, `androidx.compose.ui.semantics.Role`.

### 4.2 Скринридер: действие удаления у свайп-контейнера

**Якорь:** в `SwipeToDeleteBox` — `Box(modifier = modifier.fillMaxWidth()` (`~:756-759`).

```kotlin
Box(
    modifier =
    modifier
        .fillMaxWidth()
        .semantics {
            customActions =
                listOf(CustomAccessibilityAction("Удалить задание") { onDelete(); true })
        }
        .then(if (deleting) Modifier.height(startHeight * collapse.value) else Modifier)
        …
```

Важно: `onDelete` здесь удаляет сразу (с undo) — для скринридера это допустимо, потому что undo
доступен в снэкбаре, а диалог остаётся для long-press.

### 4.3 Декоративная иконка корзины

**Якорь:** `contentDescription = "Удалить",` (`~:805`).

```kotlin
Icon(
    imageVector = Icons.Default.Delete,
    contentDescription = null,   // подложка не кликабельна: описание только путало TalkBack
    …
)
```

**Риск:** низкий. Долгое нажатие уже работает — добавляем только семантику и ripple.
**Проверка:** TalkBack на карточке → «Действия → Удалить задание»; обычный тап → «долгое нажатие» →
диалог; снэкбар «ОТМЕНИТЬ» восстанавливает задание.

---

## 5. FIX-4 (P1) — Контраст активной вкладки навигации ✅ уже в дереве

**Файлы:** `ui/theme/AppTheme.kt` (`navSelected = brand`), `ui/theme/Color.kt` (тёмный COLLEGE
`navSelected = Color(0xFF6FA3D8)`).

**Делает FIX-4 (другой агент). Не дублировать.** Проверить только числа: для всех 8 тем
`ratio(darkPalette.navSelected, darkPalette.bgMain) ≥ 4.5` (замеры: 5.66…7.36).

```kotlin
// app/src/test/java/com/politex/PaletteContrastTest.kt
class PaletteContrastTest {
    private fun lum(c: Color): Double {
        fun ch(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun ratio(fg: Color, bg: Color): Double {
        val a = lum(fg); val b = lum(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    @Test fun `nav selected passes AA in every dark theme`() {
        AppTheme.entries.forEach { t ->
            val p = t.darkPalette
            assertTrue("${t.id}: ${ratio(p.navSelected, p.bgMain)}", ratio(p.navSelected, p.bgMain) >= 4.5)
        }
    }
}
```

---

## 6. FIX-7 (P1) — Контраст бейджей типов заданий

**Файл:** `ui/theme/Color.kt`, светлая палитра (`~:95-100`).
Причина: 10sp Bold + оттенок на 12 %-подложке → 2.48–4.13:1 (AA нужен 4.5).

```kotlin
// было → стало (проверено на подложке badgeColor.copy(alpha = 0.12f) поверх #FFFFFF)
badgeHW = Color(0xFF0369A1),      // было 0xFF0072CE → 4.98:1
badgeLab = Color(0xFF6D28D9),     // было 0xFF8B5CF6 → 5.82:1
badgePract = Color(0xFF0F766E),   // было 0xFF0D9488 → 4.64:1
badgeExam = Color(0xFFBE123C),    // было 0xFFE11D48 → 5.12:1
badgeCourse = Color(0xFF9A3412),  // было 0xFFF97316 → 2.48:1 (0xFFC2410C даёт лишь 4.35 — мало)
// badgeDiploma не трогаем (9.88:1), тёмная палитра проходит вся (5.75–7.99)
```

Дополнительно в `TasksScreen` бейдж: `fontSize = 10.sp` → `11.sp`, `maxLines = 1` → `2`.
**Риск:** средний — это смена визуального тона бейджей в 8 темах (светлая палитра общая для всех тем).
Согласовать скриншотом до/после.
**Проверка:** расширить `PaletteContrastTest`:

```kotlin
@Test fun `light badges pass AA on tint`() {
    val p = LightPalette
    listOf(p.badgeHW, p.badgeLab, p.badgePract, p.badgeExam, p.badgeCourse).forEach { fg ->
        val bg = blend(fg, 0.12f, p.bgMain)   // blend — смешать в sRGB
        assertTrue(ratio(fg, bg) >= 4.5)
    }
}
```

---

## 7. FIX-8 (P1) — Тач-таргеты в компактном расписании

**Файл:** `ui/screens/ScheduleScreen.kt` · **якоря:** три одинаковые строки
`.then(if (compact) Modifier else Modifier.heightIn(min = 44.dp))` (`~:1449`, `~:1514`, `~:1713`).

```kotlin
// было: в компакте кликабельная строка «преподаватель · кабинет» ~20dp высотой
.then(if (compact) Modifier else Modifier.heightIn(min = 44.dp))

// стало: 48dp в обычном режиме, 40dp в компакте (ниже 48 только чтобы не раздувать плотный список)
.heightIn(min = if (compact) 40.dp else Tokens.HitTarget)
```

**Почему не 48 в компакте:** компакт включают, когда нужен плотный список; строка 20dp → 48dp это ×2.4
и половина экрана уйдёт на скролл. 40dp проходит WCAG 2.5.8 (≥24dp) и заметно лучше текущего.
**Риск:** средний (меняется высота карточек в компакте, они станут чуть выше). Проверить скриншотом
`shot_schedule_compact.png` (Roborazzi record) до/после.
**Проверка:** Compose-тест: узел строки преподавателя `assertHeightIsAtLeast(40.dp)` при `compact = true`.

---

## 8. FIX-9 (P1) — Человеческие тексты ошибок

**4 текста, 5 мест.** Технические детали (`it.message`, `javaClass.simpleName`) убрать из UI — в лог.

```kotlin
// ui/screens/MaterialBaseScreen.kt ~:338
// было:
showErr("Не удалось импортировать: ${it.message ?: it.javaClass.simpleName}")
// стало:
showErr("Не удалось добавить материал. Файл повреждён или защищён — попробуйте другой файл или сохраните его как .docx")

// ui/screens/MaterialBaseScreen.kt ~:353
// было:
}.onFailure { showErr("Не удалось удалить: ${it.message ?: it.javaClass.simpleName}") }
// стало:
}.onFailure { showErr("Не удалось удалить материал. Проверьте свободное место и попробуйте ещё раз") }

// ui/screens/SettingsScreen.kt ~:238
// было:
"Не удалось сохранить: ${e.message}"
// стало:
"Не удалось сохранить копию. Проверьте свободное место на телефоне и повторите"

// ui/screens/SettingsScreen.kt ~:277 и ~:283
// было:
"Не удалось прочитать файл: ${it.message}"   /   "Не удалось прочитать файл: ${e.message}"
// стало (оба места):
"Это не файл копии. Выберите JSON, сохранённый через «Данные → Сохранить копию»"
```

**Риск:** нулевой (строки). `SettingsScreen` и `MaterialBaseScreen` заняты другими агентами —
согласовать порядок, чтобы не конфликтовать.
**Проверка:** `grep -n "javaClass.simpleName\|\${e.message}\|\${it.message}" app/src/main/java/com/politex/ui/` → пусто.

---

## 9. FIX-10 (P1, дешёвый) — Чекбокс «выполнено»: роль вместо описания состояния

**Файл:** `ui/screens/TasksScreen.kt` · **якорь:** `.testTag("task_check_${task.id}")` (`~:558-571`).

```kotlin
// было: Box + bouncyClickable, состояние отдавалось через contentDescription иконки
Box(
    modifier =
    Modifier
        .size(Tokens.IconHit)
        .toggleable(
            value = task.isCompleted,
            role = Role.Checkbox,
            onValueChange = { onToggleCompleted() },
        )
        .testTag("task_check_${task.id}"),
    contentAlignment = Alignment.Center,
) {
    Icon(
        imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = null,               // состояние теперь озвучивает toggleable
        tint = if (task.isCompleted) ColorBrandBlue else ColorTextMuted,
    )
}
```

Импорты: `androidx.compose.foundation.selection.toggleable`, `androidx.compose.ui.semantics.Role`.
**Риск:** низкий (визуал тот же; TalkBack начнёт говорить «флажок, не выполнено»).
**Проверка:** Compose-тест `assertIsToggleable()` / `assertIsOff()`; TalkBack проговаривает состояние.

---

## 10. Проверочный чек-лист (после каждой волны)

1. `sh ./gradlew :app:compileStandardDebugKotlin` — зелёный (**до** волны убедиться, что дерево не
   сломано чужими правками: сейчас падает на `MaterialFormats.kt:65` и `ScheduleRepository.kt:260`).
2. `sh ./gradlew :app:testStandardDebugUnitTest` — 784+ теста (новые: `PaletteContrastTest`,
   `WeightSliderA11yTest`).
3. **Контраст:** `PaletteContrastTest` — 8 тем × 2 режима, пары navSelected/bgMain, badge/tint.
4. **TalkBack-приёмка (ручная, 4 потока):**
   - расписание → тап по преподавателю → карточка → назад (все действия доступны);
   - задания → карточка → «Действия → Удалить задание» → ОТМЕНИТЬ;
   - оценки → оценка → «Действия → Удалить оценку» → ОТМЕНИТЬ; настройки предмета → вес (свайп вверх/вниз);
   - настройки → звонки → раскрыть панель → стиль/тема/область (все переключатели озвучивают состояние).
5. **Roborazzi:** `-Proborazzi.test.record=true` для `promo/shots` (compact + dark) — сравнить до/после;
   отдельно проверить кадры: бейджи заданий (FIX-7), компакт-карточки (FIX-8), высота слайдера (FIX-5).
6. **Регресс вёрстки после FIX-1 (48dp):** плотные ряды — `AnnotationOverlay` (тулбар в `horizontalScroll`,
   ок), `PdfViewer` (оверлей на странице, ок), `DocTextViewer` (тулбар, ок), `DaySlotCell` (шапка
   расписания: 2 ячейки `weight(1f)` + кнопка 48dp — по 2dp с ячейки, ок).

---

## 11. Чего в этом бэклоге нет (P2/P3 — нужны продуктовые решения)

- **Настройки:** 8 секций → 4 группы, «Звонки»/«Карта» — подстраницы. Требует макета.
- **Шапка:** показать «Группа · подгруппа» + статус синхронизации в `MainHeaderBanner` (сейчас только логотип).
- **Back:** единое правило «back → на расписание, выход только с расписания» (`MainScreen.kt:123-129`).
- **Терминология:** вкладка «Уроки» внутри содержит «Задания» — переименовать вкладку в «Учёба».
- **Удаление оценки:** заменить таймерный double-tap на long-press (после FIX-6 можно спокойно).
- **fontScale 2.0:** `GradesScreen.kt` `height(96.dp)` + 40sp; `MainScreen` label 10sp в 72dp; hub-вкладки
  11sp в 44dp → `heightIn`/`maxLines = 2`.
- **`ChipForm` = 32dp** (`Tokens.kt`): сознательно оставлен компактным; касание расширяет
  `bouncyClickable(...)` (FIX-2), но проверять диалоги на 48dp стоит после визуальной приёмки.
- **Настройки темы:** добавить «как в системе» (`isSystemInDarkTheme`).