package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.TutorialFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * V14: флаги пройденности туториалов живут в SharedPreferences «mpk_tutorial».
 * Ключевое: флаг ПЕРЕЖИВАЕТ обновление приложения (prefs-файл не стирается при установке
 * APK поверх) и не протекает между фичами — иначе после апдейта снова всплыл бы пройденный
 * туториал, а новый не показался бы.
 */
@RunWith(RobolectricTestRunner::class)
class TutorialFlagsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `fresh feature is not done, then round-trips`() {
        assertFalse(TutorialFlags.isDone(ctx, "task_swipe"))
        TutorialFlags.markDone(ctx, "task_swipe")
        assertTrue(TutorialFlags.isDone(ctx, "task_swipe"))
    }

    @Test
    fun `flag survives recreating the prefs object`() {
        TutorialFlags.markDone(ctx, "material_add")
        // «Обновление приложения» = новый процесс читает тот же файл: берём prefs заново.
        val fresh = ctx.getSharedPreferences("mpk_tutorial", Context.MODE_PRIVATE)
        assertTrue(fresh.getBoolean("tutorial_done_material_add", false))
    }

    @Test
    fun `features do not leak into each other`() {
        TutorialFlags.markDone(ctx, "grades_numpad")
        assertTrue(TutorialFlags.isDone(ctx, "grades_numpad"))
        assertFalse("чужой флаг не должен выставляться", TutorialFlags.isDone(ctx, "task_swipe"))
    }
}
