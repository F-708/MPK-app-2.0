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

/**
 * O8: разбор выгрузки учащихся в расширенном формате (18 колонок, «;»,
 * все значения в кавычках, UTF-8 с BOM).
 *
 * Фикстура `students_fixture.csv` собрана из реальных строк выгрузки
 * `students_mpk_full_1703 (2).csv` (41Д, 41С, 42М) плюс строка с удвоенными
 * кавычками внутри ФИО.
 */
@RunWith(RobolectricTestRunner::class)
class StudentsCsvParseTest {
    // Тест гоняется на флейворе standard, где ассета students.csv нет (он только в admin) —
    // поэтому разбираем фикстуру из test/resources, а не ассет сборки.
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
        // В файле 4 строки данных + заголовок
        assertEquals(4, students.size)
        assertFalse(students.any { it.fullName.contains("ФИО") })
    }

    @Test
    fun `колонки читаются по заголовку а не по индексам старого формата`() {
        val s = students.first { it.fullName.startsWith("Абражевич") }
        assertEquals("Абражевич Елизавета Владиславовна", s.fullName)
        assertEquals("41Д", s.group)
        assertEquals("4", s.course)
        assertEquals("5-04-0414-01", s.specialtyCode)
        assertEquals("Документационное обеспечение управления", s.specialty)
        assertEquals("Платное", s.funding)
        assertEquals("Нет", s.dormitory)
        // «Куратор» и «Студент-куратор» — разные колонки; у этой строки обе пусты,
        // старая (индексная) версия парсера положила бы сюда «Кабинет» или «Источник приказа»
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
        assertEquals("42М", s.group)
        assertEquals("5-04-0412-01", s.specialtyCode)
    }

    @Test
    fun `группа из каталога не помечается как несоответствие курса`() {
        // 41Д/42М — новые группы 4 курса; у специальностей Д и М в MpkCurriculum
        // нет 4 курса, но группа реальна (есть в group_codes.csv) → предупреждения быть не должно
        assertTrue(students.none { it.courseMismatch })
    }
}
