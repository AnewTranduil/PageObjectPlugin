# Task 4: Code-to-UI Highlight Bridge

> **Goal:** Selecting a Playwright locator in the code editor highlights the corresponding element in Page Mirror.
> **Depends on:** Task 3
> **Output:** Cursor on `page.locator('#username')` → blue highlight box appears on the element

## Prompt

Extend the Page Mirror IntelliJ plugin (Kotlin, 2024.3+). The plugin already has: JCEF Tool Window, snapshot loading, file watcher with auto-discovery.

Add code-to-UI highlighting:

1. **LocatorExtractor.kt:**
   - Given a line of TypeScript code, extract Playwright selectors.
   - Support these patterns:
     - `page.locator('selector')` and `page.locator("selector")`
     - `page.getByRole('role', { name: 'text' })`
     - `page.getByText('text')`
     - `page.getByTestId('id')`
     - `page.getByPlaceholder('text')`
     - `this.page.locator(...)` (page object pattern)
   - Return:
     ```kotlin
     data class ExtractedLocator(
       val type: String,        // "locator", "getByRole", "getByText", etc.
       val value: String,       // the raw selector/role/text
       val cssSelector: String? // resolved CSS equivalent when possible
     )
     ```
   - Use regex, NOT a TypeScript parser.
   - Handle single quotes, double quotes, and template literals (backticks).
   - For chained locators like `page.locator('form').locator('input')`, extract the innermost selector only.

2. **CaretHighlightListener.kt:**
   - Implement `CaretListener` on the active editor
   - On caret move: get the current line text, run `LocatorExtractor`
   - If a locator is found, call `SnapshotService.highlightElement(cssSelector)`
   - If no locator on the current line, call `SnapshotService.clearHighlight()`
   - Debounce: 150ms delay to avoid excessive calls during rapid navigation

3. **Update page-mirror.html — improved `highlightElement`:**
   - `window.highlightElement(selector)`:
     - Look up selector in `layout.json` elements
     - Matching strategy: exact CSS match first, then fuzzy match by text content, then by role+name combination
     - Draw highlight box with element info tooltip showing: tag name, role, visible text
     - Scroll the iframe viewport to show the highlighted element if it's off-screen
   - `window.clearHighlight()`: remove all highlight boxes and tooltips

4. **Listener lifecycle:**
   - Register the `CaretListener` when the Tool Window becomes visible
   - Remove it when the Tool Window is hidden (to avoid overhead when not needed)
   - Also remove on project close / plugin dispose

## Acceptance Criteria

- [x] Cursor on `this.page.locator('#username')` → highlights the username input in Page Mirror
- [x] Cursor on `page.getByRole('button', { name: 'Login' })` → highlights the login button
- [x] Moving cursor to a comment line → clears the highlight
- [x] Highlight box shows a tooltip with tag name, role, and visible text
- [x] No visible delay when moving the cursor (debounce feels instant)
- [x] Works in both `.ts` and `.spec.ts` files
- [x] No memory leaks (listener is properly disposed)

**Status: COMPLETE** (merged to main via PR #3)
