# 🔢 Hex Editor

An official [Nuclr Commander](https://nuclr.dev) plugin providing a fullscreen hexadecimal viewer and editor. Opens any binary or text file in a dual-pane view (hex bytes + ASCII side by side) or a pure hex matrix. Powered by the [ExBin Bined](https://bined.exbin.org/) library.

This plugin ships **two roles**:

| Role | Shortcut | Description |
|---|---|---|
| 🔢 **Hex Editor** | `F4` | Read/write mode — overwrite and insert editing supported |
| 👁️ **Hex Viewer** | `F3` | Read-only mode — safe inspection without accidental edits |

## ✨ What It Does

| Feature | Details |
|---|---|
| 🔢 Dual view | Hex bytes and ASCII representation side by side |
| 🔲 Matrix mode | Pure hex grid with no ASCII column (toggle with `F2`) |
| ✏️ Overwrite mode | Edit hex bytes in-place (default edit mode) |
| ➕ Insert mode | Insert new bytes at the cursor position (`F6` to toggle) |
| 💾 Save | Write changes back to the original file |
| 🪜 Striped rows | Even/odd row highlighting for easier scanning |
| ↔️ Fixed row width | 16 bytes per row |
| 🎨 Theme-aware | Background, foreground, and selection colors follow the Nuclr Commander theme |

## ⌨️ Keyboard Shortcuts

| Key | Action |
|---|---|
| `F2` | Toggle dual view / hex matrix mode |
| `F3` / `Escape` | Close viewer / editor |
| `F4` | Save (editor only) |
| `F6` | Toggle overwrite / insert mode (editor only) |
| `Ctrl+S` | Save (editor only) |

## 📥 Installation

Copy the signed plugin archive and detached signature into the Nuclr Commander `plugins/` directory:

```text
screenpanel-hex-editor-<version>.zip
screenpanel-hex-editor-<version>.zip.sig
```

Nuclr Commander verifies the RSA-SHA256 signature against `nuclr-cert.pem` on load. The plugin becomes available immediately without a restart.

## ⚙️ How it works

`EditPlugin` implements `FullscreenNuclrPlugin` (plus `NuclrEventListener`) in the editor role. It loads the entire file into a `ByteArrayEditableData` buffer in memory and binds a Bined `CodeArea` to it. `ViewPlugin` extends `EditPlugin`, overrides `isEditable()` to return `false`, and reports the viewer role instead — so the two share all rendering and navigation code. Theme colors are applied via `NuclrThemeScheme` on every `updateTheme` call. Save operations write the buffer to the file via `contentData.saveToStream(out)`.

Because the file is held entirely in memory, this is best suited to files that comfortably fit in the heap.

## 🗂️ Source Layout

```text
src/main/java/dev/nuclr/plugin/core/hex/editor/
├── EditPlugin.java     hex editor entry point (editor role) — UI, key bindings, save
└── ViewPlugin.java     hex viewer entry point (viewer role) — read-only subclass
```

## 📚 Dependencies

| Library | Version | Purpose |
|---|---|---|
| `dev.nuclr:platform-sdk` | `3.0.2` | Nuclr platform interfaces |
| `bined-swing` | `0.2.2` | Core Bined hex editor Swing component |
| `bined-highlight-swing` | `0.2.2` | Syntax/highlight layer for Bined |
| `bined-swing-section` | `0.2.2` | Section-based rendering for Bined |
| `bined-operation-swing` | `0.2.2` | Edit operations for Bined |
| `binary_data` | `0.2.2` | ExBin binary data abstraction |
| `binary_data-array` | `0.2.2` | Array-backed binary data implementation |

## 📜 License

Apache License 2.0 — see [LICENSE](LICENSE).
