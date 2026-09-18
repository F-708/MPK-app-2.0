package com.example.data.repository

import com.example.data.local.entity.LessonEntity

class TeacherInsights(
    private val teachers: List<Teacher>,
    lessons: List<LessonEntity>
) {

    private val bySurname: Map<String, List<Teacher>> =
        teachers.groupBy { surnameOf(it.name) }.filterKeys { it.isNotBlank() }

    private val matched: Map<String, TeacherFacts> = buildFacts(lessons)

    val activeNames: Set<String> = matched.keys

    fun isActive(name: String): Boolean = activeNames.contains(name)

    fun rooms(name: String): List<String> = matched[name]?.rooms.orEmpty()

    fun subjects(name: String): List<String> = matched[name]?.subjects.orEmpty()

    fun lessonCount(name: String): Int = matched[name]?.count ?: 0

    fun lastSeenDate(name: String): String = matched[name]?.lastDate.orEmpty()

    private data class TeacherFacts(
        val rooms: List<String>,
        val subjects: List<String>,
        val count: Int,
        val lastDate: String
    )

    private fun buildFacts(lessons: List<LessonEntity>): Map<String, TeacherFacts> {
        val rooms = mutableMapOf<String, MutableList<String>>()
        val subjects = mutableMapOf<String, MutableList<String>>()
        val counts = mutableMapOf<String, Int>()
        val dates = mutableMapOf<String, String>()

        lessons.forEach { lesson ->
            val names = splitTeacherNames(lesson.teacherFirst) + splitTeacherNames(lesson.teacherSecond)
            val lessonRooms = listOf(lesson.roomFirst, lesson.roomSecond).filter { it.isNotBlank() }

            names.forEach { rawName ->
                val teacher = matchTeacher(rawName) ?: return@forEach
                val full = teacher.name

                if (lessonRooms.isNotEmpty()) {
                    rooms.getOrPut(full) { mutableListOf() }.addAll(lessonRooms)
                }
                if (lesson.subjectRaw.isNotBlank()) {
                    subjects.getOrPut(full) { mutableListOf() }.add(lesson.subjectRaw)
                }
                counts[full] = (counts[full] ?: 0) + 1

                val dateKey = lesson.dateString.replace("-", ".")
                if (dateKey.isNotBlank()) {
                    val prev = dates[full]
                    if (prev == null || dateKey > prev) dates[full] = dateKey
                }
            }
        }

        return counts.keys.associateWith { full ->
            TeacherFacts(

                rooms = rooms[full].orEmpty().groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key },
                subjects = subjects[full].orEmpty().distinct().take(6),
                count = counts[full] ?: 0,
                lastDate = dates[full].orEmpty()
            )
        }
    }

    fun matchTeacher(rawName: String): Teacher? {
        val clean = rawName.trim().trim(',', '/', '-', ' ')
        if (clean.length < 3) return null

        val surname = surnameOf(clean)
        if (surname.isBlank()) return null

        bySurname[surname]?.let { candidates ->
            if (candidates.size == 1) return candidates.first()

            val initials = initialsOf(clean)
            if (initials.isEmpty()) return null
            return candidates.firstOrNull { teacher ->
                val candidateInitials = initialsOf(teacher.name)
                candidateInitials.isNotEmpty() && candidateInitials.startsWith(initials)
            }
        }

        if (surname.length >= 5) {
            val prefixMatches = bySurname.filterKeys { it.startsWith(surname) }
            if (prefixMatches.size == 1) {
                val candidates = prefixMatches.values.first()
                if (candidates.size == 1) return candidates.first()
                val initials = initialsOf(clean)
                return candidates.firstOrNull { teacher ->
                    initials.isNotEmpty() && initialsOf(teacher.name).startsWith(initials)
                }
            }
        }

        return null
    }

    companion object {

        fun splitTeacherNames(raw: String): List<String> =
            raw.split('/')
                .map { it.trim() }
                .filter { it.length >= 3 }

        fun surnameOf(raw: String): String {
            val first = raw.trim()
                .replace(Regex("[0-9]"), "")
                .split(Regex("\\s+"))
                .firstOrNull() ?: return ""
            return first.trim(' ', ',', '.', '-', '/', '\\').lowercase()
        }

        fun initialsOf(raw: String): String {
            val parts = raw.trim().split(Regex("\\s+")).drop(1)
            return parts.joinToString("") { part ->
                part.trim('.', ',', '/', '-').take(1).lowercase()
            }.filter { it.isLetter() }
        }
    }
}
