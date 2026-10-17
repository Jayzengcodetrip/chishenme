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
import java.util.concurrent.ConcurrentLinkedQueue

@RunWith(AndroidJUnit4::class)
class CookingRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: MealDatabase
    private lateinit var repository: MealRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "cooking-test-${UUID.randomUUID()}.db"
        openDatabase()
        repository.initialize()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, MealDatabase::class.java, databaseName)
            .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4).build()
        repository = MealRepository(database, context.assets)
    }

    private suspend fun reopen() {
        database.close()
        openDatabase()
        repository.initialize()
    }

    private suspend fun cookingItem(name: String): FoodItem {
        val state = repository.snapshot()
        val groupIds = state.groups.filter { it.scene == "cooking" }.map { it.id }.toSet()
        return state.items.single { it.groupId in groupIds && it.name == name }
    }

    private suspend fun readyMenu(vararg names: String): String {
        names.forEach { name ->
            val item = cookingItem(name)
            repository.addToCookingMenu(item.id)
            repository.setCookingCompleted(item.id, true)
        }
        return requireNotNull(repository.snapshot().menuSessionId)
    }

    @Test
    fun cookingSeedsHaveExactNamesOrderEmptyVegetablesAndNeverReturnAfterDeletion() = runBlocking {
        val state = repository.snapshot()
        val groups = state.groups.filter { it.scene == "cooking" }
        assertEquals(listOf("主食", "蛋白质", "蔬菜"), groups.map { it.name })
        assertEquals(listOf("红薯", "贝贝南瓜"), state.items.filter { it.groupId == groups[0].id }.map { it.name })
        assertEquals(listOf("虾", "鸡蛋", "鸡肉", "牛肉"), state.items.filter { it.groupId == groups[1].id }.map { it.name })
        assertFalse(state.items.any { it.groupId == groups[2].id })
        assertEquals(0L, state.cookingTotal)
        assertTrue(state.cart.isEmpty())
        groups.forEach { repository.deleteGroup(it.id) }
        reopen()
        assertFalse(repository.snapshot().groups.any { it.scene == "cooking" })
        assertEquals(61, repository.snapshot().items.size)
    }

    @Test
    fun cartIsSharedDeduplicatedAndPersistsChecksNamesOrderAndLanguage() = runBlocking {
        val shrimp = cookingItem("虾")
        val potato = cookingItem("红薯")
        repository.addToCart(shrimp.id)
        coroutineScope { repeat(25) { launch(Dispatchers.IO) { repository.addToCart(potato.id) } } }
        repository.setPurchased(shrimp.id, true)
        repository.addToCart(shrimp.id)
        repository.setLanguage("en")
        repository.updateItem(shrimp.id, "虾 / Shrimp", "自己填的备注")
        val before = repository.snapshot()
        assertEquals(listOf(shrimp.id, potato.id), before.cart.map { it.itemId })
        assertTrue(before.cart.first().purchased)
        assertFalse(before.cart.last().purchased)
        assertEquals(0L, before.cookingTotal)
        assertTrue(before.items.all { it.count == 0L })
        reopen()
        val after = repository.snapshot()
        assertEquals(before.cart, after.cart)
        assertEquals(before.cartSessionId, after.cartSessionId)
        assertEquals("en", after.language)
        assertEquals("虾 / Shrimp", after.items.single { it.id == shrimp.id }.name)
        repository.setPurchased(shrimp.id, false)
        assertFalse(repository.snapshot().cart.first().purchased)
    }

    @Test
    fun directMenuAddsAreDeduplicatedAndIndependentOfShoppingAndPersistCompletion() = runBlocking {
        val shrimp = cookingItem("虾")
        val egg = cookingItem("鸡蛋")
        repository.addToCart(shrimp.id)
        repository.addToCookingMenu(egg.id)
        repository.setCookingCompleted(egg.id, true)
        coroutineScope { repeat(25) { launch(Dispatchers.IO) { repository.addToCookingMenu(shrimp.id) } } }
        repository.addToCookingMenu(egg.id)
        repository.updateItem(egg.id, "鸡蛋 / Egg", "备注保留")
        val before = repository.snapshot()
        assertEquals(listOf(egg.id, shrimp.id), before.menu.map { it.itemId })
        assertEquals(listOf(true, false), before.menu.map { it.completed })
        assertEquals(listOf(shrimp.id), before.cart.map { it.itemId })
        assertFalse(before.cart.single().purchased)
        assertEquals(0L, before.cookingTotal)
        assertTrue(before.items.all { it.count == 0L })
        reopen()
        assertEquals(before, repository.snapshot())
        repository.setCookingCompleted(egg.id, false)
        assertFalse(repository.snapshot().menu.first().completed)
        repository.removeFromCookingMenu(shrimp.id)
        assertEquals(before.cart, repository.snapshot().cart)
        assertNotNull(repository.snapshot().items.singleOrNull { it.id == shrimp.id })
        repository.removeFromCart(shrimp.id)
        assertEquals(listOf(egg.id), repository.snapshot().menu.map { it.itemId })
    }

    @Test
    fun transferMovesOnlyPurchasedItemsAndPreservesExistingMenuCompletionAndOrder() = runBlocking {
        val shrimp = cookingItem("虾")
        val egg = cookingItem("鸡蛋")
        val potato = cookingItem("红薯")
        listOf(shrimp, egg, potato).forEach { repository.addToCart(it.id) }
        repository.addToCookingMenu(egg.id)
        repository.setCookingCompleted(egg.id, true)
        val menuSession = repository.snapshot().menuSessionId
        repository.setPurchased(egg.id, true)
        repository.setPurchased(shrimp.id, true)
        repository.transferPurchasedToMenu(potato.id)
        assertEquals(3, repository.snapshot().cart.size)
        repository.transferPurchasedToMenu(egg.id)
        val afterSingle = repository.snapshot()
        assertEquals(listOf(egg.id), afterSingle.menu.map { it.itemId })
        assertTrue(afterSingle.menu.single().completed)
        assertEquals(listOf(shrimp.id, potato.id), afterSingle.cart.map { it.itemId })
        repository.transferPurchasedToMenu()
        val afterBulk = repository.snapshot()
        assertEquals(listOf(egg.id, shrimp.id), afterBulk.menu.map { it.itemId })
        assertEquals(listOf(true, false), afterBulk.menu.map { it.completed })
        assertEquals(menuSession, afterBulk.menuSessionId)
        assertEquals(listOf(potato.id), afterBulk.cart.map { it.itemId })
        assertFalse(afterBulk.cart.single().purchased)
        repository.transferPurchasedToMenu()
        assertEquals(afterBulk, repository.snapshot())
        assertEquals(0L, afterBulk.cookingTotal)
        assertTrue(afterBulk.items.all { it.count == 0L })
    }

    @Test
    fun removingPurchasesAndUndoingChecksDoNotAffectMenuOrCounts() = runBlocking {
        val shrimp = cookingItem("虾")
        repository.addToCart(shrimp.id)
        repository.addToCookingMenu(shrimp.id)
        repository.setPurchased(shrimp.id, true)
        repository.setPurchased(shrimp.id, false)
        val beforeTransfer = repository.snapshot()
        assertFalse(beforeTransfer.cart.single().purchased)
        repository.transferPurchasedToMenu(shrimp.id)
        assertEquals(beforeTransfer, repository.snapshot())
        repository.removeFromCart(shrimp.id)
        assertNull(repository.snapshot().cartSessionId)
        assertEquals(beforeTransfer.menu, repository.snapshot().menu)
        assertEquals(0L, cookingItem("虾").count)
    }

    @Test
    fun completingMenuLeavesPendingAndPurchasedCartEntriesExactlyAsTheyWere() = runBlocking {
        val shrimp = cookingItem("虾")
        val potato = cookingItem("红薯")
        repository.addToCart(shrimp.id)
        repository.addToCart(potato.id)
        repository.setPurchased(shrimp.id, true)
        val session = readyMenu("虾", "鸡蛋")
        val before = repository.snapshot()
        assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(session))
        val after = repository.snapshot()
        assertEquals(before.cart, after.cart)
        assertEquals(before.cartSessionId, after.cartSessionId)
        assertTrue(after.menu.isEmpty())
        assertNull(after.menuSessionId)
        assertEquals(1L, after.cookingTotal)
        assertEquals(1L, cookingItem("虾").count)
        assertEquals(1L, cookingItem("鸡蛋").count)
        assertEquals(0L, cookingItem("红薯").count)
    }

    @Test
    fun sameNamesAcrossGroupsHaveIndependentCartAndMenuIdentityAndCounts() = runBlocking {
        val shrimp = cookingItem("虾")
        val otherGroup = repository.snapshot().groups.single { it.scene == "cooking" && it.name == "蔬菜" }
        repository.addItem(otherGroup.id, "虾", "同名但独立")
        val second = repository.snapshot().items.single { it.groupId == otherGroup.id && it.name == "虾" }
        listOf(shrimp, second).forEach {
            repository.addToCart(it.id)
            repository.setPurchased(it.id, true)
        }
        repository.transferPurchasedToMenu()
        assertEquals(listOf(shrimp.id, second.id), repository.snapshot().menu.map { it.itemId })
        repository.setCookingCompleted(shrimp.id, true)
        repository.removeFromCookingMenu(second.id)
        assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(requireNotNull(repository.snapshot().menuSessionId)))
        val after = repository.snapshot()
        assertEquals(1L, after.items.single { it.id == shrimp.id }.count)
        assertEquals(0L, after.items.single { it.id == second.id }.count)
    }

    @Test
    fun transferFailureRollsBackBothListsAndRetryMovesEveryPurchasedItemOnce() = runBlocking {
        val shrimp = cookingItem("虾")
        val egg = cookingItem("鸡蛋")
        listOf(shrimp, egg).forEach {
            repository.addToCart(it.id)
            repository.setPurchased(it.id, true)
        }
        val before = repository.snapshot()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_transfer BEFORE DELETE ON shopping_cart
            WHEN OLD.itemId = '${egg.id}'
            BEGIN SELECT RAISE(ABORT, 'test transfer rollback'); END
        """.trimIndent())
        try {
            assertTrue(runCatching { repository.transferPurchasedToMenu() }.isFailure)
            assertEquals(before, repository.snapshot())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_transfer")
        }
        coroutineScope { repeat(20) { launch(Dispatchers.IO) { repository.transferPurchasedToMenu() } } }
        val after = repository.snapshot()
        assertEquals(listOf(shrimp.id, egg.id), after.menu.map { it.itemId })
        assertTrue(after.cart.isEmpty())
        assertNull(after.cartSessionId)
        assertEquals(0L, after.cookingTotal)
    }

    @Test
    fun cookingPickAndNonCookingCartCallsCannotCrossCountSemantics() = runBlocking {
        val shrimp = cookingItem("虾")
        val before = repository.snapshot()
        val takeoutGroup = before.groups.first { it.scene == "takeout" }
        val takeaway = before.items.first { it.groupId == takeoutGroup.id }
        repository.pick(shrimp.id)
        repository.addToCart(takeaway.id)
        repository.addToCookingMenu(takeaway.id)
        assertTrue(repository.snapshot().cart.isEmpty())
        assertTrue(repository.snapshot().menu.isEmpty())
        assertNull(repository.snapshot().cartSessionId)
        assertEquals(0L, cookingItem("虾").count)
        repository.pick(takeaway.id)
        readyMenu("虾", "鸡蛋", "红薯").let { assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(it)) }
        val after = repository.snapshot()
        assertEquals(1L, after.items.single { it.id == takeaway.id }.count)
        assertEquals(1L, after.cookingTotal)
        assertEquals(1L, cookingItem("虾").count)
        assertEquals(1L, cookingItem("鸡蛋").count)
        assertEquals(1L, cookingItem("红薯").count)
    }

    @Test
    fun incompleteAndEmptyMenusCannotCompleteAndConcurrentConfirmationCountsOnlyOnce() = runBlocking {
        assertEquals(PurchaseResult.StaleCart, repository.completeCookingMeal("missing"))
        val shrimp = cookingItem("虾")
        val egg = cookingItem("鸡蛋")
        repository.addToCookingMenu(shrimp.id)
        repository.addToCookingMenu(egg.id)
        repository.setCookingCompleted(shrimp.id, true)
        val session = requireNotNull(repository.snapshot().menuSessionId)
        assertEquals(PurchaseResult.Incomplete, repository.completeCookingMeal(session))
        assertEquals(0L, repository.snapshot().cookingTotal)
        assertEquals(0L, cookingItem("虾").count)
        repository.setCookingCompleted(egg.id, true)
        val results = ConcurrentLinkedQueue<PurchaseResult>()
        coroutineScope {
            repeat(20) { launch(Dispatchers.IO) { results.add(repository.completeCookingMeal(session)) } }
        }
        assertEquals(1, results.count { it == PurchaseResult.Completed })
        assertEquals(19, results.count { it == PurchaseResult.StaleCart })
        reopen()
        val after = repository.snapshot()
        assertEquals(1L, after.cookingTotal)
        assertTrue(after.menu.isEmpty())
        assertNull(after.menuSessionId)
        assertEquals(1L, cookingItem("虾").count)
        assertEquals(1L, cookingItem("鸡蛋").count)
    }

    @Test
    fun delayedConfirmationCannotCompleteANewMenuEvenWithIdenticalIngredients() = runBlocking {
        val firstSession = readyMenu("虾")
        assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(firstSession))
        val secondSession = readyMenu("虾")
        assertNotEquals(firstSession, secondSession)
        assertEquals(PurchaseResult.StaleCart, repository.completeCookingMeal(firstSession))
        assertEquals(1, repository.snapshot().menu.size)
        assertEquals(1L, repository.snapshot().cookingTotal)
        assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(secondSession))
        assertEquals(2L, repository.snapshot().cookingTotal)
        assertEquals(2L, cookingItem("虾").count)
    }

    @Test
    fun removeAndCascadeDeleteInvalidateEmptyListsWithoutReducingCookingTotal() = runBlocking {
        val session = readyMenu("虾", "红薯")
        assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(session))
        repository.setCookingTotal(7)
        val shrimp = cookingItem("虾")
        val potato = cookingItem("红薯")
        repository.addToCart(shrimp.id)
        repository.addToCart(potato.id)
        repository.addToCookingMenu(shrimp.id)
        repository.addToCookingMenu(potato.id)
        repository.deleteItem(shrimp.id)
        assertEquals(listOf(potato.id), repository.snapshot().cart.map { it.itemId })
        assertEquals(listOf(potato.id), repository.snapshot().menu.map { it.itemId })
        assertEquals(7L, repository.snapshot().cookingTotal)
        repository.deleteGroup(potato.groupId)
        assertTrue(repository.snapshot().cart.isEmpty())
        assertNull(repository.snapshot().cartSessionId)
        assertTrue(repository.snapshot().menu.isEmpty())
        assertNull(repository.snapshot().menuSessionId)
        assertEquals(7L, repository.snapshot().cookingTotal)
        val egg = cookingItem("鸡蛋")
        repository.addToCart(egg.id)
        repository.addToCookingMenu(egg.id)
        assertNotNull(repository.snapshot().cartSessionId)
        val oldMenuSession = requireNotNull(repository.snapshot().menuSessionId)
        repository.removeFromCart(egg.id)
        assertNull(repository.snapshot().cartSessionId)
        repository.removeFromCookingMenu(egg.id)
        assertNull(repository.snapshot().menuSessionId)
        repository.addToCookingMenu(egg.id)
        assertNotEquals(oldMenuSession, repository.snapshot().menuSessionId)
        assertEquals(PurchaseResult.StaleCart, repository.completeCookingMeal(oldMenuSession))
        reopen()
        assertEquals(7L, repository.snapshot().cookingTotal)
        assertFalse(repository.snapshot().items.any { it.id == shrimp.id || it.id == potato.id })
    }

    @Test
    fun manualTotalAndIngredientCountsRemainIndependentAcrossRenameAndReorder() = runBlocking {
        val state = repository.snapshot()
        val groups = state.groups.filter { it.scene == "cooking" }
        val protein = groups.single { it.name == "蛋白质" }
        val original = state.items.filter { it.groupId == protein.id }
        repository.setCount(original[0].id, 9)
        assertEquals(0L, repository.snapshot().cookingTotal)
        repository.setCookingTotal(3)
        assertEquals(9L, cookingItem("虾").count)
        repository.reorderGroups("cooking", "cooking", groups.map { it.id }.reversed())
        repository.reorderItems(protein.id, original.map { it.id }.reversed())
        repository.renameGroup(protein.id, "Protein / 蛋白质")
        repository.updateItem(original[0].id, "Shrimp / 虾", "鲜虾")
        reopen()
        val after = repository.snapshot()
        assertEquals(groups.map { it.id }.reversed(), after.groups.filter { it.scene == "cooking" }.map { it.id })
        assertEquals(original.map { it.id }.reversed(), after.items.filter { it.groupId == protein.id }.map { it.id })
        assertEquals(9L, after.items.single { it.id == original[0].id }.count)
        assertEquals(3L, after.cookingTotal)
        assertEquals("Protein / 蛋白质", after.groups.single { it.id == protein.id }.name)
        repository.reorderGroups("cooking", "cooking", listOf(protein.id))
        assertEquals(after.groups, repository.snapshot().groups)
        val foreignId = after.groups.first { it.scene != "cooking" }.id
        repository.reorderGroups("cooking", "cooking", listOf(foreignId) + groups.drop(1).map { it.id })
        assertEquals(after.groups, repository.snapshot().groups)
        repository.addGroup("cooking", "cooking", "其他")
        val addedGroup = repository.snapshot().groups.single { it.name == "其他" && it.scene == "cooking" }
        assertEquals(3, addedGroup.position)
        repository.addItem(addedGroup.id, "豆腐", "")
        assertEquals(0L, repository.snapshot().items.single { it.groupId == addedGroup.id }.count)
        assertTrue(runCatching { repository.setCookingTotal(-1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.setCookingTotal(MAX_PICK_COUNT + 1) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(3L, repository.snapshot().cookingTotal)
    }

    @Test
    fun limitFailureLeavesEveryCounterMenuCartAndSessionUnchanged() = runBlocking {
        val session = readyMenu("虾", "鸡蛋")
        repository.setCount(cookingItem("鸡蛋").id, MAX_PICK_COUNT)
        val before = repository.snapshot()
        assertEquals(PurchaseResult.LimitReached, repository.completeCookingMeal(session))
        assertEquals(before, repository.snapshot())
        repository.setCount(cookingItem("鸡蛋").id, 0)
        repository.setCookingTotal(MAX_PICK_COUNT)
        val totalAtLimit = repository.snapshot()
        assertEquals(PurchaseResult.LimitReached, repository.completeCookingMeal(session))
        assertEquals(totalAtLimit, repository.snapshot())
    }

    @Test
    fun databaseFailureDuringCompletionRollsBackIngredientIncrementsAndRetainsMenu() = runBlocking {
        val session = readyMenu("虾", "鸡蛋")
        val before = repository.snapshot()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_cooking_total BEFORE INSERT ON app_settings
            WHEN NEW.key = 'cooking_total'
            BEGIN SELECT RAISE(ABORT, 'test completion rollback'); END
        """.trimIndent())
        try {
            assertTrue(runCatching { repository.completeCookingMeal(session) }.isFailure)
            assertEquals(before, repository.snapshot())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_cooking_total")
        }
        assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(session))
        assertEquals(1L, repository.snapshot().cookingTotal)
    }
}
