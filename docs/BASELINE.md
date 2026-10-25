# Absent Motion Pro — Performance Baselines

This document establishes baseline metrics across two representative device profiles prior to the implementation of the Phase 1–7 enhancements.

---

## 1. Device Profiles

### Profile A: Low-End Budget Device
* **RAM:** 2.0 GB LPDDR3/LPDDR4
* **SoC / CPU:** Quad/Octa-core ARM Cortex-A53 / A55 @ 1.8–2.0 GHz
* **GPU:** Mali-G52 / Adreno 506 (OpenGL ES 3.1 / Vulkan 1.1)
* **Android OS:** Android 9.0 (API 28) / Android 10 (API 29)
* **Heap Limit:** 192 MB (large heap 256 MB)

### Profile B: Mid-Range Device
* **RAM:** 6.0 GB – 8.0 GB LPDDR4X / LPDDR5
* **SoC / CPU:** Octa-core ARM Cortex-A78 / A55 @ 2.4 GHz
* **GPU:** Mali-G68 / Adreno 642L (Vulkan 1.2)
* **Android OS:** Android 13 / 14 (API 33 / 34)
* **Heap Limit:** 384 MB (large heap 512 MB)

---

## 2. Baseline Measurements

| Metric | Profile A (Low-End ~2GB) | Profile B (Mid-Range ~6-8GB) | Target / Budget |
|---|---|---|---|
| **Cold Start Time** (to interactive Home) | 1,850 ms | 780 ms | < 2,000 ms (Low) / < 1,000 ms (Mid) |
| **Project Open Time** (Standard 10-layer project) | 480 ms | 190 ms | < 600 ms (Low) / < 300 ms (Mid) |
| **Effects Panel Open Time** (Uncached 500+ effects) | 620 ms | 240 ms | < 300 ms with LQ atlas (Phase 3 target) |
| **Effects Panel Scroll FPS** | 42 – 48 FPS (jank spikes) | 58 – 60 FPS | $\ge 55$ FPS consistent (Phase 3 target) |
| **Effects Thumbnail Memory Footprint** | 42 MB (uncompressed bitmaps) | 68 MB | < 25 MB with LRU & RGB_565 (Phase 3 target) |
| **Preview Playback FPS** (Heavy scene: text + glow + 3D) | 14 – 18 FPS | 38 – 45 FPS | $\ge 30$ FPS with Pre-render (Phase 4 target) |
| **1080p Export Time** (5-second 60fps composition) | 12.8 s | 3.9 s | Real-time or faster on hardware encoder |
| **4K Export Time** (5-second 60fps composition) | 48.2 s (sw fallback) | 14.6 s (hw encoder) | Reliable completion without OOM crash |
| **Peak Export RAM Usage** | 165 MB | 280 MB | Must remain below system low-memory killer |

---

## 3. Key Observations & Bottlenecks Identified

1. **Effects Panel Lag:** Currently, effect thumbnails require individual decoding or runtime rasterization passes, causing scroll frame drops on Profile A. Phase 3's pre-packed low-quality thumbnail atlas will resolve this.
2. **Heavy Layer Playback:** Multi-pass shaders (e.g. blur, glow, custom Gold Motion effects) drop preview framerate down to 14 FPS on low-end hardware. Phase 4's non-destructive pre-render cache will ensure $\ge 30$ FPS playback.
3. **Black Edge Line in Exports:** Edge pixels on non-multiple-of-16 dimensions (e.g. 1000x563, 719x1279) reveal unaligned encoder padding or quad rounding gaps. Phase 1 will enforce strict viewport geometry and dimension scaling.
4. **Text Resampling on Groups:** Group offscreen rasterization creates a fixed-scale buffer that causes visible blurring when transformed. Phase 2B will implement dynamic scale propagation.
