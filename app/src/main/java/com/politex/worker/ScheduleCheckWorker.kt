package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.local.MpkDatabase
import com.politex.data.network.MpkNetworkClient
import com.politex.data.repository.ScheduleRepository
import com.politex.util.GroupCatalog
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
        // F4: документ уже скачан и разобран, но уроков по группе на эту дату в нём нет
        // (суббота/праздник/пустой день). Без этой отметки воркер качал и разбирал бы
        // весь документ по всем группам каждые 15 минут до 21:00 без всякого эффекта.
        val checkedKey = "checked_${groupName}_$targetDateString"
        val prefs = WidgetUpdateHelper.getPrefs(applicationContext)
        if (prefs.getBoolean(prefKey, false) || prefs.getBoolean(checkedKey, false)) {
            return@withContext Result.success()
        }

        val networkClient = MpkNetworkClient()
        val db = MpkDatabase.getInstance(applicationContext)
        // Б3: тот же репозиторий, что у приложения — документ разбирается сразу по всем
        // группам каталога и пишется в Room. Раньше воркер качал день одной группы,
        // поэтому фоновая догрузка не помогала открыть чужую группу без сети.
        val repository = ScheduleRepository(
            lessonDao = db.lessonDao(),
            networkClient = networkClient,
            catalogProvider = { GroupCatalog.load(applicationContext).all() }
        )
        val result = repository.syncScheduleForDate(groupName, targetCalendar)

        result.fold(
            onSuccess = { allLessons ->
                if (allLessons.isNotEmpty()) {
                    WidgetUpdateHelper.updateAllWidgets(applicationContext)

                    // Строки перештампованы реальными датами (парсер): фильтр только по дате
                    val targetLessons = allLessons.filter {
                        it.dateString == targetDateString
                    }
                    // Фолбэк для старого формата (пустая dateString) — по дню недели
                    val fallbackLessons = if (targetLessons.isEmpty()) {
                        allLessons.filter { it.dateString.isBlank() && it.dayOfWeek == targetDayOfWeek }
                    } else emptyList()
                    val notifyLessons = targetLessons.ifEmpty { fallbackLessons }

                    if (notifyLessons.isNotEmpty()) {
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
                            lessonCount = notifyLessons.size,
                            targetDayOfWeek = targetDayOfWeek,
                            targetDate = targetDateString
                        )

                        prefs.edit().putBoolean(prefKey, true).apply()
                    } else {
                        // День прочитан и разобран, уроков для группы нет — второй раз не ходим
                        prefs.edit().putBoolean(checkedKey, true).apply()
                    }
                } else {
                    // WID-03: success+пусто теперь бывает только когда документ найден
                    // (syncScheduleForDate при ненайденном документе возвращает failure) —
                    // день действительно пустой, второй раз не долбим сайт
                    prefs.edit().putBoolean(checkedKey, true).apply()
                }
                Result.success()
            },
            onFailure = {
                // Документ не найден/сеть отвалилась — checkedKey НЕ ставим,
                // воркер повторит попытку через 15 минут
                Result.success()
            }
        )
    }

    companion object {
        const val WORK_NAME = "mpk_schedule_check_worker"
    }
}
