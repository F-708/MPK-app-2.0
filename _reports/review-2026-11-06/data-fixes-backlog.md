# Патч-готовый бэклог домена «Данные» — «Мой Политех» (Room v11)

Источник: `_reports/review-2026-11-06/data.md` (18 находок: 3×P1, 7×P2, 8×P3).
Дата: 2026-11-06 · Ветка: `v2.4-stable` · **Код не менялся, gradle не запускался.**

Как читать: каждый пункт — `файл:строка`, диф «было → стало», риск, обратимость, способ проверки, приоритет и **занятость файла** (часть файлов домена параллельно правят другие агенты).

---

## 0. Карта занятости файлов (важно для волн)

| Файл | Пункты бэклога | Конфликты внутри домена |
|---|---|---|
| `data/local/MpkDatabase.kt` | P1-A, P2-1, P3-M (схемы) | **строго последовательно** — все три трогают `version`/`addMigrations` |
| `MpkApplication.kt` | P1-A(опц.), P2-4a | последовательно с P1-A |
| `util/BackupManager.kt` | P1-C | изолирован |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | P1-B | изолированы |
| `data/local/dao/TaskDao.kt`, `entity/StudentTaskEntity.kt`, `data/model/TaskType.kt` | P2-1 | последовательно с P2-1-миграцией |
| `data/local/dao/TeacherLessonDao.kt` | P2-2 | изолирован |
| `data/repository/ScheduleRepository.kt` | P2-2, P2-3, P3-4, P3-5 | **последовательно** |
| `widget/TeacherWidgetData.kt`, `widget/NowNextWidgetProvider.kt`, `widget/BellCountdownWidgetProvider.kt` | P2-4b | **чужая зона (виджеты)** — только по согласованию |
| `data/local/entity/TeacherOverrideEntity.kt`, `dao/TeacherOverrideDao.kt`, `util/TeacherModeStore.kt` | P2-5 | изолированы |
| `util/MaterialImporter.kt` | P3-2 | изолирован |

Безопасно параллельно в одной волне: **P1-B, P1-C, P2-2(DAO-часть), P2-5, P3-2** — разные файлы.

---

## P1-A. `fallbackToDestructiveMigration()` — три варианта

**Файл:** `app/src/main/java/com/politex/data/local/MpkDatabase.kt:277-291`
**Текущее (было):**

```kotlin
    fun getInstance(context: Context): MpkDatabase = instance ?: synchronized(this) {
        instance ?: run {
            val built =
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        MpkDatabase::class.java,
                        DATABASE_NAME,
                    ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11)
                    .fallbackToDestructiveMigration()   // ← :286
                    .build()
            instance = built
            built
        }
    }
```

**Риск:** любой путь миграции, которого Room не знает (`user_version` файла > 11 после откатa APK/восстановления чужого бэкапа; будущий bump без миграции), приводит к **удалению `mpk_schedule.db` и созданию пустой базы** без единого сообщения. Теряются ДЗ, оценки, материалы, аннотации, правки преподавателя.

---

### Вариант A — оставить destruct + авто-снимок БД перед открытием

**Плюсы:** минимальный диф; нет риска «приложение вообще не запускается»; данные физически остаются в файле-снимке.
**Минусы:** без экрана восстановления снимок бесполезен; destruct всё равно стирает; нужен UI («найдены данные от … — восстановить?»).

```kotlin
// MpkDatabase.kt — новый internal-хелпер (перед companion object)
internal fun dbFileUserVersion(context: Context): Int {
    val file = context.getDatabasePath(DATABASE_NAME)
    if (!file.exists()) return 0
    return runCatching {
        android.database.sqlite.SQLiteDatabase
            .openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            .use { it.version }
    }.getOrDefault(0)
}

internal fun snapshotDatabase(context: Context, oldVersion: Int): java.io.File? = runCatching {
    val dir = java.io.File(context.filesDir, "db-snapshots").apply { mkdirs() }
    val stamp = System.currentTimeMillis()
    val dbFile = context.getDatabasePath(DATABASE_NAME)
    val target = java.io.File(dir, "mpk_schedule-v$oldVersion-$stamp.db")
    dbFile.copyTo(target, overwrite = true)
    listOf("-wal", "-shm").forEach { suffix ->
        java.io.File("${dbFile.path}$suffix").takeIf { it.exists() }
            ?.copyTo(java.io.File("${target.path}$suffix"), overwrite = true)
    }
    // держим 2 последних снимка
    (dir.listFiles()?.filter { it.name.endsWith(".db") } ?: emptyList())
        .sortedByDescending { it.lastModified() }
        .drop(2)
        .forEach { it.delete() }
    target
}.getOrNull()
```

```kotlin
// MpkApplication.onCreate — ДО AppGraph.init(this)
val fileVersion = com.politex.data.local.MpkDatabase.dbFileUserVersion(this)
if (fileVersion > 11) {
    com.politex.data.local.MpkDatabase.snapshotDatabase(this, fileVersion)
}
AppGraph.init(this)
```

**Обратимость:** полная (снимок — обычный SQLite-файл, открывается `PRAGMA user_version`).

---

### Вариант B — убрать совсем + тест-гейт

**Было → стало:**

```diff
-                    ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11)
-                    .fallbackToDestructiveMigration()
+                    ).addMigrations(*ALL_MIGRATIONS)   // см. P3-M; тот же приём
                     .build()
```

**Плюсы:** тишина устранена — неизвестный путь даёт `IllegalStateException` с внятным текстом (`Migration didn't properly handle`); destruct-путь исчезает из продукта физически.
**Минусы:** пользователь на «неизвестном» пути получит краш-луп до тех пор, пока не появится обработчик (см. вариант C, блок `catch`). Без него — **регресс UX**: приложение перестаёт открываться вместо тихой потери данных.

**Тест-гейт** (в стиле существующих гейтов проекта, ср. `ManifestFileViewAliasTest`, `GroupCodesIntegrityTest`):

```kotlin
@Test
fun `конфигурация Room не содержит destruct fallback`() {
    val src = java.io.File("src/main/java/com/politex/data/local/MpkDatabase.kt").readText()
    assertTrue(
        "fallbackToDestructiveMigration запрещён: тихо стирает mpk_schedule.db",
        "fallbackToDestructiveMigration" !in src,
    )
}
```
(Внимание: путь к исходнику зависит от рабочей директории теста — в проекте уже есть чтение `app/proguard-rules.pro`/манифеста в тестах, использовать тот же приём разрешения пути.)

---

### Вариант C — гибрид: pre-migration снимок + destruct только для явного списка **(рекомендуется)**

1. Удалить `fallbackToDestructiveMigration()` (как в B) → destruct больше не автоматический.
2. Перед открытием — снимок файла БД (как в A), если `fileVersion > 11` **или** `fileVersion !in 1..11`.
3. Обернуть `AppGraph.init(this)` в `try/catch` `IllegalStateException` → аварийный режим вместо краша:

```kotlin
// MpkApplication.kt:32-37 — было
open class MpkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier
            .createChannel(this)
    }
}
```

