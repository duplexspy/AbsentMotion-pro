@file:Suppress("DEPRECATION")

package app.absentmotion.pro.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.ui.theme.Am
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineAssetPickerSheet(
    onDismiss: () -> Unit,
    onImportAsset: (File, OnlineAsset) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assetStore = remember { AssetStore(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var downloadingAssetId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }

    var fetchedAssets by remember { mutableStateOf<List<OnlineAsset>>(emptyList()) }
    var showWebBrowser by remember { mutableStateOf(false) }
    var webBrowserUrl by remember { mutableStateOf("https://poly.pizza") }
    val favorites by assetStore.favorites.collectAsState()
    val recents by assetStore.recents.collectAsState()
    val aiGenerations by assetStore.aiGenerations.collectAsState()

    val tabs = listOf("GIFs", "Stickers", "SVG Icons", "3D Models", "AI Renders", "Favorites", "Recents")

    fun triggerSearch() {
        scope.launch {
            isLoading = true
            fetchedAssets = when (selectedTab) {
                0 -> OnlineAssetService.searchGiphy(searchQuery, isSticker = false)
                1 -> OnlineAssetService.searchGiphy(searchQuery, isSticker = true)
                2 -> OnlineAssetService.searchFlaticonSvg(searchQuery)
                3 -> OnlineAssetService.search3dModels(searchQuery)
                else -> emptyList()
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab in 0..3) {
            triggerSearch()
        }
    }

    val displayAssets = when (selectedTab) {
        0, 1, 2, 3 -> fetchedAssets
        4 -> aiGenerations.filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
        5 -> favorites.filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
        6 -> recents.filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
        else -> emptyList()
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141722))
                .border(1.dp, Color(0xFF262C3E), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "GIF, Sticker, SVG & 3D Library",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GIPHY • Tenor • Flaticon • Free 3D Models",
                            color = Color(0xFF00FF9D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF202637))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Real Websites Browser Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E283D))
                        .border(1.dp, Color(0xFF00FF9D).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable {
                            webBrowserUrl = if (selectedTab == 3) "https://poly.pizza" else "https://giphy.com/explore"
                            showWebBrowser = true
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌐", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Browse Real Websites (Free3D, Poly Pizza, Giphy, Tenor)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Directly explore and import real 3D models & GIFs from the web", color = Color(0xFF00FF9D), fontSize = 10.sp)
                        }
                    }
                    Text("Open ↗", color = Color(0xFF00FF9D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (selectedTab in 0..2) triggerSearch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search animated GIFs, stickers, SVGs...", color = Color(0xFF7A869A), fontSize = 13.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1B2030),
                        unfocusedContainerColor = Color(0xFF1B2030),
                        focusedIndicatorColor = Color(0xFF00FF9D),
                        unfocusedIndicatorColor = Color(0xFF2C344A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.height(10.dp))

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF00FF9D),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF00FF9D)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) Color(0xFF00FF9D) else Color(0xFF8E9AA8)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Content Grid
                if (isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00FF9D), modifier = Modifier.size(36.dp))
                    }
                } else if (displayAssets.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedTab == 3) "No favorites saved yet. Tap the heart on any asset to save it!"
                            else if (selectedTab == 4) "No recent assets used yet."
                            else "No assets found. Try a different search term.",
                            color = Color(0xFF8E9AA8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayAssets, key = { it.id }) { asset ->
                            val isFav = assetStore.isFavorite(asset.id)
                            val isDownloading = downloadingAssetId == asset.id

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E2436))
                                    .border(1.dp, Color(0xFF2C344C), RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (!isDownloading) {
                                            scope.launch {
                                                downloadingAssetId = asset.id
                                                downloadProgress = 0f
                                                val localFile = runCatching {
                                                    assetStore.downloadAsset(asset) { progress ->
                                                        downloadProgress = progress
                                                    }
                                                }.getOrNull()
                                                downloadingAssetId = null
                                                if (localFile != null && localFile.exists()) {
                                                    onImportAsset(localFile, asset)
                                                    onDismiss()
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Column(Modifier.fillMaxSize().padding(8.dp)) {
                                    // Thumbnail / Preview box
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF121520)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = when (asset.type) {
                                                    AssetType.MODEL_3D -> "🎲 3D MODEL"
                                                    AssetType.SVG -> "⚡ SVG"
                                                    AssetType.STICKER -> "✨ STICKER"
                                                    AssetType.GIF -> "🎬 GIF"
                                                },
                                                color = Color(0xFF00FF9D),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = asset.title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )
                                            if (asset.details.isNotBlank()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = asset.details,
                                                    color = Color(0xFF8E9AA8),
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            }
                                        }

                                        // Favorite button
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x99000000))
                                                .clickable {
                                                    scope.launch { assetStore.toggleFavorite(asset) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isFav) "❤️" else "🤍",
                                                fontSize = 12.sp
                                            )
                                        }

                                        if (isDownloading) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(0xCC000000)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    progress = { downloadProgress },
                                                    color = Color(0xFF00FF9D),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    // Source badge and import prompt
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = asset.source.displayName,
                                            color = Color(0xFF7A869A),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        Text(
                                            text = "+ Tap to Add",
                                            color = Color(0xFF00FF9D),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Footer attribution
                Text(
                    text = "GIPHY, Tenor and Flaticon API integrated. Content subject to respective platform licenses.",
                    color = Color(0xFF5A6678),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showWebBrowser) {
        WebsiteAssetBrowserDialog(
            initialUrl = webBrowserUrl,
            onDismiss = { showWebBrowser = false },
            onImportAsset = { file, title, type ->
                showWebBrowser = false
                val onlineAsset = OnlineAsset(
                    id = "web_${System.currentTimeMillis()}",
                    title = title,
                    type = type,
                    source = when (type) {
                        AssetType.MODEL_3D -> AssetSource.POLY_PIZZA
                        AssetType.GIF, AssetType.STICKER -> AssetSource.GIPHY
                        AssetType.SVG -> AssetSource.FLATICON
                    },
                    previewUrl = file.toURI().toString(),
                    contentUrl = file.toURI().toString()
                )
                onImportAsset(file, onlineAsset)
            }
        )
    }
}
