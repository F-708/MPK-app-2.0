package com.politex.ui.screens.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextTitle
import com.politex.util.DocxParagraph
import com.politex.util.DocxTextExtractor
import com.politex.util.MaterialImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Расширения, которые встроенный просмотрщик показывает как текст (M2). */
private val TEXT_LIKE_EXT = setOf("txt", "md", "markdown", "csv", "log", "json", "xml")

/**
 * M2: просмотрщик DOCX и простого текста.
 *
 * DOCX = ZIP с `word/document.xml`: читаем `DocxTextExtractor`-ом (stdlib XmlPullParser),
 * получаем список абзацев с флагами «жирный»/«заголовок» — этого хватает, чтобы конспект
 * выглядел конспектом, а не стеной одинакового текста.
 *
 * Простой текст и материалы типа TEXT идут тем же рендером (строка = абзац): LazyColumn по
 * абзацам вместо одного гигантского Text в verticalScroll — на методичке в 300 КБ разница
 * в скорости прокрутки кратная, потому что LazyColumn меряет только видимое.
 *
 * .doc (бинарный OLE2) — честная заглушка, см. [UnsupportedFormatPlaque].
 */
@Composable
fun MaterialDocTextViewer(
    item: MaterialItemEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var paragraphs by remember(item.id, item.uriPath) { mutableStateOf<List<DocxParagraph>?>(null) }
    var failed by remember(item.id, item.uriPath) { mutableStateOf(false) }

    LaunchedEffect(item.id, item.uriPath, item.kind) {
        paragraphs = withContext(Dispatchers.IO) { loadParagraphs(item) }
        failed = paragraphs == null
    }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        MaterialViewerTopBar(title = item.title, testTag = "doc_top_bar", onBack = onBack)

        val content = paragraphs
        when {
            // Нечитаемый/неподдерживаемый формат — плашка, а не пустой экран
            failed -> UnsupportedFormatPlaque(ext = item.ext, fileName = item.fileName.ifBlank { item.title })

            content == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ColorBrandBlue, modifier = Modifier.testTag("doc_loading"))
            }

            content.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Текст не найден: документ пустой или не читается встроенным просмотрщиком.",
                    style = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.padding(24.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("doc_paragraph_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(content.size) { index ->
                    val p = content[index]
                    Text(
                        text = p.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (p.heading) Modifier.padding(top = 10.dp) else Modifier)
                            .testTag("doc_paragraph_$index"),
                        style = when {
                            p.heading -> TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle, lineHeight = 21.sp)
                            p.bold -> TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle, lineHeight = 19.sp)
                            else -> TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = ColorTextBody, lineHeight = 19.sp)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Загрузка абзацев. `null` = «встроенный просмотрщик это не умеет» (плашка), пустой список =
 * «умеем, но текста нет». Различение нужно, чтобы .xls не выглядел пустой заметкой.
 */
private fun loadParagraphs(item: MaterialItemEntity): List<DocxParagraph>? {
    // Текст, набранный в приложении: байтов на диске нет, всё уже в БД
    if (item.kindEnum == MaterialKind.TEXT) {
        if (item.textContent.isBlank()) return emptyList()
        return item.textContent.split('\n').map { DocxParagraph(it.trimEnd()) }
    }

    if (item.uriPath.isBlank()) return null
    val file = File(item.uriPath)
    if (!file.exists()) return null

    return when (item.ext) {
        "docx" -> runCatching {
            file.inputStream().use { DocxTextExtractor.extract(it) }
        }.getOrNull()

        // Для текстовых файлов — полная версия с диска (в БД лежит обрезанный поисковый индекс)
        in TEXT_LIKE_EXT -> runCatching {
            MaterialImporter.decodeSmart(file.readBytes()).split('\n').map { DocxParagraph(it.trimEnd()) }
        }.getOrNull()

        // .doc, .xls/.xlsx, .pptx и прочее — честно не умеем
        else -> null
    }
}
