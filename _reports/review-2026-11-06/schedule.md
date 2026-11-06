# Ревью домена «Расписание» — v2.4-stable / Room v11

Дата: 2026-11-06. Ревьюер: rev-schedule. Режим: read-only (чтение + grep), без сборок.
Проверялись жалобы заказчика: (1) расписание показывается старым, (2) старое расписание может
сломаться, (3) сегодняшнее расписание отображается неправильно.

Легенда: P0 — ломает основной сценарий и объясняет жалобу целиком; P1 — заметный дефект,
воспроизводится регулярно; P2 — edge case/качество; P3 — гигиена.

Все ссылки — на `app/src/main/java/com/politex/...` (кроме тестов, они помечены как `app/src/test/...`).

---

### [P0] Автосинк никогда не обновляет уже закэшированный день — нет понятия «свежесть»

- Где: `ui/viewmodel/AppViewModel.kt:210-217`, `data/repository/ScheduleRepository.kt:335-347`,
  `data/repository/ScheduleRepository.kt:28-32`, `data/repository/ScheduleRepository.kt:266-279`,
  тест-замок `app/src/test/java/com/politex/SyncCacheFirstTest.kt:178-198`.
- Симптом/влияние: колледж перепубликовал/исправил документ на сегодня или завтра — приложение
  этого не увидит. Пользователь открывает приложение в 12:00, видит расписание, загруженное
  три дня назад, и не получает ни одного признака, что оно устарело. Именно это заказчик называет
  «расписание отображается старым». Аналогично в режиме просмотра чужой группы
  (`ui/screens/GroupScheduleScreen.kt:307-316` — автопроверка выполняется один раз и только если
  кэша нет вовсе).
- Root cause (доказательство): два последовательных гейта, оба без времени.
  1. `AppViewModel.kt:212-216`:
     ```kotlin
     if (!scheduleRepository.hasSchedule(uiState.value.currentGroupName)) {
         syncSchedule(isAutoSync = true)
     }
     ```
     `hasSchedule()` = `getLessonCountForGroup(group) > 0` (`ScheduleRepository.kt:696-698`), то есть
     **любая** строка, включая урок за 60 дней до этого, навсегда выключает автосинк при старте.
  2. Даже если синк вызван, `syncScheduleDetailed(force = false)` (AppViewModel передаёт
     `force = !isAutoSync` → `false`) выходит из сети по кэшу:
     ```kotlin
     val candidateOutcome = candidates.firstNotNullOfOrNull { cachedDateOutcome(groupName, it) }
     if (candidateOutcome != null) { ...; return Result.success(cached) }   // :336-345
     ```
  Ни в `ScheduleRepository`, ни в `AppViewModel` нет ни `lastSyncAt`, ни TTL для расписания.
  При этом в проекте такой механизм уже есть в других местах: `WidgetUpdateHelper.kt:114-117`
  (`STALE_AFTER_MS`), `TeacherInsights.kt:19` (`STALE_DAYS`). Тест
  `SyncCacheFirstTest."уроки даты уже в БД — Ok из кэша, сеть не тронута"` **закрепляет** это
  поведение как ожидаемое (`assertEquals(0, client.networkCalls)`).
- Предлагаемый фикс: ввести TTL по (group, date) — `SharedPreferences`-карта
  `sync_at_<group>_<date>` обновляется в `ScheduleRepository.syncScheduleDetailed`/`syncScheduleForDate`
  после успешной записи; в начале `syncScheduleDetailed` трактовать кэш как валидный только при
  `now - syncAt < TTL` (например 30 мин для сегодня/завтра, 6 ч для остальных дат) либо при `force`.
  В `AppViewModel.init` убрать ветвление по `hasSchedule()` — всегда вызывать
  `syncSchedule(isAutoSync = true)` (при свежем кэше это 0 сетевых вызовов, как сейчас).
- Тест: новый `ScheduleFreshnessGateTest` — «кэш старше TTL требует сети», «кэш моложе TTL сети не
  трогает», «после midnight TTL вчерашней записи не считается свежестью для новой даты».

---

### [P0] «День-дырка»: отсутствие группы в документе объявляется доказанным фактом и навсегда гасит сеть

- Где: `data/repository/ScheduleRepository.kt:39-48` (`cachedOutcomeForDate`),
  `:34-37` (`isFullDayDocument`), `:50-57` (`incompleteDayMark`), `:385-389` (ранний выход),
  тест-замок `app/src/test/java/com/politex/data/repository/SyncDateFilterTest.kt:177-195`.
- Симптом/влияние: пользователь видит тост/статус «Расписание на 06.11 опубликовано, но вашей группы
  в нём пока нет» — и всё, больше приложение в этот день за документом не пойдёт. Когда колледж
  допишет группы, приложение не узнает до `force`-обновления или смерти процесса. Для дня, где
  реально нет пар (праздник/перенос), «нет занятий» и «документа нет» неразличимы.
- Root cause (доказательство):
  ```kotlin
  internal fun isFullDayDocument(groupCount: Int, catalogSize: Int): Boolean =
      catalogSize > 0 && groupCount >= (catalogSize + 1) / 2        // :34-37
  ```
  «Полный день» — это **половина** каталога (32 из 64 групп). Тогда:
  ```kotlin
  groupLessons > 0 -> Ok(...)
  isFullDayDocument(parsedGroups, catalogSize) -> FoundNoGroup(date) // :46
  ```
  → `FoundNoGroup` считается окончательным знанием, а `incompleteDayMark` для того же случая
  возвращает `false`, то есть **снимает** метку неполноты. Далее автопуть `force=false`:
  ```kotlin
  if (!force && needTomorrow && days.all { incompleteDayCache[date] == true }) { ... return FoundNoGroup }
  ```
  и основной гейт `shouldFetchDay(already, catalogSize, force) = ... already < (catalogSize+1)/2`
  (`:28-32`) — при кэше «больше половины групп» день больше не перекачивается никогда.
  `SyncDateFilterTest."СРЕД10 - неполный день помечается и не докачивается, force сбрасывает"`
  закрепляет это как норму.
