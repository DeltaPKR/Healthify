# Healthify — Store assets

Source SVGs and rendered PNGs for the Play Store listing.

## Files

| File                            | Purpose                       | Spec                          |
|---------------------------------|-------------------------------|-------------------------------|
| `store-assets/icon-512.svg`     | App icon source               | —                             |
| `store-assets/icon-512.png`     | **Upload to Play**            | 512 × 512, PNG, 20 KB         |
| `store-assets/feature-1024x500.svg` | Feature graphic source    | —                             |
| `store-assets/feature-1024x500.png` | **Upload to Play**        | 1024 × 500, PNG, 55 KB        |

## Re-rendering

If you edit the SVGs:

```bash
cd docs/store-assets
node render.js
```

`sharp` is pinned in `package.json`; the first run installs it.

## Screenshots

Play requires **≥ 2 phone screenshots**. They must come from the real app,
so capture them from the release build running on a connected device.

```bash
# 1. From repo root, install the AAB on the connected device:
"$ANDROID_HOME/cmdline-tools/latest/bin/bundletool" build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=/tmp/healthify.apks \
  --connected-device \
  --ks=$HOME/.android/keystores/healthify-release.jks \
  --ks-key-alias=healthify

"$ANDROID_HOME/cmdline-tools/latest/bin/bundletool" install-apks \
  --apks=/tmp/healthify.apks

# 2. Launch + capture each screen with adb:
adb shell am start -n com.DeltaPKR.Healthify/.MainActivity
sleep 3

mkdir -p docs/store-assets/screenshots
adb exec-out screencap -p > docs/store-assets/screenshots/01_dashboard.png
# manually navigate to each tab, then run:
adb exec-out screencap -p > docs/store-assets/screenshots/02_checkin.png
adb exec-out screencap -p > docs/store-assets/screenshots/03_insights.png
adb exec-out screencap -p > docs/store-assets/screenshots/04_reminders.png
adb exec-out screencap -p > docs/store-assets/screenshots/05_profile.png
```

### Tips for good store screenshots

- Use a device with a clean status bar (no carrier name, full battery,
  signal strength visible). The Pixel 8 emulator with
  `adb shell settings put global sysui_demo_allowed 1` then
  `adb shell am broadcast -a com.android.systemui.demo -e command enter`
  + further demo-mode commands gives a clean bar.
- Don't crop status bars — Play prefers the full device chrome.
- Long side must be 320–3840 px. A 1080×2400 Pixel screen is fine as-is.
- First two screenshots show up in search results — make them count.
  Recommended order:
  1. Dashboard with steps + sleep + HR populated.
  2. Daily check-in mid-entry.
  3. Insights weekly mood strip.
  4. Reminders list with the wheel time-picker visible.
  5. Profile / streak.

## Design notes

- Background: same `#070D1A` ↔ `#111E30` radial gradient as the in-app
  theme — keeps icon, splash, and dashboard visually identical.
- Accent: Healthify green `#1AD9A0` (matches `Theme.Healthify` accent).
- Heart silhouette is the Material `Filled.Favorite` path; reused in
  `mipmap-anydpi-v26/ic_launcher.xml` and the splash branding.
- Feature graphic positions the heart at x=800/1024 — survives Play's
  per-device crop which trims roughly the right 20–30 % on small surfaces.
