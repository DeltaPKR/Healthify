# Fernday — Store listing copy

All text below is ready to paste into Play Console → **Main store listing**.
Character counts include the limit Play enforces.

---

## App name
**Fernday: Daily Wellness**
(23 / 30)

> **Never use "Healthify" anywhere users or reviewers see it** — title,
> descriptions, release notes, graphics, screenshots, in-app text. Google
> rejected 1.4.1 on 2026-10-05 under the **Impersonation** policy: since
> Dec 2023 HealthifyMe's app (10M+ downloads, same features) is called
> "Healthify". The app was renamed Fernday in 1.4.2; only the package id
> `com.DeltaPKR.Healthify` (which Play can't change) and the GitHub Pages
> URLs keep the old word. See `PLAY_CONSOLE.md` §17.

## Short description (≤ 80 chars)
**Wellness check-ins, meals, workouts, steps and sleep, with reminders that stick.**
(80 / 80)

### Alternates
- `Log water, meals and workouts. Check in daily. See steps, sleep and your trend.` (79)
- `Daily check-ins, mood + sleep tracking, smart reminders. Built for routine.` (75)
- `Track mood, sleep, water, steps. Build a streak that rewards consistency.` (72)
- `A simple daily wellness tracker. Mood, sleep, water, steps, and streaks.` (71)

---

## Full description (≤ 4000 chars)

```
Fernday is a calm, focused wellness tracker — not a social network, not
a fitness leaderboard. Log your day as it happens, check in each evening,
and see how you're really doing, with the data to back it up.

WHAT YOU GET

• Log as you go
  Water in one tap (or straight from a reminder with "+1 glass"), meals
  and activities whenever they happen. Your evening check-in fills itself
  in from what you logged.

• Daily check-in
  Mood, water, food, sleep and a 1–5 rating of the day in under a minute,
  turned into a 0–100 wellness score you can follow over time.

• Food
  Log meals by slot and how they went. Search the Open Food Facts database,
  scan a barcode, or add your own foods. Calorie counting is optional and
  off by default — turn it on for a daily estimate and macros.

• Workouts
  Ten ready-made routines, from morning mobility to barbell 5×5, or build
  your own. 870+ exercises with how-to steps, built in and working
  offline. The workout player saves each set as you tick it, shows last
  time's numbers, runs rest timers and timed sets with a 3-2-1 countdown,
  and marks your personal records.

• Smart dashboard
  Today's steps and last night's sleep, read from Android Health Connect
  (you grant each one separately), next to the water and mood you logged.
  Steps and sleep also feed your wellness score, so you don't retype
  numbers your phone already recorded. Health Connect is optional: skip it
  and type them in by hand.

• Save workouts to Health Connect (optional)
  Off by default. Turn it on in Move settings and the workouts and
  activities you log are added to Health Connect as exercise sessions, so
  your other fitness apps see them too — type, name and time only.

• Reliable reminders
  Water, movement, check-in and wind-down nudges on exact alarms, so a
  6:00 PM reminder fires at 6:00 PM. Turn each one on or off, or change
  its time.

• Insights and streaks
  Your wellness trend, weekly mood, averages, and current and longest
  streak. The day ends at 6 PM, so a late check-in still keeps your streak.

• Offline-first
  Everything works without a connection. Your logs sync anonymously to our
  cloud database when you're back online.

PRIVACY THAT MATCHES THE MARKETING

• No ads. No trackers. No data sold to anyone.
• Health Connect readings stay on your device. Only daily summaries (e.g.
  "12 345 steps today") leave the phone.
• Food searches and scanned barcodes go to Open Food Facts to look them
  up. Nothing else is sent with them.
• Anonymous sign-in — no account, email or phone number. A display name
  is optional.
• Your local database is excluded from Android cloud backup and device
  transfer.
• Request full data deletion any time at deltapkr.developer@gmail.com.

PERMISSIONS WE ASK FOR

• Notifications — to deliver your reminders.
• Exact alarms — so reminders fire at the minute you set.
• Boot completed — to re-arm reminders after a reboot.
• Health Connect — reads Steps and Sleep only, granted separately,
  revocable any time, and used solely for the dashboard and your wellness
  score. Optionally, only if you turn it on, saves your workouts there as
  exercise sessions. Nothing else: no heart rate, no distance, no
  calories, no location.

PERMISSIONS WE WILL NEVER ASK FOR

• Location.
• Camera (barcode scanning runs inside Google Play services), microphone,
  SMS, call logs.
• Contacts, calendar, or files.

REQUIREMENTS

• Android 8.0 or higher.
• Health Connect (free, by Google) to fill in steps and sleep
  automatically or to save workouts. Optional — everything else works
  without it.

Fernday is not a medical device. It does not diagnose, treat, or prevent
any condition, and calorie targets are estimates, not medical advice. If
you have a health concern, see a clinician.

— Built by DeltaPKR. Feedback: deltapkr.developer@gmail.com
```

(3843 / 4000 chars)

> Up to 1.4.0 this said a reinstall or new phone gets your history back.
> It doesn't: Firestore sync is push-only, with no restore. Don't claim it
> until restore ships.

---

## App category
**Primary:** Health & Fitness
**Tags (up to 5):** wellness tracker, mood, sleep, water reminder, daily check-in

---

## Contact details
- **Email (required, public):** deltapkr.developer@gmail.com
- **Phone:** (leave blank — optional)
- **Website:** leave blank. The only site is the privacy page, whose URL
  still contains the old name; the privacy policy has its own field.

---

## External marketing
- **Allow Google to promote the app outside of Google Play?** Your call. Most indie apps say **Yes**.

---

## Visual assets — what Play requires

| Asset                    | Spec                         | Status                                   |
|--------------------------|------------------------------|------------------------------------------|
| App icon                 | 512 × 512 px, 32-bit PNG, ≤1 MB | ⚠️ Render needed — see `docs/ASSETS.md`  |
| Feature graphic          | 1024 × 500 px, JPEG/PNG, ≤15 MB | ⚠️ Render needed — see `docs/ASSETS.md`  |
| Phone screenshots        | min 2, max 8; 16:9 or 9:16; 320–3840 px on the long side | ⚠️ Capture from running release build    |
| 7-inch tablet screenshots| Optional; min 1, max 8       | Skip (phone-first app)                   |
| 10-inch tablet screenshots| Optional; min 1, max 8      | Skip                                     |
| TV banner                | Skip (no TV target)          |                                          |
| Wear OS screenshots      | Skip (no Wear target)        |                                          |

See `docs/ASSETS.md` for source SVGs + render instructions.

---

## Screenshot capture script (when you have a device connected)

```bash
# 1. Install the release AAB to a connected device:
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
adb exec-out screencap -p > screenshots/01_dashboard.png

# repeat after navigating to each tab:
#   01_dashboard.png         home with steps + sleep from Health Connect
#   02_food.png              Food tab with a few meals logged
#   03_food_search.png       a search or barcode result
#   04_move.png              Move tab: workouts card and today's activities
#   05_workout_player.png    the player mid-workout, rest timer showing
#   06_workout_summary.png   a summary with a personal record
#   07_check_in.png          mid check-in (mood + water + food)
#   08_insights.png          wellness trend + weekly mood
```

You need 2–8 of these; Play shows them in this order.

---

## Pre-launch checklist

- [ ] Privacy Policy published at `https://deltapkr.github.io/Healthify/privacy/` (or update manifest line 57 to the real URL).
- [ ] AAB uploaded to **Internal testing** track and verified on a personal device.
- [ ] Health apps declaration completed and Health Connect demo video
      uploaded — Policy → App content → **Health apps** (see
      `PLAY_CONSOLE.md` §7).
- [ ] Data Safety form submitted (see `PLAY_CONSOLE.md` §5).
- [ ] Permissions Declaration submitted for `USE_EXACT_ALARM` (see `PLAY_CONSOLE.md` §6).
- [ ] Content rating questionnaire completed.
- [ ] App icon (512×512 PNG) uploaded.
- [ ] Feature graphic (1024×500) uploaded.
- [ ] ≥ 2 phone screenshots uploaded.
- [ ] Short + full description pasted from this file.
- [ ] Upload-key SHA-1 added to Firebase project settings.
- [ ] Closed testing run for ≥14 days (required for first personal-account apps).
- [ ] Production rollout requested.
