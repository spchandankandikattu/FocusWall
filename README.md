# FocusWall: Your To-Do List as a Live Wallpaper

> An Android live wallpaper that keeps your most important tasks on your home screen, so you see them every time you unlock your phone.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)
![Dependencies](https://img.shields.io/badge/third--party%20libraries-0-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

<p align="center">
  <img src="screenshots/home.jpg" width="230" alt="Home screen with FocusWall" />
  <img src="screenshots/app.jpg" width="230" alt="FocusWall app screen" />
  <img src="screenshots/themes.jpg" width="230" alt="Background styles" />
</p>

<p align="center"><img src="screenshots/demo.gif" width="260" alt="Tap-to-complete demo" /></p>

---

## The problem
To-do apps fail quietly: you write tasks down, close the app, and never open it again.
A list you have to go looking for is a list you forget.

## The idea
Put the list where you already look about 100 times a day: the **home screen wallpaper**.
FocusWall shows only the few tasks that matter most, ranked automatically, and lets you tick them off with one tap.

## Features
| Feature | Details |
|---|---|
| **Live animated backgrounds** | Three styles: *Aurora* (drifting colour glows), *Particles* (floating star network), *Calm* (slow colour shift) |
| **Smart "Today's Focus" card** | Shows the top 3 / 5 / 7 open tasks, ranked by priority, then nearest due date, then age |
| **Due-date awareness** | Overdue tasks turn red, tasks due today turn amber, the rest show a relative date ("Due Friday") |
| **Tap to complete on the home screen** | Tap a task on the wallpaper; a check animation plays and the task disappears |
| **Instant sync** | Add or edit a task in the app and the wallpaper updates immediately |
| **Live in-app preview** | A miniature, animated copy of your wallpaper inside the app |
| **Battery-aware** | Rendering stops completely when the wallpaper is hidden; optional battery-saver frame rate |
| **Private by design** | No internet permission, no accounts, no analytics; all data stays on the device |

## How it works

```
┌──────────────────┐   writes    ┌───────────────────────┐   change listener   ┌────────────────────────┐
│   MainActivity   │ ──────────▶ │       TaskStore       │ ──────────────────▶ │  FocusWallpaperService │
│ (add/edit tasks, │             │ (SharedPreferences +  │                     │  (live wallpaper       │
│  style settings) │ ◀────────── │  JSON, sorting rules) │ ◀────────────────── │   engine, frame loop)  │
└────────┬─────────┘   reads     └───────────────────────┘   tap → mark done   └───────────┬────────────┘
         │                                                                                 │
         │                 ┌──────────────────────────────────────────┐                    │
         └───────────────▶ │            WallpaperRenderer             │ ◀──────────────────┘
          in-app preview   │ (backgrounds, task card, hit-testing)    │   draws every frame
                           └──────────────────────────────────────────┘
```

- **One renderer, two surfaces.** `WallpaperRenderer` draws both the real wallpaper and the in-app preview, so the preview is pixel-identical to the phone.
- **Live wallpaper engine.** `FocusWallpaperService` extends Android's `WallpaperService`. It runs a frame loop only while visible (30 fps, or 15 fps / 10 fps depending on style and battery saver) and uses a hardware-accelerated canvas, falling back to software.
- **Reactive updates.** The engine listens for storage changes, so a task added in the app appears on the wallpaper without a restart.
- **Home-screen taps.** Launchers forward taps on empty space to the wallpaper as `WallpaperManager.COMMAND_TAP`. The renderer hit-tests the tap against task rows, plays a 450 ms check animation, then marks the task done.
- **Ranking logic.** Open tasks sort by priority (High > Medium > Low), then by nearest due date (no date goes last), then oldest first.
- **Efficient drawing.** Gradient shaders are built once per screen size, rows reuse preallocated rectangles, and truncated titles are cached, which keeps per-frame memory allocation near zero.

## Tech stack
- **Kotlin**, Android SDK 35 (minimum Android 8.0 / API 26)
- Android framework only: `WallpaperService`, `Canvas`, `SharedPreferences`, `java.time`. No third-party libraries.
- Gradle (Kotlin DSL) with **GitHub Actions CI** that builds the APK on every push

## Project structure
```
app/src/main/java/com/akash/focuswall/
├── Task.kt                   Task model, storage (SharedPreferences + JSON), ranking, due-date labels
├── WallpaperRenderer.kt      Animated backgrounds + task card + tap hit-testing (shared renderer)
├── FocusWallpaperService.kt  Live wallpaper engine: frame loop, visibility handling, tap-to-complete
├── WallpaperPreviewView.kt   Miniature live preview inside the app
└── MainActivity.kt           App UI: add / edit / delete tasks, style settings, set wallpaper
```

## Try it
**Download:** grab the latest APK from the [Releases](../../releases) page, open it on an Android phone, and allow the install when prompted.

**Build from source:**
1. Clone the repo and open it in Android Studio.
2. Let Gradle sync, then press **Run** with a phone connected (USB debugging on).
3. Or run `./gradlew assembleDebug`. The APK is written to `app/build/outputs/apk/debug/`.

## Known limitations
- Tap-to-complete works on the home screen only. Android doesn't pass lock-screen taps to wallpapers, so the lock screen shows tasks read-only.
- Tasks are stored locally; there's no cloud sync between devices.

## Roadmap
- [ ] Recurring tasks (daily / weekly)
- [ ] Home-screen widget for quick add
- [ ] Google Tasks / Calendar import
- [ ] Custom photo backgrounds with the task card on top

## Documentation
- [ARCHITECTURE.md](ARCHITECTURE.md): how the app is designed and why
- [CHANGELOG.md](CHANGELOG.md): version history
- [CONTRIBUTING.md](CONTRIBUTING.md): how to report bugs or contribute
- [PRIVACY.md](PRIVACY.md): what data the app does (and doesn't) touch

## Author
**S P CHANDAN KANDIKATTU** · [LinkedIn](https://www.linkedin.com/in/chandan-kandikattu/) · [GitHub](https://github.com/spchandankandikattu)

## License
MIT. See [LICENSE](LICENSE).
