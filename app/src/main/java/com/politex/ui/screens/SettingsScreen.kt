package com.politex.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.politex.BuildConfig
import com.politex.data.model.GroupInfo
import com.politex.ui.components.AppCard
import com.politex.ui.components.SectionCaption
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.ColorWarning
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.AppThemeStore
import com.politex.util.BackupManager
import com.politex.util.BellCountdownNotifier
import com.politex.util.ExactAlarmGate
import com.politex.util.ScheduleDisplayPrefs
import com.politex.util.SubgroupPref
import com.politex.util.SubgroupSettings
import com.politex.util.TeacherModeStore
import com.politex.util.defaultBackupFileName
import com.politex.widget.WidgetUpdateHelper
import com.politex.worker.MpkWorkManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SettingsScreen(
    groupInfo: GroupInfo,
    onGroupChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val teacherModeRevision = TeacherModeStore.revision.intValue
    val isTeacherMode =
        remember(context, teacherModeRevision) {
            TeacherModeStore.isTeacher(context)
        }
    val teacherName =
        remember(context, teacherModeRevision) {
            TeacherModeStore.teacherName(context)
        }
    var isNotificationEnabled by remember {
        mutableStateOf(WidgetUpdateHelper.isNotificationEnabled(context))
    }

    val settingsLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(settingsLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationEnabled = WidgetUpdateHelper.isNotificationEnabled(context)
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { settingsLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            isNotificationEnabled = true
            WidgetUpdateHelper.setNotificationEnabled(context, true)
            MpkWorkManagerHelper.setupPeriodicScheduleCheck(context)
        } else {
            isNotificationEnabled = false
            WidgetUpdateHelper.setNotificationEnabled(context, false)
            MpkWorkManagerHelper.setupPeriodicScheduleCheck(context)
        }
    }

    var currentTheme by remember { mutableStateOf(AppThemeStore.load(context)) }
    fun onThemePicked(theme: com.politex.ui.theme.AppTheme) {
        AppThemeStore.save(context, theme)
        currentTheme = theme
    }

    var backupMessage by remember { mutableStateOf("") }

    var backupIsError by remember { mutableStateOf(false) }
    val backupScope = rememberCoroutineScope()

    var showBackupDialog by remember { mutableStateOf(false) }
    var pendingBackupSections by remember { mutableStateOf(BackupManager.BackupSection.ALL) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sections = pendingBackupSections
        backupScope.launch {
            backupMessage = try {

                withContext(Dispatchers.IO) {
                    val json = BackupManager.buildBackup(context, sections)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                backupIsError = false
                "Копия сохранена"
            } catch (e: Exception) {
                backupIsError = true
                "Не удалось сохранить: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            backupMessage = try {

                val restored = withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    if (json.isNullOrBlank()) {
                        null
                    } else {
                        BackupManager.restoreBackup(context, json)
                    }
                }
                if (restored == null) {
                    backupIsError = true
                    "Файл пустой"
                } else {
                    restored.fold(
                        onSuccess = { result ->
                            backupIsError = false

                            result.groupRestored?.let { onGroupChanged(it) }
                            val parts = mutableListOf("Восстановлено заданий: ${result.tasksRestored}")
                            if (result.gradesRestored > 0) parts += "оценок: ${result.gradesRestored}"
                            if (result.remindersRestored > 0) parts += "напоминаний: ${result.remindersRestored}"
                            if (result.lessonsRestored > 0) parts += "уроков: ${result.lessonsRestored}"
                            if (result.subjectRenamesRestored > 0) parts += "названий предметов: ${result.subjectRenamesRestored}"
                            result.groupRestored?.let { parts += "группа $it" }
                            if (result.warnings.isNotEmpty()) parts += result.warnings.joinToString("; ")
                            parts.joinToString(". ")
                        },
                        onFailure = {
                            backupIsError = true
                            "Не удалось прочитать файл: ${it.message}"
                        },
                    )
                }
            } catch (e: Exception) {
                backupIsError = true
                "Не удалось прочитать файл: ${e.message}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionCaption("РАСПИСАНИЕ И УВЕДОМЛЕНИЯ", Modifier.padding(top = 4.dp)) }
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (isNotificationEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Уведомление о новом расписании",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ColorTextTitle,
                                    ),
                                )
                            }

                            Switch(
                                checked = isNotificationEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            isNotificationEnabled = true
                                            WidgetUpdateHelper.setNotificationEnabled(context, true)
                                            MpkWorkManagerHelper.setupPeriodicScheduleCheck(context)
                                        }
                                    } else {
                                        isNotificationEnabled = false
                                        WidgetUpdateHelper.setNotificationEnabled(context, false)
                                        MpkWorkManagerHelper.setupPeriodicScheduleCheck(context)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ColorBrandBlue,

                                    uncheckedTrackColor = ColorBorderLight,
                                    uncheckedThumbColor = ColorTextMuted,
                                ),
                                modifier = Modifier.testTag("notifications_switch"),
                            )
                        }
                    }
                }
            }

            item {
                BellCountdownNotificationCard()
            }

            item {
                var compactSchedule by remember {
                    mutableStateOf(ScheduleDisplayPrefs.isCompact(context))
                }
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Компактное расписание",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorTextTitle,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = compactSchedule,
                                onCheckedChange = { on ->
                                    compactSchedule = on
                                    ScheduleDisplayPrefs.setCompact(context, on)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ColorBrandBlue,

                                    uncheckedTrackColor = ColorBorderLight,
                                    uncheckedThumbColor = ColorTextMuted,
                                ),
                                modifier = Modifier.testTag("settings_compact_schedule_switch"),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Только номер, предмет, преподаватель и кабинет",
                            style = androidx.compose.ui.text.TextStyle(
                                color = ColorTextMuted,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }

            item { SectionCaption("КАРТА", Modifier.padding(top = 4.dp)) }

            item {
                var routesEnabled by remember {
                    mutableStateOf(
                        WidgetUpdateHelper.getPrefs(context)
                            .getBoolean("map_routes_enabled", true),
                    )
                }
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_routes_toggle"),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Маршруты по карте",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorTextTitle,
                                ),
                            )
                            Switch(
                                checked = routesEnabled,
                                onCheckedChange = { on ->
                                    routesEnabled = on
                                    WidgetUpdateHelper.getPrefs(context)
                                        .edit()
                                        .putBoolean("map_routes_enabled", on)
                                        .apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ColorBrandBlue,

                                    uncheckedTrackColor = ColorBorderLight,
                                    uncheckedThumbColor = ColorTextMuted,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Когда выключено, кнопки кабинетов открывают карту без построения пути.",
                            style = androidx.compose.ui.text.TextStyle(
                                color = ColorTextMuted,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }

            if (showSubgroupCard(isTeacherMode)) {
                item {
                    var subgroupPref by remember { mutableStateOf(SubgroupSettings.get(context)) }
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subgroup_pref_card"),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Подгруппа",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorTextTitle,
                                ),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SubgroupChip(
                                    title = "Нейтрально",
                                    selected = subgroupPref == SubgroupPref.NEUTRAL,
                                    tag = "subgroup_pref_neutral",
                                    modifier = Modifier.weight(1f),
                                ) {
                                    subgroupPref = SubgroupPref.NEUTRAL
                                    SubgroupSettings.set(context, SubgroupPref.NEUTRAL)
                                }
                                SubgroupChip(
                                    title = "1-я",
                                    selected = subgroupPref == SubgroupPref.FIRST,
                                    tag = "subgroup_pref_first",
                                    modifier = Modifier.weight(1f),
                                ) {
                                    subgroupPref = SubgroupPref.FIRST
                                    SubgroupSettings.set(context, SubgroupPref.FIRST)
                                }
                                SubgroupChip(
                                    title = "2-я",
                                    selected = subgroupPref == SubgroupPref.SECOND,
                                    tag = "subgroup_pref_second",
                                    modifier = Modifier.weight(1f),
                                ) {
                                    subgroupPref = SubgroupPref.SECOND
                                    SubgroupSettings.set(context, SubgroupPref.SECOND)
                                }
                            }
                            if (subgroupPref != SubgroupPref.NEUTRAL) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Маршруты и виджеты будут вести на кабинеты выбранной подгруппы.",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = ColorTextMuted,
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCaption("ВНЕШНИЙ ВИД", Modifier.padding(top = 4.dp))
            }
            item {

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme_swatches"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(com.politex.ui.theme.AppTheme.entries.toList()) { theme ->
                        val isSelected = currentTheme == theme
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBgMain,
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) ColorTopBar else ColorBorderLight,
                            ),
                            modifier = Modifier.bouncyClickable { onThemePicked(theme) },
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(92.dp)
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(theme.palette.topBar, RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 20.dp, height = 7.dp)
                                            .background(theme.palette.brandBlue, RoundedCornerShape(1.dp)),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = theme.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = ColorTextTitle,
                                        textAlign = TextAlign.Center,
                                    ),
                                )

                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }

            item {
                var darkTheme by remember {
                    mutableStateOf(AppThemeStore.isDark(context))
                }
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Тёмная тема",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = { on ->
                                darkTheme = on
                                AppThemeStore.setDark(context, on)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ColorBrandBlue,

                                uncheckedTrackColor = ColorBorderLight,
                                uncheckedThumbColor = ColorTextMuted,
                            ),
                            modifier = Modifier.testTag("settings_dark_theme_switch"),
                        )
                    }
                }
            }

            item { SectionCaption("ДАННЫЕ", Modifier.padding(top = 4.dp)) }
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Резервная копия данных",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionChip(
                                text = "СОХРАНИТЬ",
                                filled = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backup_save_btn"),
                                onClick = { showBackupDialog = true },
                            )
                            ActionChip(
                                text = "ВОССТАНОВИТЬ",
                                filled = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backup_restore_btn"),
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            )
                        }
                        if (backupMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = backupMessage,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,

                                    color = if (backupIsError) ColorError else ColorSuccess,
                                ),
                            )
                        }
                    }
                }
            }

            if (BuildConfig.FLAVOR == "admin") {
                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_hints_card"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Сбросить подсказки",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ColorTextTitle,
                                    ),
                                )
                                Text(
                                    text = "Демо-задание в «Заданиях» и пульсация кабинетов в расписании покажутся снова.",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted,
                                    ),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ActionChip(
                                text = "СБРОСИТЬ",
                                filled = true,
                                modifier = Modifier.testTag("reset_hints_btn"),
                                onClick = {

                                    com.politex.util.HintFlags.resetAll(context)
                                    Toast.makeText(context, "Подсказки сброшены", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }

                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_group_card"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Сбросить группу",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ColorTextTitle,
                                    ),
                                )
                                Text(
                                    text = "Приложение закроется, а при следующем входе снова спросит группу и подгруппу.",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted,
                                    ),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ActionChip(
                                text = "СБРОСИТЬ",
                                filled = false,
                                modifier = Modifier.testTag("reset_group_btn"),
                                onClick = {

                                    WidgetUpdateHelper.getPrefs(context).edit()
                                        .remove("selected_group")
                                        .apply()
                                    SubgroupSettings.set(context, SubgroupPref.NEUTRAL)
                                    (context as? android.app.Activity)?.finishAffinity()
                                },
                            )
                        }
                    }
                }
            }

            item { SectionCaption("О ПРИЛОЖЕНИИ", Modifier.padding(top = 4.dp)) }
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        SettingsValueRow(
                            title = "Telegram",
                            value = "@Betterthannothing12",
                        )
                    }
                }
            }

            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Ресурсы колледжа",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LinkItem(
                            title = "Сайт колледжа",
                            subtitle = "guo-mpk.by — официальная информация",
                            onClick = { openLink(context, "https://guo-mpk.by/") },
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = ColorBorderLight)
                        Spacer(modifier = Modifier.height(6.dp))

                        LinkItem(
                            title = "Портал расписания",
                            subtitle = "guo-mpk.by/raspisanie/",
                            onClick = { openLink(context, "https://guo-mpk.by/raspisanie/") },
                        )
                    }
                }
            }

            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        SettingsValueRow(
                            title = "Версия",
                            value = BuildConfig.VERSION_NAME,
                        )
                    }
                }
            }

            if (showTeacherModeExit(isTeacherMode)) {
                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("teacher_mode_exit_card"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Режим преподавателя",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ColorTextTitle,
                                    ),
                                )
                                Text(
                                    text =
                                    "Сейчас показано расписание преподавателя" +
                                        (teacherName?.let { ": $it" } ?: "") +
                                        ". После выхода приложение снова предложит выбрать роль и группу.",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted,
                                    ),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ActionChip(
                                text = "СМЕНИТЬ РОЛЬ",
                                filled = false,
                                modifier = Modifier.testTag("teacher_mode_exit_btn"),
                                onClick = {
                                    WidgetUpdateHelper.getPrefs(context).edit()
                                        .remove("selected_group")
                                        .apply()
                                    SubgroupSettings.set(context, SubgroupPref.NEUTRAL)
                                    TeacherModeStore.clear(context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackupDialog) {
        BackupSectionsDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { sections ->
                showBackupDialog = false
                pendingBackupSections = sections
                exportLauncher.launch(defaultBackupFileName())
            },
        )
    }
}

