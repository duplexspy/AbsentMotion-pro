package app.absentmotion.pro

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.addModel
import app.absentmotion.library.ProjectStore
import app.absentmotion.ui.editor.ChromeIcons
import app.absentmotion.ui.theme.Am
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class Shape3dDef(
    val id: String,
    val name: String,
    val category: String,
    val triangleCount: Int,
    val description: String,
    val generator: () -> String,
)

object Shape3dCatalog {
    val Shapes: List<Shape3dDef> = listOf(
        Shape3dDef(
            id = "cube",
            name = "Cube",
            category = "Basic Primitives",
            triangleCount = 12,
            description = "Standard 6-sided isometric cube with sharp edges and unit dimensions.",
            generator = ::generateCubeObj
        ),
        Shape3dDef(
            id = "uv_sphere",
            name = "UV Sphere",
            category = "Curved Surfaces",
            triangleCount = 480,
            description = "Smooth 16-ring latitude/longitude sphere suitable for planets, globes, and beads.",
            generator = ::generateSphereObj
        ),
        Shape3dDef(
            id = "cylinder",
            name = "Cylinder",
            category = "Basic Primitives",
            triangleCount = 64,
            description = "Smooth 16-sided cylinder with planar top and bottom circular caps.",
            generator = ::generateCylinderObj
        ),
        Shape3dDef(
            id = "cone",
            name = "Cone",
            category = "Basic Primitives",
            triangleCount = 32,
            description = "Tapered cone with pointed apex and circular base cap.",
            generator = ::generateConeObj
        ),
        Shape3dDef(
            id = "torus",
            name = "Torus (Donut)",
            category = "Curved Surfaces",
            triangleCount = 512,
            description = "Smooth circular ring torus with adjustable tube radius.",
            generator = ::generateTorusObj
        ),
        Shape3dDef(
            id = "pyramid",
            name = "Pyramid",
            category = "Polyhedra",
            triangleCount = 6,
            description = "Classic 4-sided square pyramid with apex and flat base.",
            generator = ::generatePyramidObj
        ),
        Shape3dDef(
            id = "ground_plane",
            name = "Plane / Floor",
            category = "Architectural",
            triangleCount = 8,
            description = "Horizontal ground plane with quad subdivisions for floor shadows and reflections.",
            generator = ::generatePlaneObj
        ),
        Shape3dDef(
            id = "capsule",
            name = "Capsule",
            category = "Curved Surfaces",
            triangleCount = 384,
            description = "Elongated pill capsule with hemispherical dome ends.",
            generator = ::generateCapsuleObj
        ),
        Shape3dDef(
            id = "diamond",
            name = "Diamond",
            category = "Polyhedra",
            triangleCount = 16,
            description = "Brilliant faceted gemstone octahedron with upper crown and lower pavilion.",
            generator = ::generateDiamondObj
        ),
    )

