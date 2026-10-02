package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.data.local.entity.MaterialItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * M1: DAO материальной базы. Только метаданные — байты файлов живут в filesDir
 * (util/MaterialImporter), поэтому `deleteItem` ОБЯЗАТЕЛЬНО сопровождается удалением файла
 * на стороне вызывающего (UI делает это в одном coroutine-блоке).
 *
 * `getSubjects` — список предметов, в которых уже есть папки: корневой экран «Материальной
 * базы» строится секциями по нему, а не по всему справочнику MpkCurriculum (иначе студент
 * 4 курса видит 60 пустых предметов).
 */
@Dao
interface MaterialDao {

    // ── Папки ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM material_folders ORDER BY sortOrder ASC, id ASC")
    fun getAllFolders(): Flow<List<MaterialFolderEntity>>

    @Query("SELECT DISTINCT subjectName FROM material_folders ORDER BY subjectName ASC")
    fun getSubjects(): Flow<List<String>>

    @Query("SELECT * FROM material_folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): MaterialFolderEntity?

    @Query("SELECT * FROM material_folders WHERE isArchive = 1 LIMIT 1")
    suspend fun getArchiveFolder(): MaterialFolderEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM material_folders WHERE subjectName = :subjectName")
    suspend fun nextFolderSortOrder(subjectName: String): Int

    @Insert
    suspend fun insertFolder(folder: MaterialFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: MaterialFolderEntity)

    @Query("DELETE FROM material_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    // ── Материалы ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM material_items ORDER BY sortOrder ASC, id ASC")
    fun getAllItems(): Flow<List<MaterialItemEntity>>

    @Query("SELECT * FROM material_items WHERE folderId = :folderId ORDER BY sortOrder ASC, id ASC")
    fun getForFolder(folderId: Long): Flow<List<MaterialItemEntity>>

    /** Синхронный снимок папки — нужен при удалении папки (список файлов на стирание). */
    @Query("SELECT * FROM material_items WHERE folderId = :folderId ORDER BY sortOrder ASC, id ASC")
    suspend fun getForFolderOnce(folderId: Long): List<MaterialItemEntity>

    @Query("SELECT * FROM material_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): MaterialItemEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM material_items WHERE folderId = :folderId")
    suspend fun nextItemSortOrder(folderId: Long): Int

    @Insert
    suspend fun insertItem(item: MaterialItemEntity): Long

    @Update
    suspend fun updateItem(item: MaterialItemEntity)

    @Query("DELETE FROM material_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("DELETE FROM material_items WHERE folderId = :folderId")
    suspend fun deleteItemsOfFolder(folderId: Long)

    @Query("UPDATE material_items SET title = :title WHERE id = :itemId")
    suspend fun renameItem(itemId: Long, title: String)

    @Query("UPDATE material_items SET folderId = :folderId, sortOrder = :sortOrder WHERE id = :itemId")
    suspend fun moveItemToFolder(itemId: Long, folderId: Long, sortOrder: Int)

    @Query("UPDATE material_items SET lastPage = :page WHERE id = :itemId")
    suspend fun setLastPage(itemId: Long, page: Int)

    @Query("UPDATE material_items SET rotationDeg = :deg WHERE id = :itemId")
    suspend fun setRotationDeg(itemId: Long, deg: Int)

    /** M4: порядок страниц набора изображений — список путей обратно в `uriPath`. */
    @Query("UPDATE material_items SET uriPath = :uriPath WHERE id = :itemId")
    suspend fun setImagePaths(itemId: Long, uriPath: String)

    // ── Переупорядочивание ───────────────────────────────────────────────────

    @Query("UPDATE material_items SET sortOrder = :sortOrder WHERE id = :itemId")
    suspend fun updateSortOrder(itemId: Long, sortOrder: Int)

    /**
     * M4: перестановка соседних материалов (кнопки-стрелки в строке материала).
     * sortOrder внутри папки уникален (вставка всегда берёт MAX+1), поэтому обмен
     * значениями корректен. Транзакция — иначе читатель увидит две строки с одним порядком.
     *
     * ponytail: обмен двух соседей вместо drag&drop-переноса на произвольную позицию;
     * если понадобится drag — тот же sortOrder, только пересчёт всего хвоста папки.
     */
    @Transaction
    suspend fun swapSortOrder(itemIdA: Long, itemIdB: Long) {
        val a = getItemById(itemIdA) ?: return
        val b = getItemById(itemIdB) ?: return
        if (a.sortOrder == b.sortOrder) return
        updateSortOrder(itemIdA, b.sortOrder)
        updateSortOrder(itemIdB, a.sortOrder)
    }

    // ── Закладки ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM material_bookmarks WHERE itemId = :itemId ORDER BY page ASC, id ASC")
    suspend fun getBookmarksForItem(itemId: Long): List<MaterialBookmarkEntity>

    @Query("SELECT * FROM material_bookmarks WHERE itemId = :itemId ORDER BY page ASC, id ASC")
    fun getBookmarksFlow(itemId: Long): Flow<List<MaterialBookmarkEntity>>

    @Insert
    suspend fun insertBookmark(bookmark: MaterialBookmarkEntity): Long

    @Query("DELETE FROM material_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)

    /** Закладки удаляются вместе с материалом — иначе остаются строки-сироты. */
    @Query("DELETE FROM material_bookmarks WHERE itemId = :itemId")
    suspend fun deleteBookmarksForItem(itemId: Long)

    /**
     * M2: удаление материала целиком — записи + его закладки одной транзакцией.
     * Файл стирает вызывающий (у DAO нет Context).
     */
    @Transaction
    suspend fun deleteItemWithBookmarks(itemId: Long) {
        deleteBookmarksForItem(itemId)
        deleteItemById(itemId)
    }
}
