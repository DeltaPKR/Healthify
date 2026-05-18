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
- ☑️ **Fitness info** (step count, distance, heart rate, active calories, weight, height) — Collected, Not shared, Required for app's core functionality, TLS, Deletable ✅

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

`READ_STEPS`, `READ_SLEEP`, `READ_HEART_RATE`, `READ_DISTANCE`,
`READ_ACTIVE_CALORIES_BURNED`. See Health Apps Declaration below.

---

## 7. Health Apps Declaration (per Health Connect policy)

> Required because the app reads Health Connect data. Submit through the
> **Permissions Declaration** section in Play Console after uploading the
> AAB.

### Intended use
> Healthify is a personal wellness tracker. With explicit per-record
> permission it reads the user's Steps, Sleep Sessions, Heart Rate,
> Distance, and Active Calories Burned from Android Health Connect and
> presents them on a daily dashboard and weekly insights screen.

### Per-record-type justification (paste each verbatim)

**Steps:** Shown on the home dashboard as today's step count vs the
user's daily step goal. Contributes to the wellness-score calculation.

**Sleep:** Sum of last-night sleep sessions is shown on the dashboard and
included in the wellness-score calculation. Window: yesterday 18:00 →
now (local), clipped to session edges so overnight sessions are not
double-counted.

**Heart rate:** Average resting heart rate from the past hour is shown
on the dashboard.

**Distance:** Optional movement metric on the dashboard.

**Active calories burned:** Optional movement metric on the dashboard.

### Data handling
- All reads are on-demand; no background polling.
- Health Connect data **stays on-device**; only daily *summaries* (e.g.
  total steps for the day) are uploaded to Firestore as part of the
  daily check-in record.
- The app holds **no write permissions**.

### Demo video / screenshot evidence
Required by Play. Capture a 30–60s screen recording showing:
1. The Health Connect permission grant flow.
2. The dashboard displaying live values fetched from Health Connect.
3. The daily check-in screen where the summarised values are persisted.

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
