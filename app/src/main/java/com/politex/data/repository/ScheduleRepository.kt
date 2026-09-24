package com.politex.data.repository

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import java.io.IOException
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

private val DATE_DD_MM_YYYY = Regex("""\d{2}\.\d{2}\.\d{4}""")

private const val ARCHIVE_KEEP_DAYS = 60L

/**
 * Б3: нужен ли поход на сайт за днём целиком. Уже разобранные 2+ группы — эвристика
 * «день распарсен»: после обычного синка (он разбирает весь документ по каталогу)
 * повторный запрос был бы лишним, а при недоступном сайте — просто ошибкой.
 */
internal fun shouldFetchDay(alreadyParsedGroups: Int, catalogSize: Int, force: Boolean): Boolean =
    catalogSize > 0 && (force || alreadyParsedGroups < (catalogSize + 1) / 2)

/**
 * Б3: документ признаётся «дневным расписанием всего колледжа», только если в нём
 * набралось не меньше половины каталога групп.
 *
 * Зачем: запись идёт через `replaceForDate` = DELETE(группа, дата) + INSERT, поэтому
 * частичный или чужой документ, отданный сайтом под тем же предсказуемым именем,
 * стёр бы уже лежащее полное расписание этих групп. Не приняли документ — откатываемся
 * на прежний путь по одной группе: своя обновится, чужие останутся нетронутыми.
 */
internal fun isFullDayDocument(groupCount: Int, catalogSize: Int): Boolean =
    catalogSize > 0 && groupCount >= (catalogSize + 1) / 2

internal fun isStaleDate(date: String, cutoffMs: Long): Boolean {
    if (!DATE_DD_MM_YYYY.matches(date)) return false
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(
        date.substring(6, 10).toInt(),
        date.substring(3, 5).toInt() - 1,
        date.substring(0, 2).toInt()
    )
    return cal.timeInMillis < cutoffMs
}

