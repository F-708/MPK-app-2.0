package com.politex

import com.politex.data.repository.StudentsRepository
import com.politex.util.Csv
import com.politex.util.GroupCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StudentsMerge43ETest {

    private val file =
        sequenceOf(
            File("app/src/admin/assets/students.csv"),
            File("src/admin/assets/students.csv"),
        ).firstOrNull { it.exists() }

    private val lines: List<String> by lazy { file!!.readLines(Charsets.UTF_8) }

    private val students by lazy {
        StudentsRepository(RuntimeEnvironment.getApplication()).parse(
            lines = lines,
            knownGroups = GroupCatalog.load(RuntimeEnvironment.getApplication()).all().toSet(),
        )
    }

    @Test
    fun `после мерджа в базе 1733 записи и 63 группы`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        assertEquals(1733, students.size)
        assertEquals(63, students.map { it.group }.distinct().size)
    }

    @Test
    fun `43Э - ровно 30 новых записей с корректными полями`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        val group = students.filter { it.group == "43Э" }
        assertEquals(30, group.size)
        assertTrue(group.all { it.course == "4" })
        assertTrue(group.all { it.specialtyCode == "5-04-0712-01" })
        assertTrue(group.all { it.specialty == "Монтаж и эксплуатация электрооборудования" })
        assertTrue(group.all { it.funding == "Бюджет" })
        assertTrue(group.all { it.dormitory == "Нет" })
        assertTrue(group.all { it.curator == "Савицкая Ольга Владимировна" })
        assertTrue(group.all { it.room == "343" })
        assertTrue(group.any { it.fullName == "Агиевич Артём Владимирович" })
    }

    @Test
    fun `прочие группы не пострадали при мердже`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        val counts = students.groupingBy { it.group }.eachCount()
        assertEquals(26, counts["11А"])
        assertEquals(40, counts["41Д"])
        assertEquals(25, counts["42Э"])
        assertEquals(32, counts["31О"])
        assertEquals(32, counts["33Э"])
    }

    @Test
    fun `43Э не помечаются как несоответствие курса - у специальности 4 курса`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        assertFalse(students.filter { it.group == "43Э" }.any { it.courseMismatch })
    }

    @Test
    fun `схема колонок сохранена после мерджа`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        val header = Csv.splitRow(Csv.stripBom(lines.first()))
        assertEquals(
            listOf(
                "№", "ФИО", "Фамилия", "Имя", "Отчество", "Курс", "Группа",
                "Когорта (Актуальный/Архив)", "Год набора", "Базовое образование",
                "Код специальности", "Специальность", "Основа обучения", "Общежитие",
                "Куратор", "Студент-куратор", "Кабинет", "Источник приказа",
            ),
            header,
        )
    }

    @Test
    fun `нумерация записей сквозная от 1 до 1733`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        lines.drop(1).forEachIndexed { index, line ->
            val number = Csv.splitRow(line).first()
            assertEquals("строка ${index + 2}", "${index + 1}", number)
        }
    }
}
