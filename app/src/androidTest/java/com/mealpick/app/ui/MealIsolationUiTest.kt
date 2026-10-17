package com.mealpick.app.ui

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual home routes, editing dialogs, meal-specific statistics and cooking navigation. */
@RunWith(AndroidJUnit4::class)
class MealIsolationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var context: Context
    private lateinit var database: MealDatabase

    @Before
    fun initializeAllMeals() {
        context = ApplicationProvider.getApplicationContext()
        database = MealDatabase.get(context)
        runBlocking(Dispatchers.IO) {
            database.clearAllTables()
            repository("dinner").initialize()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("home_stats")
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun breakfastWheelEditsDoNotChangeLunchAndSurviveReturningAndRecreation() {
        val breakfastId = scopedSeedId("breakfast", "nutrition-01")
        val lunchId = scopedSeedId("lunch", "nutrition-01")
        openNutritionEditor("breakfast")
        compose.onNodeWithTag("group_$breakfastId").performClick()
        compose.onNodeWithTag("dialog_name").performTextReplacement("早餐专用赛百味")
        compose.onNodeWithTag("dialog_save").performClick()
        // GroupList's clickable Row merges its label onto the tagged node itself.
        // Check both the persisted value and that merged, user-visible label.
        compose.waitUntil(5_000) {
            runBlocking { repository("breakfast").snapshot().groups.single { it.id == breakfastId }.name == "早餐专用赛百味" }
        }
        waitForTaggedText("group_$breakfastId", "早餐专用赛百味")
        compose.onNodeWithTag("add_group").performClick()
        compose.onNodeWithTag("dialog_name").performTextReplacement("只在早餐的新店")
        compose.onNodeWithTag("dialog_save").performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository("breakfast").snapshot().groups.any { it.name == "只在早餐的新店" } }
        }
        returnHome()

        openNutritionEditor("lunch")
        compose.onNodeWithTag("current_meal").assert(hasText("午餐"))
        compose.onNodeWithTag("group_$lunchId").assert(hasText("赛百味"))
        val lunch = runBlocking { repository("lunch").snapshot() }
        assertEquals(5, lunch.groups.count { it.zone == "nutrition" })
        assertTrue(lunch.groups.none { it.name == "早餐专用赛百味" || it.name == "只在早餐的新店" })
        returnHome()

        openNutritionEditor("breakfast")
        compose.onNodeWithTag("current_meal").assert(hasText("早餐"))
        compose.onNodeWithTag("group_$breakfastId").assert(hasText("早餐专用赛百味"))
        scenario.recreate()
        waitForTag("home_stats")
        openNutritionEditor("breakfast")
        compose.onNodeWithTag("group_$breakfastId").assert(hasText("早餐专用赛百味"))
    }

    @Test
    fun nationalCardsPickAndStatisticsAreIndependentAcrossMealsAndReturnPaths() {
        val breakfastId = scopedSeedId("breakfast", "dining-01-item-01")
        val lunchId = scopedSeedId("lunch", "dining-01-item-01")
        openNationalCards("breakfast")
        compose.onNodeWithTag("pick_$breakfastId").performClick()
        waitForRowText("item_$breakfastId", "1次")
        compose.onNodeWithTag("pick_$breakfastId").performClick()
        waitForRowText("item_$breakfastId", "2次")
        returnHome()

        openNationalCards("lunch")
        compose.onNodeWithTag("current_meal").assert(hasText("午餐"))
        waitForRowText("item_$lunchId", "0次")
        compose.onNodeWithTag("pick_$lunchId").performClick()
        waitForRowText("item_$lunchId", "1次")
        returnHome()
        compose.onNodeWithTag("home_stats").performScrollTo().performClick()
        waitForTag("stats_list")
        assertDiningTotal("lunch", "1次")
        listOf("breakfast" to "2次", "dinner" to "0次", "afternoon_tea" to "0次", "late_night" to "0次").forEach { (meal, count) ->
            compose.onNodeWithTag("stats_meal_$meal").performClick()
            assertDiningTotal(meal, count)
        }
        compose.onNodeWithTag("stats_meal_breakfast").performClick()
        assertDiningTotal("breakfast", "2次")
        returnHome()
        openNationalCards("breakfast")
        waitForRowText("item_$breakfastId", "2次")
        compose.onNodeWithTag("dining_card_grid").assertIsDisplayed()
        compose.onNodeWithTag("dialog_notes").assertDoesNotExist()
    }

    @Test
    fun cookingExpansionShoppingAndMenuStateDoNotLeakToAnotherMeal() {
        val breakfast = runBlocking { repository("breakfast").snapshot() }
        val lunch = runBlocking { repository("lunch").snapshot() }
        val breakfastGroup = breakfast.groups.single { it.scene == "cooking" && it.name == "主食" }.id
        val lunchGroup = lunch.groups.single { it.scene == "cooking" && it.name == "主食" }.id
        val breakfastPotato = breakfast.items.single { it.groupId == breakfastGroup && it.name == "红薯" }.id
        val lunchPotato = lunch.items.single { it.groupId == lunchGroup && it.name == "红薯" }.id
        openWay("breakfast", "cooking")
        waitForTag("cooking_list")
        compose.onNodeWithTag("cooking_group_$breakfastGroup").performScrollTo().performClick()
        compose.onNodeWithTag("cart_$breakfastPotato").performScrollTo().performClick()
        compose.onNodeWithTag("menu_add_$breakfastPotato").performScrollTo().performClick()
        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        compose.onNodeWithTag("purchase_check_$breakfastPotato").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository("breakfast").snapshot().let { it.cart.singleOrNull()?.purchased == true && it.menu.size == 1 } }
        }
        returnHome()

        openWay("lunch", "cooking")
        waitForTag("cooking_list")
        compose.onNodeWithTag("cart_$lunchPotato").assertDoesNotExist()
        compose.onNodeWithTag("cooking_group_$lunchGroup").performScrollTo().performClick()
        compose.onNodeWithTag("cart_$lunchPotato").performScrollTo().assert(hasText("加入购物车"))
        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        compose.onNodeWithTag("transfer_all").assertIsNotEnabled()
        compose.onNodeWithTag("open_cooking_menu").performClick()
        waitForTag("cooking_menu_list")
        compose.onNodeWithTag("complete_meal").assertDoesNotExist()
        val untouched = runBlocking { repository("lunch").snapshot() }
        assertTrue(untouched.cart.isEmpty() && untouched.menu.isEmpty())
        returnHome()

        openWay("breakfast", "cooking")
        waitForTag("cooking_list")
        // Breakfast retains its own expanded section; lunch expansion used a separate key.
        compose.onNodeWithTag("cart_$breakfastPotato").performScrollTo().assert(hasText("已加入购物车"))
        compose.onNodeWithTag("menu_add_$breakfastPotato").performScrollTo().assert(hasText("已加入菜单"))
        assertTrue(runBlocking { repository("breakfast").snapshot().cart.single().purchased })
    }

    @Test
    fun cancellingIngredientButtonsOnlyChangesTheirOwnMealAndList() {
        fun ingredient(meal: String) = runBlocking {
            val saved = repository(meal).snapshot()
            val group = saved.groups.single { it.scene == "cooking" && it.name == "主食" }
            group.id to saved.items.single { it.groupId == group.id && it.name == "红薯" }.id
        }
        val (breakfastGroup, breakfastPotato) = ingredient("breakfast")
        val (lunchGroup, lunchPotato) = ingredient("lunch")
        openWay("breakfast", "cooking")
        waitForTag("cooking_list")
        compose.onNodeWithTag("cooking_group_$breakfastGroup").performScrollTo().performClick()
        compose.onNodeWithTag("cart_$breakfastPotato").performScrollTo().performClick()
        compose.onNodeWithTag("menu_add_$breakfastPotato").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository("breakfast").snapshot().let { it.cart.size == 1 && it.menu.size == 1 } }
        }
        returnHome()

        openWay("lunch", "cooking")
        waitForTag("cooking_list")
        compose.onNodeWithTag("cooking_group_$lunchGroup").performScrollTo().performClick()
        compose.onNodeWithTag("cart_$lunchPotato").performScrollTo().performClick()
        compose.onNodeWithTag("menu_add_$lunchPotato").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository("lunch").snapshot().let { it.cart.size == 1 && it.menu.size == 1 } }
        }
        compose.onNodeWithTag("cart_$lunchPotato").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository("lunch").snapshot().let { it.cart.isEmpty() && it.menu.size == 1 } }
        }
        val unchangedBreakfast = runBlocking { repository("breakfast").snapshot() }
        assertEquals(listOf(breakfastPotato), unchangedBreakfast.cart.map { it.itemId })
        assertEquals(listOf(breakfastPotato), unchangedBreakfast.menu.map { it.itemId })
        returnHome()

        openWay("breakfast", "cooking")
        waitForTag("cooking_list")
        compose.onNodeWithTag("cart_$breakfastPotato").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("menu_add_$breakfastPotato").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository("breakfast").snapshot().let { it.cart.isEmpty() && it.menu.isEmpty() } }
        }
        val unchangedLunch = runBlocking { repository("lunch").snapshot() }
        assertTrue(unchangedLunch.cart.isEmpty())
        assertEquals(listOf(lunchPotato), unchangedLunch.menu.map { it.itemId })
        assertEquals(0L, unchangedLunch.cookingTotal)
        assertEquals(0L, unchangedLunch.items.sumOf { it.count })
        returnHome()

        openWay("lunch", "cooking")
        waitForTag("cooking_list")
        compose.onNodeWithTag("cart_$lunchPotato").performScrollTo().assert(hasText("加入购物车"))
        compose.onNodeWithTag("menu_add_$lunchPotato").performScrollTo().assert(hasText("已加入菜单")).assertIsSelected()
        compose.onNodeWithTag("cart_bar").assert(hasText("购物车 · 0 项"))
        compose.onNodeWithTag("open_cooking_menu").assert(hasText("烹饪菜单 · 1 项"))
    }

    @Test
    fun rapidScopeChangesDismissOldEditorAndShowOnlyTheFinalMeal() {
        compose.onNodeWithTag("home_stats").performScrollTo().performClick()
        waitForTag("stats_list")
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("cooking_total_more"))
        compose.onNodeWithTag("cooking_total_more").performClick()
        compose.onNodeWithText("修改总次数").performClick()
        compose.onNodeWithTag("dialog_count").performTextReplacement("99")
        scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[MealViewModel::class.java]
            listOf("breakfast", "lunch", "late_night", "afternoon_tea").forEach(model::selectMeal)
        }
        waitForTag("stats_meal_afternoon_tea")
        compose.onNodeWithTag("stats_meal_afternoon_tea").assertIsSelected()
        compose.onNodeWithTag("current_meal").assert(hasText("下午茶"))
        compose.onNodeWithTag("dialog_count").assertDoesNotExist()
        Meal.entries.forEach { meal ->
            assertEquals(0L, runBlocking { repository(meal.key).snapshot().cookingTotal })
        }
    }

    @Test
    fun staleHomeAndWayClicksCannotMixMealRoutesAndBackRestoresTheRouteScope() {
        // Capture both callbacks before either changes the composition, then deliver
        // them together exactly like rapid touches queued for one UI frame.
        val breakfastClick = compose.onNodeWithTag("meal_breakfast").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        val lunchClick = compose.onNodeWithTag("meal_lunch").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {
            breakfastClick()
            lunchClick()
        }
        waitForTag("way_takeout")
        compose.onNodeWithText("早餐 · 怎么吃？").assertIsDisplayed()
        compose.onNodeWithText("午餐 · 怎么吃？").assertDoesNotExist()

        val takeoutClick = compose.onNodeWithTag("way_takeout").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        val diningClick = compose.onNodeWithTag("way_dining").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {
            takeoutClick()
            diningClick()
        }
        waitForTag("wheel_section_nutrition")
        compose.onNodeWithTag("current_meal").assert(hasText("早餐"))
        compose.onNodeWithTag("group_${scopedSeedId("breakfast", "dining-01")}").assertDoesNotExist()

        // Deliberately perturb ambient selection while the child is open. The back
        // route must repair it, even independently of the rapid-click guards above.
        lateinit var model: MealViewModel
        scenario.onActivity { activity ->
            model = ViewModelProvider(activity)[MealViewModel::class.java]
            model.selectMeal("lunch")
        }
        compose.waitUntil(10_000) { model.state.value.ready && model.state.value.mealId == "lunch" }
        compose.onNodeWithTag("back").performClick()
        waitForTag("way_dining")
        compose.waitUntil(10_000) { model.state.value.ready && model.state.value.mealId == "breakfast" }
        compose.onNodeWithText("早餐 · 怎么吃？").assertIsDisplayed()
        compose.onNodeWithTag("way_dining").performClick()
        val groupId = scopedSeedId("breakfast", "dining-01")
        waitForTag("group_$groupId")
        compose.onNodeWithTag("group_$groupId").performClick()
        val itemId = scopedSeedId("breakfast", "dining-01-item-01")
        waitForTag("pick_$itemId")
        compose.onNodeWithTag("pick_$itemId").performClick()
        waitForRowText("item_$itemId", "1次")
        assertEquals(1L, runBlocking { repository("breakfast").snapshot().items.sumOf { it.count } })
        assertEquals(0L, runBlocking { repository("lunch").snapshot().items.sumOf { it.count } })

        compose.onNodeWithTag("back").performClick() // cards -> categories
        compose.onNodeWithTag("back").performClick() // categories -> breakfast ways
        waitForTag("way_dining")
        compose.onNodeWithTag("back").performClick() // one home choice, one return home
        waitForTag("home_stats")
        compose.onNodeWithTag("back").assertDoesNotExist()
    }

    @Test
    fun queuedPickWritesKeepTheirOriginatingMealAfterImmediateScopeChanges() {
        compose.onNodeWithTag("home_stats").performScrollTo().performClick()
        waitForTag("stats_list")
        val breakfastId = scopedSeedId("breakfast", "dining-01-item-01")
        val lunchId = scopedSeedId("lunch", "dining-01-item-01")
        val finalMeal = "afternoon_tea"
        val transactionHeld = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        lateinit var model: MealViewModel

        // Hold Room's transaction queue so both writes must finish after navigation.
        // This would expose a write that reads the new selection only after suspension.
        runBlocking {
            val blocker = launch(Dispatchers.IO) {
                database.withTransaction {
                    transactionHeld.complete(Unit)
                    releaseTransaction.await()
                }
            }
            transactionHeld.await()
            try {
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MealViewModel::class.java]
                    model.selectMeal("breakfast")
                    model.pick(breakfastId)
                    model.selectMeal("lunch")
                    model.pick(lunchId)
                    model.selectMeal(finalMeal)
                }
            } finally {
                releaseTransaction.complete(Unit)
            }
            blocker.join()
        }

        compose.waitUntil(10_000) {
            runBlocking {
                repository("breakfast").snapshot().items.single { it.id == breakfastId }.count == 1L &&
                    repository("lunch").snapshot().items.single { it.id == lunchId }.count == 1L
            }
        }
        Meal.entries.forEach { meal ->
            val saved = runBlocking { repository(meal.key).snapshot() }
            val expected = if (meal.key == "breakfast" || meal.key == "lunch") 1L else 0L
            assertEquals("Only the originating meal receives its Pick: ${meal.key}", expected, saved.items.sumOf { it.count })
            assertEquals(0L, saved.cookingTotal)
        }

        compose.waitUntil(10_000) { model.state.value.ready && model.state.value.mealId == finalMeal }
        compose.onNodeWithTag("current_meal").assert(hasText("下午茶"))
        assertDiningTotal(finalMeal, "0次")
        val finalItemId = scopedSeedId(finalMeal, "dining-01-item-01")
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stat_item_$finalItemId"))
        compose.onNodeWithTag("stat_item_$finalItemId").assert(hasAnyDescendant(hasText("0次")))
        compose.onNodeWithTag("stat_item_$breakfastId").assertDoesNotExist()
        compose.onNodeWithTag("stat_item_$lunchId").assertDoesNotExist()
        assertEquals(finalMeal, model.state.value.mealId)
    }

    private fun repository(meal: String) = MealRepository(database, context.assets, mealId = meal)

    private fun openWay(meal: String, way: String) {
        compose.onNodeWithTag("meal_$meal").performScrollTo().performClick()
        waitForTag("way_$way")
        compose.onNodeWithTag("way_$way").performScrollTo().performClick()
    }

    private fun openNutritionEditor(meal: String) {
        openWay(meal, "takeout")
        waitForTag("wheel_section_nutrition")
        compose.onNode(hasText("编辑转盘") and hasAnyAncestor(hasTestTag("wheel_section_nutrition")))
            .performScrollTo().performClick()
        waitForTag("group_${scopedSeedId(meal, "nutrition-01")}")
    }

    private fun openNationalCards(meal: String) {
        openWay(meal, "dining")
        val group = scopedSeedId(meal, "dining-01")
        waitForTag("group_$group")
        compose.onNodeWithTag("group_$group").performClick()
        waitForTag("dining_card_grid")
    }

    private fun assertDiningTotal(meal: String, count: String) {
        waitForTag("stats_meal_$meal")
        compose.onNodeWithTag("stats_meal_$meal").assertIsSelected()
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stat_total_dining"))
        waitForRowText("stat_total_dining", count)
    }

    private fun returnHome() {
        repeat(8) {
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("home_stats").fetchSemanticsNodes().isNotEmpty()) return
            compose.onNodeWithTag("back").performClick()
        }
        error("Did not return to home within the expected navigation depth")
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForTaggedText(tag: String, value: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(value)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForRowText(tag: String, value: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasAnyDescendant(hasText(value))).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
