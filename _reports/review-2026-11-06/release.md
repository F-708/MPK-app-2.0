# Ревью: «Релиз и конфигурация сборки» — 2026-11-06

Объект: `E:\MPK app\MPK-app-2.1`, ветка `v2.4-stable`, HEAD `415be8c`, origin `F-708/MPK-app-2.0`.
Режим: только чтение (git read-only, без сборок и публикации). Все факты — из файлов репозитория;
секреты в отчёт не копировались.

Итог: **2 × P0, 8 × P1** в релизном контуре. Оба P0 срабатывают «тихо»: сборка зелёная,
APK валидный, но версия/доставка обновления ломаются без единой ошибки в логе.

---

## Сводка

| # | Уровень | Находка | Одной строкой |
|---|---|---|---|
| 1 | **P0** | Версия из тега даёт `2.2.0` вместо следующей | `git describe` на HEAD → `v2.2.0`; 24 из 29 тегов не матчатся шаблоном → все релизы волны получат один и тот же код |
| 2 | **P0** | `release.yml` из `RELEASE.md` не существует | Публикация не автоматизирована и не проверяема; `update-meta.json` в репо нет |
| 3 | **P1** | Превью-релиз «глушит» stable-канал | Один `update-meta.json` на оба канала |
| 4 | **P1** | Невалидный тег → `versionCode = 1` без падения | `computeVersionCode(...) ?: fallback` |
| 5 | **P1** | Нет release-подписи → warn вместо ошибки | APK уедет неподписанным/сборка упадёт неинформативно |
| 6 | **P1** | `configuration-cache` + `git describe` на этапе конфигурации | Риск устаревшего versionCode локально |
| 7 | **P1** | `min_version_code` парсится и не используется | Форсированное обновление из `RELEASE.md` — мёртвый код |
| 8 | **P1** | TOTP-секрет owner и PAT-экран adminplus в git | Утекут при копирующей синхронизации публичного репо |
| 9 | **P1** | Гейт `students.csv` только ручной | В CI гейта нет |
| 10 | **P1** | CI не собирает release ни одного флейвора | R8/shrink/подпись не проверяются до публикации |

---

## Находки

### [P0] Версия из тега: релиз соберётся как `2.2.0`, и все релизы этой волны получат один и тот же versionCode
**Где:** `app/build.gradle.kts:11`, `:44`, `:54-55`; `RELEASE.md:8-13`
**Риск:** рабочий HEAD не имеет тега, а ближайший достижимый тег, подходящий под шаблон сборки, —
`v2.2.0` (10 коммитов назад). Значит `assembleStandardRelease` на этой ветке даёт
`versionName = 2.2.0` и `versionCode = 20 200 999`. Проверено по истории: реально выпущенные
коды — `v2.4` = 5, `v2.5.1` = 7, `v2.26` = 28, `v2.2.0` = 22002 (тогда версия была захардкожена),
поэтому **первая** сборка с новым кодом установится (20 200 999 > любого из них). Ломается
дальше и тихо:
* `versionName` уезжает назад: после «2.26» пользователь видит «2.2.0» в «О приложении» и в «Что нового»;
* пока на HEAD не появится тег, подходящий под шаблон, **каждый** следующий релиз волны
  (`v2.27`, `v2.28`, …) снова разрешится в `2.2.0` / `20 200 999` — второй такой APK
  не установится поверх первого (система считает его тем же) и не будет предложен:
  `selectFromMeta` отбрасывает `versionCode <= current`
  (`app/src/main/java/com/politex/update/UpdateMeta.kt:56`), API-fallback — тоже
  (`UpdateMeta.kt:122`). То есть «выпустили — а обновления ни у кого нет», без ошибок в логах;
* `min_version_code`, который CI ставит равным `version_code` (`RELEASE.md:93-95`), тоже
  перестаёт расти.
**Root cause:** шаблон требует ровно три числовых компонента
(`^v(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?:-preview\.(\d{1,3}))?$`, строка 11), а фактическая
конвенция тегов в репозитории — двухкомпонентная: из 29 тегов **24** имеют вид `v2.6 … v2.26`
и под шаблон не подходят. `git describe --match` молча берёт *другой* (старый) тег, а не падает.
**Фикс:** 1) в `readVersionTag()` требовать тег **ровно на HEAD**
(`git describe --tags --exact-match`, иначе `error("нет тега на HEAD")`) — fallback `0.0.0-dev`
оставить только для `-PallowUntagged`/debug; 2) принимать `vM.m` (`v2.27` → `2.27.0`), чтобы
легитимная конвенция не ломала версию; 3) в CI сравнивать `versionName` из APK с тегом.
**Как проверить:**
```bash
git describe --tags --match "v[0-9]*.[0-9]*.[0-9]*" --abbrev=0   # → v2.2.0
git describe --tags --exact-match HEAD; echo $?                  # → fatal, тега нет
git tag --list 'v[0-9]*.[0-9]*.[0-9]*'                           # → только v2.2.0, v2.5.1
```

