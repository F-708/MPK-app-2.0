package com.example.data.repository

import android.content.Context
import java.io.InputStream

/**
 * Преподаватель колледжа из официальной базы guo-mpk.by.
 */
data class Teacher(
    val name: String,
    val position: String = "",
    val subjects: String = "",
    val room: String = "",
    val phone: String = "",
    val email: String = "",
    val photo: String = "",
    val department: String = "",
    val experience: String = "",
    val category: String = ""
)

/**
 * Репозиторий преподавателей: официальная база из assets (teachers.csv
 * + teachers_photos/). 101 сотрудник с фотографиями.
 *
 * Формат CSV: ФИО;должность;дисциплины;кабинет;телефон;email;фото;отдел;стаж;категория
 * Избранное хранится в SharedPreferences.
 */
class TeachersRepository(private val context: Context) {

    fun loadTeachers(): List<Teacher> = try {
        parseCsv(context.assets.open("teachers.csv"))
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseCsv(stream: InputStream): List<Teacher> {
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val p = line.split(';')
                Teacher(
                    name = p.getOrElse(0) { "" },
                    position = p.getOrElse(1) { "" },
                    subjects = p.getOrElse(2) { "" },
                    room = p.getOrElse(3) { "" },
                    phone = p.getOrElse(4) { "" },
                    email = p.getOrElse(5) { "" },
                    photo = p.getOrElse(6) { "" },
                    department = p.getOrElse(7) { "" },
                    experience = p.getOrElse(8) { "" },
                    category = p.getOrElse(9) { "" }
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name }
    }

    // ------------------------------ Избранное ------------------------------

    private val prefs get() = context.getSharedPreferences("teachers_favorites", Context.MODE_PRIVATE)

    fun isFavorite(name: String): Boolean = prefs.getBoolean(key(name), false)

    fun toggleFavorite(name: String): Boolean {
        val newValue = !isFavorite(name)
        prefs.edit().putBoolean(key(name), newValue).apply()
        return newValue
    }

    fun favorites(): Set<String> = prefs.all.keys
        .filter { prefs.getBoolean(it, false) }
        .map { it.removePrefix("fav_") }
        .toSet()

    private fun key(name: String) = "fav_" + name.lowercase()
}
