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

        // V2: вместо хардкода daysToAdd — кандидаты «завтра» (завтра → сб → пн) из
        // nextDayCandidates; цель = первый кандидат, документ которого probe нашёл на сайте.
        // Не нашёл ничего — последний кандидат (понедельник): syncScheduleForDate ищет
        // полным водопадом. Уже отмеченные (notified/checked) кандидаты не probe'ятся вовсе.
        // ponytail: в пятницу (сб+пн живые) probe — пара HEAD-запросов раз в 15 минут;
        // если станет дорого, кэшировать NotFound в prefs до конца дня.
        val repository = com.politex.AppGraph.scheduleRepository
        // QA#4: Locale.ROOT — ключ prefs и dateString сравниваются с БД (ASCII dd.MM.yyyy)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        val prefs = WidgetUpdateHelper.getPrefs(applicationContext)
        fun marked(cal: Calendar): Boolean {
            val ds = dateFormat.format(cal.time)
            return prefs.getBoolean("notified_${groupName}_$ds", false) ||
                prefs.getBoolean("checked_${groupName}_$ds", false)
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

        val targetDayOfWeek =
            when (targetCalendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

        val targetDateString = dateFormat.format(targetCalendar.time)

        val prefKey = "notified_${groupName}_$targetDateString"
        // F4: документ уже скачан и разобран, но уроков по группе на эту дату в нём нет
        // (суббота/праздник/пустой день). Без этой отметки воркер качал и разбирал бы
        // весь документ по всем группам каждые 15 минут до 21:00 без всякого эффекта.
        val checkedKey = "checked_${groupName}_$targetDateString"

        // E1/Б3: шаренный репозиторий из AppGraph — документ разбирается по всем группам
        // каталога, кэш и teacherLessonDao те же, что у UI
        val result = repository.syncScheduleForDate(groupName, targetCalendar)

        result.fold(
            onSuccess = { allLessons ->
                if (allLessons.isNotEmpty()) {
                    WidgetUpdateHelper.updateAllWidgets(applicationContext)

                    // Строки перештампованы реальными датами (парсер): фильтр только по дате
                    val targetLessons =
                        allLessons.filter {
                            it.dateString == targetDateString
                        }
                    // Фолбэк для старого формата (пустая dateString) — по дню недели
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
            },
        )
    }

    companion object {
        const val WORK_NAME = "mpk_schedule_check_worker"
    }
}
