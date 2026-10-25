# Absent Motion Pro — Regression Checklist & Verification Plan

This checklist serves as the release gate for all phases. Every item must be verified before any phase branch is considered complete.

---

## 1. Project & Data Persistence
- [ ] Existing `.amp` and `.xml` projects load without loss or corruption.
- [ ] Newly added optional fields (e.g. dash patterns, pre-render hashes) are skipped cleanly by older parsers.
- [ ] Unknown XML attributes survive load and save cycles without data loss.
- [ ] Automatic recovery creates valid project snapshots after abrupt process termination.
- [ ] Safe Mode activates automatically after 2 startup crashes and restores stability without corrupting project databases.

---

## 2. Rendering & Graphics Engine
- [ ] Canvas renders at native target resolution without unintended padding or border artifacts.
- [ ] 2D layers correctly inherit hierarchical transformations (position, scale, rotation, anchor point).
- [ ] 3D camera layers project 3D space with correct perspective division and depth sorting.
- [ ] Clipping masks and stencil buffers isolate target layers accurately.
- [ ] Blend modes (Multiply, Screen, Overlay, Add, Color Dodge/Burn) match expected Skia blend behaviors.
- [ ] Golden renders of standard test scenes match reference frames with zero pixel drift outside updated features.

---

## 3. Text & Typography
- [ ] Standard fonts (Sans, Serif, Monospace, Script, Display) render crisply at $1.0\times$ scale.
- [ ] Overhang glyphs (italics, calligraphy swashes, diacritics) are not clipped at bounding box boundaries.
- [ ] Font scaling ($0.25\times$ to $8.0\times$) and Z-axis depth changes maintain smooth, non-jumping stem widths.
- [ ] Grouped text layers maintain sharpness matching ungrouped text within 5% edge variance.
- [ ] Missing fonts fall back gracefully to default system typefaces with user-facing alerts.

---

## 4. Effects & Shaders
- [ ] Built-in CPU/GPU effects render consistently across all supported color formats.
- [ ] Gold Motion custom shaders compile to SkSL without GPU pipeline compilation errors.
- [ ] Imported `.effectspack` and single `.xml` effects load parameters into the inspector.
- [ ] Keyframed effect parameters animate smoothly across keyframe windows.
- [ ] Effect browser displays thumbnails smoothly without UI thread stutters or out-of-memory errors.

---

## 5. Video & Media Export
- [ ] Full-frame opaque renders export without 1px/2px black lines at bottom, left, or right edges.
- [ ] Video encoder respects codec dimension constraints (multiples of 16 / multiples of 2) via aspect scaling rather than black border padding.
- [ ] Hardware `MediaCodec` exports complete within timing budgets.
- [ ] Hardware-to-software fallback chain triggers smoothly when hardware encoder resources are exhausted.
- [ ] All supported resolutions (720p, 1080p, 1440p, 4K) and framerates (24, 30, 60, 120 fps) produce playable MP4 files with valid timestamps.
- [ ] Animated GIF and transparent PNG sequence exports maintain alpha transparency.

---

## 6. Stability & Performance Budgets
- [ ] App cold start finishes within target budget (< 1200 ms on mid-range, < 2000 ms on low-end).
- [ ] Effects panel with 500+ effects opens in < 300 ms and scrolls at $\ge 55$ FPS.
- [ ] Continuous timeline playback runs with flat memory usage (no unbounded object leaks).
- [ ] 10-minute continuous render loop produces no native memory accumulation.
- [ ] Strict zero-comment policy is maintained across all tracked source files (`tools/comment_check.py`).
