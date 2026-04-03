# Plugin-1: Page Object File Matching & Snapshot Auto-Discovery

> **Goal:** Automatically identify page object files via a configurable regex, extract a page name, and match it to snapshot groups under a configurable snapshots root directory.
> **Depends on:** Tasks 1–3 (plugin shell, snapshot loading, file watcher)
> **Output:** Opening `login.page.ts` auto-discovers and loads snapshots from `<snapshotsRoot>/login/`

## Problem

Currently, `SnapshotDiscoveryListener` fires when any `.ts`/`.tsx` file is opened and searches for `.snapshots/` in only the file's parent and grandparent directories. This has two problems:

1. **No page object identification.** Every TypeScript file triggers discovery — there's no way to distinguish a page object file from a utility, test, or config file.
2. **Fragile directory-relative search.** The parent/grandparent search only works if the file lives at most 2 levels from `.snapshots/`. A file at `src/pages/login.page.ts` would never find `.snapshots/` at the project root.
3. **No filename-to-snapshot mapping.** All discovered snapshots are presented equally — there's no association between `login.page.ts` and `.snapshots/login/`.

## Design

### New Settings

Two new fields in `PageMirrorSettings.State`:

| Setting | Type | Default | Description |
|---|---|---|---|
| `pageObjectPattern` | `String` | `(.+)\.page\.ts` | Java regex applied to the **filename only** (not the full path). Capture group 1 extracts the page name. |
| `snapshotsRoot` | `String` | `.snapshots` | Path to the root snapshots directory, relative to the project root. |

#### Setting Validation

- **`pageObjectPattern`**: Must be a valid Java regex with at least one capture group. The settings UI should show an error/warning if the regex is invalid or has no capture group.
- **`snapshotsRoot`**: Relative path resolved against `project.basePath`. The directory does not need to exist (no snapshots available is a valid state).

### Matching Flow

```
File opened: page-objects/login.page.ts
          │
          ▼
┌─────────────────────────────┐
│ Is it a .ts / .tsx file?    │──── No ──→ skip
└─────────────────────────────┘
          │ Yes
          ▼
┌─────────────────────────────┐
│ Apply pageObjectPattern     │
│ regex to filename           │──── No match ──→ skip (not a page object)
│ "login.page.ts"             │
└─────────────────────────────┘
          │ Match
          ▼
┌─────────────────────────────┐
│ Extract group(1) = "login"  │
│ This is the pageName        │
└─────────────────────────────┘
          │
          ▼
┌─────────────────────────────┐
│ Resolve snapshot group dir: │
│ <projectRoot>/<snapshotsRoot│
│ >/<pageName>/               │
│ e.g. project/.snapshots/    │
│      login/                 │
└─────────────────────────────┘
          │
          ▼
┌─────────────────────────────┐
│ Does the directory exist?   │──── No ──→ empty snapshot list
└─────────────────────────────┘
          │ Yes
          ▼
┌─────────────────────────────┐
│ Scan subdirectories for     │
│ valid bundles (dirs with    │
│ index.html) up to           │
│ snapshotSearchDepth         │
│                             │
│ Found: initial/             │
│        error-state/         │
└─────────────────────────────┘
          │
          ▼
┌─────────────────────────────┐
│ updateAvailableSnapshots()  │
│ Auto-load first if none     │
│ currently loaded            │
└─────────────────────────────┘
```

### Example Scenarios

**Default pattern: `(.+)\.page\.ts`**

| Filename | Match? | Page Name | Snapshot Group |
|---|---|---|---|
| `login.page.ts` | Yes | `login` | `.snapshots/login/` |
| `dashboard.page.ts` | Yes | `dashboard` | `.snapshots/dashboard/` |
| `login.spec.ts` | No | — | — |
| `helpers.ts` | No | — | — |
| `auth.utils.ts` | No | — | — |

**Custom pattern: `(.+)Page\.ts`** (PascalCase convention)

| Filename | Match? | Page Name | Snapshot Group |
|---|---|---|---|
| `LoginPage.ts` | Yes | `Login` | `.snapshots/Login/` |
| `DashboardPage.ts` | Yes | `Dashboard` | `.snapshots/Dashboard/` |

**Custom pattern: `(.+)\.po\.ts`** (`.po` convention)

| Filename | Match? | Page Name | Snapshot Group |
|---|---|---|---|
| `login.po.ts` | Yes | `login` | `.snapshots/login/` |

## Changes

### 1. `PageMirrorSettings.kt`

Add two new fields to `State`:

```kotlin
data class State(
    var snapshotSearchDepth: Int = 3,
    var autoReloadOnChange: Boolean = true,
    var highlightColor: String = "#3B82F6",
    var codeGenStyle: String = "Property",
    var pageObjectPattern: String = "(.+)\\.page\\.ts",  // NEW
    var snapshotsRoot: String = ".snapshots"              // NEW
)
```

### 2. `PageMirrorConfigurable.kt`

Add two new rows to the settings panel:

- **"Page object pattern:"** — text field bound to `pageObjectPattern`. Show validation feedback: red border or error label if regex is invalid or has no capture group.
- **"Snapshots root:"** — text field bound to `snapshotsRoot`. Show resolved absolute path as a hint label (e.g. `→ /home/user/project/.snapshots`).

Placement: add these rows **before** the existing "Snapshot search depth" row, since they are more fundamental settings.

### 3. `SnapshotDiscoveryListener.kt`

**Replace the current discovery logic entirely.**

Current logic (to be removed):
```kotlin
override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (!isTypeScriptFile(file)) return
    val filePath = file.toNioPath()
    val snapshotBundles = discoverSnapshots(filePath, settings.state.snapshotSearchDepth)
    // ...
}
```

New logic:
```kotlin
override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (!isTypeScriptFile(file)) return

    val settings = PageMirrorSettings.getInstance(project).state
    val pageName = extractPageName(file.name, settings.pageObjectPattern) ?: return

    val projectRoot = project.basePath?.let { Path.of(it) } ?: return
    val snapshotGroupDir = projectRoot.resolve(settings.snapshotsRoot).resolve(pageName)

    val bundles = scanForBundles(snapshotGroupDir, settings.snapshotSearchDepth)

    val service = SnapshotService.getInstance(project)
    service.updateAvailableSnapshots(bundles)
}
```

New companion function:
```kotlin
companion object {
    fun extractPageName(filename: String, pattern: String): String? {
        return try {
            val regex = Regex(pattern)
            val match = regex.matchEntire(filename) ?: return null
            match.groupValues.getOrNull(1)
        } catch (_: Exception) {
            null  // Invalid regex — treat as no match
        }
    }

    fun scanForBundles(dir: Path, maxDepth: Int): List<SnapshotBundle> {
        if (!dir.exists() || !dir.isDirectory()) return emptyList()
        val bundles = mutableListOf<SnapshotBundle>()
        val seen = mutableSetOf<Path>()
        scanRecursive(dir, 0, maxDepth, bundles, seen)
        return bundles
    }

    // Existing scanForBundles/scanRecursive logic stays the same
}
```

### 4. Settings UI Validation

The `pageObjectPattern` text field should validate on every keystroke:

- **Invalid regex** → show red error text: "Invalid regex"
- **No capture group** → show warning: "Pattern must have at least one capture group, e.g. `(.+)\\.page\\.ts`"
- **Valid with group** → show green/neutral preview: "Page name from `example.page.ts` → `example`" (test against a sample filename)

## Files Changed

| File | Change |
|---|---|
| `settings/PageMirrorSettings.kt` | Add `pageObjectPattern` and `snapshotsRoot` to `State` |
| `settings/PageMirrorConfigurable.kt` | Add two settings rows with validation |
| `listeners/SnapshotDiscoveryListener.kt` | Replace directory-walk with regex match + project-root-based lookup |

## Files NOT Changed

| File | Reason |
|---|---|
| `model/SnapshotBundle.kt` | Bundle format is unchanged |
| `services/SnapshotService.kt` | API (`updateAvailableSnapshots`, `loadSnapshot`) unchanged |
| `listeners/SnapshotWatcher.kt` | File watcher still monitors `.snapshots` path in VFS events |
| `actions/LoadSnapshotAction.kt` | Manual snapshot loading unchanged |
| `locators/*` | Locator extraction and picker unaffected |
| `annotators/*` | Gutter validation unaffected |
| `resources/html/*` | JCEF rendering pipeline unchanged |

## Acceptance Criteria

- [ ] Opening `login.page.ts` auto-discovers snapshots from `<project>/.snapshots/login/`
- [ ] Opening `dashboard.page.ts` discovers from `<project>/.snapshots/dashboard/` (empty if dir doesn't exist)
- [ ] Opening `login.spec.ts` or `helpers.ts` does NOT trigger snapshot discovery
- [ ] Changing `pageObjectPattern` in settings takes effect on next file open
- [ ] Changing `snapshotsRoot` in settings takes effect on next file open
- [ ] Invalid regex in settings shows validation error and does not crash
- [ ] Regex with no capture group shows a warning
- [ ] Default settings (`(.+)\.page\.ts` + `.snapshots`) work with the test project out of the box
- [ ] `snapshotSearchDepth` still controls how deep bundles are scanned within the matched group directory
- [ ] Existing tests in `SnapshotDiscoveryListenerTest` updated to reflect new logic

## Testing Strategy

### Unit Tests

- `extractPageName("login.page.ts", "(.+)\\.page\\.ts")` → `"login"`
- `extractPageName("LoginPage.ts", "(.+)Page\\.ts")` → `"Login"`
- `extractPageName("helpers.ts", "(.+)\\.page\\.ts")` → `null`
- `extractPageName("login.page.ts", "[invalid")` → `null` (bad regex)
- `extractPageName("login.page.ts", ".*\\.page\\.ts")` → `null` (no capture group)

### Integration Tests

- Open a page object file → verify correct snapshots are discovered
- Open a non-page-object file → verify no snapshots discovered
- Change `snapshotsRoot` setting → verify new root is used
- Change `pageObjectPattern` → verify new pattern is applied
