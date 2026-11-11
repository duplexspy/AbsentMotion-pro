@file:Suppress("DEPRECATION")
package app.absentmotion.pro.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.pro.ai.model.AIModelDef
import app.absentmotion.pro.ai.model.AIModelId
import app.absentmotion.pro.ai.model.AIModelManager
import app.absentmotion.pro.ai.model.AIModelRegistry
import app.absentmotion.pro.ai.model.AIModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIModelDownloadDialog(
    modelId: AIModelId,
    onDismiss: () -> Unit,
    onModelReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val manager = AIModelManager.getInstance(context)
    val modelDef: AIModelDef = AIModelRegistry.get(modelId)
    val states by manager.modelStates.collectAsState()
    val state = states[modelId] ?: AIModelState.NotInstalled

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF131622))
                .border(1.dp, Color(0xFF232A3E), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E283D))
                        .border(1.dp, Color(0xFF384768), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🧠", fontSize = 22.sp)
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "AI Model Download Required",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = modelDef.name,
                    color = Color(0xFF00FF9D),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Model Info Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C0E16))
                        .border(1.dp, Color(0xFF1E2436), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = modelDef.description,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Download Size: ${modelDef.sizeFormatted}",
                            color = Color(0xFF8E9AA8),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "RAM: ${(modelDef.memoryReqBytes / (1024 * 1024))} MB",
                            color = Color(0xFF8E9AA8),
                            fontSize = 11.sp
                        )
                    }

                    if (modelDef.isSharedInstance && modelDef.sharedFeatureNote.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "💡 Shared Instance: ${modelDef.sharedFeatureNote}",
                            color = Color(0xFFFFB74D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "License: ${modelDef.license} (${modelDef.version})",
                        color = Color(0xFF6B7280),
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Progress or Status
                when (state) {
                    is AIModelState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF00FF9D),
                                trackColor = Color(0xFF222B3D)
                            )
                            Spacer(Modifier.height(6.dp))
                            val mbDone = state.downloadedBytes.toDouble() / (1024 * 1024)
                            val mbTotal = state.totalBytes.toDouble() / (1024 * 1024)
                            val speedKb = state.speedBytesPerSec / 1024
                            Text(
                                text = "Downloading: %.1f MB / %.1f MB (%d%%) • %d KB/s".format(mbDone, mbTotal, (state.progress * 100).toInt(), speedKb),
                                color = Color(0xFF8E9AA8),
                                fontSize = 11.sp
                            )
                        }
                    }
                    is AIModelState.Paused -> {
                        Text(
                            text = "Download Paused (%d%%)".format((state.progress * 100).toInt()),
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is AIModelState.Installed -> {
                        Text(
                            text = "✓ Model Ready & Verified",
                            color = Color(0xFF00FF9D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is AIModelState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Download failed: ${state.message}",
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF282B3E))
                                    .border(1.dp, Color(0xFF00FF9D), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val ready = manager.unpackBundledOrRetry(modelId)
                                        if (ready) {
                                            onModelReady()
                                            onDismiss()
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (manager.hasBundledWeights(modelId)) "📦 Unpack Bundled Weights" else "🔄 Retry Download",
                                    color = Color(0xFF00FF9D),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    AIModelState.NotInstalled -> {
                        Text(
                            text = "Models are kept outside the APK to keep your installation lightweight.",
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cancel or Close button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E2436))
                            .clickable {
                                if (state is AIModelState.Downloading) {
                                    manager.cancelDownload(modelId)
                                }
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state is AIModelState.Installed) "Close" else "Cancel",
                            color = Color(0xFF8E9AA8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Main Action button
                    when (state) {
                        is AIModelState.Downloading -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2C354D))
                                    .clickable { manager.pauseDownload(modelId) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Pause",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is AIModelState.Paused -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF00FF9D))
                                    .clickable { manager.downloadModel(modelId) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Resume",
                                    color = Color(0xFF0D1117),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is AIModelState.Installed -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF00FF9D))
                                    .clickable {
                                        onModelReady()
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Use Model",
                                    color = Color(0xFF0D1117),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF00FF9D))
                                    .clickable { manager.downloadModel(modelId) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Download",
                                    color = Color(0xFF0D1117),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                if (state !is AIModelState.Installed && manager.hasBundledWeights(modelId)) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Unpack bundled model weights offline without downloading",
                        color = Color(0xFF00FF9D),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                val ready = manager.unpackBundledOrRetry(modelId)
                                if (ready) {
                                    onModelReady()
                                    onDismiss()
                                }
                            }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}
