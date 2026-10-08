package com.politex.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.politex.MainActivity
import com.politex.R
import com.politex.data.local.MpkDatabase
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.network.MpkScheduleParser
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLogic
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.TeachersRepository
import com.politex.data.repository.matchRowsForTeacher
import com.politex.ui.teacher.TeacherProfileStore
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Уведомления приложения-преподавателя (SPEC §5.3, §4.1 фичи 5–7, T4-B).
 * Простые NotificationCompat (кастомный стиль BellCountdownNotifier не нужен).
 *
 * Контракт честности §4.2 «д»: никогда не врать молча — включая право промолчать
 * вместо врущего пуша (пустой кэш + проваленный синк → сводки НЕТ).
 *
 * Профиль не выбран → НИЧЕГО не шлём (в студенческой сборке профиль всегда null,
 * поэтому вызовы из общих точек безопасны без flavor-гейтов — паттерн TeacherWidgetUpdater).
 *
 * Дедуп-ключи и настройки живут в prefs "mpk_teacher_profile" (контракт T4-A/T4-B):
 * настройки notif_* пишет экран настроек (агент A), здесь только читаются с дефолтами.
 */
object TeacherNotifier {
    const val CHANNEL_SUMMARY = "teacher_day_summary"
    const val CHANNEL_CHANGES = "teacher_schedule_changes"
    const val CHANNEL_REMINDER = "teacher_lesson_reminder"

    private const val PREFS = "mpk_teacher_profile"

    // Неймспейс notification-id и PendingIntent request-кодов teacher-сборки: 7_000_000+
    // (студенческие: 2026, 3_000_000+ reminder, 5_000_000+ виджеты — не пересекаются).
    private const val ID_SUMMARY = 7_000_001
    private const val PI_SUMMARY_OPEN = 7_000_002
    private const val PI_SUMMARY_MARK = 7_000_003
    private const val ID_CHANGES = 7_000_010
    private const val PI_CHANGES_OPEN = 7_000_011
    private const val ID_PUBLISHED = 7_000_020
    private const val PI_PUBLISHED_OPEN = 7_000_021
    private const val PI_REMINDER_ALARM = 7_000_100
    private const val ID_REMINDER_BASE = 7_000_200 // + lessonNumber
    private const val PI_REMINDER_BASE = 7_000_300 // + lessonNumber

