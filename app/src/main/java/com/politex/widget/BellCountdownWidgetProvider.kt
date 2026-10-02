package com.politex.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.model.BellItem
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.Dow
import java.util.Calendar

class BellCountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        WidgetAlarm.scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
        WidgetAlarm.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        WidgetAlarm.cancel(context)
    }

    companion object {

        /** Первый показ строки/виджета — 07:00 (заказчик, Q5). */
        const val SHOW_FROM_MINUTE = 7 * 60

        fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val views = buildViews(context, id)
                manager.updateAppWidget(id, views)
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellCountdownWidgetProvider::class.java))
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        fun countdownInfo(
            context: Context,
            mode: CountdownMode = CountdownMode.GROUP_LESSONS,
        ): Triple<Long, String, Int>? {
            val cal = java.util.Calendar.getInstance()
            val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> return null
            }
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val bells = CollegeBellSchedule.getBellsForDay(dow)

            // Режим «мои уроки»: cutoff = последний урок группы (уроки закончились → строка скрыта).
            // Данные считаем здесь и в цикле не матчим звонки после последнего урока группы.
            val lastGroupLesson = if (mode == CountdownMode.GROUP_LESSONS) {
                lastGroupLessonForToday(context, cal)
            } else {
                null
            }

            return countdownForMinute(minutes, dow, bells, mode, lastGroupLesson)
        }

        /**
         * W3: «До 1 урока завтра: N ч» — вечернее окно (после конца уроков сегодня,
         * до полуночи) вместо пустой строки. Чистая функция, те же входные, что
         * countdownForMinute; null → показать пусто (как раньше).
         *
         * Границы: сегодня Пн-Сб (в Вс строка скрыта весь день), минуты после
         * конца последнего звонка дня. Завтрашний первый звонок берётся из
         * звонков следующего дня недели (Вс → Пн).
         */
        fun tomorrowFirstLessonLabel(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes < SHOW_FROM_MINUTE) return null

            // Тот же критерий «уроки закончились», что в countdownForMinute:
            // GROUP_LESSONS — конец последнего урока группы, ALL_BELLS — конец дня.
            val lastEnd = if (mode == CountdownMode.GROUP_LESSONS) {
                lastGroupLesson ?: return null
                bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= lastGroupLesson }?.endMinutes
            } else {
                bells.last().endMinutes
            } ?: return null
            if (minutes <= lastEnd) return null

            // Завтра: следующий день недели. Вс (7) пропускаем: завтра понедельник,
            // а «до 1 урока завтра» в субботу вечером было бы ложью — урок не завтра,
            // а через день. Суббота вечером → null (пусто, как до Т10).
            val tomorrowDow = if (dayOfWeek == 6) 7 else dayOfWeek + 1
            if (tomorrowDow == 7) return null
            val tomorrowBells = CollegeBellSchedule.getBellsForDay(tomorrowDow)
            val first = tomorrowBells.firstOrNull { !it.isInfoHour } ?: return null

            val minutesToStart = (24 * 60 - minutes) + first.startMinutes
            return Triple(minutesToStart.toLong(), "до 1 урока завтра", 0)
        }

        /**
         * Ночное окно (00:00 – 07:00): отсчёт до первого урока СЕГОДНЯ.
         *
         * Заказчик: «0:24, виджет когда звонок пустой без текста». До этого окна виджет
         * был пуст всю ночь, а он всегда виден на экране — пустая плашка читается как поломка.
         * Ночью «завтра» уже наступило, поэтому показываем ближайший урок сегодняшнего дня.
         *
         * null: воскресенье, звонков нет, у группы сегодня нет уроков, либо первый урок уже идёт.
         */
        fun nightFirstLessonLabel(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes >= SHOW_FROM_MINUTE) return null
            // В режиме «мои уроки» день без уроков группы — показывать нечего
            if (mode == CountdownMode.GROUP_LESSONS && lastGroupLesson == null) return null

            val first = bells.firstOrNull { !it.isInfoHour } ?: return null
            if (minutes >= first.startMinutes) return null
            return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
        }

        /**
         * Чистая логика отсчёта (Т10): null = строка скрыта
         * (выходной / до 07:00 / уроки закончились / у группы нет уроков в этот день).
         *
         * Cutoff группы проверяется ДО цикла по звонкам (Т10.1) — сам цикл идёт по полному
         * списку, чтобы не ломать смежность «перемена → инфочас → следующий урок» (четверг).
         */
        fun countdownForMinute(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes < SHOW_FROM_MINUTE) return null

            if (mode == CountdownMode.GROUP_LESSONS) {
                // Нет уроков группы в этот день — скрыто весь день (без fallback на все звонки).
                if (lastGroupLesson == null) return null
                val lastBell = bells.lastOrNull {
                    !it.isInfoHour && it.lessonNumber <= lastGroupLesson
                } ?: return null
                if (minutes > lastBell.endMinutes) return null
            }

            bells.forEachIndexed { index, bell ->
                if (minutes in bell.startMinutes..bell.endMinutes) {
                    val left = bell.endMinutes - minutes
                    return Triple(left.toLong(), "до звонка", if (bell.isInfoHour) 0 else bell.lessonNumber)
                }

                val breakEnd = bell.endMinutes + bell.breakAfterMinutes
                if (bell.breakAfterMinutes > 0 && minutes in bell.endMinutes..breakEnd) {
                    val next = bells.getOrNull(index + 1) ?: return null
                    val left = next.startMinutes - minutes
                    return Triple(
                        left.toLong(),
                        if (next.isInfoHour) "до инфочаса" else "до ${next.lessonNumber} урока",
                        0,
                    )
                }
            }

            val first = bells.firstOrNull() ?: return null
            if (minutes < first.startMinutes) {
                return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
            }
            return null
        }

        /**
         * Последний урок группы сегодня: сначала строки с датой (dateString dd.MM.yyyy / dd-MM-yyyy),
         * при отсутствии — регулярные по dayOfWeek (graceful degradation). Пусто → null (уроков нет).
         */

        // O1: тот же runBlocking в Room, что и в WidgetData.todayLessons; результат
        // не зависит от appWidgetId и меняется раз в минуту → кэш по (дата+время+группа).
        // Поля @Volatile: updateAll зовётся и из будильника, и из BellTimerService.
        @Volatile private var cacheKey: String? = null

        @Volatile private var cacheLastLesson: Int? = null

        fun lastGroupLessonForToday(context: Context, cal: Calendar): Int? {
            val group = WidgetUpdateHelper.getSelectedGroup(context)
            if (group.isBlank()) return null
            val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> 7
            }
            val todayString = String.format(
                "%02d.%02d.%d",
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR),
            )
            // BEL-04/WID-04: «последний урок группы сегодня» не меняется в течение дня —
            // ключ БЕЗ минут. Раньше ключ включал HH:mm и кэш промахивался каждую минуту:
            // runBlocking+Room 1440 раз в день при живом сервисе вместо одного запроса.
            val key = "$group|$dow|$todayString"
            if (key == cacheKey) return cacheLastLesson
            val result = try {
                kotlinx.coroutines.runBlocking {
                    val lessons = com.politex.data.local.MpkDatabase.getInstance(context)
                        .lessonDao().getLessonsForDaySync(group, dow)
                    val forToday = lessons.filter {
                        it.dateString.isNotBlank() && it.dateString.replace("-", ".") == todayString
                    }
                    // Дневные строки на сегодняшнюю дату имеют приоритет; иначе — регулярные по дню недели.
                    if (forToday.isNotEmpty()) {
                        forToday.maxOf { it.lessonNumber }
                    } else {
                        lessons.filter { it.dateString.isBlank() }.maxOfOrNull { it.lessonNumber }
                    }
                }
            } catch (_: Exception) {
                null
            }
            // Ключ пишем последним (публикация data → key), см. WidgetData.todayLessons
            cacheLastLesson = result
            cacheKey = key
            return result
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bell_countdown)

            val style = WidgetStyle.load(context, appWidgetId)
            views.setInt(R.id.widget_countdown_root, "setBackgroundColor", style.backgroundColor)
            views.setTextColor(R.id.tv_countdown_label, style.subColor)
            views.setTextColor(R.id.tv_countdown_value, style.mainColor)

            val mode = CountdownMode.load(context, appWidgetId)
            val cal = java.util.Calendar.getInstance()
            val dow = Dow.of(cal)
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val bells = CollegeBellSchedule.getBellsForDay(dow)
            // Один read БД на обновление (countdownInfo читал бы его второй раз)
            val lastGroupLesson = if (mode == CountdownMode.GROUP_LESSONS && dow in 1..6) {
                lastGroupLessonForToday(context, cal)
            } else {
                null
            }

            // Тот же расчёт, что countdownInfo, но без повторного обращения к БД
            val info = if (dow == 7) null else countdownForMinute(minutes, dow, bells, mode, lastGroupLesson)
            when {
                info != null && info.first >= 0 -> {
                    val (minutesLeft, label, _) = info
                    views.setTextViewText(R.id.tv_countdown_label, label)
                    views.setTextViewText(R.id.tv_countdown_value, formatMinutes(minutesLeft))
                }
                // W3 (Т10+): вечер после конца уроков — «до 1 урока завтра» вместо пустоты
                dow in 1..6 -> {
                    val tomorrow = tomorrowFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
                    // O6/C: ночь (00:00–07:00) — виджет виден на экране, пустая плашка
                    // читается как поломка. Показываем отсчёт до первого урока сегодня.
                    val night = if (tomorrow == null) {
                        nightFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
                    } else {
                        null
                    }
                    when {
                        tomorrow != null -> {
                            views.setTextViewText(R.id.tv_countdown_label, tomorrow.second)
                            views.setTextViewText(R.id.tv_countdown_value, formatHoursRounded(tomorrow.first))
                        }
                        night != null -> {
                            views.setTextViewText(R.id.tv_countdown_label, night.second)
                            views.setTextViewText(R.id.tv_countdown_value, formatMinutes(night.first))
                        }
                        else -> {
                            views.setTextViewText(R.id.tv_countdown_label, "")
                            views.setTextViewText(R.id.tv_countdown_value, "")
                        }
                    }
                }
                else -> {
                    // Т10: строка скрыта до 07:00 / после конца уроков / в день без уроков.
                    views.setTextViewText(R.id.tv_countdown_label, "")
                    views.setTextViewText(R.id.tv_countdown_value, "")
                }
            }

            val openIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, com.politex.MainActivity::class.java)
                    // CLEAR_TOP|SINGLE_TOP: тёплый тап по виджету доходит до onNewIntent,
                    // а не плодит копии активити
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("EXTRA_OPEN_TAB", "BELLS")
                    .putExtra("EXTRA_SCROLL_TARGET", "CURRENT_BELL"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_root, openIntent)
            return views
        }

        fun formatMinutes(totalMinutes: Long): String {
            if (totalMinutes <= 0) return "меньше минуты"
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return when {
                h == 0L -> "$m мин"
                m == 0L -> "$h ч"
                else -> "$h ч $m мин"
            }
        }

        /** W3: часы, округлённые до ближайшего («до 1 урока завтра: 14 ч»). */
        fun formatHoursRounded(totalMinutes: Long): String {
            val hours = Math.round(totalMinutes / 60.0).toLong().coerceAtLeast(1L)
            return "$hours ч"
        }
    }
}

