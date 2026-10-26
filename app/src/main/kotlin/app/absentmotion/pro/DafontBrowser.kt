package app.absentmotion.pro

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.absentmotion.fonts.FontLibrary
import app.absentmotion.ui.editor.ChromeIcons
import app.absentmotion.ui.editor.FontIconButton
import app.absentmotion.ui.editor.FontPreview
import app.absentmotion.ui.editor.TextIcons
import app.absentmotion.ui.theme.Am
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private val RefreshIcon: ImageVector by lazy {
    ImageVector.Builder("Refresh", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes("M17.65 6.35A8 8 0 1 0 19.74 13M20 4L20 9L15 9"),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DafontBrowserDialog(
    initialQuery: String = "",
    initialUrl: String = "",
    library: FontLibrary,
    manager: ProFontManager,
    onFontImported: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember {
        mutableStateOf(
            when {
                initialUrl.isNotBlank() && FontSecurity.isDafontUrl(initialUrl) -> initialUrl
                initialQuery.isNotBlank() -> "https://www.dafont.com/search.php?q=${Uri.encode(initialQuery)}"
                else -> "https://www.dafont.com/"
            }
        )
    }

    var pageTitle by remember { mutableStateOf("DaFont Browser") }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf(initialQuery) }

    var downloadOffer by remember { mutableStateOf<DownloadOffer?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var validationOffer by remember { mutableStateOf<ValidationOffer?>(null) }
    var errorDialog by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Am.PanelInset)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Am.TopBar)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FontIconButton(ChromeIcons.Back, "Back") {
                    if (webViewInstance?.canGoBack() == true) {
                        webViewInstance?.goBack()
                    } else {
                        onClose()
                    }
                }

                Row(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Am.Well)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(TextIcons.Search, null, Modifier.size(16.dp), tint = Am.Caption)
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Am.OnDark, fontSize = 13.sp),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchInput.isEmpty()) {
                                Text("Search DaFont...", color = Am.Caption, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                    if (searchInput.isNotEmpty()) {
                        Icon(
                            TextIcons.Tick,
                            "Search",
                            Modifier
                                .size(18.dp)
                                .clickable {
                                    val target = "https://www.dafont.com/search.php?q=${Uri.encode(searchInput)}"
                                    currentUrl = target
                                    webViewInstance?.loadUrl(target)
                                },
                            tint = Am.Accent,
                        )
                    }
                }

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(RefreshIcon, "Reload", Modifier.size(20.dp), tint = Am.OnDark)
                }

                IconButton(
                    onClick = {
                        val active = webViewInstance?.url ?: currentUrl
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(active)))
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(TextIcons.Import, "Open in Browser", Modifier.size(20.dp), tint = Am.OnDark)
                }

                FontIconButton(ChromeIcons.Close, "Close", onClick = onClose)
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Am.Accent,
                    trackColor = Am.Panel,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                safeBrowsingEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    if (FontSecurity.isDafontUrl(targetUrl)) {
                                        return false
                                    }
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                    }
                                    return true
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    isOffline = false
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    pageTitle = view?.title ?: "DaFont"
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        isOffline = true
                                    }
                                }

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.cancel()
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadingProgress = newProgress / 100f
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    title?.let { pageTitle = it }
                                }
                            }

                            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                if (FontSecurity.isDafontUrl(url) || url.contains(".dafont.com/dl/")) {
                                    val guessedName = File(Uri.parse(url).path ?: "font.zip").name.ifBlank { "font.zip" }
                                    val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                                    downloadOffer = DownloadOffer(
                                        url = url,
                                        fileName = guessedName,
                                        contentLength = contentLength,
                                        userAgent = userAgent.orEmpty(),
                                        cookie = cookies,
                                    )
                                }
                            })

                            loadUrl(currentUrl)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (isOffline) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Am.PanelInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(RefreshIcon, null, Modifier.size(48.dp), tint = Am.Caption)
                            Spacer(Modifier.height(12.dp))
                            Text("Page Unavailable", color = Am.OnDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Please check your internet connection and try again.", color = Am.Caption, fontSize = 13.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    isOffline = false
                                    webViewInstance?.loadUrl(currentUrl)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Am.Accent),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    downloadOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = { if (!isDownloading) downloadOffer = null },
            title = {
                Text("Download Font Package", color = Am.OnDark, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Download '${offer.fileName}' from DaFont?",
                        color = Am.OnDark,
                        fontSize = 14.sp,
                    )
                    if (offer.contentLength > 0L) {
                        Text(
                            "Estimated size: ${offer.contentLength / 1024} KB",
                            color = Am.Caption,
                            fontSize = 12.sp,
                        )
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Am.Warning.copy(alpha = 0.15f))
                            .padding(10.dp)
                    ) {
                        Text(
                            "License Notice: Fonts hosted on DaFont have varied licenses (Free for personal use, Donationware, Public Domain, Demo, Commercial). Commercial use requires permission from the author. The included readme in the archive is authoritative.",
                            color = Am.Warning,
                            fontSize = 11.sp,
                        )
                    }

                    if (isDownloading) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Am.Accent)
                            Text(downloadStatus.ifBlank { "Downloading font..." }, color = Am.OnDark, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDownloading) return@Button
                        isDownloading = true
                        downloadStatus = "Downloading archive..."
                        scope.launch {
                            val targetFile = File(context.cacheDir, "dafont_dl_${System.currentTimeMillis()}.zip")
                            val downloaded = runCatching {
                                downloadFileStreaming(
                                    url = offer.url,
                                    dest = targetFile,
                                    cookie = offer.cookie,
                                    userAgent = offer.userAgent,
                                )
                            }.getOrDefault(false)

                            if (!downloaded || !targetFile.isFile || targetFile.length() == 0L) {
                                isDownloading = false
                                downloadOffer = null
                                errorDialog = "Failed to download font from DaFont. Please check connection and try again."
                                return@launch
                            }

                            downloadStatus = "Extracting and validating fonts..."
                            val tempDir = File(context.cacheDir, "font_val_${System.currentTimeMillis()}").apply { mkdirs() }
                            try {
                                val extracted = FontSecurity.safeExtractZip(targetFile, tempDir)
                                val fontFiles = extracted.filter { it.extension.lowercase() in FontSecurity.FONT_EXTENSIONS }
                                val docFiles = extracted.filter { it.extension.lowercase() in FontSecurity.DOC_EXTENSIONS }

                                if (fontFiles.isEmpty()) {
                                    isDownloading = false
                                    downloadOffer = null
                                    errorDialog = "The downloaded archive did not contain any valid .ttf or .otf font files."
                                    return@launch
                                }

                                val readmeText = docFiles.take(2).joinToString("\n\n") { doc ->
                                    runCatching { doc.readText().take(16384) }.getOrDefault("")
                                }.trim()

                                val validList = ArrayList<Pair<File, ParsedFontMetadata>>()
                                for (f in fontFiles) {
                                    val checked = FontCheckService.validateFont(context, f)
                                    if (checked.isValid) {
                                        validList.add(f to checked)
                                    }
                                }

                                if (validList.isEmpty()) {
                                    isDownloading = false
                                    downloadOffer = null
                                    errorDialog = "All fonts in the archive failed isolated security validation."
                                    return@launch
                                }

                                isDownloading = false
                                downloadOffer = null
                                validationOffer = ValidationOffer(
                                    sourceUrl = offer.url,
                                    fonts = validList,
                                    readmeText = readmeText,
                                    cleanupDir = tempDir,
                                    zipFile = targetFile,
                                )
                            } catch (e: Throwable) {
                                isDownloading = false
                                downloadOffer = null
                                tempDir.deleteRecursively()
                                targetFile.delete()
                                errorDialog = "Error inspecting font archive: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Am.Accent),
                    enabled = !isDownloading,
                ) {
                    Text("Download & Verify")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { downloadOffer = null },
                    enabled = !isDownloading,
                ) {
                    Text("Cancel", color = Am.Caption)
                }
            },
            containerColor = Am.Panel,
        )
    }

    validationOffer?.let { offer ->
        var selectedFontIndex by remember { mutableIntStateOf(0) }
        val activePair = offer.fonts.getOrNull(selectedFontIndex) ?: offer.fonts.first()
        var previewInput by remember { mutableStateOf("Absent Motion Pro 2026") }

        AlertDialog(
            onDismissRequest = {
                offer.cleanupDir.deleteRecursively()
                offer.zipFile.delete()
                validationOffer = null
            },
            title = {
                Text("Add Font to My Fonts", color = Am.OnDark, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${activePair.second.family} (${activePair.second.style})",
                        color = Am.Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (offer.fonts.size > 1) {
                        Text("Fonts in archive (${offer.fonts.size}):", color = Am.Caption, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            offer.fonts.forEachIndexed { index, pair ->
                                FilterChip(
                                    selected = index == selectedFontIndex,
                                    onClick = { selectedFontIndex = index },
                                    label = { Text(pair.second.style, fontSize = 11.sp) },
                                )
                            }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Am.Well)
                            .padding(8.dp)
                    ) {
                        Column {
                            BasicTextField(
                                value = previewInput,
                                onValueChange = { previewInput = it },
                                textStyle = TextStyle(color = Am.OnDark, fontSize = 18.sp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Type above to preview glyph rendering", color = Am.Caption, fontSize = 10.sp)
                        }
                    }

                    if (activePair.second.license.isNotBlank()) {
                        Text("License: ${activePair.second.license}", color = Am.OnDark, fontSize = 12.sp)
                    }
                    if (activePair.second.copyright.isNotBlank()) {
                        Text("Copyright: ${activePair.second.copyright}", color = Am.Caption, fontSize = 11.sp)
                    }

                    if (offer.readmeText.isNotBlank()) {
                        Text("Author Readme:", color = Am.OnDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Am.Well)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(offer.readmeText, color = Am.OnDark, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val importedMeta = ArrayList<FontMeta>()
                            for (pair in offer.fonts) {
                                runCatching {
                                    val meta = manager.importFontFile(
                                        file = pair.first,
                                        sourceUrl = offer.sourceUrl,
                                        readme = offer.readmeText,
                                    )
                                    importedMeta.add(meta)
                                }
                            }
                            offer.cleanupDir.deleteRecursively()
                            offer.zipFile.delete()
                            validationOffer = null

                            if (importedMeta.isNotEmpty()) {
                                val primary = importedMeta[selectedFontIndex.coerceIn(importedMeta.indices)]
                                onFontImported("imported?name=${primary.fileName}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Am.Accent),
                ) {
                    Text("Add to My Fonts")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        offer.cleanupDir.deleteRecursively()
                        offer.zipFile.delete()
                        validationOffer = null
                    }
                ) {
                    Text("Cancel", color = Am.Caption)
                }
            },
            containerColor = Am.Panel,
        )
    }

    errorDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("Font Import Notice", color = Am.OnDark) },
            text = { Text(msg, color = Am.Caption, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { errorDialog = null }) {
                    Text("OK", color = Am.Accent)
                }
            },
            containerColor = Am.Panel,
        )
    }
}

private data class DownloadOffer(
    val url: String,
    val fileName: String,
    val contentLength: Long,
    val userAgent: String,
    val cookie: String,
)

private data class ValidationOffer(
    val sourceUrl: String,
    val fonts: List<Pair<File, ParsedFontMetadata>>,
    val readmeText: String,
    val cleanupDir: File,
    val zipFile: File,
)

private suspend fun downloadFileStreaming(
    url: String,
    dest: File,
    cookie: String,
    userAgent: String,
): Boolean = withContext(Dispatchers.IO) {
    var connection: HttpsURLConnection? = null
    try {
        val target = URL(url)
        require(FontSecurity.isDafontUrl(url)) { "Disallowed download host: ${target.host}" }
        connection = (target.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
            if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
        }
        connection.connect()
        if (connection.responseCode !in 200..299) return@withContext false

        FileOutputStream(dest).use { fos ->
            connection.inputStream.use { input ->
                val buf = ByteArray(16384)
                var bytesWritten = 0L
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    fos.write(buf, 0, read)
                    bytesWritten += read
                    if (bytesWritten > FontSecurity.MAX_ZIP_BYTES) {
                        dest.delete()
                        return@withContext false
                    }
                }
            }
        }
        dest.isFile && dest.length() > 0L
    } catch (e: Throwable) {
        dest.delete()
        false
    } finally {
        connection?.disconnect()
    }
}
