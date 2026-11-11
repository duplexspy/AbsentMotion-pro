package app.absentmotion.pro.labs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.FileProvider
import app.absentmotion.pro.ai.classifier.ModelRoutingMode
import app.absentmotion.pro.ai.model.AIModelId
import app.absentmotion.pro.ai.model.AIModelManager
import app.absentmotion.pro.ai.tile.AITileSettings
import app.absentmotion.pro.ai.ui.AIAdvancedSettingsSheet
import app.absentmotion.pro.ai.ui.AIModelDownloadDialog
import app.absentmotion.pro.assets.AssetStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmzToolRunnerDialog(
    tool: AmzLabTool,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AmzToolResult?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    var antiCompressIphone by remember { mutableStateOf(true) }
    var antiCompressFastStart by remember { mutableStateOf(true) }
    var antiCompressColor by remember { mutableStateOf(true) }
    var antiCompressCleanTags by remember { mutableStateOf(true) }

    var convertResolution by remember { mutableStateOf("1080p") }
    var convertFps by remember { mutableIntStateOf(30) }
    var convertCodec by remember { mutableStateOf("H.264") }

    var twixtorSpeed by remember { mutableIntStateOf(50) }
    var twixtorMode by remember { mutableStateOf("Motion") }
    var twixtorAudio by remember { mutableStateOf("Kept") }
    var twixtorEngine by remember { mutableStateOf("OpenCV DIS Optical Flow") }

    var morphMode by remember { mutableStateOf("Shape Morph") }
    var morphDurationMs by remember { mutableStateOf(1500L) }
    var morphEasing by remember { mutableStateOf("EaseInOut") }
    var morphMeshGrid by remember { mutableIntStateOf(8) }

    var audioFormat by remember { mutableStateOf("M4A") }
    var audioBitrate by remember { mutableIntStateOf(256) }

    var stabilizerMode by remember { mutableStateOf("Smooth Motion") }
    var stabilizerAutoCrop by remember { mutableStateOf(true) }

    var depthColormap by remember { mutableStateOf("Greyscale") }
    var upscaleScale by remember { mutableIntStateOf(4) }
    var interpMultiplier by remember { mutableIntStateOf(2) }
    var denoiseStrength by remember { mutableIntStateOf(85) }

    var tileSettings by remember { mutableStateOf(AITileSettings()) }
    var routingMode by remember { mutableStateOf(ModelRoutingMode.AUTO) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var pendingDownloadModel by remember { mutableStateOf<AIModelId?>(null) }

    val aiModelManager = remember { AIModelManager.getInstance(context) }

    fun getRequiredModel(): AIModelId? {
        return when (tool) {
            AmzLabTool.AiUpscale -> when (routingMode) {
                ModelRoutingMode.FORCE_REAL_LIFE -> AIModelId.REAL_ESRGAN_X4PLUS
                ModelRoutingMode.FORCE_ANIME_IMAGE -> AIModelId.REAL_ESRGAN_ANIME
                ModelRoutingMode.FORCE_ANIME_VIDEO -> AIModelId.REAL_ESRGAN_ANIME_VIDEO
                ModelRoutingMode.AUTO -> AIModelId.REAL_ESRGAN_X4PLUS
            }
            AmzLabTool.Twixtor -> AIModelId.RIFE_LITE
            AmzLabTool.AiFrameInterpolation -> AIModelId.RIFE_426
            AmzLabTool.AudioDenoise -> AIModelId.RNNOISE
            AmzLabTool.DepthMap -> AIModelId.DEPTH_ANYTHING_V2
            AmzLabTool.BackgroundRemover -> AIModelId.MODNET
            AmzLabTool.Stabilizer -> AIModelId.OPENCV_OPTICAL_FLOW
            AmzLabTool.DeadFrameRemover -> AIModelId.OPENCV_SSIM
            else -> null
        }
    }

    val fileMime = when (tool.category) {
        AmzLabCategory.AUDIO -> "audio/*"
        AmzLabCategory.AI -> if (tool == AmzLabTool.DepthMap || tool == AmzLabTool.BackgroundRemover || tool == AmzLabTool.AiUpscale) "image/*,video/*" else "video/*"
        else -> "video/*"
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "selected_media"
            result = null
        }
    }

    fun startProcessing() {
        val uri = selectedUri ?: return
        isProcessing = true
        progress = 0f
        statusMessage = "Starting process..."
        result = null

        activeJob = scope.launch {
            val res = try {
                when (tool) {
                AmzLabTool.TikTokAntiCompress -> {
                    val cfg = AntiCompressConfig(
                        iphoneTag = antiCompressIphone,
                        fastStart = antiCompressFastStart,
                        colorBt709 = antiCompressColor,
                        cleanTags = antiCompressCleanTags
                    )
                    AmzLabsProcessor.processTikTokAntiCompress(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.ReverseVideo -> {
                    AmzLabsProcessor.processReverseVideo(context, uri, ReverseConfig(true)) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.ConvertVideo -> {
                    val cfg = ConvertConfig(
                        resolution = convertResolution,
                        fps = convertFps,
                        codec = convertCodec
                    )
                    AmzLabsProcessor.processConvertVideo(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.AudioConverter -> {
                    val cfg = AudioConvertConfig(
                        format = audioFormat,
                        bitrateKbps = audioBitrate
                    )
                    AmzLabsProcessor.processAudioConverter(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.DeadFrameRemover -> {
                    AmzLabsProcessor.processDeadFrameRemover(context, uri, DeadFrameConfig()) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.Stabilizer -> {
                    val cfg = StabilizerConfig(
                        mode = stabilizerMode,
                        autoCrop = stabilizerAutoCrop
                    )
                    AmzLabsProcessor.processStabilizer(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.Twixtor -> {
                    val cfg = TwixtorConfig(
                        speedPercent = twixtorSpeed,
                        motionMode = twixtorMode,
                        audioPitch = twixtorAudio,
                        engine = twixtorEngine
                    )
                    AmzLabsProcessor.processTwixtor(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.MorphStudio -> {
                    val cfg = MorphStudioConfig(
                        mode = morphMode,
                        durationMs = morphDurationMs,
                        easing = morphEasing,
                        meshGrid = morphMeshGrid
                    )
                    var bmp: android.graphics.Bitmap? = null
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            bmp = android.graphics.BitmapFactory.decodeStream(stream)
                        }
                    }
                    AmzLabsProcessor.processMorphStudio(context, cfg, bmp, bmp) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.DepthMap -> {
                    val cfg = DepthMapConfig(colormap = depthColormap)
                    AmzLabsProcessor.processDepthMap(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.BackgroundRemover -> {
                    AmzLabsProcessor.processBackgroundRemover(context, uri, BgRemoverConfig()) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.AiUpscale -> {
                    val cfg = AiUpscaleConfig(scaleFactor = upscaleScale)
                    AmzLabsProcessor.processAiUpscale(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.AiFrameInterpolation -> {
                    val cfg = FrameInterpolationConfig(targetMultiplier = interpMultiplier)
                    AmzLabsProcessor.processAiFrameInterpolation(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                AmzLabTool.AudioDenoise -> {
                    val cfg = AudioDenoiseConfig(strength = denoiseStrength)
                    AmzLabsProcessor.processAudioDenoise(context, uri, cfg) { p, msg ->
                        progress = p
                        statusMessage = msg
                    }
                }
                }
            } catch (e: Throwable) {
                AmzToolResult(
                    success = false,
                    outputFile = null,
                    message = "Processing error: ${e.message ?: "Failed"}",
                    error = e.message ?: "Processing failed",
                    durationMs = 0L
                )
            }
            result = res
            if (res.success && res.outputFile != null) {
                runCatching {
                    AssetStore(context).registerAiResult(
                        title = res.outputFile.name,
                        file = res.outputFile,
                        toolCategory = tool.title,
                        details = "Rendered via ${tool.title} (${res.outputFile.length() / 1024} KB)"
                    )
                }
            }
            isProcessing = false
        }
    }

    fun checkAndStartProcessing() {
        val uri = selectedUri ?: return
        val reqModel = getRequiredModel()
        if (reqModel != null && !aiModelManager.isInstalled(reqModel)) {
            pendingDownloadModel = reqModel
            return
        }
        startProcessing()
    }

    BasicAlertDialog(onDismissRequest = {
        if (!isProcessing) onDismiss()
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF171B26))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SidBadge(size = 48.dp)

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Absent Mation Labs: A copy of After Motion",
                    color = Color(0xFF00FF9D).copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = tool.title,
                    color = Color(0xFF00FF9D),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = tool.subtitle,
                    color = Color(0xFF8E9AA8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF202637))
                        .clickable(enabled = !isProcessing) {
                            val pickMime = if (fileMime.contains(',')) "*/*" else fileMime
                            launcher.launch(pickMime)
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedFileName == null) "Tap to Select Source File" else "Selected: $selectedFileName",
                            color = if (selectedFileName == null) Color(0xFF00FF9D) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Destination: Download/AfterMotionZ/${tool.subfolder}",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                when (tool) {
                    AmzLabTool.TikTokAntiCompress -> {
                        ToolSwitchRow("iPhone 15 Pro Device Tag", antiCompressIphone) { antiCompressIphone = it }
                        ToolSwitchRow("Fast Start (MOOV before MDAT)", antiCompressFastStart) { antiCompressFastStart = it }
                        ToolSwitchRow("Clean Rec.709 Color Profile", antiCompressColor) { antiCompressColor = it }
                        ToolSwitchRow("Remove Tracking & Bitrate Tags", antiCompressCleanTags) { antiCompressCleanTags = it }
                    }
                    AmzLabTool.ConvertVideo -> {
                        ToolOptionSelector("Resolution", listOf("1080p", "720p", "480p"), convertResolution) { convertResolution = it }
                        ToolOptionSelector("Frame Rate", listOf("24 fps", "30 fps", "60 fps"), "${convertFps} fps") {
                            convertFps = it.substringBefore(' ').toInt()
                        }
                        ToolOptionSelector("Codec", listOf("H.264", "H.265"), convertCodec) { convertCodec = it }
                    }
                    AmzLabTool.Twixtor -> {
                        ToolOptionSelector("Speed", listOf("10%", "20%", "25%", "50%", "75%"), "$twixtorSpeed%") {
                            twixtorSpeed = it.removeSuffix("%").toInt()
                        }
                        ToolOptionSelector(
                            "Optical Flow Engine",
                            listOf("OpenCV DIS Flow", "RIFE 4.26 Large", "RIFE Lite", "Farneback Flow", "Bidirectional Morph"),
                            twixtorEngine
                        ) { twixtorEngine = it }
                        ToolOptionSelector("Motion Mode", listOf("Motion", "Blend", "Nearest"), twixtorMode) { twixtorMode = it }
                        ToolOptionSelector("Audio Pitch", listOf("Kept", "Down", "Mute"), twixtorAudio) { twixtorAudio = it }
                    }
                    AmzLabTool.MorphStudio -> {
                        ToolOptionSelector("Morph Mode", listOf("Shape Morph", "Image Morph"), morphMode) { morphMode = it }
                        ToolOptionSelector("Duration", listOf("500 ms", "1000 ms", "1500 ms", "2000 ms", "3000 ms"), "${morphDurationMs} ms") {
                            morphDurationMs = it.removeSuffix(" ms").toLong()
                        }
                        ToolOptionSelector("Easing", listOf("EaseInOut", "Linear", "FastOutSlowIn"), morphEasing) { morphEasing = it }
                        if (morphMode.contains("Image")) {
                            ToolOptionSelector("Mesh Grid", listOf("4x4", "8x8", "16x16"), "${morphMeshGrid}x$morphMeshGrid") {
                                morphMeshGrid = it.substringBefore('x').toInt()
                            }
                        }
                    }
                    AmzLabTool.AudioConverter -> {
                        ToolOptionSelector("Format", listOf("M4A", "WAV", "FLAC"), audioFormat) { audioFormat = it }
                        ToolOptionSelector("Bitrate", listOf("128k", "192k", "256k", "320k"), "${audioBitrate}k") {
                            audioBitrate = it.removeSuffix("k").toInt()
                        }
                    }
                    AmzLabTool.Stabilizer -> {
                        ToolOptionSelector("Mode", listOf("Smooth Motion", "No Motion"), stabilizerMode) { stabilizerMode = it }
                        ToolSwitchRow("Auto Crop Black Edges", stabilizerAutoCrop) { stabilizerAutoCrop = it }
                    }
                    AmzLabTool.DepthMap -> {
                        ToolOptionSelector("Colormap", listOf("Greyscale", "Inferno", "Turbo", "Viridis", "Plasma", "Spectral"), depthColormap) { depthColormap = it }
                    }
                    AmzLabTool.AiUpscale -> {
                        ToolOptionSelector("Upscale Factor", listOf("2×", "4×"), "${upscaleScale}×") {
                            upscaleScale = it.removeSuffix("×").toInt()
                        }
                    }
                    AmzLabTool.AiFrameInterpolation -> {
                        ToolOptionSelector("Interpolation Factor", listOf("2×", "3×", "4×"), "${interpMultiplier}×") {
                            interpMultiplier = it.removeSuffix("×").toInt()
                        }
                    }
                    AmzLabTool.AudioDenoise -> {
                        ToolOptionSelector("Denoise Strength", listOf("50%", "70%", "85%", "100%"), "$denoiseStrength%") {
                            denoiseStrength = it.removeSuffix("%").toInt()
                        }
                    }
                    else -> {}
                }

                if (isProcessing) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00FF9D),
                        trackColor = Color(0xFF2A344A)
                    )
                    Text(
                        text = statusMessage,
                        color = Color(0xFF00FF9D),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (result != null) {
                    Spacer(Modifier.height(14.dp))
                    val res = result!!
                    if (res.success && res.outputFile != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F2B1D))
                                .border(1.dp, Color(0xFF00FF9D), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = res.message,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Path: ${res.outputFile.absolutePath}",
                                    color = Color(0xFF8E9AA8),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF202637))
                                    .clickable {
                                        shareFile(context, res.outputFile)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Share", color = Color(0xFF00FF9D), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF132A20))
                                .border(1.dp, Color(0xFF00FF9D).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "✓ Output saved to Project Assets (AI Renders tab for Timeline)",
                                    color = Color(0xFF00FF9D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else if (!res.success) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF3B151E))
                                .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = res.error ?: res.message.ifBlank { "Operation failed" },
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Advanced AI & Tile Processing Settings Button
                if (tool.isAi || tool == AmzLabTool.Twixtor || tool == AmzLabTool.Stabilizer || tool == AmzLabTool.DeadFrameRemover) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1B2336))
                            .border(1.dp, Color(0xFF2C3955), RoundedCornerShape(10.dp))
                            .clickable { showAdvancedSettings = true }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚙️ Advanced AI Engine & Tile Slicing Matrix",
                                color = Color(0xFF00FF9D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF202637))
                            .clickable(enabled = !isProcessing) {
                                activeJob?.cancel()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFF8E9AA8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val canStart = selectedUri != null && !isProcessing
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canStart) Color(0xFF00FF9D) else Color(0xFF2A344A))
                            .clickable(enabled = canStart) {
                                checkAndStartProcessing()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isProcessing) "Processing..." else "Process & Save",
                            color = if (canStart) Color(0xFF0D1117) else Color(0xFF64748B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showAdvancedSettings) {
        AIAdvancedSettingsSheet(
            tool = tool,
            currentTileSettings = tileSettings,
            onTileSettingsChanged = { tileSettings = it },
            routingMode = routingMode,
            onRoutingModeChanged = { routingMode = it },
            onDismiss = { showAdvancedSettings = false }
        )
    }

    if (pendingDownloadModel != null) {
        AIModelDownloadDialog(
            modelId = pendingDownloadModel!!,
            onDismiss = { pendingDownloadModel = null },
            onModelReady = {
                pendingDownloadModel = null
                startProcessing()
            }
        )
    }
}

@Composable
private fun ToolSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFFCBD5E1), fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00FF9D),
                checkedTrackColor = Color(0xFF1E3A2F),
                uncheckedThumbColor = Color(0xFF64748B),
                uncheckedTrackColor = Color(0xFF202637)
            )
        )
    }
}

@Composable
private fun ToolOptionSelector(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = label, color = Color(0xFF8E9AA8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                val isSel = opt == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) Color(0xFF00FF9D) else Color(0xFF202637))
                        .clickable { onSelect(opt) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = opt,
                        color = if (isSel) Color(0xFF0D1117) else Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".mp4")) "video/mp4" else if (file.name.endsWith(".png")) "image/png" else "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share After Motion Z Media"))
    } catch (_: Exception) {}
}
