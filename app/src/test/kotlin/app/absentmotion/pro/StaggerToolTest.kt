package app.absentmotion.pro

import app.absentmotion.model.Scene
import app.absentmotion.model.XmlNode
import org.junit.Assert.assertEquals
import org.junit.Test

class StaggerToolTest {

    @Test
    fun staggerLayersShiftsStartAndEndTimesSequentially() {
        val xml = """
            <scene width="1080" height="1920" fps="30" totalTime="5000">
                <shape id="101" startTime="0" endTime="1000"/>
                <shape id="102" startTime="0" endTime="1000"/>
                <shape id="103" startTime="0" endTime="1000"/>
            </scene>
        """.trimIndent()
        val scene = Scene(XmlNode.parse(xml))

        val staggered = StaggerTool.stagger(
            scene = scene,
            layerIds = listOf(101L, 102L, 103L),
            offsetFrames = 5,
            order = StaggerOrder.TopToBottom
        )

        val layers = staggered.layers
        assertEquals(3, layers.size)

        val expectedOffsetMs = (5 * 1000L / 30).toInt()

        assertEquals(0, layers[0].startTime)
        assertEquals(1000, layers[0].endTime)

        assertEquals(expectedOffsetMs, layers[1].startTime)
        assertEquals(expectedOffsetMs + 1000, layers[1].endTime)

        assertEquals(2 * expectedOffsetMs, layers[2].startTime)
        assertEquals(2 * expectedOffsetMs + 1000, layers[2].endTime)
    }

    @Test
    fun staggerPreservesDurations() {
        val xml = """
            <scene width="1080" height="1920" fps="30" totalTime="5000">
                <shape id="201" startTime="100" endTime="600"/>
                <shape id="202" startTime="200" endTime="800"/>
            </scene>
        """.trimIndent()
        val scene = Scene(XmlNode.parse(xml))

        val staggered = StaggerTool.stagger(
            scene = scene,
            layerIds = listOf(201L, 202L),
            offsetFrames = 10,
            order = StaggerOrder.TopToBottom
        )

        val l1 = staggered.layers[0]
        val l2 = staggered.layers[1]

        assertEquals(500, l1.endTime - l1.startTime)
        assertEquals(600, l2.endTime - l2.startTime)
    }
}
