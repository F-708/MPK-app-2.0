// ПАТЧ-ЗАГОТОВКА тестов. В app/src/test пока не применялась.
// Куда: app/src/test/java/com/politex/data/model/MapWallsDataTest.kt
//
// Гейты:
//  1) ассет читается и содержит все три этажа;
//  2) стены лежат внутри холста 1376x768;
//  3) каждый проём ссылается на существующий кабинет (связь слоёв не разъехалась);
//  4) кромки реальных кабинетов ложатся на стены — регресс при правке любого слоя.

package com.politex.data.model

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapWallsDataTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun walls() = MapWallsData.load(context)
    private fun map() = CollegeMapData.load(context)

    @Test
    fun `ассет читается и содержит все этажи`() {
        val data = walls()
        assertFalse("map_walls.json не загрузился", data.isEmpty)
        listOf("1", "2", "3-4").forEach { floorId ->
            val floor = data.floor(floorId)
            assertTrue("этаж $floorId: нет стен", floor.walls.count > 100)
        }
    }

    @Test
    fun `стены внутри холста плана`() {
        val data = walls()
        data.floor("1").walls.data.let { flat ->
            var index = 0
            while (index < flat.size) {
                val x0 = flat[index]
                val y0 = flat[index + 1]
                val x1 = flat[index + 2]
                val y1 = flat[index + 3]
                assertTrue("вырожденный участок", x1 > x0 && y1 > y0)
                assertTrue("выход за холст", x0 >= 0f && y0 >= 0f)
                assertTrue("выход за холст", x1 <= MapWallsData.PLAN_WIDTH && y1 <= MapWallsData.PLAN_HEIGHT)
                index += 4
            }
        }
    }

    @Test
    fun `каждый проём ссылается на существующий кабинет`() {
        val data = walls()
        val semantic = map()
        data.floor("1").doors.forEach { door ->
            assertTrue(
                "проём ссылается на неизвестный кабинет ${door.roomId}",
                semantic.floor("1").rooms.any { it.id == door.roomId },
            )
            assertTrue("нулевая ширина проёма", door.width > 0f)
        }
    }

    /**
     * Регресс-гейт: минимум две кромки из четырёх у 90% реальных кабинетов
     * должны лежать на трассированных стенах. Если кто-то сдвинет кабинеты в
     * college_map.json или перегенерирует стены другим порогом — тест упадёт.
     */
    @Test
    fun `кромки кабинетов лежат на стенах`() {
        val data = walls()
        val semantic = map()
        listOf("1", "2", "3-4").forEach { floorId ->
            val walls = data.floor(floorId)
            val flat = walls.walls.data
            var checked = 0
            var good = 0
            semantic.floor(floorId).rooms
                .filter { it.status != "nonexistent" && it.number.isNotBlank() }
                .forEach { room ->
                    val left = room.x - room.w / 2f
                    val right = room.x + room.w / 2f
                    val top = room.y - room.h / 2f
                    val bottom = room.y + room.h / 2f
                    var edges = 0
                    if (wallOnSegment(flat, true, top, left, right)) edges++
                    if (wallOnSegment(flat, true, bottom, left, right)) edges++
                    if (wallOnSegment(flat, false, left, top, bottom)) edges++
                    if (wallOnSegment(flat, false, right, top, bottom)) edges++
                    checked++
                    if (edges >= 2) good++
                }
            val ratio = good.toFloat() / checked
            assertTrue("этаж $floorId: совпадение слоёв %.2f".format(ratio), ratio >= 0.9f)
        }
    }

    /**
     * Есть ли стена, перекрывающая отрезок (горизонтальный при [horizontal] = true)
     * хотя бы на 40% его длины. Допуск 2 px на толщину линии.
     */
    private fun wallOnSegment(
        flat: FloatArray,
        horizontal: Boolean,
        fixed: Float,
        from: Float,
        to: Float,
    ): Boolean {
        val length = (to - from).coerceAtLeast(0.01f)
        var covered = 0f
        var index = 0
        while (index < flat.size) {
            val x0 = flat[index]
            val y0 = flat[index + 1]
            val x1 = flat[index + 2]
            val y1 = flat[index + 3]
            val near = if (horizontal) {
                fixed >= y0 - 2f && fixed <= y1 + 2f
            } else {
                fixed >= x0 - 2f && fixed <= x1 + 2f
            }
            if (near) {
                val a = if (horizontal) maxOf(x0, minOf(from, to)) else maxOf(y0, minOf(from, to))
                val b = if (horizontal) minOf(x1, maxOf(from, to)) else minOf(y1, maxOf(from, to))
                if (b > a) covered += b - a
            }
            index += 4
        }
        return covered / length >= 0.4f
    }
}