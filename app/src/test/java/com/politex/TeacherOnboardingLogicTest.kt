package com.politex

import com.politex.data.repository.Teacher
import com.politex.ui.teacher.TeacherOnboardingLogic
import com.politex.ui.teacher.TeacherProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherOnboardingLogicTest {

    private val teachers = listOf(
        Teacher(name = "Иванова Мария Петровна", position = "Преподаватель", subjects = "Математика"),
        Teacher(name = "Иванова Мария Сергеевна", position = "Заведующий отделением", subjects = "Физика"),
        Teacher(name = "Петров Алексей Иванович", position = "Преподаватель", subjects = "Информатика"),
        Teacher(name = "Сидорова Анна Львовна", position = "Психолог", subjects = ""),
    )

    @Test
    fun `empty query returns all`() {
        assertEquals(teachers, TeacherOnboardingLogic.filterTeachers(teachers, ""))
        assertEquals(teachers, TeacherOnboardingLogic.filterTeachers(teachers, "   "))
    }

    @Test
    fun `filters by surname and by subject`() {
        // fuzzy: «иванова» достаёт и «Иванов» (расстояние 1) — поведение общее с поиском материалов
        val bySurname = TeacherOnboardingLogic.filterTeachers(teachers, "иванова")
        assertEquals(2, bySurname.count { it.name.startsWith("Иванова") })
        val bySubject = TeacherOnboardingLogic.filterTeachers(teachers, "математика")
        assertEquals("Иванова Мария Петровна", bySubject.first().name)
    }

    @Test
    fun `fuzzy tolerates typos`() {
        val res = TeacherOnboardingLogic.filterTeachers(teachers, "иваноа")
        assertTrue(res.isNotEmpty())
        assertTrue(res.any { it.name == "Иванова Мария Петровна" })
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(TeacherOnboardingLogic.filterTeachers(teachers, "zzzqqq").isEmpty())
    }

    @Test
    fun `profile mapping uses surnameKey`() {
        val p = TeacherOnboardingLogic.profileOf(teachers[0])
        assertEquals(TeacherProfile("Иванова Мария Петровна", "иванова"), p)
        assertEquals(com.politex.data.repository.TeacherInsights.surnameOf(teachers[0].name), p.surnameKey)
    }

    @Test
    fun `surnameKey normalizes yo to ye`() {
        val t = Teacher(name = "Ерёменко Ольга Викторовна")
        assertEquals("еременко", TeacherOnboardingLogic.profileOf(t).surnameKey)
    }

    @Test
    fun `same surname shows both candidates`() {
        val c = TeacherOnboardingLogic.confirmCandidates(teachers[0], teachers)
        assertEquals(2, c.size)
        assertTrue(c.contains(teachers[0]) && c.contains(teachers[1]))
    }

    @Test
    fun `unique surname shows single candidate`() {
        assertEquals(listOf(teachers[3]), TeacherOnboardingLogic.confirmCandidates(teachers[3], teachers))
    }

    @Test
    fun `skip does not write profile - onboarding stays dismissible`() {
        // «Пропустить» = profile null + skipped true → онбординг больше не показываем, профиль не записан
        assertTrue(TeacherOnboardingLogic.showOnboarding(profile = null, skipped = false))
        assertFalse(TeacherOnboardingLogic.showOnboarding(profile = null, skipped = true))
        assertFalse(TeacherOnboardingLogic.showOnboarding(TeacherProfile("а", "а"), skipped = false))
    }
}