@Composable
private fun BackupSectionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Set<BackupManager.BackupSection>) -> Unit,
) {
    var selected by remember { mutableStateOf(BackupManager.BackupSection.ALL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        modifier = Modifier.testTag("backup_sections_dialog"),
        title = {
            Text(
                text = "Что сохранить",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorTextTitle,
                ),
            )
        },
        text = {
            Column {
                BackupManager.BackupSection.entries.forEach { section ->
                    val checked = section in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Tokens.HitTarget)
                            .bouncyClickable {
                                selected =
                                    if (checked) selected - section else selected + section
                            }
                            .testTag("backup_opt_${section.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected =
                                    if (checked) selected - section else selected + section
                            },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = backupSectionTitle(section),
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = ColorTextBody,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier = Modifier
                    .heightIn(min = Tokens.ButtonHeight)
                    .then(if (selected.isEmpty()) Modifier else Modifier.bouncyClickable { onConfirm(selected) })
                    .testTag("backup_confirm_btn"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "СОХРАНИТЬ",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (selected.isEmpty()) ColorTextMuted else Color.White,
                        ),
                    )
                }
            }
        },
        dismissButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .heightIn(min = Tokens.ButtonHeight)
                    .bouncyClickable(onClick = onDismiss)
                    .testTag("backup_cancel_btn"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "ОТМЕНА",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ColorTextTitle,
                        ),
                    )
                }
            }
        },
    )
}

