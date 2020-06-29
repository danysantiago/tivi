/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NOTHING_TO_INLINE")

package app.tivi.common.compose

import android.view.View
import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.Stable
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.onCommit
import androidx.compose.remember
import androidx.compose.setValue
import androidx.compose.staticAmbientOf
import androidx.core.view.ViewCompat
import androidx.ui.core.Constraints
import androidx.ui.core.IntrinsicMeasurable
import androidx.ui.core.IntrinsicMeasureScope
import androidx.ui.core.LayoutDirection
import androidx.ui.core.LayoutModifier
import androidx.ui.core.Measurable
import androidx.ui.core.MeasureScope
import androidx.ui.core.Modifier
import androidx.ui.core.ViewAmbient
import androidx.ui.core.composed
import androidx.ui.core.enforce
import androidx.ui.core.offset
import androidx.ui.layout.height
import androidx.ui.layout.width
import kotlin.math.max
import kotlin.math.min

/**
 * Main holder of our inset values.
 */
@Stable
class DisplayInsets {
    val systemBars = Insets()
}

@Stable
class Insets {
    var left by mutableStateOf(0)
        internal set
    var top by mutableStateOf(0)
        internal set
    var right by mutableStateOf(0)
        internal set
    var bottom by mutableStateOf(0)
        internal set

    /**
     * TODO: doesn't currently work
     */
    var visible by mutableStateOf(true)
        internal set
}

val InsetsAmbient = staticAmbientOf<DisplayInsets>()

@Composable
fun ProvideDisplayInsets(content: @Composable () -> Unit) {
    val view = ViewAmbient.current

    val displayInsets = remember { DisplayInsets() }

    onCommit(view) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            displayInsets.systemBars.updateFrom(windowInsets.systemWindowInsets)

            // Return the unconsumed insets
            windowInsets
        }

        // Add an OnAttachStateChangeListener to request an inset pass each time we're attached
        // to the window
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        view.addOnAttachStateChangeListener(attachListener)

        if (view.isAttachedToWindow) {
            // If the view is already attached, we can request an inset pass now
            view.requestApplyInsets()
        }

        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
        }
    }

    Providers(InsetsAmbient provides displayInsets) {
        content()
    }
}

/**
 * Selectively apply additional space which matches the width/height of any system bars present
 * on the respective edges of the screen.
 *
 * @param enabled Whether to apply padding using the system bar dimensions on the respective edges.
 * Defaults to `true`.
 */
fun Modifier.systemBarsPadding(enabled: Boolean = true) = composed {
    insetsPadding(
        insets = InsetsAmbient.current.systemBars,
        left = enabled,
        top = enabled,
        right = enabled,
        bottom = enabled
    )
}

/**
 * Apply additional space which matches the height of the status height along the top edge
 * of the content.
 */
fun Modifier.statusBarPadding() = composed {
    insetsPadding(insets = InsetsAmbient.current.systemBars, top = true)
}

/**
 * Apply additional space which matches the height of the navigation bar height
 * along the [bottom] edge of the content, and additional space which matches the width of
 * the navigation bar on the respective [left] and [right] edges.
 *
 * @param bottom Whether to apply padding to the bottom edge, which matches the navigation bar
 * height (if present) at the bottom edge of the screen. Defaults to `true`.
 * @param left Whether to apply padding to the left edge, which matches the navigation bar width
 * (if present) on the left edge of the screen. Defaults to `true`.
 * @param right Whether to apply padding to the right edge, which matches the navigation bar width
 * (if present) on the right edge of the screen. Defaults to `true`.
 */
fun Modifier.navigationBarPadding(
    bottom: Boolean = true,
    left: Boolean = true,
    right: Boolean = true
) = composed {
    insetsPadding(
        insets = InsetsAmbient.current.systemBars,
        left = left,
        right = right,
        bottom = bottom
    )
}

/**
 * Allows conditional setting of [insets] on each dimension.
 */
private inline fun Modifier.insetsPadding(
    insets: Insets,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false
) = absolutePaddingPx(
    left = if (left) insets.left else 0,
    top = if (top) insets.top else 0,
    right = if (right) insets.right else 0,
    bottom = if (bottom) insets.bottom else 0
)

/**
 * Updates our mutable state backed [Insets] from an Android system insets.
 */
