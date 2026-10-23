package com.politex.owner

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AccessClock {
    @Volatile
    var overrideSeconds: Long? = null

    fun nowSeconds(): Long = overrideSeconds ?: (System.currentTimeMillis() / 1000L)
}

object AccessCode {
    const val STEP_SECONDS = 30L
    private const val VALIDATION_WINDOW = 1
    private val secret = "MpK-0wner-Secret-2026-Politex-AdminPlus-!#7F".toByteArray(Charsets.UTF_8)

    fun timeStep(epochSeconds: Long): Long = Math.floorDiv(epochSeconds, STEP_SECONDS)

    fun generateCode(epochSeconds: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        var step = timeStep(epochSeconds)
        val message = ByteArray(8)
        for (i in 7 downTo 0) {
            message[i] = (step and 0xFF).toByte()
            step = step shr 8
        }
        val hash = mac.doFinal(message)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    fun isCodeValid(
        code: String,
        epochSeconds: Long,
    ): Boolean {
        if (code.length != 6 || code.any { it !in '0'..'9' }) return false
        val step = timeStep(epochSeconds)
        return (-VALIDATION_WINDOW..VALIDATION_WINDOW).any { delta ->
            generateCode((step + delta) * STEP_SECONDS) == code
        }
    }

    fun currentCode(): String = generateCode(AccessClock.nowSeconds())

    fun isCurrentCodeValid(code: String): Boolean = isCodeValid(code, AccessClock.nowSeconds())

    fun secondsLeftInStep(epochSeconds: Long): Int = (STEP_SECONDS - Math.floorMod(epochSeconds, STEP_SECONDS)).toInt()
}
