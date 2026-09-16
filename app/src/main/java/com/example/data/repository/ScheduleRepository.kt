package com.example.data.repository

import com.example.data.local.dao.LessonDao
import com.example.data.local.entity.LessonEntity
import com.example.data.network.MpkNetworkClient
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Формат дат в LessonEntity.dateString — «дд.ММ.гггг» (задаёт MpkScheduleParser). */
private val DATE_DD_MM_YYYY = Regex("""\d{2}\.\d{2}\.\d{4}""")

/**
 * Сколько дней архива расписания хранить. Колледж публикует документы раз в день,
 * и каждая синхронизация добавляет новую дату — без чистки БД растёт бесконечно.
 */
private const val ARCHIVE_KEEP_DAYS = 60L

/**
 * true, если дата «дд.ММ.гггг» старше [cutoffMs]. Строки, не похожие на дату,
 * НЕ считаются устаревшими: лучше оставить лишнее, чем удалить действующее расписание
 * (уроки-шаблоны по дню недели хранятся с пустым dateString).
 */
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
            pruneOldLessons()
            distinct.size
        }
    }

    /**
     * Ограничивает архив расписания: без этого БД растёт бесконечно — каждая
     * синхронизация добавляет новую дату и старые никогда не удаляются.
     * Чистит по всем группам сразу (включая те, на которые пользователь уже
     * переключился), поэтому вызывается из синхронизации, а не из удаления группы.
     */
    suspend fun pruneOldLessons(keepDays: Long = ARCHIVE_KEEP_DAYS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L
        for (date in lessonDao.getAllDistinctDatesSync()) {
            if (isStaleDate(date, cutoff)) lessonDao.deleteLessonsByDate(date)
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

