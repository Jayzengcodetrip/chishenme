package com.mealpick.app.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mealpick.app.data.FoodGroup
import com.mealpick.app.data.FoodItem
import com.mealpick.app.data.MealState
import com.mealpick.app.data.MealViewModel
import com.mealpick.app.data.PurchaseResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Product copy only: editable category and ingredient strings never go through this class. */
internal class CookingStrings(language: String) {
    val english = language == "en"
    private fun text(zh: String, en: String) = if (english) en else zh
    val title = text("自己做饭", "Cook at Home")
    val purchaseTitle = text("购物车", "Shopping Cart")
    val menuTitle = text("烹饪菜单", "Cooking Menu")
    val editTotal = text("修改总次数", "Edit cooking total")
    val addIngredient = text("新增食材", "Add ingredient")
    val noIngredients = text("这里还没有食材", "No ingredients here yet")
    val addToCart = text("加入购物车", "Add to cart")
    val addedToCart = text("已加入购物车", "In cart")
    val addToMenu = text("加入烹饪菜单", "Add to cooking menu")
    val addedToMenu = text("已加入菜单", "In cooking menu")
    val shoppingCart = text("购物车", "Shopping cart")
    val removeFromPending = text("移出待采购", "Remove from shopping")
    val removeFromCart = text("移出购物车", "Remove from cart")
    val removeFromMenu = text("移出烹饪菜单", "Remove from cooking menu")
    val emptyCart = text("购物车还是空的，去食材库加入想买的食材。", "Your cart is empty. Add ingredients from Cook at Home.")
    val purchased = text("已采购", "Purchased")
    val notPurchased = text("待采购", "To buy")
    val expanded = text("已展开", "Expanded")
    val collapsed = text("已收起", "Collapsed")
    val enterMenu = text("进入烹饪菜单", "Open cooking menu")
    val pending = text("待完成", "To cook")
    val completed = text("已完成", "Completed")
    val completeMeal = text("本餐完成", "Finish this meal")
    val finishHint = text("全部食材完成后，即可记录本餐", "Complete every ingredient to record this meal")
    val emptyMenu = text("当前没有待烹饪的食材", "No ingredients to cook yet")
    val recorded = text("本餐已记录", "Meal recorded")
    val chooseIngredients = text("去选食材", "Choose ingredients")
    val errorTitle = text("本餐尚未记录", "Meal not recorded")
    val staleMessage = text("菜单已发生变化，请重新检查后再完成。", "The menu has changed. Please review it before finishing.")
    val incompleteMessage = text("请先完成全部食材，或移出不准备做的食材。", "Complete every ingredient, or remove any you will not cook.")
    val limitMessage = text("做饭总次数或某个食材的次数已达到上限，请先修改次数后再完成。", "The cooking total or an ingredient count has reached its limit. Edit that count before finishing.")
    val failedMessage = text("未能保存，请重试。烹饪菜单已保留。", "Could not save. Your cooking menu is still here; please try again.")
    val gotIt = text("知道了", "OK")
    fun cartCount(count: Int) = text("购物车 · $count 项", "Cart · $count")
    fun menuCount(count: Int) = text("烹饪菜单 · $count 项", "Cooking menu · $count")
    fun total(count: Int) = text("共 $count 项", "$count ingredients")
    fun section(label: String, count: Int) = text("$label · $count 项", "$label · $count")
    fun mealProgress(total: Int, done: Int) = text("本餐 $total 项 · 已完成 $done 项", "$total ingredients · $done completed")
}

private sealed interface CookingEditor {
    data class Group(val value: FoodGroup? = null) : CookingEditor
    data class Ingredient(val groupId: String, val value: FoodItem? = null) : CookingEditor
    data class Count(val value: FoodItem) : CookingEditor
}

private data class CookingDrag(
    val id: String,
    val groupId: String?,
    val originalOrder: List<String>,
    val originalTop: Float,
    val originalPointer: Float,
    val delta: Float = 0f,
) {
    val pointer get() = originalPointer + delta
    val top get() = originalTop + delta
    val measurementKey get() = if (groupId == null) "group_$id" else "item_$id"
}

// Keep offscreen coordinates intact: clipped bounds collapse at a scroll viewport's
// edge, which otherwise makes drag thresholds jump while auto-scrolling.
private fun LayoutCoordinates.unclippedRootBounds(): Rect {
    val position = positionInRoot()
    return Rect(position.x, position.y, position.x + size.width, position.y + size.height)
}

/**
 * Categories and ingredients share one scroll container. A category is a keyed block,
 * so dragging it naturally carries its expanded children, without nested scroll gestures.
 * The caller retains this screen in a SaveableStateProvider during shopping navigation.
 */
