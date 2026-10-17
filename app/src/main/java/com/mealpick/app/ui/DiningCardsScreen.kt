package com.mealpick.app.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mealpick.app.data.FoodItem
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun DiningCardsScreen(
    items: List<FoodItem>,
    groupId: String = CARD_DINING_GROUP_ID,
    strings: UiStrings,
    draw: DiningDrawState,
    onAdd: () -> Unit,
    onNotes: (FoodItem) -> Unit,
    onPick: (FoodItem) -> Unit,
    onEdit: (FoodItem) -> Unit,
    onCount: (FoodItem) -> Unit,
    onDelete: (FoodItem) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val ids = items.map { it.id }
    var chosenBounds by remember { mutableStateOf<Rect?>(null) }
    val cardLayout = remember { DiningCardLayout() }
    var departure by remember { mutableStateOf<List<DiningCardPlacement>>(emptyList()) }
    var departureFromReveal by remember { mutableStateOf(false) }
    var revealPlacement by remember { mutableStateOf<DiningCardPlacement?>(null) }
    LaunchedEffect(ids) { draw.reconcile(ids) }
    val view = LocalView.current
    val selected = items.find { it.id == draw.selectedId }

    Column(Modifier.fillMaxSize().testTag("dining_cards_screen")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (draw.phase) {
                    DrawPhase.Browse -> strings.restaurantCount(items.size)
                    DrawPhase.Shuffling -> strings.shufflingCards
                    DrawPhase.Choosing -> strings.chooseFaceDown
                    else -> strings.yourCard
                },
                Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = Mp.Ink2,
            )
            if (!draw.active) {
                AddButton(strings.addItem, "add_item_$groupId", onAdd)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            when (draw.phase) {
                DrawPhase.Browse -> if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize().testTag("dining_card_grid"), contentAlignment = Alignment.Center) {
                        Text(strings.emptyItems, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    DiningCardGrid(items, strings, onNotes, onPick, onEdit, onCount, onDelete, onReorder, layout = cardLayout)
                }
                DrawPhase.Shuffling -> ShuffleTable(items, strings, departure, departureFromReveal, onComplete = draw::deal)
                DrawPhase.Choosing -> HiddenCards(draw.deck, strings) { index, bounds ->
                    // Synchronous guard means two taps in the same frame cannot reveal two cards.
                    if (draw.choose(index)) {
                        chosenBounds = bounds
                        Haptics.tap(view)
                    }
                }
                DrawPhase.Flipping, DrawPhase.Revealed -> if (selected != null) {
                    // The cards you did not pick step back and leave while yours lifts and travels.
                    if (draw.phase == DrawPhase.Flipping) LeavingDeck(draw.deck, draw.selectedId?.let { draw.deck.indexOf(it) } ?: -1)
                    RevealedCard(
                        item = selected, strings = strings, source = chosenBounds,
                        flipping = draw.phase == DrawPhase.Flipping,
                        onComplete = draw::reveal,
                        onPlacement = { revealPlacement = it },
                        onNotes = { if (draw.phase == DrawPhase.Revealed) onNotes(selected) },
                        onPick = { if (draw.phase == DrawPhase.Revealed) onPick(selected) },
                    )
                }
            }
        }

        Surface(color = Mp.Bg) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (draw.phase == DrawPhase.Browse || draw.phase == DrawPhase.Revealed) {
                    PillButton(
                        onClick = {
                            departureFromReveal = draw.active
                            departure = if (draw.active) listOfNotNull(revealPlacement) else cardLayout.capture()
                            chosenBounds = null
                            draw.start(ids)
                        },
                        enabled = items.isNotEmpty(),
                        container = Mp.Red,
                        shadow = null,
                        minHeight = 56.dp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            .testTag(if (draw.active) "draw_again" else "draw_restaurant"),
                    ) {
                        Icon(Icons.Outlined.Style, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (draw.active) strings.drawAgain else strings.drawRestaurant, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (draw.active) {
                    TextButton(
                        onClick = draw::reset,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("show_all"),
                        colors = ButtonDefaults.textButtonColors(contentColor = Mp.Ink2),
                    ) { Text(strings.viewAllCards, fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun HiddenCards(deck: List<String>, strings: UiStrings, onChoose: (Int, Rect?) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth < 340.dp || density.fontScale > 1.25f) 1 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("dining_hidden_grid"),
        ) {
            itemsIndexed(deck, key = { _, id -> id }) { index, _ ->
                var bounds by remember { mutableStateOf<Rect?>(null) }
                val interaction = remember { MutableInteractionSource() }
                CardBack(
                    Modifier.fillMaxWidth().height(184.dp).onGloballyPositioned { bounds = it.boundsInRoot() }
                        .pressScale(interaction, Motion.ButtonPressScale)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(interactionSource = interaction, indication = ripple(), onClickLabel = strings.turnCard) { onChoose(index, bounds) }
                        .semantics { contentDescription = strings.hiddenCard(index + 1) }
                        .testTag("hidden_card_$index"),
                )
            }
        }
    }
}

/** Decorative backs contain no restaurant identity, even in the accessibility tree. */
@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Mp.Tile,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFFEBCFC2)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            TileWall(Modifier.fillMaxSize(), tile = 22.dp)
            Box(
                Modifier.fillMaxSize().padding(8.dp)
                    .border(BorderStroke(1.5.dp, Color.White.copy(alpha = .9f)), RoundedCornerShape(14.dp)),
            )
            Box(
                Modifier.size(58.dp).rotate(-6f).background(Mp.Red, CircleShape).border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("食", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
            }
        }
    }
}

/** Timing of "抽一家" (1.4.3, approved motion draft). */
internal object DrawMotion {
    const val ShuffleMs = 1130
    const val LiftMs = 120f
    const val TravelMs = 460f
    const val SettleMs = 180f
    const val FlipMs = 760
    const val LeaveMs = 200
}

/**
 * The face-down cards you did not choose, in the same grid as [HiddenCards], fading and
 * shrinking slightly (never to zero) as they leave. Decorative: hidden from accessibility.
 */
@Composable
private fun LeavingDeck(deck: List<String>, chosenIndex: Int) {
    val density = LocalDensity.current
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (ValueAnimator.areAnimatorsEnabled()) leave.animateTo(1f, tween(DrawMotion.LeaveMs, easing = Motion.EaseOut))
        else leave.snapTo(1f)
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("dining_leaving_deck").clearAndSetSemantics { }) {
        val columns = if (maxWidth < 340.dp || density.fontScale > 1.25f) 1 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(deck, key = { _, id -> id }) { index, _ ->
                CardBack(
                    Modifier.fillMaxWidth().height(184.dp).graphicsLayer {
                        val v = leave.value
                        alpha = if (index == chosenIndex) 0f else 1f - v
                        scaleX = 1f - .04f * v
                        scaleY = scaleX
                    },
                )
            }
        }
    }
}

