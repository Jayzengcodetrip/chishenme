package com.mealpick.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Things" palette (1.4.2): white page, warm-grey grouped cards, one persimmon accent.
 * Role names are kept from the earlier palette so every screen restyles from this one place:
 * Jade* = primary accent, Red = signal accent, Card = grouped card fill, Bg = page.
 * Full rationale and tokens: design-system/chishenme/MASTER.md.
 */
internal object Mp {
    val Bg = Color(0xFFFFFFFF)
    val Card = Color(0xFFF7F6F4)
    val Line = Color(0xFFE6E5E2)
    val LineSoft = Color(0xFFE6E5E2)
    val Ink = Color(0xFF1C1C1E)
    val Ink2 = Color(0xFF6E6E73)
    val IconTint = Color(0xFF8E8E93)
    val Jade = Color(0xFFC9481F)
    val JadeDeep = Color(0xFF9E3815)
    val JadeShadow = Color(0xFF8A3112)
    val JadeTint = Color(0xFFFBEDE7)
    val Red = Color(0xFFC9481F)
    val RedShadow = Color(0xFF9E3815)
    val Yellow = Color(0xFFC9481F)
    val YellowShadow = Color(0xFF9E3815)
    val Tile = Color(0xFFF6E3DA)
    val Disabled = Color(0xFFEFEFF1)
    val DisabledInk = Color(0xFF8E8E93)
}

/** Scene accents: solid colour, soft tint and the readable ink used on that tint. */
internal data class SceneTone(val solid: Color, val tint: Color, val ink: Color)

internal object Tones {
    val Takeout = SceneTone(Color(0xFFE0A21A), Color(0xFFFDF3DC), Color(0xFF7A5200))
    val Dining = SceneTone(Color(0xFFC9481F), Color(0xFFFBEDE7), Color(0xFF9E3815))
    val Snacks = SceneTone(Color(0xFF8A5A3C), Color(0xFFF3ECE6), Color(0xFF6B4628))
    val Cooking = SceneTone(Color(0xFF2E7D50), Color(0xFFE6F2EA), Color(0xFF1F5C3A))

    fun forScene(scene: String): SceneTone = when (scene) {
        "dining" -> Dining
        "snacks" -> Snacks
        "cooking" -> Cooking
        else -> Takeout
    }
}

/** Every surface container is set explicitly, so menus and dialogs never fall back to lavender. */
internal val MealColorScheme = lightColorScheme(
    primary = Mp.Jade,
    onPrimary = Color.White,
    primaryContainer = Mp.JadeTint,
    onPrimaryContainer = Mp.JadeDeep,
    secondary = Mp.Ink2,
    onSecondary = Color.White,
    secondaryContainer = Mp.JadeTint,
    onSecondaryContainer = Mp.JadeDeep,
    tertiary = Mp.Red,
    onTertiary = Color.White,
    background = Mp.Bg,
    onBackground = Mp.Ink,
    surface = Color.White,
    onSurface = Mp.Ink,
    surfaceVariant = Mp.Card,
    onSurfaceVariant = Mp.Ink2,
    surfaceTint = Color.Transparent,
    outline = Color(0xFFD1D1D6),
    outlineVariant = Mp.LineSoft,
    error = Color(0xFFB3261E),
    surfaceBright = Color.White,
    surfaceDim = Mp.Card,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Mp.Card,
)

@Composable
internal fun MealTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MealColorScheme, content = content)
}

/** A grouped card: warm-grey fill on the white page, no visible border unless one is asked for. */
@Composable
internal fun MealCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    color: Color = Mp.Card,
    contentColor: Color = Mp.Ink,
    border: Color = Color.Transparent,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            modifier = modifier.pressScale(interaction),
            shape = RoundedCornerShape(radius),
            color = color,
            contentColor = contentColor,
            border = BorderStroke(1.dp, border),
            shadowElevation = elevation,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(radius),
            color = color,
            contentColor = contentColor,
            border = BorderStroke(1.dp, border),
            shadowElevation = elevation,
            content = content,
        )
    }
}

/**
 * The earlier style painted a flat "hard" shadow under pills. The Things style is flat,
 * so this is now a no-op; the signature stays so call sites and layout are unchanged.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.hardShadow(color: Color, shape: Shape = CircleShape, depth: Dp = 3.dp, enabled: Boolean = true): Modifier = this

/** Primary pill. Disabled state keeps a quiet grey instead of a washed-out accent. */
@Composable
internal fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Mp.Jade,
    content: Color = Color.White,
    shadow: Color? = Mp.JadeShadow,
    enabled: Boolean = true,
    minHeight: Dp = 52.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    label: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Mp.Disabled,
            disabledContentColor = Mp.DisabledInk,
        ),
        elevation = null,
        modifier = modifier.pressScale(interaction, Motion.ButtonPressScale).heightIn(min = minHeight)
            .hardShadow(shadow ?: Color.Transparent, enabled = enabled && shadow != null),
        content = label,
    )
}

/** Count label stays one Text node ("4次"), highlighted once something has been picked. */
@Composable
internal fun CountChip(count: Long, strings: UiStrings, modifier: Modifier = Modifier, onDark: Boolean = false) {
    if (count > 0) {
        Text(
            strings.times(count),
            modifier = modifier
                .background(if (onDark) Color.White.copy(alpha = .18f) else Mp.JadeTint, CircleShape)
                .padding(horizontal = 8.dp, vertical = 1.dp),
            color = if (onDark) Color.White else Mp.JadeDeep,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    } else {
        Text(
            strings.times(count),
            modifier = modifier,
            color = if (onDark) Color.White.copy(alpha = .8f) else Mp.Ink2,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

/** The count label rolls up when it changes (170ms in / 120ms out, ease-out); a plain fade under reduced motion. */
@Composable
internal fun AnimatedCountChip(count: Long, strings: UiStrings, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            if (Motion.reduced()) {
                fadeIn(tween(Motion.QuickMs)) togetherWith fadeOut(tween(100))
            } else {
                (slideInVertically(tween(170, easing = Motion.EaseOut)) { it / 3 } + fadeIn(tween(170, easing = Motion.EaseOut))) togetherWith
                    (slideOutVertically(tween(120, easing = Motion.EaseOut)) { -it / 3 } + fadeOut(tween(120, easing = Motion.EaseOut)))
            }
        },
        label = "count",
    ) { shown -> CountChip(shown, strings) }
}

/** Round back control: a soft grey disc in a 48dp touch target. */
@Composable
internal fun BackDisc(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Box(
            Modifier.size(40.dp).background(Mp.Card, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = description, tint = Mp.Ink, modifier = Modifier.size(26.dp))
        }
    }
}

/** Horizontal three-dot glyph for overflow menus. */
@Composable
internal fun MoreGlyph(tint: Color = Mp.IconTint) {
    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
}
