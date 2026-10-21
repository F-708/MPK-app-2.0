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
class StudentsDatabaseTest {

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
    fun `база содержит 1693 текущих учащихся в 59 группах`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        assertEquals(1693, students.size)
        assertEquals(59, students.map { it.group }.distinct().size)
    }

    @Test
    fun `архивные выпускники в базу не попадают`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        assertTrue(students.isNotEmpty())
        assertEquals(0, lines.count { it.contains("Архив (выпускники") })
    }

    @Test
    fun `43О и 43Э присутствуют по 30 учащихся с корректными полями`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        val g43o = students.filter { it.group == "43О" }
        val g43e = students.filter { it.group == "43Э" }
        assertEquals(30, g43o.size)
        assertEquals(30, g43e.size)
        assertTrue(g43o.all { it.course == "4" && it.specialtyCode == "5-04-0715-05" })
        assertTrue(g43e.all { it.course == "4" && it.specialtyCode == "5-04-0712-01" })
        assertTrue(g43o.all { it.curator == "Березовская Нелли Викторовна" && it.room == "139" })
        assertTrue(g43e.all { it.curator == "Савицкая Ольга Владимировна" && it.room == "343" })
        assertTrue(g43o.any { it.fullName == "Автушко Дмитрий Владимирович" })
        assertTrue(g43e.any { it.fullName == "Агиевич Артём Владимирович" })
    }

    @Test
    fun `выбывшие группы 12Т 13Д 13М 13О 13Э 33М 41Д 41М 42Д 42М отсутствуют в базе`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        val gone = listOf("12Т", "13Д", "13М", "13О", "13Э", "33М", "41Д", "41М", "42Д", "42М")
        assertFalse(students.any { it.group in gone })
    }

    @Test
    fun `переведённые на курс выше группы 23Д 23М 23О содержат по 26 учащихся`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        val counts = students.groupingBy { it.group }.eachCount()
        assertEquals(26, counts["23Д"])
        assertEquals(26, counts["23М"])
        assertEquals(26, counts["23О"])
        assertTrue(students.filter { it.group in listOf("23Д", "23М", "23О") }.all { it.course == "2" })
    }

    @Test
    fun `курс каждой записи согласован с первой цифрой кода группы`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        assertTrue(students.all { it.course == it.group.first().toString() })
    }

    @Test
    fun `контрольные счётчики групп совпадают с полной выгрузкой`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        val counts = students.groupingBy { it.group }.eachCount()
        assertEquals(26, counts["11А"])
        assertEquals(32, counts["31О"])
        assertEquals(30, counts["41А"])
        assertEquals(30, counts["42О"])
        assertEquals(25, counts["42Н"])
        assertEquals(30, counts["42Т"])
        assertEquals(26, counts["12Э"])
        assertEquals(51, counts["22Т"])
        assertEquals(51, counts["23Э"])
    }

    @Test
    fun `ни у одного учащегося нет несоответствия курса`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        assertFalse(students.any { it.courseMismatch })
    }

    @Test
    fun `схема колонок - 18 полей`() {
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
    fun `нумерация записей сквозная от 1 до 1693`() {
        assumeTrue("students.csv есть только в admin-сборке", file != null)

        lines.drop(1).forEachIndexed { index, line ->
            val number = Csv.splitRow(line).first()
            assertEquals("строка ${index + 2}", "${index + 1}", number)
        }
    }
}
