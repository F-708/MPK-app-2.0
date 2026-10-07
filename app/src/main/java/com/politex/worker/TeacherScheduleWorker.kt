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

            var anyOk = false
            var anyFail = false
            targets.forEachIndexed { index, cal ->
                // Force-перечитывание по SPEC §5.3: утренний прогон (обе цели) + окно
                // 14:00–18:00 для сегодня (внутридневные правки); прочие тики — кэш-первым.
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val force = decision == SyncDecision.MORNING_FORCE || (index == 0 && hour in 14..17)
                repository
                    .syncTeacherScheduleForDate(cal, force = force)
                    .onSuccess { anyOk = true }
                    .onFailure { anyFail = true }
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
                return@withContext Result.success()
            }
            // Сетевые ошибки тихо: retry/следующий тик, без уведомлений (уведомления — T4).
            if (anyFail) Result.retry() else Result.success()
        }

    companion object {
        const val WORK_NAME = "teacher_schedule_sync"
    }
}
