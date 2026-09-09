# 🌿 Healthify

A calm daily wellness tracker for Android. One check-in a day — mood,
water, food, sleep, and a rating of how the day went — turned into a
wellness score, a streak, and a weekly trend.

**Status:** production. Play production access granted; first public
release (1.0.15, versionCode 16) pending review. The store listing is not
public yet, so there is no Play link below on purpose.

| | |
|---|---|
| Package | `com.DeltaPKR.Healthify` |
| Current version | 1.0.15 (16) |
| Min / target SDK | 26 (Android 8.0) / 36 |
| Privacy policy | https://deltapkr.github.io/Healthify/privacy/ |
| Publisher | DeltaPKR — deltapkr.developer@gmail.com |

---

## What it does

- **Daily check-in.** Mood, water glasses, food quality, sleep hours, and
  a 1–5 day rating, in under a minute. Produces a 0–100 wellness score.
- **Dashboard.** Today's steps and last night's sleep read from Health
  Connect, next to the water and mood you logged yourself.
- **Reminders.** Water, movement, check-in and wind-down nudges on exact
  alarms, surviving reboot and app update.
- **Insights.** Weekly mood strip, wellness average, current and longest
  streak.
- **Streaks with a 6 PM day boundary**, so a late check-in still counts.
- **Offline-first**, with anonymous Firestore sync for cross-device
  history.

No ads, no trackers, no account. Health Connect readings stay on the
device; only the daily summary saved with a check-in is synced.

---

## Health Connect — minimum scope

The app requests exactly two read permissions and no write permissions:

| Permission | Rendered by | Also feeds |
|---|---|---|
| `health.READ_STEPS` | Dashboard step card vs. the user's step goal | Wellness score (≤15 pts) |
| `health.READ_SLEEP` | Dashboard sleep card, last night's total | Wellness score (≤15 pts) |

**Do not add a third without shipping a screen that renders it.** Google
rejected 1.0.14 for declaring `READ_HEART_RATE`, `READ_DISTANCE` and
`READ_ACTIVE_CALORIES_BURNED` under *"Excessive data access for declared
feature"*; distance and calories had no code behind them at all. The
manifest, `HealthConnectManager.requiredPermissions`,
`HealthConnectManager.permissionRationales`, the in-app rationale dialog
and the Play declaration in `docs/PLAY_CONSOLE.md` §7 must all list the
same set. See `docs/PLAY_CONSOLE.md` §12 for the full post-mortem.

Granting is optional — decline it and the app still works, with steps and
sleep entered by hand.

---

## Other permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Deliver reminders (Android 13+) |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Fire a 6:00 PM reminder at 6:00 PM |
| `RECEIVE_BOOT_COMPLETED` | Re-arm reminders after reboot or update |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Firestore sync, offline detection |

Firebase's injected advertising-ID and AdServices permissions are stripped
in the manifest with `tools:node="remove"` — the app declares no ad data.

---

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Build the signed AAB from Android Studio — Build → Generate Signed App
   Bundle → **Android App Bundle**, keystore
   `~/.android/keystores/healthify-release.jks`, alias `healthify`.
   A command-line `bundleRelease` needs `keystore.properties` at the repo
   root or it **silently signs with the debug key**, which Play rejects.
   Verify before uploading — the fingerprint must be
   `93:BD:6E:36:50:3A:66:93:38:CD:74:79:67:D0:EA:2A:A2:5F:52:F2`, and
   check `versionCode` and the Health Connect permission list inside the
   bundle:

   ```bash
   keytool -printcert -jarfile app/release/app-release.aab | grep -E "Owner|SHA1"
   ```
3. Work through `docs/PLAY_CONSOLE.md` — data safety, permissions
   declaration, and the **health apps declaration** at Policy → App
   content → Health apps.
4. Re-record the Health Connect demo video on a device that actually has
   step and sleep data. An empty dashboard reads as a missing feature.
5. Paste listing copy from `docs/STORE_LISTING.md`.
6. Republish `docs/` to GitHub Pages if the privacy policy changed — the
   manifest points reviewers at that URL, and a stale page is its own
   policy mismatch.

---

## Stack

```
Compose BOM 2024.06.00      UI
Navigation Compose 2.7.7    Screen routing
Room 2.6.1 (KSP)            Local SQLite
WorkManager 2.9.0           Background scheduling
Health Connect 1.1.0        Steps + sleep
Firebase BOM 33.1.0         Firestore, Auth, Crashlytics, Analytics
DataStore 1.1.1             Preferences
Accompanist 0.34.0          Runtime permissions
```

Release builds run R8 with `isMinifyEnabled` and resource shrinking; keep
`app/proguard-rules.pro` in step when adding reflective or serialized
types.

---

## Notes for contributors

- The Room database is `version = 1` with **no migrations**. Changing an
  entity's columns needs a migration or it destroys user data on upgrade.
  `CheckInEntity.heartRateAvg` is kept and always `0` for exactly this
  reason.
- Health data is excluded from Android cloud backup and device transfer
  via `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.
- The upload keystore is backed up in three places, but **a keystore
  without its password is unrecoverable** — losing it means no further
  updates to this listing without a Play upload-key reset. Keep the
  password in a password manager, not only in Android Studio's safe.
- The app is not a medical device and must not present itself as one.

---

*Healthify · com.DeltaPKR.Healthify · minSdk 26 · targetSdk 36*
