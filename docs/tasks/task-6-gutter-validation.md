# Task 6: Live Selector Validation (Gutter Badges)

> **Goal:** Show match count badges in the editor gutter as the user types Playwright selectors.
> **Depends on:** Task 4 (needs `LocatorExtractor` and loaded snapshot)
> **Output:** Green "1", red "0", or yellow "3" badges next to locator lines

## Prompt

Extend the Page Mirror IntelliJ plugin (Kotlin, 2024.3+). The plugin already has: JCEF Tool Window, snapshot loading, code-to-UI highlighting, `LocatorExtractor`.

Add live selector validation in the editor gutter:

1. **SelectorValidationAnnotator.kt:**
   - Implement `ExternalAnnotator<PsiFile, List<SelectorAnnotation>>`
   - `collectInformation`: scan all lines in the file for locator patterns (reuse `LocatorExtractor`)
   - `doAnnotate`: for each extracted selector, run `querySelectorAll` against the currently loaded snapshot DOM via Jsoup (on the Kotlin side, NOT in JCEF)
   - `apply`: add gutter icons showing match count

2. **Match count logic (Kotlin-side, using Jsoup):**
   - `SnapshotService` keeps a parsed `org.jsoup.nodes.Document` of the current snapshot's `index.html`
   - For `locator('cssSelector')`: run `document.select(cssSelector).size()`
   - For `getByRole(role, { name: text })`: approximate by selecting `[role=role]` then filtering by text content
   - For `getByText(text)`: search all elements for matching text content
   - For `getByTestId(id)`: select `[data-testid=id]`
   - For `getByPlaceholder(text)`: select `[placeholder=text]` or `[placeholder*=text]`
   - Cache the Jsoup document instance. Invalidate on snapshot reload.

3. **Gutter icons:**
   - **1 match**: green circle with "1" (ideal — unique selector)
   - **0 matches**: red circle with "0" (broken selector — needs attention)
   - **2+ matches**: yellow circle with the count (ambiguous selector)
   - Clicking the gutter icon: highlight ALL matching elements in Page Mirror (send all selectors to JCEF)

4. **Performance requirements:**
   - Only run the annotator when a snapshot is loaded in `SnapshotService`
   - Only annotate `.ts` files
   - The `ExternalAnnotator` runs on a background thread (IntelliJ handles this automatically)
   - After snapshot reload, force re-annotation: `DaemonCodeAnalyzer.getInstance(project).restart()`

5. **Dependencies:**
   - Add Jsoup to `build.gradle.kts`: `implementation("org.jsoup:jsoup:1.17.2")`

## Acceptance Criteria

- [x] Line with `page.locator('#username')` shows green "1" badge in gutter
- [x] Line with `page.locator('.nonexistent')` shows red "0" badge
- [x] Line with `page.locator('input')` shows yellow badge with actual count
- [x] Badges update within ~500ms of editing the selector string
- [x] No UI freezes when annotating a 500-line file
- [x] Clicking a badge highlights matching elements in Page Mirror
- [x] Reloading a snapshot updates the badges automatically

**Status: COMPLETE** (merged to main via PR #3)
