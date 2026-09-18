package com.example.data.repository

import com.example.data.local.dao.LessonDao
import com.example.data.local.entity.LessonEntity
import com.example.data.network.MpkNetworkClient
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
        for (dateString in lessons.map { it.dateString }.filter { it.isNotBlank() }.distinct()) {
            lessonDao.deleteLessonsForDate(groupName, dateString)
        }
        lessonDao.insertLessons(lessons)
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
