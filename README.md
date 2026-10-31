# StandBy TV

![StandBy TV screens](docs/preview.png)

An iOS-StandBy-style clock and widget screen for **Android TV 8.0+**. You can open it as an app or set it as the TV's screensaver.

**Package:** `com.yash.standbytv` · **minSdk:** 26 (Android 8.0) · **APK size:** about 160 KB · **No ads, no tracking**. The only library is ZXing, which draws the QR codes

---

## Screens

Use **◀ ▶** to move between screens and **▲ ▼** to change a screen's style. Each screen remembers its style.

| Screen | Styles | Notes |
|---|---|---|
| **Duo** | Analog + Calendar, Digital + Calendar, Analog + Weather, Digital + Weather, Weather + Calendar | The classic StandBy look: two rounded widgets side by side |
| **Big Clock** | 18 colour themes, including Diwali, Holi, Aurora, Peacock, Himalaya and Monsoon | Gradient digits that fill the screen. Some themes glow |
| **Flip Clock** | Graphite, Paper, Amber, Midnight Blue | Each flap animates in two stages |
| **Analog** | StandBy, Classic White, Minimal, Ticks | Shows the date and weather next to the dial |
| **Weather** | Sky, Black | The background colour follows the current conditions |
| **Now Playing** | Blurred Art, Card, Minimal | Spotify, YouTube Music, YouTube or any other player on the TV. **OK** shows ⏮ ⏯ ⏭ |
| **Photos** | Small clock, Large clock, Photos only | Slideshow from **Google Photos albums**, TV storage or a USB drive |
| **Clock of Clocks** | Dark, Light, Blueprint, Copper | 60 small analog clocks whose hands draw the time. They spin into place every minute |
| **Ants Clock** | Sunny, Night, Rainy, Snowy, AMOLED | 760 ants walk into the shape of the digits |
| **Solar Watch** | Sun Arc, 24-hour Dial | Shows where the sun is today, using real sunrise and sunset times |
| **Neon** | Hot Pink, Ice Cyan, Lime, Sunset Orange, Pink & Blue | Neon-sign glow on a brick wall, with an occasional flicker |
| **LED Clock** | Red LED, Green VFD, Ice Blue, Amber, White | Seven-segment display with faint unlit segments |
| **Pixel Art** | Landscape, Game Boy, Arcade | 8-bit digits over a landscape that changes from day to night |
| **Word Clock** | White, Amber, Cyan, Mint, Rose | Spells the time, e.g. "IT IS TWENTY FIVE PAST NINE" |
| **Matrix** | Green, Cyan, Red, Purple, Gold | Falling green code behind the time |
| **World Clock** | Asia & Europe, Americas, Pacific | Four cities, each showing its offset from your local time |
| **Quotes** | Inspiration, Stoic, Calm, Affirmations | A new quote every 45 seconds, with a soft fade |

**OK** opens Settings. On Now Playing, OK shows the music controls instead; **hold OK** opens Settings from any screen. **BACK** exits.

## Settings
- 12/24-hour time and an optional seconds display
- °C or °F. Location is either **automatic** (worked out from your internet connection, since TVs have no GPS) or a **city you type in**
- **Night mode** (off, scheduled, or always on), shown as a red tint (like iOS) or dimmed. You pick the start and end hours
- **Burn-in protection**: the picture shifts by a few pixels every minute
- **Auto-rotate screens** every 1, 5, 15, 30 or 60 minutes
- **Open when TV starts** (works on Android TV 8/9; newer versions may block it)
- **Screensaver shows**: the last screen you used, or one fixed screen
- Show or hide each screen

## Now Playing (Spotify / YouTube Music)
This screen shows whatever is playing **on the TV**, from any app that uses Android's standard media controls: Spotify, YouTube Music, YouTube, Prime Video and so on. It also works when you cast to the TV.