private fun Insets.updateFrom(insets: androidx.core.graphics.Insets) {
    left = insets.left
    top = insets.top
    right = insets.right
    bottom = insets.bottom
}

/**
 * Declare the height of the content to match the height of the status bar exactly.
 *
 * This is very handy when used with `Spacer` to push content below the status bar:
 * ```
 * Column {
 *     Spacer(Modifier.statusBarHeight())
 *
 *     // Content to be drawn below status bar (y-axis)
 * }
 * ```
 *
 * It's also useful when used with `Box` to draw a scrim which matches the status bar:
 * ```
 * Box(
 *     Modifier.statusBarHeight()
 *         .fillMaxWidth()
 *         .drawBackground(MaterialTheme.colors.background.copy(alpha = 0.3f)
 * )
 * ```
 *
 * Internally this uses [Modifier.height] so has the same characteristics with regards to incoming
 * layout constraints.
 */
fun Modifier.statusBarHeight() = composed {
    // TODO: Move to Android 11 WindowInsets APIs when they land in AndroidX.
    // It currently assumes that status bar == top which is probably fine, but doesn't work
    // in multi-window, etc.
    val insets = InsetsAmbient.current
    Modifier.heightPx(insets.systemBars.top)
}

/**
 * Declare the preferred height of the content to match the height of the navigation bar when present at the bottom of the screen.
 *
 * This is very handy when used with `Spacer` to push content below the navigation bar:
 * ```
 * Column {
 *     // Content to be drawn above status bar (y-axis)
 *     Spacer(Modifier.navigationBarHeight())
 * }
 * ```
 *
 * It's also useful when used with `Box` to draw a scrim which matches the navigation bar:
 * ```
 * Box(
 *     Modifier.navigationBarHeight()
 *         .fillMaxWidth()
 *         .drawBackground(MaterialTheme.colors.background.copy(alpha = 0.3f)
 * )
 * ```
 *
 * Internally this uses [Modifier.height] so has the same characteristics with regards to incoming
 * layout constraints.
 */
fun Modifier.navigationBarHeight() = composed {
    // TODO: Move to Android 11 WindowInsets APIs when they land in AndroidX.
    // It currently assumes that nav bar == bottom, which is wrong in landscape.
    // It also doesn't handle the IME correctly.
    val insets = InsetsAmbient.current
    Modifier.heightPx(insets.systemBars.bottom)
}

enum class HorizontalSide { Left, Right }

/**
 * Declare the preferred width of the content to match the width of the navigation bar,
 * on the given [side].
 *
 * This is very handy when used with `Spacer` to push content inside from any vertical
 * navigation bars (typically when the device is in landscape):
 * ```
 * Row {
 *     Spacer(Modifier.navigationBarWidth(HorizontalSide.Left))
 *
 *     // Content to be inside the navigation bars (x-axis)
 *
 *     Spacer(Modifier.navigationBarWidth(HorizontalSide.Right))
 * }
 * ```
 *
 * It's also useful when used with `Box` to draw a scrim which matches the navigation bar:
 * ```
 * Box(
 *     Modifier.navigationBarWidth(HorizontalSide.Left)
 *         .fillMaxHeight()
 *         .drawBackground(MaterialTheme.colors.background.copy(alpha = 0.3f)
 * )
 * ```
 *
 * Internally this uses [Modifier.width] so has the same characteristics with regards to incoming
 * layout constraints.
 *
 * @param side The navigation bar side to use as the source for the width.
 */
fun Modifier.navigationBarWidth(side: HorizontalSide) = composed {
    // TODO: Move to Android 11 WindowInsets APIs when they land in AndroidX.
    // It currently assumes that nav bar == left/right
    val insets = InsetsAmbient.current
    when (side) {
        HorizontalSide.Left -> Modifier.widthPx(insets.systemBars.left)
        HorizontalSide.Right -> Modifier.widthPx(insets.systemBars.right)
    }
}

fun Modifier.absolutePaddingPx(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0
) = this + PaddingPxModifier(
    start = left,
    top = top,
    end = right,
    bottom = bottom,
    rtlAware = false
)

