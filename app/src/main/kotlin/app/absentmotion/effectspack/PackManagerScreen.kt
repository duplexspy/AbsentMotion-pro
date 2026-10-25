package app.absentmotion.effectspack

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.absentmotion.R
import app.absentmotion.platform.DialogStatusBarIcons
import app.absentmotion.platform.LocalContext
import app.absentmotion.platform.Toast
import app.absentmotion.platform.rememberDocumentPicker
import app.absentmotion.platform.sheetDialogProperties
import app.absentmotion.ui.editor.ChromeIcons
import app.absentmotion.ui.editor.PanelIcons
import app.absentmotion.ui.editor.PhoneScreen
import app.absentmotion.ui.editor.effects.EffectIcons
import app.absentmotion.ui.editor.panels.ThinSwitch
import app.absentmotion.ui.home.HomeIcons
import app.absentmotion.ui.home.TitleBar
import app.absentmotion.ui.theme.Am
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun PackManagerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs by PackCatalog.packs.collectAsState()
    var deletingPack by remember { mutableStateOf<InstalledPack?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val picker = rememberDocumentPicker { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        val cr = context.contentResolver
                        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                            PackStore.importZip(context, bytes.inputStream())
                        } else {
                            val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                            PackStore.importSingleXml(context, text)
                        }
                    }.getOrNull()
                }
                if (res != null) {
                    PackCatalog.refresh(context)
                    importStatus = "${context.getString(R.string.effects_pack_enable)}: ${res.name} (${res.importedCount} effects)"
                } else {
                    Toast.makeText(context, context.getString(R.string.effects_pack_compile_error, "Invalid format"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(onClose, sheetDialogProperties()) {
        PhoneScreen {
            DialogStatusBarIcons(darkIcons = Am.Card.luminance() > 0.5f)
            Box(Modifier.fillMaxSize().background(Am.SheetBg)) {
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                            Icon(HomeIcons.Close, stringResource(R.string.action_close), Modifier.size(24.dp), tint = Am.Navy)
                        }
                        Text(
                            stringResource(R.string.effects_pack_manager),
                            Modifier.weight(1f).padding(horizontal = 8.dp),
                            color = Am.Navy,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            Modifier.size(44.dp).clickable { picker.launch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(EffectIcons.Add, stringResource(R.string.effects_pack_import), Modifier.size(24.dp), tint = Am.Navy)
                        }
                    }

                    if (packs.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.effects_pack_empty),
                                color = Am.Navy.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            items(packs, key = { it.manifest.id }) { pack ->
                                PackCard(
                                    pack = pack,
                                    onToggle = { enabled ->
                                        PackStore.setPackEnabled(context, pack.manifest.id, enabled)
                                        PackCatalog.refresh(context)
                                    },
                                    onExport = {
                                        scope.launch {
                                            val exportFile = File(context.cacheDir, "${pack.manifest.id}.effectspack")
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    FileOutputStream(exportFile).use { out ->
                                                        PackStore.exportPackZip(context, pack.manifest.id, out)
                                                    }
                                                }
                                            }
                                            if (exportFile.isFile) {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        exportFile
                                                    )
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, pack.manifest.name))
                                            }
                                        }
                                    },
                                    onDelete = { deletingPack = pack }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }

                deletingPack?.let { pack ->
                    AlertDialog(
                        onDismissRequest = { deletingPack = null },
                        title = { Text(pack.manifest.name, color = Am.Navy) },
                        text = { Text(stringResource(R.string.effects_pack_delete), color = Am.Navy) },
                        confirmButton = {
                            TextButton(onClick = {
                                PackStore.deletePack(context, pack.manifest.id)
                                PackCatalog.refresh(context)
                                deletingPack = null
                            }) {
                                Text(stringResource(R.string.delete), color = Am.Warning)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deletingPack = null }) {
                                Text(stringResource(R.string.cancel), color = Am.Navy)
                            }
                        },
                        containerColor = Am.Card
                    )
                }

                importStatus?.let { status ->
                    AlertDialog(
                        onDismissRequest = { importStatus = null },
                        title = { Text(stringResource(R.string.effects_pack_manager), color = Am.Navy) },
                        text = { Text(status, color = Am.Navy) },
                        confirmButton = {
                            TextButton(onClick = { importStatus = null }) {
                                Text(stringResource(R.string.ok), color = Am.Navy)
                            }
                        },
                        containerColor = Am.Card
                    )
                }
            }
        }
    }
}

@Composable
private fun PackCard(
    pack: InstalledPack,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Am.Card).padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pack.manifest.name, color = Am.Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val info = "v${pack.manifest.version} • ${pack.effects.size} effects • ${pack.manifest.author}"
                Text(info, color = Am.Navy.copy(alpha = 0.65f), fontSize = 12.sp)
            }
            ThinSwitch(pack.enabled, onChange = onToggle)
        }

        if (pack.manifest.description.isNotBlank()) {
            Text(
                pack.manifest.description,
                Modifier.padding(top = 8.dp),
                color = Am.Navy.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.clickable(onClick = onExport).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ChromeIcons.Share, null, Modifier.size(16.dp), tint = Am.Accent)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.export), color = Am.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clickable(onClick = onDelete).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ChromeIcons.Trash, null, Modifier.size(16.dp), tint = Am.Warning)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.delete), color = Am.Warning, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
