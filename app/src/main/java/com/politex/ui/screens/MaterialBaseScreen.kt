package com.politex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.data.model.GroupInfo
import com.politex.ui.screens.materials.BrandActionButton
import com.politex.ui.screens.materials.MaterialCard
import com.politex.ui.screens.materials.MaterialDeleteDialog
import com.politex.ui.screens.materials.MaterialDocTextViewer
import com.politex.ui.screens.materials.MaterialImageViewer
import com.politex.ui.screens.materials.MaterialImportTypeDialog
import com.politex.ui.screens.materials.MaterialPdfViewer
import com.politex.ui.screens.materials.MaterialRenameDialog
import com.politex.ui.screens.materials.MaterialSubjectDialog
import com.politex.ui.screens.materials.MaterialTextDialog
import com.politex.ui.screens.materials.ViewerMode
import com.politex.ui.screens.materials.materialIconFor
import com.politex.ui.screens.materials.materialIconTint
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import com.politex.util.FuzzySearch
import com.politex.util.MaterialImporter
import com.politex.util.MpkCurriculum
import com.politex.util.PendingMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// M1–M6 + W11–W15: «Материалы» — локальное хранилище раздаточных материалов.
//
//  M1 хранение: байты в filesDir/materials, в БД только метаданные
//  M2 просмотрщики: PDF (PdfRenderer + закладки), DOCX/TXT, изображения (зум + поворот)
//  M3 импорт: SAF-пикеры → диалог «предмет» (W11: БЕЗ папок)
//  M4 список: W11: ПРЕДМЕТЫ (пустые скрыты) + Архив секцией внизу; внутри предмета —
//      переименование/удаление/архив/порядок
//  M5 поиск: W13: поле в шапке, FuzzySearch под капотом (Даммерау-Левенштейн ≤2),
//      без тогла «Aa» и пояснительных текстов
//  M6 навигация: sealed-состояния экранов и СВОЙ back-стек (без Navigation), системный back
// ─────────────────────────────────────────────────────────────────────────────

/** W11: предмет для материалов, восстановленных из старого (до-v7) Архива без предмета. */
private const val RESTORED_SUBJECT = "Восстановленные"

/**
 * M6: запись back-стека. Кодирование строкой («s:ТОЭ», «i:7», «a») — чтобы стек переживал
 * смерть процесса через rememberSaveable без кастомного Saver'а: ArrayList<String> в Bundle
 * кладётся штатно, а sealed-класс пришлось бы писать Parcelable/Saver вручную.
 */
private sealed interface MaterialRoute {
    /** W11: экран предмета. */
    data class SubjectScreen(
        val subjectName: String,
    ) : MaterialRoute {
        override fun toString() = "s:$subjectName"
    }

    /** W11: Архив — спец-секция внизу списка предметов. */
    object ArchiveScreen : MaterialRoute {
        override fun toString() = "a"
    }

    /** Просмотрщик материала. */
    data class Viewer(
        val itemId: Long,
    ) : MaterialRoute {
        override fun toString() = "i:$itemId"
    }

    companion object {
        fun decode(raw: String): MaterialRoute? =
            when (raw.firstOrNull()) {
                'a' -> if (raw == "a") ArchiveScreen else null
                'i' -> raw.substringAfter(':', "").toLongOrNull()?.let { Viewer(it) }
                's' -> raw.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { SubjectScreen(it) }
                else -> null
            }
    }
}

/** Набор действий над материалом — передаётся в списки, чтобы не плодить callback-параметры. */
private class MaterialActions(
    val onOpen: (MaterialItemEntity) -> Unit,
    val onRename: (MaterialItemEntity) -> Unit,
    /** W11: archived = true — в Архив, false — обратно в предмет. */
    val onSetArchived: (MaterialItemEntity, Boolean) -> Unit,
    val onDelete: (MaterialItemEntity) -> Unit,
    /** list — упорядоченный список того экрана, где лежит item; delta = ±1. */
    val onReorder: (List<MaterialItemEntity>, MaterialItemEntity, Int) -> Unit,
    /**
     * Размер файлов материала в байтах. Берётся из предпосчитанной карты, а НЕ вызовом
     * `File.length()` в композиции: stat каждого файла на главном потоке на каждой
     * строке списка — это дропы кадров и StrictMode-диско-вечеринка.
     */
    val sizeOf: (MaterialItemEntity) -> Long,
)

