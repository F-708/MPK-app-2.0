package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.TutorialFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TutorialFlagsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `fresh feature is not done, then round-trips`() {
        assertFalse(TutorialFlags.isDone(ctx, "TASK_SWIPE"))
        TutorialFlags.markDone(ctx, "TASK_SWIPE")
        assertTrue(TutorialFlags.isDone(ctx, "TASK_SWIPE"))
    }

    @Test
    fun `flag survives recreating the prefs object`() {
        TutorialFlags.markDone(ctx, "SCHEDULE_TAP")

        val fresh = ctx.getSharedPreferences("mpk_tutorial", Context.MODE_PRIVATE)
        assertTrue(fresh.getBoolean("tutorial_done_SCHEDULE_TAP", false))
    }

    @Test
    fun `features do not leak into each other`() {
        TutorialFlags.markDone(ctx, "SCHEDULE_TAP")
        assertTrue(TutorialFlags.isDone(ctx, "SCHEDULE_TAP"))
        assertFalse("чужой флаг не должен выставляться", TutorialFlags.isDone(ctx, "TASK_SWIPE"))
    }

    @Test
    fun `resetAll clears every done flag`() {

        TutorialFlags.markDone(ctx, "TASK_SWIPE")
        TutorialFlags.markDone(ctx, "SCHEDULE_TAP")
        assertTrue(TutorialFlags.isDone(ctx, "TASK_SWIPE"))
        assertTrue(TutorialFlags.isDone(ctx, "SCHEDULE_TAP"))
        TutorialFlags.resetAll(ctx)
        assertFalse(TutorialFlags.isDone(ctx, "TASK_SWIPE"))
        assertFalse(TutorialFlags.isDone(ctx, "SCHEDULE_TAP"))
    }
}
