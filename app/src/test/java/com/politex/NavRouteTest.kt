package com.politex

import com.politex.ui.screens.buildGroupScheduleRoute
import com.politex.ui.screens.buildMapRoute
import com.politex.ui.screens.buildScrollRoute
import com.politex.ui.screens.buildTeacherRoute
import com.politex.ui.screens.scrollRoutePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * E4/N15: построители route-строк Navigation-Compose — чистые функции (URLEncoder, JVM).
 * Ошибкодельность кодирования = сломанная навигация по ФИО/группам с кириллицей и пробелами,
 * поэтому round-trip проверяется явно.
 */
class NavRouteTest {

    private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")

    @Test
    fun `карта из меню — голый route без параметров`() {
        assertEquals("map", buildMapRoute(null, null, false))
    }

    @Test
    fun `карта с кабинетом — только room`() {
        assertEquals("map?room=214", buildMapRoute("214", null, false))
    }

    @Test
    fun `from пустой строкой сохраняется — это маршрут от главного входа (R1)`() {
        // from="" и from=null РАЗНЫЕ: "" = показать маршрут от главного входа, null = без маршрута
        assertEquals("map?room=321&from=", buildMapRoute("321", "", false))
    }

    @Test
    fun `карта из расписания — room и from`() {
        assertEquals("map?room=321&from=101", buildMapRoute("321", "101", false))
    }

    @Test
    fun `виджет NowNext — явный флаг маршрута (N8)`() {
        assertEquals("map?room=321&route=true", buildMapRoute("321", null, true))
    }

    @Test
    fun `виджет Today — маршрута нет (N8)`() {
        assertFalse(buildMapRoute("321", null, false).contains("route="))
    }

    @Test
    fun `ФИО с пробелами и кириллицей — round-trip`() {
        val name = "Иванова Мария Петровна"
        val route = buildTeacherRoute(name)
        assertTrue(route.startsWith("teacher?name="))
        assertFalse(route.contains(" "))
        assertEquals(name, decode(route.substringAfter("teacher?name=")))
    }

    @Test
    fun `группа с кириллической буквой — round-trip`() {
        val group = "41О"
        val route = buildGroupScheduleRoute(group)
        assertEquals("group_schedule?group=" + route.substringAfter("group="), route)
        assertEquals(group, decode(route.substringAfter("group=")))
    }

    @Test
    fun `расписание группы без группы — выбор группы (picker)`() {
        assertEquals("group_schedule", buildGroupScheduleRoute(null))
        assertEquals("group_schedule", buildGroupScheduleRoute(""))
    }

    @Test
    fun `scroll-аргумент — только когда виджет его прислал`() {
        assertEquals("schedule", buildScrollRoute("schedule", null))
        assertEquals("schedule", buildScrollRoute("schedule", ""))
        assertEquals("schedule?scroll=CURRENT_LESSON", buildScrollRoute("schedule", "CURRENT_LESSON"))
        assertEquals("bells?scroll=CURRENT_BELL", buildScrollRoute("bells", "CURRENT_BELL"))
    }

    @Test
    fun `паттерн вкладки объявляет scroll как опциональный аргумент`() {
        assertEquals("schedule?scroll={scroll}", scrollRoutePattern("schedule"))
        assertEquals("bells?scroll={scroll}", scrollRoutePattern("bells"))
    }
}
