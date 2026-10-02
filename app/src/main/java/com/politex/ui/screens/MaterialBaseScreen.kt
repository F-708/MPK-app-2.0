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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.data.model.GroupInfo
import com.politex.ui.screens.materials.BrandActionButton
import com.politex.ui.screens.materials.MaterialCard
import com.politex.ui.screens.materials.MaterialDeleteDialog
import com.politex.ui.screens.materials.MaterialDestinationDialog
import com.politex.ui.screens.materials.MaterialDocTextViewer
import com.politex.ui.screens.materials.MaterialImageViewer
import com.politex.ui.screens.materials.MaterialImportTypeDialog
import com.politex.ui.screens.materials.MaterialPdfViewer
import com.politex.ui.screens.materials.MaterialRenameDialog
import com.politex.ui.screens.materials.MaterialTextDialog
import com.politex.ui.screens.materials.PdfSearchHintPlaque
import com.politex.ui.screens.materials.materialIconFor
import com.politex.ui.screens.materials.materialIconTint
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
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
// M1–M6: «Материальная база» — локальное хранилище раздаточных материалов.
//
//  M1 хранение: байты в filesDir/materials, в БД только метаданные
//  M2 просмотрщики: PDF (PdfRenderer + закладки), DOCX/TXT, изображения (зум + поворот)
//  M3 импорт: SAF-пикеры → «куда положить» (предмет + папка), авто-папка «Архив»
//  M4 список: предметы → папки → материалы, переименование/удаление/архив/порядок
//  M5 поиск: Даммерау-Левенштейн ≤2 + префикс ≥4, ё→е, тогл регистра «Aa»
//  M6 навигация: sealed-состояния экранов и СВОЙ back-стек (без Navigation), системный back
// ─────────────────────────────────────────────────────────────────────────────

/** Сколько материалов показывать в корне под папкой, прежде чем предложить «показать все». */
private const val ROOT_PREVIEW_LIMIT = 4

/**
 * M6: запись back-стека. Кодирование строкой («f:12», «i:7», «s:-1») — чтобы стек переживал
 * смерть процесса через rememberSaveable без кастомного Saver'а: ArrayList<String> в Bundle
 * кладётся штатно, а sealed-класс пришлось бы писать Parcelable/Saver вручную.
 */
private sealed interface MaterialRoute {
    /** Экран папки. */
    data class FolderScreen(val folderId: Long) : MaterialRoute {
        override fun toString() = "f:$folderId"
    }

    /** Просмотрщик материала. */
    data class Viewer(val itemId: Long) : MaterialRoute {
        override fun toString() = "i:$itemId"
    }

    /** Поиск; folderId = -1 — по всей базе, иначе по содержимому папки. */
    data class SearchScreen(val folderId: Long) : MaterialRoute {
        override fun toString() = "s:$folderId"
    }

    companion object {
        fun decode(raw: String): MaterialRoute? {
            val id = raw.substringAfter(':', "").toLongOrNull() ?: return null
            return when (raw.substringBefore(':', "")) {
                "f" -> FolderScreen(id)
                "i" -> Viewer(id)
                "s" -> SearchScreen(id)
                else -> null
            }
        }
    }
}

/** Набор действий над материалом — передаётся в списки, чтобы не плодить 5 callback-параметров. */
private class MaterialActions(
    val onOpen: (MaterialItemEntity) -> Unit,
    val onRename: (MaterialItemEntity) -> Unit,
    val onArchive: (MaterialItemEntity) -> Unit,
    val onDelete: (MaterialItemEntity) -> Unit,
    /** list — упорядоченный список той папки, где лежит item; delta = ±1. */
    val onReorder: (List<MaterialItemEntity>, MaterialItemEntity, Int) -> Unit,
    /**
     * Размер файлов материала в байтах. Берётся из предпосчитанной карты, а НЕ вызовом
     * `File.length()` в композиции: stat каждого файла на главном потоке на каждой
     * строке списка — это дропы кадров и StrictMode-диско-вечеринка.
     */
    val sizeOf: (MaterialItemEntity) -> Long
)

/**
 * Ф4/M1: «Материальная база».
 *
 * Экран-состояние (список / папка / просмотрщик / поиск) с собственным back-стеком:
 * Navigation в проекте не используется, а вкладка «Уроки» живёт внутри LessonsHubScreen,
 * поэтому вложенная навигация сделана как rememberSaveable-стек + системный BackHandler.
 */
