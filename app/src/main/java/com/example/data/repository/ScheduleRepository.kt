package com.example.data.repository

import com.example.data.local.dao.LessonDao
import com.example.data.local.entity.LessonEntity
import com.example.data.network.MpkNetworkClient
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Репозиторий для работы с расписанием занятий колледжа.
 * Поддерживает локальное хранилище Room, сетевую синхронизацию и календарный архив.
 */
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

    /**
     * Синхронизация с сайтом: качает расписание на СЕГОДНЯ и на СЛЕДУЮЩИЙ учебный день
     * (колледж публикует завтрашний документ накануне). Сегодняшний запрос выполняется
     * последним — его статус остаётся в панели диагностики настроек.
     */
    suspend fun syncScheduleFromWeb(groupName: String): Result<Int> = withContext(Dispatchers.IO) {
        val tomorrowLessons = networkClient
            .fetchScheduleForGroup(groupName, nextSchoolDay())
            .getOrDefault(emptyList())

        networkClient.fetchScheduleForGroup(groupName).mapCatching { todayLessons ->
            val distinct = (tomorrowLessons + todayLessons)
                .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
            if (distinct.isNotEmpty()) {
                replaceSyncedLessons(groupName, distinct)
            }
            distinct.size
        }
    }

    /** Следующий учебный день: завтра, пропуская воскресенье (в понедельник). */
    private fun nextSchoolDay(base: Calendar = Calendar.getInstance()): Calendar {
        val cal = (base.clone() as Calendar)
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
        return cal
    }

    /**
     * Заменяет ранее синхронизированные уроки тех же дат (подход МПК v1: delete + insert,
     * но с привязкой к дате, чтобы повторные синхронизации не плодили дубли карточек
     * и не стирали архив других дат).
     */
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

