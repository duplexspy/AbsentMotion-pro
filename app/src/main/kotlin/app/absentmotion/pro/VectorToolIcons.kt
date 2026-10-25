package app.absentmotion.pro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

internal object VectorToolIcons {
    private fun vec(name: String, w: Float, h: Float, vw: Float, vh: Float, build: PathBuilder.() -> Unit): ImageVector {
        val builder = ImageVector.Builder(name, w.dp, h.dp, vw, vh)
        val path = PathBuilder().apply(build)
        builder.addPath(path.nodes, stroke = SolidColor(Color.White), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        return builder.build()
    }

    private fun PathBuilder.stroke(d: String) {
        addPathNodes(d)
    }

    val NodeEdit = vec("NodeEdit", 24f, 24f, 24f, 24f) {
        stroke("M4 4L11 20L14 13L21 10L4 4ZM14 13L19 18")
    }

    val Knife = vec("Knife", 24f, 24f, 24f, 24f) {
        stroke("M6 6L18 18M3 10L14 21M9 3L21 15")
    }

    val Join = vec("Join", 24f, 24f, 24f, 24f) {
        stroke("M4 12H9M15 12H20M12 6V9M12 15V18M12 12A3 3 0 1 0 12 12")
    }

    val Shapes = vec("Shapes", 24f, 24f, 24f, 24f) {
        stroke("M3 4H13V14H3ZM10 10A5 5 0 1 0 20 20A5 5 0 1 0 10 10Z")
    }

    val Corner = vec("Corner", 24f, 24f, 24f, 24f) {
        stroke("M5 19V5H19")
    }

    val Smooth = vec("Smooth", 24f, 24f, 24f, 24f) {
        stroke("M3 17C7 7 17 7 21 17M12 7V11")
    }

    val Symmetric = vec("Symmetric", 24f, 24f, 24f, 24f) {
        stroke("M3 12H21M7 9V15M17 9V15M12 7A2 2 0 1 0 12 7")
    }

    val AutoSmooth = vec("AutoSmooth", 24f, 24f, 24f, 24f) {
        stroke("M3 16C6 8 10 8 12 12C14 16 18 16 21 8")
    }

    val Magnet = vec("Magnet", 24f, 24f, 24f, 24f) {
        stroke("M5 4V11A7 7 0 0 0 19 11V4M5 8H9M15 8H19")
    }

    val Loupe = vec("Loupe", 24f, 24f, 24f, 24f) {
        stroke("M11 19A8 8 0 1 0 11 3A8 8 0 1 0 11 19ZM21 21L16.65 16.65M11 7V15M7 11H15")
    }

    val Star = vec("Star", 24f, 24f, 24f, 24f) {
        stroke("M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z")
    }

    val Polygon = vec("Polygon", 24f, 24f, 24f, 24f) {
        stroke("M12 2L21 8.5V17.5L12 22L3 17.5V8.5L12 2Z")
    }

    val Rect = vec("Rect", 24f, 24f, 24f, 24f) {
        stroke("M4 5H20V19H4Z")
    }

    val RoundRect = vec("RoundRect", 24f, 24f, 24f, 24f) {
        stroke("M7 5H17A3 3 0 0 1 20 8V16A3 3 0 0 1 17 19H7A3 3 0 0 1 4 16V8A3 3 0 0 1 7 5Z")
    }

    val Ellipse = vec("Ellipse", 24f, 24f, 24f, 24f) {
        stroke("M12 4C17.52 4 22 7.58 22 12C22 16.42 17.52 20 12 20C6.48 20 2 16.42 2 12C2 7.58 6.48 4 12 4Z")
    }

    val Line = vec("Line", 24f, 24f, 24f, 24f) {
        stroke("M4 20L20 4")
    }

    val Nudge = vec("Nudge", 24f, 24f, 24f, 24f) {
        stroke("M12 3L8 7H16ZM12 21L8 17H16ZM3 12L7 8V16ZM21 12L17 8V16Z")
    }

    val DashedLine = vec("DashedLine", 24f, 24f, 24f, 24f) {
        stroke("M3 12H7M11 12H15M19 12H21")
    }
}