    fun generateCubeObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Cube")
        appendLine("v -1.0 -1.0  1.0")
        appendLine("v  1.0 -1.0  1.0")
        appendLine("v  1.0  1.0  1.0")
        appendLine("v -1.0  1.0  1.0")
        appendLine("v -1.0 -1.0 -1.0")
        appendLine("v  1.0 -1.0 -1.0")
        appendLine("v  1.0  1.0 -1.0")
        appendLine("v -1.0  1.0 -1.0")
        appendLine("vn  0.0  0.0  1.0")
        appendLine("vn  0.0  0.0 -1.0")
        appendLine("vn  0.0  1.0  0.0")
        appendLine("vn  0.0 -1.0  0.0")
        appendLine("vn  1.0  0.0  0.0")
        appendLine("vn -1.0  0.0  0.0")
        appendLine("f 1//1 2//1 3//1")
        appendLine("f 1//1 3//1 4//1")
        appendLine("f 6//2 5//2 8//2")
        appendLine("f 6//2 8//2 7//2")
        appendLine("f 4//3 3//3 7//3")
        appendLine("f 4//3 7//3 8//3")
        appendLine("f 5//4 6//4 2//4")
        appendLine("f 5//4 2//4 1//4")
        appendLine("f 2//5 6//5 7//5")
        appendLine("f 2//5 7//5 3//5")
        appendLine("f 5//6 1//6 4//6")
        appendLine("f 5//6 4//6 8//6")
    }

    fun generateSphereObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D UV Sphere")
        val rings = 16
        val sectors = 16
        val r = 1.0f
        for (i in 0..rings) {
            val v = i.toFloat() / rings
            val phi = v * PI.toFloat()
            for (j in 0..sectors) {
                val u = j.toFloat() / sectors
                val theta = u * 2f * PI.toFloat()
                val x = r * sin(phi) * cos(theta)
                val y = r * cos(phi)
                val z = r * sin(phi) * sin(theta)
                appendLine("v $x $y $z")
                appendLine("vn ${x / r} ${y / r} ${z / r}")
            }
        }
        val stride = sectors + 1
        for (i in 0 until rings) {
            for (j in 0 until sectors) {
                val a = i * stride + j + 1
                val b = (i + 1) * stride + j + 1
                val c = (i + 1) * stride + j + 2
                val d = i * stride + j + 2
                appendLine("f $a//$a $b//$b $c//$c")
                appendLine("f $a//$a $c//$c $d//$d")
            }
        }
    }

    fun generateCylinderObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Cylinder")
        val segments = 16
        val r = 1.0f
        val h = 1.0f
        appendLine("v 0.0 $h 0.0")
        appendLine("vn 0.0 1.0 0.0")
        appendLine("v 0.0 -$h 0.0")
        appendLine("vn 0.0 -1.0 0.0")
        for (i in 0 until segments) {
            val theta = i.toFloat() / segments * 2f * PI.toFloat()
            val x = r * cos(theta)
            val z = r * sin(theta)
            appendLine("v $x $h $z")
            appendLine("vn $x 0.0 $z")
            appendLine("v $x -$h $z")
            appendLine("vn $x 0.0 $z")
        }
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            val topA = 3 + i * 2
            val botA = 4 + i * 2
            val topB = 3 + next * 2
            val botB = 4 + next * 2
            appendLine("f $topA $botA $botB")
            appendLine("f $topA $botB $topB")
            appendLine("f 1 $topA $topB")
            appendLine("f 2 $botB $botA")
        }
    }

    fun generateConeObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Cone")
        val segments = 16
        val r = 1.0f
        val h = 1.2f
        appendLine("v 0.0 $h 0.0")
        appendLine("v 0.0 -$h 0.0")
        for (i in 0 until segments) {
            val theta = i.toFloat() / segments * 2f * PI.toFloat()
            val x = r * cos(theta)
            val z = r * sin(theta)
            appendLine("v $x -$h $z")
        }
        for (i in 0 until segments) {
            val cur = 3 + i
            val next = 3 + ((i + 1) % segments)
            appendLine("f 1 $cur $next")
            appendLine("f 2 $next $cur")
        }
    }

    fun generateTorusObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Torus")
        val mainSeg = 16
        val tubeSeg = 16
        val R = 1.0f
        val r = 0.35f
        for (i in 0..mainSeg) {
            val u = i.toFloat() / mainSeg * 2f * PI.toFloat()
            for (j in 0..tubeSeg) {
                val v = j.toFloat() / tubeSeg * 2f * PI.toFloat()
                val x = (R + r * cos(v)) * cos(u)
                val y = r * sin(v)
                val z = (R + r * cos(v)) * sin(u)
                appendLine("v $x $y $z")
            }
        }
        val stride = tubeSeg + 1
        for (i in 0 until mainSeg) {
            for (j in 0 until tubeSeg) {
                val a = i * stride + j + 1
                val b = (i + 1) * stride + j + 1
                val c = (i + 1) * stride + j + 2
                val d = i * stride + j + 2
                appendLine("f $a $b $c")
                appendLine("f $a $c $d")
            }
        }
    }

    fun generatePyramidObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Pyramid")
        appendLine("v  0.0  1.0  0.0")
        appendLine("v -1.0 -1.0  1.0")
        appendLine("v  1.0 -1.0  1.0")
        appendLine("v  1.0 -1.0 -1.0")
        appendLine("v -1.0 -1.0 -1.0")
        appendLine("f 1 2 3")
        appendLine("f 1 3 4")
        appendLine("f 1 4 5")
        appendLine("f 1 5 2")
        appendLine("f 2 4 3")
        appendLine("f 2 5 4")
    }

    fun generatePlaneObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Ground Plane")
        appendLine("v -2.0 0.0 -2.0")
        appendLine("v  0.0 0.0 -2.0")
        appendLine("v  2.0 0.0 -2.0")
        appendLine("v -2.0 0.0  0.0")
        appendLine("v  0.0 0.0  0.0")
        appendLine("v  2.0 0.0  0.0")
        appendLine("v -2.0 0.0  2.0")
        appendLine("v  0.0 0.0  2.0")
        appendLine("v  2.0 0.0  2.0")
        appendLine("f 1 4 5")
        appendLine("f 1 5 2")
        appendLine("f 2 5 6")
        appendLine("f 2 6 3")
        appendLine("f 4 7 8")
        appendLine("f 4 8 5")
        appendLine("f 5 8 9")
        appendLine("f 5 9 6")
    }

    fun generateCapsuleObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Capsule")
        val rings = 8
        val sectors = 16
        val r = 0.8f
        val h = 0.6f
        for (i in 0..rings) {
            val v = i.toFloat() / rings * 0.5f * PI.toFloat()
            for (j in 0..sectors) {
                val u = j.toFloat() / sectors * 2f * PI.toFloat()
                val x = r * cos(v) * cos(u)
                val y = r * sin(v) + h
                val z = r * cos(v) * sin(u)
                appendLine("v $x $y $z")
            }
        }
        for (i in 0..rings) {
            val v = i.toFloat() / rings * 0.5f * PI.toFloat()
            for (j in 0..sectors) {
                val u = j.toFloat() / sectors * 2f * PI.toFloat()
                val x = r * cos(v) * cos(u)
                val y = -r * sin(v) - h
                val z = r * cos(v) * sin(u)
                appendLine("v $x $y $z")
            }
        }
        val stride = sectors + 1
        for (i in 0 until rings) {
            for (j in 0 until sectors) {
                val a = i * stride + j + 1
                val b = (i + 1) * stride + j + 1
                val c = (i + 1) * stride + j + 2
                val d = i * stride + j + 2
                appendLine("f $a $b $c")
                appendLine("f $a $c $d")
            }
        }
        val offset = (rings + 1) * stride
        for (i in 0 until rings) {
            for (j in 0 until sectors) {
                val a = offset + i * stride + j + 1
                val b = offset + (i + 1) * stride + j + 1
                val c = offset + (i + 1) * stride + j + 2
                val d = offset + i * stride + j + 2
                appendLine("f $a $c $b")
                appendLine("f $a $d $c")
            }
        }
        for (j in 0 until sectors) {
            val topA = (rings + 1) * stride - stride + j + 1
            val topB = (rings + 1) * stride - stride + j + 2
            val botA = offset + j + 1
            val botB = offset + j + 2
            appendLine("f $topA $botA $botB")
            appendLine("f $topA $botB $topB")
        }
    }

    fun generateDiamondObj(): String = buildString {
        appendLine("# Absent Motion Pro 3D Diamond")
        appendLine("v  0.0  1.4  0.0")
        appendLine("v  1.0  0.3  0.0")
        appendLine("v  0.7  0.3  0.7")
        appendLine("v  0.0  0.3  1.0")
        appendLine("v -0.7  0.3  0.7")
        appendLine("v -1.0  0.3  0.0")
        appendLine("v -0.7  0.3 -0.7")
        appendLine("v  0.0  0.3 -1.0")
        appendLine("v  0.7  0.3 -0.7")
        appendLine("v  0.0 -1.4  0.0")
        for (i in 2..9) {
            val next = if (i == 9) 2 else i + 1
            appendLine("f 1 $i $next")
            appendLine("f 10 $next $i")
        }
    }
}

