package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.data.model.TaskType
import com.example.util.SubjectFormatter

/**
 * Сущность студенческого задания (ДЗ, лаб, курсовая, диплом и т.д.).
 */
@Entity(tableName = "student_tasks")
data class StudentTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupName: String,
    val course: Int,
    val subjectName: String, // Официальное наименование предмета
    val taskType: String, // ДЗ, Лабораторная, Практическая, Контрольная, Курсовая, Диплом
    val title: String, // Заголовок / описание задания
    val description: String = "", // Дополнительные заметки
    val isCompleted: Boolean = false,
    val deadlineDate: String = "", // YYYY-MM-DD
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Тип задания в виде enum TaskType.
     */
    @delegate:Ignore
    val taskTypeEnum: TaskType by lazy {
        TaskType.fromDisplayName(taskType)
    }

    /**
     * Короткое название дисциплины.
     */
    @delegate:Ignore
    val shortSubjectName: String by lazy {
        SubjectFormatter.getShortName(subjectName)
    }

    /**
     * Акроним дисциплины.
     */
    @delegate:Ignore
    val subjectAcronym: String by lazy {
        SubjectFormatter.getAcronym(subjectName)
    }
}
