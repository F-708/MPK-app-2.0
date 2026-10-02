package com.politex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.data.model.GroupInfo
import com.politex.ui.screens.MaterialBaseScreen
import com.politex.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W12: текстовый материал (kind=TEXT, байтов на диске нет) обязан открываться
 * в DocTextViewer и показывать СВОЙ текст. Воспроизводит баг «текст полностью
 * не отображается» на реальном экране (роутинг item → viewer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class MaterialTextRoutingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val group = GroupParser.parse("41О")!!
    private lateinit var db: MpkDatabase

    @Before
    fun setUp() {
        resetSingleton()
        db = MpkDatabase.getInstance(context)
        runBlocking {
            val dao = db.materialDao()
            val folderId = dao.insertFolder(MaterialFolderEntity(subjectName = "ТОЭ", name = "Лекции"))
            dao.insertItem(
                MaterialItemEntity(
                    folderId = folderId,
                    kind = MaterialKind.TEXT.name,
                    title = "Конспект по цепям",
                    ext = "txt",
                    textContent = "Первая строка конспекта\nВторая строка конспекта",
                ),
            )
        }
        composeRule.setContent {
            MyApplicationTheme {
                MaterialBaseScreen(groupInfo = group)
            }
        }
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        db.close()
        resetSingleton()
    }

    @Test
    fun `текстовый материал открывается и показывает текст`() {
        composeRule.onNodeWithTag("material_item_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("doc_top_bar").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_list").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_0").assertExists()
    }

    private fun resetSingleton() {
        try {
            val cls = Class.forName("com.politex.data.local.MpkDatabase")
            val field = cls.getDeclaredField("instance")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
    }
}
