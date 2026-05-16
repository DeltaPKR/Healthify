# 🌿 Healthify — Android App

> **Hackathon project** · Jetpack Compose · Room · WorkManager · Health Connect · Firebase

---

## 📱 What's Inside

| Feature | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Local database | Room (users, check-ins, reminders) |
| Health data | Health Connect API (steps, sleep, heart rate) |
| Background notifications | WorkManager (daily reminders, check-in nudge) |
| Cloud sync | Firebase Firestore (anonymous auth, offline-safe) |
| Streak system | Custom `StreakManager` with milestone notifications |

---

## 🛠 Prerequisites

Install these **before** opening the project:

1. **Android Studio Hedgehog (2023.1.1)** or newer
   → https://developer.android.com/studio

2. **JDK 17** (bundled with Android Studio – no separate install needed)

3. **Android device** running Android 9.0+ (API 26+ for install; Health Connect needs API 28+)
   OR **Android Emulator** with API 34 image

4. **USB cable** (for device demo) OR **Wi-Fi ADB** (optional)

---

## 🔥 Firebase Setup (15 minutes)

### Step 1 – Create Firebase project

1. Go to https://console.firebase.google.com
2. Click **"Add project"** → name it **"Healthify"** → Continue
3. Disable Google Analytics (not needed) → **Create project**

### Step 2 – Add Android app

1. Click the **Android icon** (➕ Add app)
2. Package name: `com.healthify.app`
3. App nickname: `Healthify`
4. Click **Register app**

### Step 3 – Download `google-services.json`

1. Click **"Download google-services.json"**
2. Move the file to: `healthify/app/google-services.json`
   *(it must be in the `/app` folder, not the project root)*

### Step 4 – Enable Firestore

1. In Firebase Console → **Firestore Database** → **Create database**
2. Choose **"Start in test mode"** (for hackathon – allows all reads/writes for 30 days)
3. Select any region → **Enable**

### Step 5 – Enable Authentication

1. Firebase Console → **Authentication** → **Get started**
2. Click **"Anonymous"** provider → Enable → **Save**

---

## 📦 Health Connect Setup (5 minutes)

Health Connect is a **separate app** on Android devices that acts as the health data hub.

### On a real device:

1. Open **Google Play Store**
2. Search **"Health Connect"** by Google LLC → Install
3. Open Health Connect → Grant it access to your fitness apps
   (Samsung Health, Google Fit, etc. will appear here)

### On emulator:

1. Use an **API 34 emulator** (Pixel 7 recommended)
2. Health Connect comes pre-installed on API 34 system images

> **Note:** During the hackathon demo, if Health Connect is not available, the app automatically falls back to the values entered in the check-in. The app always works.

---

## 🚀 Running the App

### Option A – Real Android Device (recommended for demo)

1. On your Android phone, go to:
   **Settings → About Phone → tap "Build Number" 7 times**
   → This enables Developer Mode

2. Go to **Settings → Developer Options → USB Debugging** → Turn ON

3. Connect phone to laptop via USB cable

4. On the phone, tap **"Allow"** when the USB debugging prompt appears

5. In Android Studio:
   - Open the project folder `healthify/`
   - Wait for Gradle sync to complete (2–5 min first time)
   - At the top toolbar, your device name should appear in the device selector
   - Click the green **▶ Run** button (or press `Shift+F10`)

6. The app installs and launches automatically on your phone 🎉

### Option B – Android Emulator

1. In Android Studio → **Device Manager** (right panel icon)
2. Click **"Create Device"**
3. Choose **Pixel 7** → Next
4. Select **API 34** system image (download if needed ~1.5GB) → Next → Finish
5. Click ▶ to start the emulator
6. Click the green **▶ Run** button in Android Studio

---

## 🔧 Troubleshooting

### ❌ "Gradle sync failed"
```
File → Invalidate Caches → Invalidate and Restart
```
Then wait for sync to complete.

### ❌ "google-services.json not found"
Make sure the file is at exactly: `app/google-services.json`
Not in the root folder.

