# Release Flow

The plugin's "What's New" panel in IDEA is generated from `CHANGELOG.md` via the
[`org.jetbrains.changelog`](https://github.com/JetBrains/gradle-changelog-plugin) Gradle plugin.
At build time, the section matching `pluginVersion` is rendered as HTML and embedded into
`plugin.xml` as `<change-notes>`.

## Authoring changes

- Add every user-facing change under the `[Unreleased]` section of `CHANGELOG.md`.
- Group entries under `Added`, `Changed`, `Fixed`, or `Removed`.
- **Keep entries user-facing.** Do not list CI, test infrastructure, internal refactors,
  or build tooling changes — the audience is plugin users reading the IDEA "What's New" popup.

## Cutting a release

1. Bump `pluginVersion` in `gradle.properties`.
2. Run `./gradlew patchChangelog` — this moves `[Unreleased]` into a new versioned section
   (e.g. `[0.4.0]`) and recreates an empty `[Unreleased]` block.
3. Review the diff in `CHANGELOG.md` and adjust wording if needed.
4. Run `./gradlew buildPlugin` — the rendered HTML for the current version is embedded into
   `plugin.xml`'s `<change-notes>`.
5. Commit `gradle.properties` and `CHANGELOG.md` together, tag the release, and publish.

## How it's wired

- `build.gradle.kts` applies the `org.jetbrains.changelog` plugin and configures the
  `changelog { }` block with the current version and group labels.
- `pluginConfiguration.changeNotes` in `build.gradle.kts` looks up the section matching
  `pluginVersion`, falling back to `[Unreleased]` if no match is found, and renders it as HTML.