@Composable
internal fun CookingScreen(state: MealState, viewModel: MealViewModel, onPurchase: () -> Unit, onMenu: () -> Unit) {
    val strings = remember(state.language) { UiStrings(state.language) }
    val copy = remember(state.language) { CookingStrings(state.language) }
    val groups = state.groups.filter { it.scene == "cooking" }.sortedBy { it.position }
    val groupById = groups.associateBy { it.id }
    val foodById = state.items.associateBy { it.id }
    val sourceGroups = groups.map { it.id }
    val sourceItems = groups.associate { group -> group.id to state.items.filter { it.groupId == group.id }.sortedBy { it.position }.map { it.id } }
    var localGroupOrder by remember { mutableStateOf<List<String>?>(null) }
    var localItemOrders by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var expandedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editor by remember { mutableStateOf<CookingEditor?>(null) }
    val cartIds = state.cart.map { it.itemId }.toSet()
    val menuIds = state.menu.map { it.itemId }.toSet()
    val scroll = rememberScrollState()
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val handleCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf<CookingDrag?>(null) }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }

    fun reconciled(local: List<String>?, source: List<String>): List<String> =
        if (local == null) source else local.filter { it in source } + source.filter { it !in local }
    val groupOrder = reconciled(localGroupOrder, sourceGroups)
    val itemOrders = sourceItems.mapValues { (id, source) -> reconciled(localItemOrders[id], source) }

    LaunchedEffect(sourceGroups, sourceItems) {
        if (localGroupOrder == sourceGroups) localGroupOrder = null
        localItemOrders = localItemOrders.filter { (id, order) -> sourceItems[id] != order && id in sourceItems }
        expandedIds = expandedIds.filter { it in sourceGroups }
        val active = dragging
        if (active != null && (active.groupId == null && active.id !in sourceGroups || active.groupId != null && active.id !in sourceItems[active.groupId].orEmpty())) dragging = null
    }

    fun reorderAtPointer(direction: Int) {
        val active = dragging ?: return
        if (direction == 0) return
        // Gesture callbacks may outlive the composition that created them. Anchor
        // this gesture to its own starting order, never an earlier database snapshot.
        val ordered = if (active.groupId == null) reconciled(localGroupOrder, active.originalOrder)
        else reconciled(localItemOrders[active.groupId], active.originalOrder)
        val from = ordered.indexOf(active.id)
        if (from < 0) return
        val crossed = ordered.mapIndexedNotNull { index, id ->
            // Handle centers remain accurate when names or notes make a row taller.
            // Expanded category contents never shift their header handle's threshold.
            val coordinates = handleCoordinates[if (active.groupId == null) "group_$id" else "item_$id"]
                ?.takeIf { it.isAttached } ?: return@mapIndexedNotNull null
            // A graphicsLayer translation can finish without a positioning callback.
            // Query the current transform here instead of caching a previous root Y.
            val midpoint = coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)).y
            when {
                direction > 0 && index > from && active.pointer >= midpoint - 1f -> index
                direction < 0 && index < from && active.pointer <= midpoint + 1f -> index
                else -> null
            }
        }
        val target = (if (direction > 0) crossed.maxOrNull() else crossed.minOrNull()) ?: return
        val changed = ordered.toMutableList().apply { add(target, removeAt(from)) }
        if (active.groupId == null) localGroupOrder = changed else localItemOrders = localItemOrders + (active.groupId to changed)
    }
    // Explicit lambdas keep updated callbacks fresh; local function references can compare equal.
    val latestReorder by rememberUpdatedState<(Int) -> Unit> { direction -> reorderAtPointer(direction) }
    LaunchedEffect(dragging?.measurementKey) {
        while (isActive && dragging != null) {
            val active = dragging ?: break
            val amount = when {
                active.pointer < viewport.top + edge -> -((viewport.top + edge - active.pointer) * .18f).coerceAtMost(24f)
                active.pointer > viewport.bottom - edge -> ((active.pointer - viewport.bottom + edge) * .18f).coerceAtMost(24f)
                else -> 0f
            }
            if (amount != 0f && scroll.scrollBy(amount) != 0f) latestReorder(if (amount > 0) 1 else -1)
            delay(16)
        }
    }
    fun startDrag(id: String, groupId: String?, pointerRootY: Float) {
        val row = bounds[if (groupId == null) "group_$id" else "item_$id"] ?: return
        dragging = CookingDrag(id, groupId, if (groupId == null) groupOrder else itemOrders[groupId].orEmpty(), row.top, pointerRootY)
    }
    fun moveDrag(amount: Float) {
        dragging = dragging?.let { it.copy(delta = it.delta + amount) }
        reorderAtPointer(if (amount > 0) 1 else if (amount < 0) -1 else 0)
    }
    fun finishDrag() {
        dragging?.let { active ->
            if (active.groupId == null) viewModel.reorderGroups("cooking", "cooking", reconciled(localGroupOrder, active.originalOrder))
            else viewModel.reorderItems(active.groupId, reconciled(localItemOrders[active.groupId], active.originalOrder))
        }
        dragging = null
    }
    fun cancelDrag() {
        dragging?.let { active ->
            if (active.groupId == null) localGroupOrder = active.originalOrder
            else localItemOrders = localItemOrders + (active.groupId to active.originalOrder)
        }
        dragging = null
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val narrowScreen = maxWidth < 340.dp
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { viewport = it.boundsInRoot() }
                    .verticalScroll(scroll, enabled = dragging == null).testTag("cooking_list")
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AddButton(strings.addGroup, "cooking_add_group") { editor = CookingEditor.Group() }
                }
                if (groups.isEmpty()) EmptyMessage(strings.emptyGroups)
                groupOrder.forEach { groupId ->
                    val group = groupById[groupId] ?: return@forEach
                    key(group.id) {
                        val active = dragging?.takeIf { it.id == group.id && it.groupId == null }
                        Box(Modifier.fillMaxWidth().zIndex(if (active != null) 2f else 0f)
                            .onGloballyPositioned { bounds["group_${group.id}"] = it.unclippedRootBounds() }
                            .testTag("cooking_group_block_${group.id}")) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().graphicsLayer {
                                    translationY = active?.let { it.top - (bounds[it.measurementKey]?.top ?: it.originalTop) } ?: 0f
                                },
                                color = if (active == null) Mp.Card else Mp.JadeTint,
                                contentColor = Mp.Ink,
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, if (active == null) Color.Transparent else Mp.Jade.copy(alpha = .4f)),
                                shadowElevation = if (active == null) 0.dp else 6.dp,
                            ) {
                                Column {
                                    val expanded = group.id in expandedIds
                                    val headerInteraction = remember { MutableInteractionSource() }
                                    Row(Modifier.fillMaxWidth().pressScale(headerInteraction, 0.99f), verticalAlignment = Alignment.CenterVertically) {
                                        CookingDragHandle("cooking_group_drag_${group.id}", strings,
                                            onStart = { pointerRootY -> startDrag(group.id, null, pointerRootY) },
                                            onHandlePositioned = { handleCoordinates["group_${group.id}"] = it },
                                            onDrag = { amount -> moveDrag(amount) },
                                            onEnd = { finishDrag() }, onCancel = { cancelDrag() })
                                        Row(Modifier.weight(1f).heightIn(min = 64.dp)
                                            .testTag("cooking_group_${group.id}")
                                            .semantics { stateDescription = if (expanded) copy.expanded else copy.collapsed }
                                            .clickable(interactionSource = headerInteraction, indication = ripple()) {
                                                expandedIds = if (expanded) expandedIds - group.id else expandedIds + group.id
                                            }
                                            .padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(group.name, Modifier.weight(1f, fill = false), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Mp.Ink)
                                            Spacer(Modifier.width(8.dp))
                                            Text(itemOrders[group.id].orEmpty().size.toString(), color = Mp.Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.background(Color.White, CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
                                            Spacer(Modifier.weight(1f))
                                            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null,
                                                tint = Mp.Ink2, modifier = Modifier.padding(horizontal = 8.dp))
                                        }
                                        MoreMenu(strings, listOf(strings.rename to { editor = CookingEditor.Group(group) }, strings.delete to { viewModel.deleteGroup(group.id) }), "cooking_group_more_${group.id}")
                                    }
                                    // 1.4.3: the ingredient list opens/closes over ~200ms instead of jumping.
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = if (Motion.reduced()) fadeIn(tween(Motion.QuickMs))
                                        else expandVertically(tween(Motion.StandardMs, easing = Motion.EaseOut), expandFrom = Alignment.Top) +
                                            fadeIn(tween(Motion.StandardMs, easing = Motion.EaseOut)),
                                        exit = if (Motion.reduced()) fadeOut(tween(100))
                                        else shrinkVertically(tween(180, easing = Motion.EaseOut), shrinkTowards = Alignment.Top) +
                                            fadeOut(tween(120, easing = Motion.EaseOut)),
                                    ) {
                                        Column {
                                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Mp.LineSoft)
                                            Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.End) {
                                                AddButton(copy.addIngredient, "cooking_add_item_${group.id}") { editor = CookingEditor.Ingredient(group.id) }
                                            }
                                            val ingredients = itemOrders[group.id].orEmpty().mapNotNull(foodById::get)
                                            if (ingredients.isEmpty()) EmptyMessage(copy.noIngredients)
                                            ingredients.forEachIndexed { position, ingredient ->
                                                if (position > 0) HorizontalDivider(Modifier.padding(start = 48.dp, end = 16.dp), color = Mp.LineSoft)
                                                key(ingredient.id) {
                                                    val itemDrag = dragging?.takeIf { it.id == ingredient.id && it.groupId == group.id }
                                                    Box(Modifier.fillMaxWidth().zIndex(if (itemDrag == null) 0f else 1f)
                                                        .onGloballyPositioned { bounds["item_${ingredient.id}"] = it.unclippedRootBounds() }) {
                                                        CookingIngredientRow(
                                                            ingredient, strings, copy, ingredient.id in cartIds, ingredient.id in menuIds,
                                                            Modifier.graphicsLayer { translationY = itemDrag?.let { it.top - (bounds[it.measurementKey]?.top ?: it.originalTop) } ?: 0f },
                                                            itemDrag != null, narrowScreen,
                                                            onEdit = { editor = CookingEditor.Ingredient(group.id, ingredient) },
                                                            onCount = { editor = CookingEditor.Count(ingredient) },
                                                            onDelete = { viewModel.deleteItem(ingredient.id) },
                                                            onToggleCart = { viewModel.toggleCart(ingredient.id) },
                                                            onToggleMenu = { viewModel.toggleCookingMenu(ingredient.id) },
                                                            onDragStart = { pointerRootY -> startDrag(ingredient.id, group.id, pointerRootY) },
                                                            onHandlePositioned = { handleCoordinates["item_${ingredient.id}"] = it },
                                                            onDrag = { amount -> moveDrag(amount) },
                                                            onDragEnd = { finishDrag() }, onDragCancel = { cancelDrag() },
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.fillMaxWidth().background(Mp.Bg)) {
                HorizontalDivider(color = Mp.Line)
                CookingActionPair(
                    labels = listOf(copy.cartCount(cartIds.size), copy.menuCount(menuIds.size)),
                    textSize = 15.sp, textChrome = 50.dp, narrowScreen = narrowScreen,
                    singleLine = copy.english, spacing = 10.dp,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                    first = { PrimaryAction(copy.cartCount(cartIds.size), "cart_bar", true, onPurchase, it, tonal = true, icon = Icons.Outlined.ShoppingCart) },
                    second = { PrimaryAction(copy.menuCount(menuIds.size), "open_cooking_menu", true, onMenu, it, icon = Icons.Outlined.SoupKitchen) },
                )
            }
        }
    }

    when (val current = editor) {
        null -> Unit
        is CookingEditor.Group -> TextEditor(
            if (current.value == null) strings.addGroup else strings.rename, current.value?.name.orEmpty(), null, strings,
            onDismiss = { editor = null }, onSave = { name, _ ->
                if (current.value == null) viewModel.addGroup("cooking", "cooking", name) else viewModel.renameGroup(current.value.id, name)
                editor = null
            },
        )
        is CookingEditor.Ingredient -> TextEditor(
            if (current.value == null) copy.addIngredient else strings.editText, current.value?.name.orEmpty(), current.value?.notes.orEmpty(), strings,
            onDismiss = { editor = null }, onSave = { name, notes ->
                if (current.value == null) viewModel.addItem(current.groupId, name, notes.orEmpty()) else viewModel.updateItem(current.value.id, name, notes.orEmpty())
                editor = null
            },
        )
        is CookingEditor.Count -> CountEditor(current.value, strings, { editor = null }) { value -> viewModel.setCount(current.value.id, value); editor = null }
    }
}

@Composable
private fun CookingIngredientRow(
    item: FoodItem, strings: UiStrings, copy: CookingStrings, inCart: Boolean, inMenu: Boolean,
    modifier: Modifier, dragging: Boolean, narrowScreen: Boolean, onEdit: () -> Unit, onCount: () -> Unit,
    onDelete: () -> Unit, onToggleCart: () -> Unit, onToggleMenu: () -> Unit, onDragStart: (Float) -> Unit, onHandlePositioned: (LayoutCoordinates) -> Unit,
    onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
) {
    Surface(modifier.fillMaxWidth().testTag("item_${item.id}"), shape = RoundedCornerShape(14.dp),
        color = if (dragging) Mp.JadeTint else Mp.Card, contentColor = Mp.Ink,
        shadowElevation = if (dragging) 4.dp else 0.dp) {
        Column(Modifier.padding(end = 4.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CookingDragHandle("drag_${item.id}", strings, onDragStart, onHandlePositioned, onDrag, onDragEnd, onDragCancel)
                Column(Modifier.weight(1f).heightIn(min = 52.dp).padding(vertical = 10.dp).testTag("item_name_${item.id}"),
                    verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, Modifier.weight(1f, fill = false), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        CountChip(item.count, strings)
                    }
                    if (item.notes.isNotBlank()) Text(item.notes, Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 19.sp, color = Mp.Ink2)
                }
                MoreMenu(strings, listOf(strings.editText to onEdit, strings.editCount to onCount, strings.delete to onDelete), "more_${item.id}")
            }
            CookingActionPair(
                // Size against both states so adding an ingredient never rearranges its buttons.
                labels = listOf(copy.addToCart, copy.addedToCart, copy.addToMenu, copy.addedToMenu),
                textSize = 14.sp, textChrome = 37.dp, narrowScreen = narrowScreen,
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 12.dp),
                first = { StepButton(
                    label = if (inCart) copy.addedToCart else copy.addToCart,
                    tag = "cart_${item.id}", done = inCart, onClick = onToggleCart,
                    actionLabel = if (inCart) copy.removeFromCart else copy.addToCart,
                    modifier = it, icon = Icons.Outlined.ShoppingCart,
                ) },
                second = { StepButton(
                    label = if (inMenu) copy.addedToMenu else copy.addToMenu,
                    tag = "menu_add_${item.id}", done = inMenu, onClick = onToggleMenu,
                    actionLabel = if (inMenu) copy.removeFromMenu else copy.addToMenu,
                    modifier = it, icon = Icons.Outlined.SoupKitchen,
                ) },
            )
        }
    }
}

