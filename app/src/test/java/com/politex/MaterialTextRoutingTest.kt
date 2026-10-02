package com.politex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.screens.MaterialBaseScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupParser
import com.politex.util.MaterialImporter
import com.politex.util.PendingMaterial
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W11/W12: экран «Материалы» — предметы вместо папок, Архив секцией внизу,
 * и текстовый материал (kind=TEXT, байтов на диске нет) обязан открываться
 * в DocTextViewer и показывать СВОЙ текст (баг «текст полностью не отображается»).
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
        // файловая БД переживает тесты в одной JVM — чистим, чтобы id/предметы не накапливались
        context.deleteDatabase(com.politex.data.local.MpkDatabase.DATABASE_NAME)
        db = MpkDatabase.getInstance(context)
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

    private fun insertItem(item: MaterialItemEntity): Long = runBlocking {
        db.materialDao().insertItem(item)
    }

    /** Вставка в БД → перекомпозиция списка идёт через асинхронный Flow Room: ждём узел явно. */
    private fun waitForNode(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `текстовый материал проходит путь импорт предмет тап и показывает текст`() {
        // Полный путь W12: как реальный пользователь — через MaterialImporter (не прямую вставку)
        val textId = runBlocking {
            MaterialImporter.importMaterial(
                context,
                db.materialDao(),
                "ТОЭ",
                PendingMaterial.Text("Первая строка конспекта\nВторая строка конспекта"),
            )
        }
        composeRule.waitForIdle()
        waitForNode("material_subject_ТОЭ")

        // W11: корень = список предметов, у которых есть материалы
        composeRule.onNodeWithTag("material_subject_ТОЭ").assertExists()
        composeRule.onNodeWithTag("material_subject_ТОЭ").performClick()
        composeRule.waitForIdle()

        // Внутри предмета — сам материал; тап открывает просмотрщик
        composeRule.onNodeWithTag("material_item_$textId").performClick()
        composeRule.waitForIdle()

        // W12: kind=TEXT → DocTextViewer с textContent, а не «пустой экран»/картинка
        composeRule.onNodeWithTag("doc_top_bar").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_list").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_0").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_1").assertExists()

        // Текст реально доезжает из БД в просмотрщик
        val stored = runBlocking { db.materialDao().getItemById(textId) }
        assertEquals("Первая строка конспекта", stored?.title)
        assertTrue(stored?.textContent?.contains("Вторая строка конспекта") == true)
    }

    @Test
    fun `архивный материал виден только в Архиве и возвращается оттуда`() {
        val id = insertItem(
            MaterialItemEntity(
                kind = "FILE",
                title = "Старая лекция",
                ext = "pdf",
                subjectName = "ТОЭ",
                archived = true,
                sortOrder = 0,
            ),
        )
        insertItem(
            MaterialItemEntity(kind = "FILE", title = "Лекция 1", ext = "pdf", subjectName = "ТОЭ", sortOrder = 0),
        )
        composeRule.waitForIdle()
        waitForNode("material_subject_ТОЭ")

        // W11: предмет показан (в нём есть материал), Архив — отдельной секцией внизу
        composeRule.onNodeWithTag("material_subject_ТОЭ").assertExists()
        composeRule.onNodeWithTag("material_archive_section").assertExists()

        // W11: внутри предмета архивного материала НЕТ (баг «файл в архиве показывался и под предметами»)
        composeRule.onNodeWithTag("material_subject_ТОЭ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("material_item_${id}").assertDoesNotExist()
        composeRule.onNodeWithTag("subject_back_btn").performClick()
        composeRule.waitForIdle()

        // Архив: материал там, кнопка восстановления на месте
        composeRule.onNodeWithTag("material_archive_section").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("material_item_${id}").assertExists()
        composeRule.onNodeWithTag("material_restore_${id}").assertExists()
    }

    @Test
    fun `поиск находит текстовый материал по содержимому`() {
        insertItem(
            MaterialItemEntity(
                kind = "TEXT",
                title = "Заметка",
                ext = "txt",
                subjectName = "Математика",
                textContent = "интеграл по частям формула",
                sortOrder = 0,
            ),
        )
        composeRule.waitForIdle()
        waitForNode("material_search_field")

        // W13: поле поиска в шапке, без тогла «Aa»
        composeRule.onNodeWithTag("material_search_field").performTextInput("интеграл")
        waitForNode("material_search_list")

        composeRule.onNodeWithTag("material_search_list").assertExists()
        composeRule.onNodeWithTag("material_item_1").assertExists()
        // W13: пояснительная плашка про PDF убрана
        composeRule.onNodeWithTag("pdf_search_hint").assertDoesNotExist()
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
