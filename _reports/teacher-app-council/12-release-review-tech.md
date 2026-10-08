# 12 — Релиз-ревью техническое (flavor teacher, HEAD 042c18f)

Дата: 2026-10-08. Ревьюер: технический критик консилиума. Read-only (чтение/grep/git log, gradle не запускался).

## ВЕРДИКТ: **SHIP С ПРАВКАМИ** — 3 блокера (1 код, 2 релизных гейта), далее по серьёзности.

Код в целом дисциплинированный: честная деградация, Calendar-конвенция, goAsync+IO во всех receiver, ноль runBlocking в teacher-коде, дедуп-ключи на все уведомления, FLAVOR-гейты только в MainActivity. Но жёсткое условие §5.4 (полевой diff-сброс) не выполнено на ГЛАВНОМ пути синка (воркер), и релизные гейты T5 не выполнены.

---

## 1. Долг чести §4.2 — 11 пунктов

| # | Пункт | Статус | Доказательство |
|---|---|---|---|
| 1а | timestamp + утренняя плашка свежести | ✅ | TeacherHomeScreen.kt:172-182 («обновлено в HH:MM» / «данные кэша»); :420-426 плашка «по данным на вчерашний вечер — сверьтесь с чатом учебной части» на ИДЁТ/СЛЕДУЮЩАЯ при lastSyncEpoch < сегодня 00:00 (:148-153) |
| 1б | override + ПОЛЕВОЙ diff-сброс + уведомление о сбросе | ⚠️→❌ на пути воркера | Diff по-полевой реализован корректно: TeacherDayLoader.kt:196-229 (ROOM/TIME/SUBJECT/GROUPS/LESSON_GONE, ключ (dateString,lessonNumber), fail-safe «нет в before → не трогаем», мульстроки слота сравниваются агрегатом). НО вызывается только из loadDay (:370) и TeacherDayViewModel.refresh (:75). **TeacherScheduleWorker.kt:63 синхронизирует напрямую `syncTeacherScheduleForDate` — без diff-обёртки**: правки НЕ сбрасываются и НЕ уведомляются весь день (окна 06:30-07:15 и каждые 15 мин 10:00-21:00). То же в TeacherWeekScreen.refresh (:138). «Уведомление о сбросе» — только in-app плашка (TeacherDayViewModel.kt:118-126, комментарий сам признаёт «T4 — здесь же post системного уведомления»), системное уведомление не отправляется |
| 1в | онбординг-договор дословно | ✅ | TeacherOnboardingScreen.kt:122 — текст совпадает с §4.2в посимвольно |
| 1г | сводка с action «Пометить» | ✅ | TeacherNotifier.kt:375-390 (action → MainActivity + EXTRA_MARK_OVERRIDES); плашка-приём в TeacherNav.kt:99-111, 199-221 |
| 1д | никогда не врать молча (молчание при пустом кэше) | ✅ | TeacherNotifierLogic.summaryText:57-58 (пустой кэш + проваленный синк → null); TeacherNotifier.kt:193-197 (text==null → флаг НЕ ставится, молчим); flushPendingPublished:255 (данные исчезли → молчим); ошибки синка без уведомлений (TeacherScheduleWorker.kt:93) |
| 2 | surnameKey-плашка неоднозначности | ✅ | matchRowToTeacher YES/NO/AMBIGUOUS (TeacherInsights.kt:167-194); плашка на дне (TeacherHomeScreen.kt:319-329) и неделе (TeacherWeekScreen.kt:303-313); онбординг показывает ВСЕ карточки однофамильцев, тапнутая — первой (TeacherOnboardingLogic.confirmCandidates, sortedByDescending — демо-правка №2 выполнена) |
| 3 | метки свежести: экран + виджеты + сводка | ✅ | экран (:172-182); виджеты «обновлено в» (TeacherWidgetLogic.updatedLabel:112-117, Next:110-111, Today:73-78 — НЕ пустота/спиннер); сводка — cacheTimeLabel «от вчера 21:40» / «данные сайта на HH:MM» (TeacherNotifierLogic:76-90, TeacherNotifier:186-192) |
| 4 | MigrationV9ToV10Test полон | ✅ | MigrationV9ToV10Test.kt:37-134 — ВСЯ схема v9 (10 таблиц: lessons, student_tasks, teacher_lessons, task_reminders, material_*×4, grade_*×2), не 4 таблицы; + выживание данных (:248-268), + unique-индекс и отклонение дубля (:186-222), + REPLACE-семантика upsert (:225-245). Робolectric, реальная БД |
| 5 | CI-гейт | ✅ | .github/workflows/build-apk.yml «Unit tests (main gate)»: `testStandardDebugUnitTest`; `testTeacherDebugUnitTest` — в чеклисте MASTER-PLAN.md:885 (T5). Прогон T5 НЕ выполнен (см. блокеры) |
| 6 | ноль BuildConfig.FLAVOR вне MainActivity | ✅* | grep app/src/main: MainActivity.kt:49,69 (teacher, разрешено) + CollegeScreen.kt:233, SettingsScreen.kt:726 — это ПРЕДСУЩЕСТВУЮЩИЕ admin-гейты (git: коммиты T11/BB12, не teacher-волны). В новом teacher-коде — ноль, паттерн «профиль null → выход» (TeacherWidgetUpdater, TeacherNotifier) |
| 7 | релиз паровозом, versionCode общий | ✅ | app/build.gradle.kts:37-38 — один defaultConfig versionCode=20015/versionName=2.6 на все flavor'ы; teacher только suffix'ы (:29-33) |
| 8 | LiveDocTest | ✅ | TeacherScheduleLiveDocTest.kt — 7 тестов по живому доку 05.10.2026 (фикстур app/src/test/resources/fixtures/05.10.2026-raspisanie-prepodavatelej.doc, 108K, на месте): шапка-мусор, сдвиг номеров, уроки 11-12, matchesRequestedDate. Живой curl В ДЕНЬ рассылки — гейт T5, не выполнен |
| 9 | промер окна публикации | ❌ **ДО РЕЛИЗА** | Никаких следов выполнения: grep «промер/время публикации» по _reports, docs, MASTER-PLAN — только ASSUMPTION-напоминания (08-debate-critic-product.md:154, 10-advocate-final.md:126). Тайминги §5.3 остаются непромеренными |
| 10 | dexdump-гейт в чеклисте | ⚠️ | В SPEC есть (§4.2.10, §9.4), в операционном чеклисте MASTER-PLAN T5 (:885) — НЕТ (только students.csv=0). Не добавлен, не выполнен |
| 11 | versionName в «О программе» | ✅/⚠️ | TeacherMoreScreen.kt:161 «Версия ${BuildConfig.VERSION_NAME}» (= «2.6-teacher»); имя файла `mpk-teacher-x.y.apk` — автопереименования output в gradle НЕТ, в чеклист T5 не внесено (ручная операция при публикации) |

