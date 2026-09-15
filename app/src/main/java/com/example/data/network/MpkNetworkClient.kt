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

class MpkNetworkClient {

    companion object {
        const val SCHEDULE_PORTAL_URL = "https://guo-mpk.by/raspisanie/"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val BROWSER_USER_AGENT = USER_AGENT
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
                            val parsed = MpkScheduleParser.parseFile(directBytes, targetGroup, dateDotStr)
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
                    else "Расписание группы $targetGroup пока не опубликовано",
                    isSuccess = isSuccess,
                    isSyncing = false
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
            statusMessage = "Ошибка: $errMsg",
            isSuccess = false,
            isSyncing = false
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

    fun extractScheduleDocLinksFromHtml(html: String, baseUrl: String): List<String> {
        val links = mutableSetOf<String>()
        val regex = Regex("(?i)<a\\s+[^>]*href=[\"']([^\"']*(?:raspisanie|uchashh)[^\"']*\\.(?:docx|doc)(?:\\?[^\"']*)?)[\"']", RegexOption.IGNORE_CASE)
        regex.findAll(html).forEach { match ->
            links.add(resolveAbsoluteUrl(baseUrl, match.groupValues[1]))
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

    private fun executeRequest(url: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", BROWSER_ACCEPT)
            .build()
        return client.newCall(request).execute()
    }
}
