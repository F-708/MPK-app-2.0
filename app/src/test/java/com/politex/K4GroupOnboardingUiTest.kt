package com.politex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class K4GroupOnboardingUiTest {

    private fun src(relative: String): String =
        sequenceOf(
            File("src/main/java/$relative"),
            File("app/src/main/java/$relative"),
        ).first { it.exists() }.readText()

    private val dialogs = src("com/politex/ui/components/GroupSelectionDialog.kt")
    private val college = src("com/politex/ui/screens/CollegeScreen.kt")
    private val picker = src("com/politex/ui/screens/GroupScheduleScreen.kt")

    @Test
    fun `KK5 - ручной ввод группы полностью убран из диалогов`() {
        assertFalse(dialogs.contains("first_run_manual_input"))
        assertFalse(dialogs.contains("first_run_manual_button"))
        assertFalse(dialogs.contains("Или введите группу вручную"))
        assertFalse(dialogs.contains("Или введите свою группу"))
        assertFalse(dialogs.contains("Неверный формат группы"))
        assertFalse(dialogs.contains("OutlinedTextField"))
    }

    @Test
    fun `KK5 - кнопка преподавателя это заметная полноширинная карточка`() {
        val card =
            dialogs
                .substringBefore("first_run_teacher_button")
                .substringAfterLast("Surface(")
        assertTrue(card.contains("fillMaxWidth()"))
        assertTrue(card.contains("heightIn(min = Tokens.ButtonHeight)"))
        assertTrue(card.contains("ColorSurfaceHighlight"))
        assertTrue(card.contains("BorderStroke(1.dp, ColorBorderLight)"))
    }

    @Test
    fun `KK5 - у карточки преподавателя есть иконка заголовок подпись и шеврон`() {
        val content =
            dialogs
                .substringAfter("first_run_teacher_button")
                .substringBefore("FirstRunStep.TEACHER")
        assertTrue(content.contains("Icons.Default.Groups"))
        assertTrue(content.contains("Я ПРЕПОДАВАТЕЛЬ"))
        assertTrue(content.contains("Расписание преподавателя вместо группы"))
        assertTrue(content.contains("Icons.Default.ChevronRight"))
        assertTrue(content.contains("ColorTextTitle"))
    }

    @Test
    fun `KK10 - подгруппы Первая и Вторая в первой строке Нейтрально во второй`() {
        val block =
            dialogs
                .substringAfterLast("FirstRunStep.SUBGROUP ->")
                .substringBefore("first_run_done_button")
        assertTrue(block.contains("pairOptions.forEach"))
        assertTrue(block.contains("neutralOption"))
        assertTrue(block.indexOf("pairOptions.forEach") < block.indexOf("neutralOption.second"))
        val pairSection = block.substringBefore("neutralOption.second")
        assertTrue(pairSection.contains("Modifier.weight(1f)"))
        val neutralSection = block.substringAfter("pairOptions.forEach")
        assertTrue(neutralSection.contains("Modifier.fillMaxWidth()"))
    }

    @Test
    fun `KK10 - описание подгруппы про маршруты виджеты и нейтральный режим`() {
        assertTrue(dialogs.contains("По подгруппе строятся маршруты и работают виджеты"))
        assertTrue(dialogs.contains("показывать обе подгруппы"))
        assertFalse(dialogs.contains("Если занятия идут по подгруппам"))
        assertFalse(dialogs.contains("Маршруты и виджеты будут вести на кабинеты"))
    }

    @Test
    fun `KK11 - иконка смены группы карандаш а не синк`() {
        assertFalse(college.contains("Icons.Default.Sync"))
        assertFalse(college.contains("Icons.AutoMirrored.Filled.Edit"))
        assertTrue(college.contains("Изменить группу"))
        val icon =
            college
                .substringAfter("specialty_change_group_btn")
                .substringBefore("LazyColumn")
        assertTrue(icon.contains("Icons.Default.Edit"))
    }

    @Test
    fun `KK11 - поведение кнопки смены группы сохранено`() {
        val button =
            college
                .substringBefore("specialty_change_group_btn")
                .substringAfterLast("Surface(")
        assertTrue(button.contains("showGroupDialog = true"))
        assertTrue(button.contains("Tokens.HitTarget"))
    }

    @Test
    fun `KK12 - GroupSelectionDialog теперь каскадный пикер`() {
        val block = dialogs.substringAfter("fun GroupSelectionDialog(")
        assertTrue(block.contains("GroupSchedulePicker"))
        assertTrue(block.contains("GroupCatalog.load(context)"))
        assertTrue(block.contains("GroupParser.parse(currentGroupName)"))
        assertTrue(block.contains("ВЫБРАТЬ"))
        assertTrue(block.contains("group_selection_dialog"))
    }

    @Test
    fun `KK12 - таблица-список и ручной ввод из диалога смены группы убраны`() {
        assertFalse(dialogs.contains("LazyVerticalGrid"))
        assertFalse(dialogs.contains("GridCells"))
        assertFalse(dialogs.contains("generateCommonGroups"))
        assertFalse(dialogs.contains("verticalScroll"))
        assertFalse(dialogs.contains("MpkCurriculum"))
    }

    @Test
    fun `KK12 - подпись диалога и кнопка закрытия сохранены`() {
        val block = dialogs.substringAfter("fun GroupSelectionDialog(")
        assertTrue(block.contains("showClose: Boolean = true"))
        assertTrue(block.contains("Сейчас: "))
        assertTrue(block.contains("Закрыть"))
        assertTrue(block.contains("Icons.Default.Close"))
    }

    @Test
    fun `KK6 - у букв пикера есть состояние скролла градиент и шевроны`() {
        val block = picker.substringAfter("internal fun GroupSchedulePicker(")
        assertTrue(block.contains("rememberLazyListState"))
        assertTrue(block.contains("group_picker_letters_row"))
        assertTrue(block.contains("canScrollForward"))
        assertTrue(block.contains("canScrollBackward"))
        assertTrue(block.contains("Brush.horizontalGradient"))
        assertTrue(block.contains("group_picker_letters_more_start"))
        assertTrue(block.contains("group_picker_letters_more_end"))
        assertTrue(block.contains("KeyboardArrowLeft"))
        assertTrue(block.contains("KeyboardArrowRight"))
    }

    @Test
    fun `KK6 - выбор и валидация группы в пикере не сломаны`() {
        val block = picker.substringAfter("internal fun GroupSchedulePicker(")
        assertTrue(block.contains("group_picker_letter_"))
        assertTrue(block.contains("validateAgainstCatalog(code, catalogCodes)"))
        assertTrue(block.contains("bouncyClickable(enabled = inCatalog)"))
        assertTrue(block.contains("group_picker_open_button"))
    }
}
