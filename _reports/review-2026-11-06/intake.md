# Приём внешних файлов / «Открыть с помощью» — ревью домена intake

Проект: `E:\MPK app\MPK-app-2.1`, пакет `com.politex`, ветка `v2.4-stable`.
Режим: read-only. Ничего не собрано/не запущено (гейты — у Лида).
Дата: 2026-11-06.

**Вердикт одной фразой:** приложение объявляет себя обработчиком *любого* `content://` файла (это делает манифест, а не код), runtime-гейт проверяет только APK, всё остальное молча копируется в хранилище и в Room, после чего показывается плашка «формат не поддерживается» — то есть заказчик видит ровно то, на что жалуется: «принимает абсолютно все файлы».

---

## 0. Как устроен путь файла (карта, чтобы фиксы не разъехались)

```
Intent VIEW/SEND/SEND_MULTIPLE
  └─ AndroidManifest.xml: activity-alias .FileViewAlias (67–138), MainActivity (42–61)
       └─ MainActivity.handleIntent (152–199) → ExternalMaterialIntake.fromIntent (53–64)
            └─ parse (66–97): блокируется ТОЛЬКО APK → SingleFile / Images
                 └─ ExternalMaterialIntake.offer → LessonsHubScreen.kt:70 claim()
                      └─ MaterialBaseScreen LaunchedEffect (235–272): лимит 50 МБ, поиск дубликата по sha256
                           └─ MaterialSubjectDialog → importMaterial (MaterialImporter 317–417)
                                └─ insertItem(kind=FILE) → finalizeFileImport (419–459) → copyToStorage (152–184)
                                     └─ MaterialBaseScreen 395–422: pdf → WebView|PdfViewer, docx → WebView|DocTextViewer,
                                        всё остальное → DocTextViewer (574–597) → loadContent == null →
                                        UnsupportedFormatPlaque (MaterialCommon 163–206)
```

Внутри приложения второй вход: `MaterialBaseScreen.kt:542` — `filePicker.launch(arrayOf("*/*"))`.

Ключевой вывод: **сейчас нет ни одного слоя, который отказывает по формату.** Манифест пропускает всё, код — всё кроме APK, вьюер потом рисует плашку. Т.е. мусор уже лежит в `files/materials` и в БД, занимает квоту и попадает в поиск.

---

## 1. Матрица поддержки (по фактическому коду)

Колонка «вьюер» — что реально отрисует контент, а не плашку.
Источники: `DocTextViewer.kt:89` (`TEXT_LIKE_EXT`), `DocTextViewer.kt:574–597` (`loadContent`), `MaterialBaseScreen.kt:395–422` (маршрутизация), `MaterialImporter.kt:70,72,133,438–442`, `MaterialWebViewer.kt:234–251`, `ImageViewer.kt:338`.

| Формат | MIME (как объявлено/ожидается) | Расш. | Как открывается сейчас | Вердикт |
|---|---|---|---|---|
| PDF | `application/pdf` | pdf | pdf.js в WebView (Chromium ≥125, `pageCount>0`, не Huawei) → fallback `PdfRenderer`; битый → `CorruptedPdfPlaque` | **принимать** |
| DOCX | `…wordprocessingml.document` | docx | docx-preview в WebView (Chromium ≥80) → fallback `DocxTextExtractor` | **принимать** |
| TXT | `text/plain` | txt | `DocTextViewer`, есть редактирование (`canEdit`, 228) | **принимать** |
| MD | `text/plain`, `text/markdown` | md | `DocTextViewer` как текст (разметка видна как есть) | **принимать** (только `.md`) |
| MD-длинное | `text/markdown` | markdown | **не открывается**: `extOf()` режет до 6 симв. → `markdo`, ни в `TEXT_LIKE_EXT`, ни в `PLAIN_TEXT_EXT` → плашка | **отклонять** либо чинить `extOf` (P1-8) |
| CSV | `text/csv` | csv | `DocTextViewer` как текст | **принимать** |
| JSON | `application/json` | json | `DocTextViewer` как текст | **принимать** |
| XML | `application/xml`, `text/xml` | xml | `DocTextViewer` как текст | **принимать** |
| LOG | `text/plain` | log | `DocTextViewer` как текст | **принимать** |
| RTF | `application/rtf`, `text/rtf` | rtf | **противоречие**: в `PLAIN_TEXT_EXT` есть (импортируется, текст в поиск пишется), в `TEXT_LIKE_EXT` нет → плашка | **отклонять** (и убрать из `PLAIN_TEXT_EXT`) |
| DOC (Word 97) | `application/msword` | doc | парсера нет; `loadContent` → null → плашка «конвертируйте в .docx» | **отклонять** (+ подсказка) |
| XLS / XLSX | `application/vnd.ms-excel`, `…spreadsheetml.sheet` | xls/xlsx | вьюера нет → плашка «открываются только внешним приложением», при этом **внешнего «открыть в другом приложении» в материалах нет вообще** (грепом: ни одного `ACTION_VIEW` по сохранённому файлу) — файл бесполезен | **отклонять** |
| ODT / ODS | `application/vnd.oasis.opendocument.*` | odt | плашка | **отклонять** |
| PPT / PPTX | `…presentation*` | ppt/pptx | плашка | **отклонять** |
| ZIP (и любой архив) | `application/zip`, `application/x-zip-compressed` | zip | плашка, файл сохранён | **отклонять** |
| APK | `application/vnd.android.package-archive` | apk | блокируется в коде (`ExternalMaterialIntake.kt:99–113`) — **молча**, без сообщения | **отклонять** (уже) + сообщение |
| Аудио (MP3, M4A, OGG, WAV) | `audio/*` | mp3 | плашка, файл сохранён | **отклонять** |
| Видео (MP4, MKV, WEBM) | `video/*` | mp4 | плашка, файл сохранён | **отклонять** |
| GIF | `image/gif` | gif | Coil **без** `coil-gif` (`build.gradle.kts:230` — только `coil.compose`) → показывается первый кадр, анимации нет | **принимать** (статичный) |
| JPG/PNG/WEBP/BMP | `image/*` | jpg/png/webp/bmp | `ImageViewer` → Coil → OK | **принимать** |
| HEIC/HEIF | `image/heic`, `image/heif` | heic/heif | minSdk 24, декодер HEIF/HEVC с API 28+ → на 24–27 `AsyncImage` (338) **без error-слота** → пустой экран без объяснения | **принимать** + error-слот (P2-13) |
| SVG | `image/svg+xml` | svg | Coil без `coil-svg` → пустой экран; либо как «текст» (xml) с мусорной разметкой | **отклонять** |
| EXE/ISO/DEX… | любые | exe | плашка, файл сохранён | **отклонять** |
| Пустой файл (0 Б) | любой | — | импорт проходит, `textContent=""`, `pageCount=0` → «Текст не найден: документ пустой…» (`DocTextViewer.kt:293–321`) | **отклонять** отдельным сообщением |

