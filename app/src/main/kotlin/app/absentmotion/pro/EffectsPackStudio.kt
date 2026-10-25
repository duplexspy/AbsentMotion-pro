package app.absentmotion.pro

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.absentmotion.R
import app.absentmotion.effectspack.GlslTranslator
import app.absentmotion.effectspack.PackCatalog
import app.absentmotion.effectspack.PackEffectParam
import app.absentmotion.effectspack.PackStore
import app.absentmotion.platform.DialogStatusBarIcons
import app.absentmotion.platform.LocalContext
import app.absentmotion.platform.Toast
import app.absentmotion.platform.sheetDialogProperties
import app.absentmotion.ui.editor.ChromeIcons
import app.absentmotion.ui.editor.PanelIcons
import app.absentmotion.ui.editor.PhoneScreen
import app.absentmotion.ui.editor.effects.EffectIcons
import app.absentmotion.ui.home.HomeIcons
import app.absentmotion.ui.theme.Am
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun EffectsPackStudio(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var effectName by remember { mutableStateOf("My Custom FX") }
    var effectGroup by remember { mutableStateOf("Color and Grade") }
    var effectDesc by remember { mutableStateOf("Custom GLSL fragment effect") }
    var shaderCode by remember {
        mutableStateOf(
            """void main() {
    vec2 uv = acScreenNorm;
    vec4 col = texture2DCv(inputImg.texture, uv);
    gl_FragColor = col;
}"""
        )
    }

    val params = remember {
        mutableStateListOf(
            PackEffectParam("intensity", "Intensity", "float", "1.0", 0f, 2f, 0.05f)
        )
    }

    var compileStatus by remember { mutableStateOf<String?>("Ready") }
    var compileError by remember { mutableStateOf(false) }

    fun buildXml(): String {
        val safeId = "gm.custom." + effectName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val sb = StringBuilder()
        sb.append("<effect id=\"$safeId\" name=\"$effectName\" desc=\"$effectDesc\" group=\"$effectGroup\" category=\"custom\">\n")
        sb.append("  <params>\n")
        for (p in params) {
            sb.append("    <param id=\"${p.id}\" name=\"${p.label}\" type=\"${p.kind}\" default=\"${p.default}\"")
            if (p.kind == "float") {
                sb.append(" min=\"${p.min}\" max=\"${p.max}\" step=\"${p.step}\"")
            }
            sb.append("/>\n")
        }
        sb.append("  </params>\n")
        sb.append("  <glsl><![CDATA[\n")
        sb.append(shaderCode.trim())
        sb.append("\n]]></glsl>\n")
        sb.append("</effect>")
        return sb.toString()
    }

    fun compile() {
        try {
            val sksl = GlslTranslator.translate(shaderCode, params.toList())
            if (sksl.contains("half4 main(float2 coord)")) {
                compileStatus = "Shader SkSL translation succeeded (${sksl.length} chars)"
                compileError = false
            } else {
                compileStatus = "Shader does not produce valid entry point"
                compileError = true
            }
        } catch (e: Exception) {
            compileStatus = e.message ?: "Shader translation failed"
            compileError = true
        }
    }

    Dialog(onClose, sheetDialogProperties()) {
        PhoneScreen {
            DialogStatusBarIcons(darkIcons = Am.Card.luminance() > 0.5f)
            Box(Modifier.fillMaxSize().background(Am.SheetBg)) {
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                            Icon(HomeIcons.Close, stringResource(R.string.action_close), Modifier.size(24.dp), tint = Am.Navy)
                        }
                        Text(
                            stringResource(R.string.pro_effects_pack_studio),
                            Modifier.weight(1f).padding(horizontal = 8.dp),
                            color = Am.Navy,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            Modifier.clickable {
                                val xml = buildXml()
                                PackStore.importSingleXml(context, xml)
                                PackCatalog.refresh(context)
                                Toast.makeText(context, "Added $effectName to Custom Effects", Toast.LENGTH_SHORT).show()
                            }.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Install", color = Am.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("Effect Metadata", color = Am.Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        StudioField("Name", effectName) { effectName = it }
                        Spacer(Modifier.height(8.dp))
                        StudioField("Category Group", effectGroup) { effectGroup = it }
                        Spacer(Modifier.height(8.dp))
                        StudioField("Description", effectDesc) { effectDesc = it }
                        Spacer(Modifier.height(16.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("GLSL Fragment Shader", color = Am.Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = ::compile) {
                                Text("Compile", color = Am.Accent, fontWeight = FontWeight.Bold)
                            }
                        }

                        Box(
                            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E222B)).padding(10.dp)
                        ) {
                            BasicTextField(
                                value = shaderCode,
                                onValueChange = { shaderCode = it },
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                                textStyle = TextStyle(color = Color(0xFFE2E8F0), fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                cursorBrush = SolidColor(Am.Accent)
                            )
                        }

                        compileStatus?.let { status ->
                            Text(
                                status,
                                Modifier.padding(top = 6.dp),
                                color = if (compileError) Am.Warning else Am.Accent,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("Parameters (${params.size})", color = Am.Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        for (p in params) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Am.Card).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.label, color = Am.Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${p.id} (${p.kind}) = ${p.default}", color = Am.Navy.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Am.Navy.copy(alpha = 0.7f), fontSize = 11.sp)
        Box(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp)).background(Am.Card).padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Am.Navy, fontSize = 14.sp),
                singleLine = true,
                cursorBrush = SolidColor(Am.Accent)
            )
        }
    }
}
