# ULO TV Android APK

This repository builds an installable Android WebView app for your live streaming website using GitHub Actions. No Android Studio is needed.

## Website URL
The build currently defaults to `https://ethiohub.site.je/`. If your ULO TV site uses another URL, add a repository Actions variable named `ULOTV_BASE_URL` under **Settings → Secrets and variables → Actions → Variables**.

## Build APK
1. Open the **Actions** tab.
2. Select **Build ULO TV APK**.
3. Tap **Run workflow**.
4. Open the completed run and download the **ULO-TV-debug-APK** artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android phone.

This is a debug APK for testing, not a Play Store release build.