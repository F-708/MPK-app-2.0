package com.politex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * M1/M4: MaterialDao — то, что ломается незаметно и сразу у всех: порядок материалов
 * (reorder), переименование, перенос в Архив, каскадное удаление закладок вместе с материалом.
 *
 * Порядок хранится в `sortOrder`, поэтому «переставить» = обменять значения у соседей;
 * тест проверяет именно РЕЗУЛЬТИРУЮЩУЮ выдачу getForFolder, а не сами числа — иначе тест
 * зелёный даже тогда, когда список в экране остался прежним.
 */
@RunWith(RobolectricTestRunner::class)
class MaterialDaoTest {
    private lateinit var db: MpkDatabase
    private lateinit var dao: MaterialDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.materialDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newFolder(
        subject: String = "ТОЭ",
        name: String = "Лекции",
        isArchive: Boolean = false,
    ): Long = runBlocking {
        dao.insertFolder(
            MaterialFolderEntity(
                subjectName = subject,
                name = name,
                isArchive = isArchive,
                sortOrder = dao.nextFolderSortOrder(subject),
            ),
        )
    }

    private fun newItem(
        folderId: Long,
        title: String,
        kind: MaterialKind = MaterialKind.FILE,
        ext: String = "pdf",
    ): Long = runBlocking {
        dao.insertItem(
            MaterialItemEntity(
                folderId = folderId,
                kind = kind.name,
                title = title,
                fileName = "$title.$ext",
                ext = ext,
                uriPath = "/data/files/materials/$title.$ext",
                sortOrder = dao.nextItemSortOrder(folderId),
            ),
        )
    }

    private fun titlesIn(folderId: Long): List<String> = runBlocking {
        dao.getForFolder(folderId).first().map { it.title }
    }

    @Test
    fun `вставка ставит материал в конец папки`() = runBlocking {
        val folder = newFolder()
        newItem(folder, "Лекция 1")
        newItem(folder, "Лекция 2")
        newItem(folder, "Лекция 3")
        assertEquals(listOf("Лекция 1", "Лекция 2", "Лекция 3"), titlesIn(folder))
    }

    @Test
    fun `переименование меняет только название`() = runBlocking {
        val folder = newFolder()
        val id = newItem(folder, "IMG_2049")

        dao.renameItem(id, "Лекция 3 — цепи")
        val item = dao.getItemById(id)

        assertEquals("Лекция 3 — цепи", item?.title)
        assertEquals("имя файла остаётся оригинальным — по нему ищется копия на диске", "IMG_2049.pdf", item?.fileName)
        assertEquals("pdf", item?.ext)
    }

    @Test
    fun `reorder стрелкой вниз меняет материал местами с соседом`() = runBlocking {
        val folder = newFolder()
        val a = newItem(folder, "А")
        val b = newItem(folder, "Б")
        val c = newItem(folder, "В")

        dao.swapSortOrder(a, b)
        assertEquals(listOf("Б", "А", "В"), titlesIn(folder))

        dao.swapSortOrder(a, c)
        assertEquals(listOf("Б", "В", "А"), titlesIn(folder))
    }

    @Test
    fun `reorder соседних элементов обратим`() = runBlocking {
        val folder = newFolder()
        val a = newItem(folder, "А")
        val b = newItem(folder, "Б")

        dao.swapSortOrder(a, b)
        dao.swapSortOrder(a, b)
        assertEquals("два нажатия стрелки туда-обратно возвращают исходный порядок", listOf("А", "Б"), titlesIn(folder))
    }

    @Test
    fun `swap с неизвестным id не падает`() = runBlocking {
        val folder = newFolder()
        val a = newItem(folder, "А")
        dao.swapSortOrder(a, 9999L)
        dao.swapSortOrder(9999L, a)
        assertEquals(listOf("А"), titlesIn(folder))
    }

