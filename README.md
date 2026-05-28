# MacroTrac

A minimalistic, open-source calorie and nutrient tracking app for Android.

## Features

- Log meals by searching the [Open Food Facts](https://world.openfoodfacts.org/) database or scanning a barcode
- Daily overview of calories, protein, carbs, and fat vs. your goals
- Set your own daily macro targets
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
