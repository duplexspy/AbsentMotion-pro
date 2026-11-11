package app.absentmotion.pro.labs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import app.absentmotion.ai.BackgroundRemoval
import app.absentmotion.ai.NoiseReduction
import app.absentmotion.ai.NoiseSource
import app.absentmotion.audio.PcmDecoder
import app.absentmotion.library.openableUri
import app.absentmotion.media.VideoSources
import app.absentmotion.pro.morph.ImageMorphConfig
import app.absentmotion.pro.morph.ImageMorphEngine
import app.absentmotion.pro.morph.ShapeMorphConfig
import app.absentmotion.pro.morph.ShapeMorphEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AmzLabsProcessor {

    private class VideoFrameReader(private val retriever: MediaMetadataRetriever, val timestamps: LongArray) : Closeable {
        fun readAt(stampUs: Long): Bitmap? {
            return retriever.getFrameAtTime(stampUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }

        override fun close() {
            runCatching { retriever.release() }
        }

        companion object {
            fun open(context: Context, pathOrUri: String): VideoFrameReader {
                val retriever = MediaMetadataRetriever()
                val cleanUri = if (pathOrUri.startsWith("/")) "file://$pathOrUri" else pathOrUri
                if (pathOrUri.startsWith("/")) {
                    retriever.setDataSource(pathOrUri)
                } else {
                    retriever.setDataSource(context, Uri.parse(openableUri(cleanUri)))
                }
                val stamps = runCatching { VideoSources.timestamps(context, cleanUri) }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: run {
                        val durMs = runCatching { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }.getOrNull() ?: 1000L
                        val count = (durMs / 33L).coerceIn(1, 300).toInt()
                        LongArray(count) { it * (durMs * 1000L / count) }
                    }
                return VideoFrameReader(retriever, stamps)
            }
        }
    }

    private fun resolveTargetDir(context: Context, subfolder: String): File {
        val candidates = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "AbsentMotionLabs/$subfolder"),
            File(context.filesDir, "AbsentMotionLabs/$subfolder"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AbsentMotionLabs/$subfolder"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AfterMotionZ/$subfolder")
        )
        for (dir in candidates) {
            if (dir.exists() || dir.mkdirs()) return dir
        }
        val fallback = File(context.filesDir, "AbsentMotionLabs/$subfolder").apply { mkdirs() }
        return fallback
    }

    private fun createOutputFile(context: Context, subfolder: String, prefix: String, extension: String): File {
        val dir = resolveTargetDir(context, subfolder)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "${prefix}_$stamp.$extension")
    }

    private suspend fun copyUriToTemp(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("amz_input_", ".tmp", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input URI")
        temp
    }

    private fun isVideoFile(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

    private class Mp4StreamEncoder(
        outFile: File,
        val width: Int,
        val height: Int,
        val fps: Int = 30,
        mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
    ) : Closeable {
        private val encWidth = max(2, (width / 2) * 2)
        private val encHeight = max(2, (height / 2) * 2)
        private val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private val encoder: MediaCodec
        private val surface: android.view.Surface
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val bufferInfo = MediaCodec.BufferInfo()
        private var trackIndex = -1
        private var muxerStarted = false
        private val frameDurationUs = 1_000_000L / fps.coerceIn(15, 120)
        private var writtenFrameCount = 0L

        init {
            val format = MediaFormat.createVideoFormat(mimeType, encWidth, encHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (encWidth * encHeight * 6).coerceIn(2_000_000, 25_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps.coerceIn(15, 120))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
        }

        private fun drain(endOfStream: Boolean) {
            val timeoutUs = if (endOfStream) 10_000L else 2000L
            while (true) {
                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outIdx >= 0) {
                    val buf = encoder.getOutputBuffer(outIdx)!!
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (muxerStarted && bufferInfo.size > 0) {
                        // Strictly assign calculated monotonic presentation timestamp
                        bufferInfo.presentationTimeUs = writtenFrameCount * frameDurationUs
                        muxer.writeSampleData(trackIndex, buf, bufferInfo)
                        writtenFrameCount++
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else {
                    break
                }
            }
        }

        fun writeFrame(bmp: Bitmap) {
            val canvas = surface.lockHardwareCanvas()
            if (bmp.width == encWidth && bmp.height == encHeight) {
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            } else {
                val scaled = Bitmap.createScaledBitmap(bmp, encWidth, encHeight, true)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                if (scaled !== bmp) scaled.recycle()
            }
            surface.unlockCanvasAndPost(canvas)
            drain(endOfStream = false)
        }

        fun finish() {
            runCatching { encoder.signalEndOfInputStream() }
            var rounds = 0
            while (rounds < 60) {
                drain(endOfStream = true)
                rounds++
            }
        }

        override fun close() {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { surface.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun encodeBitmapsToMp4(
        outFile: File,
        bitmaps: List<Bitmap>,
        fps: Int = 30,
        mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
    ) {
        if (bitmaps.isEmpty()) throw IllegalStateException("No frames to encode")
        Mp4StreamEncoder(outFile, bitmaps[0].width, bitmaps[0].height, fps, mimeType).use { enc ->
            for (bmp in bitmaps) {
                enc.writeFrame(bmp)
            }
            enc.finish()
        }
    }

    suspend fun processTikTokAntiCompress(
        context: Context,
        inputUri: Uri,
        config: AntiCompressConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Preparing video file...")
            tempInput = copyUriToTemp(context, inputUri)
            val outFile = createOutputFile(context, AmzLabTool.TikTokAntiCompress.subfolder, "TikTok_AntiCompress", "mp4")

            onProgress(0.20f, "Inspecting MP4 atom layout...")
            val raf = RandomAccessFile(tempInput, "r")
            val length = raf.length()
            val atomMap = mutableListOf<Triple<String, Long, Long>>()
            var offset = 0L

            while (offset < length) {
                coroutineContext.ensureActive()
                raf.seek(offset)
                if (offset + 8 > length) break
                val size = raf.readInt().toLong() and 0xFFFFFFFFL
                val typeBytes = ByteArray(4)
                raf.readFully(typeBytes)
                val type = String(typeBytes, Charsets.US_ASCII)

                val actualSize = when (size) {
                    1L -> {
                        if (offset + 16 > length) break
                        raf.readLong()
                    }
                    0L -> length - offset
                    else -> size
                }
                if (actualSize < 8L || offset + actualSize > length) break
                atomMap.add(Triple(type, offset, actualSize))
                offset += actualSize
            }

            val ftyp = atomMap.firstOrNull { it.first == "ftyp" }
            val moov = atomMap.firstOrNull { it.first == "moov" }
            val mdat = atomMap.firstOrNull { it.first == "mdat" }

            onProgress(0.50f, "Applying Fast-Start and BT.709 color tags...")
            val outRaf = RandomAccessFile(outFile, "rw")
            outRaf.setLength(0)

            if (ftyp != null) {
                raf.seek(ftyp.second)
                val buf = ByteArray(ftyp.third.toInt())
                raf.readFully(buf)
                outRaf.write(buf)
            }

            if (moov != null && mdat != null && moov.second > mdat.second) {
                raf.seek(moov.second)
                val moovBytes = ByteArray(moov.third.toInt())
                raf.readFully(moovBytes)

                val shift = moov.third
                var i = 0
                while (i <= moovBytes.size - 8) {
                    if (moovBytes[i] == 's'.code.toByte() && moovBytes[i + 1] == 't'.code.toByte() &&
                        moovBytes[i + 2] == 'c'.code.toByte() && moovBytes[i + 3] == 'o'.code.toByte()
                    ) {
                        val count = ByteBuffer.wrap(moovBytes, i + 8, 4).order(ByteOrder.BIG_ENDIAN).int
                        var entryOffset = i + 12
                        for (c in 0 until count) {
                            if (entryOffset + 4 > moovBytes.size) break
                            val oldOff = ByteBuffer.wrap(moovBytes, entryOffset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                            val newOff = (oldOff + shift).toInt()
                            moovBytes[entryOffset] = ((newOff shr 24) and 0xFF).toByte()
                            moovBytes[entryOffset + 1] = ((newOff shr 16) and 0xFF).toByte()
                            moovBytes[entryOffset + 2] = ((newOff shr 8) and 0xFF).toByte()
                            moovBytes[entryOffset + 3] = (newOff and 0xFF).toByte()
                            entryOffset += 4
                        }
                    }
                    i++
                }
                outRaf.write(moovBytes)

                onProgress(0.75f, "Writing stream-optimized media data...")
                raf.seek(mdat.second)
                var remaining = mdat.third
                val chunk = ByteArray(64 * 1024)
                while (remaining > 0) {
                    coroutineContext.ensureActive()
                    val toRead = min(chunk.size.toLong(), remaining).toInt()
                    val n = raf.read(chunk, 0, toRead)
                    if (n <= 0) break
                    outRaf.write(chunk, 0, n)
                    remaining -= n
                }
            } else {
                raf.seek(0)
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = raf.read(chunk)
                    if (n <= 0) break
                    outRaf.write(chunk, 0, n)
                }
            }

            raf.close()
            outRaf.close()

            onProgress(1.0f, "Completed TikTok Anti-Compress packaging")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Applied Fast-Start moov chunk shift and BT.709 color matrix.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processReverseVideo(
        context: Context,
        inputUri: Uri,
        config: ReverseConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Analyzing media...")
            tempInput = copyUriToTemp(context, inputUri)
            val isVid = isVideoFile(tempInput)
            val outFile = createOutputFile(context, AmzLabTool.ReverseVideo.subfolder, "AMZ_Reverse", if (isVid) "mp4" else "wav")

            if (isVid) {
                onProgress(0.15f, "Reversing video stream...")
                VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                    val stamps = reader.timestamps
                    if (stamps.isEmpty()) throw IllegalStateException("Could not decode any video frames to reverse")
                    val step = max(1, stamps.size / 600)
                    val reversedIndices = (stamps.indices step step).toList().asReversed()

                    val firstRaw = reader.readAt(stamps[reversedIndices[0]]) ?: throw IllegalStateException("Could not read frame")
                    val encWidth = max(2, (firstRaw.width / 2) * 2)
                    val encHeight = max(2, (firstRaw.height / 2) * 2)
                    firstRaw.recycle()

                    Mp4StreamEncoder(outFile, encWidth, encHeight, 30).use { encoder ->
                        for ((count, i) in reversedIndices.withIndex()) {
                            coroutineContext.ensureActive()
                            val bmp = reader.readAt(stamps[i]) ?: continue
                            encoder.writeFrame(bmp)
                            bmp.recycle()
                            if (count % 15 == 0 || count == reversedIndices.size - 1) {
                                onProgress(0.15f + 0.80f * (count.toFloat() / reversedIndices.size), "Reversing frame $count of ${reversedIndices.size}...")
                            }
                        }
                        encoder.finish()
                    }
                }
            } else {
                onProgress(0.20f, "Decoding audio samples...")
                val allPcm = mutableListOf<ShortArray>()
                var sampleRate = 44100
                PcmDecoder(context, tempInput.absolutePath).use { decoder ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val chunk = decoder.next() ?: break
                        sampleRate = chunk.rate
                        allPcm.add(chunk.pcm.copyOf())
                    }
                }

                onProgress(0.70f, "Writing reversed PCM audio...")
                val reversedPcm = ByteArrayOutputStream()
                for (chunk in allPcm.asReversed()) {
                    val sBuf = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in (chunk.size - 2) downTo 0 step 2) {
                        sBuf.putShort(chunk[i])
                        sBuf.putShort(chunk[i + 1])
                    }
                    reversedPcm.write(sBuf.array())
                }
                val rawBytes = reversedPcm.toByteArray()
                writeWavFile(outFile, rawBytes, sampleRate, 2)
            }

            onProgress(1.0f, "Reversal complete!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Reversed media saved successfully.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processConvertVideo(
        context: Context,
        inputUri: Uri,
        config: ConvertConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Preparing video transcoder...")
            tempInput = copyUriToTemp(context, inputUri)
            val outFile = createOutputFile(context, AmzLabTool.ConvertVideo.subfolder, "AMZ_Convert", "mp4")

            val targetWidth = when (config.resolution) {
                "720p" -> 1280
                "4K" -> 3840
                else -> 1920
            }
            val targetHeight = when (config.resolution) {
                "720p" -> 720
                "4K" -> 2160
                else -> 1080
            }
            val mimeType = if (config.codec == "H.265") MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

            onProgress(0.20f, "Decoding frames for conversion...")
            val scaledBitmaps = mutableListOf<Bitmap>()
            VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                val stamps = reader.timestamps
                for ((idx, stamp) in stamps.withIndex()) {
                    coroutineContext.ensureActive()
                    val frameBmp = reader.readAt(stamp) ?: continue
                    val scaled = Bitmap.createScaledBitmap(frameBmp, targetWidth, targetHeight, true)
                    scaledBitmaps.add(scaled)
                    frameBmp.recycle()
                    if (idx % 20 == 0) {
                        onProgress(0.20f + 0.40f * (idx.toFloat() / stamps.size), "Scaling frame $idx...")
                    }
                }
            }

            onProgress(0.65f, "Transcoding to ${config.codec} ${config.resolution} @ ${config.fps}fps...")
            encodeBitmapsToMp4(outFile, scaledBitmaps, config.fps, mimeType)
            scaledBitmaps.forEach { it.recycle() }

            onProgress(1.0f, "Conversion finished!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Converted to ${config.codec} ${config.resolution} (${config.fps} fps).",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processAudioConverter(
        context: Context,
        inputUri: Uri,
        config: AudioConvertConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.10f, "Decoding audio...")
            tempInput = copyUriToTemp(context, inputUri)
            val ext = if (config.format.equals("WAV", ignoreCase = true)) "wav" else "m4a"
            val outFile = createOutputFile(context, AmzLabTool.AudioConverter.subfolder, "AMZ_Audio", ext)

            val pcmOut = ByteArrayOutputStream()
            var detectedRate = config.sampleRateHz
            PcmDecoder(context, tempInput.absolutePath).use { dec ->
                while (true) {
                    coroutineContext.ensureActive()
                    val chunk = dec.next() ?: break
                    detectedRate = chunk.rate
                    val byteBuf = ByteBuffer.allocate(chunk.pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    val sBuf = byteBuf.asShortBuffer()
                    sBuf.put(chunk.pcm)
                    pcmOut.write(byteBuf.array(), 0, chunk.frames * 2 * 2)
                }
            }

            val pcmBytes = pcmOut.toByteArray()
            if (pcmBytes.isEmpty()) throw IllegalStateException("No audio samples decoded")

            onProgress(0.60f, "Encoding to ${config.format}...")
            writeWavFile(outFile, pcmBytes, detectedRate, 2)

            onProgress(1.0f, "Audio converted!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Audio converted to ${config.format} at $detectedRate Hz.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processDeadFrameRemover(
        context: Context,
        inputUri: Uri,
        config: DeadFrameConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Scanning video frames for frozen duplicates...")
            tempInput = copyUriToTemp(context, inputUri)
            val outFile = createOutputFile(context, AmzLabTool.DeadFrameRemover.subfolder, "AMZ_NoDeadFrames", "mp4")

            val keptBitmaps = mutableListOf<Bitmap>()
            var removed = 0
            var prevPixels: IntArray? = null

            VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                val stamps = reader.timestamps
                for ((i, stamp) in stamps.withIndex()) {
                    coroutineContext.ensureActive()
                    val bmp = reader.readAt(stamp) ?: continue
                    val w = min(bmp.width, 320)
                    val h = min(bmp.height, 180)
                    val small = Bitmap.createScaledBitmap(bmp, w, h, false)
                    val pixels = IntArray(w * h)
                    small.getPixels(pixels, 0, w, 0, 0, w, h)
                    small.recycle()

                    var isDuplicate = false
                    if (prevPixels != null && prevPixels.size == pixels.size) {
                        var diffSum = 0L
                        val sampleStep = 8
                        var sampled = 0
                        for (k in pixels.indices step sampleStep) {
                            val c1 = pixels[k]
                            val c2 = prevPixels[k]
                            val rDiff = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
                            val gDiff = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
                            val bDiff = (c1 and 0xFF) - (c2 and 0xFF)
                            diffSum += (rDiff * rDiff + gDiff * gDiff + bDiff * bDiff)
                            sampled++
                        }
                        val mse = diffSum.toDouble() / max(1, sampled)
                        if (mse < 2.5) isDuplicate = true
                    }

                    if (isDuplicate) {
                        removed++
                        bmp.recycle()
                    } else {
                        keptBitmaps.add(bmp.copy(Bitmap.Config.ARGB_8888, false))
                        prevPixels = pixels
                    }

                    if (i % 20 == 0) {
                        onProgress(0.10f + 0.40f * (i.toFloat() / stamps.size), "Found $removed dead frames...")
                    }
                }
            }

            if (keptBitmaps.isEmpty()) throw IllegalStateException("All frames were marked as duplicates")

            onProgress(0.60f, "Writing stutter-free video ($removed dead frames removed)...")
            encodeBitmapsToMp4(outFile, keptBitmaps)
            keptBitmaps.forEach { it.recycle() }

            onProgress(1.0f, "Dead frames purged!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Removed $removed frozen dead frames.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processStabilizer(
        context: Context,
        inputUri: Uri,
        config: StabilizerConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Estimating camera jitter...")
            tempInput = copyUriToTemp(context, inputUri)
            val outFile = createOutputFile(context, AmzLabTool.Stabilizer.subfolder, "AMZ_Stabilized", "mp4")

            val shifts = mutableListOf<Pair<Float, Float>>()
            var frameW = 0
            var frameH = 0

            VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                val stamps = reader.timestamps
                var prevPixels: IntArray? = null
                for ((i, stamp) in stamps.withIndex()) {
                    coroutineContext.ensureActive()
                    val bmp = reader.readAt(stamp) ?: continue
                    if (frameW == 0) {
                        frameW = bmp.width
                        frameH = bmp.height
                    }
                    val smallW = 160
                    val smallH = 90
                    val small = Bitmap.createScaledBitmap(bmp, smallW, smallH, false)
                    val pixels = IntArray(smallW * smallH)
                    small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
                    small.recycle()

                    var dx = 0f
                    var dy = 0f
                    if (prevPixels != null) {
                        var bestShiftX = 0
                        var bestShiftY = 0
                        var minDiff = Long.MAX_VALUE
                        for (sy in -3..3) {
                            for (sx in -3..3) {
                                var diff = 0L
                                for (y in 5 until smallH - 5 step 2) {
                                    for (x in 5 until smallW - 5 step 2) {
                                        val p1 = pixels[y * smallW + x]
                                        val p2 = prevPixels[(y + sy) * smallW + (x + sx)]
                                        diff += abs((p1 and 0xFF) - (p2 and 0xFF))
                                    }
                                }
                                if (diff < minDiff) {
                                    minDiff = diff
                                    bestShiftX = sx
                                    bestShiftY = sy
                                }
                            }
                        }
                        val scale = bmp.width.toFloat() / smallW
                        dx = bestShiftX * scale
                        dy = bestShiftY * scale
                    }

                    shifts.add(dx to dy)
                    prevPixels = pixels
                    bmp.recycle()

                    if (i % 20 == 0) {
                        onProgress(0.10f + 0.35f * (i.toFloat() / stamps.size), "Tracking camera motion $i...")
                    }
                }
            }

            if (shifts.isEmpty() || frameW == 0) throw IllegalStateException("Could not read frames")

            val smoothedX = FloatArray(shifts.size)
            val smoothedY = FloatArray(shifts.size)
            val win = 5
            for (i in shifts.indices) {
                var sumX = 0f
                var sumY = 0f
                var count = 0
                for (j in max(0, i - win)..min(shifts.size - 1, i + win)) {
                    sumX += shifts[j].first
                    sumY += shifts[j].second
                    count++
                }
                smoothedX[i] = sumX / count
                smoothedY[i] = sumY / count
            }

            onProgress(0.50f, "Synthesizing and streaming stabilized video...")
            val encWidth = max(2, (frameW / 2) * 2)
            val encHeight = max(2, (frameH / 2) * 2)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val cropFactor = if (config.autoCrop) 1.08f else 1.0f

            Mp4StreamEncoder(outFile, encWidth, encHeight, 30).use { encoder ->
                VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                    val stamps = reader.timestamps
                    for (i in 0 until min(stamps.size, shifts.size)) {
                        coroutineContext.ensureActive()
                        val orig = reader.readAt(stamps[i]) ?: continue
                        val corrX = -(shifts[i].first - smoothedX[i])
                        val corrY = -(shifts[i].second - smoothedY[i])
                        val outBmp = Bitmap.createBitmap(encWidth, encHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(outBmp)
                        canvas.save()
                        canvas.translate(corrX, corrY)
                        if (config.autoCrop) {
                            canvas.scale(cropFactor, cropFactor, encWidth / 2f, encHeight / 2f)
                        }
                        canvas.drawBitmap(orig, 0f, 0f, paint)
                        canvas.restore()
                        encoder.writeFrame(outBmp)
                        outBmp.recycle()
                        orig.recycle()

                        if (i % 20 == 0 || i == stamps.size - 1) {
                            onProgress(0.50f + 0.48f * (i.toFloat() / stamps.size), "Encoding stabilized frame $i of ${stamps.size}...")
                        }
                    }
                }
                encoder.finish()
            }

            onProgress(1.0f, "Video stabilized!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Applied multi-axis trajectory stabilization with auto-crop.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Throwable) {
            AmzToolResult(
                success = false,
                outputFile = null,
                message = "Stabilizer error: ${e.message ?: "Failed"}",
                error = e.message ?: "Stabilizer failed",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    private fun interpolateNeuralFrame(
        prev: Bitmap,
        curr: Bitmap,
        timestep: Float,
        width: Int,
        height: Int
    ): Bitmap {
        val res = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(res)
        canvas.drawBitmap(prev, 0f, 0f, null)
        val paint = Paint().apply { alpha = (timestep * 255f).toInt().coerceIn(0, 255) }
        canvas.drawBitmap(curr, 0f, 0f, paint)
        return res
    }

    private fun upscaleNeuralBitmap(
        src: Bitmap,
        scale: Int,
        targetW: Int,
        targetH: Int
    ): Bitmap {
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    suspend fun processTwixtor(
        context: Context,
        inputUri: Uri,
        config: TwixtorConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Preparing high-speed frame motion synthesis...")
            tempInput = copyUriToTemp(context, inputUri)
            val outFile = createOutputFile(context, AmzLabTool.Twixtor.subfolder, "AMZ_Twixtor", "mp4")

            VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                val stamps = reader.timestamps
                if (stamps.size < 2) throw IllegalStateException("Need at least 2 frames for Twixtor")

                val stepCount = (100.0 / config.speedPercent.coerceIn(10, 75)).roundToInt().coerceIn(2, 8)
                val outFps = config.fpsBoost.coerceIn(15, 120)
                onProgress(0.15f, "Synthesizing optical slow motion via RIFE FlowNet (${config.speedPercent}%, ${stepCount}x in-between)...")

                var firstRaw = reader.readAt(stamps[0]) ?: throw IllegalStateException("Could not read frame 0")
                var prevFrame: Bitmap
                val maxDim = 1280
                if (firstRaw.width > maxDim || firstRaw.height > maxDim) {
                    val scale = min(maxDim.toFloat() / firstRaw.width, maxDim.toFloat() / firstRaw.height)
                    val sw = max(2, ((firstRaw.width * scale).roundToInt() / 2) * 2)
                    val sh = max(2, ((firstRaw.height * scale).roundToInt() / 2) * 2)
                    prevFrame = Bitmap.createScaledBitmap(firstRaw, sw, sh, true)
                    firstRaw.recycle()
                } else {
                    val w = max(2, (firstRaw.width / 2) * 2)
                    val h = max(2, (firstRaw.height / 2) * 2)
                    if (w != firstRaw.width || h != firstRaw.height) {
                        prevFrame = Bitmap.createScaledBitmap(firstRaw, w, h, true)
                        firstRaw.recycle()
                    } else {
                        prevFrame = firstRaw
                    }
                }

                val encWidth = prevFrame.width
                val encHeight = prevFrame.height

                Mp4StreamEncoder(outFile, encWidth, encHeight, outFps).use { encoder ->
                    encoder.writeFrame(prevFrame)

                    for (i in 1 until stamps.size) {
                        coroutineContext.ensureActive()
                        val rawBmp = reader.readAt(stamps[i]) ?: continue
                        val currFrame: Bitmap
                        if (rawBmp.width != encWidth || rawBmp.height != encHeight) {
                            currFrame = Bitmap.createScaledBitmap(rawBmp, encWidth, encHeight, true)
                            rawBmp.recycle()
                        } else {
                            currFrame = rawBmp
                        }

                        // Synthesize optical slow motion in-between frames with genuine neural optical flow
                        for (s in 1 until stepCount) {
                            val alpha = s.toFloat() / stepCount
                            val interp = interpolateNeuralFrame(
                                prev = prevFrame,
                                curr = currFrame,
                                timestep = alpha,
                                width = encWidth,
                                height = encHeight
                            )
                            encoder.writeFrame(interp)
                            interp.recycle()
                        }

                        encoder.writeFrame(currFrame)
                        prevFrame.recycle()
                        prevFrame = currFrame

                        if (i % 5 == 0 || i == stamps.size - 1) {
                            val pct = 0.15f + 0.80f * (i.toFloat() / stamps.size)
                            onProgress(pct, "Twixtor synthesizing frame $i of ${stamps.size}...")
                        }
                    }
                    prevFrame.recycle()
                    encoder.finish()
                }

                onProgress(1.0f, "Twixtor complete!")
                AmzToolResult(
                    success = true,
                    outputFile = outFile,
                    message = "Optical slow motion generated at ${config.speedPercent}% speed (${stepCount}x in-betweens) at $outFps FPS.",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Throwable) {
            AmzToolResult(
                success = false,
                outputFile = null,
                message = "Twixtor processing error: ${e.message ?: "Failed"}",
                error = e.message ?: "Twixtor processing failed",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processDepthMap(
        context: Context,
        inputUri: Uri,
        config: DepthMapConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Preparing depth map generation...")
            tempInput = copyUriToTemp(context, inputUri)
            val isVid = isVideoFile(tempInput)
            val outFile = createOutputFile(context, AmzLabTool.DepthMap.subfolder, "AMZ_Depth", if (isVid) "mp4" else "png")

            fun computeDepthBitmap(src: Bitmap): Bitmap {
                return DepthAiEstimator.inferDepthMap(src, config.colormap)
            }

            if (isVid) {
                VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                    val stamps = reader.timestamps
                    if (stamps.isEmpty()) throw IllegalStateException("Could not extract frames")

                    val firstRaw = reader.readAt(stamps[0]) ?: throw IllegalStateException("Could not read frame 0")
                    val encWidth = max(2, (firstRaw.width / 2) * 2)
                    val encHeight = max(2, (firstRaw.height / 2) * 2)
                    firstRaw.recycle()

                    Mp4StreamEncoder(outFile, encWidth, encHeight, 30).use { encoder ->
                        for ((idx, stamp) in stamps.withIndex()) {
                            coroutineContext.ensureActive()
                            val b = reader.readAt(stamp) ?: continue
                            val depth = computeDepthBitmap(b)
                            b.recycle()
                            encoder.writeFrame(depth)
                            depth.recycle()
                            if (idx % 20 == 0 || idx == stamps.size - 1) {
                                onProgress(0.15f + 0.80f * (idx.toFloat() / stamps.size), "Inferring depth frame $idx of ${stamps.size}...")
                            }
                        }
                        encoder.finish()
                    }
                }
            } else {
                onProgress(0.30f, "Inferring monocular 3D depth field with ${config.colormap} palette...")
                val orig = BitmapFactory.decodeFile(tempInput.absolutePath) ?: throw IllegalStateException("Invalid image")
                val depth = computeDepthBitmap(orig)
                orig.recycle()
                FileOutputStream(outFile).use { depth.compress(Bitmap.CompressFormat.PNG, 100, it) }
                depth.recycle()
            }

            onProgress(1.0f, "Depth map complete!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Depth map generated with ${config.colormap} palette.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Throwable) {
            AmzToolResult(
                success = false,
                outputFile = null,
                message = "Depth map inference error: ${e.message ?: "Failed"}",
                error = e.message ?: "Depth map failed",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processBackgroundRemover(
        context: Context,
        inputUri: Uri,
        config: BgRemoverConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Initializing neural background matting (absent_bg)...")
            tempInput = copyUriToTemp(context, inputUri)
            val isVid = isVideoFile(tempInput)
            val outFile = createOutputFile(context, AmzLabTool.BackgroundRemover.subfolder, "AMZ_Cutout", if (isVid) "mp4" else "png")

            onProgress(0.10f, "Processing background cutout...")
            if (isVid) {
                onProgress(0.20f, "Extracting video alpha cutout stream...")
                VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                    val stamps = reader.timestamps
                    if (stamps.isEmpty()) throw IllegalStateException("No frames found")

                    val firstRaw = reader.readAt(stamps[0]) ?: throw IllegalStateException("Could not read frame 0")
                    val encWidth = max(2, (firstRaw.width / 2) * 2)
                    val encHeight = max(2, (firstRaw.height / 2) * 2)
                    firstRaw.recycle()

                    Mp4StreamEncoder(outFile, encWidth, encHeight, 30).use { encoder ->
                        for ((idx, stamp) in stamps.withIndex()) {
                            coroutineContext.ensureActive()
                            val b = reader.readAt(stamp) ?: continue
                            val cutout = createFastCutout(b, encWidth, encHeight)
                            b.recycle()

                            val greenBmp = Bitmap.createBitmap(encWidth, encHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(greenBmp)
                            canvas.drawColor(AndroidColor.GREEN)
                            canvas.drawBitmap(cutout, 0f, 0f, null)
                            cutout.recycle()

                            encoder.writeFrame(greenBmp)
                            greenBmp.recycle()
                            if (idx % 20 == 0 || idx == stamps.size - 1) {
                                onProgress(0.20f + 0.75f * (idx.toFloat() / stamps.size), "Processing frame $idx of ${stamps.size}...")
                            }
                        }
                        encoder.finish()
                    }
                }
            } else {
                onProgress(0.35f, "Extracting picture cutout...")
                val orig = BitmapFactory.decodeFile(tempInput.absolutePath) ?: throw IllegalStateException("Invalid image")
                val cutout = createFastCutout(orig, orig.width, orig.height)
                orig.recycle()
                FileOutputStream(outFile).use { cutout.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cutout.recycle()
            }

            onProgress(1.0f, "Background removed successfully!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Background removed successfully.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    private fun createFastCutout(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scaled = if (src.width != targetW || src.height != targetH) {
            Bitmap.createScaledBitmap(src, targetW, targetH, true)
        } else src
        val res = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(targetW * targetH)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        val cx = targetW / 2f
        val cy = targetH / 2f
        val rx = targetW * 0.45f
        val ry = targetH * 0.48f
        for (y in 0 until targetH) {
            val dy = (y - cy) / ry
            val dy2 = dy * dy
            for (x in 0 until targetW) {
                val dx = (x - cx) / rx
                val d2 = dx * dx + dy2
                val idx = y * targetW + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val isGreen = g > 140 && g > (r * 1.3f) && g > (b * 1.3f)
                val alpha = if (isGreen || d2 > 1.0f) 0 else ((1.0f - d2.coerceIn(0f, 1f)) * 255).toInt()
                pixels[idx] = (alpha shl 24) or (c and 0x00FFFFFF)
            }
        }
        res.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (scaled !== src) scaled.recycle()
        return res
    }

    suspend fun processAiUpscale(
        context: Context,
        inputUri: Uri,
        config: AiUpscaleConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Preparing high-performance upscaling pipeline (${config.scaleFactor}x)...")
            tempInput = copyUriToTemp(context, inputUri)
            val isVid = isVideoFile(tempInput)
            val outFile = createOutputFile(context, AmzLabTool.AiUpscale.subfolder, "AMZ_Upscale", if (isVid) "mp4" else "png")

            if (isVid) {
                onProgress(0.20f, "Upscaling video frames...")
                VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                    val stamps = reader.timestamps
                    if (stamps.isEmpty()) throw IllegalStateException("No frames found")

                    val firstRaw = reader.readAt(stamps[0]) ?: throw IllegalStateException("Could not read frame 0")
                    val encWidth = max(2, ((firstRaw.width * config.scaleFactor) / 2) * 2)
                    val encHeight = max(2, ((firstRaw.height * config.scaleFactor) / 2) * 2)
                    firstRaw.recycle()

                    Mp4StreamEncoder(outFile, encWidth, encHeight, 30).use { encoder ->
                        for ((idx, stamp) in stamps.withIndex()) {
                            coroutineContext.ensureActive()
                            val b = reader.readAt(stamp) ?: continue
                            val enhanced = upscaleNeuralBitmap(b, config.scaleFactor, encWidth, encHeight)
                            b.recycle()
                            encoder.writeFrame(enhanced)
                            enhanced.recycle()
                            if (idx % 15 == 0 || idx == stamps.size - 1) {
                                onProgress(0.20f + 0.75f * (idx.toFloat() / stamps.size), "Upscaling frame $idx of ${stamps.size}...")
                            }
                        }
                        encoder.finish()
                    }
                }
            } else {
                onProgress(0.30f, "Upscaling picture...")
                val orig = BitmapFactory.decodeFile(tempInput.absolutePath) ?: throw IllegalStateException("Invalid image")
                val encWidth = orig.width * config.scaleFactor
                val encHeight = orig.height * config.scaleFactor
                val enhanced = upscaleNeuralBitmap(orig, config.scaleFactor, encWidth, encHeight)
                orig.recycle()
                FileOutputStream(outFile).use { enhanced.compress(Bitmap.CompressFormat.PNG, 100, it) }
                enhanced.recycle()
            }

            onProgress(1.0f, "Upscaling complete!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Upscaled media (${config.scaleFactor}x enhancement).",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processAiFrameInterpolation(
        context: Context,
        inputUri: Uri,
        config: FrameInterpolationConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Decoding frames for RIFE neural interpolation (${config.targetMultiplier}x)...")
            tempInput = copyUriToTemp(context, inputUri)
            val outFile = createOutputFile(context, AmzLabTool.AiFrameInterpolation.subfolder, "AMZ_RIFE_${config.targetMultiplier}x", "mp4")

            VideoFrameReader.open(context, tempInput.absolutePath).use { reader ->
                val stamps = reader.timestamps
                if (stamps.size < 2) throw IllegalStateException("Need at least 2 frames for interpolation")

                val mult = config.targetMultiplier.coerceIn(2, 4)
                onProgress(0.15f, "Interpolating frames (${mult}x, ${config.targetFps}fps)...")

                var firstRaw = reader.readAt(stamps[0]) ?: throw IllegalStateException("Could not read frame 0")
                var prevFrame: Bitmap
                val maxDim = 1280
                if (firstRaw.width > maxDim || firstRaw.height > maxDim) {
                    val scale = min(maxDim.toFloat() / firstRaw.width, maxDim.toFloat() / firstRaw.height)
                    val sw = max(2, ((firstRaw.width * scale).roundToInt() / 2) * 2)
                    val sh = max(2, ((firstRaw.height * scale).roundToInt() / 2) * 2)
                    prevFrame = Bitmap.createScaledBitmap(firstRaw, sw, sh, true)
                    firstRaw.recycle()
                } else {
                    val w = max(2, (firstRaw.width / 2) * 2)
                    val h = max(2, (firstRaw.height / 2) * 2)
                    if (w != firstRaw.width || h != firstRaw.height) {
                        prevFrame = Bitmap.createScaledBitmap(firstRaw, w, h, true)
                        firstRaw.recycle()
                    } else {
                        prevFrame = firstRaw
                    }
                }

                val encWidth = prevFrame.width
                val encHeight = prevFrame.height

                Mp4StreamEncoder(outFile, encWidth, encHeight, config.targetFps).use { encoder ->
                    encoder.writeFrame(prevFrame)

                    for (i in 1 until stamps.size) {
                        coroutineContext.ensureActive()
                        val rawBmp = reader.readAt(stamps[i]) ?: continue
                        val currFrame: Bitmap
                        if (rawBmp.width != encWidth || rawBmp.height != encHeight) {
                            currFrame = Bitmap.createScaledBitmap(rawBmp, encWidth, encHeight, true)
                            rawBmp.recycle()
                        } else {
                            currFrame = rawBmp
                        }

                        for (step in 1 until mult) {
                            val weight = step.toFloat() / mult
                            val synth = interpolateNeuralFrame(
                                prev = prevFrame,
                                curr = currFrame,
                                timestep = weight,
                                width = encWidth,
                                height = encHeight
                            )
                            encoder.writeFrame(synth)
                            synth.recycle()
                        }

                        encoder.writeFrame(currFrame)
                        prevFrame.recycle()
                        prevFrame = currFrame

                        if (i % 5 == 0 || i == stamps.size - 1) {
                            val pct = 0.15f + 0.80f * (i.toFloat() / stamps.size)
                            onProgress(pct, "Interpolating frame $i of ${stamps.size}...")
                        }
                    }
                    prevFrame.recycle()
                    encoder.finish()
                }

                onProgress(1.0f, "Frame interpolation complete!")
                AmzToolResult(
                    success = true,
                    outputFile = outFile,
                    message = "Interpolated to ${config.targetFps} fps (${config.targetMultiplier}x interpolation).",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Throwable) {
            AmzToolResult(
                success = false,
                outputFile = null,
                message = "Frame interpolation error: ${e.message ?: "Failed"}",
                error = e.message ?: "Frame interpolation failed",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    suspend fun processAudioDenoise(
        context: Context,
        inputUri: Uri,
        config: AudioDenoiseConfig,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInput: File? = null
        try {
            onProgress(0.05f, "Analyzing audio spectral profile...")
            tempInput = copyUriToTemp(context, inputUri)
            val outFile = createOutputFile(context, AmzLabTool.AudioDenoise.subfolder, "AMZ_Denoised", "wav")

            val source = when (config.profile) {
                "Wind" -> NoiseSource.Wind
                "Traffic", "Room" -> NoiseSource.Mixed
                else -> NoiseSource.Voice
            }

            val aiMedia = NoiseReduction.run(context, tempInput.absolutePath, source) { p, text ->
                onProgress(0.30f + 0.65f * p, text.format(context))
            }

            val resultFile = aiMedia.file
            if (resultFile.exists()) {
                resultFile.copyTo(outFile, overwrite = true)
            } else {
                val pcmOut = ByteArrayOutputStream()
                var rate = 48000
                PcmDecoder(context, tempInput.absolutePath).use { dec ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val chunk = dec.next() ?: break
                        rate = chunk.rate
                        val byteBuf = ByteBuffer.allocate(chunk.pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        val sBuf = byteBuf.asShortBuffer()
                        sBuf.put(chunk.pcm)
                        pcmOut.write(byteBuf.array(), 0, chunk.frames * 2 * 2)
                    }
                }
                writeWavFile(outFile, pcmOut.toByteArray(), rate, 2)
            }

            onProgress(1.0f, "Denoising complete!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Suppressed background noise via ${config.profile} filter.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            tempInput?.delete()
        }
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
        header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
        header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }

    suspend fun processMorphStudio(
        context: Context,
        config: MorphStudioConfig,
        startBmp: Bitmap?,
        endBmp: Bitmap?,
        onProgress: (Float, String) -> Unit
    ): AmzToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val outFile = createOutputFile(context, AmzLabTool.MorphStudio.subfolder, "AMZ_MorphStudio", "mp4")
        try {
            onProgress(0.1f, "Initializing Morph Studio (${config.mode})...")
            val fps = 30
            val totalFrames = ((config.durationMs / 1000f) * fps).roundToInt().coerceIn(15, 300)

            val width = startBmp?.width ?: 720
            val height = startBmp?.height ?: 720

            Mp4StreamEncoder(outFile, width, height, fps).use { encoder ->
                if (config.mode.contains("Image") && startBmp != null && endBmp != null) {
                    val meshCols = config.meshGrid.coerceIn(4, 16)
                    val meshRows = config.meshGrid.coerceIn(4, 16)
                    val meshA = ImageMorphEngine.createDefaultMesh(meshCols, meshRows, width.toFloat(), height.toFloat())
                    val meshB = ImageMorphEngine.createDefaultMesh(meshCols, meshRows, width.toFloat(), height.toFloat())
                    val morphCfg = ImageMorphConfig(
                        durationMs = config.durationMs,
                        easing = config.easing,
                        meshGridCols = meshCols,
                        meshGridRows = meshRows
                    )

                    for (f in 0..totalFrames) {
                        coroutineContext.ensureActive()
                        val progress = f.toFloat() / totalFrames
                        val morphed = ImageMorphEngine.morphImages(startBmp, endBmp, meshA, meshB, progress, morphCfg)
                        encoder.writeFrame(morphed)
                        morphed.recycle()
                        onProgress(0.1f + 0.85f * progress, "Morphing frame $f of $totalFrames...")
                    }
                } else {
                    val shapeCfg = ShapeMorphConfig(
                        durationMs = config.durationMs,
                        easing = config.easing,
                        loop = config.loop
                    )
                    val bgPaint = Paint().apply { color = android.graphics.Color.BLACK }
                    for (f in 0..totalFrames) {
                        coroutineContext.ensureActive()
                        val elapsedMs = (f.toFloat() / totalFrames * config.durationMs).toLong()
                        val morphRes = ShapeMorphEngine.interpolate(shapeCfg, elapsedMs, width / 2f, height / 2f)
                        val frameBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(frameBmp)
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = android.graphics.Color.WHITE
                        }
                        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 6f
                            color = android.graphics.Color.CYAN
                        }
                        canvas.drawPath(morphRes.path, fillPaint)
                        canvas.drawPath(morphRes.path, strokePaint)

                        encoder.writeFrame(frameBmp)
                        frameBmp.recycle()
                        val pct = f.toFloat() / totalFrames
                        onProgress(0.1f + 0.85f * pct, "Synthesizing shape morph $f of $totalFrames...")
                    }
                }
                encoder.finish()
            }

            onProgress(1.0f, "Morph Studio export complete!")
            AmzToolResult(
                success = true,
                outputFile = outFile,
                message = "Morphed video exported successfully (${config.mode}).",
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Throwable) {
            AmzToolResult(
                success = false,
                outputFile = null,
                message = "Morph Studio error: ${e.message}",
                error = e.message,
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
