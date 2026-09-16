package com.example.data.repository

import android.content.Context
import com.example.util.MpkCurriculum

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
    val room: String,
    /**
     * Курс в базе больше, чем курсов у специальности (например, 4-й курс у ДОУ,
     * где учат три года). Такие записи не удаляем — это живые люди, — но помечаем,
     * чтобы не отправлять администратора искать несуществующую группу.
     */
    val courseMismatch: Boolean = false
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
                val group = p.getOrElse(1) { "" }
                // В исходной базе курс записан как «4 курс» — оставляем только цифру
                val course = p.getOrElse(2) { "" }.filter { it.isDigit() }
                // Буква специальности — последний символ группы («41Д» -> «Д»)
                val letter = group.lastOrNull()
                val maxCourse = letter
                    ?.let { MpkCurriculum.getSpecialty(it) }
                    ?.subjectsByCourse?.keys?.maxOrNull()
                Student(
                    fullName = p.getOrElse(0) { "" },
                    group = group,
                    course = course,
                    specialtyCode = p.getOrElse(3) { "" },
                    specialty = p.getOrElse(4) { "" },
                    funding = p.getOrElse(5) { "" },
                    dormitory = p.getOrElse(6) { "" },
                    curator = p.getOrElse(7) { "" },
                    room = p.getOrElse(8) { "" },
                    courseMismatch = maxCourse != null &&
                        (course.toIntOrNull() ?: 0) > maxCourse
                )
            }
            .filter { it.fullName.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}
