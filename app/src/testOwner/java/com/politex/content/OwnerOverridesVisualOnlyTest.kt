package com.politex.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.entity.LessonEntity
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.TeacherRowMatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OwnerOverridesVisualOnlyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val original =
        Teacher(
            name = "Иванова Анна Петровна",
            position = "Преподаватель",
            subjects = "Математика",
            phone = "111-111",
        )

    @Before
    fun setUp() {
        OwnerOverridesStore.clearAll(context)
    }

    @After
    fun tearDown() {
        OwnerOverridesStore.clearAll(context)
    }

    private fun renameTo(newFio: String): Teacher {
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = OwnerOverridesStore.keyOf(original.name),
            fio = newFio,
            position = original.position,
            phone = original.phone,
            subjects = original.subjects,
        )
        return OwnerOverridesStore.applyTeacherOverrides(context, listOf(original)).single()
    }

    @Test
    fun `переименование меняет только отображаемое имя и сохраняет исходный ключ`() {
        val renamed = renameTo("Сидорова Анна Петровна")
        assertEquals("Сидорова Анна Петровна", renamed.name)
        assertEquals("Иванова Анна Петровна", renamed.matchName)
        assertEquals("Иванова Анна Петровна", renamed.sourceName)
    }

    @Test
    fun `матчинг строки расписания ищет по старой фамилии после переименования`() {
        val renamed = renameTo("Сидорова Анна Петровна")
        assertEquals(
            TeacherRowMatch.YES,
            TeacherInsights.matchRowToTeacher("Иванова А.П.", renamed, listOf(renamed)),
        )
        assertEquals(
            TeacherRowMatch.NO,
            TeacherInsights.matchRowToTeacher("Сидорова А.П.", renamed, listOf(renamed)),
        )
    }

    @Test
    fun `матчинг преподавателей с одной фамилией идёт по исходным ключам`() {
        val rival = Teacher(name = "Иванова Мария Сергеевна")
        val renamed = renameTo("Сидорова Анна Петровна")
        val all = listOf(renamed, rival)
        assertEquals(
            TeacherRowMatch.YES,
            TeacherInsights.matchRowToTeacher("Иванова А.П.", renamed, all),
        )
        assertEquals(
            TeacherRowMatch.YES,
            TeacherInsights.matchRowToTeacher("Иванова М.С.", rival, all),
        )
    }

    @Test
    fun `инсайты расписания находят переименованного по исходному ФИО и отдают факты по новому`() {
        val renamed = renameTo("Сидорова Анна Петровна")
        val lesson =
            LessonEntity(
                groupName = "11А",
                dayOfWeek = 1,
                lessonNumber = 1,
                timeStart = "08:00",
                timeEnd = "08:45",
                subjectRaw = "Математика",
                roomFirst = "101",
                teacherFirst = "Иванова А.П.",
                dateString = "12.10.2026",
            )
        val now =
            java.text
                .SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT)
                .parse("12.10.2026")!!
                .time
        val insights = TeacherInsights(listOf(renamed), listOf(lesson), nowMillis = now)
        assertEquals(renamed, insights.matchTeacher("Иванова А.П."))
        assertNull(insights.matchTeacher("Сидорова А.П."))
        assertEquals(1, insights.lessonCount(renamed.name))
        assertTrue(insights.isActive(renamed.name))
        assertEquals(listOf("101"), insights.rooms(renamed.name))
    }

    @Test
    fun `правка без переименования оставляет ключ пустым и матчинг не меняется`() {
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = OwnerOverridesStore.keyOf(original.name),
            fio = original.name,
            position = "Старший преподаватель",
        )
        val merged = OwnerOverridesStore.applyTeacherOverrides(context, listOf(original)).single()
        assertEquals("Старший преподаватель", merged.position)
        assertEquals(original.name, merged.name)
        assertEquals(original.name, merged.matchName)
        assertEquals(
            TeacherRowMatch.YES,
            TeacherInsights.matchRowToTeacher("Иванова А.П.", merged, listOf(merged)),
        )
    }
}
