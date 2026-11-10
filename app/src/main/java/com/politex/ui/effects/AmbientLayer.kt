package com.politex.ui.effects

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.isDarkThemeActive
import com.politex.weather.Precipitation
import com.politex.weather.Season
import com.politex.weather.WeatherScene

/** Готовый набор частиц под текущее состояние: пересобирается только при его смене. */
data class AmbientField(
    val rain: List<RainDrop>,
    val snow: List<SnowFlake>,
    val stars: List<Star>,
    val pumpkins: List<Pumpkin> = emptyList(),
    val garland: List<GarlandLight> = emptyList(),
) {
    val isEmpty: Boolean
        get() = rain.isEmpty() && snow.isEmpty() && stars.isEmpty() && pumpkins.isEmpty() && garland.isEmpty()
}

/** Детерминированный набор частиц для [scene] (без обращения к Android). */
@Composable
fun rememberAmbientField(scene: WeatherScene): AmbientField {
    val hasRain = scene.precipitation == Precipitation.RAIN
    val hasSnow = scene.precipitation == Precipitation.SNOW
    // В дождь и снег неба не видно: звёзды не рисуем, даже если сцена их просит.
    val hasStars = scene.stars && !hasRain && !hasSnow
    val season = scene.season
    return remember(hasRain, hasSnow, hasStars, season) {
        AmbientField(
            rain = if (hasRain) AmbientParticles.rain() else emptyList(),
            snow = if (hasSnow) AmbientParticles.snow() else emptyList(),
            stars = if (hasStars) AmbientParticles.stars() else emptyList(),
            pumpkins = if (season == Season.HALLOWEEN) AmbientParticles.pumpkins() else emptyList(),
            garland = if (season == Season.NEW_YEAR) AmbientParticles.garland() else emptyList(),
        )
    }
}

/**
 * Кадровые часы слоя: секунды с первого кадра.
 *
 * Время берётся из кадрового цикла Compose, поэтому слой не держит собственный поток.
 * При выключенной анимации цикл не запускается и значение остаётся нулевым —
 * на экране один статичный кадр.
 */
@Composable
fun rememberAmbientSeconds(animated: Boolean): MutableFloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var startNanos = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { frameNanos ->
                if (startNanos < 0L) startNanos = frameNanos
                seconds.floatValue = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }
    return seconds
}

/**
 * Эмбиент-слой погоды: дождь, снег и звёзды на Canvas.
 *
 * Рисует только частицы — фон под ними остаётся чужим (это и нужно карте: слой
 * лежит на фоне карты, но под схемой этажа). Касания не перехватываются: у слоя
 * нет `pointerInput`, события проходят к элементам под ним.
 *
 * Состояние времени читается внутри фазы отрисовки, поэтому кадр вызывает только
 * перерисовку, без рекомпозиции дерева.
 */
@Composable
fun AmbientLayer(
    scene: WeatherScene,
    modifier: Modifier = Modifier,
) {
    val field = rememberAmbientField(scene)
    if (field.isEmpty) return
    val dark = isDarkThemeActive
    val seconds = rememberAmbientSeconds(scene.animated)

    Canvas(modifier = modifier) {
        drawAmbientParticles(
            field = field,
            seconds = seconds.floatValue,
            dark = dark,
        )
    }
}

/**
 * Общая отрисовка частиц: один код на Canvas-слой, модификатор фона и полосу.
 *
 * [seconds] — время в секундах (0 при выключенной анимации), [dark] — тема,
 * от неё зависят цвета: на светлом фоне тёмные капли, на тёмном — светлые.
 *
 * [alphaScale] приглушает весь слой (для ненавязчивой полосы на чужих экранах),
 * [verticalFade] плавно гасит частицы к низу (0 — не гасить, 1 — в ноль),
 * [surfaceHeight] — высота области падения: для полосы это её высота, а не экрана.
 */
