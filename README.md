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
