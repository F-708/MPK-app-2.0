package com.politex.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object ConfettiRenderer {

    private val COLORS = intArrayOf(
        0xFF0B3564.toInt(),
        0xFF0072CE.toInt(),
        0xFFE8C55A.toInt(),
        0xFFE25555.toInt(),
        0xFF2FA96E.toInt(),
        0xFF9B6BD4.toInt(),
        0xFFFF8A3D.toInt(),
        0xFFFFFFFF.toInt()
    )

    fun drawFrame(width: Int, height: Int, frame: Int, total: Int, seed: Long = 42L): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rnd = Random(seed)

        val count = 42
        val t = frame.toFloat() / total
        for (i in 0 until count) {
            val x0 = rnd.nextFloat() * w
            val delay = rnd.nextFloat() * 0.25f
            val vx = (rnd.nextFloat() - 0.5f) * w * 0.45f
            val vy = 0.9f + rnd.nextFloat() * 0.8f
            val gravity = vy * (0.5f + rnd.nextFloat() * 0.3f)
            val size = w * (0.030f + rnd.nextFloat() * 0.040f)
            val shape = rnd.nextInt(4)
            val color = COLORS[rnd.nextInt(COLORS.size)]
            val spin = (rnd.nextFloat() - 0.5f) * 720f
            val baseRot = rnd.nextFloat() * 360f
            val swayAmp = w * (0.008f + rnd.nextFloat() * 0.028f)
            val swayFreq = 1.2f + rnd.nextFloat() * 1.8f
            val swayPhase = rnd.nextFloat() * 2f * PI.toFloat()
            val flipFreq = 0.8f + rnd.nextFloat() * 1.6f

            val local = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue

            // Та же физика, что и в приложении: подъём с индивидуальной гравитацией
            // и падение, без общего схлопывания в конце.
            val y = h * (1.04f - vy * local + gravity * local * local)
            val sway = sin(local * swayFreq * 2f * PI.toFloat() + swayPhase) * swayAmp * local
            val x = x0 + vx * local + sway

            val alpha = (when {
                local < 0.08f -> local / 0.08f
                local > 0.70f -> (1f - local) / 0.30f
                else -> 1f
            } * 255f).toInt().coerceIn(0, 255)

            val flip = 0.35f + 0.65f * abs(cos(local * flipFreq * 2f * PI.toFloat() + swayPhase))

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.alpha = alpha
            }

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(baseRot + spin * local)
            when (shape) {
                0 -> canvas.drawRect(-size / 2, -size / 2 * flip, size / 2, size / 2 * flip, paint)
                1 -> {
                    val path = Path().apply {
                        moveTo(0f, -size / 2)
                        lineTo(size / 2, size / 2)
                        lineTo(-size / 2, size / 2)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
                2 -> canvas.drawRoundRect(-size / 6, -size * flip, size / 6, size * flip, size / 6, size / 6, paint)
                else -> canvas.drawCircle(0f, 0f, size * 0.35f, paint)
            }
            canvas.restore()
        }
        return bmp
    }
}
