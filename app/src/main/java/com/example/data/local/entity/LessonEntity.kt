package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.util.SubjectFormatter

/**
 * Сущность учебного занятия (пары) в расписании колледжа МГПК.
 */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupName: String,
    val dayOfWeek: Int, // 1 = Понедельник .. 6 = Суббота
    val lessonNumber: Int, // 1..12 (номер урока; документы guo-mpk.by нумеруют уроками, не парами)
    val timeStart: String, // Например "08:30"
    val timeEnd: String, // Например "10:05"
    val subjectRaw: String, // Сырое наименование из расписания
    val roomFirst: String, // Аудитория подгруппы 1 (или общая)
    val teacherFirst: String, // Преподаватель подгруппы 1 (или общий)
    val roomSecond: String = "", // Аудитория подгруппы 2 (при делении)
    val teacherSecond: String = "", // Преподаватель подгруппы 2 (при делении)
    val isSplit: Boolean = false, // Флаг деления на 2 подгруппы
    val dateString: String = "" // Календарная дата в формате YYYY-MM-DD (для точечных изменений)
) {
    /**
     * Короткое название предмета (до 15 символов) для отображения в расписании.
     */
    @delegate:Ignore
    val shortSubjectName: String by lazy {
        SubjectFormatter.getShortName(subjectRaw)
    }

    /**
     * Акроним / бейдж дисциплины (2-4 символа).
     */
    @delegate:Ignore
    val subjectAcronym: String by lazy {
        SubjectFormatter.getAcronym(subjectRaw)
    }

    /**
     * Нормализованное официальное название из справочника МГПК.
     */
    @delegate:Ignore
    val officialSubjectName: String by lazy {
        SubjectFormatter.normalize(subjectRaw)
    }

    /**
     * Форматированный временной интервал пары: "08:30 - 10:05".
     */
    @delegate:Ignore
    val timeRangeDisplay: String by lazy {
        if (timeStart.isNotBlank() && timeEnd.isNotBlank()) "$timeStart – $timeEnd" else ""
    }
}
