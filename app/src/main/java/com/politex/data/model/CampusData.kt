package com.politex.data.model

import android.content.Context
import com.politex.content.ContentAssets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Минималистичное окружение колледжа вокруг плана (стадион, авеню, деревья, здания).
 *
 * Координаты — пиксели расширенного мира, тот же базис, что и план этажа
 * (1376x768 в центре, мир шире). Рисуется тем же трансформом, поэтому при любом
 * этаже и зуме объекты остаются на своих местах.
 */

/** Многоугольник слоя окружения. kind задаёт заливку/обводку (lawn/road/sidewalk/building/...). */
data class CampusShape(
    val id: String,
    val kind: String,
    val points: FloatArray, // плоский x0,y0,x1,y1,...
    val roofTone: String = "",
) {
    val isEmpty: Boolean get() = points.size < 6
}

/** Дерево: центр и радиус кроны. */
data class CampusTree(
    val x: Float,
    val y: Float,
    val r: Float,
    val dark: Boolean,
)

/** Эллипс стадиона: центр и полуоси. */
data class CampusEllipse(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
)

class CampusData(
    val worldLeft: Float,
    val worldTop: Float,
    val worldRight: Float,
    val worldBottom: Float,
    val shapes: List<CampusShape>,
    val trees: List<CampusTree>,
    val stadiumOuter: CampusEllipse?,
    val stadiumInner: CampusEllipse?,
) {
    val isEmpty: Boolean get() = shapes.isEmpty() && trees.isEmpty()

    companion object {
        private const val ASSET = "campus.json"

        fun load(context: Context): CampusData = try {
            ContentAssets
                .open(context, ASSET)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .let(::parse)
        } catch (_: Exception) {
            CampusData(0f, 0f, 0f, 0f, emptyList(), emptyList(), null, null)
        }

        fun parse(json: String): CampusData {
            val root = JSONObject(json)
            val world = root.optJSONArray("world")
            val wl = world?.optDouble(0)?.toFloat() ?: 0f
            val wt = world?.optDouble(1)?.toFloat() ?: 0f
            val wr = world?.optDouble(2)?.toFloat() ?: 0f
            val wb = world?.optDouble(3)?.toFloat() ?: 0f

            val shapes = ArrayList<CampusShape>()
            // Слои-многоугольники. Порядок важен: рисуются от дальнего к ближнему.
            listOf("lawn", "distant", "paths", "parking", "road", "sidewalk", "markings", "buildings", "garages", "workshop")
                .forEach { key ->
                    root.optJSONArray(key)?.forEachShape { o ->
                        shapes += CampusShape(
                            id = o.optString("id"),
                            kind = o.optString("kind", key),
                            points = o.optJSONArray("polygon").toFloatArray(),
                            roofTone = o.optString("roofTone", ""),
                        )
                    }
                }

            val trees = ArrayList<CampusTree>()
            root.optJSONArray("trees")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    trees += CampusTree(
                        x = o.optDouble("x").toFloat(),
                        y = o.optDouble("y").toFloat(),
                        r = o.optDouble("r").toFloat(),
                        dark = o.optString("kind") == "tree-dark",
                    )
                }
            }

            val stadium = root.optJSONObject("stadium")
            val outer = stadium?.optJSONArray("ellipse")?.toEllipse()
            val inner = stadium?.optJSONArray("innerField")?.toEllipse()

            return CampusData(wl, wt, wr, wb, shapes, trees, outer, inner)
        }

        private fun JSONArray.forEachShape(block: (JSONObject) -> Unit) {
            for (i in 0 until length()) optJSONObject(i)?.let(block)
        }

        private fun JSONArray?.toFloatArray(): FloatArray {
            if (this == null) return FloatArray(0)
            val out = ArrayList<Float>()
            for (i in 0 until length()) {
                val pt = optJSONArray(i) ?: continue
                if (pt.length() >= 2) {
                    out += pt.optDouble(0).toFloat()
                    out += pt.optDouble(1).toFloat()
                }
            }
            return out.toFloatArray()
        }

        private fun JSONArray?.toEllipse(): CampusEllipse? {
            if (this == null || length() < 4) return null
            return CampusEllipse(
                cx = optDouble(0).toFloat(),
                cy = optDouble(1).toFloat(),
                rx = optDouble(2).toFloat(),
                ry = optDouble(3).toFloat(),
            )
        }
    }
}
