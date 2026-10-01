package com.politex.data.network

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.SyncDiagnosticInfo
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MpkNetworkClient {

    companion object {
        const val SCHEDULE_PORTAL_URL = "https://guo-mpk.by/raspisanie/"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val BROWSER_USER_AGENT = USER_AGENT
        const val BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,*/*;q=0.8"

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8000, TimeUnit.MILLISECONDS)
                .readTimeout(8000, TimeUnit.MILLISECONDS)
                .writeTimeout(8000, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun buildDirectScheduleUrl(calendar: Calendar = Calendar.getInstance()): String {
            val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
            val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
            val ddMmYyyy = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(calendar.time)
            return "https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$ddMmYyyy-raspisanie-uchashhihsya.doc"
        }
    }

    private val _diagnosticInfo = MutableStateFlow(SyncDiagnosticInfo())
    val diagnosticInfo: StateFlow<SyncDiagnosticInfo> = _diagnosticInfo.asStateFlow()

    /**
     * SCH-01: сайт публикует документ в папке месяца ЗАГРУЗКИ (документ на 01.10
     * доказанно лежал в /2026/09/ с суффиксом коллизии «-1»), поэтому перебираются
     * месяц целевой даты И предыдущий, суффиксы "", "-1", "-2" и имена dd.MM.yyyy /
     * dd_MM_yyyy. Первые два кандидата — быстрый путь (точный месяц, без суффикса).
     *
     * @param nameBase «raspisanie-uchashhihsya» (учащиеся) или «raspisanie-prepodavatelej»
     */
    fun generateCandidateDirectUrls(
        calendar: Calendar,
        nameBase: String = "raspisanie-uchashhihsya"
    ): List<Pair<String, String>> {
        val ddMmYyyy = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(calendar.time)
        val dd = SimpleDateFormat("dd", Locale.ROOT).format(calendar.time)
        val prevMonth = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }

        val folders = listOf(
            SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time) to
                SimpleDateFormat("MM", Locale.ROOT).format(calendar.time),
            SimpleDateFormat("yyyy", Locale.ROOT).format(prevMonth.time) to
                SimpleDateFormat("MM", Locale.ROOT).format(prevMonth.time)
        )

        val result = mutableListOf<Pair<String, String>>()
        fun add(yyyy: String, mm: String, name: String, suffix: String, ext: String) {
            val url = "https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$name-$nameBase$suffix.$ext"
            if (result.none { it.first == url }) result.add(Pair(url, ddMmYyyy))
        }

        // Быстрый путь: точный месяц, точечное имя, без суффикса
        val (ty, tm) = folders[0]
        add(ty, tm, ddMmYyyy, "", "doc")
        add(ty, tm, ddMmYyyy, "", "docx")

        for ((yyyy, mm) in folders) {
            for (name in listOf(ddMmYyyy, "${dd}_${mm}_$yyyy")) {
                for (suffix in listOf("", "-1", "-2")) {
                    for (ext in listOf("doc", "docx")) {
                        add(yyyy, mm, name, suffix, ext)
                    }
                }
            }
        }
        return result
    }

    /**
     * Event-страницы даты (внутри них документ в iframe officeapps). У преподавателей
     * страница может иметь суффикс коллизии «-2».
     */
    fun generateCandidateEventUrls(
        calendar: Calendar,
        nameBase: String = "raspisanie-uchashhihsya"
    ): List<Pair<String, String>> {
        val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
        val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
        val dd = SimpleDateFormat("dd", Locale.ROOT).format(calendar.time)
        val dateStr = "$dd-$mm-$yyyy"
        val occStr = "$yyyy-$mm-$dd"
        val slugs = if (nameBase.endsWith("prepodavatelej")) listOf("", "-2") else listOf("")
        return slugs.flatMap { slugSuffix ->
            listOf(
                Pair("https://guo-mpk.by/raspisanie/$dateStr-$nameBase$slugSuffix/?occurrence=$occStr", "$dd.$mm.$yyyy"),
                Pair("https://guo-mpk.by/$dateStr-$nameBase$slugSuffix/", "$dd.$mm.$yyyy")
            )
        }
    }

    /** Ссылки event-страниц конкретной даты из HTML портала. */
    fun extractEventPageUrls(html: String, dateStr: String): List<String> =
        Regex("(?i)https?://guo-mpk\\.by/(?:raspisanie/)?" + Regex.escape(dateStr) +
            "-raspisanie-(?:uchashhihsya|prepodavatelej)[a-z0-9-]*/?")
            .findAll(html)
            .map { it.value }
            .distinct()
            .toList()

    /**
     * SCH-01, общий резолвер документов расписания. Порядок:
     * (а) прямые имена (месяц даты и предыдущий, суффиксы коллизий);
     * (б) event-страницы даты — документ внутри в iframe officeapps;
     * (в) портал — event-страницы нужной даты из него, затем прямые .doc-ссылки портала.
     *
     * [visit] получает каждый URL документа по приоритету; true от него — «документ
     * принят», дальше не идём (остальные фазы и их запросы не выполняются).
     */
    suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String = "raspisanie-uchashhihsya",
        visit: suspend (String) -> Boolean
    ) = withContext(Dispatchers.IO) {
        val tried = LinkedHashSet<String>()

        suspend fun phaseUrls(urls: List<String>): Boolean {
            for (url in urls) {
                if (!tried.add(url)) continue
                if (visit(url)) return true
            }
            return false
        }

        suspend fun phaseEventPages(pages: List<String>): Boolean {
            for (page in pages) {
                if (!tried.add(page)) continue
                val html = downloadFileBytes(page) ?: continue
                if (phaseUrls(MpkScheduleParser.extractDocumentUrls(String(html, Charsets.UTF_8), page))) return true
            }
            return false
        }

        // (а) быстрый путь: предсказуемые имена
        if (phaseUrls(generateCandidateDirectUrls(calendar, nameBase).map { it.first })) return@withContext

        // Портал нужен и для event-страниц нужной даты, и для прямых .doc-ссылок
        val dd = SimpleDateFormat("dd", Locale.ROOT).format(calendar.time)
        val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
        val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
        val portalDocs = mutableListOf<String>()
        val portalEventPages = mutableListOf<String>()
        downloadFileBytes(SCHEDULE_PORTAL_URL)?.let { bytes ->
            val html = String(bytes, Charsets.UTF_8)
            portalDocs += MpkScheduleParser.extractDocumentUrls(html, SCHEDULE_PORTAL_URL)
            portalEventPages += extractEventPageUrls(html, "$dd-$mm-$yyyy")
        }

        // (б) event-страницы даты — сгенерированные и найденные на портале
        if (phaseEventPages(
                generateCandidateEventUrls(calendar, nameBase).map { it.first } + portalEventPages
            )
        ) return@withContext

        // (в) прямые .doc-ссылки портала (нестандартные имена документов)
        phaseUrls(portalDocs)
    }

    suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar = calculateTargetCalendar(),
        maxRetries: Int = 2
    ): Result<List<LessonEntity>> = withContext(Dispatchers.IO) {
        val timeFormat = SimpleDateFormat("HH:mm:ss (dd.MM.yyyy)", Locale.ROOT)
        val nowStr = timeFormat.format(Date())
        val dateDotStr = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(targetCalendar.time)

        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            isSyncing = true,
            statusMessage = "Запрос расписания на $dateDotStr..."
        )

        var lastCheckedUrl = buildDirectScheduleUrl(targetCalendar)
        var lastHttpCode = 0
        var totalBytesReceived = 0L
        var lastException: Throwable? = null

        val allLessons = mutableListOf<LessonEntity>()
        var lastResponsePreview = ""
        var lastParseFormat = ""
        var lastParseStrategy = ""
        var lastParseRuns = 0
        var groupWasFound = false
        var lastParseError = ""
        var lastTextPreview = ""

        fun rememberResponsePreview(bytes: ByteArray) {

            lastResponsePreview = String(bytes.copyOfRange(0, minOf(100, bytes.size)), Charsets.UTF_8)
                .replace(Regex("\\s+"), " ")
                .take(100)
        }

        fun rememberParseStats() {
            lastParseFormat = MpkScheduleParser.lastParseStats.format
            lastParseStrategy = MpkScheduleParser.lastParseStats.strategy
            lastParseRuns = MpkScheduleParser.lastParseStats.runsExtracted
            groupWasFound = groupWasFound || MpkScheduleParser.lastParseStats.groupFound
            lastParseError = MpkScheduleParser.lastParseStats.parseError
            lastTextPreview = MpkScheduleParser.lastParseStats.textPreview
        }

        for (attempt in 1..maxRetries) {
            try {
                // SCH-01: единый резолвер — прямые имена (текущий+предыдущий месяц,
                // суффиксы коллизий), затем event-страницы даты (документ в iframe
                // officeapps), затем прямые ссылки портала. Остановка — на первом
                // документе, где группа нашлась.
                forEachDocumentUrl(targetCalendar) { url ->
                    lastCheckedUrl = url
                    val docBytes = downloadFileBytes(url)
                    if (docBytes == null || docBytes.isEmpty()) {
                        false
                    } else {
                        totalBytesReceived += docBytes.size
                        lastHttpCode = 200
                        rememberResponsePreview(docBytes)
                        val parsed = MpkScheduleParser.parseFile(docBytes, targetGroup, dateDotStr)
                        rememberParseStats()
                        if (parsed.isNotEmpty()) {
                            allLessons += parsed
                            true
                        } else false
                    }
                }

                val distinctLessons = allLessons.distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
                val isSuccess = lastHttpCode in 200..299 || distinctLessons.isNotEmpty()

                _diagnosticInfo.value = SyncDiagnosticInfo(
                    lastSyncTime = nowStr,
                    checkedUrl = lastCheckedUrl,
                    httpStatusCode = if (lastHttpCode == 0 && isSuccess) 200 else lastHttpCode,
                    receivedBytes = totalBytesReceived,
                    lessonsFound = distinctLessons.size,
                    statusMessage = when {
                        distinctLessons.isNotEmpty() -> "Успешно: загружено ${distinctLessons.size} уроков" +
                            (if (lastParseError.isNotBlank()) " (с ошибкой разбора: $lastParseError)" else "")

                        totalBytesReceived > 0L && lastParseError.isNotBlank() ->
                            "Документ получен (${totalBytesReceived / 1024} КБ), ошибка разбора: $lastParseError"
                        totalBytesReceived > 0L -> "Документ получен (${totalBytesReceived / 1024} КБ), но группа $targetGroup в нём не найдена"
                        else -> "Расписание группы $targetGroup пока не опубликовано"
                    },
                    isSuccess = isSuccess,
                    isSyncing = false,
                    responsePreview = lastResponsePreview,
                    parseFormat = lastParseFormat,
                    parseStrategy = lastParseStrategy,
                    parseRuns = lastParseRuns,
                    groupFound = groupWasFound,
                    parseError = lastParseError,
                    textPreview = lastTextPreview
                )

                return@withContext Result.success(distinctLessons)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) delay(300)
            }
        }

        val errMsg = lastException?.message ?: "Не удалось загрузить расписание"
        _diagnosticInfo.value = SyncDiagnosticInfo(
            lastSyncTime = nowStr,
            checkedUrl = lastCheckedUrl,
            httpStatusCode = lastHttpCode,
            receivedBytes = totalBytesReceived,
            lessonsFound = 0,
            statusMessage = "Ошибка: ${lastException?.javaClass?.simpleName ?: ""} $errMsg".trim(),
            isSuccess = false,
            isSyncing = false,
            responsePreview = lastResponsePreview,
            parseFormat = lastParseFormat,
            parseStrategy = lastParseStrategy,
            parseRuns = lastParseRuns,
            groupFound = groupWasFound,
            parseError = lastParseError,
            textPreview = lastTextPreview
        )

        Result.failure(lastException ?: IOException(errMsg))
    }

    private fun calculateTargetCalendar(): Calendar {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        if ((dayOfWeek == Calendar.FRIDAY && hour >= 15) || dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            val daysUntilMon = if (dayOfWeek == Calendar.SUNDAY) 1 else (Calendar.SATURDAY - dayOfWeek + 2) % 7
            cal.add(Calendar.DAY_OF_YEAR, daysUntilMon)
        }
        return cal
    }

    suspend fun downloadFileBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest(url)
            if (response.isSuccessful) {
                return@withContext response.body?.bytes()
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun fetchTeacherSchedule(
        targetCalendar: Calendar = Calendar.getInstance()
    ): Result<Map<String, List<TeacherSlot>>> = withContext(Dispatchers.IO) {
        val ddMmYyyy = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(targetCalendar.time)
        var lastUrl = ""
        var lastMismatch: String? = null

        try {
            var accepted: Map<String, List<TeacherSlot>>? = null
            // SCH-01: тот же резолвер, что у учащихся — прямые имена (в т.ч. прошлый
            // месяц и суффиксы), event-страницы преподавателей (могут иметь «-2»), портал
            forEachDocumentUrl(targetCalendar, "raspisanie-prepodavatelej") { url ->
                lastUrl = url
                val bytes = downloadFileBytes(url)
                if (bytes == null || bytes.isEmpty()) return@forEachDocumentUrl false

                // O7: показываем только документ, реально относящийся к запрошенной дате.
                // Если файл подменён/битый (дата в заголовке не совпала с именем файла),
                // это ошибка, а не повод показать чужой день.
                val text = TeacherScheduleParser.extractText(bytes)
                if (text.isNotBlank() && !TeacherScheduleParser.matchesRequestedDate(text, targetCalendar)) {
                    val inDoc = TeacherScheduleParser.dateFromHeaderText(
                        TeacherScheduleParser.findHeaderLine(text).orEmpty()
                    )
                    lastMismatch = if (inDoc == null) "Дата не найдена в документе: $url"
                    else "Документ за $inDoc вместо $ddMmYyyy: $url"
                    return@forEachDocumentUrl false
                }

                val parsed = TeacherScheduleParser.parse(bytes)
                if (parsed.isEmpty()) {
                    lastMismatch = "Не удалось разобрать расписание преподавателей"
                    false
                } else {
                    accepted = parsed
                    true
                }
            }

            when {
                accepted != null -> Result.success(accepted!!)
                lastMismatch != null -> Result.failure(IOException(lastMismatch!!))
                else -> Result.failure(IOException("Документ не найден: ${lastUrl.ifBlank { ddMmYyyy }}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeRequest(url: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", BROWSER_ACCEPT)
            .build()
        return client.newCall(request).execute()
    }
}
