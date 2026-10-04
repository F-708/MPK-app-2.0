package com.politex.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
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
import com.politex.ui.components.AppDivider
import com.politex.ui.components.MainTopBar
import com.politex.ui.components.UndoSnackbar
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTopBar
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
import com.politex.ui.screens.materials.ViewerMode
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.collectLatest
import java.net.URLEncoder
import kotlin.math.abs

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

fun scrollRoutePattern(base: String): String = "$base?$ARG_SCROLL={$ARG_SCROLL}"

fun buildScrollRoute(
    base: String,
    scrollTarget: String?,
): String = if (scrollTarget.isNullOrBlank()) base else "$base?$ARG_SCROLL=${enc(scrollTarget)}"

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

enum class BackAction { EXIT, TO_SCHEDULE, NONE }

fun backAction(route: String?): BackAction =
    when (route?.substringBefore("?")) {
        ROUTE_COLLEGE -> BackAction.TO_SCHEDULE
        ROUTE_SCHEDULE, ROUTE_LESSONS, ROUTE_BELLS -> BackAction.EXIT
        else -> BackAction.NONE
    }

fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}

/** Индекс вкладки по маршруту (с отброшенным query), -1 если не таб. */
fun tabIndex(route: String?): Int {
    val base = route?.substringBefore("?")
    return AppTab.entries.indexOfFirst { it.route == base }
}

/** DD4: индекс РОДИТЕЛЬСКОЙ вкладки: точные табы → их индекс; под-экраны Другого → 3; карта (исключение заказчика), неизвестно, null → -1. */
fun ownerTabIndex(route: String?): Int {
    val base = route?.substringBefore("?") ?: return -1
    return when (base) {
        ROUTE_SPECIALTY, ROUTE_TEACHERS, ROUTE_SETTINGS, ROUTE_STUDENTS,
        ROUTE_TEACHER.substringBefore("?"), ROUTE_GROUP_SCHEDULE.substringBefore("?"),
        -> AppTab.COLLEGE.ordinal
        else -> tabIndex(base)
    }
}

/** Направление слайда: +1 влево-переход (вперёд), -1 вправо (назад), 0 не-таб/тот же. */
fun tabSlideDir(from: Int, to: Int): Int = when {
    from < 0 || to < 0 || from == to -> 0
    to > from -> 1
    else -> -1
}

