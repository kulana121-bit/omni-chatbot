package com.ravi.omnichat.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Smooth, bouncy springs used across the app. */
object Motion {
    val offset = spring<IntOffset>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val float = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
}

/** Squishy press: shrinks on touch and springs back with a little overshoot. Never blocks the click. */
fun Modifier.bounce(pressedScale: Float = 0.9f): Modifier = composed {
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val sc by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.4f, stiffness = 520f), label = "bounce"
    )
    this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(PointerEventPass.Initial)
                pressed = false
            }
        }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val heroBounds = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.8f, stiffness = 380f) }

/** Hero (shared element) morph between screens when both use the same key. */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.hero(key: String): Modifier = composed {
    val shared = LocalSharedScope.current
    val anim = LocalNavAnimScope.current
    if (shared == null || anim == null) this
    else with(shared) {
        this@composed.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = heroBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
}
