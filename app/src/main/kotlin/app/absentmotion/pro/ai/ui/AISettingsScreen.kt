@file:Suppress("DEPRECATION")
package app.absentmotion.pro.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.pro.ai.memory.AIModelMemoryManager
import app.absentmotion.pro.ai.memory.AIProfileConfig
import app.absentmotion.pro.ai.memory.AIProfileType
import app.absentmotion.pro.ai.model.AIModelCategory
import app.absentmotion.pro.ai.model.AIModelDef
import app.absentmotion.pro.ai.model.AIModelId
import app.absentmotion.pro.ai.model.AIModelManager
import app.absentmotion.pro.ai.model.AIModelRegistry
import app.absentmotion.pro.ai.model.AIModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val memoryManager = AIModelMemoryManager.getInstance(context)
    val modelManager = AIModelManager.getInstance(context)

    val profileConfig by memoryManager.configFlow.collectAsState()
    val modelStates by modelManager.modelStates.collectAsState()
    val totalStorageBytes by modelManager.totalStorageBytes.collectAsState()

    var selectedCategoryTab by remember { mutableStateOf("All") }
    val categoryTabs = listOf("All", "Super Resolution", "Frame Interpolation", "Matting", "3D Depth", "Audio & DSP")

    val displayedModels = remember(selectedCategoryTab) {
        when (selectedCategoryTab) {
            "Super Resolution" -> AIModelRegistry.models.filter { it.category == AIModelCategory.SUPER_RESOLUTION }
            "Frame Interpolation" -> AIModelRegistry.models.filter { it.category == AIModelCategory.FRAME_INTERPOLATION }
            "Matting" -> AIModelRegistry.models.filter { it.category == AIModelCategory.MATTING }
            "3D Depth" -> AIModelRegistry.models.filter { it.category == AIModelCategory.DEPTH_ESTIMATION }
            "Audio & DSP" -> AIModelRegistry.models.filter {
                it.category == AIModelCategory.AUDIO_ENHANCE ||
                it.category == AIModelCategory.VIDEO_STABILIZATION ||
                it.category == AIModelCategory.FRAME_OPTIMIZATION
            }
            else -> AIModelRegistry.models
        }
    }

    val totalRamGb = memoryManager.getDeviceTotalRamBytes().toDouble() / (1024 * 1024 * 1024)
    val availRamGb = memoryManager.getDeviceAvailRamBytes().toDouble() / (1024 * 1024 * 1024)
    val totalInstalledStorageMb = totalStorageBytes.toDouble() / (1024 * 1024)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F121C))
                .border(1.dp, Color(0xFF22293C), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Absent Motion Pro AI Engine",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Intelligent Upscaling & Performance Controls",
                                color = Color(0xFF00FF9D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E2638))
                                .clickable { onDismiss() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // System RAM & Storage Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF161B28))
                            .border(1.dp, Color(0xFF263047), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Device Memory Status", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (memoryManager.isLowMemory()) "⚠️ Low Memory" else "✓ Healthy",
                                color = if (memoryManager.isLowMemory()) Color(0xFFFFB74D) else Color(0xFF00FF9D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total RAM: %.1f GB".format(totalRamGb), color = Color(0xFF8E9AA8), fontSize = 12.sp)
                            Text("Available: %.1f GB".format(availRamGb), color = Color(0xFF8E9AA8), fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI Models Disk Space: %.1f MB".format(totalInstalledStorageMb),
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (totalInstalledStorageMb > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF381C24))
                                        .clickable {
                                            AIModelRegistry.models.forEach { modelManager.deleteModel(it.id) }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Reclaim All Space", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Performance Profile Selector
                item {
                    Text(
                        text = "AI Processing Budget Profile",
                        color = Color(0xFF00FF9D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AIProfileType.entries.forEach { profile ->
                            val isSel = profileConfig.profileType == profile
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Color(0xFF00FF9D) else Color(0xFF161B28))
                                    .border(1.dp, if (isSel) Color(0xFF00FF9D) else Color(0xFF263047), RoundedCornerShape(10.dp))
                                    .clickable {
                                        memoryManager.saveConfig(profileConfig.copy(profileType = profile))
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = when (profile) {
                                            AIProfileType.LITE -> "Lite (3 GB)"
                                            AIProfileType.PRO -> "Pro (5 GB)"
                                            AIProfileType.CUSTOM -> "Custom"
                                        },
                                        color = if (isSel) Color(0xFF0D1117) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (profile) {
                                            AIProfileType.LITE -> "Safe / Slower"
                                            AIProfileType.PRO -> "Speed / GPU"
                                            AIProfileType.CUSTOM -> "Tweak All"
                                        },
                                        color = if (isSel) Color(0xFF1A3326) else Color(0xFF8E9AA8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Custom Profile Controls (if Custom selected)
                if (profileConfig.profileType == AIProfileType.CUSTOM) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141926))
                                .border(1.dp, Color(0xFF2B3650), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Custom Profile Parameters",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            val customBudgetGb = profileConfig.customBudgetBytes.toFloat() / (1024 * 1024 * 1024)
                            Text(
                                text = "AI Budget Ceiling: %.1f GB".format(customBudgetGb),
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Slider(
                                value = customBudgetGb,
                                onValueChange = { gb ->
                                    val b = (gb * 1024 * 1024 * 1024).toLong()
                                    memoryManager.saveConfig(profileConfig.copy(customBudgetBytes = b))
                                },
                                valueRange = 1f..8f,
                                steps = 13,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00FF9D),
                                    activeTrackColor = Color(0xFF00FF9D)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GPU Acceleration (Vulkan)", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                Switch(
                                    checked = profileConfig.gpuAcceleration,
                                    onCheckedChange = { checked ->
                                        memoryManager.saveConfig(profileConfig.copy(gpuAcceleration = checked))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00FF9D),
                                        checkedTrackColor = Color(0xFF1B3D2E)
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Thermal Throttling Protection", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                Switch(
                                    checked = profileConfig.thermalThrottlingProtection,
                                    onCheckedChange = { checked ->
                                        memoryManager.saveConfig(profileConfig.copy(thermalThrottlingProtection = checked))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00FF9D),
                                        checkedTrackColor = Color(0xFF1B3D2E)
                                    )
                                )
                            }
                        }
                    }
                }

                // AI Model Registry & Manager
                item {
                    Column {
                        Text(
                            text = "AI Models Registry (${AIModelRegistry.models.size} models)",
                            color = Color(0xFF00FF9D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Every model is optional. No models are bundled inside the APK.",
                            color = Color(0xFF8E9AA8),
                            fontSize = 11.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        // Category Filter Tabs (prevents loading/rendering everything at once)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categoryTabs.forEach { tabName ->
                                val selected = selectedCategoryTab == tabName
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFF00FF9D) else Color(0xFF1A2234))
                                        .clickable { selectedCategoryTab = tabName }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tabName,
                                        color = if (selected) Color.Black else Color(0xFFCAD5E2),
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                items(displayedModels, key = { it.id.name }) { modelDef ->
                    val state = modelStates[modelDef.id] ?: AIModelState.NotInstalled
                    ModelCard(
                        model = modelDef,
                        state = state,
                        onDownload = { modelManager.downloadModel(modelDef.id) },
                        onPause = { modelManager.pauseDownload(modelDef.id) },
                        onCancel = { modelManager.cancelDownload(modelDef.id) },
                        onDelete = { modelManager.deleteModel(modelDef.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: AIModelDef,
    state: AIModelState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141824))
            .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = model.category.displayName,
                    color = Color(0xFF00FF9D),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Size / Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when (state) {
                            is AIModelState.Installed -> Color(0xFF143826)
                            is AIModelState.Downloading -> Color(0xFF1E283D)
                            else -> Color(0xFF202636)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (state) {
                        is AIModelState.Installed -> "✓ Installed"
                        is AIModelState.Downloading -> "%d%%".format((state.progress * 100).toInt())
                        is AIModelState.Paused -> "Paused"
                        else -> model.sizeFormatted
                    },
                    color = when (state) {
                        is AIModelState.Installed -> Color(0xFF00FF9D)
                        is AIModelState.Downloading -> Color(0xFF38BDF8)
                        else -> Color(0xFFCBD5E1)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = model.description,
            color = Color(0xFF8E9AA8),
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        if (model.isSharedInstance && model.sharedFeatureNote.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "⚡ ${model.sharedFeatureNote}",
                color = Color(0xFFFFB74D),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Progress bar if downloading
        if (state is AIModelState.Downloading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF00FF9D),
                trackColor = Color(0xFF222B3D)
            )
            Spacer(Modifier.height(4.dp))
            val speedKb = state.speedBytesPerSec / 1024
            Text(
                text = "%d KB/s".format(speedKb),
                color = Color(0xFF8E9AA8),
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                is AIModelState.Downloading -> {
                    Text(
                        text = "Pause",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onPause() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Cancel",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onCancel() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                is AIModelState.Paused -> {
                    Text(
                        text = "Resume",
                        color = Color(0xFF00FF9D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDownload() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Cancel",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onCancel() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                is AIModelState.Installed -> {
                    if (model.files.isNotEmpty()) {
                        Text(
                            text = "Delete",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onDelete() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00FF9D))
                            .clickable { onDownload() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Download", color = Color(0xFF0D1117), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
