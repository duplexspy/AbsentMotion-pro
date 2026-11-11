package app.absentmotion.pro.ai.memory

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import app.absentmotion.pro.ai.model.AIModelId
import app.absentmotion.pro.ai.model.AIModelRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

class AIModelMemoryManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("absentmotion_ai_config", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<AIProfileConfig> = _configFlow.asStateFlow()

    private fun loadConfig(): AIProfileConfig {
        val typeName = prefs.getString("profile_type", AIProfileType.LITE.name) ?: AIProfileType.LITE.name
        val type = try {
            AIProfileType.valueOf(typeName)
        } catch (_: Exception) {
            AIProfileType.LITE
        }
        val customBudget = prefs.getLong("custom_budget", 3L * 1024 * 1024 * 1024)
        val customTileSize = prefs.getInt("custom_tile_size", 256)
        val customOverlap = prefs.getInt("custom_overlap", 10)
        val customBatch = prefs.getInt("custom_batch", 1)
        val customGpu = prefs.getBoolean("custom_gpu", false)
        val thermal = prefs.getBoolean("thermal_prot", true)

        return AIProfileConfig(
            profileType = type,
            customBudgetBytes = customBudget,
            customTileSize = customTileSize,
            customOverlapPercent = customOverlap,
            customBatchSize = customBatch,
            gpuAcceleration = customGpu,
            thermalThrottlingProtection = thermal
        )
    }

    fun saveConfig(newConfig: AIProfileConfig) {
        prefs.edit()
            .putString("profile_type", newConfig.profileType.name)
            .putLong("custom_budget", newConfig.customBudgetBytes)
            .putInt("custom_tile_size", newConfig.customTileSize)
            .putInt("custom_overlap", newConfig.customOverlapPercent)
            .putInt("custom_batch", newConfig.customBatchSize)
            .putBoolean("custom_gpu", newConfig.gpuAcceleration)
            .putBoolean("thermal_prot", newConfig.thermalThrottlingProtection)
            .apply()
        _configFlow.value = newConfig
    }

    fun getSystemMemoryInfo(): ActivityManager.MemoryInfo {
        val actMgr = context.getSystemService<ActivityManager>()
        val memInfo = ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        return memInfo
    }

    fun getDeviceTotalRamBytes(): Long {
        return getSystemMemoryInfo().totalMem
    }

    fun getDeviceAvailRamBytes(): Long {
        return getSystemMemoryInfo().availMem
    }

    fun isLowMemory(): Boolean {
        return getSystemMemoryInfo().lowMemory
    }

    fun canExecuteModel(modelId: AIModelId): Boolean {
        val def = AIModelRegistry.get(modelId)
        val activeBudget = _configFlow.value.activeBudgetBytes
        return activeBudget >= def.memoryReqBytes
    }

    fun recommendTileSize(imageWidth: Int, imageHeight: Int): Int {
        val active = _configFlow.value
        val baseSize = active.activeTileSize
        val maxDim = min(imageWidth, imageHeight)
        return when {
            baseSize >= maxDim -> {
                // Downscale tile to fit within dimension cleanly if smaller
                if (maxDim > 512) 512
                else if (maxDim > 256) 256
                else if (maxDim > 128) 128
                else 64
            }
            else -> baseSize
        }
    }

    fun reportOomAndDowngradeTileSize(currentTileSize: Int): Int {
        val steps = listOf(1024, 768, 512, 384, 256, 128, 64)
        val next = steps.firstOrNull { it < currentTileSize } ?: 64
        // Automatically save safe profile
        if (_configFlow.value.profileType == AIProfileType.CUSTOM) {
            saveConfig(_configFlow.value.copy(customTileSize = next))
        }
        return next
    }

    companion object {
        @Volatile
        private var instance: AIModelMemoryManager? = null

        fun getInstance(context: Context): AIModelMemoryManager {
            return instance ?: synchronized(this) {
                instance ?: AIModelMemoryManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
