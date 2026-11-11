package app.absentmotion.pro.labs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SidBadge(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    borderWidth: Dp = 2.dp,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0F121C))
            .border(borderWidth, Color(0xFF64748B), CircleShape)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = (w / 2f) * 0.92f

            val hexPath = Path()
            for (i in 0 until 6) {
                val angle = (i * 60f - 30f) * (PI.toFloat() / 180f)
                val x = cx + radius * cos(angle)
                val y = cy + radius * sin(angle)
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()

            drawPath(
                path = hexPath,
                color = Color.White,
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val strokeW = w * 0.08f
            val sPath = Path().apply {
                val sx1 = cx - w * 0.28f
                val sx2 = cx - w * 0.08f
                val sy1 = cy - h * 0.25f
                val sy2 = cy
                val sy3 = cy + h * 0.25f

                moveTo(sx2, sy1)
                lineTo(sx1, sy1)
                lineTo(sx1, sy2)
                lineTo(sx2, sy2)
                lineTo(sx2, sy3)
                lineTo(sx1, sy3)
            }
            drawPath(
                path = sPath,
                color = Color.White,
                style = Stroke(width = strokeW, cap = StrokeCap.Square, join = StrokeJoin.Miter)
            )

            drawLine(
                color = Color.White,
                start = Offset(cx, cy - h * 0.25f),
                end = Offset(cx, cy + h * 0.25f),
                strokeWidth = strokeW,
                cap = StrokeCap.Square
            )

            val dPath = Path().apply {
                val dx1 = cx + w * 0.08f
                val dx2 = cx + w * 0.28f
                val dy1 = cy - h * 0.25f
                val dy3 = cy + h * 0.25f

                moveTo(dx1, dy1)
                lineTo(dx2 - w * 0.06f, dy1)
                cubicTo(dx2, dy1, dx2, dy3, dx2 - w * 0.06f, dy3)
                lineTo(dx1, dy3)
                close()
            }
            drawPath(
                path = dPath,
                color = Color.White,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
