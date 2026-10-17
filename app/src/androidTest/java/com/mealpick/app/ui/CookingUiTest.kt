package com.mealpick.app.ui

import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mealpick.app.MainActivity
import com.mealpick.app.data.MealDatabase
import com.mealpick.app.data.MealRepository
import com.mealpick.app.data.MealState
import com.mealpick.app.data.MealViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User journeys through the actual Activity, ViewModel, database and touch handlers. */
@RunWith(AndroidJUnit4::class)
class CookingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var repository: MealRepository

    @Before
    fun seedBeforeLaunchingActivity() {
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
    fun directAndPurchasedIngredientsDeduplicateAndCountOnlyWhenTheMealFinishes() {
        val staples = groupId("主食")
        val protein = groupId("蛋白质")
        val potato = itemId("红薯")
        val shrimp = itemId("虾")
        val egg = itemId("鸡蛋")
        openCooking()
        clickInCooking("cooking_group_$staples")
        clickInCooking("menu_add_$potato")
        clickInCooking("cart_$potato")
        clickInCooking("cooking_group_$protein")
        clickInCooking("cart_$shrimp")
        clickInCooking("cart_$egg")
        waitForState { it.cart.size == 3 && it.menu.size == 1 }
        compose.onNodeWithTag("cart_$egg").assertIsEnabled().assertIsSelected()
        assertEquals(listOf(potato), snapshot().menu.map { it.itemId })
        assertNoCookingCounts(potato, shrimp, egg)

        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        compose.onNodeWithTag("cart_list").assertDoesNotExist()
        assertPurchasedCountCopy(0)
        compose.onNodeWithTag("transfer_all").assertIsNotEnabled()
        setPurchase(potato, true)
        setPurchase(shrimp, true)
        clickScrollInPurchase("purchase_pending_heading")
        compose.onNodeWithTag("purchase_pending_heading").assert(hasText("待采购 · 1 项"))
        clickScrollInPurchase("purchase_done_heading")
        compose.onNodeWithTag("purchase_done_heading").assert(hasText("已采购 · 2 项"))
        assertPurchasedCountCopy(2)
        clickInPurchase("transfer_$potato")
        waitForState { it.cart.size == 2 && it.menu.size == 1 }
        assertEquals("A second route does not duplicate an existing ingredient", listOf(potato), snapshot().menu.map { it.itemId })
        assertPurchasedCountCopy(1)
        compose.onNodeWithTag("transfer_all").assertIsEnabled().performClick()
        waitForState { it.menu.size == 2 && it.cart.size == 1 }
        assertEquals(listOf(potato, shrimp), snapshot().menu.sortedBy { it.position }.map { it.itemId })
        assertEquals(egg, snapshot().cart.single().itemId)
        assertTrue(!snapshot().cart.single().purchased)
        assertNoCookingCounts(potato, shrimp, egg)
        assertPurchasedCountCopy(0)
        compose.onNodeWithTag("transfer_all").assertIsNotEnabled()

        compose.onNodeWithTag("back").performClick()
        waitForTag("open_cooking_menu")
        compose.onNodeWithTag("open_cooking_menu").performClick()
        waitForTag("complete_meal")
        compose.onNodeWithTag("complete_meal").assertIsNotEnabled()
        setMenuCompleted(potato, true)
        compose.onNodeWithTag("complete_meal").assertIsNotEnabled()
        setMenuCompleted(shrimp, true)
        compose.onNodeWithTag("complete_meal").assertIsEnabled()
        assertNoCookingCounts(potato, shrimp, egg)
        // Invoke the same enabled callback twice in one turn, before recomposition can
        // disable it. The session guard must keep this at one meal, never two.
        compose.onNodeWithTag("complete_meal").performSemanticsAction(SemanticsActions.OnClick) { action ->
            action()
            action()
        }
        waitForState { it.cookingTotal == 1L && it.menu.isEmpty() }
        waitForTag("meal_recorded")
        compose.onNodeWithTag("complete_meal").assertDoesNotExist()
        assertEquals(1L, snapshot().items.single { it.id == potato }.count)
        assertEquals(1L, snapshot().items.single { it.id == shrimp }.count)
        assertEquals(0L, snapshot().items.single { it.id == egg }.count)
        assertEquals(egg, snapshot().cart.single().itemId)

        // A new Activity loads the stored result, and the shopping work is still there.
        scenario.recreate()
        waitForTag("home_stats")
        compose.onNodeWithTag("home_stats").performScrollTo().performClick()
        waitForTag("stats_list")
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stat_total_cooking"))
        compose.onNodeWithTag("stat_total_cooking").assert(hasAnyDescendant(hasText("1次")))
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stat_item_$potato"))
        compose.onNodeWithTag("stat_item_$potato").assert(hasAnyDescendant(hasText("1次")))
        assertEquals(egg, snapshot().cart.single().itemId)
    }

    @Test
    fun ingredientButtonsRemoveAndReaddBothListsWithoutCountsAndRestoreAfterRelaunch() {
        val staples = groupId("主食")
        val potato = itemId("红薯")
        val pumpkin = itemId("贝贝南瓜")
        openCooking()
        ensureGroupExpanded(staples)
        listOf(potato, pumpkin).forEach { id ->
            clickInCooking("cart_$id")
            clickInCooking("menu_add_$id")
        }
        waitForState { it.cart.size == 2 && it.menu.size == 2 }
        assertMembership("cart_$potato", true, "已加入购物车", "移出购物车")
        assertMembership("menu_add_$potato", true, "已加入菜单", "移出烹饪菜单")
        assertDockCounts(2, 2)

        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        setPurchase(potato, true)
        compose.onNodeWithTag("back").performClick()
        waitForTag("cart_bar")
        compose.onNodeWithTag("open_cooking_menu").performClick()
        waitForTag("cooking_menu_list")
        setMenuCompleted(potato, true)
        compose.onNodeWithTag("back").performClick()
        waitForTag("cart_bar")

        // Removing a purchased ingredient only changes the cart, not its menu state.
        clickInCooking("cart_$potato")
        waitForState { it.cart.size == 1 }
        assertMembership("cart_$potato", false, "加入购物车", "加入购物车")
        assertTrue(snapshot().menu.single { it.itemId == potato }.completed)
        assertDockCounts(1, 2)
        clickInCooking("cart_$potato")
        waitForState { it.cart.size == 2 }
        assertMembership("cart_$potato", true, "已加入购物车", "移出购物车")
        assertTrue(!snapshot().cart.single { it.itemId == potato }.purchased)
        assertEquals(listOf(pumpkin, potato), snapshot().cart.sortedBy { it.position }.map { it.itemId })
        assertDockCounts(2, 2)

        // Removing and readding a completed menu item starts a fresh, incomplete entry.
        clickInCooking("menu_add_$potato")
        waitForState { it.menu.size == 1 }
        assertMembership("menu_add_$potato", false, "加入烹饪菜单", "加入烹饪菜单")
        assertEquals(2, snapshot().cart.size)
        assertDockCounts(2, 1)
        clickInCooking("menu_add_$potato")
        waitForState { it.menu.size == 2 }
        assertMembership("menu_add_$potato", true, "已加入菜单", "移出烹饪菜单")
        assertTrue(!snapshot().menu.single { it.itemId == potato }.completed)
        assertEquals(listOf(pumpkin, potato), snapshot().menu.sortedBy { it.position }.map { it.itemId })
        assertDockCounts(2, 2)
        assertNoCookingCounts(potato, pumpkin)

        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        clickScrollInPurchase("purchase_check_$potato")
        compose.onNodeWithTag("purchase_check_$potato").assertIsOff()
        compose.onNodeWithTag("open_cooking_menu").performClick()
        waitForTag("cooking_menu_list")
        clickScrollInMenu("cooking_completed_$potato")
        compose.onNodeWithTag("cooking_completed_$potato").assertIsOff()
        compose.onNodeWithTag("complete_meal").assertIsNotEnabled()

        scenario.recreate()
        waitForTag("home_stats")
        openCooking()
        ensureGroupExpanded(staples)
        assertMembership("cart_$potato", true, "已加入购物车", "移出购物车")
        assertMembership("menu_add_$potato", true, "已加入菜单", "移出烹饪菜单")
        assertDockCounts(2, 2)
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("home_stats")
        openCooking()
        ensureGroupExpanded(staples)
        assertMembership("cart_$potato", true, "已加入购物车", "移出购物车")
        assertMembership("menu_add_$potato", true, "已加入菜单", "移出烹饪菜单")
        assertDockCounts(2, 2)
        assertNoCookingCounts(potato, pumpkin)
    }

    @Test
    fun repeatedCallbacksBeforeRecompositionToggleTheCommittedMembership() {
        val potato = itemId("红薯")
        openCooking()
        ensureGroupExpanded(groupId("主食"))
        lateinit var model: MealViewModel
        scenario.onActivity { model = ViewModelProvider(it)[MealViewModel::class.java] }

        fun rapidClicks(times: Int) {
            compose.onNodeWithTag("cart_$potato").performScrollTo()
            val cartClick = compose.onNodeWithTag("cart_$potato").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            val menuClick = compose.onNodeWithTag("menu_add_$potato").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            var pendingWrites = emptyList<Job>()
            compose.runOnIdle {
                val existingJobs = model.viewModelScope.coroutineContext.job.children.toSet()
                repeat(times) {
                    cartClick()
                    menuClick()
                }
                // Join only jobs created by these clicks, not the permanent state observer.
                // An initial empty snapshot must not let the even-click assertion pass early.
                pendingWrites = model.viewModelScope.coroutineContext.job.children.filterNot { it in existingJobs }.toList()
            }
            runBlocking { pendingWrites.joinAll() }
        }

        rapidClicks(2)
        assertTrue(snapshot().cart.isEmpty() && snapshot().menu.isEmpty())
        assertMembership("cart_$potato", false, "加入购物车", "加入购物车")
        assertMembership("menu_add_$potato", false, "加入烹饪菜单", "加入烹饪菜单")
        assertDockCounts(0, 0)
        rapidClicks(3)
        assertEquals(listOf(potato), snapshot().cart.map { it.itemId })
        assertEquals(listOf(potato), snapshot().menu.map { it.itemId })
        assertMembership("cart_$potato", true, "已加入购物车", "移出购物车")
        assertMembership("menu_add_$potato", true, "已加入菜单", "移出烹饪菜单")
        assertDockCounts(1, 1)
        rapidClicks(2)
        assertEquals(listOf(potato), snapshot().cart.map { it.itemId })
        assertEquals(listOf(potato), snapshot().menu.map { it.itemId })
        assertDockCounts(1, 1)
        assertNoCookingCounts(potato)
    }

    @Test
    fun englishIngredientToggleLabelsAndRemovalActionsKeepUserNames() {
        val potato = itemId("红薯")
        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_en").performClick()
        waitForState { it.language == "en" }
        openCooking()
        ensureGroupExpanded(groupId("主食"))
        assertMembership("cart_$potato", false, "Add to cart", "Add to cart")
        assertMembership("menu_add_$potato", false, "Add to cooking menu", "Add to cooking menu")
        clickInCooking("cart_$potato")
        clickInCooking("menu_add_$potato")
        waitForState { it.cart.size == 1 && it.menu.size == 1 }
        assertMembership("cart_$potato", true, "In cart", "Remove from cart")
        assertMembership("menu_add_$potato", true, "In cooking menu", "Remove from cooking menu")
        clickInCooking("cart_$potato")
        clickInCooking("menu_add_$potato")
        waitForState { it.cart.isEmpty() && it.menu.isEmpty() }
        assertMembership("cart_$potato", false, "Add to cart", "Add to cart")
        assertMembership("menu_add_$potato", false, "Add to cooking menu", "Add to cooking menu")
        compose.onNodeWithTag("item_name_$potato").assert(hasAnyDescendant(hasText("红薯")))
        assertEquals("红薯", snapshot().items.single { it.id == potato }.name)
        assertNoCookingCounts(potato)
    }

    @Test
    fun purchasedAndCookingChecksCanBeUndoneRemovedAndRestoredAfterRecreation() {
        val potato = itemId("红薯")
        val pumpkin = itemId("贝贝南瓜")
        val shrimp = itemId("虾")
        val egg = itemId("鸡蛋")
        runBlocking {
            repository.addToCart(potato)
            repository.addToCart(pumpkin)
            repository.addToCookingMenu(shrimp)
            repository.addToCookingMenu(egg)
        }
        openCooking()
        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        setPurchase(potato, true)
        setPurchase(potato, false)
        assertPurchasedCountCopy(0)
        compose.onNodeWithTag("transfer_all").assertIsNotEnabled()
        setPurchase(potato, true)
        setPurchase(pumpkin, true)
        clickInPurchase("purchase_more_$pumpkin")
        compose.onNodeWithText("移出购物车").performClick()
        waitForState { it.cart.size == 1 && it.cart.single().purchased }
        assertPurchasedCountCopy(1)
        compose.onNodeWithTag("back").performClick()
        waitForTag("open_cooking_menu")
        compose.onNodeWithTag("open_cooking_menu").performClick()
        setMenuCompleted(shrimp, true)
        setMenuCompleted(shrimp, false)
        compose.onNodeWithTag("complete_meal").assertIsNotEnabled()
        setMenuCompleted(shrimp, true)
        clickInMenu("cooking_menu_more_$egg")
        compose.onNodeWithText("移出烹饪菜单").performClick()
        waitForState { it.menu.size == 1 && it.menu.single().completed }
        compose.onNodeWithTag("complete_meal").assertIsEnabled()
        assertNoCookingCounts(potato, pumpkin, shrimp, egg)

        scenario.recreate()
        waitForTag("home_stats")
        openCooking()
        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        clickScrollInPurchase("purchase_check_$potato")
        compose.onNodeWithTag("purchase_check_$potato").assertIsOn()
        assertEquals(listOf(potato), snapshot().cart.map { it.itemId })
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForTag("open_cooking_menu")
        compose.onNodeWithTag("open_cooking_menu").performClick()
        clickScrollInMenu("cooking_completed_$shrimp")
        compose.onNodeWithTag("cooking_completed_$shrimp").assertIsOn()
        compose.onNodeWithTag("complete_meal").assertIsEnabled()
        assertEquals(listOf(shrimp), snapshot().menu.map { it.itemId })
        assertNoCookingCounts(potato, pumpkin, shrimp, egg)
        compose.onNodeWithTag("back").performClick()
        waitForTag("cart_bar")
        compose.onNodeWithTag("cart_list").assertDoesNotExist()
    }

    @Test
    fun emptyShoppingAndMenuOpenDirectlyButCannotTransferOrFinish() {
        openCooking()
        compose.onNodeWithTag("cart_bar").assertIsEnabled().performClick()
        waitForTag("purchase_list")
        compose.onNodeWithTag("cart_list").assertDoesNotExist()
        assertPurchasedCountCopy(0)
        compose.onNodeWithTag("transfer_all").assertIsNotEnabled()
        compose.onNodeWithTag("back").performClick()
        waitForTag("open_cooking_menu")
        compose.onNodeWithTag("open_cooking_menu").assertIsEnabled().performClick()
        waitForTag("choose_ingredients")
        compose.onNodeWithTag("complete_meal").assertDoesNotExist()
        compose.onNodeWithTag("choose_ingredients").performClick()
        waitForTag("cart_bar")
        assertTrue(snapshot().cart.isEmpty())
        assertTrue(snapshot().menu.isEmpty())
        assertEquals(0L, snapshot().cookingTotal)
    }

    @Test
    fun bulkTransferIsBelowPurchasedHeadingAndNavigationDoesNotTransferIngredients() {
        val potato = itemId("红薯")
        val shrimp = itemId("虾")
        runBlocking {
            repository.addToCart(potato)
            repository.addToCart(shrimp)
            repository.setPurchased(potato, true)
            repository.setPurchased(shrimp, true)
        }
        openCooking()
        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_done_heading")
        val heading = compose.onNodeWithTag("purchase_done_heading").fetchSemanticsNode().boundsInRoot
        val bulk = compose.onNodeWithTag("transfer_all").fetchSemanticsNode().boundsInRoot
        val firstPurchased = compose.onNodeWithTag("purchase_item_$potato").fetchSemanticsNode().boundsInRoot
        assertTrue("Bulk transfer follows the purchased heading", heading.bottom <= bulk.top)
        assertTrue("Bulk transfer precedes the first purchased ingredient", bulk.bottom <= firstPurchased.top)
        compose.onNodeWithTag("transfer_all").assertIsEnabled()
        assertPurchasedCountCopy(2)

        compose.onNodeWithTag("open_cooking_menu").performClick()
        waitForTag("choose_ingredients")
        assertTrue("Navigation must not transfer ingredients", snapshot().menu.isEmpty())
        assertEquals(2, snapshot().cart.count { it.purchased })
        compose.onNodeWithTag("back").performClick()
        waitForTag("purchase_list")
        clickInPurchase("transfer_all")
        waitForState { it.cart.isEmpty() && it.menu.size == 2 }
        assertNoCookingCounts(potato, shrimp)
    }

    @Test
    fun narrowCookingLayoutStacksIngredientAndNavigationActionsWithoutTruncation() {
        showCookingAt(320.dp)
        assertCookingActionLayout(stacked = true)
    }

    @Test
    fun largeFontCookingLayoutStacksIngredientAndNavigationActionsWithoutTruncation() {
        showCookingAt(360.dp, fontScale = 1.6f)
        assertCookingActionLayout(stacked = true)
    }

    @Test
    fun englishCookingActionsStackWhenLongLabelsNeedMoreRoom() {
        showCookingAt(360.dp, language = "en")
        assertCookingActionLayout(stacked = true)
    }

    @Test
    fun regularChineseCookingLayoutKeepsSideBySideActions() {
        showCookingAt(360.dp)
        assertCookingActionLayout(stacked = false)
    }

    @Test
    fun editsCancelOrSaveAndLongPressReordersCategoriesAndIngredients() {
        val staples = groupId("主食")
        val protein = groupId("蛋白质")
        val vegetables = groupId("蔬菜")
        val shrimp = itemId("虾")
        val egg = itemId("鸡蛋")
        openCooking()

        // Drag collapsed categories by their real handles; this moves the entire group.
        dragBetween("cooking_group_drag_$staples", "cooking_group_block_$staples", "cooking_group_block_$protein")
        waitForState { it.groups.filter { group -> group.scene == "cooking" }.minBy { group -> group.position }.id == protein }
        clickInCooking("cooking_group_$protein")
        runBlocking { repository.setCount(shrimp, 3) }
        dragBetween("drag_$shrimp", "item_$shrimp", "item_$egg")
        waitForState { it.items.filter { item -> item.groupId == protein }.minBy { item -> item.position }.id == egg }
        assertEquals(3L, snapshot().items.single { it.id == shrimp }.count)
        clickInCooking("cooking_group_$protein")

        clickInCooking("cooking_group_$vegetables")
        clickInCooking("cooking_add_item_$vegetables")
        compose.onNodeWithTag("dialog_name").performTextReplacement("取消的蔬菜")
        compose.onNodeWithText("取消").performClick()
        assertTrue(snapshot().items.none { it.groupId == vegetables })
        clickInCooking("cooking_add_item_$vegetables")
        compose.onNodeWithTag("dialog_name").performTextReplacement("菠菜")
        compose.onNodeWithTag("dialog_notes").performTextReplacement("洗净后清炒 / wash first")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForState { it.items.any { item -> item.groupId == vegetables && item.name == "菠菜" } }
        val spinach = itemId("菠菜")

        clickInCooking("more_$spinach")
        compose.onNodeWithText("编辑文字").performClick()
        compose.onNodeWithTag("dialog_name").performTextReplacement("取消的改名")
        compose.onNodeWithTag("dialog_notes").performTextReplacement("不保存")
        compose.onNodeWithText("取消").performClick()
        assertEquals("菠菜", snapshot().items.single { it.id == spinach }.name)
        assertEquals("洗净后清炒 / wash first", snapshot().items.single { it.id == spinach }.notes)
        clickInCooking("more_$spinach")
        compose.onNodeWithText("编辑文字").performClick()
        val longName = "菠菜和青菜混合包 / spinach & greens"
        compose.onNodeWithTag("dialog_name").performTextReplacement(longName)
        compose.onNodeWithTag("dialog_notes").performTextReplacement("第一行备注\n第二行保留原文")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForState { it.items.single { item -> item.id == spinach }.name == longName }

        scenario.recreate()
        waitForTag("home_stats")
        openCooking()
        val groups = snapshot().groups.filter { it.scene == "cooking" }.sortedBy { it.position }
        assertEquals(listOf(protein, staples, vegetables), groups.map { it.id })
        ensureGroupExpanded(protein)
        val upper = compose.onNodeWithTag("item_$egg").fetchSemanticsNode().boundsInRoot.top
        val lower = compose.onNodeWithTag("item_$shrimp").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Ingredient order survives Activity recreation", upper < lower)
        assertEquals(3L, snapshot().items.single { it.id == shrimp }.count)
        clickInCooking("cooking_group_$protein")
        ensureGroupExpanded(vegetables)
        compose.onNodeWithTag("item_name_$spinach").performScrollTo().assert(hasAnyDescendant(hasText(longName)))
        assertEquals("第一行备注\n第二行保留原文", snapshot().items.single { it.id == spinach }.notes)
    }

    @Test
    fun tallIngredientUsesItsActualHandleAndCancelledDragRestoresSavedOrder() {
        val protein = groupId("蛋白质")
        val shrimp = itemId("虾")
        val egg = itemId("鸡蛋")
        val longNotes = "第一行先解冻\n第二行去虾线\n第三行保留虾壳\n第四行清水冲洗\n第五行擦干水分\n第六行再下锅"
        runBlocking {
            repository.updateItem(shrimp, "虾", longNotes)
            repository.setCount(shrimp, 3)
        }
        openCooking()
        clickInCooking("cooking_group_$protein")
        compose.onNodeWithTag("item_$shrimp").performScrollTo()
        compose.waitForIdle()
        val fromHandle = compose.onNodeWithTag("drag_$shrimp").fetchSemanticsNode().boundsInRoot.center.y
        val toHandle = compose.onNodeWithTag("drag_$egg").fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithTag("drag_$egg").assertIsDisplayed()
        // A tall note moves the source handle far below the row's top. End just past
        // the target handle: a synthetic row-top pointer cannot pass this test.
        compose.onNodeWithTag("drag_$shrimp").performTouchInput {
            down(center)
            advanceEventTime(700)
            for (step in 1..20) moveTo(center + Offset(0f, (toHandle - fromHandle + 4f) * step / 20f), delayMillis = 32)
            up()
        }
        waitForState { it.items.filter { item -> item.groupId == protein }.minBy { item -> item.position }.id == egg }
        val committedIds = snapshot().items.filter { it.groupId == protein }.sortedBy { it.position }.map { it.id }
        assertEquals(3L, snapshot().items.single { it.id == shrimp }.count)

        // Make the whole tall target visible before the cancellation gesture. Checking
        // its clipped bounds would otherwise pin its top to the viewport on narrow phones.
        compose.onNodeWithTag("item_$shrimp").performScrollTo()
        compose.waitForIdle()
        fun targetOffsetInGroup(): Float =
            compose.onNodeWithTag("item_$shrimp").fetchSemanticsNode().positionInRoot.y -
                compose.onNodeWithTag("cooking_group_block_$protein").fetchSemanticsNode().positionInRoot.y
        val originalTargetOffset = targetOffsetInGroup()
        val sourceHandleNode = compose.onNodeWithTag("drag_$egg").fetchSemanticsNode()
        val targetHandleNode = compose.onNodeWithTag("drag_$shrimp").fetchSemanticsNode()
        val cancelFrom = sourceHandleNode.positionInRoot.y + sourceHandleNode.size.height / 2f
        val cancelTo = targetHandleNode.positionInRoot.y + targetHandleNode.size.height / 2f
        val crossingMargin = targetHandleNode.size.height * .6f
        compose.onNodeWithTag("drag_$egg").assertIsDisplayed()
        compose.onNodeWithTag("drag_$shrimp").assertIsDisplayed()
        assertEquals("The source handle is entirely inside the viewport", sourceHandleNode.size.height.toFloat(), sourceHandleNode.boundsInRoot.height, 1f)
        assertEquals("The target handle is entirely inside the viewport", targetHandleNode.size.height.toFloat(), targetHandleNode.boundsInRoot.height, 1f)
        compose.onNodeWithTag("drag_$egg").performTouchInput {
            down(center)
            advanceEventTime(700)
            for (step in 1..20) moveTo(center + Offset(0f, (cancelTo - cancelFrom + crossingMargin) * step / 20f), delayMillis = 32)
        }
        try {
            compose.waitForIdle()
            val liveTargetOffset = targetOffsetInGroup()
            assertTrue("The live drag must actually cross the tall row before cancellation: target offset $originalTargetOffset -> $liveTargetOffset",
                liveTargetOffset < originalTargetOffset - 8f)
            assertEquals("Moving without dropping must not write an order", committedIds,
                snapshot().items.filter { it.groupId == protein }.sortedBy { it.position }.map { it.id })
        } finally {
            compose.onNodeWithTag("drag_$egg").performTouchInput { cancel() }
        }
        compose.waitForIdle()
        val restoredEggTop = compose.onNodeWithTag("item_$egg").fetchSemanticsNode().boundsInRoot.top
        val restoredShrimpTop = compose.onNodeWithTag("item_$shrimp").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Cancellation restores the original visual order", restoredEggTop < restoredShrimpTop)
        assertEquals(committedIds, snapshot().items.filter { it.groupId == protein }.sortedBy { it.position }.map { it.id })

        scenario.recreate()
        waitForTag("home_stats")
        assertEquals(committedIds, snapshot().items.filter { it.groupId == protein }.sortedBy { it.position }.map { it.id })
        assertEquals(longNotes, snapshot().items.single { it.id == shrimp }.notes)
        assertEquals(3L, snapshot().items.single { it.id == shrimp }.count)
    }

    @Test
    fun englishControlsKeepChineseIngredientsAndTotalCorrectionIsIndependent() {
        val originalGroups = snapshot().groups
        val originalItems = snapshot().items
        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_en").performClick()
        waitForState { it.language == "en" }
        compose.onNodeWithTag("meal_dinner").performScrollTo().performClick()
        waitForTag("way_cooking")
        compose.onNodeWithTag("way_cooking").performScrollTo().assert(hasText("Cook at Home")).performClick()
        waitForTag("cart_bar")
        val staples = groupId("主食")
        val potato = itemId("红薯")
        compose.onNodeWithText("主食").assertIsDisplayed()
        compose.onNodeWithText("蛋白质").assertIsDisplayed()
        compose.onNodeWithText("蔬菜").assertIsDisplayed()
        clickInCooking("cooking_group_$staples")
        compose.onNodeWithText("红薯").assertIsDisplayed()
        val pumpkin = itemId("贝贝南瓜")
        clickInCooking("menu_add_$potato")
        clickInCooking("cart_$pumpkin")
        waitForState { it.cart.size == 1 && it.menu.size == 1 }
        compose.onNodeWithTag("cart_bar").performClick()
        waitForTag("purchase_list")
        compose.onNodeWithText("Shopping Cart").assertIsDisplayed()
        setPurchase(pumpkin, true)
        clickScrollInPurchase("transfer_all")
        compose.onNodeWithTag("transfer_all").assert(hasText("Add all 1 purchased ingredients to cooking menu")).performClick()
        waitForState { it.cart.isEmpty() && it.menu.size == 2 }
        compose.onNodeWithTag("open_cooking_menu").performClick()
        waitForTag("cooking_menu_list")
        compose.onNodeWithText("Cooking Menu").assertIsDisplayed()
        clickScrollInMenu("cooking_menu_item_$potato")
        compose.onNodeWithTag("cooking_menu_item_$potato").assert(hasAnyDescendant(hasText("红薯")))
        clickScrollInMenu("cooking_menu_item_$pumpkin")
        compose.onNodeWithTag("cooking_menu_item_$pumpkin").assert(hasAnyDescendant(hasText("贝贝南瓜")))
        compose.onNodeWithTag("complete_meal").assert(hasText("Finish this meal")).assertIsNotEnabled()
        assertNoCookingCounts(potato, pumpkin)
        compose.onNodeWithTag("back").performClick()
        waitForTag("purchase_list")
        compose.onNodeWithTag("back").performClick()
        waitForTag("cart_bar")
        clickInCooking("more_$potato")
        compose.onNodeWithText("Edit count").performClick()
        compose.onNodeWithTag("dialog_count").performTextReplacement("7")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForState { it.items.single { item -> item.id == potato }.count == 7L }
        assertEquals(0L, snapshot().cookingTotal)
        compose.onNodeWithTag("back").performClick()
        waitForTag("way_cooking")
        compose.onNodeWithTag("back").performClick()
        waitForTag("home_stats")
        compose.onNodeWithTag("home_stats").performScrollTo().performClick()
        waitForTag("stats_list")
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("cooking_total_more"))
        compose.onNodeWithTag("cooking_total_more").performClick()
        compose.onNodeWithText("Edit total count").performClick()
        compose.onNodeWithTag("dialog_count").performTextReplacement("5")
        compose.onNodeWithTag("dialog_save").performClick()
        waitForState { it.cookingTotal == 5L }
        assertEquals(7L, snapshot().items.single { it.id == potato }.count)
        assertEquals(originalGroups, snapshot().groups)
        assertEquals(originalItems.map { Triple(it.id, it.name, it.notes) }, snapshot().items.map { Triple(it.id, it.name, it.notes) })

        scenario.recreate()
        waitForTag("home_stats")
        compose.onNodeWithTag("meal_dinner").performScrollTo().assert(hasText("Dinner"))
        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_zh").performClick()
        waitForState { it.language == "zh" }
        compose.onNodeWithTag("meal_dinner").assert(hasText("晚餐")).performClick()
        waitForTag("way_cooking")
        compose.onNodeWithTag("way_cooking").assert(hasText("自己做"))
        assertEquals(5L, snapshot().cookingTotal)
    }

    /** Override only the test content's width and density, never global device settings. */
    private fun showCookingAt(width: Dp, fontScale: Float = 1f, language: String = "zh") {
        val state = snapshot().copy(language = language)
        scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[MealViewModel::class.java]
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    MealTheme {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.width(width)) {
                                CookingScreen(state, viewModel, onPurchase = {}, onMenu = {})
                            }
                        }
                    }
                }
            }
        }
        waitForTag("cart_bar")
    }

    private fun assertCookingActionLayout(stacked: Boolean) {
        val potato = itemId("红薯")
        clickInCooking("cooking_group_${groupId("主食")}")
        compose.onNodeWithTag("item_$potato").performScrollTo()
        compose.waitForIdle()
        listOf("cart_$potato" to "menu_add_$potato", "cart_bar" to "open_cooking_menu").forEach { (first, second) ->
            val firstBounds = compose.onNodeWithTag(first).assertIsDisplayed().assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            val secondBounds = compose.onNodeWithTag(second).assertIsDisplayed().assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            if (stacked) {
                assertTrue("$first should sit above $second", firstBounds.bottom <= secondBounds.top)
                assertEquals("Stacked actions share the same left edge", firstBounds.left, secondBounds.left, 1f)
            } else {
                assertTrue("$first should sit to the left of $second", firstBounds.right <= secondBounds.left)
                assertEquals("Side-by-side actions share a center line", firstBounds.center.y, secondBounds.center.y, 1f)
            }
            listOf(first, second).forEach { tag ->
                val textLayouts = mutableListOf<TextLayoutResult>()
                val textNode = compose.onNodeWithTag("${tag}_label", useUnmergedTree = true)
                textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
                assertTrue("$tag must expose its full label", textLayouts.isNotEmpty())
                val node = textNode.fetchSemanticsNode()
                assertTrue("$tag text must not be clipped by its viewport",
                    node.boundsInRoot.width + 0.5f >= node.size.width && node.boundsInRoot.height + 0.5f >= node.size.height)
                compose.runOnIdle { textLayouts.forEach { assertFullTextFits(tag, it, node.size) } }
            }
        }
    }

    private fun openCooking() {
        compose.onNodeWithTag("meal_dinner").performScrollTo().performClick()
        waitForTag("way_cooking")
        compose.onNodeWithTag("way_cooking").performScrollTo().performClick()
        waitForTag("cart_bar")
    }

    private fun clickInCooking(tag: String) {
        waitForTag(tag)
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun ensureGroupExpanded(groupId: String) {
        if (compose.onAllNodesWithTag("cooking_add_item_$groupId").fetchSemanticsNodes().isEmpty()) {
            clickInCooking("cooking_group_$groupId")
        }
    }

    private fun setPurchase(id: String, checked: Boolean) {
        clickInPurchase("purchase_check_$id")
        waitForState { it.cart.single { entry -> entry.itemId == id }.purchased == checked }
        clickScrollInPurchase("purchase_check_$id")
        if (checked) compose.onNodeWithTag("purchase_check_$id").assertIsOn()
        else compose.onNodeWithTag("purchase_check_$id").assertIsOff()
    }

    private fun setMenuCompleted(id: String, completed: Boolean) {
        clickInMenu("cooking_completed_$id")
        waitForState { it.menu.single { entry -> entry.itemId == id }.completed == completed }
        clickScrollInMenu("cooking_completed_$id")
        if (completed) compose.onNodeWithTag("cooking_completed_$id").assertIsOn()
        else compose.onNodeWithTag("cooking_completed_$id").assertIsOff()
        compose.onNodeWithTag("cooking_menu_item_$id").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (completed) "已完成" else "待完成")
        )
    }

    private fun clickScrollInPurchase(tag: String) {
        waitForTag("purchase_list")
        compose.onNodeWithTag("purchase_list").performScrollToNode(hasTestTag(tag))
    }

    private fun clickInPurchase(tag: String) {
        clickScrollInPurchase(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun clickScrollInMenu(tag: String) {
        waitForTag("cooking_menu_list")
        compose.onNodeWithTag("cooking_menu_list").performScrollToNode(hasTestTag(tag))
    }

    private fun clickInMenu(tag: String) {
        clickScrollInMenu(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun assertMembership(tag: String, selected: Boolean, label: String, actionLabel: String) {
        compose.onNodeWithTag(tag).performScrollTo()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val node = compose.onNodeWithTag(tag).assertIsEnabled().fetchSemanticsNode()
        assertEquals(actionLabel, node.config[SemanticsActions.OnClick].label)
        assertTrue("Membership action remains clickable", node.config[SemanticsActions.OnClick].action != null)
    }

    private fun assertDockCounts(cart: Int, menu: Int) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("cart_bar") and hasText("购物车 · $cart 项")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasTestTag("open_cooking_menu") and hasText("烹饪菜单 · $menu 项")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoCookingCounts(vararg ids: String) {
        assertEquals(0L, snapshot().cookingTotal)
        ids.forEach { id -> assertEquals(0L, snapshot().items.single { it.id == id }.count) }
    }

    private fun assertPurchasedCountCopy(count: Int) {
        clickScrollInPurchase("transfer_all")
        val expected = "把已采购的${count}个食材全部加入烹饪菜单"
        compose.onNodeWithTag("transfer_all").assert(hasText(expected))
        val text = compose.onNodeWithTag("transfer_all_text", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single { it.text == expected }
        val countStart = expected.indexOf(count.toString())
        assertTrue("The purchased count has its own emphasized span", text.spanStyles.any { span ->
            span.start == countStart && span.end == countStart + count.toString().length &&
                span.item.fontWeight != null && span.item.fontWeight!! >= FontWeight.Bold
        })
    }

    private fun dragBetween(handle: String, from: String, to: String) {
        compose.onNodeWithTag(from).performScrollTo()
        compose.waitForIdle()
        val origin = compose.onNodeWithTag(from).fetchSemanticsNode().boundsInRoot.center.y
        val destination = compose.onNodeWithTag(to).fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithTag(handle).performTouchInput {
            down(center)
            advanceEventTime(700)
            for (step in 1..20) moveTo(center + Offset(0f, (destination - origin) * step / 20f), delayMillis = 32)
            up()
        }
    }

    private fun snapshot(): MealState = runBlocking { repository.snapshot() }
    private fun groupId(name: String): String = snapshot().groups.single { it.scene == "cooking" && it.name == name }.id
    private fun itemId(name: String): String {
        val state = snapshot()
        val groupIds = state.groups.filter { it.scene == "cooking" }.map { it.id }.toSet()
        return state.items.single { it.groupId in groupIds && it.name == name }.id
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForState(predicate: (MealState) -> Boolean) {
        compose.waitUntil(5_000) { predicate(snapshot()) }
        compose.waitForIdle()
    }
}
