package com.politex.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.politex.MainActivity
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherDayLoader
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.TeachersRepository
import com.politex.data.repository.matchRowsForTeacher
import com.politex.ui.teacher.TeacherProfileStore
import com.politex.util.DateFormats
import com.politex.util.Dow
import kotlinx.coroutines.launch
import java.util.Calendar

data class TeacherWidgetSnapshot(
    val dateString: String,
    val dow: Int,
    val dayOfMonth: Int,
    val month1: Int,
    val lessons: List<TeacherWidgetLesson>, // сегодня
    val next: NextLessonRef?, // из кэша следующих ≤7 дней (когда сегодня пар больше нет)
    val lastSyncEpoch: Long,
)

/**
 * Обновление обоих teacher-виджетов. Регистрация провайдеров — ТОЛЬКО в teacher-сборке
 * (src/teacher/AndroidManifest.xml), поэтому вызовы из общих точек (WidgetAlarmReceiver,
 * TeacherScheduleWorker) идут БЕЗ flavor-гейта: в студенческой сборке профиль null →
 * мгновенный выход (одно чтение prefs, без БД и корутин).
 */
object TeacherWidgetUpdater {
    fun updateAll(context: Context) {
        if (TeacherProfileStore.get(context) == null) return
        WidgetUpdateHelper.widgetScope.launch { updateAllNow(context) }
    }

    /** Для вызова из уже запущенной корутины (WidgetAlarmReceiver). */
    suspend fun updateAllNow(context: Context) {
        if (TeacherProfileStore.get(context) == null) return
        // GG8(а)-паттерн: падение сборки одного виджета не рвёт обновление второго
        runCatching { TeacherNextWidgetProvider.updateAll(context) }
        runCatching { TeacherTodayWidgetProvider.updateAll(context) }
    }

    // T4 сделает deep-link на экран дня; пока тап просто открывает приложение (SPEC §4.1 фича 8)
    fun openAppIntent(
        context: Context,
        code: Int,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * Снапшот «моего дня» из БД (teacher_lessons напрямую, как требует задача T3-C: loader
 * агента A виджеты не проходят). Кэш — по образцу WidgetData.todayLessons: ключ с
 * датой+минутой+профилем (грабли v2.x).
 */
object TeacherWidgetData {
    @Volatile private var cacheKey = ""

    @Volatile private var cache: TeacherWidgetSnapshot? = null

    /** null — профиль не выбран (виджет показывает «Выберите себя в приложении»). */
    suspend fun snapshot(context: Context): TeacherWidgetSnapshot? {
        val profile = TeacherProfileStore.get(context) ?: return null
        val cal = Calendar.getInstance()
        val today = DateFormats.dotDate(cal)
        val minute = WidgetData.minuteOfDay(cal)

        val db = MpkDatabase.getInstance(context)

        val key = TeacherWidgetLogic.cacheKey(today, minute, profile.name)
        if (key == cacheKey) return cache

        val teachers = TeachersRepository(context).loadTeachers()
        val selected = Teacher(name = profile.name)
        val surnameKey = profile.surnameKey.ifBlank { TeacherInsights.surnameOf(profile.name) }
        val dow = Dow.of(cal)

        val lessons = matchedRows(db, surnameKey, selected, teachers, today, dow).map { toLesson(it, dow) }

        // ponytail: скан следующих дней — только когда сегодня пар больше нет (≤7 дешёвых
        // запросов кэша БД, без сети; синк — работа TeacherScheduleWorker)
        val next =
            if (lessons.any { it.startMinutes > minute }) {
                null
            } else {
                scanNextDays(db, surnameKey, selected, teachers, cal)
            }

        val snap =
            TeacherWidgetSnapshot(
                dateString = today,
                dow = dow,
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                month1 = cal.get(Calendar.MONTH) + 1,
                lessons = lessons,
                next = next,
                lastSyncEpoch = TeacherDayLoader.readLastSyncEpoch(context),
            )
        cache = snap
        cacheKey = key
        return snap
    }

    private suspend fun scanNextDays(
        db: MpkDatabase,
        surnameKey: String,
        selected: Teacher,
        teachers: List<Teacher>,
        baseCal: Calendar,
    ): NextLessonRef? {
        val days =
            (1..7).mapNotNull { offset ->
                val c = (baseCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                val d = Dow.of(c)
                if (d == 7) return@mapNotNull null
                val date = DateFormats.dotDate(c)
                val lessons = matchedRows(db, surnameKey, selected, teachers, date, d).map { toLesson(it, d) }
                TeacherDayRef(date, d, lessons)
            }
        return TeacherWidgetLogic.findNextLesson(days)
    }

    // Фильтр однофамильцев ОБЯЗАТЕЛЕН (matchRowToTeacher YES/NO/AMBIGUOUS через matchRowsForTeacher)
    private suspend fun matchedRows(
        db: MpkDatabase,
        surnameKey: String,
        selected: Teacher,
        teachers: List<Teacher>,
        dateString: String,
        dow: Int,
    ): List<TeacherLessonEntity> =
        matchRowsForTeacher(
            db.teacherLessonDao().getForSurnameOnDate(surnameKey, dateString),
            selected,
            teachers,
            dow,
        ).first

    private fun toLesson(
        row: TeacherLessonEntity,
        dow: Int,
    ): TeacherWidgetLesson {
        val bell = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { it.lessonNumber == row.lessonNumber }
        return TeacherWidgetLesson(
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
}
