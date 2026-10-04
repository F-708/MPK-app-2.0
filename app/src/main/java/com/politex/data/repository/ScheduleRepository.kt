package com.politex.data.repository

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.ProbeResult
import com.politex.util.Dow
import com.politex.util.GroupParser
import com.politex.util.lessonsWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

private val DATE_DD_MM_YYYY = Regex("""\d{2}\.\d{2}\.\d{4}""")

private const val ARCHIVE_KEEP_DAYS = 60L

/**
 * Б3: нужен ли поход на сайт за днём целиком. Уже разобранные 2+ группы — эвристика
 * «день распарсен»: после обычного синка (он разбирает весь документ по каталогу)
 * повторный запрос был бы лишним, а при недоступном сайте — просто ошибкой.
 */
internal fun shouldFetchDay(
    alreadyParsedGroups: Int,
    catalogSize: Int,
    force: Boolean,
): Boolean = catalogSize > 0 && (force || alreadyParsedGroups < (catalogSize + 1) / 2)

/**
 * Б3: документ признаётся «дневным расписанием всего колледжа», только если в нём
 * набралось не меньше половины каталога групп.
 *
 * Зачем: запись идёт через `replaceForDate` = DELETE(группа, дата) + INSERT, поэтому
 * частичный или чужой документ, отданный сайтом под тем же предсказуемым именем,
 * стёр бы уже лежащее полное расписание этих групп. Не приняли документ — откатываемся
 * на прежний путь по одной группе: своя обновится, чужие останутся нетронутыми.
 */
internal fun isFullDayDocument(
    groupCount: Int,
    catalogSize: Int,
): Boolean = catalogSize > 0 && groupCount >= (catalogSize + 1) / 2

/**
 * N2: сколько уроков ЗАПРОШЕННОЙ группы вернул синк за сегодня+завтра. Документ разбирается
 * по всему каталогу, поэтому в `finalLessons` лежат строки всех групп; тост «обновлено (N уроков)»
 * должен показывать N по своей группе, а не общее число по колледжу.
 */
internal fun syncedCountForGroup(
    finalLessons: List<LessonEntity>,
    groupName: String,
): Int = finalLessons.count { it.groupName == groupName }

/**
 * Y1: различимый исход синка. Раньше `success(0)` одинаково означал и «документ не
 * опубликован», и «документ есть, но твоей группы в нём нет» — UI показывал «Найдено,
 * скачиваю…»/«обновлено», вводя в заблуждение. [count] — уроки ЗАПРОШЕННОЙ группы.
 */
sealed class SyncOutcome {
    abstract val count: Int

    /** Ни один кандидат/водопад не дал документ нужной даты (probe 404 / чужой документ). */
    object NotFound : SyncOutcome() {
        override val count = 0
    }

    /** Документ даты опубликован и принят (прошёл date-валидацию), но запрошенной группы в нём нет. */
    data class FoundNoGroup(val date: String) : SyncOutcome() {
        override val count = 0
    }

    /** Документ разобран, у запрошенной группы [count] уроков. */
    data class Ok(override val count: Int) : SyncOutcome()
}

/**
 * Y1: исход из сигналов (чисто, тестируемо). [groupLessons] — уроки запрошенной группы
 * после разбора; [documentFound] — принят ли документ, ПРОШЕДШИЙ date-валидацию
 * (`documentMatchesDate`). Рассинхрон даты / чужой документ → documentFound=false → NotFound.
 */
internal fun resolveSyncOutcome(
    groupLessons: Int,
    documentFound: Boolean,
    targetDate: String,
): SyncOutcome = when {
    groupLessons > 0 -> SyncOutcome.Ok(groupLessons)
    documentFound -> SyncOutcome.FoundNoGroup(targetDate)
    else -> SyncOutcome.NotFound
}

/** Y1: честная строка статуса (diagnosticInfo / empty-state) по исходу. */
internal fun statusMessageFor(outcome: SyncOutcome): String = when (outcome) {
    is SyncOutcome.Ok -> "Расписание загружено (${outcome.count} ${lessonsWord(outcome.count)})"
    is SyncOutcome.FoundNoGroup -> "Расписание на ${outcome.date} опубликовано, но вашей группы в нём пока нет"
    SyncOutcome.NotFound -> "Расписание ещё не опубликовано"
}