- Предлагаемый фикс: разделить «документ получен» и «день полон». Хранить
  `documentGroupsCount` и считать день полным только при `groupCount >= catalogSize` (или ≥0.9 —
  решение зафиксировать константой с комментарием); для `FoundNoGroup` не подавлять сеть, а
  применять короткий TTL (10-15 мин) и повторять; `incompleteDayMark` для «половины каталога»
  должен оставаться `true` (неполный), а не `false`.
- Тест: `SchedulePartialDocumentGateTest` — «документ на 32/64 группы — неполный», «FoundNoGroup
  протухает и уходит в сеть через N минут», «после появления группы день обновляется без force».

---

### [P0] Неверно определённый день недели перештамповывает документ на чужую дату и молча его выбрасывает

- Где: `data/network/MpkScheduleParser.kt:807-836` (детект дня),
  `:135-143` (`restampByDow`), `:523-542` (`applyHeaderDate`),
  `data/repository/ScheduleRepository.kt:59-62` (`lessonsForWrite`) и `:445-465` (итог синка).
- Симптом/влияние: документ за сегодня найден и скачан, но в базу не попадает ничего — приложение
  сообщает «расписание опубликовано, но вашей группы в нём нет» (или «нет занятий»). Ровно жалоба
  «сегодняшнее расписание неправильно отображается»: документ есть, отображения нет. Второй эффект:
  уроки получают неверный `dayOfWeek`, а время пар считается по звонкам этого дня
  (`MpkScheduleParser.kt:1162` → `CollegeBellSchedule.getTimeForLessonNumber(lessonNumber, dayOfWeek)`),
  поэтому карточки (и виджет «Сейчас/Дальше») показывают чужие часы.
- Root cause (доказательство): день определяется поиском **первого** ключевого слова по первым 100
  строкам всего документа:
  ```kotlin
  var detectedDay = 1
  val searchScopeText = lines.take(100).joinToString(" ").lowercase(Locale.ROOT)
  for ((dayName, dayNum) in DAY_KEYWORDS) { if (searchScopeText.contains(dayName)) { detectedDay = dayNum; break } }
  ```
  `DAY_KEYWORDS` (`:57-64`) начинается с «понедельник». Любое упоминание другого дня в шапке/примечании
  (перенос, «смотрите расписание на среду») даёт чужой `detectedDay`. Далее дата каждого урока
  пересчитывается от даты в шапке:
  ```kotlin
  cal.add(Calendar.DAY_OF_YEAR, (lesson.dayOfWeek - anchorDow).coerceIn(-6, 6))
  ```
  Документ на 05.10 (понедельник) при `detectedDay = 3` превращается в 07.10. Запись фильтруется:
  ```kotlin
  lessonsForWrite(lessons, allowedDates) = lessons.filter { it.dateString.isBlank() || it.dateString in allowedDates }
  ```
  (`allowedDates` = {сегодня, завтра}) → все строки отбрасываются → `groupCount = 0`,
  `documentFound = true` → `resolveSyncOutcome` → `FoundNoGroup`. Ошибка проглатывается: ни
  исключения, ни признака «документ оказался за другую дату».
- Предлагаемый фикс: (1) приоритет всегда у даты шапки — `anchorDow = dowOfCalendar(anchor)`, а
  `Dow.ofWord` использовать только если дата не распознана (сейчас наоборот, `:533-537`);
  (2) `detectedDay` искать только в шапке (первые 5 строк), а не во всех 100;
  (3) в `lessonsForWrite`/`syncScheduleDetailed` при непустом `fetched`, но пустом результате после
  фильтра, возвращать явную ошибку `IOException("Документ за другую дату")` вместо `FoundNoGroup`.
- Тест: `ScheduleRestampSafetyTest` — «документ 05.10 без даты в шапке не превращается в 07.10»,
  «отброс по allowedDates даёт явную ошибку, а не FoundNoGroup», «`dayOfWeek` урока равен дню из шапки».

---

### [P0] `documentMatchesDate` fail-open: единственная защита от «старого документа» отключается сама

- Где: `data/network/MpkScheduleParser.kt:567-574` (`documentMatchesDate`),
  `:544-560` (`extractHeaderText`), `:91-92` (`HEADER_ZIP_REGEX`/`HEADER_LINE_REGEX`),
  `data/repository/ScheduleRepository.kt:534-537` (проверка даты только для «похожих на документ»
  байтов), тест-замок `app/src/test/java/com/politex/X1DocumentDateTest.kt:14-25`.
- Симптом/влияние: самое опасное проявление жалобы №1 — **вчерашний (или прошлонедельный) документ
  показывается как сегодняшний**, причём правдоподобно: все группы, все пары, правильные кабинеты.
  Именно этот сценарий выглядит для заказчика как «расписание отображается старым» без внешних
  признаков.
