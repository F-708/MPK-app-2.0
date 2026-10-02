package com.politex

import com.politex.util.GradeAggregator
import com.politex.util.GradeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ф4.2 (G2): формула заказчика — парное сворачивание по priority.
 * Чистый JVM-тест: GradeAggregator не знает ни про Room, ни про Android.
 * Кейсы из спеки: без экзамена, с экзаменом, пустая контрольная пропускается,
 * только практ, свой priority, LAB не входит, округление до 1 знака.
 */
class GradeAggregatorTest {
    private val delta = 1e-9

    @Test
    fun `дефолтная формула без экзамена`() {
        // ((8 + 7) / 2 + 9) / 2 = 8.25 — обычные средние, практ, контрольная
        val avg =
            GradeAggregator.average(
                mapOf(
                    "REGULAR" to listOf(8, 8),
                    "PRACTICAL" to listOf(7),
                    "CONTROL" to listOf(9),
                ),
            )
        assertEquals(8.25, avg!!, delta)
    }

    @Test
    fun `дефолтная формула с экзаменом`() {
        // (((8 + 7) / 2 + 9) / 2 + 10) / 2 = 9.125
        val avg =
            GradeAggregator.average(
                mapOf(
                    "REGULAR" to listOf(8),
                    "PRACTICAL" to listOf(7),
                    "CONTROL" to listOf(9),
                    "EXAM" to listOf(10),
                ),
            )
        assertEquals(9.125, avg!!, delta)
        assertEquals(9.1, GradeAggregator.round1(avg), delta)
    }

    @Test
    fun `пустая контрольная пропускается без сдвига acc`() {
        // (8 + 6) / 2 = 7.0; CONTROL пуст — acc НЕ делится лишний раз
        val avg =
            GradeAggregator.average(
                mapOf(
                    "REGULAR" to listOf(8),
                    "PRACTICAL" to listOf(6),
                    "CONTROL" to emptyList(),
                ),
            )
        assertEquals(7.0, avg!!, delta)

        // то же самое, если категории вообще нет в карте
        assertEquals(
            7.0,
            GradeAggregator.average(mapOf("REGULAR" to listOf(8), "PRACTICAL" to listOf(6)))!!,
            delta,
        )
    }

    @Test
    fun `только практ — средний равен среднему категории`() {
        val avg = GradeAggregator.average(mapOf("PRACTICAL" to listOf(7, 9)))
        assertEquals(8.0, avg!!, delta)
    }

    @Test
    fun `нет оценок вовсе — null, а не ноль`() {
        assertNull(GradeAggregator.average(emptyMap()))
        assertNull(GradeAggregator.average(mapOf("REGULAR" to emptyList(), "EXAM" to emptyList())))
    }

    @Test
    fun `свой priority меняет результат сворачивания`() {
        val values = mapOf("REGULAR" to listOf(6), "CONTROL" to listOf(8), "EXAM" to listOf(10))
        // дефолт: ((6 + 8) / 2 + 10) / 2 = 8.5
        assertEquals(8.5, GradeAggregator.average(values)!!, delta)
        // свой: экзамен первым — ((10 + 8) / 2 + 6) / 2 = 7.5
        assertEquals(
            7.5,
            GradeAggregator.average(values, listOf("EXAM", "CONTROL", "REGULAR"))!!,
            delta,
        )
    }

    @Test
    fun `LAB не входит в агрегацию даже если попал в priority`() {
        val values = mapOf("REGULAR" to listOf(8), "LAB" to listOf(0, 1))
        assertEquals(8.0, GradeAggregator.average(values)!!, delta)
        // явно сунули LAB в priority — всё равно пропускается
        assertEquals(
            8.0,
            GradeAggregator.average(values, listOf("REGULAR", "LAB"))!!,
            delta,
        )
        // только LAB → среднего нет
        assertNull(GradeAggregator.average(mapOf("LAB" to listOf(1)), listOf("LAB")))
    }

    @Test
    fun `округление и формат показа — 1 знак`() {
        assertEquals(8.3, GradeAggregator.round1(8.25), delta)
        assertEquals(9.1, GradeAggregator.round1(9.125), delta)
        assertEquals("8.3", GradeAggregator.format(8.25))
        assertEquals("7", GradeAggregator.format(7.0))
        assertEquals("—", GradeAggregator.format(null))
    }

    @Test
    fun `дефолтный priority из спеки — без LAB, порядок REGULAR PRACTICAL CONTROL EXAM`() {
        assertEquals(
            listOf(
                GradeCategory.REGULAR.name,
                GradeCategory.PRACTICAL.name,
                GradeCategory.CONTROL.name,
                GradeCategory.EXAM.name,
            ),
            GradeAggregator.DEFAULT_PRIORITY,
        )
    }

    // ── W22: buildTreeLevels — дерево формулы для отрисовки ──────────────────

    @Test
    fun `buildTreeLevels — лестница склеивает первые два узла каждого уровня`() {
        assertEquals(
            listOf(
                listOf("REGULAR", "PRACTICAL", "CONTROL", "EXAM"),
                listOf("REGULAR+PRACTICAL", "CONTROL", "EXAM"),
                listOf("REGULAR+PRACTICAL+CONTROL", "EXAM"),
                listOf("REGULAR+PRACTICAL+CONTROL+EXAM"),
            ),
            GradeAggregator.buildTreeLevels(GradeAggregator.DEFAULT_PRIORITY),
        )
    }

    @Test
    fun `buildTreeLevels — пустые категории и LAB выпадают из дерева`() {
        assertEquals(
            listOf(listOf("REGULAR", "EXAM"), listOf("REGULAR+EXAM")),
            GradeAggregator.buildTreeLevels(
                listOf("REGULAR", "LAB", "CONTROL", "EXAM"),
                setOf("REGULAR", "EXAM", "LAB"),
            ),
        )
    }

    @Test
    fun `buildTreeLevels — один лист и совсем пусто`() {
        assertEquals(listOf(listOf("PRACTICAL")), GradeAggregator.buildTreeLevels(listOf("PRACTICAL")))
        assertEquals(emptyList<String>(), GradeAggregator.buildTreeLevels(listOf("REGULAR"), emptySet()))
        assertEquals(emptyList<String>(), GradeAggregator.buildTreeLevels(emptyList()))
    }

    @Test
    fun `buildTreeLevels — порядок узлов зеркалит порядок сворачивания average`() {
        // дерево по приоритету [EXAM, CONTROL, REGULAR] — пары те же, что в average
        val priority = listOf("EXAM", "CONTROL", "REGULAR")
        assertEquals(
            listOf(
                listOf("EXAM", "CONTROL", "REGULAR"),
                listOf("EXAM+CONTROL", "REGULAR"),
                listOf("EXAM+CONTROL+REGULAR"),
            ),
            GradeAggregator.buildTreeLevels(priority),
        )
        // верхний узел = те же листья, что сворачивает average
        assertEquals(
            GradeAggregator.average(mapOf("EXAM" to listOf(10), "CONTROL" to listOf(8), "REGULAR" to listOf(6)), priority)!!,
            GradeAggregator.average(
                mapOf("EXAM" to listOf(10), "CONTROL" to listOf(8), "REGULAR" to listOf(6)),
                GradeAggregator.buildTreeLevels(priority).last().single().split("+"),
            )!!,
            delta,
        )
    }
}
