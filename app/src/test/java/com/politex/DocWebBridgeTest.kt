package com.politex

import androidx.compose.ui.geometry.Offset
import com.politex.ui.screens.materials.PortMessage
import com.politex.ui.screens.materials.chromiumMajor
import com.politex.ui.screens.materials.docxRouteWeb
import com.politex.ui.screens.materials.pageFromPort
import com.politex.ui.screens.materials.pageToPort
import com.politex.ui.screens.materials.parsePortMessage
import com.politex.ui.screens.materials.parseRange
import com.politex.ui.screens.materials.pdfRouteWeb
import com.politex.ui.screens.materials.useRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// READER-PLAN D12: чистая логика WebView-моста (порт gander + ink) и роутинг PDF/DOCX.
// parsePortMessage — security-граница (вход из недоверенной страницы): всё невалидное → null.
class DocWebBridgeTest {

    private val eps = 1e-6f

    // ---- parsePortMessage: форматы gander ----
    @Test
    fun `page N OF разбирается, 1-based`() {
        assertEquals(PortMessage.Page(3, 10), parsePortMessage("page 3 10"))
    }

    @Test
    fun `page вне диапазона дропается`() {
        assertNull(parsePortMessage("page 0 10")) // n<1
        assertNull(parsePortMessage("page 11 10")) // n>of
        assertNull(parsePortMessage("page 3 0")) // of<1
        assertNull(parsePortMessage("page x 10"))
        assertNull(parsePortMessage("page 3")) // не 3 поля
    }

    @Test
    fun `search count и locked`() {
        assertEquals(PortMessage.SearchCount(5, 20, true), parsePortMessage("5 20 1"))
        assertEquals(PortMessage.SearchCount(0, 0, false), parsePortMessage("0 0 0"))
        assertNull(parsePortMessage("-1 5 1"))
        assertEquals(PortMessage.Locked, parsePortMessage("locked"))
    }

    // ---- parsePortMessage: ink-глаголы MPK ----
    @Test
    fun `ink — чётное число координат, нормализация и clamp`() {
        val one = parsePortMessage("ink 0 0.1 0.2")
        assertTrue(one is PortMessage.Ink)
        assertEquals(0, (one as PortMessage.Ink).page0)
        assertEquals(listOf(Offset(0.1f, 0.2f)), one.pts)

        val two = parsePortMessage("ink 2 0.1 0.2 0.3 0.4") as PortMessage.Ink
        assertEquals(2, two.page0)
        assertEquals(2, two.pts.size)

        // за пределами 0..1 клампится, а не дропается
        val clamped = parsePortMessage("ink 0 5 -3") as PortMessage.Ink
        assertEquals(Offset(1f, 0f), clamped.pts[0])
    }

    @Test
    fun `ink невалидный дропается`() {
        assertNull(parsePortMessage("ink 0 0.1")) // <4 токенов
        assertNull(parsePortMessage("ink 0 0.1 0.2 0.3")) // нечётное (5)
        assertNull(parsePortMessage("ink -1 0.1 0.2")) // page0<0
        assertNull(parsePortMessage("ink 0 a 0.2")) // не число
        assertNull(parsePortMessage("ink x 0.1 0.2"))
    }

    @Test
    fun `zap — 5 полей, aspect положителен`() {
        val z = parsePortMessage("zap 1 0.5 0.25 1.414") as PortMessage.Zap
        assertEquals(1, z.page0)
        assertEquals(Offset(0.5f, 0.25f), z.at)
        assertEquals(1.414f, z.aspect, eps)

        assertNull(parsePortMessage("zap 0 0.5 0.5")) // 4 токена
        assertNull(parsePortMessage("zap 0 0.5 0.5 0")) // aspect<=0
        assertNull(parsePortMessage("zap 0 0.5 0.5 -1"))
        assertNull(parsePortMessage("zap -1 0.5 0.5 1"))
    }

    @Test
    fun `мусор и пустота дропаются молча`() {
        assertNull(parsePortMessage(null))
        assertNull(parsePortMessage(""))
        assertNull(parsePortMessage("garbage"))
        assertNull(parsePortMessage("q произвольный текст поиска")) // исходящая команда, не входящая
    }

    // ---- DocRange ----
    @Test
    fun `useRanges порог 16МБ`() {
        assertTrue(useRanges(16L * 1024 * 1024))
        assertFalse(useRanges(16L * 1024 * 1024 - 1))
    }

    @Test
    fun `parseRange — валидные, кламп и отказы`() {
        assertEquals(0L to 99L, parseRange("bytes=0-99", 1000))
        assertEquals(500L to 999L, parseRange("bytes=500-", 1000))
        assertEquals(0L to 999L, parseRange("bytes=0-5000", 1000)) // end клампится
        assertNull(parseRange("bytes=2000-3000", 1000)) // start>=total
        assertNull(parseRange("bytes=100-50", 1000)) // start>end
        assertNull(parseRange("garbage", 1000))
        assertNull(parseRange("bytes=0-99", 0)) // total<=0
    }

    // ---- Индексы страниц: БД 0-based ↔ порт 1-based (QA CRITICAL #2) ----
    @Test
    fun `конвертер страниц round-trip`() {
        assertEquals(1, pageToPort(0))
        assertEquals(0, pageFromPort(1))
        for (p in 0..50) assertEquals(p, pageFromPort(pageToPort(p)))
    }

    // ---- Роутинг ----
    @Test
    fun `pdfRouteWeb — WebView только при новом движке, целом файле и не-Huawei`() {
        assertTrue(pdfRouteWeb(fileExists = true, pageCount = 5, major = 125, locked = false))
        assertFalse(pdfRouteWeb(true, 5, 124, false)) // ниже пола pdf.js
        assertFalse(pdfRouteWeb(true, 5, null, false)) // версия не прочитана → нативно
        assertFalse(pdfRouteWeb(true, 5, 130, true)) // Huawei locked
        assertFalse(pdfRouteWeb(true, 0, 130, false)) // pageCount=0 (битый) → нативный plaque
        assertFalse(pdfRouteWeb(false, 5, 130, false)) // файла нет
    }

    @Test
    fun `docxRouteWeb — правленые и отсутствующие не в WebView`() {
        assertTrue(docxRouteWeb(textEdited = false, fileExists = true, major = 80))
        assertTrue(docxRouteWeb(false, true, null)) // docx: null-версия пропускается (пол низкий)
        assertFalse(docxRouteWeb(false, true, 79)) // ниже пола docx-preview
        assertFalse(docxRouteWeb(true, true, 130)) // textEdited → плоский рендер из БД (QA CRITICAL #1)
        assertFalse(docxRouteWeb(false, false, 130)) // файла нет
    }

    // ---- chromiumMajor ----
    @Test
    fun `chromiumMajor — из UA, из пакета, Huawei-мусор отбрасывается`() {
        assertEquals(138, chromiumMajor("Mozilla/5.0 Chrome/138.0.7204.179 Mobile") { null })
        assertEquals(125, chromiumMajor(null) { "125.0.6422.76" })
        assertEquals(91, chromiumMajor("Mozilla/5.0 (no chrome token)") { "91.0.4472" })
        assertNull(chromiumMajor(null) { "15.0.4.326" }) // Huawei-нумерация ниже PLAUSIBLE
        assertNull(chromiumMajor(null) { null })
    }
}