- Root cause (доказательство):
  ```kotlin
  fun documentMatchesDate(bytes: ByteArray, dateDot: String): Boolean {
      val headerDate = extractHeaderDate(extractHeaderText(bytes)) ?: return true   // ← fail-open
      ...
  }
  ```
  Дата берётся из текста шапки, который извлекается как «РАСПИСАНИЕ» + всё до конца строки/до `<`:
  `HEADER_ZIP_REGEX = Regex("РАСПИСАНИЕ[^<]*")` для docx и `HEADER_LINE_REGEX` для doc. В реальном
  .docx дата лежит в отдельном `<w:t>`-ране (`<w:t>РАСПИСАНИЕ</w:t><w:t>занятий на</w:t><w:t>03.11.2026</w:t>`),
  в .doc — в отдельном text-run: `extractHeaderText` вернёт только «РАСПИСАНИЕ», `extractHeaderDate`
  вернёт `null` → `documentMatchesDate` вернёт `true` для **любой** даты. Принятый документ далее
  штампуется запрошенной датой, потому что в `parsePlainTextSchedule` `detectedDate = targetDate`
  (`:817-818`) и `applyHeaderDate` при отсутствии даты берёт `targetDate` (`:532`). Тест
  `X1DocumentDateTest` прямо фиксирует fail-open для не-документа:
  `assertTrue(MpkScheduleParser.documentMatchesDate(html, "06.10.2026"))`.
- Предлагаемый фикс: `documentMatchesDate` → `return false`, если дата в шапке не распознана
  (fail-closed) + расширить извлечение шапки: склеивать содержимое `w:t` до 300–400 символов
  (для docx уже есть `Docx.readDocumentXml` — взять больше контекста, чем `[^<]*`); для .doc —
  склеивать несколько первых run'ов `extractUtf16LeRuns`/`extractCp1251Runs`. В
  `fetchAllGroupsForDate` проверять дату для любых байтов, не только для `looksLikeDocument`.
- Тест: `DocumentDateGuardTest` — «docx с датой в отдельном run определяется правильно»,
  «документ без распознанной даты не принимается (fail-closed)», «HTML не считается документом».

---

### [P1] Legacy-строки без даты (`dateString=""`) показываются как расписание сегодняшнего дня

- Где: `ui/screens/ScheduleScreen.kt:1842-1853` (`lessonsForDay`), `:192` (hasSchedule-лямбда),
  `ui/components/CalendarArchiveDialog.kt:148-171`, `widget/NowNextWidgetProvider.kt:312-321`
  (`filterTodayLessons`), запись: `data/repository/ScheduleRepository.kt:59-62` и `:668-679`.
- Симптом/влияние: на устройстве, обновлённом с версий до появления `dateString`, в БД лежит
  недельный шаблон с пустыми датами. Для любого дня без точных строк приложение показывает этот
  шаблон **как расписание этого дня**, без всякой пометки. Это второй по частоте механизм
  «старое расписание вместо текущего».
- Root cause: fallback без маркера свежести в трёх местах:
  ```kotlin
  val exact = allGroupLessons.filter { it.dateString == activeDateString }
  return if (exact.isNotEmpty()) exact
         else allGroupLessons.filter { it.dateString.isBlank() && it.dayOfWeek == activeDayOfWeek }
  ```
  Плюс запись таких строк разрешена и **не идемпотентна**: `lessonsForWrite` пропускает `isBlank()`,
  а `replaceSyncedLessons` для пустой даты делает `insertLessons(...)` без предварительного delete
  (`:672-678`) → дубли копятся от синка к синку.
- Предлагаемый фикс: одноразовая чистка `DELETE FROM lessons WHERE dateString=''` (в существующей
  миграции или в `AppGraph.init` по флагу в prefs); запретить запись blank-строк
  (`lessonsForWrite` — только `dateString in allowedDates`); fallback оставить, но с явной пометкой
  в UI «недельный шаблон, дата неизвестна» (флаг из `lessonsForDay`).
- Тест: `BlankDateLegacyRowsTest` — «blank-строки не участвуют в выдаче за дату», «повторный синк
  не создаёт дублей blank-строк».

---

### [P1] `syncScheduleForDate` пишет день без гейта полноты и без проверки даты — частичный документ затирает полный

- Где: `data/repository/ScheduleRepository.kt:490-520` (`syncScheduleForDate`) и `:522-547`
  (`fetchAllGroupsForDate`). Вызывается из виджета/ресивера времени
  (`widget/WidgetUpdateHelper.kt:154-158`), из воркера (`worker/ScheduleCheckWorker.kt:113`),
  из архива.
- Симптом/влияние: «старое расписание может сломаться» в буквальном смысле — то, что было показано
  корректно, после фонового обновления становится неполным (пропали пары, кабинеты, преподаватели).
  Хуже: `fetchAllGroupsForDate` для байтов, не похожих на документ, не проверяет дату вовсе, поэтому
  HTML-страница (листинг портала, 404-страница WordPress), разобранная как таблица, может быть
  записана под запрошенную дату.
- Root cause: в `syncScheduleDetailed` запись защищена
  `if (isFullDayDocument(dayGroups, allGroups.size)) fetched += dayLessons` (`:405-406`), а в
  `syncScheduleForDate` такой проверки нет вообще:
  ```kotlin
  val all = forGroup.getOrDefault(emptyList())
  if (all.isNotEmpty()) { writeByGroup(all, setOf(date)); pruneOldLessons() }   // :505-508
  ```
  `writeByGroup` → `replaceForDate` (`LessonDao.kt:141-149`) удаляет все строки группы за дату и
  вставляет новые — то есть усечённый набор уничтожает прежний полный.
- Предлагаемый фикс: вынести общий гейт в `fetchAllGroupsForDate`/`writeByGroup` — не писать, если
  `!isFullDayDocument(dayGroups, allGroups.size)`, и возвращать `Result.failure`; проверять
  `documentMatchesDate` для любых байтов; `replaceForDate` вызывать только при непустом результате
  и только после проверки даты документа.
