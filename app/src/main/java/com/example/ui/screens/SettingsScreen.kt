package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.GroupInfo
import com.example.ui.components.GroupSelectionDialog
import com.example.ui.theme.ColorActiveBlue
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
import com.example.ui.util.bouncyClickable
import com.example.util.AppThemeStore
import com.example.util.MpkCurriculum
import com.example.util.defaultBackupFileName
import kotlinx.coroutines.launch
import com.example.widget.WidgetUpdateHelper
import com.example.worker.MpkWorkManagerHelper
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSuccess
import com.example.ui.theme.ColorSuccessBg
import com.example.ui.theme.ColorSuccessText
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight

@Composable
fun SettingsScreen(
    groupInfo: GroupInfo,
    onGroupChanged: (String) -> Unit,
    onRunConnectionTest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showGroupDialog by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember {
        mutableStateOf(WidgetUpdateHelper.isNotificationEnabled(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
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
    fun onThemePicked(theme: com.example.ui.theme.AppTheme) {
        AppThemeStore.save(context, theme)
        currentTheme = theme
    }

    var backupMessage by remember { mutableStateOf("") }
    val backupScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            backupMessage = try {
                val json = com.example.util.BackupManager.buildBackup(context)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                "Копия сохранена"
            } catch (e: Exception) {
                "Не удалось сохранить: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            backupMessage = try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (json.isNullOrBlank()) {
                    "Файл пустой"
                } else {
                    com.example.util.BackupManager.restoreBackup(context, json).fold(
                        onSuccess = { result ->
                            val parts = mutableListOf("Восстановлено заданий: ${result.tasksRestored}")
                            result.groupRestored?.let { parts += "группа $it" }
                            if (result.warnings.isNotEmpty()) parts += result.warnings.joinToString("; ")
                            parts.joinToString(". ")
                        },
                        onFailure = { "Не удалось прочитать файл: ${it.message}" }
                    )
                }
            } catch (e: Exception) {
                "Не удалось прочитать файл: ${e.message}"
            }
        }
    }

    val specialty = remember(groupInfo.specialtyCode) {
        MpkCurriculum.getSpecialty(groupInfo.specialtyCode)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                SectionHeader("ТЕМА ПРИЛОЖЕНИЯ")
            }
            items(com.example.ui.theme.AppTheme.entries.toList()) { theme ->
                val isSelected = currentTheme == theme
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable { onThemePicked(theme) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(theme.palette.topBar, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 8.dp)
                                    .background(theme.palette.brandBlue, RoundedCornerShape(1.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = theme.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = ColorTextTitle
                                )
                            )
                            Text(
                                text = theme.hint,
                                style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorTextMuted)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = ColorBrandBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader("ДАННЫЕ")
            }
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Резервная копия",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Настройки и все учебные задания сохраняются в файл. " +
                                "Файл можно перекинуть на другой телефон " +
                                "или использовать как запасную копию.",
                            style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorTextMuted)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActionChip(
                                text = "СОХРАНИТЬ В ФАЙЛ",
                                filled = true,
                                modifier = Modifier.weight(1f),
                                onClick = { exportLauncher.launch(defaultBackupFileName(context)) }
                            )
                            ActionChip(
                                text = "ВОССТАНОВИТЬ",
                                filled = false,
                                modifier = Modifier.weight(1f),
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                            )
                        }
                        if (backupMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = backupMessage,
                                style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorBrandBlue)
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isNotificationEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Уведомления о расписании",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = ColorTextTitle
                                        )
                                    )
                                    Text(
                                        text = if (isNotificationEnabled) "Включены (10:00 – 21:00)" else "Отключены",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextMuted
                                        )
                                    )
                                }
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
                                    checkedTrackColor = ColorBrandBlue
                                ),
                                modifier = Modifier.testTag("notifications_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Приходит один раз, когда на сайте появляется расписание на следующий день.",
                            style = androidx.compose.ui.text.TextStyle(
                                color = ColorTextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            item {
                BellCountdownNotificationCard()
            }

            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Ресурсы колледжа",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ColorTextTitle
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LinkItem(
                            title = "Официальный сайт МГПК",
                            subtitle = "guo-mpk.by",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://guo-mpk.by"))
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = ColorBorderLight)
                        Spacer(modifier = Modifier.height(6.dp))

                        LinkItem(
                            title = "Портал расписания",
                            subtitle = "guo-mpk.by/raspisanie/",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://guo-mpk.by/raspisanie/"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            item { SectionHeader("ОБРАТНАЯ СВЯЗЬ") }
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Telegram",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle
                            )
                        )
                        Text(
                            text = "@Betterthannothing12",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorBrandBlue
                            )
                        )
                    }
                }
            }
        }
    }

    if (showGroupDialog) {
        GroupSelectionDialog(
            currentGroupName = groupInfo.canonicalName,
            onDismissRequest = { showGroupDialog = false },
            onGroupSelected = { newGroup ->
                showGroupDialog = false
                onGroupChanged(newGroup)
            }
        )
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 11.sp,
                color = ColorTextMuted
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Medium,
                color = ColorTextBody,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                fontSize = if (isMonospace) 10.sp else 11.sp
            ),
            modifier = Modifier.weight(1.3f)
        )
    }
}

