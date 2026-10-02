package com.politex.data.local.entity

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
    val priorityJson: String
)
