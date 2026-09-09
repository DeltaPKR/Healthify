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
- ☑️ **Health info** (mood, sleep hours, water intake, day rating, conditions) — Collected, Not shared, Required for app's core functionality, TLS, Deletable ✅
- ☑️ **Fitness info** (step count, weight, height) — Collected, Not shared, Required for app's core functionality, TLS, Deletable ✅
  > Do **not** tick distance, heart rate or active calories here. As of
  > 1.0.15 the app requests none of them, and a Data Safety form that
  > claims more than the manifest requests is itself a policy violation.

#### App activity
- ☑️ **App interactions** (Firebase Analytics events: check-in completed, screen views) — Collected, Not shared, Optional, TLS, **Cannot request deletion** (aggregated)

#### App info and performance
- ☑️ **Crash logs** — Collected, Not shared, Optional, TLS, Cannot request deletion (aggregated)
- ☑️ **Diagnostics** (device model, OS version) — Collected, Not shared, Optional, TLS, Cannot request deletion

#### Device or other IDs
- ☑️ **Device or other IDs** — anonymous Firebase Installations ID. Collected, Not shared, Required, TLS, Deletable ✅

### Data types **NOT** collected (leave unchecked)
Financial info, Location (precise/approximate), Messages, Photos & videos,
Audio, Files & docs, Calendar, Contacts, Web browsing, Search history,
Installed apps, User-payment info.

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

Firestore sync + offline state detection.

### Health Connect permissions

`READ_STEPS` and `READ_SLEEP`. Nothing else. See the health apps declaration
below.

> **History — do not regress.** Up to 1.0.14 the manifest also declared
> `READ_HEART_RATE`, `READ_DISTANCE` and `READ_ACTIVE_CALORIES_BURNED`.
> Distance and active calories were never read by any code path at all,
> and the heart-rate reading was surfaced in a single dashboard card.
> The Sep 8 2026 production submission was rejected on two counts —
> *"Excessive data access for declared feature"* and *"Insufficient
> Information to Determine App Functionality"* — naming exactly those
> three data types. 1.0.15 removes all three from the manifest and the
> code. Keep this list identical to
> `HealthConnectManager.requiredPermissions`.

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

> Healthify is a daily wellness check-in tracker. Its single core feature
> is a once-a-day check-in: the user logs mood, water, food quality,
> sleep and a 1–5 rating of their day, and the app turns that entry into
> a 0–100 wellness score, a streak, and a weekly trend on the Insights
> screen.
>
> Two of those inputs — how much the user moved and how much they slept —
> are already recorded by the phone or wearable. Healthify reads exactly
> those two from Health Connect so the check-in is accurate and takes
> under a minute instead of asking the user to recall and retype numbers
> their device already has. Both values are displayed on the home
> dashboard the moment the app opens, and both are inputs to the wellness
> score that is stored with each check-in.
>
> Healthify requests two read permissions and no write permissions.

### Per-record-type justification (paste each verbatim)

**Steps (`READ_STEPS`)** — Required for the home dashboard's step card
and the daily wellness score.
> The dashboard shows today's total step count against the daily step
> goal the user sets during onboarding. The same figure is one of two
> Health Connect inputs to the 0–100 wellness score saved with each daily
> check-in (steps contribute up to 15 points, scaled against a 10 000-step
> reference), and it is written into the check-in record so the Insights
> screen can chart the week. Read window: local midnight → now. Without
> this permission the step card is empty and the user must type their
> step count by hand on the check-in screen.

**Sleep (`READ_SLEEP`)** — Required for the home dashboard's sleep card
and the daily wellness score.
> The dashboard shows the total hours the user slept last night. The same
> figure is the second Health Connect input to the wellness score (sleep
> contributes up to 15 points, scaled against an 8-hour reference) and is
> stored with the check-in for the weekly trend. Read window: yesterday
> 18:00 local → now, with sessions clipped to the window edges so an
> overnight session is not double-counted; the late start keeps the
> feature correct for night-shift workers and late risers. Without this
> permission the sleep card is empty and the user must enter sleep hours
> manually.

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
- The app holds **no write permissions** — it never writes to Health
  Connect.
- Health data is excluded from Android cloud backup and device transfer
  (`backup_rules.xml` / `data_extraction_rules.xml`).
- Users can request full deletion at deltapkr.developer@gmail.com, and
  can revoke either permission at any time in Health Connect.

### In-app rationale (what a reviewer will see)
On first launch, before the system Health Connect sheet appears, the app
shows its own dialog naming each data type and what it is used for
(`HealthConnectRationaleDialog` in `MainActivity.kt`, driven by
`HealthConnectManager.permissionRationales`). Manifest, rationale dialog
and this declaration are all kept in sync with that one list.

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

The `heart_rate_avg` column stays in the `check_ins` table and is always
`0`. The database is `version = 1` with no migrations, so dropping the
column would need a migration or would destroy existing check-ins. Nothing
writes it and no screen reads it.

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
