package com.example.util

import android.content.Context
import com.example.data.local.MpkDatabase
import com.example.data.local.entity.StudentTaskEntity
import com.example.widget.WidgetUpdateHelper
import com.example.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия настроек и заданий в файл.
 *
 * Формат — JSON с номером версии. При чтении все поля берутся с значениями
 * по умолчанию, а незнакомые ключи игнорируются: файл, сохранённый более
 * старой версией приложения, обязан открыться в новой без потерь.
 *
 * Задания переносятся ВМЕСТЕ с группами — иначе при смене группы копия
 * оказалась бы пустой.
 */
object BackupManager {

    /** Версия формата. Поднимать только при несовместимом изменении структуры. */
    private const val FORMAT_VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_APP = "app"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_TASKS = "tasks"

    data class ImportResult(
        val tasksRestored: Int,
        val groupRestored: String?,
        val themeRestored: Boolean,
        val warnings: List<String>
    )

    // -----------------------------------------------------------------------
    // Экспорт
    // -----------------------------------------------------------------------

    /**
     * Собирает содержимое резервной копии.
     *
     * @param includeTasks класть ли задания (настройки можно сохранить и отдельно)
     */
    suspend fun buildBackup(context: Context, includeTasks: Boolean = true): String {
        val root = JSONObject()
        root.put(KEY_VERSION, FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_APP, "Мой Политех")

        val settings = JSONObject()
        settings.put("group", WidgetUpdateHelper.getSelectedGroup(context))
        settings.put("notificationsEnabled", WidgetUpdateHelper.isNotificationEnabled(context))
        settings.put("appTheme", AppThemeStore.load(context).id)
        settings.put("notificationsStyle", com.example.util.BellCountdownNotifier.getStyle(context).id)
        settings.put("notificationsTheme", com.example.util.BellCountdownNotifier.getTheme(context).id)
        root.put(KEY_SETTINGS, settings)

        if (includeTasks) {
            val tasks = MpkDatabase.getInstance(context).taskDao().getAllTasks()
            val array = JSONArray()
            tasks.forEach { task -> array.put(taskToJson(task)) }
            root.put(KEY_TASKS, array)
        }

        return root.toString(2)
    }

    private fun taskToJson(task: StudentTaskEntity): JSONObject = JSONObject().apply {
        // id намеренно не сохраняем: при восстановлении база выдаст свои номера,
        // иначе та же копия, залитая дважды, создала бы дубли с теми же id
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

    // -----------------------------------------------------------------------
    // Импорт
    // -----------------------------------------------------------------------

    /**
     * Восстанавливает данные из файла.
     *
     * Задания ДОБАВЛЯЮТСЯ к уже имеющимся, но одинаковые не
     * дублируются: если такое же задание уже есть, оно пропускается.
     * Так копию можно залить дважды без вреда и нельзя случайно
     * стереть текущие записи неудачным файлом.
     */
    suspend fun restoreBackup(
        context: Context,
        json: String
    ): Result<ImportResult> = try {
        val root = JSONObject(json)
        val warnings = mutableListOf<String>()

        val version = root.optInt(KEY_VERSION, 0)
        if (version > FORMAT_VERSION) {
            warnings += "Файл сохранён более новой версией приложения. " +
                "Что удалось — восстановлено, остальное пропущено."
        }

        // --- Настройки ---
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

        // --- Задания ---
        val dao = MpkDatabase.getInstance(context).taskDao()
        val array = root.optJSONArray(KEY_TASKS)
        var restored = 0

        if (array != null) {
            // Ключ уже существующих заданий — чтобы не плодить дубли
            val existing = dao.getAllTasks()
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
                val entity = StudentTaskEntity(
                    groupName = item.optString("groupName"),
                    course = item.optInt("course", 0),
                    subjectName = item.optString("subjectName"),
                    taskType = item.optString("taskType"),
                    title = title,
                    description = item.optString("description"),
                    isCompleted = item.optBoolean("isCompleted", false),
                    deadlineDate = item.optString("deadlineDate"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
                if (!existing.add(entity.dedupeKey())) {
                    skipped++
                    continue
                }
                dao.insertTask(entity)
                restored++
            }
            if (skipped > 0) warnings += "Уже есть, пропущено: $skipped"
        }

        Result.success(
            ImportResult(
                tasksRestored = restored,
                groupRestored = groupRestored,
                themeRestored = themeRestored,
                warnings = warnings
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Отпечаток задания для поиска дублей при восстановлении.
 * id не годится: после восстановления база выдаёт свои номера.
 */
private fun StudentTaskEntity.dedupeKey(): String =
    listOf(groupName, subjectName, title, deadlineDate).joinToString("|").lowercase()

/** Имя файла резервной копии с датой — чтобы копии не перезаписывали друг друга. */
fun defaultBackupFileName(context: Context): String {
    val cal = java.util.Calendar.getInstance()
    val stamp = String.format(
        java.util.Locale.ROOT, "%04d-%02d-%02d",
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
    return "moy-politech-backup-$stamp.json"
}
