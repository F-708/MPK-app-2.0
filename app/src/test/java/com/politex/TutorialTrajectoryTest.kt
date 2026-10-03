package com.politex

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.politex.ui.components.trajectorySwipe
import com.politex.ui.components.trajectoryTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V14: траектории демо-жеста — чистые функции (Rect/Offset из compose-ui-geometry, Android не нужен).
 * Стенд: hole 100×50px в (0,0).
 */
class TutorialTrajectoryTest {
    private val rect = Rect(0f, 0f, 100f, 50f)

    @Test
    fun `tap is a single point at the center`() {
        val pts = trajectoryTap(rect)
        assertEquals(1, pts.size)
        assertEquals(rect.center, pts[0])
    }

    @Test
    fun `swipe is two points, right to left`() {
        val pts = trajectorySwipe(rect)
        assertEquals(2, pts.size)
        val (start, end) = pts
        // Обе точки на одной высоте (горизонтальный свайп).
        assertEquals(rect.center.y, start.y, 0.001f)
        assertEquals(rect.center.y, end.y, 0.001f)
        // Старт справа, конец левее — жест слева направо НЕ возможен.
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