Демо-ревью (11-demo-review.md) — все 4 обязательные правки выполнены: №1 pre-prompt уведомлений перенесён на экран дня баннером (TeacherHomeScreen.kt:243-276, только при !loading, POST_NOTIFICATIONS в онбординге отсутствует — grep подтверждает); №2 первая карточка = тапнутая; №3 утренняя плашка свежести; №4 «На этот день пар нет» для ЗАВТРА (TeacherDayLoader.kt:267).

## 2. Критические пути данных

- **Правила override-оверлея идентичны** в loadDay (TeacherDayLogic.applyOverrides:146-162) и виджетах (TeacherWidgetLogic.applyOverrides:56-75): NO_LESSON→удаление, OTHER_ROOM→замена кабинета (blank→fallback/игнор), SOMEONE_ELSE→isReplacement/replaced. Виджет правку показывает (§5.4 ✅) — при условии минутного тика WidgetAlarm (BellCountdownWidgetProvider.kt:520, hasWork включает teacher-виджеты :455-456).
- **Diff-сброс по-полевой** ✅ (см. 1б), «сбросить день» нигде нет; deleteForDate в DAO существует, но не вызывается из diff-пути.
- **Прун overrides** ✅ Calendar: TeacherDayLoader.overridesToPrune:168-186 — parseDotDate + cutoff через Calendar.add, неразобранную дату не удаляет.
- **Мина (блокёр 1)**: воркер — основной синк — идёт мимо diff-обёртки.

