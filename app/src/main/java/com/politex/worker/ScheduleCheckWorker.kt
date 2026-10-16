package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.network.ProbeResult
import com.politex.data.repository.nextDayCandidates
import com.politex.util.GroupParser
import com.politex.util.NotificationHelper
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!WidgetUpdateHelper.isNotificationEnabled(applicationContext)) {
            return@withContext Result.success()
        }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        if (currentMinutes < 10 * 60 || currentMinutes > 21 * 60) {
            return@withContext Result.success()
        }

        val groupName = WidgetUpdateHelper.getSelectedGroup(applicationContext)
        val groupInfo = GroupParser.parse(groupName)
        val isFirstCourse = groupInfo?.course == 1

        val repository = com.politex.AppGraph.scheduleRepository

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        val prefs = WidgetUpdateHelper.getPrefs(applicationContext)
        fun marked(cal: Calendar): Boolean {
            val ds = dateFormat.format(cal.time)
            return prefs.getBoolean("notified_${groupName}_$ds", false) ||
                prefs.getBoolean("checked_${groupName}_$ds", false)
        }

        fun mark(key: String) {
            val editor = prefs.edit().putBoolean(key, true)
            staleMarkKeys(prefs.all.keys, System.currentTimeMillis()).forEach { editor.remove(it) }
            editor.apply()
        }
        val live = nextDayCandidates(calendar, saturdayIsSchoolDay = isFirstCourse).filterNot { marked(it) }
        if (live.isEmpty()) {
            return@withContext Result.success()
        }
        var targetCalendar = live.last()
        for (candidate in live) {
            if (repository.probeDate(candidate) is ProbeResult.Found) {
                targetCalendar = candidate
                break
            }
        }

        val targetDayOfWeek = com.politex.util.Dow.of(targetCalendar)

        val targetDateString = dateFormat.format(targetCalendar.time)

        val prefKey = "notified_${groupName}_$targetDateString"

        val checkedKey = "checked_${groupName}_$targetDateString"

        val result = repository.syncScheduleForDate(groupName, targetCalendar)

        result.fold(
            onSuccess = { allLessons ->
                if (allLessons.isNotEmpty()) {
                    WidgetUpdateHelper.updateAllWidgets(applicationContext)

                    val targetLessons =
                        allLessons.filter {
                            it.dateString == targetDateString
                        }

                    val fallbackLessons =
                        if (targetLessons.isEmpty()) {
                            allLessons.filter { it.dateString.isBlank() && it.dayOfWeek == targetDayOfWeek }
                        } else {
                            emptyList()
                        }
                    val notifyLessons = targetLessons.ifEmpty { fallbackLessons }

                    if (notifyLessons.isNotEmpty()) {
                        val dayTitles =
                            mapOf(
                                1 to "понедельник",
                                2 to "вторник",
                                3 to "среду",
                                4 to "четверг",
                                5 to "пятницу",
                                6 to "субботу",
                            )
                        val titleDay = dayTitles[targetDayOfWeek] ?: "следующий учебный день"

                        NotificationHelper.showScheduleReleaseNotification(
                            context = applicationContext,
                            dayTitle = titleDay,
                            lessonCount = notifyLessons.size,
                            targetDayOfWeek = targetDayOfWeek,
                            targetDate = targetDateString,
                        )

                        mark(prefKey)
                    } else {

                        mark(checkedKey)
                    }
                } else {

                    mark(checkedKey)
                }
                Result.success()
            },
            onFailure = {

                Result.success()
            },
        )
    }

    companion object {
        const val WORK_NAME = "mpk_schedule_check_worker"

        private val DATE_IN_KEY = Regex("""\d{2}\.\d{2}\.\d{4}""")

        internal fun staleMarkKeys(
            allKeys: Set<String>,
            nowMillis: Long,
            keepDays: Int = 7,
        ): List<String> {
            val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
            val cutoff = nowMillis - keepDays * 24L * 60 * 60 * 1000
            return allKeys.filter { key ->
                (key.startsWith("notified_") || key.startsWith("checked_")) &&
                    DATE_IN_KEY.find(key)?.value?.let { ds ->
                        runCatching { fmt.parse(ds)?.time }.getOrNull()?.let { it < cutoff } ?: false
                    } ?: false
            }
        }
    }
}
