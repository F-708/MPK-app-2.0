# Полное ревью «Мой Политех» — сводка (2026-11-06)

Ветка `v2.4-stable`, HEAD `415be8c`, Room v11, Kotlin 178 файлов / 44 764 строки в `src/main`,
228 тестовых файлов / 1 677 `@Test`, флейворы `standard` / `admin` / `adminplus` / `teacher`.

## 1. Как проводилось

- 12 независимых доменов, 8 durable-агентов + 4 one-shot субагента, все read-only по коду.
- Каждый домен писал отдельный отчёт в эту папку; Лид свёл результаты, приоритизировал и вёл фиксы.
- Baseline до правок: `:app:testStandardDebugUnitTest` — **зелёный** (BUILD SUCCESSFUL, 4m17s).
- Код-гейты после волн: `compileStandardDebugKotlin` → `testStandardDebugUnitTest` → коммиты по блокам.

Отчёты доменов:

| Домен | Файл | Находки |
|---|---|---|
| Расписание | [schedule.md](schedule.md) | 4×P0, 5×P1, 5×P2, 2×P3 |
| Домашние задания | [homework.md](homework.md) | 3×P0, 5×P1, 4×P2, 3×P3 |
| Приём файлов / интенты | [intake.md](intake.md) | 4×P0, 5×P1, 4×P2, 2×P3 |
| Темы и внешний вид | [theme.md](theme.md) | 3×P1 + модель кастомизации |
| Безопасность / приватность | [security.md](security.md) | 3×P0, 5×P1, остальное P2/P3 |
| Данные / Room / бэкап | [data.md](data.md) | 3×P1, 7×P2, 8×P3 (миграции 1→11 полные) |
| Производительность / Compose | [perf.md](perf.md) | 4×P1 (P0 нет) |
| UX / доступность | [ux-a11y.md](ux-a11y.md) | 3×P0, 6×P1, 7×P2, 4×P3 |
| Тесты и CI | [tests-ci.md](tests-ci.md) | 3×P0 (гейты мертвы) |
| Релиз / конфигурация | [release.md](release.md) | 2×P0, 8×P1 |
| Архитектура и техдолг | [architecture.md](architecture.md) | 2×P0, 3×P1 |
| Функциональные сценарии | — | (в работе отдельного субагента) |

Бэклоги с готовыми патчами (для следующей волны):

- [data-fixes-backlog.md](data-fixes-backlog.md) — destruct-fallback, облачный бэкап, Room 11→12, индексы.
- [perf-fixes-backlog.md](perf-fixes-backlog.md) — секундный тик виджетов, `collectAsStateWithLifecycle`, двойной декод .doc.
- [ux-fixes-backlog.md](ux-fixes-backlog.md) — доступное удаление задания/оценки, слайдер веса, тексты ошибок.

## 2. Вердикт

Продукт функционально зрелый (расписание, ДЗ, материалы, оценки, карта, звонки, 4 виджета,
4 флейвора, teacher-режим, обновления из приложения), но три класса проблем делают его хрупким:

1. **Данные и приватность.** База ФИО учащихся лежит в истории **публичного** GitHub-репозитория
   (см. раздел 6). TOTP-секрет владельца — в коде и в dex. `fallbackToDestructiveMigration`
   включён рядом с полной цепочкой миграций.
2. **«Тихие» деградации.** Устаревшее расписание показывается как свежее; документ не от той даты
   молча отбрасывается; неподдерживаемый файл принимается и остаётся мёртвым грузом; ошибки
   глотаются `runCatching{}.getOrDefault`. Пользователь видит «ничего не произошло» вместо причины.
3. **Мёртвые гейты.** CI не запускается на рабочей ветке, Roborazzi снимает в никуда (нет
   `outputDir`/эталонов), версия из git-тега считается неверно (`git describe` → `v2.2.0`),
   release-workflow описан в RELEASE.md, но отсутствует.

## 3. ТОП-12 рисков (сводно по доменам)

