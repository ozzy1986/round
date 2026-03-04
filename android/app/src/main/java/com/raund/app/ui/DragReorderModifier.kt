package com.raund.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.unit.dp
import com.raund.app.viewmodel.ProfileEditorState
import com.raund.app.viewmodel.ProfileEditorViewModel
import kotlin.math.roundToInt

/**
 * Modifier for long-press drag-to-reorder. When long press is detected, starts drag;
 * on pointer release computes target index and calls [viewModel].reorderRounds.
 * If [onTapWhenNoDrag] is non-null and the user taps without long-press, invokes it (e.g. toggle selection).
 */
fun Modifier.dragReorder(
    index: Int,
    state: ProfileEditorState,
    viewModel: ProfileEditorViewModel,
    roundsSize: Int,
    haptics: HapticFeedback,
    density: androidx.compose.ui.unit.Density,
    onTapWhenNoDrag: (() -> Unit)? = null
): Modifier = pointerInput(index, roundsSize, state.itemHeightPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = onTapWhenNoDrag == null)
        if (onTapWhenNoDrag != null) down.consume()
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress != null) {
            longPress.consume()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.setDraggedIndex(index)
            viewModel.setDragOffset(0f)
            viewModel.clearSelection()

            val dragged = drag(longPress.id) { change ->
                viewModel.addDragOffset(change.positionChange().y)
                change.consume()
            }

            if (dragged) {
                val di = state.draggedIndex
                if (di != null && state.itemHeightPx > 0f) {
                    val spacerPx = with(density) { 12.dp.toPx() }
                    val stepPx = state.itemHeightPx + spacerPx
                    val positions = (state.dragOffset / stepPx).roundToInt()
                    val targetIdx = (di + positions).coerceIn(0, roundsSize - 1)
                    if (targetIdx != di) {
                        viewModel.reorderRounds(di, targetIdx)
                    }
                }
            }
            viewModel.setDraggedIndex(null)
            viewModel.setDragOffset(0f)
        } else {
            onTapWhenNoDrag?.invoke()
        }
    }
}
