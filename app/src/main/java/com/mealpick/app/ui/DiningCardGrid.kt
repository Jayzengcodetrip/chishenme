package com.mealpick.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mealpick.app.data.FoodItem
import com.mealpick.app.data.MAX_PICK_COUNT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class DiningCardPlacement(val item: FoodItem, val bounds: Rect)

/** Captures the currently visible cards without coupling draw state to persisted order. */
internal class DiningCardLayout {
    var capture: () -> List<DiningCardPlacement> = { emptyList() }
}

private fun LayoutCoordinates.fullBoundsInRoot(): Rect =
    Rect(positionInRoot(), Size(size.width.toFloat(), size.height.toFloat()))

private data class CardDrag(
    val id: String,
    val originalOrder: List<String>,
    val size: IntSize,
    val grabOffset: Offset,
    val pointer: Offset,
) {
    val topLeft: Offset get() = pointer - grabOffset
    val center: Offset get() = topLeft + Offset(size.width / 2f, size.height / 2f)
}

/** Row-major card browsing. A drop is the only operation that persists the draft order. */
@Composable
internal fun DiningCardGrid(
    items: List<FoodItem>,
    strings: UiStrings,
    onNotes: (FoodItem) -> Unit,
    onPick: (FoodItem) -> Unit,
    onEdit: (FoodItem) -> Unit,
    onCount: (FoodItem) -> Unit,
    onDelete: (FoodItem) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    layout: DiningCardLayout? = null,
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val edgeSize = with(density) { 76.dp.toPx() }
    val sourceOrder = items.sortedBy { it.position }.map { it.id }
    val currentItems = items.associateBy { it.id }
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragging by remember { mutableStateOf<CardDrag?>(null) }
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val handleBounds = remember { mutableMapOf<String, Rect>() }
    val cardBounds = remember { mutableMapOf<String, Rect>() }
    val order = localOrder?.let { local ->
        local.filter { it in currentItems } + sourceOrder.filter { it !in local }
    } ?: sourceOrder
    val orderedItems = order.mapNotNull(currentItems::get)

    SideEffect {
        layout?.capture = {
            gridState.layoutInfo.visibleItemsInfo.mapNotNull { cell ->
                val id = cell.key as? String ?: return@mapNotNull null
                val item = currentItems[id] ?: return@mapNotNull null
                val bounds = cardBounds[id] ?: return@mapNotNull null
                DiningCardPlacement(item, bounds)
            }
        }
    }

    LaunchedEffect(sourceOrder) {
        // Keep the optimistic order while Room completes the transaction.
        if (localOrder == sourceOrder && dragging == null) localOrder = null
        if (dragging?.id?.let { it !in currentItems } == true) {
            localOrder = dragging?.originalOrder
            dragging = null
        }
        handleBounds.keys.retainAll(sourceOrder.toSet())
        cardBounds.keys.retainAll(sourceOrder.toSet())
    }

    fun reorderAtPointer() {
        val active = dragging ?: return
        val visible = gridState.layoutInfo.visibleItemsInfo
        // A second move must wait until layout reflects the previous local reorder.
        if (visible.any { order.getOrNull(it.index) != it.key }) return
        val coordinates = gridCoordinates ?: return
        val origin = coordinates.positionInRoot()
        val center = active.center.copy(y = active.center.y.coerceIn(0f, (coordinates.size.height - 1).coerceAtLeast(0).toFloat()))
        // Use each cell's settled layout slot, not its animated on-screen bounds: neighbours
        // sliding into place must never be mistaken for the slot under the lifted card.
        val target = visible.minByOrNull { cell ->
            val cellCenter = Offset(cell.offset.x + cell.size.width / 2f, cell.offset.y + cell.size.height / 2f)
            val dx = center.x - cellCenter.x
            val dy = center.y - cellCenter.y
            dx * dx + dy * dy
        } ?: return
        val targetId = target.key as? String ?: return
        if (targetId == active.id) return
        val from = order.indexOf(active.id)
        val to = order.indexOf(targetId)
        if (from < 0 || to < 0) return
        localOrder = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    val latestReorderAtPointer by rememberUpdatedState<() -> Unit> { reorderAtPointer() }
    val startDrag by rememberUpdatedState<(String, Offset) -> Boolean> { id, pointer ->
        val cell = gridState.layoutInfo.visibleItemsInfo.find { it.key == id }
        val bounds = cardBounds[id]
        val origin = gridCoordinates?.positionInRoot()
        if (cell == null || bounds == null || origin == null || dragging != null) {
            false
        } else {
            dragging = CardDrag(
                id = id,
                originalOrder = order,
                size = cell.size,
                grabOffset = pointer - (bounds.topLeft - origin),
                pointer = pointer,
            )
            true
        }
    }
    val moveDrag by rememberUpdatedState<(Offset) -> Unit> { pointer ->
        dragging = dragging?.copy(pointer = pointer)
        reorderAtPointer()
    }
    val finishDrag by rememberUpdatedState<() -> Unit> {
        if (dragging != null) onReorder(order)
        dragging = null
    }
    val cancelDrag by rememberUpdatedState<() -> Unit> {
        dragging?.let { localOrder = it.originalOrder }
        dragging = null
    }

    LaunchedEffect(dragging?.id) {
        if (dragging == null) return@LaunchedEffect
        while (isActive) {
            val active = dragging ?: break
            val top = edgeSize
            val bottom = (gridCoordinates?.size?.height ?: 0) - edgeSize
            val amount = when {
                active.pointer.y < top -> -((top - active.pointer.y) * .18f).coerceAtMost(26f)
                active.pointer.y > bottom -> ((active.pointer.y - bottom) * .18f).coerceAtMost(26f)
                else -> 0f
            }
            if (amount != 0f) {
                gridState.scrollBy(amount)
                latestReorderAtPointer()
            }
            delay(16)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .onGloballyPositioned { gridCoordinates = it }
            .pointerInput(Unit) {
                // Owning the gesture here keeps it alive when a card leaves the viewport.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = gridCoordinates?.positionInRoot() ?: return@awaitEachGesture
                    val id = handleBounds.entries.firstOrNull { it.value.contains(down.position + origin) }?.key
                        ?: return@awaitEachGesture
                    val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    if (!startDrag(id, held.position)) return@awaitEachGesture
                    try {
                        val ended = drag(held.id) { change ->
                            change.consume()
                            moveDrag(change.position)
                        }
                        if (ended) finishDrag() else cancelDrag()
                    } catch (cancelled: CancellationException) {
                        cancelDrag()
                        throw cancelled
                    }
                }
            },
    ) {
        val columns = if (maxWidth < 340.dp || density.fontScale > 1.25f) 1 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            userScrollEnabled = dragging == null,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().testTag("dining_card_grid"),
        ) {
            items(orderedItems, key = { it.id }) { item ->
                val active = dragging?.takeIf { it.id == item.id }
                if (active != null) {
                    // The lifted card lives in the overlay; this preserves its layout slot.
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(with(density) { active.size.height.toDp() })
                            .onGloballyPositioned { cardBounds[item.id] = it.fullBoundsInRoot() },
                        shape = RoundedCornerShape(20.dp),
                        color = Mp.Jade.copy(alpha = .05f),
                        border = BorderStroke(1.5.dp, Mp.Jade.copy(alpha = .25f)),
                    ) {}
                } else {
                    RestaurantFace(
                        item = item,
                        strings = strings,
                        onNotes = { if (dragging == null) onNotes(item) },
                        onPick = { if (dragging == null) onPick(item) },
                        modifier = Modifier.animateItem().onGloballyPositioned { cardBounds[item.id] = it.fullBoundsInRoot() },
                        tools = {
                            RestaurantCardTools(
                                item = item,
                                strings = strings,
                                onEdit = { if (dragging == null) onEdit(item) },
                                onCount = { if (dragging == null) onCount(item) },
                                onDelete = { if (dragging == null) onDelete(item) },
                                modifier = Modifier.onGloballyPositioned { handleBounds[item.id] = it.boundsInRoot() },
                            )
                        },
                    )
                }
            }
        }
        val active = dragging
        val lifted = active?.let { currentItems[it.id] }
        if (active != null && lifted != null) {
            RestaurantFace(
                item = lifted,
                strings = strings,
                onNotes = {},
                onPick = {},
                modifier = Modifier
                    .offset { IntOffset(active.topLeft.x.roundToInt(), active.topLeft.y.roundToInt()) }
                    .width(with(density) { active.size.width.toDp() })
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height(with(density) { active.size.height.toDp() })
                    .zIndex(2f)
                    .graphicsLayer {
                        scaleX = 1.025f
                        scaleY = 1.025f
                        shadowElevation = 10.dp.toPx()
                        shape = RoundedCornerShape(20.dp)
                    },
                tools = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CardHandle(strings, Modifier.testTag("drag_${lifted.id}"))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            MoreGlyph()
                        }
                    }
                },
            )
        }
    }
}

