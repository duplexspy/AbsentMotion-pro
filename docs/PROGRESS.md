# Absent Motion Pro — Engineering Progress Tracker

| Phase | Description | Branch | Status | Test Harness | Release Gate |
|---|---|---|---|---|---|
| **Phase 0** | Baseline & Harness (Architecture docs, ProFeatures flags, Safe Mode crash-loop guard, test suites) | `feature/phase0-baseline-harness` | **Completed** | `ProFeaturesTest`, `tools/comment_check.py` | Pass |
| **Phase 1** | Export Black-Line Fix (Encoder alignment, viewport rounding, edge clamp, odd dimensions) | `feature/phase1-export-edge-fix` | **Completed** | `ExportEdgeTest` matrix | Pass |
| **Phase 2A**| Text Rendering Fix: Scale & Z Artifacts (Subpixel antialiasing, stem stability, overhang bounds) | `feature/phase2a-text-scale-z` | **Completed** | `TextTortureTest` | Pass |
| **Phase 2B**| Text Blurry After Grouping (Effective scale propagation, isolated pass bypass, sharpness audit) | `feature/phase2b-group-sharpness` | **Completed** | `GroupSharpnessTest` | Pass |
| **Phase 3** | Effect Thumbnails Without Lag (Pre-packed LQ atlas, LRU cache, RGB_565, fast fling deferral) | `feature/phase3-fast-thumbnails` | Planned | `ThumbnailPerfTest` | Pending |
| **Phase 4** | Pre-Render for Heavy Layers & Ranges (Non-destructive cache key, invalidation map, video/WebP intermediate) | `feature/phase4-prerender` | Planned | `PreRenderInvalidationTest` | Pending |
| **Phase 5** | New Vector Tool & Dashed Lines (Touch-first pen/nodes, loupe, keyframable dash/gap, SVG I/O) | `feature/phase5-vector-tool` | Planned | `VectorGeometryFuzzTest` | Pending |
| **Phase 6** | DaFont Browser & Secure Importer (Hardened allowlisted WebView, isolated `:fontcheck` service) | `feature/phase6-dafont-browser` | Planned | `FontSecurityAndImportTest` | Pending |
| **Phase 7** | Premium UI Polish (Design tokens, Classic/Premium switch, polished touch targets & sheets) | `feature/phase7-premium-ui` | Planned | UI Contrast & Regression Goldens | Pending |

---

## Phase 0 Execution Checklist
- [x] Create `docs/ARCHITECTURE.md` (Module map, rendering and export pipelines).
- [x] Create `docs/CORE_TOUCHPOINTS.md` (Touchpoint register and before/after verification rules).
- [x] Create `docs/REGRESSION.md` (System-wide regression verification checklist).
- [x] Create `docs/BASELINE.md` (Low-end and mid-range performance baselines).
- [x] Implement `ProFeatures.kt` with crash-loop guard and feature flags.
- [x] Integrate startup stability timer and crash detection into `MainActivity.kt` and `CrashLogs.kt`.
- [x] Implement Export Edge Test harness (`tools/export_edge_test.py` and unit test).
- [x] Implement Text Torture Test harness (`tools/text_torture_test.py` and unit test).
- [x] Implement Benchmark scene generator (`tools/perf_bench_harness.py`).
- [x] Verify zero comments (`python3 tools/comment_check.py`).
- [x] Build clean debug APK and run unit tests.
- [x] Commit Phase 0 to branch `feature/phase0-baseline-harness`.

---

## Phase 1 Execution Checklist
- [x] Investigate root causes across hardware encoders, swapchain, `EncoderFeed`, `ImageWriter`, clear colors, and YUV conversions.
- [x] Create `docs/EXPORT_EDGE_LINE_REPORT.md` documenting root causes and fixes.
- [x] Update `docs/CORE_TOUCHPOINTS.md` with touched files and guard flags.
- [x] Implement adaptive clear background color in `EngineThread.kt` (`clearColor` based on `document.bgColor`).
- [x] Implement macroblock alignment and dimension synchronization in `VideoExport.kt` (`openVideoEncoder`, `encoderFormat`, `EncoderFeed`, `drain`).
- [x] Implement visible crop bounds in `Muxers.kt` (`MatroskaSink.addTrack`).
- [x] Implement UV clamping within active decoded frame bounds in `VideoProxies.kt` (`ProxyGl`).
- [x] Implement edge-clamped YUV buffer padding initialization in `AiVideo.kt` (`writeYuv`).
- [x] Update `DeviceCapabilities.kt` (`EncoderCaps.fitsSize`) to allow aligned dimensions when Pro feature is enabled.
- [x] Expand `ExportEdgeHarnessTest.kt` with unit tests covering alignment, clear color, crop metadata, and proxy UV clamping.
- [x] Verify zero comments policy (`python3 tools/comment_check.py`).
- [x] Run unit tests and regression checks (`testProDebugUnitTest`, `bug_regression_check.py`).
- [x] Build Pro debug APK (`assembleProDebug`).
- [x] Commit Phase 1 to branch `feature/phase1-export-edge-fix`.

