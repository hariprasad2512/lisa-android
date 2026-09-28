# Lisa — hands-free Spotify voice assistant for Android

Lisa listens in the background for the wake word **"Hey Lisa"**, understands a
spoken command (English + Hinglish, incl. Indian song titles), and plays music
on **Spotify**. Everything is open source and speech runs on-device.

Full plan, milestones and acceptance criteria: [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md).
Session log: [`docs/PROGRESS.md`](docs/PROGRESS.md) (created after the M0 build).

## Current state — M0: Spotify intent spike

A bare app with a text field and a button. Tapping the button fires
`MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` at `com.spotify.music`
(unstructured search mode) to answer one question: does Spotify start
playback, or only open search results?

## Build & run

Prerequisites: JDK 17, Android SDK with platform 36 + build-tools 36
(`sdk.dir` in `local.properties`, which is git-ignored), Spotify installed
on the test device.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.hpsdstudio.lisa/.MainActivity
adb logcat -s Lisa:*   # all app logs use the tag "Lisa"
```

## Project layout

Single `:app` module for M0. Split into `app / core / audio / music`
modules (see `docs/PROJECT_SPEC.md` §11) from M1/M4 onward.

## License

App code is open source. Model and library licenses will be tracked in
`docs/LICENSES.md` as soon as the first model/dependency beyond AndroidX
is added.
