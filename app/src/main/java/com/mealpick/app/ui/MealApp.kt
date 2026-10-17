package com.mealpick.app.ui

import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mealpick.app.data.FoodGroup
import com.mealpick.app.data.FoodItem
import com.mealpick.app.data.MealState
import com.mealpick.app.data.MealViewModel
import com.mealpick.app.data.scopedSeedId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private sealed interface Page {
    data object Home : Page
    data class Ways(val meal: Meal) : Page
    data object Canteen : Page
    data object Family : Page
    data object Takeout : Page
    data object Dining : Page
    data object Snacks : Page
    data object Cooking : Page
    data object Purchase : Page
    data object CookingMenu : Page
    data object Statistics : Page
    data class ItemList(val groupId: String) : Page
    data class ManageGroups(val scene: String, val zone: String) : Page
}

/** A page plus its depth in the back stack, so the transition knows forward from back. */
private data class PageEntry(val page: Page, val depth: Int)

/**
 * Home and meal choices stay in place during a short fade. Deeper routes retain the existing
 * directional transition. Each entry owns its full viewport, including its header and insets.
 */
private fun AnimatedContentTransitionScope<PageEntry>.pageTransition(): ContentTransform {
    if (Motion.reduced()) return fadeIn(tween(Motion.QuickMs)) togetherWith fadeOut(tween(100))
    val homeAndWays = (initialState.page == Page.Home && targetState.page is Page.Ways) ||
        (initialState.page is Page.Ways && targetState.page == Page.Home)
    if (homeAndWays) {
        return fadeIn(tween(120, delayMillis = 40, easing = Motion.EaseOut)) togetherWith
            fadeOut(tween(90, easing = Motion.EaseOut))
    }
    val enter = tween<IntOffset>(Motion.PageMs, easing = Motion.EaseOut)
    val fade = tween<Float>(Motion.PageMs, easing = Motion.EaseOut)
    val quick = tween<Float>(120, easing = Motion.EaseOut)
    return if (targetState.depth >= initialState.depth) {
        (slideInHorizontally(enter) { it / 12 } + fadeIn(fade)) togetherWith fadeOut(quick)
    } else {
        (slideInHorizontally(enter) { -it / 12 } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(tween(180, easing = Motion.EaseOut)) { it / 12 } + fadeOut(quick))
    }
}

