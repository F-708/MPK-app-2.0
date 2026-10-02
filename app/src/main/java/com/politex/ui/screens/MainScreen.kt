package com.politex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.politex.data.model.GroupInfo
import com.politex.ui.components.MainTopBar
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.viewmodel.ARG_SCROLL
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.AppViewModel
import com.politex.ui.viewmodel.ROUTE_BELLS
import com.politex.ui.viewmodel.ROUTE_COLLEGE
import com.politex.ui.viewmodel.ROUTE_GROUP_SCHEDULE
import com.politex.ui.viewmodel.ROUTE_LESSONS
import com.politex.ui.viewmodel.ROUTE_MAP
import com.politex.ui.viewmodel.ROUTE_SCHEDULE
import com.politex.ui.viewmodel.ROUTE_SETTINGS
import com.politex.ui.viewmodel.ROUTE_SPECIALTY
import com.politex.ui.viewmodel.ROUTE_STUDENTS
import com.politex.ui.viewmodel.ROUTE_TEACHER
import com.politex.ui.viewmodel.ROUTE_TEACHERS
import kotlinx.coroutines.flow.collectLatest
import java.net.URLEncoder

// ---------- E4: построители route-строк (чистые функции — см. NavRouteTest) ----------

// URLEncoder — form-encoding (пробел → «+»), но Navigation трактует «+» буквально и не
// декодирует его в пробел. Для query-компонента URI правильный пробел — «%20»: докодируем.
// (Критично для ФИО преподавателей с пробелами: иначе surnameOf не сматчит → onNotFound.)
private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** Паттерн вкладки с одноразовым scroll-аргументом: «schedule» → «schedule?scroll={scroll}». */
fun scrollRoutePattern(base: String): String = "$base?$ARG_SCROLL={$ARG_SCROLL}"

/** Конкретный route вкладки: scrollTarget null/пуст → базовый route без аргумента. */
fun buildScrollRoute(
    base: String,
    scrollTarget: String?,
): String = if (scrollTarget.isNullOrBlank()) base else "$base?$ARG_SCROLL=${enc(scrollTarget)}"

/**
 * Route карты. Семантика R1 сохранена: from != null (включая "") = «показать маршрут»
 * ("" — старт от главного входа); from == null — просто кабинет без маршрута.
 * route=true — явный запрос маршрута (виджет NowNext, N8).
 */
fun buildMapRoute(
    room: String?,
    from: String?,
    showRoute: Boolean,
): String {
    val params =
        buildList {
            if (!room.isNullOrBlank()) add("room=${enc(room)}")
            if (from != null) add("from=${enc(from)}")
            if (showRoute) add("route=true")
        }
    val base = ROUTE_MAP.substringBefore("?")
    return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
}

fun buildTeacherRoute(name: String): String = "${ROUTE_TEACHER.substringBefore("?")}?name=${enc(name)}"

fun buildGroupScheduleRoute(group: String?): String {
    val base = ROUTE_GROUP_SCHEDULE.substringBefore("?")
    return if (group.isNullOrBlank()) base else "$base?group=${enc(group)}"
}

/**
 * Стандартный паттерн bottom-navigation: состояние каждого таба ЖИВЁТ (saveState/restoreState),
 * повторный тап не плодит записи (launchSingleTop), «назад» с таба выходит из приложения.
 */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * E4/N15: NavHost вместо when(currentTab) (ARC-01). Поддерево вкладки больше не уничтожается
 * при переключении — записи табов сохраняются паттерном navigateToTab, глубокие экраны
 * (карта/преподаватель/расписание группы/…) — обычные записи backstack'а НАД вкладками,
 * поэтому системное «назад» всегда возвращает ровно на предыдущий экран (корень N15 устранён).
 */