### ❌ "No device connected"
- Check USB cable (try a different port/cable)
- Ensure USB Debugging is enabled
- Run `adb devices` in terminal to verify connection

### ❌ Health Connect permissions denied
The app shows a permission request dialog. Tap **"Allow all"**.
If it opens Health Connect app, grant access there, then return to Healthify.

### ❌ App crashes on start
Check **Logcat** in Android Studio (bottom panel) for the error.
Most common cause: `google-services.json` missing or wrong package name.

---

## 📂 Project Structure

```
app/src/main/java/com/healthify/app/
│
├── HealthifyApp.kt              ← Application class (DB, repo, init)
├── MainActivity.kt              ← Nav graph entry point
│
├── data/
│   ├── db/AppDatabase.kt        ← Room DB (Users, CheckIns, Reminders)
│   └── repository/AppRepository.kt
│
├── health/
│   └── HealthConnectManager.kt  ← Steps, sleep, heart rate
│
├── notifications/
│   └── NotificationWorker.kt    ← WorkManager + scheduler + BootReceiver
│
├── firebase/
│   └── FirebaseSync.kt          ← Firestore sync (anonymous auth)
│
├── streak/
│   └── StreakManager.kt         ← Daily streak calculation + milestones
│
└── ui/
    ├── theme/Theme.kt            ← Dark teal design tokens
    ├── onboarding/OnboardingScreen.kt
    ├── dashboard/DashboardScreen.kt   ← Main screen + streak banner
    ├── checkin/CheckInScreen.kt       ← 5-question daily check-in
    └── NotificationsAndInsightsScreens.kt
```

---

## 🔥 Streak System

The streak tracks consecutive **healthy days**. A day is healthy when:

| Metric | Threshold |
|---|---|
| Water | ≥ 6 glasses |
| Sleep | ≥ 6 hours |
| Steps | ≥ 5,000 |
| Mood | ≥ "Okay" (score 2+) |

**Milestone notifications** fire at streaks: 3, 7, 14, 21, 30, 60, 90, 100, 365 days.

The streak badge changes as it grows:
- 🌱 0 days (starting out)
- 🌿 1–2 days (building)
- 🔥 3–6 days (on fire)
- ⚡ 7–13 days (one week)
- 🌟 14–29 days (two weeks)
- 👑 30+ days (champion)

---

## 🎯 Demo Script (Hackathon Presentation)

1. **Cold launch** → Splash screen animates → Onboarding starts
2. Fill in name **"Demo"**, age **25**, gender, height/weight
3. Select conditions: **"🧠 Anxiety"** → goals: **"💧 Drink more water"**, **"😴 Better sleep"**
4. Tap "Let's Go" → Dashboard loads with animated health score ring
5. Show the **streak banner** (will be 0 on first run – that's fine, explain the system)
6. Tap the green **❤️ Check-in** button → walk through 5 questions
7. Select good answers → submit → show the **wellness score + streak update**
8. Navigate to **📊 Insights** → show weekly charts
9. Navigate to **🔔 Reminders** → toggle medication reminder → explain WorkManager
10. Pull down notification shade → show a queued reminder notification
11. Mention: **Firebase syncs every check-in** → show Firestore console live

---

## 📋 Required Permissions Summary

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Show health reminders |
| `RECEIVE_BOOT_COMPLETED` | Reschedule reminders after reboot |
| `health.READ_STEPS` | Step count from Health Connect |
| `health.READ_SLEEP` | Sleep hours from Health Connect |
| `health.READ_HEART_RATE` | Heart rate monitoring |
| `INTERNET` | Firebase sync |

---

## 🧱 Dependencies at a Glance

```
Compose BOM 2024.06.00     UI framework
Room 2.6.1                 Local SQLite database  
WorkManager 2.9.0          Background task scheduling
Health Connect 1.1.0-alpha07  Google health data API
Firebase BOM 33.1.0        Cloud sync (Firestore + Auth)
DataStore 1.1.1            User preferences
Accompanist 0.34.0         Runtime permissions helper
```

---

*Built for hackathon · Package: com.healthify.app · minSdk 26 · targetSdk 34*
