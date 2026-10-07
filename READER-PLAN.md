# READER-PLAN — перенос рендера PDF/DOCX из Gander в MPK

Дата: 2026-10-08. Статус: принят консиллиумом (5 агентов, 2 волны). Ветка: v2.4-stable.
Источник: `tools/reader/gander` (MIT, https://github.com/mokshablr/gander) — WebView + pdf.js 6.3.289 (legacy) + docx-preview 0.4.0 + JSZip 3.10.1.

## Консиллиум

Волна 1: **gander-architect** (минимальный набор ассетов ~4.3 МБ, контракт интеграции), **mpk-domain** (карта materials: редактор изолирован, ~250 строк; аннотации нормализованы 0..1), **security** (GO-с-условиями: PathHandler только /assets/viewer/, CSP-заголовок на каждый ответ, deny-навигация).
Волна 2 (адверсариальная, по проекту решения): **android-integrator** (DOM `.pg` подтверждён, overlay жизнеспособен, pinch = композитор → координаты через visualViewport.scale; mixedContentMode у gander НЕ выставлен — у MPK обязателен), **qa-adversary** (CRITICAL: textEdited-роутинг; CRITICAL: off-by-one 0/1-based; Android 7 = всегда нативный PDF; ссылки docx умрут без ACTION_VIEW).

## Решения (журнал)

- **D1. DOCX** → WebView + docx-preview, ТОЛЬКО если `ext=="docx" && !textEdited && файл существует && floor OK (Chromium≥80)`. TEXT / text-подобные / docx с `textEdited=1` → существующий плоский Compose-рендер. Fallback (Chromium<80) → плоский текст через DocxTextExtractor. Обоснование: правленые docx показывают сохранённый текст, а не оригинал (QA CRITICAL #1).
- **D2. Редактор**: из `canEdit` убираем `"docx"` (заказчик: «убери редактор текста docx»). Редактор TEXT/txt-подобных ЗАСТАЁТ (заметки пользователей). `DocxDocumentParser.kt` + Rich-ветка рендера (DocxBlocksView/ParaView/TableView/PageBreakSpacer/docSp) удаляются; `DocxTextExtractor` остаётся (поисковый индекс MaterialImporter + fallback). `textContent`/`textEdited`/`updateTextContent` в Room остаются (поиск, миграции) — схема НЕ меняется, миграция не нужна.
- **D3. PDF** → WebView + pdf.js как основной путь, ЕСЛИ `chromiumMajor ≥ 125 && !locked` (Huawei). Иначе (включая major==null — консервативно, и ВСЕ Android 7: Chrome ≤109) → существующий нативный `MaterialPdfViewer` БЕЗ ИЗМЕНЕНИЙ. `onRenderProcessGone` (API 26+) → destroy WebView + переход в нативный viewer. `locked`-сообщение (pdf с паролем) → тоже нативный (PdfRenderer спросит пароль? нет — plaque; приемлемо v1). Повреждённый pdf (pageCount==0 при импорте) → нативный (CorruptedPdfPlaque, W16-тест жив).
- **D4. «Рисунки» — ink-аннотации PDF адаптируются под WebView**: новый `mpk-ink.js` в pdf.html — overlay-canvas на каждый `div.pg` (z-index≥2, pointer-events только в ink-режиме, touch-action:none), MutationObserver/ResizeObserver. Координаты указателя → нормализация 0..1 (с visualViewport.scale при pinch, как pdf.mjs:1421). ВСЯ логика (eraserHitTest, компенсация толщины, undo, Room-персист) — существующие чистые Kotlin-функции (тесты BBAnnotationModelTest/X5X34 живы). Формат Stroke/MaterialAnnotationEntity без изменений. Ночной режим не портируем (night=0) → палитра штрихов постоянная.
- **D5. Страницы/закладки**: порт «page N OF» (1-based) → конвертер в 0-based В ОДНОМ МЕСТЕ (QA CRITICAL #2) → `setLastPage`; `resume=lastPage+1` (если >1) в URL; прыжок `g<n>` (n=bookmark.page+1). Закладки — существующая MaterialBookmarkEntity, панель в Compose.
- **D6. Обвязка** (~500 строк Kotlin): `MaterialWebViewer` (Compose AndroidView) + порты из gander: `DocRange` (адаптация: файлы локальные filesDir → RandomAccessFile вместо contentResolver), `PortMessage` (расширен ink-глаголами), `ViewerPolicy` (CSP дословно), `WebViewFloor` (срезан до PDF/DOCX). Роутинг — чистые функции `pdfRoute()/docxRoute()` (тестируемы в Robolectric, major==null → NATIVE/FLAT-консервативно... docx: null → WEB как у gander, floor 80 низкий).
- **D7. Протокол ink-порта**: native→JS: `K<page0> <json>` (канонические штрихи страницы), `M<0|1>` (режим), `S <argb> <widthCssPx>`, `E<0|1>` (ластик); JS→native: `ink <page0> <x1> <y1> …` (норм. 0..1), `zap <page0> <x> <y>` (ластик-точка). Строгий парсер, невалидное молча дропается.
- **D8. Ссылки в docx/pdf**: `shouldOverrideUrlLoading`: viewer-страницы — разрешить; `http(s)/mailto/tel` → Intent ACTION_VIEW (try/catch) + deny; остальное — deny (QA HIGH #4).
- **D9. Security-чеклист (обязателен)**: PathHandler ТОЛЬКО `/assets/viewer/`; `/doc/file` через shouldInterceptRequest (фиксированный путь, Range/206, nosniff); любой другой http(s) → notFound() (у MPK ЕСТЬ INTERNET — вторая стена обязательна); CSP-заголовок на КАЖДЫЙ asset-ответ (worker pdf.js); `allowFileAccess/allowContentAccess/domStorage=false`, cookies off, `mixedContentMode=MIXED_CONTENT_NEVER_ALLOW` (явно!), `setAllowFileAccessFromFileURLs/UniversalAccessFromFileURLs=false` (явно), `supportMultipleWindows=false`, `minimumFontSize=1` (PDF, issue #22), порт постится на фиксированный origin (не «*»), `onRenderProcessGone=true`+destroy (guard API 26), WebView-экран не exported (внутри Compose-навигации). students.csv: гейт standard-сборки не меняется; admin — недоступен из JS (узкий PathHandler).
- **D10. Ассеты (~4.3 МБ)**: `viewer/{pdf.html,docx.html,app.js,app.css,pdf-setup.js,pdf-nomodule.js,pdf.mjs,docx.js,find.js}` + `viewer/lib/{pdf.min.mjs,pdf.worker.min.mjs,docx-preview.min.js,jszip3.min.js,cmaps/,wasm/}` + `viewer/mpk-ink.js` (свой). cmaps/wasm НЕ режем (тихий отказ CJK/JPEG2000). В pdf.html добавляется один `<script src="mpk-ink.js">`. Лицензии: урезанный `assets/viewer-licences.md` (gander MIT, pdf.js Apache-2.0, docx-preview Apache-2.0, JSZip MIT elected, pako MIT, cmaps BSD-3, openjpeg BSD-2, jbig2 BSD-3+Apache) + пункт в Settings/About.
- **D11. Вне объёма**: .doc/.odt/.rtf (plaque как сейчас), поиск по документу (find.js копируется для docx.html, UI поиска нет), ночной режим PDF, печать, xlsx/pptx/md.
- **D12. Тесты**: удалить DocxDocumentParserTest (14); переписать нечего (W16 жив благодаря D3-null→native); новые: RoutingTest (pdf/docx/textEdited/off-by-one конвертер), PortMessage-ink тесты, порты DocRangeTest/PortMessageTest/WebViewFloorTest, ViewerPolicyTest (CSP-паритет с ассетами), AssetsPresenceTest. Гейт: `:app:testStandardDebugUnitTest`.

## Риски (остаточные)

- ink-слой — единственный новый JS: Robolectric его не ловит → протокольные юнит-тесты Kotlin + ручная/vision проверка на устройстве. (medium)
- pdf.js ~195 MB plateau на больших документах — слабее нативного PdfRenderer; спасает floor-роутинг + onRenderProcessGone→native. (medium)
- 0-day в pdf.js — CSP+wasm-unsafe-eval+deny-навигация; версии пинятся файлами gander. (low)
- Ошибки рендера внутри WebView — английские (vwError); файл-не-найден/битый перехвачены роутингом на русский plaque до WebView. (low, принято v1)

## Порядок работ

1. Ассеты (копирование + трим лицензий + mpk-ink.js).
2. Kotlin-порты (DocRange/PortMessage/ViewerPolicy/WebViewFloor) + роутинг-функции + тесты.
3. MaterialWebViewer (Compose, security-чеклист D9) + ink-мост.
4. Хирургия DocTextViewer (−docx-редактор, −Rich-ветка, −DocxDocumentParser+тест).
5. Роутинг MaterialBaseScreen (floor-детект + fallback-состояние).
6. Прогоны: compile → unit-гейт → коммиты по блокам.
7. Ревью-консиллиум по фактическому diff (code-review + security re-audit), фикс находок.