@Composable
private fun LinkItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = ColorTextTitle
                    )
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorActiveBlue
                    )
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ColorTextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BellCountdownNotificationCard() {
    val context = LocalContext.current
    var isOngoingEnabled by remember {
        mutableStateOf(com.example.util.BellCountdownNotifier.isEnabled(context))
    }
    var selectedStyle by remember {
        mutableStateOf(com.example.util.BellCountdownNotifier.getStyle(context))
    }
    var selectedTheme by remember {
        mutableStateOf(com.example.util.BellCountdownNotifier.getTheme(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isOngoingEnabled = true
            com.example.util.BellCountdownNotifier.setEnabled(context, true)
            com.example.widget.WidgetAlarm.scheduleNext(context)
        } else {
            isOngoingEnabled = false
        }
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bell_notification_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Строка «До звонка»",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle
                            )
                        )
                        Text(
                            text = if (isOngoingEnabled) "Показывается в шторке" else "Отключена",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = ColorTextMuted
                            )
                        )
                    }
                }

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
                                isOngoingEnabled = true
                                com.example.util.BellCountdownNotifier.setEnabled(context, true)
                                com.example.widget.WidgetAlarm.scheduleNext(context)
                            }
                        } else {
                            isOngoingEnabled = false
                            com.example.util.BellCountdownNotifier.setEnabled(context, false)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ColorBrandBlue
                    ),
                    modifier = Modifier.testTag("bell_notification_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Постоянная строка со временем до звонка — обновляется каждую минуту.",
                style = androidx.compose.ui.text.TextStyle(
                    color = ColorTextMuted,
                    fontSize = 11.sp
                )
            )

            if (isOngoingEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "СТИЛЬ СТРОКИ",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ColorBrandBlue
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                com.example.util.BellCountdownNotifier.Style.entries.forEach { style ->
                    val isSelected = selectedStyle == style
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                        color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .bouncyClickable {
                                selectedStyle = style
                                com.example.util.BellCountdownNotifier.setStyle(context, style)
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedStyle = style
                                        com.example.util.BellCountdownNotifier.setStyle(context, style)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stylePreviewTitle(style),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ColorTextTitle
                                    )
                                )
                            }
                            Text(
                                text = stylePreviewBody(style),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = ColorTextMuted
                                ),
                                modifier = Modifier.padding(start = 28.dp)
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
                        color = ColorBrandBlue
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                com.example.util.BellCountdownNotifier.Theme.entries.forEach { theme ->
                    val isSelected = selectedTheme == theme
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                        color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .bouncyClickable {
                                selectedTheme = theme
                                com.example.util.BellCountdownNotifier.setTheme(context, theme)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        if (theme.accent == 0) ColorSurfaceVariantLight else Color(theme.accent),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = theme.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = ColorTextBody
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun stylePreviewTitle(style: com.example.util.BellCountdownNotifier.Style): String =
    when (style) {
        com.example.util.BellCountdownNotifier.Style.COMPACT -> "До звонка: 12 мин"
        com.example.util.BellCountdownNotifier.Style.WITH_APP_NAME -> "Мой Политех"
        com.example.util.BellCountdownNotifier.Style.WITH_LESSON -> "До звонка: 12 мин"
        com.example.util.BellCountdownNotifier.Style.WITH_PROGRESS -> "До звонка: 12 мин"
    }

private fun stylePreviewBody(style: com.example.util.BellCountdownNotifier.Style): String =
    when (style) {
        com.example.util.BellCountdownNotifier.Style.COMPACT ->
            "Только суть — сколько осталось"
        com.example.util.BellCountdownNotifier.Style.WITH_APP_NAME ->
            "Сверху название приложения, ниже отсчёт"
        com.example.util.BellCountdownNotifier.Style.WITH_LESSON ->
            "Плюс номер идущего урока или «Перемена»"
        com.example.util.BellCountdownNotifier.Style.WITH_PROGRESS ->
            "С полосой прогресса урока"
    }

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ColorBrandBlue
        ),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ActionChip(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (filled) ColorBrandFill else ColorBgMain,
        border = BorderStroke(1.dp, if (filled) ColorBrandFill else ColorBorderLight),
        modifier = modifier.bouncyClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                softWrap = false,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (filled) Color.White else ColorTextTitle
                )
            )
        }
    }
}
