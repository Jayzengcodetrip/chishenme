package com.mealpick.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mealpick.app.data.FoodGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** The wheel begins at twelve o'clock and advances clockwise. */
object WheelMath {
    fun sweepDegrees(optionCount: Int): Float =
        if (optionCount > 0) 360f / optionCount else 0f

    fun normalizeDegrees(angle: Float): Float {
        require(angle.isFinite())
        return ((angle % 360f) + 360f) % 360f
    }

    /** Angles use Canvas coordinates: zero points right, positive angles go clockwise. */
    fun sectorAtAngle(angleDegrees: Float, optionCount: Int): Int? {
        if (optionCount <= 0 || !angleDegrees.isFinite()) return null
        val angleFromTop = normalizeDegrees(angleDegrees + 90f)
        return (angleFromTop / sweepDegrees(optionCount)).toInt().coerceIn(0, optionCount - 1)
    }

    fun sectorAtPoint(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        radius: Float,
        optionCount: Int,
    ): Int? {
        if (optionCount <= 0 || radius <= 0f || !radius.isFinite()) return null
        val dx = x - centerX
        val dy = y - centerY
        val distance = hypot(dx, dy)
        if (!distance.isFinite() || distance > radius || distance == 0f) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return sectorAtAngle(angle, optionCount)
    }

    fun centerAngleDegrees(index: Int, optionCount: Int): Float {
        require(optionCount > 0 && index in 0 until optionCount)
        return -90f + (index + 0.5f) * sweepDegrees(optionCount)
    }

    /** Pointer rotation is measured clockwise from its initial upward direction. */
    fun targetRotationDegrees(
        currentRotation: Float,
        index: Int,
        optionCount: Int,
        fullTurns: Int = 4,
    ): Float {
        require(fullTurns >= 0)
        val centerRotation = centerAngleDegrees(index, optionCount) + 90f
        return currentRotation + fullTurns * 360f +
            normalizeDegrees(centerRotation - normalizeDegrees(currentRotation))
    }

    /** A single bounded uniform draw makes probability independent of labels or Pick counts. */
    fun pickIndex(
        optionCount: Int,
        drawIndex: (Int) -> Int = { Random.nextInt(it) },
    ): Int? {
        if (optionCount <= 0) return null
        val selected = drawIndex(optionCount)
        require(selected in 0 until optionCount)
        return selected
    }

    /**
     * Colour slot for a sector from a small repeating palette. Neighbours, including the
     * last and first sectors that meet at twelve o'clock, never share a slot.
     */
    fun sectorColorIndex(index: Int, optionCount: Int, paletteSize: Int = 3): Int {
        require(paletteSize >= 3 && optionCount > 0 && index in 0 until optionCount)
        val slot = index % paletteSize
        return if (optionCount > 1 && index == optionCount - 1 && slot == 0) 1 else slot
    }
}

/** Timing of the "帮我选" spin (1.4.3, approved motion draft). */
internal object WheelMotion {
    const val SpinMs = 2000
    const val SettleMs = 260
    const val HubMs = 200
}

// Things palette: white, accent tint and warm yellow tint; neighbours never share a slot.
private val SectorFills = listOf(Color.White, Color(0xFFFBEDE7), Color(0xFFFDF3DC))

