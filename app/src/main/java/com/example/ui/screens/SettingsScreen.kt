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
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSuccess
import com.example.ui.theme.ColorSuccessBg
import com.example.ui.theme.ColorSuccessText
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight

/**
 * Экран настроек приложения «МПК Расписание» по официальному Style Guide.
 */
@Composable
fun SettingsScreen(
    groupInfo: GroupInfo,
    diagnosticInfo: SyncDiagnosticInfo = SyncDiagnosticInfo(),
    onGroupChanged: (String) -> Unit,
    onRunConnectionTest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showGroupDialog by remember { mutableStateOf(false) }
    var isDebugEnabled by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
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
        // Заголовок страницы рисует вызывающий экран (CollegeScreen.SettingsPage):
        // там он рядом со стрелкой «назад». Второй такой же заголовок здесь
        // приводил к тому, что «Настройки» показывались дважды подряд.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Компактный блок группы: только группа и кнопка «Сменить»
            // (подробности специальности — во вкладке «Колледж»)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
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
                        }

                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            color = ColorBrandFill,
                            modifier = Modifier
                                .bouncyClickable { showGroupDialog = true }
                                .testTag("change_group_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                                    softWrap = false,
                                    maxLines = 1,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Блок «Диагностика сети» — только при включённом дебаге
            if (isDebugEnabled) item {
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
                                        .background(ColorBrandFill, RoundedCornerShape(2.dp)),
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
                                    if (diagnosticInfo.isSuccess) ColorSuccess else ColorBorderLight
                                ),
                                color = if (diagnosticInfo.isSuccess) ColorSuccessBg else ColorSurfaceVariantLight
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
                                        color = if (diagnosticInfo.isSuccess) ColorSuccessText else ColorBrandBlue
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Обычному пользователю достаточно трёх строк; подробная
                        // телеметрия — под тумблером «Дебаг» в самом низу блока
                        DiagnosticRow(label = "HTTP статус", value = if (diagnosticInfo.httpStatusCode > 0) "${diagnosticInfo.httpStatusCode}" else "—")
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(
                            label = "Получено данных",
                            value = if (diagnosticInfo.receivedBytes > 0) {
                                String.format(java.util.Locale.ROOT, "%.1f КБ", diagnosticInfo.receivedBytes / 1024.0)
                            } else "0 Б"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DiagnosticRow(label = "Версия приложения", value = "2.11")

                        if (isDebugEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            DiagnosticRow(label = "Последняя синхронизация", value = diagnosticInfo.lastSyncTime)
                            Spacer(modifier = Modifier.height(4.dp))
                            DiagnosticRow(label = "Адрес на сайте", value = diagnosticInfo.checkedUrl, isMonospace = true)
                            Spacer(modifier = Modifier.height(4.dp))
                            DiagnosticRow(label = "Получено байт", value = "${diagnosticInfo.receivedBytes}")
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
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Кнопка «Запустить тест подключения»
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            color = ColorBrandFill,
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Тумблер «Дебаг»: показывает полную телеметрию синхронизации
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Дебаг",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ColorTextBody
                                )
                            )
                            Switch(
                                checked = isDebugEnabled,
                                onCheckedChange = { isDebugEnabled = it },
                                modifier = Modifier.testTag("debug_toggle")
                            )
                        }

                        if (isDebugEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            DebugTimeSection()
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

            // Постоянная строка «До звонка» в шторке уведомлений
            item {
                BellCountdownNotificationCard()
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

/**
 * Секция дебаг-времени: фиксированные дата и время, которые приложение
 * (звонки, подсветка урока, виджет «До звонка») считает «сейчас».
 */
@Composable
private fun DebugTimeSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val overridden = com.example.util.DebugClock.isOverridden(context)
    val current = com.example.util.DebugClock.now(context)

    var dateText by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(String.format(java.util.Locale.ROOT, "%02d.%02d.%04d",
            current.get(java.util.Calendar.DAY_OF_MONTH),
            current.get(java.util.Calendar.MONTH) + 1,
            current.get(java.util.Calendar.YEAR)))
    }
    var timeText by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(String.format(java.util.Locale.ROOT, "%02d:%02d",
            current.get(java.util.Calendar.HOUR_OF_DAY),
            current.get(java.util.Calendar.MINUTE)))
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceVariantLight,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ДЕБАГ-ВРЕМЯ" + if (overridden) " (включено)" else "",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = ColorBrandBlue
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Дата") },
                    placeholder = { Text("ДД.ММ.ГГГГ") },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Время") },
                    placeholder = { Text("ЧЧ:ММ") },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable {
                            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.ROOT)
                            val parsed = try {
                                sdf.parse("$dateText $timeText")
                            } catch (_: Exception) {
                                null
                            }
                            if (parsed != null) {
                                com.example.util.DebugClock.setOverride(context, parsed.time)
                                com.example.widget.BellCountdownWidgetProvider.updateAll(context)
                                com.example.widget.WidgetAlarm.scheduleNext(context)
                            }
                        }
                ) {
                    Text(
                        text = "ПРИМЕНИТЬ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = ColorBgMain,
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable {
                            com.example.util.DebugClock.setOverride(context, null)
                            com.example.widget.BellCountdownWidgetProvider.updateAll(context)
                            com.example.widget.WidgetAlarm.scheduleNext(context)
                        }
                ) {
                    Text(
                        text = "СБРОСИТЬ",
                        color = ColorTextBody,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Карточка настройки постоянного уведомления «До звонка»:
 * выключатель и выбор стиля оформления строки.
 */
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
                            // Кружок-образец цвета
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

/** Превью: как выглядит строка уведомления в выбранном стиле. */
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
