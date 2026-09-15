package com.example.data.repository

import android.content.Context

/**
 * Учащийся колледжа из базы приёмной комиссии (только admin-версия).
 */
data class Student(
    val fullName: String,
    val group: String,
    val course: String,
    val specialtyCode: String,
    val specialty: String,
    val funding: String,   // Бюджет / Платное
    val dormitory: String, // Общежитие
    val curator: String,
    val room: String
)

/**
 * Репозиторий базы учащихся (assets/students.csv, 1703 записи).
 * Формат: ФИО;Группа;Курс;КодСпец;Специальность;Основа;Общежитие;Куратор;Кабинет
 */
class StudentsRepository(private val context: Context) {

    fun loadStudents(): List<Student> = try {
        context.assets.open("students.csv").bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split(';')
                Student(
                    fullName = p.getOrElse(0) { "" },
                    group = p.getOrElse(1) { "" },
                    course = p.getOrElse(2) { "" },
                    specialtyCode = p.getOrElse(3) { "" },
                    specialty = p.getOrElse(4) { "" },
                    funding = p.getOrElse(5) { "" },
                    dormitory = p.getOrElse(6) { "" },
                    curator = p.getOrElse(7) { "" },
                    room = p.getOrElse(8) { "" }
                )
            }
            .filter { it.fullName.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}