Android needs you to allow this once. Go to Settings → **Music controls access**. Most Android TVs hide that setting, so you'll probably need to run this from your PC:
```
adb connect <TV-IP>
adb shell cmd notification allow_listener com.yash.standbytv/com.yash.standbytv.MediaListener
# Android 8.0 only:
adb shell settings put secure enabled_notification_listeners com.yash.standbytv/com.yash.standbytv.MediaListener
```
On the Now Playing screen, press **OK** to show the controls. Use ◀ ▶ to pick ⏮ ⏯ ⏭, OK to press it, and BACK to hide them. Media buttons on the remote also work.

## Google Photos slideshow
Since March 2025, Google no longer lets any app browse your library or list your albums. The only official option is the **Google Photos Picker**, so it works like this:

1. Settings → **Google Photos albums** → **Sign in with Google**. Scan the QR code with your phone and approve.
2. **＋ New album**: name it, for example "Goa trip". Scan the QR code. Google Photos opens on your phone. Pick photos, or **search an album's name, select all**, and tap **Done**.
3. The TV downloads them once (up to 600 per pick, at TV resolution). They then play offline in the **Photos** screen.
4. Each album can be **shown or hidden**, **renamed**, **deleted**, or have **more photos added**. Settings → **Slideshow source** chooses between Google Photos, TV storage, or both.

StandBy TV can only ever see the photos you pick.

### One-time Google Cloud setup (needed before building)
Google requires each app to have its own sign-in client:
1. Go to <https://console.cloud.google.com>, create a project (e.g. "StandBy TV"), then **APIs & Services → Library → enable "Photos Picker API"**.
2. Set up the **OAuth consent screen** (Google Auth Platform): choose External, give the app a name, and enter your email. Add the scope `.../auth/photospicker.mediaitems.readonly`. Under **Audience**, add your Gmail as a test user.
   *While the app is in "Testing", Google ends sign-ins after 7 days. Your downloaded albums keep playing, but you'd have to sign in again to add more. To avoid that, click **Publish app**. You'll see a "Google hasn't verified this app" warning when signing in; choose Advanced → Continue. That's fine for personal use.*
3. **Credentials → Create credentials → OAuth client ID → Application type: "TVs and Limited Input devices"**. Copy the Client ID and Client secret.
4. Add them to `local.properties` in the project folder:
   ```
   google.clientId=1234567890-xxxx.apps.googleusercontent.com
   google.clientSecret=GOCSPX-xxxx
   ```
5. Rebuild (`./gradlew assembleRelease`). Without these values, the Google Photos page shows "Setup needed" and everything else still works.

Weather comes from **Open-Meteo**, which is free and needs no API key. Automatic location uses ipwho.is, with ipapi.co as a fallback.

---

## Install on the TV

1. Copy `StandBy-TV.apk` to a USB drive, or send it with LocalSend / "Send files to TV".
2. Open it with a file manager on the TV and allow "Install unknown apps" if asked.
   Or use ADB: `adb connect <TV-IP>` then `adb install StandBy-TV.apk`
3. StandBy TV appears in the TV's app row with its banner.

## Set it as the screensaver
Go to Settings → **Set as TV screensaver**. This opens the TV's own screensaver picker if it has one.

Xiaomi Mi TV (PatchWall) and some other TVs hide that picker. In that case, run this from your PC:

```
adb connect <TV-IP>
adb shell settings put secure screensaver_components com.yash.standbytv/.StandbyDream
adb shell settings put secure screensaver_enabled 1
```

---

## Build it yourself
Open the folder in Android Studio, or run:

