package app.absentmotion.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.R
import app.absentmotion.fonts.FontLibrary
import app.absentmotion.ui.editor.ChromeIcons
import app.absentmotion.ui.editor.FontIconButton
import app.absentmotion.ui.editor.FontPreview
import app.absentmotion.ui.editor.TextIcons
import app.absentmotion.ui.theme.Am
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyFontsDialog(
    manager: ProFontManager,
    library: FontLibrary,
    onSelect: (String) -> Unit,
    onOpenDafont: (String) -> Unit,
    onClose: () -> Unit,
) {
    var fonts by remember { mutableStateOf(manager.loadAll()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedForInfo by remember { mutableStateOf<FontMeta?>(null) }
    var selectedForDelete by remember { mutableStateOf<FontMeta?>(null) }
    var previewText by remember { mutableStateOf("The quick brown fox jumps over the lazy dog 123") }

    val filtered = remember(fonts, searchQuery) {
        if (searchQuery.isBlank()) fonts
        else fonts.filter {
            it.family.contains(searchQuery, ignoreCase = true) ||
                it.style.contains(searchQuery, ignoreCase = true) ||
                it.fileName.contains(searchQuery, ignoreCase = true)
        }
    }

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
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FontIconButton(ChromeIcons.Back, "Back", onClick = onClose)
                Text(
                    "My Custom Fonts (${fonts.size})",
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    color = Am.OnDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { onOpenDafont("") },
                    colors = ButtonDefaults.buttonColors(containerColor = Am.Accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(TextIcons.Cloud, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DaFont", fontSize = 13.sp)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Am.Well)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TextIcons.Search, null, Modifier.size(18.dp), tint = Am.Caption)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Am.OnDark, fontSize = 14.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search installed fonts...", color = Am.Caption, fontSize = 14.sp)
                        }
                        inner()
                    },
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        ChromeIcons.Close,
                        "Clear",
                        Modifier
                            .size(16.dp)
                            .clickable { searchQuery = "" },
                        tint = Am.Caption,
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(TextIcons.Import, null, Modifier.size(48.dp), tint = Am.Caption)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isEmpty()) "No custom fonts imported yet" else "No matching fonts found",
                            color = Am.OnDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Download fonts from DaFont or import .ttf/.otf/.zip files",
                            color = Am.Caption,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onOpenDafont("") },
                            colors = ButtonDefaults.buttonColors(containerColor = Am.Accent),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Browse DaFont")
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.fileName }) { meta ->
                        val fontXml = "imported?name=${meta.fileName}"
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Am.Line, RoundedCornerShape(10.dp))
                                .clickable { onSelect(fontXml) },
                            colors = CardDefaults.cardColors(containerColor = Am.Panel),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            meta.family,
                                            color = Am.OnDark,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "${meta.style} • ${meta.fileSize / 1024} KB",
                                            color = Am.Caption,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { selectedForInfo = meta },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                TextIcons.Menu,
                                                "License",
                                                Modifier.size(18.dp),
                                                tint = Am.Accent,
                                            )
                                        }
                                        IconButton(
                                            onClick = { selectedForDelete = meta },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                ChromeIcons.Close,
                                                "Delete",
                                                Modifier.size(18.dp),
                                                tint = Am.Warning,
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Am.Well)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    FontPreview(
                                        library = library,
                                        name = fontXml,
                                        content = previewText.take(40),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedForInfo?.let { meta ->
        AlertDialog(
            onDismissRequest = { selectedForInfo = null },
            title = {
                Text("${meta.family} (${meta.style})", color = Am.OnDark, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (meta.license.isNotBlank()) {
                        Text("License: ${meta.license}", color = Am.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (meta.copyright.isNotBlank()) {
                        Text("Copyright: ${meta.copyright}", color = Am.Caption, fontSize = 12.sp)
                    }
                    if (meta.version.isNotBlank()) {
                        Text("Version: ${meta.version}", color = Am.Caption, fontSize = 12.sp)
                    }
                    val dateStr = runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(meta.importDate))
                    }.getOrDefault("")
                    Text("Imported: $dateStr (${meta.fileSize / 1024} KB)", color = Am.Caption, fontSize = 12.sp)
                    if (meta.sha256.isNotBlank()) {
                        Text("SHA-256: ${meta.sha256.take(16)}...", color = Am.Caption, fontSize = 10.sp)
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Am.Warning.copy(alpha = 0.15f))
                            .padding(8.dp)
                    ) {
                        Text(
                            "Notice: DaFont font licenses vary by author. Many are free for personal use only. Commercial projects require author permission.",
                            color = Am.Warning,
                            fontSize = 11.sp,
                        )
                    }

                    if (meta.readmeText.isNotBlank()) {
                        Text("Author Readme:", color = Am.OnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Am.Well)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(meta.readmeText, color = Am.OnDark, fontSize = 11.sp)
                        }
                    }

                    if (meta.sourceUrl.isNotBlank()) {
                        Button(
                            onClick = { onOpenDafont(meta.sourceUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = Am.Accent),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Source Page on DaFont", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedForInfo = null }) {
                    Text("Close", color = Am.Accent)
                }
            },
            containerColor = Am.Panel,
        )
    }

    selectedForDelete?.let { meta ->
        AlertDialog(
            onDismissRequest = { selectedForDelete = null },
            title = { Text("Delete Font?", color = Am.OnDark) },
            text = {
                Text(
                    "Are you sure you want to remove '${meta.family}'? Layers using this font will revert to default fallback.",
                    color = Am.Caption,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        manager.deleteFont(meta.fileName)
                        library.deleteImported("imported?name=${meta.fileName}")
                        fonts = manager.loadAll()
                        selectedForDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Am.Warning),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedForDelete = null }) {
                    Text("Cancel", color = Am.Caption)
                }
            },
            containerColor = Am.Panel,
        )
    }
}
