package com.politex.util

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/**
 * M3: то, что выбрано в диалоге импорта и ещё не положено на диск.
 * SINGLE_FILE — один документ; IMAGES — набор изображений (один материал, несколько страниц);
 * TEXT — текст, набранный в приложении (байтов нет вообще).
 */
sealed interface PendingMaterial {
    data class SingleFile(
        val uri: Uri,
    ) : PendingMaterial

    data class Images(
        val uris: List<Uri>,
    ) : PendingMaterial

    data class Text(
        val text: String,
    ) : PendingMaterial
}

/**
 * M1/M3: хранение файлов материальной базы.
 *
 * Правило босса: в БД — только метаданные, байты копируются в `filesDir/materials/<itemId>.<ext>`.
 * Отсюда порядок импорта (иначе нечем назвать файл):
 *  1. INSERT строки → получаем `itemId`;
 *  2. копирование InputStream из SAF Uri в `filesDir/materials/<itemId>.<ext>` на Dispatchers.IO;
 *  3. UPDATE строки: путь, число страниц (PDF), извлечённый текст (TXT/DOCX) для поиска.
 * Любой сбой на шагах 2–3 → строка и уже записанные байты удаляются (см. [importMaterial]).
 *
 * Набор изображений — один материал, страницы лежат рядом: `<itemId>-0.jpg`, `<itemId>-1.jpg`, …
 * (порядок в `uriPath` = порядок страниц в просмотрщике).
 */
object MaterialImporter {
    /** Подкаталог filesDir, где живут все материалы. */
    const val STORAGE_DIR_NAME = "materials"

    /**
     * Сколько символов извлечённого текста кладём в `textContent`. Хватает и на учебник,
     * и на поиск; мегабайтный лог в БД тащить незачем — просмотрщик читает файл целиком.
     *
     * ponytail: обрезка по символам, а не «умный» индекс. Поднять/снять при переходе на FTS.
     */
    const val MAX_INDEXED_CHARS = 200_000

    /** Расширения, для которых при импорте извлекается текст (поиск + встроенный просмотр). */
    private val PLAIN_TEXT_EXT = setOf("txt", "md", "markdown", "csv", "log", "json", "xml", "rtf")

    /** Расширения изображений, которые SAF отдаёт по маске image (см. пикер набора изображений). */
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    fun storageDir(context: Context): File = File(context.filesDir, STORAGE_DIR_NAME).apply { if (!exists()) mkdirs() }