```
./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so you can install it straight away. Use your own keystore before publishing to the Play Store.

To preview every screen as a PNG without a TV:

```
./gradlew testReleaseUnitTest --tests '*ScreenshotTest*'   # → screenshots/
```

### Code map
```
app/src/main/java/com/yash/standbytv/
  MainActivity.kt      full-screen app, keeps the screen on, sends remote keys to StandbyView
  StandbyDream.kt      screensaver (DreamService)
  StandbyView.kt       hosts the screens: D-pad, burn-in shift, night filter, auto-rotate
  SettingsActivity.kt  remote-friendly settings (white border on the focused row)
  WeatherRepo.kt       Open-Meteo + IP location + city search, cached for 30 min
  Prefs.kt / T.kt      settings, time formatting, colour themes
  BootReceiver.kt      optional "open on TV start"
  MediaListener.kt     lets Now Playing see media sessions
  GPhotosActivity.kt   Google Photos sign-in, QR picker, albums
  gphotos/             GoogleAuth (TV sign-in), GPhotos (Picker API + local albums), Http
  TvUi.kt              shared rows with focus border, QR code drawing
  faces/               each screen is a Canvas View (Face.kt is the base class, Draw.kt has shared drawing code)
```
To add a new screen, subclass `Face`, implement `draw()`, and add it to `StandbyView.allFaces()`.

---

## How the original phone app works (reverse-engineering notes)

These notes come from `br.com.zetabit.ios_standby` v2.1.24 (563), which is the phone app "StandBy Mode – Clock & Widgets". It was studied with jadx. **None of its code or assets are used in StandBy TV.** Everything above was written from scratch. The original was only studied to learn what it does and how it is put together.

**Package and stack**
- minSdk 32, targetSdk 37. The UI is all **Jetpack Compose**, and the code is heavily obfuscated with R8.
- It uses Koin for dependency injection, Coil 3 for images and OkHttp for networking.
- It also bundles Firebase (FCM, analytics), AdMob, PostHog analytics and Play Billing (for the "Pro" upgrade).

**Entry points (from the manifest)**
- `WidgetActivity`: the single Compose activity that hosts every screen. It handles deep links `…/premium` and `…/screen?name=X`, and notification extras such as `extra_target_screen`.
- `screensaver.StandByDream`: a `DreamService` with `setInteractive(false)` and `setFullscreen(true)`. It hosts a `ComposeView` and wires the Compose lifecycle owners by hand. StandBy TV's screensaver follows the same idea.
- `quicklaunchservice.ChargingAppServiceV2` (plus `Legacy…` versions and `QuickLaunchTriggerJobService`): a foreground service that watches for the charger being plugged in or unplugged and opens StandBy automatically. Its actions include `ACTION_RUN_OPEN_CHARGING`, `ACTION_POWER_DISCONNECTED` and `ACTION_OPEN_STANDBY`. Boot receivers restart it after a reboot.
- `widget.player.NotificationListener`: reads media sessions for the "Now Playing" widgets.
- `widget.pomodoro.service.PomodoroService`: a foreground service for the timer.

**Screens**
The app has about 70 screens, grouped as Analog, Digital, Battery, Weather, Media, Utility, Fun and Third-party. They are full-screen screens and "Duo" half-width widgets, for example Big Crop Clock (40+ themes), Retro Flip, Matrix, Word Clock, Solar Watch, Clock of Clocks, Ants Clock, Photo Stack, Pomodoro, Calendar, World Clock, and up to 6 slots for third-party Android widgets.

**Other features**
- Weather from WeatherAPI.com and OpenWeatherMap. Giphy for GIFs.
- Night mode as a red tint, triggered by the light sensor (lux threshold), by schedule, or manually.
- Burn-in protection by moving the picture plus "dimming alternating pixels".
- Brightness override and a sleep timer.
- 3-finger gestures for going home, locking the screen and switching "Spaces".
- A small TensorFlow Lite model (`assets/model.tflite`).

**What changed for TV**

| Phone feature | TV version |
|---|---|
| Opens automatically when charging | TVs are always plugged in, so it runs as the **screensaver** (plus optional open-on-boot) |
| Touch swipes | **D-pad**: ◀ ▶ switch screens, ▲ ▼ change style |
| Light-sensor night mode | **Scheduled** night mode, because TVs have no light sensor |
| GPS weather | **IP-based** location or a typed city |
| Battery widgets | Dropped (TVs have no battery) |
| Compose + Firebase + ads | Plain Views and Canvas, no third-party SDKs, so it runs smoothly on low-end TV chips |
