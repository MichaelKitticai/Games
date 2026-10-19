# Status Compressor: project handoff

Paste or point a new chat at this file to pick up where we left off.

## The goal

An app that compresses my photos and videos **before** I post them to WhatsApp
Status, so WhatsApp's own heavy compression does less damage and they stay
close to the original sharpness, the way Instagram Stories do. Also works for
Instagram and TikTok. Main target: **Android**. I want to publish it and share
it with people if it works well.

## Where things are

- Repo: `MichaelKitticai/Games` (public)
- Branch: `ccr-d76c46d6-rcm3fa` (not merged; the repo's default branch is `ccr-4f6c2145-7nu665`)
- Get it locally:
  ```sh
  git clone https://github.com/MichaelKitticai/Games.git
  cd Games
  git checkout ccr-d76c46d6-rcm3fa
  ```

| Path | What it is |
|---|---|
| `status-compressor-android/` | **Main app.** Native Android, Kotlin + Jetpack Compose + Media3 Transformer |
| `status-compressor/` | Earlier web/PWA version (works offline, runs in the browser). Kept as a fallback / for iPhone |
| `.github/workflows/android.yml` | Builds the APK on every push that touches the Android folder |

## Download the current build

- APK: https://github.com/MichaelKitticai/Games/releases/download/android-latest/StatusCompressor.apk
- Release page: https://github.com/MichaelKitticai/Games/releases/tag/android-latest

Each push to `status-compressor-android/**` rebuilds the APK and replaces the
`android-latest` release. The version code is the Actions run number, and
every build is signed with the same key, so new builds install over old ones.

## Android app: what it does

**Flow (like the Twitter video downloader):**
1. In the gallery, tap **Share** on a photo or video and pick **Status Compressor**
   (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` for `image/*` and `video/*`).
   Or open the app and tap **Choose photos or videos** (system photo picker).
2. Pick a target: **WhatsApp HD**, **WhatsApp**, **Instagram**, **TikTok**.
3. After compressing, tap a **Send to** button: WhatsApp Status (WhatsApp icon
   inside a dashed green status ring), WhatsApp Chat, Instagram, TikTok, More.
   Only installed apps are shown.

**Stats:** each file shows before → after size, % smaller, and "saved X of
storage and upload data". The top card keeps a lifetime total (files, bytes
saved, average % smaller), stored in SharedPreferences.

**Presets** (`Presets.kt`):

| | WhatsApp HD | WhatsApp | Instagram | TikTok |
|---|---|---|---|---|
| Photo max | 2560 px long edge | 1600 px | 1080 x 1920 | 1080 x 1920 |
| Photo size cap | 3.5 MB | 1 MB | 4 MB | 4 MB |
| Video | 720p, 1.45 Mbps (x264 slow) | 480p, ≤ 2 Mbps, each part ≤ ~16 MB | 1080p, ≤ 8 Mbps | 1080p, ≤ 10 Mbps |
| Split long videos | even parts ≤ 60 s | even parts ≤ 60 s | no | no |

**Video pipeline** (`VideoCompressor.kt`): Media3 Transformer, H.264 + AAC,
step-halving downscale then `Presentation` scale-to-fit, `FrameDropEffect` to
30 fps, HDR to SDR tone mapping via OpenGL. Video is encoded with **x264**
(`X264.kt`, `GlFrameReader.kt`, `cpp/x264_jni.c`), plugged into Media3 as a
custom `Codec`; the phone's hardware encoder is only a fallback. Bitrate is
capped at the source's bitrate. Splits evenly (70 s → 2 x 35 s). Fragmented
MP4s (many stock videos) report 0 duration; the length is read from samples.

**Why x264 (measured, Oct 2026, Android, WhatsApp HD upload on):**
- WhatsApp Status re-encodes any video over ~1.7 Mbps to 720p at ~1.8 Mbps with
  a weak encoder. Videos **under ~1.66 Mbps pass through untouched** (720p or
  1080p, with AAC audio, 6 s and 33 s tested). 1.94 Mbps and up were re-encoded.
- So the app makes 720p at ~1.6 Mbps total, and spends those bits with x264.
  SSIM vs original: x264 veryslow 0.946, slow 0.937, WhatsApp's own HD 0.914,
  hardware encoder at the same bitrate ~0.84.
- The user preferred test clip N: 720p30, x264 veryslow, film tune, 1.66 Mbps.
- "Send to yourself in HD then forward to Status" helps on iPhone, hurts on Android.
- x264 is GPL, so the app's source must stay open (the repo is public).

**Photo pipeline** (`ImageCompressor.kt`): `ImageDecoder` (EXIF rotation,
HEIC), resize once, flatten transparency onto white, thresholded unsharp mask
(amount 0.45, threshold 3), JPEG quality 92 stepping down to 80 to hit the size cap.

**Other details:**
- `minSdk 29`, `targetSdk/compileSdk 36`, AGP 8.13.0, Kotlin 2.2.20, Gradle 8.14.3, Media3 1.8.0, Compose BOM 2025.09.00
- No storage permission needed: photo picker + MediaStore saves to `Pictures/Status Compressor` and `Movies/Status Compressor` (toggle in app)
- Outputs live in `cacheDir/out/`, shared through a FileProvider (`${applicationId}.files`), cleaned after 48 h
- WhatsApp Status button sends the unofficial extra `jid=status@broadcast`; if WhatsApp ignores it, the normal picker shows with "My status" on top
- Package: `com.michaelkitticai.statuscompressor`
- `isMinifyEnabled = false`, so the APK is ~25 MB

**Files:** `MainActivity.kt` (share intents), `MainViewModel.kt` (queue, one
item at a time, cancel, stats), `App.kt` (Compose UI), `Presets.kt`,
`VideoCompressor.kt`, `ImageCompressor.kt`, `Sharing.kt`, `Storage.kt`.

## Status / what's verified

- Real phone: installs and runs; earlier builds tested by the user.
- Self-update over the air (`Updater.kt`, `latest.json` in the `android-latest` release) works.
- x264 build tested on an arm64 emulator: correct picture, 720x1280 portrait,
  1.44 Mbps video + 145 kbps audio, splitting 75 s into 2 parts. The emulator's
  software decoder drops ~0.5% of frames before the encoder; the phone's hardware
  path didn't, so this is likely emulator-only. **Check smoothness on a real phone.**
- Encoding speed on phones is unknown; on the emulator a 75 s 1440p60 clip took ~7 min.
- Web version was tested in headless Chromium: photo resizing, video splitting,
  30 fps, audio kept, and offline mode all worked. Two bugs found and fixed
  (audio-only "Ready" output when video couldn't be decoded; fractional bitrate).
- The preset numbers are educated guesses; WhatsApp doesn't publish its specs.

## Known caveats / to-do

1. **Test the x264 build on a real phone**: speed on long videos, smoothness,
   and confirm WhatsApp passes the app's output through untouched.
2. **Signing key is in the public repo** (`status-compressor-android/keystore/shared.keystore`,
   password `statuscompressor`). Fine for testing; before sharing publicly or using
   the Play Store, create a private key and pass it via GitHub secrets
   (`SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
   `SIGNING_KEY_PASSWORD`, read in `app/build.gradle.kts`).
3. Turn on R8 (`isMinifyEnabled = true`) to shrink the APK.
4. Consider moving the app to its own repo (e.g. `MichaelKitticai/status-compressor`).
5. Play Store needs a $25 Google developer account, a privacy policy, and store listing.
6. Tune presets after comparing real WhatsApp Status uploads side by side.

## Building locally

Needs Android Studio, or JDK 17 + Android SDK 36, NDK 27.0.12077973 and CMake 3.22.1:

```sh
cd status-compressor-android
scripts/build-x264.sh   # once; builds x264 into app/src/main/cpp/x264
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Web version: `cd status-compressor && python3 -m http.server 8000`, then open
http://localhost:8000.

## My preferences

- No em dashes in responses.
- Keep explanations plain and practical.
