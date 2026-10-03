package com.politex.data.model

import com.politex.ui.screens.Waypoint
import com.politex.ui.screens.greedyOrder
import com.politex.ui.screens.offsetPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V23: simplifyRoute — чистка крюков полилинии (дубли, коллинеарные середины, шипы).
 * Числа из спецификации тех-советника.
 */
class SimplifyRouteTest {
    private fun p(x: Float, y: Float) = MapPoint(x, y)

    private fun assertSimplified(expected: List<MapPoint>, input: List<MapPoint>) {
        val out = CollegeRouteFinder.simplifyRoute(input)
        assertEquals(expected, out)
    }

    @Test
    fun `коллинеарная середина убирается`() {
        assertSimplified(listOf(p(0f, 0f), p(2f, 0f)), listOf(p(0f, 0f), p(1f, 0f), p(2f, 0f)))
    }

    @Test
    fun `шип - возврат назад - убирается`() {
        assertSimplified(listOf(p(0f, 0f), p(2f, 0f)), listOf(p(0f, 0f), p(5f, 0f), p(2f, 0f)))
    }

    @Test
    fun `каскад шип плюс коллинеар схлопывается за вызов`() {
        assertSimplified(
            listOf(p(0f, 0f), p(2f, 0f)),
            listOf(p(0f, 0f), p(3f, 0f), p(1f, 0f), p(2f, 0f)),
        )
    }

    @Test
    fun `прямой угол не трогается`() {
        val corner = listOf(p(0f, 0f), p(1f, 0f), p(1f, 1f))
        assertSimplified(corner, corner)
    }

    @Test
    fun `дубли подряд убираются`() {
        assertSimplified(listOf(p(0f, 0f), p(1f, 0f)), listOf(p(0f, 0f), p(0f, 0f), p(1f, 0f)))
    }

    @Test
    fun `короткие списки возвращаются как есть`() {
        assertEquals(emptyList<MapPoint>(), CollegeRouteFinder.simplifyRoute(emptyList()))
        assertEquals(listOf(p(1f, 2f)), CollegeRouteFinder.simplifyRoute(listOf(p(1f, 2f))))
    }

    @Test
    fun `концы пути сохраняются даже среди шума`() {
        val input = listOf(p(0f, 0f), p(0.5f, 0f), p(0.25f, 0f), p(0.5f, 0.0001f), p(0.9f, 0.4f))
        val out = CollegeRouteFinder.simplifyRoute(input)
        assertEquals(p(0f, 0f), out.first())
        assertEquals(p(0.9f, 0.4f), out.last())
        assertTrue("шум должен уменьшать число точек", out.size < input.size)
    }
}

/** V25: чистые функции RouteBuilder — greedyOrder и offsetPolyline (минимальные проверки). */
class RouteBuilderPureTest {
    @Test
    fun `greedyOrder ведёт через ближайших соседей`() {
        val table =
            mapOf(
                "A" to "B" to 10f, "A" to "C" to 1f, "A" to "D" to 5f,
                "C" to "B" to 1f, "C" to "D" to 5f,
                "B" to "D" to 1f,
            )
        fun d(a: String, b: String): Float = table[a to b] ?: table[b to a] ?: Float.MAX_VALUE
        val start = Waypoint("A", "A")
        val rest = listOf(Waypoint("B", "B"), Waypoint("C", "C"), Waypoint("D", "D"))
        val ordered = greedyOrder(start, rest, ::d)
        assertEquals(listOf("C", "B", "D"), ordered.map { it.key })
    }

    @Test
    fun `greedyOrder недостижимые точки опускает в конец`() {
        val start = Waypoint("A", "A")
        val rest = listOf(Waypoint("X", "X"), Waypoint("B", "B"))
        val ordered =
            greedyOrder(start, rest) { a, b ->
                if (a == "A" && b == "B") 1f else if (a == "B" && b == "X") 2f else Float.MAX_VALUE
            }
        assertEquals(listOf("B", "X"), ordered.map { it.key })
    }

    @Test
    fun `offsetPolyline сдвигает горизонталь вдоль перпендикуляра`() {
        val line = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f))
        // n = normalize(dy*imgH, -dx*imgW) = (0, -1) → сдвиг на −offsetPx/imgH по y
        val out = offsetPolyline(line, imgW = 100f, imgH = 100f, offsetPx = 10f)
        assertEquals(2, out.size)
        assertEquals(0f, out[0].x, 1e-5f)
        assertEquals(-0.1f, out[0].y, 1e-5f)
        assertEquals(1f, out[1].x, 1e-5f)
        assertEquals(-0.1f, out[1].y, 1e-5f)
    }

    @Test
    fun `offsetPolyline с нулевым сдвигом не трогает точки`() {
        val line = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 0.3f))
        assertEquals(line, offsetPolyline(line, 100f, 60f, 0f))
    }
}