/** Shared front face for browsing and the larger, revealed card. */
@Composable
internal fun RestaurantFace(
    item: FoodItem,
    strings: UiStrings,
    onNotes: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    tools: (@Composable () -> Unit)? = null,
) {
    val tone = Tones.Dining
    val nameInteraction = remember { MutableInteractionSource() }
    val namePressed by nameInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (namePressed) .985f else 1f, label = "restaurantPress")
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 218.dp).testTag("item_${item.id}")
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale },
        shape = RoundedCornerShape(20.dp),
        color = Mp.Card,
        contentColor = Mp.Ink,
        border = BorderStroke(1.dp, Color.Transparent),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 12.dp, top = 4.dp, bottom = 14.dp)) {
            if (tools != null) {
                tools()
            } else {
                Spacer(Modifier.height(20.dp))
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("item_name_${item.id}")
                    .clickable(interactionSource = nameInteraction, indication = null, onClick = onNotes),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(item.name, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = Mp.Ink)
            }
            if (item.notes.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("item_notes_${item.id}")
                        .clickable(interactionSource = nameInteraction, indication = null, onClick = onNotes)
                        .padding(top = 4.dp, bottom = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // User notes remain complete; the grid and revealed card can grow and scroll.
                    Text(item.notes, fontSize = 14.sp, lineHeight = 20.sp, color = Mp.Ink2)
                }
            }
            Box(Modifier.padding(top = 8.dp).size(18.dp, 3.dp).background(Mp.Red, RoundedCornerShape(2.dp)))
            AnimatedCountChip(item.count, strings, Modifier.padding(top = 10.dp, bottom = 14.dp).testTag("count_${item.id}"))
            Spacer(Modifier.weight(1f))
            PillButton(
                // 1.4.3: Pick is pressed many times a day, so no bounce; PillButton's 0.97 press is the feedback.
                onClick = onPick,
                enabled = item.count < MAX_PICK_COUNT,
                container = Color.White,
                content = tone.ink,
                shadow = null,
                minHeight = 44.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("pick_${item.id}"),
            ) {
                Text(strings.pick, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Same tool-row geometry for browsing and temporary animation clones. */
@Composable
internal fun RestaurantCardTools(
    item: FoodItem,
    strings: UiStrings,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    onCount: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CardHandle(strings, modifier.testTag("drag_${item.id}"))
        Spacer(Modifier.weight(1f))
        CardMoreMenu(item, strings, onEdit, onCount, onDelete)
    }
}

@Composable
private fun CardHandle(strings: UiStrings, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).semantics { contentDescription = strings.dragToSort }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.padding(start = 2.dp)) { DragDots() }
    }
}

@Composable
private fun CardMoreMenu(
    item: FoodItem,
    strings: UiStrings,
    onEdit: () -> Unit,
    onCount: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp).testTag("more_${item.id}").semantics { contentDescription = strings.moreActions },
        ) {
            MoreGlyph()
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(strings.editText to onEdit, strings.editCount to onCount, strings.delete to onDelete).forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() })
            }
        }
    }
}
