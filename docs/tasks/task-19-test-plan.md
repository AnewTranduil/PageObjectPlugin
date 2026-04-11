# Task 19 — Test Plan: Demo Report & CI Integration

Three verification checks that together prove the feature-demo pipeline works
end-to-end in CI.

---

## Check 1: `./gradlew demoReport -PfeatureName=smoke` produces `build/reports/demo/smoke/index.html`

### What it proves

The full Gradle pipeline — test selection, UI test execution with trace capture,
and HTML rendering — produces the expected artifact on the CI runner.

### Preconditions

- CI runner has JDK 21, Xvfb, and the display/font packages installed
  (same as the `ui-tests` job in `ci.yml`).
- `DemoSmokeUiTest` exists with exactly 2 methods tagged `@Feature("smoke")`
  (`happyPath`, `negativePath`) — satisfying the >=2 scenario rule.

### Steps

1. Push the branch `claude/test-plan-demo-reporting-uPBh4`.
2. CI triggers the `CI` workflow (`.github/workflows/ci.yml`) on branch push.
3. In a **separate manual or workflow run**, execute:
   ```bash
   ./gradlew demoReport -PfeatureName=smoke
   ```
   This internally:
   - Calls `DemoTestSelector.select()` which scans `src/uiTest/kotlin/` for
     `@Feature("smoke")` annotations and finds `DemoSmokeUiTest.happyPath`
     and `DemoSmokeUiTest.negativePath` (`build.gradle.kts:286-293`).
   - Asserts `taggedCount >= 2` (`build.gradle.kts:295-300`).
   - Spawns `./gradlew uiTest -PcaptureAllTraces=true --tests <fqn>` for the
     selected tests (`build.gradle.kts:302-310`).
   - Reads trace bundles from `build/reports/uiTest/traces/` (`build.gradle.kts:312-315`).
   - Invokes `DemoReportRenderer.render()` which assembles the self-contained
     HTML from `src/main/resources/demo-viewer/{index.html,styles.css,app.js}`
     with base64-inlined screenshots and trace JSON (`build.gradle.kts:324-331`).
   - Writes output to `build/reports/demo/smoke/index.html` (`build.gradle.kts:323`).

### Pass criteria

| # | Assertion | How to verify |
|---|-----------|---------------|
| 1 | `build/reports/demo/smoke/index.html` exists | `test -f build/reports/demo/smoke/index.html` |
| 2 | File is valid HTML (>1 KB) | `wc -c` check |
| 3 | Contains `"feature":"smoke"` | `grep -q '"feature":"smoke"' index.html` |
| 4 | Contains both test names | `grep -q 'happyPath' index.html && grep -q 'negativePath' index.html` |
| 5 | Contains base64-inlined screenshot data | `grep -q 'data:image/png;base64,' index.html` |
| 6 | All template placeholders substituted | No `/*__STYLES__*/`, `/*__DATA__*/`, `/*__APP__*/` literals remain |

### Failure modes

| Failure | Root cause | File to investigate |
|---------|-----------|---------------------|
| `taggedCount < 2` error | `@Feature("smoke")` missing from one of the two methods, or class-level annotation removed | `DemoSmokeUiTest.kt` |
| `uiTest sub-build failed` | IDE sandbox / Xvfb / robot-server not starting | Same as `ui-tests` job infra — check Xvfb, display, font deps |
| Trace bundles empty | `captureAllTraces` not wired, or `TraceBundleExtension` not writing bundles for passing tests | `TraceBundleExtension.kt`, `build.gradle.kts` uiTest config |
| HTML missing placeholders | Template files missing or misaligned | `src/main/resources/demo-viewer/` |

### Existing unit test coverage (does NOT replace this check)

- `DemoTestSelectorTest` — verifies tag scanning and package heuristic
  (`buildSrc/src/test/kotlin/.../DemoTestSelectorTest.kt`).
- `DemoReportRendererTest` — verifies HTML rendering from synthetic trace JSON
  (`buildSrc/src/test/kotlin/.../DemoReportRendererTest.kt`).
- Neither exercises the full Gradle task orchestration or real IDE execution.

---

## Check 2: Adding `demo` label to a PR with `[demo:smoke]` title uploads artifact and posts comment

### What it proves

The `.github/workflows/demo.yml` workflow fires on the `demo` label, correctly
parses the feature tag from the PR title, runs `demoReport`, uploads the
artifact, and posts a PR comment with the download link.

### Preconditions

