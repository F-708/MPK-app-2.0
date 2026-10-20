package com.politex

import com.politex.util.GroupCatalog
import com.politex.util.GroupParser
import com.politex.util.MpkCurriculum
import com.politex.ui.screens.buildGroupCode
import com.politex.ui.screens.groupPickerLetters
import com.politex.ui.screens.groupPickerMaxNumber
import com.politex.ui.screens.validateAgainstCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GroupCodesIntegrityTest {

    private val packaged = GroupCatalog.load(RuntimeEnvironment.getApplication()).all()

    private val diskLines: List<String>
        get() =
            sequenceOf(
                File("app/src/main/assets/group_codes.csv"),
                File("src/main/assets/group_codes.csv"),
            ).first { it.exists() }.readLines(Charsets.UTF_8)

    @Test
    fun `упакованный ассет совпадает с файлом group_codes_csv`() {
        val disk = diskLines.map { it.trim() }.filter { it.isNotBlank() }
        assertEquals(disk, packaged)
    }

    @Test
    fun `все коды групп валидны и каноничны`() {
        packaged.forEach { code ->
            assertEquals(3, code.length)
            assertEquals(code, GroupCatalog.normalize(code))
            assertEquals(code, GroupParser.parse(code)?.canonicalName)
        }
    }

    @Test
    fun `коды уникальны и отсортированы`() {
        assertEquals(packaged.distinct(), packaged)
        assertEquals(packaged.sorted(), packaged)
    }

    @Test
    fun `каталог содержит 61 группу - 43О и 43Э присутствуют`() {
        assertEquals(61, packaged.size)
        assertTrue("43О" in packaged)
        assertTrue("43Э" in packaged)
        val catalog = GroupCatalog.load(RuntimeEnvironment.getApplication())
        assertEquals("43О", catalog.find("43О"))
        assertEquals("43Э", catalog.find("43Э"))
    }

    @Test
    fun `43Э - 4 курс специальности Э, у которой 4 курса существует`() {
        val info = GroupParser.parse("43Э")
        assertEquals(4, info?.course)
        assertEquals(3, info?.groupNumber)
        assertEquals('Э', info?.specialtyCode)
        assertEquals("43Э", info?.canonicalName)

        val maxCourse = MpkCurriculum.getSpecialty('Э')?.subjectsByCourse?.keys?.maxOrNull()
        assertEquals(4, maxCourse)
    }

    @Test
    fun `каскадный пикер на 4 курсе номере 3 предлагает 43О и 43Э`() {
        assertEquals(3, groupPickerMaxNumber(packaged, 4))
        assertEquals(listOf('О', 'Э'), groupPickerLetters(packaged, 4, 3))

        assertEquals("43О", buildGroupCode(4, 3, 'О'))
        assertEquals("43Э", buildGroupCode(4, 3, 'Э'))
        assertTrue(validateAgainstCatalog("43О", packaged))
        assertTrue(validateAgainstCatalog("43Э", packaged))
    }
}
