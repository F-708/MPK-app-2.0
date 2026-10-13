package com.politex.ui.teacher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.politex.AppGraph
import com.politex.data.repository.NextLesson
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLoader
import com.politex.data.repository.defaultTeacherDayOffset
import com.politex.data.repository.teacherTargetCalendar
import com.politex.util.DateFormats
import com.politex.util.Dow
import kotlinx.coroutines.CancellationException
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
    fun refresh() {
        if (profile == null) return
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val cal = teacherTargetCalendar(_state.value.dayOffset, null)
            val ctx = getApplication<Application>()
            val result =
                runCatching { AppGraph.scheduleRepository.syncTeacherScheduleForDate(cal, force = true) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrElse { Result.failure(it) }
            if (result.isSuccess) TeacherDayLoader.markSyncedNow(ctx)
            load()
        }
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
