package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * M1: папка материалов. W11: папки ПОЛНОСТЬЮ выпилены из UI — материал принадлежит предмету
 * (MaterialItemEntity.subjectName). Таблица `material_folders` и эта сущность ОСТАВЛЕНЫ в БД
 * (миграция v6→v7 не дропает таблицу — безопаснее), сущность остаётся в @Database, чтобы Room
 * продолжал валидировать неизменённую схему. Никто больше не создаёт и не читает папки.
 */
@Deprecated("W11: папки удалены из UI; таблица оставлена в БД ради безопасной миграции")
@Entity(tableName = "material_folders")
data class MaterialFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectName: String,
    val name: String,
    val isArchive: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** Предмет-владелец системной папки «Архив» (M3). */
        const val ARCHIVE_SUBJECT = "Архив"

        /** Имя системной папки «Архив». */
        const val ARCHIVE_NAME = "Архив"
    }
}
