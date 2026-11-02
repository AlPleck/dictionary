# Repository Guidelines

## Project Structure

This directory is the `:app` Android module; the Gradle wrapper, settings, and version catalog are in the parent project directory. Kotlin application code and resources live in `src/main/java` and `src/main/res`. Local JVM tests belong in `src/test/java`; device or emulator tests belong in `src/androidTest/java`. The app currently uses Jetpack Compose and the package `com.example.myapplication`.

## Build, Test, and Development

Run these commands from the parent project directory:

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew :app:testDebugUnitTest` runs local JUnit tests.
- `./gradlew :app:connectedDebugAndroidTest` runs instrumented tests on a connected device or emulator.
- `./gradlew :app:installDebug` builds and installs the debug app on a connected device.

## Coding Style and Naming

Use Kotlin conventions and four spaces for indentation. Keep packages lowercase, classes and objects in `PascalCase`, and functions and properties in `camelCase`. Name Compose UI functions in `PascalCase`; use lowercase `snake_case` for Android resource filenames. Keep dependency versions in the parent project's `gradle/libs.versions.toml` and module-specific dependencies in this directory's `build.gradle.kts`.

## Testing Guidelines

Name test files and classes for the behavior or component they cover, ending in `Test` (for example, `MainActivityTest.kt`). Prefer local JUnit tests for logic that does not need Android; use instrumented tests for framework, UI, and device behavior. No coverage threshold is configured. Run the relevant test task before submitting changes.

## Commits and Pull Requests

This checkout has no Git history to establish a repository-specific commit convention. Use a short imperative commit subject, such as `Add profile navigation`. Pull requests should explain the change, list verification commands and results, link related issues when applicable, and include screenshots for visible UI changes.

## Configuration

Do not commit signing credentials, API secrets, generated build output, or machine-specific SDK configuration. Keep app behavior and dependencies aligned with the existing Gradle setup unless the task requires a deliberate change.
