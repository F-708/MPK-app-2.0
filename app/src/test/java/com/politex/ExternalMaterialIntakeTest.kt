package com.politex

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.materials.ExternalMaterialIntake
import com.politex.util.PendingMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalMaterialIntakeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun contentUri(name: String): Uri = Uri.parse("content://com.telegram.provider/media/$name")

    @Test
    fun `ACTION_VIEW с документом даёт одиночный файл`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri("лекция.pdf"), "application/pdf")
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
        assertEquals(contentUri("лекция.pdf"), (pending as PendingMaterial.SingleFile).uri)
    }

    @Test
    fun `ACTION_VIEW с картинкой становится набором из одного изображения`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri("доска.jpg"), "image/jpeg")
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(1, (pending as PendingMaterial.Images).uris.size)
    }

    @Test
    fun `ACTION_SEND кладёт файл из EXTRA_STREAM`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri("конспект.pdf"))
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
    }

    @Test
    fun `ACTION_SEND_MULTIPLE с изображениями даёт набор`() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(contentUri("1.jpg"), contentUri("2.jpg"), contentUri("3.jpg")),
                )
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(3, (pending as PendingMaterial.Images).uris.size)
    }

    @Test
    fun `пустые и посторонние intent игнорируются`() {
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, Intent(Intent.ACTION_MAIN)))
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, Intent(Intent.ACTION_VIEW)))
        assertNull(
            ExternalMaterialIntake.fromIntent(
                context.contentResolver,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/page")),
            ),
        )
    }
}
