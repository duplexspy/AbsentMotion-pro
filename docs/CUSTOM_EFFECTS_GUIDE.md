# Absent Motion Pro - Official Custom Effects & EffectPack Manual

Version: 2.0.0 (Official Absent Motion SkSL Engine)  
Target Engine: Absent Motion Skia / Vulkan GPU SkSL Pipeline

---

## 1. Overview & Architecture

Absent Motion Pro utilizes **SkSL (Skia Shading Language)** as its native, official shader pipeline. SkSL is compiled directly to Vulkan SPIR-V, Metal MSL, and OpenGL ES fragment shaders at zero runtime translation overhead.

Unlike legacy third-party shaders (such as Gold Motion or Alight Motion GLSL wrappers that used normalized inverted coordinates and custom sampler macros), the **Official Absent Motion Shader Type** operates directly on native SkSL syntax:
- Standard coordinate space: `coord` is passed directly in layer pixels `[0, size]`.
- Direct shader evaluation: `image.eval(coord)`.
- Native high-performance floating-point and vector operations (`half4`, `float2`, `float3x3`).
- Seamless keyframing, timeline caching, and real-time GPU preview.

---

## 2. Official SkSL Shader Specification

### 2.1 The Official Entry Point

Every official Absent Motion effect uses `half4 main(float2 coord)`:

```sksl
uniform shader image;
uniform float2 size;
uniform float time;

// Custom effect parameter uniforms:
uniform float intensity;

half4 main(float2 coord) {
    // 1. Sample original layer color at pixel coordinate:
    half4 color = image.eval(coord);

    // 2. Perform custom shader math:
    half3 inverted = half3(1.0) - color.rgb;

    // 3. Output premultiplied half4:
    return half4(mix(color.rgb, inverted, half(intensity)), color.a);
}
```

### 2.2 Built-in Automatic Uniforms

When rendering your effect, the Absent Motion engine automatically provides the following uniforms:

| Uniform | Type | Description |
|---|---|---|
| `image` | `shader` | The input texture/layer content. Evaluated via `image.eval(float2 pixelCoord)`. |
| `size` | `float2` | Layer dimensions in pixels `(width, height)`. |
| `time` | `float` | Current timeline position in seconds (supports frame-accurate scrubbing). |

To compute normalized UV coordinates `[0.0, 1.0]`, simply divide `coord` by `size`:
```sksl
float2 uv = coord / size;
```

---

## 3. Parameter Types & Uniform Bindings

Parameters defined in your effect XML are automatically mapped to shader uniforms and UI controls in the Absent Motion Inspector:

### 3.1 Float Parameter (`float`)
Used for intensities, angles, radii, and speeds.
```xml
<param id="radius" name="Blur Radius" type="float" default="10.0" min="0.0" max="100.0" step="0.5"/>
```
- In SkSL:
  ```sksl
  uniform float radius;
  ```

### 3.2 Color Parameter (`color`)
Used for tinting, color wheels, and lighting.
```xml
<param id="tintColor" name="Tint Color" type="color" default="#ff00ff80"/>
```
- In SkSL:
  ```sksl
  layout(color) uniform float4 tintColor;
  ```

### 3.3 Switch Parameter (`switch`)
Used for toggling effect modes (e.g., Invert Alpha, Clamp Edges).
```xml
<param id="clampEdges" name="Clamp Edges" type="switch" default="true"/>
```
- In SkSL:
  ```sksl
  uniform int clampEdges; // 1 for true, 0 for false
  ```

### 3.4 Selector Choice (`choice`)
Used for dropdown selection menus.
```xml
<selector id="blendMode" label="Blend Mode" default="0">
  <choice value="0" label="Normal"/>
  <choice value="1" label="Multiply"/>
  <choice value="2" label="Screen"/>
</selector>
```
- In SkSL:
  ```sksl
  uniform int blendMode;
  ```

---

## 4. Single Effect File Format (`.xml` / `.ameffect`)

A custom effect is written in structured XML with the `<sksl>` block containing the official shader code.

### Complete Example 1: Chromatic Aberration (`chromatic_aberration.xml`)

```xml
<effect id="absent.custom.chromatic_aberration" 
        name="Chromatic Aberration" 
        desc="Radial RGB channel split distortion" 
        group="Distortion and Warp" 
        category="custom">
  <params>
    <param id="offset" name="RGB Split Shift" type="float" default="8.0" min="0.0" max="50.0" step="0.5"/>
    <param id="angle" name="Split Direction" type="float" default="0.0" min="0.0" max="360.0" step="1.0"/>
  </params>
  <sksl><![CDATA[
uniform shader image;
uniform float2 size;
uniform float time;
uniform float offset;
uniform float angle;

half4 main(float2 coord) {
    float rad = radians(angle);
    float2 dir = float2(cos(rad), sin(rad)) * offset;

    half r = image.eval(coord + dir).r;
    half g = image.eval(coord).g;
    half b = image.eval(coord - dir).b;
    half a = image.eval(coord).a;

    return half4(r, g, b, a);
}
  ]]></sksl>
</effect>
```

