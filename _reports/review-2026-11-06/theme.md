# Темы и внешний вид — аудит и дизайн-предложение

**Ревизор:** rev-theme · **Дата:** 2026-11-06 · **Ветка:** `v2.4-stable` (415be8c)
**Режим:** read-only. Код не менялся, gradle/тесты/Roborazzi не запускались (гейты у Лида).
**Область:** `ui/theme/*`, `util/AppThemeStore.kt`, `ui/screens/SettingsScreen.kt` (секция «Тема оформления»), `ui/teacher/TeacherAppearance.kt`, `widget/*`, `util/BellCountdownNotifier.kt`, `res/values*/themes.xml`, `app/proguard-rules.pro`, тесты темы/шотов.

> **Ограничение по источникам.** `web_search` в этой сессии недоступен (HTTP 401, ключ web-search невалиден). Индустрию собрал через `web_fetch` — ниже только ссылки, реально отдавшие 200. Для двух пунктов (Notion, YouTube) документированного URL получить не удалось — помечено явно.

---

## 1. Аудит текущего

### Общая картина

Система тем — **не Material 3 ColorScheme, а собственный слой токенов**: `MpkPalette` (51 поле, `ui/theme/Color.kt:8-58`), 8 пресетов × 2 палитры (`ui/theme/AppTheme.kt:7-153`), глобальный `paletteState` + 51 геттер (`ui/theme/Color.kt:166-225`). В коде **1 028 обращений** к токенам в **51 файле**.

Проверено грепом: `MaterialTheme.colorScheme` — **0 использований**; `isSystemInDarkTheme` — **0 использований**. Значит: (а) Material-схема в `Theme.kt:18-65` декоративна (реально работает `paletteState`), (б) режима «системная» нет вообще, а не «сломан».

Что есть: 8 пресетов, ручной тумблер тёмной, сохранение в SharedPreferences, `-keep` для enum, гейт контраста на 12 парах токенов, шоты в тёмной теме (2 теста), следование темы за приложением в виджетах (частично, см. P1-2).

Чего нет: режима «Системная», второго измерения (тон/контраст), системного акцента, тёмной темы в teacher-флейворе, обновления виджетов при переключении только режима, чистого чтения настроек (чтение = мутация глобального состояния).

---

### [P1] Нет режима «Системная» — тёмная тема игнорирует систему
**Где:** `app/src/main/java/com/politex/util/AppThemeStore.kt:16,37`; `ui/theme/AppTheme.kt:187-205`; `ui/theme/Theme.kt:16`
**Симптом:** у кого в системе включена тёмная — приложение при первом запуске светлое; «как в системе» выбрать нельзя, только вручную. При смене системной темы приложение не реагирует.
**Root cause:** модель состояния — булев флаг `app_dark` (`getBoolean(KEY_DARK, false)`), а не трёхзначный режим; `darkModeState: MutableState<Boolean>` не имеет состояния «не задано»; `isSystemInDarkTheme()` не вызывается нигде (grep = 0).
**Фикс:** `enum ThemeMode { SYSTEM, LIGHT, DARK }`, ключ `app_mode`; резолв `resolveDark(mode, isSystemInDarkTheme())` в `MyApplicationTheme`; дефолт — `SYSTEM` для новых, миграция — см. §6.
**Тест:** JVM `AppearanceResolveTest` (3 режима × systemDark true/false = 6 кейсов); Roborazzi-шот «настройки при системной тёмной».

### [P1] Виджет «Как в приложении» никогда не бывает тёмным
**Где:** `app/src/main/java/com/politex/widget/BellCountdownWidgetProvider.kt:443-461` (ветка `WidgetStyle.APP`, строка 452)
**Симптом:** тёмное приложение + виджет «Как в приложении» → светлая (синяя) плашка с белым текстом. Пользователь считает, что «тема не применяется к виджетам».
**Root cause:** `AppThemeStore.load(context).palette` — всегда **светлая** палитра пресета; режим не участвует в выборе. RemoteViews не умеют Compose, поэтому «слежение за темой» реализовано вручную и неполно.
**Фикс:** `AppearanceStore.read(context).resolvedPalette(systemNight)`; для `WidgetStyle.APP` брать `resolvedPalette`, для `WidgetStyle.SYSTEM` оставить `Configuration.uiMode` (сейчас корректно, `:445-449`) и подписать в конфиге виджета разницу.
**Тест:** JVM `WidgetStyleColorsTest` (APP × dark → `backgroundColor == darkPalette.brandFill.toArgb()`); ручная матрица 3 стиля × 3 режима на устройстве.

### [P1] В teacher-флейворе тёмную тему включить физически нечем
**Где:** `app/src/main/java/com/politex/ui/teacher/TeacherSettingsScreen.kt:134` (`item { ThemeSwatchesCard() }`); `ui/teacher/TeacherAppearance.kt:95-151`
**Симптом:** преподаватель видит 8 свотчей, но тумблера тёмной темы в его настройках нет (grep `Тёмная|setDark|DarkMode` по `ui/teacher` = 0). Тёмная применяется только если её включили в student-флейворе или восстановили из бэкапа.
**Root cause:** две независимые реализации выбора темы; в teacher-версию перенесли только грид пресетов.
**Фикс:** один общий composable `ui/components/AppearanceSection.kt`, использовать в обоих флейворах (`SettingsScreen` и `TeacherSettingsScreen`).
**Тест:** Roborazzi-шот `teacher_settings` с секцией; `ThemeSwatchesCard` удалить после переноса.

### [P2] Переключение только тёмной темы не обновляет виджеты
**Где:** `util/AppThemeStore.kt:20-34` (`save` → `updateAllWidgets`) vs `:40-50` (`setDark` → только `applyDarkMode`)
**Симптом:** включил тёмную — виджеты остаются в старом виде до следующего тика (до минуты и дольше, `WidgetAlarm`), а после фикса P1-2 выглядели бы «в старой теме».
**Root cause:** побочный эффект (обновление виджетов + строки «до звонка») продублирован только в одном сеттере.
**Фикс:** единственная точка записи `AppearanceStore.save(context, appearance)` = persist + `apply` + `updateAllWidgets` + `BellCountdownNotifier.refresh`; `setDark`/`save` сделать обёртками.
**Тест:** JVM `AppearanceStoreTest.переключение_режима_обновляет_виджеты` (spy на `WidgetUpdateHelper.updateAllWidgets`).

