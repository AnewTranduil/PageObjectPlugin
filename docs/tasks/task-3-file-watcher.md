# Task 3: File Watcher + Automatic Snapshot Discovery

> **Goal:** Auto-detect snapshot folders next to open .ts files and load them without manual action.
> **Depends on:** Task 2
> **Output:** Opening a .ts file auto-populates the Tool Window with available snapshots

## Prompt

Extend the Page Mirror IntelliJ plugin (Kotlin, 2024.3+). The plugin already has: Tool Window with JCEF, `SnapshotBundle`, `SnapshotService` with `loadSnapshot()`.

Add automatic snapshot discovery:

1. **SnapshotDiscoveryListener.kt:**
   - Implement `FileEditorManagerListener`
   - On `fileOpened`: check if the opened file is `.ts` or `.spec.ts`
   - If so, look for a sibling `.snapshots/` directory (same directory as the file, or parent directory)
   - Scan `.snapshots/` recursively (max 2 levels deep) for subdirectories that contain valid snapshot bundles
   - Populate a dropdown/selector in the Tool Window header with available snapshots (e.g., "login/initial", "login/error-state")
   - Auto-load the first snapshot found

2. **Update the Tool Window UI:**
   - Add a toolbar at the top with:
     - `ComboBox` for snapshot selection
     - Refresh button (re-scans `.snapshots/`)
     - A `JBLabel` showing the source `.ts` file name
   - When the user selects a different snapshot from the combo, load it via `SnapshotService`

3. **SnapshotWatcher.kt:**
   - Use `VirtualFileManager.addVirtualFileListener` to watch for changes inside `.snapshots/` directories
   - When files change (e.g., after a test re-run), auto-reload the currently displayed snapshot
   - Debounce: 500ms delay before reload to batch rapid file changes
   - After external changes, call `VirtualFileManager.getInstance().refreshWithoutFileWatcher()` to ensure VFS picks them up

4. Register the listener in `plugin.xml`.

**Edge cases to handle:**
- If no `.snapshots/` found, show "No snapshots found. Run your tests to generate snapshots."
- If the user switches to a non-TS file, keep the last snapshot visible (don't clear)
- If the Tool Window is hidden/closed, don't do unnecessary work
- Handle multiple `.snapshots/` dirs in nested project structures

## Acceptance Criteria

- [ ] Open a `.spec.ts` file next to a `.snapshots/` folder → Tool Window auto-populates the dropdown
- [ ] Selecting a snapshot from the dropdown loads it in the JCEF view
- [ ] Re-running the Playwright test triggers auto-reload in the Tool Window
- [ ] Opening a `.py` file does not clear the current snapshot
- [ ] Refresh button re-scans and updates the dropdown
- [ ] "No snapshots found" message shows when there's no `.snapshots/` directory
