# Absent Motion Pro — UI Design System Specification

## 1. Overview & Principles

The Absent Motion Pro UI Design System (`docs/UI_DESIGN_SYSTEM.md`) establishes a cohesive visual language and interaction framework for high-precision mobile motion graphics editing.

### Core Principles
1. **Content First**: Editing canvases and user compositions remain the visual heroes. Surfaces, panels, and chrome stay unobtrusive, deep, and matte to avoid visual glare and color distortion during color grading.
2. **Ergonomic Touch Targets**: In compliance with mobile accessibility standards, all interactive controls, keyframe targets, and selection handles provide a minimum $48\text{ dp} \times 48\text{ dp}$ touch hit zone, regardless of visual glyph size.
3. **Contrast Integrity**: All text and icon combinations achieve a minimum WCAG AA contrast ratio of $\ge 4.5:1$ against their respective surface backgrounds, with primary text exceeding $14:1$.
4. **Predictable Motion Physics**: UI state transitions maintain responsive durations ($150\text{--}250\text{ ms}$) with natural spring curves for bottom sheets and gestures.
5. **Fluid Appearance Switch**: Users can toggle between **Classic** and **Premium** visual styles in Settings with instant preview and zero workflow disruption. Safe Mode automatically defaults to Classic style for guaranteed stability.

---

## 2. Design Tokens

### 2.1 Surface Hierarchy & Dark-First Palette

| Token | Hex Value | Role |
|---|---|---|
| `Surface0` | `#0E1015` | Deepest root viewport, canvas surrounding well, and background |
| `Surface1` | `#161820` | Base panels, bottom sheets, toolbars, and primary container backgrounds |
| `Surface2` | `#1F222C` | Elevated cards, dialog surfaces, inset wells, and menu backgrounds |
| `Surface3` | `#2A2E3B` | Interactive components, active hover/focus fields, and chips |

### 2.2 Text & Iconography Tiers

| Token | Hex Value | Minimum Contrast | Role |
|---|---|---|---|
| `TextPrimary` | `#F6F7FA` | $\ge 14:1$ | High-emphasis headings, primary labels, active values |
| `TextSecondary` | `#A6ADB9` | $\ge 5.8:1$ | Medium-emphasis captions, secondary details, timestamps |
| `TextTertiary` | `#6F7788` | $\ge 3.0:1$ | Low-emphasis placeholders, subtle borders, inactive states |

### 2.3 Brand Accent & Functional Semantics

| Token | Hex Value | Role |
|---|---|---|
| `AccentPrimary` | `#FF6A3D` | Primary interactive accent, selected tools, playhead head |
| `AccentLit` | `#FF8E6B` | Hover/pressed highlight on accent elements |
| `AccentGlow` | `#FFA688` | Keyframe selection glow, focus rings |
| `AccentDeep` | `#4D1B0A` | Accent tinted container background |
| `OnAccent` | `#0E1015` | Text and icons rendered atop `AccentPrimary` ($\ge 6.5:1$ contrast) |
| `Cool` | `#4F9BFF` | Informational badges, secondary selections |
| `Warning` | `#FF5C6A` | Destructive actions, delete warnings, clipping warnings |
| `LayerBar` | `#3EB89B` | Visual layer clip track fill |
| `TextBar` | `#F0B843` | Typography layer clip track fill |
| `AudioBar` | `#7C66DC` | Audio waveform layer clip track fill |

---

## 3. Spacing, Radii & Grid System

### 3.1 4/8 dp Grid Spacing Tokens

| Token | Value | Standard Usage |
|---|---|---|
| `Space2` | $2\text{ dp}$ | Sub-element padding, stroke offsets |
| `Space4` | $4\text{ dp}$ | Micro-spacers between related icon/text pairs |
| `Space8` | $8\text{ dp}$ | Standard inner-card padding, grid gaps |
| `Space12` | $12\text{ dp}$ | Outer component margins, list item separation |
| `Space16` | $16\text{ dp}$ | Screen edge padding, sheet horizontal padding |
| `Space20` | $20\text{ dp}$ | Section header spacing |
| `Space24` | $24\text{ dp}$ | Dialog body padding, major block separation |
| `Space32` | $32\text{ dp}$ | Large section breaks |
| `Space48` | $48\text{ dp}$ | Standard touch boundary dimension |

### 3.2 Corner Radii

| Token | Value | Application |
|---|---|---|
| `RadiusXs` | $4\text{ dp}$ | Badges, micro-indicators, timeline grips |
| `RadiusSmall` | $8\text{ dp}$ | Thumbnails, chip buttons, inner containers |
| `RadiusMedium` | $12\text{ dp}$ | Project cards, text input fields, bottom panels |
| `RadiusLarge` | $16\text{ dp}$ | Dialogs, bottom sheet top corners |
| `RadiusPill` | $999\text{ dp}$ | Drag handles, circular icon buttons, scrubbers |

---

## 4. Component Refinements

### 4.1 Bottom Sheets
- **Drag Handle**: Centered pill handle ($36\text{ dp} \times 4\text{ dp}$) positioned $10\text{ dp}$ from the sheet top with subtle high-contrast fill (`Color(0x55FFFFFF)`).
- **Corner Radii**: Top-start and top-end corners rounded to $16\text{ dp}$ (`RadiusLarge`).
- **Physics**: Modal presentation powered by damped spring animation (damping ratio $0.82$, stiffness $380$).

### 4.2 Keyframe Diamonds
- **Geometry**: Rhombus with crisp anti-aliasing rendered at exact sub-frame timestamp locations.
- **Selection Glow**: Active keyframe rendered with high-contrast accent ring (`Am.AccentGlow`, $3.5\text{ dp}$ stroke) and inner contrast fill.
- **Unselected Keyframes**: Semi-transparent white ($70\%$ opacity) ensuring legibility across both dark and light layer bars.

### 4.3 Timeline Clips
- **Corner Curvature**: $6\text{ dp}$ outer corner radius in Premium mode for distinct layer card boundaries.
- **Color Differentiation**: Refined muted tones (`#3EB89B` for standard layers, `#F0B843` for text, `#7C66DC` for audio).
- **Trim Grips**: High-contrast grip handles with directional chevron indicators and minimum $48\text{ dp}$ grab targets.

### 4.4 Selection Handles
- **Visual Footprint**: $12\text{ dp}$ diameter solid circle with $1.5\text{ dp}$ dark contrast border.
- **Hit Detection Zone**: Outer circular hitbox expanded to $24\text{ dp}$ radius ($48\text{ dp}$ diameter), preventing missed touches during fast scrubbing.

### 4.5 Empty States & Skeleton Loaders
- **Empty States**: Centered illustration icon with bold title, secondary explanatory prompt, and subtle animated pulse.
- **Skeleton Shimmer**: Linear gradient shimmer cycling across $1000\text{ ms}$ between `Surface1` and `Surface2` during IO fetch operations.

---

## 5. Classic vs. Premium Appearance Modes

- **Classic Mode**: Preserves original AbsentMotion 3.3.1 palette (`#22242A` slate-gray tone, $4\text{ dp}$ clip radii, square drag headers).
- **Premium Mode**: Applies the refined dark-first palette (`#0E1015` background, `#161820` panels, $6\text{ dp}$ clip radii, pill drag handles, enhanced keyframe diamonds).
- **Safe Mode Behavior**: If startup crashes trigger Safe Mode, the appearance automatically forces Classic mode with zero overhead.
