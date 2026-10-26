package app.absentmotion.pro

import app.absentmotion.model.Contour
import app.absentmotion.model.Handle
import app.absentmotion.model.Knot
import app.absentmotion.model.Pt
import app.absentmotion.model.withPointRemoved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VectorToolHarnessTest {
    @Test
    fun testShapeCreationRect() {
        val rect = makeRect(200f, 100f, 0f)
        assertTrue(rect.closed)
        assertEquals(4, rect.knots.size)
        assertEquals(-100f, rect.knots[0].p.x, 1e-3f)
        assertEquals(-50f, rect.knots[0].p.y, 1e-3f)
        assertEquals(100f, rect.knots[1].p.x, 1e-3f)
        assertEquals(-50f, rect.knots[1].p.y, 1e-3f)
    }

    @Test
    fun testShapeCreationRoundRect() {
        val rrect = makeRect(200f, 100f, 20f)
        assertTrue(rrect.closed)
        assertEquals(8, rrect.knots.size)
        assertTrue(rrect.knots.all { it.inHandle != null || it.outHandle != null })
    }

    @Test
    fun testShapeCreationEllipse() {
        val ellipse = makeEllipse(100f, 50f)
        assertTrue(ellipse.closed)
        assertEquals(4, ellipse.knots.size)
        assertEquals(Pt(0f, -50f), ellipse.knots[0].p)
        assertEquals(Pt(100f, 0f), ellipse.knots[1].p)
        assertEquals(Pt(0f, 50f), ellipse.knots[2].p)
        assertEquals(Pt(-100f, 0f), ellipse.knots[3].p)
        assertTrue(ellipse.knots.all { it.inHandle != null && it.outHandle != null })
    }

    @Test
    fun testShapeCreationPolygonAndStar() {
        val poly5 = makePolygon(5, 50f)
        assertTrue(poly5.closed)
        assertEquals(5, poly5.knots.size)

        val star5 = makeStar(5, 20f, 50f)
        assertTrue(star5.closed)
        assertEquals(10, star5.knots.size)
    }

    @Test
    fun testShapeCreationLine() {
        val line = makeLine(Pt(0f, 0f), Pt(100f, 50f))
        assertFalse(line.closed)
        assertEquals(2, line.knots.size)
        assertEquals(Pt(0f, 0f), line.knots[0].p)
        assertEquals(Pt(100f, 50f), line.knots[1].p)
    }

    @Test
    fun testKnotClassificationAndConversion() {
        val corner = Knot(Pt(0f, 0f))
        assertEquals(KnotType.Corner, corner.knotType())

        val smooth = corner.toSmooth()
        assertEquals(KnotType.Smooth, smooth.knotType())
        val vIn = smooth.inHandle!! - smooth.p
        val vOut = smooth.outHandle!! - smooth.p
        assertEquals(-1f, vIn.normalized().dot(vOut.normalized()), 1e-3f)

        val symmetric = corner.toSymmetric()
        assertEquals(KnotType.Symmetric, symmetric.knotType())
        assertEquals((symmetric.inHandle!! - symmetric.p).length, (symmetric.outHandle!! - symmetric.p).length, 1e-3f)

        val contour = Contour(listOf(Knot(Pt(0f, 0f)), Knot(Pt(50f, 50f)), Knot(Pt(100f, 0f))), closed = false)
        val autoSmoothed = contour.toAutoSmooth(1)
        assertNotNull(autoSmoothed.knots[1].inHandle)
        assertNotNull(autoSmoothed.knots[1].outHandle)
    }

    @Test
    fun testKnifeCutAtKnot() {
        val closedContour = Contour(listOf(Knot(Pt(0f, 0f)), Knot(Pt(100f, 0f)), Knot(Pt(100f, 100f)), Knot(Pt(0f, 100f))), closed = true)
        val cutOpen = closedContour.knifeCutAt(2)
        assertEquals(1, cutOpen.size)
        assertFalse(cutOpen[0].closed)
        assertEquals(5, cutOpen[0].knots.size)
        assertEquals(Pt(100f, 100f), cutOpen[0].knots[0].p)
        assertEquals(Pt(100f, 100f), cutOpen[0].knots.last().p)

        val openContour = Contour(listOf(Knot(Pt(0f, 0f)), Knot(Pt(50f, 0f)), Knot(Pt(100f, 0f))), closed = false)
        val cutTwo = openContour.knifeCutAt(1)
        assertEquals(2, cutTwo.size)
        assertEquals(2, cutTwo[0].knots.size)
        assertEquals(2, cutTwo[1].knots.size)
        assertEquals(Pt(50f, 0f), cutTwo[0].knots[1].p)
        assertEquals(Pt(50f, 0f), cutTwo[1].knots[0].p)
    }

    @Test
    fun testKnifeCutSegment() {
        val lineContour = Contour(listOf(Knot(Pt(0f, 0f)), Knot(Pt(100f, 0f))), closed = false)
        val split = lineContour.knifeCutSegment(0, 0.4f)
        assertEquals(2, split.size)
        assertEquals(40f, split[0].knots.last().p.x, 1e-2f)
        assertEquals(40f, split[1].knots.first().p.x, 1e-2f)
    }

    @Test
    fun testKnifeCutLineIntersection() {
        val rect = makeRect(200f, 200f, 0f)
        val cut = knifeCutContoursWithLine(listOf(rect), Pt(-150f, 0f), Pt(150f, 0f))
        assertTrue(cut.isNotEmpty())
    }

    @Test
    fun testJoinContours() {
        val c1 = Contour(listOf(Knot(Pt(0f, 0f)), Knot(Pt(50f, 0f))), closed = false)
        val c2 = Contour(listOf(Knot(Pt(55f, 0f)), Knot(Pt(100f, 0f))), closed = false)
        val joined = joinContours(c1, c2, tolerance = 10f)
        assertNotNull(joined)
        assertEquals(4, joined!!.knots.size)
        assertEquals(Pt(0f, 0f), joined.knots.first().p)
        assertEquals(Pt(100f, 0f), joined.knots.last().p)

        val list = listOf(c1, c2)
        val joinedList = joinNearestContours(list, tolerance = 10f)
        assertEquals(1, joinedList.size)
    }

    @Test
    fun testDashedLinePatternAndAutoFit() {
        val pattern = parseDashPattern("16, 8, 4, 8")
        assertEquals(4, pattern.size)
        assertEquals(16f, pattern[0], 1e-3f)
        assertEquals(8f, pattern[1], 1e-3f)

        val formatted = formatDashPattern(pattern)
        assertEquals("16,8,4,8", formatted)

        val totalLength = 100f
        val cycle = 36f
        val fitted = autoFitDashPattern(pattern, totalLength)
        val fittedCycle = fitted.sum()
        val numCycles = totalLength / fittedCycle
        assertEquals(numCycles, Math.round(numCycles.toDouble()).toFloat(), 1e-3f)
    }

    @Test
    fun testPathMorphingKnotResampling() {
        val rect = makeRect(100f, 100f, 0f)
        val resampled16 = rect.resample(16)
        assertTrue(resampled16.closed)
        assertEquals(16, resampled16.knots.size)

        val star = makeStar(5, 20f, 50f)
        val (pairA, pairB) = resamplePairForMorph(rect, star)
        assertEquals(pairA.knots.size, pairB.knots.size)
        assertTrue(pairA.knots.size >= 16)
    }

    @Test
    fun testSnappingEngine() {
        val target = Pt(19.5f, 41.2f)
        val snappedGrid = snapPoint(target, grid = 20f, snapGrid = true, snapNodes = false, existingKnots = emptyList(), tolerance = 5f)
        assertEquals(20f, snappedGrid.point.x, 1e-3f)
        assertEquals(40f, snappedGrid.point.y, 1e-3f)

        val nodePt = Pt(100f, 100f)
        val nearNode = Pt(102f, 99f)
        val snappedNode = snapPoint(nearNode, grid = 20f, snapGrid = false, snapNodes = true, existingKnots = listOf(nodePt), tolerance = 5f)
        assertEquals(nodePt, snappedNode.point)

        val origin = Pt(0f, 0f)
        val handleNear45 = Pt(10f, 10.5f)
        val snappedAngle = snapHandleAngle(origin, handleNear45, stepDegrees = 15f, toleranceDegrees = 5f)
        val angleDeg = (snappedAngle - origin).angleDegrees()
        assertEquals(45f, angleDeg, 0.5f)
    }

    @Test
    fun testSvgRoundTrip() {
        val rect = makeRect(100f, 60f, 0f)
        val svg = exportSvg(listOf(rect))
        assertTrue(svg.startsWith("M"))
        assertTrue(svg.endsWith("Z"))

        val imported = importSvg(svg)
        assertEquals(1, imported.size)
        assertEquals(4, imported[0].knots.size)
        assertTrue(imported[0].closed)
    }

    @Test
    fun testSafeModeCleanReversion() {
        ProFeatures.setSafeMode(context = null, enabled = false)
        assertTrue(ProFeatures.isEnabled(ProFeatures.newVectorTool))

        ProFeatures.setSafeMode(context = null, enabled = true)
        assertFalse(ProFeatures.isEnabled(ProFeatures.newVectorTool))

        ProFeatures.setSafeMode(context = null, enabled = false)
        assertTrue(ProFeatures.isEnabled(ProFeatures.newVectorTool))
    }

    @Test
    fun testContourPointRemoval() {
        val rect = makeRect(100f, 60f, 0f)
        assertEquals(4, rect.knots.size)
        val afterDelete = rect.withPointRemoved(1)
        assertEquals(3, afterDelete.knots.size)
        assertEquals(rect.knots[0].p, afterDelete.knots[0].p)
        assertEquals(rect.knots[2].p, afterDelete.knots[1].p)
        assertEquals(rect.knots[3].p, afterDelete.knots[2].p)
    }
}
