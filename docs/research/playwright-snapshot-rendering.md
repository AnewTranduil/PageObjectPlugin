# Playwright Snapshot Rendering — Research Report

> **Date:** 2026-04-01
> **Purpose:** Document how Playwright's trace-rendered snapshots work at runtime, and the sanitization required for embedding them in external viewers.

---

## Snapshot HTML Structure

When Playwright renders a snapshot from a trace (via `SnapshotRenderer.render()`), the output HTML has two key parts:

### 1. Visibility Guard

The very first element is a style tag that hides everything:

```html
<style>*,*::before,*::after { visibility: hidden }</style>
```

This prevents a flash of unstyled/unprocessed content while the bootstrap script runs.

### 2. Bootstrap Script (`applyPlaywrightAttributes`)

A self-executing script that:

1. **Restores form state** — sets `value`, `checked`, `selected` from `__playwright_value_`, `__playwright_checked_`, `__playwright_selected_` attributes
2. **Reconstructs shadow DOM** — finds `<template __playwright_shadow_root_>` elements and calls `attachShadow()` + `appendChild(template.content)`
3. **Restores scroll positions** — reads `__playwright_scroll_top_` and `__playwright_scroll_left_` attributes
4. **Registers custom elements** — reads `__playwright_custom_elements__` from `<body>` and defines empty custom element classes
5. **Applies adopted stylesheets** — reads `__playwright_style_sheet_` templates and adds them to `root.adoptedStyleSheets`
6. **Highlights target elements** — finds `__playwright_target__` attributes and applies blue outline/background overlay
7. **Unhides content** — on `load` event, disables the first stylesheet (`document.styleSheets[0].disabled = true`)

---

## Target Element Highlighting Problem

### Symptom

Elements that were assertion targets (e.g., the element checked by `expect(locator).toBeVisible()`) appear with a blue overlay instead of their actual CSS colors.

### Cause

Playwright marks assertion target elements with a `__playwright_target__` attribute:

```html
<DIV __playwright_target__="call@21" id="flash" class="error">...</DIV>
```

The bootstrap script then applies trace-viewer highlighting:

```javascript
for (const target of root.querySelectorAll(`[__playwright_target__="${targetId}"]`)) {
  style.outline = "2px solid #006ab1";
  style.backgroundColor = "#6fa8dc7f";  // semi-transparent blue
}
```

This is useful in Playwright's own trace viewer (to show which element an action targeted), but unwanted when embedding snapshots in external tools like Page Mirror.

### Fix

Strip all `__playwright_target__` attributes from the rendered HTML before writing to disk:

```typescript
const cleanHtml = rendered.html.replace(/ __playwright_target__="[^"]*"/g, '');
```

The `querySelectorAll` reference inside the bootstrap script is left intact — it simply matches nothing since all target attributes are removed.

Applied in `extractor.ts`, which is called by both the `extractSnapshots()` API and the Playwright reporter.

---

## Iframe Sandbox Requirements

### Symptom

Snapshot HTML renders as a blank white page when loaded in an iframe with `sandbox="allow-same-origin"`.

### Cause

The visibility guard (`* { visibility: hidden }`) is only removed by the bootstrap script (step 7 above). Without `allow-scripts`, the script never runs and all content stays invisible.

### Fix

Use `sandbox="allow-same-origin allow-scripts"`. The script is also needed for shadow DOM reconstruction, form state restoration, and scroll position recovery — not just unhiding.

The sandbox still prevents navigation, form submission, popups, and other potentially unsafe behaviors.

---

## Summary of Playwright-Specific Attributes

| Attribute | Purpose | Stripped? |
|-----------|---------|-----------|
| `__playwright_target__` | Trace viewer action target highlight | Yes |
| `__playwright_value_` | Form input value restoration | No (needed) |
| `__playwright_checked_` | Checkbox/radio state | No (needed) |
| `__playwright_selected_` | Select option state | No (needed) |
| `__playwright_scroll_top_` | Scroll position | No (needed) |
| `__playwright_scroll_left_` | Scroll position | No (needed) |
| `__playwright_shadow_root_` | Shadow DOM reconstruction | No (needed) |
| `__playwright_custom_elements__` | Custom element registration | No (needed) |
| `__playwright_style_sheet_` | Adopted stylesheet | No (needed) |
| `__playwright_src__` | Iframe src rewriting | No (needed) |
| `__playwright_bounding_rect__` | Iframe bounding rect for trace viewer | No (harmless) |
