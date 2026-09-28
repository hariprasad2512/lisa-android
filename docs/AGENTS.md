# Lisa — "Siri for Android"

Lisa is an Android app that listens in the background for the wake word **"Hey Lisa"**, then understands a spoken command and plays music on **Spotify**. It must handle Indian song names and Hinglish ("Hey Lisa, play Sahiba", "Hey Lisa, Kesariya chalao"). A small Siri-style overlay appears over other apps while Lisa is listening.

Full plan, milestones and acceptance criteria: `docs/PROJECT_SPEC.md`. Read it before starting any milestone.

## Hard constraints (never violate)

1. **Open-source only.** No paid services, no proprietary SDKs, no API keys that cost money. Forbidden: Picovoice/Porcupine, Google Cloud Speech, Firebase, any closed-source AI SDK. Before adding any dependency or model, check its license and tell me.
2. **Speech runs on-device.** No audio leaves the phone.
3. **Android + Spotify only** for now. Do not build iOS, other streaming services, or a backend.
4. **Never invent APIs.** If unsure about an Android, Spotify, sherpa-onnx or Vosk API, look up the official docs or source and cite what you found. Do not guess method names.
5. **Never guess dependency versions.** Look up the latest stable version, pin it in `gradle/libs.versions.toml`, and tell me what you chose.

## Tech stack

- Language: Kotlin, Gradle Kotlin DSL, version catalog (`libs.versions.toml`)
- UI: Jetpack Compose (overlay may need a custom LifecycleOwner / SavedStateRegistryOwner — see spec)
- minSdk 26, targetSdk 35 or the latest stable (verify), package `com.example.lisa` (placeholder, ask me before changing)
- Wake word + VAD + speech-to-text: **sherpa-onnx** first; Vosk as the simple fallback for STT
- Music: Android media intents first (no Spotify API); Spotify Web API later, optional
- Test device: CMF Phone 2 Pro (Nothing OS), wireless debugging

## Architecture

Keep core logic in plain Kotlin (no Android imports) so it can be unit tested on the JVM. Android-specific code sits behind interfaces.

```
app/            Android app: service, overlay, permissions, settings UI, debug screen
core/           Pure Kotlin: IntentParser, SongMatcher, CommandModels, pipeline state machine
audio/          AudioRecord capture, VAD, WakeWordDetector, SpeechToText implementations
music/          MusicController interface + SpotifyIntentController (+ later SpotifyWebApiController)
```

Interfaces to keep stable: `WakeWordDetector`, `SpeechToText`, `IntentParser`, `SongMatcher`, `MusicController`.

Pipeline: `mic (16 kHz mono PCM) → WakeWordDetector → pause/duck music → VAD → SpeechToText (top-N hypotheses) → IntentParser → SongMatcher → MusicController → overlay feedback`.

## Commands

```bash
./gradlew assembleDebug            # build
./gradlew testDebugUnitTest        # JVM unit tests (must pass before every commit)
./gradlew lintDebug                # lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.lisa/.MainActivity
adb logcat -s Lisa:*               # all app logs use the tag "Lisa"
```

## Android rules that shape the design

- Background listening = foreground service of type `microphone` with a persistent notification.
- A microphone foreground service **cannot be started from the background or from BOOT_COMPLETED**, even with the overlay permission. It must be started while an activity is visible (app open, Quick Settings tile, notification tap). Do not try to work around this with hacks; build the "tap to resume" paths instead.
- Android 11+ needs `<queries>` for `com.spotify.music` to detect/launch it.
- Overlays use `TYPE_APPLICATION_OVERLAY` and need the "Display over other apps" permission. They do not show on the lock screen; use an activity with `setShowWhenLocked(true)` for that case.
- Request battery-optimization exemption via a clear in-app flow.

## How I want you to work

- **Plan first.** For every milestone, start in Plan mode: list files to touch, risks, and how we will verify. Wait for my approval before building.
- **Small steps.** One milestone at a time. Do not build ahead of the current milestone.
- **Verify, don't assume.** After changes run build + unit tests. When something needs the phone, tell me exactly what to say/do and what log line to paste back.
- **Commit per milestone** with a clear message. Update `docs/PROGRESS.md` (done / decisions / next / known issues) at the end of each session.
- **Log everything useful** under the `Lisa` tag: wake score, raw transcripts, parsed query, matches, latency.
- **Ask, don't guess,** when a decision changes scope, adds a dependency, or touches permissions.
- Keep secrets (Spotify client ID etc.) in `local.properties`, never in git.
