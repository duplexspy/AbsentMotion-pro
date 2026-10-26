# Phase 6 Technical Report: Hardened DaFont Browser, Isolated Font Verification, and Custom Font Manager

## 1. Executive Summary

Phase 6 introduces a hardened in-app DaFont catalog browser, secure archive extraction pipeline, isolated font validation sandbox, and custom font management subsystem for Absent Motion Pro (`feature/phase6-dafont-browser`).

Motion design workflows rely heavily on diverse typography. However, downloading and installing third-party mobile fonts has traditionally required leaving the editing app, downloading untrusted ZIP archives to public device storage, using external file archivers, and dealing with native crashes caused by malformed TrueType/OpenType tables in Android's text rendering engine (FreeType/libhwui).

Phase 6 resolves these pain points with an integrated, defense-in-depth architecture:
1. **Hardened In-App DaFont Browser**: Sandboxed `WebView` dialog restricted to DaFont domains, blocking mixed content, local file access, and third-party cookies, with automatic download interception.
2. **Safe Multi-Font Archive Extraction Pipeline**: Resilient decompression engine enforcing zip-slip traversal protection, strict file extension filtering, entry count limits ($\le 100$), uncompressed size caps ($\le 50\text{ MB}$), and compression ratio bomb defense ($\le 25:1$).
3. **SFNT / OpenType Header & Table Parser**: Pure Kotlin binary table parser validating font magic headers (`0x00010000`, `OTTO`, `ttcf`, `true`), table directory offsets, and reading the `name` table for family, style, copyright, and license terms.
4. **Isolated `:fontcheck` Verification Service**: Out-of-process Android service running in `android:process=":fontcheck"`. If a corrupt or adversarial font triggers a native segmentation fault in FreeType or HarfBuzz during glyph rasterization, only the helper process terminates; the main editor process remains crash-free and gracefully rejects the font.
5. **My Custom Fonts Manager**: Font library management dialog with live sample rendering, font family grouping, license terms inspection, SHA-256 deduplication, and project-dependency-aware font deletion.
6. **Project Missing Font Auto-Resolution**: When opening a project referencing fonts not installed locally, Absent Motion alerts the user and provides a direct "Find on DaFont" button pre-populating search with the missing typeface family.

---

## 2. Threat Model & Security Architecture

### 2.1 Threat Vector Matrix

| Threat Vector | Attack Scenario | Defense Implementation |
|---|---|---|
| **Directory Traversal (Zip-Slip)** | An archive contains entry names such as `../../../../system/bin/malware` or relative paths attempting to overwrite app files. | Canonical path validation rejects any entry with `..`, root prefixes, or resolving outside the target extraction directory. |
| **Zip Bomb (Decompression Bomb)** | A tiny archive (e.g., 50 KB) expands into hundreds of gigabytes, exhausting device flash and RAM. | Strict caps enforced: max 100 entries, max 50 MB total uncompressed bytes, and max 25:1 compression ratio per entry. |
| **Native FreeType/libhwui Crash** | Malformed TrueType/OpenType bytecode or table offsets trigger SIGSEGV or out-of-bounds heap read/write in native libraries. | Initial SFNT table bounds validation in pure Kotlin followed by full font loading and glyph rendering inside an isolated bound helper service (`:fontcheck`). |
| **WebView Hijack & Cross-Site Scripting** | In-app browser navigates to phishing domains or executes unauthorized file access schemes. | URL allowlist restricts navigation exclusively to `dafont.com`, `www.dafont.com`, `dl.dafont.com`, and `img.dafont.com`. `allowFileAccess`, `allowContentAccess`, and third-party cookies are disabled. External links redirect to the system browser. |
| **Licensing Infringement** | Users unwittingly deploy fonts licensed for personal non-commercial use into commercial projects. | Automatic parsing of OpenType name records (copyright and license IDs) and companion readme files (`readme.txt`, `license.txt`) presented prior to font installation. |

---

## 3. Font Parsing & Security Pipeline

### 3.1 SFNT Table Directory Verification

The TrueType and OpenType specifications define an SFNT container beginning with an Offset Table:

$$\text{sfntVersion} \in \{0\text{x}00010000, 0\text{x}4F54544F \text{ ('OTTO')}, 0\text{x}74746366 \text{ ('ttcf')}, 0\text{x}74727565 \text{ ('true')}\}$$

The Kotlin table parser checks:
1. Valid 4-byte SFNT version tag.
2. Number of tables $N_{tables} \in [1, 128]$.
3. For each table record $i \in [0, N_{tables}-1]$:
   - Tag: 4 ASCII characters (e.g., `cmap`, `head`, `hhea`, `maxp`, `name`).
   - Offset: $O_i \ge 12 + 16 \cdot N_{tables}$.
   - Length: $L_i > 0$ and $O_i + L_i \le \text{fileLength}$.