Правило, которое должно быть в коде (сейчас его нет): **принимаем ровно то, что умеем отрисовать** — `pdf`, `docx`, `txt/md/csv/log/json/xml`, изображения `jpg/jpeg/png/webp/bmp/gif` (+`heic/heif` при наличии декодера). Всё остальное — отказ до диалога выбора предмета.

---

## 2. Находки

### [P0] Манифест объявляет приём любого `content://` файла
**Где:** `app/src/main/AndroidManifest.xml:72–91` (плюс `94–137`).
**Симптом:** «Мой Политех» есть в «Открыть с помощью» для APK, ZIP, MP3, MP4, XLSX — для чего угодно. Это и есть жалоба заказчика.
**Root cause:** в Android несколько `<data>`-элементов внутри одного `<intent-filter>` комбинируются **по ИЛИ** (атрибуты внутри одного `<data>` — по И). Здесь автор написал:
```xml
<data android:scheme="content" />
<data android:mimeType="*/*" />
```
и во втором фильтре:
```xml
<data android:scheme="content" />          <!-- ← сам по себе матчит ВСЁ -->
<data android:mimeType="application/pdf" />
… <data android:pathPattern=".*\.pdf" />   <!-- ← декоративен -->
```
Элемент `<data android:scheme="content"/>` без mimeType/пути матчит **любой** `content://` Uri. Поэтому:
* первый VIEW-фильтр — «любой content-uri, любой тип» → APK проходит на уровне ОС;
* второй VIEW-фильтр («документы и картинки») фактически равен первому: перечисление MIME и все 44 `pathPattern` в нём мертвы — их «съедает» голый `scheme`;
* фильтр octet-stream (`94–137`) по той же причине не ограничен расширениями.
Классическая ловушка Android: scheme и mimeType/путь должны стоять **в одном** `<data>`.
**Фикс:** см. §3 — удалить VIEW-фильтр с `*/*`, слить `scheme`+`mimeType` (и `pathPattern`) в единые `<data>`-элементы.
**Оговорка о проверке:** семантика «несколько `<data>` = ИЛИ» — стандартное и документированное поведение (на нём же построена известная ловушка «scheme в одном `<data>`, host в другом — приложение ловит все http-ссылки»), но перед правкой её стоит подтвердить одной командой на устройстве:
`adb shell cmd package query-activities -a android.intent.action.VIEW -d content://x/y -t application/vnd.android.package-archive | grep politex`
— если alias попадает в выдачу при типе APK, ИЛИ подтверждён. **Патч ниже верен при любой из двух семантик** (при И-семантике он тоже нужен: VIEW-фильтр `*/*` и `msword/ms-excel` всё равно лишние).
**Тест:** переписать `ManifestFileViewAliasTest` (см. P0-2) — проверять, что в манифесте **нет** `*/*` и **нет** `<data>` с `scheme`, но без `mimeType`/`pathPattern`.

### [P0] Тест закрепляет дыру как контракт — без его правки любой фикс манифеста краснеет
**Где:** `app/src/test/java/com/politex/ManifestFileViewAliasTest.kt:69–80` (`VIEW content star-star с DEFAULT висит на alias`).
**Симптом:** тест **требует** наличия VIEW-фильтра `content` + `*/*` без `pathPattern`. То есть «принимаем всё» зафиксировано как ожидаемое поведение.
**Root cause:** тест писался под готовую реализацию, а не под продукт. Это ровно тот случай, когда ИИ-код защищает ИИ-баг.
**Фикс:** инвертировать тест (тот же файл):
* `alias не объявляет wildcard mimeType` → ни в одном `<data>` фильтра alias нет `*/*`;
* `каждый data-элемент alias ограничен scheme и mimeType/путём` → нет «голых» `<data android:scheme="content"/>`;
* `alias не принимает архивы, аудио и APK` → `application/zip`, `audio/*`, `video/*`, `application/vnd.android.package-archive` отсутствуют в фильтрах alias;
* оставить как есть: `file схема не принимается alias` (83–90), `на alias остался только VIEW` (92–98), `teacher overlay удаляет FileViewAlias` (124–129).
**Тест:** этот же переписанный класс — он и есть гейт манифеста (образец парсинга XML уже в файле).

### [P0] Runtime-гейт: блокируется только APK, дальше — «пропускаем всё»
**Где:** `ui/screens/materials/ExternalMaterialIntake.kt:70–97` (`parse`), `99–113` (`isApk`).
**Симптом:** ZIP/MP3/DOC/XLSX/EXE → `PendingMaterial.SingleFile` → диалог предмета → копия в `files/materials` → строка в Room → плашка «формат не поддерживается» **после** импорта.
**Root cause:** проверка implemented как чёрный список из одной позиции; доверие к `intent.type`/`resolver.getType()` без sniff; нет вайтлиста форматов.
**Фикс:** вайтлист-гейт `MaterialFormats.isSupported(...)` (§4) в двух точках: (1) до диалога предмета в `MaterialBaseScreen` (там уже есть состояние `checking` — «Проверяем материал…»), (2) внутри `MaterialImporter.importMaterial` перед `insertItem` (defense in depth, чтобы в БД не появлялось неподдерживаемого ни из какого пути).
**Тест:** `MaterialFormatGateTest` — кейсы в §6.

