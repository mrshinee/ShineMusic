# Releasing ShineMusic (signed APK + in-app update)

## 1. One-time setup
- `shinemusic.jks` + `keystore.properties` (project root) are your signing key. **Back them up.**
  Every update MUST be signed with this same key or Android refuses to install it over the old app.
- Your source repo `mrshinee/ShineMusic` is private, and the app can't read private repos.
  Create a second, PUBLIC repo that holds ONLY releases (no source code), e.g. `mrshinee/ShineMusic-releases`,
  and put it in `secrets.properties`:
  `UPDATE_REPO=mrshinee/ShineMusic-releases`
  Publish every release (tag + notes + APK) in THAT repo. Your code stays private.
- Build + install this version once. From now on the app can update itself.

## 2. Every new release
1. In `app/build.gradle` raise **versionCode** (1 -> 2 -> 3 ...) and **versionName** ("1.0" -> "1.1").
2. Build -> Build APK(s) (release). File: `app/build/outputs/apk/release/app-release.apk`
   (signed automatically with `shinemusic.jks`).
3. GitHub -> Releases -> Draft a new release
   - Tag: `v1.1` (must be higher than the installed versionName)
   - Title + Description: the release notes. Markdown works:
     `## Added`, `- New equalizer`, `## Fixed`, `- Duplicate songs`
   - Attach `app-release.apk` (any name ending in .apk)
   - Publish release (not draft). Pre-release is fine too.
4. Open the old app -> within ~3 s the "Update Available" card appears with your notes ->
   Download Update -> Install. (First time Android asks to allow "Install unknown apps" for ShineMusic.)
