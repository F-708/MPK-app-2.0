# Ревью: Сборка, безопасность, производительность, конфигурация

_Дата: 2026-10-01. Область: build-security-perf. Ревьюер: босс (лично)._

## Резюме и вердикт

Сборка настроена НА УДИВЛЕНИЕ грамотно для DeepSeek-генерации: R8+shrink дали 23.7→4.8 МБ, `-keep` для enum на месте (критичная конвенция соблюдена), flavors изолируют students.csv, firebase/retrofit/moshi вычищены, конфигурация-кэш включён. Проблемы: устаревшие composeBom/okhttp (BSP-01/02), versionCode не соответствует выпущенным APK (BSP-03), нет baseline profile, нет spotless/ktlint (стиль плавает — см. 08-architecture), navigation-compose объявлен в каталоге но закомментирован (пригодится для ARC-01).

## Критичные проблемы

_(нет — сборка не ломает функциональность; всё ниже high и слабее)_

## High

### BSP-01 [high/security] okhttp 4.10.0 — известные CVE, версия 2022 года
- **Файл**: `gradle/libs.versions.toml:33`.
- OkHttp 4.10 уязвим (CVE-2023-3635 GzipSource DoS и др. в <4.11/4.12). Всё сетевое взаимодействие приложения (расписание, портал) — на нём.
- **Фикс**: поднять до 4.12.0 (API-совместим с 4.x, drop-in). Проверить `LiveNetworkSmokeTest` после.

### BSP-02 [high/compat] composeBom 2024.09.00 при compileSdk 36 — отставание на год
- **Файл**: `gradle/libs.versions.toml:12`.
- BOM сентября 2024; проект таргетит SDK 36 (Android 16) и AGP 9.1.1. Старые compose-ui/material3 могут некорректно вести себя на новых API (edge-to-edge принудителен с SDK 35, predictive back, и т.д.). Плюс material-icons-**extended** тянет ~2 МБ неиспользуемых иконок (см. BSP-06).
- **Фикс**: поднять BOM до текущего (2026.xx), прогнать UiRenderSmokeTest/Roborazzi — скриншот-тесты поймают визуальные регрессии. ОТДЕЛЬНОЙ волной от баг-фиксов (обновление Compose меняет визуал).

### BSP-03 [high/process] versionCode=20000, versionName="2.0" — не соответствуют выпущенным v3.x
- **Файл**: `app/build.gradle.kts:32-33`.
- В корне проекта лежат APK `moy-polytech-v3.3-release.apk` и т.д., а в gradle — 2.0/20000. Либо версии поднимали вручную перед сборкой (и забыли закоммитить), либо APK собраны не из этого состояния. Риск: выложить в стор сборку с versionCode МЕНЬШЕ уже опубликованного → отказ публикации/откат.
- **Фикс**: синхронизировать versionCode/versionName с реально последней выпущенной (v3.3 → versionName "3.3", versionCode ≥ 20303 или другая单调 схема); зафиксировать правило в mpk-build скилле: «перед release — проверить versionCode больше предыдущего».

## Medium

### BSP-04 [medium/performance] Нет baseline profile — холодный старт медленнее на 20-30%
- **Файлы**: `app/src/*/baseline-prof.txt` отсутствует, плагин baselineprofile не подключён.
- Для Compose-приложения baseline profile — самый дешёвый выигрыш стартовой производительности (AOT горячих путей). Roborazzi-тесты уже гоняют UI — профиль можно сгенерировать из них.
- **Фикс**: плагин `androidx.baselineprofile` + генерация через instrumented-тест (нужен эмулятор; можно отложить до CI). Альтернатива-минимум: вручную написать baseline-prof.txt с классами стартового пути (MainActivity, MainScreen, ScheduleScreen, theme) — даёт часть выигрыша без эмулятора.

### BSP-05 [medium/security] Автобэкап включён без правил — в облако уходит ВСЁ
- **Файлы**: манифест:15-17 (`allowBackup=true`, `fullBackupContent`, `dataExtractionRules`), `backup_rules.xml` = пустой `<full-backup-content />`, `data_extraction_rules.xml` = `<cloud-backup />` без include/exclude.
- Комментарий в backup_rules.xml врёт: «Резервируются настройки группы, уведомлений и учебные задания» — на деле правил нет, бэкапится всё, включая Room-базу (в admin — `teacher_lessons`, кэш расписания; `student_tasks` — ок, их и хотим). В admin-сборке база может содержать производные от students.csv данные (избранное преподавателей — по имени). Персональные данные учащихся в БД не хранятся (CSV — ассет, ассеты не бэкапятся) — критичной утечки нет, но принцип минимальности нарушен.
- **Фикс**: явные правила — include только `mpk_widget_prefs.xml`, тему, и БД `mpk_schedule.db` (student_tasks); exclude кэш-таблицы. Десять строк XML.

### BSP-06 [medium/size] material-icons-extended — ~2-3 МБ dex ради десятка иконок
- **Файл**: `app/build.gradle.kts` (`implementation(libs.androidx.compose.material.icons.extended)`).
- R8 должен вырезать неиспользуемое, но icons-extended известен неполным shrunk-результатом при reflective-доступе. APK 4.8 МБ — вероятно, уже вырезано, но проверить mapping легко.
- **Фикс**: заменить extended на точечные иконки из `material-icons-core` или собственные drawable (те же `ic_mpk_emblem`); если R8 и так вырезает — оставить как есть (проверить по mapping.txt release-сборки).