## 3. Воркер (TeacherScheduleWorker + MpkWorkManagerHelper.setupTeacherSync:43-60)

- Окна ✅: shouldTeacherSync:16-26 — 06:30-07:15 MORNING_FORCE (45-мин окно гарантирует ≥1 тик PeriodicWork 15 мин), 10:00-21:00 PERIODIC, вс SKIP, сб как будень; покрыто TeacherScheduleWorkerTest (7 тестов, границы включительно).
- force ✅: только MORNING_FORCE (обе цели) + index==0 && hour 14..17 (:61) — «сегодня» в 14-18, прочие тики кэш-первым.
- Профиль null → `Result.success()` молча ✅ (:34). lastSyncEpoch пишется при anyOk ✅ (:70-76). Widget-обновление после синка ✅ (:78). NetworkType.CONNECTED ✅. Падение нотификатора не рвёт синк (runCatching :83-92).
- ⚠️ Отклонения: (а) пропуск утреннего окна → сводка уходит первым тиком ≥10:00 (shouldSendSummary без верхней границы) — поздно, но честно; (б) «повтор на 15-мин тиках до 07:15» реализован как Result.retry() с дефолтным backoff, а не явные 15 мин; (в) force-окно 14..17 не включает 18:00 ровно (косметика).

## 4. Уведомления (TeacherNotifier + Logic)

- Дедуп-ключи на все 4 типа ✅: `summary_sent_<date>` (:179,195), `diff_notified_<today>_<hash>` (:213-217), `tomorrow_published_<date>` (:225-229), `lesson_reminded_<date>_<n>` (:305-311).
- Quiet hours ✅: quietHours 21:00-07:00 (Logic:20); diff ночью пропускается без pending (утренний before-снапшот поймает, :208-219); «на завтра опубликовано» в quiet → pending-флаг до 07:00 (:223-235, flushPendingPublished:243-259); напоминание <07:00 сдвигается на 07:00 (:278).
- ЗАМЕНА не напоминает ✅: reminderCandidates фильтрует isReplacement (Logic:177-180); только первая пара или смена кабинета; настройка 5/10/20 (TeacherMoreScreen:212-227).
- PendingIntent: неймспейс 7_000_00x ✅, FLAG_IMMUTABLE везде (:321,341). Коллизий нет: grep 5_000_000/6_000_000/7_000_0 вне teacher-файлов — пусто; студенческие коды: 2026, 1M/2M/3M/4M, 4002 — не пересекаются (комментарий TeacherNotifier:53 неточен: студенческие виджеты на 4M, не 5M — на коллизии не влияет).
- ≤1 diff-уведомление на синк, дни склеены ✅ (diffMessages + один ID_CHANGES).

## 5. Виджеты

- Кэш-ключ: дата+минута+профиль+**overrideCount** (TeacherWidgetLogic.cacheKey:105-110). ⚠️ счётчик — слабый прокси: правка 241→305 (count 1→1) в ту же минуту вернёт stale-снапшот; лечится минутным тиком (≤60-120 c). Для QA-гейта «Viber сказал 241 → виджет изменился» — проходить с ожиданием минуты. Ключ без surnameKey (name достаточно) и без pref'ов уведомлений (не влияют на контент).
- matchRowToTeacher-фильтр применён ✅: TeacherWidgetData.matchedRows:151-164 — однофамильцы не сливаются.
- runBlocking в onReceive НЕТ ✅: оба провайдера goAsync + widgetScope(Dispatchers.IO, WidgetUpdateHelper.kt:27); GG8-паттерн (падение одного виджета не рвёт второй, TeacherWidgetData:46-49); будильник перевзводится в finally.
- Состояния ✅: ИДЁТ/ДАЛЬШЕ/«Сегодня пар нет»+следующая/«Пары закончились»/«Выходной»/«Выберите себя», ЗАМЕНА-бейдж (Today:144-153), кабинет chip-крупно, «обновлено в», 4×1-компакт по minHeight, колокольчик-AVD сохранён.

## 6. Миграция/БД