### [P0] Кнопка «Файл» внутри приложения: `arrayOf("*/*")` + подпись, обещающая XLS
**Где:** `ui/screens/MaterialBaseScreen.kt:540–544`; `ui/screens/materials/MaterialDialogs.kt:80`.
**Симптом:** пикер показывает APK/ZIP; подпись «PDF, DOCX, XLS и другие документы» обещает XLS, который вьюера не имеет.
**Root cause:** пикер не отражает реальные возможности; текст писался «на вырост».
**Фикс:** `filePicker.launch(SUPPORTED_FILE_MIMES)` (массив из вайтлиста, без `*/*` и без `application/octet-stream`), подпись → «PDF, DOCX, TXT, CSV, MD, JSON, XML». Остаточные «врущие» MIME от кривых провайдеров ловит гейт.
**Тест:** `MaterialPickerMimesTest` — массив пикера == вайтлист (юнит, без UI).

### [P1] Отказ APK (и будущих форматов) — молчаливый: приложение «не реагирует»
**Где:** `ExternalMaterialIntake.kt:74,84` → `null`; `MainActivity.kt:167–183` (при `external == null` управление уходит в `EXTRA_OPEN_TAB` → `return`); `ExternalMaterialIntake.kt:47–51` (`consumeWarning` читает только `MaterialBaseScreen`).
**Симптом:** пользователь жмёт «Мой Политех» в «Открыть с помощью» на APK — не происходит ничего, выглядит как поломка. Сообщение о превышении 50 МБ для внешнего пути есть (`MaterialBaseScreen:246`), а для формата — нет.
**Root cause:** канал `warning` доступен только экрану материалов, куда отклонённый материал не доезжает.
**Фикс:** добавить в `ExternalMaterialIntake` состояние `rejection: String?` + `consumeRejection()`; `MainActivity.handleIntent` при `null` с причиной показывает `Toast` (импорт уже есть, `MainActivity:179`). Внутри приложения — `Banner` (там механизм есть).
**Тест:** `ExternalMaterialIntakeTest`: `apk оставляет сообщение об отказе, а не тишину`.

### [P1] Импортированные неподдерживаемые файлы — мёртвый груз: нет «открыть в другом приложении»
**Где:** `MaterialCommon.kt:163–206` (плашка советует «внешнее приложение»), но `ACTION_VIEW` по сохранённому файлу в материалах не вызывается нигде (грепом по `com.politex`: только `UpdateInstallActivity`, ссылки WebView и `SettingsScreen`).
**Симптом:** XLSX сохранён, квоты съел, открыть нельзя никак; пользователь не может его ни посмотреть, ни убрать иначе как удалением материала.
**Root cause:** приняли формат без возможности использовать; плашка обещает несуществующую кнопку.
**Фикс:** отклонять при приёме (P0-3). Опционально (отдельным тикетом) — «Открыть в другом приложении» через `FileProvider` для легаси-строк.
**Тест:** гейт-тесты §6 покрывают отказ; для легаси — `неподдерживаемый формат не попадает ни в файлы, ни в БД`.

### [P1] `SEND_MULTIPLE` со смешанным набором превращает PDF в «картинку»
**Где:** `ExternalMaterialIntake.kt:81–93` (тип определяет `limited.first()`), `MaterialImporter.kt:369–415` (все копируются с ext первой картинки).
**Симптом:** набор «фото доски + скан.pdf» → PDF сохраняется как `12-1.jpg`, вьюер изображений показывает пустое место, в БД мусор.
**Root cause:** набор классифицируется по первому элементу, а не поэлементно.
**Фикс:** прогонять через гейт каждый Uri; в `Images` оставлять только поддерживаемые изображения, остальные — в отказ с общим сообщением «часть файлов не добавлена»; если изображений 0 — отказ целиком.
**Тест:** `смешанный набор SEND_MULTIPLE оставляет только изображения и сообщает об отброшенных`.

### [P1] Расширения, которые код сам себе ломает: `.markdown` и `rtf`
**Где:** `MaterialImporter.kt:133` (`extOf` → `take(6)`), `MaterialImporter.kt:70` (`PLAIN_TEXT_EXT` содержит `rtf`), `DocTextViewer.kt:89` (`TEXT_LIKE_EXT` без `rtf`).
**Симптом:** `.markdown` импортируется без текста и не открывается; `.rtf` индексируется как текст, но не показывается.
**Root cause:** три разных списка форматов в трёх файлах + обрезка расширения до 6 символов.
**Фикс:** один источник истины `MaterialFormats` (`TEXT_EXT`, `IMAGE_EXT`, `mime→ext`), `extOf` без `take(6)` (или `take(12)`), убрать `rtf` из `PLAIN_TEXT_EXT`, `TEXT_LIKE_EXT` заменить на `MaterialFormats.TEXT_EXT`.
**Тест:** `markdown открывается как текст`; `rtf отклоняется на входе`.

### [P1] Не различаются «формат не поддерживается», «файл битый», «файл пустой»
**Где:** `DocTextViewer.kt:287` (плашка), `287→293–321` (пустой/нечитаемый → «Текст не найден: документ пустой или не читается встроенным просмотрщиком»), `PdfViewer.kt:128–190` (`CorruptedPdfPlaque` — единственный корректный кейс).
**Симптом:** пустой файл и «docx без `word/document.xml`» выглядят одинаково; пользователь не понимает, что делать.
**Root cause:** нет типизированного результата загрузки (только `null`).
**Фикс:** `loadContent` → `LoadOutcome.Ok | Empty | Corrupt | Unsupported`; отдельные тексты (§5).
**Тест:** `пустой файл отклоняется на входе отдельным сообщением`; `docx без word-document.xml показывает «файл повреждён»`.

### [P2] Zip-bomb / OOM при разборе DOCX и чтении текста
**Где:** `MaterialImporter.kt:291–297` (`readDocxText`), `461–506` (`docxPageCount`, `countPageBreaks`), `270–272` (`readTextFile` → `file.readBytes()`), `util/DocxTextExtractor.kt:22–30`.
**Симптом:** DOCX до 50 МБ может развернуться в гигабайты XML: `ZipInputStream` без лимитов, `parseDocumentXml` копит все абзацы в `ArrayList`, `docxPageCount` крутит парсер по всему `document.xml`. Итог — OOM-краш процесса, а не сообщение.
**Root cause:** лимит 50 МБ применён к сжатому размеру, ограничений на распакованный объём/число абзацев/символов нет.
**Фикс:** в `DocxTextExtractor`/`docxPageCount` — счётчик распакованных байт (напр. 8 МБ) и максимум абзацев; в `readTextFile` — потоковое чтение с обрезкой на `MAX_INDEXED_CHARS`, без `readBytes()` целиком.
**Тест:** `docx-бомба из повторяющегося xml обрывается на лимите распаковки и не роняет тест` (фикстура: zip с 200 МБ нулевого `document.xml`, собранный программно, но с заявленным сжатым размером <50 МБ).

