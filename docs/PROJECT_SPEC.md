# Lisa — Project Spec

## 1. Goal

A hands-free voice assistant for Android focused on one job: **play music from Spotify by voice**, including Indian songs and Hinglish phrasing.

- Wake word: **"Hey Lisa"**, always listening in the background (like "Ok Google").
- Commands: "play Shape of You", "play Sahiba", "play Tum Hi Ho by Arijit Singh", "pause", "resume", "next", "previous", Hinglish like "Kesariya chalao".
- Feedback: a small Siri-style orb/overlay over other apps showing listening state and transcript.
- Everything **open source**, on-device speech, no paid services.

### Non-goals (for now)
iOS, other streaming services (YouTube Music, JioSaavn, Apple Music), a backend, general Q&A / smart-home features, Play Store release.

## 2. Users and scenarios

Primary user: the developer on a CMF Phone 2 Pro, screen off or another app in front, music possibly already playing.

1. Screen on, another app open: "Hey Lisa, play Sahiba" → orb appears → Spotify starts the song.
2. Music already playing loudly: "Hey Lisa, next" → still detected, skips.
3. Lisa mishears a title → best fuzzy match still plays; debug screen shows what happened.
4. Phone rebooted or service killed → notification / Quick Settings tile lets the user resume listening in one tap.

## 3. Pipeline

```
AudioRecord (16 kHz, mono, PCM16, source VOICE_RECOGNITION)
  └─► WakeWordDetector  ("Hey Lisa")
        └─► on detect: pause/duck music, show overlay
              └─► VAD: capture until end of speech (max ~6 s, min ~0.4 s)
                    └─► SpeechToText → top-N hypotheses
                          └─► IntentParser → Command(type, query, artist?)
                                └─► SongMatcher (rerank candidates)
                                      └─► MusicController.play(...)
                                            └─► overlay result → dismiss → resume listening
```

One shared `AudioRecord` stream feeds both the wake word and the command capture.

## 4. Component decisions

| Piece | First choice | Fallback | Notes |
|---|---|---|---|
| Wake word | sherpa-onnx keyword spotting | openWakeWord or microWakeWord (train custom "Hey Lisa" on free Colab) | "Hey Lisa" is short; tune threshold to balance false accepts vs. misses; test Indian-accent voices |
| VAD | Silero VAD (via sherpa-onnx) | simple energy VAD | |
| Speech-to-text | Vosk small models (English-India, Hindi) | Whisper (whisper.cpp or via sherpa-onnx), bigger but better on Hinglish | Compare on the accent test set, decide with data |
| Intent parsing | Rule-based (regex + keyword lists) | small classifier later | Must handle English and Hinglish verbs: play, chalao, bajao, laga do; pause, roko; next, agla |
| Music (phase 1) | Android media intents to `com.spotify.music` | — | See section 5 |
| Music (phase 2) | Spotify Web API | — | Optional, limited, see section 5 |
| Overlay | `TYPE_APPLICATION_OVERLAY` + Compose orb | plain View animation | Compose in a Service needs custom LifecycleOwner + SavedStateRegistryOwner |

Check every model's license before bundling it (some pretrained wake-word models carry non-commercial licenses). Record the license of each model in `docs/LICENSES.md`.

## 5. Spotify strategy

**Why not just the Web API?** As of 2026, Spotify Development Mode requires the app owner to have Premium, allows only a handful of allowlisted users, caps search results (10), and the extended quota that lifts this is for registered organizations only. So a public "connect your Spotify" flow is not realistic. The Web API is fine for personal use, but the design must not depend on it.

### Phase 1 — Intents (no API, no OAuth)
- `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` with `setPackage("com.spotify.music")`, `EXTRA_MEDIA_FOCUS = "vnd.android.cursor.item/*"`, and the query in `SearchManager.QUERY`.
- Fallback: `ACTION_VIEW` on a `spotify:search:<query>` URI.
- Pause / resume / next / previous: dispatch media key events (`AudioManager.dispatchMediaKeyEvent` with `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`).
- **VERIFY on the real phone** (with and without Premium, screen on and off, other app in front): does Spotify actually start playback, or only open search results? Record findings in `docs/PROGRESS.md`. This result decides how much Phase 2 is needed.
- Starting Spotify's activity from a background service is restricted on Android 10+; confirm the overlay permission covers it, otherwise route through the overlay activity.

### Phase 2 — Web API (optional, personal use)
- OAuth Authorization Code with PKCE using AppAuth-Android; custom-scheme redirect; tokens in EncryptedSharedPreferences or DataStore.
- Search → rerank with SongMatcher → `PUT /me/player/play` with the chosen track URI; needs a Premium account and an active Spotify device.
- Handle 403 (endpoint blocked in Development Mode), 429 with quota reason, and "no active device".

## 6. Indian song-title matching

ASR will not spell "Sahiba", "Kesariya" or "Tum Hi Ho" reliably. Design for that:

1. Strip command words: `play`, `chalao`, `bajao`, `laga do`, "Hey Lisa" leftovers, filler words.
2. Split "X by Y" / "X ka Y" into title and artist.
3. Use the top-N ASR hypotheses, not only the best.
4. Query Spotify (phase 1: pass the best cleaned query; phase 2: search each hypothesis).
5. Rerank candidates by phonetic + edit-distance similarity (Double Metaphone or a small transliteration step for Devanagari/Hinglish, plus Levenshtein).
6. Keep a small local alias file (`aliases.json`) for titles that always fail, e.g. `"sahiba": ["sahiba", "saheba", "sahiba hindi"]`.