/**
 * Ф4/M1+W11: «Материалы».
 *
 * Экран-состояние (корень-предметы / Архив / просмотрщик) с собственным back-стеком:
 * Navigation в проекте не используется, а вкладка «Уроки» живёт внутри LessonsHubScreen,
 * поэтому вложенная навигация сделана как rememberSaveable-стек + системный BackHandler.
 */
@Composable
fun MaterialBaseScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dao = remember { MpkDatabase.getInstance(context).materialDao() }
    val scope = rememberCoroutineScope()

    val items by dao.getAllItems().collectAsState(initial = emptyList())

    // M4: размеры файлов считаем ВНЕ главного потока — File.length() это дисковый stat,
    // а строк в списке может быть много. Карта пересчитывается при любом изменении items.
    val sizesState = remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    LaunchedEffect(items) {
        sizesState.value =
            withContext(Dispatchers.IO) {
                items.associate { it.id to MaterialImporter.storedSize(it) }
            }
    }

    // M6: back-стек одной строкой через «|» (см. KDoc MaterialRoute).
    // W13/W14: поиск живёт прямо в шапке корня; вход в предмет/архив сбрасывает запрос.
    var query by rememberSaveable { mutableStateOf("") }
    val stackState = rememberSaveable { mutableStateOf("") }
    val stack =
        remember(stackState.value) {
            if (stackState.value.isEmpty()) emptyList() else stackState.value.split('|').mapNotNull(MaterialRoute.Companion::decode)
        }
    val push: (MaterialRoute) -> Unit = { route ->
        query = ""
        stackState.value = if (stackState.value.isEmpty()) route.toString() else stackState.value + "|" + route
    }
    val pop: () -> Unit = { stackState.value = stackState.value.substringBeforeLast('|', "") }

    val curriculumSubjects =
        remember(groupInfo.specialtyCode, groupInfo.course) {
            MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course)
        }

    // ── Состояния диалогов импорта/правок ────────────────────────────────────
    var showImportType by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var showSubject by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingMaterial?>(null) }
    var renamingItem by remember { mutableStateOf<MaterialItemEntity?>(null) }
    var deletingItem by remember { mutableStateOf<MaterialItemEntity?>(null) }
    var importing by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<Banner?>(null) }

    fun showOk(text: String) {
        banner = Banner(text, isError = false)
    }

    fun showErr(text: String) {
        banner = Banner(text, isError = true)
    }

    // ── M3: SAF-пикеры ───────────────────────────────────────────────────────
    // OpenDocument не умеет «всё, кроме image/*», поэтому одиночные картинки отсекаются
    // уже в результате — иначе фото доски станет материалом из одной страницы вместо набора.
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime.startsWith("image/")) {
                showErr("Картинки загружайте через «Набор изображений»: так лекция из 12 фото останется одним материалом")
                return@rememberLauncherForActivityResult
            }
            pending = PendingMaterial.SingleFile(uri)
            showSubject = true
        }
    val imagesPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
            pending = PendingMaterial.Images(uris)
            showSubject = true
        }

    // ── Операции над данными ─────────────────────────────────────────────────
    fun importInto(
        subject: String,
        material: PendingMaterial,
    ) {
        importing = true
        scope.launch {
            val outcome = runCatching { MaterialImporter.importMaterial(context, dao, subject, material) }
            importing = false
            outcome
                .onSuccess { showOk("Материал добавлен в «$subject»") }
                .onFailure { showErr("Не удалось импортировать: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteItem(item: MaterialItemEntity) {
        scope.launch {
            // M1: копия файла стирается вместе с записью (исходный файл пользователя
            // не трогается — W15), иначе filesDir обрастает мусором
            MaterialImporter.deleteStoredFiles(item)
            dao.deleteItemWithBookmarks(item.id)
        }
    }

    /** W11: перенос в Архив (archived=1) и обратно. Мягкая альтернатива удалению (W15). */
    fun setArchived(
        item: MaterialItemEntity,
        archived: Boolean,
    ) {
        scope.launch {
            if (archived) {
                dao.setArchived(item.id, true, dao.nextArchiveSortOrder())
                showOk("Убрано в Архив")
            } else {
                // Возврат в предмет. Старый (до-v7) Архив терял предмет — такие материалы
                // собираем в «Восстановленные», чтобы не потерялись молча.
                val subject = item.subjectName.ifBlank { RESTORED_SUBJECT }
                dao.updateItem(item.copy(subjectName = subject, archived = false, sortOrder = dao.nextItemSortOrder(subject)))
                showOk("Возвращено в «$subject»")
            }
        }
    }

    val actions =
        remember {
            MaterialActions(
                onOpen = { item -> push(MaterialRoute.Viewer(item.id)) },
                onRename = { item -> renamingItem = item },
                onSetArchived = { item, archived -> setArchived(item, archived) },
                onDelete = { item -> deletingItem = item },
                onReorder = { list, item, delta ->
                    val index = list.indexOfFirst { it.id == item.id }
                    val neighbour = list.getOrNull(index + delta)
                    if (neighbour != null) scope.launch { dao.swapSortOrder(item.id, neighbour.id) }
                },
                sizeOf = { item -> sizesState.value[item.id] ?: 0L },
            )
        }

    // Системный back: сначала выходим из вложенного экрана, и только потом — из раздела
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val currentRoute = stack.lastOrNull()
    val viewerItem = (currentRoute as? MaterialRoute.Viewer)?.let { route -> items.find { it.id == route.itemId } }

    Box(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val viewer = currentRoute as? MaterialRoute.Viewer
            if (viewer != null && viewerItem != null) {
                // V7/V8: полноэкранный режим для ЛЮБОГО просмотрщика (PDF, изображения,
                // DOCX/текст) — баннер и нижняя навигация скрыты, пока читалка открыта.
                // onDispose покрывает ЛЮБОЙ выход: back из читалки, системный back, удаление
                // материала из другого экрана; при смерти процесса object-состояние сбрасывается само.
                DisposableEffect(viewerItem.id) {
                    ViewerMode.active = true
                    onDispose { ViewerMode.active = false }
                }
                // W12: роутинг просмотрщика — ЯВНО по kind: TEXT всегда идёт в DocTextViewer
                // и читает textContent, независимо от ext/папок.
                when (viewerItem.kindEnum) {
                    MaterialKind.IMAGES ->
                        MaterialImageViewer(
                            item = viewerItem,
                            dao = dao,
                            onBack = pop,
                            onDelete = { actions.onDelete(viewerItem) },
                            modifier = Modifier.fillMaxSize(),
                        )

                    MaterialKind.FILE ->
                        if (viewerItem.ext == "pdf") {
                            MaterialPdfViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())
                        } else {
                            MaterialDocTextViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())
                        }

                    MaterialKind.TEXT ->
                        MaterialDocTextViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())
                }
            } else if (viewer != null) {
                // W12: материал ещё не доехал из БД (поток Room асинхронный) — показываем
                // загрузку и проверяем существование ОДНИМ запросом, а не выкидываем
                // пользователя назад наугад: раньше тут был безусловный pop(), и при
                // медленной выдаче просмотрщик закрывался сам собой.
                LaunchedEffect(viewer.itemId) {
                    if (dao.getItemById(viewer.itemId) == null) pop()
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorBrandBlue, modifier = Modifier.testTag("material_viewer_loading"))
                }
            } else {
                val subjectRoute = currentRoute as? MaterialRoute.SubjectScreen
                val archiveRoute = currentRoute as? MaterialRoute.ArchiveScreen

                // W14: шапка — только поиск и «Добавить», в одну строку
                MaterialHeader(
                    query = query,
                    onQueryChange = { query = it },
                    onAdd = {
                        showImportType = true
                    },
                )

                val subjectItems =
                    remember(items, subjectRoute) {
                        subjectRoute?.let { route -> items.filter { !it.archived && it.subjectName == route.subjectName } }
                    }
                val archivedItems = remember(items, archiveRoute) { if (archiveRoute != null) items.filter { it.archived } else null }

                when {
                    subjectRoute != null && subjectItems != null ->
                        MaterialSubjectContent(
                            subjectName = subjectRoute.subjectName,
                            isArchive = false,
                            items = subjectItems,
                            actions = actions,
                            onBack = pop,
                            modifier = Modifier.weight(1f),
                        )

                    archiveRoute != null && archivedItems != null ->
                        MaterialSubjectContent(
                            subjectName = null,
                            isArchive = true,
                            items = archivedItems,
                            actions = actions,
                            onBack = pop,
                            modifier = Modifier.weight(1f),
                        )

                    else ->
                        if (query.isBlank()) {
                            MaterialRootList(
                                items = items,
                                onOpenSubject = { subject -> push(MaterialRoute.SubjectScreen(subject)) },
                                onOpenArchive = { push(MaterialRoute.ArchiveScreen) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            MaterialSearchContent(
                                query = query,
                                items = items,
                                actions = actions,
                                onClear = { query = "" },
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
            }
        }

        if (importing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(2.dp), color = ColorBgMain, border = BorderStroke(1.dp, ColorBorderLight)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        CircularProgressIndicator(color = ColorBrandBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Копируем в хранилище…", style = TextStyle(fontSize = 13.sp, color = ColorTextBody))
                    }
                }
            }
        }

        banner?.let { current ->
            MessageBanner(
                banner = current,
                onClose = { banner = null },
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }

    // ── Диалоги ──────────────────────────────────────────────────────────────
    if (showImportType) {
        MaterialImportTypeDialog(
            onDismiss = { showImportType = false },
            onPickFile = {
                showImportType = false
                runCatching { filePicker.launch(arrayOf("*/*")) }
                    .onFailure { showErr("Не удалось открыть выбор файлов") }
            },
            onPickImages = {
                showImportType = false
                runCatching { imagesPicker.launch(arrayOf("image/*")) }
                    .onFailure { showErr("Не удалось открыть выбор изображений") }
            },
            onPickText = {
                showImportType = false
                showTextInput = true
            },
        )
    }

    if (showTextInput) {
        MaterialTextDialog(
            onDismiss = { showTextInput = false },
            onConfirm = { text ->
                showTextInput = false
                pending = PendingMaterial.Text(text)
                showSubject = true
            },
        )
    }

    // W11: после выбора файла/текста спрашиваем ТОЛЬКО предмет — никаких папок
    if (showSubject) {
        val material = pending
        MaterialSubjectDialog(
            curriculumSubjects = curriculumSubjects,
            usedSubjects = remember(items) {
                items.filter { !it.archived && it.subjectName.isNotBlank() }.map { it.subjectName }.distinct()
            },
            onDismiss = {
                showSubject = false
                pending = null
            },
            onConfirm = { subject ->
                showSubject = false
                pending = null
                if (material != null) importInto(subject, material)
            },
        )
    }

    renamingItem?.let { item ->
        MaterialRenameDialog(
            initial = item.title,
            label = "Переименовать материал",
            onDismiss = { renamingItem = null },
            onConfirm = { newTitle ->
                renamingItem = null
                scope.launch { dao.renameItem(item.id, newTitle) }
            },
        )
    }

    deletingItem?.let { item ->
        MaterialDeleteDialog(
            title = "Удалить материал?",
            // W15: честно — из приложения убирается копия, исходный файл на устройстве остаётся
            message = "«${item.title}» уберётся из приложения. Исходный файл на устройстве останется.",
            softActionText = if (item.archived) "ВЕРНУТЬ ИЗ АРХИВА" else "УБРАТЬ В АРХИВ",
            onSoftAction = {
                deletingItem = null
                setArchived(item, !item.archived)
            },
            onDismiss = { deletingItem = null },
            onConfirm = {
                deletingItem = null
                deleteItem(item)
            },
        )
    }
}

/** Короткое сообщение об успехе/ошибке вместо SnackbarHost (его в этот экран не передают). */
private data class Banner(
    val text: String,
    val isError: Boolean,
)

@Composable
private fun MessageBanner(
    banner: Banner,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // M1: само исчезает, чтобы не перекрывать список навсегда
    LaunchedEffect(banner) {
        delay(4000)
        onClose()
    }
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (banner.isError) ColorError else ColorBorderLight),
        color = if (banner.isError) ColorError.copy(alpha = 0.08f) else ColorSurfaceHighlight,
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(if (banner.isError) "material_error_banner" else "material_info_banner"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = banner.text,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = TextStyle(fontSize = 12.sp, color = if (banner.isError) ColorError else ColorTextBody),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = ColorTextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** W14: шапка — строка [поиск] + [Добавить], без заголовка/группы/счётчика. */
@Composable
private fun MaterialHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // X14: общий SearchBar (высота = Tokens.SearchHeight = высоте кнопки «Добавить»)
        com.politex.ui.components.SearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Поиск материалов",
            testTag = "material_search_field",
            modifier = Modifier.weight(1f),
        )
        BrandActionButton(
            icon = Icons.Default.Add,
            label = "ДОБАВИТЬ",
            testTag = "material_add_btn",
            onClick = onAdd,
        )
    }
}