- Тест: `SyncForDateSafetyTest` — «частичный документ не перезаписывает полный день»,
  «HTML-страница не пишется как расписание дня».

---

### [P1] При смене даты в приложении данные не обновляются (00:05 / после 23:59)

- Где: `MainActivity.kt` (нет обработки `ACTION_DATE_CHANGED` для экрана; `onResume` пустой —
  `:148-150`), `ui/util/TodayProvider.kt:17-38`, `ui/viewmodel/AppViewModel.kt:210-217`,
  `worker/ScheduleCheckWorker.kt:65-67` и `:93`.
- Симптом/влияние: в 00:05 вкладка «СЕГОДНЯ» переключается на новую дату (это работает), но данных
  за новую дату нет → «Расписания на этот день нет», хотя вчера всё было. После 23:59 то же самое.
  Приложение в фоне на ночь — единственный, кто синкает новую дату, это
  `widget/TimeChangedReceiver.onTimeChanged` → `WidgetUpdateHelper.ensureTodaySchedule`
  (`WidgetUpdateHelper.kt:123-170`), но он: (а) только для виджета/уведомлений, (б) пропускает
  воскресенье (`if (dow == 7) return`), (в) обновляет только «сегодня», не «завтра», (г) ограничен
  `SYNC_BUDGET_MS = 6_000` и после таймаута не повторяет 15 минут.
- Root cause: в приложении нет триггера «дата сменилась → синхронизировать новую дату». `rememberToday`
  двигает только UI-состояние. Периодический воркер тоже не помогает: он работает лишь в окне
  10:00–21:00 (`:65-67`) и в кандидатах держит только `nextDayCandidates` — **сегодня в кандидатах
  нет никогда** (`:93`), то есть сегодняшний документ фоново не перезагружается вообще.
- Предлагаемый фикс: в `ScheduleScreen`/`AppViewModel` на смену `dayKey` (уже есть в
  `rememberToday`) однократно вызывать `syncSchedule(isAutoSync = true)`; в `ScheduleCheckWorker`
  добавить `Calendar.getInstance()` (сегодня) в список кандидатов; окно 10:00–21:00 заменить на
  «не чаще раза в 15 минут» для проверки сегодняшнего дня.
- Тест: `DateRolloverSyncTest` — «при смене даты инициируется один синк на новую дату»,
  «воркер включает сегодня в кандидаты».

---

### [P1] Воркер помечает день «проверенным», ничего не скачав — уведомление о выходе расписания не придёт

- Где: `worker/ScheduleCheckWorker.kt:115-156` (ветки `mark(checkedKey)`), `:93` (фильтр
  `filterNot { marked(it) }`), `:239-252` (`staleMarkKeys`, хранение 7 дней).
- Симптом/влияние: расписание на завтра публикуют в 14:00–16:00; воркер в 10:15 зашёл, ничего не
  нашёл и поставил `checked_<group>_<date>` → в 14:30 он уже не пойдёт (день отфильтрован), поэтому
  уведомление «вышло расписание» не покажется вообще, а данные не подтянутся до ручного открытия.
- Root cause:
  ```kotlin
  } else { mark(checkedKey) }   // allLessons.isEmpty()
  ```
  Метка ставится и при пустом ответе, и при ошибке сети (`onFailure = { Result.success() }` — без
  метки, но и без повтора до следующего периода 15 мин, который отфильтруется другими метками).
  Отличие «документа нет» и «документ есть, группы нет» (`resolveSyncOutcome`) здесь не используется,
  хотя исход уже посчитан репозиторием.
- Предлагаемый фикс: `mark(checkedKey)` только если `ProbeResult.Found` или исход не `NotFound`;
  для `NotFound` — exponential backoff (10/30/60 мин) вместо «навсегда»; использовать
  `syncScheduleDetailed` и его `SyncOutcome` вместо `syncScheduleForDate`.
- Тест: `ScheduleCheckWorkerRetryTest` — «NotFound не ставит checked и повторяется», «FoundNoGroup
  ставит checked», «после появления документа уведомление отправляется один раз».

---

### [P1] Teacher-режим: «сегодня» фиксируется при создании ViewModel; документ валидируется только по дню недели

- Где: `ui/viewmodel/AppViewModel.kt:125-126`
  (`teacherScheduleDateFlow = MutableStateFlow(DateFormats.dotDate(Calendar.getInstance()))`),
  `ui/teacher/TeacherDayViewModel.kt:43`, `data/network/TeacherScheduleParser.kt:49-64`
  (`matchesRequestedDate`), `data/repository/ScheduleRepository.kt:577-586`.
- Симптом/влияние: (а) в teacher-режиме после полуночи/смены даты «сегодня» остаётся вчерашним до
  перезапуска процесса — преподаватель видит вчерашние пары как сегодняшние; (б) если в шапке
  учительского документа не оказалось даты (только «ПОНЕДЕЛЬНИК»), проверка падает на сравнение
  дня недели, и **документ прошлой недели принимается за текущий** — те же пары, те же кабинеты,
  правдоподобно.
  Слияния учительского и студенческого расписания в коде нет — teacher-режим полностью подменяет
  поток (`AppViewModel.kt:161-169`: `if (isTeacher) teacherLessons else …`), поэтому «устаревшие
  записи при слиянии» как отдельный дефект не подтверждаются.
- Root cause: `teacherScheduleDateFlow` инициализируется один раз и не связан с `rememberToday`/
  `ACTION_DATE_CHANGED`; `matchesRequestedDate`:
  ```kotlin
  if (inDocDate != null) { /* сравнение по Y/DAY_OF_YEAR */ }
  val dow = dowFromHeaderText(header) ?: return false
  return dow == MpkScheduleParser.dowOfCalendar(requested)      // только день недели
  ```
  и `syncTeacherScheduleForDate` (`:584-586`) добавляет вторую, столь же слабую проверку по `dow`.
