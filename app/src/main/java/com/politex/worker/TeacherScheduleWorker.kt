package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.repository.nextDayCandidates
import com.politex.ui.teacher.TeacherProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class SyncDecision { MORNING_FORCE, PERIODIC, SKIP }

// Чистая функция окон синка (hourMinute = часы*60+минуты, dayOfWeek = Calendar.DAY_OF_WEEK).
// Суббота трактуется как будень (может быть учебной), воскресенье — всегда SKIP.
fun shouldTeacherSync(
    hourMinute: Int,
    dayOfWeek: Int,
): SyncDecision {
    if (dayOfWeek == Calendar.SUNDAY) return SyncDecision.SKIP
    return when {
        hourMinute in 6 * 60 + 30..7 * 60 + 15 -> SyncDecision.MORNING_FORCE
        hourMinute in 10 * 60..21 * 60 -> SyncDecision.PERIODIC
        else -> SyncDecision.SKIP
    }
}

class TeacherScheduleWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            TeacherProfileStore.get(applicationContext) ?: return@withContext Result.success()

            val now = Calendar.getInstance()
            val decision =
                shouldTeacherSync(
                    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
                    now.get(Calendar.DAY_OF_WEEK),
                )
            if (decision == SyncDecision.SKIP) return@withContext Result.success()

            val repository = com.politex.AppGraph.scheduleRepository
            // ponytail: saturdayIsSchoolDay=true всегда — у преподавателей суббота может быть учебной.
            val targets = listOf(now) + nextDayCandidates(now, saturdayIsSchoolDay = true)
            val dates = targets.map { com.politex.util.DateFormats.dotDate(it) }

            // T4-B: снапшот кэша ДО синка (для diff-уведомлений) и метка прошлого синка
            // (для честной freshness-ветки утренней сводки). Профиль null → пустой снапшот.
            val lastSyncBefore = com.politex.data.repository.TeacherDayLoader.readLastSyncEpoch(applicationContext)
            val before = com.politex.util.TeacherNotifier.snapshotRaw(applicationContext, dates)

            var anyOk = false
            var anyFail = false
            var todayOk = false
            targets.forEachIndexed { index, cal ->
                // Force-перечитывание по SPEC §5.3: утренний прогон (обе цели) + окно
                // 14:00–18:00 для сегодня (внутридневные правки); прочие тики — кэш-первым.
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val force = decision == SyncDecision.MORNING_FORCE || (index == 0 && hour in 14..17)
                repository
                    .syncTeacherScheduleForDate(cal, force = force)
                    .onSuccess {
                        anyOk = true
                        if (index == 0) todayOk = true
                    }.onFailure { anyFail = true }
            }

            if (anyOk) {
                // Контракт с экраном «Мой день»: метка «обновлено в HH:MM».
                applicationContext
                    .getSharedPreferences("mpk_teacher_profile", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("lastSyncEpoch", System.currentTimeMillis())
                    .apply()
                // T3-C: после успешного синка — перерисовать teacher-виджеты (в студенческой сборке профиль null → мгновенный выход)
                com.politex.widget.TeacherWidgetUpdater.updateAll(applicationContext)
            }
            // T4-B: хуки уведомлений (сводка ≥07:15, diff ≤1 на синк, «на завтра опубликовано»,
            // будильник напоминаний). Даже при провале синка — сводка уходит по кэшу с честной
            // меткой «от <дата/время кэша>». Падение нотификатора не рвёт синк (GG8-паттерн).
            runCatching {
                com.politex.util.TeacherNotifier.onSyncCompleted(
                    context = applicationContext,
                    dates = dates,
                    before = before,
                    todayOk = todayOk,
                    lastSyncBefore = lastSyncBefore,
                    now = Calendar.getInstance(),
                )
            }
            // Сетевые ошибки тихо: retry/следующий тик, без уведомлений об ошибке (SPEC §4.2 «д»).
            if (anyFail) Result.retry() else Result.success()
        }

    companion object {
        const val WORK_NAME = "teacher_schedule_sync"
    }
}
