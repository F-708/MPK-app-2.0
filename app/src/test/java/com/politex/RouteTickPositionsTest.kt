package com.politex

import com.politex.data.model.CollegeRouteFinder
import com.politex.data.model.MapPoint
import com.politex.ui.screens.fitStageViewport
import com.politex.ui.screens.routeTickPositions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun `короткий путь - без меток (иначе они мигали бы всей пачкой)`() {
        // 100 < 2*margin + step = 112 -> пусто
        assertTrue(routeTickPositions(100f, 56f, 0f, 28f).isEmpty())
        // ровно на границе: метки на обоих краях отступов
        assertEquals(listOf(28f, 84f), routeTickPositions(112f, 56f, 0f, 28f))
    }

    @Test
    fun `старт со смещения и шаг ровно stepPx`() {
        assertEquals(listOf(0f, 56f), routeTickPositions(100f, 56f, 0f))
        assertEquals(listOf(30f, 86f), routeTickPositions(100f, 56f, 30f))
    }

    @Test
    fun `первая метка не раньше start+step÷2, последняя не позже end-step÷2`() {
        val pos = routeTickPositions(200f, 56f, 0f, 28f)
        assertEquals(listOf(28f, 84f, 140f), pos)
        assertTrue(pos.first() >= 28f)
        assertTrue(pos.last() <= 200f - 28f)
        // при любой фазе границы отступа не нарушаются
        for (phase in 0..20) {
            val p = routeTickPositions(500f, 56f, phase * 2.7f, 28f)
            assertTrue(p.all { it >= 28f - eps && it <= 500f - 28f + eps })
        }
    }

    @Test
    fun `метки не ближе size÷2 к концам пути`() {
        val size = 26f // ROUTE_TICK_SIZE_DP(10dp) * pxPerDp(~2.6) — экранных px
        val margin = size / 2f
        val total = 400f
        for (phase in 0..30) {
            val p = routeTickPositions(total, 56f, phase * 1.9f, margin)
            assertTrue(p.isNotEmpty())
            assertTrue("первая слишком близко к старту", p.first() >= margin - eps)
            assertTrue("последняя слишком близко к финишу", p.last() <= total - margin + eps)
        }
    }

    @Test
    fun `смещение заворачивается в 0 до step (включая отрицательное)`() {
        // offset == step -> 0
        assertEquals(routeTickPositions(200f, 56f, 0f), routeTickPositions(200f, 56f, 56f))
        // offset > step
        assertEquals(listOf(42f, 98f, 154f), routeTickPositions(200f, 56f, 70f, 28f))
        // отрицательное: -10 -> фаза 46
        assertEquals(listOf(74f, 130f), routeTickPositions(200f, 56f, -10f, 28f))
    }

    @Test
    fun `периодичность по offset - бесшовная петля и постоянная скорость`() {
        // сдвиг фазы на целый шаг ничего не меняет => течение непрерывно
        val base = routeTickPositions(500f, 56f, 12f, 28f)
        assertEquals(base, routeTickPositions(500f, 56f, 12f + 56f, 28f))
        assertEquals(base, routeTickPositions(500f, 56f, 12f + 56f * 3f, 28f))
    }

    @Test
    fun `равномерный шаг, число меток примерно total÷step`() {
        val total = 1000f
        val pos = routeTickPositions(total, 56f, 7f, 28f)
        for (i in 1 until pos.size) {
            assertEquals(56f, pos[i] - pos[i - 1], eps)
        }
        // число меток ≈ total/step (отступы съедают не больше пары меток)
        assertTrue(abs(pos.size - total / 56f) <= 2f)
        assertEquals(35f, pos.first(), eps) // margin + phase
        assertTrue(pos.last() <= total - 28f)
    }

    @Test
    fun `при zoom x2 позиции по доле длины те же (однородность по масштабу)`() {
        // totalScreenLen = totalImg*zoom: при zoom×2 все ЭКРАННЫЕ длины удваиваются.
        // Однородность routeTickPositions => доли позиций (p/total) не меняются —
        // метки не переставляются скачком при зуме.
        val total = 331f
        val p1 = routeTickPositions(total, 56f, 12f, 28f)
        val p2 = routeTickPositions(total * 2f, 112f, 24f, 56f)
        assertEquals(p1.size, p2.size)
        for (i in p1.indices) {
            assertEquals(p1[i] / total, p2[i] / (total * 2f), 1e-5f)
        }
    }

    @Test
    fun `на стыках полилиний общего контура метки не дублируются и не теряются`() {
        // Общий контур (AA1): две полилинии 90 + 60 px с базами 0 и 90.
        val bases = listOf(0f, 90f)
        val cums = listOf(listOf(0f, 30f, 90f), listOf(0f, 60f))
        val total = 150f
        val positions = routeTickPositions(total, 30f, 0f, 15f) // [15,45,75,105,135]
        assertEquals(listOf(15f, 45f, 75f, 105f, 135f), positions)

        // Зеркало обхода из drawRoutePath: полуинтервал [base+cum[i-1], base+cum[i]).
        val drawn = ArrayList<Float>()
        var idx = 0
        for ((pi, cum) in cums.withIndex()) {
            for (i in 1 until cum.size) {
                if (idx >= positions.size) break
                val segStart = bases[pi] + cum[i - 1]
                val segEnd = bases[pi] + cum[i]
                if (segEnd - segStart <= 0f) continue
                while (idx < positions.size) {
                    val p = positions[idx]
                    if (p >= segEnd) break
                    if (p >= segStart) drawn += p
                    idx++
                }
            }
        }
        // каждая метка нарисована ровно один раз, порядок вдоль контура сохранён
        assertEquals(positions, drawn)
    }

    // ---- AA7: zoom-to-fit этапа ----

    @Test
    fun `fitStageViewport - scale по bbox и центрирование`() {
        val viewW = 1080f
        val viewH = 600f
        val imgH = viewW / CollegeRouteFinder.PLAN_ASPECT
        val pts = listOf(MapPoint(0.2f, 0.3f), MapPoint(0.6f, 0.5f), MapPoint(0.4f, 0.4f))
        val fit = fitStageViewport(pts, viewW, viewH)
        assertNotNull(fit)
        val (s, ox, oy) = fit!!
        val bw = 0.4f * viewW
        val bh = 0.2f * imgH
        val rest = (viewH / imgH * 0.72f).coerceIn(1f, 2.2f)
        val expected = (minOf(viewW / bw, viewH / bh) * 0.85f).coerceIn(rest, 8f)
        assertEquals(expected, s, 1e-3f)
        val cx = 0.4f * viewW
        val cy = 0.4f * imgH
        assertEquals(viewW / 2f - cx * s, ox, 1e-2f)
        assertEquals(viewH / 2f - cy * s, oy, 1e-2f)
    }

    @Test
    fun `fitStageViewport - clamp scale в restZoom и maxZoom`() {
        val viewW = 1080f
        val viewH = 600f
        val imgH = viewW / CollegeRouteFinder.PLAN_ASPECT
        val rest = (viewH / imgH * 0.72f).coerceIn(1f, 2.2f)
        // одна точка (вырожденный bbox) — зум упирается в потолок 8
        val (sMax, _, _) = fitStageViewport(listOf(MapPoint(0.5f, 0.5f)), viewW, viewH)!!
        assertEquals(8f, sMax, 1e-3f)
        // bbox на весь план — scale ниже rest, клампим снизу в restZoom
        val all = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f))
        val (sMin, _, _) = fitStageViewport(all, viewW, viewH)!!
        assertEquals(rest, sMin, 1e-3f)
        assertTrue(sMin <= sMax)
    }

    @Test
    fun `fitStageViewport - пусто или нулевой view дают null`() {
        assertNull(fitStageViewport(emptyList(), 1080f, 600f))
        assertNull(fitStageViewport(listOf(MapPoint(0.5f, 0.5f)), 0f, 600f))
        assertNull(fitStageViewport(listOf(MapPoint(0.5f, 0.5f)), 1080f, 0f))
    }
}
