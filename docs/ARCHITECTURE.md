# Absent Motion Pro — Architecture & Module Guide

## 1. System Overview

Absent Motion Pro is a high-performance, keyframe-based 2D/3D motion graphics editor for Android. It combines a modern declarative Jetpack Compose UI with a native multi-threaded Skia C++ rendering and audio DSP engine.

```
+------------------------------------------------------------------+
|                       Jetpack Compose UI                         |
|  HomeScreen | EditorScreen | EffectBrowser | VectorTool | Theme  |
+------------------------------------------------------------------+
                                |
                                v
+------------------------------------------------------------------+
|                   Editor & State Management                      |
|      EditorState | Layer / Scene Models | ProFeatures Guard      |
+------------------------------------------------------------------+
                                |
        +-----------------------+-----------------------+
        |                                               |
        v                                               v
+-------------------------------+   +------------------------------+
|     Skia 2D/3D Engine         |   |    Video & Media Pipeline    |
| EngineThread | JNI Native Core|   | MediaCodec | VideoExport     |
| SkCanvas | SkRuntimeEffect    |   | FFmpeg / Fallback Encoder    |
+-------------------------------+   +------------------------------+
```

---

## 2. Module & Package Map

### `app.absentmotion` (Root)
* `MainActivity.kt`: Central activity coordinating lifecycle, Compose root, edge-to-edge window insets, crash recovery, and feature initialization.
* `ImportActivity.kt`: System intent receiver for XML, zip, font, and pack file imports.
* `Tier.kt`: Feature tier capability flags.

### `app.absentmotion.model`
* `Scene.kt`: Root composition container (dimensions, fps, background color, layer hierarchy, markers).
* `Layer.kt`: Layer abstract base class and concrete layers (ShapeLayer, TextLayer, MediaLayer, GroupLayer, CameraLayer, NullLayer).
* `Keyframe.kt` / `KeyframeWindow.kt`: Parameter keyframing, Bezier curve evaluation, and interpolation.
* `XmlNode.kt`: Serialization and deserialization format for project files and presets.

### `app.absentmotion.engine`
* `EngineThread.kt`: Background rendering thread driving the Skia graphics pipeline, project evaluation at timestamp $t$, transform hierarchy resolution, and buffer presentation.
* `Renderer.kt`: Low-level drawing commands, mesh distortion, blur/shadow passes, and clipping.

### `app.absentmotion.export`
* `VideoExport.kt`: Video rendering loop, MediaCodec surface configuration, audio track multiplexing, and hardware encoder management.
* `ExportChoices.kt`: User export configurations (resolution, framerate, bitrate, codec, format).
* `MediaStoreExport.kt`: Android MediaStore output provider and scoped storage handling.

### `app.absentmotion.effects` & `app.absentmotion.effectspack`
* `EffectCatalog.kt`: Built-in effect definitions, parameters, and categories.
* `PackCatalog.kt`: Custom effects pack store, manifest indexing, and pack activation.
* `PackParser.kt`: XML and manifest parser for Gold Motion and custom effect definitions.
* `GlslTranslator.kt`: Real-time GLSL-to-SkSL compiler converting fragment shaders into Skia Runtime Effects.
* `PackThumbnailGenerator.kt`: Offline thumbnail generation for custom packs.

### `app.absentmotion.pro`
* `ProFeatures.kt`: Central flag controller for Pro additions (crash-loop guard, Safe Mode fallback).
* `EffectsPackStudio.kt`: In-app IDE for developing and live-testing custom fragment shaders.
* `StaggerTool.kt`: Multi-layer staggered animation sequencer.
* `ProjectPalette.kt`: Project color swatch manager.
* `KeyframeTools.kt`: Keyframe mirror, reverse, and scale utilities.
* `SocialPresets.kt`: Social aspect ratio templates.

---

## 3. Graphics & Rendering Architecture

1. **Composition Evaluation:**
   * At any timeline position $t$, `Scene` queries each visible `Layer`.
   * Keyframed properties (position, scale, rotation, opacity, effect uniforms) are interpolated using cubic Bezier curves.
2. **Transform Matrix Chain:**
   * Parent-child transforms are resolved top-down through 4x4 coordinate matrices.
   * Camera 3D layers project layers onto the 2D viewport with perspective division.
3. **Layer Rasterization & Effects:**
   * Text and vector layers are converted into Skia paths and paint commands.
   * Effects are evaluated via `SkRuntimeEffect` shaders or multi-pass offscreen surfaces.
   * Group layers isolate children or composite them directly to parent targets.
4. **Buffer Presentation:**
   * In preview mode, frames are blitted to an Android `SurfaceView` / Vulkan swapchain.
   * In export mode, frames are rendered directly into the input surface of an `AMediaCodec` video encoder.

---

## 4. Key Constraints & Policies

* **Zero-Comment Policy:** Tracked source code must contain zero inline comments, strictly enforced by `tools/comment_check.py`.
* **Crash-Loop Guard:** If two consecutive startup crashes occur within 10 seconds of launch, the app automatically boots into Safe Mode, disabling experimental features until dismissed by the user.
* **Fail-Soft Media Handling:** Missing fonts, corrupted media files, or unsupported shader uniforms gracefully fall back to default rendering rather than crashing.
* **Strict Offline Operation:** No network calls or analytics anywhere in the app, with the sole exception of user-initiated DaFont browsing (Phase 6) on allowlisted hosts.
