package app.absentmotion.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.VectorToolMode
import app.absentmotion.editor.at
import app.absentmotion.editor.withEmptyTail
import app.absentmotion.model.Contour
import app.absentmotion.model.Handle
import app.absentmotion.model.Knot
import app.absentmotion.model.Pt
import app.absentmotion.model.contours
import app.absentmotion.model.withContoursAt
import app.absentmotion.model.withEveryShape
import app.absentmotion.model.withHandleMoved
import app.absentmotion.model.withPointAppended
import app.absentmotion.model.withPointMoved
import app.absentmotion.model.withPointRemoved
import app.absentmotion.model.withStrokeDash

fun EditorState.vectorContours(mode: VectorToolMode): List<Contour> =
    (scene.layerAt(mode.layerId, sampleMs)?.contours ?: emptyList()).withEmptyTail(mode.selectedContour)

fun EditorState.changeVector(mode: VectorToolMode, key: Any? = null, change: (List<Contour>) -> List<Contour>) {
    edit(key) { s ->
        s.update(mode.layerId, sampleMs) { l ->
            l.withContoursAt(change((l.contours ?: emptyList()).withEmptyTail(mode.selectedContour)))
        }
    }
}

fun EditorState.reshapeVector(mode: VectorToolMode, change: (List<Contour>) -> List<Contour>) {
    edit { s ->
        s.update(mode.layerId, sampleMs) { l ->
            l.withEveryShape { change(it.withEmptyTail(mode.selectedContour)) }
        }
    }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }

fun EditorState.addShape(mode: VectorToolMode, shapeType: ShapeType) {
    val contour = when (shapeType) {
        ShapeType.Rect -> makeRect(200f, 150f, 0f)
        ShapeType.RoundedRect -> makeRect(200f, 150f, mode.rectRoundness)
        ShapeType.Ellipse -> makeEllipse(100f, 80f)
        ShapeType.Polygon -> makePolygon(mode.polygonSides, 100f)
        ShapeType.Star -> makeStar(mode.starPoints, 100f * mode.starInnerRatio, 100f)
        ShapeType.Line -> makeLine(Pt(-100f, 0f), Pt(100f, 0f))
    }
    reshapeVector(mode) { list ->
        val filtered = list.filter { it.knots.isNotEmpty() }
        filtered + contour
    }
    val contours = vectorContours(mode)
    mode.selectedContour = contours.lastIndex.coerceAtLeast(0)
    mode.selectedKnot = 0
    mode.selectedHandle = Handle.Main
}

fun EditorState.setKnotType(mode: VectorToolMode, type: KnotType) {
    mode.knotType = type
    changeVector(mode) { list ->
        val c = list.getOrNull(mode.selectedContour) ?: return@changeVector list
        val k = c.knots.getOrNull(mode.selectedKnot) ?: return@changeVector list
        val converted = when (type) {
            KnotType.Corner -> k.toCorner()
            KnotType.Smooth -> k.toSmooth(mode.selectedHandle)
            KnotType.Symmetric -> k.toSymmetric(mode.selectedHandle)
            KnotType.AutoSmooth -> return@changeVector list.replaced(mode.selectedContour, c.toAutoSmooth(mode.selectedKnot))
        }
        list.replaced(mode.selectedContour, c.copy(knots = c.knots.replaced(mode.selectedKnot, converted)))
    }
}

fun EditorState.deleteSelectedKnot(mode: VectorToolMode) {
    var selectContour = mode.selectedContour
    var selectPoint = mode.selectedKnot
    reshapeVector(mode) { list ->
        val c = list.getOrNull(mode.selectedContour)?.takeIf { mode.selectedKnot in it.knots.indices } ?: return@reshapeVector list
        val removed = c.withPointRemoved(mode.selectedKnot)
        if (removed.knots.isEmpty() && list.size > 1) {
            selectContour = (mode.selectedContour - 1).coerceAtLeast(0)
            selectPoint = (list.getOrNull(selectContour)?.knots?.lastIndex ?: -1).coerceAtLeast(0)
            list.filterIndexed { i, _ -> i != mode.selectedContour }
        } else {
            selectPoint = (mode.selectedKnot - 1).coerceAtLeast(0).coerceAtMost(removed.knots.lastIndex)
            list.replaced(mode.selectedContour, removed)
        }
    }
    mode.selectedContour = selectContour
    mode.selectedKnot = selectPoint
}

fun EditorState.breakContourAtSelected(mode: VectorToolMode) {
    reshapeVector(mode) { list ->
        val c = list.getOrNull(mode.selectedContour)?.takeIf { mode.selectedKnot in it.knots.indices } ?: return@reshapeVector list
        val cut = c.knifeCutAt(mode.selectedKnot)
        val out = ArrayList<Contour>()
        list.forEachIndexed { i, cont ->
            if (i == mode.selectedContour) out.addAll(cut) else out.add(cont)
        }
        out
    }
    mode.selectedKnot = 0
}