/**
 * W11: корень — список ПРЕДМЕТОВ, у которых есть материалы. Пустые предметы скрыты
 * («если у предмета нет материала — не показываем предмет»). Архив — спец-строка В САМОМ
 * НИЗУ, материалы из него в предметах не видны.
 */
@Composable
private fun MaterialRootList(
    items: List<MaterialItemEntity>,
    onOpenSubject: (String) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = remember(items) { items.filter { !it.archived && it.subjectName.isNotBlank() } }
    val subjects = remember(active) { active.groupBy { it.subjectName }.keys.sorted() }
    val archiveCount = remember(items) { items.count { it.archived } }

    if (active.isEmpty() && archiveCount == 0) {
        MaterialEmptyState(
            title = "Материалов пока нет",
            hint =
            "Нажмите «ДОБАВИТЬ» и выберите файл, набор изображений или текст. " +
                "Материал ляжет в предмет — потом его можно переименовать, " +
                "перенести в Архив и читать прямо в приложении.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("material_root_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(count = subjects.size, key = { "subject:${subjects[it]}" }) { index ->
            val subject = subjects[index]
            SubjectRow(
                subjectName = subject,
                count = active.count { it.subjectName == subject },
                onOpen = { onOpenSubject(subject) },
            )
        }

        // W11: Архив — последняя, спец-секция: показывается только когда есть что показать
        if (archiveCount > 0) {
            item(key = "archive") {
                MaterialCard(testTag = "material_archive_section", onClick = onOpenArchive) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = ColorTopBar,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Архив",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextTitle),
                            )
                            Text(
                                text = "$archiveCount отложенных",
                                maxLines = 1,
                                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** W11: строка предмета в корне. Тап открывает материалы только этого предмета. */
@Composable
private fun SubjectRow(
    subjectName: String,
    count: Int,
    onOpen: () -> Unit,
) {
    MaterialCard(testTag = "material_subject_$subjectName", onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = subjectName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextTitle),
            )
            Text(
                text = "$count",
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ColorTextMuted),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorTextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * W11: экран предмета (или Архива, isArchive = true) — список его материалов с кнопками
 * порядка и правкой. У Архива вместо кнопки-архива кнопка восстановления.
 */
@Composable
private fun MaterialSubjectContent(
    subjectName: String?,
    isArchive: Boolean,
    items: List<MaterialItemEntity>,
    actions: MaterialActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag(if (isArchive) "archive_back_btn" else "subject_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ColorTextTitle)
            }
            if (isArchive) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = ColorTopBar,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subjectName ?: "Архив",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
                )
                Text(
                    text = if (isArchive) "${items.size} отложенных" else "${items.size} материалов",
                    maxLines = 1,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        }

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(1.dp)
                .background(ColorDividerLight),
        )

        if (items.isEmpty()) {
            MaterialEmptyState(
                title = if (isArchive) "В Архиве пусто" else "Материалов пока нет",
                hint =
                if (isArchive) {
                    "Сюда переносятся материалы кнопкой-архивом: не удаляются, а убираются с глаз."
                } else {
                    "Нажмите «ДОБАВИТЬ» на главном экране материалов и выберите этот предмет."
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(if (isArchive) "material_archive_list" else "material_subject_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(count = items.size, key = { items[it].id }) { index ->
                    MaterialItemRow(
                        item = items[index],
                        actions = actions,
                        isArchiveRow = isArchive,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.size - 1,
                        siblings = items,
                    )
                }
            }
        }
    }
}

/**
 * M5/W13: поиск по всей базе (кроме Архива). Нечётко по `title + textContent`
 * (FuzzySearch: Даммерау-Левенштейн ≤2 для слов от 4 символов, «ё»→«е»), пояснительных
 * текстов и тогла «Aa» больше нет — только поле в шапке и результаты.
 */
@Composable
private fun MaterialSearchContent(
    query: String,
    items: List<MaterialItemEntity>,
    actions: MaterialActions,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = remember(items) { items.filter { !it.archived } }

    // Тяжёлая часть (разбор текста на слова) считается на каждый запрос; для сотен материалов
    // это доли миллисекунд. См. ponytail-комментарий в FuzzySearch про порог перехода на FTS.
    val hits =
        remember(scope, query) {
            val q = query.trim()
            if (q.isEmpty()) return@remember emptyList()
            scope
                .mapNotNull { item ->
                    val score = FuzzySearch.score(item.title + "\n" + item.textContent, q)
                    if (score > 0) item to score else null
                }.sortedWith(compareByDescending<Pair<MaterialItemEntity, Int>> { it.second }.thenBy { it.first.title })
        }

    val queryWords = remember(query) { FuzzySearch.queryWordCount(query) }

    if (hits.isEmpty()) {
        // N12: тупик поиска — подсказка (архив не ищется) + кнопка «Очистить» как выход
        MaterialEmptyState(
            title = "Ничего не нашлось",
            hint = "Архив в поиске не участвует",
            showStorageNote = false,
            modifier = modifier,
            action = {
                BrandActionButton(
                    icon = Icons.Default.Close,
                    label = "Очистить",
                    testTag = "material_search_clear",
                    filled = false,
                    onClick = onClear,
                )
            },
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("material_search_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(count = hits.size, key = { hits[it].first.id }) { index ->
            val (item, score) = hits[index]
            MaterialItemRow(
                item = item,
                actions = actions,
                isArchiveRow = false,
                showReorder = false,
                matchCount = score to queryWords,
            )
        }
    }
}

/**
 * M4: строка материала. Верх — иконка по расширению, название, «тип • размер • страницы»;
 * для набора изображений под названием горизонтальная мини-лента превью.
 * Низ — действия: порядок, переименовать, в Архив/обратно, удалить.
 * Действия отдельной строкой, а не тремя иконками в одну линию: хитбокс каждой кнопки 44dp,
 * иначе на телефоне промахиваешься и случайно удаляешь материал.
 */
@Composable
private fun MaterialItemRow(
    item: MaterialItemEntity,
    actions: MaterialActions,
    isArchiveRow: Boolean = false,
    showReorder: Boolean = true,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    siblings: List<MaterialItemEntity> = emptyList(),
    matchCount: Pair<Int, Int>? = null,
) {
    MaterialCard(testTag = "material_item_${item.id}", onClick = { actions.onOpen(item) }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ColorSurfaceHighlight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = materialIconFor(item),
                        contentDescription = null,
                        tint = materialIconTint(item),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorTextTitle),
                    )
                    Text(
                        text = materialMeta(item, actions.sizeOf(item)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                    )
                }
                if (matchCount != null) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBrandBlue.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("material_match_${item.id}"),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text(
                                text = "${matchCount.first}/${matchCount.second}",
                                maxLines = 1,
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = ColorBrandBlue),
                            )
                        }
                    }
                }
            }

            // M4: мини-лента превью набора изображений — видно, тот ли скан, не открывая
            val paths = item.imagePaths
            if (paths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    items(count = minOf(paths.size, 8)) { index ->
                        AsyncImage(
                            model = File(paths[index]),
                            contentDescription = "Превью ${index + 1}",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ColorSurfaceHighlight)
                                .testTag("material_thumb_${item.id}_$index"),
                        )
                    }
                    if (paths.size > 8) {
                        item {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(2.dp)).background(ColorSurfaceHighlight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+${paths.size - 8}", style = TextStyle(fontSize = 11.sp, color = ColorTextMuted))
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                if (showReorder) {
                    // M4: кнопки-стрелки вместо drag&drop — порядок сохраняется в sortOrder.
                    // ponytail: перестановка соседних элементов; drag-reorder добавить, если
                    // списки станут длинными настолько, что стрелками листать надоест.
                    RowIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        description = "Выше",
                        enabled = canMoveUp,
                        testTag = "material_up_${item.id}",
                    ) { actions.onReorder(siblings, item, -1) }
                    RowIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        description = "Ниже",
                        enabled = canMoveDown,
                        testTag = "material_down_${item.id}",
                    ) { actions.onReorder(siblings, item, 1) }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                RowIconButton(
                    icon = Icons.Default.Edit,
                    description = "Переименовать",
                    enabled = true,
                    testTag = "material_rename_${item.id}",
                ) { actions.onRename(item) }
                if (isArchiveRow) {
                    // W11: в Архиве кнопка-архив превращается в восстановление
                    RowIconButton(
                        icon = Icons.Default.Unarchive,
                        description = "Вернуть из Архива",
                        enabled = true,
                        testTag = "material_restore_${item.id}",
                    ) { actions.onSetArchived(item, false) }
                } else {
                    RowIconButton(
                        icon = Icons.Default.Inventory2,
                        description = "Убрать в Архив",
                        enabled = true,
                        testTag = "material_archive_${item.id}",
                    ) { actions.onSetArchived(item, true) }
                }
                RowIconButton(
                    icon = Icons.Default.Delete,
                    description = "Удалить",
                    enabled = true,
                    danger = true,
                    testTag = "material_delete_${item.id}",
                ) { actions.onDelete(item) }
            }
        }
    }
}

