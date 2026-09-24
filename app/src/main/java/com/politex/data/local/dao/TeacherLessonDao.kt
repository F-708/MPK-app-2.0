package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.politex.data.local.entity.TeacherLessonEntity

@Dao
interface TeacherLessonDao {

    /**
     * Занятия преподавателя на дату. Ключ — фамилия (в документе ФИО сокращённое),
     * порядок — по номеру занятия.
     */
    @Query("""
        SELECT * FROM teacher_lessons
        WHERE surnameKey = :surnameKey AND dateString = :dateString
        ORDER BY lessonNumber ASC
    """)
    suspend fun getForSurnameOnDate(surnameKey: String, dateString: String): List<TeacherLessonEntity>

    /** Преподаватели, у которых есть занятие в этот день — для списка активных. */
    @Query("SELECT DISTINCT teacherName FROM teacher_lessons WHERE dateString = :dateString")
    suspend fun getTeachersOnDate(dateString: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<TeacherLessonEntity>)

    @Query("DELETE FROM teacher_lessons WHERE dateString = :dateString")
    suspend fun deleteForDate(dateString: String)

    /**
     * Замена дня одной транзакцией: DELETE и INSERT неразделимы, иначе на миг
     * эмитится пустой список и в UI мелькает «занятий нет».
     */
    @Transaction
    suspend fun replaceForDate(dateString: String, lessons: List<TeacherLessonEntity>) {
        deleteForDate(dateString)
        insertLessons(lessons)
    }

    @Query("""
        SELECT DISTINCT dateString FROM teacher_lessons
        WHERE dateString != '' AND dateString IN (:dates)
    """)
    suspend fun getPresentDates(dates: List<String>): List<String>
}