package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.LessonEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для доступа к расписанию занятий.
 */
@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons WHERE groupName = :groupName AND dayOfWeek = :dayOfWeek ORDER BY lessonNumber ASC")
    fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE groupName = :groupName AND dateString = :dateString ORDER BY lessonNumber ASC")
    fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>>

    @Query("""
        SELECT * FROM lessons 
        WHERE groupName = :groupName 
          AND (
            (dateString != '' AND dateString = :dateString)
            OR (dateString = '' AND dayOfWeek = :dayOfWeek)
          )
        ORDER BY lessonNumber ASC
    """)
    fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE groupName = :groupName ORDER BY dayOfWeek ASC, lessonNumber ASC")
    fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE groupName = :groupName AND dayOfWeek = :dayOfWeek ORDER BY lessonNumber ASC")
    suspend fun getLessonsForDaySync(groupName: String, dayOfWeek: Int): List<LessonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: LessonEntity): Long

    @Query("DELETE FROM lessons WHERE groupName = :groupName")
    suspend fun clearLessonsForGroup(groupName: String)

    @Query("DELETE FROM lessons WHERE groupName = :groupName AND dateString = :dateString")
    suspend fun deleteLessonsForDate(groupName: String, dateString: String)

    @Query("DELETE FROM lessons WHERE groupName = :groupName AND dayOfWeek = :dayOfWeek")
    suspend fun deleteLessonsForDay(groupName: String, dayOfWeek: Int)

    // dateString хранится как «дд.ММ.гггг», поэтому обычная сортировка строк дала бы
    // 31.12 < 01.01 — сортируем по частям: год, месяц, день.
    @Query("""
        SELECT DISTINCT dateString FROM lessons
        WHERE groupName = :groupName AND dateString != ''
        ORDER BY substr(dateString, 7, 4) DESC, substr(dateString, 4, 2) DESC, substr(dateString, 1, 2) DESC
    """)
    fun getDistinctDatesForGroup(groupName: String): Flow<List<String>>

    @Query("SELECT * FROM lessons WHERE groupName = :groupName AND (subjectRaw LIKE '%' || :query || '%' OR teacherFirst LIKE '%' || :query || '%' OR teacherSecond LIKE '%' || :query || '%' OR roomFirst LIKE '%' || :query || '%') ORDER BY dayOfWeek ASC, lessonNumber ASC")
    fun searchLessonsForGroup(groupName: String, query: String): Flow<List<LessonEntity>>

    @Query("SELECT COUNT(*) FROM lessons WHERE groupName = :groupName")
    suspend fun getLessonCountForGroup(groupName: String): Int

    /** Все встречающиеся даты (по всем группам) — для ограничения размера архива. */
    @Query("SELECT DISTINCT dateString FROM lessons WHERE dateString != ''")
    suspend fun getAllDistinctDatesSync(): List<String>

    /** Удаление уроков конкретной даты по всем группам (устаревший архив). */
    @Query("DELETE FROM lessons WHERE dateString = :dateString")
    suspend fun deleteLessonsByDate(dateString: String)

    @Transaction
    suspend fun replaceScheduleForGroup(groupName: String, newLessons: List<LessonEntity>) {
        clearLessonsForGroup(groupName)
        insertLessons(newLessons)
    }
}
