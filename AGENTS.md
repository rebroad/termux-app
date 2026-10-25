# Repository Guidelines

## Project Structure & Module Organization

This is the Termux Android app and terminal-emulation repository; packages installed inside Termux belong in `termux/termux-packages`. The main Android application is under `app/`. Reusable code is split into `termux-shared/`, terminal rendering and emulation live in `terminal-view/` and `terminal-emulator/`, and their tests are under each module's `src/test/` or `src/androidTest/`. Documentation is in `README.md` and `docs/`; release artwork and scripts are in `art/`, while CI workflows are in `.github/workflows/`.

## Build, Test, and Development Commands

Use Java 17 and the checked-in Gradle wrapper:

```sh
./gradlew test                 # Run JVM unit tests for all modules
./gradlew assembleDebug        # Build debug APK variants
./gradlew :terminal-emulator:test  # Run one module's tests
```

In this workspace, edit the source tree and run builds only in the designated sibling build tree (`termux-app.build` or `termux-app.make`); synchronize first with `cpto`. Do not commit generated build outputs.

## Coding Style & Naming Conventions

Follow the existing Java and Android style: four-space indentation, descriptive `UpperCamelCase` classes, `lowerCamelCase` methods and fields, and package names rooted at `com.termux`. Keep app/plugin-specific shared code under `com.termux.shared.termux`; put general utilities elsewhere in `termux-shared`. Avoid hardcoded Termux paths or package constants. Check `.editorconfig` and preserve Android resource naming conventions such as lowercase snake case.

## Testing Guidelines

Use JUnit 4 tests, with Robolectric where Android framework behavior is needed. Name test classes after the class or behavior under test, ending in `Test`. Add tests alongside the module being changed and run `./gradlew test` before submitting; use a module-qualified task for focused iteration.

## Commit & Pull Request Guidelines

Use Conventional Commits with a capitalized type and present-tense description, for example `Fixed(terminal): Fix cursor resize`. Valid types are `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, and `Security`; use `!` before the colon for breaking changes. PRs should explain the behavior change, link relevant issues, include test results, and attach screenshots or reproduction details for UI or Android-device changes. Keep `versionName` in semantic `major.minor.patch` form when changing releases.

## Security & Configuration

Never include private logs, signing keys, or credentials in commits. `app/testkey_untrusted.jks` is only a public test key; do not treat it as an official release key. Review `SECURITY.md` for vulnerability reports.

The fork-local signing-file locations and signed-build procedure are documented in `docs/rebroad-signing.md`; the files themselves remain outside the repository.