```kotlin
// стало
open class MpkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val snapshot = MpkDatabase.guardVersion(this)   // снимок при аномалии, null если всё ок
        try {
            AppGraph.init(this)
        } catch (e: IllegalStateException) {
            // база не читается: показываем экран восстановления, приложение не падает
            AppGraph.markStorageBroken(snapshot)
        }
        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier.createChannel(this)
    }
}
```

**Плюсы:** тишина устранена, данные остаются в снимке, есть путь восстановления, destruct превращается в **осознанное действие пользователя** («сбросить и начать заново»).
**Минусы:** нужен экран/диалог (объём UI — вне домена «Данные», согласовать с UI-агентом); +~60 строк кода.
**Обратимость:** высокая — снимок валиден, можно откатить фичу, оставив `fallbackToDestructiveMigrationFrom(...)` с пустым списком как эквивалент.

**Рекомендация: C.** Если UI-экран не готов к релизу — выпустить **B (гейт) + A (снимок)** как два независимых безопасных шага: они не требуют UI и вместе дают «громко + с копией». `fallbackToDestructiveMigrationFrom()` с пустым списком семантически равен «не удалять никогда», поэтому не нужен — достаточно удалить вызов.
**Тест:** гейт из B + тест `dbFileUserVersion` на подложенном файле с `PRAGMA user_version = 12` (Robolectric, `FrameworkSQLiteOpenHelperFactory` — приём уже используется в `MigrationV1ToV2Test.kt:23-60`).

---

## P1-B. Системный бэкап: точный состав, WAL, что исключить

**Файлы:** `app/src/main/res/xml/data_extraction_rules.xml` (API 31+), `app/src/main/res/xml/backup_rules.xml` (API ≤30).

### Текущее (было) — оба файла, идентично по составу

```xml
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="mpk_widget_prefs.xml" />
        <include domain="database" path="mpk_schedule.db" />
    </cloud-backup>
    <device-transfer>
        <include domain="sharedpref" path="mpk_widget_prefs.xml" />
        <include domain="database" path="mpk_schedule.db" />
    </device-transfer>
</data-extraction-rules>
```

### Полный инвентарь `SharedPreferences` проекта (проверено по коду)

| Файл prefs | Что внутри | Кто пишет | В бэкап? |
|---|---|---|---|
| `mpk_widget_prefs.xml` | выбранная группа, подгруппа, вкл/выкл уведомлений, стиль/тема/область строки звонков, **служебные** `notified_*`, `checked_*` | `WidgetUpdateHelper.kt:21`, `SubgroupSettings.kt:20`, `BellCountdownNotifier.kt:83-151` | **да** (уже) |
| `mpk_ui_prefs.xml` | тема (`AppThemeStore.kt:10`), тёмная тема, **режим преподавателя — имя** (`TeacherModeStore.kt:7-8`), масштаб UI (`UiScaleStore.kt:17`), канал обновлений (`UpdateChannelStore.kt:51`), режим отображения расписания (`ScheduleDisplayPrefs.kt:7`) | 5 стора | **ДОБАВИТЬ** |
| `mpk_subject_renames.xml` | пользовательские названия предметов | `SubjectRenameStore.kt:9` | **ДОБАВИТЬ** |
| `mpk_curators.xml` | кураторы групп | `CuratorStore.kt:9` | **ДОБАВИТЬ** |
| `teachers_favorites.xml` | избранные преподаватели (ФИО) | `TeachersRepository.kt:77` | **ДОБАВИТЬ**¹ |
| `mpk_teacher_profile.xml` | профиль преподавателя (ФИО) + `lastSyncEpoch` | `TeacherScheduleWorker.kt:67` | **ДОБАВИТЬ**¹ |
| `mpk_hints.xml` | одноразовые подсказки | `HintFlags.kt:6` | добавить (косметика, 1 строка) |
| `content_pack_prefs.xml` | версия активного контент-пакета | `content/ContentPackStore.kt` | **НЕ добавлять** (проверяется `isUsablePackDir` при старте, пакет качается заново) |

¹ ФИО преподавателей уже публичны (`teachers.csv` в ассетах) — не ПДн-инцидент; но если заказчик решит «минимум в облако» — эти два файла исключить.

### Стало — `data_extraction_rules.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <!-- настройки -->
        <include domain="sharedpref" path="mpk_widget_prefs.xml" />
        <include domain="sharedpref" path="mpk_ui_prefs.xml" />
        <include domain="sharedpref" path="mpk_subject_renames.xml" />
        <include domain="sharedpref" path="mpk_curators.xml" />
        <include domain="sharedpref" path="mpk_hints.xml" />
        <include domain="sharedpref" path="teachers_favorites.xml" />
        <include domain="sharedpref" path="mpk_teacher_profile.xml" />
        <!-- база: весь домен, чтобы попали -wal/-shm (см. ниже) -->
        <include domain="database" path="." />
        <!-- служебное: НЕ в облако -->
        <exclude domain="database" path="androidx.work.workdb" />
        <exclude domain="database" path="androidx.work.workdb-wal" />
        <exclude domain="database" path="androidx.work.workdb-shm" />
        <exclude domain="database" path="androidx.work.workdb-journal" />
        <exclude domain="sharedpref" path="androidx.work.util.preferences.xml" />
        <exclude domain="sharedpref" path="androidx.work.util.workmanager.xml" />
        <!-- материалы (файлы) в облако не попадают никогда -->
        <exclude domain="file" path="materials" />
    </cloud-backup>
    <device-transfer>
        <!-- тот же набор: device-transfer безопаснее облака, можно и материалы -->
        <include domain="sharedpref" path="mpk_widget_prefs.xml" />
        <include domain="sharedpref" path="mpk_ui_prefs.xml" />
        <include domain="sharedpref" path="mpk_subject_renames.xml" />
        <include domain="sharedpref" path="mpk_curators.xml" />
        <include domain="sharedpref" path="mpk_hints.xml" />
        <include domain="sharedpref" path="teachers_favorites.xml" />
        <include domain="sharedpref" path="mpk_teacher_profile.xml" />
        <include domain="database" path="." />
        <exclude domain="database" path="androidx.work.workdb" />
        <exclude domain="database" path="androidx.work.workdb-wal" />
        <exclude domain="database" path="androidx.work.workdb-shm" />
    </device-transfer>
