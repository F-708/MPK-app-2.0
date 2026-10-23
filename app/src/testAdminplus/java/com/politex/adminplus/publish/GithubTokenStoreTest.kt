package com.politex.adminplus.publish

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private class FakeTokenCipher : TokenCipher {
    override fun encrypt(raw: String): String = "enc:" + raw.reversed()

    override fun decrypt(encoded: String): String? =
        if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
}

@RunWith(RobolectricTestRunner::class)
class GithubTokenStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val token = "github_pat_" + "A1".repeat(30)

    @Before
    fun setUp() {
        GithubTokenStore.cipher = FakeTokenCipher()
        GithubTokenStore.clear(context)
    }

    @After
    fun tearDown() {
        GithubTokenStore.clear(context)
    }

    @Test
    fun `сохранение и чтение токена`() {
        assertFalse(GithubTokenStore.hasToken(context))
        assertTrue(GithubTokenStore.save(context, token))
        assertTrue(GithubTokenStore.hasToken(context))
        assertEquals(token, GithubTokenStore.load(context))
    }

    @Test
    fun `в хранилище лежит только шифротекст`() {
        assertTrue(GithubTokenStore.save(context, token))
        val prefsFile = File(File(context.applicationInfo.dataDir, "shared_prefs"), "adminplus_publish_prefs.xml")
        assertTrue(prefsFile.isFile)
        val raw = prefsFile.readText(Charsets.UTF_8)
        assertFalse(raw.contains(token))
        assertTrue(raw.contains("enc:"))
    }

    @Test
    fun `удаление токена`() {
        GithubTokenStore.save(context, token)
        GithubTokenStore.clear(context)
        assertFalse(GithubTokenStore.hasToken(context))
        assertNull(GithubTokenStore.load(context))
    }

    @Test
    fun `короткий токен и токен с пробелами не сохраняются`() {
        assertFalse(GithubTokenStore.save(context, "короткий"))
        assertFalse(GithubTokenStore.save(context, "github_pat_12 345678901234567890"))
        assertFalse(GithubTokenStore.hasToken(context))
    }

    @Test
    fun `пробелы по краям подрезаются`() {
        assertTrue(GithubTokenStore.save(context, "  $token  "))
        assertEquals(token, GithubTokenStore.load(context))
    }

    @Test
    fun `распознавание правдоподобного токена`() {
        assertTrue(GithubTokenStore.isPlausibleToken(token))
        assertFalse(GithubTokenStore.isPlausibleToken(""))
        assertFalse(GithubTokenStore.isPlausibleToken("abc"))
        assertFalse(GithubTokenStore.isPlausibleToken(token + " x"))
    }

    @Test
    fun `сбой шифра не роняет сохранение`() {
        GithubTokenStore.cipher =
            object : TokenCipher {
                override fun encrypt(raw: String): String? = null

                override fun decrypt(encoded: String): String? = null
            }
        assertFalse(GithubTokenStore.save(context, token))
        assertFalse(GithubTokenStore.hasToken(context))
    }
}
