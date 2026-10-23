package com.politex

import com.politex.data.repository.StudentsRepository
import com.politex.util.GroupCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StudentsCsvParseTest {

    private val students =
        StudentsRepository(RuntimeEnvironment.getApplication()).parse(
            lines =
            javaClass.classLoader!!
                .getResourceAsStream("students_fixture.csv")!!
                .bufferedReader(Charsets.UTF_8)
                .readLines(),
            knownGroups = GroupCatalog.load(RuntimeEnvironment.getApplication()).all().toSet(),
        )

    @Test
    fun `заголовок не попадает в список студентов`() {
        assertEquals(4, students.size)
        assertFalse(students.any { it.fullName.contains("ФИО") })
    }

    @Test
    fun `колонки читаются по заголовку а не по индексам старого формата`() {
        val s = students.first { it.fullName.startsWith("Примерова") }
        assertEquals("Примерова Анна Сергеевна", s.fullName)
        assertEquals("43Э", s.group)
        assertEquals("4", s.course)
        assertEquals("5-04-0712-01", s.specialtyCode)
        assertEquals("Монтаж и эксплуатация электрооборудования", s.specialty)
        assertEquals("Платное", s.funding)
        assertEquals("Нет", s.dormitory)

        assertEquals("", s.curator)
        assertEquals("", s.room)
    }

    @Test
    fun `курс берётся цифрами из значения вида 4 курс`() {
        assertTrue(students.all { it.course == "4" })
    }

    @Test
    fun `кавычки внутри значения снимаются а не рвут строку`() {
        val s = students.first { it.fullName.startsWith("Иванов") }
        assertEquals("Иванов \"Тест\" Иван Иванович", s.fullName)
        assertEquals("43О", s.group)
        assertEquals("5-04-0715-05", s.specialtyCode)
    }

    @Test
    fun `группа из каталога не помечается как несоответствие курса`() {
        assertTrue(students.none { it.courseMismatch })
    }
}