### [P2] Чтение настроек — это мутация глобального Compose-состояния (сторонний эффект во время композиции)
**Где:** `util/AppThemeStore.kt:14-18` (`load()` → `applyDarkMode`); вызовы: `ui/screens/SettingsScreen.kt:208`, `ui/teacher/TeacherAppearance.kt:97`, `widget/BellCountdownWidgetProvider.kt:452`, `util/BellCountdownNotifier.kt:307`
**Симптом:** `AppThemeStore.load(context)` вызывается внутри `remember { mutableStateOf(...) }` — то есть **в теле композиции** — и пишет в snapshot-состояние `paletteState`/`darkModeState`. Плюс из фоновых потоков (RemoteViews/уведомления): рендер виджета может «перекрасить» уже открытое приложение.
**Root cause:** API не разделён на «прочитать» и «применить».
**Фикс:** `read(context): Appearance` — чистая функция; `apply(appearance)` — явный вызов из `MainActivity`/настроек. Вызовы в композиции заменить на `remember { AppearanceStore.read(context).mode }`.
**Тест:** JVM `AppearanceStoreReadIsPureTest` (после `read` глобальное состояние не менялось).

### [P2] Белая вспышка при холодном старте, если тёмная включена вручную
**Где:** `app/src/main/res/values/themes.xml:4` (`android:windowBackground = @android:color/white`), `values-night/themes.xml:4` (`#0F1419`)
**Симптом:** система светлая, приложение форсировано в тёмную → при запуске белый кадр до первой отрисовки Compose. После добавления режима «Светлая»/«Тёмная» это станет заметнее (сейчас частично маскируется, т.к. ручной тёмной мало кто пользуется).
**Root cause:** `windowBackground` берётся из ресурсов, которые реагируют только на `uiMode`, а не на пользовательскую настройку; `AppCompatDelegate.localNightMode` недоступен — активити наследует `ComponentActivity` (`MainActivity.kt:48`), не `AppCompatActivity`.
**Фикс:** в `onCreate` сразу после резолва настроек — `window.setBackgroundDrawable(ColorDrawable(palette.bgMain.toArgb()))` (≈3 строки, до `setContent`); для API 31+ то же значение в `values-v31` splash (`windowSplashScreenBackground` не поддерживает рантайм-цвет — оставляем `windowBackground`).
**Тест:** ручная проверка cold start (3 режима × system light/dark); в JVM не тестируется.