| # | Приоритет | Риск | Где | Отчёт |
|---|---|---|---|---|
| 1 | **P0** | База ФИО в истории публичного репо `F-708/MPK-app-2.0` (коммит `0d1d389`, 1703 строки) | GitHub | [SECURITY-INCIDENT-pii-history.md](SECURITY-INCIDENT-pii-history.md) |
| 2 | **P0** | «Открыть с помощью» принимает любой `content://` (APK/ZIP/MP3/XLSX): VIEW-фильтр `*/*` + runtime-гейт только для APK | `AndroidManifest.xml:72-137`, `ExternalMaterialIntake.kt:99-113` | [intake.md](intake.md) |
| 3 | **P0** | Бейдж ДЗ привязан к одной календарной дате → виден один раз, второе вхождение предмета пустое | `HomeworkMarkers.kt:56-57,101-120` | [homework.md](homework.md) |
| 4 | **P0** | Расписание: нет TTL кэша → старое показывается как свежее; `FoundNoGroup` навсегда гасит сеть; документ не от той даты молча отбрасывается; `documentMatchesDate` fail-open | `ScheduleRepository.kt:34-48,335-389`, `MpkScheduleParser.kt:567-574,817-826` | [schedule.md](schedule.md) |
| 5 | **P0** | Версия релиза: `git describe` даёт `v2.2.0`; 24 из 29 тегов двухкомпонентные → каждый релиз получает один и тот же versionCode | `app/build.gradle.kts:11,54-55` | [release.md](release.md) |
| 6 | **P0** | CI мёртв: триггер только `main/master`, рабочая ветка `v2.4-stable`, `pull_request` не объявлен; Roborazzi без `outputDir` — снимки no-op | `.github/workflows/build-apk.yml` | [tests-ci.md](tests-ci.md) |
| 7 | **P0** | TOTP-секрет владельца в `src/owner/AccessCode.kt:16` и в dex admin/adminplus | `app/src/owner/...` | [security.md](security.md) |
| 8 | **P0** | `fallbackToDestructiveMigration()` при полной цепочке миграций → тихое стирание заданий/материалов/оценок | `MpkDatabase.kt:286` | [data.md](data.md) |
| 9 | **P0** | Intake-защёлка: `active` снимается не на всех путях → шаринг «застревает» навсегда; `handledIntakeKey` ставится до проверки teacher-режима | `ExternalMaterialIntake.kt`, `MainActivity.kt` | [architecture.md](architecture.md) |
| 10 | **P1** | Нет режима «Системная» тема; виджет «Как в приложении» всегда светлый; в teacher-флейворе тёмную включить нечем | `AppThemeStore.kt:16,37`, `BellCountdownWidgetProvider.kt:452`, `TeacherSettingsScreen.kt:134` | [theme.md](theme.md) |
| 11 | **P1** | adminplus без гейта доступа, хотя именно там PAT и публикация правок на GitHub | `OwnerOverlay.kt:68` | [security.md](security.md) |
| 12 | **P1** | Облачный бэкап не покрывает 6 prefs-файлов и снимает WAL-базу без `-wal/-shm`; свой JSON-бэкап теряет веса оценок, правки преподавателей, закладки | `data_extraction_rules.xml`, `BackupManager.kt:29-135` | [data.md](data.md) |

Полные списки — в отчётах доменов; здесь только то, что нельзя не заметить.

## 4. Что исправлено в этой волне

Гейт после правок: **1970 тестов, 0 падений** (было 1677 `@Test` в 228 файлах; добавились
`MaterialFormatsTest`, `HomeworkMarkersWindowTest`, `ScheduleHomeworkBadgeRenderTest`,
4 теста тем, `ScheduleCacheFreshnessTest`, `ScheduleHoleDayTest`, `ScheduleDocDateValidationTest`,
`ScheduleTodayBoundaryTest`, `AdminPlusGateTest`). Коммиты `505f6b4…f572498`.

