package com.politex.promo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.AppGraph
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.model.GroupInfo
import com.politex.data.model.TaskType
import com.politex.data.repository.TaskRepository
import com.politex.data.repository.TeacherDayLesson
import com.politex.ui.components.AppDivider
import com.politex.ui.components.MainTopBar
import com.politex.ui.screens.BellsScreen
import com.politex.ui.screens.CollegeMapScreen
import com.politex.ui.screens.LessonsHubScreen
import com.politex.ui.screens.ScheduleScreen
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.screens.materials.MaterialDocTextViewer
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorNavSelected
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.applyAppTheme
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.tabsForMode
import com.politex.ui.viewmodel.toLessonEntity
import com.politex.util.AppThemeStore
import com.politex.util.DateFormats
import com.politex.util.Dow
import com.politex.util.GradeCategory
import com.politex.util.HintFlags
import com.politex.util.ScheduleDisplayPrefs
import com.politex.util.TeacherModeStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)

@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxxhdpi")
class PromoShotsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db get() = MpkDatabase.getInstance(context)

    private val group = GroupInfo(rawName = "ИС-99", course = 1, groupNumber = 9, specialtyCode = 'О')

    private val outDir: File = run {
        val cwd = File(System.getProperty("user.dir"))
        val root = if (cwd.name == "app") cwd.parentFile else cwd
        File(root, "promo/shots").apply { mkdirs() }
    }

    private var realTz: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        realTz = TimeZone.getDefault()
        freezeAtTuesday1030()

        HintFlags.markDone(context, HintFlags.KEY_SCHEDULE_TAPS)
        HintFlags.markDone(context, HintFlags.KEY_SWIPE_TASKS)
        runBlocking { seedDemoData() }
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(realTz)
    }

    private fun freezeAtTuesday1030() {
        val real = System.currentTimeMillis()

        val target = Calendar.getInstance(BASE_TZ).apply {
            timeInMillis = real
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (target.timeInMillis <= real) target.add(Calendar.DAY_OF_YEAR, 7)
        val delta = (target.timeInMillis - real).toInt()
        val base = BASE_TZ
        TimeZone.setDefault(
            object : TimeZone() {
                init {
                    id = "PROMO/FROZEN"
                }

                override fun getOffset(
                    era: Int,
                    year: Int,
                    month: Int,
                    day: Int,
                    dayOfWeek: Int,
                    milliseconds: Int,
                ): Int = base.getOffset(era, year, month, day, dayOfWeek, milliseconds) + delta

                override fun getOffset(millis: Long): Int = base.getOffset(millis) + delta

                override fun setRawOffset(offsetMillis: Int) = Unit

                override fun getRawOffset(): Int = base.rawOffset + delta

                override fun useDaylightTime(): Boolean = false

                override fun inDaylightTime(date: java.util.Date): Boolean = false
            },
        )
    }

    private suspend fun seedDemoData() {
        val g = group.canonicalName
        val now = Calendar.getInstance()
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        fun dayIso(plusDays: Int): String =
            iso.format((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, plusDays) }.time)

        val plan = listOf(
            Triple("Математика", "214", "Иванова И.И."),
            Triple("Физика", "305", "Петров П.П."),
            Triple("ПРОГРАММИРОВАНИЕ ЦИФРОВЫХ УСТРОЙСТВ", "118", "Иванова И.И."),
            Triple("Математика", "214", "Петров П.П."),
            Triple("Физика", "305", "Иванова И.И."),
        )
        val lessons = listOf(2, 3).flatMap { dow ->
            plan.mapIndexed { i, (subject, room, teacher) ->
                val (start, end) = CollegeBellSchedule.getTimeForLessonNumber(i + 1, dow)
                LessonEntity(
                    groupName = g, dayOfWeek = dow, lessonNumber = i + 1,
                    timeStart = start, timeEnd = end,
                    subjectRaw = subject, roomFirst = room, teacherFirst = teacher,
                )
            }
        }
        with(db.lessonDao()) {
            clearLessonsForGroup(g)
            insertLessons(lessons)
        }

        with(db.taskDao()) {
            clearTasksForGroup(g)
            insertTask(
                StudentTaskEntity(
                    groupName = g, course = group.course, subjectName = "Математика",
                    taskType = TaskType.HOMEWORK.displayName, title = "Параграф 12, задачи 1-5",
                    deadlineDate = dayIso(1),
                ),
            )
            insertTask(
                StudentTaskEntity(
                    groupName = g, course = group.course, subjectName = "Физика",
                    taskType = TaskType.LAB_WORK.displayName, title = "Отчёт по лаб. 3",
                    deadlineDate = dayIso(3),
                ),
            )
        }

        with(db.gradeDao()) {
            allEntries().first().forEach { deleteById(it.id) }
            listOf(-12 to 5, -10 to 4, -7 to 5).forEach { (day, value) ->
                insert(
                    GradeEntryEntity(
                        subjectName = "Математика", category = GradeCategory.REGULAR.name,
                        value = value, dateIso = dayIso(day),
                    ),
                )
            }
            insert(
                GradeEntryEntity(
                    subjectName = "Математика", category = GradeCategory.CONTROL.name,
                    value = 4, dateIso = dayIso(-3),
                ),
            )
        }

        with(db.materialDao()) {
            getAllItems().first().forEach { deleteItemById(it.id) }
            insertItem(
                MaterialItemEntity(
                    kind = MaterialKind.TEXT.name, title = "Конспект: основы ТОЭ",
                    subjectName = "Математика", textContent = TEXT_MATERIAL, sortOrder = 0,
                ),
            )

            insertItem(
                MaterialItemEntity(
                    kind = MaterialKind.FILE.name, title = "Презентация: основы ТОЭ",
                    fileName = "toe_slides.pdf", ext = "pdf",
                    subjectName = "Математика", archived = true, sortOrder = 0,
                ),
            )
        }
    }

    private fun mergedPromoLessons(): List<LessonEntity> {
        val now = Calendar.getInstance()
        val dow = Dow.of(now)
        val date = DateFormats.dotDate(now)
        val plan = listOf(
            Triple("Электронные системы транспортных средств", "240", "Иванова И.И."),
            Triple("Электронные системы транспортных средств", "240", "Иванова И.И."),
            Triple("Электронные системы транспортных средств", "240", "Иванова И.И."),
            Triple("Физика", "305", "Петров П.П."),
        )
        return plan.mapIndexed { index, (subject, room, teacher) ->
            val number = index + 1
            val (start, end) = CollegeBellSchedule.getTimeForLessonNumber(number, dow)
            LessonEntity(
                groupName = group.canonicalName,
                dayOfWeek = dow,
                lessonNumber = number,
                timeStart = start,
                timeEnd = end,
                subjectRaw = subject,
                roomFirst = room,
                teacherFirst = teacher,
                dateString = date,
            )
        }
    }

    @Test
    fun shotScheduleMerged() {
        ScheduleDisplayPrefs.setCompact(context, false)
        val lessons = mergedPromoLessons()
        shoot("shot_schedule_merged", tab = 0, interact = { waitForText("Электрон") }) {
            ScheduleScreen(
                groupInfo = group,
                scheduleRepository = AppGraph.scheduleRepository,
                onSyncRequest = {},
                groupLessonsFromVm = lessons,
                pendingTasksFromVm = emptyList(),
            )
        }
    }

    @Test
    fun shotScheduleMergedCompact() {
        ScheduleDisplayPrefs.setCompact(context, true)
        try {
            val lessons = mergedPromoLessons()
            shoot("shot_schedule_merged_compact", tab = 0, interact = { waitForText("Электрон") }) {
                ScheduleScreen(
                    groupInfo = group,
                    scheduleRepository = AppGraph.scheduleRepository,
                    onSyncRequest = {},
                    groupLessonsFromVm = lessons,
                    pendingTasksFromVm = emptyList(),
                )
            }
        } finally {
            ScheduleDisplayPrefs.setCompact(context, false)
        }
    }

    @Test
    fun shotScheduleToday() = shoot("shot_schedule_today", tab = 0, interact = { waitForText("ЦИФРОВЫХ") }) {
        ScheduleScreen(groupInfo = group, scheduleRepository = AppGraph.scheduleRepository, onSyncRequest = {})
    }

    @Test
    fun shotScheduleTomorrow() = shoot("shot_schedule_tomorrow", tab = 0, interact = { waitForText("Завтра") }) {
        ScheduleScreen(
            groupInfo = group, scheduleRepository = AppGraph.scheduleRepository,
            onSyncRequest = {}, selectedSlot = 1,
        )
    }

    @Test
    fun shotScheduleCompact() {
        ScheduleDisplayPrefs.setCompact(context, true)
        try {
            shoot("shot_schedule_compact", tab = 0, interact = { waitForText("ЦИФРОВЫХ") }) {
                ScheduleScreen(groupInfo = group, scheduleRepository = AppGraph.scheduleRepository, onSyncRequest = {})
            }
        } finally {
            ScheduleDisplayPrefs.setCompact(context, false)
        }
    }

    @Test
    fun shotScheduleTeacher() {
        TeacherModeStore.setTeacher(context, "Иванова И.И.")
        try {
            val today = DateFormats.dotDate(Calendar.getInstance())
            val plan = listOf(
                Triple("Математика", "214", "ИС-99, ТС-12"),
                Triple("Математика", "305", "ИС-98"),
                Triple("ПРОГРАММИРОВАНИЕ ЦИФРОВЫХ УСТРОЙСТВ", "118", "ЭТ-31"),
            )
            val teacherLessons = plan.mapIndexed { i, (subject, room, groups) ->
                val (start, end) = CollegeBellSchedule.getTimeForLessonNumber(i + 1, 2)
                TeacherDayLesson(
                    lessonNumber = i + 1,
                    subject = subject,
                    groups = groups.split(", "),
                    room = room,
                    start = start,
                    end = end,
                    startMinutes = -1,
                    endMinutes = -1,
                ).toLessonEntity(today)
            }
            shoot("shot_schedule_teacher", tab = 0, interact = { waitForText("ИС-99, ТС-12") }) {
                ScheduleScreen(
                    groupInfo = group,
                    scheduleRepository = AppGraph.scheduleRepository,
                    onSyncRequest = {},
                    groupLessonsFromVm = teacherLessons,
                    pendingTasksFromVm = emptyList(),
                )
            }
        } finally {
            TeacherModeStore.clear(context)
        }
    }

    @Test
    fun shotBells() = shoot("shot_bells", tab = 2, interact = { waitForText("Расписание звонков") }) {
        BellsScreen(groupInfo = group, scheduleRepository = AppGraph.scheduleRepository)
    }

    @Test
    fun shotTasks() = shoot("shot_tasks", tab = 1, interact = { waitForText("Параграф 12") }) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    @Test
    fun shotMaterials() = shoot(
        "shot_materials",
        tab = 1,
        interact = {
            composeRule.onNodeWithText("Материалы").performClick()
            waitForTag("material_subject_Математика")
        },
    ) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    @Test
    fun shotGrades() = shoot(
        "shot_grades",
        tab = 1,
        interact = {
            composeRule.onNodeWithText("Оценки").performClick()

            waitForTag("grades_root_list")

            waitForTag("grades_subject_row_Математика")
            composeRule.onNodeWithTag("grades_subject_row_Математика").performScrollTo()
            waitForText("4.2")
        },
    ) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    @Test
    fun shotTasksSwipe() = shoot("shot_tasks_swipe", tab = 1, interact = { waitForText("Параграф 12") }) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    @Test
    fun shotGradesSubject() = shoot(
        "shot_grades_subject",
        tab = 1,
        interact = {
            composeRule.onNodeWithText("Оценки").performClick()
            waitForTag("grades_root_list")
            waitForTag("grades_subject_row_Математика")
            composeRule.onNodeWithTag("grades_subject_row_Математика").performScrollTo()
            composeRule.onNodeWithTag("grades_subject_row_Математика").performClick()
            waitForTag("grades_subject_screen")
            waitForTag("grades_num_5")
            waitForText("4.2")
        },
    ) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    @Test
    fun shotMapRoute() = shoot(
        "shot_map_route",
        tab = 0,

        interact = {
            composeRule.onNodeWithText("Шаг 1").performClick()
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
        },
    ) {
        CollegeMapScreen(initialRoom = "214", fromRoom = "", showRoute = true, onBack = {})
    }

    @Test
    fun shotSettings() = shoot("shot_settings", tab = 3) {
        SettingsScreen(groupInfo = group, onGroupChanged = {})
    }

    @Test
    fun shotViewerText() {
        val item = runBlocking { db.materialDao().getAllItems().first().first { it.kind == MaterialKind.TEXT.name } }

        shoot("shot_viewer_text", topBar = false, interact = { waitForText("Электрическая цепь") }) {
            MaterialDocTextViewer(item = item, dao = db.materialDao(), onBack = {})
        }
    }

    @Test
    fun shotScheduleTodayDark() = shootDark("shot_schedule_today_dark", tab = 0, interact = { waitForText("ЦИФРОВЫХ") }) {
        ScheduleScreen(groupInfo = group, scheduleRepository = AppGraph.scheduleRepository, onSyncRequest = {})
    }

    @Test
    fun shotSettingsDark() = shootDark("shot_settings_dark", tab = 3) {
        SettingsScreen(groupInfo = group, onGroupChanged = {})
    }

    @Test
    fun shotGradesSubjectDark() = shootDark(
        "shot_grades_subject_dark",
        tab = 1,
        interact = {
            composeRule.onNodeWithText("Оценки").performClick()
            waitForTag("grades_root_list")
            waitForTag("grades_subject_row_Математика")
            composeRule.onNodeWithTag("grades_subject_row_Математика").performScrollTo()
            composeRule.onNodeWithTag("grades_subject_row_Математика").performClick()
            waitForTag("grades_subject_screen")
            waitForTag("grades_num_5")
            waitForText("4.2")
        },
    ) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    @Test
    fun shotMapRouteDark() = shootDark(
        "shot_map_route_dark",
        tab = 0,
        interact = {
            composeRule.onNodeWithText("Шаг 1").performClick()
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
        },
    ) {
        CollegeMapScreen(initialRoom = "214", fromRoom = "", showRoute = true, onBack = {})
    }

    @Test
    fun shotMaterialsDark() = shootDark(
        "shot_materials_dark",
        tab = 1,
        interact = {
            composeRule.onNodeWithText("Материалы").performClick()
            waitForTag("material_subject_Математика")
        },
    ) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    @Test
    fun shotBellsDark() = shootDark("shot_bells_dark", tab = 2, interact = { waitForText("Расписание звонков") }) {
        BellsScreen(groupInfo = group, scheduleRepository = AppGraph.scheduleRepository)
    }

    @Test
    fun shotTasksDark() = shootDark("shot_tasks_dark", tab = 1, interact = { waitForText("Параграф 12") }) {
        LessonsHubScreen(groupInfo = group, taskRepository = TaskRepository(db.taskDao(), db.taskReminderDao()))
    }

    private fun shootDark(
        name: String,
        tab: Int = -1,
        topBar: Boolean = true,
        interact: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        AppThemeStore.setDark(context, true)
        applyAppTheme(AppTheme.COLLEGE, dark = true)
        try {
            shoot(name, tab = tab, topBar = topBar, interact = interact, content = content)
        } finally {
            AppThemeStore.setDark(context, false)
        }
    }

    private fun shoot(
        name: String,
        tab: Int = -1,
        topBar: Boolean = true,
        interact: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                if (topBar || tab >= 0) {
                    Scaffold(
                        topBar = { if (topBar) MainTopBar() },
                        bottomBar = { if (tab >= 0) PromoNavBar(tab) },
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { content() }
                    }
                } else {
                    content()
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        if (topBar) runCatching { waitForText("ПОЛИТЕХНИЧЕСКИЙ", 10_000) }
        if (interact != null) {
            interact()
            composeRule.mainClock.advanceTimeBy(1_500)
            composeRule.waitForIdle()
        }
        composeRule.onRoot().captureRoboImage(File(outDir, "$name.png").absolutePath)
    }

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
            composeRule.mainClock.advanceTimeByFrame()
        }
        throw AssertionError("Текст «$text» не появился за ${timeoutMs}мс")
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 20_000) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
            composeRule.mainClock.advanceTimeByFrame()
        }
        throw AssertionError("Узел «$tag» не появился за ${timeoutMs}мс")
    }

    @Composable
    private fun PromoNavBar(selected: Int) {
        val context = LocalContext.current
        val teacherModeRevision = TeacherModeStore.revision.intValue
        val isTeacherMode = remember(context, teacherModeRevision) { TeacherModeStore.isTeacher(context) }
        Column(modifier = Modifier.fillMaxWidth()) {
            AppDivider()
            NavigationBar(
                containerColor = ColorBgMain,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                val tabs = tabsForMode(isTeacherMode).map { tab ->
                    when (tab) {
                        AppTab.SCHEDULE -> Triple("Расписание", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth)
                        AppTab.TASKS -> Triple("Уроки", Icons.Filled.MenuBook, Icons.Outlined.MenuBook)
                        AppTab.BELLS -> Triple("Звонки", Icons.Filled.AccessTime, Icons.Outlined.AccessTime)
                        AppTab.COLLEGE -> Triple("Другое", Icons.Filled.Apps, Icons.Outlined.Apps)
                    }
                }
                tabs.forEachIndexed { i, (title, filledIcon, outlinedIcon) ->
                    val isSelected = selected == i
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) filledIcon else outlinedIcon,
                                contentDescription = title,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.sp,
                                ),
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ColorNavSelected,
                            selectedTextColor = ColorNavSelected,
                            indicatorColor = ColorSurfaceHighlight,
                            unselectedIconColor = ColorTextMuted,
                            unselectedTextColor = ColorTextMuted,
                        ),
                    )
                }
            }
        }
    }

    companion object {

        private val BASE_TZ: TimeZone = TimeZone.getDefault()

        private val TEXT_MATERIAL = listOf(
            "Электрическая цепь — совокупность устройств, образующих путь для электрического тока. " +
                "Основные элементы цепи: источник энергии, приёмник (нагрузка) и соединительные проводники.",
            "Закон Ома для участка цепи: сила тока прямо пропорциональна напряжению и обратно " +
                "пропорциональна сопротивлению участка. I = U / R.",
            "Первый закон Кирхгофа: алгебраическая сумма токов в любом узле цепи равна нулю. " +
                "Входящие токи считаются положительными, выходящие — отрицательными.",
        ).joinToString("\n\n")
    }
}
