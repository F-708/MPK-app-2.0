package com.politex

import com.politex.ui.screens.buildGroupCode
import com.politex.ui.screens.groupPickerLetters
import com.politex.ui.screens.validateAgainstCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupCodePickerTest {
    private val catalog = listOf("11О", "11Д", "21Г", "41О", "41Д", "42Э")

    @Test
    fun `сборка кода группы из курса, номера и буквы`() {
        assertEquals("41О", buildGroupCode(4, 1, 'О'))
        assertEquals("11Д", buildGroupCode(1, 1, 'Д'))
    }

    @Test
    fun `латиница и lowercase нормализуются в кириллицу`() {
        assertEquals("41О", buildGroupCode(4, 1, 'o'))
        assertEquals("41О", buildGroupCode(4, 1, 'O'))
    }

    @Test
    fun `неполный ввод - null`() {
        assertNull(buildGroupCode(null, 1, 'О'))
        assertNull(buildGroupCode(4, null, 'О'))
        assertNull(buildGroupCode(4, 1, null))
    }

    @Test
    fun `выход за диапазон - null`() {
        assertNull(buildGroupCode(5, 1, 'О'))
        assertNull(buildGroupCode(0, 1, 'О'))
        assertNull(buildGroupCode(4, 0, 'О'))
        assertNull(buildGroupCode(4, 10, 'О'))
        assertNull(buildGroupCode(4, 1, '5'))
    }

    @Test
    fun `валидация по каталогу`() {
        assertTrue(validateAgainstCatalog("41О", catalog))
        assertFalse(validateAgainstCatalog("43О", catalog))
        assertFalse(validateAgainstCatalog("41", catalog))
        assertFalse(validateAgainstCatalog("", catalog))
        // нормализация пробелов/дефисов как в GroupCatalog.normalize
        assertTrue(validateAgainstCatalog("4-1 О", catalog))
        assertFalse(validateAgainstCatalog("41Ы", catalog))
    }

    @Test
    fun `буквы берутся из каталога для курса и номера`() {
        assertEquals(listOf('Д', 'О'), groupPickerLetters(catalog, 4, 1))
        assertEquals(listOf('Э'), groupPickerLetters(catalog, 4, 2))
        assertTrue(groupPickerLetters(catalog, 1, null).containsAll(listOf('Д', 'О')))
    }

    @Test
    fun `пустой каталог - фолбэк на буквы специальностей`() {
        val letters = groupPickerLetters(emptyList(), 4, 1)
        assertTrue(letters.isNotEmpty())
        assertTrue('О' in letters)
    }
}