### BSP-07 [medium/style] Нет spotless/ktlint — «подгон под один стиль» нечем гарантировать
- Заказчик просил единый стиль (см. 08-architecture ARC: смешение qualify/импортов, BOM, дубли). Без линтера стиль будет разъезжаться после каждой правки.
- **Фикс**: `spotless` с `ktlint()` (одна секция в build.gradle.kts) + задача `spotlessApply`. Первый прогон — отдельным коммитом «форматирование, без изменения логики» (иначе смешается с баг-фиксами в blame).

### BSP-08 [medium/build] `org.gradle.workers.max=4` при 12 CPU и `in-process` компиляция Kotlin
- **Файл**: `gradle.properties`.
- `kotlin.compiler.execution.strategy=in-process` (обход проблемы с демоном) + 4 воркера на 12-ядерной машине — сборка медленнее возможного. Понятно почему (стабильность > скорость на машине заказчика), но при локальных итерациях можно `workers.max=8`.
- **Фикс**: не трогать по умолчанию (стабильность); знать, что есть рычаг. НИЗКИЙ приоритет.

## Low

### BSP-09 [low/cleanup] Мёртвые записи в libs.versions.toml
- retrofit/moshi/firebase/camera/play-services/accompanist/datastore объявлены, но закомментированы в build.gradle. `navigationCompose = 2.8.9` объявлен и НЕ подключён — понадобится для ARC-01 (Navigation-Compose миграция): подключить тогда, сейчас не трогать.
- **Фикс**: почистить каталог при миграции Navigation; закомментированные зависимости удалить (git помнит).

### BSP-10 [low/process] .env/secrets-плагин настроен, но секретов нет
- `secrets { propertiesFileName = ".env" }` + `.env.example` с закомментированным GEMINI_API_KEY — наследие шаблона AI Studio. Вреда нет (BuildConfig не генерирует ключей), но мёртвый конфиг.
- **Фикс**: удалить secrets-плагин и .env.example, пока кто-нибудь не положил туда реальный секрет «на всякий случай».

## Безопасность — проверка конвенций (статус на 2026-10-01)

| Конвенция | Статус |
|---|---|
| students.csv только в admin | ✅ `app/src/admin/assets/students.csv` (819 КБ), в main/standard — только group_codes.csv. Гейт `unzip -l ... grep -c students.csv = 0` — прогнать на следующей release-сборке |
| R8 -keep enum | ✅ proguard-rules.pro содержит `-keepclassmembers enum *` с values()/valueOf()/entries()/fields — покрывает CountdownMode/WidgetStyle/Scope/Theme/Style |
| Manifest-компоненты под R8 | ✅ -keep для AppWidgetProvider/BroadcastReceiver/Service/Activity/ContentProvider |
| Разрешения минимальны | ✅ INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, FOREGROUND_SERVICE(+SPECIAL_USE). Лишних нет. SCHEDULE_EXACT_ALARM — см. BEL-02/WID-02 (runtime-выдача на 14+) |
| FGS-тип для targetSdk 34+ | ✅ specialUse + PROPERTY_SPECIAL_USE_FGS_SUBTYPE |
| Секреты в коде | ✅ нет (сеть на публичные URL без авторизации) |
| allowBackup | ⚠️ см. BSP-05 |

## Производительность (срез сборки)

- R8 full mode (дефолт AGP 8+/9) ВКЛЮЧЁН — `-keep` правила критичны и присутствуют. ✅
- `isCrunchPngs`/`isShrinkResources` ✅ (O9, APK 4.8 МБ).
- `dependenciesInfo.includeInApk=false` ✅.
- `nonTransitiveRClass=true`, configuration-cache, caching ✅.
- Baseline profile ❌ (BSP-04).
- Рантайм-производительность — в отчётах 04 (WID-04), 07 (DAT-01 индексы), 08 (ARC-03 main-thread IO).

## Что работает хорошо (не ломать)

- Провал релизной сборки без ключа с предупреждением вместо тихой debug-подписи (signingConfig, build.gradle:60-69) — правильный выбор.
- O9-чистка зависимостей (firebase/retrofit/moshi удалены с комментариями «вернуть при»).
- `testOptions.isIncludeAndroidResources = true` — Robolectric работает корректно.
- `singleTop` + onNewIntent для виджет-тапов.
- debug.keystore-логика с двумя кандидатами — не ломает сборку на свежей машине.

## Пробелы

- Нет CI (`.github/` пуст или минимален — проверить отдельно): гейты (тесты, students.csv=0, versionCode) держатся на дисциплине, не на пайплайне. Кандидат: GitHub Actions с `testStandardDebugUnitTest` + assemble + grep-гейт (см. GITHUB-HOSTING.md в корне — план уже написан).

## Рекомендуемый порядок

1. **BSP-01** (okhttp 4.12) + **BSP-03** (versionCode) — дёшево, закрывают риск.
2. **BSP-05** (backup rules) — 10 строк XML.
3. **BSP-07** (spotless) — отдельным коммитом форматирования.
4. **BSP-02** (composeBom) — отдельной волной со скриншот-тестами.
5. **BSP-04** (baseline profile) — когда появится эмулятор/CI.
6. **BSP-06/08/09/10** — попутно.