@Composable
fun MaterialBaseScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember { MpkDatabase.getInstance(context).materialDao() }
    val scope = rememberCoroutineScope()

    val folders by dao.getAllFolders().collectAsState(initial = emptyList())
    val items by dao.getAllItems().collectAsState(initial = emptyList())

    // M4: размеры файлов считаем ВНЕ главного потока — File.length() это дисковый stat,
    // а строк в списке может быть много. Карта пересчитывается при любом изменении items.
    val sizesState = remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    LaunchedEffect(items) {
        sizesState.value = withContext(Dispatchers.IO) {
            items.associate { it.id to MaterialImporter.storedSize(it) }
        }
    }

    // M6: back-стек одной строкой через «|» (см. KDoc MaterialRoute)
    val stackState = rememberSaveable { mutableStateOf("") }
    val stack = remember(stackState.value) {
        if (stackState.value.isEmpty()) emptyList() else stackState.value.split('|').mapNotNull(MaterialRoute.Companion::decode)
    }
    val push: (MaterialRoute) -> Unit = { route ->
        stackState.value = if (stackState.value.isEmpty()) route.toString() else stackState.value + "|" + route
    }
    val pop: () -> Unit = { stackState.value = stackState.value.substringBeforeLast('|', "") }

    // M3: папка «Архив» появляется один раз при первом входе в раздел
    LaunchedEffect(Unit) { MaterialImporter.ensureArchiveFolder(dao) }

    val curriculumSubjects = remember(groupInfo.specialtyCode, groupInfo.course) {
        MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course)
    }

    // ── Состояния диалогов импорта/правок ────────────────────────────────────
    var showImportType by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var showDestination by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingMaterial?>(null) }
    var renamingItem by remember { mutableStateOf<MaterialItemEntity?>(null) }
    var renamingFolder by remember { mutableStateOf<MaterialFolderEntity?>(null) }
    var deletingItem by remember { mutableStateOf<MaterialItemEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<MaterialFolderEntity?>(null) }
    var importing by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<Banner?>(null) }

    fun showOk(text: String) { banner = Banner(text, isError = false) }
    fun showErr(text: String) { banner = Banner(text, isError = true) }

    // ── M3: SAF-пикеры ───────────────────────────────────────────────────────
    // OpenDocument не умеет «всё, кроме image/*», поэтому одиночные картинки отсекаются
    // уже в результате — иначе фото доски станет материалом из одной страницы вместо набора.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: ""
        if (mime.startsWith("image/")) {
            showErr("Картинки загружайте через «Набор изображений»: так лекция из 12 фото останется одним материалом")
            return@rememberLauncherForActivityResult
        }
        pending = PendingMaterial.SingleFile(uri)
        showDestination = true
    }
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        pending = PendingMaterial.Images(uris)
        showDestination = true
    }

    // ── Операции над данными ─────────────────────────────────────────────────
    fun importInto(folderId: Long, material: PendingMaterial) {
        importing = true
        scope.launch {
            val outcome = runCatching { MaterialImporter.importMaterial(context, dao, folderId, material) }
            importing = false
            outcome
                .onSuccess { showOk("Материал добавлен") }
                .onFailure { showErr("Не удалось импортировать: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun deleteItem(item: MaterialItemEntity) {
        scope.launch {
            // M1: файл стирается вместе с записью, иначе filesDir обрастает мусором
            MaterialImporter.deleteStoredFiles(item)
            dao.deleteItemWithBookmarks(item.id)
        }
    }

    fun deleteFolder(folder: MaterialFolderEntity) {
        scope.launch {
            dao.getForFolderOnce(folder.id).forEach {
                MaterialImporter.deleteStoredFiles(it)
                dao.deleteItemWithBookmarks(it.id)
            }
            dao.deleteFolderById(folder.id)
        }
    }

    fun archiveItem(item: MaterialItemEntity) {
        scope.launch {
            val archiveId = MaterialImporter.ensureArchiveFolder(dao)
            if (item.folderId == archiveId) {
                showErr("Материал уже в Архиве")
                return@launch
            }
            dao.moveItemToFolder(item.id, archiveId, dao.nextItemSortOrder(archiveId))
            showOk("Перемещено в Архив")
        }
    }

    val actions = remember {
        MaterialActions(
            onOpen = { item -> push(MaterialRoute.Viewer(item.id)) },
            onRename = { item -> renamingItem = item },
            onArchive = { item -> archiveItem(item) },
            onDelete = { item -> deletingItem = item },
            onReorder = { list, item, delta ->
                val index = list.indexOfFirst { it.id == item.id }
                val neighbour = list.getOrNull(index + delta)
                if (neighbour != null) scope.launch { dao.swapSortOrder(item.id, neighbour.id) }
            },
            sizeOf = { item -> sizesState.value[item.id] ?: 0L }
        )
    }

    // Системный back: сначала выходим из вложенного экрана, и только потом — из раздела
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val itemsByFolder = remember(items) { items.groupBy { it.folderId } }
    val currentRoute = stack.lastOrNull()
    val viewerItem = (currentRoute as? MaterialRoute.Viewer)?.let { route -> items.find { it.id == route.itemId } }

    Box(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val viewer = currentRoute as? MaterialRoute.Viewer
            if (viewer != null && viewerItem != null) {
                // M2: выбор просмотрщика — по типу материала, а не только по расширению
                when {
                    viewerItem.kindEnum == MaterialKind.IMAGES || viewerItem.imagePaths.isNotEmpty() ->
                        MaterialImageViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())

                    viewerItem.ext == "pdf" ->
                        MaterialPdfViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())

                    else ->
                        MaterialDocTextViewer(item = viewerItem, onBack = pop, modifier = Modifier.fillMaxSize())
                }
            } else if (viewer != null) {
                // Материал удалён (или список ещё не доехал) — не держим пользователя в пустоте
                LaunchedEffect(Unit) { pop() }
            } else {
                val searchRoute = currentRoute as? MaterialRoute.SearchScreen
                val folderRoute = currentRoute as? MaterialRoute.FolderScreen
                val folder = folderRoute?.let { route -> folders.find { it.id == route.folderId } }

                MaterialHeader(
                    groupInfo = groupInfo,
                    itemsCount = items.size,
                    searchScope = folderRoute?.folderId ?: -1,
                    onSearch = { scopeId -> push(MaterialRoute.SearchScreen(scopeId)) },
                    searchEnabled = searchRoute == null,
                    onAdd = { showImportType = true },
                    subtitleOverride = folder?.let { "${it.subjectName} • ${it.name}" }
                )

                when {
                    searchRoute != null -> MaterialSearchContent(
                        scopeFolderId = searchRoute.folderId,
                        allItems = items,
                        folders = folders,
                        actions = actions,
                        modifier = Modifier.weight(1f)
                    )

                    folder != null -> MaterialFolderContent(
                        folder = folder,
                        items = itemsByFolder[folder.id].orEmpty(),
                        actions = actions,
                        onRenameFolder = { renamingFolder = folder },
                        onDeleteFolder = { deletingFolder = folder },
                        modifier = Modifier.weight(1f)
                    )

                    folderRoute != null -> {
                        // Папка удалена (или список папок ещё не доехал) — возвращаемся в корень
                        LaunchedEffect(Unit) { pop() }
                    }

                    else -> MaterialRootList(
                        folders = folders,
                        itemsByFolder = itemsByFolder,
                        actions = actions,
                        onOpenFolder = { f -> push(MaterialRoute.FolderScreen(f.id)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (importing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(2.dp), color = ColorBgMain, border = BorderStroke(1.dp, ColorBorderLight)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
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
            }
        )
    }

    if (showTextInput) {
        MaterialTextDialog(
            onDismiss = { showTextInput = false },
            onConfirm = { text ->
                showTextInput = false
                pending = PendingMaterial.Text(text)
                showDestination = true
            }
        )
    }

    if (showDestination) {
        val material = pending
        MaterialDestinationDialog(
            curriculumSubjects = curriculumSubjects,
            folders = folders,
            onDismiss = {
                showDestination = false
                pending = null
            },
            onConfirm = { subject, folderId, newFolderName ->
                showDestination = false
                pending = null
                if (material == null) return@MaterialDestinationDialog
                scope.launch {
                    val targetId = folderId ?: runCatching {
                        dao.insertFolder(
                            MaterialFolderEntity(
                                subjectName = subject,
                                name = newFolderName,
                                sortOrder = dao.nextFolderSortOrder(subject)
                            )
                        )
                    }.getOrElse { -1L }
                    if (targetId < 0) {
                        showErr("Не удалось создать папку «$newFolderName»")
                        return@launch
                    }
                    importInto(targetId, material)
                }
            }
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
            }
        )
    }

    renamingFolder?.let { folder ->
        MaterialRenameDialog(
            initial = folder.name,
            label = "Переименовать папку",
            onDismiss = { renamingFolder = null },
            onConfirm = { newName ->
                renamingFolder = null
                scope.launch { dao.updateFolder(folder.copy(name = newName)) }
            }
        )
    }

    deletingItem?.let { item ->
        MaterialDeleteDialog(
            title = "Удалить материал?",
            message = "«${item.title}» будет удалён вместе с файлом (${MaterialImporter.humanSize(actions.sizeOf(item))}). Отменить нельзя.",
            onDismiss = { deletingItem = null },
            onConfirm = {
                deletingItem = null
                deleteItem(item)
            }
        )
    }

    deletingFolder?.let { folder ->
        val count = itemsByFolder[folder.id]?.size ?: 0
        MaterialDeleteDialog(
            title = "Удалить папку?",
            message = if (count == 0) {
                "Папка «${folder.name}» пуста и будет удалена."
            } else {
                "Вместе с папкой «${folder.name}» удалится материалов: $count (и их файлы). Отменить нельзя."
            },
            onDismiss = { deletingFolder = null },
            onConfirm = {
                deletingFolder = null
                deleteFolder(folder)
                pop()
            }
        )
    }
}

/** Короткое сообщение об успехе/ошибке вместо SnackbarHost (его в этот экран не передают). */
private data class Banner(val text: String, val isError: Boolean)

@Composable
private fun MessageBanner(banner: Banner, onClose: () -> Unit, modifier: Modifier = Modifier) {
    // M1: само исчезает, чтобы не перекрывать список навсегда
    LaunchedEffect(banner) {
        delay(4000)
        onClose()
    }
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (banner.isError) ColorError else ColorBorderLight),
        color = if (banner.isError) ColorError.copy(alpha = 0.08f) else ColorSurfaceHighlight,
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (banner.isError) "material_error_banner" else "material_info_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = banner.text,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = TextStyle(fontSize = 12.sp, color = if (banner.isError) ColorError else ColorTextBody)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = ColorTextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Шапка раздела: заголовок, счётчик, поиск и «+». Стиль — как у TasksScreen. */
@Composable
private fun MaterialHeader(
    groupInfo: GroupInfo,
    itemsCount: Int,
    searchScope: Long,
    onSearch: (Long) -> Unit,
    onAdd: () -> Unit,
    subtitleOverride: String? = null,
    // Уже внутри поиска — вторая кнопка поиска только плодила бы дубли в back-стеке
    searchEnabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Материальная база", style = TextStylePageTitle, maxLines = 1)
            Text(
                text = subtitleOverride ?: "Группа ${groupInfo.canonicalName} • $itemsCount материалов",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorBrandBlue)
            )
        }
        if (searchEnabled) {
            BrandActionButton(
                icon = Icons.Default.Search,
                label = "",
                testTag = "material_search_btn",
                filled = false,
                onClick = { onSearch(searchScope) }
            )
        }
        BrandActionButton(
            icon = Icons.Default.Add,
            label = "ДОБАВИТЬ",
            testTag = "material_add_btn",
            onClick = onAdd
        )
    }
}

