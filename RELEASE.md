# Публикация релиза

Релиз = git-тег в публичном репозитории `moy-polytech`. CI сам собирает APK,
публикует GitHub Release и обновляет `update-meta.json` в ветке `main`.
Вручную версии больше не прописываются: `versionName`/`versionCode`
вычисляются из тега при сборке.

## Версии из тега

Формат тега: `vM.m.p` (стабильный) или `vM.m.p-preview.N`, N = 1..998
(превью). При сборке читается ближайший достижимый тег
(`git describe --tags --match "v[0-9]*.[0-9]*.[0-9]*" --abbrev=0`),
логика — в `app/build.gradle.kts`.

- `versionName` = `M.m.p` или `M.m.p-preview.N` (без `v`);
- `versionCode` = `M*10 000 000 + m*100 000 + p*1 000 + N`, где для
  стабильного релиза N = 999, для превью — номер превью (1..998).

Примеры:

| Тег | versionName | versionCode |
|---|---|---|
| `v2.0.0` | 2.0.0 | 20000999 |
| `v2.0.1-preview.2` | 2.0.1-preview.2 | 20001002 |
| `v2.1.0` | 2.1.0 | 20100999 |
| `v3.0.0` | 3.0.0 | 30000999 |

Границы: M ≤ 213, m ≤ 99, p ≤ 99, N ≤ 999, `versionCode` ≤ Int.MAX
(2 147 483 647) — те же, что в клиентской формуле `versionCodeOf`
(`app/src/main/java/com/politex/update/UpdateVersionCode.kt`). Тег вне
границ считается невалидным — срабатывает fallback. Превью всегда меньше
релиза той же версии, поэтому порядок обновлений не ломается.

Fallback: тега нет или git недоступен → `versionName = "0.0.0-dev"`,
`versionCode = 1`. Сборка возможна (локальная отладка), но публиковать её
нельзя: поверх реальной версии она не установится.

Суффиксы flavor сохраняются: admin → `-admin`, teacher → `-teacher`
добавляются к `versionName`. Формула сборки задокументирована и проверена
тестом `ReleaseVersionCodeFormulaTest`, включая сверку с клиентской
`versionCodeOf` на канонических тегах (`app/src/test/java/com/politex/update/`).

## Порядок релиза

1. Синхронизировать main-репозиторий с публичным `F-708/moy-polytech`
   (включая `app/build.gradle.kts` — иначе CI соберёт APK с fallback-версией)
   и запушить `main`.
2. Создать и запушить тег в публичном репо:

   ```bash
   git tag v2.3.0            # или v2.3.0-preview.1
   git push origin v2.3.0
   ```

3. Workflow `.github/workflows/release.yml` (триггеры: push тега `v*` и
   `workflow_dispatch`) выполнит:
   - checkout с `fetch-depth: 0` — без него `git describe` не увидит теги;
   - юнит-тесты и `:app:assembleStandardRelease`;
   - переименование APK в `moy-polytech-<версия>.apk` и публикацию
     в GitHub Release; для превью: `prerelease: true`, имя `<тег> (превью)`;
   - генерацию `update-meta.json` в корне репо и коммит в `main`
     от `github-actions[bot]` с сообщением `chore: update-meta <тег>`.

Защита от цикла: workflow срабатывает только на теги и ручной запуск,
коммит `update-meta.json` в `main` его не перезапускает.

`workflow_dispatch` без тега берёт ближайший тег от HEAD — удобно для
перепубликации, если тег уже есть, а релиз не создался.

## update-meta.json

Лежит в корне публичного репозитория, обновляется CI после каждого релиза:

```json
{
  "channel": "stable",
  "version": "2.3.0",
  "version_code": 20300999,
  "apk": "https://github.com/F-708/moy-polytech/releases/download/v2.3.0/moy-polytech-2.3.0.apk",
  "apk_name": "moy-polytech-2.3.0.apk",
  "sha256": "<хеш APK>",
  "published_at": "2026-10-22T12:00:00Z",
  "min_version_code": 20300999
}
```

- `channel`: `stable` или `preview`;
- `apk`: полная https-ссылка на ассет Release — клиентский парсер
  (`UpdateMetaSource.parse`) принимает только URL, не имя файла;
- `apk_name`: имя файла в Release (справочно);
- `version_code`: то же значение, что зашито в APK (формула выше);
- `sha256`: контроль целостности APK из Release;
- `min_version_code`: минимальная поддерживаемая версия клиента; CI ставит
  её равной `version_code`, при необходимости форсировать обновление
  значение можно поднять отдельным коммитом.

Прямой адрес (кэш raw — до 5 минут):

```
https://raw.githubusercontent.com/F-708/moy-polytech/main/update-meta.json
```

## Автообновление в приложении

Приложение (flavor `standard`) читает `update-meta.json` по raw-ссылке выше
(`AppUpdateChecker` / `UpdateMetaSource`), резервный путь — GitHub API
`releases`. Клиент на канале `stable` (по умолчанию) принимает только
`channel: stable`; канал `preview` включается в настройках приложения
(`UpdateChannelStore`). Обновление предлагается, когда `version_code` в мете
больше текущего в APK. В релизе должен лежать ровно один APK (standard):
API-путь берёт первый `.apk`-ассет, не являющийся контрольной суммой.

## Локальная сборка

```bash
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android/sdk
./gradlew :app:testStandardDebugUnitTest    # все тесты зелёные
./gradlew :app:assembleStandardRelease
```

Версия возьмётся из ближайшего тега main-репозитория; без подходящего тега —
fallback `0.0.0-dev` (1).

Проверка, что персональные данные не попали в standard-сборку:

```bash
unzip -l app/build/outputs/apk/standard/release/app-standard-release.apk | grep -c students.csv
# ожидается 0
```

## Что не публикуется

`keystore.properties`, `*.jks`, `*.apk` в рабочем дереве, `students.csv`
и любые персональные данные — в `.gitignore`. admin- и teacher-сборки
в публичный репозиторий не выкладываются.
