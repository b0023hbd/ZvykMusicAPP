# Zvykmusic Android

Android app shell for https://web.zvykmusic.ru/ with a native app frame, configurable accent/theme, native bottom navigation and GitHub Actions APK builds.

## Build APK with GitHub Actions
1. Create a new GitHub repository and upload the contents of this folder.
2. Open **Actions** and enable workflows if GitHub asks.
3. Run **Build Zvykmusic APK** using **Run workflow**, or push to `main`.
4. Download `zvykmusic-debug-apk` from the successful workflow run's **Artifacts** section.
5. Unzip the artifact and install `app-debug.apk` on Android. Allow installation from your browser/files app if prompted.

## Site integration
The app loads the live site at `https://web.zvykmusic.ru/` in a WebView, so the existing catalog/account/site features remain served by your site. The bottom tabs load configurable URLs in `app/src/main/java/ru/zvykmusic/app/SiteConfig.kt`. Adjust those paths to match real routes on your site if needed.

## Customize
- App name: `app/src/main/res/values/strings.xml`
- Colors: `app/src/main/res/values/colors.xml`
- Tab destinations: `SiteConfig.kt`
- Native settings: tap the palette icon in the top bar; accent choice is saved on device.
- Package ID: `ru.zvykmusic.app` in Gradle and `MainActivity.kt`.

## Important
This is a debug APK workflow (not a Play Store release). For Play Store distribution, create a signed release with a keystore stored in GitHub Secrets. Never commit signing keys or passwords. This project has not been tested against private APIs; it only opens the public HTTPS site in an Android WebView. Android playback/background behavior depends on the site's player and browser-compatible media implementation.
