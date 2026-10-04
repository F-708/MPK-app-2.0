package com.politex

import com.politex.data.model.BellScheduleType
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.screens.bellsForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BellsSundayTest {
    @Test
    fun `AA3 sunday with today type shows no bells`() {
        assertTrue(bellsForDisplay(todayDow = 7, isTodaySelected = true, type = BellScheduleType.STANDARD).isEmpty())
    }

    @Test
    fun `AA3 sunday with other type shows its bells as usual`() {
        assertEquals(CollegeBellSchedule.THURSDAY_BELLS, bellsForDisplay(7, false, BellScheduleType.THURSDAY))
        assertEquals(CollegeBellSchedule.SATURDAY_BELLS, bellsForDisplay(7, false, BellScheduleType.SATURDAY))
    }

    @Test
    fun `AA3 weekdays are unaffected`() {
        assertEquals(CollegeBellSchedule.STANDARD_BELLS, bellsForDisplay(3, true, BellScheduleType.STANDARD))
        assertEquals(CollegeBellSchedule.STANDARD_BELLS, bellsForDisplay(3, false, BellScheduleType.STANDARD))
        assertEquals(CollegeBellSchedule.THURSDAY_BELLS, bellsForDisplay(4, true, BellScheduleType.THURSDAY))
    }
}
