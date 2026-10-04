package com.politex

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.politex.ui.components.TutorialController
import com.politex.ui.components.TutorialFeature
import com.politex.ui.components.TutorialGesture
import com.politex.ui.components.trajectorySwipe
import com.politex.ui.components.trajectoryTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTrajectoryTest {
    private val rect = Rect(0f, 0f, 100f, 50f)

    @Test
    fun `only two features remain`() {
        assertEquals(
            listOf("TASK_SWIPE", "SCHEDULE_TAP"),
            TutorialFeature.values().map { it.name },
        )
    }

    @Test
    fun `schedule tap is a TAP gesture with room and teacher anchors`() {
        val f = TutorialFeature.SCHEDULE_TAP
        assertEquals(TutorialGesture.TAP, f.gesture)
        assertEquals("schedule_room_target", f.anchorId)
        assertEquals("schedule_teacher_target", f.altAnchorId)
    }

    @Test
    fun `schedule trajectory is a tap on the anchor`() {
        val pts = trajectoryTap(rect)
        assertEquals(1, pts.size)
        assertEquals(rect.center, pts[0])
    }

    @Test
    fun `success by any of two anchors closes only the active feature`() {
        TutorialController.clear()

        TutorialController.success(TutorialFeature.SCHEDULE_TAP)
        assertEquals(0, TutorialController.successSignal)

        TutorialController.active = TutorialFeature.SCHEDULE_TAP

        TutorialController.success(TutorialFeature.TASK_SWIPE)
        assertEquals(0, TutorialController.successSignal)

        TutorialController.success(TutorialFeature.SCHEDULE_TAP)
        assertEquals(1, TutorialController.successSignal)

        TutorialController.clear()
        assertEquals(0, TutorialController.successSignal)
        assertNull(TutorialController.active)
    }

    @Test
    fun `swipe is two points, right to left`() {
        val pts = trajectorySwipe(rect)
        assertEquals(2, pts.size)
        val (start, end) = pts

        assertEquals(rect.center.y, start.y, 0.001f)
        assertEquals(rect.center.y, end.y, 0.001f)

        assertTrue("свайп должен идти справа налево", end.x < start.x)
    }

    @Test
    fun `swipe covers 60 percent of hole width`() {
        val (start, end) = trajectorySwipe(rect)
        assertEquals(rect.width * 0.6f, start.x - end.x, 0.001f)
    }

    @Test
    fun `swipe stays inside the hole horizontally`() {
        val (start, end) = trajectorySwipe(rect)
        assertTrue(start.x <= rect.right)
        assertTrue(end.x >= rect.left)
    }
}
