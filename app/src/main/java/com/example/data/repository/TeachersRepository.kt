package com.example.data.repository

import android.content.Context
import java.io.InputStream

/**
 * Преподаватель колледжа из CSV-базы.
 * photo — имя файла фотографии (в assets/teachers_photos/) или URL.
 */
data class Teacher(
    val name: String,
    val subject: String = "",
    val extra: String = "",
    val photo: String = ""
)

/**
 * Репозиторий преподавателей: читает CSV из assets (teachers.csv).
 *
 * Формат строки: ФИО;Предмет(ы);Доп.инфо;Фото
 * (разделитель — точка с запятой или запятая; кодировка UTF-8).
 * Фото: файлы кладутся в assets/teachers_photos/ с именем из 4-й колонки.
 *
 * Избранное хранится в SharedPreferences (звёздочка у преподавателя).
 */
class TeachersRepository(private val context: Context) {

    fun loadTeachers(): List<Teacher> = try {
        val stream = context.assets.open("teachers.csv")
        parseCsv(stream)
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseCsv(stream: InputStream): List<Teacher> {
        val text = stream.bufferedReader(Charsets.UTF_8).readText()
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(';', ',').map { it.trim() }
                if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null
                Teacher(
                    name = parts[0],
                    subject = parts.getOrElse(1) { "" },
                    extra = parts.getOrElse(2) { "" },
                    photo = parts.getOrElse(3) { "" }
                )
            }
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
