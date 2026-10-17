package com.mealpick.app.data

import android.content.res.AssetManager
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class MealRepository(
    private val database: MealDatabase,
    private val assets: AssetManager,
    val mealId: String = DEFAULT_MEAL_ID,
) {
    init {
        require(mealId in MEAL_IDS) { "Unknown meal: $mealId" }
    }

    private val dao = database.meals()
    private fun settingKey(key: String): String = scopedSeedId(mealId, key)

    val state: Flow<MealState> = combine(
        dao.observeGroups(), dao.observeItems(), dao.observeSettings(), dao.observeCart(), dao.observeMenu(),
    ) { _, _, _, _, _ -> Unit }.map {
        // Read one transactionally consistent snapshot of both lists and completion counters.
        snapshot()
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        database.withTransaction {
            // The marker survives deletion of every group. Never infer first launch from empty lists.
            if (dao.getSetting(settingKey("seed_version")) == null) {
                val root = assets.open("seed.json").bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
                val groupJson = root.getJSONArray("groups")
                val groups = List(groupJson.length()) { index ->
                    val row = groupJson.getJSONObject(index)
                    FoodGroup(
                        id = scopedSeedId(mealId, row.getString("id")),
                        scene = row.getString("scene"),
                        zone = row.getString("zone"),
                        name = row.getString("name"),
                        position = row.getInt("position"),
                        systemKey = if (row.has("systemKey")) row.getString("systemKey") else null,
                        mealId = mealId,
                    )
                }
                val itemJson = root.getJSONArray("items")
                val items = List(itemJson.length()) { index ->
                    val row = itemJson.getJSONObject(index)
                    FoodItem(
                        id = scopedSeedId(mealId, row.getString("id")),
                        groupId = scopedSeedId(mealId, row.getString("groupId")),
                        name = row.getString("name"),
                        notes = row.getString("notes"),
                        count = 0,
                        position = row.getInt("position"),
                    )
                }
                dao.insertGroups(groups)
                dao.insertItems(items)
                dao.putSetting(AppSetting(settingKey("seed_version"), root.getInt("version").toString()))
            }
            // Language is global: initializing another meal must not reset the user's choice.
            if (dao.getSetting("language") == null) dao.putSetting(AppSetting("language", "zh"))
            // Feature seeds have their own durable marker. Upgrades never rerun the original import.
            if (dao.getSetting(settingKey("cooking_seed_version")) == null) {
                val groups = listOf(
                    FoodGroup("cooking_staples", "cooking", "cooking", "主食", 0),
                    FoodGroup("cooking_protein", "cooking", "cooking", "蛋白质", 1),
                    FoodGroup("cooking_vegetables", "cooking", "cooking", "蔬菜", 2),
                ).map { it.copy(id = scopedSeedId(mealId, it.id), mealId = mealId) }
                val items = listOf(
                    FoodItem("cooking_sweet_potato", "cooking_staples", "红薯", position = 0),
                    FoodItem("cooking_beibei_pumpkin", "cooking_staples", "贝贝南瓜", position = 1),
                    FoodItem("cooking_shrimp", "cooking_protein", "虾", position = 0),
                    FoodItem("cooking_egg", "cooking_protein", "鸡蛋", position = 1),
                    FoodItem("cooking_chicken", "cooking_protein", "鸡肉", position = 2),
                    FoodItem("cooking_beef", "cooking_protein", "牛肉", position = 3),
                ).map { it.copy(id = scopedSeedId(mealId, it.id), groupId = scopedSeedId(mealId, it.groupId)) }
                dao.insertGroups(groups)
                dao.insertItems(items)
                if (dao.getSetting(settingKey(COOKING_TOTAL)) == null) dao.putSetting(AppSetting(settingKey(COOKING_TOTAL), "0"))
                dao.putSetting(AppSetting(settingKey("cooking_seed_version"), "1"))
            }
        }
    }

    suspend fun snapshot(): MealState = database.withTransaction {
        MealState(
            groups = dao.getMealGroups(mealId),
            items = dao.getMealItems(mealId),
            language = dao.getSetting("language") ?: "zh",
            ready = true,
            cart = dao.getMealCart(mealId),
            cookingTotal = dao.getSetting(settingKey(COOKING_TOTAL))?.toLong() ?: 0L,
            cartSessionId = dao.getSetting(settingKey(CART_SESSION)),
            menu = dao.getMealMenu(mealId),
            menuSessionId = dao.getSetting(settingKey(MENU_SESSION)),
            mealId = mealId,
        )
    }

    suspend fun setLanguage(language: String) {
        if (language == "zh" || language == "en") dao.putSetting(AppSetting("language", language))
    }

    suspend fun addGroup(scene: String, zone: String, name: String) {
        if (name.isBlank()) return
        if ((scene to zone) !in setOf("takeout" to "nutrition", "takeout" to "mood", "dining" to "dining", "snacks" to "snacks", "cooking" to "cooking")) return
        database.withTransaction {
            val last = dao.getMealGroups(mealId).filter { it.scene == scene && it.zone == zone }.maxOfOrNull { it.position } ?: -1
            dao.insertGroups(listOf(FoodGroup(UUID.randomUUID().toString(), scene, zone, name.trim(), last + 1, mealId = mealId)))
        }
    }

    suspend fun renameGroup(id: String, name: String) {
        if (name.isBlank()) return
        database.withTransaction {
            if (ownedGroup(id) != null) dao.renameGroup(id, name.trim())
        }
    }

    suspend fun deleteGroup(id: String) = database.withTransaction {
        if (ownedGroup(id) == null) return@withTransaction
        dao.deleteGroup(id)
        clearEmptyListSessions()
    }

    suspend fun addItem(groupId: String, name: String, notes: String) {
        if (name.isBlank()) return
        database.withTransaction {
            if (ownedGroup(groupId) == null) return@withTransaction
            val last = dao.getGroupItems(groupId).maxOfOrNull { it.position } ?: -1
            dao.insertItems(listOf(FoodItem(UUID.randomUUID().toString(), groupId, name.trim(), notes, 0, last + 1)))
        }
    }

    suspend fun updateItem(id: String, name: String, notes: String) {
        if (name.isBlank()) return
        database.withTransaction {
            if (ownedItem(id) != null) dao.updateItem(id, name.trim(), notes)
        }
    }

    suspend fun setCount(id: String, count: Long) {
        require(count in 0L..MAX_PICK_COUNT) { "Count must be between 0 and $MAX_PICK_COUNT." }
        database.withTransaction {
            if (ownedItem(id) != null) dao.setCount(id, count)
        }
    }

    suspend fun pick(id: String) = database.withTransaction {
        if (ownedItem(id) != null) dao.pick(id)
    }

    suspend fun deleteItem(id: String) = database.withTransaction {
        if (ownedItem(id) == null) return@withTransaction
        dao.deleteItem(id)
        clearEmptyListSessions()
    }

    suspend fun reorderItems(groupId: String, orderedIds: List<String>) {
        database.withTransaction {
            if (ownedGroup(groupId) == null) return@withTransaction
            val current = dao.getGroupItems(groupId)
            // A stale or partial drag must never silently drop or duplicate an item.
            if (orderedIds.size != current.size || orderedIds.toSet() != current.map { it.id }.toSet()) return@withTransaction
            orderedIds.forEachIndexed { index, id -> dao.setItemPosition(groupId, id, index) }
        }
    }

    suspend fun reorderGroups(scene: String, zone: String, orderedIds: List<String>) {
        database.withTransaction {
            val current = dao.getMealGroups(mealId).filter { it.scene == scene && it.zone == zone }
            if (orderedIds.size != current.size || orderedIds.toSet() != current.map { it.id }.toSet()) return@withTransaction
            orderedIds.forEachIndexed { index, id -> dao.setGroupPosition(scene, zone, id, index) }
        }
    }

    suspend fun addToCart(id: String) = database.withTransaction {
        if (ownedCookingItem(id) == null) return@withTransaction
        appendToCart(id)
    }

    suspend fun toggleCart(id: String) = database.withTransaction {
        if (ownedCookingItem(id) == null) return@withTransaction
        // Read and switch in one transaction: rapid taps each toggle the committed state,
        // rather than repeating a decision based on an older rendered snapshot.
        val cart = dao.getMealCart(mealId)
        if (cart.any { it.itemId == id }) {
            dao.removeFromCart(id)
            if (cart.size == 1) dao.deleteSetting(settingKey(CART_SESSION))
        } else {
            appendToCart(id)
        }
    }

    private suspend fun appendToCart(id: String) {
        val cart = dao.getMealCart(mealId)
        if (cart.any { it.itemId == id }) return
        if (cart.isEmpty() || dao.getSetting(settingKey(CART_SESSION)) == null) {
            dao.putSetting(AppSetting(settingKey(CART_SESSION), UUID.randomUUID().toString()))
        }
        dao.insertCartEntry(CartEntry(id, (cart.maxOfOrNull { it.position } ?: -1) + 1))
    }

    suspend fun removeFromCart(id: String) = database.withTransaction {
        if (ownedItem(id) == null) return@withTransaction
        dao.removeFromCart(id)
        clearEmptyListSessions()
    }

    suspend fun setPurchased(id: String, purchased: Boolean) = database.withTransaction {
        if (ownedCookingItem(id) != null) dao.setPurchased(id, purchased)
    }

    suspend fun addToCookingMenu(id: String) = database.withTransaction {
        if (ownedCookingItem(id) == null) return@withTransaction
        appendToMenu(id)
    }

    suspend fun toggleCookingMenu(id: String) = database.withTransaction {
        if (ownedCookingItem(id) == null) return@withTransaction
        val menu = dao.getMealMenu(mealId)
        if (menu.any { it.itemId == id }) {
            dao.removeFromMenu(id)
            if (menu.size == 1) dao.deleteSetting(settingKey(MENU_SESSION))
        } else {
            appendToMenu(id)
        }
    }

    suspend fun removeFromCookingMenu(id: String) = database.withTransaction {
        if (ownedItem(id) == null) return@withTransaction
        dao.removeFromMenu(id)
        clearEmptyListSessions()
    }

    suspend fun setCookingCompleted(id: String, completed: Boolean) = database.withTransaction {
        if (ownedCookingItem(id) != null) dao.setCookingCompleted(id, completed)
    }

    suspend fun transferPurchasedToMenu(id: String? = null) = database.withTransaction {
        if (id != null && ownedCookingItem(id) == null) return@withTransaction
        val purchased = dao.getMealCart(mealId).filter { it.purchased && (id == null || it.itemId == id) }
        purchased.forEach { entry ->
            if (ownedCookingItem(entry.itemId) == null) return@forEach
            // A duplicate keeps its existing position and completion state in the menu.
            appendToMenu(entry.itemId)
            dao.removeFromCart(entry.itemId)
        }
        clearEmptyListSessions()
    }

    private suspend fun appendToMenu(id: String) {
        val menu = dao.getMealMenu(mealId)
        if (menu.any { it.itemId == id }) return
        if (menu.isEmpty() || dao.getSetting(settingKey(MENU_SESSION)) == null) {
            dao.putSetting(AppSetting(settingKey(MENU_SESSION), UUID.randomUUID().toString()))
        }
        dao.insertMenuEntry(CookingMenuEntry(id, (menu.maxOfOrNull { it.position } ?: -1) + 1))
    }

    suspend fun setCookingTotal(count: Long) {
        require(count in 0L..MAX_PICK_COUNT) { "Count must be between 0 and $MAX_PICK_COUNT." }
        dao.putSetting(AppSetting(settingKey(COOKING_TOTAL), count.toString()))
    }

    suspend fun completeCookingMeal(expectedSessionId: String): PurchaseResult = database.withTransaction {
        val menu = dao.getMealMenu(mealId)
        if (menu.isEmpty() || dao.getSetting(settingKey(MENU_SESSION)) != expectedSessionId) {
            return@withTransaction PurchaseResult.StaleCart
        }
        if (menu.any { !it.completed }) return@withTransaction PurchaseResult.Incomplete
        val ingredients = menu.map { entry ->
            ownedCookingItem(entry.itemId) ?: return@withTransaction PurchaseResult.StaleCart
        }
        val total = dao.getSetting(settingKey(COOKING_TOTAL))?.toLong() ?: 0L
        if (total >= MAX_PICK_COUNT || ingredients.any { it.count >= MAX_PICK_COUNT }) {
            return@withTransaction PurchaseResult.LimitReached
        }
        ingredients.forEach { dao.setCount(it.id, it.count + 1) }
        dao.putSetting(AppSetting(settingKey(COOKING_TOTAL), (total + 1).toString()))
        dao.clearMealMenu(mealId)
        dao.deleteSetting(settingKey(MENU_SESSION))
        PurchaseResult.Completed
    }

    private suspend fun clearEmptyListSessions() {
        if (dao.getMealCart(mealId).isEmpty()) dao.deleteSetting(settingKey(CART_SESSION))
        if (dao.getMealMenu(mealId).isEmpty()) dao.deleteSetting(settingKey(MENU_SESSION))
    }

    // Ownership checks and their mutations run together in the caller's transaction, so a
    // stale screen or an ID from another meal can never write outside this repository.
    private suspend fun ownedGroup(id: String): FoodGroup? =
        dao.getGroup(id)?.takeIf { it.mealId == mealId }

    private suspend fun ownedItem(id: String): FoodItem? =
        dao.getItem(id)?.takeIf { ownedGroup(it.groupId) != null }

    private suspend fun ownedCookingItem(id: String): FoodItem? =
        dao.getItem(id)?.takeIf { ownedGroup(it.groupId)?.scene == "cooking" }

    private companion object {
        const val COOKING_TOTAL = "cooking_total"
        const val CART_SESSION = "cooking_cart_session"
        const val MENU_SESSION = "cooking_menu_session"
    }
}