@Composable
private fun ShuffleTable(
    items: List<FoodItem>, strings: UiStrings, departure: List<DiningCardPlacement>,
    departureFromReveal: Boolean, onComplete: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val latestComplete by rememberUpdatedState(onComplete)
    LaunchedEffect(Unit) {
        if (ValueAnimator.areAnimatorsEnabled()) progress.animateTo(1f, tween(DrawMotion.ShuffleMs, easing = LinearEasing))
        latestComplete()
    }
    val density = LocalDensity.current
    var tableOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { tableOrigin = it.boundsInRoot().topLeft }
        .testTag("dining_shuffling").clearAndSetSemantics { contentDescription = strings.shufflingCards }) {
        val columns = if (maxWidth < 340.dp || density.fontScale > 1.25f) 1 else 2
        val cardWidth = (maxWidth - 40.dp - 12.dp * (columns - 1)) / columns
        val widthPx = with(density) { cardWidth.toPx() }
        val heightPx = with(density) { 184.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val left = with(density) { 20.dp.toPx() }
        val top = with(density) { 4.dp.toPx() }
        val centerX = with(density) { maxWidth.toPx() } / 2 - widthPx / 2
        val centerY = (with(density) { maxHeight.toPx() } / 2 - heightPx / 2).coerceAtLeast(0f)
        val visibleRows = ((with(density) { maxHeight.toPx() } + gap) / (heightPx + gap)).toInt().coerceAtLeast(1)
        val count = maxOf(departure.size, min(items.size, visibleRows * columns))
        repeat(count) { index ->
            val p = progress.value
            val source = departure.getOrNull(index)
            val finalX = left + (index % columns) * (widthPx + gap)
            val finalY = top + (index / columns) * (heightPx + gap)
            val startX = source?.let { it.bounds.left - tableOrigin.x } ?: centerX
            val startY = source?.let { it.bounds.top - tableOrigin.y } ?: centerY
            // 1.4.3 (Emil): gather with a 30ms stagger, a short riffle, then deal out one card
            // after another (50ms stagger), all with a strong ease-out. Staggers shrink when many
            // cards are on the table so the whole shuffle stays within DrawMotion.ShuffleMs.
            val elapsed = p * DrawMotion.ShuffleMs
            val gatherStep = if (count > 1) minOf(30f, 120f / (count - 1)) else 0f
            val dealStep = if (count > 1) minOf(50f, 150f / (count - 1)) else 0f
            val collect = Motion.EaseOut.transform(((elapsed - gatherStep * index) / 240f).coerceIn(0f, 1f))
            val mix = ((elapsed - 360f) / 320f).coerceIn(0f, 1f)
            val wave = sin(Motion.EaseInOut.transform(mix) * PI * 2).toFloat() * sin(mix * PI).toFloat()
            val spread = Motion.EaseOut.transform(((elapsed - 700f - dealStep * index) / 280f).coerceIn(0f, 1f))
            val side = if (index % 2 == 0) 1f else -1f
            val stackX = centerX + side * widthPx * .38f * wave + index * 1.5f
            val stackY = centerY - index * 2f
            val x = (startX + (stackX - startX) * collect) * (1 - spread) + finalX * spread
            val y = (startY + (stackY - startY) * collect) * (1 - spread) + finalY * spread
            val sourceWidth = source?.bounds?.width ?: widthPx
            val sourceHeight = source?.bounds?.height ?: heightPx
            val w = sourceWidth + (widthPx - sourceWidth) * collect
            val h = sourceHeight + (heightPx - sourceHeight) * collect
            val transform = Modifier.width(with(density) { w.toDp() }).height(with(density) { h.toDp() }).graphicsLayer {
                alpha = if (source == null) (p / .25f).coerceIn(0f, 1f) else 1f
                translationX = x
                translationY = y
                rotationZ = (index - count / 2f) * 3f * collect * (1 - spread) + side * wave * 8
                scaleX = 1f - .10f * collect * (1 - spread)
                scaleY = scaleX
            }
            if (collect < .35f && source != null) {
                // Keep the original face layout while it gathers into the deck. Remeasuring
                // a tall note into the shrinking back-card height would truncate its text.
                val frontTransform = Modifier.width(with(density) { sourceWidth.toDp() })
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height(with(density) { sourceHeight.toDp() }).graphicsLayer {
                        val shrink = 1f - .10f * collect * (1 - spread)
                        translationX = x + (w - sourceWidth) / 2
                        translationY = y + (h - sourceHeight) / 2
                        rotationZ = (index - count / 2f) * 3f * collect * (1 - spread) + side * wave * 8
                        scaleX = w / sourceWidth * shrink
                        scaleY = h / sourceHeight * shrink
                    }
                RestaurantFace(
                    source.item, strings, {}, {}, modifier = frontTransform,
                    tools = if (departureFromReveal) null else ({ RestaurantCardTools(source.item, strings) }),
                )
            } else CardBack(transform)
        }
    }
}

