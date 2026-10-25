package app.absentmotion.effectspack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PackThumbnailGenerator {
    private const val THUMB_SIZE = 256

    suspend fun generateMissingThumbnails(context: Context, pack: InstalledPack) = withContext(Dispatchers.IO) {
        val thumbsDir = File(pack.dir, "thumbs").apply { mkdirs() }
        var changed = false

        for (effect in pack.effects) {
            val baseName = effect.id.removePrefix("gm.custom.")
            val thumbFile = File(thumbsDir, "$baseName.webp")
            if (thumbFile.isFile && thumbFile.length() > 0) continue

            val generated = runCatching {
                createPlaceholderThumbnail(effect.name, effect.group)
            }.getOrNull() ?: continue

            runCatching {
                FileOutputStream(thumbFile).use { out ->
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        generated.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                    } else {
                        @Suppress("DEPRECATION")
                        generated.compress(Bitmap.CompressFormat.WEBP, 85, out)
                    }
                }
                generated.recycle()
                changed = true
            }
        }

        if (changed) {
            PackCatalog.refresh(context)
        }
    }

    fun createPlaceholderThumbnail(name: String, group: String): Bitmap {
        val bmp = Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val hash = (name.hashCode() xor group.hashCode()).let { if (it < 0) -it else it }
        val hue1 = (hash % 360).toFloat()
        val hue2 = ((hash + 60) % 360).toFloat()

        val c1 = Color.HSVToColor(floatArrayOf(hue1, 0.65f, 0.45f))
        val c2 = Color.HSVToColor(floatArrayOf(hue2, 0.85f, 0.25f))

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, THUMB_SIZE.toFloat(), THUMB_SIZE.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, THUMB_SIZE.toFloat(), THUMB_SIZE.toFloat(), bgPaint)

        val initial = name.firstOrNull { it.isLetter() }?.uppercase() ?: "FX"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 80f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val yPos = (THUMB_SIZE / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initial, THUMB_SIZE / 2f, yPos, textPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val subY = THUMB_SIZE - 28f
        val shortGroup = if (group.length > 16) group.take(14) + ".." else group
        canvas.drawText(shortGroup, THUMB_SIZE / 2f, subY, subPaint)

        return bmp
    }
}
