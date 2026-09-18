package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MpkDatabase
import com.example.data.model.GroupInfo
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.TaskRepository
import com.example.util.GroupParser
import com.example.widget.WidgetUpdateHelper
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
    COLLEGE("Другое")
}

data class AppUiState(
    val currentGroupName: String,
    val groupInfo: GroupInfo?,
    val currentTab: AppTab = AppTab.SCHEDULE,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false,
    /** Показывать обязательный диалог выбора группы (первый вход). */
    val showGroupSelection: Boolean = false,
    /** ФИО преподавателя, которого нужно открыть в «Другом» (переход из расписания). */
    val pendingTeacherName: String? = null,
    /**
     * Экран «Код приложения» занимает весь экран — рисуется вместо основного
     * интерфейса, чтобы не было ни баннера, ни нижних вкладок.
     */
    val showAppCode: Boolean = false,
    /** Кабинет, который нужно показать на карте (переход из расписания). */
    val pendingRoom: String? = null,
    /**
     * Кабинет текущего урока — от него строится маршрут. Пусто, если карту
     * открыли из меню: тогда маршрута нет, кабинет просто подсвечивается.
     */
    val pendingFromRoom: String? = null
)

/**
 * Главная ViewModel приложения «Мой Политех».
 *
 * Группа хранится в SharedPreferences: при первом входе приложение
 * обязательно предлагает выбрать группу, смена — из настроек.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MpkDatabase.getInstance(application)
    val scheduleRepository = ScheduleRepository(database.lessonDao())
    val taskRepository = TaskRepository(database.taskDao())

    val diagnosticInfo = scheduleRepository.diagnosticInfo

    private val _uiState = MutableStateFlow(
        AppUiState(
            currentGroupName = WidgetUpdateHelper.getSelectedGroup(application),
            groupInfo = GroupParser.parse(WidgetUpdateHelper.getSelectedGroup(application)),
            showGroupSelection = !WidgetUpdateHelper.hasSelectedGroup(application)
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        // Фоновая автосинхронизация при запуске приложения
        if (!uiState.value.showGroupSelection) {
            syncSchedule(isAutoSync = true)
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setGroup(newGroupName: String) {
        val parsed = GroupParser.parse(newGroupName) ?: return
        WidgetUpdateHelper.setSelectedGroup(getApplication(), parsed.canonicalName)
        _uiState.update {
            it.copy(
                currentGroupName = parsed.canonicalName,
                groupInfo = parsed,
                hasSyncError = false,
                showGroupSelection = false
            )
        }
        viewModelScope.launch {
            _toastEvent.emit("Выбрана группа: ${parsed.canonicalName}")
            syncSchedule(isAutoSync = false)
        }
    }

    /**
     * Открыть карточку преподавателя из любого места приложения:
     * переключаемся на «Другое» и передаём ФИО для открытия.
     */
    fun openTeacher(teacherName: String) {
        _uiState.update {
            it.copy(currentTab = AppTab.COLLEGE, pendingTeacherName = teacherName)
        }
    }

    /** Сброс после того, как карточка преподавателя открыта. */
    fun consumePendingTeacher() {
        _uiState.update { it.copy(pendingTeacherName = null) }
    }

    /**
     * Открыть карту на нужном кабинете (нажатие на кабинет в расписании).
     * [fromRoom] — кабинет текущего урока: от него проложат маршрут.
     */
    fun openMap(room: String, fromRoom: String? = null) {
        _uiState.update {
            it.copy(currentTab = AppTab.COLLEGE, pendingRoom = room, pendingFromRoom = fromRoom)
        }
    }

    /** Сброс после того, как карта открыта. */
    fun consumePendingRoom() {
        _uiState.update { it.copy(pendingRoom = null, pendingFromRoom = null) }
    }

    /** Открыть полноэкранный «Код приложения» (админская версия). */
    fun openAppCode() {
        _uiState.update { it.copy(showAppCode = true) }
    }

    fun closeAppCode() {
        _uiState.update { it.copy(showAppCode = false) }
    }

    fun syncSchedule(isAutoSync: Boolean = false) {
        if (_uiState.value.isSyncing) return

        val targetGroup = _uiState.value.currentGroupName
        // Группа ещё не выбрана — синхронизировать нечего, не дёргаем сайт колледжа
        if (targetGroup.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, hasSyncError = false) }

            // Ручное обновление (нажатие кнопки) перекачивает оба дня,
            // фоновая автосинхронизация — только то, чего ещё нет
            val result = scheduleRepository.syncScheduleFromWeb(targetGroup, force = !isAutoSync)

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
                onFailure = {
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = true) }
                    if (!isAutoSync) {
                        _toastEvent.emit("Сбой обновления: проверьте интернет (сайт guo-mpk.by недоступен)")
                    }
                }
            )
        }
    }
}
