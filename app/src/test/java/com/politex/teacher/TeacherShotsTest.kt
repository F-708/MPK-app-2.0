package com.politex.teacher

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.AppGraph
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.repository.TeacherDayLoader
import com.politex.ui.teacher.TeacherNav
import com.politex.ui.teacher.TeacherOnboardingScreen
import com.politex.ui.teacher.TeacherProfile
import com.politex.ui.teacher.TeacherProfileStore
import com.politex.ui.teacher.TeacherTheme
import com.politex.ui.teacher.TeacherWeekLogic
import com.politex.util.DateFormats
import com.politex.util.Dow
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
import java.util.Calendar
import java.util.TimeZone

/**
 * КАДРЫ teacher-flavor для совета приложения (T5): реальные экраны преподавателя на
 * Robolectric + Roborazzi, демо-данные вымышленные. PNG → _reports/teacher-app-council/shots/.
 *
 * Запись: sh ./gradlew :app:recordRoborazziStandardDebug --tests "*TeacherShotsTest*"
 *
 * Профиль — «Лебедева Ольга Дмитриевна»: фамилии НЕТ в assets/teachers.csv, поэтому
 * TeacherInsights.matchRowToTeacher даёт YES (кандидатов 0) без однофамильцев и без
 * реальных ФИО в кадрах/отчётах.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class TeacherShotsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = MpkDatabase.getInstance(context)

    private val profile = TeacherProfile(name = "Лебедева Ольга Дмитриевна", surnameKey = SURNAME_KEY)

    private val outDir: File =
        run {
            val cwd = File(System.getProperty("user.dir"))
            val root = if (cwd.name == "app") cwd.parentFile else cwd
            File(root, "_reports/teacher-app-council/shots").apply { mkdirs() }
        }

    /**
     * Фейковая TimeZone живёт на весь тест-JVM: без восстановления java.time-код чужих тестов
     * (DocxTextExtractorTest, Step7) падает с ZoneRulesException «Unknown time-zone ID».
     */
    private var realTz: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        realTz = TimeZone.getDefault()
        freezeAtTuesday1030()
        TeacherProfileStore.set(context, profile)
        runBlocking { seedWeek() }
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(realTz)
    }

    // ------------------------------------------------------------------ данные

    /** Сегодня (вт): 3-я пара ИДЁТ, 4-я СЛЕДУЮЩАЯ. */
    private val todayPlan =
        listOf(
            Row(1, "Математика", "41О", "214"),
            Row(2, "Информатика", "21О", "118"),
            Row(3, "Математика", "41О", "305"),
            Row(4, "Математика", "31О, 32О", "214", teacher = "Лебедева О.Д./Смирнов А.В."),
            Row(5, "Физика", "11О", ""),
            Row(6, "Математика", "51О", "402"),
        )

    /** Остальные дни недели: 4 пары, чтобы полоса Пн–Сб была с бейджами и нагрузка считалась. */
    private val weekPlan =
        listOf(
            Row(1, "Математика", "41О", "214"),
            Row(2, "Математика", "22О", "216"),
            Row(3, "Информатика", "31О", "118"),
            Row(4, "Математика", "12О", "214"),
        )

    private data class Row(
        val number: Int,
        val subject: String,
        val groups: String,
        val room: String,
        val teacher: String = "Лебедева О.Д.",
    )

    private suspend fun seedWeek() {
        val today = DateFormats.dotDate(Calendar.getInstance())
        val rows =
            TeacherWeekLogic
                .mondayToSaturday(Calendar.getInstance())
                .flatMap { cal ->
                    val date = DateFormats.dotDate(cal)
                    val plan = if (date == today) todayPlan else weekPlan
                    plan.map { r ->
                        TeacherLessonEntity(
                            teacherName = r.teacher,
                            surnameKey = SURNAME_KEY,
                            dateString = date,
                            dayOfWeek = Dow.of(cal),
                            lessonNumber = r.number,
                            groups = r.groups,
                            subject = r.subject,
                            room = r.room,
                        )
                    }
                }
        val lessonDao = db.teacherLessonDao()
        rows.map { it.dateString }.distinct().forEach { lessonDao.deleteForDate(it) }
        lessonDao.insertLessons(rows)

        // «обновлено в HH:MM» в шапке
        TeacherDayLoader.markSyncedNow(context)
    }

    // ------------------------------------------------------------------ кадры

    @Test
    fun shotOnboardingWelcome() {
        setOnboarding()
        waitForText("Добро пожаловать!")
        waitForText("Неофициальное приложение")
        capture("teacher_onboarding_welcome")
    }

    @Test
    fun shotOnboardingSearch() {
        setOnboarding()
        waitForText("Добро пожаловать!")
        composeRule.onNodeWithText("Продолжить").performClick()
        advance(1_000)
        waitForText("Найдите себя в списке")
        waitForTag("teacher_onboarding_search")
        // список грузится асинхронно из assets/teachers.csv — ждём первую карточку
        val first = AppGraph.teachersRepository.loadTeachers().firstOrNull()?.name ?: error("teachers.csv пуст")
        waitForText(first)
        advance(500)
        capture("teacher_onboarding_search")
    }

    @Test
    fun shotHomeDay() {
        setNav()
        waitForText("обновлено в")
        waitForText("ИДЁТ")
        waitForText("СЛЕДУЮЩАЯ")
        advance(2_000)
        capture("teacher_home_day")
    }

    @Test
    fun shotWeek() {
        setNav()
        waitForTag("teacher_tab_week")
        composeRule.onNodeWithTag("teacher_tab_week").performClick()
        advance(2_000)
        waitForTag("teacher_week_tab_load")
        // дни грузятся последовательно: ждём карточку выбранного дня И бейджи всех 6 ячеек,
        // иначе кадр ловит спиннер и пустую полосу (первый прогон так и снял)
        waitForText("Математика")
        waitFor("бейджи Пн–Сб") {
            composeRule.onAllNodesWithText("4", substring = false).fetchSemanticsNodes().size >= 5 &&
                composeRule.onAllNodesWithText("6", substring = false).fetchSemanticsNodes().size >= 1
        }
        advance(500)
        capture("teacher_week")

        composeRule.onNodeWithTag("teacher_week_tab_load").performClick()
        advance(1_500)
        waitForTag("teacher_week_load_disclaimer")
        waitForTag("teacher_week_by_subject")
        capture("teacher_week_load")
    }

    @Test
    fun shotMore() {
        // autoAdvance=true ОБЯЗАТЕЛЕН: performScrollTo при стоящих часах livelock'ится
        // (scroll-семантика ждёт кадров, которых нет) — проверено на практике.
        setNav(autoAdvance = true)
        waitForTag("teacher_tab_more")
        composeRule.onNodeWithTag("teacher_tab_more").performClick()
        advance(1_500)
        waitForText("Утренняя сводка")
        waitForTag("more_profile")
        capture("teacher_more")

        // низ экрана отдельным кадром: секции СТАБИЛЬНОСТЬ и О ПРОГРАММЕ
        composeRule.onNodeWithTag("more_delete_contacts").performScrollTo()
        composeRule.waitForIdle()
        capture("teacher_more_bottom")
    }

    // ------------------------------------------------------------------ обвязка

    private fun setOnboarding() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TeacherTheme { TeacherOnboardingScreen(onProfileSaved = {}, onSkipAll = {}) }
        }
        advance(2_000)
    }

    private fun setNav(autoAdvance: Boolean = false) {
        composeRule.mainClock.autoAdvance = autoAdvance
        composeRule.setContent { TeacherTheme { TeacherNav(profile = profile) } }
        advance(3_000)
        waitForTag("teacher_bottom_nav")
    }

    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    private fun capture(
        name: String,
        node: SemanticsNodeInteraction = composeRule.onRoot(),
    ) = node.captureRoboImage(File(outDir, "$name.png").absolutePath)

    /** Room/IO асинхронны, часы теста стоят — крутим кадры вручную (паттерн PromoShotsTest). */
    private fun waitForText(
        text: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            pump()
        }
        throw AssertionError("Текст «$text» не появился за ${timeoutMs}мс")
    }

    private fun waitForTag(
        tag: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
            pump()
        }
        throw AssertionError("Узел «$tag» не появился за ${timeoutMs}мс")
    }

    /** Дать кадру отрисоваться: при стоящих часах — один кадр, при живых — полный idle. */
    private fun pump() {
        Thread.sleep(50)
        if (composeRule.mainClock.autoAdvance) composeRule.waitForIdle() else composeRule.mainClock.advanceTimeByFrame()
    }

    private fun waitFor(
        what: String,
        timeoutMs: Long = 20_000,
        ready: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (ready()) return
            pump()
        }
        throw AssertionError("Условие «$what» не выполнено за ${timeoutMs}мс")
    }

    /**
     * «Заморозка» на ближайший вторник 10:30 (идёт 3 урок, 10:20–11:05) — иначе статусы
     * ИДЁТ/СЛЕДУЮЩАЯ в кадре не получить. Копия приёма из PromoShotsTest: экраны читают
     * Calendar.getInstance(), поэтому сдвигаем дефолтный TimeZone, а не ShadowSystemClock.
     */
    private fun freezeAtTuesday1030() {
        val real = System.currentTimeMillis()
        val target =
            Calendar.getInstance(BASE_TZ).apply {
                timeInMillis = real
                set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        while (target.timeInMillis <= real) target.add(Calendar.DAY_OF_YEAR, 7)
        val delta = (target.timeInMillis - real).toInt()
        TimeZone.setDefault(
            object : TimeZone() {
                init {
                    id = "TEACHER_SHOTS/FROZEN"
                }

                override fun getOffset(
                    era: Int,
                    year: Int,
                    month: Int,
                    day: Int,
                    dayOfWeek: Int,
                    milliseconds: Int,
                ): Int = BASE_TZ.getOffset(era, year, month, day, dayOfWeek, milliseconds) + delta

                override fun getOffset(millis: Long): Int = BASE_TZ.getOffset(millis) + delta

                override fun setRawOffset(offsetMillis: Int) = Unit

                override fun getRawOffset(): Int = BASE_TZ.rawOffset + delta

                override fun useDaylightTime(): Boolean = false

                override fun inDaylightTime(date: java.util.Date): Boolean = false
            },
        )
    }

    companion object {
        private val BASE_TZ: TimeZone = TimeZone.getDefault()
        private const val SURNAME_KEY = "лебедева"
    }
}
