@file:Suppress("DEPRECATION")

package app.absentmotion.pro.assets

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * In-App Web Browser to explore real websites (Poly Pizza, Free3D, GIPHY, Tenor, Flaticon)
 * and import 3D models and animated GIFs directly into the project timeline.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteAssetBrowserDialog(
    initialUrl: String = "https://poly.pizza",
    onDismiss: () -> Unit,
    onImportAsset: (File, String, AssetType) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Web Browser") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }

    val presetWebsites = listOf(
        Triple("Poly.Pizza (3D)", "https://poly.pizza", AssetType.MODEL_3D),
        Triple("Free3D.com (3D)", "https://free3d.com", AssetType.MODEL_3D),
        Triple("GIPHY.com (GIF)", "https://giphy.com/explore", AssetType.GIF),
        Triple("Tenor.com (GIF)", "https://tenor.com", AssetType.GIF),
        Triple("Flaticon.com (SVG)", "https://www.flaticon.com", AssetType.SVG)
    )

    fun downloadAndImport(urlStr: String, suggestedName: String, type: AssetType) {
        scope.launch {
            isDownloading = true
            downloadStatus = "Downloading $suggestedName..."
            val file = withContext(Dispatchers.IO) {
                try {
                    val url = URL(urlStr)
                    val conn = (url.openConnection() as HttpsURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    }
                    val ext = when (type) {
                        AssetType.MODEL_3D -> if (urlStr.contains(".glb", ignoreCase = true)) "glb" else "obj"
                        AssetType.GIF, AssetType.STICKER -> "gif"
                        AssetType.SVG -> "svg"
                    }
                    val cacheDir = File(context.cacheDir, "web_assets").apply { mkdirs() }
                    val target = File(cacheDir, "imported_${System.currentTimeMillis()}.$ext")
                    conn.inputStream.use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (target.exists() && target.length() > 0) target else null
                } catch (e: Exception) {
                    null
                }
            }
            isDownloading = false
            if (file != null) {
                Toast.makeText(context, "Imported $suggestedName!", Toast.LENGTH_SHORT).show()
                onImportAsset(file, suggestedName, type)
                onDismiss()
            } else {
                Toast.makeText(context, "Failed to download asset from $urlStr", Toast.LENGTH_LONG).show()
            }
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141722))
                .border(1.dp, Color(0xFF262C3E), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C2130))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A3144))
                                .clickable { webViewRef?.goBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("◀", color = Color.White, fontSize = 12.sp)
                        }

                        Spacer(Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A3144))
                                .clickable { webViewRef?.goForward() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color.White, fontSize = 12.sp)
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pageTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUrl,
                                color = Color(0xFF8E9AA8),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3144))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 14.sp)
                    }
                }

                if (loadProgress in 0.01f..0.99f) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Color(0xFF00FF9D),
                        trackColor = Color(0xFF1C2130)
                    )
                }

                // Preset Website Shortcuts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181D29))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((name, url, _) in presetWebsites) {
                        val active = currentUrl.startsWith(url.substringBefore("/explore"))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0xFF00FF9D) else Color(0xFF262C3E))
                                .clickable {
                                    currentUrl = url
                                    webViewRef?.loadUrl(url)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (active) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Interactive WebView
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) pageTitle = title
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        if (url != null) currentUrl = url
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        if (url != null) currentUrl = url
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val lower = url.lowercase()
                                        if (lower.endsWith(".obj") || lower.endsWith(".glb") || lower.endsWith(".gltf")) {
                                            downloadAndImport(url, url.substringAfterLast('/'), AssetType.MODEL_3D)
                                            return true
                                        }
                                        if (lower.endsWith(".gif")) {
                                            downloadAndImport(url, url.substringAfterLast('/'), AssetType.GIF)
                                            return true
                                        }
                                        if (lower.endsWith(".svg")) {
                                            downloadAndImport(url, url.substringAfterLast('/'), AssetType.SVG)
                                            return true
                                        }
                                        return false
                                    }
                                }

                                setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
                                    val filename = contentDisposition?.substringAfter("filename=")?.replace("\"", "")
                                        ?: url.substringAfterLast('/')
                                    val type = when {
                                        url.contains(".obj", ignoreCase = true) || url.contains(".glb", ignoreCase = true) -> AssetType.MODEL_3D
                                        url.contains(".gif", ignoreCase = true) || mimeType.contains("gif", ignoreCase = true) -> AssetType.GIF
                                        url.contains(".svg", ignoreCase = true) || mimeType.contains("svg", ignoreCase = true) -> AssetType.SVG
                                        else -> AssetType.MODEL_3D
                                    }
                                    downloadAndImport(url, filename, type)
                                })

                                loadUrl(initialUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isDownloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xCC0B0D14)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF00FF9D))
                                Spacer(Modifier.height(12.dp))
                                Text(downloadStatus, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Footer Floating Bar: Quick Import Prompt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C2130))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "💡 Tap any Download link on the website to import to timeline",
                        color = Color(0xFF00FF9D),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00FF9D))
                            .clickable {
                                // Fallback import current page URL or download asset
                                val filename = currentUrl.substringAfterLast('/').substringBefore('?').ifBlank { "web_asset" }
                                val type = if (currentUrl.contains("3d", ignoreCase = true) || currentUrl.contains("poly", ignoreCase = true)) AssetType.MODEL_3D else AssetType.GIF
                                downloadAndImport(currentUrl, filename, type)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Import This", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