internal fun DrawScope.drawAmbientParticles(
    field: AmbientField,
    seconds: Float,
    dark: Boolean,
    alphaScale: Float = 1f,
    verticalFade: Float = 0f,
    surfaceHeight: Float = size.height,
) {
    val widthPx = size.width
    val heightPx = surfaceHeight
    if (widthPx <= 0f || heightPx <= 0f) return

    fun fadeAt(y: Float): Float {
        if (verticalFade <= 0f) return 1f
        val k = (y / heightPx).coerceIn(0f, 1f)
        return (1f - verticalFade * k).coerceIn(0f, 1f)
    }

    if (field.rain.isNotEmpty()) {
        val rainColor = if (dark) Color(0xFFCFE0F2) else Color(0xFF33517A)
        // На светлой теме дождь почти растворялся: чуть поднимаем альфу и толщину.
        // Геометрия при этом не меняется — капля по-прежнему строго вертикальна.
        val themeScale = if (dark) 1f else 1.45f
        val strokeWidth = if (dark) 1.dp.toPx() else 1.15.dp.toPx()
        field.rain.forEach { drop ->
            val length = drop.lengthDp.dp.toPx()
            val segment =
                AmbientParticles.rainSegment(
                    drop = drop,
                    seconds = seconds,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    lengthPx = length,
                )
            val alpha = (drop.alpha * themeScale * alphaScale * fadeAt(segment.endY)).coerceIn(0f, 1f)
            drawLine(
                color = rainColor.copy(alpha = alpha),
                start = Offset(segment.startX, segment.startY),
                end = Offset(segment.endX, segment.endY),
                strokeWidth = strokeWidth,
            )
        }
    }

    if (field.snow.isNotEmpty()) {
        val snowColor = if (dark) Color(0xFFF2F7FF) else Color(0xFF5B7FA8)
        val baseAlpha = if (dark) 0.72f else 0.55f
        field.snow.forEach { flake ->
            val radius = flake.radiusDp.dp.toPx()
            val progress = AmbientParticles.fallProgress(seconds, flake.fallSec, flake.phase)
            val y = progress * (heightPx + radius * 2f) - radius * 2f
            val drift = AmbientParticles.driftFactor(seconds, flake.driftSec, flake.phase) * flake.driftDp.dp.toPx()
            val alpha = (baseAlpha * alphaScale * fadeAt(y)).coerceIn(0f, 1f)
            drawCircle(
                color = snowColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(flake.x * widthPx + drift, y),
            )
        }
    }

    if (field.stars.isNotEmpty()) {
        // На светлом небе белые точки не читаются — рисуем тёмно-синие и тише.
        val starColor = if (dark) Color(0xFFFFF6E0) else Color(0xFF44506B)
        val themeScale = if (dark) 1f else 0.6f
        field.stars.forEach { star ->
            val life = AmbientParticles.starAlpha(seconds, star.lifeSec, star.twinkleSec, star.phase)
            val y = star.y * heightPx
            val alpha = (star.alpha * life * themeScale * alphaScale * fadeAt(y)).coerceIn(0f, 1f)
            if (alpha <= 0.01f) return@forEach
            drawCircle(
                color = starColor.copy(alpha = alpha),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(star.x * widthPx, y),
            )
        }
    }

    if (field.pumpkins.isNotEmpty()) {
        drawPumpkins(field.pumpkins, seconds, widthPx, heightPx, dark, alphaScale)
    }

    if (field.garland.isNotEmpty()) {
        drawGarland(field.garland, seconds, widthPx, heightPx, dark, alphaScale)
    }
}

/** Тыква-светильник: оранжевое тело, тёмный «фасонный» срез и мерцающее тёплое свечение. */
private fun DrawScope.drawPumpkins(
    pumpkins: List<Pumpkin>,
    seconds: Float,
    widthPx: Float,
    heightPx: Float,
    dark: Boolean,
    alphaScale: Float,
) {
    val body = if (dark) Color(0xFFC96A1B) else Color(0xFFB85C14)
    val glow = if (dark) Color(0xFFFFC36B) else Color(0xFFFFD9A0)
    pumpkins.forEach { p ->
        val r = p.radiusDp.dp.toPx()
        val cx = p.x * widthPx
        val cy = p.y * heightPx
        // Мерцание свечи: мягкая синусоида 0.35..1.
        val flicker = 0.675f + 0.325f * AmbientParticles.twinkleFactor(seconds, p.flickerSec, p.phase)
        val a = alphaScale.coerceIn(0f, 1f)
        // Тело: чуть приплюснутый овал.
        drawOval(
            color = body.copy(alpha = 0.9f * a),
            topLeft = Offset(cx - r, cy - r * 0.82f),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 1.64f),
        )
        // Свечение изнутри (глаза/рот условно): тёплое пятно.
        drawCircle(
            color = glow.copy(alpha = 0.55f * flicker * a),
            radius = r * 0.62f,
            center = Offset(cx, cy - r * 0.08f),
        )
        // Хвостик.
        drawLine(
            color = body.copy(alpha = 0.9f * a),
            start = Offset(cx, cy - r * 0.8f),
            end = Offset(cx, cy - r * 1.14f),
            strokeWidth = r * 0.18f,
        )
    }
}

/** Новогодняя гирлянда: цепь разноцветных огоньков вдоль верхней кромки с мерцанием. */
private fun DrawScope.drawGarland(
    garland: List<GarlandLight>,
    seconds: Float,
    widthPx: Float,
    heightPx: Float,
    dark: Boolean,
    alphaScale: Float,
) {
    val palette = if (dark) {
        listOf(Color(0xFFFF5F57), Color(0xFFFFD166), Color(0xFF6BCB77), Color(0xFF4D96FF))
    } else {
        listOf(Color(0xFFD64541), Color(0xFFD9A400), Color(0xFF3E9E5B), Color(0xFF2F6FBF))
    }
    val a = alphaScale.coerceIn(0f, 1f)
    // Цепь-провод: тонкая полупрозрачная линия через все точки.
    if (garland.size > 1) {
        val wire = (if (dark) Color(0xFF8A94A6) else Color(0xFF9AA5B1)).copy(alpha = 0.35f * a)
        for (i in 1 until garland.size) {
            drawLine(
                color = wire,
                start = Offset(garland[i - 1].x * widthPx, garland[i - 1].y * heightPx),
                end = Offset(garland[i].x * widthPx, garland[i].y * heightPx),
                strokeWidth = 1.dp.toPx() * 0.7f,
            )
        }
    }
    garland.forEach { g ->
        val twinkle = AmbientParticles.twinkleFactor(seconds, g.twinkleSec, g.phase)
        val base = palette[g.colorIndex % palette.size]
        val alpha = (0.55f + 0.45f * twinkle) * a
        val center = Offset(g.x * widthPx, g.y * heightPx)
        val r = 3.2f.dp.toPx()
        // Ореол + яркое ядро.
        drawCircle(color = base.copy(alpha = 0.30f * alpha), radius = r * 2.1f, center = center)
        drawCircle(color = base.copy(alpha = alpha), radius = r, center = center)
    }
}