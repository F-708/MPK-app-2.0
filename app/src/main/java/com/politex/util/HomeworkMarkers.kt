package com.politex.util

import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object HomeworkMarkers {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    data class DayContext(
        val dayIso: String,
        val isToday: Boolean,
        val isPast: Boolean
    )

    fun dayIsoOf(cal: Calendar): String = isoFormat.format(cal.time)

    fun dedupeByLatestDate(lessons: List<LessonEntity>): List<LessonEntity> {
        val latestDated = lessons
            .filter { it.dateString.isNotBlank() }
            .maxOfOrNull { it.dateString.replace("-", ".") }
        return lessons.filter { it.dateString.isBlank() || it.dateString.replace("-", ".") == latestDated }
    }

    fun markerLessonNumbers(
        dayLessons: List<LessonEntity>,
        day: DayContext,
        nowMillis: Long,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
        pendingTasks: List<StudentTaskEntity>
    ): Set<Int> {
        if (day.isPast || dayLessons.isEmpty() || pendingTasks.isEmpty()) return emptySet()

        val result = mutableSetOf<Int>()
        for (lesson in dayLessons) {
            val subject = lesson.officialSubjectName
            val matched = pendingTasks.any { task ->
                val deadline = task.deadlineDate.trim()
                if (!subjectsMatch(subject, task.subjectName)) {
                    false
                } else if (deadline.isNotBlank()) {
                    sameIsoDay(deadline, day.dayIso)
                } else {
                    val targetIso = resolveNextOccurrenceIso(task.createdAt, subject, weeklyLessonsByDow)
                    targetIso != null && sameIsoDay(targetIso, day.dayIso)
                }
            }
            if (matched && !lessonPassed(lesson, day, nowMillis)) {
                result += lesson.lessonNumber
            }
        }
        return result
    }

    fun subjectsMatch(lessonSubject: String, taskSubject: String): Boolean {
        if (lessonSubject.isBlank() || taskSubject.isBlank()) return false
        val a = SubjectFormatter.normalize(lessonSubject)
        val b = SubjectFormatter.normalize(taskSubject)
        if (a.equals(b, ignoreCase = true)) return true
        return if (a.length >= 6 && b.length >= 6) {
            a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)
        } else false
    }

    fun resolveNextOccurrenceIso(
        createdAt: Long,
        subject: String,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>
    ): String? {
        val created = (Calendar.getInstance().apply { timeInMillis = createdAt }).clone() as Calendar
        for (offset in 0 until 28) {
            val cal = (created.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dow = calendarDow(cal)
            val dayLessons = weeklyLessonsByDow[dow] ?: continue
            val subjectLessons = dayLessons
                .filter { subjectsMatch(it.officialSubjectName, subject) }
            for (lesson in subjectLessons) {
                val endMillis = endOfDayMillis(cal, lesson.timeEnd)
                if (endMillis > createdAt) return dayIsoOf(cal)
            }
        }
        return null
    }

    private fun lessonPassed(lesson: LessonEntity, day: DayContext, nowMillis: Long): Boolean {
        if (!day.isToday) return false
        val nowMinutes = run {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
        val endMinutes = parseMinutes(lesson.timeEnd) ?: return false
        return endMinutes <= nowMinutes
    }

    private fun endOfDayMillis(dayCal: Calendar, timeEnd: String): Long {
        val cal = (dayCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val minutes = parseMinutes(timeEnd) ?: (24 * 60)
        cal.add(Calendar.MINUTE, minutes)
        return cal.timeInMillis
    }

    private fun parseMinutes(time: String): Int? {
        val digits = Regex("(\\d{1,2})\\D+(\\d{1,2})").find(time.trim()) ?: return null
        val h = digits.groupValues[1].toIntOrNull() ?: return null
        val m = digits.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun sameIsoDay(a: String, b: String): Boolean =
        normalizeIso(a) == normalizeIso(b) && normalizeIso(a).isNotBlank()

    private fun normalizeIso(value: String): String =
        value.trim().replace("-", ".").replace("/", ".")

    private fun calendarDow(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }
}
