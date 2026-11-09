package com.politex.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.politex.data.model.DoorOpening
import com.politex.data.model.FloorWalls
import com.politex.data.model.MapWallsData
import com.politex.data.model.StairBox

/**
 * Path стен строится один раз на этаж и переиспользуется между кадрами.
 * 1490 прямоугольников -> ~1-2 мс один раз, в кадре один drawPath.
 */
fun buildWallPath(
    walls: FloorWalls,
    imgW: Float,
    imgH: Float,
): Path {
    val sx = imgW / MapWallsData.PLAN_WIDTH
    val sy = imgH / MapWallsData.PLAN_HEIGHT
    val data = walls.walls.data
    val path = Path()
    var i = 0
    while (i + 3 < data.size) {
        path.addRect(
            Rect(
                left = data[i] * sx,
                top = data[i + 1] * sy,
                right = data[i + 2] * sx,
                bottom = data[i + 3] * sy,
            ),
        )
        i += 4
    }
    return path
}

/**
 * Векторная подложка плана: стены, лестничные марши, створки дверей.
 *
 * Лежит под Canvas с номерами/маршрутом. [unit] = 1/zoom, чтобы штриховка и
 * створки держали экранную толщину при любом зуме (как у номеров кабинетов).
 */
@Composable
fun MapWallsLayer(
    floorId: String,
    walls: MapWallsData,
    imgW: Float,
    imgH: Float,
    wallColor: Color,
    stairColor: Color,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val floor = walls.floor(floorId)
    val path = remember(floorId, imgW, imgH, floor) { buildWallPath(floor, imgW, imgH) }

    Canvas(modifier) {
        if (floor.isEmpty || path.isEmpty) return@Canvas
        drawPath(path, color = wallColor, style = Fill)
        floor.stairs.forEach { drawStair(it, imgW, imgH, stairColor, unit) }
        floor.doors.forEach { drawDoor(it, imgW, imgH, wallColor, unit) }
    }
}

private fun DrawScope.drawStair(
    stair: StairBox,
    imgW: Float,
    imgH: Float,
    color: Color,
    unit: Float,
) {
    val sx = imgW / MapWallsData.PLAN_WIDTH
    val sy = imgH / MapWallsData.PLAN_HEIGHT
    val left = stair.x * sx
    val top = stair.y * sy
    val right = (stair.x + stair.w) * sx
    val bottom = (stair.y + stair.h) * sy
    drawRect(color = color, topLeft = Offset(left, top), size = Size(right - left, bottom - top), style = Stroke(width = 1f * unit))
    // штриховка марша: линии под 45°, как на исходном плане
    val step = 5f * sx
    var x = left - (bottom - top)
    while (x < right) {
        val x0 = x.coerceAtLeast(left)
        val x1 = (x + (bottom - top)).coerceAtMost(right)
        drawLine(
            color = color,
            start = Offset(x0, bottom - (x0 - x)),
            end = Offset(x1, bottom - (x1 - x)),
            strokeWidth = 0.9f * unit,
        )
        x += step
    }
}

/** Створка двери: дуга от простенка внутрь кабинета. Положение проёма измерено по растру. */
private fun DrawScope.drawDoor(
    door: DoorOpening,
    imgW: Float,
    imgH: Float,
    color: Color,
    unit: Float,
) {
    val sx = imgW / MapWallsData.PLAN_WIDTH
    val sy = imgH / MapWallsData.PLAN_HEIGHT
    val radius = (door.width * 0.85f).coerceIn(4f, 34f)
    val radiusX = radius * (if (door.horizontal) sx else sy)
    val radiusY = radius * (if (door.horizontal) sy else sx)
    val stroke = 1f * unit

    val hinge: Offset
    val startAngle: Float
    if (door.horizontal) {
        val dirY = if (door.side == "n") 1f else -1f
        hinge = Offset(door.lo * sx, door.pos * sy)
        startAngle = if (dirY > 0f) 0f else 180f
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = if (dirY > 0f) 90f else -90f,
            useCenter = false,
            topLeft = Offset(hinge.x - radiusX, hinge.y - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = stroke),
        )
        drawLine(color, hinge, Offset(hinge.x, hinge.y + dirY * radiusY), strokeWidth = stroke)
    } else {
        val dirX = if (door.side == "w") 1f else -1f
        hinge = Offset(door.pos * sx, door.lo * sy)
        startAngle = if (dirX > 0f) 90f else 270f
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = if (dirX > 0f) 90f else -90f,
            useCenter = false,
            topLeft = Offset(hinge.x - radiusX, hinge.y - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = stroke),
        )
        drawLine(color, hinge, Offset(hinge.x + dirX * radiusX, hinge.y), strokeWidth = stroke)
    }
}
