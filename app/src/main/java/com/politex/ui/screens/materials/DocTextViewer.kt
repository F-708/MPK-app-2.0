package com.politex.ui.screens.materials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.DocxParagraph
import com.politex.util.DocxTextExtractor
import com.politex.util.MaterialImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

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
 * V10: встроенный редактор (карандаш в шапке для TEXT/текстоподобных/docx): ОДИН multiline
 * TextField вместо списка абзацев, разметка токенами **жирный** / *курсив* / __подчёркнутый__
 * и «- » → маркер списка. Сохранение ТОЛЬКО явной кнопкой (dao.updateTextContent → textContent
 * + textEdited=1 в БД; файл на диске не трогаем). Находка 3: выход из редактора (карандаш
 * toggle-off И системный back) с несохранёнными правками спрашивает «СОХРАНИТЬ / ВЫЙТИ БЕЗ
 * СОХРАНЕНИЯ» — текст больше не сгорает молча, и «передумал» по-прежнему не пишет в БД.
 *
 * .doc (бинарный OLE2) — честная заглушка, см. [UnsupportedFormatPlaque].
 */
@Composable
fun MaterialDocTextViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var paragraphs by remember(item.id, item.uriPath) { mutableStateOf<List<DocxParagraph>?>(null) }
    var failed by remember(item.id, item.uriPath) { mutableStateOf(false) }

    LaunchedEffect(item.id, item.uriPath, item.kind, item.textEdited) {
        paragraphs = withContext(Dispatchers.IO) { loadParagraphs(item) }
        failed = paragraphs == null
    }

    // ── V10: состояние редактора ──────────────────────────────────────────────
    var editMode by remember(item.id) { mutableStateOf(false) }
    var textValue by remember(item.id) { mutableStateOf(TextFieldValue()) }
    // Находка 3: исходный текст на момент входа в редактор — для сравнения «есть ли правки»
    var originalText by remember(item.id) { mutableStateOf("") }
    var showUnsavedDialog by remember(item.id) { mutableStateOf(false) }
    val undoStack = remember(item.id) { ArrayDeque<String>() }
    val redoStack = remember(item.id) { ArrayDeque<String>() }

    // ── Y3: единый зум документа (как в PDF): session-only, щипок 2 пальца, двойной тап — сброс.
    // Масштабируется колонка (width=container*docZoom), fontSize и padding — как Word: при <1
    // текст мельче и видны поля, при >1 — крупнее + общий горизонтальный скролл.
    val docZoomState = remember(item.id) { mutableFloatStateOf(DocZoom.DEFAULT) }

    fun startEdit() {
        val initial = paragraphs?.joinToString("\n") { it.text } ?: item.textContent
        textValue = TextFieldValue(initial)
        originalText = initial
        undoStack.clear()
        redoStack.clear()
        editMode = true
    }

    // Находка 3: выход из редактора (карандаш toggle-off И системный back) — если текст
    // изменён, спрашиваем «СОХРАНИТЬ / ВЫЙТИ БЕЗ СОХРАНЕНИЯ», иначе молча закрываем.
    fun requestExitEdit() {
        if (textValue.text != originalText) showUnsavedDialog = true else editMode = false
    }

    // ponytail: снапшоты на каждое изменение, стек cap 10; для текстов >100КБ снапшоты
    // скипаем (10×100КБ строк в памяти того не стоят) — undo на больших текстах не работает.
    fun snapshot() {
        if (textValue.text.length > 100_000) return
        undoStack.addLast(textValue.text)
        if (undoStack.size > 10) undoStack.removeFirst()
        redoStack.clear()
    }

    fun wrapSelection(marker: String) {
        snapshot()
        val t = textValue.text
        val sel = textValue.selection
        val newText = t.substring(0, sel.start) + marker + t.substring(sel.start, sel.end) + marker + t.substring(sel.end)
        textValue = TextFieldValue(newText, selection = TextRange(sel.start + marker.length, sel.end + marker.length))
    }

    fun bulletLine() {
        snapshot()
        val t = textValue.text
        val cursor = textValue.selection.min
        val lineStart = if (cursor == 0) 0 else t.lastIndexOf('\n', cursor - 1) + 1
        textValue = TextFieldValue(t.substring(0, lineStart) + "- " + t.substring(lineStart), selection = TextRange(cursor + 2))
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(textValue.text)
        if (redoStack.size > 10) redoStack.removeFirst()
        textValue = TextFieldValue(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(textValue.text)
        if (undoStack.size > 10) undoStack.removeFirst()
        textValue = TextFieldValue(next)
    }

    fun save() {
        val text = textValue.text
        scope.launch { dao.updateTextContent(item.id, text) }
        paragraphs = text.split('\n').map { DocxParagraph(it.trimEnd()) }
        failed = false
        editMode = false
    }

    val canEdit = item.kindEnum == MaterialKind.TEXT || item.ext in TEXT_LIKE_EXT || item.ext == "docx"

    // Находка 3: системный «назад» в режиме правки — тот же путь, что toggle-off карандаша
    BackHandler(enabled = editMode) { requestExitEdit() }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        MaterialViewerTopBar(title = item.title, testTag = "doc_top_bar", onBack = onBack) {
            if (!editMode) {
                // Y3: индикатор зума — «140%», тап = сброс к 100%
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(onClick = { docZoomState.floatValue = DocZoom.DEFAULT })
                        .testTag("doc_zoom_indicator"),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            "${(docZoomState.floatValue * 100).roundToInt()}%",
                            maxLines = 1,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                        )
                    }
                }
            }
            if (canEdit) {
                ViewerAction(
                    icon = Icons.Default.Edit,
                    description = "Редактировать текст",
                    testTag = "doc_edit_btn",
                    active = editMode,
                    onClick = { if (editMode) requestExitEdit() else startEdit() },
                )
            }
        }

        if (editMode) {
            TextEditToolbar(
                onBold = { wrapSelection("**") },
                onItalic = { wrapSelection("*") },
                onUnderline = { wrapSelection("__") },
                onBullet = ::bulletLine,
                onUndo = ::undo,
                onRedo = ::redo,
                onSave = ::save,
            )
            TextField(
                value = textValue,
                onValueChange = { next ->
                    snapshot()
                    textValue = next
                },
                modifier = Modifier.fillMaxSize().testTag("doc_edit_field"),
                textStyle = TextStyle(fontSize = 14.sp, color = ColorTextBody, lineHeight = 19.sp),
            )
        } else {
            val content = paragraphs
            when {
                // Нечитаемый/неподдерживаемый формат — плашка, а не пустой экран
                failed -> UnsupportedFormatPlaque(ext = item.ext, fileName = item.fileName.ifBlank { item.title })

                content == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorBrandBlue, modifier = Modifier.testTag("doc_loading"))
                }

                content.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = "Текст не найден: документ пустой или не читается встроенным просмотрщиком.",
                            style = TextStyle(fontSize = 13.sp),
                            textAlign = TextAlign.Center,
                        )
                        // Находка 12: у пустого состояния есть действие — «НАЗАД» (onBack)
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandBlue,
                            modifier = Modifier.bouncyClickable(onClick = onBack).testTag("doc_empty_back"),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    "НАЗАД",
                                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                                )
                            }
                        }
                    }
                }

                else -> BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .docZoomGestures(docZoomState),
                ) {
                    val z = docZoomState.floatValue
                    val containerW = maxWidth
                    val pageW = containerW * z
                    Box(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .width(maxOf(containerW, pageW)),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(pageW)
                                .testTag("doc_paragraph_list"),
                            contentPadding = PaddingValues(horizontal = (16 * z).dp, vertical = (12 * z).dp),
                            verticalArrangement = Arrangement.spacedBy((6 * z).dp),
                        ) {
                            items(content.size) { index ->
                                val p = content[index]
                                Text(
                                    // V10: токены разметки (**/*/__) и «- » → «• » рендерятся в AnnotatedString
                                    text = parseInlineMarks(p.text),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (p.heading) Modifier.padding(top = (10 * z).dp) else Modifier)
                                        .testTag("doc_paragraph_$index"),
                                    style = when {
                                        p.heading -> TextStyle(fontSize = (16 * z).sp, fontWeight = FontWeight.Bold, color = ColorTextTitle, lineHeight = (21 * z).sp)
                                        p.bold -> TextStyle(fontSize = (14 * z).sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle, lineHeight = (19 * z).sp)
                                        else -> TextStyle(fontSize = (14 * z).sp, fontWeight = FontWeight.Normal, color = ColorTextBody, lineHeight = (19 * z).sp)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Находка 3: выход из редактора с несохранёнными правками — СОХРАНИТЬ / ВЫЙТИ БЕЗ СОХРАНЕНИЯ
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            shape = RoundedCornerShape(2.dp),
            containerColor = ColorBgMain,
            title = {
                Text(
                    "Правки не сохранены",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
                )
            },
            text = {
                Text(
                    "Сохранить изменения перед выходом?",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        save()
                    },
                    modifier = Modifier.testTag("doc_unsaved_save"),
                ) {
                    Text("СОХРАНИТЬ", style = TextStyle(fontWeight = FontWeight.Bold, color = ColorBrandBlue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        editMode = false
                    },
                    modifier = Modifier.testTag("doc_unsaved_discard"),
                ) {
                    Text("ВЫЙТИ БЕЗ СОХРАНЕНИЯ", style = TextStyle(fontWeight = FontWeight.Bold, color = ColorTextBody))
                }
            },
        )
    }
}

/** V10: панель редактора [B][I][U][•][undo][redo][СОХРАНИТЬ] под хедером. */
@Composable
private fun TextEditToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onBullet: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceHighlight,
        modifier = Modifier.fillMaxWidth().testTag("doc_edit_toolbar"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            EditMarkButton("B", "doc_bold_btn", TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle), onBold)
            EditMarkButton("I", "doc_italic_btn", TextStyle(fontStyle = FontStyle.Italic, fontSize = 15.sp, color = ColorTextTitle), onItalic)
            EditMarkButton(
                "U",
                "doc_underline_btn",
                TextStyle(textDecoration = TextDecoration.Underline, fontSize = 15.sp, color = ColorTextTitle),
                onUnderline,
            )
            EditMarkButton("•", "doc_bullet_btn", TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle), onBullet)
            EditMarkIcon(Icons.AutoMirrored.Filled.Undo, "Отменить", "doc_undo_btn", onUndo)
            EditMarkIcon(Icons.AutoMirrored.Filled.Redo, "Повторить", "doc_redo_btn", onRedo)
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandBlue,
                modifier = Modifier.size(width = 104.dp, height = 36.dp).bouncyClickable(onClick = onSave).testTag("doc_save_btn"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("СОХРАНИТЬ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                }
            }
        }
    }
}