    /** Имя файла из SAF (DISPLAY_NAME), с откатом на lastPathSegment. */
    fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name.trim()
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "материал"
    }

    /** Расширение без точки, в нижнем регистре. Пустая строка, если имени с точкой нет. */
    fun extOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase().take(6)

    /** Имя без расширения — заголовок материала по умолчанию. */
    fun titleOf(fileName: String): String = fileName.substringBeforeLast('.', fileName).ifBlank { fileName }

    /** MIME → расширение, когда в имени файла точки нет (SAF такое отдаёт регулярно). */
    fun extFromMime(
        context: Context,
        uri: Uri,
    ): String = when (context.contentResolver.getType(uri)) {
        "application/pdf" -> "pdf"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/msword" -> "doc"
        "text/plain" -> "txt"
        "text/csv" -> "csv"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> ""
    }

    /** Скопировать байты SAF-Uri в хранилище материалов. Отменяемо, на Dispatchers.IO. */
    suspend fun copyToStorage(
        context: Context,
        uri: Uri,
        fileName: String,
    ): File = withContext(Dispatchers.IO) {
        val target = File(storageDir(context), fileName)
        val input =
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("Не удалось открыть файл (нет доступа к выбранному документу)")
        input.use { source ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
        target
    }

    /** Число страниц PDF (0, если файл не PDF или повреждён — просмотрщик тогда сам разберётся). */
    suspend fun pdfPageCount(file: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        }.getOrDefault(0)
    }

    /**
     * Прочитать текстовый файл. UTF-8 пробуется СТРОГО: русскоязычные .txt из колледжа сплошь
     * и рядом в windows-1251, и «прочитать как UTF-8» даёт кашу из U+FFFD — падаем на 1251.
     */
    suspend fun readTextFile(file: File): String = withContext(Dispatchers.IO) {
        runCatching { decodeSmart(file.readBytes()) }.getOrDefault("")
    }

    internal fun decodeSmart(bytes: ByteArray): String {
        runCatching {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
        }.getOrNull()?.let { return it.toString() }
        return runCatching {
            String(
                bytes,
                java.nio.charset.Charset
                    .forName("windows-1251"),
            )
        }.getOrDefault(String(bytes))
    }

    /** Текст DOCX для поиска (пустая строка, если файл не читается). */
    suspend fun readDocxText(file: File): String = withContext(Dispatchers.IO) {
        runCatching {
            DocxTextExtractor.toPlainText(
                file.inputStream().use { DocxTextExtractor.extract(it) },
            )
        }.getOrDefault("")
    }

    /** Удалить файлы материала с диска (вызывается вместе с DELETE из БД). */
    fun deleteStoredFiles(item: MaterialItemEntity) {
        if (item.uriPath.isBlank()) return
        item.uriPath.split('\n').forEach { path ->
            if (path.isNotBlank()) runCatching { File(path).delete() }
        }
    }

    /** «1,4 МБ» / «320 КБ» — подпись размера в строке материала. */
    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${max(1, bytes / 1024)} КБ"
        else -> String.format(java.util.Locale.ROOT, "%.1f МБ", bytes / (1024.0 * 1024.0))
    }

    /** Суммарный размер файлов материала (для набора изображений — всех страниц). */
    fun storedSize(item: MaterialItemEntity): Long {
        if (item.uriPath.isBlank()) return 0
        return item.uriPath.split('\n').sumOf { p -> if (p.isBlank()) 0 else runCatching { File(p).length() }.getOrDefault(0) }
    }

    /**
     * M3/W11: полный импорт выбранного материала в ПРЕДМЕТ. Возвращает id новой строки.
     *
     * Ошибка = исключение (IOException/SecurityException); вызывающий показывает сообщение.
     * Ровно один материал на вызов, кроме [PendingMaterial.Images] — там набор становится
     * ОДНОЙ строкой, иначе «лекция в 12 фото» рассыпается на 12 материалов в списке.
     */
    suspend fun importMaterial(
        context: Context,
        dao: MaterialDao,
        subjectName: String,
        pending: PendingMaterial,
    ): Long {
        val now = System.currentTimeMillis()

        return when (pending) {
            is PendingMaterial.Text -> {
                val text = pending.text.trim()
                require(text.isNotEmpty()) { "Пустой текст материала" }
                require(subjectName.isNotBlank()) { "Не выбран предмет" }
                val title =
                    text
                        .lineSequence()
                        .first { it.isNotBlank() }
                        .trim()
                        .take(60)
                        .ifBlank { "Заметка" }
                dao.insertItem(
                    MaterialItemEntity(
                        kind = MaterialKind.TEXT.name,
                        title = title,
                        ext = "txt",
                        subjectName = subjectName,
                        sortOrder = dao.nextItemSortOrder(subjectName),
                        textContent = text.take(MAX_INDEXED_CHARS),
                        createdAt = now,
                    ),
                )
            }

            is PendingMaterial.SingleFile -> {
                val name = queryDisplayName(context, pending.uri)
                val ext = extOf(name).ifBlank { extFromMime(context, pending.uri) }
                val id =
                    dao.insertItem(
                        MaterialItemEntity(
                            kind = MaterialKind.FILE.name,
                            title = titleOf(name),
                            fileName = name,
                            ext = ext,
                            subjectName = subjectName,
                            sortOrder = dao.nextItemSortOrder(subjectName),
                            createdAt = now,
                        ),
                    )
                finalizeFileImport(context, dao, id, pending.uri, ext, name)
            }

            is PendingMaterial.Images -> {
                require(pending.uris.isNotEmpty()) { "Не выбрано ни одного изображения" }
                require(subjectName.isNotBlank()) { "Не выбран предмет" }
                val firstName = queryDisplayName(context, pending.uris.first())
                val ext = extOf(firstName).ifBlank { "jpg" }.let { if (it in IMAGE_EXT) it else "jpg" }
                val title =
                    if (pending.uris.size == 1) {
                        titleOf(firstName)
                    } else {
                        "${titleOf(firstName)} (набор: ${pending.uris.size})"
                    }
                val id =
                    dao.insertItem(
                        MaterialItemEntity(
                            kind = MaterialKind.IMAGES.name,
                            title = title,
                            fileName = firstName,
                            ext = ext,
                            pageCount = pending.uris.size,
                            subjectName = subjectName,
                            sortOrder = dao.nextItemSortOrder(subjectName),
                            createdAt = now,
                        ),
                    )
                val copied = ArrayList<String>(pending.uris.size)
                try {
                    pending.uris.forEachIndexed { index, uri ->
                        val file = copyToStorage(context, uri, "$id-$index.$ext")
                        copied.add(file.absolutePath)
                    }
                    val text = extractImageSetSearchText(firstName)
                    dao.updateItem(
                        requireNotNull(dao.getItemById(id)).copy(
                            uriPath = copied.joinToString("\n"),
                            textContent = text,
                        ),
                    )
                } catch (t: Throwable) {
                    rollback(dao, id, copied.joinToString("\n"))
                    throw t
                }
                id
            }
        }
    }

    /** Шаг 2–3 для одиночного файла: копирование + pageCount/textContent, откат при сбое. */
    private suspend fun finalizeFileImport(
        context: Context,
        dao: MaterialDao,
        itemId: Long,
        uri: Uri,
        ext: String,
        originalName: String,
    ): Long {
        var stored: File? = null
        try {
            stored = copyToStorage(context, uri, "$itemId.$ext")
            val pages = if (ext == "pdf") pdfPageCount(stored) else 0
            val text =
                when {
                    ext == "docx" -> readDocxText(stored)
                    ext in PLAIN_TEXT_EXT -> readTextFile(stored)
                    else -> ""
                }
            val row = requireNotNull(dao.getItemById(itemId)) { "Строка материала исчезла во время импорта" }
            dao.updateItem(
                row.copy(
                    uriPath = stored.absolutePath,
                    pageCount = pages,
                    textContent = text.take(MAX_INDEXED_CHARS),
                    ext = if (ext.isBlank()) row.ext else ext,
                    title = row.title.ifBlank { titleOf(originalName) },
                ),
            )
            return itemId
        } catch (t: Throwable) {
            rollback(dao, itemId, stored?.absolutePath ?: "")
            throw t
        }
    }

    /**
     * Поиск по набору изображений честным способом невозможен без OCR (ML Kit ≈ +15 МБ),
     * поэтому индексируем только имя файла («Скан_лекция3.jpg» ищется по «лекция»).
     *
     * ponytail: без OCR. Добавить ML Kit Text Recognition, если заказчик попросит поиск по сканам.
     */
    private fun extractImageSetSearchText(firstName: String): String = titleOf(firstName)

    /** Сбой импорта: стираем строку и уже записанные байты, чтобы не оставлять мусор. */
    private suspend fun rollback(
        dao: MaterialDao,
        itemId: Long,
        uriPath: String,
    ) {
        runCatching {
            uriPath.split('\n').forEach { if (it.isNotBlank()) File(it).delete() }
            dao.deleteBookmarksForItem(itemId)
            dao.deleteItemById(itemId)
        }
    }
}
