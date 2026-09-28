# Lisa — Progress log

Updated every session: done / decisions / next / known issues.

## M0 — Spotify spike (2026-09-28)

**Done:** bare Kotlin/Compose app builds (`./gradlew assembleDebug` green).
Single `:app` module, package `com.hpsdstudio.lisa`. One screen: text field
(default "Shape of You") + "Play on Spotify" button firing
`MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` with
`setPackage("com.spotify.music")`, unstructured focus
(`vnd.android.cursor.item/*`) and `SearchManager.QUERY`. Manifest has the
`<queries>` entry for `com.spotify.music`. All app logs under tag `Lisa`.

**Decisions (all versions pinned in `gradle/libs.versions.toml`, looked up 2026-09-28):**
- AGP 9.4.0, Gradle 9.6.0, JDK 17, Kotlin 2.4.20.
- AGP 9.x has **built-in Kotlin**: `org.jetbrains.kotlin.android` must NOT be
  applied (build fails otherwise). KGP version chosen via buildscript
  classpath `org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20`; Compose
  compiler plugin `org.jetbrains.kotlin.plugin.compose:2.4.20` still applied
  explicitly. Ref: developer.android.com/build/migrate-to-built-in-kotlin.
- Compose BOM **2026.06.01 (= Compose 1.11.4)**, NOT the newest 2026.08.00:
  BOM 2026.08.00 (Compose 1.12) requires compileSdk 37, whose platform is
  not installable via the stable SDK manager yet (API 37 still in preview).
  So: compileSdk 36 / targetSdk 36 (Play requires 36 since 2026-08-31) /
  minSdk 26. Revisit BOM + compileSdk 37 once the stable platform ships.
- activity-compose 1.12.4, core-ktx 1.18.0 (latest stables predating the
  Aug-2026 compileSdk-37 wave).
- Single `:app` module for M0; split into `app/core/audio/music` at M1/M4.
- No `EXTRA_MEDIA_TITLE`/`ARTIST`: unstructured mode matches spec Phase 1;
  structured-vs-unstructured behavior is itself a finding to record.

**Next:** on-device verification on CMF Phone 2 Pro (screen on, app in
front): install, play "Shape of You" then "Sahiba", paste
`adb logcat -s Lisa:*`. Record: playback starts vs search-only, Free vs
Premium. This answers spec risk #1 and sizes M8 (Web API).

**Known issues:** none yet (unverified on device).

## M0 verification — on-device result (2026-09-28)

**Result: search page only, no autoplay.** Device: CMF Phone 2 Pro, Spotify
**Premium**, screen on, app in front. Both queries opened Spotify's search
page; playback did NOT start.

Log (`adb logcat -s Lisa:*`), pasted back by tester:
```
09-28 16:33:21.076 I Lisa : play-from-search fired query="Shape of You"
09-28 16:33:43.291 I Lisa : play-from-search fired query="Sahiba"
09-28 16:34:20.397 I Lisa : play-from-search fired query="Shape of You"
```
Intents fired cleanly, so our side is correct — Spotify chose search-only.

**Decision:** unstructured Phase 1 intent alone cannot autoplay (spec risk #1
answered for this path). M0 acceptance is still met: we know exactly what
happens instead. Weight shifts to M8 (Web API play-by-URI; Premium present).
Before closing Phase 1, run one structured-intent experiment
(`EXTRA_MEDIA_TITLE` + audio focus, plus `ACTION_VIEW spotify:search:`
fallback) — it may autoplay on a confident match, though no doc promises it.

**Next:** M0 follow-up experiment (structured + ACTION_VIEW variants), then
M1 (foreground service + permissions).

## M0 follow-up — all intent variants search-only, Phase 1 closed (2026-09-28)

**Done:** added two variant buttons (commit `a1739e3`): structured Song mode
(`EXTRA_MEDIA_FOCUS = Audio.Media.ENTRY_CONTENT_TYPE` + `EXTRA_MEDIA_TITLE`
+ `QUERY`) and `ACTION_VIEW spotify:search:<query>` fallback. Verified
green build, pushed, tester re-ran on the same device (Premium, screen on).

**Result: all three variants open Spotify search only, no autoplay.**

Log pasted back by tester:
```
09-28 16:39:26.513 I Lisa : play-from-search fired query="Shape of You"
09-28 16:39:33.970 I Lisa : structured fired query="Shape of You"
09-28 16:39:41.512 I Lisa : view-fallback fired query="Shape of You"
09-28 16:39:53.470 I Lisa : structured fired query="Sahiba"
09-28 16:39:57.807 I Lisa : play-from-search fired query="Sahiba"
```

**Decision: Phase 1 (media intents) is closed — no intent variant autoplays,
so M8 (Spotify Web API) is REQUIRED, not optional.** Transport keys
(pause/resume/next/prev via media-key events) remain valid for M5; only
starting a song needs the API.

**Next:** M8-spike — PKCE login (AppAuth) + search + play-by-URI from a
debug screen, proving the API path before the pipeline is built on it.

## M8-spike — API path proven, active device required (2026-09-28)

**Done:** PKCE login + search + play-by-URI debug section (commit `9aa1ec8`).
New pins: `net.openid:appauth 0.11.1`, `androidx.datastore:datastore-preferences
1.2.1`. Chose DataStore over EncryptedSharedPreferences (deprecated in
security-crypto 1.1.0). Client ID via git-ignored `local.properties` →
`BuildConfig.SPOTIFY_CLIENT_ID`; redirect `com.hpsdstudio.lisa://oauth2redirect`.

**Result: login ✓, search ✓ (10/10 hits), play ✓ when playback is active.**
Without an active device, `play` returns 404 `NO_ACTIVE_DEVICE` (four
identical attempts). Tester confirms: "working when there is playback active."

Log (abridged):
```
09-28 17:49:13.699 I Lisa : spotify login ok
09-28 17:49:15.794 I Lisa : spotify search query="Sahiba" hits=10
09-28 17:49:18.156 I Lisa : spotify play uri="spotify:track:0eLtIxPRNJfsmehITZ1qaJ" code=404 ...
                           "reason" : "NO_ACTIVE_DEVICE"
```

**Decision:** M8 path proven for personal Premium use. M5 must handle the
cold case: list devices (`GET /me/player/devices`) → transfer playback to
the phone (`PUT /me/player`) → `play`. No extra spike needed.

**Next:** M1 — foreground mic service + permissions + tile + debug skeleton.