- Предлагаемый фикс: обновлять `teacherScheduleDateFlow` при смене даты (ON_RESUME + таймер, как в
  `ui/util/TodayProvider.kt`) и при `TimeChangedReceiver`; в `matchesRequestedDate` при отсутствии даты в
  документе возвращать `false` (fail-closed), либо требовать дату в шапке для teacher-документов.
- Тест: `TeacherDateGuardTest` — «после смены даты teacher-день перезагружается», «учительский
  документ без даты не принимается».

---

### [P2] Кэш `WidgetData` не сбрасывается после синка воркера — виджет отдаёт старые пары в ту же минуту

- Где: `widget/NowNextWidgetProvider.kt:245-283` (`WidgetData.todayLessons`, ключ
  `lessonsCacheKey(minute, group, date)`), `worker/ScheduleCheckWorker.kt:118`
  (`updateAllWidgets` без `invalidateCache`), `widget/WidgetUpdateHelper.kt:167` (а вот в
  `ensureTodaySchedule` инвалидация есть — расхождение).
- Симптом/влияние: воркер скачал новое расписание и вызвал `WidgetUpdateHelper.updateAllWidgets`;
  в пределах той же минуты ключ кэша (`date|minute|group`) не изменился → виджет рисует прежние
  уроки. Наблюдаемое: «данные в приложении новые, в виджете старые» (или наоборот).
- Root cause: ключ кэша не содержит версии данных; инвалидация выполняется вручную и в
  `ScheduleCheckWorker` забыта.
- Предлагаемый фикс: добавить в `WidgetData` монотонный `dataVersion` в ключ и увеличивать его в
  `updateAllWidgets()` (или вызывать `WidgetData.invalidateCache()` в начале `updateAllWidgets`).
- Тест: `WidgetDataCacheTest` — «после updateAllWidgets кэш не отдаёт прежний список».

---

### [P2] `MonthGrid`: неверное число дней и клик по несуществующей дате

- Где: `ui/components/MonthGrid.kt:111-137` (`calculateMonthDays`), использование —
  `ui/components/CalendarArchiveDialog.kt:425`.
- Симптом/влияние: архив показывает «февраль» с 31 днём и неверным смещением первого дня; клик по
  30-му числу февраля даёт `2026-02-30`, который `parseDotDate` (lenient Calendar) превращает в
  `02.03.2026` — пользователь выбрал один день, увидел другой. Условие срабатывания: текущее число
  месяца больше длины отображаемого (например, «сегодня» 30-31-е и открыт февраль/30-дневный месяц).
- Root cause: `Calendar.getInstance().apply { set(YEAR); set(MONTH); set(DAY_OF_MONTH, 1) }` — нет
  `clear()`, и `set(MONTH)` идёт до `set(DAY_OF_MONTH, 1)`: при «сегодня» 31.01 и `month=1` объект
  сначала уезжает на 31 февраля → 2-3 марта, затем ставится 1-е → считается максимум марта.
- Предлагаемый фикс: `Calendar.getInstance().apply { clear(); set(year, month, 1, 0, 0, 0) }`.
- Тест: `MonthGridDaysTest` — «февраль невисокосного года = 28 ячеек при «сегодня» 30 марта»,
  «смещение 1-го числа соответствует дню недели».

---

### [P2] URL-резолв: окно поиска только «текущий + предыдущий месяц»; в диагностике показывается URL, который не проверялся

- Где: `data/network/MpkNetworkClient.kt:173-204` (`generateCandidateDirectUrls`),
  `:206-222` (`generateCandidateEventUrls`), `:91-95` (`buildDirectScheduleUrl`), `:326`.
- Симптом/влияние: документ, загруженный за 2+ месяца до даты занятия (каникулы, сессия, перенос,
  публикация «на весь семестр»), прямым перебором не находится. Дальше всё зависит от портала:
  `generateCandidateEventUrls` жёстко предполагает permalink `/raspisanie/{dd}-{mm}-{yyyy}-raspisanie-uchashhihsya/`
  и `?occurrence=…`; если вёрстка иная, остаётся только листинг `SCHEDULE_PORTAL_URL`, где лежат
  только свежие документы. Итог — «расписание ещё не опубликовано» при существующем документе.
  Отдельно: `buildDirectScheduleUrl` (его значение попадает в `SyncDiagnosticInfo.checkedUrl`,
  `:326`) строит путь только по месяцу даты и потому в диагностике показывает URL, которого
  резолвер не пробовал — это сбивает диагностику с устройства.
- Root cause: `folders = [текущий месяц, предыдущий месяц]` — без `nextMonth` и без более глубокого
  окна; нет варианта имени файла без даты; `-1/-2` суффиксы покрыты корректно, `.doc`/`.docx` тоже.
- Предлагаемый фикс: окно `-3..+1` месяц к дате; добавить кандидатов без даты в имени; при провале —
  разобрать листинг `/wp-content/uploads/YYYY/MM/` (если отдаётся) и/или страницу `/raspisanie/`
  с поиском ссылок по дате (`MpkScheduleParser.findSchedulePostLinks` уже умеет);
  `buildDirectScheduleUrl` использовать для диагностики от первого кандидата
  `generateCandidateDirectUrls(...).first()`.
- Тест: расширить `ScheduleUrlResolverTest` — «документ из позапрошлого месяца находится»,
  «checkedUrl в диагностике равен реально проверенному URL».