### [P2] Акцент уведомления «до звонка» не учитывает тёмную тему
**Где:** `util/BellCountdownNotifier.kt:303-310`
**Симптом:** при `Theme.APP` акцент = `AppThemeStore.load(context).palette.brandFill` — светлый вариант. В тёмной шторке это `#0B3564` (тёмно-синий) → заливка иконки почти не читается (контраст к тёмному фону < 2:1).
**Root cause:** тот же резолв палитры без режима + `Color.White`-подобные допущения в `Theme.kt:22,26,50`.
**Фикс:** резолвить `resolvedPalette(...)`; цвет текста на заливке брать из нового токена `onAccent`, а не из литерала `Color.White`.
**Тест:** JVM `BellNotificationAccentTest` (dark → акцент = тёмная палитра brandFill, contrast(brandFill, #111) ≥ 3.0).

### [P2] Гейт контраста покрывает 12 «новых» пар и не покрывает базовые
**Где:** `app/src/test/java/com/politex/ThemeTokensContrastTest.kt:34-47`
**Симптом:** проверяются `mapDeadText/mapDeadBg`, `accentGold*`, `navSelected`, но **не** проверяются главные пары: `textTitle/textBody/textMuted × bgMain/surfaceVariant`, `onAccent × brandFill`, `menuText/menuSubtext × menuBg`. Именно они поедут при добавлении варианта фона (см. §5 — «Мягкий» роняет `textMuted` до 4.50).
**Root cause:** тест писался под конкретную задачу «токены карты/admin», а не как инвариант палитры.
**Фикс:** расширить набор до ~14 пар и гонять его по всем комбинациям пресет×режим×тон (48 наборов) — чистый JVM, без Roborazzi.
**Тест:** сам новый гейт.

### [P3] Локальный state настроек расходится с глобальным
**Где:** `ui/screens/SettingsScreen.kt:208,597`; `ui/teacher/TeacherAppearance.kt:97`
**Симптом:** бэкап-восстановление или смена темы в другом месте не обновляют уже отрисованный переключатель до ухода с экрана (`remember` держит снимок).
**Root cause:** значение читается один раз при входе, а не из наблюдаемого state.
**Фикс:** читать `AppearanceState.mode` напрямую (это `mutableStateOf`), локальный `var` убрать.
**Тест:** Compose-тест «внешнее изменение режима → UI обновился».

### [P3] Дублирование UI выбора темы (две копии)
**Где:** `ui/screens/SettingsScreen.kt:592-667` (`AppearanceCard`, грид 4×2 + тумблер) и `ui/teacher/TeacherAppearance.kt:95-151` (`ThemeSwatchesCard`, `LazyRow` + другой размер/подписи)
**Симптом:** любые изменения темы нужно делать дважды; уже разошлись (тумблер есть только в одной).
**Root cause:** копипаста между флейворами.
**Фикс:** `ui/components/AppearanceSection.kt`.
**Тест:** шоты обоих экранов после унификации.

### [P2/замер] R8 для новых настроек
**Где:** `app/proguard-rules.pro:12-17` (`-keepclassmembers enum * { ... <fields>; }`)
**Состояние:** правило корректно и спасает enum'ы, которые **хранятся по `.name`**: `WidgetStyle` (`BellCountdownWidgetProvider.kt:431-440`), `BellCountdownNotifier.Scope/Theme/Style` (`:44,59,76`). А `AppTheme` хранится по строковому `id` (`AppThemeStore.kt:27`), поэтому от R8 не зависит вовсе.
**Риск:** новый режим/тон, если их хранить по `.name`, попадут под то же правило — но правило `-keepclassmembers` **не мешает** R8 мержить/переименовывать сам класс enum, пока поля читаются рефлексией… достаточно для `.name`, недостаточно, если кто-то захочет `valueOf` по строке из внешнего ввода.
**Фикс:** конвенция «persist ids, never names» + строковый `id` в новых enum + явный тест «все исторические id резолвятся» + комментарий в proguard.
**Тест:** `AppearanceIdFrozenTest` (жёсткий список id в тесте; изменение id = красный тест).

---

## 2. Как делают гиганты

| Кто | Оси настройки | Сколько контролов у пользователя | Что берём |
|---|---|---|---|
| **Google / Material You** | Режим (Light/Dark/System) + **dynamic color из обоев** (API 31+). Роли: 26 тональных ролей на схему | 1 селектор режима + тумблер «Wallpaper colors» | «Системная» по умолчанию + системный акцент как *опция*, а не как палитра |
| **Apple HIG** | Appearance: Light / Dark / Automatic (за системой) + **один** AccentColor на всё приложение | 1 выбор + (у разработчика) один акцент | Один акцент — это **пресет**, а не пикер цвета. Пользователю не даём 8-й цветовой круг |
| **Telegram** | `baseTheme` ∈ {Classic, Day, Night, Tinted, Arctic} × `accent_color` (int) × `outbox_accent_color` | Пресеты фона + палитра акцентов + ночной режим | **Эталон:** «база» (свет/тьма/полутьма) отделена от акцента; «Tinted»/«Arctic» — это ровно наш «Мягкий/Глубокий» |
| **shadcn/ui + Tailwind** | Цвета только как CSS-переменные; `.dark` на корне; `next-themes` с `system` | 1 переключатель темы + `system` | Токены не плодим, варианты **вычисляем** из базы; «system» — обязательный третий пункт |
| **Notion** *(URL не подтверждён)* | Light / Dark / Use system settings + размер текста | 2 контрола | Ровно 2 контрола — предел, за которым начинается «засирание настроек» |

Ссылки (HTTP 200 проверены):
- Material 3, роли цвета — https://m3.material.io/styles/color/roles
- Android: Implement dark theme — https://developer.android.com/develop/ui/views/theming/darktheme
- Apple HIG: Dark Mode — https://developer.apple.com/design/human-interface-guidelines/dark-mode
- Telegram themes API (`baseThemeClassic/Day/Night/Tinted/Arctic`, `accent_color`, `outbox_accent_color`) — https://core.telegram.org/api/themes
- shadcn/ui: Dark Mode / Theming — https://ui.shadcn.com/docs/dark-mode

**Выделенный паттерн (максимум настройки при минимуме UI):** *две ортогональные оси с дефолтом «как система/как сейчас»*. Ось 1 — **режим** (System/Light/Dark), ось 2 — **характер поверхностей** (мягкий/стандартный/глубокий, т.е. светло-тёмная ↔ чёрная). Акцент — не третья ось-пикер, а пресеты (у нас уже 8) плюс опциональная автоматика (системный цвет). Итого 3×3 = 9 состояний без единого нового экрана и без 8×N ручных палитр.

---

## 3. Предлагаемая модель

### Данные (`ui/theme/Appearance.kt`, новый файл, ~180 строк)

```kotlin
enum class ThemeMode(val id: String, val title: String) {
    SYSTEM("system", "Как в системе"),
    LIGHT ("light",  "Светлая"),
    DARK  ("dark",   "Тёмная");
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: SYSTEM }
}

enum class SurfaceTone(val id: String, val title: String) {
    NATIVE("native", "Стандартный"),   // ровно как сейчас — вид не меняется
    SOFT  ("soft",   "Мягкий"),        // светлее: светлая = тонированный светлый фон, тёмная = «светло-тёмная»
    DEEP  ("deep",   "Глубокий");      // контрастнее: светлая = белый + тёмные границы, тёмная = чёрный (AMOLED)
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: NATIVE }
}

data class Appearance(
    val theme: AppTheme = AppTheme.COLLEGE,
    val mode: ThemeMode = ThemeMode.SYSTEM,     // НОВЫЙ дефолт
    val tone: SurfaceTone = SurfaceTone.NATIVE, // дефолт = текущий вид
    val systemAccent: Boolean = false,          // опция, Android 12+
)

fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT  -> false
    ThemeMode.DARK   -> true
}

// единственная точка, которую знает UI-слой
fun Appearance.resolvedPalette(systemDark: Boolean, dynamicPrimary: Color? = null): MpkPalette =
    (if (resolveDark(mode, systemDark)) theme.darkPalette else theme.palette)
        .let { if (systemAccent && dynamicPrimary != null) it.withSystemAccent(dynamicPrimary) else it }
        .tone(tone, dark = resolveDark(mode, systemDark))
```

Ключевое: **`AppTheme` не трогаем вообще** — 8 пресетов, их `palette`/`darkPalette`, `id`, `title`, `hint` остаются байт-в-байт. Все варианты — производные от них. Ноль правок в 51 файле с 1 028 обращениями к токенам: геттеры `ColorBgMain` и т.д. продолжают читать `paletteState`, в который теперь кладётся *трансформированная* палитра.

### Слой акцента системы (Material You) — да, но узко

`isSystemInDarkTheme` + `dynamicLightColorScheme/dynamicDarkColorScheme` требуют API 31+ (minSdk приложения = 24, `app/build.gradle.kts:86`), поэтому:

```kotlin
val dmPrimary: Color? = if (Build.VERSION.SDK_INT >= 31 && appearance.systemAccent)
    dynamicDarkColorScheme(context).primary else null   // обёрнуто в SDK-гейт
```

и в палитру меняем **только акцентную группу** (6 полей из 51): `brandBlue, activeBlue, brandFill, activeFill, badgeDiploma, navSelected`, `topBar = mix(primary, black, 0.55)`. Фон/поверхности/границы **остаются от пресета** — иначе теряется контроль контраста и рассыпается ось «тон».

Честная оценка: это не полноценный Material You (не 26 ролей), а «акцент из обоев». Полный dynamic color в этом приложении **не окупается**: 1 028 ручных обращений к токенам, из них акцентных — ~15%, а контраст-гейт пришлось бы переписать под произвольные обои. Статус: **фаза 6, опционально**; выключено по умолчанию.

### Свой акцентный цвет (палитра из 8 свотчей) — **не делаем**

Обоснование (это ответ на «не засирая настройки»): 8 пресетов **уже являются** выбором акцента. Второй грид из 8 свотчей даёт 64 комбинации, из которых часть нечитаема, и +1 ряд UI в самой заметной секции. То, что заказчик называет «свой цвет», дешевле и качественнее закрывается системным акцентом. Если позже понадобится — это уже «тема как в Telegram», отдельный продукт.

### Что видит пользователь (итог)

| Ось | Значения | Дефолт | Видимость |
|---|---|---|---|
| Пресет | 8 (как сейчас) | COLLEGE | всегда, 2 ряда свотчей |
| Режим | Системная / Светлая / Тёмная | **Системная** | всегда, 1 строка-сегменты (заменяет тумблер, та же высота 56 dp) |
| Тон | Стандартный / Мягкий / Глубокий | Стандартный | **свёрнуто** под строкой «Тон оформления», раскрытие по тапу |
| Акцент системы | вкл/выкл | выкл | внутри раскрытия, только API 31+ (иначе строка скрыта) |

Итого: **0 новых видимых строк по умолчанию** (тумблер заменился селектором той же высоты), **+1 строка** при раскрытии «Тон оформления» с 3 чипами.

---

## 4. Эскиз секции настроек

```
┌──────────────────────────────────────────────────────────┐
│ 🎨 Тема оформления                                       │
│                                                          │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐                        │
│  │Фирм.│ │Изум.│ │Борд.│ │Граф.│   ← 8 свотчей как есть │
│  └─────┘ └─────┘ └─────┘ └─────┘      (2 ряда по 4)      │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐                        │
│  │Индиг│ │Янтар│ │Сталь│ │Слив.│                        │
│  └─────┘ └─────┘ └─────┘ └─────┘                        │
│ ──────────────────────────────────────────────────────── │
│ ◐  Режим          ┌────────────┬─────────┬──────────┐    │
│                   │Как в системе│Светлая │ Тёмная   │    │  ← НОВОЕ
│                   └────────────┴─────────┴──────────┘    │    (замена
│ ──────────────────────────────────────────────────────── │     тумблера)
│ ▤  Тон оформления            Стандартный             ›   │  ← свёрнуто
│ ──────────────────────────────────────────────────────── │
│                                                          │
│   ── раскрыто по тапу ───────────────────────────────    │
│   ┌────────────┐ ┌─────────┐ ┌──────────┐               │
│   │Стандартный │ │ Мягкий  │ │ Глубокий │               │  ← 3 чипа
│   └────────────┘ └─────────┘ └──────────┘               │
│   Мягкий — фон светлее и спокойнее                       │  ← подпись
│   Глубокий — максимальный контраст, в тёмной — чёрный    │    меняется
│                                                          │    по режиму
│ ──────────────────────────────────────────────────────── │
│ ✨ Акцент как в системе                         ( ● )     │  ← только API 31+
│    Цвета обоев Android 12+. Меняет только акцент         │
└──────────────────────────────────────────────────────────┘
```

Поведение и тексты:

- **Segmented control** в строке «Режим» — 3 равных сегмента, высота 44 dp, `Role.RadioButton` + `selected` в семантике (как у `ThemeSwatch`, `SettingsScreen.kt:689-693`), testTag `settings_mode_system/light/dark`.
- Подпись под «Тон оформления» в свёрнутом виде — текущее значение (`Стандартный` / `Мягкий` / `Глубокий`); при раскрытии — одна строка-пояснение, **меняющаяся по режиму**:
  - Мягкий: светлая → «Фон чуть тонированный, границы спокойнее»; тёмная → «Светло-тёмная: фон приподнят, меньше черноты».
  - Глубокий: светлая → «Белый фон, границы и текст контрастнее»; тёмная → «Чёрный фон (AMOLED), экономит батарею на OLED».
- Progressive disclosure: свёрнуто по умолчанию **всегда**, даже если выбран не «Стандартный». Состояние раскрытия не сохраняется между заходами (не мусорим в prefs).
- Применение — **мгновенное, без перезапуска**: запись в `paletteState` → рекомпозиция читателей токенов. Анимацию цвета не делаем (51 токен не анимируем, это дало бы 8×2×3 анимаций и джанк); меняется за один кадр, как сейчас при смене пресета.
- Доступность: каждый сегмент — hit-target 44 dp, `contentDescription` = подпись; состояние выделения не только цветом (галочка/жирность внутри свотча уже есть, `SettingsScreen.kt:723`).

---

## 5. Палитра / токены

### Новые поля `MpkPalette` (только 2)

| Поле | Зачем | Значение по умолчанию |
|---|---|---|
| `onAccent: Color` | цвет текста/иконки на `brandFill`/`activeFill`; убирает 4 литерала `Color.White` (`Theme.kt:22,26,50,63`) и `0xFFFFFFFF` в виджетах | `Color.White` (свет), `Color.White` (тень) — совместимо |
| `onAccentMuted: Color` | второстепенный текст на заливке (сейчас захардкожен в виджете: `subColor = 0xFFD6E2F0`, `BellCountdownWidgetProvider.kt:456`) | `#D6E2F0` (тень) / `#C9D6E4` (свет, если понадобится) |

Остальные 49 полей не меняются. Никаких новых цветовых токенов для «мягкого/глубокого» — **варианты вычисляются**.

### Формулы (HSL/смешения, не 8×N ручных палитр)

```
mix(a, b, t)        = a + (b - a) * t                 // покомпонентно, sRGB
contrast(fg, bg)    = (Lmax + .05) / (Lmin + .05)     // WCAG 2.x, L — относительная яркость
ensureContrast(fg, bg, min):
    if contrast(fg,bg) >= min: return fg
    repeat 8: fg = затемнить по HSL  L -= 2%  (в тёмной теме — осветлить)  // детерминированно
    return fg                                     // если не сошлось — вернуть исходный и пометить в тесте
```

Одна трансформация на комбинацию, считается лениво (только активная) — <1 мс; в тестах считаются все 48.

**Светлая тема**

| Токены | NATIVE | SOFT | DEEP |
|---|---|---|---|
| `bgMain` | `#FFFFFF` | `mix(#FFF, brand, 4%)` → `#F5F7F9` (COLLEGE) | `#FFFFFF` |
| `surfaceVariant` | `#F8FAFC` | `mix(bg, brand, 3%)` | `#F2F5F9` |
| `surfaceHighlight` | `#EDF2F7` | `mix(bg, brand, 6%)` | `#E7EDF4` |
| `borderLight` | `#E2E8F0` | `mix(border, bg, 25%)` (мягче) | `#7B8794` (3.66:1) |
| `dividerLight` | `#E5E7EB` | мягче | `#94A3B8` (2.56:1) |
| `textMuted` | `#6B7280` (4.83) | **`ensureContrast(..., 4.6)`** → `#5F6675` (**5.37**) | `#5B6472` (**5.98**) |
| `textDisabled` | `#CBD5E1` | без изменений | `mix(disabled, textBody, 35%)` |

Причина clamp'а — замер: на `#F5F7F9` исходный `#6B7280` даёт **4.50** (на грани провала AA), после clamp'а — **5.37**.

**Тёмная тема**

| Токены | NATIVE | SOFT («светло-тёмная») | DEEP (чёрная/AMOLED) |
|---|---|---|---|
| `bgMain` | `#0F1419` | `mix(#0F1419, #FFF, 5.5%)` = **`#1C2126`** | **`#000000`** |
| `surfaceVariant` | `#161C24` | `#25292E` | `#0A0C0F` |
| `surfaceHighlight` | `#1F2833` | `mix 12%` | `#121519` |
| `menuBg` / `menuSubBg` | `#12161B` / `#0C1014` | `mix 5%` | `#000000` |
| `borderLight` | `#2A333F` | без изменений | `mix(#2A333F,#FFF,22%)` = **`#596069`** (3.30:1) |
| `dividerLight` | `#232C37` | без изменений | **`#464E57`** (2.49:1) |
| `textMuted` | `#98A2B3` (7.19) | 6.30 | 8.15 |

Замеры, обосновывающие цифры:
- `textTitle` на `#1C2126` → 14.84; `textBody` → 13.06; `textMuted` → 6.30 — AA с запасом.
- AMOLED: `surfaceVariant #0A0C0F` к `#000` даёт всего **1.07** → разделение поверхностей на чёрном **обязано** идти границами (3.30:1), а не заливкой. Это записано в дизайн-правило: в DEEP-тёмной «поверхность = чёрный + бордер», не «чуть светлее чёрного».
- `success #4ADE80` на `#000` → 12.05; `textBody #E3E7EC` → 16.91 (AA/AAA ок).

**Акцент не двигаем вовсе** — это охраняет 8 привычных палитр и «мягкость» не превращается в другую тему. Но запас посчитан (на случай будущей просьбы «акцент посветлее»): белый текст на заливке держит AA при осветлении акцента до **+10 %** в тёмной теме (5.32–6.16) и до **+20 %** в светлой; на +25 % в светлой уже провал (EMERALD 4.28, AMBER 4.04, STEEL 4.22). Если когда-нибудь включать — только через `ensureContrast(onAccent, brandFill, 4.6)`.

### Сколько комбинаций и как они проверяются

8 пресетов × 2 базовых палитры × 3 тона = **48 наборов** ≈ 2 448 значений токенов. Руками — невозможно, поэтому: **все варианты — чистые функции**, а гейт контраста перебирает 48 наборов × 14 пар = 672 проверки в одном JVM-тесте (~10 мс, без Roborazzi).

---

## 6. Миграция prefs + R8

### Ключи SharedPreferences (`mpk_ui_prefs`)

| Ключ | Тип | Статус |
|---|---|---|
| `app_theme` | String id (`college`…`plum`) | **без изменений**, 8 id заморожены |
| `app_dark` | Boolean | **остаётся**, но становится *зеркалом* (для откатов и старых бэкапов) |
| `app_mode` | String id (`system`/`light`/`dark`) | **новый** |
| `app_tone` | String id (`native`/`soft`/`deep`) | **новый** |
| `app_dynamic` | Boolean | **новый**, дефолт `false` |

### Правила ленивой миграции (ничего не перезаписываем при чтении)

```kotlin
fun read(context: Context): Appearance {
    val p = context.getSharedPreferences("mpk_ui_prefs", MODE_PRIVATE)
    val mode = p.getString("app_mode", null)?.let(ThemeMode::from)
        ?: if (p.contains("app_dark"))                       // выбор сделан вручную в старой версии
             if (p.getBoolean("app_dark", false)) ThemeMode.DARK else ThemeMode.LIGHT
           else ThemeMode.SYSTEM                             // не трогал — получает «как в системе»
    return Appearance(
        theme  = AppTheme.from(p.getString("app_theme", null)),
        mode   = mode,
        tone   = SurfaceTone.from(p.getString("app_tone", null)),
        systemAccent = p.getBoolean("app_dynamic", false),
    )
}
```

| Было у пользователя | Стало | Комментарий |
|---|---|---|
| `app_theme = "bordeaux"` | `theme = BORDEAUX` | id не меняются |
| `app_dark = true` | `mode = DARK` | выбор сохранён |
| `app_dark = false` | `mode = LIGHT` | **важно:** ключ присутствует → не «переводим» человека в SYSTEM против его воли |
| ключа `app_dark` нет | `mode = SYSTEM` | новый дефолт (единственное изменение поведения) |
| — | `tone = NATIVE` | вид не меняется ни у кого |
| — | `systemAccent = false` | ни у кого не меняется |

**Запись** (`save`): пишет `app_mode`, `app_tone`, `app_dynamic`, `app_theme` **и зеркалит** `app_dark = (mode == DARK)`. Зеркало нужно для откатов: старая сборка на `app_mode=LIGHT` увидит `app_dark=false` и останется в светлой — деградация мягкая.

### Бэкап (`util/BackupManager.kt:69-70,194-199`)

Добавить в JSON `appMode`, `appTone`, `appDynamic`; при восстановлении:
1) есть `appMode` → применить; 2) нет → вывести из `appDark` (как в миграции); 3) `appDark` писать всегда (совместимость со старыми сборками, читающими бэкап). Валидация: неизвестные id → дефолт (не падать), `themeRestored` считать по-новому.