**Accent test set:** record 40–50 commands (Indian and English titles, quiet and noisy, music playing in the background), store as 16 kHz mono WAV under `core/src/test/resources/audio/` with a CSV of expected transcripts/titles. Report a top-1 and top-3 title hit rate. This is the project's main quality metric.

## 7. Android platform notes

**Permissions / manifest:** `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `INTERNET` (only for Spotify search/API), `<queries>` entry for `com.spotify.music`. Service declared with `android:foregroundServiceType="microphone"`.

**Hands-free limits (design around them, do not fight them):**
- Microphone foreground service can only start while an activity is visible; not from boot or the background. Provide: (a) a Quick Settings tile, (b) an in-notification "Resume Lisa" action that opens an activity, (c) a persistent "Lisa stopped" notification if the service dies.
- Use `START_STICKY`, a battery-optimization exemption flow, and a watchdog that detects a dead service on next app open and reports it in the debug screen.
- Experiment later: register as the default assistant (`VoiceInteractionService`) and check whether the OS then allows a post-boot start. Treat as unverified; read current Android docs first.
- The green microphone indicator will always show. Fine, but mention it in the onboarding screen.

**Audio behavior:** on wake, pause or duck Spotify so the command is audible; if no command follows within the timeout, resume. Loud music will hurt wake-word accuracy, so test with music at typical volume and consider a higher-quality mic source or a slightly lower detection threshold while music plays.

## 8. Milestones and acceptance criteria

**M0 — Spotify spike (day 1)**
Bare Kotlin app, one button, plus a text field. Fires the play-from-search intent.
✔ Tapping the button starts "Shape of You" (or we know exactly what happens instead). Findings written to `PROGRESS.md`.

**M1 — Foreground service + permissions**
Mic foreground service, notification, permission flow (mic, notifications, overlay, battery), Quick Settings tile, debug screen skeleton.
✔ Service runs with the screen off for 30 min; tile and notification start/stop it; logs show audio frames flowing.

**M2 — Wake word**
sherpa-onnx keyword spotting on the shared audio stream; log scores; simple sound/vibration on detect.
✔ "Hey Lisa" detected ≥ 90% in quiet, ≤ 1 false accept per hour of ordinary TV/speech; results in `PROGRESS.md`.

**M3 — Command capture + speech-to-text**
VAD-bounded capture after the wake word; Vosk transcription; show transcript on the debug screen.
✔ 10 English + 10 Indian-title commands transcribed and logged; latency from end of speech to transcript < 2 s.

**M4 — Intent parsing + song matching (pure Kotlin, unit tested)**
`IntentParser` and `SongMatcher` with the WAV/CSV accent test set and a scoring test.
✔ Parser handles play / pause / resume / next / previous in English and Hinglish; test set reports top-1 and top-3 hit rates; `./gradlew testDebugUnitTest` passes.

**M5 — End-to-end music control**
Wire wake → STT → parser → matcher → `SpotifyIntentController`; pause/duck music on wake and resume on timeout.
✔ "Hey Lisa, play Sahiba" plays the song from a locked and unlocked phone, with another app in front.

**M6 — Overlay UI**
Siri-style orb with listening / thinking / result states and live transcript; dismisses itself; "show when locked" activity for the lock screen.
✔ Overlay appears within 300 ms of wake and disappears after the command.

**M7 — Hardening**
Battery-exemption flow, restart paths, false-trigger tuning, overnight test, crash logging to a local file, simple settings screen (sensitivity, language).
✔ 24 h run with ≥ 95% uptime and no crashes; write a "known issues" list.

**M8 — Spotify Web API (optional)**
PKCE login, search, play by URI, error handling for 403 / 429 / no active device.
✔ Personal account can play by API; failure falls back to the intent path.

## 9. Testing strategy

- **JVM unit tests** for `IntentParser`, `SongMatcher`, and the pipeline state machine (fake `WakeWordDetector`, `SpeechToText`, `MusicController`).
- **WAV replay harness:** feed recorded WAVs through wake word and STT off-device so the agent can iterate on accuracy without you speaking each time.
- **On-device checks** (you run them; the agent tells you exactly what to do and which log lines to paste back): wake word in noise, service survival, Spotify playback, overlay behavior.
- **Debug screen** shows: wake score, raw hypotheses, cleaned query, top matches with scores, latency per stage, service uptime.

## 10. Risks / open questions

1. Does the Spotify intent actually start playback, and does it behave differently for free users? (M0 answers this.)
2. Wake-word accuracy for "Hey Lisa" with Indian accents and music playing. (M2 answers this; fallback: train custom model.)
3. Vosk vs. Whisper accuracy on Hinglish song titles. (M3/M4 decide with the test set.)
4. Service survival on Nothing OS after hours idle. (M7.)
5. Whether default-assistant registration changes the post-boot restrictions. (Unverified, later experiment.)
6. Model licenses for anything bundled. (Track in `docs/LICENSES.md`.)

## 11. Repo layout

```
lisa/
├── AGENTS.md
├── docs/
│   ├── PROJECT_SPEC.md      (this file)
│   ├── PROGRESS.md          (updated every session)
│   └── LICENSES.md          (models + libraries)
├── app/  core/  audio/  music/
├── gradle/libs.versions.toml
└── local.properties         (git-ignored: Spotify client ID, etc.)
```
