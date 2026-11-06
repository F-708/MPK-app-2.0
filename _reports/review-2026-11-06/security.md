# Аудит безопасности и приватности — «Мой Политех» (com.politex)

- **Дата:** 2026-11-06
- **Объект:** `E:\MPK app\MPK-app-2.1` (ветка `v2.4-stable`, HEAD `415be8c`), флейворы `standard` / `admin` / `adminplus` / `teacher` + приватный sourceSet `src/owner`
- **Режим:** только статический анализ (чтение кода, git read-only, разбор уже собранных APK из `app/build/outputs/apk`). Код не изменялся, gradle/тесты не запускались, сетевых запросов к GitHub/колледжу не делалось.
- **Секреты:** реальные значения в отчёт не переносятся, маскируются (`MpK-0…7F`). Содержимое `keystore.properties` / `*.jks` не открывалось.
- **Сборки, на которых проверялись артефакты:** `app-standard-release.apk`, `app-admin-release.apk`, `app-adminplus-release.apk` (6,7 МБ, release, minify+shrink).

## Резюме

| Приоритет | Кол-во | Суть |
|---|---|---|
| **P0** | 3 | База ФИО учащихся в публичной git-ветке/истории; база открытым CSV внутри admin-APK; TOTP-секрет захардкожен и лежит в dex admin/adminplus |
| **P1** | 5 | adminplus без гейта доступа, но с PAT и публикацией; ПДн преподавателей публикуются в публичный репозиторий; облачный бэкап тащит БД с оценками и текстом документов; избыточный `REQUEST_INSTALL_PACKAGES` во всех флейворах; экспортированный `*/*`-alias для приёма файлов |
| **P2** | 5 | zip-bomb в docx; санитизация имён без разделителей пути; отсутствие pinning/NSC; отсутствие throttling на код доступа; CI без гейта по `students.csv` |
| **P3** | 4 | устаревшие зависимости, отсутствие user-auth binding у Keystore-ключа, тестовые рычаги в проде, гигиена секретов рядом с публичным репо |

**Главный вывод:** инженерные контроли в приложении сделаны заметно выше среднего (WebView изолирован, APK-обновления проверяются по подписи, PAT шифруется Keystore, логи чистые, ZIP-распаковка контент-пака защищена от zip-slip). Все P0/P1 — не в «коде приложения», а в **обращении с ПДн и в модели доверия к клиенту**: база учащихся мыслится как «защищённая кодом доступа», хотя она (а) лежит в открытом ассете APK, (б) была закоммичена в публичный репозиторий и до сих пор находится в вершине удалённой ветки.

---

## P0

### [P0] Полная база ФИО учащихся в публичной git-ветке и в истории репозитория

- **Где:** `app/src/admin/assets/students.csv` (blob `6bea4ef`, **357 717 байт**); в удалённой ветке `origin/v2.4-stable` (tip `cb3bb1f`) файл присутствует **в вершине**, не только в истории. Коммиты с файлом: `0d1d389`, `cb3bb1f`, `f8e5b21`, `6affb7d`; удаление из индекса сделано в `6affb7d` («…+ PII из индекса»), но **не запушено** (локальная ветка на 338 коммитов впереди remote), поэтому в публичной ветке файл всё ещё есть.
- **Сценарий атаки/утечки:** любой человек с доступом к репозиторию (remote `https://github.com/F-708/MPK-app-2.0`; публичность предполагается самим проектом — комментарий в `.gitignore:26-31` «репозиторий публичный», `GITHUB-HOSTING.md:577-581`) выполняет `git clone -b v2.4-stable` или `git log --all -- app/src/admin/assets/students.csv` и получает полный CSV: ФИО, группа, курс, специальность, основа обучения, общежитие, куратор, кабинет. ПДн несовершеннолетних, без признаков согласия, доступны бессрочно (git-история неизменяема без перезаписи). Даже после удаления файла из будущих коммитов он остаётся в старых.
- **Root cause:** файл коммитился до появления `.gitignore`; затем «удаление из индекса» сделали локально и не переписали историю; ветка с файлом осталась на remote. Гейт `unzip -l | grep students.csv` проверяет только APK и ничего не говорит о репозитории.
- **Фикс:** рассматривать как **инцидент с ПДн**, а не как баг сборки. 1) Немедленно: сделать репозиторий приватным либо удалить ветку `v2.4-stable` с remote. 2) `git filter-repo --path app/src/admin/assets/students.csv --path tools/students-original.csv --invert-paths` по всем веткам и тегам, force-push; запросить в GitHub purge кэшируемых представлений; проверить форки. 3) Уведомить колледж/ответственного за ПДн, оценить legal-последствия. 4) Профилактика: pre-commit hook + CI-job, который падает при `git ls-files | grep -E 'students.*\.csv'`, и синхронизация в публичный репо **только** по allow-list путей.
- **Тест/проверка:** `git log --all --oneline -- app/src/admin/assets/students.csv` → пусто; `git cat-file -e origin/main:app/src/admin/assets/students.csv` → fail; `git rev-list --objects --all | grep -i students` → пусто; CI-job «no-PII-in-git» зелёный.

### [P0] База учащихся лежит открытым CSV внутри admin/adminplus APK — код доступа её не защищает