### R8 (`app/proguard-rules.pro`)

- Существующее правило `-keepclassmembers enum * { values(); valueOf(java.lang.String); entries(); <fields>; }` (строки 12-17) **оставить**: оно спасает `WidgetStyle` и `BellCountdownNotifier.Scope/Theme/Style`, которые хранятся по `.name`.
- Новые enum храним **по строковому `id`**, а не по `.name` (как `AppTheme`) → дополнительные правила не нужны; риск от R8 = 0.
- В proguard — комментарий-конвенция: «persist ids, never names» (одна строка, чтобы следующий разработчик не сломал).
- Дополнительно (дешёвая страховка, не обязательно): `-keepclassmembers enum com.politex.ui.theme.ThemeMode { <fields>; }` — избыточно при текущем правиле, но явно фиксирует намерение.

### Совместимость API (чтобы 5 существующих тестов не переписывать)

`applyAppTheme(theme, dark)` / `applyDarkMode(dark)` / `isDarkThemeActive` / `darkThemeState` (`ui/theme/AppTheme.kt:187-205`) оставляем как **тонкие шимы** над новым состоянием (`applyDarkMode(false)` → `mode=LIGHT`, `true` → `DARK`). Тогда `DarkThemeTest`, `AppThemeTest`, `AppThemeStoreDarkTest`, `PromoShotsTest:581`, `CollegeLogoShotTest:109` продолжают работать без правок; убирать шимы — отдельной задачей после стабилизации.

