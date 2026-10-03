package com.politex

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.materials.PdfZoomStore
import com.politex.ui.screens.materials.Stroke
import com.politex.ui.screens.materials.eraserHitTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * X5/X34: чистые функции просмотрщиков материалов.
 *
 * X5 — eraserHitTest: касание на линии / в пределах порога / далеко, тычок-точка, aspect.
 * X34 — PdfZoomStore: serializePan/parsePan round-trip (в т.ч. битые строки) и prefs
 * round-trip флага/зума/пана на реальных SharedPreferences Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class X5X34AnnotationToolsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // ── X5: eraserHitTest ─────────────────────────────────────────────────────

    private val line = Stroke(0xFF000000.toInt(), 4f, listOf(Offset(0.1f, 0.5f), Offset(0.5f, 0.5f), Offset(0.9f, 0.5f)))

    @Test
    fun `касание точно на линии — попадание`() {
        assertTrue(eraserHitTest(line, Offset(0.5f, 0.5f), threshold = 0.02f))
        assertTrue(eraserHitTest(line, Offset(0.1f, 0.5f), threshold = 0.02f)) // конец штриха
    }

    @Test
    fun `касание далеко от линии — промах`() {
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.9f), threshold = 0.02f))
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.1f), threshold = 0.02f))
    }

    @Test
    fun `порог — расстояние меньше порога попадает, больше нет`() {
        // отклонение 0.019 < 0.02 → попадание; 0.05 > 0.02 → промах
        assertTrue(eraserHitTest(line, Offset(0.5f, 0.519f), threshold = 0.02f))
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.55f), threshold = 0.02f))
        // широкий порог дотягивается до того же касания
        assertTrue(eraserHitTest(line, Offset(0.5f, 0.55f), threshold = 0.1f))
    }

    @Test
    fun `тычок из одной точки — расстояние до точки`() {
        val dot = Stroke(0xFF000000.toInt(), 8f, listOf(Offset(0.3f, 0.3f)))
        assertTrue(eraserHitTest(dot, Offset(0.3f, 0.3f), threshold = 0.02f))
        assertTrue(eraserHitTest(dot, Offset(0.31f, 0.3f), threshold = 0.02f))
        assertFalse(eraserHitTest(dot, Offset(0.4f, 0.3f), threshold = 0.02f))
        assertFalse(eraserHitTest(Stroke(0, 4f, emptyList()), Offset(0.3f, 0.3f), threshold = 0.5f))
    }

    @Test
    fun `aspect учитывается — вертикальное расстояние домножается на высоту-к-ширине`() {
        // точка ниже линии на 0.03 (норм. по X): при aspect=1 это дальше порога 0.02…
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.53f), threshold = 0.02f, aspect = 1f))
        // …но в «изотропном» пространстве 0.03*0.5 = 0.015 — уже попадание
        assertTrue(eraserHitTest(line, Offset(0.5f, 0.53f), threshold = 0.02f, aspect = 0.5f))
        // обратная сторона: широкий бокс (aspect=2) растягивает вертикаль — 0.015*2=0.03 > 0.02
        assertTrue(eraserHitTest(line, Offset(0.5f, 0.515f), threshold = 0.02f, aspect = 1f))
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.515f), threshold = 0.02f, aspect = 2f))
    }

    // ── X34: PdfZoomStore — чистая сериализация пана ─────────────────────────

    @Test
    fun `serializePan-parsePan round-trip`() {
        listOf(Offset.Zero, Offset(123.5f, -456.25f), Offset(-0.001f, 99999f)).forEach { p ->
            assertEquals(p, PdfZoomStore.parsePan(PdfZoomStore.serializePan(p)))
        }
    }

    @Test
    fun `parsePan битых строк — null`() {
        assertNull(PdfZoomStore.parsePan(null))
        assertNull(PdfZoomStore.parsePan(""))
        assertNull(PdfZoomStore.parsePan("abc"))
        assertNull(PdfZoomStore.parsePan("1.5"))
        assertNull(PdfZoomStore.parsePan("1.5,2.5,3.5"))
        assertNull(PdfZoomStore.parsePan("x,y"))
    }

    // ── X34: prefs round-trip ────────────────────────────────────────────────

    @Test
    fun `keepZoom флаг round-trip и значение по умолчанию`() {
        assertFalse(PdfZoomStore.keep(context, 42L))
        PdfZoomStore.setKeep(context, 42L, true)
        assertTrue(PdfZoomStore.keep(context, 42L))
        PdfZoomStore.setKeep(context, 42L, false)
        assertFalse(PdfZoomStore.keep(context, 42L))
    }

    @Test
    fun `saveZoom-loadZoom round-trip и clearZoom`() {
        assertNull("нет записи — нет зума", PdfZoomStore.loadZoom(context, 7L))

        PdfZoomStore.saveZoom(context, 7L, 2.5f, Offset(-100.5f, 220.25f))
        val (z, p) = PdfZoomStore.loadZoom(context, 7L)!!
        assertEquals(2.5f, z)
        assertEquals(Offset(-100.5f, 220.25f), p)

        // zoom≈1 сохранять смысла нет — loadZoom отдаёт null (страницы в дефолтном 1×)
        PdfZoomStore.saveZoom(context, 7L, 1f, Offset.Zero)
        assertNull(PdfZoomStore.loadZoom(context, 7L))

        PdfZoomStore.saveZoom(context, 7L, 3f, Offset(10f, 10f))
        PdfZoomStore.clearZoom(context, 7L)
        assertNull(PdfZoomStore.loadZoom(context, 7L))
    }

    @Test
    fun `битый pan в prefs не роняет loadZoom — зум возвращается с нулевым паном`() {
        PdfZoomStore.saveZoom(context, 9L, 2f, Offset(5f, 5f))
        context
            .getSharedPreferences("mpk_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pdf_pan_9", "мусор")
            .commit()
        val (z, p) = PdfZoomStore.loadZoom(context, 9L)!!
        assertEquals(2f, z)
        assertEquals(Offset.Zero, p)
    }
}
