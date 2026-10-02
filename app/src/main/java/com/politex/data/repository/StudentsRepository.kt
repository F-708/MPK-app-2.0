package com.politex.data.repository

import android.content.Context
import com.politex.util.GroupCatalog
import com.politex.util.MpkCurriculum

data class Student(
    val fullName: String,
    val group: String,
    val course: String,
    val specialtyCode: String,
    val specialty: String,
    val funding: String,
    val dormitory: String,
    val curator: String,
    val room: String,

    val courseMismatch: Boolean = false
)

/**
 * База учащихся (только flavor admin, файл app/src/admin/assets/students.csv).
 *
 * O8: источник — полная выгрузка на 1703 строки, 62 группы, 18 колонок,
 * разделитель «;», UTF-8 с BOM. Колонки читаются ПО ЗАГОЛОВКУ, а не по индексам:
 * порядок и набор полей выгрузки меняются от выгрузки к выгрузке
 * (в новой добавлены Фамилия/Имя/Отчество, Когорта, Год набора,
 * Базовое образование, Студент-куратор, Кабинет). Кавычки снимаются,
 * поэтому строку нельзя резать по «;» без учёта обёртки.
 */
class StudentsRepository(private val context: Context) {

    /**
     * E2/ARC-03: 800 КБ CSV парсились на main-thread внутри remember{} (ANR-риск на
     * admin-сборке). Кэш один на процесс (приём TeachersRepository Б13), а UI-сайты
     * читают через produceState+IO.
     */
    fun loadStudents(): List<Student> = cached ?: try {
        parse(
            context.assets.open("students.csv").bufferedReader(Charsets.UTF_8).readLines(),
            knownGroups = GroupCatalog.load(context).all().toSet()
        ).also { cached = it }
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        @Volatile
        private var cached: List<Student>? = null
    }

    /** O8: виден тестам — разбор выгрузки без чтения ассета флейвора admin. */
    internal fun parse(lines: List<String>, knownGroups: Set<String>): List<Student> {
        val header = lines.firstOrNull()?.let { splitRow(stripBom(it)) } ?: return emptyList()

        // Заголовок ищется с начала строки, поэтому BOM уже снят и «№» не мешает.
        val idxName = header.indexOfFirst { it.startsWith("ФИО") }
        val idxGroup = header.indexOf("Группа")
        val idxCourse = header.indexOf("Курс")
        val idxCode = header.indexOf("Код специальности")
        val idxSpecialty = header.indexOf("Специальность")
        val idxFunding = header.indexOf("Основа обучения")
        val idxDorm = header.indexOf("Общежитие")
        val idxCurator = header.indexOfFirst { it.startsWith("Куратор") }
        val idxRoom = header.indexOfFirst { it.startsWith("Кабинет") }

        if (idxName < 0 || idxGroup < 0 || idxCourse < 0) return emptyList()

        return lines.drop(1)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val p = splitRow(line)
                fun cell(i: Int) = p.getOrElse(i) { "" }.trim()

                val group = cell(idxGroup)
                val course = cell(idxCourse).filter { it.isDigit() }

                val letter = group.lastOrNull()
                val maxCourse = letter
                    ?.let { MpkCurriculum.getSpecialty(it) }
                    ?.subjectsByCourse?.keys?.maxOrNull()
                Student(
                    fullName = cell(idxName),
                    group = group,
                    course = course,
                    specialtyCode = cell(idxCode),
                    specialty = cell(idxSpecialty),
                    funding = cell(idxFunding),
                    dormitory = cell(idxDorm),
                    curator = cell(idxCurator),
                    room = cell(idxRoom),
                    // O8.4: группа есть в каталоге групп колледжа → она реальна, значит
                    // учебный план MpkCurriculum просто отстал от неё (новые группы
                    // 41Д/41М/42Д/42М: у специальностей Д и М в плане нет 4 курса).
                    // Без этой поправки 125 студентов получают ложное предупреждение
                    // «группы нет в расписании».
                    courseMismatch = group !in knownGroups && maxCourse != null &&
                        (course.toIntOrNull() ?: 0) > maxCourse
                )
            }
            .filter { it.fullName.isNotBlank() }
    }

    /**
     * Разбор строки CSV. Разделитель «;», все значения в двойных кавычках,
     * кавычки внутри значения удваиваются. Простой split(';') для этой выгрузки
     * даёт тот же результат (в данных нет ни «;», ни кавычек внутри значений),
     * но он развалится на первой же ФИО с кавычкой — поэтому состояние кавычек
     * отслеживается явно.
     * ponytail: без экранирования обратным слэшем и без многострочных значений —
     * добавить, если выгрузка начнёт их содержать.
     */
    private fun splitRow(line: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                // Удвоенная кавычка внутри значения — это литеральная кавычка, а не конец поля.
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ';' && !inQuotes -> {
                    cells.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        cells.add(sb.toString())
        return cells
    }

    /** BOM в начале файла иначе прилипнет к имени первой колонки и сломает поиск по заголовку. */
    private fun stripBom(s: String): String =
        if (s.firstOrNull() == '﻿') s.substring(1) else s
}