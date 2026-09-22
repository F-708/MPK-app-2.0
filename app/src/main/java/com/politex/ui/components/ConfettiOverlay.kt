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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Полноэкранное конфетти: основной поток сыплется сверху по всей ширине экрана,
 * небольшая доля (15%) «выстреливает» из нижних углов. Физика — свободное падение
 * с постоянным ускорением, индивидуальный ветер и колыхание, вращение плюс
 * 3D-кувырок (пульсация размера по второй оси). Частицы улетают за нижний край
 * и удаляются как сошедшие с экрана; лёгкий фейд — только в последних ~15% пути
 * каждой частицы, общего затухания в конце нет. Сид = [trigger]: каждый залп
 * свой, но детерминированный и тестируемый.
 */
@Composable
fun ConfettiOverlay(
    trigger: Long,
    onDone: () -> Unit
) {
    if (trigger == 0L) return

    val progress = remember { Animatable(1f) }
    val pieces = remember(trigger) { makePieces(150, trigger) }

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

            // Вертикаль: свободное падение y = y0 + v·t + g·t² (в долях высоты экрана).
            // g подобрана из целевой глубины падения, чтобы к local = 1 частица
            // гарантированно пролетала ниже нижнего края — схлопывания в конце нет.
            val y = (p.y0 + p.vy * local + p.gravity * local * local) * h

            // Горизонталь: постоянный ветер плюс колыхание с индивидуальной фазой.
            val sway = sin(local * p.swayFreq * 2f * PI.toFloat() + p.swayPhase) * p.swayAmp * w
            val x = (p.x0 + p.vx * local) * w + sway

            // Мягкое появление и лёгкий фейд в последних ~15% пути — без резких
            // «лопаний» у края; размер при этом не меняется.
            val alpha = when {
                local < 0.06f -> local / 0.06f
                local > 0.85f -> ((1f - local) / 0.15f).coerceIn(0f, 1f)
                else -> 1f
            } * p.alpha0

            val s = p.size * unit

            // Сошла с экрана (низ или бока) — не рисуем.
            if (y < -s || y > h + s || x < -s || x > w + s) return@forEach

            val angle = p.rot0 + p.vrot * local
            // 3D-кувырок: фигура периодически поворачивается ребром (вторая ось вращения).
            val flip = 0.30f + 0.70f * abs(cos(local * p.flipFreq * 2f * PI.toFloat() + p.flipPhase))

            rotate(degrees = angle, pivot = Offset(x, y)) {
                when (p.kind) {
                    0 -> drawRect(  // прямоугольник с индивидуальным соотношением сторон
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - s / 2f, y - s * p.aspect * flip / 2f),
                        size = Size(s, s * p.aspect * flip)
                    )
                    1 -> {
                        val path = Path().apply {
                            moveTo(x, y - s / 2f * flip)
                            lineTo(x - s / 2f, y + s / 2f * flip)
                            lineTo(x + s / 2f, y + s / 2f * flip)
                            close()
                        }
                        drawPath(path, p.color.copy(alpha = alpha))
                    }
                    2 -> drawRect(  // тонкая лента, длина тоже от aspect
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - s * 0.7f * p.aspect, y - s * 0.12f * flip),
                        size = Size(s * 1.4f * p.aspect, s * 0.24f * flip)
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

internal data class Piece(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    val gravity: Float,
    val delay: Float,
    val size: Float,
    val color: Color,
    val kind: Int,
    val rot0: Float,
    val vrot: Float,
    val aspect: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val swayPhase: Float,
    val flipFreq: Float,
    val flipPhase: Float,
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

internal fun makePieces(count: Int, seed: Long): List<Piece> {
    val rnd = Random(seed)
    return List(count) {
        // 85% сыплется сверху по всей ширине, 15% — редкие «пушки» из нижних углов.
        val sideCannon = rnd.nextFloat() < 0.15f
        val fromLeft = rnd.nextBoolean()
        val x0 = when {
            sideCannon && fromLeft -> rnd.nextFloat() * 0.08f
            sideCannon -> 0.92f + rnd.nextFloat() * 0.08f
            else -> rnd.nextFloat()
        }
        val y0 = if (sideCannon) 1.02f else -(0.02f + rnd.nextFloat() * 0.12f)
        val vx = when {
            sideCannon && fromLeft -> 0.04f + rnd.nextFloat() * 0.18f
            sideCannon -> -(0.04f + rnd.nextFloat() * 0.18f)
            else -> (rnd.nextFloat() - 0.5f) * 0.24f
        }
        // Начальная скорость: у «пушек» вверх (отрицательная), у топа лёгкая вниз.
        val vy = if (sideCannon) -(0.70f + rnd.nextFloat() * 0.40f) else 0.05f + rnd.nextFloat() * 0.15f
        // Гравитация из целевой глубины падения: y(local=1) = fallDepth от высоты
        // экрана, всегда > 1 — каждая частица уходит за нижний край, скорость у всех своя.
        val fallDepth = 1.15f + rnd.nextFloat() * 0.30f
        val gravity = fallDepth - y0 - vy
        Piece(
            x0 = x0,
            y0 = y0,
            vx = vx,
            vy = vy,
            gravity = gravity,
            delay = rnd.nextFloat() * 0.22f,
            size = 0.012f + rnd.nextFloat() * 0.020f,
            color = CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.size)],
            kind = rnd.nextInt(4),
            rot0 = rnd.nextFloat() * 360f,
            vrot = (rnd.nextFloat() - 0.5f) * 720f,
            aspect = 0.45f + rnd.nextFloat() * 0.75f,
            swayAmp = 0.008f + rnd.nextFloat() * 0.030f,
            swayFreq = 1.2f + rnd.nextFloat() * 1.8f,
            swayPhase = rnd.nextFloat() * 2f * PI.toFloat(),
            flipFreq = 0.8f + rnd.nextFloat() * 1.6f,
            flipPhase = rnd.nextFloat() * 2f * PI.toFloat(),
            alpha0 = if (rnd.nextFloat() < 0.25f) 0.65f + rnd.nextFloat() * 0.2f else 1f
        )
    }
}
