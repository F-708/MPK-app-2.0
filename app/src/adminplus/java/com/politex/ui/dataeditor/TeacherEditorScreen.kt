package com.politex.ui.dataeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.adminplus.publish.AdminPlusPublishDeps
import com.politex.adminplus.publish.GithubTokenStore
import com.politex.adminplus.publish.PublishFailure
import com.politex.adminplus.publish.PublishResult
import com.politex.content.ContentAssets
import com.politex.content.OwnerOverridesStore
import com.politex.content.OwnerPhotoResizer
import com.politex.data.repository.Teacher
import com.politex.dataeditor.TeacherEditorForm
import com.politex.dataeditor.TeacherEditorLogic
import com.politex.dataeditor.TeacherEditorStatus
import com.politex.ui.components.AppCard
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorErrorText
import com.politex.ui.theme.ColorSuccessBg
import com.politex.ui.theme.ColorSuccessText
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextDisabled
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberTeachers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class PublishErrorUi(
    val message: String,
    val needsToken: Boolean,
)

internal fun publishErrorUi(failure: PublishFailure): PublishErrorUi =
    when (failure) {
        PublishFailure.NO_TOKEN -> PublishErrorUi("Токен публикации не настроен", needsToken = true)
        PublishFailure.AUTH -> PublishErrorUi("GitHub отклонил токен — обновите его в настройках токена", needsToken = true)
        PublishFailure.NETWORK -> PublishErrorUi("Нет связи с GitHub — проверьте интернет и повторите", needsToken = false)
        PublishFailure.CONFLICT, PublishFailure.SERVER -> PublishErrorUi("Сервер отклонил публикацию — повторите попытку", needsToken = false)
        PublishFailure.PAYLOAD -> PublishErrorUi("Не удалось подготовить изменения — повторите", needsToken = false)
    }