private sealed interface Editor {
    data class Group(val scene: String, val zone: String, val value: FoodGroup? = null) : Editor
    data class Item(val groupId: String, val value: FoodItem? = null) : Editor
    data class Count(val value: FoodItem) : Editor
    data class Notes(val value: FoodItem) : Editor
    data object CookingTotal : Editor
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MealApp(viewModel: MealViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = remember(state.language) { UiStrings(state.language) }
    val pages = remember { mutableStateListOf<Page>(Page.Home) }
    val pageStateHolder = rememberSaveableStateHolder()
    // Wait for saved language/data once at launch. Later meal loads must not remove navigation.
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(state.ready) { if (state.ready) initialized = true }
    val page = pages.last()
    val currentMeal = Meal.entries.first { it.key == state.mealId }
    val diningGroupId = scopedSeedId(state.mealId, CARD_DINING_GROUP_ID)
    val diningDraw = remember(page, state.mealId) { DiningDrawState() }
    var editor by remember(state.mealId) { mutableStateOf<Editor?>(null) }
    val goBack: () -> Unit = {
        // A second callback from the outgoing frame must not pop a different page.
        if (pages.last() == page) {
            if (page is Page.ItemList && page.groupId == diningGroupId && diningDraw.active) {
                diningDraw.reset()
            } else if (pages.size > 1) {
                pages.removeAt(pages.lastIndex)
                // The route owns its meal. Rebind on return even if another selection
                // happened while a child was open, rather than trusting ambient state.
                (pages.last() as? Page.Ways)?.let { viewModel.selectMeal(it.meal.key) }
            }
        }
    }

    BackHandler(enabled = pages.size > 1 && editor == null, onBack = goBack)

    MealTheme {
        Box(Modifier.fillMaxSize().background(Mp.Bg).semantics { testTagsAsResourceId = true }) {
            if (!initialized) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Mp.Jade)
            } else AnimatedContent(
                targetState = PageEntry(page, pages.size),
                contentKey = { it.page },
                transitionSpec = { pageTransition().using(null) },
                label = "page",
                modifier = Modifier.fillMaxSize(),
            ) { entry ->
                // An outgoing page retains its own header, insets and callback identity.
                val page = entry.page
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Mp.Bg,
                    topBar = {
                        if (page != Page.Home) {
                            Row(
                                Modifier.fillMaxWidth().background(Mp.Bg).statusBarsPadding().heightIn(min = 60.dp)
                                    .padding(start = 6.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BackDisc(
                                    onClick = { if (pages.last() == page) goBack() },
                                    description = strings.back,
                                    modifier = Modifier.testTag("back"),
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    if (page !is Page.Ways) {
                                        Text(
                                            strings.mealName(currentMeal),
                                            modifier = Modifier.testTag("current_meal").padding(bottom = 2.dp),
                                            color = Mp.Jade,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    Text(
                                        when (page) {
                                            Page.Home -> strings.appTitle
                                            is Page.Ways -> strings.waysTitle(page.meal)
                                            Page.Canteen -> strings.canteen
                                            Page.Family -> strings.family
                                            Page.Takeout -> strings.takeout
                                            Page.Dining -> strings.dining
                                            Page.Snacks -> strings.nightTakeout
                                            Page.Cooking -> strings.cooking
                                            Page.Purchase -> strings.shoppingList
                                            Page.CookingMenu -> strings.cookingMenu
                                            Page.Statistics -> strings.statistics
                                            is Page.ItemList -> state.groups.find { it.id == page.groupId }
                                                ?.let(strings::groupName).orEmpty()
                                            is Page.ManageGroups -> strings.editWheel
                                        },
                                        modifier = Modifier.semantics { heading() },
                                        color = Mp.Ink,
                                        fontSize = 24.sp,
                                        lineHeight = 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                ) { contentPadding ->
                    val padding = if (page == Page.Home) PaddingValues(bottom = contentPadding.calculateBottomPadding()) else contentPadding
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        if (!state.ready && page != Page.Home && page !is Page.Ways) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Mp.Jade)
                        } else {
                            // Selecting a meal must not recreate the outgoing Home scroll/press state.
                            key(if (page == Page.Home || page is Page.Ways) null else state.mealId) {
                                when (page) {
                                    Page.Home -> MealHomeScreen(
                                        strings = strings,
                                        onLanguage = viewModel::setLanguage,
                                        onMeal = { meal ->
                                            // Ignore another home-card callback queued before recomposition.
                                            if (pages.last() == Page.Home) {
                                                viewModel.selectMeal(meal.key)
                                                pages.add(Page.Ways(meal))
                                            }
                                        },
                                        onStatistics = { if (pages.last() == Page.Home) pages.add(Page.Statistics) },
                                    )
                                    is Page.Ways -> WaysScreen(strings) { way ->
                                        if (pages.last() == page) {
                                            viewModel.selectMeal(page.meal.key)
                                            pages.add(
                                                when (way) {
                                                    Way.Takeout -> if (page.meal == Meal.LateNight) Page.Snacks else Page.Takeout
                                                    Way.Dining -> Page.Dining
                                                    Way.Cooking -> Page.Cooking
                                                    Way.Canteen -> Page.Canteen
                                                    Way.Family -> Page.Family
                                                }
                                            )
                                        }
                                    }
                                    Page.Canteen -> QuoteScreen(strings, canteen = true)
                                    Page.Family -> QuoteScreen(strings, canteen = false)
                                    Page.Takeout -> Column(
                                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Spacer(Modifier.height(0.dp))
                                        listOf("nutrition", "mood").forEach { zone ->
                                            WheelSection(
                                                title = if (zone == "nutrition") strings.nutrition else strings.mood,
                                                options = state.groups.filter { it.scene == "takeout" && it.zone == zone }.sortedBy { it.position },
                                                helpLabel = strings.helpMePick,
                                                editLabel = strings.editWheel,
                                                emptyLabel = strings.emptyOptions,
                                                onEdit = { pages.add(Page.ManageGroups("takeout", zone)) },
                                                onSelect = { pages.add(Page.ItemList(it.id)) },
                                                modifier = Modifier.fillMaxWidth().testTag("wheel_section_$zone"),
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    Page.Dining -> GroupList(
                                        groups = state.groups.filter { it.scene == "dining" }.sortedBy { it.position },
                                        strings = strings,
                                        manage = false,
                                        onSelect = { pages.add(Page.ItemList(it.id)) },
                                        onAdd = { editor = Editor.Group("dining", "dining") },
                                        onRename = { editor = Editor.Group(it.scene, it.zone, it) },
                                        onDelete = { viewModel.deleteGroup(it.id) },
                                    )
                                    is Page.ManageGroups -> GroupList(
                                        groups = state.groups.filter { it.scene == page.scene && it.zone == page.zone }.sortedBy { it.position },
                                        strings = strings,
                                        manage = true,
                                        onSelect = { editor = Editor.Group(it.scene, it.zone, it) },
                                        onAdd = { editor = Editor.Group(page.scene, page.zone) },
                                        onRename = { editor = Editor.Group(it.scene, it.zone, it) },
                                        onDelete = { viewModel.deleteGroup(it.id) },
                                    )
                                    is Page.ItemList, Page.Snacks -> {
                                        val groups = if (page is Page.ItemList) {
                                            state.groups.filter { it.id == page.groupId }
                                        } else {
                                            state.groups.filter { it.scene == "snacks" }.sortedBy { it.position }
                                        }
                                        if (page is Page.ItemList && page.groupId == diningGroupId && groups.isNotEmpty()) {
                                            DiningCardsScreen(
                                                items = state.items.filter { it.groupId == page.groupId }.sortedBy { it.position },
                                                groupId = page.groupId,
                                                strings = strings,
                                                draw = diningDraw,
                                                onAdd = { editor = Editor.Item(page.groupId) },
                                                onNotes = { editor = Editor.Notes(it) },
                                                onPick = { viewModel.pick(it.id) },
                                                onEdit = { editor = Editor.Item(it.groupId, it) },
                                                onCount = { editor = Editor.Count(it) },
                                                onDelete = { viewModel.deleteItem(it.id) },
                                                onReorder = { viewModel.reorderItems(page.groupId, it) },
                                            )
                                        } else FoodLists(
                                            groups = groups,
                                            allItems = state.items,
                                            strings = strings,
                                            showGroupHeadings = page == Page.Snacks,
                                            onAdd = { editor = Editor.Item(it.id) },
                                            onEdit = { editor = Editor.Item(it.groupId, it) },
                                            onCount = { editor = Editor.Count(it) },
                                            onNotes = { editor = Editor.Notes(it) },
                                            onPick = { viewModel.pick(it.id) },
                                            onDelete = { viewModel.deleteItem(it.id) },
                                            onReorder = viewModel::reorderItems,
                                        )
                                    }
                                    Page.Cooking -> pageStateHolder.SaveableStateProvider("cooking_${state.mealId}") {
                                        CookingScreen(state, viewModel,
                                            onPurchase = { pages.add(Page.Purchase) },
                                            onMenu = { pages.add(Page.CookingMenu) })
                                    }
                                    Page.Purchase -> PurchaseScreen(state, viewModel, onMenu = { pages.add(Page.CookingMenu) })
                                    Page.CookingMenu -> CookingMenuScreen(state, viewModel, onChooseIngredients = {
                                        val cookingIndex = pages.indexOf(Page.Cooking)
                                        if (cookingIndex >= 0) {
                                            while (pages.lastIndex > cookingIndex) pages.removeAt(pages.lastIndex)
                                        } else pages.add(Page.Cooking)
                                    })
                                    Page.Statistics -> StatisticsScreen(state, strings, onMeal = viewModel::selectMeal,
                                        onEditCookingTotal = { editor = Editor.CookingTotal })
                                }
                            }
                        }
                    }
                }
            }
        }

        when (val current = editor) {
            null -> Unit
            Editor.CookingTotal -> CountValueEditor(
                initialCount = state.cookingTotal,
                title = strings.editTotal,
                name = strings.cooking,
                strings = strings,
                onDismiss = { editor = null },
                onSave = { viewModel.setCookingTotal(it); editor = null },
            )
            is Editor.Group -> TextEditor(
                title = if (current.value != null) strings.rename else if (current.scene == "dining") strings.addGroup else strings.addOption,
                initialName = current.value?.name.orEmpty(),
                initialNotes = null,
                strings = strings,
                onDismiss = { editor = null },
                onSave = { name, _ ->
                    if (current.value == null) viewModel.addGroup(current.scene, current.zone, name)
                    else viewModel.renameGroup(current.value.id, name)
                    editor = null
                },
            )
            is Editor.Item -> TextEditor(
                title = if (current.value == null) strings.addItem else strings.editText,
                initialName = current.value?.name.orEmpty(),
                initialNotes = current.value?.notes.orEmpty(),
                strings = strings,
                onDismiss = { editor = null },
                onSave = { name, notes ->
                    if (current.value == null) viewModel.addItem(current.groupId, name, notes.orEmpty())
                    else viewModel.updateItem(current.value.id, name, notes.orEmpty())
                    editor = null
                },
            )
            is Editor.Count -> CountEditor(current.value, strings, { editor = null }) { count ->
                viewModel.setCount(current.value.id, count)
                editor = null
            }
            is Editor.Notes -> NotesEditor(current.value, strings, { editor = null }) { notes ->
                viewModel.updateItem(current.value.id, current.value.name, notes)
                editor = null
            }
        }
    }
}

@Composable
private fun GroupList(
    groups: List<FoodGroup>,
    strings: UiStrings,
    manage: Boolean,
    onSelect: (FoodGroup) -> Unit,
    onAdd: () -> Unit,
    onRename: (FoodGroup) -> Unit,
    onDelete: (FoodGroup) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "add_group") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AddButton(if (manage) strings.addOption else strings.addGroup, "add_group", onAdd)
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty_groups") { EmptyMessage(if (manage) strings.emptyOptions else strings.emptyGroups) }
        }
        items(groups, key = { it.id }) { group ->
            val interaction = remember { MutableInteractionSource() }
            MealCard(modifier = Modifier.pressScale(interaction), radius = 22.dp) {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).testTag("group_${group.id}")
                            .clickable(interactionSource = interaction, indication = ripple()) { onSelect(group) }.heightIn(min = 68.dp)
                            .padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.groupName(group), Modifier.weight(1f), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Mp.Ink)
                        if (!manage) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Mp.IconTint)
                    }
                    MoreMenu(strings, listOf(strings.rename to { onRename(group) }, strings.delete to { onDelete(group) }), "group_more_${group.id}")
                }
            }
        }
    }
}

