package app.absentmotion.pro.labs

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun FloatingSidBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(420f) }

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetY = (offsetY + dragAmount.y).coerceIn(100f, 1600f)
                }
            }
    ) {
        SidBadge(
            size = 46.dp,
            borderWidth = 1.5.dp,
            onClick = onClick
        )
    }
}
