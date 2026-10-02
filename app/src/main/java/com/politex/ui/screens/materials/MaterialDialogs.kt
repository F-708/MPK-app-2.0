package com.politex.ui.screens.materials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable

/**
 * M3: диалоги материальной базы — выбор типа импорта, «куда положить», набор текста,
 * переименование и подтверждение удаления. Стиль тот же, что у TaskEditDialog:
 * RoundedCornerShape(2.dp), ColorBgMain, хитбоксы ≥ 44dp, тап вне диалога не закрывает
 * (набранный текст не должен теряться от случайного касания).
 */

/** M3: шаг 1 импорта — что именно загружаем. */
@Composable
fun MaterialImportTypeDialog(
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onPickImages: () -> Unit,
    onPickText: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitle("Добавить материал", onDismiss) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogActionRow(
                    icon = Icons.Default.Description,
                    title = "Файл",
                    subtitle = "PDF, DOCX, XLS и другие документы",
                    testTag = "import_type_file",
                    onClick = onPickFile,
                )
                DialogActionRow(
                    icon = Icons.Default.Image,
                    title = "Набор изображений",
                    subtitle = "Фото страниц и доски — один материал",
                    testTag = "import_type_images",
                    onClick = onPickImages,
                )
                DialogActionRow(
                    icon = Icons.Default.TextFields,
                    title = "Текст",
                    subtitle = "Записать конспект или объявление вручную",
                    testTag = "import_type_text",
                    onClick = onPickText,
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

/** M3: шаг 2 — ввод текста материала. */
@Composable
fun MaterialTextDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitle("Текстовый материал", onDismiss) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст", fontSize = 12.sp) },
                    placeholder = { Text("Первая строка станет названием", fontSize = 12.sp) },
                    minLines = 5,
                    maxLines = 12,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("material_text_field"),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Текст сохраняется в базе и участвует в поиске — в том числе с опечатками.",
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        },
        confirmButton = {
            DialogButton(text = "СОХРАНИТЬ", color = ColorBrandBlue, enabled = text.isNotBlank(), testTag = "material_text_save") {
                onConfirm(text)
            }
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "material_text_cancel", onClick = onDismiss)
        },
    )
}

/**
 * M3: шаг 2 для файлов и изображений — КУДА положить.
 * Предмет: справочник MpkCurriculum для специальности/курса группы + уже использованные
 * предметы + «Другое…» (своё название). Папка: существующие папки предмета + «Новая папка».
 * Архив в список назначения не попадает — в него переносят кнопкой, а не при импорте.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDestinationDialog(
    curriculumSubjects: List<String>,
    folders: List<MaterialFolderEntity>,
    onDismiss: () -> Unit,
    onConfirm: (subject: String, folderId: Long?, newFolderName: String) -> Unit,
) {
    val knownSubjects = remember(folders) {
        folders.filterNot { it.isArchive }.map { it.subjectName }.distinct()
    }
    val subjectOptions = remember(curriculumSubjects, knownSubjects) {
        (curriculumSubjects + knownSubjects).distinct().sorted()
    }

    var subjectExpanded by remember { mutableStateOf(false) }
    var isCustomSubject by remember { mutableStateOf(false) }
    var customSubject by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(subjectOptions.firstOrNull() ?: "") }

    val subject = if (isCustomSubject) customSubject.trim() else selectedSubject
    val subjectFolders = remember(folders, subject) {
        folders.filter { !it.isArchive && it.subjectName.equals(subject, ignoreCase = true) }
    }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var newFolderMode by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // смена предмета сбрасывает выбор папки: папки принадлежат предмету
    LaunchedEffect(subject) {
        selectedFolderId = null
        newFolderMode = folders.none { !it.isArchive && it.subjectName.equals(subject, ignoreCase = true) }
    }

    val canConfirm = subject.isNotBlank() &&
        (if (newFolderMode) newFolderName.trim().isNotBlank() else selectedFolderId != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitle("Куда положить?", onDismiss) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    ToggleChip("Из списка", !isCustomSubject, Modifier.weight(1f)) { isCustomSubject = false }
                    ToggleChip("Другое…", isCustomSubject, Modifier.weight(1f)) { isCustomSubject = true }
                }

                if (isCustomSubject) {
                    OutlinedTextField(
                        value = customSubject,
                        onValueChange = { customSubject = it },
                        label = { Text("Свой предмет", fontSize = 12.sp) },
                        placeholder = { Text("Например: Репетитор", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dest_custom_subject_field"),
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = subjectExpanded, onExpandedChange = { subjectExpanded = it }) {
                        OutlinedTextField(
                            value = selectedSubject,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Предмет", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("dest_subject_field"),
                        )
                        ExposedDropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                            subjectOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSubject = option
                                        selectedFolderId = null
                                        subjectExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Text("Папка:", style = TextStyle(fontSize = 12.sp, color = ColorTextMuted))

                if (subjectFolders.isEmpty() && !newFolderMode) {
                    Text(
                        text = "У предмета пока нет папок — создайте первую.",
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    )
                }
                subjectFolders.forEach { folder ->
                    FolderPickRow(folder = folder, selected = selectedFolderId == folder.id) {
                        selectedFolderId = folder.id
                        newFolderMode = false
                    }
                }

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, if (newFolderMode) ColorBrandBlue else ColorBorderLight),
                    color = if (newFolderMode) ColorBrandBlue.copy(alpha = 0.08f) else ColorBgMain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .bouncyClickable {
                            newFolderMode = true
                            selectedFolderId = null
                        }
                        .testTag("dest_new_folder_row"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Новая папка",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ColorTextTitle),
                        )
                    }
                }

                if (newFolderMode) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Название папки", fontSize = 12.sp) },
                        placeholder = { Text("Например: Лекции, Лабораторные", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dest_new_folder_field"),
                    )
                }
            }
        },
        confirmButton = {
            DialogButton(
                text = "ПОЛОЖИТЬ",
                color = ColorBrandBlue,
                enabled = canConfirm,
                testTag = "dest_confirm_btn",
            ) {
                onConfirm(subject, selectedFolderId, newFolderName.trim())
            }
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "dest_cancel_btn", onClick = onDismiss)
        },
    )
}

/** M4: переименование материала или папки. */
@Composable
fun MaterialRenameDialog(
    initial: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitle(label, onDismiss) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Название", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_field"),
            )
        },
        confirmButton = {
            DialogButton(text = "СОХРАНИТЬ", color = ColorBrandBlue, enabled = value.isNotBlank(), testTag = "rename_confirm_btn") {
                onConfirm(value.trim())
            }
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "rename_cancel_btn", onClick = onDismiss)
        },
    )
}

