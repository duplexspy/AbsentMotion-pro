package app.absentmotion.pro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.VectorToolMode
import app.absentmotion.editor.at
import app.absentmotion.editor.endsAt
import app.absentmotion.editor.inverseMap
import app.absentmotion.editor.leaveDrawing
import app.absentmotion.editor.map
import app.absentmotion.editor.screenMatrix
import app.absentmotion.editor.switchToClassicVector
import app.absentmotion.model.Contour
import app.absentmotion.model.Handle
import app.absentmotion.model.Knot
import app.absentmotion.model.Pt
import app.absentmotion.model.hit
import app.absentmotion.model.stroke
import app.absentmotion.ui.editor.DrawingIcons
import app.absentmotion.ui.editor.PanelIcons
import app.absentmotion.ui.editor.panels.ThinSlider
import app.absentmotion.ui.theme.Am
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun VectorToolOverlay(state: EditorState, mode: VectorToolMode, modifier: Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val scene = state.scene
    val scale = if (canvasSize.width == 0) 1f else min(canvasSize.width.toFloat() / scene.width, canvasSize.height.toFloat() / scene.height)
    val dx = (canvasSize.width - scene.width * scale) / 2f
    val dy = (canvasSize.height - scene.height * scale) / 2f
    SideEffect { mode.previewScale = scale }
    val view by rememberUpdatedState(Triple(scale, dx, dy))
    var knifeDrag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var penPull by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(state, mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val (s, ox, oy) = view
                    val m = mode.screenMatrix(state.scene, state.timeMs) ?: return@awaitEachGesture
                    fun toScene(p: Offset) = Pt((p.x - ox) / s, (p.y - oy) / s)
                    val contours = state.vectorContours(mode)
                    val inScene = contours.map { c ->
                        c.copy(knots = c.knots.map { k ->
                            Knot(m.map(k.p), k.inHandle?.let { m.map(it) }, k.outHandle?.let { m.map(it) })
                        })
                    }

                    if (mode.subtool == VectorSubtool.Knife) {
                        val startPos = down.position
                        knifeDrag = startPos to startPos
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                knifeDrag = null
                                val p1 = m.inverseMap(toScene(startPos))
                                val p2 = m.inverseMap(toScene(change.position))
                                if (p1.distanceTo(p2) > 5f) {
                                    state.knifeSlice(mode, p1, p2)
                                }
                                break
                            }
                            knifeDrag = startPos to change.position
                            change.consume()
                        }
                        return@awaitEachGesture
                    }

                    val picked = inScene.hit(toScene(down.position), 28.dp.toPx() / s, mode.selectedContour.takeIf { mode.selectedKnot >= 0 }?.let { it to mode.selectedKnot })
                    var moved = false
                    val now = System.currentTimeMillis()

                    if (picked != null) {
                        val wasSelected = mode.selectedContour == picked.first && mode.selectedKnot == picked.second
                        mode.selectedContour = picked.first
                        mode.selectedKnot = picked.second
                        mode.selectedHandle = picked.third
                        val knot = contours.getOrNull(picked.first)?.knots?.getOrNull(picked.second)
                        if (knot != null) mode.knotType = knot.knotType()

                        if (wasSelected && picked.third == Handle.Main && now - lastTapTime < 350L) {
                            val nextType = when (mode.knotType) {
                                KnotType.Corner -> KnotType.Smooth
                                KnotType.Smooth -> KnotType.Symmetric
                                KnotType.Symmetric -> KnotType.AutoSmooth
                                KnotType.AutoSmooth -> KnotType.Corner
                            }
                            state.setKnotType(mode, nextType)
                            lastTapTime = 0L
                            return@awaitEachGesture
                        }
                        lastTapTime = now

                        val startPt = contours[picked.first].knots[picked.second].at(picked.third) ?: return@awaitEachGesture
                        val allKnots = contours.flatMap { it.knots.map { kn -> kn.p } }

                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                mode.loupeActive = false
                                mode.activeGuide = null
                                break
                            }
                            if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            if (moved) {
                                val rawTarget = startPt + (m.inverseMap(toScene(change.position)) - m.inverseMap(toScene(down.position)))
                                val target = if (!mode.snappingEnabled) rawTarget else {
                                    if (picked.third == Handle.Main) {
                                        val snap = snapPoint(rawTarget, mode.gridSize, mode.snapGrid, mode.snapNodes, allKnots)
                                        mode.activeGuide = snap.guide
                                        snap.point
                                    } else {
                                        val origin = contours[picked.first].knots[picked.second].p
                                        snapHandleAngle(origin, rawTarget)
                                    }
                                }
                                state.moveVectorSelectedTo(mode, target)
                                mode.loupeActive = true
                                mode.loupeTarget = change.position
                                mode.loupePosition = Offset(change.position.x, (change.position.y - 92.dp.toPx()).coerceAtLeast(10.dp.toPx()))
                            }
                            change.consume()
                        }
                        if (moved) state.endGesture()
                    } else if (mode.subtool == VectorSubtool.Pen) {
                        val downPt = m.inverseMap(toScene(down.position))
                        val allKnots = contours.flatMap { it.knots.map { kn -> kn.p } }
                        val snappedDown = if (mode.snappingEnabled) snapPoint(downPt, mode.gridSize, mode.snapGrid, mode.snapNodes, allKnots).point else downPt
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                penPull = null
                                mode.loupeActive = false
                                mode.activeGuide = null
                                val pull = if (moved) m.inverseMap(toScene(change.position)) - snappedDown else null
                                state.placeVectorPoint(mode, snappedDown, pull)
                                break
                            }
                            if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) {
                                penPull = down.position to change.position
                                mode.loupeActive = true
                                mode.loupeTarget = change.position
                                mode.loupePosition = Offset(change.position.x, (change.position.y - 92.dp.toPx()).coerceAtLeast(10.dp.toPx()))
                            }
                            change.consume()
                        }
                        penPull = null
                    }
                }
            },
    ) {
        val m = mode.screenMatrix(state.scene, state.timeMs) ?: return@Canvas
        fun toScreen(p: Pt) = m.map(p).let { Offset(it.x * scale + dx, it.y * scale + dy) }
        val contours = state.vectorContours(mode)
        val layer = state.scene.layer(mode.layerId)
        val strokeData = layer?.stroke
        val dashPattern = parseDashPattern(strokeData?.dash)

        contours.forEachIndexed { cIdx, c ->
            if (c.knots.size < 2) return@forEachIndexed
            val outline = Path()
            outline.moveTo(toScreen(c.knots[0].p).x, toScreen(c.knots[0].p).y)
            for (i in 0 until if (c.closed) c.knots.size else c.knots.size - 1) {
                val a = c.knots[i]
                val b = c.knots[(i + 1) % c.knots.size]
                val end = toScreen(b.p)
                if (a.outHandle == null && b.inHandle == null) {
                    outline.lineTo(end.x, end.y)
                } else {
                    val c1 = toScreen(a.outHandle ?: a.p)
                    val c2 = toScreen(b.inHandle ?: b.p)
                    outline.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
                }
            }
            if (c.closed) outline.close()

            val isSelectedContour = cIdx == mode.selectedContour
            val strokeColor = if (isSelectedContour) Am.Accent else Color.White.copy(alpha = 0.6f)
            val shadowColor = Color.Black.copy(alpha = 0.7f)
            val fittedDash = if (dashPattern.isNotEmpty() && strokeData?.dashAutoFit == true) {
                autoFitDashPattern(dashPattern, c.arcLength())
            } else dashPattern
            val effect = if (fittedDash.isNotEmpty()) {
                PathEffect.dashPathEffect(fittedDash.map { it * scale }.toFloatArray(), (strokeData?.dashOffset ?: 0f) * scale)
            } else null

            drawPath(outline, shadowColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(outline, strokeColor, style = Stroke(width = 1.8.dp.toPx(), pathEffect = effect, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        contours.forEachIndexed { cIdx, c ->
            val isSelectedContour = cIdx == mode.selectedContour
            c.knots.forEachIndexed { kIdx, k ->
                val isSelectedKnot = isSelectedContour && kIdx == mode.selectedKnot
                val screenPt = toScreen(k.p)

                if (isSelectedKnot) {
                    k.inHandle?.let { h ->
                        val screenH = toScreen(h)
                        drawLine(Am.Accent.copy(alpha = 0.8f), screenPt, screenH, 1.2.dp.toPx())
                        drawCircle(Am.Accent, 4.dp.toPx(), screenH)
                        drawCircle(Color.Black, 4.dp.toPx(), screenH, style = Stroke(1.dp.toPx()))
                    }
                    k.outHandle?.let { h ->
                        val screenH = toScreen(h)
                        drawLine(Am.Accent.copy(alpha = 0.8f), screenPt, screenH, 1.2.dp.toPx())
                        drawCircle(Am.Accent, 4.dp.toPx(), screenH)
                        drawCircle(Color.Black, 4.dp.toPx(), screenH, style = Stroke(1.dp.toPx()))
                    }
                    drawCircle(Am.Accent, 6.5.dp.toPx(), screenPt)
                    drawCircle(Color.White, 7.5.dp.toPx(), screenPt, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.Black, 9.5.dp.toPx(), screenPt, style = Stroke(1.2.dp.toPx()))
                } else {
                    drawCircle(Color.Black, 5.dp.toPx(), screenPt)
                    drawCircle(Color.White, 4.dp.toPx(), screenPt)
                }
            }
        }

        penPull?.let { (from, to) ->
            drawLine(Color.White, from, to, 2.dp.toPx(), StrokeCap.Round)
            drawCircle(Am.Accent, 6.dp.toPx(), to)
            drawCircle(Color.White, 8.dp.toPx(), from)
        }

        knifeDrag?.let { (from, to) ->
            val effect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()), 0f)
            drawLine(Color(0xFFFF5252), from, to, 2.5.dp.toPx(), pathEffect = effect)
            drawCircle(Color(0xFFFF5252), 6.dp.toPx(), from)
            drawCircle(Color(0xFFFF5252), 6.dp.toPx(), to)
        }

        mode.activeGuide?.let { guide ->
            val sFrom = toScreen(guide.from)
            val sTo = toScreen(guide.to)
            val effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
            drawLine(Am.AccentLit, sFrom, sTo, 1.2.dp.toPx(), pathEffect = effect)
        }

        if (mode.loupeActive) {
            val center = mode.loupePosition
            val radius = 46.dp.toPx()
            val zoom = 2.2f
            val target = mode.loupeTarget

            val clipCircle = Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }
            drawCircle(Color(0xFF141722), radius, center)
            clipPath(clipCircle) {
                contours.forEach { c ->
                    if (c.knots.size < 2) return@forEach
                    val outline = Path()
                    fun loupePt(p: Pt): Offset {
                        val sp = toScreen(p)
                        return Offset(center.x + (sp.x - target.x) * zoom, center.y + (sp.y - target.y) * zoom)
                    }
                    outline.moveTo(loupePt(c.knots[0].p).x, loupePt(c.knots[0].p).y)
                    for (i in 0 until if (c.closed) c.knots.size else c.knots.size - 1) {
                        val a = c.knots[i]
                        val b = c.knots[(i + 1) % c.knots.size]
                        val end = loupePt(b.p)
                        if (a.outHandle == null && b.inHandle == null) {
                            outline.lineTo(end.x, end.y)
                        } else {
                            val c1 = loupePt(a.outHandle ?: a.p)
                            val c2 = loupePt(b.inHandle ?: b.p)
                            outline.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
                        }
                    }
                    if (c.closed) outline.close()
                    drawPath(outline, Color.White, style = Stroke(2.dp.toPx()))
                    c.knots.forEach { kn ->
                        drawCircle(Am.Accent, 5.dp.toPx(), loupePt(kn.p))
                    }
                }
            }
            drawCircle(Am.Accent, radius, center, style = Stroke(2.5.dp.toPx()))
            drawLine(Am.Accent.copy(alpha = 0.7f), Offset(center.x - radius * 0.7f, center.y), Offset(center.x + radius * 0.7f, center.y), 1.dp.toPx())
            drawLine(Am.Accent.copy(alpha = 0.7f), Offset(center.x, center.y - radius * 0.7f), Offset(center.x, center.y + radius * 0.7f), 1.dp.toPx())
            drawCircle(Color.White, 2.dp.toPx(), center)
        }
    }
}