/** Ingredient membership: outlined when absent, filled and checked when present; both toggle. */
@Composable
private fun StepButton(
    label: String, tag: String, done: Boolean, onClick: () -> Unit, actionLabel: String,
    modifier: Modifier, icon: ImageVector,
) {
    // 1.4.3: the toggle cross-fades its colours (150ms) instead of switching instantly; press scales 0.97.
    val spec = tween<Color>(Motion.QuickMs, easing = Motion.EaseOut)
    val container by animateColorAsState(if (done) Mp.JadeTint else Color.White, spec, label = "stepContainer")
    val content by animateColorAsState(if (done) Mp.JadeDeep else Mp.Ink, spec, label = "stepContent")
    val outline by animateColorAsState(if (done) Mp.JadeTint else Color(0xFFD1D1D6), spec, label = "stepBorder")
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = content),
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction, Motion.ButtonPressScale).heightIn(min = 48.dp).testTag(tag).semantics {
            selected = done
            onClick(label = actionLabel, action = null)
        },
    ) {
        Crossfade(targetState = done, animationSpec = tween(Motion.QuickMs, easing = Motion.EaseOut), label = "stepIcon") { checked ->
            Icon(if (checked) Icons.Filled.Check else icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(label, modifier = Modifier.testTag("${tag}_label"), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Preserve roomy Chinese rows, and stack when the actual labels cannot fit safely. */
@Composable
private fun CookingActionPair(
    labels: List<String>, textSize: TextUnit, textChrome: Dp, narrowScreen: Boolean,
    modifier: Modifier = Modifier, singleLine: Boolean = true, spacing: Dp = 8.dp,
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge.merge(TextStyle(fontSize = textSize, fontWeight = FontWeight.Bold))
    BoxWithConstraints(modifier) {
        val availableLabelWidth = with(density) { ((maxWidth - spacing) / 2f - textChrome).roundToPx() }
        val labelsFit = availableLabelWidth > 0 && labels.all { label ->
            !measurer.measure(
                text = label, style = textStyle,
                constraints = Constraints(maxWidth = availableLabelWidth),
                maxLines = if (singleLine) 1 else 2,
            ).hasVisualOverflow
        }
        if (narrowScreen || density.fontScale > 1.25f || !labelsFit) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

/** White bar pinned under scrolling content for the page's main actions. */
@Composable
private fun BottomDock(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Mp.Bg)) {
        HorizontalDivider(color = Mp.Line)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun CookingDragHandle(tag: String, strings: UiStrings, onStart: (Float) -> Unit, onHandlePositioned: (LayoutCoordinates) -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit, onCancel: () -> Unit) {
    val start by rememberUpdatedState(onStart)
    val positioned by rememberUpdatedState(onHandlePositioned)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(Modifier.size(48.dp).testTag(tag).semantics { contentDescription = strings.dragToSort }
        .onGloballyPositioned { current ->
            coordinates = current
            positioned(current)
        }
        .pointerInput(tag) {
            detectDragGesturesAfterLongPress(onDragStart = { point -> coordinates?.takeIf { it.isAttached }?.let { start(it.localToRoot(point).y) } }, onDragEnd = { end() }, onDragCancel = { cancel() }, onDrag = { change, amount -> change.consume(); move(amount.y) })
        }, contentAlignment = Alignment.Center) {
        DragDots()
    }
}

@Composable
private fun PrimaryAction(
    label: String, tag: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    tonal: Boolean = false, icon: ImageVector? = null,
) {
    PillButton(
        onClick = onClick,
        enabled = enabled,
        container = if (tonal) Mp.JadeTint else Mp.Jade,
        content = if (tonal) Mp.JadeDeep else Color.White,
        shadow = if (tonal) null else Mp.JadeShadow,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.testTag(tag),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, modifier = Modifier.testTag("${tag}_label"), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PurchaseScreen(state: MealState, viewModel: MealViewModel, onMenu: () -> Unit) {
    val strings = remember(state.language) { UiStrings(state.language) }
    val copy = remember(state.language) { CookingStrings(state.language) }
    val foodById = state.items.associateBy { it.id }
    val groupById = state.groups.associateBy { it.id }
    val cart = state.cart.sortedBy { it.position }.filter { it.itemId in foodById }
    val pending = cart.filterNot { it.purchased }
    val purchased = cart.filter { it.purchased }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("purchase_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(copy.total(cart.size), Modifier.padding(start = 4.dp), color = Mp.Ink2, fontSize = 14.sp) }
            if (cart.isEmpty()) item { EmptyMessage(copy.emptyCart) }
            if (pending.isNotEmpty()) item { SectionLabel(copy.section(copy.notPurchased, pending.size), "purchase_pending_heading") }
            items(pending, key = { it.itemId }) { entry ->
                val item = foodById.getValue(entry.itemId)
                // 1.4.3: same key in both sections, so checking an item slides it to its new place.
                Box(Modifier.animateItem(placementSpec = listMove())) {
                    ShoppingIngredientCard(item, groupById[item.groupId]?.name.orEmpty(), false, strings, copy,
                        onPurchased = { viewModel.setPurchased(item.id, it) },
                        onRemove = { viewModel.removeFromCart(item.id) },
                        onTransfer = { viewModel.transferPurchasedToMenu(item.id) })
                }
            }
            item(key = "purchased_heading") { SectionLabel(copy.section(copy.purchased, purchased.size), "purchase_done_heading") }
            item(key = "transfer_all") {
                val enabled = purchased.isNotEmpty()
                // White (4.7:1 on the accent fill); the old pale yellow fell below 4.5:1 on the new accent.
                val emphasis = if (enabled) Color.White else Mp.DisabledInk
                PillButton(onClick = { viewModel.transferPurchasedToMenu() }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).testTag("transfer_all"),
                    minHeight = 56.dp) {
                    Text(buildAnnotatedString {
                        append(if (copy.english) "Add all " else "把已采购的")
                        withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.ExtraBold)) { append(purchased.size.toString()) }
                        append(if (copy.english) " purchased ingredients to cooking menu" else "个食材全部加入烹饪菜单")
                    }, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp).testTag("transfer_all_text"))
                }
            }
            items(purchased, key = { it.itemId }) { entry ->
                val item = foodById.getValue(entry.itemId)
                Box(Modifier.animateItem(placementSpec = listMove())) {
                    ShoppingIngredientCard(item, groupById[item.groupId]?.name.orEmpty(), true, strings, copy,
                        onPurchased = { viewModel.setPurchased(item.id, it) },
                        onRemove = { viewModel.removeFromCart(item.id) },
                        onTransfer = { viewModel.transferPurchasedToMenu(item.id) })
                }
            }
        }
        BottomDock {
            PrimaryAction(copy.enterMenu, "open_cooking_menu", true, onMenu, Modifier.weight(1f), icon = Icons.Outlined.SoupKitchen)
        }
    }
}

/**
 * "本餐完成" happens once per meal, so it gets the one small celebration in the app (Emil's
 * delight tier): the badge grows in from 0.85 (never from zero), a soft ring spreads once and
 * fades, a check pops on, the text rises in, plus one confirm haptic. Reduced motion: fades only.
 */
@Composable
private fun RecordedBadge(copy: CookingStrings) {
    val view = LocalView.current
    val reduced = remember { Motion.reduced() }
    val badge = remember { Animatable(if (reduced) 1f else 0f) }
    val ring = remember { Animatable(if (reduced) 1f else 0f) }
    val check = remember { Animatable(if (reduced) 1f else 0f) }
    val label = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        Haptics.confirm(view)
        if (reduced) {
            label.animateTo(1f, tween(Motion.QuickMs))
            return@LaunchedEffect
        }
        launch { badge.animateTo(1f, tween(320, easing = Motion.EaseOut)) }
        launch { ring.animateTo(1f, tween(560, easing = Motion.EaseOut)) }
        launch { delay(180); check.animateTo(1f, tween(220, easing = Motion.EaseOut)) }
        launch { delay(120); label.animateTo(1f, tween(Motion.StandardMs, easing = Motion.EaseOut)) }
    }
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        // One spreading ring behind the badge.
        Box(Modifier.size(84.dp).graphicsLayer {
            val r = ring.value
            scaleX = 1f + .4f * r
            scaleY = scaleX
            alpha = if (reduced) 0f else .45f * (1f - r)
        }.background(Mp.JadeTint, CircleShape))
        Box(Modifier.size(84.dp).graphicsLayer {
            val b = badge.value
            scaleX = .85f + .15f * b
            scaleY = scaleX
            alpha = b
        }.background(Mp.JadeTint, CircleShape), contentAlignment = Alignment.Center) {
            Image(Art.cooking, contentDescription = null, modifier = Modifier.size(64.dp))
        }
        Box(Modifier.align(Alignment.Center).padding(start = 58.dp, top = 58.dp).size(28.dp).graphicsLayer {
            val c = check.value
            scaleX = .6f + .4f * c
            scaleY = scaleX
            alpha = c
        }.background(Mp.Jade, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(copy.recorded, Modifier.testTag("meal_recorded").graphicsLayer {
        val t = label.value
        alpha = t
        translationY = if (reduced) 0f else (1f - t) * 8.dp.toPx()
    }, fontSize = 22.sp, color = Mp.JadeDeep, fontWeight = FontWeight.Bold)
}

/** List reorder (an item moving between sections): it travels on screen, so ease-in-out; none under reduced motion. */
private fun listMove(): FiniteAnimationSpec<IntOffset>? =
    if (Motion.reduced()) null else tween(Motion.StandardMs + 40, easing = Motion.EaseInOut)

@Composable
private fun SectionLabel(label: String, tag: String) {
    Text(label, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp).testTag(tag),
        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Mp.Ink)
}

@Composable
private fun ShoppingIngredientCard(
    item: FoodItem, groupName: String, purchased: Boolean, strings: UiStrings, copy: CookingStrings,
    onPurchased: (Boolean) -> Unit, onRemove: () -> Unit, onTransfer: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth().testTag("purchase_item_${item.id}"), color = Mp.Card, contentColor = Mp.Ink,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.Transparent)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 12.dp, end = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(item.name, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                    Text(groupName, Modifier.padding(top = 2.dp), fontSize = 13.sp, color = Mp.Ink2)
                }
                MoreMenu(strings, listOf((if (purchased) copy.removeFromCart else copy.removeFromPending) to onRemove), "purchase_more_${item.id}")
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("purchase_check_${item.id}")
                .toggleable(value = purchased, role = Role.Checkbox, onValueChange = onPurchased)
                .semantics { contentDescription = item.name + ", " + copy.purchased }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(purchased, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp),
                    colors = CheckboxDefaults.colors(checkedColor = Mp.Jade, uncheckedColor = Mp.IconTint, checkmarkColor = Color.White))
                Text(copy.purchased, fontSize = 15.sp)
            }
            if (purchased) PrimaryAction(copy.addToMenu, "transfer_${item.id}", true, onTransfer, Modifier.fillMaxWidth().padding(end = 8.dp))
        }
    }
}

