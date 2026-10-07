# 09 — ДЕБАТЫ, КРИТИК-2: техника / дистрибуция / privacy / QA

Фаза 2 консилиума, 2026-10-07. Автор: КРИТИК-2 (adversarial reviewer).
Метод: каждое техническое утверждение бумаг 01–07 проверено по коду `D:\MPK app\MPK-app-2.1` (чтение/grep, gradle не запускался). Метки: **CONFIRMED** / **PLAUSIBLE** / **REFUTED** + [VERIFIED: файл] или [ASSUMPTION].

---

## 1. Атака на архитектуру (бумага 05)

### (а) Существует ли отдельный док преподавателей и его URL-паттерн

**CONFIRMED.** Полный пайплайн живёт в общем коде:
- `fetchTeacherSchedule` качает док с `nameBase = "raspisanie-prepodavatelej"`, слаги `""` и `"-2"` [VERIFIED: MpkNetworkClient.kt:353, :129].
- Кандидаты URL — тот же `forEachDocumentUrl`: (месяц даты + предыдущий) × суффиксы × .doc/.docx → event-страницы `/raspisanie/DD-MM-YYYY-…-raspisanie-prepodavatelej(-2)/` [VERIFIED: MpkNetworkClient.kt:103, :129-133].
- Гейт даты в шапке: `matchesRequestedDate`, ошибка «Документ за X вместо Y» [VERIFIED: MpkNetworkClient.kt:368-379] — утверждение 05 §3.3 **CONFIRMED**.
- Парсер: таблицы `║/│`, `GROUP_REGEX ^([1-4])([1-9])([А-Я])$`, `ROOM_REGEX` [VERIFIED: TeacherScheduleParser.kt:17-25, 108-132]. Утверждения 01 §1 и 05 §2 **CONFIRMED**.

Замечание критика: слаг `-2` у дока преподавателей означает, что при коллизии загрузок WordPress перебираются оба варианта — регрессия URL от 01.10.2026 (доказанный root cause из ревью) уже покрыта общим резолвером. Отдельного «ScheduleUrlResolver» как класса НЕТ — логика внутри `MpkNetworkClient`; бумага 07 B6.8 называет его как сущность — **PLAUSIBLE** (по сути верно, по имени нет).

### (б) Правда ли, что teacher_lessons не заполняется текущим воркером

**CONFIRMED.** `ScheduleCheckWorker` → `repository.syncScheduleForDate(groupName, …)` [VERIFIED: ScheduleCheckWorker.kt:76]; тело `syncScheduleForDate` (ScheduleRepository.kt:503-535) НЕ вызывает `syncTeacherScheduleForDate`. Кэш преподавателей наполняется только из:
1. `syncScheduleDetailed` — ученический полный синк [VERIFIED: ScheduleRepository.kt:463];
2. UI преподавателя по требованию [VERIFIED: CollegeScreen.kt:1526 sync = syncTeacherScheduleForDate].

Следствие, которое архитектор НЕ проговорил: **в teacher-сборке без нового воркера кэш устаревает между открытиями приложения** — «кэш-первым» (`loadTeacherDay`, CollegeScreen.kt:1442) при непустом кэше сеть не трогает вообще. Ученик триггерит `syncScheduleDetailed` каждым pull-to-refresh, преподаватель — никогда. TeacherScheduleWorker — не «волна 2», а часть MVP: без него приложение второй категории свежести.

### (в) Оценка «MVP 1–1.5 дня» — моя оценка по пунктам

Архитектор считает «MVP = волны 0–1» (перенос существующего таба + онбординг). Даже в этом объёме оценка занижена; а MVP в понимании бумаг 01/04/07 (виджет + уведомление + «сегодня со статусами») — это волны 0–2+. Мои оценки (агентная разработка, волны по 2–3 агента, компиляция после волны):

