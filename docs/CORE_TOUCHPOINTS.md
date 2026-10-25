# Absent Motion Pro — Core Touchpoints Log

This document records all modifications made to core engine, rendering, export, and UI files.
In accordance with Ground Rule 3, every core change is kept surgical, flag-guarded via `ProFeatures`, and verified to ensure unaffected content remains pixel-identical.

---

## 1. Core Touchpoint Register

| Phase | Target File | Purpose | Guard Flag | Status |
|---|---|---|---|---|
| **Phase 0** | `app/src/main/kotlin/app/absentmotion/MainActivity.kt` | Initialize `ProFeatures` and startup stability timer | N/A | Active |
| **Phase 0** | `app/src/main/kotlin/app/absentmotion/library/CrashLogs.kt` | Hook startup crash counter for Safe Mode guard | N/A | Active |
| **Phase 0** | `app/src/main/kotlin/app/absentmotion/ui/home/SettingsDrawer.kt` | Safe Mode status display and reset toggle | N/A | Active |
| **Phase 1** | `app/src/main/kotlin/app/absentmotion/export/VideoExport.kt` | Alignment, viewport rounding, and edge clamp fixes | `ProFeatures.exportEdgeLineFix` | Active |
| **Phase 1** | `app/src/main/kotlin/app/absentmotion/engine/EngineThread.kt` | Adaptive background clear color for export targets | `ProFeatures.exportEdgeLineFix` | Active |
| **Phase 1** | `app/src/main/kotlin/app/absentmotion/export/Muxers.kt` | Matroska/WebM visible crop rect header compliance | `ProFeatures.exportEdgeLineFix` | Active |
| **Phase 1** | `app/src/main/kotlin/app/absentmotion/media/VideoProxies.kt` | Proxy downsampler UV clamping within frame bounds | `ProFeatures.exportEdgeLineFix` | Active |
| **Phase 1** | `app/src/main/kotlin/app/absentmotion/ai/AiVideo.kt` | YUV buffer padding edge clamp initialization | `ProFeatures.exportEdgeLineFix` | Active |
| **Phase 1** | `app/src/main/kotlin/app/absentmotion/export/DeviceCapabilities.kt` | Encoder dimension alignment support detection | `ProFeatures.exportEdgeLineFix` | Active |
| **Phase 2A**| `app/src/main/kotlin/app/absentmotion/fonts/TextLayout.kt` | Linear text, subpixel positioning, disabled hinting, overhang padding | `ProFeatures.textRenderFix` | Active |
| **Phase 2A**| `app/src/main/cpp/text.cpp` | Linear metrics, subpixel glyphs, no hinting, grayscale edging, ink bounds | `ProFeatures.textRenderFix` | Active |
| **Phase 2B**| `app/src/main/cpp/styles.cpp` | Offscreen pass bypass, effective scale propagation, trilinear/nearest sampling | `ProFeatures.groupSharpnessFix` | Active |
| **Phase 2B**| `app/src/main/cpp/scene.h` | Native `SetGroupSharpnessFix` and `GroupSharpnessFixOn` API declarations | `ProFeatures.groupSharpnessFix` | Active |
| **Phase 2B**| `app/src/main/kotlin/app/absentmotion/fonts/TextLayout.kt` | Dual guard check for text linear metrics and subpixel positioning | `ProFeatures.groupSharpnessFix` | Active |
| **Phase 2B**| `app/src/main/kotlin/app/absentmotion/editor/StructureActions.kt` | Default dynamicResolution precomposition on newly grouped scenes | `ProFeatures.groupSharpnessFix` | Active |
| **Phase 3** | `app/src/main/kotlin/app/absentmotion/pro/EffectThumbnailManager.kt` | 25MB LRU cache, 64x64 RGB_565 atlas, category placeholder tokens | `ProFeatures.effectThumbnailsLq` | Active |
| **Phase 3** | `app/src/main/kotlin/app/absentmotion/effects/EffectCatalog.kt` | Intercept thumbnail loading with memory cache lookup | `ProFeatures.effectThumbnailsLq` | Active |
| **Phase 3** | `app/src/main/kotlin/app/absentmotion/ui/editor/effects/EffectBrowser.kt` | Fast fling deferred rendering and composition local velocity tracking | `ProFeatures.effectThumbnailsLq` | Active |
| **Phase 4** | `app/src/main/kotlin/app/absentmotion/engine/EngineThread.kt` | Cache key verification and pre-rendered frame playback hook | `ProFeatures.preRender` | Planned |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/model/ShapeLayer.kt` | Optional dashed stroke and vector node morph fields | `ProFeatures.newVectorTool` | Planned |
| **Phase 6** | `app/src/main/AndroidManifest.xml` | Allowlisted WebView permissions and isolated font validation service | `ProFeatures.dafontBrowser` | Planned |
| **Phase 7** | `app/src/main/kotlin/app/absentmotion/ui/theme/Am.kt` | Design token layer and Classic/Premium UI switch | `ProFeatures.premiumUi` | Planned |

---

## 2. Touchpoint Verification Protocol

For every core change logged above:
1. **Automated Reproduction:** A reproduction test must fail before the fix and pass after.
2. **Flag Isolation:** With the feature flag set to `false`, behavior must revert to baseline without regressions.
3. **Golden Pixel Comparison:** Non-affected standard layers and projects must render with zero pixel drift (strict golden diff).
4. **Before/After Evidence:** Concrete diffs and test results must be appended to this document during phase execution.

---

## 3. Phase 3 Before/After Evidence (Effect Thumbnails Without Lag)

### 3.1 Memory & Latency Comparison
- **Memory Footprint:** Baseline 192x192 ARGB_8888 decoded asset is 147.5 KB; Pro 64x64 RGB_565 atlas tile is 8.2 KB (18.0x reduction).
- **Entire Catalog in RAM:** All 184 bundled thumbnails require 1.44 MB total RAM.
- **Scroll Simulation (1000 frames):**
  - Baseline: p50 = 32.56 ms, p95 = 40.41 ms, p99 = 40.50 ms; 522 dropped frames (>32 ms); estimated frame rate: 24.7 FPS.
  - Pro (Fast Thumbnails): p50 = 0.07 ms, p95 = 0.07 ms, p99 = 0.07 ms; 0 dropped frames (>32 ms); estimated frame rate: $\ge 60$ FPS.
- **Verification Suites:**
  - `ThumbnailPerfHarnessTest.kt`: 6/6 tests passing.
  - `tools/thumbnail_perf_test.py`: Verified $p99 < 24$ ms and 0 dropped frames $> 32$ ms.
  - `tools/comment_check.py`: 0 comments.

