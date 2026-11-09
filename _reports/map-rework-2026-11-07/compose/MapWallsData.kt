package com.politex.data.model

import android.content.Context
import com.politex.content.ContentAssets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Прямоугольные участки стен этажа в пикселях плана.
 * Хранится плоским массивом (x0, y0, x1, y1, ...): 1490 участков занимают ~24 КБ и
 * превращаются в Path одним проходом, без аллокации объектов на кадр.
 */
class WallRects(
    val data: FloatArray,
) {
    val count: Int get() = data.size / 4
}

data class StairBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/**
 * Проём двери: измерен по растру.
 * side = n/s: lo..hi — диапазон по X, pos — Y линии стены.
 * side = w/e: lo..hi — диапазон по Y, pos — X линии стены.
 */
data class DoorOpening(
    val roomId: String,
    val side: String,
    val lo: Float,
    val hi: Float,
    val pos: Float,
) {
    val width: Float get() = hi - lo
    val horizontal: Boolean get() = side == "n" || side == "s"
}

data class FloorWalls(
    val walls: WallRects = WallRects(FloatArray(0)),
    val stairs: List<StairBox> = emptyList(),
    val doors: List<DoorOpening> = emptyList(),
) {
    val isEmpty: Boolean get() = walls.count == 0
}

/**
 * Векторный слой плана: стены, лестничные марши, проёмы дверей.
 * Данные трассированы с исходных растров и лежат отдельным ассетом, поэтому
 * кабинеты, коридоры и маршруты (college_map.json) остаются без изменений.
 */
class MapWallsData(
    private val floors: Map<String, FloorWalls>,
) {
    fun floor(id: String): FloorWalls = floors[id] ?: FloorWalls()

    val isEmpty: Boolean get() = floors.isEmpty()

    companion object {
        const val PLAN_WIDTH = 1376
        const val PLAN_HEIGHT = 768

        private const val ASSET = "map_walls.json"

        fun load(context: Context): MapWallsData = try {
            ContentAssets
                .open(context, ASSET)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .let(::parse)
        } catch (_: Exception) {
            MapWallsData(emptyMap())
        }

        fun parse(json: String): MapWallsData {
            val root = JSONObject(json)
            val floorsObj = root.optJSONObject("floors") ?: return MapWallsData(emptyMap())
            val result = HashMap<String, FloorWalls>(floorsObj.length())

            floorsObj.keys().forEach { fid ->
                val obj = floorsObj.optJSONObject(fid) ?: return@forEach
                result[fid] =
                    FloorWalls(
                        walls = WallRects(obj.optJSONArray("walls").toFloatArray()),
                        stairs =
                        obj.optJSONArray("stairs").mapObjects { s ->
                            StairBox(
                                x = s.optDouble("x").toFloat(),
                                y = s.optDouble("y").toFloat(),
                                w = s.optDouble("w").toFloat(),
                                h = s.optDouble("h").toFloat(),
                            )
                        },
                        doors =
                        obj.optJSONArray("doors").mapObjects { d ->
                            val gap = d.optJSONArray("gap")
                            DoorOpening(
                                roomId = d.optString("roomId"),
                                side = d.optString("side", "n"),
                                lo = gap?.optDouble(0)?.toFloat() ?: 0f,
                                hi = gap?.optDouble(1)?.toFloat() ?: 0f,
                                pos = if (d.optString("side") in setOf("n", "s")) {
                                    d.optDouble("y").toFloat()
                                } else {
                                    d.optDouble("x").toFloat()
                                },
                            )
                        }.filter { it.hi > it.lo },
                    )
            }
            return MapWallsData(result)
        }
    }
}

private fun JSONArray?.toFloatArray(): FloatArray {
    if (this == null) return FloatArray(0)
    val out = FloatArray(length())
    for (i in 0 until length()) out[i] = optDouble(i).toFloat()
    return out
}

private inline fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(block(it)) }
    return out
}