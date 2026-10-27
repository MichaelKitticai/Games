# Meno

Dental clinic scheduling for Nairobi: reception books on a computer, dentists and nurses see their day live on their phones. Research and build list: [`../research/dental-scheduler-research.md`](../research/dental-scheduler-research.md).

Built with Expo (React Native), so one codebase runs on Android, iOS and the web (reception PC).

## Run locally

```bash
npm install
npm start          # scan the QR code with Expo Go, or press w for web
npm run typecheck
```

## OTA updates

JavaScript and design changes reach installed apps without a new app store release, using [EAS Update](https://docs.expo.dev/eas-update/introduction/).

How it works:

1. A push to `main` that changes `meno/` runs `.github/workflows/meno-ota.yml`, which publishes the update to the `production` channel. Run the workflow by hand to publish to `preview` instead.
2. The app checks for an update on launch and whenever it comes back to the foreground (`src/components/UpdateBanner.tsx`), downloads it in the background, then shows a "Restart" banner. Anything not restarted picks the update up on its next launch.
3. The home screen shows the version, channel and update ID, so you can confirm a device is on the latest update.

Updates only apply to builds with the same `runtimeVersion` (the app `version` in `app.json`). Adding a native module or changing native config needs a new build: bump `version`, then `npx eas-cli build`.

The web version updates by redeploying `npm run export:web` (the `dist/` folder) to hosting.

### One-time setup

Needs a free Expo account.

```bash
cd meno
npx eas-cli login
npx eas-cli init               # links the project, adds extra.eas.projectId to app.json
npx eas-cli update:configure   # adds updates.url to app.json
npx eas-cli build --profile preview --platform android   # first installable build
```

Commit the `app.json` changes, then add an access token from expo.dev (Account settings > Access tokens) as the `EXPO_TOKEN` secret in GitHub (repo Settings > Secrets and variables > Actions).

## Channels

| Build profile (`eas.json`) | Channel | Used for |
|---|---|---|
| development | development | Dev client for building features |
| preview | preview | Test phones at the clinic |
| production | production | Live clinic devices |
