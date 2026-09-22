package com.politex

import com.politex.ui.components.makePieces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Чистая логика генерации частиц ConfettiOverlay: спавн сверху по всей ширине,
 * падение за нижний край (без схлопывания), детерминированность по сиду.
 */
class ConfettiPhysicsTest {

    @Test
    fun testDeterministicBySeed() {
        val a = makePieces(150, 42L)
        val b = makePieces(150, 42L)
        assertEquals(a, b)
    }

    @Test
    fun testTopRainDominatesAndCoversWidth() {
        val pieces = makePieces(600, 7L)
        val top = pieces.filter { it.y0 < 0f }
        // Основная масса сыплется сверху (85%), малая доля — боковые «пушки».
        assertTrue(top.size in (600 * 0.75).toInt()..(600 * 0.95).toInt())
        // Верхние частицы распределены по всей ширине, а не в двух пятнах.
        val minX = top.minOf { it.x0 }
        val maxX = top.maxOf { it.x0 }
        assertTrue(maxX - minX > 0.9f)
        assertTrue(minX < 0.05f && maxX > 0.95f)
        // Все верхние стартуют над экраном.
        assertTrue(top.all { it.y0 < 0f && it.y0 > -0.15f })
    }

    @Test
    fun testEveryParticleFallsPastBottomEdge() {
        val pieces = makePieces(300, 11L)
        pieces.forEach { p ->
            assertTrue("gravity must accelerate downward", p.gravity > 0f)
            // y(local=1) = y0 + vy + gravity = целевая глубина > 1 высоты экрана:
            // частица гарантированно пролетает нижний край, исчезновение — не сжатием.
            assertTrue("fall depth must exceed screen height", p.y0 + p.vy + p.gravity > 1f)
            assertTrue(p.delay in 0f..0.25f)
        }
    }

    @Test
    fun testSideCannonsStartLowNearCorners() {
        val pieces = makePieces(600, 3L)
        val side = pieces.filter { it.y0 > 1f }
        assertTrue(side.isNotEmpty())
        side.forEach { p ->
            assertTrue("side particles start below bottom", p.y0 > 1f)
            assertTrue("side particles start near a corner", p.x0 < 0.1f || p.x0 > 0.9f)
            assertTrue("side particles fly upward initially", p.vy < 0f)
        }
    }
}
