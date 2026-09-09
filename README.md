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

## Build

Requires JDK 17+ (Android Studio's bundled JBR works) and the Android SDK.

```bash
./gradlew :app:assembleDebug
```

`app/google-services.json` is gitignored and required. Pull it from the
Firebase console (project settings → your apps → Android) if it is
missing.

### Release builds

`app/build.gradle.kts` reads signing config from `keystore.properties` at
the repo root — gitignored, see `keystore.properties.template`. **If that
file is absent the release build silently falls back to the debug key**,
which Play rejects on upload with a signature mismatch.

In practice releases are cut from Android Studio instead: Build →
Generate Signed App Bundle / APK → **Android App Bundle** → the keystore
at `~/.android/keystores/healthify-release.jks`, alias `healthify`.
Studio remembers the password in its own password safe.

Verify any bundle before uploading — the upload key fingerprint must be
`93:BD:6E:36:50:3A:66:93:38:CD:74:79:67:D0:EA:2A:A2:5F:52:F2`:

```bash
keytool -printcert -jarfile app/release/app-release.aab | grep -E "Owner|SHA1"
```

The `.jks` is backed up in three places, but **a keystore without its
password is unrecoverable** and losing it means no further updates to this
listing without a Play upload-key reset. Keep the password in a password
manager, not only in Android Studio.

---

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Build the signed AAB (above) and verify signer, `versionCode` and the
   Health Connect permission list inside it.
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

## Layout

Sources live flat under `app/src/main/kotlin/`; package names are declared
in-file and do not mirror directories.

```
app/src/main/kotlin/
├── HealthifyApp.kt          Application: DB, repository, Firebase init
├── MainActivity.kt          Nav graph, permission flow, rationale dialog
├── AppDatabase.kt           Room entities + DAOs (version 1, no migrations)
├── AppRepository.kt         Single data access layer over Room + Firestore
├── HealthConnectManager.kt  Steps + sleep reads, permission rationale copy
├── FirebaseSync.kt          Anonymous auth + Firestore mirror
├── StreakManager.kt         Streak evaluation, 6 PM day boundary
├── NotificationWorker.kt    Alarm scheduling, ReminderReceiver, BootReceiver
├── Theme.kt                 Dark design tokens
├── OnboardingScreen.kt · DashboardScreen.kt · CheckInScreen.kt
├── NotificationsScreen.kt · ProfileScreen.kt
└── com/healthify/app/ui/insights/InsightsScreen.kt
```

`docs/` holds the Play submission material and the GitHub Pages site
(privacy policy and data-deletion page) served at
https://deltapkr.github.io/Healthify/.

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
- The app is not a medical device and must not present itself as one.

---

*Healthify · com.DeltaPKR.Healthify · minSdk 26 · targetSdk 36*