@Composable
internal fun VectorToolPanel(state: EditorState, mode: VectorToolMode) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("tools") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Am.PanelInset)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SubtoolButton(DrawingIcons.Pen, "Pen", mode.subtool == VectorSubtool.Pen) { mode.subtool = VectorSubtool.Pen }
                SubtoolButton(VectorToolIcons.NodeEdit, "Node", mode.subtool == VectorSubtool.Node) { mode.subtool = VectorSubtool.Node }
                SubtoolButton(VectorToolIcons.Shapes, "Shape", mode.subtool == VectorSubtool.Shapes) { mode.subtool = VectorSubtool.Shapes }
                SubtoolButton(VectorToolIcons.Knife, "Knife", mode.subtool == VectorSubtool.Knife) { mode.subtool = VectorSubtool.Knife }
                SubtoolButton(VectorToolIcons.Join, "Join", mode.subtool == VectorSubtool.Join) { mode.subtool = VectorSubtool.Join }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val hasPoint = mode.selectedKnot >= 0
                SubtoolButton(DrawingIcons.DeletePoint, "Delete Point", false, enabled = hasPoint) { state.deleteSelectedKnot(mode) }
                SubtoolButton(DrawingIcons.AddPoint, "Classic Points", false) { state.switchToClassicVector() }
                SubtoolButton(VectorToolIcons.Magnet, "Snap", mode.snappingEnabled) { mode.snappingEnabled = !mode.snappingEnabled }
                SubtoolButton(VectorToolIcons.DashedLine, "Dash", activeTab == "dash") {
                    activeTab = if (activeTab == "dash") "tools" else "dash"
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            if (activeTab == "dash") {
                DashConfigPanel(state, mode)
            } else {
                when (mode.subtool) {
                    VectorSubtool.Pen -> PenConfigPanel(state, mode)
                    VectorSubtool.Node -> NodeConfigPanel(state, mode)
                    VectorSubtool.Shapes -> ShapesConfigPanel(state, mode)
                    VectorSubtool.Knife -> KnifeConfigPanel(state, mode)
                    VectorSubtool.Join -> JoinConfigPanel(state, mode)
                    VectorSubtool.Freehand -> PenConfigPanel(state, mode)
                }
            }
        }
    }
}

