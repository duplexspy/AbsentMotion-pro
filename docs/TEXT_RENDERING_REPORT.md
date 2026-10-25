# Absent Motion Pro — Text Rendering Diagnostic and Resolution Report (Phase 2A)

This document provides a comprehensive root cause analysis, hypothesis testing record, and verification summary for text rendering scale and camera Z-axis artifacts.

---

## 1. Problem Statement & Symptoms

During motion graphics authoring, keyframing layer scale (0.25x to 8.0x) or moving layers along the Z-axis (camera depth / 3D perspective) exhibited two prominent visual regressions:
1. **Sudden Boldness Jumps:** Stems abruptly popped in thickness at scale boundaries instead of scaling continuously and smoothly.
2. **X-Axis Edge Fringing & Artifacts:** Glyphs developed dark or tinted fringe bands on their horizontal edges, particularly on italicized fonts, script fonts, and offscreen tinted layers.
3. **Overhang Clipping:** Glyphs with large italic slant, negative side bearings (such as 'f', 'j', italic capitals), swashes, or diacritic accents suffered straight vertical/horizontal cut-offs at layer bounding box limits.

---

## 2. Hypothesis Testing & Root Cause Analysis

| Hypothesis | Theory | Investigation & Findings | Resolution Applied |
|---|---|---|---|
| **H1: Hinting & Grid Snapping** | FreeType bytecode and autohinter snap stems to whole pixel coordinates, causing abrupt stem width jumps. | **Confirmed.** Default Skia and Android font paints used default hinting and integer advance metrics, causing stems to snap discontinuously when transformed. | Disabled hinting (`HINTING_OFF`, `SkFontHinting::kNone`), enabled subpixel text (`isSubpixelText = true`, `setSubpixel(true)`), and enabled linear metrics (`isLinearText = true`, `setLinearMetrics(true)`). |
| **H2: Synthetic Bold & Variable Axes** | Synthetic bold or variable font `wght` axes resolving differently at scale thresholds. | **Evaluated.** Emboldening was verified to be scale-independent. Font weight mappings preserved across all scale steps. | Retained scale-independent weight mappings; ensured synthetic emboldening is never implicitly injected under scale. |
| **H3: Offscreen Re-rasterization** | Re-rasterizing text into offscreen buffers at arbitrary scale boundaries creates resampling artifacts. | **Confirmed.** `DrawText` utilized an offscreen `canvas->saveLayer(&area, &tint)` pass with `SkBlendMode::kSrcIn` tinting. Under 3D perspective and scale, this forced double-resampling. | Direct canvas drawing (`DrawRuns` with target paint) replaces `saveLayer`, rasterizing directly to viewport resolution with exact subpixel transforms. |
| **H4: Mipmap & Filtering Boundaries** | Non-premultiplied blending and lack of edge clamping create halos or LOD jumps. | **Confirmed.** Offscreen layers without proper padding or edge clamping produced fringe boundaries. | Direct vector path/glyph rendering completely eliminates offscreen texture sampling seams. |
| **H5: Overhang Bounds Truncation** | Bounds computed solely from layout `wrapWidth` and line heights clip negative bearings, italic swashes, and ascenders. | **Confirmed.** TrueType metrics across all 76 bundled fonts revealed negative left side bearings (e.g. -985 in Roboto, -208 in KaushanScript, -111 in Caveat) and negative right side bearings. Text bounding paths clipped these regions. | Expanded `_layout` height with `padTop` (ascent/accent clearance) and `padBottom` (descender/swash clearance), adjusted line baselines, and united layout bounds with true glyph ink bounds in `TextPath`. |
| **H6: LCD Subpixel Anti-Aliasing** | LCD subpixel anti-aliasing applied to transparent offscreen buffers distorts RGB channels under scale/rotation/perspective. | **Confirmed.** LCD subpixel anti-aliasing is valid only for unscaled, axis-aligned text over opaque backdrops. On transparent surfaces or under 3D transforms, it creates horizontal color fringing. | Forced standard grayscale anti-aliasing (`SkFont::Edging::kAntiAlias`) for all text rendering passes. |

---

## 3. Verification Protocol

The resolution was validated using automated test suites:
- **`tools/text_torture_test.py`**:
  - Discovered and parsed 76 fonts across `app/src/main/assets/fonts/` and `tests/fonts/`.
  - Swept 40 logarithmic scale steps from 0.25x to 8.0x.
  - Swept camera Z perspective range from -1000 to +1000.
  - Asserted normalized ink coverage `(ink / scale^2)` delta <= 5.0% between steps and deviation <= 8.0% from high-resolution reference.
  - Verified overhang and negative bearing clearance across scripts, display, serif, and sans fonts.
  - Generated visual contact sheet golden at `tests/golden/text_torture_contact_sheet.ppm`.
- **`app/src/test/kotlin/app/absentmotion/pro/TextTortureHarnessTest.kt`**:
  - Automated unit test verifying linear coverage stability, detection of non-linear jumps, camera Z perspective sweep, and font metrics padding calculation.
- **Safe Mode Isolation:**
  - With `ProFeatures.isEnabled(ProFeatures.textRenderFix)` set to `false`, text layout and drawing cleanly fall back to original AbsentMotion 3.3.1 behavior.