---

## 7. План внедрения по шагам

| # | Шаг | Файлы | Объём | Риск |
|---|---|---|---|---|
| 1 | Модель + трансформации + гейт контраста (без UI) | `ui/theme/Appearance.kt` (новый), `ui/theme/Color.kt` (+2 поля), `test/.../TonePaletteContrastTest.kt` | ~180 + 90 тест | низкий — только новые символы, поведение не меняется |
| 2 | Store: `read` (чистый) / `save` (persist+apply+виджеты+уведомление), ленивая миграция, шимы | `util/AppThemeStore.kt` (51 → ~95), `test/.../AppearanceMigrationTest.kt`, `AppearanceStoreTest.kt` | ~45 + 80 тест | низкий; **дефолт SYSTEM меняет поведение** только у тех, кто ни разу не трогал тёмную |
| 3 | Резолв палитры в Theme/Activity/статус-бар/окно | `ui/theme/Theme.kt`, `ui/theme/AppTheme.kt`, `MainActivity.kt` | ~30 | низкий; статус-бар: иконки по `luminance(topBar) < 0.5` вместо жёсткого `false` (`Theme.kt:75`) |
| 4 | Общая секция UI, оба флейвора | `ui/components/AppearanceSection.kt` (новый ~170), `ui/screens/SettingsScreen.kt` (−75 / +40), `ui/teacher/TeacherSettingsScreen.kt` (+1), `ui/teacher/TeacherAppearance.kt` (−57) | ~210 | **средний:** Roborazzi-шоты настроек придётся перезаписать (гейт Лида) |
| 5 | Виджеты + уведомление | `widget/BellCountdownWidgetProvider.kt` (~8), `widget/WidgetConfigActivity.kt` (~4 подписи), `util/BellCountdownNotifier.kt` (~6) | ~18 | **средний:** RemoteViews, только ручная проверка на устройстве; Roborazzi не покрывает |
| 6 | Акцент системы (опция) | `ui/theme/Theme.kt`, `ui/theme/Appearance.kt`, `MainActivity.kt` (context для dynamic scheme) | ~55 | средний: только API 31+, контраст-гейт должен пропускать произвольные обои → clamp в §5 обязателен |
| 7 | Бэкап, proguard-комментарий, доки | `util/BackupManager.kt` (~15), `app/proguard-rules.pro` (+2), `MASTER-PLAN.md` | ~20 | низкий |

