package com.politex.util

import android.content.Context
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.ui.theme.AppTheme
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private const val FORMAT_VERSION = 2

    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_APP = "app"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_TASKS = "tasks"
    private const val KEY_GRADES = "grades"
    private const val KEY_REMINDERS = "reminders"

    data class ImportResult(
        val tasksRestored: Int,
        val groupRestored: String?,
        val themeRestored: Boolean,
        val warnings: List<String>,
        val gradesRestored: Int = 0,
        val remindersRestored: Int = 0,
    )

    suspend fun buildBackup(
        context: Context,
        includeTasks: Boolean = true,
    ): String {
        val root = JSONObject()
        root.put(KEY_VERSION, FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_APP, "Мой Политех")

        val settings = JSONObject()
        settings.put("group", WidgetUpdateHelper.getSelectedGroup(context))
        settings.put("notificationsEnabled", WidgetUpdateHelper.isNotificationEnabled(context))
        settings.put("appTheme", AppThemeStore.load(context).id)
        settings.put(
            "notificationsStyle",
            com.politex.util.BellCountdownNotifier
                .getStyle(context)
                .id,
        )
        settings.put(
            "notificationsTheme",
            com.politex.util.BellCountdownNotifier
                .getTheme(context)
                .id,
        )
        root.put(KEY_SETTINGS, settings)

        if (includeTasks) {
            val db = MpkDatabase.getInstance(context)
            val reminderDao = db.taskReminderDao()
            val tasks = db.taskDao().getAllTasks()
            val array = JSONArray()
            tasks.forEach { task ->
                array.put(
                    taskToJson(task).apply {

                        val reminders = reminderDao.getForTask(task.id)
                        if (reminders.isNotEmpty()) {
                            put(
                                KEY_REMINDERS,
                                JSONArray(reminders.map { it.triggerAtMillis }),
                            )
                        }
                    },
                )
            }
            root.put(KEY_TASKS, array)
        }

        val grades = MpkDatabase.getInstance(context).gradeDao().allEntries().first()
        val gradesArray = JSONArray()
        grades.forEach { grade ->
            gradesArray.put(
                JSONObject()
                    .put("subjectName", grade.subjectName)
                    .put("category", grade.category)
                    .put("value", grade.value)
                    .put("label", grade.label)
                    .put("dateIso", grade.dateIso)
                    .put("createdAt", grade.createdAt),
            )
        }
        root.put(KEY_GRADES, gradesArray)

        return root.toString(2)
    }

    private fun taskToJson(task: StudentTaskEntity): JSONObject = JSONObject().apply {
        put("groupName", task.groupName)
        put("course", task.course)
        put("subjectName", task.subjectName)
        put("taskType", task.taskType)
        put("title", task.title)
        put("description", task.description)
        put("isCompleted", task.isCompleted)
        put("deadlineDate", task.deadlineDate)
        put("createdAt", task.createdAt)
    }

    suspend fun restoreBackup(
        context: Context,
        json: String,
    ): Result<ImportResult> = try {
        val root = JSONObject(json)
        val warnings = mutableListOf<String>()

        val version = root.optInt(KEY_VERSION, 0)
        if (version > FORMAT_VERSION) {
            warnings += "Файл сохранён более новой версией приложения. " +
                "Что удалось — восстановлено, остальное пропущено."
        }

        val settings = root.optJSONObject(KEY_SETTINGS)
        var groupRestored: String? = null
        var themeRestored = false

        settings?.optString("group")?.takeIf { it.isNotBlank() }?.let { group ->
            WidgetUpdateHelper.setSelectedGroup(context, group)
            groupRestored = group
        }
        settings?.optString("appTheme")?.takeIf { it.isNotBlank() }?.let { themeId ->
            val theme = AppTheme.from(themeId)
            AppThemeStore.save(context, theme)
            themeRestored = theme != AppTheme.COLLEGE || themeId == AppTheme.COLLEGE.id
        }
        if (settings?.has("notificationsEnabled") == true) {
            WidgetUpdateHelper.setNotificationEnabled(context, settings.optBoolean("notificationsEnabled"))
        }

        val db = MpkDatabase.getInstance(context)
        val dao = db.taskDao()
        val reminderDao = db.taskReminderDao()
        val array = root.optJSONArray(KEY_TASKS)
        var restored = 0
        var remindersRestored = 0

        if (array != null) {
            val existing =
                dao
                    .getAllTasks()
                    .map { it.dedupeKey() }
                    .toHashSet()
            var skipped = 0

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title")
                if (title.isBlank()) {
                    warnings += "Задание без названия пропущено"
                    continue
                }
                val entity =
                    StudentTaskEntity(
                        groupName = item.optString("groupName"),
                        course = item.optInt("course", 0),
                        subjectName = item.optString("subjectName"),
                        taskType = item.optString("taskType"),
                        title = title,
                        description = item.optString("description"),
                        isCompleted = item.optBoolean("isCompleted", false),
                        deadlineDate = item.optString("deadlineDate"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    )
                if (!existing.add(entity.dedupeKey())) {
                    skipped++
                    continue
                }
                val newTaskId = dao.insertTask(entity)
                restored++

                val remindersJson = item.optJSONArray(KEY_REMINDERS)
                if (remindersJson != null && remindersJson.length() > 0) {
                    val reminders =
                        (0 until remindersJson.length())
                            .map { remindersJson.optLong(it) }
                            .filter { it > 0L }
                            .map { TaskReminderEntity(taskId = newTaskId, triggerAtMillis = it) }
                    if (reminders.isNotEmpty()) {
                        reminderDao.insertAll(reminders)
                        remindersRestored += reminders.size
                    }
                }
            }
            if (skipped > 0) warnings += "Уже есть, пропущено: $skipped"
        }

        val gradeDao = db.gradeDao()
        val gradesArray = root.optJSONArray(KEY_GRADES)
        var gradesRestored = 0
        if (gradesArray != null) {
            val existingGrades =
                gradeDao
                    .allEntries()
                    .first()
                    .map { it.dedupeKey() }
                    .toHashSet()
            for (i in 0 until gradesArray.length()) {
                val item = gradesArray.optJSONObject(i) ?: continue
                val entry =
                    GradeEntryEntity(
                        subjectName = item.optString("subjectName"),
                        category = item.optString("category"),
                        value = item.optInt("value"),
                        label = item.optString("label"),
                        dateIso = item.optString("dateIso"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    )
                if (entry.subjectName.isBlank() || entry.dateIso.isBlank()) continue
                if (!existingGrades.add(entry.dedupeKey())) continue
                if (entry.category == GradeCategory.EXAM.name) {
                    gradeDao.upsertExam(entry)
                } else {
                    gradeDao.insert(entry)
                }
                gradesRestored++
            }
        }

        Result.success(
            ImportResult(
                tasksRestored = restored,
                groupRestored = groupRestored,
                themeRestored = themeRestored,
                warnings = warnings,
                gradesRestored = gradesRestored,
                remindersRestored = remindersRestored,
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun StudentTaskEntity.dedupeKey(): String = listOf(groupName, subjectName, title, deadlineDate).joinToString("|").lowercase()

private fun GradeEntryEntity.dedupeKey(): String = listOf(subjectName, category, value, label, dateIso).joinToString("|").lowercase()

fun defaultBackupFileName(): String {
    val cal = java.util.Calendar.getInstance()
    val stamp =
        String.format(
            java.util.Locale.ROOT,
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    return "moy-politech-backup-$stamp.json"
}