</data-extraction-rules>
```

`backup_rules.xml` (API ≤30, `full-backup-content`) — тот же набор `include`/`exclude`, тег `<full-backup-content>`, без `cloud-backup`/`device-transfer`. Атрибут `disableIfNoEncryptionCapabilities` на API ≤30 недоступен — там шифрование облака задаётся аккаунтом, ничего не делаем.

### Почему `path="."` вместо перечисления `-wal`/`-shm`

Room по умолчанию использует WAL. Правило `<include domain="database" path="mpk_schedule.db"/>` матчит **ровно один файл**: `mpk_schedule.db-wal` и `mpk_schedule.db-shm` в бэкап не попадают, а значит транзакции, не прошедшие чекпойнт, теряются при восстановлении. Варианты:

1. **`path="."` + явные `exclude` для WorkManager** — рекомендуется: коротко, автоматически подхватит любые будущие файлы БД, а мусор отсекается тремя строками.
2. Перечислить явно `mpk_schedule.db`, `mpk_schedule.db-wal`, `mpk_schedule.db-shm` — точнее, но 6 строк вместо 1 и легко забыть при переименовании.
3. Кастомный `BackupAgent` с `PRAGMA wal_checkpoint(TRUNCATE)` перед снимком — самый корректный, но это новый класс + `android:backupAgent` в манифесте; избыточно, пока хватает варианта 1.

**Материалы:** файлы лежат в `filesDir/materials` (`MaterialImporter.kt:40`, `:74`), домен `file` сейчас не включается вообще — то есть материалы **уже** не в облаке. `exclude` выше — страховка от будущего `<include domain="file" path="."/>`. Размер + содержимое (студенческие конспекты, сканы, PDF с ПДн) — в облако не отправлять.

**Проверка:** Robolectric-тест, парсящий оба XML и сверяющий множество `include` с константами `PREFS` из кода; плюс ручная проверка `adb shell bmgr backupnow com.politex` на устройстве и `adb shell dumpsys backup` (список файлов).

**Обратимость:** полная — правка двух XML.

**Занятость:** файлы только мои (бэкап-правила), но согласовать с владельцем `AndroidManifest.xml`, если добавлять `backupAgent`.

---

## P1-C. `BackupManager` JSON: состав, транзакции, ключ дедупа

**Файл:** `app/src/main/java/com/politex/util/BackupManager.kt` (397 строк) — изолирован, других агентов нет.

### C-1. Новые секции (было → стало)

**Было (`:29-38`):**
```kotlin
    enum class BackupSection {
        SCHEDULE_HISTORY,
        TASKS,
        GRADES,
        SUBJECT_NAMES,
        SETTINGS,
        ;

        companion object {
            val ALL: Set<BackupSection> = entries.toSet()
        }
    }
```

**Стало:**
```kotlin
    enum class BackupSection {
        SCHEDULE_HISTORY,
        TASKS,
        GRADES,
        GRADE_CONFIG,        // priorityJson/weightsJson/roundingMode/roundUp/weightsEnabled
        TEACHER_OVERRIDES,   // teacher_overrides
        MATERIALS_META,      // bookmarks + annotations + lastPage/rotationDeg
        SUBJECT_NAMES,
        SETTINGS,
        ;

        companion object {
            val ALL: Set<BackupSection> = entries.toSet()
        }
    }
```

`FORMAT_VERSION` 2 → **3** (`:17`). Чтение старых файлов не ломается: `version` читается как `optInt`, секции ищутся через `has()`/`optJSONArray`.

### C-2. Экспорт `grade_config` (вставить после блока GRADES, `:113`)

Новые DAO-метода нет — `GradeDao.allConfigs(): Flow<List<GradeConfigEntity>>` уже есть.

```kotlin
        if (BackupSection.GRADE_CONFIG in sections) {
            val configsArray = JSONArray()
            db.gradeDao().allConfigs().first().forEach { cfg ->
                configsArray.put(
                    JSONObject()
                        .put("subjectName", cfg.subjectName)
                        .put("priorityJson", cfg.priorityJson)
                        .put("weightsJson", cfg.weightsJson)
                        .put("roundingMode", cfg.roundingMode)
                        .put("roundUp", cfg.roundUp)
                        .put("weightsEnabled", cfg.weightsEnabled),
                )
            }
            root.put(KEY_GRADE_CONFIGS, configsArray)
        }
```

### C-3. Экспорт `teacher_overrides` (после C-2)

```kotlin
        if (BackupSection.TEACHER_OVERRIDES in sections) {
            val overridesArray = JSONArray()
            db.teacherOverrideDao().all().forEach { o ->
                overridesArray.put(
                    JSONObject()
                        .put("dateString", o.dateString)
                        .put("lessonNumber", o.lessonNumber)
                        .put("kind", o.kind)
                        .put("room", o.room)
                        .put("note", o.note)
                        .put("createdAt", o.createdAt),
                )
            }
            root.put(KEY_TEACHER_OVERRIDES, overridesArray)
        }
```

### C-4. Экспорт `materials_meta` — нужны 2 bulk-запроса в `MaterialDao`

Сейчас есть только по-элементные `getBookmarksForItem` / `getAnnotationsFlow` → N+1 на экспорте. Добавить:

```kotlin
    @Query("SELECT * FROM material_bookmarks ORDER BY itemId ASC, page ASC")
    suspend fun allBookmarks(): List<MaterialBookmarkEntity>

    @Query("SELECT * FROM material_annotations ORDER BY itemId ASC, pageIndex ASC")
    suspend fun allAnnotations(): List<MaterialAnnotationEntity>
```

Экспорт:

```kotlin
        if (BackupSection.MATERIALS_META in sections) {
            val items = db.materialDao().getAllItems().first().map { item ->
                JSONObject()
                    .put("id", item.id)
                    .put("lastPage", item.lastPage)
                    .put("rotationDeg", item.rotationDeg)
                    .put("subjectName", item.subjectName)
                    .put("archived", item.archived)
            }
            val bookmarks = JSONArray().also { arr ->
                db.materialDao().allBookmarks().forEach { b ->
                    arr.put(JSONObject().put("itemId", b.itemId).put("page", b.page).put("label", b.label))
                }
            }
            val annotations = JSONArray().also { arr ->
                db.materialDao().allAnnotations().forEach { a ->
                    arr.put(
                        JSONObject().put("itemId", a.itemId).put("pageIndex", a.pageIndex)
                            .put("strokesJson", a.strokesJson).put("updatedAt", a.updatedAt),
                    )
                }
            }
            root.put(KEY_MATERIALS, JSONObject().put("items", JSONArray(items)).put("bookmarks", bookmarks).put("annotations", annotations))
        }
