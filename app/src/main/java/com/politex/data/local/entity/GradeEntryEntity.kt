package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ф4.2 (G1): одна оценка студента. `category` — строка из GradeCategory
 * (REGULAR/PRACTICAL/CONTROL/LAB/EXAM):
 * - LAB хранит 0 = незачёт / 1 = зачёт (UI — тумблер, в агрегацию НЕ входит);
 * - EXAM — не более одной активной записи на предмет (upsert через GradeDao);
 * - `label` — подпись работы («Практ. №3»), пустая для быстрых оценок;
 * - `dateIso` — дата оценки, yyyy-MM-dd (конвенция дат проекта).
 *
 * Индекса на subjectName нет: строк сотни (по числу оценок за годы учёбы),
 * фильтр по предмету дешевле индекса.
 *
 * ponytail: значения по умолчанию в Kotlin, а не @ColumnInfo(defaultValue=...) —
 * иначе MIGRATION_5_6 обязана повторять DEFAULT-выражения побайтово (приём из material_*).
 */
@Entity(tableName = "grade_entries")
data class GradeEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectName: String,
    val category: String,
    val value: Int,
    val label: String = "",
    val dateIso: String,
    val createdAt: Long = System.currentTimeMillis(),
)
