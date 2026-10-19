package com.politex.update

import com.politex.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdatePackageValidationTest {
    @Test
    fun `applicationId сборки — com politex`() {
        assertEquals("com.politex", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `apk с собственным пакетом принимается`() {
        assertTrue(UpdateDownloader.apkPackageAccepted("com.politex", "com.politex"))
    }

    @Test
    fun `apk со старым aistudio id отклоняется`() {
        assertFalse(
            UpdateDownloader.apkPackageAccepted(
                "com.politex",
                "com.aistudio.mpkschedule.vbyqzt",
            ),
        )
    }

    @Test
    fun `чужие и пустые пакеты отклоняются`() {
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", null))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", ""))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", "com.example.fake"))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", "com.politex.evil"))
    }

    @Test
    fun `flavor-суффиксы валидируются строго по своему пакету`() {
        assertTrue(UpdateDownloader.apkPackageAccepted("com.politex.admin", "com.politex.admin"))
        assertTrue(UpdateDownloader.apkPackageAccepted("com.politex.teacher", "com.politex.teacher"))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", "com.politex.admin"))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex.admin", "com.politex"))
    }

    @Test
    fun `минимальный размер apk задан`() {
        assertTrue(UpdateDownloader.MIN_APK_BYTES >= 1_000_000L)
    }
}