    @Test
    fun `перенос в архив убирает материал из исходной папки и ставит в конец архива`() = runBlocking {
        val folder = newFolder()
        val archive = newFolder(subject = MaterialFolderEntity.ARCHIVE_SUBJECT, name = "Архив", isArchive = true)
        newItem(archive, "Старое")
        val moved = newItem(folder, "Лекция 1")
        newItem(folder, "Лекция 2")

        dao.moveItemToFolder(moved, archive, dao.nextItemSortOrder(archive))

        assertEquals(listOf("Лекция 2"), titlesIn(folder))
        assertEquals("в архиве перенесённый материал оказывается последним", listOf("Старое", "Лекция 1"), titlesIn(archive))
        assertEquals(archive, dao.getItemById(moved)?.folderId)
        assertTrue("папка архива помечена флагом", dao.getFolderById(archive)?.isArchive == true)
        assertFalse("обычная папка архивом не считается", dao.getFolderById(folder)?.isArchive == true)
    }

    @Test
    fun `удаление материала уносит его закладки`() = runBlocking {
        val folder = newFolder()
        val id = newItem(folder, "Учебник")
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 12, label = "Формулы"))
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 40, label = "Вывод"))
        assertEquals(2, dao.getBookmarksForItem(id).size)

        dao.deleteItemWithBookmarks(id)

        assertNull("материал удалён", dao.getItemById(id))
        assertTrue("закладки-сироты удалены вместе с ним", dao.getBookmarksForItem(id).isEmpty())
        assertTrue(titlesIn(folder).isEmpty())
    }

    @Test
    fun `закладки сортируются по странице`() = runBlocking {
        val folder = newFolder()
        val id = newItem(folder, "Учебник")
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 40, label = "Поздняя"))
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 5, label = "Ранняя"))
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 20, label = "Средняя"))

        assertEquals(listOf(5, 20, 40), dao.getBookmarksForItem(id).map { it.page })

        val middle = dao.getBookmarksForItem(id).first { it.page == 20 }
        dao.deleteBookmarkById(middle.id)
        assertEquals(listOf(5, 40), dao.getBookmarksForItem(id).map { it.page })
    }

    @Test
    fun `getSubjects возвращает предметы без дублей`() = runBlocking {
        newFolder(subject = "ТОЭ", name = "Лекции")
        newFolder(subject = "ТОЭ", name = "Лабораторные")
        newFolder(subject = "Математика", name = "Методички")
        newFolder(subject = MaterialFolderEntity.ARCHIVE_SUBJECT, name = "Архив", isArchive = true)

        val subjects = dao.getSubjects().first()
        assertEquals(setOf("ТОЭ", "Математика", MaterialFolderEntity.ARCHIVE_SUBJECT), subjects.toSet())
        assertEquals("без дублей по предмету", 3, subjects.size)
    }

    @Test
    fun `архив находится отдельным запросом и не путается с обычными папками`() = runBlocking {
        val folder = newFolder(subject = "ТОЭ", name = "Лекции")
        val archive = newFolder(subject = MaterialFolderEntity.ARCHIVE_SUBJECT, name = "Архив", isArchive = true)

        assertEquals(archive, dao.getArchiveFolder()?.id)
        assertEquals("getAllFolders отдаёт и архив тоже", 2, dao.getAllFolders().first().size)
        assertEquals(
            folder,
            dao
                .getAllFolders()
                .first()
                .first { !it.isArchive }
                .id,
        )
    }

    @Test
    fun `состояние просмотрщика сохраняется в строке материала`() = runBlocking {
        val folder = newFolder()
        val id = newItem(folder, "Методичка", kind = MaterialKind.IMAGES, ext = "jpg")

        dao.setLastPage(id, 7)
        assertEquals(7, dao.getItemById(id)?.lastPage)

        dao.setRotationDeg(id, 270)
        assertEquals(270, dao.getItemById(id)?.rotationDeg)

        // порядок страниц набора живёт в uriPath: строки = страницы
        val paths = listOf("/files/materials/$id-0.jpg", "/files/materials/$id-1.jpg")
        dao.setImagePaths(id, paths.joinToString("\n"))
        assertEquals(paths, dao.getItemById(id)?.imagePaths)

        // перестановка страниц — тот же механизм, что и reorder стрелками
        dao.setImagePaths(id, paths.reversed().joinToString("\n"))
        assertEquals(paths.reversed(), dao.getItemById(id)?.imagePaths)
    }
}