---

### [P2] Метрика «сколько групп за день разобрано» подмешивает legacy-строки другого дня

- Где: `data/local/dao/LessonDao.kt:71-82` (`getDistinctGroupsForDateOrDay`), используется в
  `ScheduleRepository.kt:274` (`already`), `:486` (`parsedGroups`).
- Симптом/влияние: гейты `shouldFetchDay`/`isFullDayDocument` могут преждевременно решить, что день
  «полный» (в подсчёт попадают группы из blank-строк того же дня недели), и день перестанет
  перекачиваться. На устройствах с legacy-строками это усиливает P0-2.
- Root cause: `WHERE (dateString != '' AND dateString = :dateString) OR (dateString = '' AND dayOfWeek = :dayOfWeek)`
  — для метрики полноты нужны только строки точной даты.
- Предлагаемый фикс: добавить в DAO отдельный запрос `getDistinctGroupsForDate(dateString)` и
  использовать его в `cachedDateOutcome`/`syncDayAllGroupsCached`.
- Тест: `ScheduleCompletenessMetricTest` — «blank-строки не увеличивают parsedGroups».

---

### [P2] `pruneOldLessons`/`isStaleDate`: невалидные даты и blank-строки не чистятся никогда

- Где: `data/repository/ScheduleRepository.kt:125-138` (`isStaleDate`), `:658-664`
  (`pruneOldLessons`), `LessonDao.kt:117-118` (`getAllDistinctDatesSync` — только `dateString != ''`).
- Симптом/влияние: битые даты (`"32.13.2026"` и подобное из кривых документов/restamp) считаются
  «не старыми» (`if (!DATE_DD_MM_YYYY.matches(date)) return false`), а blank-строки не попадают в
  выборку вовсе → мусор живёт вечно и участвует в fallback-выдаче (см. P1 про blank).
- Root cause: отсутствие валидации календарной даты (regex проверяет только форму), фильтр по
  `dateString != ''` для прунинга.
- Предлагаемый фикс: в `isStaleDate` после `cal.set(...)` проверять, что полученные Y/M/D совпадают
  с исходными (иначе считать дату битой и удалять); добавить прунинг blank-строк (одноразово) и
  записей со сдвигом даты > 1 дня от сегодня.
- Тест: `StaleDateSanitizeTest` — «дата 32.13.2026 удаляется», «blank-строки не приходят в выдачу за дату».

---

### [P3] «Расписания на этот день нет» показывается даже сразу после успешного синка пустого дня

- Где: `ui/screens/ScheduleScreen.kt:1814-1826` (`emptyStateTitle`), `data/repository/ScheduleRepository.kt:115-123`
  (`emptyStateText`).
- Симптом/влияние: `emptyStateText(forDate)` вызывается с дефолтными `lastOutcomeDate=""` и
  `outcome=null`, поэтому ветка «Расписание на … загружено» — мёртвый код; после успешной загрузки
  дня без пар пользователь видит «Расписания на этот день нет» и не понимает, проверяли ли сайт.
- Root cause: параметры `lastOutcomeDate`/`outcome` не передаются из UI.
- Предлагаемый фикс: прокинуть последний `SyncOutcome` (он уже есть в `AppUiState`-соседе —
  `ScheduleScreen` получает `isSyncing/hasSyncError`; добавить `lastOutcome`) и различать
  «проверено, пар нет» / «не проверяли».
- Тест: `EmptyStateMessageTest`.

---

### [P3] `fetchScheduleForGroup` по умолчанию считает «пятница после 15:00 → понедельник»

- Где: `data/network/MpkNetworkClient.kt:311-316` (`targetCalendar = calculateTargetCalendar()`),
  `:441-451`.
- Симптом/влияние: скрытая ловушка: любой новый вызов без явного календаря в пятницу вечером
  молча получит понедельник. Сейчас все вызовы в `ScheduleRepository` передают календарь явно,
  поэтому дефект не проявляется, но он ждёт первого неаккуратного вызова.
- Предлагаемый фикс: убрать дефолт (сделать параметр обязательным) либо переименовать в
  `fetchScheduleForGroupForTarget(...)`.
- Тест: не требуется (компилятор зафиксирует).

---

## Журнал фикса (task-5): принятые решения и осознанные правки тестов

- **TTL свежести** (`data/repository/ScheduleFreshness.kt`, SharedPreferences `mpk_schedule_stamps`,
  ключ `дд.ММ.гггг|группа`): сегодня — 15 мин, будущие дни — 6 ч, прошедшие — окончательны
  (`dayCacheTtlMs` → `null`). Кэш-первый рендер сохранён: `syncScheduleDetailed(force=false)` попрежнему
  отдаёт кэш мгновенно, решение об обновлении принимает `AppViewModel` по `staleCachedDays()`.
  Штампа нет (данные от старой версии) — день считается устаревшим ровно один раз после обновления.
- **Порог «доказанной полноты» — 85 % каталога** (`COMPLETE_DOCUMENT_PERCENT`,
  `isCompleteDayDocument`). `isFullDayDocument` (половина каталога) оставлен как порог **записи**
  (частичный документ полезен), но вердикт «вашей группы нет» теперь требует ≥85 %.
- **Negative-cache «день неполный»** живёт 20 мин (`INCOMPLETE_DAY_TTL_MS`) вместо «навсегда».
- **`documentMatchesDate` — fail-closed**: есть дата → сверяем дату; нет даты → сверяем день недели
  из шапки; нет ни того ни другого → reject. Шапка читается широко (`extractHeaderContext`: склейка
  `w:t` до 400 символов для docx, 3 первые строки для .doc) — реальный фикстур
  `05-10-2026-raspisanie-uchashhihsya.doc` по-прежнему принимается.