- **Где:** `app/build/outputs/apk/admin/release/app-admin-release.apk` → `assets/students.csv` (проверено: запись есть, у standard — 0 записей); `app/src/admin/assets/students.csv`; чтение: `app/src/main/java/com/politex/data/repository/StudentsRepository.kt:25-35`.
- **Сценарий атаки/утечки:** admin-APK распространяется штучно (лежит в `E:\MPK app\releases\`, передаётся преподавателям/кураторам). Любой получатель (или тот, кому APK переслали в мессенджере) выполняет `unzip -p app-admin-release.apk assets/students.csv` и получает базу **без ввода кода доступа**, без root, без запуска приложения. TOTP-гейт (`AccessGateScreen`) защищает только навигацию по UI, а не данные: ассет — обычный zip-entry.
- **Root cause:** ПДн упакованы в клиентский артефакт, распространяемый вне контролируемого канала; единственный контроль — клиентская проверка кода в UI.
- **Фикс:** не поставлять полную базу ассетом. Варианты по убыванию: (а) отдавать выборку с сервера по авторизованному запросу; (б) шифровать CSV ключом, производным от пользовательского секрета (PBKDF2/Argon2 от кода, введённого владельцем), а не от константы в APK; (в) минимум — не публиковать и не пересылать admin-APK, выдавать по одному устройству и фиксировать получателей. Обязательно: сократить набор полей (общежитие/куратор/кабинет — избыточны для задачи «найти группу учащегося»).
- **Тест/проверка:** `unzip -l app-admin-release.apk | grep students.csv` → 0; попытка `strings`/`unzip -p` не даёт читаемого CSV; в приложении база грузится только после успешной авторизации (проверить на устройстве, что без кода список пуст и файла на диске нет).

### [P0] TOTP-секрет захардкожен в исходниках и присутствует в dex admin/adminplus APK

- **Где:** `app/src/owner/java/com/politex/owner/AccessCode.kt:16` — статическая строка-секрет (`MpK-0…7F`, UTF-8, 30-секундный шаг, HMAC-SHA256, окно ±1 шаг, код 6 цифр). Проверено: подстрока секрета находится в `classes*.dex` **admin** и **adminplus** release-APK (`standard` — отсутствует, изоляция флейворов по коду корректная). Секрет также попадёт в любой публичный репозиторий, куда синхронизируется `src/owner`.
- **Сценарий атаки/утечки:** любой, у кого есть admin/adminplus APK (а его получают все, кому нужна база учащихся, и он лежит в `releases/`), извлекает секрет (`strings classes.dex`, jadx) и с этого момента генерирует валидные коды **бессрочно**, для любого устройства. Обход гейта: коллега/потерянный телефон/установка admin-APK себе; гейт после этого не значит ничего. Ротация невозможна без выпуска новой сборки.
- **Root cause:** статический секрет в коде + проверка целиком на клиенте (`AccessGateScreen.kt:160`), без сервера, без привязки к устройству, без ротации.
- **Фикс:** убрать секрет из репозитория и APK. Практичные варианты: (а) код выдаёт владелец вручную (список одноразовых кодов, хранящихся в Keystore-обёрнутом виде, либо просто «код на день» в приватном канале); (б) серверный TOTP-эндпоинт, приложение только отправляет введённый код; (в) минимум — секрет в `BuildConfig` из `.env` (Secrets-плагин уже подключён, `app/build.gradle.kts:187-193`), но это не спасает от извлечения из APK — только от утечки через git. После фикса: **ротировать секрет**, считать текущий скомпрометированным; добавить throttling и журнал попыток.
- **Тест/проверка:** `strings app-admin-release.apk | grep -c "MpK-0"` → 0; `grep -rn "MpK-0" app/src` → пусто; unit-тест «секрет не приходит из BuildConfig/ресурсов»; на устройстве: код, сгенерированный по старому секрету сторонним генератором, не принимается.

---

## P1

### [P1] adminplus: гейт доступа отключён у самого привилегированного флейвора (PAT + публикация)

- **Где:** `app/src/owner/java/com/politex/owner/OwnerOverlay.kt:68` — `AccessGateWrapper(gateRequired = BuildConfig.FLAVOR == "admin")`; `app/src/adminplus/java/com/politex/adminplus/AdminPlusOverlay.kt:54` — `OwnerRoot { … }` (без кода). Т.е. в **adminplus** экран кода не показывается вообще, при этом именно там `ИНСТРУМЕНТЫ ДАННЫХ` (редактор преподавателей) и `ТОКЕН ПУБЛИКАЦИИ` (`AdminPlusOverlay.kt:78-96`).
- **Сценарий атаки/утечки:** телефон с adminplus разблокирован и на минуту оставлен без присмотра (или потерян, или экран не заблокирован) → посторонний открывает «Инструменты данных», меняет ФИО/телефоны преподавателей и жмёт «Применить» → правки уходят в GitHub и становятся видны всем пользователям приложения (дефейс контента). Читает локальные ПДн преподавателей. Кода не требуется.
- **Root cause:** «adminplus — сборка владельца, значит гейт не нужен»; но владелец — это ровно тот, у кого сосредоточены максимум прав, а защита устройства ничем не отличается.
- **Фикс:** `gateRequired = BuildConfig.FLAVOR == "admin" || BuildConfig.FLAVOR == "adminplus"`; отдельно — повторный запрос кода/PIN **перед каждой публикацией** (сейчас есть только `AlertDialog` подтверждения, `TeacherEditorScreen.kt:421-444`); авто-блокировка через N минут бездействия и сброс `AccessUnlockStore` при `onStop`.
- **Тест/проверка:** на устройстве: adminplus при холодном старте просит код; после сворачивания на 10 минут — снова просит; публикация без повторного ввода кода недоступна. Unit-тест на `gateRequired(flavor)`.

### [P1] Персональные данные преподавателей (ФИО + телефоны) уходят в публичный GitHub-репозиторий без предупреждения о ПДн

- **Где:** `app/src/adminplus/java/com/politex/adminplus/publish/OwnerPublishPayloadBuilder.kt:28-63` (в payload попадают `fio`, `phone`, `position`, `experience`, `category` + фото), `GithubDataApiPublisher.kt:104-133` (коммит `content/owner_overrides.json` в `main`), `BuildConfig.GITHUB_OWNER/REPO` = `F-708` / `moy-polytech` (`app/build.gradle.kts:91-92`). Публикация запускается из `TeacherEditorScreen.kt:201-234` с подтверждением «Отправка всем пользователям…» (`:252`, `:421-444`).
- **Сценарий атаки/утечки:** владелец публикует правку одного телефона — в публичный репозиторий (и в его историю, и в кэши/форки) навсегда уезжает JSON с ФИО и телефонами всех изменённых преподавателей; выгрузка видна и через `raw.githubusercontent.com`, и любому, кто клонирует репо. Подтверждение в UI говорит про «пользователей приложения», а не про «публичный интернет/историю git». Отзыва/удаления нет.
- **Root cause:** «контент-пак» как канал распространения данных не разделяет публичный контент (расписание, фото) и ПДн (телефоны); согласия/основания на публикацию и минимизации нет.
- **Фикс:** не публиковать телефоны в публичный репозиторий (телефон — только по запросу к серверу с авторизацией, либо убрать поле из pack вовсе; `teachers.csv` в `assets` тоже содержит телефоны и лежит в git — см. «Приватность»). Если публикация остаётся: отдельный диалог с явным списком полей, предупреждением «попадёт в публичный git-репозиторий навсегда», чекбокс согласия и запись в аудит-лог локально. Добавить в `pack` только “безопасные” поля по allow-list.
- **Тест/проверка:** unit-тест `OwnerPublishPayloadBuilderTest`: при заданных телефонах в payload **нет** `phone`; `git show` публикации не содержит телефонов; ручная проверка: диалог перечисляет уезжающие поля.

### [P1] Облачный бэкап выгружает всю Room-БД: оценки, задания, напоминания и текстовый индекс импортированных документов

- **Где:** `app/src/main/AndroidManifest.xml:17` (`android:allowBackup="true"`), `res/xml/data_extraction_rules.xml:8-10` и `res/xml/backup_rules.xml:8-9` — в `<cloud-backup>` / `<device-transfer>` включены `sharedpref/mpk_widget_prefs.xml` и **`database/mpk_schedule.db`** целиком. В БД: `GradeEntryEntity`, `StudentTaskEntity`, `TaskReminderEntity`, `LessonEntity`, `MaterialItemEntity` (`textContent` — до `MAX_INDEXED_CHARS = 200_000` символов на документ, `app/src/main/java/com/politex/util/MaterialImporter.kt:42,344,448`), аннотации/закладки.
- **Сценарий атаки/утечки:** пользователь с включённым Google-бэкапом отдаёт в Google Drive (и при переносе — на новое устройство/в другой аккаунт) свои оценки, задания с дедлайнами и **содержимое личных документов** (конспекты, чужие PDF/DOCX, выдержки до 200 тыс. символов). На устройстве с общим/корпоративным аккаунтом это доступно третьим лицам.
- **Root cause:** «минимальность» реализована на уровне файлов, а не таблиц: БД — единый файл, поэтому «включить настройки» = «включить всё».
- **Фикс:** исключить БД из бэкапа (оставить только `mpk_widget_prefs.xml`), либо разделить данные на две БД (настройки/кэш расписания — бэкапить; оценки/задания/материалы — нет), либо шифровать чувствительные таблицы. Для `admin`/`adminplus` — `allowBackup="false"` (или `tools:replace` в манифестах флейворов) и явный `dataExtractionRules` без БД. `textContent` ограничить и не индексировать содержимое документов в БД — держать индекс в отдельной, не бэкапируемой БД.
- **Тест/проверка:** на устройстве `adb shell bmgr backupnow com.politex` + разбор получившегося датасета: нет `mpk_schedule.db` (или в БД нет таблиц grades/tasks/material); unit-тест на содержимое `data_extraction_rules.xml`; `aapt dump xmltree` по release-APK показывает ожидаемые правила.

### [P1] `REQUEST_INSTALL_PACKAGES` запрошен во всех флейворах, хотя установщик обновлений включён только в standard

- **Где:** `app/src/main/AndroidManifest.xml:13`; фактическое использование — `app/src/main/java/com/politex/update/AppUpdateChecker.kt:28` (`UPDATE_ENABLED_FLAVORS = setOf("standard")`), `UpdatePayment…`/`UpdateInstallController.kt`, `AppUpdateWorker.kt:134-142`. Проверено в merged/packaged-манифестах: право присутствует у `standardRelease`, `adminRelease`, `adminplusRelease` (и, по общим правилам, у teacher).
- **Сценарий атаки/утечки:** у teacher/admin/adminplus появляется разрешение «Установка неизвестных приложений» без функциональной необходимости — это раздувает поверхность социальной инженерии («приложению разрешено ставить APK»), блокирует публикацию в Google Play без декларации и снижает доверие модерации; при утечке подписи/репозитория упрощает доставку стороннего APK. Дополнительно: в fallback-пути через GitHub Releases `sha256` не заполняется (`update/UpdateMeta.kt:126-132`, `update/AppUpdateChecker.kt:71-77`), поэтому проверка контрольной суммы пропускается (`UpdateDownloader.kt:149-158` возвращает `true` при пустом ожидании) — остаётся только проверка подписи.
- **Root cause:** permission объявлен в общем манифесте, а фича — во флейвор-специфичном коде.
- **Фикс:** перенести `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>` в `app/src/standard/AndroidManifest.xml` (создать оверлей) и, если нужно, в adminplus; в `standard` требовать `sha256` из `update-meta.json` (при отсутствии — отказываться от установки для канала stable).
- **Тест/проверка:** `aapt dump permissions app-teacher-release.apk` → нет `REQUEST_INSTALL_PACKAGES`; `app-standard-release.apk` → есть; unit-тест «stable-канал без sha256 отклоняется».

### [P1] Экспортированный `activity-alias` с фильтром `content:// */*` — любое приложение может подсунуть файл

- **Где:** `app/src/main/AndroidManifest.xml:67-138` (`FileViewAlias` → `MainActivity`, `android:exported="true"`, первый `<intent-filter>`: `VIEW` + `scheme=content` + `mimeType="*/*"`), приём: `app/src/main/java/com/politex/ui/screens/materials/ExternalMaterialIntake.kt:70-97`, обработка интента: `MainActivity.kt:152-199` (в т.ч. `EXTRA_ROOM`, `EXTRA_OPEN_TAB` из внешнего интента).
- **Сценарий атаки/утечки:** вредоносное/чужое приложение шлёт `ACTION_VIEW` с `content://`-URI своего провайдера на `com.politex/.FileViewAlias` (в admin — тоже, intake разрешён для всех, кроме teacher). Открывается экран материалов с предложением добавить «файл»; DISPLAY_NAME контролируется атакующим (см. P2 о санитизации), содержимое — произвольный ZIP/DOCX (см. P2 zip-bomb). Дополнительно: extras `EXTRA_ROOM`/`EXTRA_OPEN_TAB` позволяют внешне управлять навигацией (переход на карту с произвольной строкой) — UI-спуфинг и источник мусорных состояний. Прямой кражи данных нет, но канал «внешний ввод → парсинг файлов в приложении» полностью открыт.
- **Root cause:** фильтр `*/*` скопирован «чтобы файловые менеджеры точно попали»; проверка APK есть (`ExternalMaterialIntake.kt:99-113`), а allow-list типов/размеров на входе — нет.
- **Фикс:** убрать `*/*`-фильтр, оставить перечисление MIME (уже есть второй фильтр `:79-91`) + `pathPattern`-фильтр для octet-stream (`:94-137`) — этого достаточно для файловых менеджеров; `EXTRA_ROOM` принимать только с `scheme`/`package`-проверкой или из `PendingIntent`; ограничить `MAX_FILES_PER_SHARE` (уже 20) и суммарный размер на уровне интента.
- **Тест/проверка:** `adb shell am start -a android.intent.action.VIEW -d "content://…/x.bin" -t "*/*" -n com.politex/.FileViewAlias` → приложение не принимает файл; `-t application/pdf` → принимает как раньше.

---

## P2

### [P2] Zip-bomb / OOM при импорте DOCX (нет лимита на распакованный объём)

- **Где:** `app/src/main/java/com/politex/util/DocxTextExtractor.kt:22-79` (стриминг `word/document.xml` без счётчика распакованных байт, `StringBuilder` + список абзацев без верхней границы), `app/src/main/java/com/politex/util/MaterialImporter.kt:461-506` (`docxPageCount`, тот же паттерн), лимит есть только на **входной** файл — 50 МБ (`:44`); рендер в WebView использует JSZip (`assets/viewer/lib/jszip3.min.js`, `docx-preview.min.js`), где распаковка идёт целиком в память.
- **Сценарий атаки/утечки:** «одногруппник» присылает DOCX 40 МБ с коэффициентом сжатия ~1000× (→ десятки ГБ XML). Пользователь добавляет файл → приложение надолго занимает CPU/память, ANR или падение процесса; в WebView — убийство рендерера (обрабатывается через `onRenderProcessGone`, `MaterialWebViewer.kt:298-306`, но UX ломается).
- **Root cause:** защита только по размеру сжатого файла; распакованный объём не считается ни в Kotlin, ни в JS.
- **Фикс:** в `ZipInputStream`-циклах считать распакованные байты и обрывать на лимите (например 100 МБ, как в `ContentPackValidator.MAX_UNPACKED_BYTES`, `content/ContentPackValidator.kt:35`); ограничить `textContent` до 200 тыс. символов **до** накопления (сейчас `take()` уже после полного парсинга); для WebView — передавать в JS уже проверенный размер и ограничить `docx-preview` по числу страниц/абзацев; показывать понятную ошибку «документ слишком сложный».
- **Тест/проверка:** unit-тест с синтетическим ZIP-бомбом (entry с `Deflater` 10^9 нулей) → `IOException`, память не растёт; ручной импорт bomb-файла: приложение не падает, показывает ошибку.

### [P2] `sanitizeFileName` не убирает разделители пути и `..` (defense-in-depth)

- **Где:** `app/src/main/java/com/politex/util/MaterialImporter.kt:76-86` — фильтруются control/невидимые символы, пробелы и длина (120), но **не** `/`, `\`, `..`, `:`; результат (`fileName`, `title`) сохраняется в БД и подставляется в URL WebView (`MaterialWebViewer.kt:319-323` — там `Uri.encode`, ок). Запись на диск использует `"$itemId.$ext"` (`:430`), поэтому **сейчас** traversal не эксплуатируется: `ext` = `lowercase().take(6)` и требует несуществующего каталога-«префикса» для выхода из `filesDir/materials`.
- **Сценарий атаки/утечки:** при любом будущем использовании `fileName` как пути (экспорт, «поделиться», кэш превью) имя вида `a.b/../../shared_prefs/x` даст запись вне каталога материалов; кроме того, имена с `/` ломают отображение и поиск.
- **Root cause:** санитизация написана под «косметику», а не под «имя — это данные, но может стать путём».
- **Фикс:** заменить запрещённые символы (`/ \ : * ? " < > |` и управляющие) на `_`, схлопывать `..`, отрезать ведущие точки/пробелы; для диска всегда использовать только `id + "." + ext`, где `ext` из allow-list (`pdf, docx, doc, txt, md, csv, log, json, xml, rtf` + image), иначе `bin`.
- **Тест/проверка:** unit-тест `sanitizeFileName("../../x.pdf")` → без `/` и `..`; импорт с DISPLAY_NAME `a.b/../../../evil` → файл только внутри `filesDir/materials`, имя в БД без разделителей.

### [P2] Нет `networkSecurityConfig`/pinning: доверие пользовательским CA

- **Где:** `res/xml/network_security_config.xml` **отсутствует**, в манифесте нет `android:networkSecurityConfig` и `android:usesCleartextTraffic` (проверено по packaged-манифесту). Клиенты: `data/network/MpkNetworkClient.kt:62,80-89` (OkHttp по умолчанию), `update/UpdateCheckController.kt:57-63`, `adminplus/publish/GithubApi.kt:87-94`, `content/ContentPackWorker` (raw.githubusercontent).
- **Сценарий атаки/утечки:** на устройстве с установленным пользовательским/корпоративным/AV-корневым сертификатом (или при активном MITM-прокси) перехватывается `update-meta.json` и манифест контент-пака. Код приложения подменить нельзя (проверка подписи APK, `UpdateDownloader.kt:137-147`), но **контент** (ФИО/фото/телефоны преподавателей, карта колледжа, ссылки в описании обновления) — можно: `sha256` контент-пака берётся из того же перехваченного манифеста (`ContentPackValidator.kt:42-59`). Плюс утечка факта проверки обновлений/версии устройства.
- **Root cause:** дефолтная политика доверия; для публичного GitHub/сайта колледжа дополнительных ограничений не задавали.
- **Фикс:** добавить `res/xml/network_security_config.xml` с `<base-config cleartextTrafficPermitted="false">` и `certificates src="system"` (без `user`), для `api.github.com`, `raw.githubusercontent.com`, `guo-mpk.by` — pinning (SPKI) или хотя бы `cleartextTrafficPermitted=false` + отказ от user-CA. Для контент-пака — подпись манифеста (Ed25519-ключ в APK) вместо `sha256` из того же канала.
- **Тест/проверка:** `aapt dump xmltree --file res/xml/network_security_config.xml …` показывает нужную политику; mitmproxy с user-CA: запросы к `raw.githubusercontent.com` падают; без CA — работают.

### [P2] Нет throttling/блокировки на ввод кода доступа; разблокировка бессрочная

- **Где:** `app/src/owner/java/com/politex/owner/AccessGateScreen.kt:151-166` — попытки не считаются, задержки/блокировки нет, `AccessUnlockStore.unlock()` пишет флаг навсегда (`AccessUnlockStore.kt:13-18`); отображение кода доступно из меню (`OwnerOverlay.kt:159-167`). `AccessClock.overrideSeconds` — публичная изменяемая переменная в проде (`AccessCode.kt:6-11`).
- **Сценарий атаки/утечки:** подбор 6-значного кода вручную нереален (3 валидных значения на шаг), но сценарий «взял чужой телефон, тыкает, пока владелец отошёл» ничем не ограничен; после разовой разблокировки доступ сохраняется навсегда и переживает перезапуск — потерянный/переданный телефон остаётся открытым.
- **Root cause:** гейт задуман как «покажи код коллеге», а не как контроль доступа к ПДн.
- **Фикс:** счётчик неудач в `AccessUnlockStore` + нарастающая задержка/блокировка после 5 попыток; TTL разблокировки (например 15 минут бездействия) и сброс при `onStop`; убрать `AccessClock.overrideSeconds` из release-сборки (`@VisibleForTesting` + `internal` + отдельный sourceSet).
- **Тест/проверка:** 5 неверных кодов → заблокировано; через TTL экран кода появляется снова; в release-dex нет сеттера `overrideSeconds`.

### [P2] Гейт `students.csv` в CI отсутствует; release-workflow живёт в другом репозитории

- **Где:** `.github/workflows/build-apk.yml:39-49` — только `testStandardDebugUnitTest` + `assembleDebug` + upload debug-APK; проверки «в standard APK нет `students.csv`» нет. Release-workflow (`.github/workflows/release.yml`) в этом репозитории отсутствует — по `RELEASE.md:55-60` он собирает `:app:assembleStandardRelease` в публичном `F-708/moy-polytech`.
- **Сценарий атаки/утечки:** регрессия конфигурации sourceSets (`app/build.gradle.kts:150-171`) или копирование файла в `src/main/assets` тихо приведёт к публикации базы в стандартном APK, и никто этого не заметит до жалобы.
- **Root cause:** гейт описан в инструкциях как ручная команда (`unzip -l … | grep -c students.csv`), но не автоматизирован.
- **Фикс:** job после сборки: `unzip -l app-standard-release.apk | grep -c 'assets/students.csv'` должен быть 0 (и `!= 0` для admin — как ожидание); плюс `git ls-files`-гейт из P0; продублировать в release-workflow публичного репо; сделать скрипт `tools/gate-no-pii.sh` и вызывать из обоих workflow.
- **Тест/проверка:** локально `bash tools/gate-no-pii.sh` на текущих артефактах → PASS (standard 0, admin 1); искусственно добавить файл в `src/main/assets` → job краснеет.

---

## P3

### [P3] Зависимости: известных критичных CVE не найдено, но набор устарел
- **Где:** `gradle/libs.versions.toml:3-45` — `okhttp 4.12.0`, `coil 2.7.0`, `room 2.7.0`, `webkit 1.16.0`, Compose BOM `2024.09.00`, `lifecycle 2.8.7`, `kotlin 2.2.10`, AGP `9.1.1`, Gradle `9.3.1`, `coroutines 1.10.2`. `androidx.security:security-crypto` **не используется** (кастомный AES/GCM — плюс: нет deprecated-библиотеки с известными проблемами).
- **Сценарий:** не эксплуатируется напрямую; риск — накопление известных уязвимостей в WebView/Compose/OkHttp со временем и невозможность быстрого патча.
- **Root cause:** обновления «по мере надобности».
- **Фикс:** добавить `dependencyUpdates`/OSV-скан в CI, поднять BOM/lifecycle/okhttp (5.x) по мере совместимости.
- **Тест:** CI-job `osv-scanner --lockfile` без HIGH/CRITICAL.

### [P3] Keystore-ключ PAT без привязки к аутентификации пользователя
- **Где:** `app/src/adminplus/java/com/politex/adminplus/publish/GithubTokenStore.kt:28-59` — `KeyGenParameterSpec` без `setUserAuthenticationRequired(true)`/`setInvalidatedByBiometricEnrollment`; PAT шифруется AES-GCM (правильно), alias `adminplus_github_pat`; prefs `adminplus_publish_prefs` в бэкап **не** входят (не перечислены в include — хорошо).
- **Сценарий:** вредоносное приложение с root/ADB-бэкапом (или физический доступ к разблокированному устройству через backup-инструменты) может инициировать расшифровку в контексте приложения; сам PAT на диск в открытом виде не попадает.
- **Фикс:** `setUserAuthenticationRequired(true)` + `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` с fallback-обработкой `UserNotAuthenticatedException`; автозачистка токена после N дней.
- **Тест:** на устройстве: после блокировки экрана публикация требует разблокировки; после смены биометрии токен недействителен (нужен повторный ввод).

### [P3] Тестовые рычаги и отладочные ветки в production-коде
- **Где:** `AccessClock.overrideSeconds` (см. P2), `GithubTokenStore.cipher` (internal mutable), `UpdateCheckController.checkOverride`, `UpdateInstallController.downloadOverride/validateOverride/installLaunchOverride`, `AppUpdateWorker.downloadOverride` — всё в `main`/release.
- **Сценарий:** не эксплуатируется внешним атакующим (нет точек входа), но увеличивает риск случайного «обхода» проверок при будущих правках и утечки внутренних API в публичный код.
- **Фикс:** вынести тестовые хуки в `src/test`/`src/debug` через `internal` + DI, либо аннотировать `@VisibleForTesting` и обнулять в `release` (R8 `-assumenosideeffects`).
- **Тест:** `grep -rn "Override =" app/build/tmp/kotlin-classes/release` → пусто.

### [P3] Гигиена секретов рядом с публикуемым репозиторием
- **Где:** `keystore.properties` и `my-upload-key.jks` лежат в рабочем каталоге репозитория (`git check-ignore` подтверждает: игнорируются, в индекс не попали); `keystore-border`-копии — в `E:\MPK app\keystore-backup` (вне git). `tools/students-original.csv`, `.claude/worktrees/t2b-verify/…/students.csv` — также на диске вне индекса.
- **Сценарий:** при «синхронизации main-репозитория с публичным» копированием каталога (а не `git push`) секреты/ПДн уедут в публичный репо вместе с игнорируемыми файлами. Это ровно тот механизм, которым, судя по `RELEASE.md:45-46`, пользуется проект.
- **Фикс:** синхронизировать только `git archive`/`git push`, никогда — копированием дерева; ключи хранить вне каталога репозитория; в публичный репо добавить `.gitattributes`/CI-проверку на отсутствие `*.jks`, `keystore.properties`, `students*.csv`.
- **Тест:** `git archive HEAD | tar -t | grep -E "jks|keystore.properties|students"` → пусто.

---

## Экспортированные компоненты

Данные — из `app/build/intermediates/packaged_manifests/*Release*/AndroidManifest.xml` (итоговый merged-манифест).

| Компонент | exported | Чем опасен | Нужен ли |
|---|---|---|---|
| `com.politex.MainActivity` | **true** | Принимает `SEND`/`SEND_MULTIPLE` (PDF/DOCX/XLS/CSV/TXT/`image/*`/octet-stream) и `VIEW`; обрабатывает extras `EXTRA_ROOM`, `EXTRA_OPEN_TAB`, `EXTRA_SCROLL_TARGET` из внешнего интента → внешнее управление навигацией и импортом файлов | Да (лаунчер + приём «поделиться») |
| `com.politex.FileViewAlias` (activity-alias) | **true** | Фильтр `VIEW` + `content://` + **`*/*`** — любое приложение подсовывает любой файл; intake работает и в admin/adminplus (кроме teacher, где alias удалён merger'ом) | Частично: нужен только для «открыть с помощью»; `*/*` — убрать |
| `BellCountdownWidgetProvider`, `NowNextWidgetProvider`, `TodayScheduleWidgetProvider`, `BellsWidgetProvider` (+ teacher-виджеты в teacher) | **true** | Только `APPWIDGET_UPDATE`; могут слать сторонние приложения → лишние обновления виджетов (без утечек) | Да (требование AppWidget) |
| `com.politex.widget.WidgetConfigActivity` | **true** | `APPWIDGET_CONFIGURE`; чужая активити может открыть экран настройки виджета (без правки данных без действия пользователя) | Да (требование AppWidget) |
| `com.politex.update.UpdateInstallActivity` | false | Точка установки APK; принимает `EXTRA_APK_PATH`; повторно валидирует пакет/подпись/версию (`UpdateInstallActivity.kt:25-29`, `:53-71`) | Да, но только для standard |
| `com.politex.util.BellTimerService` | false | FGS specialUse, запуск только изнутри | Да |
| `WidgetAlarmReceiver`, `BellServiceAlarmReceiver`, `TimeChangedReceiver` | false | Кастомные action'ы + системные broadcast'ы, `exported=false` | Да |
| `androidx.core.content.FileProvider` (`${applicationId}.updateprovider`) | false, `grantUriPermissions=true` | Раздаёт URI на `cache/updates/*.apk`; пути узкие (`res/xml/update_file_paths.xml`), провайдер не экспортирован | Да (установка обновления) |
| `androidx.startup.InitializationProvider`, `androidx.room.MultiInstanceInvalidationService`, work-manager receiver'ы (`RescheduleReceiver`, `ConstraintProxy*`, `DiagnosticsReceiver`), `ProfileInstallReceiver` | false / системные | Добавляются merger'ом AndroidX; не экспортированы (кроме системных с permission `DUMP`) | Да (библиотечные) |
| `android.permission.REQUEST_INSTALL_PACKAGES` (не компонент, но витрина) | — | Висит на **всех** флейворах, нужен только standard → см. P1 | Только standard |

Интентов с `android:exported="true"` и привилегированными действиями (установка, публикация, доступ к базе) вне `MainActivity`/`FileViewAlias` нет; `provider`/`service`/`receiver` с чувствительными операциями закрыты. Это хорошая гигиена.

---

## Приватность: что хранится, где, сколько живёт, куда утекает

| Данные | Где | Срок жизни | Бэкап | Логи | Сеть |
|---|---|---|---|---|---|
| ФИО/группа/курс/специальность/общежитие/куратор учащихся | `assets/students.csv` в admin/adminplus APK; читается в память (`StudentsRepository`), на диск не пишется | Пока установлен APK | Нет (в APK) | Нет | Нет. **Но:** файл в истории/вершине публичной git-ветки (P0) |
| ФИО, должность, предметы, **телефон**, стаж, категория, фото преподавателей | `assets/teachers.csv` + `assets/teachers_photos/*` (во **всех** сборках, файл в git); правки — `filesDir/owner_overrides/manifest.json` + `photos/`; публикация — `content/owner_overrides.json` в GitHub | Бессрочно (git), локально — до удаления | Нет | Нет | Да: публичный GitHub (P1) |
| Оценки, экзамены | Room `mpk_schedule.db` (`grade_entries`) | До удаления приложения/очистки | **Да** (cloud + device-transfer) | Нет | Нет |
| Задания, дедлайны, напоминания | Room (`student_tasks`, `task_reminders`) | То же | **Да** | Нет | Нет |
| Расписание/история занятий, кэш ДЗ | Room (`lessons`, `teacher_lessons`), prefs | Пока не перезапишется | **Да** | Нет | Нет (только загрузка с `guo-mpk.by` по HTTPS) |
| Импортированные документы (личные файлы) | `filesDir/materials/<id>.<ext>` (до 50 МБ на файл, число не ограничено) | До удаления материала | Нет | Нет | Нет |
| Текстовый индекс документов (первые 200 000 символов) | Room `material_items.text_content` | До удаления материала | **Да** (в составе БД) | Нет | Нет |
| GitHub PAT | SharedPreferences `adminplus_publish_prefs` в зашифрованном виде (AES-GCM, ключ в AndroidKeyStore, alias `adminplus_github_pat`) | До «Удалить»/переустановки | Нет (не в include) | Нет | Только `api.github.com` (заголовок `Authorization: Bearer`, `GithubApi.kt:67`); в теле/URL не попадает |
| Флаг разблокировки admin | prefs `admin_access` (boolean) | Бессрочно (до reset) | Нет | Нет | Нет |
| Настройки виджетов/темы/уведомлений | prefs `mpk_widget_prefs.xml` | Постоянно | **Да** | Нет | Нет |
| Диагностика синхронизации (URL, превью 100 символов документа расписания) | `mpk_update_prefs`/память; отображается в UI | Сессия | Нет | Нет | Нет |
| Логи | — | — | — | **PII не логируется**: во всём `app/src` только `Log.w` в `BellTimerService.kt:59,127,208` (сообщения без данных), `println` — только в тестах | — |
| Аналитика/трекеры | отсутствуют (Firebase/analytics/play-services в зависимостях закомментированы, `app/build.gradle.kts:203-252`) | — | — | — | — |

**Итог по приватности:** приложение собирает минимум (нет аккаунта, нет аналитики, нет геолокации), но: (1) ПДн учащихся живут в git и в APK открыто; (2) ПДн преподавателей публикуются в публичный репозиторий; (3) облачный бэкап забирает БД с оценками и текстом личных документов. Всё остальное — в норме.

---

## Что проверить на устройстве (ручной чек-лист)

1. **Гейт по флейворам:** admin при холодном старте просит код; **adminplus — не просит** (подтвердить баг P1); после ввода кода перезапуск не спрашивает снова (P2).
2. **ПДн в APK:** `unzip -l` установленного/скачанного APK: standard — 0 `students.csv`, admin/adminplus — 1; `unzip -p` даёт читаемый CSV без кода (P0).
3. **Секрет:** `strings` по `classes.dex` admin-APK находит префикс секрета (P0); сгенерировать код сторонним TOTP-генератором по извлечённому секрету и убедиться, что приложение его принимает.
4. **Внешний ввод:** `adb shell am start -a android.intent.action.VIEW -d "content://<чужой-провайдер>/x.bin" -t "*/*" -n com.politex/.FileViewAlias` → должно отклоняться после фикса; сейчас принимается. Повторить с `-t application/pdf`.
5. **DOCX-бомба:** собрать zip ~1 КБ с `word/document.xml` из 10^9 символов, отправить в приложение через «Поделиться» → зафиксировать ANR/OOM/время; после фикса — ошибка «слишком сложный документ».
6. **WebView:** открыть PDF/DOCX со ссылкой `https://example.com` → должна открыться во внешнем браузере, а не внутри; проверить, что `file:///data/data/...` из JS недоступен (allowFileAccess=false) и запрос на сторонний хост даёт 404 (`shouldInterceptRequest`).
7. **Бэкап:** `adb shell bmgr backupnow com.politex` (или `adb backup -f`), распаковать датасет: должна отсутствовать `mpk_schedule.db` после фикса; сейчас присутствует и содержит оценки/задания/`text_content`.
8. **Права:** `adb shell dumpsys package com.politex.teacher | grep REQUEST_INSTALL_PACKAGES` → после фикса пусто; `com.politex` (standard) — есть.
9. **Публикация:** сохранить PAT, выключить сеть → «Нет связи с GitHub», токен не появляется в logcat (`adb logcat | grep -i github_pat`) — ожидаемо чисто.
10. **Установка обновления:** подсунуть в `cache/updates` APK, подписанный другим ключом → `UpdateInstallActivity` отклоняет («подпись не совпадает»); APK с тем же пакетом, но меньшим versionCode → «версия не новее».
11. **Git-гигиена (на машине разработчика):** `git log --all -- app/src/admin/assets/students.csv` и `git cat-file -e origin/v2.4-stable:app/src/admin/assets/students.csv` — сейчас файл есть в удалённой ветке; после инцидент-реакции должно быть пусто.
12. **Release-APK:** `aapt dump badging` — `debuggable` отсутствует, `targetSdk 36`; `aapt dump xmltree` — нет `usesCleartextTraffic`, нет `networkSecurityConfig` (после фикса — есть).

---

## Что уже сделано правильно (не переделывать)

- **WebView-вьюер документов** (`ui/screens/materials/MaterialWebViewer.kt:232-311`, `DocWebBridge.kt:13-27`): контент отдаётся только с синтетического `https://appassets.androidplatform.net`; `allowFileAccess=false`, `allowContentAccess=false`, `setAllowFileAccessFromFileURLs/UniversalAccessFromFileURLs=false`, `mixedContentMode=NEVER_ALLOW`, cookie выключены, `addJavascriptInterface` не используется (только `WebMessagePort`), CSP `default-src 'none'; …; object-src 'none'; frame-src 'none'`; `shouldInterceptRequest` отдаёт 404 на всё, кроме `/assets/viewer/*` и `/doc/file.<ext>`; проверка `".."` в пути. Это близко к эталону.
- **Установка APK** (`update/UpdateDownloader.kt:103-158`, `UpdateInstallActivity.kt`): проверка имени пакета == `BuildConfig.APPLICATION_ID`, **сверка SHA-256 подписи с установленной**, требование `versionCode > текущего`, опциональный sha256 файла, FileProvider не экспортирован и отдаёт только `cache/updates/`, установщик выбирается только системный. Компрометация GitHub-аккаунта не даёт установить чужой APK.
- **Контент-пак** (`content/ContentPackValidator.kt:98-196`): защита от zip-slip (`isZipSlip` + `canonicalPath`), лимиты 25 МБ/100 МБ распакованного, обязательный sha256, проверка состава/минимумов, атомарная активация с prune.
- **PAT**: AES-GCM в AndroidKeyStore (не SharedPreferences plaintext, не в `BuildConfig`), в UI `PasswordVisualTransformation`, в логи не попадает, передаётся только на `api.github.com`; предпросмотр последствий публикации есть.
- **Манифест**: `debuggable` не выставлен, cleartext запрещён (targetSdk 36 + нет `usesCleartextTraffic`), `FileProvider` с `exported=false` и узкими путями, сервисы/ресиверы с чувствительными действиями `exported=false`, `extractNativeLibs=false`, `dependenciesInfo.includeInApk=false`.
- **R8** (`app/proguard-rules.pro:12-17`): `-keepclassmembers enum *` с `values()/valueOf()/entries()` — настройки на enum'ах не ломаются; `-keep` для компонентов из манифеста; `-dontwarn` только для okhttp/okio/coroutines.
- **Секреты в git**: `.env`, `*.jks`, `keystore.properties`, `students*.csv` — в `.gitignore` и фактически не в индексе (`git check-ignore` подтверждает); в собранных APK нет `keystore.properties`/`.jks` (единственный `.properties` — служебный `META-INF/…/app-metadata.properties`).
- **Сеть**: все запросы по HTTPS (`guo-mpk.by`, `api.github.com`, `raw.githubusercontent.com`), нет трекеров/аналитики; OkHttp без `logging-interceptor`.

---

## Гейт `students.csv`: как гарантируется сейчас

1. **Структурно** — корректно: файл лежит только в `app/src/admin/assets/`; `standard`/`teacher` его не наследуют, `admin` получает автоматически (AGP-конвенция), `adminplus` — явно через `sourceSets` (`app/build.gradle.kts:150-171`). Проверено на артефактах: `app-standard-release.apk` — 0 записей `students.csv`, `app-admin-release.apk` / `app-adminplus-release.apk` — по 1.
2. **В git** — `.gitignore` + `**/students*.csv` (`.gitignore:26-31`), в индексе файла нет (`git ls-files`).
3. **В CI** — **никак**: workflow собирает только debug и не проверяет содержимое APK; release-workflow находится в другом (публичном) репозитории и в этом дереве отсутствует.
4. **В репозитории (remote)** — **не гарантируется**: ветка `origin/v2.4-stable` содержит файл в вершине (P0).

Рекомендуемый минимальный набор гейтов: (а) `git ls-files` + `git log --all -- <paths>` (PII в индексе/истории); (б) `unzip -l` по собранному standard-APK == 0 и по admin == 1; (в) запрет копирования дерева в публичный репо (только `git archive`/`git push`).

---

## Приоритетный план (что делать в этом порядке)

1. **Сегодня:** сделать репозиторий приватным / удалить `v2.4-stable` с remote; запустить процедуру инцидента по ПДн (P0-1).
2. **Сегодня:** убрать TOTP-секрет из кода, ротировать его, включить гейт в adminplus (P0-3, P1-1).
3. **1–2 дня:** перестать поставлять `students.csv` ассетом в admin/adminplus APK (шифрование/сервер), вычистить историю `git filter-repo` (P0-1, P0-2).
4. **Неделя:** пересмотреть состав облачного бэкапа, убрать телефоны из публикации, перенести `REQUEST_INSTALL_PACKAGES` в standard, сузить `FileViewAlias` (P1).
5. **Спринт:** zip-bomb-лимиты, санитизация имён, `network_security_config`+pinning, throttling кода, CI-гейты (P2/P3).