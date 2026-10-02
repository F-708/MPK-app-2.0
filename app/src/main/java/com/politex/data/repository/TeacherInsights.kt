package com.politex.data.repository

import com.politex.data.local.entity.LessonEntity

class TeacherInsights(
    private val teachers: List<Teacher>,
    lessons: List<LessonEntity>,
    // W28: «срез» актуальности для шильдика «Ведёт у вас». Замены/ушедшие преподаватели
    // fade-out: погашаем, если нет ни недельного шаблона, ни урока с датой за последние
    // [STALE_DAYS] дней. Дефолт — текущее время (вызывающие не меняются).
    private val nowMillis: Long = System.currentTimeMillis(),
) {
    private val bySurname: Map<String, List<Teacher>> =
        teachers.groupBy { surnameOf(it.name) }.filterKeys { it.isNotBlank() }

    /** Порог погашения W28 — ВЫШЕ matched: buildFacts читает его при инициализации. */
    private val staleMillis = STALE_DAYS * 24L * 60L * 60L * 1000L

    private val matched: Map<String, TeacherFacts> = buildFacts(lessons)

    val activeNames: Set<String> =
        matched.filterValues { it.activeNow }.keys

    fun isActive(name: String): Boolean = activeNames.contains(name)

    fun rooms(name: String): List<String> = matched[name]?.rooms.orEmpty()

    fun subjects(name: String): List<String> = matched[name]?.subjects.orEmpty()

    fun lessonCount(name: String): Int = matched[name]?.count ?: 0

    fun lastSeenDate(name: String): String = matched[name]?.lastDate.orEmpty()

    private data class TeacherFacts(
        val rooms: List<String>,
        val subjects: List<String>,
        val count: Int,
        val lastDate: String,
        // W28: недельный шаблон (регулярное расписание) ИЛИ последняя датированная
        // пара свежее порога → преподаватель «ведёт сейчас»
        val activeNow: Boolean = false,
    )

    private fun buildFacts(lessons: List<LessonEntity>): Map<String, TeacherFacts> {
        val rooms = mutableMapOf<String, MutableList<String>>()
        val subjects = mutableMapOf<String, MutableList<String>>()
        val counts = mutableMapOf<String, Int>()
        val dates = mutableMapOf<String, String>()
        val weekly = mutableMapOf<String, Boolean>()
        val dateMillis = mutableMapOf<String, Long>()

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
                    // W28: максимальная дата в epoch (сравнение дат — только через epoch,
                    // лексикографика dd.MM.yyyy запрещена конвенцией)
                    parseDotDateMillis(dateKey)?.let { ms ->
                        val prevMs = dateMillis[full]
                        if (prevMs == null || ms > prevMs) dateMillis[full] = ms
                    }
                } else {
                    // Пустая dateString = недельный шаблон — преподаватель в актуальном
                    // расписании группы, свежесть не проверяем
                    weekly[full] = true
                }
            }
        }

        return counts.keys.associateWith { full ->
            TeacherFacts(
                rooms =
                rooms[full]
                    .orEmpty()
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key },
                subjects = subjects[full].orEmpty().distinct().take(6),
                count = counts[full] ?: 0,
                lastDate = dates[full].orEmpty(),
                activeNow =
                weekly[full] == true ||
                    (dateMillis[full] != null && dateMillis[full]!! >= nowMillis - staleMillis),
            )
        }
    }

    /** Дата урока → epoch millis; оба встречающихся формата: dd.MM.yyyy и yyyy-MM-dd (→ yyyy.MM.dd). null при кривом. */
    private fun parseDotDateMillis(date: String): Long? {
        val parts = date.split(".")
        if (parts.size != 3) return null
        val pattern =
            if (parts[0].length == 4) "yyyy.MM.dd" else "dd.MM.yyyy"
        return runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).apply {
                isLenient = false
            }.parse(date)?.time
        }.getOrNull()
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
        /** W28: дней свежести датированных уроков для шильдика «Ведёт у вас». */
        const val STALE_DAYS = 30L

        fun splitTeacherNames(raw: String): List<String> = raw
            .split('/')
            .map { it.trim() }
            .filter { it.length >= 3 }

        fun surnameOf(raw: String): String {
            val first =
                raw
                    .trim()
                    .replace(Regex("[0-9]"), "")
                    .split(Regex("\\s+"))
                    .firstOrNull() ?: return ""
            return first.trim(' ', ',', '.', '-', '/', '\\').lowercase()
        }

        fun initialsOf(raw: String): String {
            val parts = raw.trim().split(Regex("\\s+")).drop(1)
            return parts
                .joinToString("") { part ->
                    part.trim('.', ',', '/', '-').take(1).lowercase()
                }.filter { it.isLetter() }
        }
    }
}
