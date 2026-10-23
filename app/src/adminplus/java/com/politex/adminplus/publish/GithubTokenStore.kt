package com.politex.adminplus.publish

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenCipher {
    fun encrypt(raw: String): String?

    fun decrypt(encoded: String): String?
}

object KeystoreTokenCipher : TokenCipher {
    private const val KEY_STORE = "AndroidKeyStore"
    private const val ALIAS = "adminplus_github_pat"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    private fun openKeyStore(): KeyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val existing = runCatching { openKeyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry }.getOrNull()
        existing?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(raw: String): String? =
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val data = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + data, Base64.NO_WRAP)
        }.getOrNull()

    override fun decrypt(encoded: String): String? =
        runCatching {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            if (blob.size <= IV_SIZE) return@runCatching null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE))
            String(cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE), Charsets.UTF_8)
        }.getOrNull()
}

object GithubTokenStore {
    const val MIN_TOKEN_LENGTH = 20

    private const val PREFS = "adminplus_publish_prefs"
    private const val KEY_TOKEN = "github_pat_enc"

    internal var cipher: TokenCipher = KeystoreTokenCipher

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPlausibleToken(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.length >= MIN_TOKEN_LENGTH && trimmed.none { it.isWhitespace() }
    }

    fun hasToken(context: Context): Boolean = load(context) != null

    fun save(
        context: Context,
        token: String,
    ): Boolean {
        val trimmed = token.trim()
        if (!isPlausibleToken(trimmed)) return false
        val encoded = cipher.encrypt(trimmed) ?: return false
        prefs(context)
            .edit()
            .putString(KEY_TOKEN, encoded)
            .apply()
        return true
    }

    fun load(context: Context): String? =
        prefs(context)
            .getString(KEY_TOKEN, null)
            ?.let { cipher.decrypt(it) }
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_TOKEN)
            .apply()
    }
}
