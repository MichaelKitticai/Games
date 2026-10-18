# Status Compressor (Android)

Native Android app that compresses photos and videos so they stay sharp on
WhatsApp Status, Instagram and TikTok.

## How it works for you

1. In your gallery (or any app), tap **Share** on a photo or video and pick
   **Status Compressor**. Or open the app and tap **Choose photos or videos**.
2. Pick what it's for: **WhatsApp HD**, **WhatsApp**, **Instagram** or **TikTok**.
3. When it's done, tap **WhatsApp Status**, **WhatsApp Chat**, **Instagram**,
   **TikTok** or **More** to send it straight there.

Every result shows the size before and after, the percentage saved, and the
storage and upload data you saved. The top of the app keeps a running total.

The photo picker needs no permission, and saving copies to the
"Status Compressor" album needs none on Android 10+.

## What it does to your media

| | WhatsApp HD | WhatsApp | Instagram | TikTok |
|---|---|---|---|---|
| Photos (max) | 2560 px long edge | 1600 px | 1080 x 1920 | 1080 x 1920 |
| Videos | 1080p, up to 6 Mbps | 720p, parts <= ~16 MB | 1080p, up to 8 Mbps | 1080p, up to 10 Mbps |
| Long videos | Split evenly into <= 60 s parts | Same | Not split | Not split |

- Videos: H.264 + AAC at 30 fps using the phone's hardware encoder (Media3
  Transformer). HDR clips (the default on many recent phones) are tone-mapped
  to normal video first, which avoids the washed-out look apps give them.
- Bitrate is never raised above the source's, so lean files don't get bigger.
- Photos: decoded with EXIF rotation and HEIC support, scaled once at high
  quality, lightly sharpened (flat areas left alone so they don't get grainy),
  saved as JPEG.

"WhatsApp Status" passes WhatsApp's unofficial `status@broadcast` hint. If a
WhatsApp version ignores it, you get the normal picker with "My status" on top.

## Download / install

Every push that touches this folder builds an APK on GitHub Actions and
publishes it to the repo's **Releases** page under `android-latest`. On your
phone, open that page, download `StatusCompressor.apk`, and allow your browser
to install apps when asked. New builds install over old ones.

## Building locally

Needs Android Studio (or JDK 17 + Android SDK 36):

```sh
cd status-compressor-android
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

## Signing

`keystore/shared.keystore` is a shared key so sideloaded updates install over
each other. Before publishing on the Play Store, create a private upload key,
add it as GitHub secrets, and pass them as `SIGNING_KEYSTORE_PATH`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` (see
`app/build.gradle.kts`). Play App Signing then holds the real release key.
