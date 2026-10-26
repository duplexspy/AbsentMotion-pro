package app.absentmotion.pro

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

class FontCheckService : Service() {
    companion object {
        const val MSG_CHECK_FONT = 100
        const val MSG_RESULT = 101

        const val KEY_FILE_PATH = "file_path"
        const val KEY_SUCCESS = "success"
        const val KEY_FAMILY = "family"
        const val KEY_STYLE = "style"
        const val KEY_VERSION = "version"
        const val KEY_COPYRIGHT = "copyright"
        const val KEY_LICENSE = "license"
        const val KEY_POSTSCRIPT = "postscript"
        const val KEY_SHA256 = "sha256"
        const val KEY_ERROR = "error"

        suspend fun validateFont(context: Context, file: File): ParsedFontMetadata = withContext(Dispatchers.IO) {
            val inspect = FontSecurity.inspectFontFile(file)
            if (!inspect.isValid) return@withContext inspect

            val remoteResult = withTimeoutOrNull(6000L) {
                runRemoteCheck(context.applicationContext, file)
            }

            if (remoteResult != null) {
                return@withContext remoteResult
            }

            validateInProcess(file, inspect)
        }

        private fun validateInProcess(file: File, inspect: ParsedFontMetadata): ParsedFontMetadata {
            return try {
                val typeface = Typeface.Builder(file).build() ?: return inspect.copy(
                    isValid = false,
                    errorReason = "Failed to create typeface from font",
                )
                val paint = Paint().apply { this.typeface = typeface; textSize = 24f }
                val metrics = paint.fontMetrics
                val height = metrics.descent - metrics.ascent
                if (height.isNaN() || height.isInfinite() || height <= 0f) {
                    return inspect.copy(isValid = false, errorReason = "Invalid font metrics height: $height")
                }
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawText("AbsentPro", 0f, 32f, paint)
                bitmap.recycle()
                inspect
            } catch (e: Throwable) {
                inspect.copy(isValid = false, errorReason = e.message ?: "Render validation failed")
            }
        }

        private suspend fun runRemoteCheck(appContext: Context, file: File): ParsedFontMetadata? =
            suspendCancellableCoroutine { continuation ->
                var isResumed = false
                fun safeResume(result: ParsedFontMetadata?) {
                    if (!isResumed) {
                        isResumed = true
                        continuation.resume(result)
                    }
                }

                val replyHandler = object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        if (msg.what == MSG_RESULT) {
                            val data = msg.data
                            val success = data.getBoolean(KEY_SUCCESS, false)
                            val family = data.getString(KEY_FAMILY).orEmpty()
                            val style = data.getString(KEY_STYLE, "Regular")
                            val version = data.getString(KEY_VERSION).orEmpty()
                            val copyright = data.getString(KEY_COPYRIGHT).orEmpty()
                            val license = data.getString(KEY_LICENSE).orEmpty()
                            val postscript = data.getString(KEY_POSTSCRIPT).orEmpty()
                            val sha256 = data.getString(KEY_SHA256).orEmpty()
                            val error = data.getString(KEY_ERROR)

                            safeResume(
                                ParsedFontMetadata(
                                    family = family,
                                    style = style,
                                    version = version,
                                    copyright = copyright,
                                    license = license,
                                    postScriptName = postscript,
                                    sha256 = sha256,
                                    isValid = success,
                                    errorReason = error,
                                )
                            )
                        }
                    }
                }

                val replyMessenger = Messenger(replyHandler)
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        if (service == null) {
                            safeResume(null)
                            return
                        }
                        val remoteMessenger = Messenger(service)
                        val msg = Message.obtain(null, MSG_CHECK_FONT).apply {
                            data = Bundle().apply { putString(KEY_FILE_PATH, file.absolutePath) }
                            replyTo = replyMessenger
                        }
                        try {
                            remoteMessenger.send(msg)
                        } catch (e: RemoteException) {
                            safeResume(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        safeResume(
                            ParsedFontMetadata(
                                family = file.nameWithoutExtension,
                                style = "Regular",
                                version = "",
                                copyright = "",
                                license = "",
                                postScriptName = file.nameWithoutExtension,
                                sha256 = "",
                                isValid = false,
                                errorReason = "Isolated font validation process terminated unexpectedly",
                            )
                        )
                    }
                }

                val intent = Intent(appContext, FontCheckService::class.java)
                val bound = try {
                    appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (e: Throwable) {
                    false
                }

                if (!bound) {
                    safeResume(null)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    runCatching { appContext.unbindService(connection) }
                }
            }
    }

    private class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == MSG_CHECK_FONT) {
                val path = msg.data.getString(KEY_FILE_PATH)
                val replyTo = msg.replyTo
                if (path.isNullOrBlank() || replyTo == null) return

                val file = File(path)
                val inspect = FontSecurity.inspectFontFile(file)
                val reply = Message.obtain(null, MSG_RESULT)

                if (!inspect.isValid) {
                    reply.data = Bundle().apply {
                        putBoolean(KEY_SUCCESS, false)
                        putString(KEY_ERROR, inspect.errorReason)
                        putString(KEY_SHA256, inspect.sha256)
                    }
                    runCatching { replyTo.send(reply) }
                    return
                }

                try {
                    val typeface = Typeface.Builder(file).build() ?: error("Typeface build returned null")
                    val paint = Paint().apply { this.typeface = typeface; textSize = 24f }
                    val metrics = paint.fontMetrics
                    val height = metrics.descent - metrics.ascent
                    require(!height.isNaN() && !height.isInfinite() && height > 0f) { "Corrupt font metrics" }

                    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawText("AbsentProTest", 0f, 32f, paint)
                    bitmap.recycle()

                    reply.data = Bundle().apply {
                        putBoolean(KEY_SUCCESS, true)
                        putString(KEY_FAMILY, inspect.family)
                        putString(KEY_STYLE, inspect.style)
                        putString(KEY_VERSION, inspect.version)
                        putString(KEY_COPYRIGHT, inspect.copyright)
                        putString(KEY_LICENSE, inspect.license)
                        putString(KEY_POSTSCRIPT, inspect.postScriptName)
                        putString(KEY_SHA256, inspect.sha256)
                    }
                } catch (t: Throwable) {
                    reply.data = Bundle().apply {
                        putBoolean(KEY_SUCCESS, false)
                        putString(KEY_ERROR, t.message ?: "Render test failure")
                        putString(KEY_SHA256, inspect.sha256)
                    }
                }
                runCatching { replyTo.send(reply) }
            }
        }
    }

    private val messenger = Messenger(IncomingHandler())

    override fun onBind(intent: Intent?): IBinder? = messenger.binder
}