```

**Ограничение, которое надо честно зафиксировать в UI/KDoc:** аннотации и bookmarks привязаны к `itemId` локальных материалов. На новом устройстве материалов нет → импорт этих строк **пропускается с предупреждением** (сначала пользователь переносит материалы отдельно). Экспорт без этого бессмыслен, но и вреден только размером; крупные `strokesJson` могут раздуть файл — при >2 МБ добавлять предупреждение.

### C-5. Транзакционный restore (было → стало)

**Было (`:161-165`, `:218-224`, `:361-375`):** все вставки — отдельными DAO-вызовами, `try/catch` вокруг всего.
**Стало:** обернуть всё, что пишет в БД, в `db.withTransaction`:

```kotlin
// import
+import androidx.room.withTransaction
```

```diff
     suspend fun restoreBackup(
         context: Context,
         json: String,
     ): Result<ImportResult> = try {
         val root = JSONObject(json)
         val warnings = mutableListOf<String>()
@@
         val db = MpkDatabase.getInstance(context)

+        // prefs — вне транзакции (идемпотентны), БД — одной транзакцией
         val settings = root.optJSONObject(KEY_SETTINGS)
         var groupRestored: String? = null
         var themeRestored = false
```

```diff
-        val dao = db.taskDao()
-        val reminderDao = db.taskReminderDao()
+        var restored = 0
+        var remindersRestored = 0
+        var gradesRestored = 0
+        var lessonsRestored = 0
+        var gradeConfigsRestored = 0
+        var overridesRestored = 0
+        var materialsMetaRestored = 0
+
+        db.withTransaction {
+        val dao = db.taskDao()
+        val reminderDao = db.taskReminderDao()
         val array = root.optJSONArray(KEY_TASKS)
-        var restored = 0
-        var remindersRestored = 0
```

…и закрыть блок перед `Result.success(...)`:

```diff
         val renamesObj = root.optJSONObject(KEY_SUBJECT_RENAMES)
         var subjectRenamesRestored = 0
         if (renamesObj != null) {
             val keys = renamesObj.keys()
             while (keys.hasNext()) {
                 val original = keys.next()
                 val custom = renamesObj.optString(original, "").trim()
                 if (original.isNotBlank() && custom.isNotBlank()) {
                     SubjectRenameStore.setCustomName(context, original, custom)
                     subjectRenamesRestored++
                 }
             }
         }
+        }  // конец db.withTransaction
```

Важно: `SubjectRenameStore.*` пишет в SharedPreferences — оставить **вне** транзакции (иначе рестарт-семантика БД и prefs разъедется при падении; prefs идемпотентны, повторный импорт безвреден). Счётчики объявлены снаружи лямбды. `lessonsRestored`/`gradesRestored` сейчас объявлены внутри блоков (`:250`, `:281`, `:302`) — поднять наверх.

### C-6. Ключ дедупа без схлопывания разных заданий

**Было (`:378`):**
```kotlin
private fun StudentTaskEntity.dedupeKey(): String = listOf(groupName, subjectName, title, deadlineDate).joinToString("|").lowercase()
```

**Стало:**
```kotlin
private fun StudentTaskEntity.dedupeKey(): String =
    listOf(groupName, subjectName, title, deadlineDate, taskType, createdAt).joinToString("|").lowercase()
```

Плюс два независимых изменения в `restoreBackup`:

1. **`existing` — снимок БД, не мутируем в процессе импорта.** Было (`:251`): `if (!existing.add(entity.dedupeKey())) { skipped++; continue }` — из-за мутации два разных задания из одного файла с одинаковым ключом схлопываются. Стало:

```diff
-            val existing =
-                dao
-                    .getAllTasks()
-                    .map { it.dedupeKey() }
-                    .toHashSet()
+            val existingFromDb =
+                dao
+                    .getAllTasks()
+                    .map { it.dedupeKey() }
+                    .toHashSet()
```
```diff
-                if (!existing.add(entity.dedupeKey())) {
+                if (entity.dedupeKey() in existingFromDb) {
                     skipped++
                     continue
                 }
```
(То же для оценок `:296` и уроков `:339` — там мутация `existing.add` внутри `filter` тоже теряет внутрифайловые дубли; для уроков ключ полный, риск низкий, но правило единое.)

2. **`id` в JSON + обновление состояния существующего задания.** `taskToJson` (`:134-144`) — добавить `put("id", task.id)`. При импорте, если ключ совпал с существующим — **обновить** `isCompleted`/`title`/`description`/`taskType`, а не просто пропустить:

```kotlin
                val matched = dao.getAllTasks().firstOrNull { it.dedupeKey() == entity.dedupeKey() }
                if (matched != null) {
                    if (matched.isCompleted != entity.isCompleted ||
                        matched.title != entity.title ||
                        matched.description != entity.description
                    ) {
                        dao.updateTask(
                            matched.copy(
                                isCompleted = entity.isCompleted,
                                title = entity.title,
                                description = entity.description,
                                taskType = entity.taskType,
                            ),
                        )
                    }
                    skipped++
                    continue
                }
```
(Для производительности — вынести `getAllTasks()` один раз до цикла и держать `Map<String, StudentTaskEntity>`.)

**Риск пункта C:** низкий — меняется только формат файла (аддитивно) и порядок записи. **Обратимость:** файлы `FORMAT_VERSION = 3` читаются старым кодом частично (он игнорирует неизвестные секции) → откат безопасен.
**Проверка (тесты):**
- экспорт/импорт `grade_config` (веса 25/25/50/100 переживают круг);
- экспорт/импорт `teacher_overrides` (правка «кабинет 305» возвращается);
- **два задания с одинаковым `title`+`deadline` оба восстанавливаются** (главный тест на регресс);
- `isCompleted = true` из бэкапа применяется к существующему заданию;
- инъекция исключения на середине импорта → БД в исходном состоянии (атомарность);
- файл `version = 2` импортируется без ошибок (обратная совместимость).

---

## P2-1. Индексы `student_tasks` + миграция 11→12

Без индексов: `SCAN student_tasks` + temp B-tree на `ORDER BY` в трёх экранных запросах (`TaskDao.kt:12-25`) и в каждом обновлении виджета.

### 1) Entity — было (`StudentTaskEntity.kt:9-21`)

```kotlin
@Entity(tableName = "student_tasks")
data class StudentTaskEntity(
```

**Стало:**
```kotlin
@Entity(
    tableName = "student_tasks",
    indices = [
        Index("groupName", "isCompleted", "deadlineDate"),
        Index("groupName", "subjectName"),
    ],
)
data class StudentTaskEntity(
```
(+ `import androidx.room.Index`.)

### 2) Миграция 11→12 — было (`MpkDatabase.kt:44`, `:270-275`, `:285`)

```kotlin
    version = 11,
...
        private val migration10To11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `contentHash` TEXT NOT NULL DEFAULT ''")
                }
            }
...
                    ).addMigrations(migration1To2, ..., migration10To11)
```

**Стало:**
```kotlin
    version = 12,
...
        private val migration11To12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_student_tasks_groupName_isCompleted_deadlineDate` " +
                            "ON `student_tasks` (`groupName`, `isCompleted`, `deadlineDate`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_student_tasks_groupName_subjectName` " +
                            "ON `student_tasks` (`groupName`, `subjectName`)",
                    )
                }
            }
...
                    ).addMigrations(..., migration10To11, migration11To12)
```
Имена индексов — строго по конвенции Room `<index>_<table>_<col>_<col>`; иначе валидация схемы при открытии упадёт.

### 3) (опционально, но рекомендуется в той же миграции) `deadlineEpoch`

`deadlineDate` — TEXT `dd.MM.yyyy`; лексикографическая сортировка ≠ календарная между годами. Один раз исправить в этой же миграции:

```kotlin
                    db.execSQL("ALTER TABLE `student_tasks` ADD COLUMN `deadlineEpoch` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL(
                        """
                        UPDATE `student_tasks` SET `deadlineEpoch` =
                          CAST(strftime('%s', substr(`deadlineDate`,7,4)||'-'||substr(`deadlineDate`,4,2)||'-'||substr(`deadlineDate`,1,2)||'T00:00:00Z') AS INTEGER) * 1000
                        WHERE `deadlineDate` != ''
                        """.trimIndent(),
                    )
