package com.politex.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.politex.ui.components.GroupSelectionDialog
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
import com.politex.ui.theme.ColorWarning
import com.politex.ui.util.bouncyClickable
import com.politex.util.AppThemeStore
import com.politex.util.GradeSettings
import com.politex.util.MpkCurriculum
import com.politex.util.TutorialFlags
import com.politex.util.defaultBackupFileName
import com.politex.widget.WidgetUpdateHelper
import com.politex.worker.MpkWorkManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ф4: исход «Проверить соединение» — живёт в теле экрана, а не в item{} LazyColumn. */
private enum class ConnState { IDLE, RUNNING, OK, FAIL }

@Composable
fun SettingsScreen(
    groupInfo: GroupInfo,
    onGroupChanged: (String) -> Unit,
    onRunConnectionTest: () -> Unit = {},
    isSyncing: Boolean = false,
    hasSyncError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showGroupDialog by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember {
        mutableStateOf(WidgetUpdateHelper.isNotificationEnabled(context))
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
    // Ф4: успех/ошибка бэкапа красятся по-разному — флаг живёт рядом с сообщением
    var backupIsError by remember { mutableStateOf(false) }
    val backupScope = rememberCoroutineScope()

    // Ф4: «Проверить соединение» = viewModel.syncSchedule() (асинхронно). Результат ловим
    // по переходу isSyncing true→false: hasSyncError определяет ✓/✗. Состояние в теле экрана
    // (не в item{}) — иначе терялось при скролле и «Запущено…» висело вечно.
    var connectionState by remember { mutableStateOf(ConnState.IDLE) }
    var wasSyncing by remember { mutableStateOf(false) }
    LaunchedEffect(isSyncing) {
        if (wasSyncing && !isSyncing && connectionState == ConnState.RUNNING) {
            connectionState = if (hasSyncError) ConnState.FAIL else ConnState.OK
        }
        wasSyncing = isSyncing
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            backupMessage = try {
                // QA#20: SAF-поток и сборка бэкапа — на IO, не на Main
                withContext(Dispatchers.IO) {
                    val json = com.politex.util.BackupManager.buildBackup(context)
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
                // QA#20: SAF-поток и восстановление — на IO; результат разбираем на Main
                // (onGroupChanged дёргает живой ViewModel)
                val restored = withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    if (json.isNullOrBlank()) {
                        null
                    } else {
                        com.politex.util.BackupManager.restoreBackup(context, json)
                    }
                }
                if (restored == null) {
                    backupIsError = true
                    "Файл пустой"
                } else {
                    restored.fold(
                        onSuccess = { result ->
                            backupIsError = false
                            // QA#9(а): prefs уже перезаписаны в restoreBackup — дёргаем
                            // onGroupChanged, чтобы живой AppViewModel подхватил группу
                            result.groupRestored?.let { onGroupChanged(it) }
                            val parts = mutableListOf("Восстановлено заданий: ${result.tasksRestored}")
                            if (result.gradesRestored > 0) parts += "оценок: ${result.gradesRestored}"
                            if (result.remindersRestored > 0) parts += "напоминаний: ${result.remindersRestored}"
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

    val specialty = remember(groupInfo.specialtyCode) {
        MpkCurriculum.getSpecialty(groupInfo.specialtyCode)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("МОЯ ГРУППА") }
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = groupInfo.canonicalName,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = ColorTextTitle,
                                    ),
                                )
                                val spec = specialty?.shortName
                                if (!spec.isNullOrBlank()) {
                                    Text(
                                        text = spec,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextMuted,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier
                                // ST4: хитбокс «СМЕНИТЬ» 48dp — смена группы важное действие
                                .heightIn(min = com.politex.ui.theme.Tokens.ButtonHeight) // V15: токен 44dp
                                .bouncyClickable { showGroupDialog = true },
                        ) {
                            // Surface — это Box с выравниванием TopStart: без обёртки текст 11sp
                            // прилипал к верху 48dp-кнопки, снизу оставалась пустая полоса.
                            // Тот же приём, что в GroupScheduleScreen («СМЕНИТЬ» там по центру).
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = "СМЕНИТЬ",
                                    softWrap = false,
                                    maxLines = 1,
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

            // X28: порядок секций — расписание/синхронизация сверху, тема компактно, данные внизу
            item { SectionHeader("РАСПИСАНИЕ И СИНХРОНИЗАЦИЯ") }
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
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
                                Column {
                                    Text(
                                        text = "Уведомления о расписании",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = ColorTextTitle,
                                        ),
                                    )
                                    Text(
                                        text = if (isNotificationEnabled) "Включены (10:00 – 21:00)" else "Отключены",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextMuted,
                                        ),
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
                                    checkedTrackColor = ColorBrandBlue,
                                ),
                                modifier = Modifier.testTag("notifications_switch"),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Приходит один раз, когда на сайте появляется расписание на следующий день.",
                            style = androidx.compose.ui.text.TextStyle(
                                color = ColorTextMuted,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }

            // X27: «Проверить соединение» — подключён мёртвый параметр onRunConnectionTest
            // (MainScreen передаёт viewModel.syncSchedule()): кнопка запускает проверку,
            // Ф4: состояние (connectionState) поднято в тело экрана, спиннер — по isSyncing из VM.
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth(),
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
                                text = "Проверить соединение",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorTextTitle,
                                ),
                            )
                            // Ф4: успех/ошибка различимы — ✓ зелёный, ✗ красный
                            when (connectionState) {
                                ConnState.OK -> Text(
                                    text = "✓ Соединение есть, расписание обновилось",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorSuccess,
                                    ),
                                )
                                ConnState.FAIL -> Text(
                                    text = "✗ Не удалось связаться с сайтом",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorError,
                                    ),
                                )
                                else -> {}
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier
                                .heightIn(min = com.politex.ui.theme.Tokens.ButtonHeight)
                                .testTag("connection_test_button")
                                // Ф4: на время синхронизации кнопка заблокирована —
                                // повторные клики не запускают syncSchedule снова
                                .bouncyClickable(enabled = !isSyncing) {
                                    connectionState = ConnState.RUNNING
                                    onRunConnectionTest()
                                },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        text = "ЗАПУСТИТЬ",
                                        softWrap = false,
                                        maxLines = 1,
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
            }

            item { SectionHeader("КАРТА") }

            // W36/X28: тогл «Маршруты по карте» — вынесен из «Обратной связи» в свою секцию
            item {
                var routesEnabled by remember {
                    mutableStateOf(
                        com.politex.widget.WidgetUpdateHelper.getPrefs(context)
                            .getBoolean("map_routes_enabled", true),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
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
                                    com.politex.widget.WidgetUpdateHelper.getPrefs(context)
                                        .edit()
                                        .putBoolean("map_routes_enabled", on)
                                        .apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ColorBrandBlue,
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

            item { SectionHeader("ЗВОНКИ И СТРОКА ДО ЗВОНКА") }
            item {
                BellCountdownNotificationCard()
            }

            item { SectionHeader("ОЦЕНКИ") }
            // X9: глобальный тогл весов — пишет GradeSettings, GradesScreen читает тот же ключ
            item {
                var weightsEnabled by remember {
                    mutableStateOf(GradeSettings.getGlobalWeightsEnabled(context))
                }
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grades_weights_toggle"),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Учитывать веса категорий",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorTextTitle,
                                ),
                            )
                            Switch(
                                checked = weightsEnabled,
                                onCheckedChange = { on ->
                                    weightsEnabled = on
                                    GradeSettings.setGlobalWeightsEnabled(context, on)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ColorBrandBlue,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Когда выключено, средний балл считается без весов во всех предметах.",
                            style = androidx.compose.ui.text.TextStyle(
                                color = ColorTextMuted,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }

            item {
                SectionHeader("ВНЕШНИЙ ВИД")
            }
            item {
                // N13: компактная тема — горизонтальная лента свотчей в одну строку
                // вместо развернутой сетки на весь экран (как чипы категорий в AddTaskDialog)
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
                                if (isSelected) ColorBrandBlue else ColorBorderLight,
                            ),
                            modifier = Modifier.bouncyClickable { onThemePicked(theme) },
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(92.dp)
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // превью темы: верхняя панель + брендовая полоса
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
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = ColorTextTitle,
                                        textAlign = TextAlign.Center,
                                    ),
                                )
                                // W30: галочка с выбранного свотча УБРАНА (меняла высоту —
                                // масштаб ленты прыгал при скролле). Выбор виден только
                                // синей обводкой + жирным заголовком.
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }

            item { SectionHeader("ДАННЫЕ") }
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Резервная копия",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // X28: описание сокращено по заказчику — одна строка вместо трёх
                        Text(
                            text = "Настройки и всё остальное сохраняются в файл.",
                            style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionChip(
                                text = "СОХРАНИТЬ В ФАЙЛ",
                                filled = true,
                                modifier = Modifier.weight(1f),
                                onClick = { exportLauncher.launch(defaultBackupFileName()) },
                            )
                            ActionChip(
                                text = "ВОССТАНОВИТЬ",
                                filled = false,
                                modifier = Modifier.weight(1f),
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            )
                        }
                        if (backupMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = backupMessage,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    // Ф4: успех зелёный, ошибка красная — не одним синим
                                    color = if (backupIsError) ColorError else ColorSuccess,
                                ),
                            )
                        }
                    }
                }
            }

            // X37: админ-сброс пройденных туториалов; в standard-сборке строки нет
            if (BuildConfig.FLAVOR == "admin") {
                item {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBgMain,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = com.politex.ui.theme.Tokens.HitTarget)
                            .testTag("reset_tutorials_button")
                            .bouncyClickable {
                                TutorialFlags.resetAll(context)
                                Toast
                                    .makeText(context, "Обучения будут показаны заново", Toast.LENGTH_SHORT)
                                    .show()
                            },
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = "Сбросить обучения",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorTextTitle,
                                ),
                            )
                        }
                    }
                }
            }

            item { SectionHeader("О ПРИЛОЖЕНИИ") }
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
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
                            text = "Telegram",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Text(
                            text = "@Betterthannothing12",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorBrandBlue,
                            ),
                        )
                    }
                }
            }

            // N14: «Сайт колледжа» переехал сюда из вкладки «Другое»
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
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

            // X28: строка версии — последняя в «О приложении»
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
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
                            text = "Версия",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = ColorTextMuted,
                            ),
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
            },
        )
    }
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
        mutableStateOf(com.politex.util.BellCountdownNotifier.isEnabled(context))
    }
    var selectedStyle by remember {
        mutableStateOf(com.politex.util.BellCountdownNotifier.getStyle(context))
    }
    var selectedTheme by remember {
        mutableStateOf(com.politex.util.BellCountdownNotifier.getTheme(context))
    }
    // C3: разрешение на точные будильники; перепроверяем на ON_RESUME (возврат из настроек системы)
    var canExactAlarms by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canExactAlarms = canScheduleExactAlarms(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            isOngoingEnabled = true
            com.politex.util.BellCountdownNotifier.setEnabled(context, true)
            com.politex.widget.WidgetAlarm.scheduleNext(context)
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
                            text = "Строка «До звонка»",
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
                                com.politex.util.BellCountdownNotifier.setEnabled(context, true)
                                com.politex.widget.WidgetAlarm.scheduleNext(context)
                            }
                        } else {
                            isOngoingEnabled = false
                            com.politex.util.BellCountdownNotifier.setEnabled(context, false)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ColorBrandBlue,
                    ),
                    modifier = Modifier.testTag("bell_notification_switch"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Постоянная строка со временем до звонка — обновляется каждую минуту (в стиле «с секундами» — каждую секунду).",
                style = androidx.compose.ui.text.TextStyle(
                    color = ColorTextMuted,
                    fontSize = 11.sp,
                ),
            )

            // C3: нет разрешения на точные будильники — отсчёт будет с задержкой, предлагаем открыть настройки
            if (exactAlarmHintNeeded(Build.VERSION.SDK_INT, canExactAlarms)) {
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
                        // ST3: хитбокс кнопки 44dp
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .bouncyClickable { openExactAlarmSettings(context) },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = "РАЗРЕШИТЬ",
                                    softWrap = false,
                                    maxLines = 1,
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

            if (isOngoingEnabled) {
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

                // W26: стиль «Мой Политех» (WITH_APP_NAME) убран из выбора по заказчику —
                // остаются: компактный / с номером урока / с полосой прогресса / с секундами
                com.politex.util.BellCountdownNotifier.Style.entries
                    .filter { it != com.politex.util.BellCountdownNotifier.Style.WITH_APP_NAME }
                    .forEach { style ->
                    val isSelected = selectedStyle == style
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                        color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            // ST3: хитбокс строки-опции 44dp
                            .heightIn(min = 44.dp)
                            .bouncyClickable {
                                selectedStyle = style
                                com.politex.util.BellCountdownNotifier.setStyle(context, style)
                            },
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedStyle = style
                                        com.politex.util.BellCountdownNotifier.setStyle(context, style)
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stylePreviewTitle(style),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ColorTextTitle,
                                    ),
                                )
                            }
                            Text(
                                text = stylePreviewBody(style),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = ColorTextMuted,
                                ),
                                modifier = Modifier.padding(start = 28.dp),
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

                com.politex.util.BellCountdownNotifier.Theme.entries.forEach { theme ->
                    val isSelected = selectedTheme == theme
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                        color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            // ST3: хитбокс строки-опции 44dp
                            .heightIn(min = 44.dp)
                            .bouncyClickable {
                                selectedTheme = theme
                                com.politex.util.BellCountdownNotifier.setTheme(context, theme)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        if (theme.accent == 0) ColorSurfaceVariantLight else Color(theme.accent),
                                        CircleShape,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = theme.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = ColorTextBody,
                                ),
                            )
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

                var selectedScope by remember {
                    mutableStateOf(com.politex.util.BellCountdownNotifier.getScope(context))
                }
                com.politex.util.BellCountdownNotifier.Scope.entries.forEach { scope ->
                    val isSelected = selectedScope == scope
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                        color = if (isSelected) ColorSurfaceHighlight else ColorBgMain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            // ST3: хитбокс строки-опции 44dp
                            .heightIn(min = 44.dp)
                            .bouncyClickable {
                                selectedScope = scope
                                com.politex.util.BellCountdownNotifier.setScope(context, scope)
                            },
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedScope = scope
                                        com.politex.util.BellCountdownNotifier.setScope(context, scope)
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

// C3: на SDK < 31 точные будильники разрешены всегда, на 31+ спрашиваем AlarmManager
internal fun canScheduleExactAlarms(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

// C3: строка-подсказка нужна только на SDK 31+ и без разрешения
internal fun exactAlarmHintNeeded(sdkInt: Int, canScheduleExact: Boolean): Boolean = sdkInt >= Build.VERSION_CODES.S && !canScheduleExact

// QA#12: открытие внешней ссылки — без браузера не роняемся, а говорим пользователю.
// Общий путь для «Сайт колледжа» и «Портал расписания».
private fun openLink(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Нет браузера", Toast.LENGTH_SHORT).show()
        false
    }

// C3: системная страница выдачи разрешения на точные будильники (intent c package URI)
private fun openExactAlarmSettings(context: Context) {    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    } catch (_: Exception) {
        // нет такой страницы в прошивке — открываем общие настройки
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

private fun stylePreviewTitle(style: com.politex.util.BellCountdownNotifier.Style): String = when (style) {
    // QA#17: WITH_APP_NAME недостижим (Style.from маппит его в COMPACT) — превью как у компактного
    com.politex.util.BellCountdownNotifier.Style.COMPACT,
    com.politex.util.BellCountdownNotifier.Style.WITH_APP_NAME,
    -> "До звонка: 12 мин"
    com.politex.util.BellCountdownNotifier.Style.WITH_LESSON -> "До звонка: 12 мин"
    com.politex.util.BellCountdownNotifier.Style.WITH_PROGRESS -> "До звонка: 12 мин"
    com.politex.util.BellCountdownNotifier.Style.WITH_SECONDS -> "До звонка: 11:32"
}

private fun stylePreviewBody(style: com.politex.util.BellCountdownNotifier.Style): String = when (style) {
    com.politex.util.BellCountdownNotifier.Style.COMPACT,
    com.politex.util.BellCountdownNotifier.Style.WITH_APP_NAME,
    -> "Только суть — сколько осталось"
    com.politex.util.BellCountdownNotifier.Style.WITH_LESSON ->
        "Плюс номер идущего урока или «Перемена»"
    com.politex.util.BellCountdownNotifier.Style.WITH_PROGRESS ->
        "С полосой прогресса урока"
    com.politex.util.BellCountdownNotifier.Style.WITH_SECONDS ->
        "Посекундный отсчёт — обновляется каждую секунду"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ColorBrandBlue,
        ),
        modifier = Modifier.padding(top = 4.dp),
    )
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
            // ST3: хитбокс чипа 48dp
            .heightIn(min = com.politex.ui.theme.Tokens.ButtonHeight) // V15: токен 44dp
            .bouncyClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                softWrap = false,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (filled) Color.White else ColorTextTitle,
                ),
            )
        }
    }
}
