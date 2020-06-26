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

import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.Stable
import androidx.compose.StructurallyEqual
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.onCommit
import androidx.compose.remember
import androidx.compose.setValue
import androidx.compose.staticAmbientOf
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.ViewAmbient
import androidx.ui.core.composed
import androidx.ui.layout.absolutePadding
import androidx.ui.layout.height
import androidx.ui.layout.width
import androidx.ui.unit.Density
import androidx.ui.unit.dp

/**
 * Main holder of our inset values.
 */
@Stable
class DisplayInsets {
    val systemBars by lazy(LazyThreadSafetyMode.NONE) { Insets() }
}

@Stable
class Insets {
    var left by mutableStateOf(0.dp, StructurallyEqual)
        internal set
    var top by mutableStateOf(0.dp, StructurallyEqual)
        internal set
    var right by mutableStateOf(0.dp, StructurallyEqual)
        internal set
    var bottom by mutableStateOf(0.dp, StructurallyEqual)
        internal set

    /**
     * TODO: doesn't currently work
     */
    var visible by mutableStateOf(true)
        internal set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Insets
        if (left != other.left) return false
        if (top != other.top) return false
        if (right != other.right) return false
        if (bottom != other.bottom) return false
        if (visible != other.visible) return false
        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        result = 31 * result + visible.hashCode()
        return result
    }
}

val InsetsAmbient = staticAmbientOf<DisplayInsets>()

@Composable
fun ProvideInsets(content: @Composable () -> Unit) {
    val view = ViewAmbient.current
    val density = DensityAmbient.current

    val displayInsets = remember { DisplayInsets() }

    onCommit(view) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            displayInsets.systemBars.updateFrom(windowInsets.systemWindowInsets, density)

            // Return the unconsumed insets
            windowInsets
        }
        view.doOnAttach { it.requestApplyInsets() }

        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
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
) = absolutePadding(
    left = if (left) insets.left else 0.dp,
    top = if (top) insets.top else 0.dp,
    right = if (right) insets.right else 0.dp,
    bottom = if (bottom) insets.bottom else 0.dp
)

/**
 * Updates our mutable state backed [Insets] from an Android system insets.
 */
private fun Insets.updateFrom(
    insets: androidx.core.graphics.Insets,
    density: Density
) = with(density) {
    left = insets.left.toDp()
    top = insets.top.toDp()
    right = insets.right.toDp()
    bottom = insets.bottom.toDp()
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
    Modifier.height(insets.systemBars.top)
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
    Modifier.height(insets.systemBars.bottom)
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
        HorizontalSide.Left -> Modifier.width(insets.systemBars.left)
        HorizontalSide.Right -> Modifier.width(insets.systemBars.right)
    }
}
