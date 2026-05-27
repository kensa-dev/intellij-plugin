<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Kensa IntelliJ Plugin Changelog

## [Unreleased]

## [0.8.0] - 2026-05-11

### Added

- Site-mode discovery: gutter icons and the test-tree context menu now recognise Kensa 0.8 site bundles (`build/kensa-site/sources/<id>/`) in addition to the default `kensa-output/` layout. The plugin walks for `indices.json` whose grandparent is `sources/` and whose great-grandparent has a `manifest.json`.
- Multi-source routing: when a test class lives in a Gradle source set whose name matches a Kensa source id (the default mapping), gutter clicks open the site shell with a `<sourceId>::<class>` route, so the report sidebar lands on the correct source. Same class run in two source sets stays separated; the gutter icon for a file in `src/uiTest/...` reflects the `uiTest` source's status.
- Tools → "Install Kensa Agent Skills…" action (also surfaced in Settings → Tools → Kensa) that writes the `kensa-development` AI skill into a project. Targets: GitHub Copilot (path-scoped or always-loaded), JetBrains Junie, Cursor, and Claude Code. Skill files are bundled with the plugin and pinned to the Kensa version in `version.txt` (sourced from [`kensa-dev/agent-skills`](https://github.com/kensa-dev/agent-skills)).

### Fixed

- `KensaTestRunListener` failed to instantiate on IntelliJ 2026.1+ (build 261+) with `Cannot find suitable constructor`, breaking gutter status updates and the engagement notification on that build line. Added `@JvmOverloads` so the platform's stricter constructor lookup finds the `(Project)` overload.
- Possible fixes for unreliable display of test runner icon & Kensa Report bubble.
- Opening a report from a site-mode source bundle whose shell isn't on disk now produces a clear "run `./gradlew assembleKensaSite`" warning, instead of silently failing.
- Project-view "Open Kensa Report" group now lists site-mode reports (`build/kensa-site/index.html`), not just `kensa-output/` bundles. Previously the group's children walker only matched the legacy parent name and rendered the menu disabled in site-mode projects.
- "Kensa Report Ready" balloon no longer fires twice when a multi-sourceset site build completes. The notification debounce is now per-`index.html` over a 60s window, so the two `sources/<id>/indices.json` writes that share one site shell coalesce into a single balloon.
- Run-tab "Open Kensa Report" toolbar icon stayed hidden after Gradle-delegated test runs. `KensaTestRunListener.onTestingStarted` was capturing the active `RunContentDescriptor` via `RunContentManager.selectedContent`, but for Gradle-delegated runs the Build tool window holds focus while Gradle ramps up — selected content is `null` (or the previous tab) at that moment, so `DESCRIPTOR_KEY` never gets written, `rootDescriptor()` returns null on every later event, and `KensaRunTabRegistry.seenClasses` stays empty for the whole session. The listener now lazy-captures: if `DESCRIPTOR_KEY` is unset when an event arrives, it reads `selectedContent` then and stores it back.
- Clicking the run-tab toolbar icon now opens the report routed to a test from the just-completed run (using the recorded class's `sourceId`), matching the gutter / test-tree right-click behaviour, instead of landing on the UI's default sidebar selection.
- Site-mode discriminator no longer requires `manifest.json` to be on disk — it matches the directory shape `…/kensa-site/sources/<id>/indices.json`, so reports load after running `:uiTest` standalone without `:assembleKensaSite`. The "open" path still surfaces the missing-shell warning when the user clicks before assembling the site.

## [0.7.1]

### Added

- Star prompt shown after every 5 Kensa test runs, with a "Don't ask again" option
- Status bar widget showing aggregated pass/fail/ignored counts with coloured state icons; click opens the latest report, or a picker listing per-module reports with counts when multiple `kensa-output` directories are present. Hidden when the project has no Kensa results
- Live preview in Settings → Tools → Kensa for the CI report URL template, rendered in red with a tooltip when the substituted URL is invalid

### Changed

- Run window toolbar icon is now hidden unless the active run tab itself produced Kensa output, rather than any Kensa test having ever run in the project
- Gutter icon statuses update live during test runs, not just on index file reload

### Removed

- Search Everywhere contributor

## [0.6.7]

### Added

- Project view context menu — right-click any folder to open Kensa reports found below it; one menu item per output directory, sorted alphabetically. Works across multi-module Gradle and Maven projects
- Ignored/disabled test state with amber gutter icon, in addition to existing pass/fail icons
- Configurable output directory name — Settings → Tools → Kensa → Output directory name (default: `kensa-output`), for projects that customise the Kensa output path

### Changed

- Gutter icons now appear on project open if output already exists, without requiring a file change or test run in the IDE
- Stale gutter icons are removed immediately when `indices.json` is rewritten (e.g. after running a subset of tests)
- Class-level gutter icon shown only when at least one method in that class appears in the index
- Method gutter icons shown only for methods present in the index

## [0.6.6]

### Fixed

- Plugin display name capitalisation on JetBrains Marketplace
- Synchronise with required Kensa version

## [0.6.5]

### Added

- Gutter icons showing pass/fail status for `@Test` and `@ParameterizedTest` methods and their containing classes
- Click gutter icon to open the Kensa HTML report in your browser, navigating directly to the relevant test or class
- CI report URL template support — configure a remote report URL in Settings → Tools → Kensa
- Console hyperlinks — `Kensa Output :` marker in test output becomes a clickable link to the report
- Live balloon notification when a new Kensa report is written, with an Open Report action
- Context menu action in the test tree to open a specific test in the browser

[Unreleased]: https://github.com/kensa-dev/intellij-plugin/compare/0.8.0...HEAD
[0.8.0]: https://github.com/kensa-dev/intellij-plugin/compare/0.7.1...0.8.0
[0.7.1]: https://github.com/kensa-dev/intellij-plugin/compare/0.6.7...0.7.1
[0.6.7]: https://github.com/kensa-dev/intellij-plugin/compare/0.6.6...0.6.7
[0.6.6]: https://github.com/kensa-dev/intellij-plugin/compare/0.6.5...0.6.6
[0.6.5]: https://github.com/kensa-dev/intellij-plugin/commits/0.6.5
