package com.politex.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateCheckerTest {
    private val manifest =
        """
        {
          "standard": {"versionCode": 21001, "versionName": "2.1.1", "url": "https://example.com/a.apk", "notes": "фикс виджета"},
          "admin": {"versionCode": 99, "url": "https://example.com/admin.apk"}
        }
        """.trimIndent()

    @Test
    fun `новая версия находится`() {
        val info = AppUpdateChecker.parse(manifest, "standard", 21000)!!
        assertEquals(21001, info.versionCode)
        assertEquals("2.1.1", info.versionName)
        assertEquals("фикс виджета", info.notes)
    }

    @Test
    fun `своя и старая версия не предлагаются`() {
        assertNull(AppUpdateChecker.parse(manifest, "standard", 21001))
        assertNull(AppUpdateChecker.parse(manifest, "standard", 30000))
    }

    @Test
    fun `мусорный манифест не роняет проверку`() {
        assertNull(AppUpdateChecker.parse(null, "standard", 1))
        assertNull(AppUpdateChecker.parse("", "standard", 1))
        assertNull(AppUpdateChecker.parse("не json", "standard", 1))
        assertNull(AppUpdateChecker.parse("{}", "standard", 1))
        assertNull(AppUpdateChecker.parse(manifest, "teacher", 1))
    }

    @Test
    fun `запись без url игнорируется`() {
        val broken = """{"standard": {"versionCode": 99}}"""
        assertNull(AppUpdateChecker.parse(broken, "standard", 1))
    }

    @Test
    fun `автообновление включено только для standard`() {
        assertEquals(setOf("standard"), AppUpdateChecker.UPDATE_ENABLED_FLAVORS)
    }
}
