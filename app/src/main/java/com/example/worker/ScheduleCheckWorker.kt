package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.MpkDatabase
import com.example.data.network.MpkNetworkClient
import com.example.data.repository.ScheduleRepository
import com.example.util.GroupParser
import com.example.util.NotificationHelper
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleCheckWorker(
    context: Context,
    workerParams: WorkerParameters
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

        val currentDayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val targetCalendar = Calendar.getInstance()
        val daysToAdd = when (currentDayOfWeek) {
            5 -> if (isFirstCourse) 1 else 3
            6 -> 2
            7 -> 1
            else -> 1
        }
        targetCalendar.add(Calendar.DAY_OF_YEAR, daysToAdd)

        val targetDayOfWeek = when (targetCalendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val targetDateString = dateFormat.format(targetCalendar.time)

        val prefKey = "notified_${groupName}_$targetDateString"
        val prefs = WidgetUpdateHelper.getPrefs(applicationContext)
        if (prefs.getBoolean(prefKey, false)) {
            return@withContext Result.success()
        }

        val networkClient = MpkNetworkClient()
        val result = networkClient.fetchScheduleForGroup(groupName, targetCalendar)

        result.fold(
            onSuccess = { allLessons ->
                if (allLessons.isNotEmpty()) {
                    val db = MpkDatabase.getInstance(applicationContext)
                    ScheduleRepository(db.lessonDao()).replaceSyncedLessons(groupName, allLessons)

                    WidgetUpdateHelper.updateAllWidgets(applicationContext)

                    val targetLessons = allLessons.filter {
                        it.dateString == targetDateString || it.dayOfWeek == targetDayOfWeek
                    }

                    if (targetLessons.isNotEmpty()) {
                        val dayTitles = mapOf(
                            1 to "понедельник",
                            2 to "вторник",
                            3 to "среду",
                            4 to "четверг",
                            5 to "пятницу",
                            6 to "субботу"
                        )
                        val titleDay = dayTitles[targetDayOfWeek] ?: "следующий учебный день"

                        NotificationHelper.showScheduleReleaseNotification(
                            context = applicationContext,
                            dayTitle = titleDay,
                            lessonCount = targetLessons.size,
                            targetDayOfWeek = targetDayOfWeek,
                            targetDate = targetDateString
                        )

                        prefs.edit().putBoolean(prefKey, true).apply()
                    }
                }
                Result.success()
            },
            onFailure = {

                Result.success()
            }
        )
    }

    companion object {
        const val WORK_NAME = "mpk_schedule_check_worker"
    }
}
