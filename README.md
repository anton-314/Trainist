# MacroTrac

A minimalistic, open-source calorie and nutrient tracking app for Android.

## Features

- Log meals by searching the [Open Food Facts](https://world.openfoodfacts.org/) database or scanning a barcode
- **Recently eaten** shortcuts — the last 15 distinct foods appear instantly when you open the food search
- Daily overview with animated calorie ring and pastel-colored progress bars for protein, carbs, and fat
- **Navigate between days** — track past meals or plan ahead; jump back to today with one tap
- **Weight tracking** — log today's weight directly from the overview with a single tap
- **Stats screen** — calorie bar chart + weight line chart for the last 7 days, 30 days, or 1 year
- Set your own daily macro targets
- **Edit food entries** — swipe right-to-left to delete, left-to-right to edit amount and meal category
- **Full backup export/import** — ZIP file containing all food entries, weight history, and goals; backward-compatible with old food-only CSV exports
- All data stored locally — no account, no cloud

## Download

Die aktuellste APK ist immer direkt als [GitHub Release](https://github.com/anton-314/MacroTrac/releases/tag/latest) verfügbar — kein Build nötig.

## Requirements

- Android 7.0 (API 24) or higher

## Building

```bash
# Clone the repo
git clone https://github.com/anton-314/MacroTrac.git
cd MacroTrac

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

## License

[MIT](LICENSE)