| Пункт | Оценка 05 | Моя оценка | Обоснование |
|---|---|---|---|
| Flavor-конфиг + res + иконка/имя + ветка MainActivity + TeacherRoot-заглушка | 0.5 д | **0.5 д** | прецедент admin полный [VERIFIED: build.gradle.kts:18-29, ls app/src/admin] |
| Экран «Сегодня/Завтра» (перенос TeacherScheduleTab ~260 строк [VERIFIED: CollegeScreen.kt:1468-1730]) + вынос TeacherDayLoader | 1 д (волна 1A) | **1–1.5 д** | перенос механический, но UX-бумага 04 §3 требует НОВОГО: статусы ИДЁТ/СЛЕДУЮЩАЯ/ПРОШЛА, автопрокрутка, плейсхолдер «пар нет + следующая пара», метка «обновлено в HH:MM» — этого в текущем табе нет |
| Онбординг «выбери себя» + TeacherProfileStore (prefs) | 0.5 д (волна 1B) | **0.5 д** | TeachersPage + FuzzySearch существуют [VERIFIED: KnownTeachers.kt, TeachersRepository.kt] |
| Фикс однофамильцев + тест-фикстура | «1 строка» | **0.5 д** | строка-то одна (см. §2), но нужен тест на коллизию + UX ветка «не удалось точно определить» (07 B6.3) |
| **MVP (демо-способное: сегодня/завтра/календарь + выбор себя)** | **1–1.5 д** | **2–2.5 д** | |
| Экран «Неделя» (полоса дат + бейджи + синк пн–сб = 6 доков, частичные отказы) | 1.5 д (в волне 3B) | **1–1.5 д** | syncTeacherScheduleForDate умеет любую дату [VERIFIED: ScheduleRepository.kt:577]; но 6 последовательных фетчей + агрегат + состояния «3 дня из 6 скачались» |
| TeacherScheduleWorker + уведомление «опубликовано» | 1.5 д (волна 2A) | **1 д** | паттерн ScheduleCheckWorker + nextDayCandidates + staleMarkKeys копируется [VERIFIED: ScheduleCheckWorker.kt:35-81, 141-154; MpkWorkManagerHelper.kt:15-41] |
| Override-таблица + миграция v9→v10 + оверлей + bottom-sheet UI + автосброс при официальном изменении (04 §8.4) | 1.5 д (волна 3A) | **1.5–2 д** | миграция+dao дёшевы, но diff-логика «официальный док изменил пару → сбросить правку + тихое уведомление» — новая механика, которой нет нигде в коде |
| Виджеты (3 провайдера на teacher_lessons + 3 layout) | 1.5 д (волна 2C, вместе с NavHost) | **1.5–2 д + живое устройство** | все три group-driven [VERIFIED: NowNextWidgetProvider.kt:97,233; TodayScheduleWidgetProvider.kt:60; BellCountdownWidgetProvider.kt:198-199]; виджеты — исторически самая хрупкая зона проекта (proguard-keep, OEM-батарея) |
| NavHost teacher (Расписание/Звонки/Карта/Профиль) + адаптация BellsScreen | (волна 2B) | **1 д** | |
| Privacy-разделение teachers.csv (см. §3) | НЕ учтено | **0.5–1 д** | архитектор эту работу вообще не оценил |
| Релиз: гейты, 2 flavor-прогона тестов, девайс-smoke, Play/Viber-метаданные | 0.5–1 д | **1 д** | |
| **Полное приложение (v1 по бумагам 01/04)** | **5–6 д** | **8–10 рабочих дней** | |

**Вердикт по (в): оценка 05 REFUTED как заниженная примерно вдвое на «полном варианте» и в 1.5 раза на MVP.** Не катастрофа — порядок тот же (недели, не месяцы), — но план «за неделю реально» (05 §7) без privacy-работы, статусов UX-бумаги и 48-часового девайс-теста виджетов нереалистичен. Честный ориентир заказчику: демо за 2–3 дня, релиз-кандидат за 8–10 рабочих дней + 2 календарных дня на 48ч тест виджетов.

### (г) Общая Room-база на все flavor — мина или ок?

**ОК при соблюдении конвенции, мина при нарушении — и она уже заряжена.** Факты: version 9, `fallbackToDestructiveMigration()` включён СОЗНАТЕЛЬНО [VERIFIED: MpkDatabase.kt:39, 247-263], забытая миграция = тихое стирание student_tasks у учеников. `teacher_overrides` bump'ит версию и для ученического APK (таблица там мёртвым грузом — безвредно). Отдельная БД на flavor была бы хуже (две линии миграций). Митигации из 05 (MigrationV9ToV10Test + schemaLocation [VERIFIED: build.gradle.kts ksp arg]) достаточны, НО: **CI не запускает тесты вообще** — `build-apk.yml` делает только `assembleDebug` [VERIFIED: .github/workflows/build-apk.yml:40]. Конвенция «тест после каждой волны» держится на дисциплине одного разработчика. Утверждение 05 §6 «CI-гейты testTeacherDebugUnitTest» — **REFUTED как описание текущего состояния** (это пожелание, не факт): сегодня нет CI-гейта даже для standard.

