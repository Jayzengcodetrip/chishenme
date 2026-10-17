package com.mealpick.app.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Motion tokens for 1.4.3, following Emil Kowalski's rules (design-system/chishenme/MASTER.md § 动效):
 * no ease-in on UI, strong ease-out for things appearing / responding, strong ease-in-out for
 * things travelling on screen, press feedback 100–160ms, UI transitions under 300ms.
 * The two "random pick" moments (wheel, card draw) are the only longer, delight-tier motions.
 */
internal object Motion {
    /** Strong ease-out: responses, entrances, deceleration. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** Strong ease-in-out: something moving from one place on screen to another. */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val PressMs = 140
    const val QuickMs = 150
    const val StandardMs = 220
    const val PageMs = 220

    /** Scale for pressable buttons and for pressable rows/cards. */
    const val ButtonPressScale = 0.97f
    const val RowPressScale = 0.98f

    /**
     * True when the user has turned animations off ("Remove animations" / animator scale 0).
     * Then we keep fades that aid comprehension and drop movement and scale.
     */
    fun reduced(): Boolean = !ValueAnimator.areAnimatorsEnabled()
}

/** Light, distinct haptics. All are no-ops when the user disabled touch feedback in system settings. */
internal object Haptics {
    /** One detent of the wheel. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** The user's own tap registered on something that matters (choosing a face-down card). */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** A random pick has landed, or a meal has been recorded. */
    fun confirm(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(constant)
    }
}

/**
 * Press feedback: scales the element while its interaction source reports a press.
 * Transform only, so layout and drag geometry are untouched. Skipped under reduced motion.
 */
@Composable
internal fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = Motion.RowPressScale): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val reduced = remember { Motion.reduced() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduced) pressed else 1f,
        animationSpec = tween(Motion.PressMs, easing = Motion.EaseOut),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