- A PR exists on `AnewTranduil/PageObjectPlugin` whose title starts with
  `[demo:smoke]` (e.g. `[demo:smoke] Task 19 test plan`).
- The `demo` label exists in the repo (create it if not).
- The PR branch contains the full task 19 implementation (all commits through
  `fd6c343`).

### Steps

1. Create a PR from this branch to `main` with title:
   `[demo:smoke] Task 19 — demo report verification`.
2. Add the label `demo` to the PR.
3. Observe: the `demo-report` workflow (`.github/workflows/demo.yml`) triggers.
4. Wait for the workflow run to complete.

### Workflow execution detail

The workflow proceeds through these steps (referencing `demo.yml` line numbers):

| Step | Lines | What happens |
|------|-------|-------------|
| Checkout | 15-18 | Full-history clone (`fetch-depth: 0`) |
| Parse tag | 20-30 | Regex `^\[demo:([a-z0-9-]+)\]` extracts `smoke` from PR title |
| Validate tag | 32-43 | If tag is empty, posts error comment and fails — should NOT trigger here |
| Setup JDK | 45-49 | Temurin JDK 21 |
| Compute changed files | 51-59 | `git diff --name-only origin/$BASE_REF...HEAD` |
| Run demoReport | 61-67 | `./gradlew demoReport -PfeatureName=smoke -PchangedFiles=<csv>` |
| Upload artifact | 69-74 | `actions/upload-artifact@v4` with name `demo-report-<sha>` |
| Post comment | 76-86 | `github-script` creates a PR comment with artifact link |

### Pass criteria

| # | Assertion | How to verify |
|---|-----------|---------------|
| 1 | Workflow completes with green status | GitHub Actions UI shows check mark |
| 2 | Artifact `demo-report-<sha>` appears in workflow run | Actions > Run > Artifacts section |
| 3 | Artifact contains `index.html` | Download and inspect |
| 4 | PR has a comment from github-actions bot | PR conversation tab |
| 5 | Comment text includes `Demo report for \`smoke\` uploaded` | Read comment body |
| 6 | Comment links to the correct workflow run URL | Click link, verify it goes to the demo-report run |

### Negative case: missing tag

| # | Scenario | Expected result |
|---|----------|----------------|
| 1 | PR title is `Fix something` (no `[demo:...]`) + `demo` label added | Workflow posts error comment explaining the prefix requirement, job fails |
| 2 | PR title is `[demo:UPPER]` (uppercase tag) | Regex `[a-z0-9-]+` does not match → error comment |

### Failure modes

| Failure | Root cause | Fix |
|---------|-----------|-----|
| Workflow doesn't trigger | Label name mismatch (case-sensitive) — must be exactly `demo` | Check repo labels |
| Tag parse returns empty | PR title doesn't match `^\[demo:([a-z0-9-]+)\]` | Fix title format |
| demoReport fails | Same as Check 1 infra issues — UI test sandbox on ubuntu-latest | demo.yml needs Xvfb + display deps (currently missing!) |
| Artifact upload fails | Path `build/reports/demo/**` doesn't exist | demoReport didn't produce output |
| Comment not posted | `pull-requests: write` permission missing | Check workflow permissions block (line 12-13) |

### Known risk: demo.yml missing Xvfb setup

The `demo.yml` workflow runs `demoReport` which internally executes `uiTest`.
UI tests require Xvfb and display dependencies. **Currently `demo.yml` does NOT
install Xvfb or start a virtual display** — it only sets up JDK. This will
cause `demoReport` to fail when the IDE sandbox cannot open a display.

Compare `demo.yml` steps (lines 45-67) vs `ci.yml` ui-tests job (lines 77-94):
`ci.yml` installs Xvfb, display libs, fonts, starts `Xvfb :99`, sets
`DISPLAY=:99`, and starts the IDE sandbox. `demo.yml` does none of this.

**Resolution:** `demo.yml` needs the same Xvfb/display setup as `ci.yml`'s
`ui-tests` job before the `Run demoReport` step, plus `env: DISPLAY: ':99'`
at job level.

---

## Check 3: `test-report` CI job green

### What it proves

Task 19 changes (new files, build.gradle.kts modifications, new workflow) do
not break any existing tests. The authoritative signal is the `test-report`
aggregator job in `.github/workflows/ci.yml`.

### Preconditions

- Branch `claude/test-plan-demo-reporting-uPBh4` pushed to origin.
- CI workflow triggers on `push` to `claude/**` branches (`ci.yml:5-6`).

### Steps