@Composable
internal fun CookingMenuScreen(state: MealState, viewModel: MealViewModel, onChooseIngredients: () -> Unit) {
    val strings = remember(state.language) { UiStrings(state.language) }
    val copy = remember(state.language) { CookingStrings(state.language) }
    val foodById = state.items.associateBy { it.id }
    val groupById = state.groups.associateBy { it.id }
    val menu = state.menu.sortedBy { it.position }.filter { it.itemId in foodById }
    val pending = menu.filterNot { it.completed }
    val completed = menu.filter { it.completed }
    var submitting by remember { mutableStateOf(false) }
    var recorded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<PurchaseResult?>(null) }
    val canComplete = menu.isNotEmpty() && pending.isEmpty() && state.menuSessionId != null && !submitting

    LaunchedEffect(menu.isNotEmpty()) { if (menu.isNotEmpty()) recorded = false }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("cooking_menu_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (menu.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (recorded) RecordedBadge(copy)
                    EmptyMessage(copy.emptyMenu)
                    PrimaryAction(copy.chooseIngredients, "choose_ingredients", true, onChooseIngredients)
                }
            } else {
                item { Text(copy.mealProgress(menu.size, completed.size), Modifier.padding(start = 4.dp).testTag("cooking_menu_progress"),
                    color = Mp.Ink2, fontSize = 14.sp) }
                if (pending.isNotEmpty()) item { SectionLabel(copy.pending, "menu_pending_heading") }
                items(pending, key = { it.itemId }) { entry ->
                    val item = foodById.getValue(entry.itemId)
                    Box(Modifier.animateItem(placementSpec = listMove())) {
                        CookingMenuCard(item, groupById[item.groupId]?.name.orEmpty(), false, !submitting, strings, copy,
                            onCompleted = { viewModel.setCookingCompleted(item.id, it) }, onRemove = { viewModel.removeFromCookingMenu(item.id) })
                    }
                }
                if (completed.isNotEmpty()) item { SectionLabel(copy.completed, "menu_done_heading") }
                items(completed, key = { it.itemId }) { entry ->
                    val item = foodById.getValue(entry.itemId)
                    Box(Modifier.animateItem(placementSpec = listMove())) {
                        CookingMenuCard(item, groupById[item.groupId]?.name.orEmpty(), true, !submitting, strings, copy,
                            onCompleted = { viewModel.setCookingCompleted(item.id, it) }, onRemove = { viewModel.removeFromCookingMenu(item.id) })
                    }
                }
            }
        }
        if (menu.isNotEmpty()) {
            HorizontalDivider(color = Mp.Line)
            Column(Modifier.fillMaxWidth().background(Mp.Bg).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.finishHint, fontSize = 13.sp, color = Mp.Ink2)
                PrimaryAction(copy.completeMeal, "complete_meal", canComplete, {
                    val session = state.menuSessionId
                    if (!submitting && session != null) {
                        submitting = true
                        viewModel.completeCookingMeal(session) { result ->
                            submitting = false
                            if (result == PurchaseResult.Completed) recorded = true else error = result
                        }
                    }
                }, Modifier.fillMaxWidth())
            }
        }
    }
    error?.let { result ->
        AlertDialog(onDismissRequest = { error = null }, modifier = Modifier.testTag("cooking_menu_error"), title = { Text(copy.errorTitle) },
            text = { Text(when (result) { PurchaseResult.LimitReached -> copy.limitMessage; PurchaseResult.Incomplete -> copy.incompleteMessage; PurchaseResult.Failed -> copy.failedMessage; else -> copy.staleMessage }) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(copy.gotIt) } })
    }
}

