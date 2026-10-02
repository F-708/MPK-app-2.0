package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M2: закладка в PDF-материале (страница + подпись). Отдельная таблица, а не JSON в колонке
 * материала: закладки добавляются/удаляются по одной из просмотрщика, и список закладок
 * читается в top-bar — SQL-запрос проще и безопаснее ручного парсинга строки.
 *
 * Индекс по `itemId`: getForItem фильтрует именно по нему.
 */
@Entity(
    tableName = "material_bookmarks",
    indices = [Index("itemId")],
)
data class MaterialBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val page: Int,
    val label: String = "",
)