@Composable
private fun RevealedCard(
    item: FoodItem, strings: UiStrings, source: Rect?, flipping: Boolean,
    onComplete: () -> Unit, onPlacement: (DiningCardPlacement) -> Unit, onNotes: () -> Unit, onPick: () -> Unit,
) {
    val progress = remember(item.id) { Animatable(if (flipping) 0f else 1f) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    val latestComplete by rememberUpdatedState(onComplete)
    val view = LocalView.current
    LaunchedEffect(item.id) {
        if (flipping) {
            if (ValueAnimator.areAnimatorsEnabled()) progress.animateTo(1f, tween(DrawMotion.FlipMs, easing = LinearEasing))
            else progress.snapTo(1f)
            Haptics.confirm(view)
            latestComplete()
        }
    }
    Box(
        Modifier.fillMaxSize().testTag(if (flipping) "dining_flipping" else "dining_reveal"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("dining_reveal_scroll")
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().onGloballyPositioned {
                // boundsInRoot is clipped by the scroll viewport, unlike the card itself.
                // Use its full bounds so long notes do not distort the flip or next shuffle.
                val fullBounds = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                targetBounds = fullBounds
                onPlacement(DiningCardPlacement(item, fullBounds))
            }) {
                // 1.4.3 (Emil): lift 120ms (ease-out) -> travel + flip 460ms (ease-in-out, it moves
                // on screen) -> settle 180ms (ease-out). One linear clock drives all three phases.
                val elapsed = progress.value * DrawMotion.FlipMs
                val lift = Motion.EaseOut.transform((elapsed / DrawMotion.LiftMs).coerceIn(0f, 1f))
                val p = Motion.EaseInOut.transform(((elapsed - DrawMotion.LiftMs) / DrawMotion.TravelMs).coerceIn(0f, 1f))
                val settle = Motion.EaseOut.transform(((elapsed - DrawMotion.LiftMs - DrawMotion.TravelMs) / DrawMotion.SettleMs).coerceIn(0f, 1f))
                Box(Modifier.graphicsLayer {
                    val extra = 1f + .05f * lift * (1 - p) + .033f * p * (1 - settle)
                    val target = targetBounds
                    if (source != null && target != null && target.width > 0 && target.height > 0) {
                        translationX = (source.center.x - target.center.x) * (1 - p)
                        translationY = (source.center.y - target.center.y) * (1 - p)
                        scaleX = (source.width / target.width + (1 - source.width / target.width) * p) * extra
                        scaleY = (source.height / target.height + (1 - source.height / target.height) * p) * extra
                    } else {
                        scaleX = extra
                        scaleY = extra
                    }
                    rotationY = if (p < .5f) 180 * p else -180 + 180 * p
                    cameraDistance = 18 * density
                }.then(if (flipping) Modifier.clearAndSetSemantics { } else Modifier)) {
                    RestaurantFace(
                        item, strings, onNotes, onPick,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 276.dp).graphicsLayer { alpha = if (p >= .5f) 1f else 0f },
                    )
                    if (p < .5f) CardBack(Modifier.matchParentSize())
                }
            }
        }
    }
}
