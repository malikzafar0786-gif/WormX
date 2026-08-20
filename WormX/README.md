# WormX

Universal file downloader (video, image, audio, document, archive, APK) with a
built-in encrypted Vault to hide downloaded or imported files.

## Open the project
1. Android Studio → **Open** → select the `WormX` folder.
2. Let Gradle sync (pulls dependencies from `app/build.gradle.kts`).
3. Run on a device/emulator with **minSdk 24+**.

## Implemented

**Downloads**
- `downloads/DownloadEngine.kt` — real pause/resume via HTTP `Range` requests.
- `downloads/DownloadForegroundService.kt` — keeps transfers alive when the
  app is minimized, with a persistent progress notification.
- `downloads/LinkResolver` family (`QualityResolver.kt`) — pluggable
  architecture that inspects a link and reports selectable qualities before
  download starts; ships with `DirectLinkResolver` (reads file headers).
  `QualityPickerDialog` shows a chooser only when more than one option comes
  back. Platform-specific resolvers (Instagram/TikTok/etc.) are an
  intentional extension point — see the note in `QualityResolver.kt`.
- `downloads/BatchPasteDialog.kt` + `BatchDownloadManager.kt` — paste many
  links at once (long-press the **Grab** button) and queue them all.
- `downloads/MediaConverter.kt` — MP4 → MP3 audio extraction and video
  re-encode/compress, via FFmpeg-Kit (long-press a completed video's name).
- All file types sorted into category folders (Videos/Images/Audio/Documents/
  Archives/Apps) — see `DownloadItem.FileCategory`.

**Vault**
- `vault/VaultCryptoManager.kt` — AES-256-GCM file encryption, PIN hash
  storage, **decoy PIN**, and **failed-attempt tracking** with a growing
  cooldown lockout.
- `vault/VaultPinActivity.kt` — PIN keypad + biometric unlock; a correct
  decoy PIN opens an always-empty vault view (`VaultGridActivity` in decoy
  mode) without ever touching the real vault contents.
- Downloads → one-tap "Move to Vault".

**Ads**
- `ads/AdFrequencyManager.kt` — caps interstitials at **2/day**, shown only
  after a batch of completed downloads, never mid-download or mid-vault-unlock.

**Branding**
- Launcher icon (adaptive, API 26+, in `mipmap-anydpi-v26/` +
  `drawable/ic_launcher_foreground.xml`) and raster fallbacks for API 24–25
  in `mipmap-mdpi` … `mipmap-xxxhdpi`, generated from the same blue/gold
  vortex motif as the in-app hero.

## Before you ship
- Replace `AdFrequencyManager.TEST_AD_UNIT_ID` and the `APPLICATION_ID`
  meta-data in `AndroidManifest.xml` with your real AdMob IDs.
- Write and register a resolver per platform you want first-class support
  for, behind the existing `QualityResolver` interface — build it against
  each platform's own official surface (their public APIs/oEmbed endpoints
  where available) rather than private/undocumented endpoints, since those
  change without notice and can conflict with a platform's terms of service.
- Consider Room (or similar) if you want download/vault history to survive a
  full process kill — both repositories are in-memory for now.
- Vault viewing (`onOpen` in `VaultAdapter`) currently just wires the click —
  add the decrypt-to-temp-file + viewer/player launch when you're ready.

## Legal note
Downloading video from platforms like Instagram/TikTok/Facebook generally
goes against those platforms' Terms of Service. Keep any platform-specific
extraction logic maintained against each platform's official surface.

## Building without a computer (GitHub Actions)

A ready-to-use workflow lives at `.github/workflows/build-apk.yml`. Once this
project is pushed to a GitHub repository, it will automatically compile a
debug APK in the cloud — no Android Studio required.

1. Push/upload this whole folder to a new GitHub repository (the GitHub
   mobile app or github.com's "Add file → Upload files" both work for this).
2. Open the repo → **Actions** tab → **Build WormX APK** → **Run workflow**
   (or just push a commit — it also runs automatically).
3. Wait for the green checkmark (a few minutes).
4. Open the finished run → scroll to **Artifacts** → download
   `WormX-debug-apk` → unzip it on your phone to get `app-debug.apk` →
   install it directly (allow "install from unknown sources" if asked).