```
+ поле в entity `@ColumnInfo(defaultValue = "0") val deadlineEpoch: Long = 0` и правка `ORDER BY deadlineDate ASC` → `ORDER BY deadlineEpoch ASC` в `TaskDao`. **Плюс:** сортировка становится корректной и индекс используется. **Минус:** +3 точки правки в UI-слое (форматирование даты) — согласовать.

**Риск:** средний (первая смена `version` после 11; забыть добавить миграцию в `addMigrations` = `IllegalStateException` у всех при открытии). Именно поэтому P1-A должен быть решён **до** или **вместе** с этим пунктом.
**Обратимость:** схема аддитивная, индексы удаляемы; откат = миграция 12→11 (не нужна на практике).
**Проверка:** `MigrationV11ToV12Test` (Robolectric, приём из `MigrationV10ToV11Test.kt`): создать v11-БД, вставить 2 задания, открыть `MpkDatabase.getInstance()`, проверить `PRAGMA index_list(student_tasks)` + что задания на месте + `deadlineEpoch` заполнен. Плюс тест плана запроса:
```kotlin
db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM student_tasks WHERE groupName = '41О' AND isCompleted = 0 ORDER BY deadlineDate").use { c ->
    while (c.moveToNext()) assertFalse(c.getString(3).contains("SCAN"))
}
```

---

## P2-2. Прунинг `teacher_lessons`

**Проблема:** `pruneOldLessons` берёт `staleDates` из `lessons` и тем же списком чистит `teacher_lessons` (`ScheduleRepository.kt:658-664`) → даты, где есть только расписание преподавателей, не чистятся никогда.

**Было (`TeacherLessonDao.kt:40-46` + `ScheduleRepository.kt:658-664`):**
```kotlin
    @Query(
        """
        SELECT DISTINCT dateString FROM teacher_lessons
        WHERE dateString != '' AND dateString IN (:dates)
        """,
    )
    suspend fun getPresentDates(dates: List<String>): List<String>
```
```kotlin
    suspend fun pruneOldLessons(keepDays: Long = ARCHIVE_KEEP_DAYS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L
        val staleDates = lessonDao.getAllDistinctDatesSync().filter { isStaleDate(it, cutoff) }
        if (staleDates.isEmpty()) return@withContext
        lessonDao.deleteLessonsByDates(staleDates)
        teacherLessonDao?.deleteForDates(staleDates)
    }
```

**Стало — DAO:**
```kotlin
    @Query("SELECT DISTINCT dateString FROM teacher_lessons WHERE dateString != ''")
    suspend fun getAllDistinctDates(): List<String>
```

**Стало — репозиторий:**
```kotlin
    suspend fun pruneOldLessons(keepDays: Long = ARCHIVE_KEEP_DAYS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L

        val staleLessonDates = lessonDao.getAllDistinctDatesSync().filter { isStaleDate(it, cutoff) }
        if (staleLessonDates.isNotEmpty()) lessonDao.deleteLessonsByDates(staleLessonDates)

        val teacherDao = teacherLessonDao ?: return@withContext
        val staleTeacherDates = teacherDao.getAllDistinctDates().filter { isStaleDate(it, cutoff) }
        if (staleTeacherDates.isNotEmpty()) teacherDao.deleteForDates(staleTeacherDates)
    }
```

Дополнительно: вызвать `pruneOldLessons()` в конце `TeacherScheduleWorker.doWork()` (после успешного синка) — иначе у пользователя, который открывает только виджеты преподавателя, прунинг вообще не запускается.

**Риск:** низкий — удаляются только даты старше 60 дней, критерий тот же, что для уроков.
**Обратимость:** полная (данные и так уже за пределами TTL; пересинк восстановит).
**Проверка:** Robolectric-тест `TeacherScheduleSyncChainTest`-стиля: вставить `teacher_lessons` на дату 2024-01-01 и `lessons` без этой даты → после `pruneOldLessons()` строки преподавателей удалены; вставить на вчерашнюю дату → не удалены.

---

## P2-3. Защита от затирания дня неполным разбором

**Где:** `ScheduleRepository.kt:668-679` (`replaceSyncedLessons`), внутри `LessonDao.replaceForDate` — `delete + insert` без сравнения объёмов.

**Было:**
```kotlin
    internal fun formatDate(calendar: Calendar): String = DateFormats.dotDate(calendar)

    suspend fun replaceSyncedLessons(
        groupName: String,
        lessons: List<LessonEntity>,
    ) = withContext(Dispatchers.IO) {
        lessons.groupBy { it.dateString }.forEach { (dateString, dayLessons) ->
            if (dateString.isBlank()) {
                lessonDao.insertLessons(dayLessons)
            } else {
                lessonDao.replaceForDate(groupName, dateString, dayLessons)
            }
        }
    }
```

**Стало** — чистый предикат рядом с остальными (`:59-73`), чтобы тестировался без БД:
```kotlin
internal fun isDayRegression(
    oldCount: Int,
    newCount: Int,
): Boolean = oldCount >= 3 && (newCount == 0 || newCount * 2 < oldCount)
```
```kotlin
    suspend fun replaceSyncedLessons(
        groupName: String,
        lessons: List<LessonEntity>,
    ) = withContext(Dispatchers.IO) {
        lessons.groupBy { it.dateString }.forEach { (dateString, dayLessons) ->
            if (dateString.isBlank()) {
                lessonDao.insertLessons(dayLessons)
                return@forEach
            }
            val oldCount = lessonDao.getLessonCountForDate(groupName, dateString)
            if (isDayRegression(oldCount, dayLessons.size)) {
                // не затираем: подозрение на неполный разбор документа
                android.util.Log.w(
                    "ScheduleRepository",
                    "day regression skipped: group=$groupName date=$dateString old=$oldCount new=${dayLessons.size}",
                )
                return@forEach
            }
            lessonDao.replaceForDate(groupName, dateString, dayLessons)
        }
    }
