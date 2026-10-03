# 🌿 Healthify

A calm daily wellness tracker for Android. One check-in a day — mood,
water, food, sleep, and a rating of how the day went — turned into a
wellness score, a streak, and a weekly trend.

**Status:** production on Google Play. Current release 1.3.0
(versionCode 20): add meals by searching Open Food Facts or by barcode
(Google Code Scanner, no camera permission), keep your own foods, and
count calories against a personal daily target if you want to — off
unless turned on.

| | |
|---|---|
| Package | `com.DeltaPKR.Healthify` |
| Current version | 1.3.0 (20) |
| Min / target SDK | 26 (Android 8.0) / 36 |
| Privacy policy | https://deltapkr.github.io/Healthify/privacy/ |
| Publisher | DeltaPKR — deltapkr.developer@gmail.com |

---

## What it does

- **Daily check-in.** Mood, water glasses, food quality, sleep hours, and
  a 1–5 day rating, in under a minute. Ends on a celebration: the day's
  score, what each part earned, and the streak ticking over.
- **Wellness score (0–100).** Water, steps and sleep 20 each against the
  user's own goals, mood 20, food 10, day rating 10 — the same number on
  home, the celebration, Insights and Profile.
- **Dashboard.** Activity rings for water, steps and sleep around the
  score; steps and sleep come from Health Connect, water from the
  check-in. A Mon–Sun chain shows the week's check-ins.
- **Food.** Meals by slot with a quality tag; add foods from Open Food
  Facts search, a barcode or your own list, in servings, grams or
  ounces. Calorie counting is optional (Mifflin-St Jeor estimate or your
  own target) and never feeds the wellness score.
- **Reminders.** Water, movement, check-in and wind-down nudges on exact
  alarms, surviving reboot and app update.
- **Insights.** Wellness-trend chart, 7-day averages and goal progress,
  weekly mood strip, current and longest streak.
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
| `health.READ_STEPS` | Dashboard step card vs. the user's step goal | Wellness score (≤20 pts) |
| `health.READ_SLEEP` | Dashboard sleep card, last night's total | Wellness score (≤20 pts) |

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

*Healthify · com.DeltaPKR.Healthify · minSdk 26 · targetSdk 36*