@Composable
fun TeacherEditorScreen(
    teacherName: String,
    onBack: () -> Unit,
    onOpenTokenSetup: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val teachers = rememberTeachers()
    val overridesRevision = OwnerOverridesStore.revision.intValue

    val teacher =
        remember(teachers, teacherName) {
            val key = OwnerOverridesStore.keyOf(teacherName)
            teachers.firstOrNull { OwnerOverridesStore.keyOf(it.name) == key || OwnerOverridesStore.keyOf(it.matchName) == key }
        }
    val override =
        remember(teacherName, overridesRevision) {
            OwnerOverridesStore.findTeacher(context, teacherName)
        }
    val baseForm =
        remember(teacher, override, teacherName) {
            TeacherEditorLogic.formFor(teacherName, teacher, override)
        }
    var edit by remember { mutableStateOf<TeacherEditorForm?>(null) }
    val form = edit ?: baseForm
    var saving by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(TeacherEditorStatus.IDLE) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showApplyConfirm by remember { mutableStateOf(false) }
    var showSubjectPicker by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var publishSent by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<PublishErrorUi?>(null) }

    LaunchedEffect(status) {
        if (status != TeacherEditorStatus.IDLE) {
            delay(2500)
            status = TeacherEditorStatus.IDLE
        }
    }

    LaunchedEffect(publishSent) {
        if (publishSent) {
            delay(4000)
            publishSent = false
        }
    }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            photoBusy = true
            scope.launch(Dispatchers.IO) {
                val old = form.pendingPhoto
                val tmp = File(context.cacheDir, "owner_photo_pending_${System.currentTimeMillis()}.jpg")
                val ok =
                    OwnerPhotoResizer.resizeJpeg(
                        open = { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() },
                        dest = tmp,
                    )
                old?.let { runCatching { it.delete() } }
                withContext(Dispatchers.Main) {
                    photoBusy = false
                    if (ok) {
                        edit = form.copy(pendingPhoto = tmp)
                    } else {
                        runCatching { tmp.delete() }
                        status = TeacherEditorStatus.ERROR
                    }
                }
            }
        }

    val dirty = TeacherEditorLogic.isDirty(form, baseForm)
    val validationError = TeacherEditorLogic.validate(form)
    val canSave = TeacherEditorLogic.canSave(form, baseForm, saving || photoBusy)
    val canApply = !saving && !photoBusy && !publishing && validationError == null && (dirty || override != null)

    fun saveLocalBlocking(): Boolean {
        val record =
            OwnerOverridesStore.upsertTeacher(
                context = context,
                key = form.key,
                fio = form.fio,
                position = form.position,
                phone = form.phone,
                subjects = form.subjects,
                experience = form.experience,
                category = form.category,
                photoSource = form.pendingPhoto,
            )
        if (record != null) OwnerOverridesStore.refreshDependentCaches(context)
        return record != null
    }

    fun startApply() {
        if (!GithubTokenStore.hasToken(context)) {
            publishError = PublishErrorUi("Токен публикации не настроен", needsToken = true)
            onOpenTokenSetup()
            return
        }
        publishing = true
        publishError = null
        publishSent = false
        scope.launch(Dispatchers.IO) {
            var failure: PublishErrorUi? = null
            if (TeacherEditorLogic.isDirty(form, baseForm) && !saveLocalBlocking()) {
                failure = PublishErrorUi("Не удалось сохранить правку на устройстве — повторите", needsToken = false)
            }
            if (failure == null) {
                val token = GithubTokenStore.load(context)
                val payload = AdminPlusPublishDeps.payloadBuilder(context)
                failure =
                    when {
                        token.isNullOrBlank() -> PublishErrorUi("Токен публикации не настроен", needsToken = true)
                        payload == null -> PublishErrorUi("Не удалось подготовить изменения — повторите", needsToken = false)
                        else ->
                            when (val result = AdminPlusPublishDeps.publisherFactory(context).publish(token, payload)) {
                                is PublishResult.Success -> null
                                is PublishResult.Failed -> publishErrorUi(result.failure)
                            }
                    }
            }
            withContext(Dispatchers.Main) {
                publishing = false
                if (failure == null) {
                    edit = null
                    publishSent = true
                } else {
                    publishError = failure
                }
            }
        }
    }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(ColorBgMain)
            .testTag("teacher_editor_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubPageHeader(
                title = teacher?.name ?: teacherName,
                subtitle =
                when {
                    publishing -> "Отправка всем пользователям…"
                    photoBusy -> "Обработка фото…"
                    saving -> "Сохранение…"
                    override != null -> "Есть правка • версия ${override.version}"
                    else -> null
                },
                onBack = onBack,
            )

            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditorPhotoCard(
                    teacherName = teacherName,
                    teacher = teacher,
                    form = form,
                    photoBusy = photoBusy,
                    onPickPhoto = { photoPicker.launch(arrayOf("image/*")) },
                )

                EditorField(
                    label = "ФИО",
                    value = form.fio,
                    testTag = "teacher_editor_fio",
                    error = if (form.fio.isBlank()) validationError else null,
                    onValueChange = { edit = form.copy(fio = it) },
                )
                EditorField(
                    label = "Должность",
                    value = form.position,
                    testTag = "teacher_editor_position",
                    onValueChange = { edit = form.copy(position = it) },
                )
                EditorField(
                    label = "Телефон",
                    value = form.phone,
                    testTag = "teacher_editor_phone",
                    onValueChange = { edit = form.copy(phone = it) },
                )

                SubjectsField(
                    subjects = form.subjects,
                    onPick = { showSubjectPicker = true },
                )

                EditorField(
                    label = "Стаж",
                    value = form.experience,
                    testTag = "teacher_editor_experience",
                    onValueChange = { edit = form.copy(experience = it) },
                )
                EditorField(
                    label = "Категория",
                    value = form.category,
                    testTag = "teacher_editor_category",
                    onValueChange = { edit = form.copy(category = it) },
                )

                StatusBanner(status)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (override != null) {
                        EditorButton(
                            icon = Icons.Default.Delete,
                            label = "Удалить правку",
                            danger = true,
                            enabled = !saving && !photoBusy && !publishing,
                            testTag = "teacher_editor_delete",
                            modifier = Modifier.weight(1f),
                            onClick = { showDeleteConfirm = true },
                        )
                    }
                    EditorButton(
                        icon = Icons.Default.Save,
                        label = "Сохранить",
                        enabled = canSave,
                        testTag = "teacher_editor_save",
                        modifier = Modifier.weight(1f),
                    ) {
                        saving = true
                        scope.launch(Dispatchers.IO) {
                            val ok = saveLocalBlocking()
                            withContext(Dispatchers.Main) {
                                saving = false
                                if (ok) {
                                    edit = null
                                    status = TeacherEditorStatus.SAVED
                                } else {
                                    status = TeacherEditorStatus.ERROR
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        PublishBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            publishing = publishing,
            publishSent = publishSent,
            publishError = publishError,
            canApply = canApply,
            onApply = { showApplyConfirm = true },
            onSetupToken = {
                publishError = null
                onOpenTokenSetup()
            },
            onDismissError = { publishError = null },
        )

        if (showSubjectPicker) {
            SubjectPickerSheet(
                initial = form.subjects,
                onDismiss = { showSubjectPicker = false },
                onConfirm = { joined ->
                    showSubjectPicker = false
                    edit = form.copy(subjects = joined)
                },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить правку?") },
            text = { Text("Данные и фото вернутся к контент-пакету или встроенным значениям.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        saving = true
                        scope.launch(Dispatchers.IO) {
                            val ok = OwnerOverridesStore.deleteTeacher(context, form.key)
                            if (ok) OwnerOverridesStore.refreshDependentCaches(context)
                            withContext(Dispatchers.Main) {
                                saving = false
                                if (ok) {
                                    edit = null
                                    status = TeacherEditorStatus.DELETED
                                } else {
                                    status = TeacherEditorStatus.ERROR
                                }
                            }
                        }
                    },
                ) {
                    Text("Удалить", color = ColorDangerFill, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена", color = ColorTextMuted)
                }
            },
        )
    }

    if (showApplyConfirm) {
        AlertDialog(
            onDismissRequest = { showApplyConfirm = false },
            title = { Text("Действительно применить изменения?") },
            text = {
                Text(
                    "Правка уйдёт в контент-пакет и после пересборки появится у всех пользователей. " +
                        "На этом устройстве изменения видны сразу.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyConfirm = false
                        startApply()
                    },
                    modifier = Modifier.testTag("teacher_editor_apply_confirm"),
                ) {
                    Text("Да", color = ColorBrandBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showApplyConfirm = false },
                    modifier = Modifier.testTag("teacher_editor_apply_cancel"),
                ) {
                    Text("Отмена", color = ColorTextMuted)
                }
            },
        )
    }
}

@Composable
private fun SubjectsField(
    subjects: String,
    onPick: () -> Unit,
) {
    Column {
        Text(
            text = "Предметы",
            style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = Tokens.SectionHeader, color = ColorTextMuted),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.HitTarget)
                .background(ColorSurfaceVariantLight, RoundedCornerShape(2.dp))
                .border(1.dp, ColorBorderLight, RoundedCornerShape(2.dp))
                .bouncyClickable(onClick = onPick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("teacher_editor_pick_subjects"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = subjects.ifBlank { "Выберите предметы из справочника" },
                style =
                TextStyle(
                    fontSize = 13.sp,
                    color = if (subjects.isBlank()) ColorTextDisabled else ColorTextTitle,
                ),
                modifier = Modifier.weight(1f).testTag("teacher_editor_subjects"),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ИЗМЕНИТЬ",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = ColorBrandBlue),
            )
        }
    }
}

