package com.example.data.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Точка на плане: доли от ширины и высоты картинки (0..1). */
data class MapPoint(val x: Float, val y: Float)

/** Кабинет на плане. */
data class MapRoom(
    val id: String,
    val number: String,
    val title: String,
    val note: String,
    /** normal | closed | nonexistent | service */
    val status: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

/** Проход по коридору — ломаная. */
data class MapCorridor(val id: String, val points: List<MapPoint>, val width: Float)

/** Перекрёсток коридоров; [links] — соседние узлы, с которыми есть проход. */
data class MapNode(val id: String, val x: Float, val y: Float, val links: List<String>)

/** Надпись на плане. */
data class MapLabel(val id: String, val x: Float, val y: Float, val text: String, val size: Float)

/** Выноска: текст со ссылкой на объект. */
data class MapCallout(val id: String, val text: String, val x: Float, val y: Float, val targetId: String)

/** Вход, выход или лестница. [dir] — направление стрелки в градусах. */
data class MapEntrance(
    val id: String,
    val x: Float,
    val y: Float,
    val kind: String,
    val dir: Float,
    val title: String
)

/** Всё, что размечено на одном этаже. */
data class FloorMap(
    val rooms: List<MapRoom> = emptyList(),
    val corridors: List<MapCorridor> = emptyList(),
    val nodes: List<MapNode> = emptyList(),
    val labels: List<MapLabel> = emptyList(),
    val callouts: List<MapCallout> = emptyList(),
    val entrances: List<MapEntrance> = emptyList()
) {
    fun room(number: String): MapRoom? =
        rooms.firstOrNull { it.number.equals(number, ignoreCase = true) }
}

/**
 * Разметка карты колледжа: читается из `assets/college_map.json`.
 *
 * Файл готовит заказчик в редакторе `tools/editor.html`. Если файла нет
 * или он битый — карта просто останется пустой, приложение не упадёт.
 */
class CollegeMapData(private val floors: Map<String, FloorMap>) {

    fun floor(id: String): FloorMap = floors[id] ?: FloorMap()

    val isEmpty: Boolean get() = floors.values.all { it.rooms.isEmpty() }

    companion object {

        private const val ASSET = "college_map.json"

        fun load(context: Context): CollegeMapData = try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
                .let(::parse)
        } catch (_: Exception) {
            CollegeMapData(emptyMap())
        }

        fun parse(json: String): CollegeMapData {
            val root = JSONObject(json)
            val floorsObj = root.optJSONObject("floors") ?: return CollegeMapData(emptyMap())
            val result = mutableMapOf<String, FloorMap>()

            floorsObj.keys().forEach { fid ->
                val f = floorsObj.optJSONObject(fid) ?: return@forEach
                result[fid] = FloorMap(
                    rooms = f.optJSONArray("rooms").mapObjects { o ->
                        MapRoom(
                            id = o.optString("id"),
                            number = o.optString("number"),
                            title = o.optString("title"),
                            note = o.optString("note"),
                            status = o.optString("status", "normal").ifBlank { "normal" },
                            x = o.optDouble("x").toFloat(),
                            y = o.optDouble("y").toFloat(),
                            w = o.optDouble("w").toFloat(),
                            h = o.optDouble("h").toFloat()
                        )
                    },
                    corridors = f.optJSONArray("corridors").mapObjects { o ->
                        MapCorridor(
                            id = o.optString("id"),
                            points = o.optJSONArray("points").mapPoints(),
                            width = o.optDouble("width", 0.012).toFloat()
                        )
                    },
                    nodes = f.optJSONArray("nodes").mapObjects { o ->
                        MapNode(
                            id = o.optString("id"),
                            x = o.optDouble("x").toFloat(),
                            y = o.optDouble("y").toFloat(),
                            links = o.optJSONArray("links").mapStrings()
                        )
                    },
                    labels = f.optJSONArray("labels").mapObjects { o ->
                        MapLabel(
                            id = o.optString("id"),
                            x = o.optDouble("x").toFloat(),
                            y = o.optDouble("y").toFloat(),
                            text = o.optString("text"),
                            size = o.optDouble("size", 16.0).toFloat()
                        )
                    },
                    callouts = f.optJSONArray("callouts").mapObjects { o ->
                        MapCallout(
                            id = o.optString("id"),
                            text = o.optString("text"),
                            x = o.optDouble("x").toFloat(),
                            y = o.optDouble("y").toFloat(),
                            targetId = o.optString("targetId")
                        )
                    },
                    entrances = f.optJSONArray("entrances").mapObjects { o ->
                        MapEntrance(
                            id = o.optString("id"),
                            x = o.optDouble("x").toFloat(),
                            y = o.optDouble("y").toFloat(),
                            kind = o.optString("kind", "backup").ifBlank { "backup" },
                            dir = o.optDouble("dir", 0.0).toFloat(),
                            title = o.optString("title")
                        )
                    }
                )
            }
            return CollegeMapData(result)
        }
    }
}

// --- Мелкие помощники разбора: пустой список вместо падения ------------------

/**
 * Одна общая функция на все списки. Раньше здесь было шесть одинаково
 * названных функций с разными типами — Kotlin не мог выбрать нужную.
 */
private inline fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(block(it)) }
    return out
}

/** Точки ломаной: [[x, y], ...] */
private fun JSONArray?.mapPoints(): List<MapPoint> {
    if (this == null) return emptyList()
    val out = ArrayList<MapPoint>(length())
    for (i in 0 until length()) {
        val p = optJSONArray(i) ?: continue
        if (p.length() >= 2) out.add(MapPoint(p.optDouble(0).toFloat(), p.optDouble(1).toFloat()))
    }
    return out
}

/** Список строк: ["id", ...] */
private fun JSONArray?.mapStrings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
    return out
}
