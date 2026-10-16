# Публикация релиза

Релиз состоит из двух шагов: APK в GitHub Releases и обновление `update.json`.
Без второго автообновление у пользователей не сработает.

## 1. Сборка

```bash
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android/sdk
./gradlew :app:testStandardDebugUnitTest    # все тесты зелёные
./gradlew :app:assembleStandardRelease
```

Проверка, что персональные данные не попали в standard-сборку:

```bash
unzip -l app/build/outputs/apk/standard/release/app-standard-release.apk | grep -c students.csv
# ожидается 0
```

## 2. GitHub Release

Загрузить `app-standard-release.apk` в релиз с тегом `v<версия>`
(например `v2.1`). Имя файла в релизе должно быть ровно
`app-standard-release.apk` — на него ссылается `update.json`.

Прямая ссылка на последний релиз, которую использует приложение:

```
https://github.com/F-708/MPK-app-2.0/releases/latest/download/app-standard-release.apk
```

## 3. update.json

Манифест автообновления лежит в корне репозитория и читается по адресу

```
https://raw.githubusercontent.com/F-708/MPK-app-2.0/main/update.json
```

Формат (ключ — имя flavor; `standard` — единственная линия с автообновлением):

```json
{
  "standard": {
    "versionCode": 21000,
    "versionName": "2.1",
    "url": "https://github.com/F-708/MPK-app-2.0/releases/latest/download/app-standard-release.apk",
    "notes": "Что нового — текст попадёт в уведомление"
  }
}
```

Правила:

- `versionCode` должен быть строго больше текущего у пользователей — иначе
  приложение сочтёт, что обновления нет;
- `versionCode` обязан совпадать со `versionCode` в `app/build.gradle.kts`
  загруженной сборки;
- после изменения `update.json` нужен коммит в `main` — raw-ссылка отдаёт
  содержимое ветки, не тега;
- raw-контент GitHub кэшируется до 5 минут — обновление может стать видимым
  не мгновенно.

## Порядок версий

`versionCode` — целое, растёт монотонно (`21000` = 2.1). `versionName` —
человекочитаемое, отображается в «О программе» и в уведомлении об обновлении.

## Что не публикуется

`keystore.properties`, `*.jks`, `*.apk` в рабочем дереве, `students.csv`
и любые персональные данные — в `.gitignore`. admin- и teacher-сборки
в публичный репозиторий не выкладываются.
