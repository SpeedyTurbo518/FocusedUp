# Install Focused Up on your iPhone (free, with AltStore)

No paid Apple Developer account needed. GitHub builds the app on a free Mac, and AltStore installs it with your normal Apple ID and keeps it working.

## 1. Get the app file (.ipa)
1. Open **github.com/SpeedyTurbo518/FocusedUp → Actions**.
2. Click the latest green ✓ **Build iPhone app (.ipa)** run.
3. Under **Artifacts**, download **FocusedUp-ipa**. It downloads as a `.zip`; unzip it to get **`FocusedUp.ipa`**.

To build again after code changes, go to Actions → Build iPhone app (.ipa) → **Run workflow**. Pushing changes to `shared/` or `iosApp/` also starts a build automatically.

## 2. One-time PC setup (Windows)
Install these. Get iTunes and iCloud **from Apple's website**, not the Microsoft Store, because AltServer needs the website versions:
- **iTunes**: apple.com/itunes → "Windows" → download the 64-bit installer
- **iCloud**: support.apple.com → search "iCloud for Windows" → download from Apple's site
- **AltServer**: altstore.io → Download → **AltServer for Windows**

Open iCloud and sign in with your Apple ID. Then run **AltServer**; it lives in the system tray (bottom-right, ◆ icon).

## 3. Put AltStore on the iPhone (one time)
1. Plug the iPhone into the PC with a cable. Tap **Trust** on the phone and enter your passcode.
2. Open **iTunes**, click the phone icon and tick **"Sync with this iPhone over Wi-Fi"**, then **Apply**. This lets AltStore refresh apps without the cable later.
3. Click the AltServer tray icon → **Install AltStore** → pick your iPhone. Sign in with your Apple ID when it asks. The login goes straight to Apple.
4. On the iPhone, go to **Settings → Privacy & Security → Developer Mode → On**. The phone restarts; confirm "Turn On".
5. Go to **Settings → General → VPN & Device Management**, tap your Apple ID and tap **Trust**.

## 4. Install Focused Up
1. Get `FocusedUp.ipa` onto the iPhone. Easiest: save it to **iCloud Drive** on the PC, or email it to yourself and save the attachment to Files.
2. Open **AltStore** on the iPhone → **My Apps** → tap **+** (top-left) → choose `FocusedUp.ipa`.
3. Wait for it to install. Focused Up appears on your home screen and works fully offline.

## 5. Keep it working (every 7 days)
Free Apple IDs sign apps for 7 days. AltStore renews them automatically in the background when:
- the PC is on with **AltServer running**, and
- the iPhone is on the **same Wi-Fi**.

You can also open AltStore → **My Apps** → **Refresh All** any time. If an app ever expires, it just won't open until you refresh; your tasks and calendar stay saved on the phone.

Limits of a free Apple ID: 3 sideloaded apps at a time (AltStore counts as one), and 10 app IDs per week.

## On iPhone, Focus Mode means…
Apple doesn't let any app turn on Focus or silence the phone. When you start a session, swipe down to **Control Center → Focus → Do Not Disturb** (or Work). The timer, tasks, calendar and session-complete alert all work inside the app.
