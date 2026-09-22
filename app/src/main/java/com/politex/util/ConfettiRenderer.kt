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

    // Физика повторяет ConfettiOverlay: сыпется сверху по всей ширине,
    // падение с постоянным ускорением, фейд только в конце пути.
    private const val TOP_SHARE = 0.85f   // доля частиц, падающих сверху
    private const val DELAY_MAX = 0.22f
    private const val FADE_START = 0.85f  // последние 15% пути — мягкий фейд

    fun drawFrame(width: Int, height: Int, frame: Int, total: Int, seed: Long = 42L): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rnd = Random(seed)

        val count = 42
        val t = frame.toFloat() / total
        // Один Paint и один Path на кадр вместо новых на каждую частицу.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        for (i in 0 until count) {
            // Порядок чтения из rnd фиксирован — кадры детерминированы, как и раньше.
            val isTop = rnd.nextFloat() < TOP_SHARE
            val fromLeft = rnd.nextBoolean()
            val x0 = when {
                !isTop && fromLeft -> rnd.nextFloat() * w * 0.08f
                !isTop -> w * (0.92f + rnd.nextFloat() * 0.08f)
                else -> rnd.nextFloat() * w
            }
            val y0 = if (isTop) -(0.02f + rnd.nextFloat() * 0.12f) else 1.02f
            val vx = when {
                !isTop && fromLeft -> w * (0.04f + rnd.nextFloat() * 0.18f)
                !isTop -> -w * (0.04f + rnd.nextFloat() * 0.18f)
                else -> (rnd.nextFloat() - 0.5f) * w * 0.24f
            }
            val vy = if (isTop) 0.05f + rnd.nextFloat() * 0.15f else -(0.70f + rnd.nextFloat() * 0.40f)
            // Гравитация из целевой глубины падения: y(local=1) = fallDepth высоты
            // экрана (> 1) — частица уходит за нижний край, схлопывания нет.
            val fallDepth = 1.15f + rnd.nextFloat() * 0.30f
            val gravity = fallDepth - y0 - vy
            val delay = rnd.nextFloat() * DELAY_MAX
            val size = w * (0.030f + rnd.nextFloat() * 0.040f)
            val aspect = 0.45f + rnd.nextFloat() * 0.75f
            val shape = rnd.nextInt(4)
            val color = COLORS[rnd.nextInt(COLORS.size)]
            val spin = (rnd.nextFloat() - 0.5f) * 720f
            val baseRot = rnd.nextFloat() * 360f
            val swayAmp = w * (0.008f + rnd.nextFloat() * 0.028f)
            val swayFreq = 1.2f + rnd.nextFloat() * 1.8f
            val swayPhase = rnd.nextFloat() * 2f * PI.toFloat()
            val flipFreq = 0.8f + rnd.nextFloat() * 1.6f
            val flipPhase = rnd.nextFloat() * 2f * PI.toFloat()

            val local = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue

            val y = (y0 + vy * local + gravity * local * local) * h
            val sway = sin(local * swayFreq * 2f * PI.toFloat() + swayPhase) * swayAmp
            val x = x0 + vx * local + sway

            // Сошла с экрана — не рисуем; мягкий фейд в последних 15% пути.
            if (y < -size || y > h + size || x < -size || x > w + size) continue
            val alpha = (when {
                local < 0.06f -> local / 0.06f
                local > FADE_START -> ((1f - local) / (1f - FADE_START)).coerceIn(0f, 1f)
                else -> 1f
            } * 255f).toInt().coerceIn(0, 255)

            // 3D-кувырок: пульсация размера по второй оси поверх вращения.
            val flip = 0.30f + 0.70f * abs(cos(local * flipFreq * 2f * PI.toFloat() + flipPhase))

            paint.color = color
            paint.alpha = alpha

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(baseRot + spin * local)
            when (shape) {
                0 -> canvas.drawRect(-size / 2, -size * aspect / 2 * flip, size / 2, size * aspect / 2 * flip, paint)
                1 -> {
                    path.rewind()
                    path.moveTo(0f, -size / 2 * flip)
                    path.lineTo(size / 2, size / 2 * flip)
                    path.lineTo(-size / 2, size / 2 * flip)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                2 -> canvas.drawRoundRect(-size * 0.7f, -size * 0.12f * flip, size * 0.7f, size * 0.12f * flip, size * 0.1f, size * 0.1f, paint)
                else -> canvas.drawCircle(0f, 0f, size * 0.35f, paint)
            }
            canvas.restore()
        }
        return bmp
    }
}
