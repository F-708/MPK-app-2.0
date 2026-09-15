package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GroupInfo
import com.example.ui.components.MainTopBar
import com.example.ui.util.bouncyClickable
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Главный контейнер приложения «МПК Расписание».
 *
 * Объединяет MainTopBar, плавную смену 4 экранов (Расписание, Задания, Звонки, Настройки)
 * и нижнюю панель навигации с тактильным откликом.
 */
@Composable
fun MainScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val diagnosticInfo by viewModel.diagnosticInfo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Безопасный показ Toast/Snackbar СТРОГО на UI потоке через LaunchedEffect
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val currentGroupInfo = uiState.groupInfo ?: GroupInfo(
        rawName = uiState.currentGroupName,
        course = 4,
        groupNumber = 1,
        specialtyCode = 'О'
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MainTopBar(
                currentGroup = uiState.currentGroupName,
                isSyncing = uiState.isSyncing,
                hasSyncError = uiState.hasSyncError,
                onGroupChanged = { newGroup ->
                    viewModel.setGroup(newGroup)
                },
                onSyncClicked = {
                    viewModel.syncSchedule()
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                val tabs = listOf(
                    Triple(AppTab.SCHEDULE, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                    Triple(AppTab.TASKS, Icons.Filled.Checklist, Icons.Outlined.Checklist),
                    Triple(AppTab.BELLS, Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
                    Triple(AppTab.SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings)
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
                                modifier = Modifier
                                    .size(24.dp)
                                    .bouncyClickable { viewModel.selectTab(tab) }
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
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
            AnimatedContent(
                targetState = uiState.currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "MainTabTransition"
            ) { targetTab ->
                when (targetTab) {
                    AppTab.SCHEDULE -> {
                        ScheduleScreen(
                            groupInfo = currentGroupInfo,
                            scheduleRepository = viewModel.scheduleRepository,
                            onSyncRequest = { viewModel.syncSchedule() }
                        )
                    }
                    AppTab.TASKS -> {
                        TasksScreen(
                            groupInfo = currentGroupInfo,
                            taskRepository = viewModel.taskRepository
                        )
                    }
                    AppTab.BELLS -> {
                        BellsScreen()
                    }
                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            groupInfo = currentGroupInfo,
                            isDarkTheme = uiState.isDarkTheme,
                            diagnosticInfo = diagnosticInfo,
                            onThemeChanged = { isDark ->
                                viewModel.toggleTheme(isDark)
                            },
                            onGroupChanged = { newGroup ->
                                viewModel.setGroup(newGroup)
                            },
                            onRunConnectionTest = {
                                viewModel.syncSchedule()
                            }
                        )
                    }
                }
            }
        }
    }
}