---

## Phase 2A Execution Checklist
- [x] Investigate root causes across FreeType hinting, integer metrics snapping, offscreen LCD subpixel anti-aliasing, and overhang truncation.
- [x] Create `docs/TEXT_RENDERING_REPORT.md` documenting root causes, hypotheses H1-H6, and fixes.
- [x] Update `docs/CORE_TOUCHPOINTS.md` with Phase 2A touched files and guard flag `ProFeatures.textRenderFix`.
- [x] Implement linear text, subpixel text, and disabled hinting on `TextPaint` in `TextLayout.kt`.
- [x] Implement font metrics padding (`padTop`, `padBottom`) for line baselines and layout height in `TextLayout.kt`.
- [x] Implement `gTextRenderFix` flag, linear metrics, subpixel positioning, disabled hinting, and grayscale edging (`Edging::kAntiAlias`) in `text.cpp`.
- [x] Implement expanded ink bounds in `TextPath` to prevent overhang clipping in `text.cpp`.
- [x] Implement direct canvas text drawing bypassing offscreen `saveLayer` in `text.cpp`.
- [x] Expand `tools/text_torture_test.py` with 76 bundled font discovery, 40-step scale sweep, Z perspective sweep, and golden contact sheet generator.
- [x] Expand `TextTortureHarnessTest.kt` unit tests covering linear coverage, non-linear jump detection, and overhang calculation.
- [x] Verify zero comments policy (`python3 tools/comment_check.py`).
- [x] Run unit tests and regression checks (`testProDebugUnitTest`, `bug_regression_check.py`).
- [x] Build Pro debug APK (`assembleProDebug`).
- [x] Commit Phase 2A to branch `feature/phase2a-text-scale-z`.

---

## Phase 2B Execution Checklist
- [x] Investigate root causes across premature offscreen pass allocation, fixed base-resolution baking, and subpixel misalignment.
- [x] Create `docs/GROUP_SHARPNESS_REPORT.md` documenting analysis, mathematics, and empirical results.
- [x] Update `docs/CORE_TOUCHPOINTS.md` with touched files and guard flag `ProFeatures.groupSharpnessFix`.
- [x] Implement offscreen pass bypass for non-isolated groups in `styles.cpp` (`DrawOne`).
- [x] Implement cumulative effective scale propagation to offscreen buffers in `styles.cpp` (`DrawGroup`).
- [x] Implement scale-adaptive resampling (1.0x nearest whole-pixel copy, <1.0x trilinear mipmapped downsampling, >1.0x high-res linear) in `styles.cpp` (`DrawGroup`).
- [x] Add thread-local `gGroupSharpnessFix` and API control in `scene.h` and `styles.cpp`.
- [x] Update `TextLayout.kt` to guard linear metrics and subpixel positioning under `groupSharpnessFix`.
- [x] Update `StructureActions.kt` to default new group nodes to dynamicResolution.
- [x] Create `tools/group_sharpness_test.py` with Laplacian edge variance calculation and visual contact sheet generator.
- [x] Create `GroupSharpnessHarnessTest.kt` unit test suite covering effective scale, offscreen bypass, sampling mode, and Laplacian variance match within 5%.
- [x] Verify zero comments policy (`python3 tools/comment_check.py`).
- [x] Run unit tests and regression checks (`testProDebugUnitTest`, `bug_regression_check.py`, `group_sharpness_test.py`).
- [x] Build Pro debug APK (`assembleProDebug`).
- [x] Commit Phase 2B to branch `feature/phase2b-group-sharpness`.