/** M4: подтверждение удаления — операция стирает и запись, и файл с диска, отменить нельзя. */
@Composable
fun MaterialDeleteDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle)) },
        text = { Text(message, style = TextStyle(fontSize = 13.sp, color = ColorTextBody)) },
        confirmButton = {
            DialogButton(text = "УДАЛИТЬ", color = ColorDangerFill, testTag = "delete_confirm_btn", onClick = onConfirm)
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "delete_cancel_btn", onClick = onDismiss)
        },
    )
}

// ── Мелкие строительные блоки диалогов ───────────────────────────────────────

@Composable
private fun DialogTitle(text: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle))
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = ColorTextMuted)
        }
    }
}

@Composable
private fun DialogActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBorderLight),
        color = ColorSurfaceVariantLight,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .bouncyClickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ColorBrandBlue, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorTextTitle))
                Text(
                    text = subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        }
    }
}

@Composable
private fun FolderPickRow(folder: MaterialFolderEntity, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorBrandBlue else ColorBorderLight),
        color = if (selected) ColorBrandBlue.copy(alpha = 0.08f) else ColorBgMain,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .bouncyClickable(onClick = onClick)
            .testTag("dest_folder_${folder.id}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (selected) ColorBrandBlue else ColorTextMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = ColorTextTitle,
                ),
            )
        }
    }
}

@Composable
private fun ToggleChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorBrandBlue else ColorBorderLight),
        color = if (selected) ColorBrandBlue else ColorBgMain,
        contentColor = if (selected) Color.White else ColorTextBody,
        modifier = modifier
            .heightIn(min = 36.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(text, maxLines = 1, style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp))
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    color: Color,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(
                text = text,
                maxLines = 1,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (enabled) color else ColorTextMuted.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

/** Кнопка-подложка фирменного цвета (используется в шапке раздела). */
@Composable
fun BrandActionButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue),
        color = if (filled) ColorBrandFill else ColorBgMain,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .bouncyClickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (filled) Color.White else ColorBrandBlue,
                modifier = Modifier.size(16.dp),
            )
            if (label.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label.uppercase(),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (filled) Color.White else ColorBrandBlue,
                    ),
                )
            }
        }
    }
}