@Composable
private fun EditMarkButton(
    label: String,
    tag: String,
    style: TextStyle,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = Modifier.size(40.dp).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, style = style) }
    }
}

@Composable
private fun EditMarkIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = Modifier.size(40.dp).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, tint = ColorTextBody, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * V10: рендер инлайн-разметки абзаца: `**жирный**`, `*курсив*`, `__подчёркнутый__` →
 * span'ы AnnotatedString, ведущий «- » → «• ». Незакрытый токен остаётся литералом
 * (текст конспекта важнее «красивой» разметки). Чистая функция — покрыта unit-тестом.
 *
 * ponytail: токенайзер в один проход без вложенности (`*a **b** c*` разберется плоско);
 * полноценный markdown-парсер тащить только если появится реальная вложенная разметка.
 */
internal fun parseInlineMarks(par: String): AnnotatedString {
    val s = if (par.startsWith("- ")) "• " + par.substring(2) else par
    return buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            when {
                s.startsWith("**", i) -> {
                    val end = s.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(s[i]); i++
                    }
                }
                s[i] == '*' -> {
                    val end = s.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(s[i]); i++
                    }
                }
                s.startsWith("__", i) -> {
                    val end = s.indexOf("__", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(s.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(s[i]); i++
                    }
                }
                else -> {
                    append(s[i]); i++
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
    // V10: ПЕРВЫМ делом — пользовательская правка: она приоритетнее любого файла
    if (item.textEdited) {
        return item.textContent.split('\n').map { DocxParagraph(it.trimEnd()) }
    }

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
