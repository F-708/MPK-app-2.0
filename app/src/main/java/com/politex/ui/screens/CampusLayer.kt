package com.politex.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.politex.data.model.CampusData
import com.politex.data.model.MapWallsData

/** Затемнение цвета для «боковой грани» экструзии. */
private fun Color.darker(factor: Float): Color =
    Color(red * factor, green * factor, blue * factor, alpha)

/**
 * Слой окружения колледжа под векторным планом: газон, дороги, деревья, здания.
 *
 * Мир и план делят один базис координат (план — это центральный прямоугольник
 * мира 0..1376 x 0..768), поэтому маппинг — просто масштаб плана. Рисуется один
 * раз под планом и не меняется между этажами и зумом.
 */
@Composable
fun CampusLayer(
    campus: CampusData,
    imgW: Float,
    imgH: Float,
    dark: Boolean,
    modifier: Modifier = Modifier,
    extrude3d: Boolean = false,
) {
    if (campus.isEmpty) return
    Canvas(modifier) {
        val scale = imgW / MapWallsData.PLAN_WIDTH
        drawCampus(campus, scale, dark, extrude3d)
    }
}

private fun DrawScope.drawCampus(
    campus: CampusData,
    scale: Float,
    dark: Boolean,
    extrude3d: Boolean,
) {
    fun colorFor(kind: String, roofTone: String): Color = when (kind) {
        "lawn" -> if (dark) Color(0xFF14231A) else Color(0xFFE3EDDD)
        "distant" -> if (dark) Color(0xFF232B33) else Color(0xFFD3D9DF)
        "path" -> if (dark) Color(0xFF2C333B) else Color(0xFFDCD6CB)
        "parking" -> if (dark) Color(0xFF23272C) else Color(0xFFD2D5D2)
        "road" -> if (dark) Color(0xFF1E2226) else Color(0xFFC4C9CE)
        "sidewalk" -> if (dark) Color(0xFF272C32) else Color(0xFFE0E4E8)
        "dash", "stall" -> if (dark) Color(0xFF4A5158) else Color(0xFFF2F4F6)
        "building" ->
            if (roofTone == "red") {
                if (dark) Color(0xFF4A2E2A) else Color(0xFFD8B4A8)
            } else {
                if (dark) Color(0xFF2A3138) else Color(0xFFC9CFD6)
            }
        "garage", "workshop" -> if (dark) Color(0xFF2C333A) else Color(0xFFCACFC5)
        else -> if (dark) Color(0xFF22282E) else Color(0xFFD5DBE0)
    }

    campus.shapes.forEach { shape ->
        val p = shape.points
        if (p.size < 6) return@forEach
        val base = Path()
        base.moveTo(p[0] * scale, p[1] * scale)
        var i = 2
        while (i + 1 < p.size) {
            base.lineTo(p[i] * scale, p[i + 1] * scale)
            i += 2
        }
        base.close()

        val color = colorFor(shape.kind, shape.roofTone)
        // Экструзия зданий в 3D: тёмный «юбочный» контур со сдвигом вниз-вправо
        // даёт ощущение объёма без настоящего 3D-движка.
        if (extrude3d && (shape.kind == "building" || shape.kind == "garage" || shape.kind == "workshop")) {
            val lift = 10f * scale
            val side = Path()
            side.moveTo(p[0] * scale, p[1] * scale)
            var j = 2
            while (j + 1 < p.size) {
                side.lineTo(p[j] * scale, p[j + 1] * scale)
                j += 2
            }
            side.close()
            val shadow = Path()
            shadow.addPath(base)
            shadow.translate(Offset(0f, lift))
            drawPath(shadow, color = color.copy(alpha = color.alpha * 0.55f).darker(0.7f), style = Fill)
        }
        drawPath(base, color = color, style = Fill)
    }

    campus.stadiumOuter?.let { e ->
        drawOval(
            color = if (dark) Color(0xFF5A3A32) else Color(0xFFC98D7A),
            topLeft = Offset((e.cx - e.rx) * scale, (e.cy - e.ry) * scale),
            size = Size(e.rx * 2f * scale, e.ry * 2f * scale),
        )
    }
    campus.stadiumInner?.let { e ->
        drawOval(
            color = if (dark) Color(0xFF1B2E20) else Color(0xFFCBE3BC),
            topLeft = Offset((e.cx - e.rx) * scale, (e.cy - e.ry) * scale),
            size = Size(e.rx * 2f * scale, e.ry * 2f * scale),
        )
    }

    campus.trees.forEach { t ->
        val c = if (t.dark) {
            if (dark) Color(0xFF1F3527) else Color(0xFF8FB08A)
        } else {
            if (dark) Color(0xFF274030) else Color(0xFFA7C4A0)
        }
        drawCircle(color = c, radius = t.r * scale, center = Offset(t.x * scale, t.y * scale))
    }
}
