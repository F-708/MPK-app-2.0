package com.politex.content

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContentPackValidatorOverridesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildPackDir(
        teachers: Int = 55,
        groups: Int = 25,
    ): File {
        val dir = File(tempFolder.root, "pack").apply { mkdirs() }
        File(dir, "teachers.csv").writeText(
            (1..teachers).joinToString("\n") { i -> "Фамилия$i Имя Отчество;Преподаватель;Предмет;;;;;;;" },
            Charsets.UTF_8,
        )
        File(dir, "group_codes.csv").writeText((1..groups).joinToString("\n") { "$it" + "1А" }, Charsets.UTF_8)
        File(dir, "college_map.json").writeText("{}", Charsets.UTF_8)
        File(dir, "teachers_photos").mkdirs()
        return dir
    }

    private fun validate(dir: File) = ContentPackValidator.validateContent(dir) { true }

    @Test
    fun `старый пакет без owner_overrides json остаётся валидным`() {
        val dir = buildPackDir()
        assertEquals(ContentPackRejection.NONE, validate(dir))
    }

    @Test
    fun `пакет с валидным owner_overrides json принимается`() {
        val dir = buildPackDir()
        File(dir, ContentPackValidator.OVERRIDES_FILE).writeText(
            """{"format":1,"updatedAt":1700000000000,"teachers":{"иванова анна":{"fio":"Иванова А. П.","phone":"123"}}}""",
            Charsets.UTF_8,
        )
        assertEquals(ContentPackRejection.NONE, validate(dir))
    }

    @Test
    fun `пакет с пустым списком правок принимается`() {
        val dir = buildPackDir()
        File(dir, ContentPackValidator.OVERRIDES_FILE).writeText("""{"format":1,"teachers":{}}""", Charsets.UTF_8)
        assertEquals(ContentPackRejection.NONE, validate(dir))
    }

    @Test
    fun `пакет с битым owner_overrides json отклоняется`() {
        val dir = buildPackDir()
        File(dir, ContentPackValidator.OVERRIDES_FILE).writeText("совсем не json", Charsets.UTF_8)
        assertEquals(ContentPackRejection.OVERRIDES, validate(dir))
    }

    @Test
    fun `пакет с неизвестной версией формата отклоняется`() {
        val dir = buildPackDir()
        File(dir, ContentPackValidator.OVERRIDES_FILE).writeText("""{"format":7,"teachers":{}}""", Charsets.UTF_8)
        assertEquals(ContentPackRejection.OVERRIDES, validate(dir))
    }

    @Test
    fun `пакет без секции teachers отклоняется`() {
        val dir = buildPackDir()
        File(dir, ContentPackValidator.OVERRIDES_FILE).writeText("""{"format":1}""", Charsets.UTF_8)
        assertEquals(ContentPackRejection.OVERRIDES, validate(dir))
    }
}
