package com.mealpick.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val MAX_PICK_COUNT: Long = 2_147_483_647L
const val DEFAULT_MEAL_ID = "dinner"
val MEAL_IDS = listOf("breakfast", "lunch", DEFAULT_MEAL_ID, "afternoon_tea", "late_night")

/** Stable seed identities; dinner retains legacy identities for an in-place upgrade. */
fun scopedSeedId(mealId: String, originalId: String): String {
    require(mealId in MEAL_IDS) { "Unknown meal: $mealId" }
    return if (mealId == DEFAULT_MEAL_ID) originalId else "$mealId::$originalId"
}

fun isNationalDiningGroup(group: FoodGroup): Boolean =
    group.scene == "dining" && group.id == scopedSeedId(group.mealId, "dining-01")

@Entity(tableName = "food_groups", indices = [Index(value = ["scene", "zone"]), Index(value = ["mealId", "scene", "zone"])])
data class FoodGroup(
    @PrimaryKey val id: String,
    val scene: String,
    val zone: String,
    val name: String,
    val position: Int,
    val systemKey: String? = null,
    @ColumnInfo(defaultValue = "'dinner'") val mealId: String = DEFAULT_MEAL_ID,
)

@Entity(
    tableName = "food_items",
    foreignKeys = [ForeignKey(
        entity = FoodGroup::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("groupId")],
)
data class FoodItem(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val notes: String = "",
    val count: Long = 0,
    val position: Int,
)

@Entity(
    tableName = "shopping_cart",
    foreignKeys = [ForeignKey(
        entity = FoodItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CartEntry(
    @PrimaryKey val itemId: String,
    val position: Int,
    val purchased: Boolean = false,
)

@Entity(
    tableName = "cooking_menu",
    foreignKeys = [ForeignKey(
        entity = FoodItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CookingMenuEntry(
    @PrimaryKey val itemId: String,
    val position: Int,
    val completed: Boolean = false,
)

enum class PurchaseResult { Completed, StaleCart, Incomplete, LimitReached, Failed }

data class MealState(
    val groups: List<FoodGroup> = emptyList(),
    val items: List<FoodItem> = emptyList(),
    val language: String = "zh",
    val ready: Boolean = false,
    val cart: List<CartEntry> = emptyList(),
    val cookingTotal: Long = 0,
    val cartSessionId: String? = null,
    val menu: List<CookingMenuEntry> = emptyList(),
    val menuSessionId: String? = null,
    val mealId: String = DEFAULT_MEAL_ID,
)

@Entity(tableName = "app_settings")
data class AppSetting(@PrimaryKey val key: String, val value: String)
