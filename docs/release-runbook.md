# Release Runbook

Step-by-step procedure for publishing a new version of the Page Mirror
plugin and its two npm packages. No CI automation yet — every command
in this runbook runs on the maintainer's local machine.

The repo produces **three independently versioned artifacts**:

| Artifact | Tag format | Publish target |
|---|---|---|
| IntelliJ plugin | `v<version>` (e.g. `v0.5.0`) | JetBrains Marketplace (manual upload) + GitHub Release |
| `@pagemirror/snapshot-core` (npm) | `@pagemirror/snapshot-core@<version>` | npmjs.com |
| `playwright-snapshot-saver` (npm) | `playwright-snapshot-saver@<version>` | npmjs.com |

Run the releases in that order — the saver depends on snapshot-core, so
bumping the saver first leaves users in a broken state until snapshot-core
is available.

## Preconditions

- You are on `main` with no uncommitted changes.
- `CHANGELOG.md` (plugin) and `packages/*/CHANGELOG.md` have the new
  version section at the top, with `### ⚠️ Breaking changes` at the
  top of each new section if applicable.
- All `version` fields in `gradle.properties` / `package.json` match
  the tag you're about to push.
- The CI `test-report` job is green on `main`.

## Plugin release

1. **Build the plugin locally** (sanity check):

   ```bash
   ./gradlew buildPlugin
   ```

   Output: `build/distributions/PageObjectHelper-<version>.zip`.

2. **Patch & verify change-notes** render:

   ```bash
   ./gradlew patchPluginXml
   cat build/patchedPluginXmlFiles/plugin.xml | grep -A 40 '<change-notes>'
   ```

   The breaking-change callout from `CHANGELOG.md` should appear as
   rendered HTML.

3. **Tag + push**:

   ```bash
   git tag -a "v<version>" -m "Page Object Helper <version>"
   git push origin "v<version>"
   ```

4. **Create the GitHub Release** with the CHANGELOG section as the
   body and the plugin zip attached:

   ```bash
   gh release create "v<version>" \
     build/distributions/PageObjectHelper-<version>.zip \
     --title "v<version>" \
     --notes-from-tag
   ```

5. **Upload to JetBrains Marketplace** (manual). Log in at
   <https://plugins.jetbrains.com/plugin/edit/com.github.artem.pageobjectplugin>,
   click **Upload update**, select the zip from step 1, paste the
   CHANGELOG section into "What's new" (or leave blank — the
   `<change-notes>` inside `plugin.xml` is the source of truth).

6. **Verify** via a fresh IDE: *Settings → Plugins → Installed → Page
   Object Helper → Update*. The breaking-change callout should appear
   in the update dialog.

## npm releases

Requires `NPM_TOKEN` or an active `npm login` session with publish
rights on the `@pagemirror` scope and on `playwright-snapshot-saver`.

1. **Publish `@pagemirror/snapshot-core` first**:

   ```bash
   cd packages/snapshot-core
   npm pack --dry-run                        # sanity: README.md must appear
   npm publish                               # publishConfig.access: public already set
   cd ../..
   git tag -a "@pagemirror/snapshot-core@<version>" -m "snapshot-core <version>"
   git push origin "@pagemirror/snapshot-core@<version>"
   ```

2. **Publish `playwright-snapshot-saver` next**:

   ```bash
   cd packages/playwright-snapshot-saver
   npm pack --dry-run                        # sanity: README.md must appear
   npm publish
   cd ../..
   git tag -a "playwright-snapshot-saver@<version>" -m "playwright-snapshot-saver <version>"
   git push origin "playwright-snapshot-saver@<version>"
   ```

3. **Deprecate old versions that predate the current breaking
   change**. Run this once after the publish that introduces the
   break; it adds an install-time warning to every affected version
   range.

   ```bash
   # Saver: v2 bundles are required by Page Mirror plugin 0.5+
   npm deprecate "playwright-snapshot-saver@<0.7" \
     "v1 bundles are no longer supported by Page Mirror plugin 0.5+. Upgrade to ^0.7 and regenerate your .snapshots/."

   # snapshot-core: no historical range to deprecate today, but
   # scaffold the command here so the next breaking bump is trivial.
   # npm deprecate "@pagemirror/snapshot-core@<0.2" \
   #   "Old snapshot-core output incompatible with Page Mirror plugin X.Y+. Upgrade to ^0.2."
   ```

   Deprecation is reversible: `npm deprecate "<pkg>@<range>" ""` clears
   the warning if you need to roll back.

## After the release

- Confirm `npm view playwright-snapshot-saver dist-tags` shows the new
  `latest`.
- Confirm the JetBrains Marketplace listing shows the new version as
  "current".
- Open a PR bumping `[Unreleased]` in the plugin CHANGELOG to signal
  the release cycle is complete.

## Rolling back

- **Plugin**: archive the Marketplace listing for the bad version
  (**Edit plugin → Versions → … → Unapprove**) and push a patch
  version with the fix. Previously-installed users get the rollback
  automatically on next IDE update.
- **npm**: you cannot `npm unpublish` after 72 hours or while other
  packages depend on the version. Always prefer `npm deprecate` with
  a clear message pointing at the fixed version.
- **Tags**: `git tag -d <tag>` then
  `git push origin :refs/tags/<tag>`. Avoid deleting tags that other
  clones have already fetched — prefer to move forward with a new
  version.
