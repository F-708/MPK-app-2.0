package com.politex.ui.screens.materials

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.util.bouncyClickable
import org.json.JSONArray
import org.json.JSONObject

/**
 * V9: рисование поверх страниц PDF/изображений.
 *
 * Штрих хранится в НОРМАЛИЗОВАННЫХ координатах (0..1 от габаритов бокса страницы), поэтому
 * он не зависит ни от разрешения рендера, ни от текущего зума: Canvas слоя лежит ВНУТРИ
 * graphicsLayer страницы и масштабируется вместе с ней (штрих «приклеен» к бумаге).
 *
 * `strokesJson` (колонка material_annotations): {"v":1,"strokes":[{"c":ARGB,"w":dp,"pts":[x1,y1,…]}]}.
 */
data class Stroke(
    val color: Int,
    val widthDp: Float,
    val pts: List<Offset>,
) {
    companion object {
        /** Чистая функция — покрывается unit-тестом round-trip (см. MaterialAnnotationTest). */
        fun serialize(strokes: List<Stroke>): String {
            val arr = JSONArray()
            strokes.forEach { s ->
                val pts = JSONArray()
                s.pts.forEach {
                    pts.put(it.x.toDouble())
                    pts.put(it.y.toDouble())
                }
                arr.put(JSONObject().put("c", s.color).put("w", s.widthDp.toDouble()).put("pts", pts))
            }
            return JSONObject().put("v", 1).put("strokes", arr).toString()
        }

        /** Битый/пустой JSON → пустой список: страница остаётся читаемой, а не падает. */
        fun parse(json: String): List<Stroke> =
            runCatching {
                val arr = JSONObject(json).optJSONArray("strokes") ?: return emptyList()
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val ptsArr = o.optJSONArray("pts") ?: JSONArray()
                    val pts =
                        (0 until ptsArr.length() / 2).map { k ->
                            Offset(ptsArr.getDouble(2 * k).toFloat(), ptsArr.getDouble(2 * k + 1).toFloat())
                        }
                    Stroke(o.optInt("c"), o.optDouble("w", 4.0).toFloat(), pts)
                }
            }.getOrDefault(emptyList())
    }
}

/** Палитра саб-тулбара: чёрный/красный/синий/зелёный/жёлтый. */
val ANNOTATION_COLORS = listOf(0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt())

/** Три толщины пера, dp. */
val ANNOTATION_WIDTHS = listOf(2f, 4f, 8f)

/** Порог добавления точки в нормализованных координатах — дрожащий палец не плодит точки. */
private const val MIN_POINT_DIST = 0.004f

/**
 * Слой рисования. Кладётся ВНУТРИ graphicsLayer страницы (matchParentSize), поверх контента,
 * ПОД кнопками страницы (закладка/номер остаются кликабельными).
 *
 * Поведение по жестам:
 * - drawMode && 1 палец → рисуем, потребляем ВСЕ события (страница не листается);
 * - drawMode && ≥2 пальцев → [transformGestures] (pan, zoom) — зум страницы не выходя из режима;
 * - !drawMode → у Canvas нет pointerInput: слой полностью прозрачен для событий,
 *   зум/скролл страницы работают как раньше.
 */
@Composable
fun AnnotationLayer(
    strokes: List<Stroke>,
    current: Stroke?,
    drawMode: Boolean,
    strokeColor: Int,
    strokeWidthDp: Float,
    onCurrentChange: (Stroke?) -> Unit,
    onStrokeFinished: (Stroke) -> Unit,
    transformGestures: ((pan: Offset, zoom: Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val gestureModifier =
        if (drawMode) {
            Modifier.pointerInput(drawMode, strokeColor, strokeWidthDp) {
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var pts = emptyList<Offset>()
                    fun addPoint(pos: Offset) {
                        val n = Offset(pos.x / w, pos.y / h)
                        if (pts.isEmpty() || (n - pts.last()).getDistance() >= MIN_POINT_DIST) {
                            pts = pts + n
                            onCurrentChange(Stroke(strokeColor, strokeWidthDp, pts))
                        }
                    }
                    addPoint(down.position)
                    var transformed = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            // ≥2 пальцев — жест страницы (zoom/pan), начатый штрих выбрасываем
                            if (!transformed) {
                                transformed = true
                                pts = emptyList()
                                onCurrentChange(null)
                            }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                transformGestures?.invoke(panChange, zoomChange)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (!transformed) {
                            event.changes.forEach { c ->
                                if (c.pressed) {
                                    if (c.positionChanged()) addPoint(c.position)
                                    c.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (!transformed && pts.isNotEmpty()) onStrokeFinished(Stroke(strokeColor, strokeWidthDp, pts))
                    onCurrentChange(null)
                }
            }
        } else {
            Modifier
        }

    Canvas(modifier = modifier.then(gestureModifier).testTag("annotation_layer")) {
        (strokes + listOfNotNull(current)).forEach { drawStroke(it) }
    }
}

/** Сглаживание: квадратичные Безье через середины соседних точек (классический freehand-приём). */
private fun DrawScope.drawStroke(s: Stroke) {
    if (s.pts.isEmpty()) return
    val pts = s.pts.map { Offset(it.x * size.width, it.y * size.height) }
    val path =
        Path().apply {
            moveTo(pts[0].x, pts[0].y)
            if (pts.size == 1) {
                // точка-тычок
                lineTo(pts[0].x + 0.1f, pts[0].y + 0.1f)
            } else {
                for (i in 0 until pts.size - 2) {
                    val mid = Offset((pts[i].x + pts[i + 1].x) / 2f, (pts[i].y + pts[i + 1].y) / 2f)
                    quadraticBezierTo(pts[i].x, pts[i].y, mid.x, mid.y)
                }
                lineTo(pts.last().x, pts.last().y)
            }
        }
    drawPath(
        path = path,
        color = Color(s.color),
        style = DrawStroke(width = s.widthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * V9: саб-тулбар рисования (под хедером, пока drawMode). Две строки: палитра+толщины и
 * действия (undo / очистить страницу / выход) — 11 кнопок в одну строку на 360dp не влезают
 * без нарушения 40dp-хитбоксов.
 */
@Composable
fun AnnotationToolbar(
    color: Int,
    widthDp: Float,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderLight),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("draw_toolbar"),
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                ANNOTATION_COLORS.forEachIndexed { i, c ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).bouncyClickable { onColor(c) }.testTag("draw_color_$i"),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size(if (c == color) 24.dp else 20.dp)
                                .background(Color(c), CircleShape)
                                .then(if (c == color) Modifier.border(2.dp, ColorBrandBlue, CircleShape) else Modifier),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                ANNOTATION_WIDTHS.forEachIndexed { i, w ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).bouncyClickable { onWidth(w) }.testTag("draw_width_$i"),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size((w + 6).dp)
                                .background(if (w == widthDp) ColorBrandBlue else ColorTextMuted, CircleShape),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                ToolbarAction(Icons.AutoMirrored.Filled.Undo, "Отменить штрих", "draw_undo_btn", onUndo)
                ToolbarAction(Icons.Default.Delete, "Очистить страницу", "draw_clear_btn", onClear)
                ToolbarAction(Icons.Default.Close, "Выйти из рисования", "draw_exit_btn", onExit)
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = Modifier.size(40.dp).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, tint = ColorBrandBlue, modifier = Modifier.size(20.dp))
        }
    }
}