### [P0] Релизного workflow не существует: `RELEASE.md` описывает несуществующий CI
**Где:** `RELEASE.md:55-68`, `:70-101`; `.github/workflows/` (единственный файл `build-apk.yml`)
**Риск:** документация обещает, что «CI сам собирает APK, публикует GitHub Release и обновляет
`update-meta.json`». Фактически: `release.yml` в репозитории нет и в истории `.github/workflows`
он не появлялся (`git log --all -- .github/workflows` → только `build-apk.yml`), `update-meta.json`
в репозитории отсутствует. Следствия: (а) релиз собирается руками — весь пайплайн подписи,
`sha256`, `prerelease`, `min_version_code` не репетируется; (б) первичный канал апдейтера
(`raw.githubusercontent.com/.../update-meta.json`, `AppUpdateChecker.kt:43-46`) на живом репо
отдаёт 404 → клиент молча уходит в `apiFallback` (`UpdateCheckController.kt:113,138-142`),
т.е. поведение обновлений в проде отличается от задокументированного.
**Root cause:** релизный пайплайн живёт (если живёт) только в публичном зеркале, а не в этом
репозитории; в `build-apk.yml` попал лишь каркас «тесты + debug APK».
**Фикс:** положить `release.yml` в этот репозиторий (или субмодуль/`workflow_call`), с
`fetch-depth: 0`, `KEYSTORE_*` из secrets, `apksigner verify`, `sha256sum`, генерацией
`update-meta.json` и коммитом от бота; `RELEASE.md` синхронизировать с фактом.
**Как проверить:** `ls .github/workflows` → один файл; `git ls-files | grep update-meta` → пусто.

### [P1] Превью-релиз выключает stable-канал до следующего stable-релиза
**Где:** `app/src/main/java/com/politex/update/UpdateMeta.kt:44-47`,
`app/src/main/java/com/politex/update/UpdateCheckController.kt:102-114`, `RELEASE.md:61`
**Риск:** `update-meta.json` — один файл на оба канала, CI перезаписывает его каждым релизом.
Если последним выпущен превью, в мете `channel: "preview"`; клиент на stable получает
`metaMatchesChannel("preview", STABLE) == false` → `selectFromMeta` возвращает `null` →
`fallbackToApi = false` → `FetchOutcome.UpToDate`. Итог: **stable-клиенты видят «У вас последняя
версия»**, даже когда stable-сборка новее их, и в API-fallback не уходят (мета валидна).
**Root cause:** канал хранится в единственном файле вместо двух (`update-meta-stable.json` /
`update-meta-preview.json`) либо вместо списка релизов.
**Фикс:** раздельные мета-файлы по каналам; в клиенте — URL по выбранному каналу; при
`meta.channel != выбранного` — считать это «нет данных» и идти в API-fallback, а не в «последняя версия».
**Как проверить:** в тестах `UpdateChannelSelectionTest`/`UpdateChannelOfferTest` есть сценарий
«preview-мета + stable-клиент» — убедиться, что ожидается `UpToDate` (это и есть баг), и добавить
кейс «preview-мета, stable-клиент, в API есть stable новее».

### [P1] Невалидный/непригодный тег не роняет сборку, а даёт `versionCode = 1`
**Где:** `app/build.gradle.kts:20-25`, `:54-55`
**Риск:** при `major > 213 || minor > 99 || patch > 99`, `preview > 998` или отсутствии тега
`computeVersionCode` возвращает `null`, и пара становится `"0.0.0-dev" to 1`. Сборка **успешна**,
APK подписан, релиз публикуется — но `versionCode = 1` не установится ни на одну живую установку
(в прошивке уже 28+ / 20 200 999+), а `versionName = 0.0.0-dev` попадёт в «О приложении».
**Root cause:** fallback задуман для локальной отладки (`RELEASE.md:34-36`), но не отделён от
релизного пути.
**Фикс:** падать на `assemble*Release`, если тег не распознан; fallback — только для
`Debug`-вариантов (`-PdevVersion=true`).
**Как проверить:** `./gradlew :app:assembleStandardRelease` при `git tag v2.100.0` — сборка
должна упасть, а не выдать `1`.

