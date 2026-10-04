package com.politex

import com.politex.ui.screens.routeTickPositions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTickPositionsTest {
    private val eps = 1e-4f

    @Test
    fun `пусто при неположительной длине или шаге`() {
        assertTrue(routeTickPositions(0f, 56f, 0f).isEmpty())
        assertTrue(routeTickPositions(-10f, 56f, 0f).isEmpty())
        assertTrue(routeTickPositions(100f, 0f, 0f).isEmpty())
        assertTrue(routeTickPositions(100f, -5f, 0f).isEmpty())
    }

    @Test
    fun `старт со смещения и шаг ровно stepPx`() {
        assertEquals(listOf(0f, 56f), routeTickPositions(100f, 56f, 0f))
        assertEquals(listOf(30f, 86f), routeTickPositions(100f, 56f, 30f))
    }

    @Test
    fun `конец пути исключён (полуинтервал) - метка не за краем`() {
        // totalLen ровно = step -> только старт, второй метки на границе нет
        assertEquals(listOf(0f), routeTickPositions(56f, 56f, 0f))
        // 112 не попадает
        assertEquals(listOf(0f, 56f), routeTickPositions(112f, 56f, 0f))
    }

    @Test
    fun `смещение заворачивается в 0 до step (включая отрицательное)`() {
        // offset == step -> 0
        assertEquals(routeTickPositions(200f, 56f, 0f), routeTickPositions(200f, 56f, 56f))
        // offset > step
        assertEquals(listOf(14f, 70f, 126f, 182f), routeTickPositions(200f, 56f, 70f))
        // отрицательное: -10 -> 46
        assertEquals(listOf(46f, 102f, 158f), routeTickPositions(200f, 56f, -10f))
    }

    @Test
    fun `периодичность по offset - бесшовная петля и постоянная скорость`() {
        // сдвиг фазы на целый шаг ничего не меняет => течение непрерывно
        val base = routeTickPositions(500f, 56f, 12f)
        assertEquals(base, routeTickPositions(500f, 56f, 12f + 56f))
        assertEquals(base, routeTickPositions(500f, 56f, 12f + 56f * 3f))
    }

    @Test
    fun `равномерный шаг независимо от длины - скорость постоянна`() {
        val pos = routeTickPositions(1000f, 56f, 7f)
        for (i in 1 until pos.size) {
            assertEquals(56f, pos[i] - pos[i - 1], eps)
        }
        // количество = ceil((total - start) / step)
        val expected = kotlin.math.ceil((1000f - 7f) / 56f).toInt()
        assertEquals(expected, pos.size)
        assertEquals(7f, pos.first(), eps)
        assertTrue(pos.last() < 1000f)
    }

    @Test
    fun `на стыках сегментов метки не дублируются и не теряются`() {
        // накопленные длины: стык в 30 (нулевой сегмент) и в 90
        val cum = listOf(0f, 30f, 30f, 90f, 150f)
        val total = cum.last()
        val positions = routeTickPositions(total, 30f, 0f) // [0,30,60,90,120]
        assertEquals(listOf(0f, 30f, 60f, 90f, 120f), positions)

        val assigned = assignToSegments(positions, cum)
        // каждая метка поставлена ровно один раз (ни потерь, ни дублей)
        assertEquals(positions.size, assigned.size)
        // порядок вдоль пути не убывает
        for (i in 1 until assigned.size) assertTrue(assigned[i] >= assigned[i - 1])
        // стыковая метка 30 ушла сегменту 3 (нулевой сегмент 2 пропущен), 90 - сегменту 4
        assertEquals(listOf(1, 3, 3, 4, 4), assigned)
    }

    // Зеркало обхода из drawRoutePath: полуинтервал [cum[i-1], cum[i]), нулевые сегменты пропускаются.
    private fun assignToSegments(
        positions: List<Float>,
        cum: List<Float>,
    ): List<Int> {
        val out = ArrayList<Int>()
        var idx = 0
        for (i in 1 until cum.size) {
            if (idx >= positions.size) break
            val segStart = cum[i - 1]
            val segEnd = cum[i]
            if (segEnd - segStart <= 0f) continue
            while (idx < positions.size) {
                val p = positions[idx]
                if (p >= segEnd) break
                if (p >= segStart) out += i
                idx++
            }
        }
        return out
    }
}
