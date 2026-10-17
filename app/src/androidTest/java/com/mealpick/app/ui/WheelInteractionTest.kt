package com.mealpick.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mealpick.app.data.FoodGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real pointer input and callback boundaries, without exposing private animation state. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
// 1.4.3: a full spin is 2000ms + 260ms settle + 200ms hub pulse, so "after the spin" waits 2.6s.
class WheelInteractionTest {
    // Keep the animation real even when the instrumentation runner disables system animations.
    @get:Rule val compose = createComposeRule(object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    private val title = "心情口味区"
    private val help = "帮我选"
    private val edit = "编辑转盘"
    private val empty = "添加一个选项，开始转盘"

    @Test
    fun emptyWheelDisablesRandomChoiceButKeepsEditingAvailable() {
        val selected = mutableListOf<String>()
        var edits = 0
        showWheel(emptyList(), onSelect = { selected += it.id }, onEdit = { edits += 1 })

        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.onNodeWithText(help).assertIsNotEnabled()
        compose.onNodeWithContentDescription(title).performTouchInput {
            click(Offset(width * 0.82f, height * 0.5f))
        }
        compose.onNodeWithText(edit).assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(emptyList<String>(), selected)
            assertEquals(1, edits)
        }
    }

    @Test
    fun completedSingleOptionSpinDoesNotSelectNavigateOrShowAResult() {
        val options = groups(1)
        val selected = mutableListOf<String>()
        var edits = 0
        showWheel(options, onSelect = { selected += it.id }, onEdit = { edits += 1 })
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText(help).performClick()
        compose.mainClock.advanceTimeBy(2_600)
        compose.onNodeWithContentDescription(title).assertIsDisplayed()
        compose.onAllNodesWithText("指针结果", substring = true).assertCountEquals(0)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(emptyList<String>(), selected)
            assertEquals(0, edits)
        }

        // Entering still requires a separate, deliberate tap after the spin has finished.
        compose.onNodeWithContentDescription(title).performTouchInput {
            click(Offset(width * 0.5f, height * 0.82f))
        }
        compose.runOnIdle { assertEquals(listOf(options.single().id), selected) }
    }

    @Test
    fun nineSliceWheelAcceptsDirectTapsInTheCorrectVisibleSectorsBeforeSpinning() {
        val options = groups(9)
        val selected = mutableListOf<String>()
        showWheel(options, onSelect = { selected += it.id })

        // A clockwise wheel starting at twelve o'clock puts right/bottom/left in 2/4/6.
        // Coordinates are deliberately independent of the implementation's angle helper.
        compose.onNodeWithContentDescription(title).performTouchInput {
            click(Offset(width * 0.82f, height * 0.5f))
            click(Offset(width * 0.5f, height * 0.82f))
            click(Offset(width * 0.18f, height * 0.5f))
        }

        compose.runOnIdle {
            assertEquals(listOf(options[2].id, options[4].id, options[6].id), selected)
        }
    }

    @Test
    fun tappingSectorOrEditDuringASpinActsImmediatelyWithoutLateNavigation() {
        val options = groups(9)
        val selected = mutableListOf<String>()
        var edits = 0
        showWheel(options, onSelect = { selected += it.id }, onEdit = { edits += 1 })
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText(help).performClick()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithContentDescription(title).performTouchInput {
            click(Offset(width * 0.82f, height * 0.5f))
        }
        compose.runOnIdle {
            assertEquals(listOf(options[2].id), selected)
            assertEquals(0, edits)
        }
        compose.mainClock.advanceTimeBy(2_600)
        compose.runOnIdle { assertEquals(listOf(options[2].id), selected) }

        compose.onNodeWithText(help).performClick()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithText(edit).performClick()
        compose.runOnIdle {
            assertEquals(1, edits)
            assertEquals(listOf(options[2].id), selected)
        }
        compose.mainClock.advanceTimeBy(2_600)
        compose.runOnIdle {
            assertEquals(1, edits)
            assertEquals(listOf(options[2].id), selected)
        }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    private fun showWheel(
        options: List<FoodGroup>,
        onSelect: (FoodGroup) -> Unit,
        onEdit: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                WheelSection(
                    title = title,
                    options = options,
                    helpLabel = help,
                    editLabel = edit,
                    emptyLabel = empty,
                    onEdit = onEdit,
                    onSelect = onSelect,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun groups(count: Int) = List(count) { index ->
        FoodGroup(
            id = "wheel-test-$index",
            scene = "takeaway",
            zone = "mood",
            name = "选项${index + 1}",
            position = index,
        )
    }
}
