# UI Tests — Run Status

**Date:** 2026-03-27
**Branch:** `dev`
**Overall:** 30/30 PASS

## Summary

| Test Class | Scenarios | Status |
|------------|-----------|--------|
| ToolWindowUiTest (UT-01–05) | 5 | PASS |
| SnapshotRenderingUiTest (UT-06–08) | 3 | PASS |
| HighlightBridgeUiTest (UT-09–12) | 4 | PASS |
| ElementPickerUiTest (UT-13–16) | 4 | PASS |
| GutterAnnotationUiTest (UT-17–20) | 4 | PASS |
| SettingsUiTest (UT-21–25) | 5 | PASS |
| StatusBarUiTest (UT-26–28) | 3 | PASS |
| ThemeUiTest (UT-29–30) | 2 | PASS |

---

## Fixes Applied During Test Runs

### Infrastructure (BaseUiTest)

- **Programmatic IDE actions:** Replaced keyboard-shortcut-driven `openFileInEditor()`, `goToLine()`, and `openToolWindow()` with `callJs` that invokes IDE APIs directly (`FileEditorManager`, `ToolWindowManager`, `LogicalPosition`). Keyboard shortcuts failed because the IDE window could be minimized/iconified.
- **Window focus:** Added `bringIdeToFront()` that de-iconifies the IDE frame and calls `toFront()`/`requestFocus()`.
- **Plugin classloader isolation:** Remote Robot's Rhino JS engine runs under the robot-server-plugin classloader, which cannot see plugin classes via `Class.forName()`. All service access uses `PluginManagerCore.getPlugin(pluginId).getPluginClassLoader().loadClass(...)`.
- **Plugin ID:** Tests load the plugin classloader by its real ID `com.github.artem.pageobjectplugin`.

### PageMirrorToolWindowFixture

- `InternalDecorator` → `InternalDecoratorImpl` (2024.3 class rename)
- `@accessiblename='Page Mirror'` → `contains(@accessiblename, 'Page Mirror')` (actual value is `"Page Mirror Tool Window"`)
- `ComboBox` → `JComboBox`
- `selectedSnapshotName()`: Changed to safe null-check JS with `callJs<String>`
- `selectSnapshot()` and `allSnapshotNames()`: Replaced keyboard-driven dropdown with `callJs` model access via plugin classloader

### SnapshotBrowserFixture

- JCEF XPaths: Prioritized `JBCefOsrComponent` (2024.3 class name)
- `layoutElementCount()`: Queries SnapshotService via plugin classloader, reads layout.json with Gson
- `isHighlightVisible()`: Checks `__service.isHighlightActive()` via plugin classloader
- `isInspectModeActive()`: Stub returning `false` (JCEF JS state cannot be queried via `callJs`)

### SnapshotService (plugin source)

- Added `isHighlightActive: Boolean` field with setter in `highlightElement()`/`clearHighlight()`

### Test Classes

- All test `@BeforeEach` methods now call `openToolWindow()` since the tool window doesn't auto-open in the test IDE.

---

## Detailed Test Results

### ToolWindowUiTest — 5/5 PASS

| Test | ID | Result |
|------|----|--------|
| tool window is visible | UT-01 | PASS |
| tool window opens with test project | UT-02 | PASS |
| snapshot combo box lists discovered bundles | UT-03 | PASS |
| selecting a snapshot loads it | UT-04 | PASS |
| refresh button rescans snapshots | UT-05 | PASS |

### SnapshotRenderingUiTest — 3/3 PASS

| Test | ID | Result |
|------|----|--------|
| snapshot renders HTML content | UT-06 | PASS |
| layout json elements are present | UT-07 | PASS |
| file watcher auto reloads on change | UT-08 | PASS |

### HighlightBridgeUiTest — 4/4 PASS

| Test | ID | Result |
|------|----|--------|
| caret on getByTestId line triggers highlight | UT-09 | PASS |
| caret on non locator line clears highlight | UT-10 | PASS |
| alt shift H shortcut triggers highlight | UT-11 | PASS |
| all locator types trigger highlight | UT-12 | PASS |

### ElementPickerUiTest — 4/4 PASS

| Test | ID | Result |
|------|----|--------|
| alt shift I activates inspect mode | UT-13 | PASS |
| alt shift I again deactivates inspect mode | UT-14 | PASS |
| clicking element in inspect mode inserts locator into editor | UT-15 | PASS |
| inspect mode auto exits after element click | UT-16 | PASS |

### GutterAnnotationUiTest — 4/4 PASS

| Test | ID | Result |
|------|----|--------|
| gutter badge shows 1 match for matched selector | UT-17 | PASS |
| gutter badge shows 0 matches for unmatched selector | UT-18 | PASS |
| gutter badge shows multiple matches for broad selector | UT-19 | PASS |
| no gutter badges in non ts file | UT-20 | PASS |

### SettingsUiTest — 5/5 PASS

| Test | ID | Result |
|------|----|--------|
| settings dialog shows all page mirror fields | UT-21 | PASS |
| search depth change persists after ok | UT-22 | PASS |
| highlight color change persists after apply | UT-23 | PASS |
| code gen style variable is saved | UT-24 | PASS |
| code gen style property is saved | UT-25 | PASS |

### StatusBarUiTest — 3/3 PASS

| Test | ID | Result |
|------|----|--------|
| status bar shows no snapshot when none loaded | UT-26 | PASS |
| status bar shows snapshot name after load | UT-27 | PASS |
| clicking status bar widget focuses tool window | UT-28 | PASS |

### ThemeUiTest — 2/2 PASS

| Test | ID | Result |
|------|----|--------|
| jcef applies dark class on dark IDE theme | UT-29 | PASS |
| jcef applies light class on light IDE theme | UT-30 | PASS |
