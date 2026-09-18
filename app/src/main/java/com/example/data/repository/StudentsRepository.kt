package com.example.data.repository

import android.content.Context
import com.example.util.MpkCurriculum

data class Student(
    val fullName: String,
    val group: String,
    val course: String,
    val specialtyCode: String,
    val specialty: String,
    val funding: String,
    val dormitory: String,
    val curator: String,
    val room: String,

    val courseMismatch: Boolean = false
)

class StudentsRepository(private val context: Context) {

    fun loadStudents(): List<Student> = try {
        context.assets.open("students.csv").bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split(';')
                val group = p.getOrElse(1) { "" }

                val course = p.getOrElse(2) { "" }.filter { it.isDigit() }

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