✅ всё: version=10 + exportSchema (MpkDatabase.kt:42-43), migration9To10 (:251-266) в addMigrations (:283), app/schemas/.../10.json на месте (23.7K), unique index (dateString, lessonNumber) в entity (:9) и миграции (:262), тесты V1→V10 цепочкой. fallbackToDestructiveMigration — осознанно, задокументирован (:268-273).

## 7. FLAVOR-чистота

- BellsScreen не тронут teacher-волнами ✅ (git log: последние коммиты GG16/GG12 — до teacher). TeacherBellsPage — новая страница в TeacherMoreScreen.
- CollegeScreen: правки только T0 (общий surnameKey-фикс — разрешён §3.4, «уходит в студенческий релиз независимо») и T2-A (перенос TeacherScheduleTab в loader, «студенческое поведение сохранено», E2E поправлены) ✅.
- Манифест: teacher-виджеты и TeacherReminderReceiver зарегистрированы ТОЛЬКО в src/teacher/AndroidManifest.xml ✅.
- ⚠️ Обратная утечка: студенческие виджет-провайдеры зарегистрированы в main-манифесте (:41-90) → видны в teacher-APK в picker'е. См. мину №1 ниже.

## 8. Онбординг

✅ POST_NOTIFICATIONS не запрашивается до экрана дня (единственный запрос — баннер на HomeScreen:256, после отрисовки расписания, одноразовый флаг в prefs). Договор дословно ✅. «Пропустить» на каждом шаге ✅. mailto «не нашли себя» ✅. Чип «добавьте виджет» ✅. ⚠️ косметика: карточка подтверждения не показывает кабинет (§6.2 обещает ФИО+должность+предметы+кабинет).

## 9. Пробелы §4.1 (19 фич)

| Фича | Статус | Серьёзность |
|---|---|---|
| 1-8, 10-13, 16-18 | ✅ реализованы (оговорки в разделах выше) | — |
| 8 (48ч Xiaomi+Samsung) | гейт НЕ выполнен | **БЛОКЕР релиза** (§9.6: иначе сдвигаем) |
| 9 «Нагрузка» | неделя есть, **месяц — нет** (aggregateLoad только по weekDates) | можно-ship |
| 14 честная деградация | выходной ✅, «открыть на сайте» ✅; **каникулы (с датой)/сессия/практика — нет детекции**, будет «Сегодня пар нет» | можно-ship (честно, но не по спеке) |
| 17 свайп между вкладками | не добавлен (TeacherNav.kt:57 «осознанное упрощение») — прямое противоречие §6.1 | можно-ship, зафиксировать в DECISIONS |
| 19 первый запуск <60 сек | ручной промер 50+ не выполнен | гейт релиза |
| §5.3 настройка времени сводки 07:00-08:00 | нет (только вкл/выкл) | косметика |
| §6.1 TopBar: тап по ФИО → смена профиля; pull-to-refresh | нет (смена только из «Ещё»; sync только кнопкой) | косметика |
| §6.1 раздел «Виджеты» в «Ещё» | нет | косметика |
| Имя файла `mpk-teacher-x.y.apk` | нет авто-rename | чеклист T5 |

## 10. Мины

