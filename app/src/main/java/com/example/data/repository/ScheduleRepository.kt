package com.example.data.repository

import com.example.data.local.dao.LessonDao
import com.example.data.local.entity.LessonEntity
import com.example.data.network.MpkNetworkClient
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

    fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> {
        return lessonDao.getAllLessonsForGroup(groupName).flowOn(Dispatchers.IO)
    }

    fun getDistinctDates(groupName: String): Flow<List<String>> {
        return lessonDao.getDistinctDatesForGroup(groupName).flowOn(Dispatchers.IO)
    }

    fun searchLessons(groupName: String, query: String): Flow<List<LessonEntity>> {
        return lessonDao.searchLessonsForGroup(groupName, query).flowOn(Dispatchers.IO)
    }

    suspend fun syncScheduleFromWeb(groupName: String): Result<Int> = withContext(Dispatchers.IO) {
        val result = networkClient.fetchScheduleForGroup(groupName)
        result.mapCatching { lessons ->
            if (lessons.isNotEmpty()) {
                lessonDao.insertLessons(lessons)
            }
            lessons.size
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

