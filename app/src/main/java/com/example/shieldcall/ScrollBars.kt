package com.example.shieldcall

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.scrollbar(state: ScrollState, bottomInset: Dp = 0.dp): Modifier {
    val color = MaterialTheme.colorScheme.onSurface
    val alpha by animateFloatAsState(if (state.isScrollInProgress) 0.7f else 0.3f, label = "scrollbar")
    return drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max > 0 && max != Int.MAX_VALUE) {
            val viewport = size.height
            val track = (viewport - bottomInset.toPx()).coerceAtLeast(24.dp.toPx())
            val thumb = (track * viewport / (viewport + max)).coerceIn(24.dp.toPx(), track)
            val top = state.value.toFloat() / max * (track - thumb)
            val w = 4.dp.toPx()
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width + 10.dp.toPx(), top),
                size = Size(w, thumb),
                cornerRadius = CornerRadius(w / 2)
            )
        }
    }
}

@Composable
fun Modifier.scrollbar(state: LazyListState, vertical: Boolean = true, bottomInset: Dp = 0.dp): Modifier {
    val color = MaterialTheme.colorScheme.onSurface
    val alpha by animateFloatAsState(if (state.isScrollInProgress) 0.7f else 0.3f, label = "scrollbar")
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isNotEmpty() && (state.canScrollForward || state.canScrollBackward)) {
            val avg = visible.sumOf { it.size }.toFloat() / visible.size
            val total = avg * info.totalItemsCount
            val viewport = if (vertical) size.height else size.width
            val track = if (vertical) (viewport - bottomInset.toPx()).coerceAtLeast(24.dp.toPx()) else viewport
            val offset = state.firstVisibleItemIndex * avg + state.firstVisibleItemScrollOffset
            val thumb = (track * viewport / total).coerceIn(24.dp.toPx(), track)
            val pos = (offset / (total - viewport).coerceAtLeast(1f)).coerceIn(0f, 1f) * (track - thumb)
            val w = 4.dp.toPx()
            if (vertical) {
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(size.width + 10.dp.toPx(), pos),
                    size = Size(w, thumb),
                    cornerRadius = CornerRadius(w / 2)
                )
            } else {
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(pos, size.height - w - 2.dp.toPx()),
                    size = Size(thumb, w),
                    cornerRadius = CornerRadius(w / 2)
                )
            }
        }
    }
}