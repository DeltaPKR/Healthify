# Fernday — Store assets

Everything uploaded to the Play Store listing, and how to rebuild it.

## Files

| File                                   | Upload to Play as        | Spec                     |
|----------------------------------------|--------------------------|--------------------------|
| `store-assets/icon-512.png`            | App icon                 | 512 × 512 PNG            |
| `store-assets/feature-1024x500.png`    | Feature graphic          | 1024 × 500 PNG           |
| `store-assets/screenshots/01_score.png` … `08_insights.png` | Phone screenshots, in this order | 1080 × 1920 PNG (9:16) |

Sources: `icon-512.svg` (rendered by `render.js`, needs `sharp`), and
`render-screenshots.js`, which renders the screenshots and the feature
graphic with headless Chrome from raw app captures.

## Screenshots and feature graphic

Each screenshot is a headline and a subline over a real app screen in a
phone frame, on the app's own navy and green with the Plus Jakarta Sans
font from `app/src/main/assets/fonts`. Headlines and their order live in
`SLIDES` in `render-screenshots.js`.

**Rules (Play policy, learned the hard way):**
- Only Fernday's own name, colours and screens. No other app's look, and
  never the old name (see `PLAY_CONSOLE.md` §17).
- No third-party brand names on screen. A live Open Food Facts search shows
  brands (e.g. yogurt makers), so the food slide uses generic foods.
- Demo data only: the user is the fictional "Alex"; never a real person.
- Captions must describe what the screen shows; no "#1", "best", awards or
  prices.

### 1. Capture the raw screens (emulator, debug build)

The 2026-10 set was taken on the `healthify35` AVD (1080 × 2400, Android 15):

1. `adb root`, then set a fixed time so the week charts are full:
   `adb shell settings put global auto_time 0` and
   `adb shell date 101019302026.00` (Saturday 19:30).
2. `./gradlew :app:installDebug`, `adb shell pm clear com.DeltaPKR.Healthify`,
   onboard as "Alex", decline Health Connect, then force-stop the app.
3. Seed demo history with `run-as com.DeltaPKR.Healthify sqlite3
   databases/healthify.db`: two weeks of `check_ins` (+ matching
   `water_logs`), a few `workout_sessions`, last Thursday's "Gym Upper
   Body" with its `workout_sets` (the baseline today's records beat),
   today's `meal_entries`, and some generic `food_items` with `useCount > 0`
   for the Recent list. Set `users.countCalories = 1` and the streak
   fields.
4. Clean status bar:
   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1930
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
   adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   ```
5. Use the app normally and capture each screen with
   `adb exec-out screencap -p > <raw-dir>/<name>.png`:

   | Raw file          | Screen                                                  |
   |-------------------|---------------------------------------------------------|
   | `home.png`        | Home after the check-in                                 |
   | `checkin.png`     | The check-in celebration                                |
   | `food.png`        | Food tab, calories on                                   |
   | `food_search.png` | Add food → Recent list (generic foods)                  |
   | `move.png`        | Move tab after the workout                              |
   | `player.png`      | Player with a record set ticked and the rest timer      |
   | `records.png`     | The finished workout's summary (offer card dismissed)   |
   | `insights.png`    | Insights                                                |

### 2. Render

```bash
node docs/store-assets/render-screenshots.js <raw-dir>
```

Writes the 8 screenshots and `feature-1024x500.png`. Set `CHROME` if
Chrome isn't at the default Windows path.

## Design notes

- Background: the app's navy (`#070D1A` → `#0D1730`) with soft green,
  sky and lavender glows; accent words use the green → sky gradient.
- The heart is the Material `Filled.Favorite` path, as in the launcher
  icon and splash.
- The feature graphic keeps the wordmark on the left; Play may crop the
  right edge on small surfaces, which only clips the phones.
