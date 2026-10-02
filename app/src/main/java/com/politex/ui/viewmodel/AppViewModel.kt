package com.politex.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.GroupInfo
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.TaskRepository
import com.politex.util.GroupCatalog
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

enum class AppTab(val title: String) {
    SCHEDULE("Расписание"),
    // Ф4: вкладка переименована по заказчику: внутри — Материалы/Задания/Оценки
    TASKS("Уроки"),
    BELLS("Звонки"),
    COLLEGE("Другое")
}

data class AppUiState(
    val currentGroupName: String,
    val groupInfo: GroupInfo?,
    val currentTab: AppTab = AppTab.SCHEDULE,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false,

    val showGroupSelection: Boolean = false,

    val pendingTeacherName: String? = null,

    val pendingRoom: String? = null,

    val pendingFromRoom: String? = null,

    /**
     * R1/N8: одноразовый флаг «показать маршрут» (виджет NowNext / явный запрос).
     * Живёт отдельно от pendingRoom: CollegeScreen съедает pendingRoom ДО композиции
     * карты, поэтому флаг потребляет сам CollegeMapScreen (consumeShowRouteOnMap).
     */
    val showRouteOnMap: Boolean = false,

    val returnTab: AppTab? = null,

    /** Одноразовый запрос автопрокрутки из виджета (см. requestScrollTarget). */
    val scrollTarget: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MpkDatabase.getInstance(application)
    // E1: единый экземпляр из AppGraph (полный набор deps: каталог + teacherLessonDao),
    // вместо локального создания с риском рассинхрона зависимостей
    val scheduleRepository = com.politex.AppGraph.scheduleRepository
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
    val groupLessons: StateFlow<List<LessonEntity>> = currentGroup
        .flatMapLatest { name -> scheduleRepository.getAllLessonsForGroup(name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** C3: то же для домашки — Flow тоже переживает пересоздание поддерева вкладки. */
    val pendingTasks: StateFlow<List<StudentTaskEntity>> = currentGroup
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

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab, returnTab = null) }
    }

    private fun enterCollegeFrom(returnTab: AppTab): AppTab? =
        if (returnTab == AppTab.COLLEGE) null else returnTab

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
            // Б3: после синка всех групп день обычно уже в Room — принудительный поход в
            // сеть при каждой смене группы больше не нужен (заказчик жаловался на
            // повторные скачивания при недоступном сайте).
            if (!scheduleRepository.hasSchedule(parsed.canonicalName)) {
                syncSchedule(isAutoSync = false)
            }
        }
    }

    fun openTeacher(teacherName: String) {
        _uiState.update {
            it.copy(
                currentTab = AppTab.COLLEGE,
                pendingTeacherName = teacherName,
                returnTab = enterCollegeFrom(it.currentTab) ?: it.returnTab
            )
        }
    }

    fun consumePendingTeacher() {
        _uiState.update { it.copy(pendingTeacherName = null) }
    }

    /**
     * @param showRoute N8/R1: показать маршрут на карте (виджет NowNext). Одноразовый флаг —
     * потребляет CollegeMapScreen (consumeShowRouteOnMap), т.к. CollegeScreen съедает
     * pendingRoom до композиции карты.
     */
    fun openMap(room: String, fromRoom: String? = null, showRoute: Boolean = false) {
        _uiState.update {
            it.copy(
                currentTab = AppTab.COLLEGE,
                pendingRoom = room,
                pendingFromRoom = fromRoom,
                showRouteOnMap = showRoute,
                returnTab = enterCollegeFrom(it.currentTab) ?: it.returnTab
            )
        }
    }

    fun consumePendingRoom() {
        _uiState.update { it.copy(pendingRoom = null, pendingFromRoom = null) }
    }

    /** Одноразовое чтение флага маршрута: true — и сразу сброс (как consumePendingRoom). */
    fun consumeShowRouteOnMap(): Boolean {
        val was = _uiState.value.showRouteOnMap
        if (was) _uiState.update { it.copy(showRouteOnMap = false) }
        return was
    }

    fun restoreReturnTab() {
        _uiState.update {
            val target = it.returnTab
            if (target != null) it.copy(currentTab = target, returnTab = null) else it
        }
    }

    /**
     * Цель автопрокрутки из виджета («CURRENT_LESSON»/«CURRENT_BELL»).
     * Одноразовый запрос: экран потребляет его и сбрасывает, повторный вход
     * на вкладку из меню уже не скроллит.
     */
    fun requestScrollTarget(target: String) {
        _uiState.update { it.copy(scrollTarget = target) }
    }

    fun consumeScrollTarget() {
        if (_uiState.value.scrollTarget != null) {
            _uiState.update { it.copy(scrollTarget = null) }
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
                }
            )
        }
    }
}