enum class CountdownMode(val title: String, val description: String) {
    GROUP_LESSONS(
        "До конца моих уроков",
        "Отсчёт заканчивается на последнем уроке вашей группы",
    ),
    ALL_BELLS(
        "До конца всех звонков",
        "Отсчёт идёт до последнего звонка в колледже",
    ),
    ;

    companion object {
        private const val PREF_PREFIX = "countdown_mode_"

        fun load(context: Context, appWidgetId: Int): CountdownMode {
            val name = WidgetUpdateHelper.getPrefs(context).getString(PREF_PREFIX + appWidgetId, null)
            return entries.firstOrNull { it.name == name } ?: GROUP_LESSONS
        }

        fun save(context: Context, appWidgetId: Int, mode: CountdownMode) {
            WidgetUpdateHelper.getPrefs(context)
                .edit()
                .putString(PREF_PREFIX + appWidgetId, mode.name)
                .apply()
        }

        fun remove(context: Context, appWidgetId: Int) {
            WidgetUpdateHelper.getPrefs(context).edit().remove(PREF_PREFIX + appWidgetId).apply()
        }
    }
}

enum class WidgetStyle(
    val backgroundColor: Int,
    val mainColor: Int,
    val subColor: Int,

    val highlightColor: Int,

    val pastColor: Int,
    val title: String,
) {
    LIGHT(
        0xFFFFFFFF.toInt(),
        0xFF111827.toInt(),
        0xFF6B7280.toInt(),
        0xFFE8F0FA.toInt(),
        0xFF9AA3AF.toInt(),
        "Белый",
    ),
    DARK(
        0xFF001737.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFB9C6D8.toInt(),
        0xFF0B3564.toInt(),
        0xFF5A6B85.toInt(),
        "Тёмно-синий",
    ),
    BLUE(
        0xFF0B3564.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFAFC6E4.toInt(),
        0xFF16457F.toInt(),
        0xFF6E86A6.toInt(),
        "Фирменный синий",
    ),
    SKY(
        0xFF0072CE.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFD5E9FA.toInt(),
        0xFF0B3564.toInt(),
        0xFF8FB8DD.toInt(),
        "Яркий синий",
    ),
    ;

    companion object {
        private const val PREF_PREFIX = "widget_style_"

        fun load(context: Context, appWidgetId: Int): WidgetStyle {
            val name = WidgetUpdateHelper.getPrefs(context).getString(PREF_PREFIX + appWidgetId, null)
            return entries.firstOrNull { it.name == name } ?: LIGHT
        }

        fun save(context: Context, appWidgetId: Int, style: WidgetStyle) {
            WidgetUpdateHelper.getPrefs(context)
                .edit()
                .putString(PREF_PREFIX + appWidgetId, style.name)
                .apply()
        }

        fun remove(context: Context, appWidgetId: Int) {
            WidgetUpdateHelper.getPrefs(context).edit().remove(PREF_PREFIX + appWidgetId).apply()
        }
    }
}

