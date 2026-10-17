package com.mealpick.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Query("SELECT * FROM food_groups ORDER BY scene, zone, position, id")
    fun observeGroups(): Flow<List<FoodGroup>>

    @Query("SELECT * FROM food_items ORDER BY groupId, position, id")
    fun observeItems(): Flow<List<FoodItem>>

    @Query("SELECT value FROM app_settings WHERE `key` = 'language'")
    fun observeLanguage(): Flow<String?>

    @Query("SELECT * FROM app_settings")
    fun observeSettings(): Flow<List<AppSetting>>

    @Query("SELECT * FROM shopping_cart ORDER BY position, itemId")
    fun observeCart(): Flow<List<CartEntry>>

    @Query("SELECT * FROM shopping_cart ORDER BY position, itemId")
    suspend fun getCart(): List<CartEntry>

    @Query("SELECT * FROM cooking_menu ORDER BY position, itemId")
    fun observeMenu(): Flow<List<CookingMenuEntry>>

    @Query("SELECT * FROM cooking_menu ORDER BY position, itemId")
    suspend fun getMenu(): List<CookingMenuEntry>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getItem(id: String): FoodItem?

    @Query("SELECT * FROM food_groups ORDER BY scene, zone, position, id")
    suspend fun getGroups(): List<FoodGroup>

    @Query("SELECT * FROM food_groups WHERE mealId = :mealId ORDER BY scene, zone, position, id")
    suspend fun getMealGroups(mealId: String): List<FoodGroup>

    @Query("SELECT food_items.* FROM food_items INNER JOIN food_groups ON food_groups.id = food_items.groupId WHERE food_groups.mealId = :mealId ORDER BY food_items.groupId, food_items.position, food_items.id")
    suspend fun getMealItems(mealId: String): List<FoodItem>

    @Query("SELECT shopping_cart.* FROM shopping_cart INNER JOIN food_items ON food_items.id = shopping_cart.itemId INNER JOIN food_groups ON food_groups.id = food_items.groupId WHERE food_groups.mealId = :mealId ORDER BY shopping_cart.position, shopping_cart.itemId")
    suspend fun getMealCart(mealId: String): List<CartEntry>

    @Query("SELECT cooking_menu.* FROM cooking_menu INNER JOIN food_items ON food_items.id = cooking_menu.itemId INNER JOIN food_groups ON food_groups.id = food_items.groupId WHERE food_groups.mealId = :mealId ORDER BY cooking_menu.position, cooking_menu.itemId")
    suspend fun getMealMenu(mealId: String): List<CookingMenuEntry>

    @Query("DELETE FROM cooking_menu WHERE itemId IN (SELECT food_items.id FROM food_items INNER JOIN food_groups ON food_groups.id = food_items.groupId WHERE food_groups.mealId = :mealId)")
    suspend fun clearMealMenu(mealId: String)

    @Query("SELECT * FROM food_items ORDER BY groupId, position, id")
    suspend fun getItems(): List<FoodItem>

    @Query("SELECT * FROM food_groups WHERE id = :id")
    suspend fun getGroup(id: String): FoodGroup?

    @Query("SELECT * FROM food_items WHERE groupId = :groupId ORDER BY position, id")
    suspend fun getGroupItems(groupId: String): List<FoodItem>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroups(groups: List<FoodGroup>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<FoodItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: AppSetting)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCartEntry(entry: CartEntry)

    @Query("DELETE FROM shopping_cart WHERE itemId = :id")
    suspend fun removeFromCart(id: String)

    @Query("UPDATE shopping_cart SET purchased = :purchased WHERE itemId = :id")
    suspend fun setPurchased(id: String, purchased: Boolean)

    @Query("DELETE FROM shopping_cart")
    suspend fun clearCart()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMenuEntry(entry: CookingMenuEntry)

    @Query("DELETE FROM cooking_menu WHERE itemId = :id")
    suspend fun removeFromMenu(id: String)

    @Query("UPDATE cooking_menu SET completed = :completed WHERE itemId = :id")
    suspend fun setCookingCompleted(id: String, completed: Boolean)

    @Query("DELETE FROM cooking_menu")
    suspend fun clearMenu()

    @Query("UPDATE food_groups SET name = :name WHERE id = :id")
    suspend fun renameGroup(id: String, name: String)

    @Query("DELETE FROM food_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    @Query("UPDATE food_items SET name = :name, notes = :notes WHERE id = :id")
    suspend fun updateItem(id: String, name: String, notes: String)

    @Query("UPDATE food_items SET count = :count WHERE id = :id")
    suspend fun setCount(id: String, count: Long)

    @Query("UPDATE food_items SET count = count + 1 WHERE id = :id AND count < 2147483647 AND groupId IN (SELECT id FROM food_groups WHERE scene != 'cooking')")
    suspend fun pick(id: String)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("UPDATE food_items SET position = :position WHERE id = :id AND groupId = :groupId")
    suspend fun setItemPosition(groupId: String, id: String, position: Int)

    @Query("UPDATE food_groups SET position = :position WHERE id = :id AND scene = :scene AND zone = :zone")
    suspend fun setGroupPosition(scene: String, zone: String, id: String, position: Int)
}