Порядок обязателен: 1 → 2 → 3 можно параллельно с 4 (разные файлы), 5 после 2-3, 6 последним и только по решению заказчика.

**Итого кода:** ~420–480 строк (в т.ч. UI ~215, тесты ~170). Новых экранов — 0. Новых видимых строк в настройках — 0 по умолчанию, +1 при раскрытии.

---

## 8. План тестов и шотов

### JVM (основной гейт, быстро)

1. `TonePaletteContrastTest` — 48 наборов × 14 пар: `textTitle/textBody/textMuted/textWeekdays × bgMain, surfaceVariant`, `menuText/menuSubtext × menuBg`, `onAccent × brandFill`, `onAccent × activeFill`, `borderLight × bgMain ≥ 1.4` (декоративно), `successText/successBg`, `errorText × bgMain`, `navSelected × surfaceHighlight`. Плюс инварианты: «DEEP-тёмная `bgMain` = `#000000`», «SOFT-тёмная светлее NATIVE по luminance», «`onAccent` не белый, если контраст < 4.5».
2. `AppearanceResolveTest` — `ThemeMode × systemDark` = 6 кейсов.
3. `AppearanceMigrationTest` — матрица prefs: {`app_dark` отсутствует / true / false} × {`app_theme` отсутствует / все 8 id} × {`app_mode` есть/нет} → ожидаемый `Appearance`; проверка «старые id не сломались» (`AppTheme.from`).
4. `AppearanceStoreTest` — round-trip записи; `save` обновляет виджеты и зеркалит `app_dark`; `read` не мутирует глобальное состояние.
5. `AppearanceIdFrozenTest` — жёсткий список id (`system/light/dark/native/soft/deep`) — изменение ломает тест.
6. `WidgetStyleColorsTest` — `WidgetStyle.APP` в тёмной теме = тёмная палитра; `SYSTEM` по `uiMode`.
7. `BellNotificationAccentTest` — акцент уведомления в тёмной теме.
8. Обновить существующие: `DarkThemeTest` (+ режимы), `ThemeTokensContrastTest` (перенести набор пар в общий хелпер).