### [P2] 50 МБ: перечитывание файла целиком до отказа
**Где:** `MaterialBaseScreen.kt:245` (`exceedsSizeLimit` по hint), `250–253` (`findExistingByContent` → `contentUriSha256`, `MaterialImporter.kt:190–217` — полный проход до 50 МБ), затем `importMaterial` снова `exceedsSizeLimit` (324) и `copyToStorage` (430).
**Симптом:** провайдер без `OpenableColumns.SIZE` + 60 МБ файл = один полный проход до `null`, второй проход с обрывом на 50 МБ. Не падает (rollback корректен: `rollback` 510–520 удаляет файл, закладки, строку), но это секунды напрасного I/O на слабом устройстве.
**Root cause:** хэш считается отдельным проходом, до проверки лимита по факту.
**Фикс:** хэшировать в том же проходе, что и `copyToStorage` (уже поддерживается — `digest`-параметр), а предварительный поиск дубликата ограничить `maxBytes` и/или дешёвым ключом (имя+размер).
**Тест:** `contentUriSha256 не читает поток при hint больше лимита` (уже частично покрыто `copyToStorage обрывает поток…`, `ExternalMaterialIntakeTest:288`).

### [P2] `DISPLAY_NAME` не чистится от разделителей пути и ведущих точек
**Где:** `MaterialImporter.kt:76–86` (`sanitizeFileName` убирает только ISO-control и невидимые), `88–103` (`queryDisplayName`), `301–304`/`312–315` (имя используется как метаданные).
**Симптом:** имя `../../databases/mpk.db.pdf` сохраняется в БД как есть. Сейчас **эксплойта нет** — файлы всегда пишутся как `"$itemId.$ext"` / `"$id-$index.$ext"` (430, 398), имя нигде не становится компонентом пути. Но это латентная мина: любой будущий экспорт/шаринг/переименование файла начнётся с этого `fileName`.
**Root cause:** санитайзер писался под «красивое имя», не под файловую безопасность; тесты закрывают bidi/длину, но не `..`/`/` (`MaterialFileNameSanitizeTest.kt`).
**Фикс:** дополнительно удалять/заменять `\\ / : * ? " < > |`, срезать ведущие `.` и пробелы, схлопывать `..`; экстеншен брать только из последнего сегмента.
**Тест:** `DISPLAY_NAME с ../ и / не сохраняется в имени`, `ведущая точка в имени срезается`.

### [P2] HEIC и битые картинки дают пустой экран без сообщения
**Где:** `ImageViewer.kt:338` (`AsyncImage` без слота ошибки), `ImageViewer.kt:86–92` (сообщение только про отсутствующие файлы).
**Симптом:** на API 24–27 (minSdk 24) HEIC с iPhone → серый экран; повреждённый JPG — то же.
**Root cause:** нет `error`/`placeholder`-состояния.
**Фикс:** `error`-слот: «Не удалось показать изображение. Возможно, формат не поддерживается на этом устройстве» + «НАЗАД». Гейт может отклонять `heic/heif` на `Build.VERSION.SDK_INT < 28`.
**Тест:** `битое изображение показывает плашку ошибки, а не пустой экран` (Robolectric + подложенный битый файл).

### [P3] Второй шаринг затирает ожидающий материал
**Где:** `ExternalMaterialIntake.kt:32–34` (`offer` перезаписывает `pending`).
**Симптом:** пока открыт диалог предмета для файла №1, шаринг файла №2 молча теряет №1 (или наоборот).
**Root cause:** одиночный слот `pending` без очереди.
**Фикс:** минимум — при `pending != null` не перезаписывать, а показать сообщение «Сначала завершите добавление текущего файла»; лучше — очередь.
**Тест:** `второй offer при занятом pending не теряет первый материал` (частично есть: «второй материал ждёт», 328–343).

### [P3] Teacher-flavor рекламирует приём файлов, но отказывает тостом
**Где:** `MainActivity.kt:37–40` (`acceptIntake`), `src/teacher/AndroidManifest.xml` (удалён только `activity-alias`), `main/AndroidManifest.xml:42–61` (SEND/SEND_MULTIPLE остаются).
**Симптом:** в teacher-сборке приложение есть в «Поделиться», затем тост «Материалы недоступны в режиме преподавателя».
**Root cause:** overlay снимает alias, но не SEND-фильтры; отдельный `<intent-filter>` через `tools:node` не удалить, не переписав весь `<activity>`.
**Фикс:** сознательно оставить тост (дёшево) **или** в teacher-overlay добавить `tools:node="remove"` на `<activity android:name=".MainActivity"/>` и заново объявить её с нужными фильтрами (дороже, риск сломать виджеты — не рекомендую в этой задаче).
**Тест:** `teacher overlay не объявляет приём файлов` — только если выбран второй вариант.

### Проверено и в порядке (не находки, но фиксирую для протокола)
* WebView: `allowFileAccess=false`, `allowContentAccess=false`, `setAllowFileAccessFromFileURLs(false)`, `setAllowUniversalAccessFromFileURLs(false)`, `X-Content-Type-Options: nosniff` (`MaterialWebViewer.kt:234–251, 330`). JS включён — обязателен для pdf.js/docx-preview; локальный файл не открывается как `file://`, контент грузится через контролируемый канал.
* APK не может быть установлен из материала: `REQUEST_INSTALL_PACKAGES` используется только `UpdateInstallActivity` (`exported=false`), действия «открыть сохранённый файл системой» в материалах нет.
* Откат импорта корректен: `rollback` удаляет частичный файл, закладки и строку (`MaterialImporter.kt:455–458, 510–520`); потоковый лимит работает и удаляет недописанный файл (покрыто тестом `ExternalMaterialIntakeTest:288`).
* `file://` корректно отклоняется (`ExternalMaterialIntake.kt:73`, тест 207–225).
* `takePersistableUriPermission` не вызывается — и не нужен: содержимое копируется сразу. Единственное следствие: при смерти процесса между выбором и подтверждением возможен `IOException` «Не удалось открыть файл» — обрабатывается баннером и откатом.