@Database(entities = [FoodGroup::class, FoodItem::class, AppSetting::class, CartEntry::class, CookingMenuEntry::class], version = 4, exportSchema = true)
abstract class MealDatabase : RoomDatabase() {
    abstract fun meals(): MealDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS shopping_cart (
                        itemId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        purchased INTEGER NOT NULL,
                        PRIMARY KEY(itemId),
                        FOREIGN KEY(itemId) REFERENCES food_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cooking_menu (
                        itemId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        PRIMARY KEY(itemId),
                        FOREIGN KEY(itemId) REFERENCES food_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room runs the migration in one transaction. Existing data remains the dinner
                // copy; all other meals begin with independent, complete copies of that data.
                db.execSQL("ALTER TABLE food_groups ADD COLUMN mealId TEXT NOT NULL DEFAULT 'dinner'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_groups_mealId_scene_zone ON food_groups (mealId, scene, zone)")
                MEAL_IDS.filter { it != DEFAULT_MEAL_ID }.forEach { mealId ->
                    val prefix = scopedSeedId(mealId, "")
                    db.execSQL("""
                        INSERT INTO food_groups (id, scene, zone, name, position, systemKey, mealId)
                        SELECT ? || id, scene, zone, name, position, systemKey, ?
                        FROM food_groups WHERE mealId = ?
                    """.trimIndent(), arrayOf(prefix, mealId, DEFAULT_MEAL_ID))
                    db.execSQL("""
                        INSERT INTO food_items (id, groupId, name, notes, count, position)
                        SELECT ? || food_items.id, ? || food_items.groupId,
                               food_items.name, food_items.notes, food_items.count, food_items.position
                        FROM food_items INNER JOIN food_groups ON food_groups.id = food_items.groupId
                        WHERE food_groups.mealId = ?
                    """.trimIndent(), arrayOf(prefix, prefix, DEFAULT_MEAL_ID))
                    db.execSQL("""
                        INSERT INTO shopping_cart (itemId, position, purchased)
                        SELECT ? || shopping_cart.itemId, shopping_cart.position, shopping_cart.purchased
                        FROM shopping_cart
                        INNER JOIN food_items ON food_items.id = shopping_cart.itemId
                        INNER JOIN food_groups ON food_groups.id = food_items.groupId
                        WHERE food_groups.mealId = ?
                    """.trimIndent(), arrayOf(prefix, DEFAULT_MEAL_ID))
                    db.execSQL("""
                        INSERT INTO cooking_menu (itemId, position, completed)
                        SELECT ? || cooking_menu.itemId, cooking_menu.position, cooking_menu.completed
                        FROM cooking_menu
                        INNER JOIN food_items ON food_items.id = cooking_menu.itemId
                        INNER JOIN food_groups ON food_groups.id = food_items.groupId
                        WHERE food_groups.mealId = ?
                    """.trimIndent(), arrayOf(prefix, DEFAULT_MEAL_ID))
                    db.execSQL("""
                        INSERT INTO app_settings (`key`, value)
                        SELECT ? || `key`,
                               CASE WHEN `key` IN ('cooking_cart_session', 'cooking_menu_session')
                                    THEN ? || value ELSE value END
                        FROM app_settings
                        WHERE `key` IN ('seed_version', 'cooking_seed_version', 'cooking_total',
                                        'cooking_cart_session', 'cooking_menu_session')
                    """.trimIndent(), arrayOf(prefix, prefix))
                }
            }
        }

        @Volatile private var instance: MealDatabase? = null

        fun get(context: Context): MealDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MealDatabase::class.java,
                "mealpick.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
