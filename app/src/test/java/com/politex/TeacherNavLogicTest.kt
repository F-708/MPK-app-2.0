package com.politex

import com.politex.ui.teacher.TeacherNavLogic
import com.politex.ui.teacher.TeacherTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TeacherNavLogicTest {
    @Test
    fun `deep-link extra maps to tab`() {
        assertEquals(TeacherTab.TODAY, TeacherNavLogic.tabForExtra("today"))
        assertEquals(TeacherTab.WEEK, TeacherNavLogic.tabForExtra("week"))
        assertEquals(TeacherTab.MORE, TeacherNavLogic.tabForExtra("more"))
    }

    @Test
    fun `unknown or null extra falls back to TODAY (root)`() {
        assertEquals(TeacherTab.TODAY, TeacherNavLogic.tabForExtra(null))
        assertEquals(TeacherTab.TODAY, TeacherNavLogic.tabForExtra(""))
        assertEquals(TeacherTab.TODAY, TeacherNavLogic.tabForExtra("SCHEDULE"))
        assertEquals(TeacherTab.TODAY, TeacherNavLogic.tabForExtra("garbage"))
    }

    @Test
    fun `case and whitespace tolerant`() {
        assertEquals(TeacherTab.WEEK, TeacherNavLogic.tabForExtra("WEEK"))
        assertEquals(TeacherTab.MORE, TeacherNavLogic.tabForExtra("  More "))
    }
}