```

Порог: блокируем, когда новый набор **меньше половины** старого (6→2 блок, 6→3 пропуск, 3→2 пропуск). Аналог уже принятой в проекте логики для преподавателей («пустой/мусорный разбор не стирает кэш», `TeacherScheduleSyncChainTest`).

Вариант «правильнее, но дороже»: мерж по `lessonNumber` — обновлять совпавшие слоты, удалять только номера, которых в новом документе нет **и** которые не «дырка» (в документе есть пары с бо́льшим номером). Это то, что делает `MpkScheduleParser` при повторной публикации; вводить в репозиторий — отдельная задача.

**Риск:** низкий. Обратная сторона: реальное сокращение пар (например, сессия) не применится, пока разница <2×; для таких дней есть ручное «обновить принудительно» — при `force` предикат обходить (`if (force)` не пробрасывается в `replaceSyncedLessons` — передать параметр `allowShrink: Boolean = false`).
**Обратимость:** полная (один предикат).
**Проверка:** юнит-тесты на `isDayRegression` (6/3 → false, 6/2 → true, 3/3 → false, 5/0 → true, 2/1 → false) + Robolectric: положить 6 уроков, вызвать `replaceSyncedLessons` с 2 → в БД осталось 6.

---

## P2-4. `runBlocking` в виджетах

### P2-4a — прогрев БД в фоне (безопасно, не трогает виджеты)

**Проблема:** `Room.databaseBuilder(...).build()` файл не открывает; первое открытие + миграции 1→11 случаются внутри `runBlocking` в приёмнике виджета (`NowNextWidgetProvider.kt:268`, `BellCountdownWidgetProvider.kt:220`, `TeacherWidgetData.kt:100`), т.е. на main thread после перезагрузки.

**Было (`MpkApplication.kt:32-41`):** `AppGraph.init(this)` в `onCreate` — создаёт объект БД, но не открывает её.
**Стало:**
```kotlin
open class MpkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // прогрев: миграции уходят с main thread приёмников виджетов
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
            .launch { MpkDatabase.getInstance(this@MpkApplication).openHelper.writableDatabase }
        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier.createChannel(this)
    }
}
```
Файл `MpkApplication.kt` общий с P1-A → делать последовательно.

### P2-4b — `goAsync()` вместо `runBlocking` (чужая зона, только по согласованию)

**Файлы:** `widget/NowNextWidgetProvider.kt:259-282`, `widget/BellCountdownWidgetProvider.kt:208-234`, `widget/TeacherWidgetData.kt:98-101`.
**Паттерн:**
```diff
-    fun todayLessons(context: Context): List<LessonEntity> {
+    suspend fun todayLessons(context: Context): List<LessonEntity> {
         ...
-            try {
-                kotlinx.coroutines.runBlocking {
-                    val all = MpkDatabase.getInstance(context).lessonDao().getLessonsForDaySync(group, dow)
-                    filterTodayLessons(all, todayString)
-                }
-            } catch (_: Exception) { emptyList() }
+            try {
+                val all = MpkDatabase.getInstance(context).lessonDao().getLessonsForDaySync(group, dow)
+                filterTodayLessons(all, todayString)
+            } catch (_: Exception) { emptyList() }
```
и в провайдере:
```kotlin
override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try { rebuild(context, manager, ids) } finally { pending.finish() }
    }
}
```
**Риск:** средний (переписывание трёх провайдеров, риск регресса вида виджетов). **Рекомендация:** выпустить P2-4a сейчас; P2-4b — отдельной задачей владельцу виджетов, после P2-4a (прогрев уже убирает ANR-риск миграции, остаётся только чтение из открытой БД ~1-5 мс).
**Проверка:** для 4a — Robolectric `MpkApplication.onCreate` → `db.isOpen == true` через `runBlocking { }` в тесте; для 4b — существующие `WidgetRegistrationTest`/`WidgetLayoutsTest` не должны упасть, плюс ручной чек виджета после перезагрузки.

---

## P2-5. `teacher_overrides` не привязаны к преподавателю

Сейчас ключ правки — `(dateString, lessonNumber)` (`TeacherOverrideEntity.kt`), при смене преподавателя правки первого применяются ко второму.

### Вариант A (рекомендуется сейчас, без миграции)

Добавить в `TeacherOverrideDao`:
```kotlin
    @Query("DELETE FROM teacher_overrides")
    suspend fun clearAll()
```
и в `util/TeacherModeStore.kt` — при смене/очистке профиля:
```diff
     fun setTeacher(context: Context, name: String) {
         val trimmed = name.trim()
         context.applicationContext
             .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
             .edit()
             .putString(KEY_NAME, trimmed)
             .apply()
+        // правки расписания принадлежат предыдущему преподавателю
+        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
+            .launch { MpkDatabase.getInstance(context).teacherOverrideDao().clearAll() }
     }
```
(и то же в `clear(context)`).
**Плюсы:** 5 строк, без схемы, без миграции. **Минусы:** при смене туда-обратно правки не вернутся; user-visible поведение нужно озвучить в диалоге смены профиля.

### Вариант B (правильно, но дороже): `teacherSurnameKey` в ключе

```kotlin
@Entity(
    tableName = "teacher_overrides",
    indices = [Index(value = ["teacherSurnameKey", "dateString", "lessonNumber"], unique = true)],
)
```
+ миграция: создать новую таблицу, перенести строки с `teacherSurnameKey = ''`, переименовать (SQLite не умеет менять индекс на месте). Делать только если на одном устройстве реально больше одного преподавателя. **Сейчас рекомендую A**, B — в бэклог следующего релиза.
**Проверка:** Robolectric-тест «правка от преподавателя X не видна после `setTeacher(Y)`».

---

## P3 — мелкие, патч-готовые

### P3-1. `TaskType` хранится как русская строка с fallback в HOMEWORK
**Где:** `data/model/TaskType.kt:16-20`, поле `StudentTaskEntity.taskType`.
**Было:**
```kotlin
        fun fromDisplayName(name: String): TaskType = entries.firstOrNull {
            it.displayName.equals(name, ignoreCase = true) ||
                it.badge.equals(name, ignoreCase = true) ||
                it.name.equals(name, ignoreCase = true)
        } ?: HOMEWORK
```
**Стало:** читать `name()` с явным legacy-фолбэком по displayName (без тихого `HOMEWORK`):
```kotlin
        fun fromStored(value: String): TaskType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) }
                    ?.also { android.util.Log.w("TaskType", "legacy displayName stored: $value") }

        fun fromDisplayName(name: String): TaskType = fromStored(name) ?: HOMEWORK
```
**Миграция 11→12** (в той же, что P2-1):
```sql
UPDATE `student_tasks` SET `taskType` = 'HOMEWORK'        WHERE `taskType` = 'Домашнее задание';
UPDATE `student_tasks` SET `taskType` = 'LAB_WORK'        WHERE `taskType` = 'Лабораторная работа';
UPDATE `student_tasks` SET `taskType` = 'PRACTICAL_WORK'  WHERE `taskType` = 'Практическая работа';
UPDATE `student_tasks` SET `taskType` = 'CONTROL_WORK'    WHERE `taskType` = 'Контрольная работа';
UPDATE `student_tasks` SET `taskType` = 'COURSE_WORK'     WHERE `taskType` = 'Курсовая работа';
UPDATE `student_tasks` SET `taskType` = 'DIPLOMA'         WHERE `taskType` = 'Дипломное проектирование';
```
Значения взяты из `TaskType.kt:7-12` дословно. **Риск:** низкий; забыть про бэкап-файлы старого формата (там `taskType` = displayName) — поэтому `fromStored` с legacy-ветвью обязателен.

### P3-2. `MaterialImporter.rollback` оставляет сиротские аннотации
**Где:** `util/MaterialImporter.kt:510-520`.
**Было:**
```kotlin
    private suspend fun rollback(
        dao: MaterialDao,
        itemId: Long,
        uriPath: String,
    ) {
        runCatching {
            uriPath.split('\n').forEach { if (it.isNotBlank()) File(it).delete() }
            dao.deleteBookmarksForItem(itemId)
            dao.deleteItemById(itemId)
        }
    }
