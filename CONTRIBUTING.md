# Contributing to FocusWall

Thanks for your interest! Bug reports, ideas and pull requests are all welcome.

## Reporting a bug
Open an issue and include:
- Phone model and Android version (e.g. Vivo T4, Android 15)
- What you did, what you expected, and what happened
- A screenshot or screen recording if possible

## Suggesting a feature
Open an issue describing the problem it solves. Check the roadmap in the [README](README.md) and [CHANGELOG](CHANGELOG.md) first.

## Making a change
1. Fork the repository and create a branch: `git checkout -b feature/short-description`
2. Open the project in Android Studio and make your change.
3. Test on a real device or emulator: set FocusWall as the live wallpaper and check both the wallpaper and the app screen.
4. Keep the project dependency-free unless there's a strong reason (explain it in the PR).
5. Commit with a clear message, e.g. `Add recurring daily tasks`.
6. Open a pull request describing what changed and why, with a screenshot for any visual change.

## Code style
- Kotlin, following the official Kotlin style (Android Studio: *Code › Reformat Code*)
- Avoid creating objects inside the wallpaper's draw loop (see [ARCHITECTURE.md](ARCHITECTURE.md))
- Keep functions small and name things for what they do

## Project structure
See [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces fit together.
