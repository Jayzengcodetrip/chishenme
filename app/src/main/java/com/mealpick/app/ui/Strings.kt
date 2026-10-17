package com.mealpick.app.ui

import com.mealpick.app.data.FoodGroup

/** Only application copy is translated. User-entered group and food names stay intact. */
internal class UiStrings(language: String) {
    val english = language == "en"
    private fun text(zh: String, en: String) = if (english) en else zh

    /** The home sign is the brand line and reads the same in both languages. */
    val appTitle = "每日五餐，你要哪餐"
    val takeout = text("点外卖", "Takeout")
    val dining = text("出门吃大餐", "Dining out")
    val snacks = text("整点宵夜（零食）", "Snack Time")
    val cooking = text("自己做", "Cook at Home")
    val canteen = text("食堂有啥吃啥", "Canteen Pot Luck")
    val family = text("家里有啥吃啥", "Whatever's at Home")
    val nightTakeout = text("宵夜 · 点外卖", "Late-night Snack · Takeout")
    val now = text("现在", "Now")
    val languageLabel = text("中文", "English")
    val shoppingList = text("购物车", "Shopping Cart")
    val cookingMenu = text("烹饪菜单", "Cooking Menu")
    val editTotal = text("修改总次数", "Edit total count")
    val nutrition = text("营养控制区", "Nutrition Choices")
    val mood = text("心情口味区", "Mood & Cravings")
    val food = text("吃的", "Food")
    val drink = text("喝的", "Drinks")
    val statistics = text("数据统计", "Statistics")
    val helpMePick = text("帮我选", "Help me choose")
    val edit = text("编辑", "Edit")
    val editWheel = text("编辑转盘", "Edit Wheel")
    val addOption = text("新增选项", "Add option")
    val addGroup = text("新增分类", "Add category")
    val addItem = text("新增条目", "Add item")
    val rename = text("修改名称", "Rename")
    val editText = text("编辑文字", "Edit text")
    val editCount = text("修改次数", "Edit count")
    val delete = text("删除", "Delete")
    val cancel = text("取消", "Cancel")
    val save = text("保存", "Save")
    val back = text("返回", "Back")
    val name = text("名称", "Name")
    val notes = text("备注", "Notes")
    val restaurantNotes = text("餐馆备注", "Restaurant notes")
    val optionalNotes = text("备注（选填）", "Notes (optional)")
    val count = text("次数", "Count")
    val emptyItems = text("这里还没有条目", "No items here yet")
    val emptyGroups = text("这里还没有分类", "No categories here yet")
    val emptyOptions = text("暂无选项，点编辑添加", "No options yet. Tap Edit to add one.")
    val moreActions = text("更多操作", "More actions")
    val dragToSort = text("长按拖动排序", "Touch and hold to reorder")
    val totalPicks = text("累计次数", "Total picks")
    val groupBreakdown = text("按组明细", "By group")
    val noStatistics = text("暂无分类数据", "No group data yet")
    val invalidCount = text("请输入 0 至 2147483647 的整数", "Enter a whole number from 0 to 2147483647")
    val requiredName = text("名称不能为空", "A name is required")
    val pick = "Pick"
    val drawRestaurant = text("抽一家", "Draw a restaurant")
    val drawAgain = text("再抽一次", "Draw again")
    val viewAllCards = text("查看全部", "View all")
    val shufflingCards = text("洗牌中…", "Shuffling…")
    val chooseFaceDown = text("挑一张，翻开看看", "Choose a card to turn over")
    val yourCard = text("你翻开的这一张", "The card you turned over")
    val turnCard = text("翻开", "Turn over")
    val canteenSign = text("今日干饭签", "Today's Meal Fortune")
    val drawAnotherSign = text("再抽一签", "Draw another")
    val familyNote = text("家的味道", "Taste of Home")
    val anotherLine = text("换一句", "Another one")

    fun mealName(meal: Meal) = when (meal) {
        Meal.Breakfast -> text("早餐", "Breakfast")
        Meal.Lunch -> text("午餐", "Lunch")
        Meal.Dinner -> text("晚餐", "Dinner")
        Meal.AfternoonTea -> text("下午茶", "Afternoon Tea")
        Meal.LateNight -> text("宵夜", "Late-night Snack")
    }
    fun waysTitle(meal: Meal) = if (english) "${mealName(meal)} · What's the plan?" else "${mealName(meal)} · 怎么吃？"
    fun signNumber(number: Int) = if (english) "No. %02d".format(number) else "第 %02d 签".format(number)

    fun restaurantCount(value: Int) = if (english) "$value ${if (value == 1) "restaurant" else "restaurants"}" else "${value}家餐馆"
    fun hiddenCard(value: Int) = if (english) "Card $value, face down" else "第${value}张牌，背面朝上"

    fun times(value: Long) = if (english) "$value ${if (value == 1L) "time" else "times"}" else "${value}次"
    fun groupName(group: FoodGroup): String = when {
        group.scene == "snacks" && group.systemKey == "food" -> food
        group.scene == "snacks" && group.systemKey == "drink" -> drink
        else -> group.name
    }
}

/** The five meals on the home screen, in display order. */
enum class Meal(val key: String) {
    Breakfast("breakfast"),
    Lunch("lunch"),
    Dinner("dinner"),
    AfternoonTea("afternoon_tea"),
    LateNight("late_night");

    companion object {
        /** Hour of day (0–23) to the meal that is "now"; only a hint, every meal stays selectable. */
        fun at(hour: Int): Meal = when (hour) {
            in 5..9 -> Breakfast
            in 10..13 -> Lunch
            in 14..16 -> AfternoonTea
            in 17..20 -> Dinner
            else -> LateNight
        }
    }
}

