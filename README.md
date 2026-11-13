# Focused Up

A dark, glassy focus app made by **STG Studios, a faith based company**. It runs on **Android, Windows, Mac and iPhone** from one shared codebase (Kotlin + Compose Multiplatform). Everything is stored on your device; the only online parts are the optional Notion feed and profile sharing.

## What's in it

| Tab | What's there |
|---|---|
| **Focus** | One big power button to start Focus Mode, plus **Today's tasks**: type a task and press Enter, or paste a whole list (one task per line). Unfinished tasks carry over to the next day. |
| **Timer** | **01 · Quick Sprint** (40 min), **02 · Deep Dive** (1 hr 30), **03 · Full Armor** (1 hr 45), or a **custom time**. It can also start and stop Focus Mode for you. |
| **Calendar** | A month view with your own events (with optional push notifications), logged focus sessions, and an optional **Notion feed** (Android): your upcoming recordings with date and time, **Remind me** buttons, a chime when your content goes to review or a video upload finishes, and **Clear** for finished shoots. |
| **On Task** | For sales-floor and retail shifts: shift timer, check-in nudges, break and lunch reminders, a floor checklist and a scoreboard (customers helped, sales, add-ons, sales goal). Shifts are logged to the calendar. |
| **Profile** | Name, tagline, profile color, cover photo and profile photos, your stats, and **Share** (a link that opens the app, or asks before offering the download). |
| **Setup** | **App appearance** (colors, hue/contrast/saturation, app icon), prayer music, permissions, full screen (desktop) and About. |

After a short splash screen, a **prayer page** opens once a day and starts a 15-minute countdown with your own background music. Snooze asks "You should really pray before working, Are you sure you want to continue?" and brings the page back after 30 minutes.

### What Focus Mode does on each device
| | Do Not Disturb | Silencing |
|---|---|---|
| **Android** | ✅ Switched on automatically ("alarms & media only") | ✅ Ringer, notification and system sounds off; media stays on for Adobe apps |
| **Windows** | Windows has no API for this. Setup has a button to open the setting. | ✅ **Mutes every app except Adobe Creative Cloud** (the app list is editable in Setup), then unmutes exactly what it muted |
| **Mac** | macOS doesn't allow it. Turn on a Focus in Control Center. | macOS doesn't allow muting other apps |
| **iPhone** | Apple doesn't allow it. Turn on a Focus in Control Center. | Apple doesn't allow it. Use the Ring/Silent switch. |

Timer, tasks, calendar and splash screen work the same everywhere.

## Downloads
Get the latest build from **[Releases](https://github.com/SpeedyTurbo518/FocusedUp/releases/latest)**:
- `FocusedUp-android.apk`: Android. Open it and allow the install when Android asks.
- `FocusedUp-windows-installer.msi`: Windows. Installs for your user, no admin rights needed. SmartScreen may warn because the app isn't code-signed: **More info → Run anyway**.

Shared profile links open **[the share page](https://speedyturbo518.github.io/FocusedUp/share/)** (in `docs/share`, served by GitHub Pages), which opens the app or asks before offering the download.

## Privacy and security
- No accounts or servers. Tasks, calendar, photos, music and settings stay on the device.
- **Notion (Android):** you sign in on Notion's own page inside the app; the app stores that session on the phone only (excluded from Android backups) and only *reads*. Because guests can't add official Notion integrations, the feed reads through Notion's website the same way a browser does. That isn't an official Notion API, so a Notion change could break it.
- Profile share links carry only name, tagline, color and stats, in the part of the link after `#`, which browsers never send to a server.
- Optional Notion login keys go in `focusedup.properties` (see `focusedup.properties.example`), which is git-ignored and never committed.

## Building
With JDK 17 installed, from this folder:
```
./gradlew :androidApp:assembleRelease      # Android APK
./gradlew :desktopApp:packageMsi           # Windows installer
./gradlew :desktopApp:run                  # run the desktop app directly
```
Release APKs are signed with your local debug key for sideloading. Use your own signing key for a store release.

### Mac app (needs a Mac)
1. Install JDK 17, copy this folder over, then run `./gradlew :desktopApp:packageDmg`.
2. Optional: make an icon with `iconutil` from `desktopApp/icons/FocusedUp.png`, save it as `desktopApp/icons/FocusedUp.icns`, and add `iconFile.set(project.file("icons/FocusedUp.icns"))` to the `macOS { }` block.

### iPhone app without a Mac or paid account
GitHub Actions builds `FocusedUp.ipa` on a free Mac (`.github/workflows/ios.yml`), and AltStore installs it with a free Apple ID. Step-by-step: **[INSTALL-IPHONE.md](INSTALL-IPHONE.md)**.

### iPhone app (with your own Mac and Xcode)
1. `brew install xcodegen`
2. `cd iosApp && xcodegen && open FocusedUp.xcodeproj`
3. In Xcode, pick your Apple ID under **Signing & Capabilities → Team**, plug in your iPhone and press **Run**. The first build compiles the shared Kotlin code (a few minutes).
- With a free Apple ID, the app runs on your phone for 7 days before it needs re-installing. A paid Apple Developer account ($99/yr) removes that limit and allows TestFlight and the App Store.

> The iPhone and Mac builds use the same shared code as Android and Windows, which are built and tested. The iPhone-specific file (`shared/src/iosMain`) can only be compiled on a Mac, so it has not been compiled yet. Expect a possible small fix on the first Xcode build.

## Project layout
```
shared/                    code used by every platform
  commonMain/.../engine    Focus Mode + timer logic
  commonMain/.../data      tasks, calendar, settings, Notion feed, sharing
  commonMain/.../ui        splash, theme, glass components, all screens
  iosMain/                 iPhone storage + notifications + entry point
androidApp/                Android: Do Not Disturb, volume, alarms, notification service
desktopApp/                Windows/Mac: window, tray, Windows per-app muting
  native/FocusAudio.cs     tiny Windows helper that mutes/unmutes app audio
iosApp/                    iPhone app shell (XcodeGen project + SwiftUI entry)
docs/share/                share-link page (GitHub Pages)
```

## License
See [LICENSE](LICENSE). Open-source components and their licenses are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
