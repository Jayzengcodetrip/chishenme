package com.mealpick.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CookingToggleTest {
    private lateinit var context: Context
    private lateinit var database: MealDatabase
    private lateinit var repositories: Map<String, MealRepository>
    private lateinit var databaseName: String
    private val dinner get() = repositories.getValue(DEFAULT_MEAL_ID)

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "cooking-toggle-${UUID.randomUUID()}.db"
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
            .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4)
            .build()
        repositories = MEAL_IDS.associateWith { MealRepository(database, context.assets, it) }
    }

    private suspend fun snapshots(): Map<String, MealState> = repositories.mapValues { it.value.snapshot() }

    @Test
    fun cartToggleResetsPurchasedReappendsAndPreservesMenuCountsAndPersistence() = runBlocking {
        val shrimp = "cooking_shrimp"
        val egg = "cooking_egg"
        dinner.setCount(shrimp, 7)
        dinner.setCookingTotal(4)
        dinner.addToCookingMenu(shrimp)
        dinner.setCookingCompleted(shrimp, true)
        dinner.toggleCart(shrimp)
        dinner.toggleCart(egg)
        dinner.setPurchased(shrimp, true)
        val before = dinner.snapshot()
        assertTrue(before.cart.first().purchased)

        dinner.toggleCart(shrimp)
        val removed = dinner.snapshot()
        assertEquals(listOf(egg), removed.cart.map { it.itemId })
        assertEquals(before.cartSessionId, removed.cartSessionId)
        assertEquals(before.menu, removed.menu)
        assertEquals(before.menuSessionId, removed.menuSessionId)
        assertEquals(before.items, removed.items)
        assertEquals(before.cookingTotal, removed.cookingTotal)

        dinner.toggleCart(shrimp)
        val readded = dinner.snapshot()
        assertEquals(listOf(egg, shrimp), readded.cart.map { it.itemId })
        assertFalse(readded.cart.last().purchased)
        assertTrue(readded.cart.last().position > readded.cart.first().position)
        assertEquals(before.cartSessionId, readded.cartSessionId)

        dinner.toggleCart(egg)
        dinner.toggleCart(shrimp)
        assertTrue(dinner.snapshot().cart.isEmpty())
        assertNull(dinner.snapshot().cartSessionId)
        dinner.toggleCart(shrimp)
        val restarted = dinner.snapshot()
        assertEquals(listOf(CartEntry(shrimp, 0, false)), restarted.cart)
        assertNotNull(restarted.cartSessionId)
        assertNotEquals(before.cartSessionId, restarted.cartSessionId)
        assertEquals(before.menu, restarted.menu)
        assertEquals(before.menuSessionId, restarted.menuSessionId)
        assertEquals(before.items, restarted.items)
        assertEquals(before.cookingTotal, restarted.cookingTotal)

        database.close()
        openDatabase()
        dinner.initialize()
        assertEquals(restarted, dinner.snapshot())
    }

    @Test
    fun menuToggleResetsCompletedReappendsAndPreservesCartAndCounts() = runBlocking {
        val shrimp = "cooking_shrimp"
        val egg = "cooking_egg"
        dinner.setCount(shrimp, 8)
        dinner.setCookingTotal(6)
        dinner.addToCart(shrimp)
        dinner.setPurchased(shrimp, true)
        dinner.toggleCookingMenu(shrimp)
        dinner.toggleCookingMenu(egg)
        dinner.setCookingCompleted(shrimp, true)
        val before = dinner.snapshot()
        assertTrue(before.menu.first().completed)

        dinner.toggleCookingMenu(shrimp)
        val removed = dinner.snapshot()
        assertEquals(listOf(egg), removed.menu.map { it.itemId })
        assertEquals(before.menuSessionId, removed.menuSessionId)
        assertEquals(before.cart, removed.cart)
        assertEquals(before.cartSessionId, removed.cartSessionId)
        assertEquals(before.items, removed.items)
        assertEquals(before.cookingTotal, removed.cookingTotal)

        dinner.toggleCookingMenu(shrimp)
        val readded = dinner.snapshot()
        assertEquals(listOf(egg, shrimp), readded.menu.map { it.itemId })
        assertFalse(readded.menu.last().completed)
        assertTrue(readded.menu.last().position > readded.menu.first().position)
        assertEquals(before.menuSessionId, readded.menuSessionId)

        dinner.toggleCookingMenu(egg)
        dinner.toggleCookingMenu(shrimp)
        assertTrue(dinner.snapshot().menu.isEmpty())
        assertNull(dinner.snapshot().menuSessionId)
        dinner.toggleCookingMenu(shrimp)
        val restarted = dinner.snapshot()
        assertEquals(listOf(CookingMenuEntry(shrimp, 0, false)), restarted.menu)
        assertNotNull(restarted.menuSessionId)
        assertNotEquals(before.menuSessionId, restarted.menuSessionId)
        assertEquals(before.cart, restarted.cart)
        assertEquals(before.cartSessionId, restarted.cartSessionId)
        assertEquals(before.items, restarted.items)
        assertEquals(before.cookingTotal, restarted.cookingTotal)
    }

    @Test
    fun allFiveMealsAndBothListsToggleIndependently() = runBlocking {
        repositories.forEach { (mealId, repository) ->
            val id = scopedSeedId(mealId, "cooking_shrimp")
            repository.toggleCart(id)
            repository.toggleCookingMenu(id)
            repository.setPurchased(id, true)
            repository.setCookingCompleted(id, true)
            repository.setCount(id, 5)
            repository.setCookingTotal(3)
        }
        val initial = snapshots()
        repositories.forEach { (mealId, repository) ->
            val id = scopedSeedId(mealId, "cooking_shrimp")
            val beforeCart = snapshots()
            repository.toggleCart(id)
            val afterCart = snapshots()
            beforeCart.filterKeys { it != mealId }.forEach { (otherMeal, state) ->
                assertEquals("Cart toggle must not affect $otherMeal", state, afterCart.getValue(otherMeal))
            }
            assertTrue(afterCart.getValue(mealId).cart.isEmpty())
            assertEquals(initial.getValue(mealId).menu, afterCart.getValue(mealId).menu)
            assertEquals(initial.getValue(mealId).menuSessionId, afterCart.getValue(mealId).menuSessionId)
            repository.toggleCart(id)
            repository.setPurchased(id, true)

            val beforeMenu = snapshots()
            repository.toggleCookingMenu(id)
            val afterMenu = snapshots()
            beforeMenu.filterKeys { it != mealId }.forEach { (otherMeal, state) ->
                assertEquals("Menu toggle must not affect $otherMeal", state, afterMenu.getValue(otherMeal))
            }
            assertTrue(afterMenu.getValue(mealId).menu.isEmpty())
            assertEquals(beforeMenu.getValue(mealId).cart, afterMenu.getValue(mealId).cart)
            assertEquals(beforeMenu.getValue(mealId).cartSessionId, afterMenu.getValue(mealId).cartSessionId)
            repository.toggleCookingMenu(id)
            repository.setCookingCompleted(id, true)

            val restored = repository.snapshot()
            assertEquals(initial.getValue(mealId).items, restored.items)
            assertEquals(3L, restored.cookingTotal)
        }
    }

    @Test
    fun foreignMissingDeletedAndNonCookingIdsCannotToggleAnyList() = runBlocking {
        val breakfast = repositories.getValue("breakfast")
        val foreignId = scopedSeedId("lunch", "cooking_shrimp")
        val lunch = repositories.getValue("lunch")
        lunch.addToCart(foreignId)
        lunch.addToCookingMenu(foreignId)
        lunch.setPurchased(foreignId, true)
        lunch.setCookingCompleted(foreignId, true)
        val deletedId = scopedSeedId("breakfast", "cooking_egg")
        breakfast.deleteItem(deletedId)
        val state = breakfast.snapshot()
        val nonCookingGroupIds = state.groups.filter { it.scene != "cooking" }.map { it.id }.toSet()
        val nonCookingId = state.items.first { it.groupId in nonCookingGroupIds }.id
        val before = snapshots()
        listOf(foreignId, nonCookingId, deletedId, "missing", "").forEach { id ->
            repeat(2) {
                breakfast.toggleCart(id)
                breakfast.toggleCookingMenu(id)
            }
        }
        assertEquals(before, snapshots())
    }

    @Test
    fun rapidConcurrentAndSequentialTogglesFollowParityWithoutDuplicateEntriesOrCounts() = runBlocking {
        val id = "cooking_shrimp"
        val before = dinner.snapshot()
        coroutineScope {
            repeat(51) { launch(Dispatchers.IO) { dinner.toggleCart(id) } }
            repeat(50) { launch(Dispatchers.IO) { dinner.toggleCookingMenu(id) } }
        }
        val first = dinner.snapshot()
        assertEquals(listOf(CartEntry(id, 0, false)), first.cart)
        assertNotNull(first.cartSessionId)
        assertTrue(first.menu.isEmpty())
        assertNull(first.menuSessionId)

        coroutineScope {
            repeat(51) { launch(Dispatchers.IO) { dinner.toggleCart(id) } }
            repeat(51) { launch(Dispatchers.IO) { dinner.toggleCookingMenu(id) } }
        }
        val second = dinner.snapshot()
        assertTrue(second.cart.isEmpty())
        assertNull(second.cartSessionId)
        assertEquals(listOf(CookingMenuEntry(id, 0, false)), second.menu)
        assertNotNull(second.menuSessionId)
        repeat(20) {
            dinner.toggleCart(id)
            dinner.toggleCookingMenu(id)
        }
        val third = dinner.snapshot()
        assertTrue(third.cart.isEmpty())
        assertNull(third.cartSessionId)
        assertEquals(second.menu, third.menu)
        assertNotEquals(second.menuSessionId, third.menuSessionId)
        assertEquals(before.items, third.items)
        assertEquals(before.cookingTotal, third.cookingTotal)
    }

    @Test
    fun cancellingLastMenuItemInvalidatesConfirmationAndReaddingStartsANewSession() = runBlocking {
        val id = "cooking_shrimp"
        dinner.toggleCookingMenu(id)
        dinner.setCookingCompleted(id, true)
        val oldSession = requireNotNull(dinner.snapshot().menuSessionId)
        dinner.toggleCookingMenu(id)
        assertEquals(PurchaseResult.StaleCart, dinner.completeCookingMeal(oldSession))
        dinner.toggleCookingMenu(id)
        val fresh = dinner.snapshot()
        val newSession = requireNotNull(fresh.menuSessionId)
        assertNotEquals(oldSession, newSession)
        assertFalse(fresh.menu.single().completed)
        assertEquals(PurchaseResult.StaleCart, dinner.completeCookingMeal(oldSession))
        assertEquals(PurchaseResult.Incomplete, dinner.completeCookingMeal(newSession))
        dinner.setCookingCompleted(id, true)
        assertEquals(PurchaseResult.StaleCart, dinner.completeCookingMeal(oldSession))
        assertEquals(0L, dinner.snapshot().items.single { it.id == id }.count)
        assertEquals(0L, dinner.snapshot().cookingTotal)
        assertEquals(PurchaseResult.Completed, dinner.completeCookingMeal(newSession))
        assertEquals(1L, dinner.snapshot().items.single { it.id == id }.count)
        assertEquals(1L, dinner.snapshot().cookingTotal)
        assertTrue(dinner.snapshot().menu.isEmpty())
        assertNull(dinner.snapshot().menuSessionId)
    }
}
