package com.example.data.network

import com.example.data.local.entity.LessonEntity
import com.example.data.model.SyncDiagnosticInfo
import java.io.IOException
import java.net.URLDecoder
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

/**
 * Сетевой клиент для загрузки и синхронизации расписания с официального сайта guo-mpk.by.
 *
 * ТЕХНИЧЕСКИЕ ТРЕБОВАНИЯ:
 * 1. Браузерные заголовки (User-Agent Chrome Windows, Accept) для исключения 403 Forbidden.
 * 2. Прямая и динамическая адресация (прямой WordPress uploads -> страница события -> портал).
 * 3. Отказоустойчивость: таймаут 3.5с, 2 повтора, детальная диагностика сети.
 */
class MpkNetworkClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .writeTimeout(3500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        const val SCHEDULE_PORTAL_URL = "https://guo-mpk.by/raspisanie/"
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    }

    private val _diagnosticInfo = MutableStateFlow(SyncDiagnosticInfo())
    val diagnosticInfo: StateFlow<SyncDiagnosticInfo> = _diagnosticInfo.asStateFlow()

    /**
     * Основной метод загрузки расписания для указанной группы.
     */
    suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar = Calendar.getInstance(),
        maxRetries: Int = 2
    ): Result<List<LessonEntity>> = withContext(Dispatchers.IO) {
        val timeFormat = SimpleDateFormat("HH:mm:ss (dd.MM.yyyy)", Locale.ROOT)
        val nowStr = timeFormat.format(Date())

        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            isSyncing = true,
            statusMessage = "Синхронизация..."
        )

        var lastCheckedUrl = SCHEDULE_PORTAL_URL
        var lastHttpCode = 0
        var totalBytesReceived = 0L
        var lastException: Throwable? = null

        val allLessons = mutableListOf<LessonEntity>()

        for (attempt in 1..maxRetries) {
            try {
                // ШАГ 1: Пробуем прямые предсказуемые URL WordPress для дат текущей недели
                val candidateUrls = generateCandidateDirectUrls(targetCalendar)

                for ((directUrl, dateStr) in candidateUrls) {
                    lastCheckedUrl = directUrl
                    try {
                        val response = executeRequest(directUrl)
                        lastHttpCode = response.code
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes() ?: ByteArray(0)
                            totalBytesReceived += bytes.size
                            if (bytes.isNotEmpty()) {
                                val lessons = MpkScheduleParser.parseFile(
                                    bytes = bytes,
                                    targetGroup = targetGroup,
                                    targetDate = dateStr
                                )
                                if (lessons.isNotEmpty()) {
                                    allLessons.addAll(lessons)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Продолжаем проверку других адресов
                    }
                    if (allLessons.isNotEmpty()) break
                }

                // ШАГ 2: Если прямой URL не дал уроков, сканируем страницы событий и iframe officeapps
                if (allLessons.isEmpty()) {
                    val eventPageUrls = generateCandidateEventUrls(targetCalendar)
                    for ((eventUrl, dateStr) in eventPageUrls) {
                        lastCheckedUrl = eventUrl
                        try {
                            val response = executeRequest(eventUrl)
                            lastHttpCode = response.code
                            if (response.isSuccessful) {
                                val eventHtml = response.body?.string() ?: ""
                                totalBytesReceived += eventHtml.toByteArray().size

                                // Извлекаем ссылки на документы и iframe
                                val docUrls = MpkScheduleParser.extractDocumentUrls(eventHtml, eventUrl)
                                for (docUrl in docUrls) {
                                    try {
                                        val docResp = executeRequest(docUrl)
                                        if (docResp.isSuccessful) {
                                            val bytes = docResp.body?.bytes() ?: ByteArray(0)
                                            totalBytesReceived += bytes.size
                                            val lessons = MpkScheduleParser.parseFile(bytes, targetGroup, dateStr)
                                            if (lessons.isNotEmpty()) {
                                                allLessons.addAll(lessons)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }

                                if (allLessons.isEmpty() && eventHtml.isNotBlank()) {
                                    val plainLessons = MpkScheduleParser.parsePlainTextSchedule(eventHtml, targetGroup, dateStr)
                                    if (plainLessons.isNotEmpty()) {
                                        allLessons.addAll(plainLessons)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        if (allLessons.isNotEmpty()) break
                    }
                }

                // ШАГ 3: Если расписание все еще пусто, сканируем главную страницу портала
                if (allLessons.isEmpty()) {
                    lastCheckedUrl = SCHEDULE_PORTAL_URL
                    val portalResp = executeRequest(SCHEDULE_PORTAL_URL)
                    lastHttpCode = portalResp.code
                    if (portalResp.isSuccessful) {
                        val portalHtml = portalResp.body?.string() ?: ""
                        totalBytesReceived += portalHtml.toByteArray().size

                        val postLinks = MpkScheduleParser.findSchedulePostLinks(portalHtml, SCHEDULE_PORTAL_URL)
                        for (post in postLinks.take(3)) {
                            try {
                                val postResp = executeRequest(post.url)
                                if (postResp.isSuccessful) {
                                    val postHtml = postResp.body?.string() ?: ""
                                    totalBytesReceived += postHtml.toByteArray().size

                                    val docUrls = MpkScheduleParser.extractDocumentUrls(postHtml, post.url)
                                    for (docUrl in docUrls.take(2)) {
                                        try {
                                            val docResp = executeRequest(docUrl)
                                            if (docResp.isSuccessful) {
                                                val bytes = docResp.body?.bytes() ?: ByteArray(0)
                                                totalBytesReceived += bytes.size
                                                val lessons = MpkScheduleParser.parseFile(bytes, targetGroup, post.dateString)
                                                if (lessons.isNotEmpty()) {
                                                    allLessons.addAll(lessons)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            } catch (_: Exception) {}
                            if (allLessons.isNotEmpty()) break
                        }
                    }
                }

                val isSuccess = lastHttpCode in 200..299 || allLessons.isNotEmpty()
                _diagnosticInfo.value = SyncDiagnosticInfo(
                    lastSyncTime = nowStr,
                    checkedUrl = lastCheckedUrl,
                    httpStatusCode = if (lastHttpCode == 0 && isSuccess) 200 else lastHttpCode,
                    receivedBytes = totalBytesReceived,
                    lessonsFound = allLessons.size,
                    statusMessage = if (allLessons.isNotEmpty()) "Успешно: найдено ${allLessons.size} пар"
                    else if (isSuccess) "Сайт доступен, но расписание группы $targetGroup пока не опубликовано"
                    else "Ошибка соединения (HTTP $lastHttpCode)",
                    isSuccess = isSuccess,
                    isSyncing = false
                )

                return@withContext Result.success(allLessons)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(400)
                }
            }
        }

        val errMsg = lastException?.message ?: "Не удалось загрузить расписание"
        _diagnosticInfo.value = SyncDiagnosticInfo(
            lastSyncTime = nowStr,
            checkedUrl = lastCheckedUrl,
            httpStatusCode = lastHttpCode,
            receivedBytes = totalBytesReceived,
            lessonsFound = 0,
            statusMessage = "Ошибка: $errMsg",
            isSuccess = false,
            isSyncing = false
        )

        Result.failure(lastException ?: IOException(errMsg))
    }

    /**
     * Выполняет запрос с обязательными заголовками браузера.
     */
    private fun executeRequest(url: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", BROWSER_ACCEPT)
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Connection", "keep-alive")
            .build()

        return client.newCall(request).execute()
    }

    /**
     * Формирует список прямых URL на документы WordPress для дней текущей недели.
     */
    fun generateCandidateDirectUrls(calendar: Calendar = Calendar.getInstance()): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val sdfYear = SimpleDateFormat("yyyy", Locale.ROOT)
        val sdfMonth = SimpleDateFormat("MM", Locale.ROOT)
        val sdfDay = SimpleDateFormat("dd", Locale.ROOT)
        val sdfDot = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)

        val cal = (calendar.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
        }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (currentDay == Calendar.SUNDAY) 6 else currentDay - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)

        for (i in 0..5) {
            val dCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val yyyy = sdfYear.format(dCal.time)
            val mm = sdfMonth.format(dCal.time)
            val dd = sdfDay.format(dCal.time)
            val dot = sdfDot.format(dCal.time)

            // Варианты именования файлов на сайте колледжа
            list.add(Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$dot-raspisanie-uchashhihsya.doc", dot))
            list.add(Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/${dd}_${mm}_$yyyy-raspisanie-uchashhihsya.doc", dot))
            list.add(Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$dot-raspisanie-uchashhihsya.docx", dot))
        }
        return list
    }

    /**
     * Формирует список URL страниц событий для дней текущей недели.
     */
    fun generateCandidateEventUrls(calendar: Calendar = Calendar.getInstance()): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val sdfDot = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        val sdfDash = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT)
        val sdfOccur = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

        val cal = (calendar.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
        }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (currentDay == Calendar.SUNDAY) 6 else currentDay - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)

        for (i in 0..5) {
            val dCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val dot = sdfDot.format(dCal.time)
            val dash = sdfDash.format(dCal.time)
            val occur = sdfOccur.format(dCal.time)

            list.add(Pair("https://guo-mpk.by/raspisanie/$dash-raspisanie-uchashhihsya/?occurrence=$occur", dot))
            list.add(Pair("https://guo-mpk.by/$dash-raspisanie-uchashhihsya/", dot))
        }
        return list
    }
}
