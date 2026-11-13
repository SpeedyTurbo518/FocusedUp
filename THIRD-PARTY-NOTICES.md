# Third-party notices

Focused Up is built with the open-source components below. Their licenses allow use in a free or commercial app, as long as their notices are kept.

| Component | Used for | License |
|---|---|---|
| Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime (JetBrains) | Language and core libraries | Apache License 2.0 |
| Compose Multiplatform, Material 3, Material Icons (JetBrains / Google) | User interface on every platform | Apache License 2.0 |
| AndroidX Core, Activity, Lifecycle (Google) | Android app | Apache License 2.0 |
| Gradle wrapper | Builds | Apache License 2.0 |
| **JLayer 1.0.1** (JavaZOOM) | Playing MP3 prayer music in the Windows/Mac app | **GNU LGPL 2.1** |
| Eclipse Temurin OpenJDK 17 runtime (bundled in the Windows installer) | Runs the desktop app | GPL 2.0 with the Classpath Exception |

## What the LGPL (JLayer) requires
JLayer is used unmodified as a separate library file (`jlayer-1.0.1.jar` inside the desktop app's `app` folder). Under the LGPL you may:
- use it in Focused Up without opening up Focused Up's own code,
- as long as this notice stays, the LGPL text is available, and users can swap in their own build of JLayer (replace the jar).

JLayer source and license: https://www.javazoom.net/javalayer/javalayer.html · LGPL 2.1: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

## Bundled Java runtime
The Windows installer includes a trimmed Eclipse Temurin (OpenJDK) 17 runtime. Its license and notices ship in `runtime/legal` inside the installed app. Source: https://adoptium.net

## Content
- Bible verses on the prayer screen are from the King James Version (public domain in most countries; in the UK it's under Crown patent).
- The alert chime (`mellow_chime.wav`) and the app icons were made for Focused Up.
- Prayer music and photos are chosen by each user from their own device and are never included in the app or this repository.

## Trademarks
Notion, Google, Gmail and Adobe are trademarks of their owners. Focused Up isn't affiliated with or endorsed by them; the names only describe what the app works with.