### Complete Example 2: Duotone Color Grade (`duotone.xml`)

```xml
<effect id="absent.custom.duotone" 
        name="Cinematic Duotone" 
        desc="Maps shadows and highlights to custom duotone colors" 
        group="Color and Grade" 
        category="custom">
  <params>
    <param id="shadowColor" name="Shadows" type="color" default="#ff1a0033"/>
    <param id="highlightColor" name="Highlights" type="color" default="#ffffdd00"/>
    <param id="intensity" name="Mix Amount" type="float" default="1.0" min="0.0" max="1.0" step="0.05"/>
  </params>
  <sksl><![CDATA[
uniform shader image;
uniform float2 size;
uniform float time;
layout(color) uniform float4 shadowColor;
layout(color) uniform float4 highlightColor;
uniform float intensity;

half4 main(float2 coord) {
    half4 orig = image.eval(coord);
    if (orig.a <= 0.0) return orig;

    // Relative luminance (Rec. 709)
    float lum = dot(float3(orig.rgb), float3(0.2126, 0.7152, 0.0722));
    float3 duo = mix(shadowColor.rgb, highlightColor.rgb, lum);

    float3 finalRgb = mix(float3(orig.rgb), duo, intensity);
    return half4(half3(finalRgb) * orig.a, orig.a);
}
  ]]></sksl>
</effect>
```

---

## 5. EffectPack Package Format (`.effectspack`)

An `.effectspack` bundle is a standard ZIP archive containing multiple custom effects with metadata and optional preview thumbnails.

### Directory Structure

```
MyEffectsPack.effectspack (ZIP)
├── manifest.json              [Required: Pack identifier and index]
├── effects/                   [Required: Directory of XML effect files]
│   ├── chromatic_aberration.xml
│   ├── duotone.xml
│   └── ...
└── thumbs/                    [Optional: 256x256 PNG or WebP thumbnails]
    ├── chromatic_aberration.png
    ├── duotone.png
    └── ...
```

### `manifest.json` Specification

```json
{
  "formatVersion": 1,
  "id": "absent.pack.cinematic_fx",
  "name": "Cinematic Pro FX Pack",
  "version": "1.0.0",
  "author": "Absent Motion Community",
  "description": "Professional color grades, glitches, and optical lens distortions.",
  "effects": [
    {
      "id": "absent.custom.chromatic_aberration",
      "file": "effects/chromatic_aberration.xml",
      "name": "Chromatic Aberration",
      "group": "Distortion and Warp",
      "tags": ["rgb", "glitch", "lens", "aberration"],
      "thumb": "thumbs/chromatic_aberration.png"
    },
    {
      "id": "absent.custom.duotone",
      "file": "effects/duotone.xml",
      "name": "Cinematic Duotone",
      "group": "Color and Grade",
      "tags": ["duotone", "grade", "palette"],
      "thumb": "thumbs/duotone.png"
    }
  ]
}
```

### Packaging via Command Line

To create an `.effectspack` file from your terminal:
```bash
cd MyPackFolder
zip -r -X ../MyEffectsPack.effectspack manifest.json effects/ thumbs/
```

---

## 6. Using the In-App "Effects Pack Studio"

Absent Motion Pro features a built-in shader workstation for creating and installing effects directly on your device:

1. Open **Settings** (or tap **Effect Browser -> Studio**).
2. Tap **Effects Pack Studio**.
3. Choose your effect name and category group (e.g. `Color and Grade`, `Blur and Focus`).
4. Write or edit your official SkSL shader code directly in the code editor.
5. Tap **Validate** to verify syntax and ensure `half4 main(float2 coord)` compiles without errors.
6. Tap **Install** to immediately register the effect in your project's **Custom Effects** category.

---

## 7. Migration Guide: Gold Motion GLSL to Absent Motion SkSL

For creators migrating existing Gold Motion or legacy packs to Absent Motion SkSL:

| Legacy Gold Motion GLSL | Official Absent Motion SkSL | Explanation |
|---|---|---|
| `acScreenNorm` (inverted 0..1 UV) | `coord / size` | Absent Motion uses standard non-inverted coordinates. |
| `texture2DCv(inputImg.texture, uv)` | `image.eval(coord)` | Direct sampling using pixel coordinates. |
| `gl_FragColor = col;` | `return col;` | SkSL functions return `half4` directly from `main()`. |
| `acScreenSize` | `size` | Native built-in `float2` size uniform. |
| `acTime` | `time` | Native built-in `float` time uniform in seconds. |
| `vec2`, `vec3`, `vec4` | `float2`, `float3`, `float4` (or `half4`) | Standard SkSL types. |
| `mat2`, `mat3`, `mat4` | `float2x2`, `float3x3`, `float4x4` | SkSL matrix notation. |

> [!TIP]
> The Absent Motion engine includes a smart compatibility translator: if you import a legacy Gold Motion pack, it automatically converts it into native SkSL at load time!