### [P1] Release-подпись: при отсутствии ключа только предупреждение, текст противоречит коду
**Где:** `app/build.gradle.kts:110-131` (особенно `:126-129`), `:142`
**Риск:** если `keystore.properties`/`KEYSTORE_PATH` не заданы или файл не найден, в
`signingConfig("release")` не выставляется ни `storeFile`, ни пароли — остаётся пустой конфиг,
который всё равно присваивается `buildTypes.release` (`:142`). Комментарий обещает «упадёт с
понятной ошибкой», а `logger.warn` пишет «сборка будет подписана ключом отладки» — **оба
утверждения неверны**: debug-ключ не подставляется, а поведение AGP (неподписанный APK либо
падение на `validateSigningRelease`) не проверяется ни в CI, ни в чек-листе. Плюс алиас по
умолчанию `"upload"` (`:117`) захардкожен, а `storePassword`/`keyPassword` по умолчанию `""` —
пустой пароль молча уйдёт в конфиг.
**Root cause:** мягкая деградация вместо fail-fast в точке, где цена ошибки — публикация
неподписанного APK.
**Фикс:** `if (!keystoreCandidate.exists()) throw GradleException(...)` для release-задач;
проверка `storePass.isNotBlank() && keyPass.isNotBlank()`; в CI — шаг
`apksigner verify --print-certs` с ожидаемым отпечатком upload-ключа.
**Как проверить:** временно переименовать `keystore.properties` → `assembleStandardRelease`
должен падать; `apksigner verify --print-certs app-standard-release.apk` — CN не `Android Debug`.

### [P1] `configuration-cache` + `git describe` и `File.exists()` на этапе конфигурации
**Где:** `gradle.properties:21` (`org.gradle.configuration-cache=true`),
`app/build.gradle.kts:40-55`, `:120-121`
**Риск:** `providers.exec { git describe }` исполняется при конфигурации, результат попадает в
configuration cache. Локально `.gradle/configuration-cache` уже существует. Сценарий «поставил
тег → `assembleStandardRelease`» может переиспользовать закэшированную конфигурацию и собрать
APK со **старым** versionName/versionCode; то же для ветки «ключ появился/исчез». В CI кэша
конфигурации обычно нет, поэтому расхождение «локально 2.2.0, в CI 2.27.0» будет выглядеть
как загадка.
**Root cause:** значение версии — вход сборки, но не объявлено как вход задачи/провайдер с
отслеживанием.
**Фикс:** вынести версию в `providers.exec(...)` c явным `--no-configuration-cache` для
release-задач либо передавать её из CI (`-PversionName=... -PversionCode=...`, вычисленными
скриптом), а `git describe` оставить только для локальной сборки.
**Как проверить:** `./gradlew :app:assembleStandardRelease` дважды — до и после `git tag v9.9.9`
(без очистки `.gradle`), сравнить `BuildConfig.VERSION_CODE` в
`app/build/generated/source/buildConfig/standard/release/.../BuildConfig.java`.

### [P1] `min_version_code` реализован только на бумаге
**Где:** `app/src/main/java/com/politex/update/UpdateMeta.kt:15`, `:40`;
`RELEASE.md:93-95`
**Риск:** поле парсится в `UpdateMeta.minVersionCode`, но **нигде не читается** (grep по
`app/src` даёт только объявление, присваивание и тесты). Задокументированный механизм
«форсировать обновление, подняв `min_version_code`» не работает: старые клиенты продолжат
работать с несовместимым API/контентом, а релиз-менеджер будет уверен, что «заблокировал» их.
**Root cause:** фича добавлена в парсер и в документацию, но не в UI/логику.
**Фикс:** либо реализовать (при `BuildConfig.VERSION_CODE < minVersionCode` — блокирующий экран
«Обновитесь»), либо убрать поле из документации и парсера, чтобы не создавать ложной гарантии.
**Как проверить:** `git grep -n "minVersionCode" -- app/src/main` → только `UpdateMeta.kt`.

### [P1] TOTP-секрет owner и PAT-контур adminplus лежат в git
**Где:** `app/src/owner/java/com/politex/owner/AccessCode.kt:16` (секрет TOTP),
`:21` (`Mac`/алгоритм, 6 цифр, шаг 30 с); `app/src/adminplus/java/com/politex/adminplus/publish/`
(`GithubTokenStore.kt`, `PatSetupScreen.kt`, `GithubApi.kt`)
**Риск:** оба каталога **трекаются** (`git ls-files app/src/owner app/src/adminplus`) и не
исключены `.gitignore`. Пока репозиторий приватный — это допустимо. Но `RELEASE.md:45-47`
предписывает «синхронизировать main-репозиторий с публичным `F-708/moy-polytech`»; если
синхронизация делается копированием дерева/архива (а не отдельной веткой с фильтром), секрет
TOTP уедет в публичный репозиторий — и 6-значный код администратора становится вычислимым
любым, кто умеет HMAC. Аналогично публичными станут исходники PAT-экрана (сам PAT шифруется
через AndroidKeyStore — `GithubTokenStore.kt:23-70`, это сделано корректно).
**Root cause:** секрет — константа в исходнике, а не в `.env`/secrets-плагине; граница
«приватные флейворы» описана только комментарием в `app/build.gradle.kts:147-149`.
**Фикс:** 1) вынести секрет в `secrets`-плагин (`OWNER_TOTP_SECRET` в `.env`, `ignoreList`),
собирать owner только при наличии значения; 2) в публичный репозиторий не переносить
`app/src/owner`, `app/src/adminplus` вовсе (публичный CI собирает только `standard`);
3) при уже случившейся утечке — ротация секрета (он в истории git навсегда).
**Как проверить:** `git ls-files app/src/owner app/src/adminplus`; в публичном клоне —
`git grep -l "AccessCode\|github_pat"` и `git log --all -S "private val secret"`.

