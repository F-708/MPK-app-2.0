package com.politex

import com.politex.ui.screens.roomNumberFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1: размер номера кабинета на карте — цифра обязана влезать в рамку,
 * но при этом выглядеть единообразно по этажу.
 */
class RoomNumberFitTest {

    @Test
    fun `никогда не превышает влезающий размер`() {
        // Мелкий кабинет: raw меньше медианы — цифра обязана ужаться до raw
        val fit = roomNumberFit(raw = 0.5f, median = 1.2f, minReadable = 0.2f)
        assertTrue("fit=$fit должен быть <= raw=0.5", fit!! <= 0.5f)
    }

    @Test
    fun `мелкий кабинет не раздувается до медианы`() {
        // Это был баг: loFit = median*0.70 мог превысить raw и раздуть цифру за рамку
        val fit = roomNumberFit(raw = 0.4f, median = 1.0f, minReadable = 0.1f)
        assertEquals(0.4f, fit!!, 0.0001f)
    }

    @Test
    fun `крупный кабинет получает немножечко больше медианы`() {
        val fit = roomNumberFit(raw = 5.0f, median = 1.0f, minReadable = 0.1f)!!
        assertTrue("ожидали > медианы, получили $fit", fit > 1.0f)
        assertEquals("не более 115% медианы", 1.15f, fit, 0.0001f)
    }

    @Test
    fun `крупный кабинет всё равно ограничен влезающим размером`() {
        // raw чуть выше медианы — рост не должен выйти за raw
        val fit = roomNumberFit(raw = 1.05f, median = 1.0f, minReadable = 0.1f)!!
        assertTrue("fit=$fit должен быть <= raw=1.05", fit <= 1.05f)
    }

    @Test
    fun `кабинет ровно по медиане получает медиану`() {
        // raw == median → роста нет (он ограничен влезающим размером), цифра = медиана
        val fit = roomNumberFit(raw = 1.0f, median = 1.0f, minReadable = 0.1f)!!
        assertEquals(1.0f, fit, 0.0001f)
    }

    @Test
    fun `слишком мелкий кабинет — номер не рисуем`() {
        assertNull(roomNumberFit(raw = 0.05f, median = 1.0f, minReadable = 0.2f))
    }

    @Test
    fun `размеры большинства кабинетов совпадают`() {
        // Заказчик: «примерно одного размера». Кабинеты с большим запасом raw
        // должны получить ОДИНАКОВЫЙ размер, а не разъезжаться по величине кабинета
        val fits = listOf(2.0f, 3.5f, 10f, 1.5f).map {
            roomNumberFit(raw = it, median = 1.0f, minReadable = 0.1f)!!
        }
        assertEquals(1, fits.distinct().size)
    }

    @Test
    fun `нулевой или отрицательный размер — не рисуем`() {
        assertNull(roomNumberFit(raw = 0f, median = 1f, minReadable = 0.1f))
        assertNull(roomNumberFit(raw = -1f, median = 1f, minReadable = 0.1f))
    }

    @Test
    fun `результат всегда читаем когда нарисован`() {
        var raw = 0.05f
        while (raw < 3f) {
            roomNumberFit(raw, median = 1.0f, minReadable = 0.2f)?.let {
                assertTrue("при raw=$raw получили нечитаемый $it", it >= 0.2f)
            }
            raw += 0.01f
        }
    }
}