/** Y1: честная строка тоста (AppViewModel) по исходу. */
internal fun toastFor(outcome: SyncOutcome, group: String): String = when (outcome) {
    is SyncOutcome.Ok -> "Расписание группы $group обновлено (${outcome.count} ${lessonsWord(outcome.count)})"
    is SyncOutcome.FoundNoGroup -> "Расписание на ${outcome.date} опубликовано, но вашей группы ($group) в нём пока нет"
    SyncOutcome.NotFound -> "Расписание ещё не опубликовано"
}

internal fun isStaleDate(
    date: String,
    cutoffMs: Long,
): Boolean {
    if (!DATE_DD_MM_YYYY.matches(date)) return false
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(
        date.substring(6, 10).toInt(),
        date.substring(3, 5).toInt() - 1,
        date.substring(0, 2).toInt(),
    )
    return cal.timeInMillis < cutoffMs
}

/**
 * W1: следующий учебный день после base. Суббота — учебный день только для 1 курса
 * (GroupInfo.hasSaturdayClasses, как в UI/воркере), воскресенье — не учебный никогда.
 *
 * Раньше синк считал «завтра» просто «+1 день минус воскресенье»: в пятницу для групп
 * 2–4 курсов он качал СУББОТУ, а UI показывал вкладку ПОНЕДЕЛЬНИКА — документ
 * понедельника (уже опубликованный на сайте и проверенный curl'ом 2026-10-02,
 * /2026/10/05.10.2026-raspisanie-uchashhihsya.doc, 200 OK) вообще не запрашивался.
 * Это и был root cause «расписание есть на сайте, а приложение говорит
 * „ещё не опубликовано“».
 */
internal fun nextSchoolDay(
    base: Calendar,
    saturdayIsSchoolDay: Boolean,
): Calendar {
    val cal = (base.clone() as Calendar)
    do {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
        (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY && !saturdayIsSchoolDay))
    return cal
}

/**
 * W1: суббота учебная для группы? Как в UI (GroupScheduleScreen): 1 курс ИЛИ у группы
 * уже есть субботние уроки в БД (правило курса — эвристика, фактические данные точнее).
 */
internal fun saturdayIsSchoolDayFor(
    groupName: String,
    existingSaturdayLessons: Boolean,
): Boolean = GroupParser.parse(groupName)?.hasSaturdayClasses == true || existingSaturdayLessons

/** V2: цель «завтра» — дата (null, если показывать пока нечего) и подпись слота. */
data class NextDayTarget(val date: Calendar?, val label: String)

/**
 * V2: единая логика «завтра» с фолбэком суббота→понедельник:
 * 1) завтра — учебный день (пн-пт, либо учебная суббота) → завтра, «Завтра»;
 * 2) завтра — НЕучебная суббота, но на неё ЕСТЬ расписание (группа реально учится,
 *    правило курса не сработало) → тоже суббота, «Завтра»;
 * 3) иначе ближайший понедельник: расписание на него уже есть → понедельник, «Понедельник»;
 * 4) ничего нет → null (UI подставит обычный nextSchoolDay), «Понедельник».
 */
internal fun nextSchoolDayWithFallback(
    today: Calendar,
    saturdayIsSchoolDay: Boolean,
    hasSchedule: (Calendar) -> Boolean,
): NextDayTarget {
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val tomorrowDow = Dow.of(tomorrow)
    if (tomorrowDow in 1..5 || (tomorrowDow == 6 && saturdayIsSchoolDay)) return NextDayTarget(tomorrow, "Завтра")
    if (tomorrowDow == 6 && hasSchedule(tomorrow)) return NextDayTarget(tomorrow, "Завтра")
    val monday = (today.clone() as Calendar).apply { do { add(Calendar.DAY_OF_YEAR, 1) } while (Dow.of(this) != 1) }
    return if (hasSchedule(monday)) NextDayTarget(monday, "Понедельник") else NextDayTarget(null, "Понедельник")
}

/**
 * V2: кандидаты «завтра» в порядке перебора (первый найденный на сайте — цель):
 * учебное завтра → [завтра]; неучебная суббота → [сб, пн]; воскресенье → [пн].
 */
internal fun nextDayCandidates(
    today: Calendar,
    saturdayIsSchoolDay: Boolean,
): List<Calendar> {
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val monday = (today.clone() as Calendar).apply { do { add(Calendar.DAY_OF_YEAR, 1) } while (Dow.of(this) != 1) }
    val dow = Dow.of(tomorrow)
    return when {
        dow in 1..5 || (dow == 6 && saturdayIsSchoolDay) -> listOf(tomorrow)
        dow == 6 -> listOf(tomorrow, monday)
        else -> listOf(monday)
    }
}

