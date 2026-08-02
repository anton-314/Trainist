# Trainist

A minimalistic, open-source nutrition and workout tracking app for Android.

## Features

### Nutrition
- Log meals by scanning a barcode or searching [Open Food Facts](https://world.openfoodfacts.org/) by name — results are filtered to products sold in your country, because an unfiltered worldwide search buries the thing you actually meant
- **Recently eaten** shortcuts — your food history and custom foods appear instantly when you open the food search, and always rank above the online results
- Daily overview with an animated, segmented calorie ring and pastel-colored progress bars for protein, carbs, and fat
- **Clean-eating tags** — mark foods Healthy / Neutral / Unhealthy; the ring segments by tag and a "% clean" score tracks how much of your intake is clean
- **Meal sections** grouped by breakfast/lunch/dinner/snack, each with its own kcal total; copy yesterday's meal into today with one tap
- **Navigate between days** — track past meals or plan ahead; jump back to today with one tap
- **Weight tracking** — log today's weight directly from the overview with a single tap; set an optional target weight
- **Edit food entries** — swipe left-to-right to edit amount/meal/tag, right-to-left to delete (with undo)
- Set your own daily macro targets, with built-in recommendations
- **Calorie calculator** — a six-step BMR/TDEE wizard (Mifflin-St Jeor) that turns your body data and goal into a calorie target, and says what weekly rate of change that implies

### Workouts
- **Templates** — build reusable workout templates, plan each slot's sets individually (warm-up, normal, drop, failure), and reorder everything by drag-and-drop
- **Supersets** — link adjacent exercises into a group, in the plan or on the fly; the rest timer then only runs after the group's last exercise, which is the whole point of supersetting
- **Live session tracking** — start a session from a template or from scratch, log weight/reps per set, check off completed sets, add/remove/reorder exercises and sets on the fly
- **Inline history hints** — see what you lifted last time as placeholder text while logging a new set
- **Volume & estimated 1RM** — per-exercise summary computed live as you train
- **Personal records** — automatic PR detection and a trophy badge wherever a record-setting set shows up
- **Rest timer** — automatic countdown after checking off a set, with a background notification (chronometer-style) that alerts you when rest is over — works even while the app is closed or the phone is locked
- **Workout history** — a calendar view of trained days; edit past sessions' sets in place
- **Exercise catalog** — searchable/filterable library of 800+ exercises (muscle groups, equipment, mechanics), plus your own custom exercises
- **Exercise detail** — per-exercise page with metadata, current PR, and an all-time strength progression chart

### Stats
- Reorderable chart cards: **weight**, **body measurements**, **progressive overload**, **calories**, **clean-eating trend**, **strength progression** per exercise, and **training frequency**
- **Body measurements** — neck, chest, waist, hips, biceps, thigh and calf in cm, with their own trend chart (numbers only, no photos)
- **Progressive overload** — one chain-linked strength index across all your exercises, so a rotating split or a new exercise can't fake progress, plus ranked coaching tips that read the cause from your actual weight trend and intake
- 7-day / 30-day / 1-year time ranges with a moving-average trend line on the weight chart

### General
- **Guided tour** on first launch, and a **Help Center** with getting-started steps, an FAQ and a glossary
- **Light, dark or system** appearance
- Available in **German, English, French and Spanish**
- **Daily meal reminder** notification if nothing's been logged by 17:00 (toggle in Settings)
- **Full backup export/import** — a ZIP with all nutrition and workout data (food entries, weight history, body measurements, goals, custom foods, custom exercises, templates, sessions); backward-compatible with older CSV exports, and safely reassembles cross-device via stable keys (no dependency on local row IDs)
- All data stored locally — no account, no cloud

## Download

Die aktuellste APK ist immer direkt als [GitHub Release](https://github.com/anton-314/Trainist/releases/tag/latest) verfügbar — kein Build nötig.

## Requirements

- Android 7.0 (API 24) or higher

## Building

```bash
# Clone the repo
git clone https://github.com/anton-314/Trainist.git
cd Trainist

# Build a debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Java 11+ is required. If `JAVA_HOME` is not set, point it at your JDK:
```bash
JAVA_HOME=/path/to/jdk ./gradlew assembleDebug
```

## Tech stack

- Kotlin + Jetpack Compose (Material3)
- Hilt (dependency injection)
- Room (local database)
- Retrofit + Moshi (Open Food Facts API)
- CameraX + ML Kit (barcode scanning)
- WorkManager (daily meal reminder) + a foreground service that owns the rest-timer countdown and fires its alert on the second

## License

Source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE) — free to use, modify, and share for any noncommercial purpose; commercial use (including resale) is not permitted.
