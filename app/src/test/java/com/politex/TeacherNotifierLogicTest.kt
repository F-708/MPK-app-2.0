package com.politex

import com.politex.data.repository.OverrideReset
import com.politex.data.repository.OverrideResetReason
import com.politex.data.repository.TeacherDayLesson
import com.politex.util.TeacherNotifierLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * T4-B: чистая логика уведомлений преподавателя (SPEC §5.3, §4.1 фичи 5–7, контракт честности §4.2 «д»).
 */
class TeacherNotifierLogicTest {
    private fun hm(h: Int, m: Int) = h * 60 + m

    private fun lesson(
        n: Int,
        subject: String = "Математика",
        room: String = "241",
        start: String = "09:50",
        end: String = "11:20",
        startMin: Int = 590,
        endMin: Int = 680,
        groups: List<String> = listOf("ИС-21"),
        replacement: Boolean = false,
    ) = TeacherDayLesson(n, subject, groups, room, start, end, startMin, endMin, isReplacement = replacement)

    private fun epoch(y: Int, m1: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance().apply {
            set(y, m1 - 1, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ——— quietHours 21:00–07:00 (логика откладывания, не уровень канала) ———

    @Test
    fun `quiet hours boundaries`() {
        assertTrue(TeacherNotifierLogic.quietHours(hm(21, 0)))
        assertTrue(TeacherNotifierLogic.quietHours(hm(23, 59)))
        assertTrue(TeacherNotifierLogic.quietHours(hm(6, 59)))
        assertFalse(TeacherNotifierLogic.quietHours(hm(7, 0)))
        assertFalse(TeacherNotifierLogic.quietHours(hm(20, 59)))
    }

    // ——— shouldSendSummary ———

    @Test
    fun `summary not before 07-15`() {
        assertFalse(TeacherNotifierLogic.shouldSendSummary(hm(7, 14), sentFlag = false, hasCache = true, syncOk = true, enabled = true))
        assertTrue(TeacherNotifierLogic.shouldSendSummary(hm(7, 15), sentFlag = false, hasCache = true, syncOk = true, enabled = true))
    }

    @Test
    fun `summary blocked by sent flag and by setting`() {
        assertFalse(TeacherNotifierLogic.shouldSendSummary(hm(8, 0), sentFlag = true, hasCache = true, syncOk = true, enabled = true))
        assertFalse(TeacherNotifierLogic.shouldSendSummary(hm(8, 0), sentFlag = false, hasCache = true, syncOk = true, enabled = false))
    }

    @Test
    fun `summary needs cache or successful sync`() {
        assertFalse(TeacherNotifierLogic.shouldSendSummary(hm(8, 0), sentFlag = false, hasCache = false, syncOk = false, enabled = true))
        assertTrue(TeacherNotifierLogic.shouldSendSummary(hm(8, 0), sentFlag = false, hasCache = false, syncOk = true, enabled = true))
        assertTrue(TeacherNotifierLogic.shouldSendSummary(hm(8, 0), sentFlag = false, hasCache = true, syncOk = false, enabled = true))
    }

    // ——— summaryText: три ветки SPEC §5.3 + «пар нет» + молчание ———

    @Test
    fun `summary fresh sync text`() {
        val text = TeacherNotifierLogic.summaryText(
            listOf(lesson(1), lesson(2, subject = "Физика", room = "100", startMin = 650), lesson(3, startMin = 710)),
            syncFresh = true,
            cacheTime = "06:40",
            syncHm = "06:40",
        )
        assertEquals("Сегодня 3 пары, первая 09:50 — Математика, каб. 241 (данные сайта на 06:40)", text)
    }

    @Test
    fun `summary fresh without room omits kab`() {
        val text = TeacherNotifierLogic.summaryText(listOf(lesson(1, room = "")), syncFresh = true, cacheTime = null, syncHm = "07:15")
        assertEquals("Сегодня 1 пара, первая 09:50 — Математика (данные сайта на 07:15)", text)
    }

    @Test
    fun `summary zero lessons is sent not silence`() {
        assertEquals("Сегодня пар нет.", TeacherNotifierLogic.summaryText(emptyList(), syncFresh = true, cacheTime = null, syncHm = "07:15"))
    }

    @Test
    fun `summary counts only my lessons - replacements excluded`() {
        val lessons = listOf(lesson(1, replacement = true), lesson(2, startMin = 650))
        assertEquals("Сегодня 1 пара, первая 09:50 — Математика, каб. 241 (данные сайта на 07:15)", TeacherNotifierLogic.summaryText(lessons, true, null, "07:15"))
        // только замены → для меня пар нет (честно)
        assertEquals("Сегодня пар нет.", TeacherNotifierLogic.summaryText(listOf(lesson(1, replacement = true)), true, null, "07:15"))
    }

    @Test
    fun `summary stale cache text`() {
        val text = TeacherNotifierLogic.summaryText(listOf(lesson(1)), syncFresh = false, cacheTime = "вчера 21:40", syncHm = "07:15")
        assertEquals("Сегодня по расписанию с сайта от вчера 21:40 — возможны изменения, сверьтесь с чатом учебной части", text)
    }

    @Test
    fun `summary stale without ever syncing omits ot-clause`() {
        val text = TeacherNotifierLogic.summaryText(listOf(lesson(1)), syncFresh = false, cacheTime = null, syncHm = "07:15")
        assertEquals("Сегодня по расписанию с сайта — возможны изменения, сверьтесь с чатом учебной части", text)
    }

    @Test
    fun `summary silence when empty cache and failed sync`() {
        // §4.2 «д»: право промолчать вместо врущего пуша
        assertNull(TeacherNotifierLogic.summaryText(emptyList(), syncFresh = false, cacheTime = "вчера 21:40", syncHm = "07:15"))
    }

    @Test
    fun `pairs word pluralization`() {
        assertEquals("пара", TeacherNotifierLogic.pairsWord(1))
        assertEquals("пары", TeacherNotifierLogic.pairsWord(2))
        assertEquals("пар", TeacherNotifierLogic.pairsWord(5))
        assertEquals("пар", TeacherNotifierLogic.pairsWord(11))
        assertEquals("пара", TeacherNotifierLogic.pairsWord(21))
        assertEquals("пар", TeacherNotifierLogic.pairsWord(112))
    }

    @Test
    fun `lesson count is distinct slots not rows`() {
        // две группы в одном слоте (номер 1) + одна пара номер 2 → 2 слота, не 3 строки
        val lessons = listOf(lesson(1, subject = "А"), lesson(1, subject = "Б"), lesson(2, startMin = 650))
        assertEquals(2, TeacherNotifierLogic.lessonCount(lessons))
        assertEquals(0, TeacherNotifierLogic.lessonCount(emptyList()))
    }

    // ——— cacheTimeLabel ———

    @Test
    fun `cache time label today yesterday older and never`() {
        val now = epoch(2026, 10, 7, 10, 0)
        assertEquals("06:40", TeacherNotifierLogic.cacheTimeLabel(epoch(2026, 10, 7, 6, 40), now))
        assertEquals("вчера 21:40", TeacherNotifierLogic.cacheTimeLabel(epoch(2026, 10, 6, 21, 40), now))
        assertEquals("04.10 21:40", TeacherNotifierLogic.cacheTimeLabel(epoch(2026, 10, 4, 21, 40), now))
        assertNull(TeacherNotifierLogic.cacheTimeLabel(0L, now))
    }

    // ——— dateLabel ———

    @Test
    fun `date label today tomorrow other`() {
        assertEquals("сегодня", TeacherNotifierLogic.dateLabel("07.10.2026", "07.10.2026", "08.10.2026"))
        assertEquals("завтра", TeacherNotifierLogic.dateLabel("08.10.2026", "07.10.2026", "08.10.2026"))
        assertEquals("10.10, сб", TeacherNotifierLogic.dateLabel("10.10.2026", "07.10.2026", "08.10.2026"))
    }

    // ——— diffMessages: склеивание ≤1 на день, SPEC-пример ———

    @Test
    fun `diff spec example - moved and added`() {
        val before = mapOf("08.10.2026" to listOf(lesson(3, room = "100")))
        val after = mapOf("08.10.2026" to listOf(lesson(3, room = "241"), lesson(5, subject = "Химия", startMin = 770)))
        val messages = TeacherNotifierLogic.diffMessages(before, after, mapOf("08.10.2026" to "завтра"))
        assertEquals(listOf("Изменения на завтра: 3-я пара перенесена в 241; добавлена 5-я пара — Химия"), messages)
    }

    @Test
    fun `diff removed and changed time and subject`() {
        val before = mapOf("07.10.2026" to listOf(lesson(2), lesson(3, start = "11:30", end = "13:00"), lesson(4)))
        val after = mapOf("07.10.2026" to listOf(lesson(3, start = "09:50", end = "11:20"), lesson(4, subject = "Физика")))
        val messages = TeacherNotifierLogic.diffMessages(before, after, mapOf("07.10.2026" to "сегодня"))
        assertEquals(listOf("Изменения на сегодня: 2-я пара отменена; 3-я пара теперь 09:50–11:20; 4-я пара другой предмет/группы"), messages)
    }

    @Test
    fun `diff no changes - empty`() {
        val day = listOf(lesson(1), lesson(2))
        assertTrue(TeacherNotifierLogic.diffMessages(mapOf("d" to day), mapOf("d" to day), mapOf("d" to "сегодня")).isEmpty())
    }

    @Test
    fun `diff two days glued into one list one line per day`() {
        val before = mapOf("07.10.2026" to listOf(lesson(1, room = "100")), "08.10.2026" to emptyList())
        val after = mapOf("07.10.2026" to listOf(lesson(1, room = "241")), "08.10.2026" to listOf(lesson(1)))
        val messages = TeacherNotifierLogic.diffMessages(before, after, mapOf("07.10.2026" to "сегодня", "08.10.2026" to "завтра"))
        assertEquals(2, messages.size)
        assertTrue(messages[0].startsWith("Изменения на сегодня: "))
        assertTrue(messages[1].startsWith("Изменения на завтра: "))
    }

    @Test
    fun `diff multi-row lesson number compares aggregates`() {
        // две группы в одном слоте: изменение во ВТОРОЙ строке не должно пройти молча
        val before = mapOf("d" to listOf(lesson(1, subject = "А"), lesson(1, subject = "Б")))
        val after = mapOf("d" to listOf(lesson(1, subject = "А"), lesson(1, subject = "В")))
        val messages = TeacherNotifierLogic.diffMessages(before, after, mapOf("d" to "сегодня"))
        assertEquals(listOf("Изменения на сегодня: 1-я пара другой предмет/группы"), messages)
    }

    @Test
    fun `diff hash stable and discriminating - dedup key`() {
        val m1 = listOf("Изменения на завтра: 3-я пара перенесена в 241")
        val m2 = listOf("Изменения на завтра: 3-я пара перенесена в 100")
        assertEquals(TeacherNotifierLogic.diffHash(m1), TeacherNotifierLogic.diffHash(m1.toList()))
        assertNotEquals(TeacherNotifierLogic.diffHash(m1), TeacherNotifierLogic.diffHash(m2))
    }

    // ——— reminderDue / reminderCandidates: только смена кабинета или первая пара, ЗАМЕНА игнорируется ———

    private val day = listOf(
        lesson(1, room = "100", startMin = 480, endMin = 530), // первая → напоминаем
        lesson(2, room = "100", startMin = 540, endMin = 590), // тот же кабинет → молчим
        lesson(3, room = "241", startMin = 600, endMin = 650), // смена → напоминаем
    )

    @Test
    fun `reminder window is start minus n until start`() {
        assertEquals(1, TeacherNotifierLogic.reminderDue(day, 480 - 10, 10)?.lessonNumber)
        assertEquals(1, TeacherNotifierLogic.reminderDue(day, 479, 10)?.lessonNumber)
        assertNull(TeacherNotifierLogic.reminderDue(day, 480, 10)) // пара началась — уже поздно
        assertNull(TeacherNotifierLogic.reminderDue(day, 480 - 11, 10)) // рано
    }

    @Test
    fun `reminder only on room change or first lesson`() {
        assertNull(TeacherNotifierLogic.reminderDue(day, 540 - 5, 10)) // 2-я пара, тот же кабинет
        assertEquals(3, TeacherNotifierLogic.reminderDue(day, 600 - 5, 10)?.lessonNumber) // 3-я, кабинет сменился
    }

    @Test
    fun `reminder n minutes setting respected`() {
        assertEquals(1, TeacherNotifierLogic.reminderDue(day, 480 - 20, 20)?.lessonNumber)
        assertNull(TeacherNotifierLogic.reminderDue(day, 480 - 20, 10))
        assertEquals(1, TeacherNotifierLogic.reminderDue(day, 480 - 5, 5)?.lessonNumber)
    }

    @Test
    fun `replacement lessons never remind`() {
        val withZamena = listOf(
            lesson(1, room = "100", startMin = 480, replacement = true),
            lesson(2, room = "241", startMin = 540),
        )
        assertNull(TeacherNotifierLogic.reminderDue(withZamena, 475, 10)) // ЗАМЕНА — веду не я
        assertEquals(2, TeacherNotifierLogic.reminderDue(withZamena, 535, 10)?.lessonNumber)
    }

    @Test
    fun `replacement does not break room comparison chain`() {
        // 1-я (100) → замена (999) → 3-я (100): напоминание только на 1-ю, замена не «предыдущая»
        val lessons = listOf(
            lesson(1, room = "100", startMin = 480),
            lesson(2, room = "999", startMin = 540, replacement = true),
            lesson(3, room = "100", startMin = 600),
        )
        assertEquals(listOf(1), TeacherNotifierLogic.reminderCandidates(lessons).map { it.lessonNumber })
    }

    @Test
    fun `lesson without time is ignored`() {
        val lessons = listOf(lesson(1, startMin = -1, endMin = -1, start = "", end = ""), lesson(2, startMin = 540))
        assertEquals(listOf(2), TeacherNotifierLogic.reminderCandidates(lessons).map { it.lessonNumber })
    }

    // ——— nextReminderAt: время следующего будильника ———

    @Test
    fun `next reminder at strictly after now`() {
        assertEquals(470, TeacherNotifierLogic.nextReminderAt(day, 460, 10))
        assertEquals(590, TeacherNotifierLogic.nextReminderAt(day, 475, 10)) // окно 1-й пары ещё открыто, но будильник уже на 3-ю
        assertNull(TeacherNotifierLogic.nextReminderAt(day, 595, 10)) // на сегодня всё
        assertNull(TeacherNotifierLogic.nextReminderAt(emptyList(), 460, 10))
    }

    // ——— §5.4: системное уведомление о сбросе правки (текст, склейка, дедуп-ключ) ———

    @Test
    fun `reset notification text names day lesson and new room`() {
        val reset = OverrideReset("05.10.2026", 3, OverrideResetReason.ROOM_CHANGED, newRoom = "305")
        assertEquals(
            "Расписание обновлено: ваша правка на 05.10, пн, 3-я пара сброшена — в доке теперь 305",
            TeacherNotifierLogic.resetNotificationText(listOf(reset)),
        )
    }

    @Test
    fun `several resets are glued into one notification`() {
        val resets = listOf(
            OverrideReset("05.10.2026", 1, OverrideResetReason.ROOM_CHANGED, newRoom = "305"),
            OverrideReset("05.10.2026", 4, OverrideResetReason.LESSON_GONE),
        )
        val text = TeacherNotifierLogic.resetNotificationText(resets)
        assertEquals(2, text.lines().size)
        assertTrue(text.lines()[1].endsWith("пары больше нет в документе"))
    }

    @Test
    fun `reset dedup key is per date and lesson`() {
        val reset = OverrideReset("05.10.2026", 3, OverrideResetReason.ROOM_CHANGED)
        assertEquals("override_reset_05.10.2026_3", TeacherNotifierLogic.resetKey(reset))
        assertEquals(TeacherNotifierLogic.resetKey(reset), TeacherNotifierLogic.resetKey("05.10.2026", 3))
        assertNotEquals(TeacherNotifierLogic.resetKey(reset), TeacherNotifierLogic.resetKey("06.10.2026", 3))
        assertNotEquals(TeacherNotifierLogic.resetKey(reset), TeacherNotifierLogic.resetKey("05.10.2026", 4))
    }
}
