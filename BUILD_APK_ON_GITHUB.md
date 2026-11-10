# Build the APK without Android Studio on your phone

This project now includes a GitHub Actions workflow that builds the Android debug APK in the cloud.

1. Create a GitHub repository and upload the **contents** of this project folder to its `main` branch.
2. Open the repository's **Actions** tab.
3. Select **Build Jetpack Jung LAN APK**.
4. Tap **Run workflow** and confirm.
5. Wait for the run to finish. Open the successful run and download the artifact named `JetpackJung-LAN-debug-apk`.
6. Extract the artifact ZIP. It contains `app-debug.apk`.

The workflow needs the repository to be public or your GitHub account to have Actions enabled for that repository. The generated debug APK is unsigned for Play Store release, but can be installed manually after allowing installs from the browser/file manager.

## Build locally

Open this folder in Android Studio with JDK 17 and Android SDK Platform 35 installed, then run `:app:assembleDebug` or use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
