package com.politex

import android.os.Build
import com.politex.ui.screens.exactAlarmHintNeeded
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// C3: подсказка про точные будильники — только SDK 31+ и только без разрешения
class ExactAlarmHintTest {
    @Test
    fun `подсказка показана на SDK 31+ без разрешения`() {
        assertTrue(exactAlarmHintNeeded(Build.VERSION_CODES.S, false))
        assertTrue(exactAlarmHintNeeded(35, false))
    }

    @Test
    fun `подсказка скрыта если разрешение есть или SDK ниже 31`() {
        assertFalse(exactAlarmHintNeeded(Build.VERSION_CODES.S, true))
        assertFalse(exactAlarmHintNeeded(35, true))
        assertFalse(exactAlarmHintNeeded(30, false))
        assertFalse(exactAlarmHintNeeded(26, true))
    }
}