### [P1] Гейт `students.csv` — ручная команда в документе, а не гейт
**Где:** `RELEASE.md:125-130`; `.gitignore:25-29`; `.github/workflows/build-apk.yml` (гейта нет)
**Риск:** единственная защита от публикации ПДн — человек, который вспомнит выполнить
`unzip -l ... | grep -c students.csv`. В CI такой шаг отсутствует, `assembleRelease` его тоже
не делает. Файлы при этом лежат на диске: `app/src/admin/assets/students.csv` и
`tools/students-original.csv` (~380 КБ). Проверено: **оба не трекаются** (`**/students*.csv`),
т.е. в git ПДн нет — но любой сценарий «заархивировать рабочее дерево и отдать наружу»
вынесет их наружу. Тестовая фикстура `app/src/test/resources/students_fixture.csv` трекается,
но синтетическая («Примерова Анна Сергеевна», 5 строк) — ок.
**Root cause:** инвариант существует как договорённость, а не как исполняемая проверка.
**Фикс:** Gradle-задача `verifyNoStudentsCsv` (проверка `zipTree(apk)`/`variant.artifacts`) либо
шаг CI после сборки; отдельный шаг «в standard APK нет `assets/students.csv`» и «в standard APK
нет `com/politex/owner/`». Продублировать в `pre-push`-хук.
**Как проверить:**
```bash
unzip -l app/build/outputs/apk/standard/release/app-standard-release.apk | grep -c students.csv   # 0
unzip -l app/build/outputs/apk/standard/release/app-standard-release.apk | grep -c 'owner/AccessCode'  # 0
```

### [P1] CI не собирает release ни одного флейвора
**Где:** `.github/workflows/build-apk.yml:39-49`
**Риск:** job делает `testStandardDebugUnitTest` + `assembleDebug`. Значит до публикации
**никогда** не проверяются: R8/`isShrinkResources` (главный источник тихих релизных поломок),
release-подпись, наличие `students.csv` в APK, компиляция `admin`/`adminplus`/`teacher`
(их исходники в CI не собираются вообще), Roborazzi-эталоны. Релизный APK впервые собирается
у человека локально — то есть в момент выпуска.
**Root cause:** workflow писался как «сборка debug APK», а релизный пайплайн остался вне репо
(см. P0-2).
**Фикс:** матрица `standard/admin/adminplus/teacher` × `assembleRelease` (или хотя бы
`standard` + `compile*ReleaseKotlin` для остальных), `lintRelease`, `apksigner verify`,
`sha256sum`, upload `mapping.txt` как артефакт для ретрейса.
**Как проверить:** `gh run list --workflow=build-apk.yml` — в логах нет `assemble.*Release`.

### [P2] В meta-канале «Что нового» всегда пустое
**Где:** `app/src/main/java/com/politex/update/UpdateMeta.kt:61` (`notes = ""`)
**Риск:** при обновлении через `update-meta.json` (основной путь) `UpdateInfo.notes` пустая,
диалог/экран обновления не показывает список изменений; через API-fallback — показывает.
Пользователь видит разные экраны в зависимости от того, дошёл ли raw-файл.
**Root cause:** в схеме `update-meta.json` нет поля `notes`, парсер подставляет пустую строку.
**Фикс:** добавить `notes` (или `changelog_url`) в мету и в `UpdateMetaSource.parse`.
**Как проверить:** `UpdateMetaSource.selectFromMeta(...).notes` в тесте.

### [P2] `sha256` опционален: проверка целостности молча выключается
**Где:** `app/src/main/java/com/politex/update/UpdateDownloader.kt:149-158`
**Риск:** `matchesChecksum` возвращает `true` при пустом ожидаемом хеше. Если CI/ручная
публикация не заполнит `sha256`, проверка не выполняется, и остаётся только сверка сертификата.
Сама сверка (`:137-142`) сравнивает первый сертификат установленного приложения с первым
сертификатом APK — это защищает от подмены (чужой ключ не совпадёт), но не проверяет цепочку
и не покрывает ротацию ключа (APK Signature Scheme v3 / `signingInfo.hasMultipleSigners`).
**Root cause:** «мягкий» дефолт ради обратной совместимости.
**Фикс:** требовать непустой `sha256` в мете; для `apkContentsSigners.size > 1` — отклонять
(`ApkRejection.SIGNATURE`) либо сверять весь набор.
**Как проверить:** `UpdatePackageValidationTest`/`UpdateDownloadProgressTest` — кейс «пустой
sha256» сейчас ожидает успех; добавить кейс «мультиподпись».