Отдельная мина, которую 05 не заметила: на одном устройстве оба приложения имеют БД с одинаковым именем `mpk_schedule.db`, но разные applicationId → разные dataDir → конфликта нет [VERIFIED: build.gradle.kts applicationIdSuffix; DATABASE_NAME MpkDatabase.kt:57]. **CONFIRMED безопасно.**

Ещё: R8-вырезание ученических веток по `BuildConfig.FLAVOR`-константе — **PLAUSIBLE** [ASSUMPTION, стандартное поведение R8], но проверять нужно не верой, а `dexdump teacher-release.apk | grep StudentsRepository` в релиз-гейте. Даже если код останется — данные-гейт (нет students.csv → emptyList [VERIFIED: StudentsRepository.kt:25-35]) держит.

Правило «ветвление ТОЛЬКО в MainActivity» — единственно верное. Сегодня FLAVOR-гейтов ровно 2 [VERIFIED: CollegeScreen.kt:233, SettingsScreen.kt:721], точка setup-воркеров в MainActivity есть [VERIFIED: MainActivity.kt:48-53].

---

## 2. Баг surnameKey (однофамильцы)

**CONFIRMED, и это баг ОБЕИХ приложений, а не только нового.**

Цепочка, проверенная по коду:
1. В teachers.csv реально 2 коллизии: `еременко`, `савицкая` [VERIFIED: awk uniq -d по app/src/main/assets/teachers.csv, 100 строк].
2. Запись: `teacherRows` кладёт `surnameKey = surnameOf(rawName)` и `teacherName = rawName.trim()` («Фамилия И.О.» сырьём) [VERIFIED: ScheduleRepository.kt:208-235].
3. Чтение: `TeacherLessonDao.getForSurnameOnDate` фильтрует ТОЛЬКО `surnameKey + dateString` [VERIFIED: TeacherLessonDao.kt:13-23].
4. **Единственный call-site чтения во всём main-коде** — `CollegeScreen.kt:1514 getTeacherLessonsForDate(surname, dateKey)`, и там НЕТ фильтра `matchTeacher` [VERIFIED: grep getTeacherLessonsForDate — один результат вне Dao/Repository]. CollegeScreen — main source set → ученическое standard-приложение сегодня показывает карточку любой Савицкой со слитыми парами обеих.
5. `matchTeacher` при >1 кандидате сравнивает инициалы [VERIFIED: TeacherInsights.kt:125-156] — но применяется только к фактам из групповых lessons, к teacher_lessons не применяется никогда.

**Фикс «matchTeacher при чтении» — ПОДТВЕРЖДАЮ, это root-cause фикс:** один call-site, `teacherName` хранит сырое ФИО с инициалами, сравнение `matchTeacher(row.teacherName) == selectedTeacher` — 1 строка + импорт. Регрессия в студенческом приложении минимальна, потому что путь ОДИН: фикс автоматически чинит и student, и teacher.

Реальные edge-cases, которые фикс ОБЯЗАН обработать (иначе регрессия):
- **Строка без инициалов** («Савицкая» голой фамилией в доке): `matchTeacher` вернёт null при коллизии [VERIFIED: TeacherInsights.kt:137 — `if (initials.isEmpty()) return null`] → строка отброшится. Раньше показывалась (вперемешку). Правильное поведение — не молча дропать, а ветка «не удалось точно определить» (07 B6.3) или показ обеих с пометкой. Для НЕколлизийных фамилий `matchTeacher` возвращает единственного кандидата и без инициалов — ок.
- **Преподаватель в доке, которого нет в CSV** (приглашённый/совместитель): в teacher-приложении пользователь выбирает себя из CSV, такие строки ему и не нужны — отброс корректен.
- **Ячейка «Иванов/Петров»**: `teacherRows` НЕ сплитит по «/» (в отличие от `splitTeacherNames` [VERIFIED: TeacherInsights.kt:162-165]) — слот достаётся только первой фамилии, и `surnameOf` от «Иванов/Петров» даст «иванов/петров»… точнее first token «иванов/петров».trim('/') — поведение надо проверить фикстурой; утверждение 05 §3.4 **CONFIRMED по коду** (сплита нет), риск на живых доках не проверен [ASSUMPTION].