@Composable
fun MainScreen(
    viewModel: AppViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val currentGroupInfo =
        uiState.groupInfo ?: GroupInfo(
            rawName = uiState.currentGroupName,
            course = 0,
            groupNumber = 0,
            specialtyCode = 'О',
        )

    if (uiState.showGroupSelection) {
        com.politex.ui.components.GroupSelectionDialog(
            currentGroupName = uiState.currentGroupName,
            onDismissRequest = { },
            showClose = false,
            onGroupSelected = { newGroup -> viewModel.setGroup(newGroup) },
        )
    }

    // Подсветка вкладки: на глубоких экранах (карта/преподаватель/расписание группы)
    // подсвечен таб, ИЗ которого пришли — их route не совпадает ни с одним табом, поэтому
    // selectedTab просто не меняется при переходе вглубь. rememberSaveable переживает
    // поворот/смерть процесса; currentBackStackEntryAsState — публичный API (в отличие от currentBackStack).
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.SCHEDULE) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry) {
        val base = backStackEntry?.destination?.route?.substringBefore("?")
        AppTab.entries.firstOrNull { it.route == base }?.let { selectedTab = it }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { MainTopBar() },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorDividerLight),
                )
                NavigationBar(
                    containerColor = ColorBgMain,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("main_bottom_nav"),
                ) {
                    val tabs =
                        listOf(
                            Triple(AppTab.SCHEDULE, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                            Triple(AppTab.TASKS, Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
                            Triple(AppTab.BELLS, Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
                            Triple(AppTab.COLLEGE, Icons.Filled.Apps, Icons.Outlined.Apps),
                        )

                    tabs.forEach { (tab, filledIcon, outlinedIcon) ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) filledIcon else outlinedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp),
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
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.sp,
                                    ),
                                )
                            },
                            colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = ColorBrandBlue,
                                selectedTextColor = ColorBrandBlue,
                                indicatorColor = ColorSurfaceHighlight,
                                unselectedIconColor = ColorTextMuted,
                                unselectedTextColor = ColorTextMuted,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_SCHEDULE,
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val scrollArg =
                listOf(
                    navArgument(ARG_SCROLL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                )

            composable(scrollRoutePattern(ROUTE_SCHEDULE), arguments = scrollArg) { entry ->
                // C3: расписание и домашка читаются из VM (кэш переживает смену вкладок);
                // чтение внутри записи — эмиссии Room не рекомпозировали поддеревья других табов.
                val groupLessons by viewModel.groupLessons.collectAsState()
                val pendingTasks by viewModel.pendingTasks.collectAsState()
                // O1/D: выбранный день — rememberSaveable записи: saveState/restoreState
                // паттерна вкладок возвращает его при возврате на таб.
                var scheduleSlot by rememberSaveable { mutableIntStateOf(0) }
                var scheduleDate by rememberSaveable { mutableStateOf("") }
                val scrollRequest = rememberScrollRequest(entry)
                ScheduleScreen(
                    groupInfo = currentGroupInfo,
                    groupLessonsFromVm = groupLessons,
                    pendingTasksFromVm = pendingTasks,
                    scheduleRepository = viewModel.scheduleRepository,
                    onSyncRequest = { viewModel.syncSchedule() },
                    // SUI-02: сетевой сбой не должен маскироваться под «не опубликовано»
                    hasSyncError = uiState.hasSyncError,
                    isSyncing = uiState.isSyncing,
                    // N18: тап по значку домашки открывает вкладку заданий
                    onOpenTasks = { navController.navigateToTab(ROUTE_LESSONS) },
                    onTeacherClick = { name -> navController.navigate(buildTeacherRoute(name)) },
                    onRoomClick = { room, fromRoom ->
                        navController.navigate(buildMapRoute(room, fromRoom, showRoute = false))
                    },
                    scrollToCurrentRequested = scrollRequest == "CURRENT_LESSON",
                    onScrollToCurrentConsumed = { entry.savedStateHandle[ARG_SCROLL] = "" },
                    selectedSlot = scheduleSlot,
                    onSlotChange = { scheduleSlot = it },
                    selectedDateString = scheduleDate,
                    onDateChange = { scheduleDate = it },
                )
            }

            composable(ROUTE_LESSONS) {
                LessonsHubScreen(
                    groupInfo = currentGroupInfo,
                    taskRepository = viewModel.taskRepository,
                    modifier = Modifier.fillMaxSize(),
                    // T1: undo-снекбар удаления задания — хост уже висит в Scaffold
                    snackbarHostState = snackbarHostState,
                )
            }

            composable(scrollRoutePattern(ROUTE_BELLS), arguments = scrollArg) { entry ->
                val scrollRequest = rememberScrollRequest(entry)
                BellsScreen(
                    groupInfo = currentGroupInfo,
                    scheduleRepository = viewModel.scheduleRepository,
                    scrollToCurrentRequested = scrollRequest == "CURRENT_BELL",
                    onScrollToCurrentConsumed = { entry.savedStateHandle[ARG_SCROLL] = "" },
                )
            }

            composable(ROUTE_COLLEGE) {
                CollegeScreen(
                    groupInfo = currentGroupInfo,
                    onOpenSpecialty = { navController.navigate(ROUTE_SPECIALTY) },
                    onOpenTeachers = { navController.navigate(ROUTE_TEACHERS) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onOpenStudents = { navController.navigate(ROUTE_STUDENTS) },
                    onOpenGroupSchedule = { navController.navigate(buildGroupScheduleRoute(null)) },
                    onOpenMap = { navController.navigate(buildMapRoute(null, null, showRoute = false)) },
                )
            }

            composable(ROUTE_SPECIALTY) {
                SpecialtyRoute(
                    groupInfo = currentGroupInfo,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(ROUTE_TEACHERS) {
                TeachersRoute(
                    groupInfo = currentGroupInfo,
                    scheduleRepository = viewModel.scheduleRepository,
                    onOpenTeacher = { teacher ->
                        navController.navigate(buildTeacherRoute(teacher.name))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                ROUTE_TEACHER,
                arguments =
                listOf(
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                TeacherCardRoute(
                    teacherName = entry.arguments?.getString("name").orEmpty(),
                    groupInfo = currentGroupInfo,
                    scheduleRepository = viewModel.scheduleRepository,
                    onOpenRoom = { room ->
                        navController.navigate(buildMapRoute(room, null, showRoute = false))
                    },
                    onNotFound = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                ROUTE_MAP,
                arguments =
                listOf(
                    navArgument("room") {
                        type = NavType.StringType
                        defaultValue = null
                        nullable = true
                    },
                    navArgument("from") {
                        type = NavType.StringType
                        defaultValue = null
                        nullable = true
                    },
                    navArgument("route") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                CollegeMapScreen(
                    initialRoom = entry.arguments?.getString("room"),
                    // "" (не null!) = маршрут от главного входа — см. buildMapRoute/R1
                    fromRoom = entry.arguments?.getString("from"),
                    showRoute = entry.arguments?.getBoolean("route") == true,
                    onBack = { navController.popBackStack() },
                    onOpenTeacher = { name -> navController.navigate(buildTeacherRoute(name)) },
                )
            }

            composable(
                ROUTE_GROUP_SCHEDULE,
                arguments =
                listOf(
                    navArgument("group") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                GroupScheduleRoute(
                    group = entry.arguments?.getString("group").orEmpty(),
                    groupInfo = currentGroupInfo,
                    scheduleRepository = viewModel.scheduleRepository,
                    onGroupPicked = { group ->
                        navController.navigate(buildGroupScheduleRoute(group))
                    },
                    // N15: «СМЕНИТЬ» и системное «назад» — popBackStack на запись выбора
                    // группы (group="), состояние курса в ней сохранено (rememberSaveable).
                    onChangeGroup = { navController.popBackStack() },
                    onTeacherClick = { name -> navController.navigate(buildTeacherRoute(name)) },
                    onRoomClick = { room, fromRoom ->
                        navController.navigate(buildMapRoute(room, fromRoom, showRoute = false))
                    },
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsRoute(
                    groupInfo = currentGroupInfo,
                    onGroupChanged = { newGroup -> viewModel.setGroup(newGroup) },
                    onRunConnectionTest = { viewModel.syncSchedule() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(ROUTE_STUDENTS) {
                StudentsRoute(
                    scheduleRepository = viewModel.scheduleRepository,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * E4/шаг 6: одноразовый запрос автопрокрутки из виджета (EXTRA_SCROLL_TARGET).
 * Механика SavedStateHandle: аргумент route'а — только начальное значение, экран-потребитель
 * гасит запрос (onScrollToCurrentConsumed → handle["scroll"]=""), поэтому ни повторная
 * композиция, ни restoreState при возврате на таб повторно не скроллят. Тёплый повторный
 * тап виджета MainActivity пишет напрямую в handle той же записи — StateFlow эмитит снова.
 */
@Composable
private fun rememberScrollRequest(entry: NavBackStackEntry): String {
    val argScroll = entry.arguments?.getString(ARG_SCROLL).orEmpty()
    val flow = remember(entry) { entry.savedStateHandle.getStateFlow(ARG_SCROLL, argScroll) }
    return flow.collectAsState().value
}