- **Тихий отброс документа устранён**: `fetchAllGroupsForDate` отбраковывает кандидата, если после
  разбора/перештамповки ни одна строка не относится к запрошенной дате, и идёт к следующему URL
  (`MpkNetworkClient.noteRejectedUrl` / `rejectionSummary()` — причина, без PII). В базу ничего не
  пишется, кэш остаётся прежним.
- **URL-кандидаты**: окно папок расширено до «месяц даты + предыдущий + следующий».
- **P1**: строки без даты больше не пишутся (`replaceSyncedLessons` фильтрует, `pruneOldLessons`
  вычищает legacy `dateString=""`), частичный документ не перезаписывает полный день
  (`syncScheduleForDate` возвращает failure), воркер ставит `checked` только при подтверждённом
  документе (`scheduleDayCheckedAllowed`), смена суток в `AppViewModel` триггерит автосинк.
- **Осознанно обновлены тесты** (меняли закреплённое поведение, что и было целью фикса):
  `SyncCacheFirstTest` — «3/4 каталога = не полный день, идём в сеть» вместо «FoundNoGroup из кэша»
  и комментарий о том, что кэш-первый контракт сохранён, а свежесть решается уровнем выше;
  `ScheduleCacheGateTest` — `FoundNoGroup` для 55/64 (86 %), а не 40/64, + тест порога 85 %;
  `X1DocumentDateTest` — HTML теперь отклоняется (было `assertTrue` на fail-open).
- **Не тронуто (осталось)**: teacher-режим (`teacherScheduleDateFlow` по-прежнему фиксируется при
  создании VM — P1 №5 отчёта), `isStaleDate` без валидации календарной даты, метрика
  `getDistinctGroupsForDateOrDay` подмешивает legacy-строки другого дня, `MonthGrid`.

## Вердикты по 7 гипотезам

1. **Устаревший кэш показывается как актуальный — ПОДТВЕРЖДЕНА.** Нет понятия «свежесть»: автосинк
   выключен при `hasSchedule()`, а `syncScheduleDetailed(force=false)` отдаёт кэш без проверки
   времени. Метки «данные от <время>» в UI нет. См. P0 №1, P0 №2, P1 (blank-строки).
2. **Резолв URL документа — ПОДТВЕРЖДЕНА ЧАСТИЧНО.** `-1/-2`, `.doc/.docx`, оба формата имени,
   `dd.MM.yyyy` и `dd_MM_yyyy` покрыты; ошибки в другом: окно папок только «текущий + предыдущий
   месяц» (нет `+1` и глубже), при провале всё зависит от жёсткого паттерна event-страниц, а
   `buildDirectScheduleUrl` для диагностики показывает URL, который резолвер не пробовал. См. P2.
3. **Сравнение дат — ОПРОВЕРГНУТА в части лексикографии** (все сравнения идут через
   `parseDotDate`/`Calendar`/`timeInMillis`, SQL-сортировка по `substr` для `дд.ММ.гггг` корректна,
   `Dow.of` даёт Пн=1…Вс=7 без сдвига на воскресенье). Но найдены другие дефекты дат: fail-open
   сравнения даты документа (P0 №4), lenient `Calendar` для невалидных дат в `MonthGrid` (P2),
   отсутствие валидации дат в `isStaleDate` (P2). Read-only, без правок.
4. **«Сегодня» (00:05, 23:59, смена даты) — ПОДТВЕРЖДЕНА.** UI-дата переключается корректно
   (`rememberToday` + опрос раз в 60 с + ON_RESUME), но данные за новую дату не запрашиваются:
   в приложении нет триггера на смену даты, `ACTION_DATE_CHANGED` обрабатывает только виджет
   (`TimeChangedReceiver`), а воркер вне окна 10:00–21:00 и вообще без «сегодня» в кандидатах.
   Teacher-режим заморожен ещё сильнее. См. P1 №3, P1 №5.
5. **«Старое расписание может сломаться» — ПОДТВЕРЖДЕНА.** `syncScheduleForDate` пишет день без
   гейта полноты и без проверки даты → частичный документ/HTML затирает полный; `detectedDay` +
   restamp молча выбрасывает документ; архив и виджет подставляют legacy blank-строки; в календаре
   можно выбрать несуществующую дату. См. P1 №1, P1 №2, P0 №3, P2 (MonthGrid).
6. **teacher-режим: слияние — ОПРОВЕРГНУТА** (слияния нет, teacher полностью подменяет поток
   `groupLessons`), **но устаревание — ПОДТВЕРЖДЕНА**: дата «сегодня» фиксируется при создании
   ViewModel, а teacher-документ валидируется только по дню недели, если в шапке нет даты.
   См. P1 №5.
7. **Виджеты: ключ NowNext без даты — ОПРОВЕРГНУТА** (ключ `"$date|$minute|$group"`, дата есть,
   после полуночи он меняется). Но найден реальный дефект: после синка воркера кэш `WidgetData` не
   инвалидируется и в пределах той же минуты виджет рисует прежние пары (P2), а при отсутствии
   сегодняшних строк виджет подставляет legacy blank-строки (P1 №1).

## Топ-5

1. **P0 «Автосинк не обновляет закэшированный день»** — `AppViewModel.kt:212-216` +
   `ScheduleRepository.kt:335-347`. Без TTL свежести «старое расписание» — это норма поведения, а не
   сбой. Самый быстрый и самый ценный фикс.
2. **P0 «День-дырка»** — `isFullDayDocument` = «половина каталога» и `FoundNoGroup` как приговор
   (`ScheduleRepository.kt:34-48, 385-389`). Объясняет «вашей группы нет» при опубликованном
   документе.
