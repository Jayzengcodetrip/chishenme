package com.mealpick.app.ui

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mealpick.app.MainActivity
import com.mealpick.app.data.MealDatabase
import com.mealpick.app.data.MealRepository
import com.mealpick.app.data.scopedSeedId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Activity, ViewModel, Room store and Compose screens together. */
@RunWith(AndroidJUnit4::class)
class MealUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var repository: MealRepository
    private lateinit var lateNightRepository: MealRepository

    @Before
    fun seedBeforeLaunchingActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = MealDatabase.get(context)
        repository = MealRepository(database, context.assets)
        lateNightRepository = MealRepository(database, context.assets, mealId = "late_night")
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
        // MealDatabase is an application singleton shared with the next Activity.
        // The next test resets its tables before launching, without closing that singleton.
    }

    @Test
    fun snackPickManualCorrectionStatisticsAndActivityRecreationStayConsistent() {
        val id = scopedSeedId("late_night", "snacks-01-item-01")
        openLateNightTakeout()
        waitForTag("pick_$id")
        compose.onNodeWithTag("pick_$id").performClick()
        waitForRowText("item_$id", "1次")

        compose.onNodeWithTag("more_$id").performClick()
        compose.onNodeWithText("修改次数").performClick()
        compose.onNodeWithTag("dialog_count").performTextReplacement("4")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForRowText("item_$id", "4次")

        compose.onNodeWithTag("back").performClick()
        waitForTag("way_takeout")
        compose.onNodeWithTag("back").performClick()
        waitForTag("home_stats")
        compose.onNodeWithTag("home_stats").performScrollTo().performClick()
        waitForTag("stats_list")
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stat_total_snacks"))
        compose.onNodeWithTag("stat_total_snacks").assert(hasAnyDescendant(hasText("4次")))
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stat_item_$id"))
        compose.onNodeWithTag("stat_item_$id")
            .assert(hasAnyDescendant(hasText("麻辣烫")))
            .assert(hasAnyDescendant(hasText("4次")))

        scenario.recreate()
        waitForTag("home_stats")
        openLateNightTakeout()
        waitForRowText("item_$id", "4次")
        compose.onNodeWithTag("item_name_$id").assert(hasAnyDescendant(hasText("麻辣烫")))
        val saved = runBlocking { lateNightRepository.snapshot() }
        assertEquals(4L, saved.items.single { it.id == id }.count)
        val snackIds = saved.groups.filter { it.scene == "snacks" }.map { it.id }.toSet()
        assertEquals(4L, saved.items.filter { it.groupId in snackIds }.sumOf { it.count })
    }

    @Test
    fun diningNameOpensOriginalNotesCancelDiscardsAndSavePersists() {
        val id = "dining-01-item-02"
        val original = "（三花趾、吊龙伴、匙肉、牛肉丸）"
        openDining()
        compose.onNodeWithTag("group_dining-01").performClick()
        waitForTag("item_name_$id")
        compose.onNodeWithText(original).assertIsDisplayed()
        compose.onNodeWithTag("item_name_$id").performClick()
        compose.onNodeWithText("餐馆备注").assertIsDisplayed()
        compose.onNodeWithTag("dialog_notes").assert(hasText(original))
        compose.onNodeWithTag("dialog_notes").performTextReplacement("取消后不应该保存的备注")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("dialog_notes").assertDoesNotExist()
        compose.onNodeWithText(original).assertIsDisplayed()

        compose.onNodeWithTag("item_name_$id").performClick()
        compose.onNodeWithTag("dialog_notes").assert(hasText(original))
        val edited = "三花趾、吊龙伴；下次尝试 beef balls"
        compose.onNodeWithTag("dialog_notes").performTextReplacement(edited)
        compose.onNodeWithTag("dialog_save").performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository.snapshot().items.single { it.id == id }.notes == edited }
        }
        compose.onNodeWithText(edited).assertIsDisplayed()
        compose.onNodeWithTag("item_name_$id").performClick()
        compose.onNodeWithTag("dialog_notes").assert(hasText(edited))
        compose.onNodeWithText("取消").performClick()
        waitForRowText("item_$id", "0次")
        assertEquals(0L, runBlocking { repository.snapshot().items.single { it.id == id }.count })
    }

    @Test
    fun ordinaryRestaurantListShowsNotesAndEditsThemWithoutChangingCounts() {
        val id = "dining-02-item-01"
        val original = "少油，米饭单独装\n第二行也直接显示"
        runBlocking { repository.updateItem(id, "阿何快餐", original) }
        openDining()
        compose.onNodeWithTag("group_dining-02").performClick()
        waitForTag("item_name_$id")
        compose.onNodeWithTag("item_notes_$id", useUnmergedTree = true).assert(hasText(original)).assertIsDisplayed()
        compose.onNodeWithTag("item_notes_$id", useUnmergedTree = true).performClick()
        compose.onNodeWithText("餐馆备注").assertIsDisplayed()
        compose.onNodeWithTag("dialog_notes").performTextReplacement("保存后直接可见")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForRowText("item_$id", "保存后直接可见")
        assertEquals(0L, runBlocking { repository.snapshot().items.single { it.id == id }.count })
    }

    @Test
    fun languageMenuTranslatesControlsPreservesUserDataAndRemembersSelection() {
        val originals = runBlocking { repository.snapshot().items }
        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_en").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Late-night Snack")).fetchSemanticsNodes().isNotEmpty()
        }
        openLateNightTakeout()
        compose.onNodeWithText("Late-night Snack · Takeout").assertIsDisplayed()
        compose.onNodeWithText("Food").assertIsDisplayed()
        compose.onNodeWithText("麻辣烫").assertIsDisplayed()
        compose.onNodeWithTag("more_${scopedSeedId("late_night", "snacks-01-item-01")}").performClick()
        compose.onNodeWithText("Edit count").assertIsDisplayed()
        compose.onNodeWithText("Edit count").performClick()
        compose.onNodeWithTag("dialog_count").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("back").performClick()
        waitForTag("way_takeout")
        compose.onNodeWithTag("back").performClick()
        waitForTag("home_stats")

        openDining()
        compose.onNodeWithText("全国连锁").assertIsDisplayed()
        compose.onNodeWithTag("group_dining-01").performClick()
        compose.onNodeWithTag("item_name_dining-01-item-02").performClick()
        compose.onNodeWithTag("dialog_notes").assert(hasText("（三花趾、吊龙伴、匙肉、牛肉丸）"))
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        scenario.recreate()
        waitForTag("home_stats")
        compose.onNodeWithText("Late-night Snack").assertIsDisplayed()
        assertEquals("en", runBlocking { repository.snapshot().language })
        assertEquals(originals, runBlocking { repository.snapshot().items })

        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_zh").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("宵夜")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("宵夜").assertIsDisplayed()
        assertEquals("zh", runBlocking { repository.snapshot().language })
        assertEquals(originals, runBlocking { repository.snapshot().items })
    }

    @Test
    fun draggingAcrossTallRowPersistsWithoutMovingCounts() {
        val first = scopedSeedId("late_night", "snacks-01-item-01")
        val second = scopedSeedId("late_night", "snacks-01-item-02")
        runBlocking {
            lateNightRepository.setCount(first, 3)
            lateNightRepository.updateItem(second, "水果", "第一行备注\n第二行备注\n第三行备注\n第四行备注")
        }
        openLateNightTakeout()
        waitForTag("drag_$first")
        compose.waitForIdle()
        val firstBounds = compose.onNodeWithTag("item_$first").fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithTag("item_$second").fetchSemanticsNode().boundsInRoot
        val displacement = secondBounds.center.y - firstBounds.center.y
        compose.onNodeWithTag("drag_$first").performTouchInput {
            down(center)
            advanceEventTime(700)
            for (step in 1..20) {
                moveTo(center + Offset(0f, displacement * step / 20f), delayMillis = 32)
            }
            up()
        }
        compose.waitUntil(5_000) {
            runBlocking { lateNightRepository.snapshot().items.filter { it.groupId == scopedSeedId("late_night", "snacks-01") }.sortedBy { it.position }.first().id == second }
        }
        scenario.recreate()
        waitForTag("home_stats")
        openLateNightTakeout()
        waitForRowText("item_$first", "3次")
        val upper = compose.onNodeWithTag("item_$second").fetchSemanticsNode().boundsInRoot.top
        val lower = compose.onNodeWithTag("item_$first").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("Dropped order survives Activity recreation", upper < lower)
    }

    @Test
    fun mealAndWayChoicesReachTheRightScreensAndQuotesChange() {
        compose.onNodeWithTag("home_title").assert(hasText("每日五餐，你要哪餐"))
        compose.onNodeWithTag("meal_lunch").performScrollTo().performClick()
        waitForTag("way_takeout")
        compose.onNodeWithText("午餐 · 怎么吃？").assertIsDisplayed()
        compose.onNodeWithTag("way_takeout").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("心情口味区")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("food_list").assertDoesNotExist()
        compose.onNodeWithTag("back").performClick()

        waitForTag("way_canteen")
        compose.onNodeWithTag("way_canteen").performScrollTo().performClick()
        waitForTag("quote_text")
        val first = quoteText()
        assertTrue(first in Quotes.canteenZh)
        compose.onNodeWithTag("quote_next").performClick()
        compose.waitUntil(5_000) { quoteText() != first }
        assertTrue(quoteText() in Quotes.canteenZh)
        compose.onNodeWithTag("back").performClick()

        waitForTag("way_family")
        compose.onNodeWithTag("way_family").performScrollTo().performClick()
        waitForTag("quote_text")
        assertTrue(quoteText() in Quotes.familyZh)
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("back").performClick()

        openLateNightTakeout()
        compose.onNodeWithText("宵夜 · 点外卖").assertIsDisplayed()
        compose.onNodeWithText("吃的").assertIsDisplayed()
    }

    private fun quoteText(): String {
        compose.waitForIdle()
        val nodes = compose.onAllNodes(hasTestTag("quote_text")).fetchSemanticsNodes()
        return nodes.last().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
    }

    private fun openLateNightTakeout() {
        compose.onNodeWithTag("meal_late_night").performScrollTo().performClick()
        waitForTag("way_takeout")
        compose.onNodeWithTag("way_takeout").performScrollTo().performClick()
        waitForTag("food_list")
    }

    private fun openDining() {
        compose.onNodeWithTag("meal_dinner").performScrollTo().performClick()
        waitForTag("way_dining")
        compose.onNodeWithTag("way_dining").performScrollTo().performClick()
        waitForTag("group_dining-01")
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForRowText(tag: String, text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasAnyDescendant(hasText(text)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