| Блок | Что стало | Файлы | Тесты |
|---|---|---|---|
| FIX-1 | Приём только поддерживаемых форматов: манифест без `*/*` и без «голого» `scheme`, единый вайтлист, отказ с текстом причины, снятие защёлки intake, пикер по вайтлисту | `AndroidManifest.xml`, `util/MaterialFormats.kt`, `materials/ExternalMaterialIntake.kt`, `util/MaterialImporter.kt`, `MaterialBaseScreen.kt`, `MainActivity.kt` | `MaterialFormatsTest` (16), `ExternalMaterialIntakeTest`, инвертирован `ManifestFileViewAliasTest` и `ManifestIntentResolutionTest` |
| FIX-2 | ДЗ на всех уроках предмета в окне активности; не гаснет после звонка; прошедшие дни календарно; даты через epoch в обоих форматах | `util/HomeworkMarkers.kt`, `ui/screens/ScheduleScreen.kt` | `HomeworkMarkersWindowTest` (18), `ScheduleHomeworkBadgeRenderTest` (4, реальный рендер) |
| FIX-3 | Свежесть кэша по TTL (15 мин / 6 ч / окончательно), тихий рефреш, порог полноты дня для вердикта «группы нет», fail-closed валидация даты документа с переходом к следующему кандидату | `data/repository/ScheduleFreshness.kt`, `ScheduleRepository.kt`, `MpkScheduleParser.kt`, `MpkNetworkClient.kt`, `ui/viewmodel/AppViewModel.kt`, `worker/ScheduleCheckWorker.kt` | `ScheduleCacheFreshnessTest`, `ScheduleHoleDayTest`, `ScheduleDocDateValidationTest`, `ScheduleTodayBoundaryTest`; осознанно обновлены `SyncCacheFirstTest`, `ScheduleCacheGateTest`, `X1DocumentDateTest` |
| FIX-4 | Режим `SYSTEM/LIGHT/DARK` (системный по умолчанию) + тон `NATIVE/SOFT/DEEP` (SOFT мягче, DEEP AMOLED) как производные палитры с `ensureContrast`; контраст активной вкладки исправлен во всех 8 тёмных темах; тёмная тема доступна и в teacher-флейворе; виджет учитывает режим; миграция настроек без сброса | `ui/theme/{Appearance,AppTheme,Color,Theme,Tokens}.kt`, `ui/components/AppearanceSection.kt`, `util/AppThemeStore.kt`, `SettingsScreen.kt`, `TeacherSettingsScreen.kt`, `BellCountdownWidgetProvider.kt`, `BackupManager.kt` | `ThemeModelTest`, `ThemeContrastTest` (48 наборов × 16 пар), `AppThemeStoreMigrationTest`, `ThemeWidgetPaletteTest` |
| FIX-5 | PDF без фриза (IO + закрытие дескриптора на всех путях), ripple и роли TalkBack в общем модификаторе нажатия, тач-таргеты 48dp, гейт доступа в Admin+, CI на `v*-stable` и PR | `materials/PdfViewer.kt`, `ui/util/BouncyClick.kt`, `ui/theme/Tokens.kt`, `adminplus/AdminPlusAccess.kt`, `.github/workflows/build-apk.yml` | `AdminPlusGateTest` |

Сознательно **не** делалось в этой волне (нужны решения или отдельный блок): удаление
`fallbackToDestructiveMigration`, миграция Room 11→12, дедлайн-UI в форме задания, `subjectKey`
для сопоставления предметов, «Акцент как в системе» (Material You), секундный тик виджетов,
доступное удаление задания/оценки, ротация TOTP, перепись истории публичного репо.

## 4.1. Визуальная проверка (Roborazzi + vision-агенты)

Скриншоты перегенерированы с актуального кода (`-Proborazzi.test.record=true`) — до этого
в репозитории лежали кадры предыдущей сборки: Roborazzi без флага record не пишет файлы,
поэтому «эталоны» были мёртвыми (см. [tests-ci.md](tests-ci.md)). Это отдельный найденный дефект
процесса: **после любой визуальной правки шоты надо снимать с явным record**, иначе проверка
всегда «проходит».

Три независимых vision-агента смотрели свежие кадры. Итог и что из этого исправлено:

| Замечание | Статус |
|---|---|
| Блок «Внешний вид»: сегменты режима на месте, пресеты 2×4, строка «Тон оформления» | подтверждено ✔ |
| **Плашки «N УРОК» на «Звонках»: белый текст на светлом акценте = 2.66:1** | исправлено: введены токены содержимого `onLightAccent`/`onSuccess`, неактивные плашки ушли в тёмную заливку бренда (≈7:1), гейт контраста расширен до 864 пар |
| Контраст активной вкладки навигации в тёмной теме | исправлено ранее (5.66–7.36:1), оба агента подтвердили |
| Тёмная тема: иерархия — восемь ярких плашек перетягивали внимание с текущего урока | исправлено вместе с контрастом (акцент остался один) |
| Светлая тема: активная плашка громче баннера «СЕЙЧАС» | в бэклог (вкусовое, не блокер) |
| Тач-таргет сегментов режима «≈36–40dp» | проверено по коду: 48dp; оценка агента по кадру ошибочна |
| Вторичный текст в тёмной теме на границе 4–4.5:1 | в бэклог: поднять на один шаг тона |
| Пустой экран «Задания» без empty-state, обрез последней строки навбаром | в бэклог UX; обрез — артефакт шот-харнесса (реальный экран внутри `Scaffold` с `innerPadding`) |
| Раскрытое состояние «Тон оформления» не покрыто шотами | добавить кадр в следующей волне |

## 4.2. Размер APK (release)

Release-сборка уже шла с R8, `isShrinkResources` и `isCrunchPngs`, debug 24 МБ против release 6,7 МБ.
Разбор готового APK показал, где ещё лежит жир: `classes.dex` 4,0 МБ, `assets/viewer` 2,2 МБ,
**`assets/viewer/lib/cmaps` 1,1 МБ** (135 файлов), `res` 0,65 МБ, wasm 0,37 МБ.

