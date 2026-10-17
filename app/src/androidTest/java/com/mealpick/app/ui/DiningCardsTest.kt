package com.mealpick.app.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mealpick.app.MainActivity
import com.mealpick.app.data.FoodItem
import com.mealpick.app.data.MealDatabase
import com.mealpick.app.data.MealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the card experience through the real Activity and persistent Room repository. */
@RunWith(AndroidJUnit4::class)
class DiningCardsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var repository: MealRepository

    @Before
    fun launchWithFreshSeed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = MealDatabase.get(context)
        repository = MealRepository(database, context.assets)
        runBlocking(Dispatchers.IO) {
            database.clearAllTables()
            repository.initialize()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("home_stats")
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun eightCardsRemainAvailableAndDrawingDoesNotChangeDataUntilPick() {
        val original = savedRestaurants()
        assertEquals(8, original.size)
        openNationalChains()
        original.forEach { restaurant ->
            compose.onNodeWithTag("dining_card_grid")
                .performScrollToNode(hasTestTag("item_${restaurant.id}"))
            compose.onNodeWithTag("item_name_${restaurant.id}")
                .assert(hasText(restaurant.name))
        }

        drawAndWait()
        original.indices.forEach { index ->
            compose.onNodeWithTag("dining_hidden_grid")
                .performScrollToNode(hasTestTag("hidden_card_$index"))
            compose.onNodeWithTag("hidden_card_$index").assertIsDisplayed()
        }
        compose.onNodeWithTag("dining_hidden_grid").performScrollToIndex(0)
        compose.onNodeWithTag("hidden_card_0").performClick()
        waitForTag("dining_reveal")
        val chosen = revealedRestaurant(original)
        assertEquals("Browsing, shuffling and revealing must not modify records", original, savedRestaurants())

        compose.onNodeWithTag("pick_${chosen.id}").performClick()
        waitForSaved { savedRestaurants().single { it.id == chosen.id }.count == 1L }
        val expected = original.map { if (it.id == chosen.id) it.copy(count = 1L) else it }
        assertEquals(expected, savedRestaurants())

        // The same editable note dialog must work on the enlarged result card.
        compose.onNodeWithTag("item_name_${chosen.id}").performClick()
        compose.onNodeWithTag("dialog_notes").assert(hasText(chosen.notes))
        compose.onNodeWithTag("dialog_notes").performTextReplacement("不保存这条备注")
        compose.onNodeWithText("取消").performClick()
        assertEquals(chosen.notes, savedRestaurants().single { it.id == chosen.id }.notes)
        compose.onNodeWithTag("item_name_${chosen.id}").performClick()
        compose.onNodeWithTag("dialog_notes").performTextReplacement("下次点这道菜 · keep my notes")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForSaved {
            savedRestaurants().single { it.id == chosen.id }.notes == "下次点这道菜 · keep my notes"
        }
        compose.onNodeWithTag("item_notes_${chosen.id}")
            .assert(hasText("下次点这道菜 · keep my notes")).assertIsDisplayed()
        assertEquals(1L, savedRestaurants().sumOf { it.count })
    }

    @Test
    fun notesAreVisibleAndTappableOnCardsAndCancelKeepsTheOriginalText() {
        val id = "dining-01-item-02"
        val original = savedRestaurants().single { it.id == id }.notes
        openNationalChains()
        compose.onNodeWithTag("item_notes_dining-01-item-01").assertDoesNotExist()
        compose.onNodeWithTag("item_notes_$id").assert(hasText(original)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("dialog_notes").assert(hasText(original))
        compose.onNodeWithTag("dialog_notes").performTextReplacement("这条不保存")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("item_notes_$id").assert(hasText(original)).assertIsDisplayed()

        compose.onNodeWithTag("item_notes_$id").performClick()
        val updated = "三花趾、吊龙伴\n牛肉丸 · keep this wording"
        compose.onNodeWithTag("dialog_notes").performTextReplacement(updated)
        compose.onNodeWithTag("dialog_save").performClick()
        waitForSaved { savedRestaurants().single { it.id == id }.notes == updated }
        compose.onNodeWithTag("item_notes_$id").assert(hasText(updated)).assertIsDisplayed()
        assertEquals(0L, savedRestaurants().single { it.id == id }.count)
    }

    @Test
    fun longNotesRemainCompleteAndPickAndDrawActionsStayReachable() {
        val longNotes = (1..32).joinToString("\n") { "第 $it 行备注 · 原文保留" } + "\n最后一行也完整显示"
        runBlocking {
            savedRestaurants().forEach { repository.deleteItem(it.id) }
            repository.addItem(GROUP_ID, "长备注餐馆", longNotes)
        }
        val only = savedRestaurants().single()
        openNationalChains()
        assertNotesAreFullyLaidOut(longNotes)
        compose.onNodeWithTag("pick_${only.id}").performScrollTo().assertIsDisplayed().performClick()
        waitForSaved { savedRestaurants().single().count == 1L }

        drawAndWait()
        compose.onNodeWithTag("hidden_card_0").performClick()
        waitForTag("dining_reveal")
        assertNotesAreFullyLaidOut(longNotes)
        compose.onNodeWithTag("pick_${only.id}").performScrollTo().assertIsDisplayed().performClick()
        waitForSaved { savedRestaurants().single().count == 2L }
        compose.onNodeWithTag("draw_again").assertIsDisplayed()
        compose.onNodeWithTag("show_all").assertIsDisplayed()
        compose.onNodeWithTag("draw_again").performClick()
        waitForTag("dining_hidden_grid")
        compose.onNodeWithTag("show_all").performClick()
        waitForTag("dining_card_grid")
        assertEquals(longNotes, savedRestaurants().single().notes)
        assertEquals(2L, savedRestaurants().single().count)
    }

    @Test
    fun drawAgainViewAllAndBothBackActionsReturnToBrowsing() {
        val original = savedRestaurants()
        openNationalChains()
        drawAndWait()
        compose.onNodeWithTag("hidden_card_0").performClick()
        waitForTag("dining_reveal")
        compose.onNodeWithTag("draw_again").performClick()
        waitForTag("dining_hidden_grid")
        compose.onNodeWithTag("back").performClick()
        waitForTag("dining_card_grid")
        compose.onNodeWithTag("dining_hidden_grid").assertDoesNotExist()

        drawAndWait()
        compose.onNodeWithTag("hidden_card_0").performClick()
        waitForTag("dining_reveal")
        compose.onNodeWithTag("show_all").performClick()
        waitForTag("dining_card_grid")

        drawAndWait()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag("dining_card_grid")
        assertEquals(original, savedRestaurants())
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag("group_dining-01")
        compose.onNodeWithTag("dining_card_grid").assertDoesNotExist()
    }

    @Test
    fun returningDuringShuffleAndFlipCancelsPendingAnimationCompletion() {
        val original = savedRestaurants()
        openNationalChains()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("draw_restaurant").performClick()
            compose.mainClock.advanceTimeByFrame()
            waitForTag("dining_shuffling")
            compose.onNodeWithTag("back").performClick()
            compose.mainClock.advanceTimeByFrame()
            waitForTag("dining_card_grid")
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithTag("dining_card_grid").assertIsDisplayed()
            compose.onNodeWithTag("dining_hidden_grid").assertDoesNotExist()

            compose.onNodeWithTag("draw_restaurant").performClick()
            compose.mainClock.advanceTimeByFrame()
            waitForTag("dining_shuffling")
            compose.mainClock.advanceTimeBy(2_000)
            waitForTag("dining_hidden_grid")
            compose.onNodeWithTag("hidden_card_0").performClick()
            compose.mainClock.advanceTimeByFrame()
            waitForTag("dining_flipping")
            // Exercise Android's back dispatcher while the reveal animation is paused.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeByFrame()
            waitForTag("dining_card_grid")
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithTag("dining_card_grid").assertIsDisplayed()
            compose.onNodeWithTag("dining_reveal").assertDoesNotExist()
            compose.onNodeWithTag("dining_hidden_grid").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertEquals(original, savedRestaurants())
    }

    @Test
    fun recreationDiscardsDrawSessionButPreservesSavedPickAndOrder() {
        val initial = savedRestaurants()
        val chosenId = initial.first().id
        openNationalChains()
        compose.onNodeWithTag("pick_$chosenId").performClick()
        waitForSaved { savedRestaurants().single { it.id == chosenId }.count == 1L }
        val saved = savedRestaurants()
        drawAndWait()
        compose.onNodeWithTag("hidden_card_0").performClick()
        waitForTag("dining_reveal")

        scenario.recreate()
        waitForTag("home_stats")
        openNationalChains()
        compose.onNodeWithTag("dining_reveal").assertDoesNotExist()
        compose.onNodeWithTag("dining_hidden_grid").assertDoesNotExist()
        assertEquals(saved, savedRestaurants())
    }

    @Test
    fun cardMenuCountCorrectionAndDeletionUpdateDiningStatistics() {
        val first = "dining-01-item-01"
        val second = "dining-01-item-02"
        openNationalChains()
        compose.onNodeWithTag("pick_$second").performClick()
        waitForSaved { savedRestaurants().single { it.id == second }.count == 1L }
        compose.onNodeWithTag("more_$first").performClick()
        compose.onNodeWithText("修改次数").performClick()
        compose.onNodeWithTag("dialog_count").performTextReplacement("4")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForSaved { savedRestaurants().sumOf { it.count } == 5L }
        compose.onNodeWithTag("more_$first").performClick()
        compose.onNodeWithText("删除").performClick()
        waitForSaved { savedRestaurants().none { it.id == first } }
        assertEquals(7, savedRestaurants().size)
        assertEquals(1L, savedRestaurants().sumOf { it.count })

        compose.onNodeWithTag("back").performClick()
        waitForTag("group_$GROUP_ID")
        compose.onNodeWithTag("back").performClick()
        waitForTag("way_dining")
        compose.onNodeWithTag("back").performClick()
        waitForTag("home_stats")
        compose.onNodeWithTag("home_stats").performScrollTo().performClick()
        waitForTag("stats_list")
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stat_total_dining"))
        compose.onNodeWithTag("stat_total_dining").assert(hasAnyDescendant(hasText("1次")))
    }

    @Test
    fun stableCategoryIdentityAndEnglishControlsPreserveUserText() {
        val original = savedRestaurants()
        runBlocking { repository.renameGroup(GROUP_ID, "我的全国餐馆 · Restaurants") }
        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_en").performClick()
        waitForSaved { runBlocking { repository.snapshot().language == "en" } }
        openNationalChains()
        compose.onNodeWithText("我的全国餐馆 · Restaurants").assertIsDisplayed()
        compose.onNodeWithTag("draw_restaurant").assert(hasText("Draw a restaurant"))
        compose.onNodeWithTag("item_name_dining-01-item-02")
            .assert(hasText("八合里"))
        compose.onNodeWithTag("item_name_dining-01-item-02").performClick()
        compose.onNodeWithTag("dialog_notes").assert(hasText("（三花趾、吊龙伴、匙肉、牛肉丸）"))
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        drawAndWait()
        compose.onNodeWithTag("hidden_card_0").performClick()
        waitForTag("dining_reveal")
        val chosen = revealedRestaurant(original)
        compose.onNodeWithTag("item_name_${chosen.id}")
            .assert(hasText(chosen.name))
        compose.onNodeWithTag("draw_again").assert(hasText("Draw again"))
        compose.onNodeWithTag("show_all").assert(hasText("View all"))
        compose.onNodeWithTag("pick_${chosen.id}").assert(hasText("Pick"))
        assertEquals(original, savedRestaurants())
    }

    @Test
    fun emptyCollectionDisablesDrawAndOneRestaurantCanStillBeRevealed() {
        runBlocking { savedRestaurants().forEach { repository.deleteItem(it.id) } }
        openNationalChains()
        compose.onNodeWithTag("draw_restaurant").assertIsNotEnabled()

        runBlocking { repository.addItem(GROUP_ID, "唯一的一家餐馆", "保留原文") }
        val only = savedRestaurants().single()
        waitForTag("item_${only.id}")
        drawAndWait()
        compose.onNodeWithTag("hidden_card_0").assertIsDisplayed()
        compose.onNodeWithTag("hidden_card_1").assertDoesNotExist()
        compose.onNodeWithTag("hidden_card_0").performClick()
        waitForTag("dining_reveal")
        compose.onNodeWithTag("item_name_${only.id}")
            .assert(hasText("唯一的一家餐馆"))
        assertEquals(listOf(only), savedRestaurants())
    }

    @Test
    fun draggingAcrossColumnsAndRowsPersistsOrderWithoutChangingCounts() {
        val first = "dining-01-item-01"
        val second = "dining-01-item-02"
        val fourth = "dining-01-item-04"
        runBlocking { repository.setCount(first, 3) }
        openNationalChains()
        val firstBounds = compose.onNodeWithTag("item_$first").fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithTag("item_$second").fetchSemanticsNode().boundsInRoot
        assertTrue("The normal phone layout should present two columns", firstBounds.center.x < secondBounds.center.x)
        dragCard(first, second)
        waitForSaved { savedRestaurants().first().id == second }
        assertEquals(1, savedRestaurants().indexOfFirst { it.id == first })

        dragCard(first, fourth)
        waitForSaved { savedRestaurants().indexOfFirst { it.id == first } == 3 }
        val saved = savedRestaurants()
        assertEquals(3L, saved.single { it.id == first }.count)
        assertEquals(3L, saved.sumOf { it.count })

        scenario.recreate()
        waitForTag("home_stats")
        openNationalChains()
        assertEquals(saved, savedRestaurants())
        val upper = compose.onNodeWithTag("item_$second").fetchSemanticsNode().boundsInRoot.top
        val lower = compose.onNodeWithTag("item_$first").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The dropped row survives recreation", upper < lower)
    }

    private fun openNationalChains() {
        compose.onNodeWithTag("meal_dinner").performScrollTo().performClick()
        waitForTag("way_dining")
        compose.onNodeWithTag("way_dining").performScrollTo().performClick()
        waitForTag("group_$GROUP_ID")
        compose.onNodeWithTag("group_$GROUP_ID").performClick()
        waitForTag("dining_card_grid")
    }

    private fun drawAndWait() {
        compose.onNodeWithTag("draw_restaurant").performClick()
        waitForTag("dining_hidden_grid")
    }

    private fun revealedRestaurant(candidates: List<FoodItem>): FoodItem = candidates.single {
        compose.onAllNodesWithTag("pick_${it.id}").fetchSemanticsNodes().isNotEmpty()
    }

    private fun savedRestaurants(): List<FoodItem> = runBlocking {
        repository.snapshot().items.filter { it.groupId == GROUP_ID }.sortedBy { it.position }
    }

    private fun dragCard(fromId: String, toId: String) {
        compose.waitForIdle()
        val from = compose.onNodeWithTag("item_$fromId").fetchSemanticsNode().boundsInRoot
        val to = compose.onNodeWithTag("item_$toId").fetchSemanticsNode().boundsInRoot
        val displacement = to.center - from.center
        compose.onNodeWithTag("drag_$fromId").performTouchInput {
            down(center)
            advanceEventTime(700)
            for (step in 1..24) {
                moveTo(center + Offset(displacement.x * step / 24f, displacement.y * step / 24f), delayMillis = 32)
            }
            up()
        }
    }

    private fun assertNotesAreFullyLaidOut(notes: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(notes, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("The visible note must retain the original wording", notes, layout.layoutInput.text.text)
        compose.runOnIdle { assertFullTextFits("Restaurant notes", layout) }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForSaved(condition: () -> Boolean) {
        compose.waitUntil(5_000, condition)
    }

    private companion object {
        const val GROUP_ID = "dining-01"
    }
}
