package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Занятие преподавателя из документа «расписание преподавателей».
 *
 * Отдельная таблица, а не колонка в `lessons`: документы разные (в студенческом —
 * группы и пары, в преподавательском — ФИО, кабинет и список групп), и одна строка
 * там описывает занятие одного преподавателя, а не группы. Смешивать их значило бы
 * ломать основной путь разбора, который кормит всё приложение.
 *
 * Заполняется при синке, поэтому расписание преподавателя открывается офлайн —
 * раньше оно качалось заново при каждом входе в карточку и при недоступном сайте
 * (VPN) не открывалось вовсе.
 */
@Entity(
    tableName = "teacher_lessons",
    // Поиск всегда идёт «занятия этого преподавателя на эту дату» — индекс по обоим полям
    indices = [Index("surnameKey", "dateString")]
)
data class TeacherLessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Полное ФИО, как в `teachers.csv` — для отображения. */
    val teacherName: String,

    /**
     * Фамилия в нижнем регистре (`TeacherInsights.surnameOf`) — ключ поиска:
     * в документе преподаватели записаны сокращённо, точное ФИО из CSV может не совпасть.
     */
    val surnameKey: String,

    val dateString: String,
    val dayOfWeek: Int,
    val lessonNumber: Int,

    /** Группы занятия одной строкой через «, » — как их рисует вкладка расписания. */
    val groups: String,
    val subject: String,
    val room: String
)