    private const val KEY_PENDING_TOMORROW = "pending_tomorrow_published"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // Quiet hours 21:00–07:00 — НЕ уровень канала, а логика откладывания (TeacherNotifierLogic.quietHours).
        listOf(
            Triple(CHANNEL_SUMMARY, "Утренняя сводка", "Одно уведомление утром: пары на сегодня и свежесть данных сайта"),
            Triple(CHANNEL_CHANGES, "Изменения расписания", "Публикация документа и изменения пар на сегодня и завтра"),
            Triple(CHANNEL_REMINDER, "Напоминание о паре", "За несколько минут до пары при смене кабинета"),
        ).forEach { (id, name, description) ->
            nm.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    this.description = description
                },
            )
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun summaryEnabled(context: Context) = prefs(context).getBoolean("notif_summary_enabled", true)

    private fun changesEnabled(context: Context) = prefs(context).getBoolean("notif_changes_enabled", true)

    private fun reminderEnabled(context: Context) = prefs(context).getBoolean("notif_reminder_enabled", true)

    private fun reminderMinutes(context: Context) = prefs(context).getInt("notif_reminder_minutes", 10)

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // ——— Снапшоты кэша (БД напрямую, БЕЗ сети — сеть только в воркере) ———

    /**
     * Сырые пары (БЕЗ оверлея личных правок) по датам — для diff ДО/ПОСЛЕ синка:
     * правки преподавателя не должны порождать уведомления об «изменениях сайта».
     * Профиль не выбран → пустая карта.
     */
    suspend fun snapshotRaw(
        context: Context,
        dates: List<String>,
    ): Map<String, List<TeacherDayLesson>> {
        val profile = TeacherProfileStore.get(context) ?: return emptyMap()
        val db = MpkDatabase.getInstance(context)
        val teachers = TeachersRepository(context).loadTeachers()
        val selected = Teacher(name = profile.name)
        val surnameKey = profile.surnameKey.ifBlank { TeacherInsights.surnameOf(profile.name) }
        return dates.associateWith { date ->
            val dow = MpkScheduleParser.parseDotDate(date)?.let { Dow.of(it) } ?: 0
            matchRowsForTeacher(db.teacherLessonDao().getForSurnameOnDate(surnameKey, date), selected, teachers, dow)
                .first
                .map { toLesson(it, dow) }
                .sortedBy { it.lessonNumber }
        }
    }

    /** Пары дня с оверлеем личных правок (§5.4) — для сводки и напоминаний. */
    private suspend fun dayLessons(
        context: Context,
        date: String,
    ): List<TeacherDayLesson> {
        val raw = snapshotRaw(context, listOf(date))[date].orEmpty()
        if (raw.isEmpty()) return raw
        val overrides = MpkDatabase.getInstance(context).teacherOverrideDao().forDate(date)
        return TeacherDayLogic.applyOverrides(date, raw, overrides)
    }

    private fun toLesson(
        row: com.politex.data.local.entity.TeacherLessonEntity,
        dow: Int,
    ): TeacherDayLesson {
        val bell = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { it.lessonNumber == row.lessonNumber }
        return TeacherDayLesson(
            lessonNumber = row.lessonNumber,
            subject = row.subject,
            groups = row.groups.split(", ").filter { it.isNotBlank() },
            room = row.room,
            start = bell?.start ?: "",
            end = bell?.end ?: "",
            startMinutes = bell?.startMinutes ?: -1,
            endMinutes = bell?.endMinutes ?: -1,
        )
    }

    // ——— Точка входа из TeacherScheduleWorker (после синка, сегодня+следующий учебный день) ———

    /**
     * Хуки уведомлений после синка (SPEC §5.3): утренняя сводка ≥07:15, diff ≤1 на синк
     * в окне 07:00–21:00 (ночные изменения уйдут первым тиком после 07:00 — before-снапшот
     * следующего синка их поймает), «на завтра опубликовано» ≥18:00 (quiet → pending до 07:00),
     * переустановка будильника напоминаний. [before] — сырый снапшот кэша ДО синка.
     */
    suspend fun onSyncCompleted(
        context: Context,
        dates: List<String>,
        before: Map<String, List<TeacherDayLesson>>,
        todayOk: Boolean,
        lastSyncBefore: Long,
        now: Calendar,
    ) {
        if (TeacherProfileStore.get(context) == null || dates.isEmpty()) return
        val today = dates[0]
        val hm = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val quiet = TeacherNotifierLogic.quietHours(hm)

        if (!quiet) flushPendingPublished(context, today, hm)

        // 1) Утренняя сводка — ОДНО уведомление, дедуп summary_sent_<date>.
        val p = prefs(context)
        if (
            TeacherNotifierLogic.shouldSendSummary(
                hm,
                p.getBoolean("summary_sent_$today", false),
                before[today]?.isNotEmpty() == true,
                todayOk,
                summaryEnabled(context),
            )
        ) {
            val lessons = dayLessons(context, today)
            val text =
                TeacherNotifierLogic.summaryText(
                    lessons,
                    todayOk,
                    TeacherNotifierLogic.cacheTimeLabel(lastSyncBefore, now.timeInMillis),
                    hhmm(now),
                )
            if (text != null) {
                sendSummary(context, text)
                p.edit().putBoolean("summary_sent_$today", true).apply()
            }
            // text == null → молчание (§4.2 «д»), флаг НЕ ставим: попробуем следующим тиком.
        }

        // 2) Diff + «на завтра опубликовано» (канал schedule_changes).
        // after-снапшот нужен в обоих, поэтому считаем при включённой настройке независимо от quiet.
        if (changesEnabled(context)) {
            val after = snapshotRaw(context, dates)
            val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowStr = DateFormats.dotDate(tomorrow)
            val labels = dates.associateWith { TeacherNotifierLogic.dateLabel(it, today, tomorrowStr) }

            // 2a) Diff: ≤1 склеенное уведомление на синк. Ночью — ПРОПУСК (без pending):
            // утренний before-снапшот первого тика после 07:00 сам поймает ночные изменения.
            if (!quiet) {
                val messages = TeacherNotifierLogic.diffMessages(before, after, labels)
                if (messages.isNotEmpty()) {
                    val key = "diff_notified_${today}_${TeacherNotifierLogic.diffHash(messages)}"
                    if (!p.getBoolean(key, false)) {
                        sendDiff(context, messages, hm)
                        p.edit().putBoolean(key, true).apply()
                    }
                }
            }

            // 2b) «Расписание на завтра опубликовано»: вечерний синк (≥18:00) впервые нашёл данные.
            // В quiet (ровно 21:00 — единственный ночной синк) НЕ шлём, а откладываем флагом до 07:00.
            if (hm >= 18 * 60) {
                dates.drop(1).forEach { date ->
                    val publishedKey = "tomorrow_published_$date"
                    if (after[date]?.isNotEmpty() == true && before[date].isNullOrEmpty() && !p.getBoolean(publishedKey, false)) {
                        if (!quiet) {
                            sendPublished(context, labels[date] ?: date, TeacherNotifierLogic.lessonCount(after[date].orEmpty()))
                            p.edit().putBoolean(publishedKey, true).apply()
                        } else {
                            p.edit().putString(KEY_PENDING_TOMORROW, date).apply()
                        }
                    }
                }
            }
        }

        // 3) Будильник следующего напоминания (после любого синка).
        rescheduleReminder(context)
    }

    /** Отложенное ночью «на завтра опубликовано» — первым тиком после 07:00. */
    private suspend fun flushPendingPublished(
        context: Context,
        today: String,
        hm: Int,
    ) {
        val p = prefs(context)
        val date = p.getString(KEY_PENDING_TOMORROW, null) ?: return
        if (TeacherNotifierLogic.quietHours(hm)) return // ещё ночь — ждём тика после 07:00
        p.edit().remove(KEY_PENDING_TOMORROW).apply()
        val publishedKey = "tomorrow_published_$date"
        if (p.getBoolean(publishedKey, false)) return
        val lessons = snapshotRaw(context, listOf(date))[date].orEmpty()
        if (lessons.isEmpty()) return // данные исчезли — молчим вместо врущего пуша
        val label = if (date == today) "сегодня" else TeacherDayLogic.dayLabel(date)
        sendPublished(context, label, TeacherNotifierLogic.lessonCount(lessons))
        p.edit().putBoolean(publishedKey, true).apply()
    }

    // ——— Напоминание о паре: свой лёгкий точный будильник (не WidgetAlarm — виджетов может не быть) ———

    /**
     * Пересчёт следующего времени напоминания и взвод будильника.
     * setExactOrIdle (существующий хелпер) честно деградирует до setAndAllowWhileIdle,
     * когда canScheduleExactAlarms=false. Напоминание раньше 07:00 (quiet) → сдвигаем на 07:00.
     * Пары с isReplacement (ЗАМЕНА) не напоминаем — фильтр в TeacherNotifierLogic.reminderCandidates.
     */
    suspend fun rescheduleReminder(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = reminderAlarmPi(context)
        val at =
            if (TeacherProfileStore.get(context) != null && reminderEnabled(context)) {
                val now = Calendar.getInstance()
                val lessons = dayLessons(context, DateFormats.dotDate(now))
                TeacherNotifierLogic
                    .nextReminderAt(lessons, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE), reminderMinutes(context))
                    ?.let { if (it < 7 * 60) 7 * 60 else it }
            } else {
                null
            }
        if (at == null) {
            am.cancel(pi)
            return
        }
        val trigger =
            (Calendar.getInstance().clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, at / 60)
                set(Calendar.MINUTE, at % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        WidgetUpdateHelper.setExactOrIdle(context, trigger.timeInMillis, pi)
    }

    /** Вход TeacherReminderReceiver: проверить условие по кэшу → отправить → взвести следующий. */
    suspend fun checkReminderNow(context: Context) {
        if (TeacherProfileStore.get(context) == null || !reminderEnabled(context)) return
        val now = Calendar.getInstance()
        val today = DateFormats.dotDate(now)
        val hm = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val lessons = dayLessons(context, today)
        val due = TeacherNotifierLogic.reminderDue(lessons, hm, reminderMinutes(context))
        if (due != null) {
            val key = "lesson_reminded_${today}_${due.lessonNumber}"
            val p = prefs(context)
            if (!p.getBoolean(key, false)) {
                sendReminder(context, due, reminderMinutes(context))
                // ponytail: дедуп-ключи не прунятся (1 bool/день, копейки); при росте — чистка ключей старше 7 дней
                p.edit().putBoolean(key, true).apply()
            }
        }
        rescheduleReminder(context)
    }

    private fun reminderAlarmPi(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            PI_REMINDER_ALARM,
            Intent(context, TeacherReminderReceiver::class.java).setAction(TeacherReminderReceiver.ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ——— Отправка (простые NotificationCompat, PendingIntent на MainActivity с экстрами T4-A) ———

    private fun mainPi(
        context: Context,
        requestCode: Int,
        tab: String,
        markOverrides: Boolean = false,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("EXTRA_OPEN_TAB", tab)
        if (markOverrides) intent.putExtra("EXTRA_MARK_OVERRIDES", true)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun show(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        contentPi: PendingIntent,
        actionTitle: String? = null,
        actionPi: PendingIntent? = null,
    ) {
        if (!canPost(context)) return
        createChannels(context)
        val n =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_mpk_emblem)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .apply { if (actionTitle != null && actionPi != null) addAction(R.drawable.ic_mpk_emblem, actionTitle, actionPi) }
                .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (ignored: SecurityException) {
        }
    }

    private fun sendSummary(
        context: Context,
        text: String,
    ) {
        show(
            context,
            CHANNEL_SUMMARY,
            ID_SUMMARY,
            "Расписание на сегодня",
            text,
            mainPi(context, PI_SUMMARY_OPEN, "today"),
            // Контракт честности §4.2 «г»: action «Пометить» → override-лист (обрабатывает T4-A).
            actionTitle = "Пометить изменение",
            actionPi = mainPi(context, PI_SUMMARY_MARK, "today", markOverrides = true),
        )
    }

    private fun sendDiff(
        context: Context,
        messages: List<String>,
        hm: Int,
    ) {
        val text = messages.joinToString("\n") + "\n(данные сайта на ${String.format(java.util.Locale.ROOT, "%02d:%02d", hm / 60, hm % 60)})"
        show(context, CHANNEL_CHANGES, ID_CHANGES, "Изменения расписания", text, mainPi(context, PI_CHANGES_OPEN, "today"))
    }

    private fun sendPublished(
        context: Context,
        label: String,
        count: Int,
    ) {
        val text = "Опубликовано пар: $count. Нажмите, чтобы посмотреть."
        show(context, CHANNEL_CHANGES, ID_PUBLISHED, "Расписание на $label опубликовано", text, mainPi(context, PI_PUBLISHED_OPEN, "today"))
    }

    private fun sendReminder(
        context: Context,
        lesson: TeacherDayLesson,
        nMin: Int,
    ) {
        val title = "Через $nMin мин — ${lesson.lessonNumber}-я пара" + if (lesson.room.isNotBlank()) ", каб. ${lesson.room}" else ""
        val groups = lesson.groups.joinToString(", ")
        val text =
            listOfNotNull(lesson.subject.takeIf { it.isNotBlank() }, groups.takeIf { it.isNotBlank() }, lesson.start.takeIf { it.isNotBlank() })
                .joinToString(" · ")
        show(
            context,
            CHANNEL_REMINDER,
            ID_REMINDER_BASE + lesson.lessonNumber,
            title,
            text,
            mainPi(context, PI_REMINDER_BASE + lesson.lessonNumber, "today"),
        )
    }

    private fun hhmm(cal: Calendar): String = String.format(java.util.Locale.ROOT, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

/**
 * Лёгкий точный будильник напоминаний (SPEC §5.3 «за N мин до пары»). Регистрация —
 * ТОЛЬКО в src/teacher/AndroidManifest.xml (в студенческой сборке профиль null и
 * будильник никогда не взводится). После срабатывания receiver сам ставит следующий.
 * После перезагрузки будильник теряется — его восстановит ближайший тик TeacherScheduleWorker
 * (WorkManager переживает reboot), это честная деградация без BOOT-ресивера.
 */
class TeacherReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        WidgetUpdateHelper.widgetScope.launch {
            try {
                TeacherNotifier.checkReminderNow(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.politex.util.ACTION_TEACHER_REMINDER"
    }
}
