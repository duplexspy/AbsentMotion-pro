package app.absentmotion.pro

import app.absentmotion.model.LAYER_TAGS
import app.absentmotion.model.Layer
import app.absentmotion.model.Scene
import kotlin.random.Random

enum class StaggerOrder {
    TopToBottom,
    BottomToTop,
    Random,
    InsideOut
}

object StaggerTool {

    fun stagger(
        scene: Scene,
        layerIds: List<Long>,
        offsetFrames: Int,
        order: StaggerOrder = StaggerOrder.TopToBottom
    ): Scene {
        if (offsetFrames == 0 || layerIds.isEmpty()) return scene

        val fps = scene.fps.coerceAtLeast(1)
        val offsetMs = (offsetFrames * 1000L / fps).toInt()

        val allLayers = scene.layers
        val targetLayers = allLayers.filter { it.id in layerIds }
        if (targetLayers.isEmpty()) return scene

        val ordered = when (order) {
            StaggerOrder.TopToBottom -> targetLayers
            StaggerOrder.BottomToTop -> targetLayers.reversed()
            StaggerOrder.Random -> targetLayers.shuffled(Random(42))
            StaggerOrder.InsideOut -> {
                val mid = targetLayers.size / 2
                val list = ArrayList<Layer>()
                var left = mid - 1
                var right = mid
                while (left >= 0 || right < targetLayers.size) {
                    if (right < targetLayers.size) list.add(targetLayers[right++])
                    if (left >= 0) list.add(targetLayers[left--])
                }
                list
            }
        }

        val offsets = HashMap<Long, Int>()
        ordered.forEachIndexed { index, layer ->
            offsets[layer.id] = index * offsetMs
        }

        val updatedChildren = scene.node.children.map { node ->
            if (node.name !in LAYER_TAGS) return@map node
            val layer = Layer(node)
            val delta = offsets[layer.id] ?: return@map node

            val duration = (layer.endTime - layer.startTime).coerceAtLeast(1)
            val newStart = (layer.startTime + delta).coerceAtLeast(0)
            val newEnd = newStart + duration

            node.with("startTime", newStart.toString())
                .with("endTime", newEnd.toString())
        }

        return Scene(scene.node.copy(children = updatedChildren))
    }
}