private fun Modifier.tabSwipeGesture(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = composed {
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val edgePx = with(density) { 24.dp.toPx() }
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Зона системного back-жеста — не перехватываем.
            if (down.position.x < edgePx) return@awaitEachGesture
            var dx = 0f
            var dy = 0f
            var claimed = false
            var aborted = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.any { it.isConsumed }) {
                    aborted = true
                    break
                }
                if (event.changes.none { it.pressed }) break
                val change = event.changes.first()
                dx = change.position.x - down.position.x
                dy = change.position.y - down.position.y
                if (!claimed && abs(dx) > slop * 4f && abs(dx) > abs(dy) * 1.5f) {
                    claimed = true
                }
                if (claimed) change.consume()
            }
            if (claimed && !aborted) {
                if (dx < 0) onSwipeLeft() else onSwipeRight()
            }
        }
    }
}

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

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.SCHEDULE) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry) {
        val base = backStackEntry?.destination?.route?.substringBefore("?")
        AppTab.entries.firstOrNull { it.route == base }?.let { selectedTab = it }
    }

    val backAct = backAction(backStackEntry?.destination?.route)
    BackHandler(enabled = backAct != BackAction.NONE) {
        when (backAct) {
            BackAction.EXIT -> (context as? Activity)?.finishAffinity()
            BackAction.TO_SCHEDULE -> navController.navigateToTab(ROUTE_SCHEDULE)
            BackAction.NONE -> {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),

            topBar = {
                if (!ViewerMode.active) MainTopBar()
            },
            bottomBar = {
                if (!ViewerMode.active && !RouteBuilderMode.active) Column(modifier = Modifier.fillMaxWidth()) {
                    AppDivider()
                    NavigationBar(
                        containerColor = ColorBgMain,
                        tonalElevation = 0.dp,
                        // BB17: высота −10% (80dp → 72dp) — место под контент
                        modifier = Modifier.testTag("main_bottom_nav").height(72.dp),
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
                                    selectedIconColor = ColorTopBar,
                                    selectedTextColor = ColorTopBar,
                                    indicatorColor = ColorSurfaceHighlight,
                                    unselectedIconColor = ColorTextMuted,
                                    unselectedTextColor = ColorTextMuted,
                                ),
                            )
                        }
                    }
                }
            },
            snackbarHost = {

                SnackbarHost(hostState = snackbarHostState) { data ->
                    UndoSnackbar(data, onExpire = { snackbarHostState.currentSnackbarData?.dismiss() })
                }
            },
        ) { innerPadding ->
            val swipeEnabled = remember(context, backStackEntry) {
                WidgetUpdateHelper.getPrefs(context).getBoolean("swipe_tabs_enabled", true)
            }
            val curIdx = ownerTabIndex(backStackEntry?.destination?.route)
            val gestureActive = swipeEnabled && curIdx >= 0 && !ViewerMode.active && !RouteBuilderMode.active
            Box(
                modifier =
                if (gestureActive) {
                    Modifier.tabSwipeGesture(
                        onSwipeLeft = {
                            val i = ownerTabIndex(backStackEntry?.destination?.route)
                            if (i in 0 until AppTab.entries.size - 1) {
                                navController.navigateToTab(AppTab.entries[i + 1].route)
                            }
                        },
                        onSwipeRight = {
                            val i = ownerTabIndex(backStackEntry?.destination?.route)
                            if (i > 0) navController.navigateToTab(AppTab.entries[i - 1].route)
                        },
                    )
                } else {
                    Modifier
                },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SCHEDULE,
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    enterTransition = {
                        val d = tabSlideDir(ownerTabIndex(initialState.destination.route), ownerTabIndex(targetState.destination.route))
                        if (d == 0) fadeIn(tween(150)) else slideInHorizontally(tween(280)) { if (d > 0) it else -it }
                    },
                    exitTransition = {
                        val d = tabSlideDir(ownerTabIndex(initialState.destination.route), ownerTabIndex(targetState.destination.route))
                        if (d == 0) fadeOut(tween(150)) else slideOutHorizontally(tween(280)) { if (d > 0) -it else it }
                    },
                    popEnterTransition = {
                        val d = tabSlideDir(ownerTabIndex(initialState.destination.route), ownerTabIndex(targetState.destination.route))
                        if (d == 0) fadeIn(tween(150)) else slideInHorizontally(tween(280)) { if (d > 0) it else -it }
                    },
                    popExitTransition = {
                        val d = tabSlideDir(ownerTabIndex(initialState.destination.route), ownerTabIndex(targetState.destination.route))
                        if (d == 0) fadeOut(tween(150)) else slideOutHorizontally(tween(280)) { if (d > 0) -it else it }
                    },
                ) {
                val scrollArg =
                    listOf(
                        navArgument(ARG_SCROLL) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    )

                composable(scrollRoutePattern(ROUTE_SCHEDULE), arguments = scrollArg) { entry ->

                    val groupLessons by viewModel.groupLessons.collectAsState()
                    val pendingTasks by viewModel.pendingTasks.collectAsState()

                    var scheduleSlot by rememberSaveable { mutableIntStateOf(0) }
                    var scheduleDate by rememberSaveable { mutableStateOf("") }
                    val scrollRequest = rememberScrollRequest(entry)
                    ScheduleScreen(
                        groupInfo = currentGroupInfo,
                        groupLessonsFromVm = groupLessons,
                        pendingTasksFromVm = pendingTasks,
                        scheduleRepository = viewModel.scheduleRepository,
                        onSyncRequest = { date -> viewModel.syncSchedule(targetDate = date) },

                        hasSyncError = uiState.hasSyncError,
                        isSyncing = uiState.isSyncing,

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

                        isSyncing = uiState.isSyncing,
                        hasSyncError = uiState.hasSyncError,
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

    }
}

@Composable
private fun rememberScrollRequest(entry: NavBackStackEntry): String {
    val argScroll = entry.arguments?.getString(ARG_SCROLL).orEmpty()
    val flow = remember(entry) { entry.savedStateHandle.getStateFlow(ARG_SCROLL, argScroll) }
    return flow.collectAsState().value
}