/** Fixed quote libraries, kept in the same order in both languages. */
internal object Quotes {
    val canteenZh = listOf(
        "打工辛苦了，这顿多打一勺饭。",
        "食堂阿姨的手不抖，就是今天最大的好运。",
        "先干饭，再干活，天塌下来也得吃饱。",
        "忙了这么久，你值得一份红烧肉。",
        "今天也是认真干饭的一天，给自己点个赞。",
        "不管菜单写的是什么，吃饱就是胜利。",
        "排队的每一分钟，都是在给胃口加分。",
        "工作会做完的，饭凉了可就不好吃了。",
        "今天的辛苦，配得上一份加肉的套餐。",
        "吃饱了才有力气去搞定那个难题。",
        "干饭不积极，思想有问题。",
        "最好吃的菜，是饿了半天后打的那一份。",
        "先把饭吃好，烦恼放到饭后再说。",
        "今天又多扛了一点，多吃一口奖励自己。",
        "没抢到喜欢的菜也没关系，明天还有新惊喜。",
        "饭点是打工人的加油站，加满再出发。",
        "一荤一素一碗汤，认真吃饭的人最可爱。",
        "别边吃边回消息啦，这十五分钟只属于你。",
        "再忙也要好好吃饭，身体最要紧。",
        "吃完这顿，你又是元气满满的自己。",
    )
    val canteenEn = listOf(
        "You've worked hard. Get an extra scoop of rice.",
        "If the lunch lady doesn't shake the ladle, that's your luck for the day.",
        "Eat first, work later. Whatever happens, don't do it hungry.",
        "After all that hustle, you've earned some braised pork.",
        "Another day of eating well. Give yourself a pat on the back.",
        "Whatever's on the menu, a full stomach is a win.",
        "Every minute in line just makes it taste better.",
        "The work will get done. Your food won't stay warm.",
        "All that hard work deserves extra meat.",
        "Fuel up first, then go crush that tough problem.",
        "Food first. Everything else can wait.",
        "The best dish is the one you grab when you're starving.",
        "Eat well now. Save the worries for after.",
        "You carried a little more today. Have another bite on me.",
        "Missed your favorite dish? There's always tomorrow.",
        "Mealtime is your pit stop. Refuel, then go.",
        "Meat, veggies and a bowl of soup. Nothing beats a proper meal.",
        "Put the phone down. These fifteen minutes are all yours.",
        "No matter how busy you are, eat well. Your health comes first.",
        "One good meal, and you're back to full power.",
    )
    val familyZh = listOf(
        "珍惜和家人的每一天，好好陪伴家人。",
        "冰箱里有什么不重要，一起吃才最香。",
        "家常便饭，是最踏实的幸福。",
        "今天早点放下手机，陪家人好好吃顿饭。",
        "剩菜热一热，也是家的味道。",
        "有人等你回家吃饭，就是最好的日子。",
        "给爸妈夹一筷子菜，比说很多话都管用。",
        "一碗热汤，暖胃也暖心。",
        "家里的饭不用多丰盛，有你在就够了。",
        "随便做点什么，一家人围着桌子就很好。",
        "吃饭的时候聊聊今天，是最温柔的陪伴。",
        "把今天的好消息，留到饭桌上分享。",
        "记得问问家人：今天过得怎么样？",
        "慢慢吃，家里的饭不用赶时间。",
        "厨房里飘出的香味，是家最好的欢迎。",
        "今天换你下厨吧，让家人尝尝你的手艺。",
        "平凡的一日三餐，藏着最深的牵挂。",
        "饭后一起洗个碗，也是一种陪伴。",
        "家里有啥吃啥，有家人在，啥都好吃。",
        "好好吃饭，就是对家人最好的回报。",
    )
    val familyEn = listOf(
        "Cherish every day with your family, and be there for them.",
        "It doesn't matter what's in the fridge. Food tastes best together.",
        "Simple home cooking is the most solid kind of happiness.",
        "Put your phone down early today and enjoy a meal with your family.",
        "Leftovers, warmed up, still taste like home.",
        "Having someone wait for you to come home for dinner is a good day.",
        "Putting food on your parents' plates says more than words.",
        "A bowl of hot soup warms your stomach and your heart.",
        "Home-cooked meals don't need to be fancy. You being there is enough.",
        "Whip up anything. Gathering around the table is what counts.",
        "Talking about your day over dinner is the gentlest way to be there.",
        "Save today's good news for the dinner table.",
        "Remember to ask your family: How was your day?",
        "Take your time. There's no rush at the family table.",
        "The smell from the kitchen is home's warmest welcome.",
        "Take a turn in the kitchen today and treat your family.",
        "Three ordinary meals a day hold the deepest care.",
        "Washing the dishes together counts as quality time too.",
        "Whatever's at home is fine. With family around, everything tastes good.",
        "Eating well is the best way to repay your family.",
    )

    fun canteen(english: Boolean) = if (english) canteenEn else canteenZh
    fun family(english: Boolean) = if (english) familyEn else familyZh

    /** A uniform pick that never repeats the quote shown last time (when there is a choice). */
    fun nextIndex(size: Int, previous: Int?, draw: (Int) -> Int): Int {
        require(size > 0)
        if (size == 1) return 0
        if (previous == null || previous !in 0 until size) return draw(size).also { require(it in 0 until size) }
        val offset = draw(size - 1)
        require(offset in 0 until size - 1)
        return if (offset >= previous) offset + 1 else offset
    }
}