object WidgetAlarm {

    private const val SCHOOL_START = 7 * 60
    private const val SCHOOL_END = 21 * 60

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetAlarmReceiver::class.java).apply {
            action = WidgetAlarmReceiver.ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            4002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * O1: будильник нужен, только если на экране есть хотя бы один виджет.
     * Раньше короткое замыкание на «сервис включён» возвращало true даже без виджетов —
     * будильник каждую минуту будил процесс ради пустого updateAll.
     * Сам дубль с BellTimerService убран в WidgetUpdateHelper.updateMinuteWidgets (тик).
     */
    private fun hasWork(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(
            BellCountdownWidgetProvider::class.java,
            NowNextWidgetProvider::class.java,
            TodayScheduleWidgetProvider::class.java,
            BellsWidgetProvider::class.java,
        ).any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun scheduleNext(context: Context) {
        if (!hasWork(context)) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val triggerAt = if (minutes in SCHOOL_START until SCHOOL_END) {
            System.currentTimeMillis() + 60_000L
        } else {
            (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
        }

        val pi = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}

class WidgetAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {
                // O1: если BellTimerService ЖИВ, он уже пересобрал BellCountdown на этой же
                // границе минуты — повторно не делаем, обновляем только NowNext (сервис его не трогает).
                // WID-02: но сервис убивается OEM (battery saver на Xiaomi/Samsung) даже как FGS —
                // тогда countdown встал бы до следующего неточного будильника. Поэтому если сервис
                // НЕ запущен, пересобираем ОБА минутных виджета сами (будильник = страховка).
                val serviceAlive = com.politex.util.BellCountdownNotifier.isEnabled(context) &&
                    com.politex.util.BellTimerService.isRunning
                if (serviceAlive) {
                    NowNextWidgetProvider.updateAll(context)
                } else {
                    WidgetUpdateHelper.updateMinuteWidgets(context)
                }
                WidgetAlarm.scheduleNext(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                WidgetAlarm.scheduleNext(context)

                if (com.politex.util.BellCountdownNotifier.isEnabled(context)) {
                    com.politex.util.BellTimerService.start(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.politex.widget.ACTION_BELL_COUNTDOWN_TICK"
    }
}