/** Строки корневого списка: секция предмета → папка → материалы (с лимитом превью). */
private sealed interface RootRow {
    data class SubjectHeader(val subject: String, val count: Int) : RootRow
    data class FolderHeader(val folder: MaterialFolderEntity, val count: Int) : RootRow
    data class ItemRow(val item: MaterialItemEntity, val siblings: List<MaterialItemEntity>) : RootRow
    data class MoreRow(val folder: MaterialFolderEntity, val hidden: Int) : RootRow
}

/**
 * M4: корень — секции предметов. Пустые предметы из справочника НЕ показываем: у 4 курса их
 * десятки, и студент утонет в пустых заголовках. Архив всегда последним.
 */
@Composable
private fun MaterialRootList(
    folders: List<MaterialFolderEntity>,
    itemsByFolder: Map<Long, List<MaterialItemEntity>>,
    actions: MaterialActions,
    onOpenFolder: (MaterialFolderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(folders, itemsByFolder) {
        val bySubject = folders.groupBy { it.subjectName }
        buildList<RootRow> {
            bySubject.keys
                .sortedWith(compareBy({ it == MaterialFolderEntity.ARCHIVE_SUBJECT }, { it }))
                .forEach { subject ->
                    val subjectFolders = bySubject.getValue(subject)
                    val total = subjectFolders.sumOf { itemsByFolder[it.id]?.size ?: 0 }
                    add(RootRow.SubjectHeader(subject, total))
                    subjectFolders.forEach { folder ->
                        val list = itemsByFolder[folder.id].orEmpty()
                        add(RootRow.FolderHeader(folder, list.size))
                        list.take(ROOT_PREVIEW_LIMIT).forEach { add(RootRow.ItemRow(it, list)) }
                        if (list.size > ROOT_PREVIEW_LIMIT) {
                            add(RootRow.MoreRow(folder, list.size - ROOT_PREVIEW_LIMIT))
                        }
                    }
                }
        }
    }

    val totalCount = itemsByFolder.values.sumOf { it.size }
    if (rows.none { it is RootRow.ItemRow }) {
        MaterialEmptyState(
            title = if (totalCount == 0) "Материалов пока нет" else "В этой папке пусто",
            hint = "Нажмите «ДОБАВИТЬ» и выберите файл, набор изображений или текст. " +
                "Материал ляжет в папку предмета — потом его можно переименовать, " +
                "перенести в Архив и читать прямо в приложении.",
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("material_root_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(count = rows.size, key = { index -> rootRowKey(rows[index]) }) { index ->
            when (val row = rows[index]) {
                is RootRow.SubjectHeader -> SubjectHeaderRow(row.subject, row.count)

                is RootRow.FolderHeader -> FolderHeaderRow(
                    folder = row.folder,
                    count = row.count,
                    onOpen = { onOpenFolder(row.folder) }
                )

                is RootRow.ItemRow -> MaterialItemRow(
                    item = row.item,
                    actions = actions,
                    showReorder = false,
                    matchCount = null
                )

                is RootRow.MoreRow -> Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .bouncyClickable { onOpenFolder(row.folder) }
                        .testTag("material_more_${row.folder.id}")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Показать все (ещё ${row.hidden})",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorBrandBlue)
                        )
                    }
                }
            }
        }
    }
}

