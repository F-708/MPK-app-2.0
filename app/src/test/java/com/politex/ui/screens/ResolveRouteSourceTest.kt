package com.politex.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF3: тап по кабинету на карте — пиннинг авто/ручного маршрута и заполнение
 * полей конструктора. Чистая функция [resolveRouteSource], без Compose.
 */
class ResolveRouteSourceTest {

    // --- (а) авто-пиннинг: запиненный маршрут тап НЕ перестраивает ---

    @Test
    fun `запиненный маршрут - тап только выбирает кабинет и не трогает маршрут`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = true,
                builderFields = BuilderFields(active = false, a = "", b = ""),
                tapRoom = "301",
            )
        assertEquals("источник остаётся запиненный маршрут", RouteSource.PINNED, res.source)
        assertEquals("301", res.target)
        assertNull("поля конструктора не трогаем", res.pairA)
        assertNull(res.pairB)
        assertFalse("маршрут не перестраивается", res.build)
    }

    @Test
    fun `ручной запиненный маршрут - тап не перестраивает`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = true,
                builderFields = BuilderFields(active = false, a = "249", b = "301"),
                tapRoom = "100",
            )
        assertEquals(RouteSource.PINNED, res.source)
        assertEquals("100", res.target)
        assertFalse("построенный маршрут живёт до Отменить", res.build)
    }

    @Test
    fun `нет маршрута и нет конструктора - тап просто выбирает`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = false, a = "", b = ""),
                tapRoom = "214",
            )
        assertEquals(RouteSource.NONE, res.source)
        assertEquals("214", res.target)
        assertFalse(res.build)
    }

    // --- (б) тап в конструкторе заполняет A-B и строит при обоих ---

    @Test
    fun `конструктор - первый тап заполняет A когда пусто, без build`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "", b = ""),
                tapRoom = "249",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertEquals("249", res.target)
        assertEquals("249", res.pairA)
        assertNull("B не трогаем", res.pairB)
        assertFalse("пока только A — не строим", res.build)
    }

    @Test
    fun `конструктор - второй тап заполняет B и сразу строит`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = ""),
                tapRoom = "301",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertEquals("301", res.target)
        assertNull("A уже заполнено — не трогаем", res.pairA)
        assertEquals("301", res.pairB)
        assertTrue("обе непустые → build", res.build)
    }

    @Test
    fun `конструктор - тап по тому же кабинету что в A не дублирует`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = ""),
                tapRoom = "249",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertEquals("249", res.target)
        assertNull("не дублируем в A", res.pairA)
        assertNull("не дублируем в B", res.pairB)
        assertFalse(res.build)
    }

    @Test
    fun `конструктор - тап по кабинету уже в B не дублирует и не перестраивает`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = "301"),
                tapRoom = "301",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertNull(res.pairA)
        assertNull(res.pairB)
        assertFalse("тот же кабинет — без build", res.build)
    }

    @Test
    fun `конструктор - тап по третьему кабинету перезаписывает B и строит`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = "301"),
                tapRoom = "100",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertNull(res.pairA)
        assertEquals("100", res.pairB)
        assertTrue(res.build)
    }

    @Test
    fun `конструктор - пробелы в A трактуются как пусто`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "   ", b = ""),
                tapRoom = "249",
            )
        assertEquals("249", res.pairA)
        assertFalse(res.build)
    }
}