1. Push the branch.
2. Wait for the `CI` workflow to complete (all 4 jobs):
   - `unit-tests` — `./gradlew test` (buildSrc unit tests including
     `DemoTestSelectorTest`, `DemoReportRendererTest`, `FeatureTagListenerTest`)
   - `ui-tests` — `./gradlew uiTest --info` (includes `DemoSmokeUiTest`
     if not filtered)
   - `playwright-tests` — Playwright snapshot saver tests (unrelated to task 19)
   - `test-report` — aggregates results, uploads `claude-summary` bundle
3. Read `claude-summary.md` from the report dashboard:
   ```bash
   BASE="${REPORT_DASHBOARD_URL:-https://reports.artemon.cloud}"
   AUTH="Authorization: Bearer $REPORT_DASHBOARD_TOKEN"
   RUN_ID=$(curl -sH "$AUTH" "$BASE/api/v1/external/runs" \
              | jq -r '.data[0].run_id')
   curl -sH "$AUTH" \
     "$BASE/api/v1/external/runs/$RUN_ID/claude-summary/claude-summary.md"
   ```

### Pass criteria

| # | Assertion | How to verify |
|---|-----------|---------------|
| 1 | `unit-tests` job green | GitHub Actions or dashboard |
| 2 | `ui-tests` job green (or expected-flaky only) | Dashboard `claude-summary.md` |
| 3 | `playwright-tests` job green | Dashboard |
| 4 | `test-report` job green | GitHub Actions — this is the gate |
| 5 | `claude-summary.json` `.totals.failed == 0` | Dashboard file content |
| 6 | Dashboard upload succeeded (HTTP 200/201) | `test-report` job log, step "Upload claude-summary bundle" |

### Test surfaces affected by task 19

| Test suite | New/changed tests | Risk |
|-----------|-------------------|------|
| buildSrc unit tests | `DemoTestSelectorTest` (3 tests), `DemoReportRendererTest` (1 test) | Low — pure functions with temp dirs |
| uiTest | `DemoSmokeUiTest` (2 tests), `FeatureTagListenerTest` (3 tests) | Medium — `DemoSmokeUiTest` needs IDE sandbox |
| Gradle config | `demoReport` task registration (`build.gradle.kts:268-334`) | Low — task only runs when explicitly invoked |
| CI workflow | New `demo.yml` — does NOT affect `ci.yml` | None — separate workflow |

### Failure modes

| Failure | Root cause | Fix |
|---------|-----------|-----|
| buildSrc tests fail | `DemoTestSelector` or `DemoReportRenderer` logic error | Fix in `buildSrc/src/main/kotlin/` |
| `DemoSmokeUiTest` fails | `BaseUiTest.takeScreenshot()` broken, or IDE sandbox issue | Check trace bundle in dashboard |
| `FeatureTagListenerTest` fails | Mocking / extension context issue | Check test in `src/uiTest/kotlin/.../support/` |
| `build.gradle.kts` parse error | Syntax error in task registration | Gradle `--dry-run` locally |
| Unrelated test regresses | Coincidental breakage on main | Compare with main branch CI |

---

## Execution order

| Order | Check | Blocking? | Notes |
|-------|-------|-----------|-------|
| 1 | Check 3 (CI green) | Yes | Must pass before the others matter |
| 2 | Check 1 (demoReport local/CI) | Yes | Core functionality |
| 3 | Check 2 (demo label on PR) | No | Requires a PR; can run after Check 1 confirms the task works |

## Summary of files under test

| File | Role | Check |
|------|------|-------|
| `build.gradle.kts:268-345` | `demoReport` task + `gitDiffChangedFiles` | 1, 2 |
| `buildSrc/.../DemoTestSelector.kt` | Tag + file-based test selection | 1, 2 |
| `buildSrc/.../DemoReportRenderer.kt` | Self-contained HTML assembly | 1, 2 |
| `src/main/resources/demo-viewer/` | HTML/CSS/JS template | 1, 2 |
| `src/uiTest/.../DemoSmokeUiTest.kt` | Smoke fixture (2 @Feature("smoke") tests) | 1, 2, 3 |
| `src/uiTest/.../Feature.kt` | Annotation definition | 1, 2, 3 |
| `src/uiTest/.../FeatureTagListener.kt` | Injects tag into trace.json | 1, 2, 3 |
| `src/uiTest/.../TraceBundleExtension.kt:152` | Reads feature tag into bundle | 1, 2, 3 |
| `.github/workflows/demo.yml` | PR label trigger workflow | 2 |
| `.github/workflows/ci.yml` | Main CI pipeline | 3 |
