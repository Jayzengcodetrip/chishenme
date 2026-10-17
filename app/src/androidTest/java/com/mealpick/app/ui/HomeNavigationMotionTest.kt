package com.mealpick.app.ui

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mealpick.app.MainActivity
import com.mealpick.app.data.MealDatabase
import com.mealpick.app.data.MealRepository
import com.mealpick.app.data.MealViewModel
import com.mealpick.app.data.scopedSeedId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks frames during navigation, rather than only asserting the settled destination. */
@RunWith(AndroidJUnit4::class)
class HomeNavigationMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var database: MealDatabase
    private lateinit var model: MealViewModel

    @Before
    fun launchInitializedHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = MealDatabase.get(context)
        runBlocking(Dispatchers.IO) {
            database.clearAllTables()
            MealRepository(database, context.assets).initialize()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[MealViewModel::class.java] }
        waitForTag("home_stats")
        compose.waitUntil(10_000) { model.state.value.ready }
        compose.waitForIdle()
    }

    @After
    fun closeActivity() {
        compose.mainClock.autoAdvance = true
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun homeAndWaysStayInPlaceThroughoutForwardAndBackFades() {
        val homeTitle = bounds("home_title")
        val homeFooter = bounds("home_stats")
        compose.mainClock.autoAdvance = false

        // Dinner is already loaded. This isolates layout/animation from the asynchronous
        // meal switch; the separate test below deliberately delays that switch.
        compose.onNodeWithTag("meal_dinner").performClick()
        compose.mainClock.advanceTimeByFrame()
        val incomingWay = bounds("way_takeout")
        repeat(3) { frame ->
            assertSameBounds("outgoing home title, sample $frame", homeTitle, bounds("home_title"))
            assertSameBounds("outgoing home footer, sample $frame", homeFooter, bounds("home_stats"))
            assertSameBounds("incoming way, sample $frame", incomingWay, bounds("way_takeout"))
            if (frame < 2) compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home_title").assertDoesNotExist()
        val settledWay = bounds("way_takeout")
        assertSameBounds("way finishes where it appeared", incomingWay, settledWay)

        compose.onNodeWithTag("back").performClick()
        compose.mainClock.advanceTimeByFrame()
        repeat(3) { frame ->
            assertSameBounds("returning home title, sample $frame", homeTitle, bounds("home_title"))
            assertSameBounds("returning home footer, sample $frame", homeFooter, bounds("home_stats"))
            assertSameBounds("outgoing way, sample $frame", settledWay, bounds("way_takeout"))
            if (frame < 2) compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(500)
        assertSameBounds("settled home title", homeTitle, bounds("home_title"))
        assertSameBounds("settled home footer", homeFooter, bounds("home_stats"))
        compose.onNodeWithTag("way_takeout").assertDoesNotExist()
        compose.onNodeWithTag("back").assertDoesNotExist()
    }

    @Test
    fun delayedMealSnapshotDoesNotInterruptHomeOrWaysAndLoadsOnlyTheSelectedMeal() {
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // A real Room transaction blocks the repository snapshot. No artificial ViewModel
        // state or production test hook is needed to reproduce a slower device's loading.
        runBlocking {
            val blocker = launch(Dispatchers.IO) {
                database.withTransaction {
                    held.complete(Unit)
                    release.await()
                }
            }
            held.await()
            try {
                compose.onNodeWithTag("meal_breakfast").performClick()
                waitForTag("way_takeout")
                compose.onNodeWithText("早餐 · 怎么吃？").assertIsDisplayed()
                compose.onNodeWithTag("way_dining").assertIsDisplayed()
                assertEquals("breakfast", model.state.value.mealId)
                assertFalse("The test must still be exercising loading", model.state.value.ready)

                compose.onNodeWithTag("back").performClick()
                waitForTag("home_stats")
                compose.onNodeWithTag("home_title").assertIsDisplayed()
                compose.onNodeWithTag("meal_breakfast").performClick()
                waitForTag("way_dining")
                compose.onNodeWithTag("way_dining").performClick()
                compose.onNodeWithTag("current_meal").assert(hasText("早餐"))
                // The next page may wait for data, but must never display dinner's rows.
                compose.onNodeWithTag("group_dining-01").assertDoesNotExist()
                assertFalse(model.state.value.ready)
            } finally {
                release.complete(Unit)
            }
            blocker.join()
        }

        waitForTag("group_${scopedSeedId("breakfast", "dining-01")}")
        compose.onNodeWithTag("current_meal").assert(hasText("早餐"))
        compose.onNodeWithTag("group_dining-01").assertDoesNotExist()
        assertEquals("breakfast", model.state.value.mealId)
        compose.onNodeWithTag("back").performClick()
        waitForTag("way_takeout")
        compose.onNodeWithText("早餐 · 怎么吃？").assertIsDisplayed()
    }

    @Test
    fun departedHeaderAndRepeatedBackCallbacksCannotPopTheNewPage() {
        compose.onNodeWithTag("meal_dinner").performClick()
        waitForTag("way_dining")
        val waysBack = clickAction("back")
        compose.onNodeWithTag("way_dining").performClick()
        waitForTag("group_dining-01")

        // The old header can still exist while its page fades out. Replaying its action
        // must not navigate the current dining page, even if queued twice in one frame.
        compose.runOnIdle { waysBack(); waysBack() }
        compose.onNodeWithTag("group_dining-01").assertIsDisplayed()
        compose.onNodeWithTag("way_takeout").assertDoesNotExist()

        val diningBack = clickAction("back")
        compose.runOnIdle { diningBack(); diningBack() }
        waitForTag("way_takeout")
        compose.onNodeWithText("晚餐 · 怎么吃？").assertIsDisplayed()
        compose.onNodeWithTag("home_title").assertDoesNotExist()
        compose.onNodeWithTag("back").performClick()
        waitForTag("home_title")
        compose.onNodeWithTag("back").assertDoesNotExist()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertSameBounds(message: String, expected: Rect, actual: Rect) {
        // Allow pixel rounding, but reject the former toolbar-sized jump or sideways slide.
        assertEquals("$message: left", expected.left, actual.left, 1f)
        assertEquals("$message: top", expected.top, actual.top, 1f)
        assertEquals("$message: right", expected.right, actual.right, 1f)
        assertEquals("$message: bottom", expected.bottom, actual.bottom, 1f)
    }

    private fun clickAction(tag: String): () -> Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
}