@Composable
private fun SubtoolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Am.Accent.copy(alpha = 0.25f) else Color.Transparent)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (active) Am.Accent else Am.OnDark)
    }
}

@Composable
private fun NodeConfigPanel(state: EditorState, mode: VectorToolMode) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Node Type", color = Am.Caption, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TypeButton(VectorToolIcons.Corner, "Corner", mode.knotType == KnotType.Corner) { state.setKnotType(mode, KnotType.Corner) }
                TypeButton(VectorToolIcons.Smooth, "Smooth", mode.knotType == KnotType.Smooth) { state.setKnotType(mode, KnotType.Smooth) }
                TypeButton(VectorToolIcons.Symmetric, "Symm", mode.knotType == KnotType.Symmetric) { state.setKnotType(mode, KnotType.Symmetric) }
                TypeButton(VectorToolIcons.AutoSmooth, "Auto", mode.knotType == KnotType.AutoSmooth) { state.setKnotType(mode, KnotType.AutoSmooth) }
            }
            Spacer(Modifier.height(4.dp))
            Text("Actions", color = Am.Caption, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("Delete Point") { state.deleteSelectedKnot(mode) }
                ActionChip("Break") { state.breakContourAtSelected(mode) }
                ActionChip("Join") { state.joinOpenContours(mode) }
            }
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(Am.PreviewBg))

        NudgePad(Modifier.width(120.dp).fillMaxHeight(), mode.nudgeStep, onStepChange = { mode.nudgeStep = it }) { dx, dy ->
            state.nudgeSelected(mode, dx * mode.nudgeStep, dy * mode.nudgeStep)
        }
    }
}

