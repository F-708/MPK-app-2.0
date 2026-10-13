package com.politex.util

import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object HomeworkMarkers {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    data class DayContext(
        val dayIso: String,
        val isToday: Boolean,
        val isPast: Boolean,
    )

    fun dayIsoOf(cal: Calendar): String = isoFormat.format(cal.time)

    fun dedupeByLatestDate(lessons: List<LessonEntity>): List<LessonEntity> {
        val latestMillis =
            lessons
                .filter { it.dateString.isNotBlank() }
                .maxOfOrNull { dateToMillis(it.dateString) } ?: return lessons
        return lessons.filter { it.dateString.isBlank() || dateToMillis(it.dateString) == latestMillis }
    }

    internal fun dateToMillis(date: String): Long = com.politex.data.network.MpkScheduleParser
        .parseDotDate(date)
        ?.timeInMillis ?: 0L

    fun markerLessonTypes(
        dayLessons: List<LessonEntity>,
        day: DayContext,
        nowMillis: Long,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
        pendingTasks: List<StudentTaskEntity>,
    ): Map<Int, List<TaskType>> {
        if (day.isPast || dayLessons.isEmpty() || pendingTasks.isEmpty()) return emptyMap()

        val result = LinkedHashMap<Int, MutableList<TaskType>>()
        for (lesson in dayLessons) {
            if (lessonPassed(lesson, day, nowMillis)) continue
            val subject = lesson.officialSubjectName
            for (task in pendingTasks) {
                val deadline = task.deadlineDate.trim()
                val matches =
                    if (!subjectsMatch(subject, task.subjectName)) {
                        false
                    } else if (deadline.isNotBlank()) {
                        sameIsoDay(deadline, day.dayIso)
                    } else {
                        val targetIso = resolveNextOccurrenceIso(task.createdAt, subject, weeklyLessonsByDow)
                        targetIso != null && sameIsoDay(targetIso, day.dayIso)
                    }
                if (matches) {
                    result.getOrPut(lesson.lessonNumber) { mutableListOf() }.add(task.taskTypeEnum)
                }
            }
        }
        return result.mapValues { (_, types) -> types.distinct() }
    }

    fun subjectsMatch(
        lessonSubject: String,
        taskSubject: String,
    ): Boolean {
        if (lessonSubject.isBlank() || taskSubject.isBlank()) return false
        val b = SubjectFormatter.normalize(taskSubject)
        // v2.0 (баг «ДЗ не показывается на split-уроках»): у урока с РАЗНЫМИ предметами
        // подгрупп subjectRaw = «ПредметA / ПредметB» (MpkScheduleParser:1006), а
        // normalize() склейки не даёт точного равенства, contains-fallback же требует
        // длину ≥6 у обеих строк — короткие предметы («ТОЭ», «Химия») терялись.
        // Сплитим склейку по « / » (сам парсер ставит этот разделитель; в каталоге
        // предметов «/» не встречается) и матчим КАЖДУЮ часть отдельно.
        return lessonSubject
            .split('/')
            .map { SubjectFormatter.normalize(it) }
            .filter { it.isNotBlank() }
            .any { a ->
                a.equals(b, ignoreCase = true) ||
                    (a.length >= 6 && b.length >= 6 &&
                        (a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)))
            }
    }

    fun resolveNextOccurrenceIso(
        createdAt: Long,
        subject: String,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
    ): String? {
        val created = (Calendar.getInstance().apply { timeInMillis = createdAt }).clone() as Calendar
        for (offset in 0 until 28) {
            val cal = (created.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dow = Dow.of(cal)
            val dayLessons = weeklyLessonsByDow[dow] ?: continue
            val subjectLessons =
                dayLessons
                    .filter { subjectsMatch(it.officialSubjectName, subject) }
            for (lesson in subjectLessons) {
                val endMillis = endOfDayMillis(cal, lesson.timeEnd)
                if (endMillis > createdAt) return dayIsoOf(cal)
            }
        }
        return null
    }

    private fun lessonPassed(
        lesson: LessonEntity,
        day: DayContext,
        nowMillis: Long,
    ): Boolean {
        if (!day.isToday) return false
        val nowMinutes =
            run {
                val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            }
        val endMinutes = parseMinutes(lesson.timeEnd) ?: return false
        return endMinutes <= nowMinutes
    }

    private fun endOfDayMillis(
        dayCal: Calendar,
        timeEnd: String,
    ): Long {
        val cal =
            (dayCal.clone() as Calendar).apply {
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

    private fun sameIsoDay(
        a: String,
        b: String,
    ): Boolean = normalizeIso(a) == normalizeIso(b) && normalizeIso(a).isNotBlank()

    private fun normalizeIso(value: String): String = value.trim().replace("-", ".").replace("/", ".")
}