suspend fun EditorState.insertProcedural3dShape(context: Context, shape: Shape3dDef) {
    withContext(Dispatchers.IO) {
        val folder = File(ProjectStore(context).mediaDir(projectId), "model-${shape.id}-${System.currentTimeMillis()}")
        folder.mkdirs()
        val file = File(folder, "${shape.id}.obj")
        file.writeText(shape.generator())
        val uri = file.toURI().toString()
        withContext(Dispatchers.Main) {
            addModel(uri, shape.name)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Shape3dLibraryDialog(
    onDismiss: () -> Unit,
    onSelectShape: (Shape3dDef) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember {
        listOf("All") + Shape3dCatalog.Shapes.map { it.category }.distinct()
    }

    val filteredShapes = remember(selectedCategory, searchQuery) {
        Shape3dCatalog.Shapes.filter { shape ->
            (selectedCategory == null || selectedCategory == "All" || shape.category == selectedCategory) &&
                (searchQuery.isBlank() || shape.name.contains(searchQuery, ignoreCase = true) || shape.description.contains(searchQuery, ignoreCase = true))
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Am.Card)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Am.Accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(ChromeIcons.Model3d, null, Modifier.size(20.dp), tint = Am.Accent)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "3D Shape & Model Library",
                        color = Am.Navy,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Absent Motion Pro Procedural Models",
                        color = Am.HomeMid,
                        fontSize = 11.sp
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(Am.Well).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ChromeIcons.Close, "Close", Modifier.size(16.dp), tint = Am.HomeDim)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = (selectedCategory == null && cat == "All") || selectedCategory == cat
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Am.Accent else Am.PanelInset)
                            .clickable { selectedCategory = if (cat == "All") null else cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            cat,
                            color = if (isSelected) Color.White else Am.OnDarkDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredShapes, key = { it.id }) { shape ->
                    Shape3dTile(shape = shape, onClick = {
                        onSelectShape(shape)
                        onDismiss()
                    })
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Am.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Shape3dTile(shape: Shape3dDef, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Am.PanelInset)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Am.Well),
            contentAlignment = Alignment.Center
        ) {
            Shape3dWireframeIcon(shape.id, Modifier.size(44.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            shape.name,
            color = Am.OnDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            "${shape.triangleCount} tris",
            color = Am.Accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Shape3dWireframeIcon(id: String, modifier: Modifier) {
    val strokeColor = Am.Accent
    val dimColor = Am.Accent.copy(alpha = 0.4f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        when (id) {
            "cube" -> {
                val s = w * 0.28f
                drawRect(dimColor, Offset(cx - s * 0.6f, cy - s * 0.6f), androidx.compose.ui.geometry.Size(s * 1.2f, s * 1.2f), style = Stroke(1.5f))
                drawRect(strokeColor, Offset(cx - s * 0.9f, cy - s * 0.2f), androidx.compose.ui.geometry.Size(s * 1.2f, s * 1.2f), style = Stroke(1.8f))
                drawLine(strokeColor, Offset(cx - s * 0.9f, cy - s * 0.2f), Offset(cx - s * 0.6f, cy - s * 0.6f), 1.5f)
                drawLine(strokeColor, Offset(cx + s * 0.3f, cy - s * 0.2f), Offset(cx + s * 0.6f, cy - s * 0.6f), 1.5f)
                drawLine(strokeColor, Offset(cx - s * 0.9f, cy + s * 1.0f), Offset(cx - s * 0.6f, cy + s * 0.6f), 1.5f)
                drawLine(strokeColor, Offset(cx + s * 0.3f, cy + s * 1.0f), Offset(cx + s * 0.6f, cy + s * 0.6f), 1.5f)
            }
            "uv_sphere" -> {
                drawCircle(strokeColor, w * 0.38f, Offset(cx, cy), style = Stroke(2f))
                drawOval(dimColor, Offset(cx - w * 0.38f, cy - h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.32f), style = Stroke(1.5f))
                drawOval(dimColor, Offset(cx - w * 0.16f, cy - h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.76f), style = Stroke(1.5f))
            }
            "cone", "pyramid" -> {
                val path = Path().apply {
                    moveTo(cx, cy - h * 0.42f)
                    lineTo(cx - w * 0.40f, cy + h * 0.36f)
                    lineTo(cx + w * 0.40f, cy + h * 0.36f)
                    close()
                }
                drawPath(path, strokeColor, style = Stroke(2f))
                drawLine(dimColor, Offset(cx, cy - h * 0.42f), Offset(cx, cy + h * 0.36f), 1.5f)
            }
            "cylinder", "capsule" -> {
                drawOval(strokeColor, Offset(cx - w * 0.34f, cy - h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.22f), style = Stroke(1.8f))
                drawOval(dimColor, Offset(cx - w * 0.34f, cy + h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.22f), style = Stroke(1.8f))
                drawLine(strokeColor, Offset(cx - w * 0.34f, cy - h * 0.27f), Offset(cx - w * 0.34f, cy + h * 0.27f), 2f)
                drawLine(strokeColor, Offset(cx + w * 0.34f, cy - h * 0.27f), Offset(cx + w * 0.34f, cy + h * 0.27f), 2f)
            }
            "torus" -> {
                drawCircle(strokeColor, w * 0.40f, Offset(cx, cy), style = Stroke(2f))
                drawCircle(dimColor, w * 0.20f, Offset(cx, cy), style = Stroke(1.8f))
            }
            "diamond" -> {
                val path = Path().apply {
                    moveTo(cx, cy - h * 0.42f)
                    lineTo(cx + w * 0.38f, cy - h * 0.05f)
                    lineTo(cx, cy + h * 0.42f)
                    lineTo(cx - w * 0.38f, cy - h * 0.05f)
                    close()
                }
                drawPath(path, strokeColor, style = Stroke(2f))
                drawLine(dimColor, Offset(cx - w * 0.38f, cy - h * 0.05f), Offset(cx + w * 0.38f, cy - h * 0.05f), 1.5f)
            }
            else -> {
                drawRect(strokeColor, Offset(cx - w * 0.35f, cy - h * 0.35f), androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.7f), style = Stroke(2f))
            }
        }
    }
}
