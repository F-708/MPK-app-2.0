package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MpkDatabase
import com.example.data.model.GroupInfo
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.TaskRepository
import com.example.util.GroupParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    SCHEDULE("Расписание"),
    TASKS("Задания"),
    BELLS("Звонки"),
    COLLEGE("Колледж"),
    SETTINGS("Настройки")
}

data class AppUiState(
    val currentGroupName: String = "41О",
    val groupInfo: GroupInfo? = GroupParser.parse("41О"),
    val currentTab: AppTab = AppTab.SCHEDULE,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false
)

/**
 * Главная ViewModel приложения «МПК Расписание».
 *
 * ЖЕЛЕЗНЫЕ ПРАВИЛА:
 * 1. Toast и ошибки передаются СТРОГО через SharedFlow в UI (LaunchedEffect на Dispatchers.Main).
 * 2. Используется AndroidViewModel(application) с чистыми репозиториями.
 * 3. Политика синхронизации: фоновый опрос при старте (2 повтора, таймаут 3.5с), янтарный индикатор при сбое.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MpkDatabase.getInstance(application)
    val scheduleRepository = ScheduleRepository(database.lessonDao())
    val taskRepository = TaskRepository(database.taskDao())

    val diagnosticInfo = scheduleRepository.diagnosticInfo

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        // Фоновая автосинхронизация при запуске приложения
        syncSchedule(isAutoSync = true)
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setGroup(newGroupName: String) {
        val parsed = GroupParser.parse(newGroupName)
        if (parsed != null) {
            _uiState.update {
                it.copy(
                    currentGroupName = parsed.canonicalName,
                    groupInfo = parsed,
                    hasSyncError = false
                )
            }
            viewModelScope.launch {
                _toastEvent.emit("Выбрана группа: ${parsed.canonicalName}")
                syncSchedule(isAutoSync = false)
            }
        }
    }


    fun syncSchedule(isAutoSync: Boolean = false) {
        if (_uiState.value.isSyncing) return

        val targetGroup = _uiState.value.currentGroupName

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, hasSyncError = false) }

            val result = scheduleRepository.syncScheduleFromWeb(targetGroup)

            result.fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = false) }
                    if (count > 0) {
                        _toastEvent.emit("Расписание группы $targetGroup обновлено ($count уроков)")
                    } else {
                        if (!isAutoSync) {
                            _toastEvent.emit("Расписание группы $targetGroup: новых данных на сайте пока нет")
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = true) }
                    if (!isAutoSync) {
                        _toastEvent.emit("Сбой обновления: проверьте интернет (сайт guo-mpk.by недоступен)")
                    }
                }
            )
        }
    }
}