CJK-карты глифов pdf.js (`UniCNS*/UniGB*/UniJIS*/UniKS*/Adobe-CNS1*/Adobe-GB1*/Adobe-Japan1*/Adobe-Korea1*`)
не нужны приложению колледжа с русскими документами. Исключены через
`androidResources.ignoreAssetsPatterns` в `app/build.gradle.kts`; `Identity-*` и `Adobe-Identity-UCS`
остались — на них держится кириллица и латиница.

| Флейвор | До | После | Δ |
|---|---|---|---|
| standard | 6,68 МБ | **5,87 МБ** | −12 % |
| admin | 6,71 МБ | **5,90 МБ** | −12 % |
| adminplus | 6,76 МБ | **5,95 МБ** | −12 % |

Компромисс зафиксирован осознанно: PDF с **не**встроенным CJK-шрифтом отрисует такой текст запасным
шрифтом, остальной документ читается; встроенные (Identity) шрифты не затронуты. Проверка артефактов:
`aapt2 dump badging` → `versionCode=20200999`/`2.2.0` (следствие сломанного тега, см. [release.md](release.md)),
`students.csv` в standard = 0, в admin/adminplus = 1 (по замыслу), подпись всех трёх — загрузочный ключ
`SHA-256 1496ea2e…3aac`.

## 5. Что осталось (бэклоги следующей волны)

- **Данные:** убрать `fallbackToDestructiveMigration` (вариант A+B/C из [data-fixes-backlog.md](data-fixes-backlog.md)),
  расширить облачный бэкап и JSON-бэкап, индексы `student_tasks`, транзакционный restore, Room 11→12
  одним патчем (сейчас 4 задачи претендуют на `MpkDatabase.kt`).
- **Производительность:** секундный тик виджетов → окно «у звонка» + `setChronometer`;
  `collectAsStateWithLifecycle` (32 места); двойной декод .doc и `PREFETCH_BATCH=6`; PDF уже поправлен.
- **Доступность:** удаление задания/оценки без свайпа, семантика слайдера веса, `navSelected` контраст,
  тач-таргеты 48dp, тексты ошибок — [ux-fixes-backlog.md](ux-fixes-backlog.md).
- **Релиз:** правильный тег на HEAD, восстановить `release.yml` (или удалить из RELEASE.md),
  `min_version_code`, раздельные `update-meta.json` для stable/preview, CI на все флейворы,
  гейт `students.csv` в CI.
- **Безопасность:** ротация TOTP-секрета и вынос из кода, PAT-публикация только с гейтом,
  `REQUEST_INSTALL_PACKAGES` — только standard, `FileViewAlias` сузить (сделано вместе с FIX-1),
  решить судьбу облачного бэкапа ПДн.
- **Архитектура:** `AppLog` + не глотать `CancellationException`; вынести `AppearanceSection` из
  SettingsScreen (сделано в FIX-4); `AppViewModel`/MainScreen разгрузить; инъекция `Clock` для тестов.

## 6. ⚠️ Инцидент приватности (требует действия владельца)

`F-708/MPK-app-2.0` — **публичный** репозиторий (подтверждено GitHub API: `"private": false`).
В его истории есть коммит `0d1d389` (2026-09-16, запушен) с `app/src/admin/assets/students.csv`
(1703 строки ФИО). Файл удалён из HEAD в `6affb7d` (2026-10-16), но история не переписана и
remote не обновлён (338 локальных коммитов ещё не запушены). Локально файл в `.gitignore`.

Готовый пошаговый плейбук с командами, проверками и чек-листом после force-push:
[SECURITY-INCIDENT-pii-history.md](SECURITY-INCIDENT-pii-history.md).
Коротко: сделать репо private **сейчас**, затем `git filter-repo` по `students*.csv`, force-push
всех ветвей и тегов, запросить очистку кэша в GitHub Support, проверить forks/архивы.

## 7. Как проверять

```bash
cd MPK-app-2.1
export JAVA_HOME=/c/Users/F708/.jdks/jbr-21.0.10
export ANDROID_HOME=/c/Users/F708/AppData/Local/Android/Sdk
sh ./gradlew :app:testStandardDebugUnitTest        # главный гейт
sh ./gradlew :app:compileStandardDebugKotlin       # быстрая проверка дерева
```

Гейт `students.csv` после любой release-сборки:
`unzip -l app/build/outputs/apk/standard/release/app-standard-release.apk | grep -c students.csv` → `0`.