/** Ключ строки списка: стабильный, иначе LazyColumn пересоздаёт элементы при любом изменении. */
private fun rootRowKey(row: RootRow): Any = when (row) {
    is RootRow.SubjectHeader -> "subject:${row.subject}"
    is RootRow.FolderHeader -> "folder:${row.folder.id}"
    is RootRow.ItemRow -> "item:${row.item.id}"
    is RootRow.MoreRow -> "more:${row.folder.id}"
}

/**
 * M4: экран папки — тот же список материалов, но с кнопками порядка (◀ ▶) и правкой папки.
 * Кнопки порядка видны только здесь: в корне они бессмысленны (материалы показаны обрезком).
 */
@Composable
private fun MaterialFolderContent(
    folder: MaterialFolderEntity,
    items: List<MaterialItemEntity>,
    actions: MaterialActions,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = if (folder.isArchive) Icons.Default.Inventory2 else Icons.Default.Folder,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle)
                )
                Text(
                    text = "${folder.subjectName} • ${items.size} материалов",
                    maxLines = 1,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted)
                )
            }
            IconButton(onClick = onRenameFolder, modifier = Modifier.size(44.dp).testTag("folder_rename_btn")) {
                Icon(Icons.Default.Edit, contentDescription = "Переименовать папку", tint = ColorTextMuted, modifier = Modifier.size(18.dp))
            }
            if (!folder.isArchive) {
                IconButton(onClick = onDeleteFolder, modifier = Modifier.size(44.dp).testTag("folder_delete_btn")) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить папку", tint = ColorTextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(1.dp).background(ColorDividerLight))

        if (items.isEmpty()) {
            MaterialEmptyState(
                title = if (folder.isArchive) "В Архиве пусто" else "В папке пока ничего нет",
                hint = if (folder.isArchive) {
                    "Сюда переносятся материалы кнопкой-архивом: не удаляются, а убираются с глаз."
                } else {
                    "Нажмите «ДОБАВИТЬ» вверху и выберите этот предмет — файл ляжет в эту папку."
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("material_folder_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(count = items.size, key = { items[it].id }) { index ->
                    MaterialItemRow(
                        item = items[index],
                        actions = actions,
                        showReorder = true,
                        matchCount = null,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.size - 1,
                        siblings = items
                    )
                }
            }
        }
    }
}

