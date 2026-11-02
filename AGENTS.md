# Repository Guidelines

## Project Structure & Modules

This is a single-module Android app. The `:app` module contains Kotlin source under `app/src/main/java/com/example/myapplication`, Android resources under `app/src/main/res`, local JVM tests under `app/src/test/java`, and device tests under `app/src/androidTest/java`. The root Gradle files configure the project; dependency versions are managed in `gradle/libs.versions.toml`. Build outputs and local SDK settings are generated or machine-specific and should not be committed.

## Build, Test, and Development

Run commands from the repository root using the Gradle wrapper:

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew :app:testDebugUnitTest` runs local JUnit tests.
- `./gradlew :app:connectedDebugAndroidTest` runs instrumented tests on a connected device or emulator.
- `./gradlew :app:installDebug` builds and installs the app on a connected device.

## Coding Style & Naming

Use Kotlin conventions with four spaces for indentation. Keep package names lowercase, types in `PascalCase`, and functions and properties in `camelCase`. Name Compose UI functions in `PascalCase`; name Android resource files in lowercase `snake_case`. Keep module dependencies in `app/build.gradle.kts` and centralize dependency versions in the version catalog. Follow the existing Jetpack Compose and Material 3 patterns.

## Testing Guidelines

Use local JUnit tests for logic that does not require Android, and instrumented tests for framework, UI, or device behavior. Name test classes and files for the behavior under test and end them with `Test` (for example, `NavigationTest`). No coverage threshold is configured. Run the relevant Gradle test task for changes that affect tested behavior.

## Commits & Pull Requests

Git history is unavailable in this checkout, so no established commit convention could be verified. Use concise, imperative commit subjects, such as `Add profile navigation`. Pull requests should describe the user-facing change, list verification commands and results, link related issues when relevant, and include screenshots for UI changes.

## Configuration & Security

Do not commit signing credentials, API secrets, `local.properties`, or generated `build/` output. Avoid changing SDK, Gradle, or dependency versions unless required by the work, and keep such changes explicit in the review description.