### Roborazzi (визуальный гейт Лида)

| Шот | Что проверяет |
|---|---|
| `settings_appearance_collapsed` | секция по умолчанию: 8 свотчей + сегменты режима, «Тон» свёрнут |
| `settings_appearance_tone_expanded` | раскрытие: 3 чипа + подпись под текущий режим |
| `settings_appearance_deep_dark` | AMOLED: чёрный фон, границы видны |
| `schedule_soft_dark` | «светло-тёмная» на реальном экране расписания |
| `teacher_settings` | секция в teacher-флейворе (раньше тёмной не было) |
| перезапись `schedule_dark`, `college_logo_dark` | существующие тёмные шоты (палитра та же при `tone=NATIVE` — должны совпасть байт-в-байт; расхождение = сигнал, что трансформация течёт в NATIVE) |

Важно: в тестах `isSystemInDarkTheme()` нужно пиннить (`applyAppearanceForTest(Appearance(mode = ...))` + `LocalConfiguration` override), иначе шоты «поплывут» от окружения CI.

### Ручная матрица (устройство, не автоматизируется)

Виджет: 3 стиля (`SYSTEM`/`APP`) × 3 режима × 2 тона = 12 прогонов, проверять читаемость текста и обновление после переключения без перезапуска. Плюс cold start: 3 режима × system light/dark — нет белой вспышки.

---

## 9. Риски

| Риск | Оценка | Митигация |
|---|---|---|
| **Контраст в 8×2×3** | Реален и **уже измерен**: SOFT-светлая роняет `textMuted` до 4.50 (AA на грани), AMOLED-фон даёт 1.07 между поверхностями | гейт 48×14 в JVM + `ensureContrast` clamp; правило «на чёрном разделяем бордером, не заливкой» |
| **Виджеты (RemoteViews не умеют Compose)** | Высокий: сейчас `WidgetStyle.APP` всегда светлый (`BellCountdownWidgetProvider.kt:452`), `WidgetStyle.SYSTEM` следует `uiMode`, а не настройке приложения | резолв палитры в провайдере; в конфиге виджета явно подписать: «Системная — по теме Android, Как в приложении — по настройке MPK»; ручная матрица 12 прогонов |
| **Уведомления** | Средний: акцент = светлый `brandFill` на тёмной шторке | резолв по режиму + токен `onAccent`; проверка на устройстве |
| **Roborazzi dark-шоты** | Средний: 2 теста шлют `setDark`+`applyAppTheme` вручную (`PromoShotsTest:581`, `CollegeLogoShotTest:109`), после рефакторинга точка входа другая | шимы `applyAppTheme/applyDarkMode` сохраняют совместимость; `tone=NATIVE` гарантирует байт-в-байт картинку; перезапись 2 шотов + 5 новых |
| **Производительность** | Низкий: один `paletteState` → смена темы инвалидирует всех читателей токенов (весь UI). Это одноразовое событие, 1 кадр. **Опаснее** запись состояния из фоновых потоков виджетов (`BellCountdownWidgetProvider.kt:452` → `load()` → `applyDarkMode`) | `read()` сделать чистым; трансформация палитры ленивая (только активная комбинация, <1 мс); не анимировать 51 токен |
| **Изменение дефолта на «Системная»** | Средний продуктовый: часть пользователей впервые увидит тёмное приложение | это и есть запрос заказчика; ключ `app_dark` различает «выбрал сам» и «не трогал» → миграция никого не переворачивает |
| **Teacher-флейвор** | Средний: секция темы там отдельная и без режима | общий `AppearanceSection`; шот `teacher_settings` |
| **R8** | Низкий | ids вместо `.name`; существующее правило не трогаем; `AppearanceIdFrozenTest` |

### Что сознательно НЕ делаем

- **Пикер акцентного цвета** (8 свотчей + color wheel) — дублирует 8 пресетов, даёт 64 нечитаемых комбинации, +1 ряд в самой заметной секции.
- **Полный Material You** (26 ролей) — 1 028 ручных обращений к токенам; окупается только переписыванием всего слоя.
- **Отдельная тема для виджетов** сверх `SYSTEM/APP` — интерфейс конфигурации виджета не резиновый.
- **Анимация смены темы** — 51 токен × 48 состояний, джанк без выгоды; мгновенная смена уже ощущается как «щелчок», привычно по текущему поведению.
- **Шрифты/масштаб в этой секции** — в teacher уже есть `ScaleCard`/`UiScale`, в student — «Компактное расписание»; не смешиваем оси.

---

## Приложение: что проверено в коде

| Проверка | Результат |
|---|---|
| `MaterialTheme.colorScheme` в `main` | 0 использований |
| `isSystemInDarkTheme` | 0 использований |
| `AppTheme` пресетов | 8 (`AppTheme.kt:14-147`), id: college/emerald/bordeaux/graphite/indigo/amber/steel/plum |
| Обращений к токенам | 1 028 в 51 файле |
| `MpkPalette` полей | 51 (`Color.kt:8-58`) |
| Порог контраста сейчас | 12 пар × 8 тем × 2 режима (`ThemeTokensContrastTest.kt:34-55`) |
| Хранение enum по `.name` | `WidgetStyle`, `BellCountdownNotifier.Scope/Theme/Style` → покрыто `-keepclassmembers enum *` |
| Хранение темы по id | `AppThemeStore.kt:27` — от R8 не зависит |
| Тёмная тема в teacher | недоступна (grep пуст, `TeacherSettingsScreen.kt:134`) |
| `windowBackground` | `values/themes.xml:4` = белый; `values-night` = `#0F1419` |
| minSdk / material3 | 24 / compose BOM 2024.09.00 (dynamic color = API 31+ гейт) |

**Вердикт:** фундамент хороший (собственные токены, 8 пресетов, гейт контраста, R8 учтён), но модель состояния — одна булева ось. Добавление двух ортогональных осей (режим + тон) даёт заказчику ровно то, что он просил — «светлее», «тёмная/светло-тёмная», «как в системе» — ценой ~1 строки раскрытия в UI и без новых экранов.

---

## 10. Статус реализации (task-4, 2026-11-06)

