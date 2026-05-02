# Kensa IntelliJ Plugin

![Build](https://github.com/kensa-dev/intellij-plugin/workflows/Build/badge.svg)

<!-- Plugin description -->
IntelliJ plugin for the [Kensa](https://github.com/kensa-dev/kensa) testing framework.

[Kensa](https://kensa.dev) is a Java/Kotlin testing library that captures expressive, human-readable test reports as HTML. This plugin brings those reports closer to your IDE.

## Features

- **Gutter icons** — pass/fail indicators appear next to `@Test` and `@ParameterizedTest` methods and their containing classes, updated automatically as tests run.
- **Click to open** — click any gutter icon to open the corresponding Kensa HTML report in your browser, navigating directly to that test or class.
- **CI report support** — configure a CI report URL template in Settings → Tools → Kensa to open remote reports when no local report is available, or choose between local and CI from a popup.
- **Console hyperlinks** — the `Kensa Output :` marker in test console output becomes a clickable link that opens the report directly.
- **Live notifications** — a balloon notification fires when a new report is written, with an _Open Report_ action.

For an overview of what Kensa is and why it exists, see the [introductory blog post](https://kensa.dev/blog/introducing-kensa).

## Requirements

Kensa must be configured in your project. See the [Kensa documentation](https://kensa.dev) and the [Kensa GitHub repository](https://github.com/kensa-dev/kensa) for setup instructions.
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Kensa"</kbd> > <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/kensa-dev/intellij-plugin/releases/latest) and install it using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>
