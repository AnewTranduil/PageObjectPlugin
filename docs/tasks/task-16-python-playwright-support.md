# Task 16: Python Playwright Support

> **Goal:** Make the plugin recognize Python Playwright locators in `.py` files (highlight + gutter) AND ship a `playwright-snapshot-saver-python` package so Python users can produce snapshot bundles.
> **Depends on:** Task 15 (snapshot-core — for the bundle spec contract), `docs/snapshot-bundle-spec.md` frozen.
> **Output:** `PythonLocatorExtractor.kt`, `LocatorExtractorRegistry`, new PyPI package `packages/playwright-snapshot-saver-python/`.

## Motivation

PageObjectPlugin currently supports TypeScript Playwright only. Python is the second-most-common Playwright language. Adding it exercises the plugin's assumed-TS-everywhere architecture and forces extraction of a language-agnostic locator interface that A2 (JVM) can reuse.

Python users also need a snapshot saver in their language — the existing npm package is unreachable from `pytest`. We ship a sibling PyPI package that produces byte-identical bundles.

## Key Files

### Plugin side
- `src/main/kotlin/com/github/artem/pageobjectplugin/locators/LocatorExtractor.kt` — introduce `LocatorExtractor` interface (refactor), keep existing class as `TypeScriptLocatorExtractor`.
- New: `src/main/kotlin/com/github/artem/pageobjectplugin/locators/PythonLocatorExtractor.kt`
- New: `src/main/kotlin/com/github/artem/pageobjectplugin/locators/LocatorExtractorRegistry.kt`
- `src/main/kotlin/com/github/artem/pageobjectplugin/annotators/SelectorValidationAnnotator.kt` — route through the registry.
- `src/main/kotlin/com/github/artem/pageobjectplugin/settings/PageMirrorSettings.kt` — add `.py` to default `fileExtensions`.
- New: `src/test/kotlin/com/github/artem/pageobjectplugin/locators/PythonLocatorExtractorTest.kt`

### Saver package
- New: `packages/playwright-snapshot-saver-python/`
  - `pyproject.toml`, `src/playwright_snapshot_saver/__init__.py`
  - `save_snapshot.py` — sync + async variants
  - `html_inliner.py` — port of the TS version using `beautifulsoup4`
  - `manifest_generator.py`
  - `pytest_plugin.py` — pytest reporter analog
  - `tests/` — `pytest` integration tests against a fixture HTML page

## Python Locator Patterns

Recognize (regex-based, no PSI — consistent with the no-JS/TS-module rule in `CLAUDE.md`):
- `page.get_by_test_id("...")`
- `page.get_by_role("button", name="...")`
- `page.get_by_label("...")`
- `page.get_by_placeholder("...")`
- `page.get_by_text("...")`
- `page.locator("...")`
- Variable-bound receivers: `self.page.locator("...")`, `my_page.get_by_test_id("...")`

## `LocatorExtractor` Interface

```kotlin
interface LocatorExtractor {
    fun supportedExtensions(): Set<String>
    fun extract(psiFileText: String, offset: Int): ExtractedLocator?
    fun extractAll(psiFileText: String): List<ExtractedLocator>
}
```

`LocatorExtractorRegistry` is a project service that holds all registered extractors and resolves by file extension.

## Python Saver API

```python
from playwright_snapshot_saver import save_snapshot

# Sync
save_snapshot(page, name="login/initial", output_dir=".snapshots")

# Async
await save_snapshot_async(page, name="login/initial", output_dir=".snapshots")
```

Options mirror the TS package: `group`, `screenshot`, `manifest`, `extra_selectors`, `exclude_selectors`, `extra_attributes`.

The pytest plugin (`pytest_plugin.py`) provides auto-capture on test failure when enabled via `@pytest.fixture` or `pytest.ini`.

## Steps

1. Refactor existing locator extraction into `LocatorExtractor` interface + `TypeScriptLocatorExtractor`; introduce `LocatorExtractorRegistry`. All existing tests still pass.
2. Implement `PythonLocatorExtractor` with regex patterns above. Unit tests cover every pattern + edge cases (nested calls, kwargs, multi-line, comments).
3. Wire `SelectorValidationAnnotator` to look up the extractor via the registry by file extension.
4. Add `.py` to default `fileExtensions` in settings; update `PageMirrorConfigurable` if the default needs to be surfaced.
5. Scaffold `packages/playwright-snapshot-saver-python/` with modern `pyproject.toml` (PEP 621) and `hatchling` or `poetry` build.
6. Port `html-inliner.ts` to Python using `beautifulsoup4` + stdlib `urllib` — produce output matching the TS version for the shared fixture page (verified by golden-file test).
7. Port `manifest-generator.ts` — emit spec-v2 compliant `manifest.json` per `docs/snapshot-bundle-spec.md`.
8. Implement `save_snapshot` / `save_snapshot_async` for `playwright.sync_api.Page` and `playwright.async_api.Page`.
9. Implement `pytest_plugin.py` reporter (fixture + hook) analogous to `playwright-snapshot-saver`'s Playwright reporter.
10. Integration tests: `pytest` suite that launches a headless Chromium, visits a fixture page, calls `save_snapshot`, and asserts bundle layout + manifest contents.

## Verification

- **Plugin**: unit tests for `PythonLocatorExtractor` pass; manual smoke — open a `.py` Page Object with Playwright locators, confirm gutter badges and caret-driven tool-window highlight.
- **Saver**: `pytest` integration tests pass; a bundle produced by the Python saver is byte-compatible with a TS bundle for the same fixture page (except for timestamp and driver fields).
- `.snapshots/` bundles from both savers load identically in the plugin tool window.

## Out of Scope

- Python PSI-based extraction (regex is sufficient per `CLAUDE.md`).
- pytest-xdist parallelization.
- Publishing to PyPI (done separately when the package is ready).
