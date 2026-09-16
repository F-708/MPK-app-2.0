package com.example.data.repository

import com.example.data.local.entity.LessonEntity

/**
 * Сопоставление преподавателей из официальной базы (assets/teachers.csv)
 * с преподавателями из расписания (Room) и вывод производной информации.
 *
 * Проблема: в CSV кабинета почти нет (у 89 из 101 стоит «—»), а в расписании
 * ФИО сокращены до «Фамилия И.О.» или даже обрезаны («Серединов А»,
 * «Дубровский»). Поэтому сопоставление идёт по ФАМИЛИИ (первое слово),
 * а при неоднозначности уточняется инициалами.
 */
class TeacherInsights(
    private val teachers: List<Teacher>,
    lessons: List<LessonEntity>
) {

    /** Фамилия (в нижнем регистре) → преподаватели базы с такой фамилией. */
    private val bySurname: Map<String, List<Teacher>> =
        teachers.groupBy { surnameOf(it.name) }.filterKeys { it.isNotBlank() }

    /** Сопоставления, найденные в расписании: ФИО из базы → факты. */
    private val matched: Map<String, TeacherFacts> = buildFacts(lessons)

    /** Преподаватели, которые ведут уроки у выбранной группы (в начале списка). */
    val activeNames: Set<String> = matched.keys

    fun isActive(name: String): Boolean = activeNames.contains(name)

    /** Кабинеты, в которых преподаватель ведёт уроки (в порядке частоты). */
    fun rooms(name: String): List<String> = matched[name]?.rooms.orEmpty()

    /** Дисциплины, которые преподаватель ведёт у группы. */
    fun subjects(name: String): List<String> = matched[name]?.subjects.orEmpty()

    /** Количество уроков у группы. */
    fun lessonCount(name: String): Int = matched[name]?.count ?: 0

    /** Дата последнего урока (для подписи «ведёт сейчас/недавно»). */
    fun lastSeenDate(name: String): String = matched[name]?.lastDate.orEmpty()

    // -----------------------------------------------------------------------

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
                // Частотная сортировка: чаще всего ведёт в этом кабинете — первым
                rooms = rooms[full].orEmpty().groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key },
                subjects = subjects[full].orEmpty().distinct().take(6),
                count = counts[full] ?: 0,
                lastDate = dates[full].orEmpty()
            )
        }
    }

    /**
     * Сопоставляет сырое ФИО из расписания («Зыбин О.Л.», «Серединов А»,
     * «Березовска») с записью официальной базы.
     *
     * Порядок: точная фамилия → префикс фамилии (в расписании ФИО бывает
     * обрезано: «Березовска» вместо «Березовская») → уточнение инициалами
     * при однофамильцах. Возвращает null, если определить не удалось.
     */
    fun matchTeacher(rawName: String): Teacher? {
        val clean = rawName.trim().trim(',', '/', '-', ' ')
        if (clean.length < 3) return null

        val surname = surnameOf(clean)
        if (surname.isBlank()) return null

        bySurname[surname]?.let { candidates ->
            if (candidates.size == 1) return candidates.first()
            // Однофамильцы — уточняем инициалами
            val initials = initialsOf(clean)
            if (initials.isEmpty()) return null
            return candidates.firstOrNull { teacher ->
                val candidateInitials = initialsOf(teacher.name)
                candidateInitials.isNotEmpty() && candidateInitials.startsWith(initials)
            }
        }

        // Обрезанная фамилия: ищем по префиксу (не короче 5 букв),
        // чтобы «Березовска» нашла «Березовская»
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

        /** Разбивает строку преподавателей «А / Б» на отдельные ФИО. */
        fun splitTeacherNames(raw: String): List<String> =
            raw.split('/')
                .map { it.trim() }
                .filter { it.length >= 3 }

        /** Фамилия: первое слово без цифр и служебных символов. */
        fun surnameOf(raw: String): String {
            val first = raw.trim()
                .replace(Regex("[0-9]"), "")
                .split(Regex("\\s+"))
                .firstOrNull() ?: return ""
            return first.trim(' ', ',', '.', '-', '/', '\\').lowercase()
        }

        /** Инициалы из «Зыбин О.Л.» / «Зыбин Олег Львович» → «ол». */
        fun initialsOf(raw: String): String {
            val parts = raw.trim().split(Regex("\\s+")).drop(1)
            return parts.joinToString("") { part ->
                part.trim('.', ',', '/', '-').take(1).lowercase()
            }.filter { it.isLetter() }
        }
    }
}