private data class DragSession(
    val itemId: String,
    val groupId: String,
    val startOffset: Int,
    val height: Int,
    val originalOrder: List<String>,
    val delta: Float = 0f,
) {
    val top: Float get() = startOffset + delta
    val bottom: Float get() = top + height
    val center: Float get() = startOffset + delta + height / 2f
}

/** One lazy list also holds both snack groups, avoiding nested vertical scrolling. */
@Composable
private fun FoodLists(
    groups: List<FoodGroup>,
    allItems: List<FoodItem>,
    strings: UiStrings,
    showGroupHeadings: Boolean,
    onAdd: (FoodGroup) -> Unit,
    onEdit: (FoodItem) -> Unit,
    onCount: (FoodItem) -> Unit,
    onNotes: (FoodItem) -> Unit,
    onPick: (FoodItem) -> Unit,
    onDelete: (FoodItem) -> Unit,
    onReorder: (String, List<String>) -> Unit,
) {
    val listState = rememberLazyListState()
    val edgeSize = with(LocalDensity.current) { 88.dp.toPx() }
    var localOrders by remember(groups.map { it.id }) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var dragging by remember(groups.map { it.id }) { mutableStateOf<DragSession?>(null) }
    val currentItems = allItems.associateBy { it.id }
    val sourceOrders = groups.associate { group ->
        group.id to allItems.filter { it.groupId == group.id }.sortedBy { it.position }.map { it.id }
    }
    // Preserve an optimistic drop until persistence catches up; reflect adds/deletes immediately.
    val orders = groups.associate { group ->
        val source = sourceOrders[group.id].orEmpty()
        val local = localOrders[group.id]
        group.id to if (local == null) source else local.filter { it in source } + source.filter { it !in local }
    }
    LaunchedEffect(sourceOrders) {
        localOrders = localOrders.filter { (groupId, order) -> sourceOrders[groupId] != order && groupId in sourceOrders }
    }

    fun reorderAtPointer(direction: Int) {
        if (direction == 0) return
        val active = dragging ?: return
        val ordered = localOrders[active.groupId] ?: sourceOrders[active.groupId].orEmpty()
        val from = ordered.indexOf(active.itemId)
        if (from < 0) return
        // Cross the next row's midpoint with the leading edge. Restricting movement
        // to the gesture direction prevents short and tall rows swapping repeatedly.
        val crossed = listState.layoutInfo.visibleItemsInfo.mapNotNull { row ->
            val rowId = row.key as? String ?: return@mapNotNull null
            val index = ordered.indexOf(rowId)
            val midpoint = row.offset + row.size / 2f
            when {
                direction > 0 && index > from && active.bottom > midpoint -> index
                direction < 0 && index in 0 until from && active.top < midpoint -> index
                else -> null
            }
        }
        val to = (if (direction > 0) crossed.maxOrNull() else crossed.minOrNull()) ?: return
        val changed = ordered.toMutableList().apply { add(to, removeAt(from)) }
        localOrders = localOrders + (active.groupId to changed)
    }
    val latestReorderAtPointer by rememberUpdatedState(::reorderAtPointer)
    LaunchedEffect(dragging?.itemId) {
        if (dragging == null) return@LaunchedEffect
        while (isActive) {
            val active = dragging ?: break
            val layout = listState.layoutInfo
            val top = layout.viewportStartOffset + edgeSize
            val bottom = layout.viewportEndOffset - edgeSize
            val activeOrder = localOrders[active.groupId] ?: sourceOrders[active.groupId].orEmpty()
            val activeIndex = activeOrder.indexOf(active.itemId)
            val amount = when {
                active.center < top && activeIndex > 0 -> -((top - active.center) * .16f).coerceAtMost(24f)
                active.center > bottom && activeIndex in 0 until activeOrder.lastIndex -> ((active.center - bottom) * .16f).coerceAtMost(24f)
                else -> 0f
            }
            if (amount != 0f) {
                listState.scrollBy(amount)
                latestReorderAtPointer(if (amount > 0f) 1 else -1)
            }
            delay(16)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("food_list"),
        userScrollEnabled = dragging == null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (groups.isEmpty()) item(key = "no_groups") { EmptyMessage(strings.emptyGroups) }
        groups.forEach { group ->
            item(key = "heading_${group.id}", contentType = "heading") {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = if (showGroupHeadings) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showGroupHeadings) {
                        val tone = Tones.forScene(group.scene)
                        Box(Modifier.size(34.dp).background(tone.tint, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            Icon(if (group.systemKey == "drink") Icons.Outlined.LocalDrink else Icons.Outlined.RamenDining,
                                contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(strings.groupName(group), Modifier.weight(1f), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Mp.Ink)
                    } else Spacer(Modifier.weight(1f))
                    AddButton(strings.addItem, "add_item_${group.id}") { onAdd(group) }
                }
            }
            val groupItems = orders[group.id].orEmpty().mapNotNull(currentItems::get)
            if (groupItems.isEmpty()) item(key = "empty_${group.id}", contentType = "empty") { EmptyMessage(strings.emptyItems) }
            items(groupItems, key = { it.id }, contentType = { "food" }) { item ->
                val active = dragging?.takeIf { it.itemId == item.id }
                val visibleOffset = listState.layoutInfo.visibleItemsInfo.find { it.key == item.id }?.offset ?: active?.startOffset ?: 0
                val translation = active?.let { it.startOffset + it.delta - visibleOffset } ?: 0f
                FoodRow(
                    item = item,
                    dining = group.scene == "dining",
                    tone = Tones.forScene(group.scene),
                    strings = strings,
                    modifier = Modifier.zIndex(if (active != null) 1f else 0f).offset { IntOffset(0, translation.roundToInt()) },
                    dragging = active != null,
                    onEdit = { onEdit(item) },
                    onCount = { onCount(item) },
                    onNotes = { onNotes(item) },
                    onPick = { onPick(item) },
                    onDelete = { onDelete(item) },
                    onDragStart = {
                        val layout = listState.layoutInfo.visibleItemsInfo.find { it.key == item.id }
                        if (layout != null) dragging = DragSession(item.id, group.id, layout.offset, layout.size, orders[group.id].orEmpty())
                    },
                    onDrag = { delta ->
                        dragging = dragging?.let { it.copy(delta = it.delta + delta) }
                        reorderAtPointer(if (delta > 0f) 1 else if (delta < 0f) -1 else 0)
                    },
                    onDragEnd = {
                        dragging?.let { session -> onReorder(session.groupId, localOrders[session.groupId] ?: session.originalOrder) }
                        dragging = null
                    },
                    onDragCancel = {
                        dragging?.let { localOrders = localOrders + (it.groupId to it.originalOrder) }
                        dragging = null
                    },
                )
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun FoodRow(
    item: FoodItem,
    dining: Boolean,
    tone: SceneTone,
    strings: UiStrings,
    modifier: Modifier = Modifier,
    dragging: Boolean,
    onEdit: () -> Unit,
    onCount: () -> Unit,
    onNotes: () -> Unit,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("item_${item.id}"),
        color = if (dragging) Mp.JadeTint else Mp.Card,
        contentColor = Mp.Ink,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (dragging) Mp.Jade.copy(alpha = .4f) else Color.Transparent),
        shadowElevation = if (dragging) 6.dp else 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(width = 40.dp, height = 48.dp).testTag("drag_${item.id}").semantics { contentDescription = strings.dragToSort }
                    .pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start() },
                            onDragEnd = { end() },
                            onDragCancel = { cancel() },
                            onDrag = { change, amount -> change.consume(); move(amount.y) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                DragDots()
            }
            Column(
                Modifier.weight(1f).testTag("item_name_${item.id}").then(if (dining) Modifier.clickable(onClick = onNotes) else Modifier)
                    .heightIn(min = 48.dp).padding(top = 10.dp, bottom = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.name, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = Mp.Ink)
                if (item.notes.isNotBlank()) {
                    Text(item.notes, Modifier.padding(top = 4.dp).testTag("item_notes_${item.id}"),
                        fontSize = 13.sp, lineHeight = 19.sp, color = Mp.Ink2)
                }
                Spacer(Modifier.height(4.dp))
                AnimatedCountChip(item.count, strings, Modifier.testTag("count_${item.id}"))
            }
            PillButton(
                onClick = onPick,
                modifier = Modifier.testTag("pick_${item.id}"),
                container = Color.White,
                content = tone.ink,
                shadow = null,
                enabled = item.count < Int.MAX_VALUE.toLong(),
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(strings.pick, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            MoreMenu(strings, listOf(strings.editText to onEdit, strings.editCount to onCount, strings.delete to onDelete), "more_${item.id}")
        }
    }
}

/** Six-dot grip shared by every reorderable row. */
@Composable
internal fun DragDots(color: androidx.compose.ui.graphics.Color = Mp.IconTint) {
    Canvas(Modifier.size(14.dp, 20.dp)) {
        repeat(3) { y -> repeat(2) { x ->
            drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(size.width * (.25f + .5f * x), size.height * (.18f + .32f * y)))
        } }
    }
}

/** Quiet "add" action: outlined plus and label in secondary ink. */
@Composable
internal fun AddButton(label: String, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
        colors = ButtonDefaults.textButtonColors(contentColor = Mp.Ink2),
    ) {
        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun MoreMenu(strings: UiStrings, actions: List<Pair<String, () -> Unit>>, tag: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(44.dp).testTag(tag).semantics { contentDescription = strings.moreActions }) {
            MoreGlyph()
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() })
            }
        }
    }
}

