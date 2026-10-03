package com.politex.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

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

    /** X9: веса по умолчанию (пустой weightsJson) — Обычн 25, Практ 25, Контр 50, Экз 100. Позиционно к [DEFAULT_PRIORITY]. */
    val DEFAULT_WEIGHTS: List<Int> = listOf(25, 25, 50, 100)

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
     * V18: средневзвешенное по весам категорий (проценты из grade_config.weightsJson).
     * Σ(avg(категория) · w) / Σw по НЕпустым категориям с w > 0; LAB не участвует.
     * null, если нет ни одной непустой категории с положительным весом.
     */
    fun weightedAverage(
        byCategory: Map<String, List<Int>>,
        weights: Map<String, Int>,
    ): Double? {
        var acc = 0.0
        var total = 0
        for ((name, values) in byCategory) {
            if (name == GradeCategory.LAB.name || values.isEmpty()) continue
            val w = weights[name] ?: 0
            if (w <= 0) continue
            acc += values.average() * w
            total += w
        }
        return if (total == 0) null else acc / total
    }

    /**
     * X9: веса отключены — все оценки всех категорий считаются как одна группа:
     * одно среднее арифметическое. LAB не участвует; нет оценок = null.
     */
    fun plainAverage(byCategory: Map<String, List<Int>>): Double? {
        val all = byCategory.filterKeys { it != GradeCategory.LAB.name }.values.flatten()
        return if (all.isEmpty()) null else all.average()
    }

    /**
     * X8: стандартное школьное округление HALF_UP (0.4 → вниз, 0.5 → вверх).
     * BigDecimal.valueOf (через Double.toString), а не арифметика double —
     * иначе 8.25 в двоичной форме « slightly less» и half-up ломается.
     *
     * @param mode ".0" = 1 знак | ".00" = 2 знака | "целое" = 0 знаков.
     *             Legacy "0.1"/"0.5"/"1"/битый = 1 знак.
     */
    fun roundAvg(
        v: Double,
        mode: String,
    ): Double {
        val scale =
            when (mode) {
                ".00" -> 2
                "целое" -> 0
                else -> 1
            }
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).toDouble()
    }

    /** X9: позиция слайдера (0..1) → вес с шагом 25: 0/25/50/75/100. */
    fun snapWeight(fraction: Float): Int = (fraction * 4).roundToInt().coerceIn(0, 4) * 25

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

    /**
     * Показ: целые без «.0» («7»), дробные с одним знаком («8.3»), нет оценок — «—».
     * X8: mode ".00" — всегда 2 знака, "целое" — всегда без дробной части
     * (иначе настройка округления не видна глазу: 8.00 vs 8).
     */
    fun format(
        x: Double?,
        mode: String = ".0",
    ): String =
        when {
            x == null -> "—"
            mode == ".00" -> String.format(Locale.ROOT, "%.2f", roundAvg(x, mode))
            mode == "целое" -> roundAvg(x, mode).toInt().toString()
            round1(x) % 1.0 == 0.0 -> round1(x).toInt().toString()
            else -> String.format(Locale.ROOT, "%.1f", round1(x))
        }
}
