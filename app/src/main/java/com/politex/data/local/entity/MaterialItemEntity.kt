package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M1: тип материала. FILE — один документ (PDF/DOCX/XLS/…), IMAGES — НАБОР изображений
 * (один материал = одна строка, все страницы набора лежат в `uriPath` через перевод строки),
 * TEXT — текст, набранный в приложении (байтов на диске нет, всё в `textContent`).
 */
enum class MaterialKind { FILE, IMAGES, TEXT }

/**
 * M1: материал. В БД — ТОЛЬКО метаданные: сами байты лежат в
 * `context.filesDir/materials/<itemId>.<ext>` (см. util/MaterialImporter). Удаление
 * материала обязано удалить и файл, иначе filesDir обрастает мусором.
 *
 * Поля-состояние просмотрщика живут здесь же, а не в отдельной таблице:
 * `pageCount`/`lastPage` — PDF (запомнить, где остановился студент), `rotationDeg` —
 * набор изображений (скан «боком»: повернули один раз, при следующем открытии применится).
 *
 * `textContent` — извлечённый при импорте текст (TXT/DOCX). По нему работает нечёткий поиск
 * (util/FuzzySearch). Для PDF пустой: извлечение текста из PDF требует pdfbox (~+5 МБ к APK),
 * поэтому PDF ищется только по названию, а внутри — закладки и страница.
 *
 * Индекс по `folderId`: список папки — самый частый запрос экрана.
 */
@Entity(
    tableName = "material_items",
    indices = [Index("folderId")]
)
data class MaterialItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val kind: String,
    val title: String,
    /** Оригинальное имя файла из SAF (DISPLAY_NAME). Для TEXT — пусто. */
    val fileName: String = "",
    /** Расширение без точки, в нижнем регистре («pdf», «docx»). Для TEXT/IMAGES — «txt»/«jpg». */
    val ext: String = "",
    /**
     * Абсолютный путь в filesDir. Для [MaterialKind.IMAGES] — несколько путей,
     * по одному на строку (порядок строк = порядок страниц в просмотрщике).
     */
    val uriPath: String = "",
    val pageCount: Int = 0,
    val lastPage: Int = 0,
    val rotationDeg: Int = 0,
    val sortOrder: Int = 0,
    val textContent: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val kindEnum: MaterialKind
        get() = runCatching { MaterialKind.valueOf(kind) }.getOrDefault(MaterialKind.FILE)

    /** Страницы набора изображений (для FILE/TEXT — список из 0..1 элемента). */
    val imagePaths: List<String>
        get() = if (kindEnum == MaterialKind.IMAGES) uriPath.split('\n').filter { it.isNotBlank() } else emptyList()
}
