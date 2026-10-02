package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * M1: папка материалов. Принадлежит ПРЕДМЕТУ (subjectName — строка из MpkCurriculum
 * или своё название), внутри предмета папок может быть несколько.
 *
 * `isArchive` — единственная системная папка «Архив»: создаётся автоматически при первом
 * входе в «Материальную базу», в неё переносятся материалы кнопкой-архивом. Отдельная
 * таблица/предмет не нужны — архив просто папка с subjectName = [ARCHIVE_SUBJECT].
 *
 * `sortOrder` — порядок папок внутри предмета (меньше = выше). Индекса нет: папок десятки,
 * сортировка в запросе дешевле индекса.
 *
 * ponytail: значения по умолчанию заданы в Kotlin, а не @ColumnInfo(defaultValue=...) —
 * иначе миграция обязана повторять DEFAULT-выражения побайтово. Колонки NOT NULL,
 * вставка всегда через Room, «старых строк без значения» не бывает.
 */
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
