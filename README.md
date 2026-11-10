# Jetpack Jung LAN - Android source project (v7)

This is an Android WebView wrapper around the supplied Jetpack Jung HTML/JavaScript game. It adds a local WebSocket room host so up to ten Android phones (one host + nine joining players) can play over the same Wi-Fi network or mobile hotspot without internet access.

## Build the APK

### Cloud build (no Android Studio required on your phone)

The repository includes `.github/workflows/build-apk.yml`. Upload this project folder's contents to a GitHub repository, open **Actions**, run **Build Jetpack Jung LAN APK**, and download the `JetpackJung-LAN-debug-apk` artifact from the successful workflow run. More detailed steps are in `BUILD_APK_ON_GITHUB.md`.

### Android Studio

- JDK 17
- Android SDK Platform 35
- Gradle 8.9 (the GitHub workflow installs this automatically)

Open the `JetpackJung_LAN_Android` folder in Android Studio and build `:app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## LAN setup

1. Connect all Android phones to the same Wi-Fi network or one phone's hotspot.
2. Open the app on the host phone and tap **CREATE LAN ROOM**.
3. Share the host phone's displayed IPv4 address and room code.
4. On the other phones, enter the host IP and room code, then tap **JOIN**.
5. The host selects the match options and taps **PLAY**. Joining phones wait for the host to start.

The local WebSocket server listens on TCP port `28765`. Some phone hotspot configurations isolate connected devices; if clients cannot connect, use a normal Wi-Fi router or a hotspot that allows connected devices to communicate.

## What is included

- Ten-seat LAN lobby (one host + up to nine clients)
- Host and join flow with room code
- Player state replication (position, health, jetpack, weapon, kills/deaths)
- Relayed bullet, grenade, melee and pickup actions
- Host match-state snapshots for timer, team score and pickups
- Host-authoritative match-end notification
- Basic per-client packet-rate limit and disconnect cleanup
- Original supplied game artwork, CSS, maps and controls retained

## Honest test status / limitations

This ZIP is a development source project, **not a verified installable APK**. The source JavaScript can be syntax-checked, but Android compilation requires Android SDK/Gradle dependencies and has not been performed in this environment. The multiplayer simulation is peer-relayed: it is suitable for LAN prototyping but is not an authoritative anti-cheat server. Latency, hit registration, hotspot compatibility, and ten-device stability still require real-device testing. The GitHub workflow is provided to build a debug APK; a successful workflow run is still required before calling the APK build verified.
