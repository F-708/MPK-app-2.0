package com.politex.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Генераторы сезонного декора: количество, диапазоны, детерминизм. */
class SeasonParticlesTest {

    @Test
    fun `тыквы в заданном количестве и на полу`() {
        val pumpkins = AmbientParticles.pumpkins()
        assertTrue("тыкв ${pumpkins.size}", pumpkins.size in AmbientSpec.PUMPKIN_COUNT)
        pumpkins.forEach { p ->
            assertTrue("x в 0..1", p.x in 0f..1f)
            assertTrue("у нижней кромки", p.y >= 0.80f)
            assertTrue("радиус в диапазоне", p.radiusDp in AmbientSpec.PUMPKIN_RADIUS_DP)
            assertTrue("мерцание положительное", p.flickerSec > 0f)
        }
    }

    @Test
    fun `тыквы детерминированы`() {
        assertEquals(AmbientParticles.pumpkins(), AmbientParticles.pumpkins())
    }

    @Test
    fun `гирлянда из фиксированного числа огней вдоль верха`() {
        val g = AmbientParticles.garland()
        assertEquals(AmbientSpec.GARLAND_COUNT, g.size)
        g.forEach { light ->
            assertTrue("x в 0..1", light.x in 0f..1f)
            assertTrue("у верхней кромки", light.y <= 0.05f)
            assertTrue("цвет в палитре", light.colorIndex in 0..3)
        }
        // Равномерная сетка слева направо.
        assertEquals(0f, g.first().x, 1e-6f)
        assertEquals(1f, g.last().x, 1e-6f)
    }

    @Test
    fun `гирлянда детерминирована`() {
        assertEquals(AmbientParticles.garland(), AmbientParticles.garland())
    }
}
