package com.politex.data.repository

import android.content.Context
import java.io.InputStream

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
    val category: String = "",
)

class TeachersRepository(
    private val context: Context,
) {
    /**
     * Б13: CSV преподавателей — неизменяемый ассет, а читался и парсился при каждом
     * вызове, в том числе прямо из тел композаблов и на каждой рекомпозиции-реминдере
     * (6 мест). Кэш один на процесс: тот же приём, что в ScheduleScreen для фамилий.
     */
    fun loadTeachers(): List<Teacher> = cached ?: parseTeachers().also { cached = it }

    private fun parseTeachers(): List<Teacher> = try {
        parseCsv(context.assets.open("teachers.csv"))
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        @Volatile
        private var cached: List<Teacher>? = null
    }

    private fun parseCsv(stream: InputStream): List<Teacher> = stream
        .bufferedReader(Charsets.UTF_8)
        .readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val p = line.split(';')
            Teacher(
                name = cell(p, 0),
                position = cell(p, 1),
                subjects = cell(p, 2),
                room = cell(p, 3),
                phone = cell(p, 4),
                email = cell(p, 5),
                photo = cell(p, 6),
                department = cell(p, 7),
                experience = cell(p, 8),
                category = cell(p, 9),
            )
        }.filter { it.name.isNotBlank() }
        .sortedBy { it.name }

    /**
     * TEA-01: в CSV прочерк «—» (U+2014) буквально стоит в room/phone/email у ~89 строк.
     * isNotBlank() его не ловит → UI рисует секцию «КОНТАКТЫ» с прочерками. Нормализуем
     * ячейку ЦЕЛИКОМ: trim, и если результат — один из прочерков, вернуть "". Дефисы
     * внутри значений не трогаем (телефон «338-33-42» остаётся как есть).
     */
    private fun cell(
        p: List<String>,
        i: Int,
    ): String {
        val v = p.getOrElse(i) { "" }.trim()
        return if (v.isEmpty() || v == "—" || v == "–" || v == "-") "" else v
    }

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
