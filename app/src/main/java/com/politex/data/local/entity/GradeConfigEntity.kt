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
     * (LAB не хранится). X9: пусто = GradeAggregator.DEFAULT_WEIGHTS (25/25/50/100).
     */
    @ColumnInfo(defaultValue = "''")
    val weightsJson: String = "",
    /** X8: режим округления среднего: ".0" (1 знак) | ".00" (2 знака) | "целое". Legacy "0.1"/"0.5"/"1" = ".0". */
    @ColumnInfo(defaultValue = "'0.1'")
    val roundingMode: String = "0.1",
    /** X8: НЕ используется (standard HALF_UP), колонка оставлена во избежание лишней миграции. */
    @ColumnInfo(defaultValue = "0")
    val roundUp: Boolean = false,
    /** X9: 0 = веса предмета отключены (средний = plainAverage), 1 = включены. */
    @ColumnInfo(defaultValue = "1")
    val weightsEnabled: Boolean = true,
)
