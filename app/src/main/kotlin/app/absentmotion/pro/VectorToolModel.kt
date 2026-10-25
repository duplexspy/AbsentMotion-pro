package app.absentmotion.pro

import app.absentmotion.model.Contour
import app.absentmotion.model.Handle
import app.absentmotion.model.Knot
import app.absentmotion.model.Pt
import app.absentmotion.model.parseSvgPath
import app.absentmotion.model.toSvg
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class VectorSubtool {
    Pen,
    Node,
    Shapes,
    Freehand,
    Knife,
    Join,
}

enum class ShapeType {
    Rect,
    RoundedRect,
    Ellipse,
    Polygon,
    Star,
    Line,
}

enum class KnotType {
    Corner,
    Smooth,
    Symmetric,
    AutoSmooth,
}

data class SnapGuide(
    val from: Pt,
    val to: Pt,
    val label: String,
)

data class SnapResult(
    val point: Pt,
    val guide: SnapGuide? = null,
)

fun Pt.dot(o: Pt): Float = x * o.x + y * o.y

fun Pt.cross(o: Pt): Float = x * o.y - y * o.x

fun Pt.normalized(): Pt {
    val l = length
    return if (l < 1e-6f) Pt(0f, 0f) else Pt(x / l, y / l)
}

fun Pt.angleDegrees(): Float {
    val deg = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
    return if (deg < 0f) deg + 360f else deg
}

fun evalCubic(p0: Pt, p1: Pt, p2: Pt, p3: Pt, t: Float): Pt {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t
    return p0 * uuu + p1 * (3f * uu * t) + p2 * (3f * u * tt) + p3 * ttt
}

fun evalCubicTangent(p0: Pt, p1: Pt, p2: Pt, p3: Pt, t: Float): Pt {
    val u = 1f - t
    return (p1 - p0) * (3f * u * u) + (p2 - p1) * (6f * u * t) + (p3 - p2) * (3f * t * t)
}

fun cubicArcLength(p0: Pt, p1: Pt, p2: Pt, p3: Pt, steps: Int = 16): Float {
    var length = 0f
    var prev = p0
    for (i in 1..steps) {
        val t = i.toFloat() / steps
        val cur = evalCubic(p0, p1, p2, p3, t)
        length += cur.distanceTo(prev)
        prev = cur
    }
    return length
}

fun Contour.arcLength(): Float {
    if (knots.size < 2) return 0f
    var sum = 0f
    val n = knots.size
    val segments = if (closed) n else n - 1
    for (i in 0 until segments) {
        val a = knots[i]
        val b = knots[(i + 1) % n]
        if (a.outHandle == null && b.inHandle == null) {
            sum += a.p.distanceTo(b.p)
        } else {
            val c1 = a.outHandle ?: a.p
            val c2 = b.inHandle ?: b.p
            sum += cubicArcLength(a.p, c1, c2, b.p)
        }
    }
    return sum
}

fun List<Contour>.totalArcLength(): Float = fold(0f) { acc, c -> acc + c.arcLength() }

fun Knot.knotType(): KnotType {
    if (inHandle == null && outHandle == null) return KnotType.Corner
    if (inHandle == null || outHandle == null) return KnotType.Corner
    val vIn = (inHandle - p).normalized()
    val vOut = (outHandle - p).normalized()
    val dot = vIn.dot(vOut)
    if (dot > -0.96f) return KnotType.Corner
    val lenIn = (inHandle - p).length
    val lenOut = (outHandle - p).length
    return if (abs(lenIn - lenOut) < 1.0f) KnotType.Symmetric else KnotType.Smooth
}

fun Knot.toCorner(): Knot = copy()

