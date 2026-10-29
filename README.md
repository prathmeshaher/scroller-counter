# Scroller Counter

Android app that counts how many Reels / Shorts you swipe through in Instagram,
YouTube, TikTok and Facebook. Shows today's count, all-time count and a per-app
breakdown.

## How it works
An Android `AccessibilityService` (`ReelScrollService`) listens for scroll events in
those apps. Swipes are debounced and only counted while a reels/shorts viewer is on
screen. Only counts are stored (locally, `SharedPreferences`); no screen content leaves the device.

## Build & install
Open in Android Studio and run, or `gradle assembleDebug` (needs the Android SDK).
The GitHub Action in `.github/workflows/build.yml` also builds a debug APK.

Then open the app, tap **Open Accessibility settings**, and enable *Scroller Counter*.

## Caveats
- Android only: iOS does not allow apps to observe other apps.
- Detection relies on view-id hints in `VIEWER_HINTS`; app updates can break them, so
  counts are approximate and the hints may need tweaking per app version.

## Privacy: fully offline
- The manifest has no `INTERNET` permission (explicitly removed with `tools:node="remove"`), so Android blocks all network access for this app.
- Cloud backup and device transfer are disabled (`allowBackup=false`, data extraction rules exclude everything).
- No analytics or third-party SDKs; the only dependencies are AndroidX core/appcompat.
- CI fails the build if the merged manifest ever contains a network permission.
- You can verify on your phone: Settings > Apps > Scroller Counter > Permissions shows none.