private fun backupSectionTitle(section: BackupManager.BackupSection): String = when (section) {
    BackupManager.BackupSection.SCHEDULE_HISTORY -> "История расписания"
    BackupManager.BackupSection.TASKS -> "Задания"
    BackupManager.BackupSection.GRADES -> "Оценки"
    BackupManager.BackupSection.SUBJECT_NAMES -> "Названия предметов"
    BackupManager.BackupSection.SETTINGS -> "Настройки"
}

@Composable
private fun LinkItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = ColorTextTitle,
                    ),
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorActiveBlue,
                    ),
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ColorTextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun BellCountdownNotificationCard() {
    val context = LocalContext.current
    var isOngoingEnabled by remember {
        mutableStateOf(BellCountdownNotifier.isEnabled(context))
    }
    var selectedStyle by remember {
        mutableStateOf(BellCountdownNotifier.getStyle(context))
    }
    var selectedTheme by remember {
        mutableStateOf(BellCountdownNotifier.getTheme(context))
    }
    var selectedScope by remember {
        mutableStateOf(BellCountdownNotifier.getScope(context))
    }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    var exactAlarmAsked by rememberSaveable { mutableStateOf(false) }

    var canExactAlarms by remember { mutableStateOf(ExactAlarmGate.canScheduleExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canExactAlarms = ExactAlarmGate.canScheduleExactAlarms(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestExactAlarmIfNeeded() {
        if (ExactAlarmGate.shouldRequest(Build.VERSION.SDK_INT, canExactAlarms, exactAlarmAsked)) {
            exactAlarmAsked = true
            ExactAlarmGate.openSettings(context)
        }
    }

    fun enableLine() {
        isOngoingEnabled = true
        BellCountdownNotifier.setEnabled(context, true)
        com.politex.widget.WidgetAlarm.scheduleNext(context)
        requestExactAlarmIfNeeded()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableLine()
        } else {
            isOngoingEnabled = false
        }
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bell_notification_card"),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Строка «до звонка»",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Text(
                            text = if (isOngoingEnabled) "Показывается в шторке" else "Отключена",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = ColorTextMuted,
                            ),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (settingsExpanded) ColorSurfaceHighlight else Color.Transparent,
                    border = BorderStroke(1.dp, if (settingsExpanded) ColorTopBar else ColorBorderLight),
                    modifier = Modifier
                        .size(Tokens.HitTarget)
                        .bouncyClickable { settingsExpanded = !settingsExpanded }
                        .testTag("bell_settings_toggle"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки строки",
                            tint = if (settingsExpanded) ColorBrandBlue else ColorTextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))

                Switch(
                    checked = isOngoingEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                enableLine()
                            }
                        } else {
                            isOngoingEnabled = false
                            BellCountdownNotifier.setEnabled(context, false)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ColorBrandBlue,
                        uncheckedTrackColor = ColorBorderLight,
                        uncheckedThumbColor = ColorTextMuted,
                    ),
                    modifier = Modifier.testTag("bell_notification_switch"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Отсчёт до конца урока в строке уведомлений",
                style = androidx.compose.ui.text.TextStyle(
                    color = ColorTextMuted,
                    fontSize = 11.sp,
                ),
            )

            if (ExactAlarmGate.hintNeeded(Build.VERSION.SDK_INT, canExactAlarms)) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorSurfaceHighlight,
                    border = BorderStroke(1.dp, ColorWarning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exact_alarm_warning"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Для точного отсчёта разрешите приложению точные будильники",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                color = ColorTextBody,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .bouncyClickable { ExactAlarmGate.openSettings(context) },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = "РАЗРЕШИТЬ",
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = settingsExpanded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "СТИЛЬ СТРОКИ",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ColorBrandBlue,
                        ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    BellCountdownNotifier.Style.entries
                        .filter { it != BellCountdownNotifier.Style.WITH_APP_NAME }
                        .forEach { style ->
                        val isSelected = selectedStyle == style
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                            color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)

                                .heightIn(min = Tokens.HitTarget)
                                .bouncyClickable {
                                    selectedStyle = style
                                    BellCountdownNotifier.setStyle(context, style)
                                    requestExactAlarmIfNeeded()
                                }
                                .testTag("bell_style_${style.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedStyle = style
                                        BellCountdownNotifier.setStyle(context, style)
                                        requestExactAlarmIfNeeded()
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = bellStyleLabel(style),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ColorTextTitle,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ЦВЕТОВАЯ ТЕМА",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ColorBrandBlue,
                        ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BellCountdownNotifier.Theme.entries.forEach { theme ->
                            val isSelected = selectedTheme == theme
                            val swatchColor =
                                when {
                                    theme == BellCountdownNotifier.Theme.APP -> ColorBrandFill
                                    theme.accent != 0 -> Color(theme.accent)
                                    else -> ColorSurfaceVariantLight
                                }
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                                color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                                modifier = Modifier
                                    .weight(1f)

                                    .heightIn(min = Tokens.HitTarget)
                                    .bouncyClickable {
                                        selectedTheme = theme
                                        BellCountdownNotifier.setTheme(context, theme)
                                    }
                                    .testTag("bell_theme_${theme.id}"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(swatchColor, CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = theme.title,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = ColorTextBody,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "СЧИТАТЬ ДО КОНЦА КАКИХ УРОКОВ",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ColorBrandBlue,
                        ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    BellCountdownNotifier.Scope.entries.forEach { scope ->
                        val isSelected = selectedScope == scope
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                            color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)

                                .heightIn(min = Tokens.HitTarget)
                                .bouncyClickable {
                                    selectedScope = scope
                                    BellCountdownNotifier.setScope(context, scope)
                                }
                                .testTag("bell_scope_${scope.id}"),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedScope = scope
                                            BellCountdownNotifier.setScope(context, scope)
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = scope.title,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = ColorTextTitle,
                                        ),
                                    )
                                }
                                Text(
                                    text = scope.description,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted,
                                    ),
                                    modifier = Modifier.padding(start = 28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bellStyleLabel(style: BellCountdownNotifier.Style): String = when (style) {
    BellCountdownNotifier.Style.COMPACT,
    BellCountdownNotifier.Style.WITH_APP_NAME,
    -> "В минутах"
    BellCountdownNotifier.Style.WITH_LESSON -> "В минутах + идущий урок"
    BellCountdownNotifier.Style.WITH_PROGRESS -> "С полосой прогресса"
    BellCountdownNotifier.Style.WITH_SECONDS -> "В секундах"
}

private fun openLink(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Нет браузера", Toast.LENGTH_SHORT).show()
        false
    }

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (onClick != null) Modifier.bouncyClickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ColorTextTitle,
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = ColorTextMuted,
                ),
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ColorTextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (filled) ColorBrandFill else ColorBgMain,
        border = BorderStroke(1.dp, if (filled) ColorBrandFill else ColorBorderLight),
        modifier = modifier

            .heightIn(min = Tokens.ButtonHeight)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(

            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (filled) Color.White else ColorTextTitle,
                ),
            )
        }
    }
}

@Composable
private fun SubgroupChip(
    title: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier =
        modifier
            .heightIn(min = Tokens.ChipScreen)
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

internal fun showSubgroupCard(teacherMode: Boolean): Boolean = !teacherMode

internal fun showTeacherModeExit(teacherMode: Boolean): Boolean = teacherMode