/**
 * M5: поиск по текущей области (папка или вся база).
 *
 * Ищем НЕЧЁТКО по `title + textContent`: FuzzySearch допускает опечатки (Даммерау-Левенштейн ≤2
 * для слов от 4 символов) и совпадение префикса от 4 символов, «ё» приводится к «е».
 * Тогл «Aa» включает регистр-чувствительность.
 *
 * PDF в выдаче участвует только названием: текста внутри PDF мы не читаем (pdfbox = +5 МБ к APK),
 * поэтому показываем честную плашку-подсказку.
 */
@Composable
private fun MaterialSearchContent(
    scopeFolderId: Long,
    allItems: List<MaterialItemEntity>,
    folders: List<MaterialFolderEntity>,
    actions: MaterialActions,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var caseSensitive by rememberSaveable { mutableStateOf(false) }

    val scope = remember(allItems, scopeFolderId) {
        if (scopeFolderId < 0) allItems else allItems.filter { it.folderId == scopeFolderId }
    }

    // Тяжёлая часть (разбор текста на слова) считается на каждый запрос; для сотен материалов
    // это доли миллисекунд. См. ponytail-комментарий в FuzzySearch про порог перехода на FTS.
    val hits = remember(scope, query, caseSensitive) {
        val q = query.trim()
        if (q.isEmpty()) return@remember emptyList()
        scope.mapNotNull { item ->
            val score = FuzzySearch.score(item.title + "\n" + item.textContent, q, caseSensitive)
            if (score > 0) item to score else null
        }.sortedWith(compareByDescending<Pair<MaterialItemEntity, Int>> { it.second }.thenBy { it.first.title })
    }

    val queryWords = remember(query, caseSensitive) { FuzzySearch.queryWordCount(query, caseSensitive) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по материалам", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .testTag("material_search_field")
            )
            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, if (caseSensitive) ColorBrandBlue else ColorBorderLight),
                color = if (caseSensitive) ColorBrandFill else ColorBgMain,
                modifier = Modifier
                    .size(44.dp)
                    .bouncyClickable { caseSensitive = !caseSensitive }
                    .testTag("material_search_case_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aa",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (caseSensitive) Color.White else ColorTextBody
                        )
                    )
                }
            }
        }

        val scopeName = if (scopeFolderId < 0) {
            "вся база"
        } else {
            folders.find { it.id == scopeFolderId }?.let { "${it.subjectName} • ${it.name}" } ?: "папка"
        }
        Text(
            text = if (query.isBlank()) "Область поиска: $scopeName" else "Найдено ${hits.size} • область: $scopeName",
            style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).testTag("material_search_status")
        )

        if (query.isNotBlank()) {
            PdfSearchHintPlaque()
        }

        if (query.isBlank()) {
            MaterialEmptyState(
                title = "Что ищем?",
                hint = "Ищем по названию и по тексту DOCX/TXT — с допуском опечаток: " +
                    "«матиматика» найдет «математика». PDF находится только по названию.",
                modifier = Modifier.weight(1f)
            )
        } else if (hits.isEmpty()) {
            MaterialEmptyState(
                title = "Ничего не нашлось",
                hint = "Попробуйте короче (первые 4 буквы слова уже считаются совпадением) " +
                    "или выключите тогл «Aa», если текст набран в другом регистре.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("material_search_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(count = hits.size, key = { hits[it].first.id }) { index ->
                    val (item, score) = hits[index]
                    MaterialItemRow(
                        item = item,
                        actions = actions,
                        showReorder = false,
                        matchCount = score to queryWords
                    )
                }
            }
        }
    }
}

