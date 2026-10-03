package com.politex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V9: штрихи рисования поверх страницы материала (PDF/изображения).
 * Одна строка = одна страница: частичное сохранение (правка страницы 7
 * не переписывает JSON страницы 1). Unique (itemId, pageIndex) — upsert REPLACE.
 *
 * `strokesJson` — {"v":1,"strokes":[{"c":<ARGB int>,"w":<dp>,"pts":[x1,y1,x2,y2,…]}]},
 * точки нормализованы 0..1 к боксу страницы (не зависят от размера рендера/зума).
 */
@Entity(
    tableName = "material_annotations",
    indices = [Index(value = ["itemId", "pageIndex"], unique = true)],
)
data class MaterialAnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val pageIndex: Int,
    val strokesJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