/** Подпись строки материала: тип • размер • объём. `sizeBytes` приходит из предпосчитанной карты. */
private fun materialMeta(
    item: MaterialItemEntity,
    sizeBytes: Long,
): String {
    val size = MaterialImporter.humanSize(sizeBytes)
    return when (item.kindEnum) {
        MaterialKind.TEXT -> "Текст • ${item.textContent.length} симв."
        MaterialKind.IMAGES -> "Набор: ${item.imagePaths.size} изобр." + if (size != "0 Б") " • $size" else ""
        MaterialKind.FILE ->
            buildString {
                append(item.ext.uppercase().ifBlank { "ФАЙЛ" })
                if (size != "0 Б") append(" • $size")
                if (item.pageCount > 0) append(" • ${item.pageCount} стр.")
            }
    }
}

@Composable
private fun RowIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    testTag: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier =
        Modifier
            .size(44.dp)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint =
                when {
                    !enabled -> ColorTextMuted.copy(alpha = 0.35f)
                    danger -> ColorDangerFill
                    else -> ColorBrandBlue
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** M6: пустое состояние с объяснением «как добавить» — экран не должен выглядеть сломанным. */
@Composable
private fun MaterialEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    showStorageNote: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ColorSurfaceHighlight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle))
            if (hint.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = hint,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("material_empty_hint"),
                )
                if (showStorageNote) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Материалы хранятся только на этом телефоне",
                        style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            // N12: опциональное действие — выход из тупикового пустого состояния
            if (action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                action()
            }
        }
    }
}