/**
 * V6a: байты — реальный документ (docx = zip PK\x03\x04, doc = OLE2 D0 CF 11 E0)?
 * Сайт отдаёт HTML-страницы (event-страница, soft-404) с кодом 200 — без проверки магии
 * они считались бы «документ найден» и глушили фолбэк-водопад.
 */
internal fun looksLikeDocument(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val docx = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
    val doc = bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
    return docx || doc
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
    private val teacherLessonDao: TeacherLessonDao? = null,
) {
    val diagnosticInfo = networkClient.diagnosticInfo

    // Кэш кодов групп: ассет читается один раз на процесс, а не на каждый синк.
    @Volatile
    private var cachedCatalog: List<String>? = null

    // W6: internal — карта проверяет непустоту каталога (гейт force-синка кабинета)
    internal fun catalog(): List<String> = cachedCatalog ?: catalogProvider().takeIf { it.isNotEmpty() }?.also { cachedCatalog = it } ?: emptyList()

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
        force: Boolean = false,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val groups = catalog()
        if (groups.isEmpty()) return@withContext Result.failure(IOException("Каталог групп пуст"))

        val already = lessonDao.getDistinctGroupsForDateOrDay(date, dow).size
        if (!shouldFetchDay(already, groups.size, force)) {
            return@withContext Result.success(already)
        }
        syncDayAllGroups(date, dow, groups)
    }

    fun getLessonsForDay(
        groupName: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForDay(groupName, dayOfWeek).flowOn(Dispatchers.IO)

    fun getLessonsForDate(
        groupName: String,
        dateString: String,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForDate(groupName, dateString).flowOn(Dispatchers.IO)

    fun getLessonsForDateOrDay(
        groupName: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForDateOrDay(groupName, dateString, dayOfWeek).flowOn(Dispatchers.IO)

    fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> = lessonDao.getAllLessonsForGroup(groupName).flowOn(Dispatchers.IO)

    fun getDistinctDates(groupName: String): Flow<List<String>> = lessonDao.getDistinctDatesForGroup(groupName).flowOn(Dispatchers.IO)

    /** Пары в кабинете на дату (для карты колледжа, «Подробнее» в RoomPanel). */
    fun getLessonsForRoomOnDate(
        room: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForRoomOnDate(room, dateString, dayOfWeek).flowOn(Dispatchers.IO)

    /** Т5: группы с уроками сегодня (для быстрого выбора на карте колледжа). */
    suspend fun getDistinctGroupsForDateOrDay(
        dateString: String,
        dayOfWeek: Int,
    ): List<String> = withContext(Dispatchers.IO) {
        lessonDao.getDistinctGroupsForDateOrDay(dateString, dayOfWeek)
    }

    /**
     * Y1: обёртка для вызовов, которым достаточно числа уроков своей группы (StudentsScreens).
     * Подробный различимый исход — в [syncScheduleDetailed].
     */
    suspend fun syncScheduleFromWeb(
        groupName: String,
        force: Boolean = false,
    ): Result<Int> = syncScheduleDetailed(groupName, force)
        .fold({ Result.success(it.count) }, { Result.failure(it) })

    /**
     * Y1: честные статусы синка. Возвращает различимый [SyncOutcome], чтобы UI/тосты не
     * показывали «Найдено, скачиваю…»/«обновлено» там, где документа нужной даты нет или
     * в нём нет запрошенной группы. «Найдено, скачиваю…» ставится ТОЛЬКО когда документ
     * прошёл date-валидацию и сейчас будет разобран (колбэк documentFound ниже).
     */
    suspend fun syncScheduleDetailed(
        groupName: String,
        force: Boolean = false,
    ): Result<SyncOutcome> = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance()
        // W1/V2: «завтра» = первый кандидат (завтра → суббота → понедельник), который либо
        // уже лежит в кэше, либо документ которого probe нашёл на сайте. Не нашёл ничего —
        // последний кандидат (понедельник): водопад fetchAllGroupsForDate (V6a) поищет там
        // документ полностью, включая event-страницы и портал.
        val saturdaySchool = saturdayIsSchoolDayFor(
            groupName,
            lessonDao.getAllLessonsForGroup(groupName).first().any { it.dayOfWeek == 6 },
        )
        val candidates = nextDayCandidates(today, saturdaySchool)
        val todayStr = formatDate(today)

        // N1/V6a: «рукопожатие» ДО полной загрузки. Probe NotFound больше НЕ причина раннего
        // выхода: предсказуемые URL — лишь быстрый путь, документ под нестандартным именем
        // (корень бага 2026-10-02) находит полный водопад ниже.
        networkClient.publishStatus(statusMessage = "Проверяю расписание…", isSyncing = true)
        val todayCached = lessonDao.getLessonCountForDate(groupName, todayStr)
        val needToday = force || todayCached == 0
        var probed: Calendar? = candidates.firstOrNull {
            !force && lessonDao.getLessonCountForDate(groupName, formatDate(it)) > 0
        }
        var probeNetworkError = false
        // W1a: URL, который probe уже нашёл, — синк скачает его ПЕРВЫМ, без водопада
        val probeFoundUrl = mutableMapOf<String, String>()
        if (probed == null) {
            for (candidate in candidates) {
                when (val probe = networkClient.probeDocument(candidate)) {
                    is ProbeResult.Found -> {
                        probed = candidate
                        probeFoundUrl[formatDate(candidate)] = probe.url
                        break
                    }
                    ProbeResult.NetworkError -> probeNetworkError = true
                    is ProbeResult.NotFound -> Unit
                }
            }
        }
        val tomorrow: Calendar = probed ?: candidates.last()
        val tomorrowStr = formatDate(tomorrow)
        val tomorrowCached = lessonDao.getLessonCountForDate(groupName, tomorrowStr)

        val needTomorrow = force || tomorrowCached == 0

        if (!needToday && !needTomorrow) {
            // Y1(d): финальный статус + isSyncing=false — иначе «Проверяю расписание…» залипает
            networkClient.publishStatus(statusMessage = "Расписание уже загружено", isSyncing = false)
            return@withContext Result.success(SyncOutcome.Ok(todayCached + tomorrowCached))
        }
        // Y1: «Найдено, скачиваю…» больше НЕ ставится по одному факту probe-Found. Его ставит
        // колбэк documentFound в момент, когда документ ПРОШЁЛ date-валидацию и будет разобран.
        // Здесь — только честное «ищу» (когда probe ничего не нашёл и сеть жива).
        if (probeFoundUrl.isEmpty() && !probeNetworkError) {
            // V6a: probe ничего не нашёл — впереди полный водопад (event-страницы + портал)
            networkClient.publishStatus(statusMessage = "Ищу на портале…")
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
                // W1a: probe уже знает URL этого дня — качаем его первым
                runCatching {
                    fetchAllGroupsForDate(calendar, date, allGroups, probeFoundUrl[date]) { found ->
                        if (found) {
                            documentFound = true
                            // Y1: документ прошёл documentMatchesDate и сейчас будет разобран —
                            // только теперь честно сказать «Найдено, скачиваю…»
                            networkClient.publishStatus(statusMessage = "Найдено, скачиваю…")
                        }
                    }
                }
                    .onSuccess { dayLessons ->
                        // F1: частичный/чужой документ под тем же именем не принимаем —
                        // replaceForDate затрёт полные данные других групп
                        val dayGroups = dayLessons.map { it.groupName }.distinct().size
                        if (isFullDayDocument(dayGroups, allGroups.size)) fetched += dayLessons
                    }.onFailure { fetchFailed = true }
            }
        } else {
            if (needTomorrow) {
                networkClient
                    // W1a: явная дата + URL от probe первым
                    .fetchScheduleForGroup(groupName, tomorrow, preferredUrl = probeFoundUrl[tomorrowStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
            if (needToday) {
                networkClient
                    // W1: явный today — прежде календарь по умолчанию (calculateTargetCalendar)
                    // в пятницу вечером уходил на понедельник, а не на запрошенный день
                    .fetchScheduleForGroup(groupName, today, preferredUrl = probeFoundUrl[todayStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
        }

        val distinct =
            fetched
                .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }

        // F5: документ получен, но по своей группе в нём ничего не нашлось — откат на
        // прежний путь с диагностикой (группа может стоять под другим написанием, либо
        // URL отдал не тот файл). Без этого своя группа молча оставалась без расписания.
        if (distinct.none { it.groupName == groupName } && documentFound) {
            if (needTomorrow) {
                networkClient
                    .fetchScheduleForGroup(groupName, tomorrow, preferredUrl = probeFoundUrl[tomorrowStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
            if (needToday) {
                networkClient
                    .fetchScheduleForGroup(groupName, today, preferredUrl = probeFoundUrl[todayStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
        }

        val finalLessons =
            fetched
                .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }

        // Y1: пишем разбор (в т.ч. чужие группы — кэш для карты/преподавателя), если есть что
        if (finalLessons.isNotEmpty()) {
            writeByGroup(finalLessons)
            // Б3: кэшируем расписание преподавателей тех же дней (ошибка догрузки не портит исход)
            days.forEach { (calendar, _) -> runCatching { syncTeacherScheduleForDate(calendar) } }
            pruneOldLessons()
        }

        // Y1: различимый исход по своей группе + documentFound (date-валидированный документ).
        // documentFound важнее fetchFailed: документ нужной даты получен — это не сетевая ошибка.
        val groupCount = syncedCountForGroup(finalLessons, groupName)
        val outcome = resolveSyncOutcome(groupCount, documentFound, tomorrowStr)
        return@withContext if (outcome is SyncOutcome.NotFound && fetchFailed) {
            // Сеть отвалилась и ничего не получили — честная ошибка (тост «проверьте интернет»).
            networkClient.publishStatus(statusMessage = "Не удалось получить расписание", isSyncing = false)
            Result.failure(IOException("Не удалось получить расписание"))
        } else {
            networkClient.publishStatus(statusMessage = statusMessageFor(outcome), isSyncing = false)
            Result.success(outcome)
        }
    }

    /** V2: probe даты для воркера — есть ли на сайте документ этого дня (дёшево, без скачивания). */
    suspend fun probeDate(calendar: Calendar): ProbeResult = networkClient.probeDocument(calendar)

    /**
     * Б3: синк конкретной даты (воркер уведомлений: «вышло расписание на завтра»).
     * Качает один документ и разбирает его по всем группам каталога, пишет в Room.
     * Возвращает уроки ТОЛЬКО запрошенной группы — их воркер показывает в уведомлении.
     * Без каталога (тесты) откатывается к разбору одной группы.
     */
    suspend fun syncScheduleForDate(
        groupName: String,
        targetCalendar: Calendar,
    ): Result<List<LessonEntity>> = withContext(Dispatchers.IO) {
        // Б3/F2: запрошенная группа добавляется к каталогу — иначе новая группа, которой
        // ещё нет в ассете group_codes.csv, не попадёт в разбор, и воркер молча не уведомит
        val allGroups = (catalog() + groupName).distinct()
        if (allGroups.isEmpty()) return@withContext networkClient.fetchScheduleForGroup(groupName, targetCalendar)

        val date = formatDate(targetCalendar)
        var documentFound = false
        val forGroup =
            runCatching {
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
        // WID-03: документа на сайте нет вовсе — это ошибка, а не «пустой день».
        // Иначе воркер отметил бы день проверенным и больше не пытался до вечера.
        if (mine.isEmpty() && !documentFound) {
            return@withContext Result.failure(IOException("Документ на $date не найден"))
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
     * W1a: [preferredUrl] — URL, который уже нашёл probe; резолвер пробует его первым,
     * остальные кандидаты не перебираются, пока он не отвергнут.
     *
     * @param documentFound отличает «документа нет на сайте» от «документ есть, но не разобрался»
     */
    private suspend fun fetchAllGroupsForDate(
        calendar: Calendar,
        date: String,
        candidateGroups: List<String>,
        preferredUrl: String? = null,
        documentFound: (Boolean) -> Unit = {},
    ): List<LessonEntity> {
        // SCH-01: единый резолвер — прямые имена (месяц даты и предыдущий, суффиксы
        // коллизий «-1»/«-2»), event-страницы даты (документ в iframe officeapps),
        // затем ссылки портала. Остановка на первом документе, который разобрался.
        var found: List<LessonEntity> = emptyList()
        networkClient.forEachDocumentUrl(calendar, preferredUrl = preferredUrl) { url ->
            val bytes = networkClient.downloadFileBytes(url)
            if (bytes == null || bytes.isEmpty()) return@forEachDocumentUrl false
            // V6a: «документ найден» — только если байты реально doc/docx. HTML-страница
            // (event-страница, soft-404) с кодом 200 документом не считается — иначе она
            // глушила бы фолбэк-водопад и F5-диагностику.
            if (looksLikeDocument(bytes)) {
                // X1: документ ДРУГОЙ даты (водопад/event-iframe отдаёт свежий документ
                // любой дате) НЕ принимается как «найденный для этой даты» — продолжаем
                // водопад. Исчерпан → documentFound останется false → «ещё не опубликовано».
                if (!MpkScheduleParser.documentMatchesDate(bytes, date)) return@forEachDocumentUrl false
                documentFound(true)
            }
            val parsed = MpkScheduleParser.parseFileAllGroups(bytes, date, candidateGroups)
            if (parsed.isNotEmpty()) {
                found = parsed
                true
            } else {
                false
            }
        }
        return found
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
        force: Boolean = false,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val dao = teacherLessonDao ?: return@withContext Result.success(0)
        val date = formatDate(targetCalendar)

        if (!force && dao.getPresentDates(listOf(date)).contains(date)) {
            return@withContext Result.success(
                dao.getTeachersOnDate(date).size,
            )
        }

        val fetched = networkClient.fetchTeacherSchedule(targetCalendar)
        val parsedAll = fetched.getOrElse { return@withContext Result.failure(it) }
        if (parsedAll.isEmpty()) return@withContext Result.success(0)

        // День документов не всегда распознаётся по заголовку; тогда берём день недели
        // запрошенной даты — иначе строки легли бы под dayOfWeek = 0 и вкладка
        // «Расписание» показывала бы их под любой датой (это чинил O7).
        val requestedDow = MpkScheduleParser.dowOfCalendar(targetCalendar)
        val docDow =
            parsedAll.values
                .firstOrNull()
                ?.firstOrNull()
                ?.dayOfWeek ?: 0
        // Документ другого дня в базу не пишем: иначе под запрошенной датой окажется
        // расписание чужого дня
        if (docDow != 0 && docDow != requestedDow) {
            return@withContext Result.failure(IOException("Документ за другой день"))
        }

        val rows =
            parsedAll
                .flatMap { (rawName, slots) ->
                    val surname = TeacherInsights.surnameOf(rawName)
                    if (surname.isBlank()) {
                        emptyList()
                    } else {
                        slots.map { slot ->
                            TeacherLessonEntity(
                                teacherName = rawName.trim(),
                                surnameKey = surname,
                                dateString = date,
                                dayOfWeek = if (slot.dayOfWeek != 0) slot.dayOfWeek else requestedDow,
                                lessonNumber = slot.lessonNumber,
                                groups = slot.groups.joinToString(", "),
                                subject = slot.subject,
                                room = slot.room,
                            )
                        }
                    }
                }.distinctBy { "${it.surnameKey}_${it.lessonNumber}_${it.room}_${it.subject}" }

        if (rows.isNotEmpty()) dao.replaceForDate(date, rows)
        Result.success(rows.size)
    }

    /** Занятия преподавателя на дату из офлайн-кэша (пусто — нет данных этого дня). */
    suspend fun getTeacherLessonsForDate(
        teacherSurname: String,
        dateString: String,
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
        candidateGroups: List<String> = emptyList(),
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (candidateGroups.isEmpty()) return@withContext Result.success(0)
        val calendar = parseCalendarDate(date) ?: return@withContext Result.success(0)

        var documentFound = false
        val fetched =
            try {
                fetchAllGroupsForDate(calendar, date, candidateGroups) { documentFound = it }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

        if (fetched.isEmpty()) {
            return@withContext if (documentFound) {
                Result.success(0)
            } else {
                Result.failure(IOException("Расписание на $date не опубликовано"))
            }
        }

        // Ключ перезаписи — группа + дата (replaceForDate внутри replaceSyncedLessons)
        val forDate =
            fetched
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

    // W1: nextSchoolDay теперь единая internal-функция файла — с субботой по курсу (см. выше)

    // QA#4: Locale.ROOT — дата идёт в БД/сравнения; в ar-локали %02d дал бы арабско-индийские цифры.
    // internal — для LocaleRootDateTest.
    internal fun formatDate(calendar: Calendar): String =
        String.format(
            java.util.Locale.ROOT,
            "%02d.%02d.%04d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR),
        )

    suspend fun replaceSyncedLessons(
        groupName: String,
        lessons: List<LessonEntity>,
    ) = withContext(Dispatchers.IO) {
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

    suspend fun replaceSchedule(
        groupName: String,
        lessons: List<LessonEntity>,
    ) = withContext(Dispatchers.IO) {
        lessonDao.replaceScheduleForGroup(groupName, lessons)
    }

    suspend fun clearSchedule(groupName: String) = withContext(Dispatchers.IO) {
        lessonDao.clearLessonsForGroup(groupName)
    }

    suspend fun hasSchedule(groupName: String): Boolean = withContext(Dispatchers.IO) {
        lessonDao.getLessonCountForGroup(groupName) > 0
    }
}
