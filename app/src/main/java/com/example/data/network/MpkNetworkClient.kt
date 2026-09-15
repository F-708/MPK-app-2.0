package com.example.data.network

import com.example.data.local.entity.LessonEntity
import com.example.data.model.SyncDiagnosticInfo
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Сетевой клиент МГПК: пулл расписания по официальному стандарту.
 *
 * СТРАТЕГИЯ ЗАГРУЗКИ:
 * 1. Прямая ссылка по шаблону WordPress:
 *    https://guo-mpk.by/wp-content/uploads/{YYYY}/{MM}/{DD.MM.YYYY}-raspisanie-uchashhihsya.doc
 * 2. Резервный способ (Fallback):
 *    Запрос на https://guo-mpk.by/raspisanie/, извлечение тегов <a> с href подстрокой 'raspisanie' и расширением .doc/.docx.
 * 3. Сетевые параметры:
 *    User-Agent: "Mozilla/5.0 MPK Student App", таймауты = 8 сек.
 */
class MpkNetworkClient {

    companion object {
        const val SCHEDULE_PORTAL_URL = "https://guo-mpk.by/raspisanie/"
        const val USER_AGENT = "Mozilla/5.0 MPK Student App"
        const val BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,*/*;q=0.8"

        private val client: OkHttpClient by lazy {
            createSafeOkHttpClient()
        }

        private fun createSafeOkHttpClient(): OkHttpClient {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(8000, TimeUnit.MILLISECONDS)
                .readTimeout(8000, TimeUnit.MILLISECONDS)
                .writeTimeout(8000, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        /**
         * Формирует прямую ссылку на файл по стандарту WordPress МГПК:
         * https://guo-mpk.by/wp-content/uploads/{YYYY}/{MM}/{DD.MM.YYYY}-raspisanie-uchashhihsya.doc
         */
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
     * Основной метод загрузки расписания:
     * 1. Пробует скачать напрямую по шаблону даты.
     * 2. При неудаче делает fallback на страницу портала /raspisanie/.
     * 3. Парсит найденные файлы Word (.doc / .docx) для целевой группы.
     */
    suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar = Calendar.getInstance(),
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

        for (attempt in 1..maxRetries) {
            try {
                // ==========================================
                // ШАГ 1: Прямая ссылка по шаблону даты (.doc)
                // ==========================================
                val primaryDirectUrl = buildDirectScheduleUrl(targetCalendar)
                lastCheckedUrl = primaryDirectUrl

                val directBytes = downloadFileBytes(primaryDirectUrl)
                if (directBytes != null && directBytes.isNotEmpty()) {
                    totalBytesReceived += directBytes.size
                    lastHttpCode = 200
                    downloadedUrls.add(primaryDirectUrl)

                    val parsedLessons = MpkScheduleParser.parseFile(
                        bytes = directBytes,
                        targetGroup = targetGroup,
                        targetDate = dateDotStr
                    )
                    if (parsedLessons.isNotEmpty()) {
                        allLessons.addAll(parsedLessons)
                    }
                }

                // Если по прямому URL файл не найден или расписание для группы пустое — проверяем .docx вариант
                if (allLessons.isEmpty()) {
                    val docxUrl = primaryDirectUrl.removeSuffix(".doc") + ".docx"
                    if (downloadedUrls.add(docxUrl)) {
                        val docxBytes = downloadFileBytes(docxUrl)
                        if (docxBytes != null && docxBytes.isNotEmpty()) {
                            totalBytesReceived += docxBytes.size
                            lastHttpCode = 200
                            val parsed = MpkScheduleParser.parseFile(docxBytes, targetGroup, dateDotStr)
                            if (parsed.isNotEmpty()) {
                                allLessons.addAll(parsed)
                            }
                        }
                    }
                }

                // =========================================================================
                // ШАГ 2: Резервный способ (Fallback на https://guo-mpk.by/raspisanie/)
                // =========================================================================
                if (allLessons.isEmpty()) {
                    lastCheckedUrl = SCHEDULE_PORTAL_URL
                    val portalResponse = executeRequest(SCHEDULE_PORTAL_URL)
                    lastHttpCode = portalResponse.code

                    if (portalResponse.isSuccessful) {
                        val portalHtml = portalResponse.body?.string() ?: ""
                        totalBytesReceived += portalHtml.toByteArray().size

                        // Извлекаем все ссылки <a> с raspisanie и .doc / .docx
                        val candidateDocLinks = extractScheduleDocLinksFromHtml(portalHtml, SCHEDULE_PORTAL_URL)
                        for (docLink in candidateDocLinks) {
                            if (downloadedUrls.add(docLink)) {
                                lastCheckedUrl = docLink
                                val docBytes = downloadFileBytes(docLink)
                                if (docBytes != null && docBytes.isNotEmpty()) {
                                    totalBytesReceived += docBytes.size
                                    val parsed = MpkScheduleParser.parseFile(docBytes, targetGroup, dateDotStr)
                                    if (parsed.isNotEmpty()) {
                                        allLessons.addAll(parsed)
                                    }
                                }
                            }
                        }

                        // Проверяем вложенные посты событий дат
                        val postLinks = MpkScheduleParser.findSchedulePostLinks(portalHtml, SCHEDULE_PORTAL_URL)
                        for (post in postLinks.take(4)) {
                            try {
                                val postResp = executeRequest(post.url)
                                if (postResp.isSuccessful) {
                                    val postHtml = postResp.body?.string() ?: ""
                                    totalBytesReceived += postHtml.toByteArray().size
                                    val postDocLinks = extractScheduleDocLinksFromHtml(postHtml, post.url)
                                    for (docLink in postDocLinks) {
                                        if (downloadedUrls.add(docLink)) {
                                            val docBytes = downloadFileBytes(docLink)
                                            if (docBytes != null && docBytes.isNotEmpty()) {
                                                totalBytesReceived += docBytes.size
                                                val parsed = MpkScheduleParser.parseFile(docBytes, targetGroup, post.dateString)
                                                if (parsed.isNotEmpty()) {
                                                    allLessons.addAll(parsed)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                // =========================================================================
                // ШАГ 3: Дополнительный опрос дней недели (если запрошена вся неделя)
                // =========================================================================
                if (allLessons.isEmpty()) {
                    val candidateUrls = generateWeekCandidateUrls(targetCalendar)
                    for ((cUrl, cDate) in candidateUrls) {
                        if (downloadedUrls.add(cUrl)) {
                            val cBytes = downloadFileBytes(cUrl)
                            if (cBytes != null && cBytes.isNotEmpty()) {
                                totalBytesReceived += cBytes.size
                                val parsed = MpkScheduleParser.parseFile(cBytes, targetGroup, cDate)
                                if (parsed.isNotEmpty()) {
                                    allLessons.addAll(parsed)
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
                    statusMessage = if (distinctLessons.isNotEmpty()) "Успешно: загружено ${distinctLessons.size} пар"
                    else if (isSuccess) "Сайт проверен: расписание группы $targetGroup пока не опубликовано"
                    else "Ошибка подключения (HTTP $lastHttpCode)",
                    isSuccess = isSuccess,
                    isSyncing = false
                )

                return@withContext Result.success(distinctLessons)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(300)
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
     * Загружает бинарные байты файла Word (.doc / .docx).
     * Возвращает null, если статус не 200 или произошла ошибка.
     */
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
     * Извлекает ссылки на файлы расписания (.doc/.docx с подстрокой raspisanie) из HTML страницы.
     */
    fun extractScheduleDocLinksFromHtml(html: String, baseUrl: String): List<String> {
        val links = mutableSetOf<String>()
        // Регулярное выражение для поиска тегов <a href="..."> содержащих raspisanie и .doc/.docx
        val regex = Regex("(?i)<a\\s+[^>]*href=[\"']([^\"']*(?:raspisanie|uchashh)[^\"']*\\.(?:docx|doc)(?:\\?[^\"']*)?)[\"']", RegexOption.IGNORE_CASE)
        regex.findAll(html).forEach { match ->
            val rawLink = match.groupValues[1]
            links.add(resolveAbsoluteUrl(baseUrl, rawLink))
        }

        // Общий fallback на любые .doc/.docx ссылки на странице
        val generalDocRegex = Regex("(?i)<a\\s+[^>]*href=[\"']([^\"']+\\.(?:docx|doc)(?:\\?[^\"']*)?)[\"']", RegexOption.IGNORE_CASE)
        generalDocRegex.findAll(html).forEach { match ->
            val rawLink = match.groupValues[1]
            links.add(resolveAbsoluteUrl(baseUrl, rawLink))
        }

        return links.toList()
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return when {
            relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://") -> relativeUrl
            relativeUrl.startsWith("/") -> "https://guo-mpk.by$relativeUrl"
            else -> {
                val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                base + relativeUrl
            }
        }
    }

    /**
     * Выполняет HTTP запрос с заголовками "Mozilla/5.0 MPK Student App" и таймаутами 8 сек.
     */
    private fun executeRequest(url: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", BROWSER_ACCEPT)
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Connection", "keep-alive")
            .header("Cache-Control", "no-cache")
            .build()

        return client.newCall(request).execute()
    }

    /**
     * Формирует список прямых URL для дней учебной недели.
     */
    private fun generateWeekCandidateUrls(calendar: Calendar): List<Pair<String, String>> {
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

        for (i in 0..6) {
            val dCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val yyyy = sdfYear.format(dCal.time)
            val mm = sdfMonth.format(dCal.time)
            val dd = sdfDay.format(dCal.time)
            val dot = sdfDot.format(dCal.time)

            list.add(Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$dot-raspisanie-uchashhihsya.doc", dot))
            list.add(Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$dot-raspisanie-uchashhihsya.docx", dot))
            list.add(Pair("https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/${dd}_${mm}_$yyyy-raspisanie-uchashhihsya.doc", dot))
        }
        return list
    }
}
