# Region Highlights Plugin — Design Document

**Date**: 2026-03-15
**Target IDE**: JetBrains Rider
**Plugin type**: IntelliJ Platform plugin (Kotlin + Gradle)
**Approach**: ExternalAnnotator-based

## Problem

JetBrains Rider has no built-in visual distinction for `#region`/`#endregion` blocks in C# files. The feature request [RIDER-45861](https://youtrack.jetbrains.com/issue/RIDER-45861) asks for full-line background highlighting and accent lines to make region blocks visually distinct, similar to Visual Studio extensions like HighlightRegions and RegionHighlights.

## Requirements

1. Full-line background color highlighting for all lines within a `#region`/`#endregion` block
2. Accent line on the bottom edge of the `#region` line and top edge of the `#endregion` line
3. Collapsed (folded) regions show both accent lines on the single visible line
4. Two color tiers: top-level regions vs all nested regions (regardless of nesting depth)
5. User-configurable colors via settings page
6. C# only

## Region Detection

- Scan document text line by line using `String.trimStart().startsWith()` checks for `#region` and `#endregion`
- Build nesting structure via a stack: each `#region` pushes, each `#endregion` pops
- Output: `List<RegionBlock>` where `RegionBlock(startLine, endLine, depth)`
- Depth 0 = top-level, depth 1+ = nested
- Malformed/unmatched directives are silently ignored

## Rendering

### Background color
- Applied via `RangeHighlighter` with `HighlighterTargetArea.LINES_IN_RANGE`
- `TextAttributes.setBackgroundColor()` with the appropriate tier color
- Depth 0: user-configured color A (default: subtle blue)
- Depth 1+: user-configured color B (default: subtle green)

### Accent lines
- Drawn via `CustomHighlighterRenderer` or `LineSeparatorRenderer` on the `RangeHighlighter`
- Top accent: drawn on the bottom edge of the `#region` line
- Bottom accent: drawn on the top edge of the `#endregion` line
- Accent colors are separately configurable per tier

### Folded region handling
- When collapsed, both accent lines render on the single visible line (top edge + bottom edge)
- Collapsed state detected via `FoldingModel.isOffsetCollapsed()` or `FoldRegion` iteration
- `FoldingListener` triggers re-highlighting when fold state changes

## Settings

Settings page at **Settings > Editor > Region Highlights**:

- Top-level region background color (color picker, default: subtle blue)
- Nested region background color (color picker, default: subtle green)
- Top-level accent line color (color picker, default: brighter blue)
- Nested accent line color (color picker, default: brighter green)
- Enable/disable toggle

Persisted via `PersistentStateComponent`. Changes apply immediately without restart.

## Plugin Structure

```
src/main/kotlin/com/canbaycay/regionhighlights/
  ├── RegionBlock.kt              — data class (startLine, endLine, depth)
  ├── RegionParser.kt             — scans document text, returns List<RegionBlock>
  ├── RegionHighlightAnnotator.kt — ExternalAnnotator: calls parser + applies highlights
  ├── RegionFoldingListener.kt    — listens for fold changes, triggers re-highlight
  ├── RegionHighlightSettings.kt  — PersistentStateComponent for colors/enabled
  └── RegionHighlightConfigurable.kt — Settings UI page

src/main/resources/
  └── META-INF/plugin.xml         — extension point registrations
```

## Build & Installation

- Gradle with IntelliJ Platform Gradle Plugin 2.x
- Target: Rider (dependency on `com.intellij.modules.rider`)
- Build artifact: `.zip` via `./gradlew buildPlugin`
- Install via **Settings > Plugins > Install from Disk**
- No marketplace publishing required
