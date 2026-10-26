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
| **Phase 4** | `app/src/main/kotlin/app/absentmotion/pro/PreRenderManager.kt` | Deterministic SHA-256 frame hashing, validity map, atomic storage, bounded memory buffer | `ProFeatures.preRender` | Active |
| **Phase 4** | `app/src/main/kotlin/app/absentmotion/library/AppSettings.kt` | Auto pre-render, export reuse, and preview cache storage limits | `ProFeatures.preRender` | Active |
| **Phase 4** | `app/src/main/kotlin/app/absentmotion/ui/home/SettingsDrawer.kt` | Preview cache storage usage, clear cache, and export reuse settings UI | `ProFeatures.preRender` | Active |
| **Phase 4** | `app/src/main/kotlin/app/absentmotion/library/ProjectStore.kt` | Prerender cache folder deletion on project removal | `ProFeatures.preRender` | Active |
| **Phase 4** | `app/src/main/kotlin/app/absentmotion/ui/editor/EditorMenus.kt` | Pre-render, Render and replace, and Pre-render range menu options | `ProFeatures.preRender` | Active |
| **Phase 4** | `app/src/main/kotlin/app/absentmotion/ui/editor/Timeline.kt` | Visual cached range indicator bar along layer track | `ProFeatures.preRender` | Active |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/pro/VectorToolModel.kt` | Vector primitives, knot types, knife cutting, join, snapping, dash auto-fit, SVG I/O | `ProFeatures.newVectorTool` | Active |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/editor/VectorToolMode.kt` | Interactive editing state, loupe geometry, snapping parameters | `ProFeatures.newVectorTool` | Active |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/pro/VectorToolActions.kt` | Undoable state mutations, subpixel nudge, clipboard SVG transfer | `ProFeatures.newVectorTool` | Active |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/pro/VectorToolEditor.kt` | Touch-first Compose Canvas overlay, high-contrast path rendering, magnifier loupe | `ProFeatures.newVectorTool` | Active |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/model/Styling.kt` | Extended StrokeStyle with dash, dashOffset, dashAutoFit, taperStart, taperEnd | `ProFeatures.newVectorTool` | Active |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/editor/DrawingMode.kt` | Entry points startVectorTool() and editVectorTool() | `ProFeatures.newVectorTool` | Active |
| **Phase 5** | `app/src/main/kotlin/app/absentmotion/ui/editor/DrawingEditor.kt` | Integrated VectorToolOverlay and VectorToolPanel | `ProFeatures.newVectorTool` | Active |
| **Phase 6** | `app/src/main/AndroidManifest.xml` | Isolated `:fontcheck` service declaration | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/pro/FontSecurity.kt` | Zip bomb/traversal guards, binary SFNT/name parser, SHA-256 fingerprinting | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/pro/FontCheckService.kt` | Out-of-process `:fontcheck` service for isolated font rasterization validation | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/pro/ProFontManager.kt` | App-private font storage, deduplication, metadata persistence, missing font scan | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/pro/DafontBrowser.kt` | Hardened sandboxed WebView, DaFont allowlist, download interception, preview sheet | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/pro/MyFontsManager.kt` | Custom font manager dialog, live preview, license viewer, dependency check | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/fonts/FontLibrary.kt` | Integration with ProFontManager and safe multi-archive extraction | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/ui/editor/FontBrowser.kt` | DaFont browser and My Custom Fonts actions in typography picker drawer | `ProFeatures.dafontBrowser` | Active |
| **Phase 6** | `app/src/main/kotlin/app/absentmotion/MainActivity.kt` | Missing font dialog with direct "Find on DaFont" launch action | `ProFeatures.dafontBrowser` | Active |
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

---

## 4. Phase 4 Before/After Evidence (Pre-Render Engine)

### 4.1 Playback Performance & Memory Flatness
- **Playback Frame Rate:** Live complex shader evaluation runs at 21.7 FPS (46.14 ms/frame); pre-rendered playback runs at $\ge 60$ FPS (0.30 ms/frame cached lookup), exceeding the $\ge 30$ FPS target.
- **Memory Stability:** 10-minute playback loop (18,000 frames) maintains 0.0 MB steady-state variance due to bounded 30-frame in-memory buffer.
- **Selective Invalidation:** Localized keyframe edit invalidates only affected frames (11 frames invalidated, 89 preserved out of 100).
- **Verification Suites:**
  - `PreRenderHarnessTest.kt`: 6/6 tests passing.
  - `tools/prerender_perf_test.py`: Verified FPS $\ge 30$, flat memory, selective invalidation.
  - `tools/comment_check.py`: 0 comments.

---

## 5. Phase 5 Before/After Evidence (Touch-First Vector Tool & Dashed Lines)

### 5.1 Touch Ergonomics & Snapping Latency
- **Touch Processing Latency:** Mean = 0.0125 ms, p95 = 0.0210 ms, p99 = 0.0253 ms (well below 16 ms / 60 FPS budget).
- **Dash Pattern Quality:** Corner and endpoint seam stutters eliminated via closed-loop integer cycle auto-fitting.
- **Verification Suites:**
  - `VectorToolHarnessTest.kt`: 15/15 tests passing.
  - `tools/vector_tool_perf_test.py`: Verified $p99 < 16$ ms.
  - `tools/comment_check.py`: 0 comments.

---

## 6. Phase 6 Before/After Evidence (Hardened DaFont Browser & Font Sandbox)

### 6.1 Security Sandbox & Import Stability
- **Zip Traversal & Bomb Defense:** 100% rejection rate for zip-slip paths (`../evil.ttf`), entries $> 100$, and compression ratios $> 25:1$.
- **Native Crash Containment:** Out-of-process `:fontcheck` service isolates FreeType/HarfBuzz execution, ensuring 0 main process crashes on corrupt font assets.
- **Verification Suites:**
  - `FontSecurityAndImportTest.kt`: 9/9 tests passing.
  - `tools/bug_regression_check.py`: 10/10 tests passing.
  - `tools/comment_check.py`: 0 comments.