```
**Стало:**
```kotlin
    private suspend fun rollback(
        dao: MaterialDao,
        itemId: Long,
        uriPath: String,
    ) {
        runCatching {
            uriPath.split('\n').forEach { if (it.isNotBlank()) File(it).delete() }
            dao.deleteItemWithBookmarks(itemId)   // bookmarks + annotations + item, уже @Transaction
        }
    }
```
**Проверка:** тест «после отката нет строк в `material_annotations` с этим `itemId`».

### P3-3. Нет внешних ключей/каскадов
Не вводить `@ForeignKey` (потребует пересоздания 3 таблиц). Вместо этого — «сборка мусора» в уже существующем периодическом воркере `ReminderCheckWorker`:
```kotlin
    @Query("DELETE FROM task_reminders WHERE taskId NOT IN (SELECT id FROM student_tasks)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM material_annotations WHERE itemId NOT IN (SELECT id FROM material_items)")
    suspend fun deleteOrphans()
```
Плюс удалить мёртвые API-ловушки: `TaskDao.clearTasksForGroup` (`TaskDao.kt:53`, вызовов нет) и `ScheduleRepository.replaceSchedule`/`clearSchedule` (`:685-694`, вызовов нет) — либо пометить `@Deprecated` с пояснением в KDoc.
**Проверка:** тест «после `deleteOrphans()` количество строк уменьшилось только для сирот».

### P3-4. `cachedCatalog` без `@Volatile`
**Где:** `ScheduleRepository.kt:242`.
```diff
-    private var cachedCatalog: List<String>? = null
+    @Volatile private var cachedCatalog: List<String>? = null
```
Рядом `incompleteDayCache` уже `ConcurrentHashMap` (`:244`) — приводим к тому же уровню.

### P3-5. `incompleteDayCache` без TTL
**Где:** `ScheduleRepository.kt:244-257`, ограничение сети на `:385`. Дата, помеченная «неполная», не перезапрашивается до перезапуска процесса.
**Стало:** хранить время метки и считать неполноту протухшей через 30 минут:
```kotlin
    private val incompleteDayCache = ConcurrentHashMap<String, Long>()

    private const val INCOMPLETE_TTL_MS = 30L * 60L * 1000L

    internal fun isKnownIncompleteDay(date: String): Boolean =
        incompleteDayCache[date]?.let { System.currentTimeMillis() - it < INCOMPLETE_TTL_MS } == true

    internal fun rememberDayCompleteness(date: String, parsedGroups: Int, catalogSize: Int) {
        when (incompleteDayMark(parsedGroups, catalogSize)) {
            true -> incompleteDayCache[date] = System.currentTimeMillis()
            false -> incompleteDayCache.remove(date)
            null -> Unit
        }
    }
