# Architecture

This document explains how FocusWall is put together and why the main design choices were made.

## Overview

FocusWall has two entry points that share one data store and one renderer:

| Component | Role |
|---|---|
| `MainActivity` | The app screen. Users add, edit, complete and delete tasks and change wallpaper style settings. |
| `FocusWallpaperService` | The live wallpaper. Android runs it whenever FocusWall is the active wallpaper. |
| `TaskStore` (in `Task.kt`) | Single source of truth: tasks and settings saved as JSON in `SharedPreferences`. Also holds the ranking and due-date rules. |
| `WallpaperRenderer` | Draws the animated background and the task card onto any `Canvas`. Also hit-tests taps. |
| `WallpaperPreviewView` | A scaled-down, animated copy of the wallpaper shown inside the app. |

```
 MainActivity ──writes──▶ TaskStore ──change event──▶ FocusWallpaperService
      │                       ▲                               │
      │                       └──── tap → mark task done ─────┤
      ▼                                                       ▼
 WallpaperPreviewView ────────▶ WallpaperRenderer ◀───────────┘
```

## Data flow

1. **User adds a task** in `MainActivity`, which calls `TaskStore.add(...)`.
2. `TaskStore` saves the updated list to `SharedPreferences`.
3. `SharedPreferences` notifies its registered listeners:
   - `FocusWallpaperService.FocusEngine` reloads tasks and redraws immediately.
   - `MainActivity` refreshes the list and the preview.
4. **User taps a task on the home screen.** The launcher sends `WallpaperManager.COMMAND_TAP` with the tap coordinates. The engine asks the renderer which row was hit, plays a 450 ms check animation, then calls `TaskStore.setDone(...)`, which triggers step 3 again.

## Key design decisions

### One renderer for wallpaper and preview
The preview draws through the same `WallpaperRenderer` at full screen resolution and then scales the canvas down.
The in-app preview therefore always matches the real wallpaper, and drawing logic lives in one place.

### SharedPreferences instead of a database
The data set is small (tens of tasks) and both components run in the same process.
`SharedPreferences` gives built-in change notifications with no extra libraries. Room/SQLite would add complexity without benefit at this scale.

### Zero third-party dependencies
Everything uses the Android framework (`WallpaperService`, `Canvas`, `java.time`, `org.json`).
This keeps the APK small and the build simple, and removes dependency-update maintenance.

### Battery efficiency
- The frame loop runs only while the wallpaper is **visible**. `onVisibilityChanged(false)` cancels all scheduled frames.
- Frame rate depends on style: 30 fps for Aurora and Particles, 10 fps for Calm, roughly halved in battery-saver mode.
- Next-frame delay subtracts the time already spent drawing, so the rate stays steady without busy-waiting.
- A hardware-accelerated canvas (`lockHardwareCanvas`) is used, with automatic fallback to software.

### Low per-frame allocation
Rendering runs up to 30 times per second, so the renderer avoids creating objects inside the frame loop:
- Gradient shaders are created once per screen size and repositioned with `canvas.translate`.
- Row hit-boxes come from a reusable pool of `RectF` objects.
- Truncated ("…") task titles are cached.
- Particle positions are a pure function of time, so no per-particle state needs updating.

## Ranking rules
Open tasks are sorted by:
1. **Priority**: High, then Medium, then Low
2. **Due date**: nearest first; tasks without a date go last
3. **Created time**: oldest first

The wallpaper shows the first N (3, 5 or 7, user setting). The rest are summarised as "+N more".

## Platform limitations
- **Lock screen taps:** Android doesn't forward lock-screen touches to wallpapers, so completion works on the home screen only.
- **Launcher support:** tap-to-complete depends on the launcher sending `COMMAND_TAP`. Most stock launchers do.
- **No sync:** data is device-local by design (see [PRIVACY.md](PRIVACY.md)).
