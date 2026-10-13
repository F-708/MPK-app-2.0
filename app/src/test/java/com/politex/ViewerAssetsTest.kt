package com.politex

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.materials.Stroke
import com.politex.ui.screens.materials.ViewerPolicy
import com.politex.ui.screens.materials.inkWireJson
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// READER-PLAN D12: ассеты viewer на месте, CSP дословно совпадает с meta-тегом страниц
// (иначе pdf.js-worker останется без политики — молчаливая дыра), mpk-ink.js подключён,
// wire-формат штрихов round-trip'ится.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ViewerAssetsTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun assetText(path: String): String =
        ctx.assets.open(path).bufferedReader().use { it.readText() }

    private fun exists(path: String): Boolean =
        runCatching { ctx.assets.open(path).use {} }.isSuccess

    @Test
    fun `все viewer-ассеты pdf и docx присутствуют`() {
        listOf(
            "viewer/pdf.html", "viewer/docx.html", "viewer/app.js", "viewer/app.css",
            "viewer/pdf.mjs", "viewer/pdf-setup.js", "viewer/pdf-nomodule.js",
            "viewer/docx.js", "viewer/find.js", "viewer/mpk-ink.js",
            "viewer/lib/pdf.min.mjs", "viewer/lib/pdf.worker.min.mjs",
            "viewer/lib/docx-preview.min.js", "viewer/lib/jszip3.min.js",
            "viewer/lib/wasm/openjpeg.wasm", "viewer/lib/wasm/jbig2.wasm",
            "viewer-licences.md",
        ).forEach { assertTrue("нет ассета $it", exists(it)) }
        // CJK/скан-шрифты: каталог cmaps не пуст
        val cmaps = ctx.assets.list("viewer/lib/cmaps").orEmpty()
        assertTrue("cmaps пуст", cmaps.isNotEmpty())
    }

    @Test
    fun `CSP в meta pdf и docx дословно равен ViewerPolicy`() {
        assertTrue(assetText("viewer/pdf.html").contains(ViewerPolicy.CSP))
        assertTrue(assetText("viewer/docx.html").contains(ViewerPolicy.CSP))
    }

    @Test
    fun `mpk-ink js подключён к pdf и docx html`() {
        assertTrue(assetText("viewer/pdf.html").contains("mpk-ink.js"))
        // v2.0: рисование и закладки работают и в DOCX-вьювере
        assertTrue(assetText("viewer/docx.html").contains("mpk-ink.js"))
    }

    @Test
    fun `inkWireJson round-trip — цвет, нормированная толщина, точки`() {
        val strokes = listOf(
            Stroke(0xFFE53935.toInt(), 4f, listOf(Offset(0.1f, 0.2f), Offset(0.3f, 0.4f))),
        )
        val arr = JSONArray(inkWireJson(strokes) { it * 0.01f })
        assertEquals(1, arr.length())
        val o = arr.getJSONObject(0)
        assertEquals(0xFFE53935.toInt(), o.getInt("c"))
        assertEquals(0.04, o.getDouble("w"), 1e-6)
        val pts = o.getJSONArray("pts")
        assertEquals(4, pts.length())
        assertEquals(0.1, pts.getDouble(0), 1e-6)
        assertEquals(0.4, pts.getDouble(3), 1e-6)
    }

    @Test
    fun `inkWireJson пустого списка — пустой массив`() {
        assertEquals("[]", inkWireJson(emptyList()) { it })
    }
}
