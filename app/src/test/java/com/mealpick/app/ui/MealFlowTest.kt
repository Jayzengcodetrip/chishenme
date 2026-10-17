package com.mealpick.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealFlowTest {
    @Test
    fun quoteLibrariesHaveTwentyMatchingLinesWithoutTimeOfDayWords() {
        listOf(Quotes.canteenZh, Quotes.canteenEn, Quotes.familyZh, Quotes.familyEn).forEach { list ->
            assertEquals(20, list.size)
            assertEquals(list.size, list.toSet().size)
        }
        (Quotes.canteenZh + Quotes.familyZh).forEach { line ->
            listOf("上午", "下午", "午休").forEach { word -> assertTrue(line, word !in line) }
        }
    }

    @Test
    fun nextQuoteNeverRepeatsThePreviousOneAndCoversEveryOtherLine() {
        val size = 20
        for (previous in 0 until size) {
            val reached = (0 until size - 1).map { draw -> Quotes.nextIndex(size, previous) { draw } }
            reached.forEach { assertNotEquals(previous, it) }
            assertEquals((0 until size).toSet() - previous, reached.toSet())
        }
        assertEquals(7, Quotes.nextIndex(size, null) { 7 })
        assertEquals(0, Quotes.nextIndex(1, 0) { error("no draw needed") })
    }

    @Test
    fun adjacentWheelSectorsNeverShareAColourIncludingTheWrapAround() {
        for (count in 1..24) {
            val slots = (0 until count).map { WheelMath.sectorColorIndex(it, count) }
            if (count > 1) {
                for (i in 0 until count) {
                    assertNotEquals("count=$count index=$i", slots[i], slots[(i + 1) % count])
                }
            }
            slots.forEach { assertTrue(it in 0..2) }
        }
    }

    @Test
    fun currentMealFollowsTheClockAndCoversEveryHour() {
        assertEquals(Meal.Breakfast, Meal.at(7))
        assertEquals(Meal.Lunch, Meal.at(12))
        assertEquals(Meal.AfternoonTea, Meal.at(15))
        assertEquals(Meal.Dinner, Meal.at(18))
        assertEquals(Meal.LateNight, Meal.at(23))
        assertEquals(Meal.LateNight, Meal.at(1))
        assertEquals(listOf("早餐", "午餐", "晚餐", "下午茶", "宵夜"), Meal.entries.map { UiStrings("zh").mealName(it) })
    }
}
