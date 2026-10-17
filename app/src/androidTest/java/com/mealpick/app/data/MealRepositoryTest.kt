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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MealRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: MealDatabase
    private lateinit var repository: MealRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "mealpick-test-${UUID.randomUUID()}.db"
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

    private suspend fun reopenDatabase() {
        database.close()
        openDatabase()
        repository.initialize()
    }

    @Test
    fun importsAllSixtyOneOriginalLinesWithoutRewritingUserData() = runBlocking {
        val state = repository.snapshot()
        val originalGroups = state.groups.filter { it.scene != "cooking" && it.zone != "nutrition" }
        val originalIds = originalGroups.map { it.id }.toSet()
        assertEquals(13, originalGroups.size)
        assertEquals(61, state.items.count { it.groupId in originalIds })
        assertEquals("zh", state.language)
        assertTrue(state.ready)
        assertTrue(state.items.all { it.count == 0L })
        val nutrition = state.groups.filter { it.zone == "nutrition" }.sortedBy { it.position }
        assertEquals(listOf("赛百味", "沙县鸡腿饭", "潮汕牛肉汤饭", "蛋白粉+农耕记米饭", "霸碗盖码饭"), nutrition.map { it.name })
        assertEquals((0..4).toList(), nutrition.map { it.position })
        assertTrue(nutrition.all { it.scene == "takeout" })
        val nutritionIds = nutrition.map { it.id }.toSet()
        // The supplied screenshot shows wheel labels only, not dishes, notes, or counts.
        assertFalse(state.items.any { it.groupId in nutritionIds })

        val originals = linkedMapOf(
            "米饭" to listOf("紫燕百味鸡", "老乡鸡", "霸碗盖码饭", "港式牛腩饭", "鲫鱼汤饭", "烧腊饭", "蛋炒饭（江味一派）", "特色炒饭（江味一派）", "鱼腩饭（江味一派，等很久）", "塑金咖喱饭"),
            "面" to listOf("辛拉面（要自己煮麻烦）", "兰州拉面（外卖会坨）", "兰州炒面", "腌面（江味一派）（外卖会坨）", "老碗会（外卖不好吃，千万别点）", "汤面"),
            "粉" to listOf("烧烤店炒粉+凉菜", "汤粉", "三两粉", "老友粉", "螺蛳粉"),
            "汉堡" to listOf("麦当劳（薯条）", "塔斯汀（不要鸭肉）", "必胜客"),
            "麻辣烫" to listOf("砂锅", "重庆"),
            "粥" to listOf("汕头鱼粥", "广州粥"),
            "饺子" to listOf("袁记云饺"),
            "披萨" to listOf("达美乐"),
            "其他" to listOf("窑鸡王", "汤（粤金府）", "小笼包", "豆浆油条"),
            "全国连锁" to listOf("费大厨", "八合里（三花趾、吊龙伴、匙肉、牛肉丸）", "探鱼（糍粑）", "渝月", "点都德（广州和兴楼）", "海底捞大排档", "蛙来哒", "繁楼（南山南油店：虾饺、叉烧包、榴莲酥、肠粉，其他再试试）"),
            "兴宁篇" to listOf("阿何快餐", "老站饭店", "猪红", "腌面"),
            "吃" to listOf("麻辣烫", "水果", "鸡柳大人", "绝味鸭脖", "沃尔玛 咸奶油泡芙", "烧烤", "糖水"),
            "喝" to listOf("煲珠公水牛奶", "喜茶", "星巴克", "瑞幸咖啡", "蜜雪冰城", "丘大叔", "霸王茶姬", "柠檬茶"),
        )
        originals.forEach { (name, expected) ->
            val group = state.groups.single { it.name == name }
            val actual = state.items.filter { it.groupId == group.id }.sortedBy { it.position }.map { it.name + it.notes }
            assertEquals("Source text in $name", expected, actual)
        }
        assertEquals(listOf("米饭", "面", "粉", "汉堡", "麻辣烫", "粥", "饺子", "披萨", "其他"), state.groups.filter { it.zone == "mood" }.sortedBy { it.position }.map { it.name })
        assertEquals(setOf("food", "drink"), state.groups.filter { it.scene == "snacks" }.mapNotNull { it.systemKey }.toSet())
    }

    @Test
    fun initializationNeverDuplicatesRowsOrReplacesEditsAfterReopen() = runBlocking {
        val initial = repository.snapshot()
        val item = initial.items.first()
        val nutrition = initial.groups.filter { it.zone == "nutrition" }.sortedBy { it.position }
        repository.updateItem(item.id, "我自己填的 Name", "我的中文 / English 备注")
        repository.setCount(item.id, 7)
        repository.setLanguage("en")
        repository.renameGroup(nutrition[0].id, "我改过的营养选项")
        repository.deleteGroup(nutrition[1].id)
        repository.initialize()
        reopenDatabase()
        val state = repository.snapshot()
        val originalIds = state.groups.filter { it.scene != "cooking" }.map { it.id }.toSet()
        assertEquals(61, state.items.count { it.groupId in originalIds })
        assertEquals("en", state.language)
        assertEquals("我自己填的 Name", state.items.single { it.id == item.id }.name)
        assertEquals("我的中文 / English 备注", state.items.single { it.id == item.id }.notes)
        assertEquals(7L, state.items.single { it.id == item.id }.count)
        assertEquals("我改过的营养选项", state.groups.single { it.id == nutrition[0].id }.name)
        assertFalse(state.groups.any { it.id == nutrition[1].id })
        assertEquals(4, state.groups.count { it.zone == "nutrition" })
    }

    @Test
    fun deletingEveryGroupStaysEmptyAfterInitializationAndReopen() = runBlocking {
        repository.snapshot().groups.forEach { repository.deleteGroup(it.id) }
        repository.initialize()
        reopenDatabase()
        assertTrue(repository.snapshot().groups.isEmpty())
        assertTrue(repository.snapshot().items.isEmpty())
    }

    @Test
    fun concurrentPicksAreAtomicAndManualCorrectionsChangeTheTotal() = runBlocking {
        val before = repository.snapshot()
        val group = before.groups.first { it.scene == "takeout" }
        val item = before.items.first { it.groupId == group.id }
        coroutineScope { repeat(100) { launch(Dispatchers.IO) { repository.pick(item.id) } } }
        assertEquals(100L, repository.snapshot().items.single { it.id == item.id }.count)
        repository.setCount(item.id, 3)
        assertEquals(3L, sceneTotal(repository.snapshot(), "takeout"))
        val negative = runCatching { repository.setCount(item.id, -5) }
        assertTrue(negative.exceptionOrNull() is IllegalArgumentException)
        assertEquals(3L, sceneTotal(repository.snapshot(), "takeout"))
        val overflow = runCatching { repository.setCount(item.id, Long.MAX_VALUE) }
        assertTrue(overflow.exceptionOrNull() is IllegalArgumentException)
        assertEquals(3L, sceneTotal(repository.snapshot(), "takeout"))
        repository.setCount(item.id, 0)
        assertEquals(0L, sceneTotal(repository.snapshot(), "takeout"))
        repository.setCount(item.id, MAX_PICK_COUNT)
        repository.pick(item.id)
        assertEquals(MAX_PICK_COUNT, repository.snapshot().items.single { it.id == item.id }.count)
    }

    @Test
    fun deletingItemAndGroupRemovesTheirCountsWithoutAHistoryRemainder() = runBlocking {
        val initial = repository.snapshot()
        val group = initial.groups.single { it.name == "米饭" }
        val items = initial.items.filter { it.groupId == group.id }
        repository.setCount(items[0].id, 5)
        repository.setCount(items[1].id, 3)
        assertEquals(8L, sceneTotal(repository.snapshot(), "takeout"))
        repository.deleteItem(items[0].id)
        assertEquals(3L, sceneTotal(repository.snapshot(), "takeout"))
        repository.deleteGroup(group.id)
        val result = repository.snapshot()
        assertFalse(result.items.any { it.groupId == group.id })
        assertEquals(0L, sceneTotal(result, "takeout"))
        reopenDatabase()
        assertFalse(repository.snapshot().groups.any { it.id == group.id })
    }

    @Test
    fun identicalNamesRemainIndependentAcrossPositionsAndRecreatedRowsStartAtZero() = runBlocking {
        val firstGroup = repository.snapshot().groups.single { it.name == "米饭" }
        repository.addGroup("takeout", "nutrition", "我的店")
        val otherGroup = repository.snapshot().groups.single { it.name == "我的店" }
        repository.addItem(firstGroup.id, "同名餐品", "备注甲")
        repository.addItem(otherGroup.id, "同名餐品", "备注乙")
        val pair = repository.snapshot().items.filter { it.name == "同名餐品" }
        val first = pair.single { it.groupId == firstGroup.id }
        val other = pair.single { it.groupId == otherGroup.id }
        repository.pick(first.id)
        repository.setCount(other.id, 4)
        assertEquals(5L, sceneTotal(repository.snapshot(), "takeout"))
        repository.updateItem(first.id, "改名", "改备注")
        assertEquals("备注乙", repository.snapshot().items.single { it.id == other.id }.notes)
        repository.deleteItem(first.id)
        assertEquals(4L, sceneTotal(repository.snapshot(), "takeout"))
        repository.addItem(firstGroup.id, "同名餐品", "新条目")
        assertEquals(0L, repository.snapshot().items.single { it.groupId == firstGroup.id && it.name == "同名餐品" }.count)
    }

    @Test
    fun reorderedRowsPersistAndNewEntriesAppendWithoutMovingCounts() = runBlocking {
        val group = repository.snapshot().groups.single { it.name == "米饭" }
        val original = repository.snapshot().items.filter { it.groupId == group.id }
        repository.setCount(original.first().id, 9)
        val reversedIds = original.map { it.id }.reversed()
        repository.reorderItems(group.id, reversedIds)
        repository.renameGroup(group.id, "Rice / 米饭")
        reopenDatabase()
        val state = repository.snapshot()
        val reordered = state.items.filter { it.groupId == group.id }
        assertEquals(reversedIds, reordered.map { it.id })
        assertEquals(9L, reordered.last().count)
        assertEquals("Rice / 米饭", state.groups.single { it.id == group.id }.name)
        repository.addItem(group.id, "新加的饭", "")
        assertEquals("新加的饭", repository.snapshot().items.filter { it.groupId == group.id }.last().name)
        val beforeInvalidOrder = repository.snapshot().items.filter { it.groupId == group.id }
        repository.reorderItems(group.id, listOf(original.first().id))
        assertEquals(beforeInvalidOrder, repository.snapshot().items.filter { it.groupId == group.id })
    }

    private fun sceneTotal(state: MealState, scene: String): Long {
        val groupIds = state.groups.filter { it.scene == scene }.map { it.id }.toSet()
        return state.items.filter { it.groupId in groupIds }.sumOf { it.count }
    }
}