### [P2] URL обновления не привязан к домену GitHub
**Где:** `app/src/main/java/com/politex/update/UpdateMeta.kt:31-32` (только `https://`)
**Риск:** при компрометации `update-meta.json` (или аккаунта, отдающего raw) клиент скачает APK
с любого хоста. Единственный барьер — совпадение сертификата, т.е. атакующий с чужим ключом
получит `SIGNATURE`-reject, но клиент всё равно сходит на произвольный домен (утечка IP/UA,
BEACON-эффект). Плюс `apk_name` не сверяется с URL.
**Root cause:** валидация ограничена схемой.
**Фикс:** allowlist хостов (`github.com`, `objects.githubusercontent.com`,
`raw.githubusercontent.com`), проверка, что путь начинается с `/F-708/moy-polytech/releases/`.
**Как проверить:** unit-тест `UpdateMetaSource.parse` с `apk: "https://evil.example/x.apk"` —
сейчас парсится успешно.

### [P2] CI: JDK 17 при локальном JBR 21, нет `fetch-depth: 0`, триггер только `main/master`
**Где:** `.github/workflows/build-apk.yml:4-5`, `:15`, `:17-20`; `gradle/wrapper/gradle-wrapper.properties` (Gradle 9.3.1), `gradle/libs.versions.toml:2` (AGP 9.1.1)
**Риск:** (а) `actions/checkout@v4` без `fetch-depth: 0` — `git describe` не увидит теги, т.е.
даже будь релизный workflow, версия станет `0.0.0-dev`/`1`; (б) рабочая ветка `v2.4-stable`
не входит в `branches: [main, master]` — пуш в неё CI не запускает; (в) JDK 17 против
`jbr-21` из `CLAUDE.md`/`mpk-build`: расхождение toolchain между CI и локалью — источник
«у меня собирается».
**Root cause:** каркасный workflow, не обновлявшийся под релизную схему.
**Фикс:** `fetch-depth: 0`, `branches: ['**']` или явный список, `java-version: '21'`,
`gradle/actions/setup-gradle` с кэшем.
**Как проверить:** `gh workflow view build-apk.yml`.

### [P2] R8: комментарий про enum устарел; реальное name-хранилище — только `MaterialKind`
**Где:** `app/proguard-rules.pro:9-17`; `app/src/main/java/com/politex/ui/theme/AppTheme.kt:151`;
`app/src/main/java/com/politex/util/BellCountdownNotifier.kt:44,59,76`;
`app/src/main/java/com/politex/util/UpdateChannelStore.kt:13-15`;
`app/src/main/java/com/politex/util/UiScaleStore.kt:11-12`;
`app/src/main/java/com/politex/data/local/entity/MaterialItemEntity.kt:49`
**Риск:** правило `-keepclassmembers enum * { ... <fields>; }` само по себе адекватно, но
обоснование в комментарии неверно: настройки хранятся **не** по `it.name`, а по строковому
полю `id` (`AppTheme.id`, `Scope.id`, `Theme.id`, `Style.id`, `UpdateChannel.wire`,
`UiScale.factor`). Единственное место, где имя константы реально попадает в хранилище, —
`MaterialKind.valueOf(kind)` в Room-колонке (`MaterialItemEntity.kt:49`), причём обёрнуто в
`runCatching{}.getOrDefault(FILE)`: если R8 когда-нибудь переименует константы, **пользователь
не увидит краша** — материалы тихо превратятся в FILE. Такая поломка не диагностируется.
**Root cause:** правило и комментарий писались под старую схему хранения; тест на «имена
констант после R8» отсутствует.
**Фикс:** 1) обновить комментарий; 2) для enum, попадающих в Room/prefs по имени, добавлять
`-keepnames enum com.politex.data.local.entity.MaterialKind { *; }`; 3) в релизный чек-лист —
smoke на release-APK (открыть «Материалы» с заранее созданной записью `IMAGES`).
**Как проверить:** после `assembleStandardRelease` —
`grep -n "MaterialKind" app/build/outputs/mapping/standardRelease/mapping.txt` (должны быть
исходные имена констант) и ручной smoke на release-сборке.

### [P2] Метаданные/документация расходятся с кодом
**Где:** `metadata.json:1-7` (артефакт AI Studio: `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`,
`requestFramePermissions`), `CHANGELOG.md:3` (последняя запись — 2.2, при тегах до v2.26),
`hs_err_pid253520.log` (130 КБ, не в `.gitignore`), `tools/students-original.csv`
**Риск:** `metadata.json` не влияет на сборку, но описывает несуществующую Gemini-интеграцию
(Firebase/AI удалены — `app/build.gradle.kts:205-207`) и сбивает с толку при аудите
«что за секреты в проекте». `hs_err_pid*.log` — кандидат на случайный коммит. `CHANGELOG`
не отражает 20+ выпущенных версий.
**Root cause:** остатки экспериментов и ручное ведение журнала.
**Фикс:** удалить `metadata.json` и `hs_err_*.log`, добавить `hs_err_pid*.log` в `.gitignore`,
генерировать `CHANGELOG` из тегов/коммитов.
**Как проверить:** `git ls-files metadata.json`; `git status --porcelain | grep hs_err`.