1. **⚠️ СРЕДНЯЯ — краш teacher-сборки через студенческий виджет**: студенческие виджеты зарегистрированы в teacher-APK; тап по чипу кабинета шлёт EXTRA_ROOM → MainActivity.handleIntent (:99-108) → `navController.navigate(...)` на контроллере БЕЗ графа (в teacher-ветке NavHost не устанавливается) → IllegalStateException. Дешёвый фикс: в handleIntent ранний return при FLAVOR=="teacher" (гейт уже разрешён в MainActivity) или guard `navController.currentBackStackEntry != null`. Вероятность низкая (надо добавить студенческий виджет), но для аудитории «не прощает багов» — исправить.
2. ⚠️ НИЗКАЯ — receiver-корутин в `widgetScope` без CoroutineExceptionHandler: необработанное исключение в TeacherReminderReceiver.checkReminderNow / widget buildViews → краш процесса. runCatching стоит вокруг сборки виджетов, но не вокруг checkReminderNow. Риск мал (БД только кэш, destructive-fallback).
3. ⚠️ НИЗКАЯ — смена настройки «за N мин» не перевзводит будильник до следующего тика воркера (до 15 мин в окне / до 10:00 вне окна).
4. ✅ Гонки prefs: SharedPreferences thread-safe, apply() асинхронный; единственный writer lastSyncEpoch — атомарен. Статик-кэш TeacherWidgetData: гонка «два вычисления» доброкачественная.
5. ✅ Утечки: LaunchedEffect-тикер 30 c (HomeScreen:136-141) снимается с композицией при уходе с вкладки; viewModelScope/job-отмена в Week (loadJob?.cancel + ensureActive).
6. ✅ АНР: вся БД/сеть через Dispatchers.IO (loadDay withContext(IO), widgetScope IO, goAsync в receiver'ах); на main — только prefs-чтения.
7. ✅ Краш-петли: воркер при ошибке сети → retry без уведомлений; профиль null → success; reminder-будильник после reboot восстанавливается тиком воркера (задокументировано, TeacherNotifier:437-438).

---

## БЛОКЕРЫ (3)

1. **КОД: полевой diff-сброс override не выполняется на пути воркера** (TeacherScheduleWorker.kt:63, TeacherWeekScreen.kt:138 идут мимо syncDayWithOverrideDiff) — нарушение жёсткого условия §5.4 и долга чести §4.2.1б на основном синк-пути: весь день (06:30-21:00) изменения дока НЕ сбрасывают устаревшие правки и НЕ уведомляют. Фикс мал: в воркере для каждой даты вызывать syncDayWithOverrideDiff (снапшоты before у него уже есть) и при resets — уведомление/плашку; в Week.refresh — та же обёртка. Заодно решить §4.2.1б «уведомление о сбросе — всегда»: сейчас только in-app плашка (TeacherDayViewModel:118-126), системного уведомления нет.
2. **ГЕЙТ: промер окна публикации дока (долг чести №9) не выполнен** — все тайминги §5.3 остаются [ASSUMPTION]. 5-7 вечеров curl ДО релиза.
3. **ГЕЙТ: T5 не выполнен** (MASTER-PLAN:885): testTeacherDebugUnitTest+standard прогон, roborazzi teacher+vision-аудит, unzip-гейт students.csv=0 в teacher-APK, LiveDocTest/curl в день рассылки, 48ч виджеты Xiaomi+Samsung, онбординг <60 c руками 50+, assembleTeacherRelease+StandardRelease (паровоз), APK в releases/. **Дополнить чеклист dexdump-гейтом** (долг чести №10 отсутствует в T5) и именем файла `mpk-teacher-x.y.apk` (№11).

## Высокие (не блокеры)

- Уведомление о сбросе правки — только in-app плашка, без системного уведомления (§5.4 «уведомление — всегда»); при сбросе в фоне пользователь не узнает. (Войдёт в фикс блокера 1.)
- Краш-мина EXTRA_ROOM в teacher-сборке (мины №1).

## Средние/косметика (можно ship, внести в v1.1/DECISIONS)

Нагрузка-месяц; каникулы/сессия без детекции; свайп вкладок; настройка времени сводки; тап по ФИО в TopBar; pull-to-refresh; раздел «Виджеты» в «Ещё»; кабинет в карточке онбординга; кэш-ключ виджетов по overrideCount (staleness ≤1 мин); сводка при пропуске утра уходит ~10:00; неточный комментарий о PI-неймспейсах (TeacherNotifier:53).

## Итог по чек-листу

1: ✅×10, ⚠️×1 (п.9 промер ❌-до-релиза, п.10 dexdump ⚠️, п.1б ❌ на пути воркера) · 2: ✅ (оверлей идентичен, diff по-полевой, пруn Calendar) кроме воркер-пути · 3: ✅ · 4: ✅ · 5: ✅ (кроме слабого overrideCount) · 6: ✅ · 7: ✅ (утечка студенческих виджетов в teacher-APK ⚠️) · 8: ✅ · 9: список выше · 10: 1 средняя мина, 2 низких.

**Вердикт: ship с правками.** После блокера 1 (фикс ~1 день: обёртка в воркере + Week + системное уведомление о сбросе) и выполнения гейтов 2-3 — релизить.