@Composable
private fun PenConfigPanel(state: EditorState, mode: VectorToolMode) {
    val context = LocalContext.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pen Tool", color = Am.OnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Tap to place a point. Drag to pull smooth curve handles.", color = Am.Caption, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mode.selectedKnot >= 0) {
                    ActionChip("Delete Point") { state.deleteSelectedKnot(mode) }
                }
                ActionChip("Close Path") { state.joinOpenContours(mode) }
                ActionChip("Copy SVG") { state.copySvg(mode, context) }
                ActionChip("Paste SVG") { state.pasteSvg(mode, context) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Snap Grid", mode.snapGrid) { mode.snapGrid = !mode.snapGrid }
                ToggleChip("Snap Angle", mode.snapAngle) { mode.snapAngle = !mode.snapAngle }
            }
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(Am.PreviewBg))

        NudgePad(Modifier.width(120.dp).fillMaxHeight(), mode.nudgeStep, onStepChange = { mode.nudgeStep = it }) { dx, dy ->
            state.nudgeSelected(mode, dx * mode.nudgeStep, dy * mode.nudgeStep)
        }
    }
}

@Composable
private fun ShapesConfigPanel(state: EditorState, mode: VectorToolMode) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Shape Generator", color = Am.Caption, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShapeTypeChip(VectorToolIcons.Rect, "Rect", mode.shapeType == ShapeType.Rect) { mode.shapeType = ShapeType.Rect }
            ShapeTypeChip(VectorToolIcons.RoundRect, "Round", mode.shapeType == ShapeType.RoundedRect) { mode.shapeType = ShapeType.RoundedRect }
            ShapeTypeChip(VectorToolIcons.Ellipse, "Ellipse", mode.shapeType == ShapeType.Ellipse) { mode.shapeType = ShapeType.Ellipse }
            ShapeTypeChip(VectorToolIcons.Polygon, "Poly", mode.shapeType == ShapeType.Polygon) { mode.shapeType = ShapeType.Polygon }
            ShapeTypeChip(VectorToolIcons.Star, "Star", mode.shapeType == ShapeType.Star) { mode.shapeType = ShapeType.Star }
            ShapeTypeChip(VectorToolIcons.Line, "Line", mode.shapeType == ShapeType.Line) { mode.shapeType = ShapeType.Line }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode.shapeType == ShapeType.Polygon) {
                Text("Sides: ${mode.polygonSides}", color = Am.OnDark, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                ThinSlider(mode.polygonSides.toFloat(), { mode.polygonSides = it.roundToInt() }, Modifier.weight(1f), valueRange = 3f..12f)
            } else if (mode.shapeType == ShapeType.Star) {
                Text("Points: ${mode.starPoints}", color = Am.OnDark, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                ThinSlider(mode.starPoints.toFloat(), { mode.starPoints = it.roundToInt() }, Modifier.weight(1f), valueRange = 3f..12f)
            } else if (mode.shapeType == ShapeType.RoundedRect) {
                Text("Radius: ${mode.rectRoundness.toInt()}px", color = Am.OnDark, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                ThinSlider(mode.rectRoundness, { mode.rectRoundness = it }, Modifier.weight(1f), valueRange = 2f..48f)
            } else {
                Spacer(Modifier.weight(1f))
            }
            ActionChip("Add Shape") { state.addShape(mode, mode.shapeType) }
        }
    }
}