### [P3] `admin`/`adminplus` не переопределяют `app_name`, у adminplus нет своих `res`
**Где:** `app/src/admin/res/` (нет `values/`), `app/src/adminplus/res/` (пусто),
`app/src/teacher/res/values/strings.xml` (есть)
**Риск:** `admin` и `adminplus` используют `app_name` из `main` («Мой Политех») — в лончере
рядом со студенческой сборкой появляются два приложения с одинаковым названием (различаются
только `applicationId` и иконкой из `src/admin/res`). Для проверяющего/куратора это путаница,
для скриншотов и инструкций — риск «нажал не то».
**Root cause:** переопределение названия сделано только для teacher.
**Фикс:** `app/src/admin/res/values/strings.xml` → «Мой Политех — Админ», `adminplus` → «… — Admin+».
**Как проверить:** `aapt2 dump badging app-admin-release.apk | grep application-label`.

### [P3] `sourceSets` дублируют один каталог в `java` и `kotlin`
**Где:** `app/build.gradle.kts:150-171`
**Риск:** `src/owner/java` и `src/admin/java` подключены и как `java.srcDir`, и как
`kotlin.srcDir`. Сейчас работает, но при появлении `.java`-файла в этих каталогах возможны
двойной учёт/предупреждения KSP, а `admin`/`adminplus` получают разный набор каталогов
(`adminplus` дополнительно `res`/`assets` из admin) — расхождение легко забыть при новом флейворе.
**Root cause:** исторический обход «Kotlin-файлы в java-каталоге».
**Фикс:** оставить только `kotlin.srcDir` (Kotlin-плагин компилирует и `.java` из своих каталогов
не собирает — при необходимости добавить отдельный `java.srcDir("src/owner/java")` лишь когда
там появятся `.java`), либо перенести в `src/owner/kotlin`.
**Как проверить:** `./gradlew :app:compileAdminReleaseKotlin --info | grep -i "duplicate"`.

### [P3] Две семантики сравнения версий в одном пакете
**Где:** `app/src/main/java/com/politex/update/AppUpdateChecker.kt:56-78,106-114`
(`parseLatestRelease` → `UpdateVersions.isNewer` по именам) и
`UpdateMeta.kt:72-133` (`selectFromLatestRelease` → `versionCodeOf` по кодам)
**Риск:** продовый путь (`UpdateCheckController`) сравнивает **коды**, а legacy-путь
`AppUpdateChecker.parseResponse` — **имена**. При двухчастных тегах (`v2.26`) и формуле
`M*10M` эти сравнения расходятся: `isNewer("2.2.0","2.26") == false`. Функция жива, покрыта
тестами и может быть случайно подключена — тогда обновления «перестанут находиться» без ошибок.
**Root cause:** переход с name-based на code-based не доведён до удаления старого кода.
**Фикс:** удалить `parseLatestRelease`/`parseResponse`/`isNewer` (или пометить `@Deprecated`
и вынести в тестовый source set), оставить единственную функцию сравнения.
**Как проверить:** `git grep -n "parseLatestRelease\|isNewer" -- app/src/main` → только
внутренние вызовы друг друга.

### [P3] `REQUEST_INSTALL_PACKAGES` и весь апдейтер присутствуют во всех флейворах
**Где:** `app/src/main/AndroidManifest.xml:13`, `:255-268`;
`app/build.gradle.kts:91-92` (owner/repo для всех)
**Риск:** `admin`/`adminplus`/`teacher` не обновляются (`UPDATE_ENABLED_FLAVORS = setOf("standard")`),
но декларируют «Установка неизвестных приложений» и содержат `UpdateInstallActivity` +
FileProvider. Лишнее разрешение в сборке, которую видят преподаватели, — вопрос доверия и
будущих проверок; R8 вырезает код, но манифест и permission остаются.
**Root cause:** манифест общий, гейт только в рантайме.
**Фикс:** вынести permission и `UpdateInstallActivity`/FileProvider в `src/standard/AndroidManifest.xml`.
**Как проверить:** `aapt2 dump permissions app-teacher-release.apk`.

