# Task 5: Element Picker + Code Generation

> **Status:** DONE — implementation collapsed a few files:
> - No `InsertLocatorAction.kt` or `InsertLocatorPopup` class shipped.
>   Insertion lives inside
>   `src/main/kotlin/.../locators/PickerResultHandler.kt` (`insertAtCaret`,
>   `insertLocatorForTest`).
> - Element metadata is read from the DOM via JS in the snapshot iframe,
>   not from `layout.json` (which was removed in Task 15).
>
> **Goal:** Click an element in Page Mirror to generate a Playwright locator and insert it into the editor.
> **Depends on:** Task 4
> **Output:** Inspect mode → click element → popup offers locator code → inserts at caret

## Prompt

Extend the Page Mirror IntelliJ plugin (Kotlin, 2024.3+). The plugin already has: JCEF Tool Window, snapshot loading, code-to-UI highlighting with `LocatorExtractor`.

Add the Element Picker:

1. **Update page-mirror.html — Picker Mode:**
   - Add a toggle button in the toolbar: "Inspect" (crosshair icon or similar)
   - When active:
     - Hovering over elements in the snapshot iframe highlights them with a green box (distinct from the blue code-highlight)
     - The element's selector and role appear in a floating tooltip near the cursor
   - Clicking an element in inspect mode:
     - Reads its metadata from the stored `layout.json` (selector, role, text, tag, attributes)
     - Sends it back to Kotlin via `JBCefJSQuery` callback as a JSON string
     - Exits inspect mode automatically

2. **PickerResultHandler.kt:**
   - Receives the element metadata JSON from the JCEF callback
   - Generates the best Playwright locator using this priority order:
     1. `getByTestId('id')` — if `data-testid` attribute exists
     2. `getByRole('role', { name: 'text' })` — if ARIA role + accessible name are available
     3. `getByText('text')` — if unique visible text exists
     4. `getByPlaceholder('text')` — if it's an input with placeholder
     5. `locator('cssSelector')` — fallback
   - Shows an `InsertLocatorPopup` near the editor caret with options:
     - **Property**: `readonly fieldName = this.page.locator(...);`
     - **Variable**: `const element = page.locator(...);`
     - **Copy selector**: copies just the selector string to clipboard

3. **InsertLocatorAction.kt:**
   - Inserts the chosen code at the current caret position in the active editor
   - Uses `WriteCommandAction` for undo support
   - Auto-detects context: if inside a class body → property style, if inside a function → variable style (but user can override via the popup)

4. **Field name generation:**
   - Generate sensible camelCase names from element metadata
   - Examples:
     - Button with text "Login" → `loginButton`
     - Input with placeholder "Username" → `usernameInput`
     - Link with text "Forgot Password?" → `forgotPasswordLink`
   - Strip special characters, collapse whitespace, truncate to 30 chars

5. **JS→Kotlin communication:**
   - Use `JBCefJSQuery`: register a handler in Kotlin that receives the JSON string
   - In JS: call the query bridge function with `JSON.stringify(elementData)`
   - Handle the case where the JCEF bridge is not yet initialized (retry or show error)

6. **ToggleInspectAction.kt:**
   - An action that toggles inspect mode on/off
   - Calls `window.toggleInspectMode()` in the JCEF browser
   - Updates the toolbar button state (pressed/unpressed)

## Acceptance Criteria

- [x] Click Inspect → hover shows green highlight → click an input → popup appears near caret
- [x] Popup shows Property / Variable / Copy options with the generated locator
- [x] Choosing "Property" inserts `readonly usernameInput = this.page.locator('#username');` at caret
- [x] Generated field names are sensible camelCase (not "element1")
- [x] Undo (`Ctrl+Z`) removes the inserted code in one step
- [x] Picker correctly sends data from JS to Kotlin without errors
- [x] Inspect mode deactivates after clicking an element

**Status: COMPLETE** (merged to main via PR #3)
