package com.politex.util

import java.util.Locale

/**
 * Ф4.2: категории оценок. `title` — подпись в UI, `name` — значение колонки category.
 */
enum class GradeCategory(
    val title: String,
) {
    REGULAR("Обычные"),
    PRACTICAL("Практические"),
    CONTROL("Контрольные"),
    LAB("Лабораторные"),
    EXAM("Экзамен"),
}

/**
 * Ф4.2 (G2): агрегатор среднего балла — ЧИСТАЯ функция, без Room/Android,
 * чтобы формула заказчика была покрыта JVM-тестами напрямую.
 *
 * Формула (парное сворачивание в порядке priority):
 *   acc = avg(категория priority[0], если есть оценки);
 *   далее для каждой следующей НЕПУСТОЙ категории c: acc = (acc + avg(c)) / 2.
 * Пустые категории пропускаются БЕЗ сдвига acc; LAB не участвует никогда,
 * даже если попал в priority. Дефолт [REGULAR, PRACTICAL, CONTROL, EXAM] даёт
 * формулу заказчика: (((обычные + практ) / 2 + контр) / 2 + экзамен) / 2 с пропусками.
 *
 * Округление только для показа: [round1] / [format].
 */
object GradeAggregator {
    /** Порядок категорий по умолчанию (MASTER-PLAN Ф4.2). LAB сюда не входит. */
    val DEFAULT_PRIORITY: List<String> =
        listOf(
            GradeCategory.REGULAR.name,
            GradeCategory.PRACTICAL.name,
            GradeCategory.CONTROL.name,
            GradeCategory.EXAM.name,
        )

    /**
     * @param byCategory значения оценок по именам категорий (category -> список value)
     * @param priority порядок сворачивания (имена категорий)
     * @return средний балл или null, если оценок нет вовсе
     */
    fun average(
        byCategory: Map<String, List<Int>>,
        priority: List<String> = DEFAULT_PRIORITY,
    ): Double? {
        var acc: Double? = null
        for (name in priority) {
            if (name == GradeCategory.LAB.name) continue // зачёт/незачёт в балл не входит
            val values = byCategory[name].orEmpty()
            if (values.isEmpty()) continue // пустая категория пропускается без сдвига acc
            val avg = values.average()
            acc = if (acc == null) avg else (acc + avg) / 2.0
        }
        return acc
    }

    /**
     * W22: дерево агрегации для отрисовки формулы (уровни СНИЗУ ВВЕРХ).
     * Уровень 0 — листья (категории priority в порядке сложения; пустые и LAB
     * выпадают — ровно как в [average]); каждый следующий уровень склеивает
     * первые два узла предыдущего в "A+B"; верхний уровень — единственный итог.
     * Разделитель узла "+" чистый: имена категорий его не содержат.
     * Схема БД не меняется: дерево РИСУЕТСЯ по плоскому priority-списку.
     *
     * @param priority порядок сворачивания (имена категорий)
     * @param nonEmpty категории, у которых есть оценки (дефолт — все из priority)
     */
    fun buildTreeLevels(
        priority: List<String>,
        nonEmpty: Set<String> = priority.toSet(),
    ): List<List<String>> {
        val leaves = priority.filter { it != GradeCategory.LAB.name && it in nonEmpty }
        if (leaves.isEmpty()) return emptyList()
        val levels = ArrayList<List<String>>(leaves.size)
        var current: List<String> = leaves
        levels += current
        while (current.size > 1) {
            current = listOf(current[0] + "+" + current[1]) + current.drop(2)
            levels += current
        }
        return levels
    }

    /** Округление для показа: 1 знак (спека Ф4.2). */
    fun round1(x: Double): Double = Math.round(x * 10) / 10.0

    /** Показ: целые без «.0» («7»), дробные с одним знаком («8.3»), нет оценок — «—». */
    fun format(x: Double?): String = when {
        x == null -> "—"
        round1(x) % 1.0 == 0.0 -> round1(x).toInt().toString()
        else -> String.format(Locale.ROOT, "%.1f", round1(x))
    }
}
