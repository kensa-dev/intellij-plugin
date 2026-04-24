<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Kensa IntelliJ Plugin Changelog

## [Unreleased]

## [0.7.0] - 2026-04-24

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

[Unreleased]: https://github.com/kensa-dev/kensa-intellij-plugin/compare/0.7.0...HEAD
[0.7.0]: https://github.com/kensa-dev/kensa-intellij-plugin/compare/0.6.7...0.7.0
[0.6.7]: https://github.com/kensa-dev/kensa-intellij-plugin/compare/0.6.6...0.6.7
[0.6.6]: https://github.com/kensa-dev/kensa-intellij-plugin/compare/0.6.5...0.6.6
[0.6.5]: https://github.com/kensa-dev/kensa-intellij-plugin/commits/0.6.5