```
(правки в двух местах: `isKnownIncompleteDay` и `rememberDayCompleteness`; вызов `:385` продолжает работать без изменений.)

### P3-6. Сортировка дат через `substr` в SQL
**Где:** `LessonDao.getDistinctDatesForGroup` — `ORDER BY substr(dateString,7,4) DESC, …` не использует индекс. Закрывается вводом `dateEpoch` в миграции 11→12 (аналогично `deadlineEpoch`), запрос становится `ORDER BY dateEpoch DESC`. Если `dateEpoch` в этом релизе не вводим — оставить как есть (объём на группу — сотни строк).

### P3-7. `runBlocking`/`IF NOT EXISTS`/стиль — правила, не код
- `runBlocking` в `widget/**` и `**Provider*` — запрещён (тест-гейт по образцу P1-A).
- В новых миграциях — без `CREATE TABLE IF NOT EXISTS`; существующие не переписывать (историческая миграция должна остаться неизменной — она уже отработала у пользователей).

### P3-8. `GradeDao` / `MaterialDao` — индексы на фильтры
Опционально в той же миграции 11→12: `Index("subjectName")` на `grade_entries`, `Index("subjectName", "archived")` на `material_items`, `Index("contentHash")` на `material_items`. Объёмы малы (сотни строк), выигрыш мизерный — включать только если миграция и так делается.

---

## P3-M. Схемы `1.json`–`3.json` и `MigrationTestHelper`

**Что не так:** `app/schemas/com.politex.data.local.MpkDatabase/` содержит только `4.json`…`11.json`. Тесты `MigrationV1ToV2Test.kt:23-60` и `MigrationV2ToV3Test.kt:23-60` создают старую БД **сырым SQL, написанным от руки**, т.е. проверяют миграцию против собственного предположения о vintage-схеме. `MigrationV2ToV3Test` — единственный без сквозного `MpkDatabase.getInstance()`, поэтому цепочка 2→11 целиком не проверяется.

### Шаг 1. Восстановить `1.json`, `2.json`, `3.json`

Не выдумывать вручную — реконструировать из git (схема в Room-формате однозначно выводится из `@Entity`-аннотаций того коммита):

```bash
# найти коммит, где схемы появились (v4), и версии до него
git log --diff-filter=A --oneline -- app/schemas
git log --oneline -- app/src/main/java/com/politex/data/local/MpkDatabase.kt
# взять деревья «до» каждого шага и сгенерировать схему сборкой на том коммите:
git worktree add ../mpk-v1 <commit-before-v2>
cd ../mpk-v1/MPK-app-2.1 && sh ./gradlew :app:kspStandardDebugKotlin   # даст app/schemas/.../1.json
```
**Важно:** это единственный шаг бэклога, который требует запуска gradle — делать силами Лида (гейты у него), не в этой сессии. Если worktree недоступен — сгенерировать JSON вручную по `@Entity` того коммита (формат детерминированный: `formatVersion`, `database`, `entities[].fields[]/indices[]`, `views`, `setupQueries`).

### Шаг 2. Вынести миграции наружу + `room-testing`

**Было (`MpkDatabase.kt:66`, `:285`):** `private val migration1To2 = …`, регистрация перечислением.
**Стало:**
```kotlin
    companion object {
        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            migration1To2, migration2To3, migration3To4, migration4To5, migration5To6,
            migration6To7, migration7To8, migration8To9, migration9To10, migration10To11,
            migration11To12,
        )
        ...
                    ).addMigrations(*ALL_MIGRATIONS)
```
(каждая `migrationNToN+1` — `internal` вместо `private`).

`gradle/libs.versions.toml`:
```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "roomRuntime" }
```
`app/build.gradle.kts`:
```kotlin
    testImplementation(libs.androidx.room.testing)
```

**Обязательно (иначе `runMigrationsAndValidate` упадёт с «Cannot find schema file»):** `MigrationTestHelper` читает JSON-схемы из **assets**, а не из `app/schemas`. Для Robolectric-юнит-тестов нужно пробросить `schemas` в assets тестового source set:

```kotlin
android {
    sourceSets {
        getByName("test") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}
```
После этого схемы доступны как `assets/com.politex.data.local.MpkDatabase/11.json` — без копирования файлов.

### Шаг 3. Параметризованный тест «любая версия → 11/12»

```kotlin
@RunWith(RobolectricTestRunner::class)
class MigrationChainTest {
    // ВНИМАНИЕ: у MigrationTestHelper в Room 2.7 несколько перегрузок конструктора
    // (с AutoMigrationSpec и/или SupportSQLiteOpenHelper.Factory) — подобрать по факту,
    // сигнатура с (Instrumentation, Class<out RoomDatabase>) может быть помечена deprecated.
    private val helper by lazy {
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MpkDatabase::class.java,
        )
    }

    private fun countOf(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }

    @Test
    fun `каждая версия 1_11 мигрирует до текущей без потери заданий`() {
        for (from in 1..MpkDatabase.ALL_MIGRATIONS.last().endVersion) {
            helper.createDatabase(TEST_DB, from).apply {
                execSQL(
                    "INSERT INTO student_tasks (groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                        "VALUES ('41О', 4, 'ТОЭ', 'HOMEWORK', 'Проба', '', 0, '', 1)",
                )
                close()
            }
            val db = helper.runMigrationsAndValidate(TEST_DB, CURRENT_VERSION, true, *MpkDatabase.ALL_MIGRATIONS)
            assertEquals("v$from → current: задание потеряно", 1, countOf(db, "student_tasks"))
            db.close()
        }
    }
}
```
`runMigrationsAndValidate` — тот самый шаг, которого сейчас нет: он валидирует **схему после каждого шага** и падает с указанием конкретной миграции, а не с общим `IllegalStateException` при открытии.

**Что даёт:** (а) тесты 1→2 и 2→3 перестают быть самопроверкой; (б) один тест закрывает все 11 стартовых версий вместо 10 рукописных схем; (в) сообщение об ошибке указывает на конкретный шаг; (г) готовая инфраструктура для будущих v12+.
**Риск:** средний (тестовая инфраструктура + генерация схем требует gradle). **Обратимость:** полная — старые тесты остаются.
**Занятость:** `MpkDatabase.kt` (visibility `private`→`internal`) — последовательно с P1-A и P2-1.

---

## Порядок применения

### Волна 0 — решения заказчика (блокируют P1-A и P1-B)
Ничего не кодим, пока не выбраны: вариант destruct-fallback (A/B/C) и состав облачного бэкапа. Всё остальное от них не зависит и может идти параллельно.

### Волна 1 — независимые файлы, можно всё сразу (4 задачи параллельно)
| # | Пункт | Файлы |
|---|---|---|
| 1 | **P1-C** `BackupManager`: секции, транзакция, дедуп | `util/BackupManager.kt`, 2 bulk-запроса в `MaterialDao.kt` |
| 2 | **P1-B** правила бэкапа | 2 XML |
| 3 | **P2-2** прунинг `teacher_lessons` (DAO + репозиторий) | `TeacherLessonDao.kt` + `ScheduleRepository.kt` |
| 4 | **P3-2** каскад отката импорта материалов | `util/MaterialImporter.kt` |

Почему именно эти: разные файлы, нет пересечений между собой и с волной 2.

### Волна 2 — `MpkDatabase.kt` (строго последовательно, один автор)
| # | Пункт | Зависимость |
|---|---|---|
| 5 | **P1-A** destruct-fallback (выбранный вариант) | решение заказчика |
| 6 | **P2-1** миграция 11→12: индексы + `deadlineEpoch` + `taskType` (P3-1) + опц. P3-8 | после 5 — оба трогают `addMigrations`/`version` |
| 7 | **P3-M** вынести `ALL_MIGRATIONS` в `internal` + `room-testing` + `MigrationChainTest` | после 6 (нужен финальный набор миграций) |
| 8 | **P1-A часть 2** прогрев БД в `MpkApplication.kt` (P2-4a) | после 5 (тот же файл) |

### Волна 3 — `ScheduleRepository.kt` (последовательно)
| # | Пункт |
|---|---|
| 9 | **P2-3** защита от неполного дня |
| 10 | **P3-4** `@Volatile` `cachedCatalog` + **P3-5** TTL `incompleteDayCache` (один патч) |

### Волна 4 — изолированные остатки, параллельно
| # | Пункт | Файлы |
|---|---|---|
| 11 | **P2-5A** очистка overrides при смене преподавателя | `TeacherOverrideDao.kt`, `TeacherModeStore.kt` |
| 12 | **P3-3** GC сирот + удаление мёртвых API | `TaskReminderDao.kt`/`MaterialDao.kt`, `ReminderCheckWorker.kt`, `TaskDao.kt`, `ScheduleRepository.kt` (после волны 3) |
| 13 | **P2-4b** `goAsync` в виджетах | `widget/**` — **только владельцем зоны виджетов**, после P2-4a |

### Проверка после каждой волны
`:app:testStandardDebugUnitTest` (гейт Лида) + `MigrationChainTest` после волны 2. Ручные проверки: `adb shell bmgr backupnow com.politex` (волна 1, п.2), восстановление бэкапа на чистом устройстве (волна 1, п.1), `EXPLAIN QUERY PLAN` для `student_tasks` (волна 2).

---

## Что требует решения заказчика

1. **Destruct-fallback — вариант A / B / C.** Рекомендация: **C** (снимок + запрет + аварийный экран). Без UI-экрана — **B+A**.
2. **Состав облачного бэкапа и ПДн.** Оценки (`grade_entries`), ДЗ (`student_tasks`), правки преподавателя (`teacher_overrides`), ФИО (`teachers_favorites`, `mpk_teacher_profile`, `mpk_curators`) — отправлять в Google-облако или только на device-transfer? Технически `<exclude>` работает по файлам, не по таблицам: чтобы убрать оценки из облака, придётся либо не бэкапить БД в `cloud-backup` (тогда теряем и расписание), либо писать кастомный `backupAgent`.
3. **`disableIfNoEncryptionCapabilities="true"`** — бэкап только на устройствах с шифрованием. Рекомендую включить (цена: часть устройств без бэкапа).
4. **Материалы (`filesDir/materials`)** — подтвердить, что они остаются вне облака (размер + содержимое). Сейчас так и есть, `exclude` — страховка.
5. **`teacher_overrides` — вариант A (чистить при смене преподавателя) или B (схема с привязкой).** A дешевле, но правки теряются при переключении профиля.
6. **Порог «регресса дня» 2×** (P2-3) — продуктовое правило: блокировать затирание, когда новый набор меньше половины старого. Нужно подтвердить, что «сессионное» сокращение пар не должно применяться автоматически.
7. **`deadlineEpoch`/`dateEpoch` в этом релизе или следующем** — влияет на сортировку в UI и на объём правок вне домена «Данные».
8. **Формат `taskType` (P3-1)** — подтвердить переход на `name()` и что старые бэкап-файлы продолжают читаться (в бэклоге это учтено ветвью legacy).