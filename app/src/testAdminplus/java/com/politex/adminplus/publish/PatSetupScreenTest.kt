package com.politex.adminplus.publish

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class PatFakeCipher : TokenCipher {
    override fun encrypt(raw: String): String = "enc:" + raw.reversed()

    override fun decrypt(encoded: String): String? =
        if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
}

private class PatFakePublisher(
    var check: TokenCheck = TokenCheck.Valid,
) : GithubPublisher {
    var verifyCalls = 0

    override suspend fun verifyToken(token: String): TokenCheck {
        verifyCalls++
        return check
    }

    override suspend fun publish(
        token: String,
        payload: PublishPayload,
    ): PublishResult = PublishResult.Success("sha")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class PatSetupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private val publisher = PatFakePublisher()

    private val token = "github_pat_" + "C3".repeat(30)

    @Before
    fun setUp() {
        GithubTokenStore.cipher = PatFakeCipher()
        GithubTokenStore.clear(context)
        AdminPlusPublishDeps.publisherFactory = { publisher }
    }

    @After
    fun tearDown() {
        AdminPlusPublishDeps.reset()
        GithubTokenStore.clear(context)
    }

    private fun openScreen() {
        composeRule.setContent {
            MyApplicationTheme { PatSetupScreen(onBack = {}) }
        }
        waitForTag("pat_setup_screen")
        waitForTag("pat_token_input")
        waitForTag("pat_save")
    }

    @Test
    fun `экран показывает инструкцию и поле ввода`() {
        openScreen()
        waitForText("Fine-grained personal access tokens")
        waitForText("moy-polytech")
        waitForText("Contents: Read and write")
    }

    @Test
    fun `валидный токен проверяется и сохраняется`() {
        openScreen()
        composeRule.onNodeWithTag("pat_token_input").performTextInput(token)
        pump()
        composeRule.onNodeWithTag("pat_save").performClick()

        waitUntilTrue { GithubTokenStore.hasToken(context) }
        assertEquals(1, publisher.verifyCalls)
        assertEquals(token, GithubTokenStore.load(context))
        waitForTag("pat_status")
        waitForText("Токен сохранён")
    }

    @Test
    fun `недействительный токен не сохраняется и показывает ошибку`() {
        publisher.check = TokenCheck.Invalid(401)
        openScreen()
        composeRule.onNodeWithTag("pat_token_input").performTextInput(token)
        pump()
        composeRule.onNodeWithTag("pat_save").performClick()

        waitUntilTrue { publisher.verifyCalls == 1 }
        waitForTag("pat_status")
        waitForText("недействителен")
        assertFalse(GithubTokenStore.hasToken(context))
    }

    @Test
    fun `нет связи - токен не сохраняется и показан путь исправления`() {
        publisher.check = TokenCheck.NetworkError
        openScreen()
        composeRule.onNodeWithTag("pat_token_input").performTextInput(token)
        pump()
        composeRule.onNodeWithTag("pat_save").performClick()

        waitUntilTrue { publisher.verifyCalls == 1 }
        waitForTag("pat_status")
        waitForText("Нет связи с GitHub")
        assertFalse(GithubTokenStore.hasToken(context))
    }

    @Test
    fun `короткий ввод отклоняется без сетевого запроса`() {
        openScreen()
        composeRule.onNodeWithTag("pat_token_input").performTextInput("abc")
        pump()
        composeRule.onNodeWithTag("pat_save").performClick()
        waitForTag("pat_status")
        waitForText("выглядит неполным")
        assertEquals(0, publisher.verifyCalls)
    }

    @Test
    fun `сохранённый токен можно удалить`() {
        GithubTokenStore.save(context, token)
        openScreen()
        waitForTag("pat_delete")
        composeRule.onNodeWithTag("pat_delete").performClick()
        waitUntilTrue { !GithubTokenStore.hasToken(context) }
        waitForTag("pat_status")
        waitForText("Токен удалён")
        composeRule.onAllNodesWithTag("pat_delete").assertCountEquals(0)
    }

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
