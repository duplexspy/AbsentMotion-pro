# Absent Motion Pro - Effects Pack (.effectspack) Specification

Version: 1.0.0
Status: Stable / Production

## 1. Overview

An `.effectspack` file is a self-contained ZIP bundle that packages custom fragment shader effects for **Absent Motion Pro** while preserving 100% interoperability with Gold Motion and Alight Motion custom effect XML schemas.

Pack files allow motion designers to share, distribute, and backup collections of shaders with pre-rendered thumbnails and structured category metadata.

---

## 2. Archive Structure

An `.effectspack` archive is a standard unencrypted ZIP archive containing:

```
my_effects.effectspack (ZIP)
├── manifest.json              [Required: Pack metadata & effect index]
├── effects/                   [Required: Directory of effect XML files]
│   ├── cinematic_punch.xml
│   ├── radial_rush.xml
│   └── ...
└── thumbs/                    [Optional: Pre-rendered 256x256 WebP or PNG thumbnails]
    ├── cinematic_punch.webp
    ├── radial_rush.webp
    └── ...
```

---

## 3. Manifest Specification (`manifest.json`)

The manifest must reside at the root of the archive and be valid JSON formatted in UTF-8.

### Schema:

```json
{
  "formatVersion": 1,
  "id": "com.example.mypack",
  "name": "My Custom Effects",
  "version": "1.0.0",
  "author": "Designer Name",
  "description": "High-impact cinematic color grades and distortions.",
  "effects": [
    {
      "id": "gm.custom.cinematic_punch",
      "file": "effects/cinematic_punch.xml",
      "name": "Cinematic Punch Pro",
      "group": "Color and Grade",
      "tags": ["color", "pro", "grade", "contrast"],
      "thumb": "thumbs/cinematic_punch.webp"
    }
  ]
}
```

### Fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `formatVersion` | Integer | Yes | Format version (must be `1`). |
| `id` | String | Yes | Unique reverse-DNS identifier (e.g. `goldmotion.customfx`). |
| `name` | String | Yes | Human-readable title displayed in the Pack Manager. |
| `version` | String | Yes | Semantic version string (e.g. `1.0.0`). |
| `author` | String | Yes | Name or organization of the author. |
| `description` | String | No | Short description of the pack contents. |
| `effects` | Array | Yes | Ordered array of effect descriptor objects. |

### Effect Entry Fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | Unique effect identifier starting with `gm.custom.`. |
| `file` | String | Yes | Relative path to XML file within the archive (`effects/<name>.xml`). |
| `name` | String | Yes | Display name of the effect. |
| `group` | String | Yes | Sub-category grouping (e.g. `Blur and Focus`, `Color and Grade`). |
| `tags` | Array<String> | No | Searchable keywords and tags. |
| `thumb` | String | No | Relative path to thumbnail image within the archive. |

---

## 4. Effect XML Schema

Individual effect files inside `effects/` use the standard Gold Motion XML schema:

```xml
<effect id="gm.custom.duotone" name="Duotone Mapper" desc="Maps shadows and highlights to two colors" group="Color and Grade" category="custom">
  <params>
    <section title="Color Controls"/>
    <param id="shadowColor" name="Shadows" type="color" default="#ff002244"/>
    <param id="highlightColor" name="Highlights" type="color" default="#ffffddaa"/>
    <param id="intensity" name="Intensity" type="float" default="1.0" min="0.0" max="1.0" step="0.01"/>
  </params>
  <glsl><![CDATA[
    void main() {
        vec2 uv = acScreenNorm;
        vec4 src = texture2DCv(inputImg.texture, uv);
        float lum = dot(src.rgb, vec3(0.299, 0.587, 0.114));
        vec3 mapped = mix(shadowColor.rgb, highlightColor.rgb, lum);
        vec3 finalCol = mix(src.rgb, mapped, intensity);
        gl_FragColor = vec4(finalCol, src.a);
    }
  ]]></glsl>
</effect>
```

### Supported Parameter Types:

- `float`: Continuous floating-point scalar (`min`, `max`, `step`).
- `angle`: Angular rotation in degrees or radians.
- `point`: 2D coordinate vector (`vec2`).
- `color`: ARGB / RGBA hex color code (e.g. `#ffff0000`).
- `switch`: Boolean toggle (`true` / `false`).
- `choice`: Discrete choice index.

---

## 5. GLSL to SkSL Runtime Translation

Absent Motion Pro executes fragment shaders via Skia's SkSL runtime pipeline (`SkRuntimeEffect`). GLSL ES shaders are translated on the fly with the following contract:

1. **Host Uniform Mapping:**
   - `acScreenNorm` maps to `float2(coord.x / size.x, 1.0 - coord.y / size.y)`.
   - `acScreenSize` maps to `size`.
   - `acTime` maps to `time`.
2. **Texture Sampling:**
   - `texture2DCv(inputImg.texture, uv)` maps to helper `gmSample(uv)` which evaluates `image.eval(float2(clamped.x, 1.0 - clamped.y) * size)`.
3. **Color Uniforms:**
   - Parameter uniforms of type `color` declare `layout(color) uniform float4 <paramId>;` to preserve wide-gamut and color space conversion.
4. **Boolean Uniforms:**
   - Switch parameters declare `uniform int <paramId>_u;` and are wrapped in the shader body with `bool <paramId> = (<paramId>_u == 1);`.
5. **Reserved Word Collisions:**
   - Parameters whose names collide with SkSL automatic uniforms (`color`, `size`) are safely remapped to `<paramId>_p`.

---

## 6. Security & Sandboxing Constraints

To protect devices from malicious zip archives, every imported pack is checked before extraction:

- **Path Traversal Prevention:** Any zip entry containing `..`, absolute paths `/`, or backslashes `\` immediately aborts extraction with a `PackSecurityException`.
- **Archive Entry Count:** Maximum 500 entries per archive.
- **Entry Size Limits:**
  - XML files: Maximum 2 MB per entry.
  - Image files: Maximum 5 MB per entry.
  - Archive Uncompressed Total: Maximum 100 MB.
- **Safe Directory Quarantine:** Archives are decompressed into a temporary sandbox in cache storage and verified before being committed to persistent storage.