fun EditorState.joinOpenContours(mode: VectorToolMode) {
    reshapeVector(mode) { list ->
        val joined = joinNearestContours(list, 80f)
        if (joined == list) {
            val c = list.getOrNull(mode.selectedContour)
            if (c != null && !c.closed && c.knots.size >= 2) {
                list.replaced(mode.selectedContour, c.copy(closed = true))
            } else list
        } else joined
    }
}

fun EditorState.knifeSlice(mode: VectorToolMode, p1: Pt, p2: Pt) {
    reshapeVector(mode) { list ->
        knifeCutContoursWithLine(list, p1, p2)
    }
}

fun EditorState.nudgeSelected(mode: VectorToolMode, dx: Float, dy: Float) {
    val delta = Pt(dx, dy)
    changeVector(mode) { list ->
        val c = list.getOrNull(mode.selectedContour) ?: return@changeVector list
        val k = c.knots.getOrNull(mode.selectedKnot) ?: return@changeVector list
        val moved = when (mode.selectedHandle) {
            Handle.Main -> c.withPointMoved(mode.selectedKnot, delta)
            else -> {
                val current = k.at(mode.selectedHandle) ?: k.p
                c.withHandleMoved(mode.selectedKnot, mode.selectedHandle, current + delta, angleLock = false, distanceLock = false)
            }
        }
        list.replaced(mode.selectedContour, moved)
    }
}

fun EditorState.applyStrokeDash(
    mode: VectorToolMode,
    pattern: FloatArray,
    offset: Float,
    autoFit: Boolean,
    taperStart: Float,
    taperEnd: Float,
) {
    edit { s ->
        s.update(mode.layerId, sampleMs) { l ->
            l.withStrokeDash(formatDashPattern(pattern), offset, autoFit, taperStart, taperEnd)
        }
    }
}

fun EditorState.copySvg(mode: VectorToolMode, context: Context): Boolean {
    val contours = vectorContours(mode).filter { it.knots.isNotEmpty() }
    if (contours.isEmpty()) return false
    val svg = exportSvg(contours)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    val clip = ClipData.newPlainText("SVG Path", svg)
    clipboard.setPrimaryClip(clip)
    return true
}

fun EditorState.pasteSvg(mode: VectorToolMode, context: Context): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    val item = clipboard.primaryClip?.getItemAt(0) ?: return false
    val text = item.text?.toString() ?: return false
    val parsed = importSvg(text)
    if (parsed.isEmpty()) return false
    reshapeVector(mode) { list ->
        val existing = list.filter { it.knots.isNotEmpty() }
        existing + parsed
    }
    val contours = vectorContours(mode)
    mode.selectedContour = contours.lastIndex.coerceAtLeast(0)
    mode.selectedKnot = 0
    return true
}

fun EditorState.resampleSelected(mode: VectorToolMode, targetCount: Int) {
    reshapeVector(mode) { list ->
        val c = list.getOrNull(mode.selectedContour) ?: return@reshapeVector list
        list.replaced(mode.selectedContour, c.resample(targetCount))
    }
}

fun EditorState.moveVectorSelectedTo(mode: VectorToolMode, to: Pt, key: Any? = "vector-drag") {
    if (mode.selectedKnot < 0) return
    changeVector(mode, key) { list ->
        val c = list.getOrNull(mode.selectedContour) ?: return@changeVector list
        val k = c.knots.getOrNull(mode.selectedKnot) ?: return@changeVector list
        val moved = when (mode.selectedHandle) {
            Handle.Main -> c.withPointMoved(mode.selectedKnot, to - k.p)
            else -> {
                val isAngleLocked = mode.knotType == KnotType.Smooth || mode.knotType == KnotType.Symmetric
                val isDistLocked = mode.knotType == KnotType.Symmetric
                c.withHandleMoved(mode.selectedKnot, mode.selectedHandle, to, isAngleLocked, isDistLocked)
            }
        }
        list.replaced(mode.selectedContour, moved)
    }
}

fun EditorState.placeVectorPoint(mode: VectorToolMode, at: Pt, pull: Pt? = null) {
    var newPointIndex = 0
    changeVector(mode) { list ->
        val c = list.getOrNull(mode.selectedContour) ?: Contour(emptyList(), closed = false)
        val updated = c.withPointAppended(at, pull)
        newPointIndex = updated.knots.lastIndex
        list.replaced(mode.selectedContour, updated)
    }
    mode.selectedKnot = newPointIndex
    mode.selectedHandle = Handle.Main
}
