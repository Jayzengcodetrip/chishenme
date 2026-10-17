package com.mealpick.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class WheelMathTest {
    @Test
    fun emptyWheelHasNoSectorAndDoesNotDrawRandomIndex() {
        var randomCalled = false
        assertEquals(0f, WheelMath.sweepDegrees(0), 0f)
        assertNull(WheelMath.sectorAtAngle(90f, 0))
        assertNull(WheelMath.sectorAtPoint(70f, 50f, 50f, 50f, 50f, 0))
        assertNull(WheelMath.pickIndex(0) {
            randomCalled = true
            0
        })
        assertFalse(randomCalled)
    }

    @Test
    fun singleOptionOccupiesWholeCircle() {
        assertEquals(360f, WheelMath.sweepDegrees(1), 0f)
        listOf(-810f, -90f, 0f, 90f, 270f, 720f).forEach { angle ->
            assertEquals(0, WheelMath.sectorAtAngle(angle, 1))
        }
        assertEquals(0, WheelMath.pickIndex(1) { bound ->
            assertEquals(1, bound)
            0
        })
    }

    @Test
    fun partitionsAreEqualAndCentersMapBackToEveryOption() {
        listOf(1, 9, 37).forEach { count ->
            val sweep = WheelMath.sweepDegrees(count)
            assertEquals(360f, sweep * count, 0.0001f)
            repeat(count) { index ->
                val centerAngle = WheelMath.centerAngleDegrees(index, count)
                assertEquals(index, WheelMath.sectorAtAngle(centerAngle, count))
                val radians = Math.toRadians(centerAngle.toDouble())
                assertEquals(index, WheelMath.sectorAtPoint(
                    x = 100f + cos(radians).toFloat() * 65f,
                    y = 100f + sin(radians).toFloat() * 65f,
                    centerX = 100f,
                    centerY = 100f,
                    radius = 100f,
                    optionCount = count,
                ))
            }
        }
    }

    @Test
    fun angularBoundaryBelongsToClockwiseSector() {
        assertEquals(40f, WheelMath.sweepDegrees(9), 0f)
        assertEquals(0, WheelMath.sectorAtAngle(-90f, 9))
        assertEquals(0, WheelMath.sectorAtAngle(-50.001f, 9))
        assertEquals(1, WheelMath.sectorAtAngle(-50f, 9))
        assertEquals(1, WheelMath.sectorAtAngle(-49.999f, 9))
        assertEquals(8, WheelMath.sectorAtAngle(269.999f, 9))
        assertEquals(0, WheelMath.sectorAtAngle(270f, 9))
        assertEquals(0, WheelMath.sectorAtAngle(630f, 9))
        assertEquals(8, WheelMath.sectorAtAngle(-90.001f, 9))
    }

    @Test
    fun tapsOutsideCircleOrAtUndefinedCenterDoNotSelect() {
        assertNull(WheelMath.sectorAtPoint(201f, 100f, 100f, 100f, 100f, 9))
        assertNull(WheelMath.sectorAtPoint(0f, 0f, 100f, 100f, 100f, 9))
        assertNull(WheelMath.sectorAtPoint(100f, 100f, 100f, 100f, 100f, 9))
        assertEquals(2, WheelMath.sectorAtPoint(200f, 100f, 100f, 100f, 100f, 9))
        assertNull(WheelMath.sectorAtAngle(Float.NaN, 9))
    }

    @Test
    fun targetAlwaysEndsAtChosenSectorCenterAfterFullTurns() {
        listOf(1, 9, 37).forEach { count ->
            listOf(-15f, 0f, 90f, 359f, 1475f).forEach { current ->
                repeat(count) { index ->
                    val target = WheelMath.targetRotationDegrees(current, index, count)
                    assertTrue(target - current >= 4f * 360f)
                    assertTrue(target - current < 5f * 360f)
                    assertEquals(index, WheelMath.sectorAtAngle(target - 90f, count))
                    assertEquals(
                        WheelMath.normalizeDegrees(WheelMath.centerAngleDegrees(index, count) + 90f),
                        WheelMath.normalizeDegrees(target),
                        0.001f,
                    )
                }
            }
        }
    }

    @Test
    fun normalizationWrapsBothDirections() {
        assertEquals(0f, WheelMath.normalizeDegrees(360f), 0f)
        assertEquals(359f, WheelMath.normalizeDegrees(-1f), 0f)
        assertEquals(25f, WheelMath.normalizeDegrees(1105f), 0f)
    }

    @Test
    fun uniformSelectionUsesExactlyOneBoundedIndexDrawWithoutRemapping() {
        listOf(1, 9, 37).forEach { count ->
            repeat(count) { chosen ->
                var calls = 0
                val result = WheelMath.pickIndex(count) { bound ->
                    calls += 1
                    assertEquals(count, bound)
                    chosen
                }
                assertEquals(1, calls)
                assertEquals(chosen, result)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun targetRejectsIndexOutsidePartition() {
        WheelMath.targetRotationDegrees(0f, 9, 9)
    }
}