@Composable
internal fun EmptyMessage(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp), fontSize = 14.sp, color = Mp.Ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatisticsScreen(
    state: MealState,
    strings: UiStrings,
    onMeal: (String) -> Unit,
    onEditCookingTotal: () -> Unit,
) {
    val countByGroup = state.items.groupBy { it.groupId }.mapValues { (_, items) -> items.sumOf { it.count } }
    val scenes = listOf("takeout" to strings.takeout, "dining" to strings.dining, "snacks" to strings.snacks, "cooking" to strings.cooking)
    Column(Modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("stats_meal_selector"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Meal.entries.forEach { meal ->
                FilterChip(
                    selected = state.mealId == meal.key,
                    onClick = { onMeal(meal.key) },
                    label = { Text(strings.mealName(meal), fontSize = 13.sp) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("stats_meal_${meal.key}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Mp.Jade,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("stats_list"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "total_label") { Text(strings.totalPicks, Modifier.padding(start = 4.dp), color = Mp.Ink2, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            items(scenes, key = { "total_${it.first}" }) { (scene, label) ->
                val total = if (scene == "cooking") state.cookingTotal else state.groups.filter { it.scene == scene }.sumOf { countByGroup[it.id] ?: 0L }
                val tone = Tones.forScene(scene)
                MealCard(modifier = Modifier.testTag("stat_total_$scene"), radius = 20.dp) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(tone.solid, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Mp.Ink)
                        Spacer(Modifier.width(16.dp))
                        Text(strings.times(total), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Mp.JadeDeep,
                            modifier = Modifier.padding(end = if (scene == "cooking") 0.dp else 12.dp))
                        if (scene == "cooking") MoreMenu(strings, listOf(strings.editTotal to onEditCookingTotal), "cooking_total_more")
                    }
                }
            }
            item(key = "breakdown_label") { Text(strings.groupBreakdown, Modifier.padding(start = 4.dp, top = 18.dp), color = Mp.Ink2, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            if (state.groups.isEmpty()) item(key = "empty_stats") { EmptyMessage(strings.noStatistics) }
            scenes.forEach { (scene, label) ->
                val groups = state.groups.filter { it.scene == scene }.sortedWith(compareBy<FoodGroup> { if (it.zone == "nutrition") 0 else 1 }.thenBy { it.position })
                if (groups.isNotEmpty()) {
                    item(key = "scene_$scene") { Text(label, Modifier.padding(start = 4.dp, top = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mp.Ink) }
                    groups.forEach { group ->
                        item(key = "stat_${group.id}") {
                            Column {
                                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(strings.groupName(group), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(16.dp))
                                    Text(strings.times(countByGroup[group.id] ?: 0L), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        items(state.items.filter { it.groupId == group.id }.sortedBy { it.position }, key = { "stat_item_${it.id}" }) { item ->
                            Row(Modifier.fillMaxWidth().testTag("stat_item_${item.id}").padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(16.dp))
                                Text(strings.times(item.count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TextEditor(
    title: String,
    initialName: String,
    initialNotes: String?,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes.orEmpty()) }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text(strings.name) },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_name"), minLines = 1, maxLines = 4,
                )
                if (initialNotes != null) OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text(strings.optionalNotes) },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_notes"), minLines = 3, maxLines = 6,
                )
            }
        },
        confirmButton = { TextButton(modifier = Modifier.testTag("dialog_save"), enabled = valid, onClick = { onSave(name.trim(), if (initialNotes == null) null else notes.trim()) }) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
internal fun CountEditor(item: FoodItem, strings: UiStrings, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    CountValueEditor(item.count, strings.editCount, item.name, strings, onDismiss, onSave)
}

@Composable
private fun CountValueEditor(initialCount: Long, title: String, name: String, strings: UiStrings, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by remember { mutableStateOf(initialCount.toString()) }
    val count = value.toLongOrNull()?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 10 && it.all { character -> character in '0'..'9' }) value = it },
                    label = { Text(strings.count) },
                    singleLine = true,
                    isError = count == null,
                    supportingText = { if (count == null) Text(strings.invalidCount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_count"),
                )
            }
        },
        confirmButton = { TextButton(modifier = Modifier.testTag("dialog_save"), enabled = count != null, onClick = { count?.let(onSave) }) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun NotesEditor(item: FoodItem, strings: UiStrings, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var notes by remember(item.id) { mutableStateOf(item.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.restaurantNotes) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text(strings.notes) },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_notes"), minLines = 4, maxLines = 8,
                )
            }
        },
        confirmButton = { TextButton(modifier = Modifier.testTag("dialog_save"), onClick = { onSave(notes.trim()) }) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
