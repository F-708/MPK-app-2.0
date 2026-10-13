package com.politex.teacher

import com.politex.ui.teacher.BellStyleChoice
import com.politex.ui.teacher.bellStyleForChoice
import com.politex.ui.teacher.choiceForStyle
import com.politex.util.BellCountdownNotifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TR7: стиль «до звонка» в teacher-UI — ТОЛЬКО два варианта. Проверяем чистый маппинг
 * UI-выбор ↔ BellCountdownNotifier.Style: из UI достижимы лишь COMPACT и WITH_SECONDS.
 */
class TeacherBellStyleTest {
    @Test
    fun `выбор в минутах маппится в COMPACT`() {
        assertEquals(BellCountdownNotifier.Style.COMPACT, bellStyleForChoice(BellStyleChoice.MINUTES))
    }

    @Test
    fun `выбор с секундами маппится в WITH_SECONDS`() {
        assertEquals(BellCountdownNotifier.Style.WITH_SECONDS, bellStyleForChoice(BellStyleChoice.SECONDS))
    }

    @Test
    fun `только два варианта стиля существуют`() {
        assertEquals(2, BellStyleChoice.entries.size)
    }

    @Test
    fun `обратный маппинг COMPACT и WITH_SECONDS`() {
        assertEquals(BellStyleChoice.MINUTES, choiceForStyle(BellCountdownNotifier.Style.COMPACT))
        assertEquals(BellStyleChoice.SECONDS, choiceForStyle(BellCountdownNotifier.Style.WITH_SECONDS))
    }

    @Test
    fun `студенческие стили недостижимые в teacher схлопываются в В минутах`() {
        // WITH_LESSON/WITH_PROGRESS/WITH_APP_NAME в teacher-UI не предлагаются; если prefs
        // остались со студенческой стороны — показываем «В минутах», но не теряем WITH_SECONDS.
        assertEquals(BellStyleChoice.MINUTES, choiceForStyle(BellCountdownNotifier.Style.WITH_LESSON))
        assertEquals(BellStyleChoice.MINUTES, choiceForStyle(BellCountdownNotifier.Style.WITH_PROGRESS))
        assertEquals(BellStyleChoice.MINUTES, choiceForStyle(BellCountdownNotifier.Style.WITH_APP_NAME))
    }

    @Test
    fun `roundtrip выбор-стиль-выбор стабилен`() {
        BellStyleChoice.entries.forEach { choice ->
            assertEquals(choice, choiceForStyle(bellStyleForChoice(choice)))
        }
    }
}