@Composable
private fun PublishBottomBar(
    modifier: Modifier = Modifier,
    publishing: Boolean,
    publishSent: Boolean,
    publishError: PublishErrorUi?,
    canApply: Boolean,
    onApply: () -> Unit,
    onSetupToken: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(ColorBgMain)
            .border(1.dp, ColorBorderLight, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (publishSent) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .background(ColorSuccessBg, RoundedCornerShape(2.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("teacher_editor_publish_status"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Изменения отправлены всем пользователям",
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ColorSuccessText),
                    )
                    Text(
                        text = "На этом устройстве правка уже видна сразу",
                        style = TextStyle(fontSize = 11.sp, color = ColorSuccessText),
                    )
                }
            }
        }
        val error = publishError
        if (error != null) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("teacher_editor_publish_status"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error.message,
                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ColorErrorText),
                    modifier = Modifier.weight(1f),
                )
                if (error.needsToken) {
                    TextButton(
                        onClick = onSetupToken,
                        modifier = Modifier.testTag("teacher_editor_token_setup"),
                    ) {
                        Text("Настроить", color = ColorBrandBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = onDismissError) {
                        Text("Скрыть", color = ColorTextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(2.dp),
            color = if (canApply || publishing) ColorBrandFill else ColorSurfaceHighlight,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (canApply || publishing) ColorBrandBlue else ColorBorderLight),
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.ButtonHeight)
                .bouncyClickable(enabled = canApply && !publishing, onClick = onApply)
                .testTag("teacher_editor_apply"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (publishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).testTag("teacher_editor_apply_progress"),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ОТПРАВКА…",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = Tokens.ButtonText, color = Color.White),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = if (canApply) Color.White else ColorTextDisabled,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ПРИМЕНИТЬ ИЗМЕНЕНИЯ",
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = Tokens.ButtonText,
                            color = if (canApply) Color.White else ColorTextDisabled,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPhotoCard(
    teacherName: String,
    teacher: Teacher?,
    form: TeacherEditorForm,
    photoBusy: Boolean,
    onPickPhoto: () -> Unit,
) {
    val context = LocalContext.current
    val overridesRevision = OwnerOverridesStore.revision.intValue
    val model =
        remember(form.pendingPhoto, teacherName, teacher?.photo, overridesRevision) {
            form.pendingPhoto
                ?.let { "file://" + it.absolutePath }
                ?: ContentAssets.teacherPhotoModel(context, teacherName, teacher?.photo ?: "")
        }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (model != null) {
                coil.compose.AsyncImage(
                    model = model,
                    contentDescription = teacherName,
                    contentScale = ContentScale.Crop,
                    modifier =
                    Modifier
                        .size(96.dp)
                        .border(1.dp, ColorBorderLight, RoundedCornerShape(2.dp))
                        .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp))
                        .testTag("teacher_editor_photo"),
                )
            } else {
                Box(
                    modifier =
                    Modifier
                        .size(96.dp)
                        .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = teacherName.trim().take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            val overridePhoto = overridePhotoActive(teacherName)
            Column(modifier = Modifier.weight(1f)) {
                EditorButton(
                    icon = Icons.Default.PhotoCamera,
                    label = if (photoBusy) "Обработка…" else "Заменить фото",
                    filled = false,
                    enabled = !photoBusy,
                    testTag = "teacher_editor_pick_photo",
                    onClick = onPickPhoto,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text =
                    when {
                        form.pendingPhoto != null -> "Новое фото будет сохранено вместе с правкой"
                        overridePhoto -> "Фото заменено вашей правкой"
                        else -> "JPEG, будет сжато до 512 px"
                    },
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        }
    }
}

@Composable
private fun overridePhotoActive(teacherName: String): Boolean {
    val context = LocalContext.current
    val overridesRevision = OwnerOverridesStore.revision.intValue
    return remember(teacherName, overridesRevision) {
        OwnerOverridesStore.photoFileFor(context, teacherName) != null
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    testTag: String,
    singleLine: Boolean = true,
    error: String? = null,
    onValueChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            isError = error != null,
            shape = RoundedCornerShape(2.dp),
            colors =
            TextFieldDefaults.colors(
                focusedContainerColor = ColorSurfaceVariantLight,
                unfocusedContainerColor = ColorSurfaceVariantLight,
                focusedIndicatorColor = ColorBrandBlue,
                unfocusedIndicatorColor = ColorBorderLight,
                errorIndicatorColor = ColorDangerFill,
            ),
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.HitTarget)
                .testTag(testTag),
        )
        if (error != null) {
            Text(
                text = error,
                style = TextStyle(fontSize = 11.sp, color = ColorErrorText),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp).testTag("${testTag}_error"),
            )
        }
    }
}

@Composable
private fun StatusBanner(status: TeacherEditorStatus) {
    val text = TeacherEditorLogic.statusText(status)
    if (text.isBlank()) return
    val isError = status == TeacherEditorStatus.ERROR
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(
                if (isError) ColorSurfaceHighlight else ColorSuccessBg,
                RoundedCornerShape(2.dp),
            ).padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("teacher_editor_status"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (isError) ColorErrorText else ColorSuccessText,
            ),
        )
    }
}

@Composable
private fun EditorButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    enabled: Boolean = true,
    filled: Boolean = true,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor =
        when {
            !enabled -> ColorTextDisabled
            filled -> Color.White
            danger -> ColorDangerFill
            else -> ColorBrandBlue
        }
    val container =
        when {
            !enabled -> ColorSurfaceHighlight
            filled && danger -> ColorDangerFill
            filled -> ColorBrandFill
            else -> ColorBgMain
        }
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(2.dp),
        color = container,
        border =
        androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                !enabled -> ColorBorderLight
                danger -> ColorDangerFill
                else -> ColorBrandBlue
            },
        ),
        modifier =
        modifier
            .heightIn(min = Tokens.ButtonHeight)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = Tokens.ButtonText, color = contentColor),
            )
        }
    }
}
