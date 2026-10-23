package com.politex.ui.dataeditor

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.politex.AppGraph
import com.politex.content.OwnerOverridesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class TeacherEditorScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        OwnerOverridesStore.clearAll(context)
        AppGraph.teachersRepository.invalidateCache()
        com.politex.ui.util.TeachersCache.invalidate()
    }

    @After
    fun tearDown() {
        OwnerOverridesStore.clearAll(context)
        AppGraph.teachersRepository.invalidateCache()
        com.politex.ui.util.TeachersCache.invalidate()
    }

    @Test
    fun `список ищет преподавателя и открывает редактор по клику`() {
        val first = AppGraph.teachersRepository.loadTeachers().first()
        var opened: String? = null
        composeRule.setContent {
            TeacherEditorListScreen(onBack = {}, onEditTeacher = { opened = it })
        }
        waitForTag("teacher_editor_search")
        composeRule.onNodeWithTag("teacher_editor_search").performTextInput(first.name.substringBefore(' '))
        waitForTag("teacher_editor_row_${first.name}")
        composeRule.onNodeWithTag("teacher_editor_row_${first.name}").performClick()
        assertNotNull(opened)
        assertEquals(first.name, opened)
    }

    @Test
    fun `список показывает пустое состояние по неизвестному запросу`() {
        composeRule.setContent {
            TeacherEditorListScreen(onBack = {}, onEditTeacher = {})
        }
        waitForTag("teacher_editor_search")
        composeRule.onNodeWithTag("teacher_editor_search").performTextInput("щщщщщщщщ щщщщщщщщщщ")
        waitForText("Никого не нашли")
    }

    @Test
    fun `редактор держит сохранение неактивным до изменений и пишет правку`() {
        val teacher = AppGraph.teachersRepository.loadTeachers().first()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { TeacherEditorScreen(teacherName = teacher.name, onBack = {}) }
        waitForTag("teacher_editor_screen")
        waitForTag("teacher_editor_fio")
        waitUntilTrue { fioText().contains(teacher.name) }
        composeRule.onNodeWithTag("teacher_editor_save").assertIsNotEnabled()

        composeRule.onNodeWithTag("teacher_editor_fio").performTextReplacement(teacher.name + "X")
        pump()
        composeRule.onNodeWithTag("teacher_editor_save").performClick()

        waitUntilTrue { OwnerOverridesStore.listTeachers(context).isNotEmpty() }
        val saved = OwnerOverridesStore.listTeachers(context).single()
        assertEquals(OwnerOverridesStore.keyOf(teacher.name), saved.key)
        assertEquals(teacher.name + "X", saved.fio)

        waitForTag("teacher_editor_status")
        composeRule.onNodeWithTag("teacher_editor_status").assertIsDisplayed()
        composeRule.onNodeWithTag("teacher_editor_save").assertIsNotEnabled()

        AppGraph.teachersRepository.invalidateCache()
        assertTrue(
            AppGraph.teachersRepository.loadTeachers().any { it.name == teacher.name + "X" },
        )
    }

    @Test
    fun `удаление правки требует подтверждения и возвращает исходные данные`() {
        val teacher = AppGraph.teachersRepository.loadTeachers().first()
        val key = OwnerOverridesStore.keyOf(teacher.name)
        OwnerOverridesStore.upsertTeacher(context, key, teacher.name + " Правка")
        OwnerOverridesStore.refreshDependentCaches(context)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { TeacherEditorScreen(teacherName = teacher.name + " Правка", onBack = {}) }
        waitForTag("teacher_editor_screen")
        waitForTag("teacher_editor_delete")
        composeRule.onNodeWithTag("teacher_editor_delete").assertIsDisplayed()

        composeRule.onNodeWithTag("teacher_editor_delete").performClick()
        waitForText("Удалить правку?")
        composeRule.onNodeWithText("Отмена").performClick()
        pump()
        assertEquals(1, OwnerOverridesStore.listTeachers(context).size)

        composeRule.onNodeWithTag("teacher_editor_delete").performClick()
        waitForText("Удалить правку?")
        composeRule.onNodeWithText("Удалить").performClick()
        waitUntilTrue { OwnerOverridesStore.listTeachers(context).isEmpty() }

        waitForTag("teacher_editor_status")
        AppGraph.teachersRepository.invalidateCache()
        assertTrue(AppGraph.teachersRepository.loadTeachers().any { it.name == teacher.name })
    }

    private fun fioText(): String = composeRule
        .onNodeWithTag("teacher_editor_fio")
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.EditableText)
        ?.text
        .orEmpty()

    private fun waitUntilTrue(
        timeoutMs: Long = 20_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            pump()
        }
        throw AssertionError("Условие не выполнено за ${timeoutMs}мс")
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

    private fun pump() {
        Thread.sleep(50)
        if (composeRule.mainClock.autoAdvance) composeRule.waitForIdle() else composeRule.mainClock.advanceTimeByFrame()
    }
}
