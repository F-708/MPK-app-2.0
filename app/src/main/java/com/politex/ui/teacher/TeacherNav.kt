package com.politex.ui.teacher

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.AppGraph
import com.politex.data.model.GroupInfo
import com.politex.ui.screens.BellsScreen
import com.politex.ui.screens.CollegeMapScreen
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTopBar

// Оболочка приложения преподавателя (TR5): 3 вкладки Расписание/График/Ещё —
// КОПИЯ студенческой нижней навигации (MainScreen.kt:235-286). Свайпа между вкладками НЕТ.

enum class TeacherTab { SCHEDULE, BELLS, MORE }

/**
 * Чистая логика навигации (покрыта TeacherNavLogicTest).
 * Контракт deep-link: MainActivity-intent extra [EXTRA_OPEN_TAB].
 * Принимаем и новые ("schedule"/"bells"), и старые ("today"/"week") строки виджетов.
 */
object TeacherNavLogic {
    const val EXTRA_OPEN_TAB = "EXTRA_OPEN_TAB"

    fun tabForExtra(raw: String?): TeacherTab =
        when (raw?.trim()?.lowercase()) {
            "bells", "week" -> TeacherTab.BELLS
            "more" -> TeacherTab.MORE
            else -> TeacherTab.SCHEDULE
        }
}

private sealed interface TeacherSubPage {
    data class Map(val room: String?) : TeacherSubPage

    object Bells : TeacherSubPage

    object OemHelp : TeacherSubPage

    data class Group(val name: String) : TeacherSubPage
}

// Заглушка группы для BellsScreen: звонки группо-независимы, canonicalName "00О" не
// совпадёт ни с одной реальной группой → todayLessons пусты → рельс серый (reuse-map §3.2).
private val bellsStubGroup = GroupInfo(rawName = "", course = 0, groupNumber = 0, specialtyCode = 'О')

@Composable
fun TeacherNav(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(TeacherTab.SCHEDULE) }
    // ponytail: remember, не rememberSaveable — sealed-иерархия не влезает в Bundle; поворот сбрасывает подстраницу, это ок
    var subPage by remember { mutableStateOf<TeacherSubPage?>(null) }

    LaunchedEffect(Unit) {
        val intent = (context as? Activity)?.intent ?: return@LaunchedEffect
        if (intent.hasExtra(TeacherNavLogic.EXTRA_OPEN_TAB)) {
            tab = TeacherNavLogic.tabForExtra(intent.getStringExtra(TeacherNavLogic.EXTRA_OPEN_TAB))
        }
    }

    // Back: подстраница → закрыли; не-корневая вкладка → Расписание; корень → выход (системный).
    BackHandler(enabled = subPage != null || tab != TeacherTab.SCHEDULE) {
        if (subPage != null) {
            subPage = null
        } else {
            tab = TeacherTab.SCHEDULE
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorBgMain,
        bottomBar = {
            // Копия студенческой нижней навигации (MainScreen.kt:235-286): высота 72dp,
            // тот же NavigationBarItemDefaults.colors, иконки CalendarMonth/AccessTime/Apps, label 10sp.
            NavigationBar(
                containerColor = ColorBgMain,
                tonalElevation = 0.dp,
                modifier = Modifier.testTag("teacher_bottom_nav").height(72.dp),
            ) {
                val tabs =
                    listOf(
                        TabSpec(TeacherTab.SCHEDULE, "Расписание", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                        TabSpec(TeacherTab.BELLS, "График", Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
                        TabSpec(TeacherTab.MORE, "Ещё", Icons.Filled.Apps, Icons.Outlined.Apps),
                    )
                tabs.forEach { spec ->
                    val selected = tab == spec.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            subPage = null
                            tab = spec.tab
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) spec.filled else spec.outlined,
                                contentDescription = spec.title,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = spec.title,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
                        modifier = Modifier.testTag("teacher_tab_${spec.tab.name.lowercase()}"),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(ColorBgMain)) {
            when (val sub = subPage) {
                is TeacherSubPage.Map ->
                    CollegeMapScreen(
                        initialRoom = sub.room,
                        onBack = { subPage = null },
                    )

                TeacherSubPage.Bells -> TeacherBellsPage(onBack = { subPage = null })
                TeacherSubPage.OemHelp -> TeacherOemHelpPage(onBack = { subPage = null })
                is TeacherSubPage.Group ->
                    TeacherGroupLessonsScreen(
                        group = sub.name,
                        profile = profile,
                        onBack = { subPage = null },
                    )

                null ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        when (tab) {
                            TeacherTab.SCHEDULE ->
                                TeacherHomeScreen(
                                    profile = profile,
                                    onPickProfile = onPickProfile,
                                    onRoomClick = { room -> subPage = TeacherSubPage.Map(room) },
                                    onGroupClick = { group -> subPage = TeacherSubPage.Group(group) },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )

                            TeacherTab.BELLS ->
                                BellsScreen(
                                    groupInfo = bellsStubGroup,
                                    scheduleRepository = AppGraph.scheduleRepository,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )

                            TeacherTab.MORE ->
                                TeacherMoreScreen(
                                    profile = profile,
                                    onPickProfile = onPickProfile,
                                    onOpenBells = { subPage = TeacherSubPage.Bells },
                                    onOpenMap = { subPage = TeacherSubPage.Map(null) },
                                    onOpenOemHelp = { subPage = TeacherSubPage.OemHelp },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )
                        }
                    }
            }
        }
    }
}

private data class TabSpec(
    val tab: TeacherTab,
    val title: String,
    val filled: ImageVector,
    val outlined: ImageVector,
)