### [P3] `secrets`-плагин остался без Firebase/AI
**Где:** `app/build.gradle.kts:8,189-193`; `.env.example` (только закомментированный `GEMINI_API_KEY`)
**Риск:** плагин генерирует `.env` из `.env.example` при сборке и добавляет шаг в конфигурацию;
`ignoreList` ссылается на `FIREBASE_APPCHECK_DEBUG_TOKEN`, которого в проекте нет. Полезной
нагрузки ноль, а `.env` в корне — ещё одна точка, где однажды окажется реальный ключ.
**Root cause:** наследие удалённой Firebase-интеграции.
**Фикс:** удалить плагин и `.env.example`, либо оставить и использовать для `OWNER_TOTP_SECRET`
(см. P1 про секрет).
**Как проверить:** `git grep -n "BuildConfig\." -- app/src/main | grep -i "gemini\|firebase"` → пусто.

---

## Матрица флейворов

| | `standard` | `admin` | `adminplus` | `teacher` |
|---|---|---|---|---|
| `applicationId` | `com.politex` | `com.politex.admin` | `com.politex.adminplus` | `com.politex.teacher` |
| Суффикс версии | — | `-admin` | `-adminplus` | `-teacher` |
| `versionCode` | общий (из тега) | общий | общий | общий |
| Доп. sourceSets | — | `src/owner/java` (java+kotlin) | `src/owner/java` + `src/admin/java` + `src/admin/assets` + `src/admin/res` | — |
| `students.csv` | нет | да (`src/admin/assets`, авто) | да (явно) | нет |
| `Application` | `MpkApplication` | `OwnerApplication` | `AdminPlusApplication : OwnerApplication` | `MpkApplication` |
| `app_name` | «Мой Политех» | «Мой Политех» (не переопределён) | «Мой Политех» | «Мой Политех — Преподавателю» |
| Иконка | main | `src/admin/res` | `src/admin/res` (своих нет) | своя |
| Манифест-оверлей | — | `tools:replace android:name` | `tools:replace android:name` | удаляет `FileViewAlias`, `NowNext*`, `TodaySchedule*`; добавляет 2 teacher-виджета |
| Автообновление | **включено** (`UPDATE_ENABLED_FLAVORS`) | выкл. | выкл. | выкл. |
| `REQUEST_INSTALL_PACKAGES` | да | да (лишнее) | да (лишнее) | да (лишнее) |
| Unit-тесты | `test` (228 файлов) | `test` + `testOwner` | `test` + `testOwner` | `test` |
| Публикуется | да | нет | нет | нет |

Факты по строкам: `app/build.gradle.kts:65-85` (флейворы), `:150-171` (sourceSets),
`app/src/*/AndroidManifest.xml`, `app/src/teacher/res/values/strings.xml`,
`AppUpdateChecker.kt:28`.

**Что тихо ломается при добавлении пятого флейвора:** (1) нет `applicationIdSuffix` — коллизия
с `standard`, установка «поверх» и путаница в CI; (2) нет `testNewflavor` в `sourceSets` —
`src/testOwner` не подключится, owner-код не протестируется; (3) `UPDATE_ENABLED_FLAVORS`
не содержит его — обновления молча выключены; (4) релизный workflow (когда появится) собирает
только `standard` — новый APK никто не выпустит.

---

## Чек-лист релиза этой волны

Предусловия (Git Bash, Windows):
```bash
cd "E:/MPK app/MPK-app-2.1"
export JAVA_HOME=/c/Users/F708/.jdks/jbr-21.0.10
export ANDROID_HOME=/c/Users/F708/AppData/Local/Android/Sdk
```

**1. Зафиксировать версию (блокирует всё остальное).**
```bash
git status --porcelain            # должно быть пусто
git describe --tags --exact-match HEAD || echo "НЕТ ТЕГА НА HEAD"
# решение: поставить трёхчастный тег, напр. v2.27.0 (или v2.27.0-preview.1)
git tag v2.27.0 && git describe --tags --match "v[0-9]*.[0-9]*.[0-9]*" --abbrev=0   # → v2.27.0
```
Ожидаемо: `2.27.0` → `22 700 999`. Если `describe` вернул не тот тег — **не собирать**.

**2. Гейт тестов (все флейворы, не только standard).**
```bash
./gradlew :app:testStandardDebugUnitTest :app:testAdminDebugUnitTest :app:testAdminplusDebugUnitTest --no-daemon
```

**3. Release-сборка + проверка подписи, версии и ПДн.**
```bash
./gradlew :app:assembleStandardRelease :app:assembleAdminRelease :app:assembleTeacherRelease --no-configuration-cache
APK=app/build/outputs/apk/standard/release/app-standard-release.apk
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)   # проверить, что в нём есть aapt2/apksigner
"$BT"/aapt2 dump badging "$APK" | grep -E "package:|application-label"
"$BT"/apksigner verify --print-certs "$APK"                      # CN = upload-ключ, не Android Debug
unzip -l "$APK" | grep -c students.csv          # 0
unzip -l "$APK" | grep -c 'owner/AccessCode'    # 0
sha256sum "$APK"
```
Ожидаемо: `versionCode='22700999' versionName='2.27.0'`, подпись upload-ключа, оба счётчика `0`.