@Composable
private fun CookingMenuCard(
    item: FoodItem, groupName: String, completed: Boolean, enabled: Boolean, strings: UiStrings, copy: CookingStrings,
    onCompleted: (Boolean) -> Unit, onRemove: () -> Unit,
) {
    val background = if (completed) Mp.Jade else Mp.Card
    val foreground = if (completed) Color.White else Mp.Ink
    val secondary = if (completed) Color.White.copy(alpha = .8f) else Mp.Ink2
    var moreExpanded by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("cooking_menu_item_${item.id}")
        .semantics { stateDescription = if (completed) copy.completed else copy.pending },
        color = background, contentColor = foreground, shape = RoundedCornerShape(20.dp),
        border = null) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 12.dp, end = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(item.name, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                    Text(groupName, Modifier.padding(top = 2.dp), fontSize = 13.sp, color = secondary)
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }, enabled = enabled,
                        modifier = Modifier.size(48.dp).testTag("cooking_menu_more_${item.id}").semantics { contentDescription = strings.moreActions }) {
                        MoreGlyph(secondary)
                    }
                    DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                        DropdownMenuItem(text = { Text(copy.removeFromMenu, color = MaterialTheme.colorScheme.onSurface) },
                            enabled = enabled, onClick = { moreExpanded = false; onRemove() })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cooking_completed_${item.id}")
                .toggleable(value = completed, enabled = enabled, role = Role.Checkbox, onValueChange = onCompleted)
                .semantics { contentDescription = item.name + ", " + copy.completed }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(completed, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(end = 10.dp),
                    colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Mp.IconTint, checkmarkColor = Mp.Jade))
                Text(copy.completed, fontSize = 15.sp, color = foreground)
            }
        }
    }
}
