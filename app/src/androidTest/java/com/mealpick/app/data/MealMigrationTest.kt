package com.mealpick.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MealMigrationTest {
    @Test
    fun versionTwoUpgradePreservesCartChecksEveryExistingValueAndCreatesAnEmptyMenu() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "meal-migration-v2-${UUID.randomUUID()}.db"
        var database: MealDatabase? = null
        val expectedGroups = listOf(
            FoodGroup("protein", "cooking", "cooking", "我改过的蛋白质", 1),
            FoodGroup("staples", "cooking", "cooking", "主食 / Staples", 4),
            FoodGroup("dining-01", "dining", "dining", "改名后的全国连锁", 7),
        )
        val expectedItems = listOf(
            FoodItem("restaurant", "dining-01", "保留的店名", "备注（原文）", 9, 3),
            FoodItem("ingredient_b", "protein", "鸡蛋 / Egg", "备注 B", 5, 2),
            FoodItem("ingredient_c", "protein", "未采购", "", 0, 8),
            FoodItem("ingredient_a", "staples", "红薯 / Sweet potato", "备注 A", 12, 6),
        )
        val expectedCart = listOf(CartEntry("ingredient_c", 1, false), CartEntry("ingredient_b", 3, true), CartEntry("ingredient_a", 10, true))
        val expectedSettings = linkedMapOf(
            "seed_version" to "1", "cooking_seed_version" to "1", "language" to "en",
            "cooking_total" to "17", "cooking_cart_session" to "existing-v2-cart", "user_extra" to "保留设置",
        )
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL("CREATE TABLE food_groups (id TEXT NOT NULL, scene TEXT NOT NULL, zone TEXT NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL, systemKey TEXT, PRIMARY KEY(id))")
                old.execSQL("CREATE INDEX index_food_groups_scene_zone ON food_groups (scene, zone)")
                old.execSQL("CREATE TABLE food_items (id TEXT NOT NULL, groupId TEXT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL, count INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(groupId) REFERENCES food_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_food_items_groupId ON food_items (groupId)")
                old.execSQL("CREATE TABLE app_settings (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))")
                old.execSQL("CREATE TABLE shopping_cart (itemId TEXT NOT NULL, position INTEGER NOT NULL, purchased INTEGER NOT NULL, PRIMARY KEY(itemId), FOREIGN KEY(itemId) REFERENCES food_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                old.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'a503b76f358994295a1d0e40799cc4bf')")
                expectedGroups.forEach { group ->
                    old.execSQL("INSERT INTO food_groups VALUES (?, ?, ?, ?, ?, ?)", arrayOf(group.id, group.scene, group.zone, group.name, group.position, group.systemKey))
                }
                expectedItems.forEach { item ->
                    old.execSQL("INSERT INTO food_items VALUES (?, ?, ?, ?, ?, ?)", arrayOf(item.id, item.groupId, item.name, item.notes, item.count, item.position))
                }
                expectedCart.forEach { entry ->
                    old.execSQL("INSERT INTO shopping_cart VALUES (?, ?, ?)", arrayOf(entry.itemId, entry.position, if (entry.purchased) 1 else 0))
                }
                expectedSettings.forEach { (key, value) -> old.execSQL("INSERT INTO app_settings VALUES (?, ?)", arrayOf(key, value)) }
                old.version = 2
            }
            database = Room.databaseBuilder(context, MealDatabase::class.java, name)
                .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4).build()
            var repository = MealRepository(requireNotNull(database), context.assets)
            repository.initialize()
            val migrated = repository.snapshot()
            assertEquals(4, database.openHelper.readableDatabase.version)
            assertEquals(expectedGroups, migrated.groups)
            assertEquals(expectedItems, migrated.items)
            assertEquals(expectedCart, migrated.cart)
            assertEquals("existing-v2-cart", migrated.cartSessionId)
            assertEquals(17L, migrated.cookingTotal)
            assertEquals("en", migrated.language)
            assertTrue(migrated.menu.isEmpty())
            assertNull(migrated.menuSessionId)
            MEAL_IDS.filter { it != "dinner" }.forEach { meal ->
                val scoped = MealRepository(requireNotNull(database), context.assets, meal)
                scoped.initialize()
                val clone = scoped.snapshot()
                assertEquals(expectedGroups.map { it.copy(id = scopedSeedId(meal, it.id), mealId = meal) }, clone.groups)
                assertEquals(expectedItems.map { it.copy(id = scopedSeedId(meal, it.id), groupId = scopedSeedId(meal, it.groupId)) }, clone.items)
                assertEquals(expectedCart.map { it.copy(itemId = scopedSeedId(meal, it.itemId)) }, clone.cart)
                assertEquals(scopedSeedId(meal, "existing-v2-cart"), clone.cartSessionId)
                assertEquals(17L, clone.cookingTotal)
                assertTrue(clone.menu.isEmpty())
            }
            val migratedDao = requireNotNull(database).meals()
            expectedSettings.forEach { (key, value) -> assertEquals(value, migratedDao.getSetting(key)) }
            repository.transferPurchasedToMenu("ingredient_b")
            repository.setCookingCompleted("ingredient_b", true)
            val afterTransfer = repository.snapshot()
            assertEquals(listOf("ingredient_b"), afterTransfer.menu.map { it.itemId })
            assertTrue(afterTransfer.menu.single().completed)
            assertEquals(expectedItems, afterTransfer.items)
            database.close()
            database = Room.databaseBuilder(context, MealDatabase::class.java, name)
                .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4).build()
            repository = MealRepository(requireNotNull(database), context.assets)
            repository.initialize()
            assertEquals(afterTransfer, repository.snapshot())
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun actualVersionOneFileMigratesWithoutReimportingOrChangingExistingUserData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "meal-migration-${UUID.randomUUID()}.db"
        var database: MealDatabase? = null
        try {
            // These statements are the exported v1 schema, including its original Room identity.
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL("CREATE TABLE food_groups (id TEXT NOT NULL, scene TEXT NOT NULL, zone TEXT NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL, systemKey TEXT, PRIMARY KEY(id))")
                old.execSQL("CREATE INDEX index_food_groups_scene_zone ON food_groups (scene, zone)")
                old.execSQL("CREATE TABLE food_items (id TEXT NOT NULL, groupId TEXT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL, count INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(groupId) REFERENCES food_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_food_items_groupId ON food_items (groupId)")
                old.execSQL("CREATE TABLE app_settings (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))")
                old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                old.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'b8b25d36935daa05907d489c7d6d338e')")
                old.execSQL("INSERT INTO food_groups VALUES ('old_group', 'takeout', 'mood', '我改过的分类', 9, NULL)")
                old.execSQL("INSERT INTO food_items VALUES ('old_first', 'old_group', '改过的餐品 / Meal', '备注（原文）', 7, 5)")
                old.execSQL("INSERT INTO food_items VALUES ('old_second', 'old_group', '排在前面的餐品', '', 2, 1)")
                old.execSQL("INSERT INTO app_settings VALUES ('seed_version', '1')")
                old.execSQL("INSERT INTO app_settings VALUES ('language', 'en')")
                old.version = 1
            }
            database = Room.databaseBuilder(context, MealDatabase::class.java, name)
                .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4).build()
            var repository = MealRepository(requireNotNull(database), context.assets)
            repository.initialize()
            val migrated = repository.snapshot()
            assertEquals(4, database.openHelper.readableDatabase.version)
            assertEquals("en", migrated.language)
            assertEquals(listOf("old_group"), migrated.groups.filter { it.scene != "cooking" }.map { it.id })
            assertEquals(9, migrated.groups.single { it.id == "old_group" }.position)
            val existing = migrated.items.filter { it.groupId == "old_group" }
            assertEquals(listOf("old_second", "old_first"), existing.map { it.id })
            assertEquals(listOf(2L, 7L), existing.map { it.count })
            assertEquals("改过的餐品 / Meal", existing.last().name)
            assertEquals("备注（原文）", existing.last().notes)
            assertEquals(3, migrated.groups.count { it.scene == "cooking" })
            assertEquals(8, migrated.items.size)
            assertEquals(0L, migrated.cookingTotal)
            assertTrue(migrated.cart.isEmpty())
            assertTrue(migrated.menu.isEmpty())
            assertNull(migrated.menuSessionId)
            MEAL_IDS.filter { it != "dinner" }.forEach { meal ->
                val scoped = MealRepository(requireNotNull(database), context.assets, meal)
                scoped.initialize()
                val clone = scoped.snapshot()
                assertEquals("en", clone.language)
                assertEquals(listOf(scopedSeedId(meal, "old_group")), clone.groups.filter { it.scene != "cooking" }.map { it.id })
                assertEquals(existing.map { it.copy(id = scopedSeedId(meal, it.id), groupId = scopedSeedId(meal, it.groupId)) }, clone.items.filter { it.groupId == scopedSeedId(meal, "old_group") })
                assertEquals(8, clone.items.size)
                assertEquals(3, clone.groups.count { it.scene == "cooking" })
                assertTrue(clone.groups.all { it.mealId == meal })
            }
            val shrimp = migrated.items.single { it.name == "虾" }
            repository.addToCookingMenu(shrimp.id)
            repository.setCookingCompleted(shrimp.id, true)
            assertEquals(PurchaseResult.Completed, repository.completeCookingMeal(requireNotNull(repository.snapshot().menuSessionId)))
            migrated.groups.filter { it.scene == "cooking" }.forEach { repository.deleteGroup(it.id) }
            database.close()
            database = Room.databaseBuilder(context, MealDatabase::class.java, name)
                .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4).build()
            repository = MealRepository(requireNotNull(database), context.assets)
            repository.initialize()
            val reopened = repository.snapshot()
            assertEquals(existing, reopened.items)
            assertFalse(reopened.groups.any { it.scene == "cooking" })
            assertEquals(1L, reopened.cookingTotal)
            assertEquals("en", reopened.language)
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun actualVersionThreeFileCopiesEveryExistingValueOnceThenMealsDivergeIndependently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "meal-migration-v3-${UUID.randomUUID()}.db"
        var database: MealDatabase? = null
        val groups = listOf(
            FoodGroup("protein", "cooking", "cooking", "蛋白质已改名", 2),
            FoodGroup("staples", "cooking", "cooking", "主食 / Staples", 6),
            FoodGroup("dining-01", "dining", "dining", "原全国连锁已改名", 5),
            FoodGroup("custom_snack", "snacks", "snacks", "自定义零食", 1, "food"),
            FoodGroup("rice", "takeout", "mood", "自定义口味分类", 8),
            FoodGroup("personal_nutrition", "takeout", "nutrition", "手机营养店·非种子", 3),
            FoodGroup("empty_nutrition", "takeout", "nutrition", "空店仍保留", 11),
        )
        val items = listOf(
            FoodItem("custom_snack_item", "custom_snack", "个人宵夜", "自填备注", 4, 2),
            FoodItem("restaurant", "dining-01", "我的餐馆", "多段备注（第一段）\n第二段", 9, 3),
            FoodItem("nutrition_food", "personal_nutrition", "我的营养餐", "不覆盖用户数据", 11, 7),
            FoodItem("egg", "protein", "鸡蛋 / Egg", "备注甲", 5, 2),
            FoodItem("shrimp", "protein", "虾", "备注乙", 12, 8),
            FoodItem("my_rice", "rice", "改名米饭", "自定义口味", 6, 9),
            FoodItem("potato", "staples", "红薯 / Sweet potato", "备注丙", 2, 6),
        )
        val cart = listOf(CartEntry("shrimp", 1, true), CartEntry("potato", 8, false), CartEntry("egg", 13, true))
        val menu = listOf(CookingMenuEntry("egg", 4, true), CookingMenuEntry("shrimp", 10, false))
        val settings = linkedMapOf(
            "seed_version" to "1", "cooking_seed_version" to "1", "language" to "en",
            "cooking_total" to "23", "cooking_cart_session" to "prior-cart-token",
            "cooking_menu_session" to "prior-menu-token", "custom_setting" to "其他设置原文保留",
        )
        try {
            createVersionThreeFile(context, name, groups, items, cart, menu, settings)
            database = openMigratingDatabase(context, name)
            val repositories = MEAL_IDS.associateWith { MealRepository(requireNotNull(database), context.assets, it) }
            repositories.values.forEach { it.initialize() }
            assertEquals(4, database.openHelper.readableDatabase.version)
            val migrated = repositories.mapValues { it.value.snapshot() }
            migrated.forEach { (meal, state) ->
                assertEquals(meal, state.mealId)
                assertEquals(groups.map { it.copy(id = scopedSeedId(meal, it.id), mealId = meal) }, state.groups)
                assertEquals(items.map { it.copy(id = scopedSeedId(meal, it.id), groupId = scopedSeedId(meal, it.groupId)) }, state.items)
                assertEquals(cart.map { it.copy(itemId = scopedSeedId(meal, it.itemId)) }, state.cart)
                assertEquals(menu.map { it.copy(itemId = scopedSeedId(meal, it.itemId)) }, state.menu)
                assertEquals(scopedSeedId(meal, "prior-cart-token"), state.cartSessionId)
                assertEquals(scopedSeedId(meal, "prior-menu-token"), state.menuSessionId)
                assertEquals(23L, state.cookingTotal)
                assertEquals("en", state.language)
                assertFalse(state.groups.any { it.id == scopedSeedId(meal, "nutrition-01") })
                assertFalse(state.groups.any { it.name == "已删除分类" })
                assertFalse(state.items.any { it.name == "已删除菜品" })
                assertEquals("1", requireNotNull(database).meals().getSetting(scopedSeedId(meal, "seed_version")))
                assertEquals("1", requireNotNull(database).meals().getSetting(scopedSeedId(meal, "cooking_seed_version")))
            }
            assertEquals(5, migrated.values.map { it.cartSessionId }.toSet().size)
            assertEquals(5, migrated.values.map { it.menuSessionId }.toSet().size)
            assertEquals("其他设置原文保留", requireNotNull(database).meals().getSetting("custom_setting"))
            assertNull(requireNotNull(database).meals().getSetting("breakfast::custom_setting"))

            val breakfast = repositories.getValue("breakfast")
            val breakfastShrimp = scopedSeedId("breakfast", "shrimp")
            breakfast.setCookingCompleted(breakfastShrimp, true)
            val beforeWrongSession = breakfast.snapshot()
            assertEquals(PurchaseResult.StaleCart, breakfast.completeCookingMeal("prior-menu-token"))
            assertEquals(beforeWrongSession, breakfast.snapshot())
            assertEquals(PurchaseResult.Completed, breakfast.completeCookingMeal(requireNotNull(beforeWrongSession.menuSessionId)))
            breakfast.updateItem(scopedSeedId("breakfast", "nutrition_food"), "早餐营养餐", "早餐备注")
            breakfast.deleteGroup(scopedSeedId("breakfast", "rice"))
            breakfast.setCount(scopedSeedId("breakfast", "restaurant"), 1)
            breakfast.transferPurchasedToMenu()
            breakfast.initialize()
            MEAL_IDS.filter { it != "breakfast" }.forEach { assertEquals(migrated.getValue(it), repositories.getValue(it).snapshot()) }
            val edited = repositories.mapValues { it.value.snapshot() }
            assertEquals(24L, edited.getValue("breakfast").cookingTotal)
            assertNotEquals(migrated.getValue("breakfast"), edited.getValue("breakfast"))
            database.close()
            database = openMigratingDatabase(context, name)
            MEAL_IDS.forEach { meal ->
                val reopened = MealRepository(requireNotNull(database), context.assets, meal)
                reopened.initialize()
                assertEquals(edited.getValue(meal), reopened.snapshot())
            }
            database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun upgradingAnIntentionallyEmptyVersionThreeDatabaseKeepsAllFiveMealsEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "meal-migration-v3-empty-${UUID.randomUUID()}.db"
        var database: MealDatabase? = null
        try {
            createVersionThreeFile(
                context, name, emptyList(), emptyList(), emptyList(), emptyList(),
                mapOf("seed_version" to "1", "cooking_seed_version" to "1", "language" to "en", "cooking_total" to "7"),
            )
            repeat(2) {
                database = openMigratingDatabase(context, name)
                MEAL_IDS.forEach { meal ->
                    val repository = MealRepository(requireNotNull(database), context.assets, meal)
                    repository.initialize()
                    val state = repository.snapshot()
                    assertTrue(state.groups.isEmpty())
                    assertTrue(state.items.isEmpty())
                    assertTrue(state.cart.isEmpty())
                    assertTrue(state.menu.isEmpty())
                    assertNull(state.cartSessionId)
                    assertNull(state.menuSessionId)
                    assertEquals(7L, state.cookingTotal)
                    assertEquals("en", state.language)
                }
                database?.close()
            }
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }

    private fun openMigratingDatabase(context: Context, name: String): MealDatabase =
        Room.databaseBuilder(context, MealDatabase::class.java, name)
            .addMigrations(MealDatabase.MIGRATION_1_2, MealDatabase.MIGRATION_2_3, MealDatabase.MIGRATION_3_4).build()

    private fun createVersionThreeFile(
        context: Context,
        name: String,
        groups: List<FoodGroup>,
        items: List<FoodItem>,
        cart: List<CartEntry>,
        menu: List<CookingMenuEntry>,
        settings: Map<String, String>,
    ) {
        // This is the exported v3 schema and identity, not a v4 database with its version lowered.
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("PRAGMA foreign_keys = ON")
            old.execSQL("CREATE TABLE food_groups (id TEXT NOT NULL, scene TEXT NOT NULL, zone TEXT NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL, systemKey TEXT, PRIMARY KEY(id))")
            old.execSQL("CREATE INDEX index_food_groups_scene_zone ON food_groups (scene, zone)")
            old.execSQL("CREATE TABLE food_items (id TEXT NOT NULL, groupId TEXT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL, count INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(groupId) REFERENCES food_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_food_items_groupId ON food_items (groupId)")
            old.execSQL("CREATE TABLE app_settings (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))")
            old.execSQL("CREATE TABLE shopping_cart (itemId TEXT NOT NULL, position INTEGER NOT NULL, purchased INTEGER NOT NULL, PRIMARY KEY(itemId), FOREIGN KEY(itemId) REFERENCES food_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE cooking_menu (itemId TEXT NOT NULL, position INTEGER NOT NULL, completed INTEGER NOT NULL, PRIMARY KEY(itemId), FOREIGN KEY(itemId) REFERENCES food_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'f24360e5dfebd8849f128739e6ac2e0a')")
            groups.forEach { group ->
                old.execSQL("INSERT INTO food_groups VALUES (?, ?, ?, ?, ?, ?)", arrayOf(group.id, group.scene, group.zone, group.name, group.position, group.systemKey))
            }
            items.forEach { item ->
                old.execSQL("INSERT INTO food_items VALUES (?, ?, ?, ?, ?, ?)", arrayOf(item.id, item.groupId, item.name, item.notes, item.count, item.position))
            }
            cart.forEach { entry ->
                old.execSQL("INSERT INTO shopping_cart VALUES (?, ?, ?)", arrayOf(entry.itemId, entry.position, if (entry.purchased) 1 else 0))
            }
            menu.forEach { entry ->
                old.execSQL("INSERT INTO cooking_menu VALUES (?, ?, ?)", arrayOf(entry.itemId, entry.position, if (entry.completed) 1 else 0))
            }
            settings.forEach { (key, value) -> old.execSQL("INSERT INTO app_settings VALUES (?, ?)", arrayOf(key, value)) }
            old.execSQL("INSERT INTO food_groups VALUES ('deleted_group', 'takeout', 'mood', '已删除分类', 999, NULL)")
            old.execSQL("INSERT INTO food_items VALUES ('deleted_item', 'deleted_group', '已删除菜品', '不应恢复', 99, 0)")
            old.execSQL("DELETE FROM food_groups WHERE id = 'deleted_group'")
            old.version = 3
        }
    }
}
