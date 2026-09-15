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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.GroupInfo
import com.example.data.model.SyncDiagnosticInfo
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
import com.example.util.MpkCurriculum
import com.example.widget.WidgetUpdateHelper
import com.example.worker.MpkWorkManagerHelper

/**
 * Экран настроек приложения «МПК Расписание» по официальному Style Guide.
 */
@Composable
fun SettingsScreen(
    groupInfo: GroupInfo,
    isDarkTheme: Boolean,
    diagnosticInfo: SyncDiagnosticInfo = SyncDiagnosticInfo(),
    onThemeChanged: (Boolean) -> Unit,
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

    val specialty = remember(groupInfo.specialtyCode) {
        MpkCurriculum.getSpecialty(groupInfo.specialtyCode)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // Заголовок страницы
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Настройки",
                style = TextStylePageTitle,
                maxLines = 1
            )
            Text(
                text = "Параметры и диагностика приложения",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = ColorBrandBlue
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Карточка текущей учебной группы и специальности
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(ColorBrandBlue, RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Группа ${groupInfo.canonicalName}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = ColorTextTitle
                                        ),
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${groupInfo.course} курс, группа №${groupInfo.groupNumber}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            color = ColorTextMuted
                                        )
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, ColorBrandBlue),
                                color = ColorBrandBlue,
                                modifier = Modifier
                                    .bouncyClickable { showGroupDialog = true }
                                    .testTag("change_group_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "СМЕНИТЬ",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        if (specialty != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = ColorBorderLight)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "СПЕЦИАЛЬНОСТЬ:",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = ColorBrandBlue
                                )
                            )
                            Text(
                                text = "${specialty.cipher} — ${specialty.fullName}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorTextBody,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "Код группы: «${specialty.code}» (${specialty.shortName})",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = ColorTextMuted
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    border = BorderStroke(1.dp, ColorBorderLight),
                                    color = Color(0xFFF8FAFC)
                                ) {
                                    Text(
                                        text = if (groupInfo.hasSaturdayClasses) "6-дневка (учеба в сб)" else "5-дневка (пн-пт)",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = ColorBrandBlue
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    border = BorderStroke(1.dp, ColorBorderLight),
                                    color = Color(0xFFF8FAFC)
                                ) {
                                    val count = specialty.subjectsByCourse[groupInfo.course]?.size ?: 0
                                    Text(
                                        text = "Предметов на ${groupInfo.course} курсе: $count",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextMuted
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Блок «Диагностика сети»
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("network_diagnostics_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(ColorBrandBlue, RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Диагностика сети",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = ColorTextTitle
                                    )
                                )
                            }

                            // Бейдж статуса
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (diagnosticInfo.isSuccess) Color(0xFF16A34A) else ColorBorderLight
                                ),
                                color = if (diagnosticInfo.isSuccess) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                            ) {
                                Text(
                                    text = when {
                                        diagnosticInfo.isSyncing -> "Проверка..."
                                        diagnosticInfo.isSuccess -> "Успешно"
                                        diagnosticInfo.httpStatusCode != 0 -> "Ошибка"
                                        else -> "Ожидание"
                                    },
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (diagnosticInfo.isSuccess) Color(0xFF166534) else ColorBrandBlue
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        DiagnosticRow(label = "Последняя синхронизация", value = diagnosticInfo.lastSyncTime)
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(label = "Адрес на сайте", value = diagnosticInfo.checkedUrl, isMonospace = true)
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(label = "HTTP статус", value = if (diagnosticInfo.httpStatusCode > 0) "${diagnosticInfo.httpStatusCode}" else "—")
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(
                            label = "Получено данных",
                            value = if (diagnosticInfo.receivedBytes > 0) {
                                String.format(java.util.Locale.ROOT, "%.1f КБ (%d Б)", diagnosticInfo.receivedBytes / 1024.0, diagnosticInfo.receivedBytes)
                            } else "0 Б"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(label = "Найдено уроков (${groupInfo.canonicalName})", value = "${diagnosticInfo.lessonsFound} уроков")
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(label = "Состояние", value = diagnosticInfo.statusMessage)
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(
                            label = "Начало ответа сервера",
                            value = diagnosticInfo.responsePreview.ifBlank { "—" },
                            isMonospace = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(
                            label = "Начало извлечённого текста",
                            value = diagnosticInfo.textPreview.ifBlank { "—" },
                            isMonospace = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(
                            label = "Разбор документа",
                            value = "формат: ${diagnosticInfo.parseFormat.ifBlank { "—" }}, " +
                                "стратегия: ${diagnosticInfo.parseStrategy.ifBlank { "—" }}, " +
                                "фрагментов: ${diagnosticInfo.parseRuns}, " +
                                "группа найдена: ${if (diagnosticInfo.groupFound) "да" else "нет"}"
                        )
                        if (diagnosticInfo.parseError.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            DiagnosticRow(
                                label = "Ошибки разбора",
                                value = diagnosticInfo.parseError,
                                isMonospace = true
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(label = "Версия приложения", value = "2.4 (движко-независимый разбор группы)")

                        Spacer(modifier = Modifier.height(12.dp))

                        // Кнопка «Запустить тест подключения»
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            color = ColorBrandBlue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(onClick = onRunConnectionTest)
                                .testTag("run_connection_test_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                if (diagnosticInfo.isSyncing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ПРОВЕРКА...",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ТЕСТ ПОДКЛЮЧЕНИЯ К САЙТУ",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Переключатель темы
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = null,
                                        tint = ColorBrandBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Темная тема",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = ColorTextTitle
                                        )
                                    )
                                    Text(
                                        text = if (isDarkTheme) "Включена" else "Выключена (светлая по умолчанию)",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextMuted
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = onThemeChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ColorBrandBlue
                                ),
                                modifier = Modifier.testTag("theme_switch")
                            )
                        }
                    }
                }
            }

            // Уведомления
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
                                        .background(ColorBrandBlue, RoundedCornerShape(2.dp)),
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
                                        text = if (isNotificationEnabled) "Включены (14:00 – 21:30)" else "Отключены",
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
                            text = "Политика нулевого спама: Ровно один пуш в день, когда публикуется расписание на следующий день.",
                            style = androidx.compose.ui.text.TextStyle(
                                color = ColorTextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Официальные ресурсы колледжа
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

                        // Сайт МГПК
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

                        // Портал расписания МГПК
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
