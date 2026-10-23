package com.politex.ui.dataeditor

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.politex.AppGraph
import com.politex.adminplus.publish.AdminPlusPublishDeps
import com.politex.adminplus.publish.GithubTokenStore
import com.politex.adminplus.publish.PublishFailure
import com.politex.adminplus.publish.PublishPayload
import com.politex.adminplus.publish.PublishResult
import com.politex.adminplus.publish.TokenCheck
import com.politex.adminplus.publish.TokenCipher
import com.politex.adminplus.publish.GithubPublisher
import com.politex.content.OwnerOverridesStore
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeCipher : TokenCipher {
    override fun encrypt(raw: String): String = "enc:" + raw.reversed()

    override fun decrypt(encoded: String): String? =
        if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
}

private class FakePublisher(
    var result: PublishResult = PublishResult.Success("sha-test"),
) : GithubPublisher {
    val published = mutableListOf<PublishPayload>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun verifyToken(token: String): TokenCheck = TokenCheck.Valid

    override suspend fun publish(
        token: String,
        payload: PublishPayload,
    ): PublishResult {
        published += payload
        gate?.await()
        return result
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class TeacherEditorPublishTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private val fake = FakePublisher()

    private val token = "github_pat_" + "B2".repeat(30)

    @Before
    fun setUp() {
        OwnerOverridesStore.clearAll(context)
        AppGraph.teachersRepository.invalidateCache()
        com.politex.ui.util.TeachersCache.invalidate()
        GithubTokenStore.cipher = FakeCipher()
        GithubTokenStore.clear(context)
        AdminPlusPublishDeps.publisherFactory = { fake }
        AdminPlusPublishDeps.payloadBuilder = { PublishPayload("""{"format":1,"teachers":{}}""", emptyList()) }
    }

    @After
    fun tearDown() {
        AdminPlusPublishDeps.reset()
        GithubTokenStore.clear(context)
        OwnerOverridesStore.clearAll(context)
        AppGraph.teachersRepository.invalidateCache()
        com.politex.ui.util.TeachersCache.invalidate()
    }

    private fun saveToken() {
        GithubTokenStore.save(context, token)
    }

    private fun firstTeacherName(): String = AppGraph.teachersRepository.loadTeachers().first().name

    @Test
    fun `кнопка применить висит внизу экрана и видна при прокрутке`() {
        val name = firstTeacherName()
        OwnerOverridesStore.upsertTeacher(context, OwnerOverridesStore.keyOf(name), name)
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_apply")
        composeRule.onNodeWithTag("teacher_editor_apply").assertIsDisplayed()

        composeRule.onNodeWithTag("teacher_editor_category").performScrollTo()
        pump()
        composeRule.onNodeWithTag("teacher_editor_apply").assertIsDisplayed()
    }

    @Test
    fun `отмена в диалоге подтверждения ничего не публикует`() {
        val name = firstTeacherName()
        OwnerOverridesStore.upsertTeacher(context, OwnerOverridesStore.keyOf(name), name)
        saveToken()
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_apply")
        composeRule.onNodeWithTag("teacher_editor_apply").performClick()
        waitForText("Действительно применить изменения?")
        composeRule.onNodeWithTag("teacher_editor_apply_cancel").performClick()
        pump()
        assertTrue(fake.published.isEmpty())
        composeRule.onAllNodesWithTag("teacher_editor_publish_status").assertCountEquals(0)
    }

    @Test
    fun `подтверждение публикует и показывает успех`() {
        val name = firstTeacherName()
        OwnerOverridesStore.upsertTeacher(context, OwnerOverridesStore.keyOf(name), name)
        saveToken()
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_apply")
        composeRule.onNodeWithTag("teacher_editor_apply").performClick()
        waitForText("Действительно применить изменения?")
        composeRule.onNodeWithTag("teacher_editor_apply_confirm").performClick()

        waitUntilTrue { fake.published.size == 1 }
        waitForTag("teacher_editor_publish_status")
        composeRule.onNodeWithTag("teacher_editor_publish_status").assertIsDisplayed()
        waitForText("Изменения отправлены всем пользователям")
        waitForText("На этом устройстве правка уже видна сразу")
    }

    @Test
    fun `во время публикации виден прогресс`() {
        val name = firstTeacherName()
        OwnerOverridesStore.upsertTeacher(context, OwnerOverridesStore.keyOf(name), name)
        saveToken()
        fake.gate = CompletableDeferred()
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_apply")
        composeRule.onNodeWithTag("teacher_editor_apply").performClick()
        waitForText("Действительно применить изменения?")
        composeRule.onNodeWithTag("teacher_editor_apply_confirm").performClick()

        waitForTag("teacher_editor_apply_progress")
        composeRule.onNodeWithTag("teacher_editor_apply_progress").assertIsDisplayed()

        fake.gate!!.complete(Unit)
        waitUntilTrue { fake.published.size == 1 }
        waitUntilTrue { composeRule.onAllNodesWithTag("teacher_editor_apply_progress").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `без токена подтверждение ведёт на экран настройки токена`() {
        val name = firstTeacherName()
        OwnerOverridesStore.upsertTeacher(context, OwnerOverridesStore.keyOf(name), name)
        var openedTokenSetup = false
        composeRule.setContent {
            TeacherEditorScreen(
                teacherName = name,
                onBack = {},
                onOpenTokenSetup = { openedTokenSetup = true },
            )
        }
        waitForTag("teacher_editor_apply")
        composeRule.onNodeWithTag("teacher_editor_apply").performClick()
        waitForText("Действительно применить изменения?")
        composeRule.onNodeWithTag("teacher_editor_apply_confirm").performClick()
        waitUntilTrue { openedTokenSetup }
        assertTrue(fake.published.isEmpty())
    }

    @Test
    fun `ошибка авторизации показывает сообщение и путь исправления`() {
        val name = firstTeacherName()
        OwnerOverridesStore.upsertTeacher(context, OwnerOverridesStore.keyOf(name), name)
        saveToken()
        fake.result = PublishResult.Failed(PublishFailure.AUTH, "401")
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_apply")
        composeRule.onNodeWithTag("teacher_editor_apply").performClick()
        waitForText("Действительно применить изменения?")
        composeRule.onNodeWithTag("teacher_editor_apply_confirm").performClick()

        waitForTag("teacher_editor_publish_status")
        waitForText("GitHub отклонил токен")
        composeRule.onNodeWithTag("teacher_editor_token_setup").assertIsDisplayed()
    }

    @Test
    fun `ошибка сети показывает сообщение с повтором`() {
        val name = firstTeacherName()
        OwnerOverridesStore.upsertTeacher(context, OwnerOverridesStore.keyOf(name), name)
        saveToken()
        fake.result = PublishResult.Failed(PublishFailure.NETWORK)
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_apply")
        composeRule.onNodeWithTag("teacher_editor_apply").performClick()
        waitForText("Действительно применить изменения?")
        composeRule.onNodeWithTag("teacher_editor_apply_confirm").performClick()

        waitForTag("teacher_editor_publish_status")
        waitForText("Нет связи с GitHub")
    }

    @Test
    fun `применение автосохраняет несохранённую правку и публикует`() {
        val name = firstTeacherName()
        saveToken()
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_fio")
        waitUntilTrue { fioText().contains(name) }
        composeRule.onNodeWithTag("teacher_editor_fio").performTextReplacement(name + " Правка")
        pump()
        composeRule.onNodeWithTag("teacher_editor_apply").performClick()
        waitForText("Действительно применить изменения?")
        composeRule.onNodeWithTag("teacher_editor_apply_confirm").performClick()

        waitUntilTrue { fake.published.size == 1 }
        waitUntilTrue { OwnerOverridesStore.listTeachers(context).isNotEmpty() }
        val saved = OwnerOverridesStore.listTeachers(context).single()
        assertEquals(OwnerOverridesStore.keyOf(name), saved.key)
        assertEquals(name + " Правка", saved.fio)
        AppGraph.teachersRepository.invalidateCache()
        assertTrue(AppGraph.teachersRepository.loadTeachers().any { it.name == name + " Правка" })
    }

    @Test
    fun `предметы выбираются из каталога а не вводятся текстом`() {
        val name = firstTeacherName()
        composeRule.setContent { TeacherEditorScreen(teacherName = name, onBack = {}) }
        waitForTag("teacher_editor_pick_subjects")
        composeRule.onNodeWithTag("teacher_editor_pick_subjects").performClick()
        waitForTag("subject_picker_list")

        val catalog = SubjectPickerLogic.catalog(emptyList())
        val first = catalog.first()
        val second = catalog[1]
        composeRule.onNodeWithTag("subject_picker_item_$first").performScrollTo()
        composeRule.onNodeWithTag("subject_picker_item_$first").performClick()
        composeRule.onNodeWithTag("subject_picker_item_$second").performScrollTo()
        composeRule.onNodeWithTag("subject_picker_item_$second").performClick()
        pump()
        composeRule.onNodeWithTag("subject_picker_confirm").performClick()

        waitUntilTrue { subjectsText().contains(first) && subjectsText().contains(second) }
        assertTrue(subjectsText().contains(", "))
    }

    private fun fioText(): String =
        composeRule
            .onNodeWithTag("teacher_editor_fio")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text
            .orEmpty()

    private fun subjectsText(): String =
        composeRule
            .onNodeWithTag("teacher_editor_subjects", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.firstOrNull()
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
