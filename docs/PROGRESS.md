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