fun Modifier.paddingPx(
    start: Int = 0,
    top: Int = 0,
    end: Int = 0,
    bottom: Int = 0
) = this + PaddingPxModifier(
    start = start,
    top = top,
    end = end,
    bottom = bottom,
    rtlAware = true
)

private data class PaddingPxModifier(
    val start: Int = 0,
    val top: Int = 0,
    val end: Int = 0,
    val bottom: Int = 0,
    val rtlAware: Boolean
) : LayoutModifier {
    init {
        require(start >= 0 && top >= 0 && end >= 0 && bottom >= 0) {
            "Padding must be non-negative"
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): MeasureScope.MeasureResult {
        val horizontal = start + end
        val vertical = top + bottom

        val placeable = measurable.measure(constraints.offset(-horizontal, -vertical))

        val width = (placeable.width + horizontal)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height + vertical)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            if (rtlAware) {
                placeable.place(start, top)
            } else {
                placeable.placeAbsolute(start, top)
            }
        }
    }
}

fun Modifier.heightPx(height: Int) = sizeInPx(minHeight = height, maxHeight = height)

fun Modifier.widthPx(width: Int) = sizeInPx(minWidth = width, maxWidth = width)

fun Modifier.sizeInPx(
    minWidth: Int = Int.MIN_VALUE,
    minHeight: Int = Int.MIN_VALUE,
    maxWidth: Int = Int.MAX_VALUE,
    maxHeight: Int = Int.MAX_VALUE
) = this + SizePxModifier(minWidth, minHeight, maxWidth, maxHeight, false)

private data class SizePxModifier(
    private val minWidth: Int = Int.MIN_VALUE,
    private val minHeight: Int = Int.MIN_VALUE,
    private val maxWidth: Int = Int.MAX_VALUE,
    private val maxHeight: Int = Int.MAX_VALUE,
    private val enforceIncoming: Boolean
) : LayoutModifier {
    private val targetConstraints
        get() = Constraints(
            minWidth = minWidth.coerceAtLeast(0),
            minHeight = minHeight.coerceAtLeast(0),
            maxWidth = maxWidth.coerceAtLeast(0),
            maxHeight = maxHeight.coerceAtLeast(0)
        )

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): MeasureScope.MeasureResult {
        val wrappedConstraints = targetConstraints.let { targetConstraints ->
            if (enforceIncoming) {
                targetConstraints.enforce(constraints)
            } else {
                val resolvedMinWidth = if (minWidth >= 0) {
                    targetConstraints.minWidth
                } else {
                    min(constraints.minWidth, targetConstraints.maxWidth)
                }
                val resolvedMaxWidth = if (maxWidth != Integer.MAX_VALUE) {
                    targetConstraints.maxWidth
                } else {
                    max(constraints.maxWidth, targetConstraints.minWidth)
                }
                val resolvedMinHeight = if (minHeight >= 0) {
                    targetConstraints.minHeight
                } else {
                    min(constraints.minHeight, targetConstraints.maxHeight)
                }
                val resolvedMaxHeight = if (maxHeight != Integer.MAX_VALUE) {
                    targetConstraints.maxHeight
                } else {
                    max(constraints.maxHeight, targetConstraints.minHeight)
                }
                Constraints(
                    resolvedMinWidth,
                    resolvedMaxWidth,
                    resolvedMinHeight,
                    resolvedMaxHeight
                )
            }
        }
        val placeable = measurable.measure(wrappedConstraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
        layoutDirection: LayoutDirection
    ) = measurable.minIntrinsicWidth(height, layoutDirection).let {
        val constraints = targetConstraints
        it.coerceIn(constraints.minWidth, constraints.maxWidth)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
        layoutDirection: LayoutDirection
    ) = measurable.maxIntrinsicWidth(height, layoutDirection).let {
        val constraints = targetConstraints
        it.coerceIn(constraints.minWidth, constraints.maxWidth)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
        layoutDirection: LayoutDirection
    ) = measurable.minIntrinsicHeight(width, layoutDirection).let {
        val constraints = targetConstraints
        it.coerceIn(constraints.minHeight, constraints.maxHeight)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
        layoutDirection: LayoutDirection
    ) = measurable.maxIntrinsicHeight(width, layoutDirection).let {
        val constraints = targetConstraints
        it.coerceIn(constraints.minHeight, constraints.maxHeight)
    }
}