package com.example.data.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка поиска пути по этажу.
 *
 * Граф собирается из узлов и коридоров, размеченных в редакторе.
 */
class CollegeRouteFinderTest {

    private fun room(id: String, x: Float, y: Float) = MapRoom(
        id = id, number = id, title = "", note = "", status = "normal",
        x = x, y = y, w = 0.02f, h = 0.02f
    )

    /** Коридор вдоль оси X с двумя кабинетами по краям. */
    private fun floorWithCorridor() = FloorMap(
        corridors = listOf(
            MapCorridor("c1", listOf(MapPoint(0.1f, 0.5f), MapPoint(0.9f, 0.5f)), 0.012f)
        )
    )

    @Test
    fun `путь строится вдоль коридора`() {
        val f = floorWithCorridor()
        val a = room("101", 0.1f, 0.3f)
        val b = room("105", 0.9f, 0.3f)

        val route = CollegeRouteFinder.findRoute(a, b, f)

        assertNotNull("маршрут должен найтись", route)
        // Начинается в кабинете отправления, заканчивается в кабинете назначения
        assertTrue(route!!.points.first().x == a.x && route.points.first().y == a.y)
        assertTrue(route.points.last().x == b.x && route.points.last().y == b.y)
        assertTrue("в пути должна быть хотя бы одна точка графа", route.points.size >= 3)
    }

    @Test
    fun `без разметки маршрута нет`() {
        val empty = FloorMap()
        assertNull(CollegeRouteFinder.findRoute(room("101", 0.1f, 0.1f), room("105", 0.9f, 0.9f), empty))
    }

    @Test
    fun `в один и тот же кабинет маршрут не строим`() {
        val f = floorWithCorridor()
        val a = room("101", 0.1f, 0.3f)
        assertNull(CollegeRouteFinder.findRoute(a, a, f))
    }

    @Test
    fun `несвязанные коридоры пути не дают`() {
        // Две отдельные линии без узлов, которые бы их связывали
        val f = FloorMap(
            corridors = listOf(
                MapCorridor("c1", listOf(MapPoint(0.05f, 0.1f), MapPoint(0.1f, 0.1f)), 0.012f),
                MapCorridor("c2", listOf(MapPoint(0.9f, 0.9f), MapPoint(0.95f, 0.9f)), 0.012f)
            )
        )
        val route = CollegeRouteFinder.findRoute(room("101", 0.07f, 0.1f), room("105", 0.92f, 0.9f), f)
        // Пути между линиями нет — лучше не строить его вовсе, чем рисовать поверх стен
        assertNull("маршрут через несвязанные коридоры строить нельзя", route)
    }

    @Test
    fun `узлы связывают коридоры в один путь`() {
        val f = FloorMap(
            corridors = listOf(
                MapCorridor("c1", listOf(MapPoint(0.05f, 0.5f), MapPoint(0.5f, 0.5f)), 0.012f),
                MapCorridor("c2", listOf(MapPoint(0.5f, 0.5f), MapPoint(0.95f, 0.5f)), 0.012f)
            ),
            nodes = listOf(MapNode("n1", 0.5f, 0.5f, emptyList()))
        )
        val route = CollegeRouteFinder.findRoute(room("101", 0.05f, 0.3f), room("105", 0.95f, 0.3f), f)
        assertNotNull("через общий узел маршрут должен строиться", route)
    }

    @Test
    fun `длина маршрута считается по графу`() {
        val f = floorWithCorridor()
        val route = CollegeRouteFinder.findRoute(room("101", 0.1f, 0.5f), room("105", 0.9f, 0.5f), f)
        assertNotNull(route)
        // Коридор длиной 0.8 — проверяем с запасом на округления
        assertTrue("длина около 0.8, получилось ${route!!.distance}",
            route.distance > 0.7f && route.distance < 0.9f)
    }
}
