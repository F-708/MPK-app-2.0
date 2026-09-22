package com.politex.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.BellItem
import com.politex.data.model.CollegeBellSchedule
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Чистая (без Android-зависимостей в логике) логика срабатывания конфетти.
 * Вынесена сюда ради юнит-тестов; всё, что требует Context/БД, — в [ConfettiController].
 */
object ConfettiGate {

    /**
     * Конфетти срабатывает РОВНО в минуту окончания последнего урока (без окна допуска):
     * задержавшийся тик на минуту позже уже не стреляет — «уроки закончились раньше».
     */
    fun shouldFireNow(endMinute: Int, nowMinute: Int): Boolean = endMinute == nowMinute

    /**
     * Полное решение триггера «уроки закончились»: уже отмечали сегодня → нет,
     * конца дня нет (воскресенье/нет звонков) → нет, иначе — ровное совпадение минут.
     */
    fun shouldCelebrate(celebratedToday: Boolean, endMinute: Int?, nowMinute: Int): Boolean {
        if (celebratedToday) return false
        if (endMinute == null) return false
        return shouldFireNow(endMinute, nowMinute)
    }

    /**
     * Минута окончания последнего урока дня.
     * [maxLessonNumber] — последний номер урока выбранной группы (null/0 → данных нет,
     * фолбэк на последний звонок колледжа — это тоже «последний урок дня», см. требование).
     */
    fun lastLessonEndMinute(bells: List<BellItem>, maxLessonNumber: Int?): Int? {
        if (bells.isEmpty()) return null
        val collegeEnd = bells.lastOrNull { !it.isInfoHour }?.endMinutes
            ?: bells.maxOf { it.endMinutes }
        if (maxLessonNumber == null || maxLessonNumber <= 0) return collegeEnd
        return bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= maxLessonNumber }
            ?.endMinutes ?: collegeEnd
    }

    /** Дата «01-02.03» и «01.02.03» — один и тот же день (в БД встречаются оба разделителя). */
    fun normalizeDate(date: String): String = date.replace('-', '.')

    /**
     * Уроки «на сегодня» из строк одного дня недели: сначала строки с сегодняшней датой
     * (новый формат — день проштампован датой) плюс пустая дата (недельный шаблон);
     * если таких нет — старый формат «неделя одной датой», берём самую позднюю штамп-дату.
     * Пустой результат → вызывающий фолбэкится на последний звонок колледжа.
     */
    fun selectTodayLessons(lessons: List<LessonEntity>, todayDate: String): List<LessonEntity> {
        val exact = lessons.filter {
            it.dateString.isBlank() || normalizeDate(it.dateString) == todayDate
        }
        if (exact.isNotEmpty()) return exact
        val latest = lessons.filter { it.dateString.isNotBlank() }
            .maxOfOrNull { normalizeDate(it.dateString) } ?: return emptyList()
        return lessons.filter {
            it.dateString.isBlank() || normalizeDate(it.dateString) == latest
        }
    }

    /** «Завтра ≤3 уроков» — повод для конфетти при первом просмотре завтрашнего дня. */
    fun isTomorrowConfettiWorthy(lessonCount: Int): Boolean = lessonCount in 1..3

    fun tomorrowPendingKey(dateString: String): String = "confetti_tomorrow_pending_$dateString"

    /**
     * Семантика флага «завтра мало уроков»: ставится только при ≤3 уроках,
     * читается и снимается РОВНО один раз (см. consumePending).
     * Дата — в формате dd.MM.yyyy, как в dateString уроков.
     */
    fun setPending(store: MutableMap<String, Boolean>, dateString: String, lessonCount: Int) {
        if (isTomorrowConfettiWorthy(lessonCount)) {
            store[tomorrowPendingKey(dateString)] = true
        }
    }

    fun consumePending(store: MutableMap<String, Boolean>, dateString: String): Boolean {
        val key = tomorrowPendingKey(dateString)
        val wasPending = store[key] == true
        store.remove(key)
        return wasPending
    }
}

object ConfettiController {

    /**
     * Оверлей в активити показывает только события, случившиеся на его глазах:
     * старше этого порога (например, тик виджета при закрытом приложении)
     * событие считается фоновым и не проигрывается при следующем открытии.
     */
    const val MAX_EVENT_AGE_MS: Long = 5_000L

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

