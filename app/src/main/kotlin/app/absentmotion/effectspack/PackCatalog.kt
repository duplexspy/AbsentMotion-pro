package app.absentmotion.effectspack

import android.content.Context
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.effects.EffectDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream

object PackCatalog {
    private val _packs = MutableStateFlow<List<InstalledPack>>(emptyList())
    val packs: StateFlow<List<InstalledPack>> = _packs.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun refresh(context: Context) {
        _packs.value = PackStore.listPacks(context)
        _revision.value++
    }

    fun activeEffects(context: Context): List<PackEffect> {
        val installed = PackStore.listPacks(context).filter { it.enabled }
        val seen = HashSet<String>()
        val result = ArrayList<PackEffect>()
        for (p in installed) {
            for (eff in p.effects) {
                if (seen.add(eff.id)) {
                    result += eff
                }
            }
        }
        return result
    }

    fun activeEffectsWithPack(context: Context): List<Pair<InstalledPack, PackEffect>> {
        val installed = PackStore.listPacks(context).filter { it.enabled }
        val seen = HashSet<String>()
        val result = ArrayList<Pair<InstalledPack, PackEffect>>()
        for (p in installed) {
            for (eff in p.effects) {
                if (seen.add(eff.id)) {
                    result += p to eff
                }
            }
        }
        return result
    }

    fun mergeInto(base: EffectCatalog, context: Context): EffectCatalog {
        val effects = activeEffectsWithPack(context)
        if (effects.isEmpty()) return base

        val adaptedDefs = effects.map { (pack, eff) ->
            eff.toEffectDefinition(eff.thumbRef, pack.manifest.name.ifBlank { "Custom Effects" }, pack.manifest.author.ifBlank { null })
        }
        val allDefs = base.definitions + adaptedDefs

        val baseOpen = base.open
        val mergedOpen: (String) -> InputStream = { path ->
            val f = File(path)
            if (f.isFile) {
                f.inputStream()
            } else if (baseOpen != null) {
                baseOpen(path)
            } else {
                throw java.io.FileNotFoundException(path)
            }
        }

        return base.copy(
            definitions = allDefs,
            open = mergedOpen,
        )
    }
}