@Composable
fun WheelSection(
    title: String,
    options: List<FoodGroup>,
    helpLabel: String,
    editLabel: String,
    emptyLabel: String,
    onEdit: () -> Unit,
    onSelect: (FoodGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    // Centre hub "landed" pulse (0.9 -> 1) and the haptic view; see Motion.kt.
    val hubScale = remember { Animatable(1f) }
    val view = LocalView.current
    var spinJob by remember { mutableStateOf<Job?>(null) }
    var spinning by remember { mutableStateOf(false) }
    var spinGeneration by remember { mutableIntStateOf(0) }
    val latestOnSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val textColor = Mp.Ink
    val labelSize = if (options.size > 12) 12.sp else 15.sp
    val labelPaint = remember(density, labelSize) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = with(density) { labelSize.toPx() }
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }
    val stopSpin: () -> Unit = {
        spinGeneration += 1
        spinJob?.cancel()
        spinJob = null
        spinning = false
    }

    // One light haptic tick per sector the pointer crosses while spinning (like a detent).
    LaunchedEffect(spinning, options.size) {
        if (!spinning || options.size < 2) return@LaunchedEffect
        snapshotFlow { WheelMath.sectorAtAngle(rotation.value - 90f, options.size) }
            .distinctUntilChanged()
            .drop(1)
            .collect { Haptics.tick(view) }
    }

    // Changing the partition invalidates any in-flight target. Each section owns its state.
    LaunchedEffect(options.map { it.id }) {
        stopSpin()
        rotation.snapTo(0f)
        hubScale.snapTo(1f)
    }

    MealCard(modifier = modifier.fillMaxWidth(), radius = 24.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                TextButton(
                    onClick = {
                        stopSpin()
                        onEdit()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Mp.Ink2),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(editLabel, fontSize = 13.sp)
                }
            }

            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics {
                        contentDescription = title
                        customActions = options.map { option ->
                            CustomAccessibilityAction(option.name) {
                                stopSpin()
                                latestOnSelect(option)
                                true
                            }
                        }
                    },
            ) {
                // The partition and horizontal labels never rotate.
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(options) {
                            detectTapGestures { tap ->
                                val radius = min(size.width, size.height) / 2f - 2.dp.toPx()
                                WheelMath.sectorAtPoint(
                                    x = tap.x,
                                    y = tap.y,
                                    centerX = size.width / 2f,
                                    centerY = size.height / 2f,
                                    radius = radius,
                                    optionCount = options.size,
                                )?.let { index ->
                                    stopSpin()
                                    latestOnSelect(options[index])
                                }
                            }
                        },
                ) {
                    val outer = size.minDimension / 2f
                    val rimWidth = outer * 0.093f
                    val rimRadius = outer - rimWidth / 2f
                    val radius = outer - rimWidth
                    if (options.isEmpty()) {
                        drawCircle(Color.White, radius = rimRadius, style = Stroke(rimWidth))
                        drawCircle(Color(0xFFFBFAF9), radius = radius)
                        drawCircle(
                            Color(0xFFD1D1D6), radius = radius,
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))),
                        )
                        return@Canvas
                    }
                    val circleTopLeft = center - Offset(radius, radius)
                    val circleSize = Size(radius * 2f, radius * 2f)
                    val sweep = WheelMath.sweepDegrees(options.size)
                    // Flat rim: a white band with one hairline, no studs. Geometry is unchanged.
                    drawCircle(Color.White, radius = rimRadius, style = Stroke(rimWidth))
                    drawCircle(Mp.Line, radius = outer - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                    options.forEachIndexed { index, _ ->
                        drawArc(
                            color = SectorFills[WheelMath.sectorColorIndex(index, options.size, SectorFills.size)],
                            startAngle = -90f + index * sweep,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = circleTopLeft,
                            size = circleSize,
                        )
                    }
                    if (options.size > 1) {
                        options.forEachIndexed { index, _ ->
                            val angle = Math.toRadians((-90f + index * sweep).toDouble())
                            drawLine(
                                Color.White, center,
                                center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius),
                                strokeWidth = 2.5.dp.toPx(),
                            )
                        }
                    }
                    drawCircle(Mp.Line, radius = radius, style = Stroke(1.dp.toPx()))

                    val labelRadius = radius * 0.65f
                    val chordWidth = if (options.size <= 2) {
                        98.dp.toPx()
                    } else {
                        2f * labelRadius * sin(Math.toRadians(sweep.toDouble() / 2)).toFloat() * 0.92f
                    }
                    val labelWidth = min(98.dp.toPx(), chordWidth).toInt().coerceAtLeast(1)
                    options.forEachIndexed { index, option ->
                        val angle = Math.toRadians(WheelMath.centerAngleDegrees(index, options.size).toDouble())
                        val labelCenter = center + Offset(
                            cos(angle).toFloat() * labelRadius,
                            sin(angle).toFloat() * labelRadius,
                        )
                        val layout = StaticLayout.Builder
                            .obtain(option.name, 0, option.name.length, labelPaint, labelWidth)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setMaxLines(2)
                            .setEllipsize(TextUtils.TruncateAt.END)
                            .setIncludePad(false)
                            .build()
                        drawContext.canvas.nativeCanvas.apply {
                            save()
                            translate(labelCenter.x - labelWidth / 2f, labelCenter.y - layout.height / 2f)
                            layout.draw(this)
                            restore()
                        }
                    }
                }

                if (options.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center).widthIn(max = 190.dp).padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = Mp.IconTint, modifier = Modifier.size(32.dp))
                        Text(emptyLabel, fontSize = 14.sp, lineHeight = 20.sp, color = Mp.Ink2, textAlign = TextAlign.Center)
                    }
                } else {
                    // Only this layer reads the animated rotation; no selection UI is added.
                    Canvas(Modifier.matchParentSize()) {
                        val outer = size.minDimension / 2f
                        val radius = outer - outer * 0.093f
                        val unit = radius / 136f
                        rotate(rotation.value, pivot = center) {
                            val pointer = Path().apply {
                                moveTo(center.x, center.y - 110f * unit)
                                lineTo(center.x + 7.5f * unit, center.y - 9f * unit)
                                lineTo(center.x, center.y + 16f * unit)
                                lineTo(center.x - 7.5f * unit, center.y - 9f * unit)
                                close()
                            }
                            drawPath(pointer, Mp.Red)
                            drawPath(pointer, Color.White, style = Stroke(2.5f * unit, join = StrokeJoin.Round))
                        }
                        val hub = hubScale.value
                        drawCircle(Color.White, radius = 17f * unit * hub)
                        drawCircle(Mp.Line, radius = 17f * unit * hub, style = Stroke(1.5f * unit))
                        drawCircle(Mp.Red, radius = 5.5f * unit * hub)
                    }
                }
            }

            val buttonAlpha by animateFloatAsState(
                if (spinning) 0.55f else 1f, tween(Motion.QuickMs, easing = Motion.EaseOut), label = "spinButton",
            )
            PillButton(
                onClick = {
                    if (!spinning) {
                        val chosen = WheelMath.pickIndex(options.size)
                        if (chosen != null) {
                            spinning = true
                            spinGeneration += 1
                            val generation = spinGeneration
                            spinJob = scope.launch {
                                try {
                                    hubScale.snapTo(1f)
                                    // Equivalent orientation, bounded values even after many spins.
                                    rotation.snapTo(WheelMath.normalizeDegrees(rotation.value))
                                    val target = WheelMath.targetRotationDegrees(
                                        currentRotation = rotation.value,
                                        index = chosen,
                                        optionCount = options.size,
                                    )
                                    if (Motion.reduced()) {
                                        // Reduced motion: no travel, the pointer is simply placed.
                                        rotation.snapTo(target)
                                    } else {
                                        // 1) Leaves at full speed the instant you tap (no ease-in), strong
                                        //    ease-out through the sectors, coasting a few degrees past the slot.
                                        val overshoot = minOf(5f, WheelMath.sweepDegrees(options.size) * 0.12f)
                                        rotation.animateTo(target + overshoot, tween(WheelMotion.SpinMs, easing = Motion.EaseOut))
                                        // 2) Settles back into the slot like a detent.
                                        rotation.animateTo(target, tween(WheelMotion.SettleMs, easing = Motion.EaseInOut))
                                        // 3) "Landed": one confirm haptic and a small hub pulse. No highlight, no text.
                                        Haptics.confirm(view)
                                        hubScale.snapTo(0.9f)
                                        hubScale.animateTo(1f, tween(WheelMotion.HubMs, easing = Motion.EaseOut))
                                    }
                                } finally {
                                    if (generation == spinGeneration) spinning = false
                                }
                            }
                        }
                    }
                },
                enabled = options.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).graphicsLayer { alpha = buttonAlpha },
                container = Mp.Jade,
                content = Color.White,
                shadow = null,
            ) {
                Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(helpLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
