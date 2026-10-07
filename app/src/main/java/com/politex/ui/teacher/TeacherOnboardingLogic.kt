package com.politex.ui.teacher

import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.util.FuzzySearch

object TeacherOnboardingLogic {

    fun filterTeachers(
        teachers: List<Teacher>,
        query: String,
    ): List<Teacher> {
        if (query.isBlank()) return teachers
        return teachers
            .mapNotNull { t ->
                val score = FuzzySearch.score("${t.name} ${t.position} ${t.subjects}", query)
                if (score > 0) t to score else null
            }.sortedWith(
                compareByDescending<Pair<Teacher, Int>> { it.second }.thenBy { it.first.name },
            )
            .map { it.first }
    }

    fun profileOf(teacher: Teacher): TeacherProfile =
        TeacherProfile(name = teacher.name, surnameKey = TeacherInsights.surnameOf(teacher.name))

    // Однофамильцы: подтверждение показывает ВСЕ карточки с той же фамилией — выбор за пользователем
    fun confirmCandidates(
        selected: Teacher,
        all: List<Teacher>,
    ): List<Teacher> {
        val key = TeacherInsights.surnameOf(selected.name)
        val same = all.filter { TeacherInsights.surnameOf(it.name) == key }
        return if (same.isEmpty()) listOf(selected) else same
    }

    fun showOnboarding(
        profile: TeacherProfile?,
        skipped: Boolean,
    ): Boolean = profile == null && !skipped
}