/**
 * M4: строка материала. Верх — иконка по расширению, название, «тип • размер • страницы»;
 * для набора изображений под названием горизонтальная мини-лента превью.
 * Низ — действия: порядок (только в папке), переименовать, в Архив, удалить.
 * Действия отдельной строкой, а не тремя иконками в одну линию: хитбокс каждой кнопки 44dp,
 * иначе на телефоне промахиваешься и случайно удаляешь материал.
 */
@Composable
private fun MaterialItemRow(
    item: MaterialItemEntity,
    actions: MaterialActions,
    showReorder: Boolean,
    matchCount: Pair<Int, Int>?,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    siblings: List<MaterialItemEntity> = emptyList()
) {
    MaterialCard(testTag = "material_item_${item.id}", onClick = { actions.onOpen(item) }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ColorSurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = materialIconFor(item),
                        contentDescription = null,
                        tint = materialIconTint(item),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorTextTitle)
                    )
                    Text(
                        text = materialMeta(item, actions.sizeOf(item)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 11.sp, color = ColorTextMuted)
                    )
                }
                if (matchCount != null) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBrandBlue.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("material_match_${item.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text(
                                text = "${matchCount.first}/${matchCount.second}",
                                maxLines = 1,
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = ColorBrandBlue)
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
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ColorSurfaceHighlight)
                                .testTag("material_thumb_${item.id}_$index")
                        )
                    }
                    if (paths.size > 8) {
                        item {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(2.dp)).background(ColorSurfaceHighlight),
                                contentAlignment = Alignment.Center
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
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                if (showReorder) {
                    // M4: кнопки-стрелки вместо drag&drop — порядок сохраняется в sortOrder.
                    // ponytail: перестановка соседних элементов; drag-reorder добавить, если
                    // списки станут длинными настолько, что стрелками листать надоест.
                    RowIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        description = "Выше",
                        enabled = canMoveUp,
                        testTag = "material_up_${item.id}"
                    ) { actions.onReorder(siblings, item, -1) }
                    RowIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        description = "Ниже",
                        enabled = canMoveDown,
                        testTag = "material_down_${item.id}"
                    ) { actions.onReorder(siblings, item, 1) }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                RowIconButton(
                    icon = Icons.Default.Edit,
                    description = "Переименовать",
                    enabled = true,
                    testTag = "material_rename_${item.id}"
                ) { actions.onRename(item) }
                RowIconButton(
                    icon = Icons.Default.Archive,
                    description = "Переместить в Архив",
                    enabled = true,
                    testTag = "material_archive_${item.id}"
                ) { actions.onArchive(item) }
                RowIconButton(
                    icon = Icons.Default.Delete,
                    description = "Удалить",
                    enabled = true,
                    danger = true,
                    testTag = "material_delete_${item.id}"
                ) { actions.onDelete(item) }
            }
        }
    }
}