---

## 3. Патч манифеста (точный)

Правила, которые нельзя нарушать:
1. Никогда не писать `<data>` с одним `scheme` — либо `scheme`+`mimeType`, либо `scheme`+`pathPattern`.
2. Ни в одном фильтре `activity-alias` не должно быть `mimeType="*/*"`.
3. `pathPattern` проверяется по `Uri.getPath()`; у SAF-провайдеров (`…/document/1234`) имени файла в пути нет → такие файлы придут только через «Поделиться», это нормально.

**Что удалить целиком** (`AndroidManifest.xml:72–77`) — VIEW-фильтр «что угодно, лишь бы content»:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="content" />
    <data android:mimeType="*/*" />
</intent-filter>
```

**Что заменить** (строки 79–137) — на три фильтра с «слитыми» `<data>`:

```xml
<!-- 1. Документы, которые реально читаются встроенными вьюерами -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="content" android:mimeType="application/pdf" />
    <data android:scheme="content" android:mimeType="application/vnd.openxmlformats-officedocument.wordprocessingml.document" />
    <data android:scheme="content" android:mimeType="text/plain" />
    <data android:scheme="content" android:mimeType="text/csv" />
    <data android:scheme="content" android:mimeType="text/markdown" />
    <data android:scheme="content" android:mimeType="application/json" />
    <data android:scheme="content" android:mimeType="application/xml" />
    <data android:scheme="content" android:mimeType="text/xml" />
</intent-filter>

<!-- 2. Изображения, которые отрисовывает Coil -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="content" android:mimeType="image/jpeg" />
    <data android:scheme="content" android:mimeType="image/png" />
    <data android:scheme="content" android:mimeType="image/webp" />
    <data android:scheme="content" android:mimeType="image/bmp" />
    <data android:scheme="content" android:mimeType="image/gif" />
    <data android:scheme="content" android:mimeType="image/heic" />
    <data android:scheme="content" android:mimeType="image/heif" />
</intent-filter>

<!-- 3. Провайдеры, отдающие octet-stream: пускаем только известные расширения.
        Расширение и mimeType — в ОДНОМ <data>, иначе фильтр снова станет «всё подряд». -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[pP][dD][fF]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[dD][oO][cC][xX]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[tT][xX][tT]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[mM][dD]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[cC][sS][vV]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[lL][oO][gG]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[jJ][sS][oO][nN]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[xX][mM][lL]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[jJ][pP][gG]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[jJ][pP][eE][gG]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[pP][nN][gG]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[wW][eE][bB][pP]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[gG][iI][fF]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[bB][mM][pP]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[hH][eE][iI][cC]" />
    <data android:scheme="content" android:mimeType="application/octet-stream" android:pathPattern=".*\\.[hH][eE][iI][fF]" />
</intent-filter>
```

> Про буквенные классы: Android сопоставляет `pathPattern` без флага ignoreCase, поэтому `.PDF`/`.Pdf` требуют классов. Можно оставить как в текущем манифесте (`.*\.pdf` + `.*\.PDF`), тогда число строк удвоится; регистронезависимые классы компактнее и покрывают `.Pdf`. Решение за имплементатором, главное — **не** выносить `scheme` отдельным элементом.

**Шаг 0 перед правкой (одна команда, обязательно):** проверить, как система читает текущие фильтры — `adb shell cmd package query-activities -a android.intent.action.VIEW -d content://x/y -t application/vnd.android.package-archive | grep politex`. Попадание alias'а в выдачу доказывает ИЛИ-семантику `<data>`; после патча та же команда должна ничего не возвращать. Если alias не попадает — значит система трактует `<data>` как И, и тогда достаточно удалить VIEW-фильтр `*/*` и MIME `msword/ms-excel/spreadsheetml` (патч ниже покрывает и этот случай).

**Что удалить из существующих фильтров (MIME, которые не читаются):**
* `application/msword` — из VIEW-фильтра (79–91) **и** из SEND (42–54);
* `application/vnd.ms-excel`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` — оттуда же;
* пути `.doc`, `.DOC`, `.xls`, `.XLS`, `.xlsx`, `.XLSX` — из octet-stream фильтра (101–108).

**Что оставить без изменений:**
* SEND-фильтр (42–54): `application/pdf`, `…wordprocessingml.document`, `text/plain`, `text/csv`, `image/*`, **`application/octet-stream`** (без него пропадёт «Поделиться» из файловых менеджеров, которые всегда шлют octet-stream; мусор отсекает runtime-гейт). Добавить `text/markdown`, `application/json` — заодно перестанут проходить только-через-octet-stream.
* SEND_MULTIPLE `image/*` — оставить.
* Launcher-фильтр, `src/teacher/AndroidManifest.xml` (alias уже удалён overlay'ем) — не трогать.
* `<data android:scheme="file"/>` — не добавлять ни в каком виде.

**Ожидаемый эффект:** приложение исчезает из «Открыть с помощью» для APK/ZIP/MP3/MP4/XLSX/DOC/ODT/PPTX — то есть починен именно тот симптом, на который жалуется заказчик. Для провайдеров с честным MIME (Telegram, Chrome, Files by Google) фильтр точный; для octet-stream — только по расширению, остальное доводит гейт.

---

## 4. Логика гейта

Один вайтлист в одном месте (`util/MaterialFormats.kt`), три точки вызова. Приоритет: **magic bytes → расширение из DISPLAY_NAME → заявленный MIME/`resolver.getType()`** — потому что провайдеры врут (`octet-stream`, пустой тип, неверный тип), а расширение врёт при переименовании; содержимое не врёт почти никогда.

```kotlin
enum class MaterialFormat { PDF, DOCX, TEXT, IMAGE }

sealed interface IntakeVerdict {
    data class Accept(
        val format: MaterialFormat,
        val ext: String,          // нормализованное расширение для БД и вьюера
    ) : IntakeVerdict

    data class Reject(val reason: RejectReason) : IntakeVerdict
}

enum class RejectReason { UNSUPPORTED, ARCHIVE, AUDIO_VIDEO, TABLE, OLD_WORD, APK, EMPTY, NO_ACCESS }

object MaterialFormats {
    val TEXT_EXT = setOf("txt", "md", "markdown", "csv", "log", "json", "xml")
    val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
    val DOC_EXT = setOf("pdf", "docx")
    val SUPPORTED_EXT = TEXT_EXT + IMAGE_EXT + DOC_EXT

    // mime → ext; единственный источник вместо MaterialImporter.extFromMime
    val MIME_EXT = mapOf(
        "application/pdf" to "pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "text/plain" to "txt", "text/markdown" to "md", "text/csv" to "csv",
        "application/json" to "json", "application/xml" to "xml", "text/xml" to "xml",
        "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp",
        "image/bmp" to "bmp", "image/gif" to "gif", "image/heic" to "heic", "image/heif" to "heif",
    )

    val PICKER_MIMES = MIME_EXT.keys.toTypedArray()   // для OpenDocument/OpenMultipleDocuments
}

/**
 * uri уже требует IO. head читает не больше 8 КиБ.
 * declaredMime: intent.type, если не */* и не пусто → EXTRA_MIME_TYPES[0] → resolver.getType(uri)
 */
