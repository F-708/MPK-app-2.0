package com.example.data.network

import com.example.data.local.entity.LessonEntity
import com.example.data.model.SyncDiagnosticInfo
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

        // Обычный клиент с системным доверием к сертификатам: у guo-mpk.by валидный
        // сертификат, trust-all здесь был лишним и небезопасным (MITM).
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

    fun generateCandidateDirectUrls(calendar: Calendar): List<Pair<String, String>> {
        val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
        val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
        val ddMmYyyy = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(calendar.time)
        val dd = SimpleDateFormat("dd", Locale.ROOT).format(calendar.time)

        return listOf(
            Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$ddMmYyyy-raspisanie-uchashhihsya.doc", ddMmYyyy),
            Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$ddMmYyyy-raspisanie-uchashhihsya.docx", ddMmYyyy),
            Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/${dd}_${mm}_$yyyy-raspisanie-uchashhihsya.doc", ddMmYyyy)
        )
    }

    fun generateCandidateEventUrls(calendar: Calendar): List<Pair<String, String>> {
        val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
        val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
        val dd = SimpleDateFormat("dd", Locale.ROOT).format(calendar.time)
        val dateStr = "$dd-$mm-$yyyy"
        val occStr = "$yyyy-$mm-$dd"
        return listOf(
            Pair("https://guo-mpk.by/raspisanie/$dateStr-raspisanie-uchashhihsya/?occurrence=$occStr", "$dd.$mm.$yyyy"),
            Pair("https://guo-mpk.by/$dateStr-raspisanie-uchashhihsya/", "$dd.$mm.$yyyy")
        )
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
        val downloadedUrls = mutableSetOf<String>()
        var lastResponsePreview = ""
        var lastParseFormat = ""
        var lastParseStrategy = ""
        var lastParseRuns = 0
        var groupWasFound = false
        var lastParseError = ""
        var lastTextPreview = ""

        fun rememberResponsePreview(bytes: ByteArray) {
            // Превью ответа: первые символы — видно, документ это или HTML-страница
            lastResponsePreview = String(bytes.copyOfRange(0, minOf(100, bytes.size)), Charsets.UTF_8)
                .replace(Regex("\\s+"), " ")
                .take(100)
        }

        /** Снимает статистику парсинга ПОСЛЕ завершения parseFile (не во время!). */
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
                // 1. Прямые ссылки (.doc и .docx)
                val directCandidates = generateCandidateDirectUrls(targetCalendar)
                for ((url, _) in directCandidates) {
                    if (downloadedUrls.add(url)) {
                        lastCheckedUrl = url
                        val directBytes = downloadFileBytes(url)
                        if (directBytes != null && directBytes.isNotEmpty()) {
                            totalBytesReceived += directBytes.size
                            lastHttpCode = 200
                            rememberResponsePreview(directBytes)
                            val parsed = MpkScheduleParser.parseFile(directBytes, targetGroup, dateDotStr)
                            rememberParseStats()
                            if (parsed.isNotEmpty()) {
                                allLessons.addAll(parsed)
                                break
                            }
                        }
                    }
                }

                // 2. Резерв через портал /raspisanie/
                if (allLessons.isEmpty()) {
                    lastCheckedUrl = SCHEDULE_PORTAL_URL
                    val portalResponse = executeRequest(SCHEDULE_PORTAL_URL)
                    lastHttpCode = portalResponse.code

                    if (portalResponse.isSuccessful) {
                        val portalHtml = portalResponse.body?.string() ?: ""
                        totalBytesReceived += portalHtml.toByteArray().size

                        // Извлекаем ссылки на документы (.doc/.docx) со страницы портала,
                        // включая iframe view.officeapps.live.com с прямой ссылкой на файл
                        val candidateDocLinks = MpkScheduleParser.extractDocumentUrls(portalHtml, SCHEDULE_PORTAL_URL)
                        for (docLink in candidateDocLinks) {
                            if (downloadedUrls.add(docLink)) {
                                lastCheckedUrl = docLink
                                val docBytes = downloadFileBytes(docLink)
                                if (docBytes != null && docBytes.isNotEmpty()) {
                                    totalBytesReceived += docBytes.size
                                    rememberResponsePreview(docBytes)
                                    val parsed = MpkScheduleParser.parseFile(docBytes, targetGroup, dateDotStr)
                                    rememberParseStats()
                                    if (parsed.isNotEmpty()) {
                                        allLessons.addAll(parsed)
                                    }
                                }
                            }
                        }
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
                        // Документ скачан, но группа в нём не найдена — показываем причину,
                        // включая ошибки разбора, если стратегии падали
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

        // Если пятница вечер (после 15:00), суббота или воскресенье — запрашиваем понедельник
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

    /**
     * Скачивает и разбирает расписание преподавателей на указанную дату.
     * Документ: /wp-content/uploads/YYYY/MM/DD.MM.YYYY-raspisanie-prepodavatelej.doc
     */
    suspend fun fetchTeacherSchedule(
        targetCalendar: Calendar = Calendar.getInstance()
    ): Result<Map<String, List<TeacherSlot>>> = withContext(Dispatchers.IO) {
        val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(targetCalendar.time)
        val mm = SimpleDateFormat("MM", Locale.ROOT).format(targetCalendar.time)
        val ddMmYyyy = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(targetCalendar.time)
        val url = "https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$ddMmYyyy-raspisanie-prepodavatelej.doc"

        try {
            val bytes = downloadFileBytes(url)
            if (bytes == null || bytes.isEmpty()) {
                return@withContext Result.failure(IOException("Документ не найден: $url"))
            }
            val parsed = TeacherScheduleParser.parse(bytes)
            if (parsed.isEmpty()) {
                Result.failure(IOException("Не удалось разобрать расписание преподавателей"))
            } else {
                Result.success(parsed)
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
