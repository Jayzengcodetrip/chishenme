package com.mealpick.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MealIsolationTest {
    private lateinit var context: Context
    private lateinit var database: MealDatabase
    private lateinit var databaseName: String
    private lateinit var repositories: Map<String, MealRepository>

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "meal-isolation-${UUID.randomUUID()}.db"
        openDatabase()
        repositories.values.forEach { it.initialize() }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, MealDatabase::class.java, databaseName)
            .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4).build()
        repositories = MEAL_IDS.associateWith { MealRepository(database, context.assets, it) }
    }

    private suspend fun snapshots(): Map<String, MealState> = repositories.mapValues { it.value.snapshot() }

    private suspend fun reopen() {
        database.close()
        openDatabase()
        repositories.values.forEach { it.initialize() }
    }

    @Test
    fun firstInstallHasFiveCompleteIndependentSeedsIncludingNutritionAndLanguageIsShared() = runBlocking {
        assertEquals(setOf("breakfast", "lunch", "dinner", "afternoon_tea", "late_night"), MEAL_IDS.toSet())
        val states = snapshots()
        val dinner = states.getValue("dinner")
        assertEquals(21, dinner.groups.size)
        assertEquals(67, dinner.items.size)
        states.forEach { (meal, state) ->
            assertEquals(meal, state.mealId)
            assertTrue(state.ready)
            assertEquals(dinner.groups.map { it.copy(id = scopedSeedId(meal, it.id), mealId = meal) }, state.groups)
            assertEquals(dinner.items.map { it.copy(id = scopedSeedId(meal, it.id), groupId = scopedSeedId(meal, it.groupId)) }, state.items)
            assertEquals(listOf("赛百味", "沙县鸡腿饭", "潮汕牛肉汤饭", "蛋白粉+农耕记米饭", "霸碗盖码饭"), state.groups.filter { it.zone == "nutrition" }.map { it.name })
            assertTrue(state.items.all { it.count == 0L })
            assertTrue(state.cart.isEmpty())
            assertTrue(state.menu.isEmpty())
            assertEquals(0L, state.cookingTotal)
            assertNull(state.cartSessionId)
            assertNull(state.menuSessionId)
        }
        assertEquals(105, states.values.flatMap { it.groups }.map { it.id }.toSet().size)
        assertEquals(335, states.values.flatMap { it.items }.map { it.id }.toSet().size)
        repositories.getValue("breakfast").setLanguage("en")
        val localized = snapshots()
        assertTrue(localized.values.all { it.language == "en" })
        localized.forEach { (meal, state) ->
            assertEquals(states.getValue(meal).groups, state.groups)
            assertEquals(states.getValue(meal).items, state.items)
        }
        reopen()
        assertEquals(localized, snapshots())
    }

    @Test
    fun breakfastWheelAndListEditsReorderingCountsAndDeletionLeaveOtherMealsUnchanged() = runBlocking {
        val before = snapshots()
        val breakfast = repositories.getValue("breakfast")
        val initial = before.getValue("breakfast")
        val nutrition = initial.groups.filter { it.zone == "nutrition" }
        val rice = initial.groups.single { it.name == "米饭" }
        val riceItems = initial.items.filter { it.groupId == rice.id }
        breakfast.renameGroup(nutrition.first().id, "早餐营养 / Breakfast")
        breakfast.reorderGroups("takeout", "nutrition", nutrition.map { it.id }.reversed())
        breakfast.deleteGroup(nutrition.last().id)
        breakfast.addGroup("takeout", "nutrition", "早餐新店")
        val newGroup = breakfast.snapshot().groups.single { it.name == "早餐新店" }
        breakfast.addItem(newGroup.id, "早餐新餐品", "只属于早餐")
        breakfast.pick(breakfast.snapshot().items.single { it.groupId == newGroup.id }.id)
        breakfast.reorderItems(rice.id, riceItems.map { it.id }.reversed())
        breakfast.updateItem(riceItems.first().id, "早餐改名", "Breakfast note")
        breakfast.setCount(riceItems.first().id, 8)
        breakfast.pick(riceItems.first().id)
        breakfast.deleteItem(riceItems.last().id)
        val edited = breakfast.snapshot()
        assertEquals(9L, edited.items.single { it.id == riceItems.first().id }.count)
        assertEquals("Breakfast note", edited.items.single { it.id == riceItems.first().id }.notes)
        assertEquals(riceItems.dropLast(1).map { it.id }.reversed(), edited.items.filter { it.groupId == rice.id }.map { it.id })
        assertEquals(nutrition.dropLast(1).map { it.id }.reversed() + newGroup.id, edited.groups.filter { it.zone == "nutrition" }.map { it.id })
        assertFalse(edited.groups.any { it.id == nutrition.last().id })
        assertEquals(1L, edited.items.single { it.groupId == newGroup.id }.count)
        MEAL_IDS.filter { it != "breakfast" }.forEach { assertEquals(before.getValue(it), repositories.getValue(it).snapshot()) }
        val after = snapshots()
        reopen()
        assertEquals(after, snapshots())
    }

    @Test
    fun everyMealOwnsDiningSnacksAndCookingEditsNotJustItsTakeoutWheel() = runBlocking {
        MEAL_IDS.forEachIndexed { index, meal ->
            val before = snapshots()
            val repository = repositories.getValue(meal)
            val state = before.getValue(meal)
            listOf("dining", "snacks", "cooking").forEach { scene ->
                val group = state.groups.first { it.scene == scene && state.items.any { item -> item.groupId == it.id } }
                val item = state.items.first { it.groupId == group.id }
                repository.updateItem(item.id, "$meal-$scene", "仅此餐备注 $index")
                repository.setCount(item.id, (index + 1).toLong())
                repository.renameGroup(group.id, "$meal 分类 $scene")
                val updated = repository.snapshot()
                assertEquals("$meal-$scene", updated.items.single { it.id == item.id }.name)
                assertEquals((index + 1).toLong(), updated.items.single { it.id == item.id }.count)
                assertEquals("$meal 分类 $scene", updated.groups.single { it.id == group.id }.name)
            }
            MEAL_IDS.filter { it != meal }.forEach { assertEquals(before.getValue(it), repositories.getValue(it).snapshot()) }
        }
    }

    @Test
    fun foreignMealIdsCannotMutateOrDeleteDataThroughAnyRepositoryWrite() = runBlocking {
        val breakfast = repositories.getValue("breakfast")
        val lunch = repositories.getValue("lunch")
        val lunchState = lunch.snapshot()
        val rice = lunchState.groups.single { it.name == "米饭" }
        val riceItems = lunchState.items.filter { it.groupId == rice.id }
        val shrimp = lunchState.items.single { it.name == "虾" }
        lunch.addToCart(shrimp.id)
        lunch.setPurchased(shrimp.id, true)
        lunch.addToCookingMenu(shrimp.id)
        lunch.setCookingCompleted(shrimp.id, true)
        val before = snapshots()
        breakfast.renameGroup(rice.id, "错误餐次")
        breakfast.addItem(rice.id, "错误新增", "不应创建")
        breakfast.updateItem(riceItems.first().id, "错误改名", "错误备注")
        breakfast.setCount(riceItems.first().id, 999)
        breakfast.pick(riceItems.first().id)
        breakfast.reorderItems(rice.id, riceItems.map { it.id }.reversed())
        breakfast.reorderGroups("takeout", "mood", lunchState.groups.filter { it.zone == "mood" }.map { it.id }.reversed())
        breakfast.addToCart(shrimp.id)
        breakfast.addToCookingMenu(shrimp.id)
        breakfast.setPurchased(shrimp.id, false)
        breakfast.setCookingCompleted(shrimp.id, false)
        breakfast.transferPurchasedToMenu(shrimp.id)
        breakfast.removeFromCart(shrimp.id)
        breakfast.removeFromCookingMenu(shrimp.id)
        breakfast.deleteItem(riceItems.first().id)
        breakfast.deleteItem(shrimp.id)
        breakfast.deleteGroup(rice.id)
        assertEquals(PurchaseResult.StaleCart, breakfast.completeCookingMeal(requireNotNull(before.getValue("lunch").menuSessionId)))
        assertEquals(before, snapshots())
    }

    @Test
    fun procurementCompletionTotalsAndSessionsStayIndependentAndSurviveReopen() = runBlocking {
        repositories.forEach { (meal, repository) ->
            val shrimp = scopedSeedId(meal, "cooking_shrimp")
            val egg = scopedSeedId(meal, "cooking_egg")
            val potato = scopedSeedId(meal, "cooking_sweet_potato")
            repository.addToCart(shrimp)
            repository.addToCart(potato)
            repository.setPurchased(shrimp, true)
            repository.addToCookingMenu(egg)
            repository.setCookingCompleted(egg, true)
        }
        val before = snapshots()
        assertEquals(5, before.values.mapNotNull { it.cartSessionId }.toSet().size)
        assertEquals(5, before.values.mapNotNull { it.menuSessionId }.toSet().size)
        val breakfast = repositories.getValue("breakfast")
        val shrimp = scopedSeedId("breakfast", "cooking_shrimp")
        val potato = scopedSeedId("breakfast", "cooking_sweet_potato")
        val egg = scopedSeedId("breakfast", "cooking_egg")
        breakfast.setCookingTotal(12)
        breakfast.transferPurchasedToMenu()
        val transferred = breakfast.snapshot()
        assertEquals(listOf(potato), transferred.cart.map { it.itemId })
        assertEquals(listOf(egg, shrimp), transferred.menu.map { it.itemId })
        assertEquals(listOf(true, false), transferred.menu.map { it.completed })
        assertEquals(before.getValue("breakfast").menuSessionId, transferred.menuSessionId)
        assertEquals(PurchaseResult.Incomplete, breakfast.completeCookingMeal(requireNotNull(transferred.menuSessionId)))
        breakfast.setCookingCompleted(shrimp, true)
        assertEquals(PurchaseResult.Completed, breakfast.completeCookingMeal(requireNotNull(transferred.menuSessionId)))
        val completed = breakfast.snapshot()
        assertEquals(13L, completed.cookingTotal)
        assertEquals(1L, completed.items.single { it.id == egg }.count)
        assertEquals(1L, completed.items.single { it.id == shrimp }.count)
        assertEquals(0L, completed.items.single { it.id == potato }.count)
        assertTrue(completed.menu.isEmpty())
        assertNull(completed.menuSessionId)
        assertEquals(transferred.cart, completed.cart)
        assertEquals(transferred.cartSessionId, completed.cartSessionId)
        assertEquals(PurchaseResult.StaleCart, breakfast.completeCookingMeal(requireNotNull(transferred.menuSessionId)))
        MEAL_IDS.filter { it != "breakfast" }.forEach { assertEquals(before.getValue(it), repositories.getValue(it).snapshot()) }
        val persisted = snapshots()
        reopen()
        assertEquals(persisted, snapshots())
        val lunch = repositories.getValue("lunch")
        lunch.transferPurchasedToMenu(scopedSeedId("lunch", "cooking_shrimp"))
        lunch.setCookingCompleted(scopedSeedId("lunch", "cooking_shrimp"), true)
        assertEquals(PurchaseResult.Completed, lunch.completeCookingMeal(requireNotNull(lunch.snapshot().menuSessionId)))
        assertEquals(1L, lunch.snapshot().cookingTotal)
        assertEquals(completed, repositories.getValue("breakfast").snapshot())
    }

    @Test
    fun deletingCookingGroupClearsOnlyItsOwnListsAndDoesNotReseedAnEmptyMeal() = runBlocking {
        repositories.forEach { (meal, repository) ->
            val shrimp = scopedSeedId(meal, "cooking_shrimp")
            repository.addToCart(shrimp)
            repository.addToCookingMenu(shrimp)
            repository.setCookingCompleted(shrimp, true)
            repository.setCookingTotal(3)
        }
        val before = snapshots()
        val breakfast = repositories.getValue("breakfast")
        breakfast.deleteGroup(scopedSeedId("breakfast", "cooking_protein"))
        val deleted = breakfast.snapshot()
        assertTrue(deleted.cart.isEmpty())
        assertTrue(deleted.menu.isEmpty())
        assertNull(deleted.cartSessionId)
        assertNull(deleted.menuSessionId)
        assertEquals(3L, deleted.cookingTotal)
        deleted.groups.forEach { breakfast.deleteGroup(it.id) }
        repeat(2) { repositories.values.forEach { it.initialize() } }
        reopen()
        val empty = repositories.getValue("breakfast").snapshot()
        assertTrue(empty.groups.isEmpty())
        assertTrue(empty.items.isEmpty())
        assertEquals(3L, empty.cookingTotal)
        MEAL_IDS.filter { it != "breakfast" }.forEach { assertEquals(before.getValue(it), repositories.getValue(it).snapshot()) }
    }

    @Test
    fun observedStatesUseTheirMealScopeAndConcurrentPicksDoNotBleedAcrossMeals() = runBlocking {
        val before = snapshots()
        coroutineScope {
            MEAL_IDS.mapIndexed { index, meal ->
                async(Dispatchers.IO) {
                    repeat(index + 1) { repositories.getValue(meal).pick(scopedSeedId(meal, "dining-01-item-01")) }
                }
            }.forEach { it.await() }
        }
        MEAL_IDS.forEachIndexed { index, meal ->
            val state = withTimeout(5_000) { repositories.getValue(meal).state.first() }
            assertEquals(meal, state.mealId)
            assertEquals(before.getValue(meal).groups, state.groups)
            assertEquals((index + 1).toLong(), state.items.single { it.id == scopedSeedId(meal, "dining-01-item-01") }.count)
            assertTrue(state.items.all { item -> state.groups.any { it.id == item.groupId && it.mealId == meal } })
        }
    }
}