**4. R8-санити: карта и enum-имена.**
```bash
ls -l app/build/outputs/mapping/standardRelease/mapping.txt
grep -c "MaterialKind -> " app/build/outputs/mapping/standardRelease/mapping.txt   # >0, имена не obfuscated
grep -c "AppTheme -> " app/build/outputs/mapping/standardRelease/mapping.txt
```
Плюс ручной smoke release-APK: «Настройки → тема» (переключить новую тему), «Материалы»
(открыть ранее сохранённый файл), «Другое» (проверка обновлений). Именно эти три места зависят
от enum/prefs/R8.

**5. Публикация и мета.**
```bash
git push origin main && git push origin v2.27.0
# после релиза:
curl -s https://raw.githubusercontent.com/F-708/moy-polytech/main/update-meta.json
```
Проверить в мете: `channel` соответствует выпуску, `version_code` = `22700999`,
`apk` ведёт на `/releases/download/v2.27.0/…apk`, `sha256` совпадает с шагом 3.
Если выпущен **preview** — помнить про P1: stable-канал замолчит до следующего stable-релиза
(сейчас это лечится только ручной правкой меты).

**6. Пост-релиз (5 минут).** Установить предыдущую версию с телефона → «Другое» → проверить,
что обновление **находится**; скачать, убедиться, что установка не отклонена
(`ApkRejection` в логах); проверить, что `versionName` в «О приложении» = `2.27.0`.

---

## R8/настройки: что добавить при новых enum темы

Сейчас (факт): тема хранится по строковому `id` (`AppTheme.kt:151`, `entries.firstOrNull { it.id == id } ?: COLLEGE`),
новые константы безопасны для уже сохранённых настроек; неизвестный id → `COLLEGE`.
`-keepclassmembers enum *` (`proguard-rules.pro:12-17`) сохраняет имена констант, но **не**
является тем, что защищает темы.

При добавлении темы/enum в этой волне:
1. **Не** добавлять `-keep enum com.politex.ui.theme.AppTheme { *; }` — не нужно и маскирует
   реальные проблемы; правило про enum уже покрывает `values()/valueOf()/entries()` и поля.
2. **Обязательно**: новый `id` — новая строка, старые `id` не переименовывать (иначе у части
   пользователей тема «сбросится» в `COLLEGE` — тихо, без краша). Тест:
   `assertEquals(entries.size, entries.map { it.id }.toSet().size)` и `assertEquals(COLLEGE, AppTheme.from("nope"))`.
3. **Если enum начинает храниться по имени** (`name`/`valueOf`) — сразу `-keepnames enum <FQN> { *; }`
   и миграция prefs (`read old name → write id`), иначе после R8 чтение вернёт дефолт.
4. **Если enum попадает в Room-колонку** (как `MaterialKind`, `MaterialItemEntity.kt:49`) —
   `@TypeConverter` на `id`-строку + `-keepnames`; `runCatching{}.getOrDefault(...)` запрещён:
   он превращает ошибку obfuscation в тихую потерю данных.
5. **Ресурсы темы**: новые `drawable`/`color` при `isShrinkResources = true` выживут только при
   статической ссылке (`R.color.x` в `when`) — не собирать имена тем через `getIdentifier()`.
6. **Roborazzi**: эталоны новых тем писать в `build/screenshots` (уже так:
   `MapScreenShotTest.kt:35`), не в `app/src/test/resources`; сравнение эталонов в CI сейчас
   не настроено — добавить `roborazzi { }` + `verifyRoborazziDebug` или явно зафиксировать,
   что скриншоты — не гейт.
7. **Проверка после R8** (обязательный пункт чек-листа): переключить каждую тему в release-APK
   и перезапустить приложение — если тема «слетает» после рестарта, значит id не сохранился.

---

## Чего не хватает в CI (сводно)

1. Релизного workflow (`release.yml`) — сборка, подпись, `sha256`, `update-meta.json`, `prerelease`.
2. `fetch-depth: 0` в checkout (иначе `git describe` не видит теги → `versionCode = 1`).
3. Сборки **release** хотя бы `standard` — иначе R8/shrink/подпись не проверяются до выпуска.
4. Компиляции/тестов `admin`, `adminplus`, `teacher` (сейчас `src/owner`, `src/adminplus` в CI не собираются).
5. Гейта `students.csv` и `owner/AccessCode` в APK (автоматически, а не «помнит человек»).
6. `apksigner verify --print-certs` + сравнение с ожидаемым отпечатком.
7. `lintRelease` (`lint` не настроен и не запускается).
8. Публикации `mapping.txt` артефактом (ретрейс релизных крашей).
9. Проверки `update-meta.json` после публикации (schema + `version_code`/`sha256`).
10. JDK 21 вместо 17 и триггера на рабочую ветку (`v2.4-stable` сейчас вне `branches`).