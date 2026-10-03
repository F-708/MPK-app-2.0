package com.politex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ф4.2 (G1): порядок категорий для агрегации среднего балла ПРЕДМЕТА.
 * `priorityJson` — JSON-массив строк-категорий, например
 * ["REGULAR","PRACTICAL","CONTROL","EXAM"]; LAB в агрегацию не входит никогда.
 * Нет строки для предмета = используется дефолт GradeAggregator.DEFAULT_PRIORITY.
 */
@Entity(tableName = "grade_config")
data class GradeConfigEntity(
    @PrimaryKey
    val subjectName: String,
    val priorityJson: String,
    /**
     * V18: веса категорий в процентах — JSON-массив [обычн, практ, контр, экз]
     * (LAB не хранится). Пусто = дефолт все по 25. Слайдеры настроек предмета.
     */
    @ColumnInfo(defaultValue = "''")
    val weightsJson: String = "",
    /** V18: шаг округления среднего: "0.1" | "0.5" | "1". */
    @ColumnInfo(defaultValue = "'0.1'")
    val roundingMode: String = "0.1",
    /** V18: 1 = округлять всегда вверх («в пользу студента»). */
    @ColumnInfo(defaultValue = "0")
    val roundUp: Boolean = false,
)
