package com.politex.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Полноэкранное конфетти: залп фигур (квадраты, треугольники, ленточки, круги)
 * по всей ширине экрана. Физика с индивидуальной гравитацией и колыханием —
 * без общего «схлопывания» в конце. Перезапускается при смене [trigger].
 */
@Composable
fun ConfettiOverlay(
    trigger: Long,
    onDone: () -> Unit
) {
    if (trigger == 0L) return

    val progress = remember { Animatable(1f) }
    val pieces = remember(trigger) { makePieces(150) }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 3400, easing = LinearEasing))
        onDone()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val unit = min(w, h)
        val t = progress.value

        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach

            // Вертикаль: подъём с индивидуальной начальной скоростью и гравитацией,
            // затем естественное падение — фигуры не собираются в одну точку.
            val y = h * (1.04f - p.vy * local + p.gravity * local * local)

            // Горизонталь: дрейф + колыхание, усиливающееся к концу полёта.
            val sway = sin(local * p.swayFreq * 2f * PI.toFloat() + p.swayPhase) *
                p.swayAmp * local * w
            val x = w * p.x0 + p.vx * local * w + sway

            // Плавное появление и мягкое затухание, без изменения размера.
            val alpha = when {
                local < 0.06f -> local / 0.06f
                local > 0.72f -> ((1f - local) / 0.28f).coerceIn(0f, 1f)
                else -> 1f
            } * p.alpha0

            val s = p.size * unit
            val angle = p.rot0 + p.vrot * local

            // Эффект «бумажного» переворота: фигура периодически поворачивается ребром.
            val flip = 0.30f + 0.70f * kotlin.math.abs(cos(local * p.flipFreq * 2f * PI.toFloat() + p.swayPhase))

            rotate(degrees = angle, pivot = Offset(x, y)) {
                when (p.kind) {
                    0 -> drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - s / 2f, y - s * 0.35f * flip),
                        size = Size(s, s * 0.7f * flip)
                    )
                    1 -> {
                        val path = Path().apply {
                            moveTo(x, y - s / 2f)
                            lineTo(x - s / 2f, y + s / 2f)
                            lineTo(x + s / 2f, y + s / 2f)
                            close()
                        }
                        drawPath(path, p.color.copy(alpha = alpha))
                    }
                    2 -> drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - s * 0.7f, y - s * 0.12f * flip),
                        size = Size(s * 1.4f, s * 0.24f * flip)
                    )
                    else -> drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = s * 0.34f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

private data class Piece(
    val x0: Float,
    val vx: Float,
    val vy: Float,
    val gravity: Float,
    val delay: Float,
    val size: Float,
    val color: Color,
    val kind: Int,
    val rot0: Float,
    val vrot: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val swayPhase: Float,
    val flipFreq: Float,
    val alpha0: Float
)

private val CONFETTI_COLORS = listOf(
    Color(0xFF0072CE),
    Color(0xFF0B3564),
    Color(0xFFE8C55A),
    Color(0xFFE25555),
    Color(0xFF2FA96E),
    Color(0xFF9B6BD4),
    Color(0xFFFF8A3D),
    Color(0xFFF06292),
    Color(0xFF4DD0E1),
    Color.White
)

private fun makePieces(count: Int): List<Piece> {
    val rnd = Random(System.currentTimeMillis())
    return List(count) {
        // 70% — залп по всей ширине снизу, 30% — боковые «пушки» из углов.
        val sideCannon = rnd.nextFloat() < 0.30f
        val fromLeft = rnd.nextBoolean()
        val x0 = when {
            sideCannon && fromLeft -> rnd.nextFloat() * 0.12f
            sideCannon -> 0.88f + rnd.nextFloat() * 0.12f
            else -> 0.03f + rnd.nextFloat() * 0.94f
        }
        val vx = when {
            sideCannon && fromLeft -> 0.10f + rnd.nextFloat() * 0.30f
            sideCannon -> -(0.10f + rnd.nextFloat() * 0.30f)
            else -> (rnd.nextFloat() - 0.5f) * 0.22f
        }
        val vy = 0.85f + rnd.nextFloat() * 0.85f
        val gravity = vy * (0.45f + rnd.nextFloat() * 0.35f)
        Piece(
            x0 = x0,
            vx = vx,
            vy = vy,
            gravity = gravity,
            delay = rnd.nextFloat() * 0.22f,
            size = 0.014f + rnd.nextFloat() * 0.022f,
            color = CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.size)],
            kind = rnd.nextInt(4),
            rot0 = rnd.nextFloat() * 360f,
            vrot = (rnd.nextFloat() - 0.5f) * 720f,
            swayAmp = 0.008f + rnd.nextFloat() * 0.030f,
            swayFreq = 1.2f + rnd.nextFloat() * 1.8f,
            swayPhase = rnd.nextFloat() * 2f * PI.toFloat(),
            flipFreq = 0.8f + rnd.nextFloat() * 1.6f,
            alpha0 = if (rnd.nextFloat() < 0.25f) 0.65f + rnd.nextFloat() * 0.2f else 1f
        )
    }
}
