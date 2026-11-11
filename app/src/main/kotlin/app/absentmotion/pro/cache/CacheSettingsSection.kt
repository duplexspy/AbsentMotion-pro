package app.absentmotion.pro.cache

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.ui.theme.Am
import kotlinx.coroutines.launch

@Composable
fun CacheSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var preset by remember { mutableStateOf(CacheManager.getPreset(context)) }
    var autoCleanup by remember { mutableStateOf(CacheManager.isAutoCleanupEnabled(context)) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var categoryBytes by remember { mutableStateOf(mapOf<CacheCategory, Long>()) }

    fun refreshUsage() {
        totalBytes = CacheManager.getTotalCacheBytes(context)
        categoryBytes = CacheCategory.entries.associateWith { CacheManager.getCategoryBytes(context, it) }
    }

    LaunchedEffect(Unit) {
        refreshUsage()
    }

    val limitMb = CacheManager.getLimitMb(context)
    val limitBytes = limitMb.toLong() * 1024L * 1024L
    val usageRatio = (totalBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E212F))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "ADVANCED CACHE MANAGEMENT",
                    color = Am.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${CacheManager.formatBytes(totalBytes)} used of $limitMb MB limit",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Am.Accent.copy(alpha = 0.15f))
                    .clickable {
                        scope.launch {
                            val cleared = CacheManager.clearAllCategories(context)
                            refreshUsage()
                            Toast.makeText(context, "Purged $cleared cache files", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Clear All", color = Am.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { usageRatio },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (usageRatio > 0.85f) Am.Warning else Am.Accent,
            trackColor = Color(0xFF2E3246),
        )

        Spacer(Modifier.height(14.dp))
        Text("CACHE BUDGET PRESET", color = Color(0xFF9EA3BA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CachePreset.entries.forEach { p ->
                val active = preset == p
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) Am.Accent else Color(0xFF282B3E))
                        .clickable {
                            preset = p
                            CacheManager.setPreset(context, p)
                            refreshUsage()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        p.displayName.substringBefore(" "),
                        color = if (active) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Automatic background LRU cleanup", color = Color.White, fontSize = 12.sp)
            Switch(
                checked = autoCleanup,
                onCheckedChange = {
                    autoCleanup = it
                    CacheManager.setAutoCleanupEnabled(context, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Am.Accent,
                    checkedTrackColor = Am.Accent.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        Text("CATEGORIES BREAKDOWN", color = Color(0xFF9EA3BA), fontSize = 10.sp, fontWeight = FontWeight.Bold)

        categoryBytes.forEach { (cat, bytes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cat.displayName, color = Color.White, fontSize = 12.sp)
                    Text(CacheManager.formatBytes(bytes), color = Color(0xFF8E94A8), fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2B2F44))
                        .clickable {
                            scope.launch {
                                CacheManager.clearCategory(context, cat)
                                refreshUsage()
                                Toast.makeText(context, "${cat.displayName} cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Clear", color = Color(0xFFBDC2D4), fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Safe purge: Cache clearing never touches projects, original media, or fonts.",
            color = Color(0xFF787D92),
            fontSize = 10.sp,
            lineHeight = 13.sp
        )
    }
}
