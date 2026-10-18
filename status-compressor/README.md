# Status Saver

A small web app that prepares photos and videos for WhatsApp Status so they keep
their sharpness after WhatsApp's own compression.

Everything runs in the browser on your phone. Nothing is uploaded, and after the
first visit the app works fully offline (it can also be installed to the home
screen with "Add to Home Screen").

## Why WhatsApp makes things blurry, and what this does about it

WhatsApp re-encodes every status. Most of the damage comes from it taking a huge
original (12 MP photo, 4K 60 fps HEVC video) and crushing it down in one fast,
low-quality pass. Instagram does a gentler version of the same job.

This app does that heavy lifting first, carefully, so WhatsApp's pass has very
little left to do:

| | HD mode | Standard mode |
|---|---|---|
| Photos | Long edge 2560 px, JPEG, mild sharpening | Long edge 1600 px, JPEG ≤ ~1 MB |
| Videos | 1080p, H.264, 30 fps, up to 6 Mbps | 720p, H.264, 30 fps, each part ≤ ~16 MB |

- Photos are downscaled in steps (no aliasing), sharpened with a thresholded
  unsharp mask (edges get crisper, flat areas don't get grainy), and flattened
  to JPEG.
- Videos are scaled, converted to a steady 30 fps, and split evenly into parts
  that fit the Status length limit (60 s by default, adjustable). Audio is kept.

## Use it

1. Open the app, pick **HD** (then tap HD in WhatsApp's editor) or **Standard**.
2. Choose photos or videos.
3. Tap **Share** and choose WhatsApp, or **Save** and post from your gallery.

Video conversion needs H.264 encoding via WebCodecs: Chrome on Android and
Safari on iPhone (iOS 16.4+) both have it. Desktop Chromium builds without
proprietary codecs (and Firefox) will show a message instead.

## Run / host it

It's a static site with no build step. Any static host works (GitHub Pages,
Vercel, Netlify). It must be served over HTTPS (or `localhost`) for the offline
service worker and the Share button.

Locally:

```sh
cd status-compressor
python3 -m http.server 8000
# open http://localhost:8000
```

When you change any file, bump `CACHE` in `sw.js` so installed copies refresh.

Video processing uses [Mediabunny](https://mediabunny.dev) 1.61.6, vendored in
`vendor/` under the MPL-2.0 license (`vendor/mediabunny.LICENSE`).
