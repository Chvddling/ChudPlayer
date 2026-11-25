package newpipe.desktop.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hold the left mouse button on a row, then drag it up or down to move it.
 * Works on a LazyColumn whose items have stable keys.
 */
class ReorderState(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null); private set
    var offset by mutableStateOf(0f); private set
    private var index = -1
    private var autoScroll: Job? = null

    fun isDragging(key: Any) = draggingKey == key

    fun start(key: Any) {
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        index = info.index
        offset = 0f
        autoScroll?.cancel()
        autoScroll = scope.launch {
            // scroll the list when the dragged row gets near the top or bottom edge
            while (isActive && draggingKey != null) {
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                if (item != null) {
                    val top = item.offset + offset
                    val bottom = top + item.size
                    val viewEnd = list.layoutInfo.viewportEndOffset
                    val step = when {
                        top < 0 -> -12f
                        bottom > viewEnd -> 12f
                        else -> 0f
                    }
                    if (step != 0f) {
                        val used = list.scrollBy(step)
                        offset += used
                        checkSwap()
                    }
                }
                delay(16)
            }
        }
    }

    fun drag(dy: Float) {
        if (draggingKey == null) return
        offset += dy
        checkSwap()
    }

    private fun checkSwap() {
        val items = list.layoutInfo.visibleItemsInfo
        val item = items.firstOrNull { it.index == index } ?: return
        val center = item.offset + offset + item.size / 2f
        val target = items.firstOrNull { it.index != index && center >= it.offset && center <= it.offset + it.size } ?: return
        onMove(index, target.index)
        offset += (item.offset - target.offset)
        index = target.index
    }

    fun end() {
        draggingKey = null
        offset = 0f
        index = -1
        autoScroll?.cancel()
    }
}

@Composable
fun rememberReorderState(list: LazyListState, onMove: (from: Int, to: Int) -> Unit): ReorderState {
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    return remember(list) { ReorderState(list, scope) { a, b -> move(a, b) } }
}

/** Put this on a row inside the LazyColumn; [key] must be the row's item key. */
fun Modifier.reorderable(state: ReorderState, key: Any): Modifier {
    val dragging = state.isDragging(key)
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer { translationY = if (dragging) state.offset else 0f }
        .then(if (dragging) Modifier.shadow(8.dp) else Modifier)
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(key) },
                onDrag = { change, amount -> change.consume(); state.drag(amount.y) },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            )
        }
}
