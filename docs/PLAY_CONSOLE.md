# Healthify — Play Console submission cheat sheet

Each section maps to a form/section in [Google Play Console](https://play.google.com/console).
Copy answers verbatim where exact wording is requested.

App package: `com.DeltaPKR.Healthify`
App name: Healthify
Default language: English (United States) — `en-US`
Category: Health & Fitness

---

## 1. App access

> **Question:** All or some functionality is restricted?

**Answer:** **All functionality is available without special access.**
Justification: anonymous sign-in is automatic; no user login screen, no
gating behind invite codes or paid tiers.

---

## 2. Ads

> **Question:** Does your app contain ads?

**Answer:** **No**

---

## 3. Content rating questionnaire

Category: **Health & Fitness**
Sub-category: **Reference, News, or Educational**

| Question                                                                                                          | Answer |
|-------------------------------------------------------------------------------------------------------------------|--------|
| Does the app contain violence?                                                                                    | No     |
| Does the app contain sexual content?                                                                              | No     |
| Does the app contain profanity?                                                                                   | No     |
| Does the app contain drug, alcohol, or tobacco references?                                                        | No     |
| Does the app contain simulated gambling?                                                                          | No     |
| Does the app contain real-money gambling?                                                                         | No     |
| Does the app collect or share users' location?                                                                    | No     |
| Does the app share users' personal information with third parties?                                                | No (only sub-processors — Firebase) |
| Does the app allow users to interact with other users?                                                            | No     |
| Does the app allow users to purchase digital goods?                                                               | No     |
| Does the app contain user-generated content?                                                                      | No     |

**Expected rating:** Everyone / PEGI 3 / USK 0 / IARC: All Ages.

---

## 4. Target audience and content

**Target age groups:** **18+** (recommended) — wellness data collection is
not appropriate for users under 18 without parent management.

**Appeal to children?** **No** — no characters, gameplay, or content
designed to attract minors.

---

## 5. Data safety form

> **Section:** Data collected and shared

### Data types collected

Check the following in the Play Console form.

#### Personal info
- ☑️ **Name** — Collected, **Not shared**, **Required for app's core functionality**, **Not encrypted in transit**? ❌ No (TLS), **User can request deletion**? ✅ Yes
- ☑️ **Other personal info** (age, gender) — Collected, Not shared, Required, TLS, Deletable ✅

#### Health and fitness
- ☑️ **Health info** (mood, sleep hours, water intake, logged meals and their quality, the nutrition of logged foods — grams, calories, macros, Nutri-Score — and the optional calorie settings, day rating, conditions) — Collected, Not shared, Required for app's core functionality, TLS, Deletable ✅
- ☑️ **Fitness info** (step count, logged activities and their minutes, workouts done in the app — exercises, sets, reps, weights, times, personal records — and the user's routines, weight, height) — Collected, Not shared, Required for app's core functionality, TLS, Deletable ✅
  > Do **not** tick distance, heart rate or active calories here. As of
  > 1.0.15 the app requests none of them, and a Data Safety form that
  > claims more than the manifest requests is itself a policy violation.

#### App activity
- ☑️ **App interactions** (Firebase Analytics events: check-in completed, screen views) — Collected, Not shared, Optional, TLS, **Cannot request deletion** (aggregated)
- ☑️ **In-app search history** (since 1.3.0: food search text and looked-up barcodes, sent to Open Food Facts to fetch results) — Collected, **Processed ephemerally** ✅ (Healthify stores no search history; only foods the user logs), Not shared (user-initiated lookup the user expects to go to the food database), Optional, Purpose: App functionality, TLS

#### App info and performance
- ☑️ **Crash logs** — Collected, Not shared, Optional, TLS, Cannot request deletion (aggregated)
- ☑️ **Diagnostics** (device model, OS version) — Collected, Not shared, Optional, TLS, Cannot request deletion

#### Device or other IDs
- ☑️ **Device or other IDs** — anonymous Firebase Installations ID. Collected, Not shared, Required, TLS, Deletable ✅

### Data types **NOT** collected (leave unchecked)
Financial info, Location (precise/approximate), Messages, Photos & videos,
Audio, Files & docs, Calendar, Contacts, Web browsing history,
Installed apps, User-payment info.
> Photos & videos stays unchecked: barcode scanning runs in Google Play
> services' code scanner, and the app never receives camera frames or holds
> the CAMERA permission.

### Security practices
- ☑️ **Data is encrypted in transit** — Yes, TLS (network security config).
- ☑️ **Users can request data deletion** — Yes, via email to deltapkr.developer@gmail.com.
- ☑️ **Independent security review** — No.
- ☑️ **Family Policy compliance** — Not enrolled.

---

## 6. Permissions declaration

### `USE_EXACT_ALARM` (Android 13+)

> **Question:** Why does your app need exact alarms?

**Answer (paste verbatim):**

> Healthify is a daily wellness reminder app. Users schedule notifications
> for water intake, movement, daily check-ins, and wind-down at specific
> times of day (e.g. exactly 8:00 AM for the morning water reminder). If
> the OS delays a reminder by even five minutes the user-facing contract
> is broken — a 6:00 PM check-in shown at 6:14 PM defeats the routine.
> Alarms are scheduled via `AlarmManager.setExactAndAllowWhileIdle` with
> one PendingIntent per reminder, are user-initiated (created and toggled
> from the in-app Reminders screen), and run at most a few times per day
> per user. This is the **core function** of the reminders feature; the
> app gracefully falls back to `setAndAllowWhileIdle` if the permission
> is revoked.

### `RECEIVE_BOOT_COMPLETED`

Used to re-schedule the user's reminders after device reboot or app
update so reminders survive reboots.

### `POST_NOTIFICATIONS`

Required on Android 13+ to display the reminder notifications above.

### `INTERNET` + `ACCESS_NETWORK_STATE`

Firestore sync, food search / barcode lookup on Open Food Facts (1.3.0+),
and offline state detection.

> No `CAMERA` permission: the barcode scanner is Google Code Scanner,
> which runs in Play services. Check the merged manifest stays free of it.

### Health Connect permissions

`READ_STEPS` and `READ_SLEEP`, and since 1.4.1 `WRITE_EXERCISE`. Nothing
else. See the health apps declaration below.

> **History — do not regress.** Up to 1.0.14 the manifest also declared
> `READ_HEART_RATE`, `READ_DISTANCE` and `READ_ACTIVE_CALORIES_BURNED`.
> Distance and active calories were never read by any code path at all,
> and the heart-rate reading was surfaced in a single dashboard card.
> The Sep 8 2026 production submission was rejected on two counts —
> *"Excessive data access for declared feature"* and *"Insufficient
> Information to Determine App Functionality"* — naming exactly those
> three data types. 1.0.15 removes all three from the manifest and the
> code. Keep the reads identical to
> `HealthConnectManager.requiredPermissions`; the one write is
> `WorkoutHealthSync.writePermission`, requested separately.

---

## 7. Health apps declaration (per Health Connect policy)

> **Where it lives:** Play Console → your app → **Policy** (left nav) →
> **App content** → the **Health apps** card → **Start** (or **Manage**
> once it has been submitted before). It is one card in the same list as
> Data safety and Target audience — not a separate top-level page, and
> not under "Permissions declarations", which is where `USE_EXACT_ALARM`
> in §6 is declared. Google renamed this form; older notes calling it the
> "Health Apps Declaration" under Permissions declarations are stale.
>
> The form has two pages: first declare which health features the app
> offers, then fill the expandable sections, which is where the
> per-data-type Health Connect justification below goes.
>
> Required because the app reads Health Connect data. A January 2026
> policy update tightened the Health Connect justification requirements
> and added medical-device labelling questions, so expect more fields
> than the form had in mid-2025.

### Core app functionality (paste verbatim)

> Healthify is a daily wellness tracker. Through the day the user logs
> water, meals and activities in a few taps; meals can be looked up by
> name or barcode in the Open Food Facts database, with optional calorie
> counting, and workouts can be followed set by set from built-in or
> their own routines, with rest timers and personal records. In the
> evening a short check-in adds their mood and a 1–5 rating of the day. The app turns these into a 0–100 wellness score, a
> streak, and weekly trends on the Insights screen.
>
> Two of the score's inputs — how much the user moved and how much they
> slept — are already recorded by the phone or wearable. Healthify reads
> exactly those two from Health Connect so the user doesn't have to
> recall and retype numbers their device already has. Steps are shown on
> the home dashboard and the Move tab, sleep on the home dashboard, and
> both are inputs to the wellness score that is stored with each
> check-in.
>
> Healthify requests two read permissions and one write permission.
> The write is optional and off by default: when the user turns on "Save
> to Health Connect" in Move settings (or accepts the offer shown after a
> finished workout), the workouts and activities they log in Healthify
> are added to Health Connect as exercise sessions, so their other health
> and fitness apps can see them.

### Per-record-type justification (paste each verbatim)

**Steps (`READ_STEPS`)** — Required for the home dashboard's step card
and the daily wellness score.
> The dashboard and the Move tab show today's total step count against
> the daily step goal the user sets during onboarding. The same figure is one of two
> Health Connect inputs to the 0–100 wellness score saved with each daily
> check-in (steps contribute up to 20 points, scaled against the user's own
> daily step goal), and it is written into the check-in record so the Insights
> screen can chart the week. Read window: local midnight → now. Without
> this permission the step card is empty and the user must type their
> step count by hand on the check-in screen.

**Sleep (`READ_SLEEP`)** — Required for the home dashboard's sleep card
and the daily wellness score.
> The dashboard shows the total hours the user slept last night. The same
> figure is the second Health Connect input to the wellness score (sleep
> contributes up to 20 points, scaled against the user's own sleep goal) and
> is stored with the check-in for the weekly trend; when it is available the
> check-in skips its manual sleep question. Read window: yesterday
> 18:00 local → now, with sessions clipped to the window edges so an
> overnight session is not double-counted; the late start keeps the
> feature correct for night-shift workers and late risers. Without this
> permission the sleep card is empty and the user must enter sleep hours
> manually.

**Exercise (`WRITE_EXERCISE`, since 1.4.1)** — Optional; saves the user's
workouts to Health Connect.
> Off by default and requested on its own, never together with the two
> reads: only when the user turns on "Save to Health Connect" in Move
> settings or taps "Turn on" on the offer shown after finishing a
> workout. Each finished workout from the workout player, and each
> activity logged by type and minutes, is written as one exercise
> session: exercise type, title, start and end time — no sets, weights,
> notes or calorie estimates. Each record carries the workout's own id,
> so editing a workout in Healthify updates its copy and deleting it
> deletes the copy. Turning the setting on also saves workouts logged
> earlier. Healthify does not read exercise sessions back and holds no
> exercise read permission. Without this permission workouts stay in
> Healthify only.

**No other Health Connect data types are requested.** Heart rate,
distance and active calories were removed in version 1.0.15.

### Data handling
- All reads are **on-demand**, triggered by the user opening the
  dashboard or submitting a check-in. There is no background polling, no
  scheduled job and no foreground service reading health data.
- Health Connect readings **stay on-device**. Only the daily *summary*
  the user checks in with (e.g. total steps and sleep hours for that day)
  is written to Firestore as part of the check-in record, under an
  anonymous account with no name, email or phone number.
- The one write permission, exercise, is used only after the user turns
  it on, and only for workouts and activities they logged in Healthify.
  Writes happen when a workout is saved, edited or deleted, or at app
  launch to catch up on ones that failed; there is no other background
  work.
- Health data is excluded from Android cloud backup and device transfer
  (`backup_rules.xml` / `data_extraction_rules.xml`).
- Users can request full deletion at deltapkr.developer@gmail.com, and
  can revoke any permission at any time in Health Connect.

### In-app rationale (what a reviewer will see)
On first launch, before the system Health Connect sheet appears, the app
shows its own dialog naming each data type and what it is used for
(`HealthConnectRationaleDialog` in `MainActivity.kt`, driven by
`HealthConnectManager.permissionRationales`). Manifest, rationale dialog
and this declaration are all kept in sync with that one list.

The exercise write is asked separately and in context: Move tab → settings
button (top right) → **Save to Health Connect** (`MoveSettingsDialog` in
`HealthConnectWriteUi.kt`), which lists what is and isn't saved before the
switch asks for the permission, or the one-time **Add workouts to Health
Connect?** card on the summary after a workout. Health Connect's privacy
policy link opens the privacy policy (`HealthPrivacyActivity`).

Whenever Health Connect is installed but the permissions are not granted,
the dashboard shows a **"Fill in steps and sleep automatically"** card
(`ConnectHealthConnectCard` in `DashboardScreen.kt`) that reopens that same
rationale. So a reviewer who dismisses the first prompt still has an
obvious in-app route to the feature rather than a permanently empty
dashboard — which is what produced the "Insufficient Information" finding.

### Demo video / screenshot evidence
Required by Play. Capture a 30–60s screen recording showing, in order:
1. The in-app rationale dialog explaining Steps and Sleep.
2. The system Health Connect permission grant flow.
3. The dashboard displaying live step and sleep values fetched from
   Health Connect.
4. The daily check-in screen where those values are persisted, and the
   resulting wellness score / streak.
5. (1.4.1, exercise write) Move tab → settings → "Save to Health
   Connect": the explanation, the switch, the system sheet asking only
   for exercise; then finish a short workout and show it in Health
   Connect (Settings → Health Connect → Data and access → Activity →
   Exercise), with Healthify as the source.

Make sure the test device has real (or seeded) step and sleep data in
Health Connect before recording — an empty dashboard is what triggered
the "Insufficient Information" rejection.

Upload via Play Console → App content → Permissions declarations.

---

## 8. Privacy policy URL

**URL to enter in Play Console:** `https://deltapkr.github.io/Healthify/privacy/`

> ⚠️ **Action required before submission:** publish `docs/PRIVACY_POLICY.md`
> as a public HTTPS page at the URL above (or a different URL — update
> `AndroidManifest.xml` line 57 to match). Cheapest hosts that work:
> GitHub Pages, Cloudflare Pages, Netlify (all free).

---

## 9. App content additional declarations

| Form                              | Answer                                                  |
|-----------------------------------|---------------------------------------------------------|
| Government apps                   | **No**                                                  |
| Financial features                | **No**                                                  |
| Health features                   | **Yes — wellness tracker** (not a medical device)       |
| News                              | **No**                                                  |
| COVID-19 contact tracing/status   | **No**                                                  |
| Data Safety                       | See §5                                                  |
| Ads                               | **No** (see §2)                                         |
| Children's policy (Designed for Families) | **Not enrolled**                                |

---

## 10. Release rollout plan

1. **Internal testing track** (1–5 testers, your own Google account)
   - Upload `app/build/outputs/bundle/release/app-release.aab`.
   - Verify install, Health Connect grant flow, reminder fires, crash-report
     pipeline (`adb shell am crash com.DeltaPKR.Healthify` to force a crash).
2. **Closed testing track** (≤100 testers via email or Google Group)
   - Run for ≥14 days. Required for first-time personal-account apps as of
     Nov 2023 Play policy.
3. **Open testing / Production**.

---

## 11. App signing

Play App Signing is **mandatory** for new apps as of Aug 2021. When you
upload the AAB:

1. Play offers to manage the app-signing key (upload key ≠ signing key).
2. Upload key = the keystore at `~/.android/keystores/healthify-release.jks`.
3. Play generates the actual signing key; we keep the upload key for
   future uploads.
4. **Back up `healthify-release.jks` + its password** somewhere off-machine
   (password manager, hardware token, encrypted USB). Losing the upload
   key requires Play support intervention.

### Upload-key fingerprints (already captured)
- **SHA-1:** `93:BD:6E:36:50:3A:66:93:38:CD:74:79:67:D0:EA:2A:A2:5F:52:F2`
- **SHA-256:** `00:03:5B:0D:8A:1F:53:61:B0:60:C9:7F:10:76:6C:47:EA:68:6C:CB:8C:70:1F:03:94:D9:2E:61:5C:E8:22:76`

Add the SHA-1 fingerprint to your Firebase project (Project settings →
Your apps → Android → Add fingerprint) so Firebase Auth keeps working with
the release-signed APK.

---

## 12. Resubmission runbook — Sep 2026 Health Connect rejection

Production access was granted, but the 1.0.14 submission was rejected on
2026-09-08 under **Health Connect by Android Permissions policy** with two
issues:

1. **"Excessive data access for declared feature"** — naming
   `ActiveCaloriesBurned`, `Distance` and `HeartRate`.
2. **"Insufficient Information to Determine App Functionality"** — asking
   for more rationale on those same three data types.

**Root cause.** The manifest declared five Health Connect permissions.
`READ_DISTANCE` and `READ_ACTIVE_CALORIES_BURNED` were never referenced by
any code path — not in `HealthConnectManager.requiredPermissions`, no read
function, no UI. `READ_HEART_RATE` was read and shown, but only in a card
gated on a non-zero value from a one-hour window, so on a reviewer's device
it rendered nothing. The store listing and the declaration also described
distance and calories as *"Optional movement metric"* — self-described as
non-essential, which is the opposite of what Minimum Scope requires.

**What 1.0.15 (versionCode 16) changes.**

| Change | Where |
|---|---|
| Removed `READ_HEART_RATE`, `READ_DISTANCE`, `READ_ACTIVE_CALORIES_BURNED` | `AndroidManifest.xml` |
| Removed `HeartRateRecord` read, permission and `HealthData` field | `HealthConnectManager.kt` |
| Removed the heart-rate dashboard card and UI state | `DashboardScreen.kt` |
| Stopped writing heart rate into the check-in record | `CheckInScreen.kt` |
| Added `permissionRationales` as the single source of truth for the rationale copy | `HealthConnectManager.kt` |
| Added an in-app rationale dialog shown before the system permission sheet | `MainActivity.kt` |
| Added a dashboard "connect Health Connect" card so the feature is reachable after dismissing the prompt | `DashboardScreen.kt` |
| Rewrote the declaration, listing, Data Safety and privacy policy copy | this file, `STORE_LISTING.md`, `PRIVACY_POLICY.md`, `docs/privacy/index.html` |

The `heartRateAvg` column stayed in the `check_ins` table (always `0`)
until 1.1.0, whose Room migration (DB v1 → v2) drops it — along with any
heart-rate values stored by ≤ 1.0.14 — without touching the rest of the
check-in history.

**Before you resubmit — every one of these:**

- [ ] Confirm the packaged manifest really contains only two health
      permissions:
      ```bash
      ./gradlew :app:bundleRelease && grep -o 'android.permission.health[^"]*' app/build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml | sort -u
      ```
      Expect exactly `READ_SLEEP` and `READ_STEPS`.
- [ ] Re-record the demo video on a device that **has real step and sleep
      data in Health Connect**. Show the in-app rationale dialog → the
      system grant sheet → the populated dashboard → a check-in being
      saved. An empty dashboard is what failed last time.
- [ ] Update the **Health apps** declaration (Policy → App content →
      Health apps → Manage) with the §7 copy above, and delete the old
      heart-rate / distance / calories entries.
- [ ] Update the **Data Safety** form per §5 — untick distance, heart rate
      and active calories under Fitness info.
- [ ] Republish `docs/privacy/` to GitHub Pages so the live policy at
      https://deltapkr.github.io/Healthify/privacy/ matches the new
      permission set. The manifest points reviewers at this URL, so a stale
      page listing five data types re-creates the mismatch on its own.
- [ ] Paste the updated full description from `STORE_LISTING.md`.

---

## 13. Release 1.2.0 (versionCode 19) — all-day logging

What changed for users: water, meals and activities can be logged any time
(Home quick-log row, the new Food and Move tabs, and a "+1 glass" button on
water reminders); the evening check-in pre-fills from those logs. Bottom
nav is now Home · Food · ✓ · Move · Insights; Profile opens from the Home
avatar and Reminders moved inside Profile. No new permissions, no new
Health Connect data types.

**Before uploading:**

- [ ] **Deploy `firestore.rules`** (repo root) in Firebase Console →
      Firestore → Rules. 1.2.0 writes three new collections under
      `users/{uid}/` (`water`, `meals`, `workouts`). If the live rules only
      allow `profile` and `checkins`, those writes fail silently.
- [ ] Health apps declaration (§7): replace the "Core app functionality"
      text with the current §7 copy (all-day logs; steps on Home and Move),
      and the per-type text (20 points against the user's own goal).
- [ ] Data safety (§5): no new categories — meals are Health info and
      activities Fitness info, both already ticked. Update descriptions
      only if the form asks.
- [ ] Privacy policy: the `docs/privacy/` update ships with the push to
      `main` (GitHub Pages). Check the live page lists the new logs.
- [ ] Store listing: new phone screenshots for the new tabs.
- [ ] Release notes — mention that the morning score now means "today so
      far" (it no longer shows last night's check-in until 18:00).

---

## 14. Release 1.3.0 (versionCode 20) — food logging and optional calories

What changed for users: meals can be added from an Open Food Facts search,
a barcode scan, or the user's own foods, with an amount in grams or
servings. Calorie counting is optional and off by default; when on, the
Food tab shows a daily target (Mifflin-St Jeor estimate or the user's own
number) and calories/macros per meal. No new Android permissions (no
`CAMERA` — the scanner runs in Play services), no new Health Connect data
types.

**Before uploading:**

- [ ] **Firestore rules:** nothing new — meals still write to
      `users/{uid}/meals`, now with extra fields. The 1.2.0 rules cover it.
- [ ] Data safety (§5): tick **App activity → In-app search history**
      (collected, processed ephemerally, not shared, optional, app
      functionality). Health info description now includes food nutrition
      and calorie settings — no new category.
- [ ] Health apps declaration (§7): on the first page tick **Nutrition**
      (food logging, calorie and macro tracking) alongside the existing
      features. Replace "Core app functionality" with the current §7 copy.
      Health Connect per-type text is unchanged (still Steps + Sleep only;
      the app does not read or write Health Connect nutrition).
- [ ] Privacy policy: `docs/privacy/` now lists Open Food Facts and Google
      Code Scanner. Check the live GitHub Pages copy after the push.
- [ ] Store listing: mention food search, barcode scanning and optional
      calorie counting; add a Food tab screenshot. Credit "Food data from
      Open Food Facts (ODbL)" in the full description.
- [ ] Release notes — e.g. "Log meals by searching or scanning a barcode
      (food data from Open Food Facts). Optional calorie counting with a
      personal daily target — off unless you turn it on in Food settings."

---

## 15. Release 1.4.0 (versionCode 21) — workouts

What changed for users: the Move tab gets workouts. Ten built-in routines
(mobility, desk break, bodyweight, core, low-impact cardio, dumbbell,
gym upper/lower, barbell 5×5, evening stretch), routines of their own,
an exercise library of ~870 exercises (free-exercise-db, public domain,
bundled — no network), and a workout player: each set saved when ticked,
last time's numbers, rest timer, 3-2-1 countdown for timed sets, personal
records and a summary. Calorie estimates for activities and workouts
show only with calorie counting on. No new Android permissions, no new
Health Connect data types (writing workouts to Health Connect is 1.4.1).

**Before uploading:**

- [ ] **Firestore rules:** nothing to deploy — the new `routines` and
      `customExercises` collections are under `users/{uid}/`, which the
      wildcard rule already covers.
- [ ] Data safety (§5): no new category. Workouts and routines are Fitness
      info, already ticked; update its description if the form asks.
- [ ] Health apps declaration (§7): on the first page make sure the
      activity/fitness feature is ticked (it should be already, for steps)
      and replace "Core app functionality" with the current §7 copy.
      Health Connect per-type text is unchanged.
- [ ] Privacy policy: `docs/privacy/` now lists workouts, routines and
      custom exercises. Check the live GitHub Pages copy after the push.
- [ ] Store listing: mention guided workouts, the exercise library and
      personal records; add screenshots of the Move tab, a routine and the
      player.
- [ ] Release notes — e.g. "Workouts are here: 10 ready-made routines or
      your own, 870+ exercises with how-tos, a set-by-set player with rest
      timer, and personal records."

## 16. Release 1.4.1 (versionCode 22) — save workouts to Health Connect

What changed for users: an optional "Save to Health Connect" switch in
Move settings (also offered once after a finished workout). When on,
finished workouts and logged activities are written to Health Connect as
exercise sessions — type, title, start and end time — and edits and
deletes follow. Also: Health Connect's privacy policy link now opens the
privacy policy, and Android 13-and-lower devices get the permissions
rationale activity Health Connect expects.

**New permission:** `android.permission.health.WRITE_EXERCISE`. This is
the release that can be rejected, so it ships on its own.

**Before uploading:**

- [ ] Health apps declaration (§7): add the **Exercise (WRITE_EXERCISE)**
      justification, replace "Core app functionality" with the current
      copy, and update the data-handling answers (the app now writes).
      If the form's first page asks which features write data, tick the
      fitness/activity one.
- [ ] Demo video (§7 item 5): record the write flow and upload it with the
      declaration.
- [ ] Data safety (§5): no change. Health Connect is on the device;
      nothing new leaves it.
- [ ] Privacy policy: `docs/privacy/` now says workouts can be written to
      Health Connect. Check the live GitHub Pages copy after the push.
- [ ] Firestore rules: nothing to deploy.
- [ ] Release notes — e.g. "Your workouts can now be saved to Health
      Connect, so your other fitness apps see them too. Turn it on in Move
      settings."
- [ ] Roll out to internal testing first and turn the switch on from a
      Play-installed build, on Android 14+ and, if you have one, an
      Android 13-or-lower phone with the Health Connect app.