    /**
     * Минутная проверка «последний урок дня закончился». Вызывается ТОЛЬКО из
     * минутных тиков (WidgetAlarmReceiver.ACTION_TICK и цикл BellTimerService) —
     * НЕ из активити/резюма/поллинга. Срабатывает ровно в минуту конца последнего
     * урока и не более одного раза в день: флаг confetti_celebrated_date общий
     * для приложения и виджетов.
     */
    fun maybeCelebrate(context: Context) {
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (ConfettiGate.shouldCelebrate(
                isCelebratedToday(context), lastLessonEndMinute(context), nowMin
            )
        ) {
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

    /**
     * Минута конца последнего урока СЕГОДНЯ. Воскресенье → null (уроков нет).
     * Если группа не выбрана или в БД нет строк на сегодня — последний звонок
     * колледжа: это всё ещё «последний урок дня», фолбэк допустим по требованиям.
     */
    fun lastLessonEndMinute(context: Context): Int? {
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

        val group = WidgetUpdateHelper.getSelectedGroup(context)
        val maxLesson = if (group.isBlank()) null else {
            groupMaxLessonNumber(context, group, dow, todayDdMmYyyy())
        }
        return ConfettiGate.lastLessonEndMinute(bells, maxLesson)
    }

    /**
     * Последний номер урока группы на сегодня: сначала строки по дате
     * (getLessonsForDateOrDay — точная дата либо пустая), при пустом результате —
     * строки дня недели целиком (совместимость со старым форматом данных).
     */
    private fun groupMaxLessonNumber(
        context: Context,
        group: String,
        dow: Int,
        today: String
    ): Int? {
        return try {
            kotlinx.coroutines.runBlocking {
                val dao = MpkDatabase.getInstance(context).lessonDao()
                val byDate = dao.getLessonsForDateOrDay(group, today, dow).first()
                val lessons = byDate.ifEmpty { dao.getLessonsForDaySync(group, dow) }
                ConfettiGate.selectTodayLessons(lessons, today).maxOfOrNull { it.lessonNumber }
            }
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Триггер (c): «завтра ≤3 уроков» при первом ПРОСМОТРЕ завтрашнего дня.
    //
    // Флаг ставит ScheduleCheckWorker сразу после сохранения нового расписания
    // (setTomorrowPending). Хук в ScheduleScreen (добавляется отдельной задачей)
    // при первом показе дня «завтра» вызывает:
    //     ConfettiController.consumeTomorrowPending(context, "dd.MM.yyyy")
    // и, если вернулось true, запускает конфетти (celebrate БЕЗ повтора — ключ
    // одноразовый: чтение снимает флаг, повторный просмотр/календарь не стреляют).
    // dateString — в формате dd.MM.yyyy, как в targetDateString воркера.
    // ------------------------------------------------------------------

    fun setTomorrowPending(context: Context, dateString: String, lessonCount: Int) {
        if (!ConfettiGate.isTomorrowConfettiWorthy(lessonCount)) return
        val prefs = WidgetUpdateHelper.getPrefs(context)
        // Один флаг за раз: старые неиспользованные подчищаем, префы не растут.
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_TOMORROW_PENDING_PREFIX) }
            .forEach { editor.remove(it) }
        editor.putBoolean(ConfettiGate.tomorrowPendingKey(dateString), true).apply()
    }

    /** Одноразовое чтение флага «завтра мало уроков» для даты dd.MM.yyyy. */
    fun consumeTomorrowPending(context: Context, dateString: String): Boolean {
        val prefs = WidgetUpdateHelper.getPrefs(context)
        val key = ConfettiGate.tomorrowPendingKey(dateString)
        val wasPending = prefs.getBoolean(key, false)
        if (prefs.contains(key)) prefs.edit().remove(key).apply()
        return wasPending
    }

    private const val KEY_CELEBRATED_DATE = "confetti_celebrated_date"
    private const val KEY_TOMORROW_PENDING_PREFIX = "confetti_tomorrow_pending_"

    private fun todayIso(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun todayDdMmYyyy(): String {
        val cal = Calendar.getInstance()
        return "%02d.%02d.%04d".format(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }
}
