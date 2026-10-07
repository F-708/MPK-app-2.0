package com.politex

import com.politex.data.local.entity.TeacherOverrideEntity
import com.politex.data.repository.LessonStatus
import com.politex.data.repository.OverrideResetReason
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** SPEC §5.4: оверлей личных правок, полевой diff-сброс, прун 60 дней. */
class TeacherOverrideLogicTest {
    private val date = "05.10.2026"

    private fun lesson(
        n: Int,
        room: String = "241",
        start: String = "09:50",
        end: String = "10:35",
        subject: String = "Математика",
        groups: List<String> = listOf("41О"),
    ) = TeacherDayLesson(n, subject, groups, room, start, end, 9 * 60 + 50, 10 * 60 + 35)

    private fun override(
        n: Int,
        kind: String = TeacherOverrideEntity.KIND_OTHER_ROOM,
        dateString: String = date,
        room: String = "305",
    ) = TeacherOverrideEntity(dateString = dateString, lessonNumber = n, kind = kind, room = room)

    private fun cal(y: Int, m: Int, d: Int): Calendar = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }

    // ——— оверлей: три kind'а ———

    @Test
    fun `OTHER_ROOM — кабинет заменён, маркер overridden`() {
        val out = TeacherDayLogic.applyOverrides(date, listOf(lesson(3)), listOf(override(3)))
        assertEquals(1, out.size)
        assertEquals("305", out[0].room)
        assertTrue(out[0].overridden)
        assertFalse("это не замена", out[0].isReplacement)
    }

    @Test
    fun `NO_LESSON — пара исключается`() {
        val out = TeacherDayLogic.applyOverrides(date, listOf(lesson(1), lesson(3)), listOf(override(3, TeacherOverrideEntity.KIND_NO_LESSON)))
        assertEquals(listOf(1), out.map { it.lessonNumber })
    }

    @Test
    fun `SOMEONE_ELSE — пара остаётся с маркером замены`() {
        val out = TeacherDayLogic.applyOverrides(date, listOf(lesson(3)), listOf(override(3, TeacherOverrideEntity.KIND_SOMEONE_ELSE)))
        assertEquals(1, out.size)
        assertTrue(out[0].isReplacement)
        assertTrue(out[0].overridden)
        assertEquals("241", out[0].room)
    }

    @Test
    fun `правка на несуществующую пару, чужую дату и пустой список не ломают день`() {
        val lessons = listOf(lesson(1), lesson(3))
        assertEquals(lessons, TeacherDayLogic.applyOverrides(date, lessons, listOf(override(7))))
        assertEquals(lessons, TeacherDayLogic.applyOverrides(date, lessons, emptyList()))
        assertEquals("правку другого дня не применяем", lessons, TeacherDayLogic.applyOverrides(date, lessons, listOf(override(3, dateString = "06.10.2026"))))
        // пустой OTHER_ROOM не стирает кабинет из документа
        assertEquals("241", TeacherDayLogic.applyOverrides(date, lessons, listOf(override(1, room = "  ")))[0].room)
    }

    @Test
    fun `статусы игнорируют пару-замену`() {
        val lessons = listOf(lesson(1), lesson(2, start = "10:45", end = "11:30").copy(isReplacement = true))
        val statuses = TeacherDayLogic.statuses(lessons, 10 * 60 + 50, isToday = true)
        assertEquals(LessonStatus.PAST, statuses[0])
        assertEquals("замена не занимает ИДЁТ", LessonStatus.UPCOMING, statuses[1])
    }

    // ——— полевой diff-сброс ———

    @Test
    fun `изменился кабинет — правка сброшена с новым кабинетом`() {
        val before = listOf(lesson(3, room = "241"))
        val after = listOf(lesson(3, room = "305"))
        val resets = TeacherDayLogic.diffOverrideResets(date, before, after, listOf(override(3)))
        assertEquals(1, resets.size)
        assertEquals(3, resets[0].lessonNumber)
        assertEquals(OverrideResetReason.ROOM_CHANGED, resets[0].reason)
        assertEquals("305", resets[0].newRoom)
    }

    @Test
    fun `время то же и комната та же — правка НЕ сброшена`() {
        val before = listOf(lesson(3, room = "241"))
        val after = listOf(lesson(3, room = " 241 "))
        assertTrue(TeacherDayLogic.diffOverrideResets(date, before, after, listOf(override(3))).isEmpty())
        // другой порядок групп — тоже не изменение
        val afterGroups = listOf(lesson(3, groups = listOf("42О", "41О")))
        val beforeGroups = listOf(lesson(3, groups = listOf("41О", "42О")))
        assertTrue(TeacherDayLogic.diffOverrideResets(date, beforeGroups, afterGroups, listOf(override(3))).isEmpty())
    }

    @Test
    fun `изменилось время, предмет или группы — сброс по конкретному полю`() {
        val resetsTime = TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3)), listOf(lesson(3, start = "11:40", end = "12:25")), listOf(override(3)))
        assertEquals(OverrideResetReason.TIME_CHANGED, resetsTime.single().reason)
        val resetsSubject = TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3)), listOf(lesson(3, subject = "Физика")), listOf(override(3)))
        assertEquals(OverrideResetReason.SUBJECT_CHANGED, resetsSubject.single().reason)
        val resetsGroups = TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3)), listOf(lesson(3, groups = listOf("42О"))), listOf(override(3)))
        assertEquals(OverrideResetReason.GROUPS_CHANGED, resetsGroups.single().reason)
    }

    @Test
    fun `пара исчезла из дока — сброс с пометкой LESSON_GONE`() {
        val resets = TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3)), emptyList(), listOf(override(3)))
        assertEquals(OverrideResetReason.LESSON_GONE, resets.single().reason)
        assertEquals("", resets.single().newRoom)
    }

    @Test
    fun `новая пара появилась — старые правки не тронуты`() {
        val before = listOf(lesson(3, room = "241"))
        val after = listOf(lesson(3, room = "241"), lesson(5, room = "118"))
        val overrides = listOf(override(3), override(5, room = "999"))
        val resets = TeacherDayLogic.diffOverrideResets(date, before, after, overrides)
        assertTrue("правку новой пары не сбрасываем без доказательства", resets.isEmpty())
    }

    @Test
    fun `две строки одного номера — порядок не важен, изменение в любой сбрасывает`() {
        val before = listOf(lesson(3, room = "241"), lesson(3, room = "305", subject = "Физика", groups = listOf("42О")))
        val reordered = listOf(lesson(3, room = "305", subject = "Физика", groups = listOf("42О")), lesson(3, room = "241"))
        assertTrue(TeacherDayLogic.diffOverrideResets(date, before, reordered, listOf(override(3))).isEmpty())
        val changed = listOf(lesson(3, room = "241"), lesson(3, room = "118", subject = "Физика", groups = listOf("42О")))
        val resets = TeacherDayLogic.diffOverrideResets(date, before, changed, listOf(override(3)))
        assertEquals(OverrideResetReason.ROOM_CHANGED, resets.single().reason)
        assertEquals("118, 241", resets.single().newRoom)
    }

    @Test
    fun `чужая дата и отсутствие правок — сбросов нет`() {
        assertTrue(TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3)), listOf(lesson(3, room = "999")), emptyList()).isEmpty())
        val other = listOf(override(3, dateString = "06.10.2026"))
        assertTrue(TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3)), listOf(lesson(3, room = "999")), other).isEmpty())
    }

    @Test
    fun `текст плашки называет день, номер пары и новый кабинет`() {
        val resets = TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3, room = "241")), listOf(lesson(3, room = "305")), listOf(override(3)))
        val text = TeacherDayLogic.resetMessage(resets.single())
        assertEquals("Расписание обновлено: ваша правка на 05.10, пн, 3-я пара сброшена — в доке теперь 305", text)
        val gone = TeacherDayLogic.diffOverrideResets(date, listOf(lesson(3)), emptyList(), listOf(override(3)))
        assertTrue(TeacherDayLogic.resetMessage(gone.single()).endsWith("пары больше нет в документе"))
    }

    // ——— прун 60 дней (Calendar, не лексикографика) ———

    @Test
    fun `прун — старше 60 дней по Calendar, граница и будущее живут`() {
        val today = cal(2026, 10, 7)
        val keep = listOf(
            override(1, dateString = "08.08.2026"), // ровно 60 дней назад — граница включительно
            override(2, dateString = "05.10.2026"), // лексикографически «меньше» 07.08.2026 — но это позавчера
            override(3, dateString = "08.10.2026"), // будущее
            override(4, dateString = "мусор"), // неразобранную дату не удаляем
        )
        val prune = listOf(
            override(5, dateString = "07.08.2026"), // 61 день
            override(6, dateString = "01.09.2025"), // год назад
        )
        assertEquals(prune, TeacherDayLogic.overridesToPrune(keep + prune, today))
        assertTrue(TeacherDayLogic.overridesToPrune(keep, today).isEmpty())
        assertTrue(TeacherDayLogic.overridesToPrune(emptyList(), today).isEmpty())
        // явный срок жизни: будущее не prune'ится даже при окне в день
        assertEquals(listOf(keep[0], keep[1]), TeacherDayLogic.overridesToPrune(keep, today, maxAgeDays = 1))
    }
}