/** Подпись строки материала: тип • размер • объём. `sizeBytes` приходит из предпосчитанной карты. */
private fun materialMeta(item: MaterialItemEntity, sizeBytes: Long): String {
    val size = MaterialImporter.humanSize(sizeBytes)
    return when (item.kindEnum) {
        MaterialKind.TEXT -> "Текст • ${item.textContent.length} симв."
        MaterialKind.IMAGES -> "Набор: ${item.imagePaths.size} изобр." + if (size != "0 Б") " • $size" else ""
        MaterialKind.FILE -> buildString {
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
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = Modifier
            .size(44.dp)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = when {
                    !enabled -> ColorTextMuted.copy(alpha = 0.35f)
                    danger -> ColorDangerFill
                    else -> ColorBrandBlue
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Заголовок секции предмета. */
@Composable
private fun SubjectHeaderRow(subject: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
            .testTag("material_subject_$subject")
    ) {
        Text(
            text = subject,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle)
        )
        Text(
            text = "$count",
            style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ColorTextMuted)
        )
    }
}

/** Строка папки: тап открывает экран папки. У Архива своя иконка. */
@Composable
private fun FolderHeaderRow(folder: MaterialFolderEntity, count: Int, onOpen: () -> Unit) {
    MaterialCard(testTag = "material_folder_${folder.id}", onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (folder.isArchive) Icons.Default.Inventory2 else Icons.Default.Folder,
                contentDescription = null,
                tint = if (folder.isArchive) ColorTopBar else ColorBrandBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextTitle)
                )
                Text(
                    text = if (folder.isArchive) "отложенное, не удаляется" else "$count материалов",
                    maxLines = 1,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted)
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorTextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** M6: пустое состояние с объяснением «как добавить» — экран не должен выглядеть сломанным. */
@Composable
private fun MaterialEmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ColorSurfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hint,
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                modifier = Modifier.padding(horizontal = 12.dp).testTag("material_empty_hint")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Материалы хранятся только на этом телефоне",
                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
