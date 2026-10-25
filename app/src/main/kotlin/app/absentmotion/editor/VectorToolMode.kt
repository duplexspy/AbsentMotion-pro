package app.absentmotion.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.absentmotion.model.Handle
import app.absentmotion.model.POINT_EDIT
import app.absentmotion.pro.KnotType
import app.absentmotion.pro.ShapeType
import app.absentmotion.pro.SnapGuide
import app.absentmotion.pro.VectorSubtool

class VectorToolMode(layerId: Long, created: Boolean) : DrawingMode(layerId, created) {
    var subtool by mutableStateOf(VectorSubtool.Pen)
    var shapeType by mutableStateOf(ShapeType.Rect)
    var selectedContour by mutableIntStateOf(0)
    var selectedKnot by mutableIntStateOf(-1)
    var selectedHandle by mutableStateOf(Handle.Main)
    var knotType by mutableStateOf(KnotType.Corner)

    var snappingEnabled by mutableStateOf(true)
    var snapGrid by mutableStateOf(false)
    var snapAngle by mutableStateOf(true)
    var snapNodes by mutableStateOf(true)
    var gridSize by mutableFloatStateOf(20f)

    var loupeActive by mutableStateOf(false)
    var loupePosition by mutableStateOf(Offset.Zero)
    var loupeTarget by mutableStateOf(Offset.Zero)

    var activeGuide by mutableStateOf<SnapGuide?>(null)

    var polygonSides by mutableIntStateOf(5)
    var starPoints by mutableIntStateOf(5)
    var starInnerRatio by mutableFloatStateOf(0.4f)
    var rectRoundness by mutableFloatStateOf(16f)

    var previewScale by mutableFloatStateOf(1f)

    var dashPresetIndex by mutableIntStateOf(0)
    var dashOffset by mutableFloatStateOf(0f)
    var dashAutoFit by mutableStateOf(false)
    var taperStart by mutableFloatStateOf(0f)
    var taperEnd by mutableFloatStateOf(0f)

    var nudgeStep by mutableFloatStateOf(1f)

    override val handlesCode: Int
        get() = POINT_EDIT or (selectedContour.coerceIn(0, 127) shl 20) or ((selectedKnot.coerceAtLeast(0)).coerceIn(0, 0xffff) shl 4) or selectedHandle.ordinal
}