fun Knot.toSmooth(fromHandle: Handle = Handle.Out): Knot {
    val base = if (fromHandle == Handle.In) inHandle else outHandle
    val other = if (fromHandle == Handle.In) outHandle else inHandle
    if (base == null && other == null) {
        val hIn = Pt(15f, 0f)
        val hOut = Pt(25f, 0f)
        return copy(inHandle = p - hIn, outHandle = p + hOut)
    }
    val ref = base ?: other ?: p
    val dir = (ref - p).normalized()
    val dRef = (ref - p).length.coerceAtLeast(10f)
    val dOther = other?.let { (it - p).length.coerceAtLeast(10f) } ?: dRef
    return if (fromHandle == Handle.In) {
        copy(inHandle = p + dir * dRef, outHandle = p - dir * dOther)
    } else {
        copy(outHandle = p + dir * dRef, inHandle = p - dir * dOther)
    }
}

fun Knot.toSymmetric(fromHandle: Handle = Handle.Out): Knot {
    val smooth = toSmooth(fromHandle)
    val ref = (if (fromHandle == Handle.In) smooth.inHandle else smooth.outHandle) ?: return smooth
    val dir = (ref - p).normalized()
    val len = (ref - p).length.coerceAtLeast(10f)
    return if (fromHandle == Handle.In) {
        copy(inHandle = p + dir * len, outHandle = p - dir * len)
    } else {
        copy(outHandle = p + dir * len, inHandle = p - dir * len)
    }
}

fun Contour.toAutoSmooth(knotIndex: Int): Contour {
    if (knots.size < 2 || knotIndex !in knots.indices) return this
    val n = knots.size
    val k = knots[knotIndex]
    val prev = knots.getOrNull(if (knotIndex > 0) knotIndex - 1 else if (closed) n - 1 else -1)?.p
    val next = knots.getOrNull(if (knotIndex < n - 1) knotIndex + 1 else if (closed) 0 else -1)?.p
    val dir = when {
        prev != null && next != null -> (next - prev).normalized()
        prev != null -> (k.p - prev).normalized()
        next != null -> (next - k.p).normalized()
        else -> Pt(1f, 0f)
    }
    val dPrev = prev?.let { k.p.distanceTo(it) / 3f } ?: 20f
    val dNext = next?.let { k.p.distanceTo(it) / 3f } ?: 20f
    val updated = k.copy(
        inHandle = if (prev != null || closed) k.p - dir * dPrev else null,
        outHandle = if (next != null || closed) k.p + dir * dNext else null,
    )
    return copy(knots = knots.mapIndexed { i, knot -> if (i == knotIndex) updated else knot })
}

private const val KAPPA = 0.5522847498f

fun makeRect(w: Float, h: Float, roundness: Float = 0f): Contour {
    val hw = w / 2f
    val hh = h / 2f
    val r = roundness.coerceIn(0f, min(hw, hh))
    if (r <= 0.01f) {
        return Contour(
            listOf(
                Knot(Pt(-hw, -hh)),
                Knot(Pt(hw, -hh)),
                Knot(Pt(hw, hh)),
                Knot(Pt(-hw, hh)),
            ),
            closed = true,
        )
    }
    val kr = r * KAPPA
    return Contour(
        listOf(
            Knot(Pt(-hw + r, -hh), outHandle = Pt(-hw + r + (hw - r), -hh)),
            Knot(Pt(hw - r, -hh), inHandle = Pt(hw - r - (hw - r), -hh), outHandle = Pt(hw - r + kr, -hh)),
            Knot(Pt(hw, -hh + r), inHandle = Pt(hw, -hh + r - kr), outHandle = Pt(hw, -hh + r + (hh - r))),
            Knot(Pt(hw, hh - r), inHandle = Pt(hw, hh - r - (hh - r)), outHandle = Pt(hw, hh - r + kr)),
            Knot(Pt(hw - r, hh), inHandle = Pt(hw - r + kr, hh), outHandle = Pt(hw - r - (hw - r), hh)),
            Knot(Pt(-hw + r, hh), inHandle = Pt(-hw + r + (hw - r), hh), outHandle = Pt(-hw + r - kr, hh)),
            Knot(Pt(-hw, hh - r), inHandle = Pt(-hw, hh - r + kr), outHandle = Pt(-hw, hh - r - (hh - r))),
            Knot(Pt(-hw, -hh + r), inHandle = Pt(-hw, -hh + r + (hh - r)), outHandle = Pt(-hw, -hh + r - kr)),
        ),
        closed = true,
    )
}

