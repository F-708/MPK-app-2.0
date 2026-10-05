package com.politex.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.GroupInfo
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.TaskRepository
import com.politex.data.repository.toastFor
import com.politex.util.GroupParser
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ROUTE_SCHEDULE = "schedule"
const val ROUTE_LESSONS = "lessons"
const val ROUTE_BELLS = "bells"
const val ROUTE_COLLEGE = "college"
const val ROUTE_SPECIALTY = "college/specialty"
const val ROUTE_TEACHERS = "college/teachers"
const val ROUTE_SETTINGS = "college/settings"
const val ROUTE_STUDENTS = "college/students"
const val ROUTE_TEACHER = "teacher?name={name}"
const val ROUTE_MAP = "map?room={room}&from={from}&route={route}"
const val ROUTE_GROUP_SCHEDULE = "group_schedule?group={group}"

const val ARG_SCROLL = "scroll"

enum class AppTab(
    val title: String,
    val route: String,
) {
    SCHEDULE("Расписание", ROUTE_SCHEDULE),

    TASKS("Уроки", ROUTE_LESSONS),
    BELLS("Звонки", ROUTE_BELLS),
    COLLEGE("Другое", ROUTE_COLLEGE),
}

data class AppUiState(
    val currentGroupName: String,
    val groupInfo: GroupInfo?,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false,
    val showGroupSelection: Boolean = false,
)

class AppViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val database = MpkDatabase.getInstance(application)

    val scheduleRepository = com.politex.AppGraph.scheduleRepository
    val taskRepository = TaskRepository(database.taskDao(), database.taskReminderDao())

    val diagnosticInfo = scheduleRepository.diagnosticInfo

    private val _uiState =
        MutableStateFlow(
            AppUiState(
                currentGroupName = WidgetUpdateHelper.getSelectedGroup(application),
                groupInfo = GroupParser.parse(WidgetUpdateHelper.getSelectedGroup(application)),
                showGroupSelection = !WidgetUpdateHelper.hasSelectedGroup(application),
            ),
        )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val currentGroup: Flow<String> =
        _uiState.map { it.currentGroupName }.distinctUntilChanged()

    // R2#13 (CC7): null до первой эмиссии — экран показывает лоадер,
    // а не вспышку «нет уроков/заданий» на первом кадре
    @OptIn(ExperimentalCoroutinesApi::class)
    val groupLessons: StateFlow<List<LessonEntity>?> =
        currentGroup
            .flatMapLatest { name -> scheduleRepository.getAllLessonsForGroup(name) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingTasks: StateFlow<List<StudentTaskEntity>?> =
        currentGroup
            .flatMapLatest { name -> taskRepository.getPendingTasksForGroup(name) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {

        if (!uiState.value.showGroupSelection) {

            viewModelScope.launch {
                if (!scheduleRepository.hasSchedule(uiState.value.currentGroupName)) {
                    syncSchedule(isAutoSync = true)
                }
            }
        }
    }

    fun setGroup(newGroupName: String) {
        val parsed = GroupParser.parse(newGroupName) ?: return
        WidgetUpdateHelper.setSelectedGroup(getApplication(), parsed.canonicalName)
        _uiState.update {
            it.copy(
                currentGroupName = parsed.canonicalName,
                groupInfo = parsed,
                hasSyncError = false,
                showGroupSelection = false,
            )
        }
        viewModelScope.launch {
            _toastEvent.emit("Выбрана группа: ${parsed.canonicalName}")

            if (!scheduleRepository.hasSchedule(parsed.canonicalName)) {
                syncSchedule(isAutoSync = false)
            }
        }
    }

    // BB14: targetDate — дата видимого слота (dd.MM.yyyy) при ручном обновлении из экрана расписания
    fun syncSchedule(
        isAutoSync: Boolean = false,
        targetDate: String? = null,
    ) {
        if (_uiState.value.isSyncing) return

        val targetGroup = _uiState.value.currentGroupName

        if (targetGroup.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, hasSyncError = false) }

            val result = scheduleRepository.syncScheduleDetailed(targetGroup, force = !isAutoSync, targetDate = targetDate)

            result.fold(
                onSuccess = { outcome ->
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = false) }

                    val showToast = outcome !is SyncOutcome.NotFound || !isAutoSync
                    if (showToast) _toastEvent.emit(toastFor(outcome, targetGroup))
                },
                onFailure = {
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = true) }
                    if (!isAutoSync) {
                        _toastEvent.emit("Сбой обновления: проверьте интернет (сайт guo-mpk.by недоступен)")
                    }
                },
            )
        }
    }
}