suspend fun isSupported(
    resolver: ContentResolver,
    uri: Uri,
    declaredMime: String?,
    displayName: String,                 // sanitize(DISPLAY_NAME ?: lastPathSegment)
    head: () -> ByteArray?,              // первые 8 КиБ через openInputStream(uri)
): IntakeVerdict {
    if (uri.scheme != "content") return Reject(UNSUPPORTED)      // как сейчас (ExternalMaterialIntake:73)

    val ext = displayName.substringAfterLast('.', "").lowercase()

    // 1) magic bytes — старший приоритет, чинит и вранье MIME, и переименования
    val bytes = head() ?: return Reject(NO_ACCESS)
    if (bytes.isEmpty()) return Reject(EMPTY)

    sniff(bytes, ext)?.let { return it }

    // 2) расширение + MIME согласованы
    if (ext in MaterialFormats.SUPPORTED_EXT) {
        // MIME либо подтверждает, либо молчит (octet-stream / null / */* ) — доверяем расширению,
        // magic уже не нашёл ничего «чужого» выше
        val extFromMime = MaterialFormats.MIME_EXT[declaredMime]
        if (extFromMime == null || extFromMime == ext || sameFamily(extFromMime, ext)) {
            return Accept(kindOf(ext), ext)
        }
        return Reject(UNSUPPORTED)
    }

    // 3) провайдер не дал расширения (WhatsApp: DISPLAY_NAME = "document")
    MaterialFormats.MIME_EXT[declaredMime]?.let { return Accept(kindOf(it), it) }

    return Reject(UNSUPPORTED)
}

