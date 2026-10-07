package com.politex.ui.teacher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.politex.AppGraph
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.TeacherOverrideEntity
import com.politex.data.repository.NextLesson
import com.politex.data.repository.OverrideReset
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLoader
import com.politex.data.repository.defaultTeacherDayOffset
import com.politex.data.repository.teacherTargetCalendar
import com.politex.util.DateFormats
import com.politex.util.Dow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class TeacherHomeState(
    val dayOffset: Int = 0,
    val dateKey: String = "",
    val dow: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val lessons: List<TeacherDayLesson> = emptyList(),
    val ambiguous: Boolean = false,
    val error: String = "",
    val isSynced: Boolean = false,
    val lastSyncEpoch: Long = 0L,
    val nextLesson: NextLesson? = null,
    /** SPEC §5.4: правки, сброшенные полевым diff'ом. Живут в памяти до закрытия плашки. */
    val overrideResets: List<OverrideReset> = emptyList(),
)

class TeacherDayViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TeacherHomeState(dayOffset = defaultTeacherDayOffset(Dow.of(Calendar.getInstance()))))
    val state: StateFlow<TeacherHomeState> = _state

    private var profile: TeacherProfile? = null

    fun setProfile(p: TeacherProfile?) {
        if (p == profile) return
        profile = p
        if (p == null) {
            _state.update { it.copy(loading = false, lessons = emptyList(), error = "") }
        } else {
            load()
        }
    }

    fun selectDay(offset: Int) {
        if (offset == _state.value.dayOffset) return
        _state.update { it.copy(dayOffset = offset) }
        load()
    }

    // SPEC §4.1 фича 14: force-синк выбранного дня; успех → lastSyncEpoch (пишем сами,
    // если воркер ещё не успел), затем перечитываем день.
    // SPEC §5.4: синк — через diff-обёртку, правка сбрасывается только по изменившемуся полю.
    fun refresh() {
        val p = profile ?: return
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val cal = teacherTargetCalendar(_state.value.dayOffset, null)
            val dateKey = DateFormats.dotDate(cal)
            val ctx = getApplication<Application>()
            val (result, resets) =
                TeacherDayLoader.syncDayWithOverrideDiff(ctx, AppGraph.scheduleRepository, p, dateKey, cal, force = true)
            if (result.isSuccess) TeacherDayLoader.markSyncedNow(ctx)
            recordResets(resets)
            load()
        }
    }

    /** SPEC §4.1 фича 10: сохранение личной правки (unique index dateString+lessonNumber). */
    fun saveOverride(
        lessonNumber: Int,
        kind: String,
        room: String = "",
    ) {
        val dateKey = currentDateKey()
        if (dateKey.isBlank()) return
        viewModelScope.launch {
            // REPLACE в DAO: одна строка на (dateString, lessonNumber)
            MpkDatabase.getInstance(getApplication()).teacherOverrideDao().upsert(
                TeacherOverrideEntity(
                    dateString = dateKey,
                    lessonNumber = lessonNumber,
                    kind = kind,
                    room = room.trim(),
                ),
            )
            load()
        }
    }

    fun cancelOverride(lessonNumber: Int) {
        val dateKey = currentDateKey()
        if (dateKey.isBlank()) return
        viewModelScope.launch {
            MpkDatabase.getInstance(getApplication()).teacherOverrideDao().deleteForLesson(dateKey, lessonNumber)
            load()
        }
    }

    fun dismissOverrideResets() = _state.update { it.copy(overrideResets = emptyList()) }

    private fun currentDateKey(): String =
        _state.value.dateKey.ifBlank { DateFormats.dotDate(teacherTargetCalendar(_state.value.dayOffset, null)) }

    /**
     * Единственная точка фиксации сбросов: плашка в UI.
     * ponytail: T4 — здесь же post системного уведомления (NotificationHelper + канал);
     * сейчас сброс молчаливым не остаётся только благодаря плашке.
     */
    private fun recordResets(resets: List<OverrideReset>) {
        if (resets.isEmpty()) return
        _state.update { it.copy(overrideResets = it.overrideResets + resets) }
    }

    private fun load() {
        val p = profile ?: return
        viewModelScope.launch {
            val cal = teacherTargetCalendar(_state.value.dayOffset, null)
            val dateKey = DateFormats.dotDate(cal)
            val dow = Dow.of(cal)
            val ctx = getApplication<Application>()
            val repo = AppGraph.scheduleRepository
            val day = TeacherDayLoader.loadDay(ctx, repo, p, dateKey)
            val next = if (day.lessons.isEmpty()) TeacherDayLoader.findNextLesson(ctx, repo, p, dateKey) else null
            recordResets(day.resets)
            _state.update {
                it.copy(
                    dateKey = dateKey,
                    dow = dow,
                    loading = false,
                    refreshing = false,
                    lessons = day.lessons,
                    ambiguous = day.ambiguous,
                    error = day.error,
                    isSynced = day.isSynced,
                    lastSyncEpoch = day.lastSyncEpoch,
                    nextLesson = next,
                )
            }
        }
    }
}
