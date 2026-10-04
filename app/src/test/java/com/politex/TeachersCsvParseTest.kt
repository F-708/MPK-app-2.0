package com.politex

import com.politex.data.repository.Teacher
import com.politex.data.repository.TeachersRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TeachersCsvParseTest {
    private val teachers: List<Teacher> =
        TeachersRepository(RuntimeEnvironment.getApplication()).loadTeachers()

    private fun parseOne(line: String): Teacher {
        val p = line.split(';')

        fun cell(i: Int): String {
            val v = p.getOrElse(i) { "" }.trim()
            return if (v.isEmpty() || v == "—" || v == "–" || v == "-") "" else v
        }
        return Teacher(
            name = cell(0),
            position = cell(1),
            subjects = cell(2),
            room = cell(3),
            phone = cell(4),
            email = cell(5),
            photo = cell(6),
            department = cell(7),
            experience = cell(8),
            category = cell(9),
        )
    }

    @Test
    fun `ячейка прочерк становится пустой в room phone email`() {
        val t = parseOne("Иванов И.И.;Преподаватель;Дисциплины;—;—;—;;;ЦК;5 лет;высшая")
        assertEquals("", t.room)
        assertEquals("", t.phone)
        assertEquals("", t.email)
    }

    @Test
    fun `телефон с дефисом внутри сохраняется как есть`() {
        val t = parseOne("Шатько А.В.;Директор;Управление;229;338-33-42;a@b.by;;;15 лет;Высшая")
        assertEquals("338-33-42", t.phone)
        assertEquals("229", t.room)
        assertEquals("a@b.by", t.email)
    }

    @Test
    fun `прочерк в реальном CSV не доходит до room phone email`() {
        assertTrue(teachers.isNotEmpty())
        for (t in teachers) {
            assertFalse("room содержит прочерк у ${t.name}", t.room == "—")
            assertFalse("phone содержит прочерк у ${t.name}", t.phone == "—")
            assertFalse("email содержит прочерк у ${t.name}", t.email == "—")
        }
    }

    @Test
    fun `все строки реального CSV имеют 10 полей`() {
        val lines =
            RuntimeEnvironment
                .getApplication()
                .assets
                .open("teachers.csv")
                .bufferedReader(Charsets.UTF_8)
                .readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.isNotEmpty())
        for (l in lines) {
            assertEquals("строка не из 10 полей: $l", 10, l.split(';').size)
        }
    }

    @Test
    fun `строки 48 и 93 дают корректные имя и должность после фикса данных`() {
        val galimova = teachers.first { it.name.startsWith("Галимова") }
        assertEquals("Преподаватель", galimova.position)
        assertEquals("ЦК физической культуры и здоровья", galimova.department)

        val bachilo = teachers.first { it.name.startsWith("Бачило") }
        assertEquals("Преподаватель", bachilo.position)
        assertEquals("ЦК городского электрического транспорта и электроприводов", bachilo.department)
    }

    @Test
    fun `фамилии не теряются нормализацией`() {
        assertTrue(teachers.all { it.name.isNotBlank() })
        assertTrue(teachers.any { it.name.startsWith("Шатько") })
    }
}
