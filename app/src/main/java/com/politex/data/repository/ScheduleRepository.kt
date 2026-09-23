package com.politex.data.repository

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
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
    private val networkClient: MpkNetworkClient = MpkNetworkClient()
) {

    val diagnosticInfo = networkClient.diagnosticInfo

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
        if (needTomorrow) {
            fetched += networkClient
                .fetchScheduleForGroup(groupName, tomorrow)
                .getOrDefault(emptyList())
        }

        return@withContext if (needToday) {
            networkClient.fetchScheduleForGroup(groupName).mapCatching { todayLessons ->
                val distinct = (fetched + todayLessons)
                    .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
                if (distinct.isNotEmpty()) {
                    replaceSyncedLessons(groupName, distinct)
                }
                pruneOldLessons()
                distinct.size
            }
        } else {
            val distinct = fetched
                .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
            if (distinct.isNotEmpty()) {
                replaceSyncedLessons(groupName, distinct)
            }
            pruneOldLessons()
            Result.success(distinct.size + todayCached)
        }
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