3. **P0 «fail-open `documentMatchesDate`»** — `MpkScheduleParser.kt:567-574` + `544-560`. Единственная
   защита от старого документа отключается сама; даёт правдоподобно-неверное «сегодняшнее»
   расписание.
4. **P0 «неверный `detectedDay` → restamp на чужую дату → тихий отброс»** —
   `MpkScheduleParser.kt:817-836, 135-143` + `ScheduleRepository.kt:59-62`. Документ есть, в базе
   ничего, пользователю сообщают «группы нет».
5. **P1 «нет синка при смене даты + воркер не знает про сегодня»** —
   `MainActivity.kt`, `ScheduleCheckWorker.kt:65-67, 93`. Объясняет 00:05 и «сегодня пусто».

## Что проверить руками на устройстве

1. **Свежесть**: закэшировать сегодняшнее расписание, затем на сайте подменить документ за эту дату
   (или временно переименовать файл). Открыть приложение (не нажимая «обновить») → убедиться, что
   показывается старый вариант и нет никакой метки «данные от <время>». Ожидаемое после фикса:
   автообновление или видимая метка давности.
2. **Дырка**: выбрать группу, которой нет в опубликованном документе (обычно 4-й курс в начале
   семестра). Дождаться сообщения «опубликовано, но вашей группы нет», затем дать колледжу дописать
   группы (или подменить документ на полный) → без force приложение должно само подхватить.
3. **Старый документ**: если есть доступ к файлу, скопировать вчерашний .doc под именем сегодняшнего
   (или временно отдать портал со ссылкой на вчерашний). Проверить, что приложение НЕ выдаёт его за
   сегодняшнее (сравнить 1-2 пары с сайтом).
4. **Полночь**: оставить приложение открытым на вкладке «СЕГОДНЯ» в 23:57. В 00:05 проверить, что
   показывает вкладка и появляется ли «Расписания на этот день нет»; после фикса — данные за новую
   дату без ручных действий.
5. **Смена часового пояса**: перевести устройство на +3 → -5 (переход через полночь), посмотреть,
   не остаётся ли вкладка «СЕГОДНЯ» на вчерашней дате и не показывает ли вчерашние пары как текущие.
6. **Виджет «Сейчас/Дальше»**: в момент публикации документа (14:00-16:00) дождаться уведомления и
   сразу посмотреть виджет — совпадает ли с приложением (проверка P2 про кэш `WidgetData`).
7. **Архив/календарь**: открыть архив, полистать месяцы (особенно февраль), выбрать 29-31-е число,
   проверить, что заголовок выбранной даты совпадает с нажатой.
8. **Teacher-режим**: оставить приложение на ночь в режиме преподавателя, утром открыть → должен быть
   новый день (сейчас останется вчерашний).

## Чего не хватает в тестах

- **Нет теста свежести**: `SyncCacheFirstTest` доказывает «кэш = нет сети», но ни один тест не
  проверяет «старый кэш → сеть». Нужен `ScheduleFreshnessGateTest` с управляемыми часами.
- **Нет теста на дату документа как защиты**: `X1DocumentDateTest` проверяет только удачный случай и
  fail-open для HTML; нет кейса «дата в шапке не извлеклась» (а это и есть реальный .docx/.doc).
  Нужен `DocumentDateGuardTest` на реальном фикстуре из `RealSiteDocParserTest`/`LiveNetworkSmokeTest`.
- **Нет теста restamp-безопасности**: `DateRestampTest` и `SyncDateFilterTest` проверяют
  `restampByDow` на корректном дне; нет кейса «`detectedDay` ≠ день шапки» → потеря данных.
- **Нет теста «частичный документ не перезаписывает полный» для `syncScheduleForDate`** (гейт
  покрыт только для `syncScheduleDetailed`).
- **Нет теста смены даты/полуночи** (UI/VM): `TodayProviderTest` покрывает `dayKeyOf`, но не «дата
  сменилась → данные перезапрошены».
- **Нет теста прунинга/санитизации дат** (`isStaleDate` с битой датой, blank-строки).
- **Нет теста кэша `WidgetData` против инвалидации** (сейчас регрессия «воркер обновил, виджет
  показал старое» не отлавливается).
- **Нет теста teacher-документа без даты** (только по дню недели).

## Быстрые победы

1. `AppViewModel.kt:212-216` — убрать гейт `hasSchedule()` и вызывать автосинк всегда; TTL-проверку
   добавить отдельным маленьким хелпером в `ScheduleRepository`. ~40 строк, закрывает главную жалобу.
2. `MpkScheduleParser.kt:567-574` — сделать fail-closed одной строкой (`?: return false`) и расширить
   `extractHeaderText` на несколько `w:t`-ранов. Сразу перестают приниматься чужие документы.
3. `MonthGrid.kt:115-120` — добавить `clear()` и `set(year, month, 1, 0, 0, 0)`. Одна строка.
4. `WidgetUpdateHelper.updateAllWidgets()` — добавить `WidgetData.invalidateCache()` в начало; убирает
   расхождение «приложение/виджет» без структурных изменений.
5. `ScheduleCheckWorker.kt:93` — добавить сегодня в кандидаты; `mark(checkedKey)` ставить только при
   `ProbeResult.Found`/не-`NotFound`. Уведомления начинают приходить.
6. `ScheduleScreen.kt:1842-1853` + `CalendarArchiveDialog` — пометить blank-fallback как «шаблон
   недели» (одна строка в тексте пустого состояния), чтобы пользователь не принимал его за актуальное.