# Contributing

Use a feature branch and pull request. Keep each change focused.
Use Conventional Commit messages.

Build with JDK 17 and Android SDK 37:

```sh
./gradlew assembleDebug assembleRelease lintRelease
```

For behavior changes, run the relevant device checks on a disposable emulator.
Include the generated report and any device or app-version limitations.
Do not add tests that only repeat the implementation.

Keep data offline. Preserve Room data with explicit migrations.
Keep signing keys, passwords, phone backups, and test artifacts out of Git.
Reuse the existing Pebble artwork and Compose components for interface changes.