fun makeEllipse(rx: Float, ry: Float): Contour {
    val kx = rx * KAPPA
    val ky = ry * KAPPA
    return Contour(
        listOf(
            Knot(Pt(0f, -ry), inHandle = Pt(-kx, -ry), outHandle = Pt(kx, -ry)),
            Knot(Pt(rx, 0f), inHandle = Pt(rx, -ky), outHandle = Pt(rx, ky)),
            Knot(Pt(0f, ry), inHandle = Pt(kx, ry), outHandle = Pt(-kx, ry)),
            Knot(Pt(-rx, 0f), inHandle = Pt(-rx, ky), outHandle = Pt(-rx, -ky)),
        ),
        closed = true,
    )
}

fun makePolygon(sides: Int, radius: Float): Contour {
    val s = sides.coerceIn(3, 32)
    val knots = (0 until s).map { i ->
        val angle = -PI / 2.0 + (2.0 * PI * i) / s
        val p = Pt((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
        Knot(p)
    }
    return Contour(knots, closed = true)
}

fun makeStar(points: Int, innerR: Float, outerR: Float): Contour {
    val pCount = points.coerceIn(3, 32)
    val knots = (0 until pCount * 2).map { i ->
        val angle = -PI / 2.0 + (PI * i) / pCount
        val r = if (i % 2 == 0) outerR else innerR
        val p = Pt((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
        Knot(p)
    }
    return Contour(knots, closed = true)
}

fun makeLine(start: Pt, end: Pt): Contour = Contour(listOf(Knot(start), Knot(end)), closed = false)

fun Contour.reverse(): Contour {
    if (knots.isEmpty()) return this
    val rev = knots.reversed().map { k -> Knot(k.p, inHandle = k.outHandle, outHandle = k.inHandle) }
    return copy(knots = rev)
}

fun Contour.knifeCutAt(knotIndex: Int): List<Contour> {
    if (knots.size < 2 || knotIndex !in knots.indices) return listOf(this)
    if (closed) {
        val reordered = ArrayList<Knot>()
        for (i in knotIndex until knots.size) reordered.add(knots[i])
        for (i in 0 until knotIndex) reordered.add(knots[i])
        reordered.add(knots[knotIndex].copy())
        return listOf(Contour(reordered, closed = false, exclude = exclude))
    }
    if (knotIndex == 0 || knotIndex == knots.lastIndex) return listOf(this)
    val part1 = knots.subList(0, knotIndex + 1)
    val part2 = knots.subList(knotIndex, knots.size)
    return listOf(
        Contour(part1.toList(), closed = false, exclude = exclude),
        Contour(part2.toList(), closed = false, exclude = exclude),
    )
}

fun Contour.knifeCutSegment(segIndex: Int, t: Float): List<Contour> {
    val n = knots.size
    if (n < 2 || segIndex !in 0 until if (closed) n else n - 1) return listOf(this)
    val ct = t.coerceIn(0.001f, 0.999f)
    val a = knots[segIndex]
    val b = knots[(segIndex + 1) % n]
    if (a.outHandle == null && b.inHandle == null) {
        val mid = a.p + (b.p - a.p) * ct
        val newA = a.copy(outHandle = null)
        val splitKnot = Knot(mid, inHandle = null, outHandle = null)
        val newB = b.copy(inHandle = null)
        val list = ArrayList<Knot>()
        for (i in 0 until n) {
            if (i == segIndex) {
                list.add(newA)
                list.add(splitKnot)
            } else if (i == (segIndex + 1) % n) {
                list.add(newB)
            } else {
                list.add(knots[i])
            }
        }
        val splitIndex = segIndex + 1
        val modified = Contour(list, closed = closed, exclude = exclude)
        return modified.knifeCutAt(splitIndex)
    }
    val c1 = a.outHandle ?: (a.p + (b.p - a.p) * (1f / 3f))
    val c2 = b.inHandle ?: (a.p + (b.p - a.p) * (2f / 3f))
    val p01 = a.p + (c1 - a.p) * ct
    val p12 = c1 + (c2 - c1) * ct
    val p23 = c2 + (b.p - c2) * ct
    val p012 = p01 + (p12 - p01) * ct
    val p123 = p12 + (p23 - p12) * ct
    val mid = p012 + (p123 - p012) * ct
    val newA = a.copy(outHandle = p01.takeIf { it != a.p })
    val splitKnot = Knot(mid, inHandle = p012.takeIf { it != mid }, outHandle = p123.takeIf { it != mid })
    val newB = b.copy(inHandle = p23.takeIf { it != b.p })

    val list = ArrayList<Knot>()
    for (i in 0 until n) {
        if (i == segIndex) {
            list.add(newA)
            list.add(splitKnot)
        } else if (i == (segIndex + 1) % n) {
            list.add(newB)
        } else {
            list.add(knots[i])
        }
    }
    val splitIndex = segIndex + 1
    val modified = Contour(list, closed = closed, exclude = exclude)
    return modified.knifeCutAt(splitIndex)
}

private fun lineIntersection(p1: Pt, p2: Pt, q1: Pt, q2: Pt): Float? {
    val d = (p2.x - p1.x) * (q2.y - q1.y) - (p2.y - p1.y) * (q2.x - q1.x)
    if (abs(d) < 1e-6f) return null
    val u = ((q1.x - p1.x) * (q2.y - q1.y) - (q1.y - p1.y) * (q2.x - q1.x)) / d
    val v = ((q1.x - p1.x) * (p2.y - p1.y) - (q1.y - p1.y) * (p2.x - p1.x)) / d
    return if (u in 0f..1f && v in 0f..1f) u else null
}

fun knifeCutContoursWithLine(contours: List<Contour>, cutStart: Pt, cutEnd: Pt): List<Contour> {
    val out = ArrayList<Contour>()
    for (c in contours) {
        if (c.knots.size < 2) {
            out.add(c)
            continue
        }
        var cutContour = c
        var cutsFound = 0
        var seg = 0
        while (seg < (if (cutContour.closed) cutContour.knots.size else cutContour.knots.size - 1) && cutsFound < 8) {
            val a = cutContour.knots[seg]
            val b = cutContour.knots[(seg + 1) % cutContour.knots.size]
            var hitT: Float? = null
            if (a.outHandle == null && b.inHandle == null) {
                hitT = lineIntersection(a.p, b.p, cutStart, cutEnd)
            } else {
                val c1 = a.outHandle ?: a.p
                val c2 = b.inHandle ?: b.p
                var prevPt = a.p
                val steps = 8
                for (s in 1..steps) {
                    val t = s.toFloat() / steps
                    val pt = evalCubic(a.p, c1, c2, b.p, t)
                    val tSub = lineIntersection(prevPt, pt, cutStart, cutEnd)
                    if (tSub != null) {
                        hitT = (s - 1 + tSub) / steps
                        break
                    }
                    prevPt = pt
                }
            }
            if (hitT != null && hitT in 0.05f..0.95f) {
                val pieces = cutContour.knifeCutSegment(seg, hitT)
                if (pieces.size == 1) {
                    cutContour = pieces[0]
                } else if (pieces.size > 1) {
                    out.addAll(pieces)
                    cutsFound++
                    break
                }
                cutsFound++
            }
            seg++
        }
        if (cutsFound == 0) {
            out.add(cutContour)
        }
    }
    return out
}

fun joinContours(c1: Contour, c2: Contour, tolerance: Float = 40f): Contour? {
    if (c1.closed || c2.closed) return null
    if (c1.knots.isEmpty() || c2.knots.isEmpty()) return null
    val s1 = c1.knots.first().p
    val e1 = c1.knots.last().p
    val s2 = c2.knots.first().p
    val e2 = c2.knots.last().p

    val dE1S2 = e1.distanceTo(s2)
    val dE1E2 = e1.distanceTo(e2)
    val dS1S2 = s1.distanceTo(s2)
    val dS1E2 = s1.distanceTo(e2)

    val minD = min(min(dE1S2, dE1E2), min(dS1S2, dS1E2))
    if (minD > tolerance) return null

    return when (minD) {
        dE1S2 -> Contour(c1.knots + c2.knots.drop(if (minD < 1f) 1 else 0), closed = false)
        dE1E2 -> {
            val r2 = c2.reverse()
            Contour(c1.knots + r2.knots.drop(if (minD < 1f) 1 else 0), closed = false)
        }
        dS1S2 -> {
            val r1 = c1.reverse()
            Contour(r1.knots + c2.knots.drop(if (minD < 1f) 1 else 0), closed = false)
        }
        else -> {
            val r1 = c1.reverse()
            val r2 = c2.reverse()
            Contour(r1.knots + r2.knots.drop(if (minD < 1f) 1 else 0), closed = false)
        }
    }
}

fun joinNearestContours(contours: List<Contour>, tolerance: Float = 40f): List<Contour> {
    if (contours.size < 2) return contours
    for (i in 0 until contours.size - 1) {
        val c1 = contours[i]
        if (c1.closed) continue
        for (j in i + 1 until contours.size) {
            val c2 = contours[j]
            if (c2.closed) continue
            val joined = joinContours(c1, c2, tolerance)
            if (joined != null) {
                val out = ArrayList<Contour>()
                for (k in contours.indices) {
                    if (k == i) out.add(joined)
                    else if (k != j) out.add(contours[k])
                }
                return out
            }
        }
    }
    return contours
}

fun Contour.resample(targetKnotCount: Int): Contour {
    val target = targetKnotCount.coerceIn(3, 256)
    if (knots.size < 2) return this
    val n = knots.size
    val segments = if (closed) n else n - 1
    val subStep = 32
    val samples = ArrayList<Pt>()
    for (i in 0 until segments) {
        val a = knots[i]
        val b = knots[(i + 1) % n]
        val c1 = a.outHandle ?: a.p
        val c2 = b.inHandle ?: b.p
        for (s in 0 until subStep) {
            val t = s.toFloat() / subStep
            samples.add(evalCubic(a.p, c1, c2, b.p, t))
        }
    }
    if (!closed) samples.add(knots.last().p)

    val cumulative = ArrayList<Float>(samples.size)
    cumulative.add(0f)
    for (i in 1 until samples.size) {
        cumulative.add(cumulative[i - 1] + samples[i].distanceTo(samples[i - 1]))
    }
    val totalL = cumulative.last()
    if (totalL < 1e-4f) return this

    val targetKnots = ArrayList<Knot>()
    val count = if (closed) target else target - 1
    val stepL = totalL / count

    for (k in 0 until target) {
        val targetL = (k * stepL).coerceIn(0f, totalL)
        var low = 0
        var high = cumulative.size - 1
        while (low < high - 1) {
            val mid = (low + high) / 2
            if (cumulative[mid] <= targetL) low = mid else high = mid
        }
        val segL = cumulative[high] - cumulative[low]
        val tFrac = if (segL < 1e-6f) 0f else (targetL - cumulative[low]) / segL
        val pt = samples[low] + (samples[high] - samples[low]) * tFrac
        targetKnots.add(Knot(pt))
    }
    val resampled = Contour(targetKnots, closed = closed, exclude = exclude)
    var smoothed = resampled
    for (k in targetKnots.indices) {
        smoothed = smoothed.toAutoSmooth(k)
    }
    return smoothed
}

fun resamplePairForMorph(c1: Contour, c2: Contour): Pair<Contour, Contour> {
    val count = max(max(c1.knots.size, c2.knots.size), 16)
    return c1.resample(count) to c2.resample(count)
}

object DashPresets {
    val Solid = floatArrayOf()
    val Dash = floatArrayOf(16f, 10f)
    val Dot = floatArrayOf(3f, 8f)
    val DashDot = floatArrayOf(16f, 8f, 3f, 8f)
    val LongDash = floatArrayOf(28f, 12f)
}

fun parseDashPattern(str: String?): FloatArray {
    if (str.isNullOrBlank()) return DashPresets.Solid
    return str.split(',').mapNotNull { it.trim().toFloatOrNull()?.takeIf { v -> v > 0f } }.toFloatArray()
}

fun formatDashPattern(pattern: FloatArray): String {
    if (pattern.isEmpty()) return ""
    return pattern.joinToString(",") { String.format(Locale.US, "%.1f", it).trimEnd('0').trimEnd('.') }
}

fun autoFitDashPattern(pattern: FloatArray, length: Float): FloatArray {
    if (pattern.isEmpty() || length < 1e-3f) return pattern
    val cycle = pattern.sum()
    if (cycle < 1e-3f) return pattern
    val count = round(length / cycle).coerceAtLeast(1f)
    val scale = length / (count * cycle)
    return FloatArray(pattern.size) { pattern[it] * scale }
}

fun snapPoint(
    p: Pt,
    grid: Float,
    snapGrid: Boolean,
    snapNodes: Boolean,
    existingKnots: List<Pt>,
    tolerance: Float = 10f,
): SnapResult {
    if (snapNodes) {
        for (k in existingKnots) {
            if (p.distanceTo(k) <= tolerance) {
                return SnapResult(k, SnapGuide(k, p, "Node"))
            }
        }
    }
    var sx = p.x
    var sy = p.y
    var guided = false
    var guide: SnapGuide? = null
    if (abs(p.x) <= tolerance) {
        sx = 0f
        guided = true
        guide = SnapGuide(Pt(0f, p.y - 100f), Pt(0f, p.y + 100f), "Center X")
    }
    if (abs(p.y) <= tolerance) {
        sy = 0f
        guided = true
        guide = SnapGuide(Pt(p.x - 100f, 0f), Pt(p.x + 100f, 0f), "Center Y")
    }
    if (snapGrid && grid >= 1f) {
        val gx = round(p.x / grid) * grid
        val gy = round(p.y / grid) * grid
        if (abs(p.x - gx) <= tolerance) {
            sx = gx
            guided = true
            guide = SnapGuide(Pt(gx, p.y - 50f), Pt(gx, p.y + 50f), "Grid")
        }
        if (abs(p.y - gy) <= tolerance) {
            sy = gy
            guided = true
            guide = SnapGuide(Pt(p.x - 50f, gy), Pt(p.x + 50f, gy), "Grid")
        }
    }
    val resPt = Pt(sx, sy)
    return SnapResult(resPt, if (guided) guide else null)
}

fun snapHandleAngle(
    origin: Pt,
    handle: Pt,
    stepDegrees: Float = 15f,
    toleranceDegrees: Float = 4f,
): Pt {
    val v = handle - origin
    val l = v.length
    if (l < 1e-4f) return handle
    val deg = v.angleDegrees()
    val snappedDeg = round(deg / stepDegrees) * stepDegrees
    if (abs(deg - snappedDeg) <= toleranceDegrees) {
        val rad = Math.toRadians(snappedDeg.toDouble())
        return Pt(origin.x + (l * cos(rad)).toFloat(), origin.y + (l * sin(rad)).toFloat())
    }
    return handle
}

fun exportSvg(contours: List<Contour>): String {
    if (contours.isEmpty()) return ""
    return contours.joinToString(" ") { it.toSvg() }.trim()
}

fun importSvg(svgD: String): List<Contour> {
    if (svgD.isBlank()) return emptyList()
    return parseSvgPath(svgD)
}