Блок A + Блок B внедрены. Material You — **не делали**, отложено (TODO ниже).

### Что появилось

| Файл | Что |
|---|---|
| `ui/theme/Appearance.kt` (новый, ~240) | `ThemeMode {SYSTEM, LIGHT, DARK}`, `SurfaceTone {NATIVE, SOFT, DEEP}`, `AppearanceConfig`, `effectiveDark`, `resolvePalette`, `mix`, `contrastRatio`, `ensureContrast`, `isLightColor`; трансформации тона `softLight/softDark/deepLight/deepDark` |
| `ui/theme/AppTheme.kt` | шимы `applyAppTheme/applyDarkMode` сохранены; добавлены `applyAppearance`, `onSystemDarkChanged`, `currentThemeMode/currentSurfaceTone`; в `darkPaletteWithAccents` `navSelected = brand` (фикс навигации) |
| `ui/theme/Color.kt` | `MpkPalette` +`onAccent`/`onAccentMuted` (дефолты, 51 поле не правилось); `DarkPalette.navSelected = #6FA3D8` (= brandBlue); геттеры `ColorOnAccent`, `ColorOnAccentMuted` |
| `ui/theme/Theme.kt` | `isSystemInDarkTheme()` + `LaunchedEffect → onSystemDarkChanged` (реакция без перезапуска Activity); иконки системных баров по `isLightColor`; `onPrimary/onSecondary = ColorOnAccent` |
| `util/AppThemeStore.kt` | `readPrefs()` (чистое чтение + ленивая миграция), `loadMode/loadTone`, `saveTheme/saveMode/saveTone/saveAll`, легаси-алиас `save`, шим `setDark` (теперь обновляет виджеты), `effectivePalette()` (без побочных эффектов), `isSystemDark()` |
| `ui/components/AppearanceSection.kt` (новый, ~360) | общая секция: 8 свотчей, сегменты режима 56 dp, свёрнутая строка «Тон оформления» + 3 чипа, подсказка под текущий режим; testTag-и `theme_mode_segments`, `theme_mode_system/light/dark`, `theme_tone_row`, `theme_tone_native/soft/deep`, `theme_swatches`, `theme_swatch_<id>` |
| `ui/screens/SettingsScreen.kt` | `AppearanceCard` + `ThemeSwatch` удалены (−142 строки), вызов → `AppearanceSection()`, чтение темы в композиции убрано |
| `ui/teacher/TeacherSettingsScreen.kt` | `ThemeSwatchesCard()` → `AppearanceSection()` — в teacher-флейворе появился выбор режима |
| `widget/BellCountdownWidgetProvider.kt` | `WidgetStyle.APP` → `effectivePalette(context)` + `ColorOnAccent`/`ColorOnAccentMuted`, `pastColor` по режиму |
| `util/BackupManager.kt` | экспорт `appTheme/appMode/appTone/appDark` (через чистое чтение), восстановление с обратной совместимостью |

Тесты (новый пакет `app/src/test/java/com/politex/theme/`):
`ThemeModelTest` (эффективная тьма, NATIVE == пресет байт-в-байт для 8 тем, тон не трогает акценты, SOFT светлее/темнее, DEEP = #000, onAccent/onAccentMuted AA для всех комбинаций, 144 сборки) · `ThemeContrastTest` (48 наборов × 16 пар, навигация против пилюли и против бара, AMOLED-границы ≥3:1) · `AppThemeStoreMigrationTest` (3 сценария `app_dark`, приоритет `app_mode`, зеркало, `saveAll`, чистота чтения, ночная конфигурация) · `ThemeWidgetPaletteTest` (APP берёт эффективную палитру, SYSTEM следует uiMode).

### Фикс контраста навигации (из UX-ревью)

Реальный фон навигации: `NavigationBar(containerColor = ColorBgMain)` (`MainScreen.kt:226`), пилюля выбранной вкладки = `surfaceHighlight` (`:275`), а цвет иконки **и подписи** = `ColorNavSelected` (`:273-274`).

- `darkPaletteWithAccents`: `navSelected = active` → **`navSelected = brand`**.
- Замеры после фикса (8 тёмных тем): `navSelected/surfaceHighlight` **4.55–5.92 : 1**, `navSelected/bgMain` **5.66–7.36 : 1** (было 4.56–6.17 и 5.67–7.67 по этим двум парам, но 2.7–3.8 при измерении против `topBar` — отсюда цифры ревьюера).
- Светлая палитра не менялась (`navSelected = topBar`): 9.46–18.2 : 1 на белом баре и 8.40–16.2 : 1 на пилюле — там всё в порядке.
- Гейт: `ThemeContrastTest` покрывает обе пары для всех 48 наборов + отдельный тест-регрессия `навигация в тёмной теме различима на обоих фонах`.

### Что осталось (осознанно вне scope task-4)

- **TODO (Material You / «Акцент как в системе»)**: `Build.VERSION.SDK_INT >= 31` + `dynamicDarkColorScheme(ctx).primary` → перекрашивать только 6 акцентных полей (`brandBlue, activeBlue, brandFill, activeFill, badgeDiploma, navSelected`) и `topBar = mix(primary, black, 0.55)`; фон/поверхности оставить пресетными, иначе теряется контроль контраста. Ориентир — +55 строк в `Theme.kt`/`Appearance.kt`. В UI место готово (внутри раскрытия «Тон оформления»), переключателя пока нет.
- `util/BellCountdownNotifier.kt:307` — акцент уведомления всё ещё берёт светлую палитру (вне write-scope task-4): заменить на `AppThemeStore.effectivePalette(context)`.
- `ui/teacher/TeacherAppearance.kt:95-151` — `ThemeSwatchesCard` больше не вызывается (вне write-scope): удалить отдельной задачей.
- `setDark` теперь обновляет виджеты, но не строку «до звонка» — обновление уведомления стоит добавить вместе с предыдущим пунктом.
- Roborazzi: существующие dark-шоты должны совпасть байт-в-байт (тон по умолчанию NATIVE = тождество, `navSelected` в шотах не участвует в пикселях навигации, кроме цвета выбранной вкладки → **возможен дифф двух шотов с нижней навигацией**, перезаписывать PNG не стал, это гейт Лида).