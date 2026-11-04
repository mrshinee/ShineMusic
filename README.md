# ShineMusic

Jetpack Compose music player with an M3-Play-inspired UI (original code, not a copy):
time-based greeting card, mood chips, 2x2 quick-access pills, expanding hero carousel,
floating pill navigation, art-coloured full-screen player with pill transport buttons,
synced lyrics (lrclib.net), queue, sleep timer, favourites + history, background playback
with notification controls (Media3).

- API base URL: `app/build.gradle` → `API_BASE` (JioSaavn-style `/api/search/songs?query=`).
- Build: open in Android Studio / AndroidIDE, or `./gradlew assembleDebug`.
- Needs: JDK 17, Android SDK 34.

## Changelog (latest)
- Home top bar: removed History / Search / Notification icons, "ShineMusic" is now single-line.
- Animated equalizer ("now playing" logo): song cards, list rows, hero button and the mini player.
- API: duplicate songs removed everywhere (by id and by title+artist), retry + fallback servers (`API_FALLBACKS`), 10-min cache,
  race fixes for chips/search, auto-skip on broken streams, tapping the current song no longer restarts it.

## Spotify
- Keys go in `secrets.properties` (project root, git-ignored): `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`.
- Client-Credentials flow (no login). Spotify = metadata/search/HD covers; audio is matched from Saavn.
- Used for: Search tab, mood chips, and the "Fresh on Spotify" Home row. Falls back to Saavn alone if Spotify is unavailable.

## Release signing
`shinemusic.jks` + `keystore.properties` sign every release build. Never lose them; always reuse the same ones.
Build: Android Studio -> Build -> Build APK(s) (release variant) or `./gradlew assembleRelease`
APK: `app/build/outputs/apk/release/app-release.apk`

## Updates (in-app "Update available" dialog)
1. Host `version.json` (see `version.json.example`) somewhere with a direct link (GitHub repo raw link is fine).
2. Put that link in `secrets.properties` as `UPDATE_URL=...` and build once.
3. For every new release: raise `versionCode` (+1) and `versionName` in `app/build.gradle`, build, upload the new APK,
   then edit version.json (new versionCode, apkUrl, notes). Users get the dialog on next app launch.
   `"force": true` makes the dialog impossible to dismiss.