private fun sniff(head: ByteArray, ext: String): IntakeVerdict? {
    // PDF: "%PDF" в первых 1024 байтах (бывает мусорный префикс)
    if (head.indexOfMarker("%PDF".toByteArray(), limit = 1024) >= 0) return Accept(PDF, "pdf")

    // ZIP-контейнер → смотрим оглавление; APK/ODT/PPTX/XLSX тоже zip
    if (head.hasPrefix(0x50, 0x4B, 0x03, 0x04)) {
        return when (peekZipKind(head, ext)) {          // читает не более 64 записей / 4 МБ
            ZipKind.DOCX -> Accept(DOCX, "docx")
            ZipKind.APK -> Reject(APK)                  // AndroidManifest.xml + classes.dex
            ZipKind.XLSX -> Reject(TABLE)               // xl/workbook.xml
            ZipKind.PPTX -> Reject(UNSUPPORTED)         // ppt/presentation.xml
            ZipKind.ODF -> Reject(UNSUPPORTED)          // mimetype: application/vnd.oasis.*
            ZipKind.OTHER -> Reject(ARCHIVE)
        }
    }

    // OLE2 (Word 97 / Excel 97)
    if (head.hasPrefix(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
        return if (ext.startsWith("xls")) Reject(TABLE) else Reject(OLD_WORD)
    }

    // Подписи изображений
    if (head.hasPrefix(0xFF, 0xD8, 0xFF)) return Accept(IMAGE, "jpg")
    if (head.hasPrefix(0x89, 0x50, 0x4E, 0x47)) return Accept(IMAGE, "png")
    if (head.hasPrefix('G'.code, 'I'.code, 'F'.code, '8'.code)) return Accept(IMAGE, "gif")
    if (head.hasPrefix('B'.code, 'M'.code)) return Accept(IMAGE, "bmp")
    if (head.isRiff("WEBP")) return Accept(IMAGE, "webp")
    if (head.isIsoBmff("heic", "heix", "hevc", "heim", "heis", "hevm")) return Accept(IMAGE, "heic")
    if (head.isIsoBmff("mif1")) return Accept(IMAGE, "heic")

    // Аудио/видео — явный отказ, чтобы не показывать «не поддерживается» после импорта
    if (head.hasPrefix('I'.code, 'D'.code, '3'.code) ||          // ID3 (mp3)
        head.hasPrefix(0xFF, 0xFB) || head.hasPrefix(0xFF, 0xF3) ||
        head.hasPrefix('O'.code, 'g'.code, 'g'.code, 'S'.code) ||
        head.isRiff("WAVE") || head.isRiff("AVI ") ||
        head.hasPrefix(0x1A, 0x45, 0xDF, 0xA3) ||                // mkv/webm
        head.isIsoBmff("isom", "mp42", "M4A ", "qt  ")
    ) return Reject(AUDIO_VIDEO)

    if (head.hasPrefix(0x7F, 'E'.code, 'L'.code, 'F'.code)) return Reject(UNSUPPORTED) // ELF
    if (head.startsWithAscii("dex\n")) return Reject(UNSUPPORTED)

    // Текст: нет NUL в первых 8 КиБ и декодируется в UTF-8 (либо cp1251 с ≥90% печатных)
    if (ext in MaterialFormats.TEXT_EXT || ext.isEmpty()) {
        if (looksLikeText(head)) return Accept(TEXT, if (ext.isNotEmpty()) ext else "txt")
    }
    return null   // не наш случай — решает слой расширения/MIME выше
}
```

Правила приоритета и обоснование:
1. **magic** — единственный надёжный признак: `application/octet-stream` + имя `.pdf` → принимаем по `%PDF`; имя `лекция.txt` + `PK...` + `AndroidManifest.xml` → отвергаем как APK (иначе переименованный APK проходит).
2. **расширение** — решает только для «текстового» класса, где сигнатуры нет; расширение и MIME должны не противоречить друг другу.
3. **MIME** — только как последний шанс, когда провайдер не дал имени (`document` без расширения — типично для WhatsApp). Сейчас из-за этого `extFromMime` (`MaterialImporter.kt:137–150`) даёт `""` для octet-stream → файл попадает в плашку `. ?`, хотя он PDF.
4. **`*/*`, `null`, пустой тип, `application/octet-stream`** считаются «MIME молчит», а не «разрешено всё».
5. Результат `Accept` **переписывает** `ext` и `title` в БД (файл всё равно копируется как `"$id.$ext"`), поэтому переименованный «file.txt» с PDF внутри станет нормальным PDF-материалом, а не плашкой.

Точки вызова:
* `ExternalMaterialIntake.fromIntent` — быстрый префильтр **без** чтения байтов (declared MIME + расширение по `DISPLAY_NAME`) для `application/vnd.android.package-archive`, `application/zip`, `audio/*`, `video/*`, `image/svg+xml`, `…msword`, `…ms-excel`, `…spreadsheetml.sheet`, `…oasis.*`, архивных расширений. Отказ → `rejection = …`; возвращаем `null`. Это убирает молчание (P1-5) и останавливает очевидный мусор на главном потоке без IO.
* `MaterialBaseScreen` (в существующем `checking`-блоке, 235–272) — полный `isSupported` с чтением 8 КиБ (Dispatchers.IO). Отказ → `Banner`, `ExternalMaterialIntake.finish(material)`, диалог предмета **не** показывается.
* `MaterialImporter.importMaterial` (перед `insertItem`, 351–353) — повторный `isSupported` как защита от обхода (например, вьюер/импорт вызваны кодом). Отказ → `IOException` с тем же текстом, откат уже есть.
* `ImageViewer`/`DocTextViewer` — не гейт, а честные состояния ошибки (P1-9, P2-13).

---

## 5. Тексты сообщений (RU, коротко)

Одно правило: **«не поддерживается» — перечень форматов; «битый» — совет перекачать; «большой» — уже существующий текст.** Никогда не показывать «Не удалось импортировать: java.io.IOException» (сейчас так бывает, `MaterialBaseScreen.kt:320`).

| Ситуация | Заголовок | Текст | Где |
|---|---|---|---|
| Неподдерживаемый формат (внешний вход) | — | «Формат не поддерживается. Можно добавить: PDF, DOCX, TXT, MD, CSV, JSON, XML и картинки JPG, PNG, WEBP, GIF. Файл не добавлен.» | Toast в `MainActivity` |
| То же внутри приложения | «Формат не поддерживается» | «Можно добавить: PDF, DOCX, TXT, MD, CSV, JSON, XML, картинки. Файл не добавлен.» | `Banner` (или плашка) |
| Архив (zip/rar/7z) | «Архив не добавляется» | «Сначала распакуйте архив и добавьте нужный файл. Поддерживаются: PDF, DOCX, TXT, MD, CSV, JSON, XML, картинки.» | Banner/Toast |
| Аудио/видео | «Аудио и видео не добавляются» | «В материалах хранятся документы и картинки. Поддерживаются: PDF, DOCX, TXT, MD, CSV, JSON, XML, картинки.» | Banner/Toast |
| Таблица (.xls/.xlsx) | «Таблицы Excel не открываются» | «Сохраните лист как PDF или CSV и добавьте заново — так файл будет открываться внутри приложения.» | Banner/Toast |
| Старый Word (.doc) | «Формат .doc не читается» | «Откройте файл в Word → «Сохранить как» → .docx и добавьте заново. .docx читается внутри приложения, и по нему работает поиск.» | Banner/Toast |
| APK | «Установочные пакеты не добавляются» | «В материалы можно добавлять только документы и картинки.» | Toast |
| Пустой файл (0 байт) | «Файл пустой» | «В файле нет содержимого (0 Б) — добавлять нечего.» | Banner/Toast |
| Нет доступа к файлу | «Нет доступа к файлу» | «Провайдер не дал прочитать файл. Попробуйте выбрать его через «Файл» в приложении.» | Banner/Toast |
| Часть набора отброшена | «Добавлена только часть» | «Из N файлов поддерживаются M: остальные не добавлены.» | Banner (уже есть механика для лимита 20, `ExternalMaterialIntake.kt:87`) |
| Битый PDF (есть) | «Файл не открывается» | «Документ повреждён или защищён паролем. Попробуйте скачать файл заново или открыть его в другом приложении.» | `CorruptedPdfPlaque` — оставить |
| Битый DOCX/текст (новое) | «Файл повреждён» | «Не удалось прочитать содержимое — файл повреждён или создан не Word. Попробуйте скачать его заново.» | плашка `DocTextViewer` вместо нынешнего «Текст не найден: документ пустой или не читается…» |
| Большой файл (есть) | — | «Файл слишком большой — максимум 50 МБ» (`MaterialImporter.TOO_LARGE_MESSAGE`) | Banner |

Тон: без «ошибка», без кодов, без имени исключения; всегда говорим, что поддерживается. В подписи кнопки «Файл» (`MaterialDialogs.kt:80`) — «PDF, DOCX, TXT, MD, CSV, JSON, XML» (про XLS убрать: обещание не выполняется).

---

## 6. План тестов

Новые файлы (Robolectric, стиль как в `ExternalMaterialIntakeTest` / `ManifestFileViewAliasTest`):

**`app/src/test/java/com/politex/MaterialFormatGateTest.kt`** — ядро, фикстуры-байты + фейковый провайдер (`ContentProvider` с `openFile`/`registerInputStream`):
* `pdf по magic принимается даже с mime octet-stream`
* `pdf с мусорным префиксом до 1024 байт всё равно pdf`
* `docx c mime octet-stream и именем без расширения принимается как docx` (zip c `word/document.xml`)
* `docx с правильным mime и правильным именем принимается`
* `apk по mime отклоняется с причина APK`
* `apk переименованный в txt отклоняется по magic (AndroidManifest.xml + classes.dex)`
* `zip отклоняется как архив, а не как документ`
* `xlsx отклоняется как таблица (PK + xl/workbook.xml)`
* `odt отклоняется (mimetype application/vnd.oasis.opendocument.text)`
* `doc 97 отклоняется как старый Word (OLE2 D0 CF 11 E0)`
* `mp3 отклоняется как аудио (ID3)`
* `mp4 отклоняется как видео (ftyp isom)`
* `svg отклоняется, хотя провайдер говорит image/svg+xml`
* `пустой файл отклоняется с причиной EMPTY`
* `поток не читается при hint больше 50 МБ` (провайдер с `OpenableColumns.SIZE = 60 МБ` и `openFile` бросающим — доказывает, что гейт не открывает поток)
* `текстовый файл без NUL принимается как TEXT`
* `бинарный файл с расширением .txt отклоняется (NUL в первых 8 КиБ)`

**`app/src/test/java/com/politex/ManifestFileViewAliasTest.kt`** — переписать (см. P0-2):
* `alias не объявляет wildcard mimeType` (ни одного `*/*`)
* `каждый data-элемент alias имеет scheme вместе с mimeType или pathPattern`
* `alias не объявляет приём архивов, аудио, видео и apk`
* `alias не объявляет application-msword и spreadsheet-mime`
* (сохранить) `file схема не принимается alias`, `на alias остался только VIEW`, `teacher overlay удаляет FileViewAlias`, `лаунчер остаётся на MainActivity`

**`app/src/test/java/com/politex/ExternalMaterialIntakeTest.kt`** — дополнить (существующие 17 тестов остаются валидными: код по-прежнему должен уметь разбирать `*/*` из SEND и от пикера):
* `apk оставляет сообщение об отказе, а не тишину` (`consumeRejection() != null`)
* `zip в SEND отклоняется до диалога предмета`
* `смешанный набор SEND_MULTIPLE оставляет только изображения и предупреждает`
* `markdown файл принимается как текст` (после P1-8)
* `файл с именем ../../../databases/mpk.db.pdf не содержит разделителей пути` (после P2-12)

**`app/src/test/java/com/politex/MaterialImporterIntakeTest.kt`** (или дополнить существующий `MaterialContentHashTest`):
* `неподдерживаемый формат не создаёт ни файла, ни строки в БД`
* `файл 60 МБ обрывается на лимите и не оставляет частичный файл` (частично есть: `ExternalMaterialIntakeTest:288` — добавить проверку БД)
* `docx-бомба обрывается на лимите распаковки` (после P2-10; фикстура собирается `ZipOutputStream` из повторяющегося `<w:t>`)
* `текстовый файл больше MAX_INDEXED_CHARS индексируется обрезанным, но открывается целиком`

**`app/src/test/java/com/politex/MaterialFileNameSanitizeTest.kt`** — дополнить:
* `разделители пути и ведущие точки вырезаются`
* `двойная точка внутри имени схлопывается`

**UI (Robolectric, если будет время):** `MaterialIntakeBannerTest` — `отказ по формату показывает баннер и не открывает диалог выбора предмета` (тег `material_format_banner`); `битое изображение показывает плашку ошибки` (тег `image_error_plaque`).

**Ручной гейт перед релизом (не gradle):**
1. На устройстве: «Открыть с помощью» на `.apk`, `.zip`, `.mp3`, `.xlsx` — «Мой Политех» **отсутствует**; на `.pdf`, `.docx`, `.txt`, `.jpg` — присутствует и открывает.
2. «Поделиться» из Telegram файла `.pdf` (тип честный) и из файлового менеджера, отдающего `octet-stream` на `.pdf` — оба добавляются.
3. «Поделиться» `.apk` — тост «Установочные пакеты не добавляются», ничего не сохранено.
4. `adb shell dumpsys package com.politex | grep -A2 "FileViewAlias"` — в фильтрах нет `*/*`.

---

## 7. Порядок работ (волнами, без пересечения файлов)

| Волна | Содержание | Файлы | Риск |
|---|---|---|---|
| 1 | Манифест + инвертированный `ManifestFileViewAliasTest` (**одним коммитом**, иначе гейт красный) | `app/src/main/AndroidManifest.xml`, `ManifestFileViewAliasTest.kt` | низкий, но затрагивает «Открыть с помощью» — проверить руками п.6.1 |
| 2 | `MaterialFormats` (вайтлист + `isSupported`) + тексты отказов + канал `rejection` + Toast/Banner + вызовы в `ExternalMaterialIntake`, `MaterialBaseScreen`, `MaterialImporter` | новые `util/MaterialFormats.kt`, `ExternalMaterialIntake.kt`, `MainActivity.kt`, `MaterialBaseScreen.kt`, `MaterialImporter.kt`, `MaterialCommon.kt` | средний: трогает импорт — прогон `:app:testStandardDebugUnitTest` |
| 3 | Пикер `arrayOf(SUPPORTED_FILE_MIMES)` + подпись диалога | `MaterialBaseScreen.kt`, `MaterialDialogs.kt` | низкий |
| 4 | Свести три списка расширений в один, починить `extOf`/`.markdown`/`rtf` | `MaterialImporter.kt`, `DocTextViewer.kt` | низкий |
| 5 | Лимиты распаковки docx, потоковое чтение текста, санитайзер имён, error-слот изображений | `DocxTextExtractor.kt`, `MaterialImporter.kt`, `ImageViewer.kt` | средний (легаси-строки должны открываться по-прежнему) |

**Легаси-данные:** у части пользователей уже лежат неподдерживаемые материалы, импортированные прошлыми версиями. Гейт их не тронет (он только на входе) — и это правильно: удаление пользовательских строк без спроса недопустимо. Плашка `UnsupportedFormatPlaque` остаётся для них; при желании — отдельная миграция-«чистка» с подтверждением, не в этой задаче.