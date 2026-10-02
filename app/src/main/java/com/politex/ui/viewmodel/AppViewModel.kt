package com.politex.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.GroupInfo
import com.politex.data.repository.TaskRepository
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

/**
 * E4/N15: карта route'ов NavHost (ARC-01). Базовые имена — navigate() ходит по ним;
 * паттерны с опциональными query-аргументами строятся в MainScreen (scrollRoutePattern,
 * buildMapRoute и т.д.). Константы живут здесь, чтобы AppTab.route и MainScreen/MainActivity
 * ссылались на один источник.
 */
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

/** Ключ одноразового запроса автопрокрутки в SavedStateHandle записи навигации. */
const val ARG_SCROLL = "scroll"

enum class AppTab(
    val title: String,
    val route: String,
) {
    SCHEDULE("Расписание", ROUTE_SCHEDULE),

    // Ф4: вкладка переименована по заказчику: внутри — Материалы/Задания/Оценки
    TASKS("Уроки", ROUTE_LESSONS),
    BELLS("Звонки", ROUTE_BELLS),
    COLLEGE("Другое", ROUTE_COLLEGE),
}

/**
 * E4: из состояния УБРАНЫ currentTab/returnTab/pendingRoom/pendingFromRoom/
 * pendingTeacherName/showRouteOnMap/scrollTarget — их заменили backstack и
 * аргументы Navigation-Compose (ARC-01/ARC-06). VM осталась только про данные.
 */
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

    // E1: единый экземпляр из AppGraph (полный набор deps: каталог + teacherLessonDao),
    // вместо локального создания с риском рассинхрона зависимостей
    val scheduleRepository = com.politex.AppGraph.scheduleRepository
    val taskRepository = TaskRepository(database.taskDao())

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

    /**
     * C3: расписание группы живёт в VM, а не в композиции ScheduleScreen.
     * Раньше collectAsState подписывался внутри экрана: при смене вкладки (when в
     * MainScreen уничтожает поддерево) подписка умирала, и на возврате первый эмит
     * Room приходил только после нового запроса к БД → спиннер → «ещё не опубликовано».
     * stateIn держит последнее значение в любом случае, поэтому возврат на вкладку
     * получает кэш синхронно, без обращения к БД.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val groupLessons: StateFlow<List<LessonEntity>> =
        currentGroup
            .flatMapLatest { name -> scheduleRepository.getAllLessonsForGroup(name) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** C3: то же для домашки — Flow тоже переживает пересоздание поддерева вкладки. */
    val pendingTasks: StateFlow<List<StudentTaskEntity>> =
        currentGroup
            .flatMapLatest { name -> taskRepository.getPendingTasksForGroup(name) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {

        if (!uiState.value.showGroupSelection) {
            // O1/C: при наличии кэша в БД не ходим в сеть на старте — иначе холодный
            // старт ждал сеть и расписание появлялось только через ~1 секунду.
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
            // Б3: после синка всех групп день обычно уже в Room — принудительный поход в
            // сеть при каждой смене группы больше не нужен (заказчик жаловался на
            // повторные скачивания при недоступном сайте).
            if (!scheduleRepository.hasSchedule(parsed.canonicalName)) {
                syncSchedule(isAutoSync = false)
            }
        }
    }

    fun syncSchedule(isAutoSync: Boolean = false) {
        if (_uiState.value.isSyncing) return

        val targetGroup = _uiState.value.currentGroupName

        if (targetGroup.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, hasSyncError = false) }

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
                },
            )
        }
    }
}