@Composable
private fun KnifeConfigPanel(state: EditorState, mode: VectorToolMode) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Knife / Scissors Tool", color = Am.OnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Drag across contours to slice them apart into separate contours.", color = Am.Caption, fontSize = 11.sp)
        Text("Select a node and tap 'Break' in Node subtool to split at an exact knot.", color = Am.Caption, fontSize = 11.sp)
    }
}

@Composable
private fun JoinConfigPanel(state: EditorState, mode: VectorToolMode) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Join Paths Tool", color = Am.OnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Fuses open path endpoints within proximity into a single continuous path.", color = Am.Caption, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip("Join Nearest Endpoints") { state.joinOpenContours(mode) }
            ActionChip("Resample to 16 Nodes") { state.resampleSelected(mode, 16) }
        }
    }
}

@Composable
private fun DashConfigPanel(state: EditorState, mode: VectorToolMode) {
    val presets = listOf(
        "Solid" to DashPresets.Solid,
        "Dash" to DashPresets.Dash,
        "Dot" to DashPresets.Dot,
        "DashDot" to DashPresets.DashDot,
        "LongDash" to DashPresets.LongDash,
    )
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Stroke Dash & Taper", color = Am.Caption, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEachIndexed { idx, (label, pattern) ->
                ActionChip(label, active = mode.dashPresetIndex == idx) {
                    mode.dashPresetIndex = idx
                    state.applyStrokeDash(mode, pattern, mode.dashOffset, mode.dashAutoFit, mode.taperStart, mode.taperEnd)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Offset: ${mode.dashOffset.roundToInt()}", color = Am.OnDark, fontSize = 11.sp, modifier = Modifier.width(64.dp))
            ThinSlider(mode.dashOffset, {
                mode.dashOffset = it
                state.applyStrokeDash(mode, presets[mode.dashPresetIndex].second, it, mode.dashAutoFit, mode.taperStart, mode.taperEnd)
            }, Modifier.weight(1f), valueRange = 0f..100f)
            ToggleChip("Auto-Fit", mode.dashAutoFit) {
                mode.dashAutoFit = !mode.dashAutoFit
                state.applyStrokeDash(mode, presets[mode.dashPresetIndex].second, mode.dashOffset, mode.dashAutoFit, mode.taperStart, mode.taperEnd)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Taper In: ${(mode.taperStart * 100).toInt()}%", color = Am.OnDark, fontSize = 11.sp, modifier = Modifier.width(76.dp))
            ThinSlider(mode.taperStart, {
                mode.taperStart = it
                state.applyStrokeDash(mode, presets[mode.dashPresetIndex].second, mode.dashOffset, mode.dashAutoFit, it, mode.taperEnd)
            }, Modifier.weight(1f), valueRange = 0f..1f)
            Text("Out: ${(mode.taperEnd * 100).toInt()}%", color = Am.OnDark, fontSize = 11.sp, modifier = Modifier.width(60.dp))
            ThinSlider(mode.taperEnd, {
                mode.taperEnd = it
                state.applyStrokeDash(mode, presets[mode.dashPresetIndex].second, mode.dashOffset, mode.dashAutoFit, mode.taperStart, it)
            }, Modifier.weight(1f), valueRange = 0f..1f)
        }
    }
}

