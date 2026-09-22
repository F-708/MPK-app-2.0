package com.politex.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.politex.data.local.MpkDatabase
import com.politex.data.model.CollegeBellSchedule
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

object ConfettiController {

    private val _events = MutableStateFlow(0L)
    val events: StateFlow<Long> = _events

    private val handler = Handler(Looper.getMainLooper())
    private var debugRunnable: Runnable? = null

    fun triggerAppConfetti() {
        _events.value = System.currentTimeMillis()
    }

    fun celebrate(context: Context) {
        markCelebrated(context)
        triggerAppConfetti()
        com.politex.widget.WidgetConfetti.play(context)
    }

    fun maybeCelebrate(context: Context) {
        if (isCelebratedToday(context)) return
        val window = lessonsEndedWindow(context) ?: return
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (nowMin in window.first..window.second) {
            celebrate(context)
        }
    }

    fun isCelebratedToday(context: Context): Boolean {
        val prefs = WidgetUpdateHelper.getPrefs(context)
        return prefs.getString(KEY_CELEBRATED_DATE, null) == todayIso()
    }

    private fun markCelebrated(context: Context) {
        WidgetUpdateHelper.getPrefs(context).edit()
            .putString(KEY_CELEBRATED_DATE, todayIso())
            .apply()
    }

    fun scheduleDebug(context: Context, delaySeconds: Int) {
        cancelDebug()
        val r = Runnable { celebrate(context) }
        debugRunnable = r
        handler.postDelayed(r, delaySeconds * 1000L)
    }

    fun cancelDebug() {
        debugRunnable?.let { handler.removeCallbacks(it) }
        debugRunnable = null
    }

    fun lessonsEndedWindow(context: Context): Pair<Int, Int>? {
        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> return null
        }
        val bells = CollegeBellSchedule.getBellsForDay(dow)
        if (bells.isEmpty()) return null

        var lastEnd: Int? = bells.lastOrNull { !it.isInfoHour }?.endMinutes
            ?: bells.maxOf { it.endMinutes }

        val group = WidgetUpdateHelper.getSelectedGroup(context)
        if (!group.isBlank()) {
            try {
                val lessons = kotlinx.coroutines.runBlocking {
                    MpkDatabase.getInstance(context).lessonDao().getLessonsForDaySync(group, dow)
                }
                val latestDated = lessons.filter { it.dateString.isNotBlank() }
                    .maxOfOrNull { it.dateString.replace("-", ".") }
                val day = lessons.filter {
                    it.dateString.isBlank() || it.dateString.replace("-", ".") == latestDated
                }
                val maxLesson = day.maxOfOrNull { it.lessonNumber }
                if (maxLesson != null && maxLesson > 0) {
                    val bell = bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= maxLesson }
                    if (bell != null) lastEnd = bell.endMinutes
                }
            } catch (_: Exception) {
            }
        }

        val end = lastEnd ?: return null
        return end to (end + 3)
    }

    private const val KEY_CELEBRATED_DATE = "confetti_celebrated_date"

    private fun todayIso(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
