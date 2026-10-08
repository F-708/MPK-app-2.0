package com.politex.ui.teacher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.components.AppCard
import com.politex.ui.screens.CollegeMapScreen
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable

// Оболочка приложения преподавателя (SPEC §6.1): 3 вкладки Сегодня/Неделя/Ещё.
// Свайп между вкладками в teacher v1 НЕ добавляем (осознанное упрощение).

enum class TeacherTab { TODAY, WEEK, MORE }

/**
 * Чистая логика навигации (покрыта TeacherNavLogicTest).
 * Контракт deep-link с T4-B (уведомления): MainActivity-intent extras
 * [EXTRA_OPEN_TAB] ("today"/"week"/"more") и [EXTRA_MARK_OVERRIDES] (Boolean).
 */
object TeacherNavLogic {
    const val EXTRA_OPEN_TAB = "EXTRA_OPEN_TAB"
    const val EXTRA_MARK_OVERRIDES = "EXTRA_MARK_OVERRIDES"

    fun tabForExtra(raw: String?): TeacherTab =
        when (raw?.trim()?.lowercase()) {
            "week" -> TeacherTab.WEEK
            "more" -> TeacherTab.MORE
            else -> TeacherTab.TODAY
        }
}

private sealed interface TeacherSubPage {
    data class Map(val room: String?) : TeacherSubPage

    object Bells : TeacherSubPage

    object OemHelp : TeacherSubPage

    data class Group(val name: String) : TeacherSubPage
}

@Composable
fun TeacherNav(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val metrics = LocalTeacherMetrics.current
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(TeacherTab.TODAY) }
    // ponytail: remember, не rememberSaveable — sealed-иерархия не влезает в Bundle; поворот сбрасывает подстраницу, это ок
    var subPage by remember { mutableStateOf<TeacherSubPage?>(null) }
    // SPEC §5.4/фича 17: deep-link «Пометить изменение» из уведомления — одноразовая плашка.
    var markOverridesPlate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val intent = (context as? Activity)?.intent ?: return@LaunchedEffect
        if (intent.hasExtra(TeacherNavLogic.EXTRA_OPEN_TAB)) {
            tab = TeacherNavLogic.tabForExtra(intent.getStringExtra(TeacherNavLogic.EXTRA_OPEN_TAB))
        }
        if (intent.getBooleanExtra(TeacherNavLogic.EXTRA_MARK_OVERRIDES, false)) {
            tab = TeacherTab.TODAY
            markOverridesPlate = true
        }
    }

    // Back: подстраница → закрыли; не-корневая вкладка → СЕГОДНЯ; корень → выход (системный).
    BackHandler(enabled = subPage != null || tab != TeacherTab.TODAY) {
        if (subPage != null) {
            subPage = null
        } else {
            tab = TeacherTab.TODAY
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorBgMain,
        bottomBar = {
            NavigationBar(
                containerColor = ColorBgMain,
                tonalElevation = 0.dp,
                // стиль студенческой навигации (BB17): высота 72dp
                modifier = Modifier.testTag("teacher_bottom_nav").height(72.dp),
            ) {
                val tabs =
                    listOf(
                        TabSpec(TeacherTab.TODAY, "СЕГОДНЯ", Icons.Filled.Today, Icons.Outlined.Today),
                        TabSpec(TeacherTab.WEEK, "НЕДЕЛЯ", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                        TabSpec(TeacherTab.MORE, "ЕЩЁ", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
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
                        if (markOverridesPlate && tab == TeacherTab.TODAY) {
                            AppCard(
                                color = ColorSurfaceHighlight,
                                modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("teacher_mark_overrides_plate"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Пометьте правку в карточке пары — кнопка «Править»",
                                        style = TextStyle(fontSize = metrics.chipText, color = ColorTextBody, fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier.size(metrics.hitTarget).bouncyClickable { markOverridesPlate = false }.testTag("teacher_mark_plate_close"),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = ColorTextMuted, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        when (tab) {
                            TeacherTab.TODAY ->
                                TeacherHomeScreen(
                                    profile = profile,
                                    onPickProfile = onPickProfile,
                                    onRoomClick = { room -> subPage = TeacherSubPage.Map(room) },
                                    onGroupClick = { group -> subPage = TeacherSubPage.Group(group) },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )

                            TeacherTab.WEEK ->
                                TeacherWeekScreen(
                                    profile = profile,
                                    onRoomClick = { room -> subPage = TeacherSubPage.Map(room) },
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
