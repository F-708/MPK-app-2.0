package com.politex.ui.screens

import com.politex.data.model.CollegeMapData
import com.politex.data.model.CollegeRouteFinder
import com.politex.data.model.FloorMap
import com.politex.data.model.MapPoint
import com.politex.data.model.MapRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Чистые функции RouteBuilder: X25 (legsToStages склейка в polylines), X36 (laneOffsetDp),
 * X19 (buildLegs глобальный дедуп). Синтетика — без Compose и без реального ассета.
 */
class RouteBuilderTest {
    private fun p(x: Float, y: Float) = MapPoint(x, y)

    private fun room(number: String, x: Float, y: Float) =
        MapRoom(
            id = number, number = number, title = "", note = "", status = "normal",
            x = x, y = y, w = 0.02f, h = 0.02f,
        )

    // ---- X36: laneOffsetDp ----
    @Test
    fun `X36 laneOffsetDp раскладывает полосы 0 6 -6 12 -12 18`() {
        assertEquals(0f, laneOffsetDp(0), 1e-6f)
        assertEquals(6f, laneOffsetDp(1), 1e-6f)
        assertEquals(-6f, laneOffsetDp(2), 1e-6f)
        assertEquals(12f, laneOffsetDp(3), 1e-6f)
        assertEquals(-12f, laneOffsetDp(4), 1e-6f)
        assertEquals(18f, laneOffsetDp(5), 1e-6f)
    }

    // ---- X25: legsToStages ----
    @Test
    fun `X25 два FloorPath одного этажа сливаются в один этап с двумя полилиниями`() {
        val mapData =
            CollegeMapData(
                mapOf("3-4" to FloorMap(rooms = listOf(room("335", 0.25f, 0.5f), room("401", 0.85f, 0.12f)))),
            )
        // подход к лестнице у 335 (заканчивается у 0.25,0.5) и площадка 4 этажа (от 0.75,0.2) —
        // конец != начало → две отдельные полилинии ОДНОГО этапа
        val fp1 = CollegeRouteFinder.FloorPath("3-4", listOf(p(0.2f, 0.5f), p(0.25f, 0.5f)))
        val fp2 = CollegeRouteFinder.FloorPath("3-4", listOf(p(0.75f, 0.2f), p(0.85f, 0.2f)))
        val route = CollegeRouteFinder.MultiFloorRoute(listOf(fp1, fp2), 1f)
        val leg = RouteLeg(Waypoint("335", "каб. 335"), Waypoint("401", "каб. 401"), route)

        val stages = legsToStages(listOf(leg), mapData)

        assertEquals("один этаж + isReturn → ОДИН этап", 1, stages.size)
        assertEquals("конец fp1 != начало fp2 → две полилинии", 2, stages[0].polylines.size)
        assertFalse("label БЕЗ «Этап N:»", stages[0].label.contains("Этап"))
        assertEquals("3-4 эт → лестница у 335 → каб. 401", stages[0].label)
    }

    @Test
    fun `X25 полилинии склеиваются когда конец совпал с началом`() {
        val mapData = CollegeMapData(mapOf("1" to FloorMap(rooms = listOf(room("101", 0.3f, 0.5f)))))
        val fp1 = CollegeRouteFinder.FloorPath("1", listOf(p(0.1f, 0.5f), p(0.2f, 0.5f)))
        val fp2 = CollegeRouteFinder.FloorPath("1", listOf(p(0.2f, 0.5f), p(0.3f, 0.5f))) // начало == конец fp1
        val route = CollegeRouteFinder.MultiFloorRoute(listOf(fp1, fp2), 1f)
        val leg = RouteLeg(Waypoint("101", "каб. 101"), Waypoint("102", "каб. 102"), route)

        val stages = legsToStages(listOf(leg), mapData)

        assertEquals(1, stages.size)
        assertEquals("конец==начало (<1e-3) → склейка в одну полилинию", 1, stages[0].polylines.size)
        assertEquals(3, stages[0].polylines[0].size)
    }

    @Test
    fun `X25 разные этажи - разные этапы`() {
        val mapData = CollegeMapData(mapOf("1" to FloorMap(), "2" to FloorMap()))
        val fp1 = CollegeRouteFinder.FloorPath("1", listOf(p(0.1f, 0.5f), p(0.2f, 0.5f)))
        val fp2 = CollegeRouteFinder.FloorPath("2", listOf(p(0.2f, 0.5f), p(0.3f, 0.5f)))
        val route = CollegeRouteFinder.MultiFloorRoute(listOf(fp1, fp2), 1f)
        val leg = RouteLeg(Waypoint("101", "каб. 101"), Waypoint("201", "каб. 201"), route)

        val stages = legsToStages(listOf(leg), mapData)

        assertEquals("смена этажа → новый этап (не слияние)", 2, stages.size)
        assertEquals("1", stages[0].floorId)
        assertEquals("2", stages[1].floorId)
    }

    // ---- X19: buildLegs глобальный дедуп ----
    @Test
    fun `X19 buildLegs схлопывает последовательный дубль`() {
        val mapData = CollegeMapData(emptyMap())
        val legs = buildLegs(listOf(Waypoint("A", "A"), Waypoint("A", "A"), Waypoint("B", "B")), mapData, emptyMap())
        assertEquals(1, legs.size)
        assertEquals("A", legs[0].from.key)
        assertEquals("B", legs[0].to.key)
    }

    @Test
    fun `X19 buildLegs A B A не даёт петли - глобальный дедуп`() {
        val mapData = CollegeMapData(emptyMap())
        val legs = buildLegs(listOf(Waypoint("A", "A"), Waypoint("B", "B"), Waypoint("A", "A")), mapData, emptyMap())
        assertEquals("A встречается один раз → один перегон, без возврата", 1, legs.size)
        assertEquals("A", legs[0].from.key)
        assertEquals("B", legs[0].to.key)
    }
}
