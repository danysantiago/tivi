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

package app.tivi.common.compose

import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.StructurallyEqual
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.onCommit
import androidx.compose.remember
import androidx.compose.setValue
import androidx.compose.staticAmbientOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.ViewAmbient
import androidx.ui.core.composed
import androidx.ui.layout.absolutePadding
import androidx.ui.layout.preferredHeight
import androidx.ui.layout.preferredWidth
import androidx.ui.unit.Density
import androidx.ui.unit.Dp
import androidx.ui.unit.dp

/**
 * Main holder of our inset values.
 *
 * TODO add other inset types (IME, visibility, etc)
 */
class DisplayInsets {
    var systemBars by mutableStateOf(Insets.Zero, areEquivalent = StructurallyEqual)
        internal set
}

data class Insets(
    val left: Dp = 0.dp,
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp
) {
    companion object {
        val Zero = Insets()
    }
}

val InsetsAmbient = staticAmbientOf { DisplayInsets() }

@Composable
fun ProvideInsets(children: @Composable () -> Unit) {
    val view = ViewAmbient.current
    val density = DensityAmbient.current

    val displayInsets = remember { DisplayInsets() }

    onCommit(view.id) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            displayInsets.updateFrom(insets, density)
            // Return the unconsumed insets
            insets
        }

        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    Providers(InsetsAmbient provides displayInsets, children = children)
}

fun Modifier.systemBarsPadding(all: Boolean = false) = systemBarsPadding(all, all, all, all)

fun Modifier.systemBarsPadding(
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false
) = composed {
    insetsPadding(InsetsAmbient.current.systemBars, left, top, right, bottom)
}

/**
 * Allows conditional setting of [insets] on each dimension.
 */
fun Modifier.insetsPadding(
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

private fun androidx.core.graphics.Insets.toInsets(density: Density) = with(density) {
    Insets(left.toDp(), top.toDp(), right.toDp(), bottom.toDp())
}

private fun DisplayInsets.updateFrom(windowInsets: WindowInsetsCompat, density: Density) {
    systemBars = windowInsets.systemWindowInsets.toInsets(density)
}

enum class VerticalSide { Top, Bottom }

fun Modifier.systemBarHeight(side: VerticalSide) = composed {
    val insets = InsetsAmbient.current
    when (side) {
        VerticalSide.Top -> Modifier.preferredHeight(insets.systemBars.top)
        VerticalSide.Bottom -> Modifier.preferredHeight(insets.systemBars.bottom)
    }
}

enum class HorizontalSide { Left, Right }

fun Modifier.systemBarWidth(side: HorizontalSide) = composed {
    val insets = InsetsAmbient.current
    when (side) {
        HorizontalSide.Left -> Modifier.preferredWidth(insets.systemBars.left)
        HorizontalSide.Right -> Modifier.preferredHeight(insets.systemBars.right)
    }
}
