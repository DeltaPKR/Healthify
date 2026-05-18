# Healthify — Store listing copy

All text below is ready to paste into Play Console → **Main store listing**.
Character counts include the limit Play enforces.

---

## App name
**Healthify**
(9 / 30)

## Short description (≤ 80 chars)
**Daily wellness check-ins, step + sleep insights, and gentle reminders that stick.**
(80 / 80)

### Alternates
- `Daily check-ins, mood + sleep tracking, smart reminders. Built for routine.` (75)
- `Track mood, sleep, water, steps. Build a streak that rewards consistency.` (72)
- `A simple daily wellness tracker. Mood, sleep, water, steps, and streaks.` (71)

---

## Full description (≤ 4000 chars)

```
Healthify is a calm, focused daily wellness tracker — not a social network,
not a coach, not a fitness leaderboard. It is one minute a day to notice how
you're doing, with the data to back it up.

WHAT YOU GET

• Daily check-in
  Log your mood, water, food quality, sleep, and a 1–5 rating of the day in
  under 60 seconds. Each entry feeds a personal wellness score so you can see
  trends over time.

• Smart dashboard
  Today's metrics — steps, sleep last night, average resting heart rate,
  water — all pulled live from Android Health Connect (read-only, with your
  per-data-type permission). Nothing is invented or estimated.

• Reliable reminders
  Schedule water, movement, check-in, and wind-down nudges. Reminders use
  exact alarms so a 6:00 PM check-in fires at 6:00 PM, not at 6:14. Edit the
  time with an iOS-style scrolling wheel and turn each one on or off
  independently.

• Insights at a glance
  Weekly mood, weekly wellness average, streak length, and your longest
  streak. The Insights tab shows your week Monday to Sunday with the mood
  you logged for each day, or a blank cell when you skipped.

• Streaks that survive the late check-in
  The day boundary is 6 PM local time — log any time between 6 PM and the
  next morning and your streak continues.

• Cross-device sync
  Reinstall the app, log in to your Google account on a new phone, and your
  history is waiting for you. Sync is anonymous: we never ask for your name
  or email.

• Offline-first
  Open the app on a plane, log a check-in, see your dashboard. Firestore
  syncs whenever you're back online.

PRIVACY THAT MATCHES THE MARKETING

• No ads. No trackers. No data sold to anyone.
• Health Connect data stays on your device. Only daily summaries (e.g.
  "12 345 steps today") leave the phone.
• Anonymous sign-in — no name, email, or phone number required.
• Your local database is excluded from Android cloud backup and device
  transfer so health data does not roam through Google account backup.
• You can request full data deletion at any time by emailing
  hayk.khachatryan25@gmail.com.

Full Privacy Policy: https://deltapkr.github.io/Healthify/privacy/

PERMISSIONS WE ASK FOR

• Notifications — to deliver your reminders.
• Exact alarms — so reminders fire at the precise minute you set.
• Boot completed — to re-arm reminders after a reboot.
• Health Connect (Steps, Sleep, Heart Rate, Distance, Active Calories) —
  read-only, and you grant each one separately.

PERMISSIONS WE WILL NEVER ASK FOR

• Location.
• Camera, microphone, SMS, call logs.
• Contacts, calendar, or files.
• Your name, email, or phone number.

REQUIREMENTS

• Android 8.0 (API 26) or higher.
• Health Connect installed (free, by Google) for the dashboard to show
  steps / heart rate / sleep / distance / calories.

Healthify is not a medical device. It does not diagnose, treat, or prevent
any condition. If you have a health concern, see a clinician.

— Built by DeltaPKR. Feedback: hayk.khachatryan25@gmail.com
```

(~2950 chars)

---

## App category
**Primary:** Health & Fitness
**Tags (up to 5):** wellness tracker, mood, sleep, water reminder, daily check-in

---

## Contact details
- **Email (required, public):** hayk.khachatryan25@gmail.com
- **Phone:** (leave blank — optional)
- **Website:** https://deltapkr.github.io/Healthify/ (or your real domain — update if you buy one)

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
#   01_dashboard.png         home with steps/sleep/HR
#   02_check_in.png          mid check-in (mood + water + food)
#   03_insights.png          weekly mood strip + wellness average
#   04_reminders.png         reminders list with the time-wheel open
#   05_profile.png           streak counter + profile
```

You need 2–8 of these. Recommended order for the listing: dashboard,
check-in, insights, reminders.

---

## Pre-launch checklist

- [ ] Privacy Policy published at `https://deltapkr.github.io/Healthify/privacy/` (or update manifest line 57 to the real URL).
- [ ] AAB uploaded to **Internal testing** track and verified on a personal device.
- [ ] Health Connect demo video recorded + uploaded (see `PLAY_CONSOLE.md` §7).
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
