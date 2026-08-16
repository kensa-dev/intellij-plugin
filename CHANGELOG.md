<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Kensa IntelliJ Plugin Changelog

## [Unreleased]

## [0.8.4] - 2026-08-16

### Added

- Right-click a gutter icon to open the **CI** report. Left-click still opens the local report in a single click; when a CI report URL template is configured (Settings → Tools → Kensa), the gutter icon's right-click menu now offers "Open Local Report" and "Open CI Report", and its tooltip advertises the CI option. Previously the local/CI chooser lived only on the Alt+Enter intention menu, which devs rarely discovered, so a configured CI template appeared to do nothing — the gutter always jumped straight to the local report. `KensaGutterLineMarkerProvider` supplies the chooser through the gutter renderer's `getPopupMenuActions()`, so the fast local path is unchanged.

### Changed

- The plugin no longer declares dependencies on the JUnit, Gradle, and Maven plugins. None of their APIs were used (the test-run listener uses the platform's SM test runner, and location URLs are string-parsed), but the declarations meant the Kensa plugin silently refused to load for anyone who had disabled the bundled Maven or Gradle plugin. Only the platform and Java module dependencies remain.

### Fixed

- Reopening a project in the same IDE session no longer loses all Kensa results until a report is rewritten. The index loader was an application-wide singleton whose "skip unchanged files" mtime gate outlived the project, so the reopened project's (empty) results cache was never repopulated — gutter icons and the status bar stayed blank. The loader is now a project-scoped service, so its state dies with the project; this also fixes two windows open on the same directory sharing one gate.
- `indices.json` reads and JSON parsing no longer run on the UI thread. VFS change events are delivered on the EDT inside a write action, and the listener parsed report files inline there — a UI freeze proportional to report size on every test run. The listener (`KensaVfsListener`) now only filters paths inline and hands file IO, parsing, and stale-entry pruning to a single-threaded background executor, preserving event order.
- Gutter icon popup no longer risks `PsiInvalidElementAccessException`. The popup-menu callback is invoked speculatively during highlighting, by which time the marker's PSI element may have been invalidated; the provider now captures the project and target up front and never dereferences the element from callbacks.
- High idle CPU. `KensaOutputFileWatcherStartupActivity` polled for new reports by walking the **entire project tree** (including `build/`, `.git`, `node_modules`) every 5 seconds, forever — continuous filesystem I/O even when the IDE was idle. The walk existed because reports written by terminal/external test runs land under an excluded `build/` dir that VFS never reports. It is replaced by three bounded layers: (1) a native file-watch (`addRootToWatch`) on each discovered report dir, so changes to known reports surface instantly with no polling; (2) a 3-second targeted probe of only the build/output dirs — read from the module model's excluded roots (Gradle `build`, Maven `target`) — checking the fixed report shape (`<buildDir>/<reportDir>/indices.json` or `…/sources/<id>/indices.json`) so brand-new reports appear quickly without ever walking a build dir's contents; (3) a 60-second pruned safety walk for reports in unconventional locations, which also refreshes the build-root set and prunes stale entries. Idle CPU drops to near zero while a terminal test run still updates gutter icons within ~3 seconds (instantly for already-known reports). Site-mode bundles (`build/<site>/sources/<id>/`) are handled across all three layers.

## [0.8.2]

### Fixed

- Run-window "Open Kensa Report" toolbar icon now appears reliably after Gradle-delegated test runs. `KensaTestRunListener` captured the run tab's `RunContentDescriptor` in `onTestingStarted` — too early for Gradle delegation, where the Build window holds focus while Gradle ramps up, so classes were filed under a stale or empty descriptor that the toolbar's `update()` never resolved. The listener now accumulates the run's classes on the stable test-root proxy and binds them to the descriptor once, at `onTestingFinished`, when the test tab is reliably the selected content.
- Toolbar icon no longer renders as a giant full-width button that opens the report when clicked anywhere on the toolbar. The shared action icon (`/icons/logo.svg`) declared a 512×512 intrinsic size, so IntelliJ sized the `ActionButton` to fill the whole toolbar; it is now a normal 16×16 action icon.
- Opening a report from the run-window toolbar icon or the test-tree menu now selects the test in single-source (non-site) reports. `buildKensaRoute` omitted the source prefix for single-source bundles, producing `#/test/<class>`, but the report keys class nodes as `<sourceId>::<class>` and falls back to source id `default`; the route now always carries the prefix, so the report lands on the test instead of opening unselected.

## [0.8.1]

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

[Unreleased]: https://github.com/kensa-dev/intellij-plugin/compare/0.8.4...HEAD
[0.8.4]: https://github.com/kensa-dev/intellij-plugin/compare/0.8.2...0.8.4
[0.8.2]: https://github.com/kensa-dev/intellij-plugin/compare/0.8.1...0.8.2
[0.8.1]: https://github.com/kensa-dev/intellij-plugin/compare/0.7.1...0.8.1
[0.7.1]: https://github.com/kensa-dev/intellij-plugin/compare/0.6.7...0.7.1
[0.6.7]: https://github.com/kensa-dev/intellij-plugin/compare/0.6.6...0.6.7
[0.6.6]: https://github.com/kensa-dev/intellij-plugin/compare/0.6.5...0.6.6
[0.6.5]: https://github.com/kensa-dev/intellij-plugin/commits/0.6.5