@Composable
private fun TypeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Am.Accent.copy(alpha = 0.3f) else Am.PreviewBg)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = if (active) Am.Accent else Am.OnDark)
        Text(label, color = if (active) Am.Accent else Am.Caption, fontSize = 9.sp)
    }
}

@Composable
private fun ShapeTypeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Am.Accent.copy(alpha = 0.3f) else Am.PreviewBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, label, Modifier.size(14.dp), tint = if (active) Am.Accent else Am.OnDark)
        Text(label, color = if (active) Am.Accent else Am.OnDark, fontSize = 11.sp)
    }
}

@Composable
private fun ActionChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Am.Accent else Am.PreviewBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color.Black else Am.OnDark, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Am.Accent.copy(alpha = 0.3f) else Am.PreviewBg)
            .border(1.dp, if (active) Am.Accent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Am.Accent else Am.Caption, fontSize = 11.sp)
    }
}

@Composable
private fun NudgePad(modifier: Modifier, step: Float, onStepChange: (Float) -> Unit, onNudge: (Float, Float) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Nudge", color = Am.Caption, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Am.PreviewBg)
                    .clickable { onStepChange(if (step >= 1f) 0.1f else 1f) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (step >= 1f) "1.0px" else "0.1px", color = Am.Accent, fontSize = 9.sp)
            }
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Am.PreviewBg)
                .clickable { onNudge(0f, -1f) },
            contentAlignment = Alignment.Center,
        ) {
            Text("▲", color = Am.OnDark, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Am.PreviewBg)
                    .clickable { onNudge(-1f, 0f) },
                contentAlignment = Alignment.Center,
            ) {
                Text("◀", color = Am.OnDark, fontSize = 10.sp)
            }
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Am.PreviewBg)
                    .clickable { onNudge(1f, 0f) },
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Am.OnDark, fontSize = 10.sp)
            }
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Am.PreviewBg)
                .clickable { onNudge(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            Text("▼", color = Am.OnDark, fontSize = 10.sp)
        }
    }
}