4. Verification of mandatory structural tables (`head`, `hhea`, `maxp`, `cmap`, `name`).

### 3.2 OpenType Name Table Decoding

Font family names, style variants, and copyright notices reside in the `name` table. The parser decodes:
- Format 0 and Format 1 naming tables.
- Name records matching platform IDs 3 (Windows Unicode BMP / UTF-16BE), 1 (Macintosh Roman / Latin-1), and 0 (Unicode).
- Record identifiers:
  - ID 1: Font Family name
  - ID 2: Font Subfamily (Style: Regular, Bold, Italic, etc.)
  - ID 4: Full Font name
  - ID 5: Version string
  - ID 0: Copyright notice
  - ID 13: License description
  - ID 14: License information URL

---

## 4. Multi-Process `:fontcheck` Service

### 4.1 Architecture & IPC Protocol

The font verification service runs in an isolated Android process declared in `AndroidManifest.xml`:

```xml
<service
    android:name="app.absentmotion.pro.FontCheckService"
    android:process=":fontcheck"
    android:exported="false" />
```

Communication proceeds via Android `Messenger` and `Bundle`:
1. Client binds to `FontCheckService` using `Context.BIND_AUTO_CREATE`.
2. Client sends `MSG_CHECK_FONT` containing `KEY_FILE_PATH`.
3. In `:fontcheck`:
   - Inspects SFNT binary table bounds.
   - Instantiates `Typeface.Builder(file).build()`.
   - Queries `Paint.fontMetrics` to check for non-zero, finite ascenders and descenders.
   - Allocates an offscreen $64 \times 64$ bitmap and executes a sample text draw call.
   - Returns `MSG_RESULT` with `KEY_SUCCESS = true` and parsed metadata.
4. If the process crashes natively during step 3, `onServiceDisconnected` fires in the main process, flagging the font as corrupt and preserving editor stability.

---

## 5. User Experience & Custom Font Management

### 5.1 DaFont Browser Dialog
- Embedded hardened web browser loading `dafont.com` with top search bar, back/forward/reload buttons, and system browser intent shortcut.
- Intercepts file downloads matching zip or font extensions.
- Downloads to app cache and streams directly to `safeExtractZip`.
- Presents interactive font installation sheet displaying font family, style, version, copyright/license metadata, and live preview rendering of custom sample text.

### 5.2 My Custom Fonts Manager
- Full management dialog displaying all user-imported fonts with live editable sample rendering.
- Search filter for filtering through extensive typeface collections.
- "License & Readme" dialog inspecting author notes, donation URLs, and license restrictions.
- Deletion safety check warning the user if the font is currently in use within active project text layers.

### 5.3 Missing Font Resolution
- On project load, `findMissingFonts(scene, library)` scans text layers for unavailable typeface families.
- Prompts user with "Missing Fonts Found" dialog featuring a "Find on DaFont" button that launches the DaFont browser pre-searched with the font family name.

---

## 6. Verification & Quality Assurance

### 6.1 Unit Test Results (`FontSecurityAndImportTest`)

The unit test suite validates all security boundaries and fallback behaviors:

| Test Case | Description | Result |
|---|---|---|
| `testZipSlipPathTraversalRejection` | Verifies rejection of relative `../evil.ttf` zip entries | PASSED |
| `testZipEntryCountLimitRejection` | Verifies rejection when archive entry count $> 100$ | PASSED |
| `testZipBombRatioRejection` | Verifies rejection when compression ratio exceeds $25:1$ | PASSED |
| `testSafeExtractFileFilter` | Verifies extraction of `.ttf` and `.txt`, while discarding `.exe` | PASSED |
| `testDafontUrlAllowlist` | Validates domain matching on `dafont.com`, `dl.dafont.com`, and blocks external domains | PASSED |
| `testCleanFontFileName` | Validates sanitization of whitespace and special characters in filenames | PASSED |
| `testValidFontHeaderInspection` | Validates SFNT parser against real asset `AbrilFatface-Regular.ttf` | PASSED |
| `testCorruptFontHeaderInspection` | Verifies rejection of truncated or malformed font headers | PASSED |
| `testSafeModeFontManagerFallback` | Verifies graceful fallback and zero imported fonts in Safe Mode | PASSED |

### 6.2 Regression & Zero-Comment Status
- Regression test suite: 10/10 tests passed (`python3 tools/bug_regression_check.py`).
- Comment check: 0 comments across all tracked repository files (`python3 tools/comment_check.py`).
- Build output: Debug APK `app/build/outputs/apk/pro/debug/app-pro-debug.apk` built successfully ($95\text{ MB}$).