class ScheduleRepository(
    private val lessonDao: LessonDao,
    private val networkClient: MpkNetworkClient = MpkNetworkClient(),

    /**
     * O3-b/Б3: каталог кодов групп колледжа для разбора документа по ВСЕМ группам за один
     * проход. Пустой список (тесты, вызовы без контекста) — перебор не выполняется,
     * синк работает ровно как раньше, по одной группе.
     *
     * ponytail: лямбда, а не Context — репозиторий создаётся в 6 местах (VM, карта,
     * студенты), тащить в них контекст ради кэша расписания не стоит. Когда кэш
     * понадобится вне Android — заменить на источник из assets-парсера.
     */
    private val catalogProvider: () -> List<String> = { emptyList() },

    /**
     * Расписание преподавателей живёт в своей таблице. DAO необязателен: тесты и вызовы
     * без контекста не имеют базы, и тогда офлайн-путь просто не используется.
     */
    private val teacherLessonDao: TeacherLessonDao? = null
) {

    val diagnosticInfo = networkClient.diagnosticInfo

    // Кэш кодов групп: ассет читается один раз на процесс, а не на каждый синк.
    @Volatile
    private var cachedCatalog: List<String>? = null

    private fun catalog(): List<String> =
        cachedCatalog ?: catalogProvider().takeIf { it.isNotEmpty() }?.also { cachedCatalog = it } ?: emptyList()

    /**
     * Б3: один документ на дату разбирается сразу по всем группам колледжа и пишется в Room.
     * После этого чужое расписание, расписание преподавателя и кабинет открываются офлайн —
     * повторный поход на сайт не нужен (в т.ч. когда сайт недоступен из-за VPN).
     *
     * @param force true — качать даже если день уже выглядит распарсенным (ручное
     *              открытие кабинета: пользователь ждёт полные данные).
     */
    suspend fun syncDayAllGroupsCached(
        date: String,
        dow: Int,
        force: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        val groups = catalog()
        if (groups.isEmpty()) return@withContext Result.failure(IOException("Каталог групп пуст"))

        val already = lessonDao.getDistinctGroupsForDateOrDay(date, dow).size
        if (!shouldFetchDay(already, groups.size, force)) {
            return@withContext Result.success(already)
        }
        syncDayAllGroups(date, dow, groups)
    }

    
    fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>> {
        return lessonDao.getLessonsForDay(groupName, dayOfWeek).flowOn(Dispatchers.IO)
    }

    fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>> {
        return lessonDao.getLessonsForDate(groupName, dateString).flowOn(Dispatchers.IO)
    }

    fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> {
        return lessonDao.getLessonsForDateOrDay(groupName, dateString, dayOfWeek).flowOn(Dispatchers.IO)
    }

    fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> {
        return lessonDao.getAllLessonsForGroup(groupName).flowOn(Dispatchers.IO)
    }

    fun getDistinctDates(groupName: String): Flow<List<String>> {
        return lessonDao.getDistinctDatesForGroup(groupName).flowOn(Dispatchers.IO)
    }

    fun searchLessons(groupName: String, query: String): Flow<List<LessonEntity>> {
        return lessonDao.searchLessonsForGroup(groupName, query).flowOn(Dispatchers.IO)
    }

    /** Пары в кабинете на дату (для карты колледжа, «Подробнее» в RoomPanel). */
    fun getLessonsForRoomOnDate(room: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> {
        return lessonDao.getLessonsForRoomOnDate(room, dateString, dayOfWeek).flowOn(Dispatchers.IO)
    }

    /** Т5: группы с уроками сегодня (для быстрого выбора на карте колледжа). */
    suspend fun getDistinctGroupsForDateOrDay(dateString: String, dayOfWeek: Int): List<String> =
        withContext(Dispatchers.IO) {
            lessonDao.getDistinctGroupsForDateOrDay(dateString, dayOfWeek)
        }

    suspend fun syncScheduleFromWeb(
        groupName: String,
        force: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance()
        val tomorrow = nextSchoolDay(today)
        val todayStr = formatDate(today)
        val tomorrowStr = formatDate(tomorrow)

        val todayCached = lessonDao.getLessonCountForDate(groupName, todayStr)
        val tomorrowCached = lessonDao.getLessonCountForDate(groupName, tomorrowStr)

        val needToday = force || todayCached == 0
        val needTomorrow = force || tomorrowCached == 0

        if (!needToday && !needTomorrow) {

            return@withContext Result.success(todayCached + tomorrowCached)
        }

        val fetched = mutableListOf<LessonEntity>()
        var fetchFailed = false
        var documentFound = false

        // Б3/F2: каталог есть → день качается ОДИН раз и разбирается сразу по всем группам
        // (parseFileAllGroups). Двойной заход (своя группа + все группы) означал бы
        // дважды скачанный и дважды разобранный один и тот же документ на дату.
        // Запрошенная группа добавляется к каталогу: её может ещё не быть в ассете.
        // Каталога нет (тесты, вызов без контекста) → прежний путь по одной группе.
        val allGroups = (catalog() + groupName).distinct()
        val useCatalog = catalog().isNotEmpty()
        val days = listOf(tomorrow to needTomorrow, today to needToday).filter { it.second }
        if (useCatalog) {
            for ((calendar, _) in days) {
                val date = formatDate(calendar)
                runCatching { fetchAllGroupsForDate(calendar, date, allGroups) { documentFound = documentFound || it } }
                    .onSuccess { dayLessons ->
                        // F1: частичный/чужой документ под тем же именем не принимаем —
                        // replaceForDate затрёт полные данные других групп
                        val dayGroups = dayLessons.map { it.groupName }.distinct().size
                        if (isFullDayDocument(dayGroups, allGroups.size)) fetched += dayLessons
                    }
                    .onFailure { fetchFailed = true }
            }
        } else {
            if (needTomorrow) {
                networkClient.fetchScheduleForGroup(groupName, tomorrow)
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
            if (needToday) {
                networkClient.fetchScheduleForGroup(groupName)
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
        }

        val distinct = fetched
            .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }

        // F5: документ получен, но по своей группе в нём ничего не нашлось — откат на
        // прежний путь с диагностикой (группа может стоять под другим написанием, либо
        // URL отдал не тот файл). Без этого своя группа молча оставалась без расписания.
        if (distinct.none { it.groupName == groupName } && documentFound) {
            if (needTomorrow) {
                networkClient.fetchScheduleForGroup(groupName, tomorrow)
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
            if (needToday) {
                networkClient.fetchScheduleForGroup(groupName)
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
        }

        val finalLessons = fetched
            .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
        return@withContext when {
            finalLessons.isNotEmpty() -> {
                writeByGroup(finalLessons)
                // Б3: заодно кэшируем расписание преподавателей тех же дней — тогда карточка
                // преподавателя открывается офлайн, как группа и кабинет. Ошибка этой
                // догрузки не должна портить основной результат: она вспомогательная.
                days.forEach { (calendar, _) ->
                    runCatching { syncTeacherScheduleForDate(calendar) }
                }
                pruneOldLessons()
                Result.success(finalLessons.size)
            }
            // Сеть отвалилась — честная ошибка (тост «проверьте интернет»).
            // Пустая успешная загрузка («расписание ещё не опубликовано») ошибкой не
            // считается: success(0) → тост «новых данных пока нет», как и раньше.
            fetchFailed -> Result.failure(IOException("Не удалось получить расписание"))
            else -> Result.success(0)
        }
    }

    /**
     * Б3: синк конкретной даты (воркер уведомлений: «вышло расписание на завтра»).
     * Качает один документ и разбирает его по всем группам каталога, пишет в Room.
     * Возвращает уроки ТОЛЬКО запрошенной группы — их воркер показывает в уведомлении.
     * Без каталога (тесты) откатывается к разбору одной группы.
     */
    suspend fun syncScheduleForDate(
        groupName: String,
        targetCalendar: Calendar
    ): Result<List<LessonEntity>> = withContext(Dispatchers.IO) {
        // Б3/F2: запрошенная группа добавляется к каталогу — иначе новая группа, которой
        // ещё нет в ассете group_codes.csv, не попадёт в разбор, и воркер молча не уведомит
        val allGroups = (catalog() + groupName).distinct()
        if (allGroups.isEmpty()) return@withContext networkClient.fetchScheduleForGroup(groupName, targetCalendar)

        val date = formatDate(targetCalendar)
        var documentFound = false
        val forGroup = runCatching {
            fetchAllGroupsForDate(targetCalendar, date, allGroups) { documentFound = it }
        }
        if (forGroup.isFailure) return@withContext Result.failure(forGroup.exceptionOrNull()!!)
        val all = forGroup.getOrDefault(emptyList())
        if (all.isNotEmpty()) {
            writeByGroup(all)
            pruneOldLessons()
        }

        val mine = all.filter { it.groupName == groupName }
        // F5: документ найден, но по запрошенной группе пусто (группа под другим написанием,
        // документ отдан не тем файлом) — откат на прежний путь по одной группе с диагностикой,
        // иначе уведомление перестало бы приходить там, где приходило.
        if (mine.isEmpty() && documentFound) {
            return@withContext networkClient.fetchScheduleForGroup(groupName, targetCalendar)
        }
        Result.success(mine)
    }

    /**
     * Б3: разбор одного документа на дату по всем группам каталога — прямыми именами,
     * затем через портал. Фолбэк на портал обязателен: сайт публикует документы и под
     * нестандартными именами (`raspisanie_03_03_2025.docx`, `izmeneniya_04_03.doc` —
     * такие есть в фикстурах проекта), а предсказуемые URL в этом случае дают 404.
     * Без фолбэка синк молча решил бы, что расписание не опубликовано.
     *
     * @param documentFound отличает «документа нет на сайте» от «документ есть, но не разобрался»
     */
    private suspend fun fetchAllGroupsForDate(
        calendar: Calendar,
        date: String,
        candidateGroups: List<String>,
        documentFound: (Boolean) -> Unit = {}
    ): List<LessonEntity> {
        val triedUrls = mutableSetOf<String>()

        for ((url, _) in networkClient.generateCandidateDirectUrls(calendar)) {
            if (!triedUrls.add(url)) continue
            val bytes = networkClient.downloadFileBytes(url) ?: continue
            if (bytes.isEmpty()) continue
            documentFound(true)
            val parsed = MpkScheduleParser.parseFileAllGroups(bytes, date, candidateGroups)
            if (parsed.isNotEmpty()) return parsed
        }

        // Фолбэк на портал — тот же путь, что в syncDayAllGroups и fetchScheduleForGroup
        for (url in MpkScheduleParser.extractDocumentUrls(portalHtml())) {
            if (!triedUrls.add(url)) continue
            val bytes = networkClient.downloadFileBytes(url) ?: continue
            if (bytes.isEmpty()) continue
            documentFound(true)
            val parsed = MpkScheduleParser.parseFileAllGroups(bytes, date, candidateGroups)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    /** Запись разобранного дня, сгруппированного по группам (одна транзакция на группу). */
    private suspend fun writeByGroup(lessons: List<LessonEntity>) {
        lessons.groupBy { it.groupName }.forEach { (group, groupLessons) ->
            if (group.isNotBlank()) replaceSyncedLessons(group, groupLessons)
        }
    }

    // --- Расписание преподавателей: офлайн-кэш ---

    /**
     * Один документ «расписание преподавателей» качается и разбирается в базу.
     *
     * Раньше карточка преподавателя качала документ при каждом открытии (кэш жил
     * только в памяти экрана), поэтому при недоступном сайте — например, включён VPN —
     * расписание преподавателя не открывалось вовсе. Теперь документ дня лежит в
     * `teacher_lessons` и читается оттуда.
     *
     * @param force true — перечитать даже если день уже в базе
     */
    suspend fun syncTeacherScheduleForDate(
        targetCalendar: Calendar,
        force: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        val dao = teacherLessonDao ?: return@withContext Result.success(0)
        val date = formatDate(targetCalendar)

        if (!force && dao.getPresentDates(listOf(date)).contains(date)) {
            return@withContext Result.success(
                dao.getTeachersOnDate(date).size
            )
        }

        val fetched = networkClient.fetchTeacherSchedule(targetCalendar)
        val parsedAll = fetched.getOrElse { return@withContext Result.failure(it) }
        if (parsedAll.isEmpty()) return@withContext Result.success(0)

        // День документов не всегда распознаётся по заголовку; тогда берём день недели
        // запрошенной даты — иначе строки легли бы под dayOfWeek = 0 и вкладка
        // «Расписание» показывала бы их под любой датой (это чинил O7).
        val requestedDow = MpkScheduleParser.dowOfCalendar(targetCalendar)
        val docDow = parsedAll.values.firstOrNull()?.firstOrNull()?.dayOfWeek ?: 0
        // Документ другого дня в базу не пишем: иначе под запрошенной датой окажется
        // расписание чужого дня
        if (docDow != 0 && docDow != requestedDow) {
            return@withContext Result.failure(IOException("Документ за другой день"))
        }

        val rows = parsedAll.flatMap { (rawName, slots) ->
            val surname = TeacherInsights.surnameOf(rawName)
            if (surname.isBlank()) emptyList()
            else slots.map { slot ->
                TeacherLessonEntity(
                    teacherName = rawName.trim(),
                    surnameKey = surname,
                    dateString = date,
                    dayOfWeek = if (slot.dayOfWeek != 0) slot.dayOfWeek else requestedDow,
                    lessonNumber = slot.lessonNumber,
                    groups = slot.groups.joinToString(", "),
                    subject = slot.subject,
                    room = slot.room
                )
            }
        }.distinctBy { "${it.surnameKey}_${it.lessonNumber}_${it.room}_${it.subject}" }

        if (rows.isNotEmpty()) dao.replaceForDate(date, rows)
        Result.success(rows.size)
    }

    /** Занятия преподавателя на дату из офлайн-кэша (пусто — нет данных этого дня). */
    suspend fun getTeacherLessonsForDate(
        teacherSurname: String,
        dateString: String
    ): List<TeacherLessonEntity> = withContext(Dispatchers.IO) {
        val dao = teacherLessonDao ?: return@withContext emptyList()
        dao.getForSurnameOnDate(teacherSurname, dateString)
    }

    /** Есть ли в базе расписание преподавателей на дату (чтобы не ходить в сеть). */
    suspend fun hasTeacherScheduleForDate(dateString: String): Boolean = withContext(Dispatchers.IO) {
        val dao = teacherLessonDao ?: return@withContext false
        dao.getPresentDates(listOf(dateString)).isNotEmpty()
    }

    /**
     * O3b: догрузка уроков ВСЕХ групп колледжа на одну дату — из того же документа, что
     * обычный синк. Нужна для «Подробнее — расписание кабинета»: в БД иначе лежат уроки только
     * выбранной группы. Выбранную группу пользователя (currentGroupName в настройках) не трогает.
     * Возвращает число записанных строк (0 — документ не найден/не разобран).
     */
    suspend fun syncDayAllGroups(
        date: String,
        dow: Int,
        candidateGroups: List<String> = emptyList()
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (candidateGroups.isEmpty()) return@withContext Result.success(0)
        val calendar = parseCalendarDate(date) ?: return@withContext Result.success(0)

        val fetched = mutableListOf<LessonEntity>()
        val triedUrls = mutableSetOf<String>()
        var documentFound = false

        try {
            for ((url, _) in networkClient.generateCandidateDirectUrls(calendar)) {
                if (!triedUrls.add(url)) continue
                val bytes = networkClient.downloadFileBytes(url) ?: continue
                if (bytes.isEmpty()) continue
                documentFound = true
                val parsed = MpkScheduleParser.parseFileAllGroups(bytes, date, candidateGroups)
                if (parsed.isNotEmpty()) {
                    fetched += parsed
                    break
                }
            }

            // Фолбэк на портал: тот же путь, что в fetchScheduleForGroup
            if (fetched.isEmpty()) {
                val documentUrls = MpkScheduleParser.extractDocumentUrls(portalHtml())
                for (url in documentUrls) {
                    if (!triedUrls.add(url)) continue
                    val bytes = networkClient.downloadFileBytes(url) ?: continue
                    if (bytes.isEmpty()) continue
                    documentFound = true
                    val parsed = MpkScheduleParser.parseFileAllGroups(bytes, date, candidateGroups)
                    if (parsed.isNotEmpty()) fetched += parsed
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (fetched.isEmpty()) {
            return@withContext if (documentFound) Result.success(0)
            else Result.failure(IOException("Расписание на $date не опубликовано"))
        }

        // Ключ перезаписи — группа + дата (replaceForDate внутри replaceSyncedLessons)
        val forDate = fetched
            .filter { it.dateString == date }
            .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
            .groupBy { it.groupName }

        var written = 0
        for ((group, lessons) in forDate) {
            replaceSyncedLessons(group, lessons)
            written += lessons.size
        }

        pruneOldLessons()
        Result.success(written)
    }

    private suspend fun portalHtml(): String {
        val bytes = networkClient.downloadFileBytes(MpkNetworkClient.SCHEDULE_PORTAL_URL) ?: return ""
        return String(bytes, Charsets.UTF_8)
    }

    /** Calendar из «dd.MM.yyyy» (день недели из строки не берём — в БД сравнения по epoch). */
    private fun parseCalendarDate(date: String): Calendar? {
        if (!DATE_DD_MM_YYYY.matches(date)) return null
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(date.substring(6, 10).toInt(), date.substring(3, 5).toInt() - 1, date.substring(0, 2).toInt())
        return cal
    }

    suspend fun pruneOldLessons(keepDays: Long = ARCHIVE_KEEP_DAYS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L
        for (date in lessonDao.getAllDistinctDatesSync()) {
            if (isStaleDate(date, cutoff)) lessonDao.deleteLessonsByDate(date)
        }
    }

    private fun nextSchoolDay(base: Calendar = Calendar.getInstance()): Calendar {
        val cal = (base.clone() as Calendar)
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
        return cal
    }

    private fun formatDate(calendar: Calendar): String =
        "%02d.%02d.%04d".format(
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )

    suspend fun replaceSyncedLessons(groupName: String, lessons: List<LessonEntity>) = withContext(Dispatchers.IO) {
        // O7: замена одной транзакцией на дату — DELETE и INSERT неразделимы, иначе Flow
        // эмитит пустой список и в UI мелькает «расписание не опубликовано».
        lessons.groupBy { it.dateString }.forEach { (dateString, dayLessons) ->
            if (dateString.isBlank()) {
                // Недельный шаблон: удалять нечего (нет привязки к дате), только вставка
                lessonDao.insertLessons(dayLessons)
            } else {
                lessonDao.replaceForDate(groupName, dateString, dayLessons)
            }
        }
    }

    suspend fun saveLessons(lessons: List<LessonEntity>) = withContext(Dispatchers.IO) {
        lessonDao.insertLessons(lessons)
    }

    suspend fun replaceSchedule(groupName: String, lessons: List<LessonEntity>) = withContext(Dispatchers.IO) {
        lessonDao.replaceScheduleForGroup(groupName, lessons)
    }

    suspend fun clearSchedule(groupName: String) = withContext(Dispatchers.IO) {
        lessonDao.clearLessonsForGroup(groupName)
    }

    suspend fun hasSchedule(groupName: String): Boolean = withContext(Dispatchers.IO) {
        lessonDao.getLessonCountForGroup(groupName) > 0
    }
}