**Вердикт:** баг существующий, обоих приложений, критичный для teacher-продукта (пользователь «выбирает себя» → чужие пары под своим именем = мгновенная потеря доверия). Фикс дешёв (0.5 дня с тестом на фикстуре «Савицкая С.И. / Савицкая Н.П.»), делать ДО любых волн — он независим от flavor-решения и чинит студенческое приложение уже сейчас.

---

## 3. Privacy-гейт (бумага 07 §9)

**Факт CONFIRMED, но предложенная схема «перенести teachers.csv во flavor source sets» — REFUTED как написана: она ломает студенческое приложение.**

Проверенные факты:
- `teachers.csv` (100 строк, колонки: ФИО;должность;предметы;кабинет;**телефон;email**;фото;кафедра;стаж;категория) лежит в `app/src/main/assets/` [VERIFIED: ls, head -1].
- `teachers_photos/` — **99 jpg, 2.1 МБ** — тоже в main assets [VERIFIED: ls | wc -l, du -sh]. Это ~40% release-APK (5.1 МБ [VERIFIED: ls D:\MPK app\releases]).
- Студенческое standard-приложение АКТИВНО использует всё это: `TeachersRepository.parseCsv(assets.open("teachers.csv"))` [VERIFIED: TeachersRepository.kt:28]; телефон и email **показываются в UI** [VERIFIED: CollegeScreen.kt:1372-1373]; фото грузятся из assets [VERIFIED: CollegeScreen.kt:927, 1267]; `TeachersCache/rememberTeachers/rememberIsTeacherKnown` используются кликабельными чипами преподавателей в расписаниях групп [VERIFIED: KnownTeachers.kt:26-77 + комментарий R2#8].
- Вывод: перенос teachers.csv из main в teacher/admin source sets оставит стандартное приложение без чипов преподавателей, карточек и поиска — **прямая регрессия v2.5**.

При этом главный privacy-факт, который бумаги занижают: **контакты преподавателей УЖЕ сегодня shipped каждому студенту** (standard-сборка, телефон+email+фото, без всякого teacher-приложения). Бумага 01 §5.5 это признаёт («teacher-сборка новых данных не добавляет») — **CONFIRMED**. Teacher-приложение не создаёт утечку, оно её масштабирует (аудитория 45–60 чувствительнее к «мой мобильный в студенческой аппке»).

**Безопасная схема (моя, минимальный diff):**
1. **Один файл, а не два.** В `main/assets/teachers.csv` колонки phone/email заполняются пустыми; полный вариант НЕ дублируется во flavor (два одноимённых CSV в main+teacher = рассинхрон при первом же обновлении каталога — мина похуже общей БД).
2. Контакты — отдельный файл `teachers_contacts.csv` (surnameKey;phone;email) в `src/teacher/assets` и `src/admin/assets`. `TeachersRepository` читает его опционально (try/catch → пустые контакты) — тот же паттерн данных-гейта, что у StudentsRepository [VERIFIED: StudentsRepository.kt:25-35].
3. **Student standard: контакты убрать** (это и есть реальное закрытие риска 99-З, а не косметика «teacher видит, student нет» — сегодня student видит всё). Вопрос «student — как сейчас?» — отвечаю: «как сейчас» оставлять нельзя, именно standard-сборка — самый широкий канал распространения. Решение принимается заказчиком, технически это 10 минут (пустые колонки).
4. **Фото**: flavor source sets НЕ умеют удалять main-assets (merge только добавляет/переопределяет). Чтобы teacher-APK был без фото (рекомендация 07 C9.1), `teachers_photos/` надо перенести из main в `standard`+`admin` (дубль 2.1 МБ на диске, в git — перенос) ИЛИ оставить фото во всех и принять риск. Полумера «переопределить 99 заглушками в teacher» — смех и грех. Цена честного переноса — 0.5 дня + регресс-тест CollegeScreen.
5. Гейты релиза: `unzip -l standard.apk | grep -c teachers_contacts` = 0; `unzip -l teacher.apk | grep -c students.csv` = 0; `unzip -l teacher.apk | grep -c teachers_photos` = 0 (если принято решение без фото).

Сложность всего разделения: **0.5–1 день** (файлы, source sets, опциональное чтение, гейты) — в оценку 05 не вошло.

---

## 4. Дистрибуция

**Два APK на одном устройстве (завуч с обеими, студент-преподаватель): конфликтов нет — CONFIRMED.**
- `applicationIdSuffix ".teacher"` → разные applicationId → разные песочницы: prefs, files, Room-файлы (имя `mpk_schedule.db` одинаковое, dataDir разные) [VERIFIED: build.gradle.kts:26; MpkDatabase.kt:57].
- Подпись: один release signingConfig на все flavor [VERIFIED: build.gradle.kts:51-71] → обе ставятся рядом, никаких INSTALL_FAILED_UPDATE_INCOMPATIBLE.
- Виджеты: провайдеры регистрируются на applicationId → два набора виджетов сосуществуют.
- Единственный реальный конфликт-на-устройство: **BellTimerService/звоночные уведомления задвоятся**, если пользователь включит bell-нотификатор в обоих приложениях (оба сервиса независимы [VERIFIED: MainActivity.kt:41-47]). Мелочь, но завуч с двумя APK получит двойной звонок — упомянуть в FAQ.

**Автообновление: его НЕТ ни у одного приложения — CONFIRMED.** В main-коде ноль ссылок на update-checker/GitHub-релизы [VERIFIED: grep UpdateChecker|checkForUpdate|releases/download|github.com по app/src/main/java — пусто]. Ученическое обновляется вручную: новый APK в `D:\MPK app\releases` → рассылка (Viber/облако); `GITHUB-HOSTING.md` — исследование про хостинг ДАННЫХ расписания, не APK. Бумага 01 §6 «in-app проверка версии» — это НОВАЯ работа (~0.5–1 д: GitHub Releases API latest + download + ACTION_VIEW на apk; read-only, «своего сервера» не требует), в оценках 05 её нет. Для v1 приемлемо обновление вручную при условии: `versionName` виден на экране «О программе» + имя файла `mpk-teacher-x.y.apk` (07 §1.6 — верно).

**Install-party на педсовете — реалистичность.**
- Хронометраж на человека: скачать 5 МБ из Viber (1 мин) + «неизвестные источники»/Play Protect на MIUI/One UI (1–3 мин, главный пожиратель времени) + первый запуск «найди себя» (1 мин) + виджет (1–2 мин, у 50+ промахиваются). Итого **3–6 мин на человека с одним помощником**.
- Пленум 100 человек × 15 минут «после доклада» с 2–3 волонтёрами = 10–15 установок, не 60. Цифра 07 §1 «60–70% с завучем» достижима только **двумя волнами**: педсовет (30–40% за 30–40 мин, если выделить полперемены) + догоняющая install-точка в учительской в следующие 2–3 дня + чат кафедры с закрепом. Вердикт: **PLAUSIBLE при поправке** — «15 минут» из 07 §1.2 это демо + первые 10–15 человек; реалистичный бюджет — ~2 часа живого присутствия разработчика/волонтёров суммарно. QR на стенде без живой помощи, как 07 верно отмечает, конвертирует в единицы.

---

## 5. QA-гейты релиза teacher-flavor

**Сначала главный пробел, который подтвердился.** Факты:
- 784 `@Test` в 109 файлах `app/src/test` [VERIFIED: find -exec grep -o "@Test" | wc -l = 784] + 2 в androidTest. Цифра «784 теста» из всех бумаг — **CONFIRMED**.
- В тестах НЕТ ни одной ссылки на `BuildConfig.FLAVOR` [VERIFIED: grep FLAVOR app/src/test — 0 результатов] → набор flavor-независим, AGP прогоняет его identically для каждой variant.
- НО конвенция проекта гоняет только `:app:testStandardDebugUnitTest`, и CI не гоняет тесты ВООБЩЕ [VERIFIED: build-apk.yml]. Значит: **teacher-вариант сегодня не тестируется ничем**. Unit-логика teacher-кода (если он в main source set и без FLAVOR-веток) покроется standard-прогоном автоматически; НЕ покроется: (1) flavor-ассеты и res-оверлей (иконка, app_name, отсутствие students.csv); (2) ветка `MainActivity if FLAVOR=="teacher"` — единственный код, который выполняется только в teacher-варианте; (3) R8-поведение teacher-release.

**Решение пробела (дешёвое, по лестнице):**
1. **Правило: ноль `BuildConfig.FLAVOR` в новом teacher-коде.** TeacherRoot/TeacherHomeScreen/Worker принимают параметры (`teacherMode: Boolean`, profile из prefs) — тогда вся логика тестируется в standard-прогоне существующими 784 + новыми teacher-тестами. Ветка FLAVOR остаётся одна (MainActivity) и она тривиальна.
2. В релиз-чеклист (не в каждую волну — дорого): `:app:testTeacherDebugUnitTest` (те же 784, несколько минут) + `assembleTeacherRelease` + unzip-гейты (§3.5, §1г) + установка на живое устройство.
3. Roborazzi-скриншоты teacher-экранов — record на teacherDebug variant (механика в проекте отлажена).
4. Отдельно (существующий долг, не teacher-специфичный): добавить в CI хотя бы `testStandardDebugUnitTest` — сегодня green-гейт держится на честном слове.

**Обязательные тесты релиза teacher (минимальный набор):**
| # | Тест | Статус сегодня |
|---|---|---|
| 1 | `MigrationV9ToV10Test` (Robolectric, реальная БД) | нет, обязательна по конвенции [VERIFIED: MpkDatabase.kt:250] |
| 2 | Парсинг дока преподавателей: фикстуры реальных .doc (существующие TeacherScheduleParserTest, TeacherScheduleCacheTest, TeacherScheduleE2ETest, TeacherScheduleDayPickTest, LiveDocTest) | есть [VERIFIED: ls app/src/test — 8 Teacher*-файлов]; расширить форматами сессии/практики (07 B6.6) |
| 3 | surnameKey-коллизия: две «Савицкая С.И./Н.П.» + строка без инициалов + «Иванов/Петров» | НЕТ — добавить с фиксом §2 |
| 4 | APK-гейты: students.csv=0 в teacher; teachers_contacts/photos по принятой схеме | скрипт есть для standard (конвенция), расширить |
| 5 | TeacherScheduleWorker: выбор дат (nextDayCandidates), дедуп-метки, вс=404→пн | частично есть (DayPickTest, defaultTeacherDayOffset [VERIFIED: CollegeScreen.kt:1419-1421]) |
| 6 | Виджеты: unit на teacher-источник данных + **руками 48ч Xiaomi+Samsung** (OEM battery-killer — известные грабли проекта) | НЕТ; только руками (07 B7 — верно, не автоматизируем) |
| 7 | Живой smoke curl-аналог: док преподавателей за сегодня/завтра качается и парсится с реального guo-mpk.by | LiveDocTest существует — запускать перед рассылкой ОБЯЗАТЕЛЬНО |
| 8 | Кэш-гейт teacher_lessons (getPresentDates/force) по образцу ScheduleCacheGateTest | частично (TeacherScheduleCacheTest) |

Утверждение 05 «784 теста прогоняются и для teacher бесплатно» — **CONFIRMED по механике AGP, REFUTED по практике** (никто их сегодня для teacher не запускает; «бесплатно» ≠ «автоматически»).

---

## 6. Стоимость поддержки двух приложений одной кодовой базой

Честная оценка (flavor-модель, не форк):
- **Код-фиксы: ×1.0.** Парсеры, URL-резолвер, Room, тема, карта — одна копия; баг 01.10 (URL) чинился бы один раз для всех. Это главный аргумент 05 и 06 (§3 steelman «инкремент 10–20%») — **CONFIRMED кодом: общего кода ~всё, teacher-специфичного будет <2k строк**.
- **Тесты: ×1.0 в повседневке, ×2 только в релиз** (standardDebug на каждой волне + teacherDebug перед релизом). Набор flavor-независим (§5).
- **Релизы: ×1.5–2.** Два APK собрать/подписать/проверить гейтами/разослать + два changelog. Ручная логистика APK — самая дорогая статья, и она ×2 навсегда (автообновления нет).
- **Фичи: ×1.2–1.4** (оценка 06 §3 — PLAUSIBLE, согласен): каждый общий экран надо мысленно прогонять в двух контекстах.

**Что сломается первым (по вероятности):**
1. **Парсер дока преподавателей при дрейфе вёрстки сайта** — ломает ОБА приложения одновременно, в пятницу вечером (06 §1.9 — верный риск; митигация — LiveDocTest перед каждым релизом + честная деградация).
2. **Дисциплина одной точки ветвления** (05 риск 5): третий `if FLAVOR` в общем экране — и flavor-матрица начинает требовать полного тестирования обоих на каждый чих. Лечится code-review-чеком.
3. **Виджеты после teacher-адаптации** на OEM-батареях — исторически самая хрупкая зона (convention SKILL.md про пересоздание виджетов после смены пакета — прецедент).
4. **Миграционная дисциплина v10+**: общая БД означает, что teacher-фича может стереть student_tasks у учеников — цена ошибки максимальная, вероятность низкая (конвенция + тесты).
5. **Рассинхрон версий**: если teacher-релиз выйдет без simultaneous standard-релиза при общем фиксе парсера — два приложения на одной кафедре показывают разное (06 §3 divergence — CONFIRMED как архитектурное свойство; правило: релиз всегда паровозом, versionCode общий).

Итого: налог реальный, но ~×1.3 на релиз, а не ×2 — при flavor-модели. Форк (вариант B) — ×2+ навсегда, отклоняю вместе с 05/06.

---

## 7. ИТОГОВЫЙ ВЕРДИКТ КРИТИКА-2

**Форма поставки: flavor «teacher» (вариант A) — технически правильное решение, поддерживаю.** Вариант C (режим) дешевле на старте (2–3 дня), но нарушает требование «отдельное приложение» и создаёт обратную миграцию UX; B (форк) — отсекается стоимостью. Аргументация 05 выдерживает проверку кодом по всем ключевым пунктам, кроме оценочных.

**Поправки к плану (обязательные):**
1. Оценки: MVP 2–2.5 дня, релиз-кандидат 8–10 рабочих дней (не 1–1.5 / 5–6).
2. Фикс surnameKey (§2) сделать СЕЙЧАС, до flavor-работ — он чинит живое студенческое приложение.
3. TeacherScheduleWorker — в MVP, не в волну 2 (иначе кэш-первым = вечно вчерашние данные).
4. Privacy-разделение teachers.csv по схеме §3 (один main-файл без контактов + contacts-файл во flavor) — 0.5–1 день, в план 05 не включено.
5. CI-гейт тестов отсутствует вообще — минимум `testStandardDebugUnitTest` в build-apk.yml, `testTeacherDebugUnitTest` в релиз-чеклист.

**Топ-5 технических условий, при которых НЕ делать:**
1. **Нет гарантии прогона MigrationV9ToV10Test и тестов на ОБОИХ flavor перед релизом** — общая БД + fallbackToDestructiveMigration превратят забытую миграцию в тихое уничтожение ДЗ студентов.
2. **Privacy-схема требует двух одноимённых teachers.csv (main + flavor-override)** — рассинхрон каталога гарантирован; без схемы §3 (один файл + contacts-файл) контакты не трогать вообще.
3. **В новом teacher-коде появляется `BuildConfig.FLAVOR` где-то кроме MainActivity** — вторая ветка значит, что тест-пробел (§5) непокрываем, а общий код начал гнить.
4. **Живой smoke дока преподавателей (LiveDocTest/curl на guo-mpk.by) не зелёный в день install-party** — не рассылать: первое «приложение врёт» у аудитории 02/03 неисправимо.
5. **Виджеты не пережили 48ч на Xiaomi/Samsung без зарядки** — релизить БЕЗ виджетов (мёртвый виджет хуже его отсутствия, 07 B6 верно); виджет — обещание №1 всей продуктовой позиции 01, невыполненное обещание = удаление.

Бонус-условие (организационное, но технически значимое): релизы обоих приложений — всегда паровозом, одним versionCode-поездом. Расхождение версий парсера между standard и teacher на одной кафедре — самый быстрый путь к «оба приложения врут по-разному».

---

## Приложение: сводная таблица проверок утверждений бумаг

| Утверждение (бумага) | Вердикт | Evidence |
|---|---|---|
| Отдельный док преподавателей, nameBase+слаги -2, гейт даты (05 §3) | CONFIRMED | MpkNetworkClient.kt:129, 353-387 |
| Воркер не заполняет teacher_lessons (05 §5) | CONFIRMED | ScheduleCheckWorker.kt:76 → ScheduleRepository.kt:503-535 |
| teacher_lessons заполняется из syncScheduleDetailed и UI (05 §5) | CONFIRMED | ScheduleRepository.kt:463; CollegeScreen.kt:1526 |
| 2 пары однофамильцев в teachers.csv (05 §3.1) | CONFIRMED | awk uniq -d: ерёменко, савицкая |
| Выборка по surnameKey сливает однофамильцев, matchTeacher не применяется (05 §3.1) | CONFIRMED | TeacherLessonDao.kt:13-23; CollegeScreen.kt:1514 (единственный call-site) |
| teacherName хранится сырьём → фикс при чтении возможен без миграции (05 §3.1) | CONFIRMED | ScheduleRepository.kt:220-221 |
| teacherRows не сплитит «Иванов/Петров» (05 §3.4) | CONFIRMED | ScheduleRepository.kt:208-235 vs TeacherInsights.kt:162-165 |
| Flavor admin: suffix+res-оверлей, манифест не нужен (05 §1) | CONFIRMED | build.gradle.kts:18-29; ls app/src/admin |
| FLAVOR-гейтов ровно 2 (05 §1) | CONFIRMED | CollegeScreen.kt:233; SettingsScreen.kt:721 |
| Единая подпись на все flavor (05 §6) | CONFIRMED | build.gradle.kts:51-71 |
| R8 вырежет ученические ветки в teacher (05 §1) | PLAUSIBLE | [ASSUMPTION] — проверять dexdump в гейте |
| 784 теста (все бумаги) | CONFIRMED | grep -o "@Test" по app/src/test = 784, 109 файлов |
| «784 прогоняются для teacher бесплатно» (05 §8) | CONFIRMED механикой / REFUTED практикой | тесты flavor-независимы (grep FLAVOR=0), но запускают только standardDebug; CI тестов не имеет (build-apk.yml) |
| teachers.csv в main/assets, перенос во flavor (07 §9.4) | факт CONFIRMED / схема REFUTED | TeachersRepository.kt:28; CollegeScreen.kt:927,1267,1372-1373; KnownTeachers.kt — standard-приложение сломается |
| Фото преподавателей в ассетах (07 §9) | CONFIRMED | teachers_photos/: 99 файлов, 2.1 МБ (40% APK) |
| APK ~5 МБ (07 §1) | CONFIRMED | releases/: 5.1–5.2 МБ |
| Документ не пишется на диск (07 §10) | CONFIRMED | downloadFileBytes → ByteArray in heap |
| Студенческое приложение уже показывает расписание преподавателя (06 §1.2) | CONFIRMED | CollegeScreen TeacherScheduleTab:1468+ |
| Виджеты group-driven, Scope.COLLEGE group-независим (05 §2) | CONFIRMED | NowNext:97,233; TodaySchedule:60; BellCountdown:198-199; BellCountdownNotifier:199,234 |
| Общая БД на flavor — разные песочницы при разных applicationId (05 §4) | CONFIRMED | applicationIdSuffix; DATABASE_NAME один, dataDir разные |
| Автообновление APK существует (01 §6 «in-app проверка») | REFUTED как текущее состояние | grep по main-коду: нет update-checker; обновление ручное |
| Tokens: HitTarget 44dp, ChipText 11sp, ButtonText 12sp (04 §9) | CONFIRMED | Tokens.kt:8,20,22 |
| fallbackToDestructiveMigration — сознательная ловушка (05 §4, SKILL) | CONFIRMED | MpkDatabase.kt:247-263 |
| MVP 1–1.5 дня, полное 5–6 дней (05 §7) | REFUTED (занижено ~×1.5–2) | детализация в §1в |
| Install-party «15 минут» покрывает 60–70% (07 §1) | PLAUSIBLE с поправкой | 3–6 мин/чел; нужна вторая волна |
| 33k LOC main (05) | не проверял | [ASSUMPTION автора 05] |

*Конец позиции 09.*
