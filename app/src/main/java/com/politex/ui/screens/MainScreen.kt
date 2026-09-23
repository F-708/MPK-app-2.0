package com.politex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.GroupInfo
import com.politex.ui.components.MainTopBar
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.AppViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val currentGroupInfo = uiState.groupInfo ?: GroupInfo(
        rawName = uiState.currentGroupName,

        course = 0,
        groupNumber = 0,
        specialtyCode = 'О'
    )

    if (uiState.showGroupSelection) {
        com.politex.ui.components.GroupSelectionDialog(
            currentGroupName = uiState.currentGroupName,
            onDismissRequest = {  },
            showClose = false,
            onGroupSelected = { newGroup -> viewModel.setGroup(newGroup) }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { MainTopBar() },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorDividerLight)
                )
                NavigationBar(
                    containerColor = ColorBgMain,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    val tabs = listOf(
                        Triple(AppTab.SCHEDULE, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                        Triple(AppTab.TASKS, Icons.Filled.Checklist, Icons.Outlined.Checklist),
                        Triple(AppTab.BELLS, Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
                        Triple(AppTab.COLLEGE, Icons.Filled.Apps, Icons.Outlined.Apps)
                    )

                    tabs.forEach { (tab, filledIcon, outlinedIcon) ->
                        val isSelected = uiState.currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) filledIcon else outlinedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    // N1: без капса — меньше букв, влезает при +15% шрифта;
                                    // Ellipsis вместо тихой обрезки по букве
                                    text = tab.title,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.sp
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ColorBrandBlue,
                                selectedTextColor = ColorBrandBlue,
                                indicatorColor = ColorSurfaceHighlight,
                                unselectedIconColor = ColorTextMuted,
                                unselectedTextColor = ColorTextMuted
                            )
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // 8.1: состояния прокрутки живут НАД when() — смена вкладки не
            // уничтожает их, возврат на вкладку восстанавливает позицию.
            // rememberSaveable — переживает и смерть процесса.
            val scheduleListState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState(0, 0)
            }
            val bellsScrollState = rememberSaveable(saver = ScrollState.Saver) {
                ScrollState(0)
            }
            // O1/D: выбранный день («Сегодня/Завтра» + архивная дата) тоже живёт НАД when().
            // Иначе возврат с «Заданий»/«Звонков» пересоздавал ScheduleScreen и терял день.
            var scheduleSlot by rememberSaveable { mutableIntStateOf(0) }
            var scheduleDate by rememberSaveable { mutableStateOf("") }
            val scrollTarget = uiState.scrollTarget

            when (uiState.currentTab) {
                // C3: расписание и домашка читаются из VM, а не подписываются внутри ScheduleScreen.
                // when() уничтожает поддерево вкладки, поэтому collectAsState там умирал и на возврат
                // отдавал emptyList до нового запроса к Room → спиннер → «ещё не опубликовано».
                // Чтение внутри ветки, чтобы эмиссии Room не рекомпозировали поддеревья других вкладок.
                AppTab.SCHEDULE -> {
                    val groupLessons by viewModel.groupLessons.collectAsState()
                    val pendingTasks by viewModel.pendingTasks.collectAsState()
                    ScheduleScreen(
                        groupInfo = currentGroupInfo,
                        groupLessonsFromVm = groupLessons,
                        pendingTasksFromVm = pendingTasks,
                        scheduleRepository = viewModel.scheduleRepository,
                        onSyncRequest = { viewModel.syncSchedule() },
                        isSyncing = uiState.isSyncing,
                        onTeacherClick = { name -> viewModel.openTeacher(name) },
                        onRoomClick = { room, fromRoom -> viewModel.openMap(room, fromRoom) },
                        listState = scheduleListState,
                        scrollToCurrentRequested = scrollTarget == "CURRENT_LESSON",
                        onScrollToCurrentConsumed = { viewModel.consumeScrollTarget() },
                        selectedSlot = scheduleSlot,
                        onSlotChange = { scheduleSlot = it },
                        selectedDateString = scheduleDate,
                        onDateChange = { scheduleDate = it }
                    )
                }
                AppTab.TASKS -> TasksScreen(
                    groupInfo = currentGroupInfo,
                    taskRepository = viewModel.taskRepository,
                    // T1: undo-снекбар удаления задания — хост уже висит в Scaffold
                    snackbarHostState = snackbarHostState
                )
                AppTab.BELLS -> BellsScreen(
                    groupInfo = currentGroupInfo,
                    scheduleRepository = viewModel.scheduleRepository,
                    scrollState = bellsScrollState,
                    scrollToCurrentRequested = scrollTarget == "CURRENT_BELL",
                    onScrollToCurrentConsumed = { viewModel.consumeScrollTarget() }
                )
                AppTab.COLLEGE -> CollegeTab(viewModel, currentGroupInfo)
            }
        }
    }
}

@Composable
private fun CollegeTab(viewModel: AppViewModel, groupInfo: GroupInfo) {
    val diagnosticInfo by viewModel.diagnosticInfo.collectAsState()
    val pendingTeacher by viewModel.uiState.collectAsState()
    CollegeScreen(
        groupInfo = groupInfo,
        scheduleRepository = viewModel.scheduleRepository,
        onGroupChanged = { newGroup -> viewModel.setGroup(newGroup) },
        onRunConnectionTest = { viewModel.syncSchedule() },
        pendingTeacherName = pendingTeacher.pendingTeacherName,
        onPendingTeacherConsumed = { viewModel.consumePendingTeacher() },
        pendingRoomName = pendingTeacher.pendingRoom,
        pendingFromRoomName = pendingTeacher.pendingFromRoom,
        onPendingRoomConsumed = { viewModel.consumePendingRoom() },
        onReturnToOrigin = { viewModel.restoreReturnTab() }
    )
}
