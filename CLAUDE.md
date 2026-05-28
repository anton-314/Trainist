# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build debug APK
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug

# Install on connected emulator and launch
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug
/home/anon/Android/Sdk/platform-tools/adb shell am start -n dev.antonlammers.macromind/.MainActivity

# Run all unit tests
JAVA_HOME=/opt/android-studio/jbr ./gradlew test

# Run a specific test class
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "dev.antonlammers.macromind.ui.overview.OverviewViewModelTest"

# Lint
JAVA_HOME=/opt/android-studio/jbr ./gradlew lint
```

## Architecture

MVVM, single `app` module, Jetpack Compose + Material3. All dependency versions live in `gradle/libs.versions.toml`.

```
app/src/main/java/dev/antonlammers/macromind/
├── domain/                  # Pure Kotlin — no Android deps
│   ├── model/               # Food, FoodEntry, DailyGoal
│   └── repository/          # FoodSearchRepository, FoodEntryRepository, GoalRepository (interfaces)
├── data/
│   ├── local/               # Room: AppDatabase, DAOs, entities (date stored as ISO String)
│   ├── remote/              # Retrofit: OpenFoodFactsApi + DTOs (Moshi @JsonClass)
│   └── repository/          # Implementations of domain interfaces, entity↔model mapping
├── di/                      # Hilt modules: DatabaseModule, NetworkModule, RepositoryModule
├── ui/
│   ├── theme/               # Color.kt, Theme.kt — swap to change the entire look
│   ├── navigation/          # AppNavigation with sealed Screen routes
│   ├── overview/            # Daily summary (OverviewScreen + OverviewViewModel)
│   ├── addfood/             # Food search + barcode scanner (AddFoodScreen, AddFoodViewModel,
│   │                        #   BarcodeScannerScreen, BarcodeAnalyzer)
│   └── goals/               # Goal editor (GoalsScreen + GoalsViewModel)
├── MacroMindApp.kt          # @HiltAndroidApp
└── MainActivity.kt          # @AndroidEntryPoint, hosts AppNavigation inside MacroMindTheme
```

### Tests

Unit tests live in `app/src/test/`. Fakes (no mocking library) are in `.../fake/`:
- `FakeFoodEntryRepository`, `FakeGoalRepository`, `FakeFoodSearchRepository`

Tests use `kotlinx-coroutines-test` + `turbine`. All ViewModels have full test coverage.

### Key design decisions

- **Domain layer is Android-free.** ViewModels depend only on `domain/repository` interfaces. Swapping the data source touches only `data/` and `di/`.
- **Theme isolation.** Replacing `ui/theme/Color.kt` and `Theme.kt` is sufficient to restyle the entire app.
- **Local dates.** `FoodEntry.date` is `LocalDate` in the domain; stored as ISO-8601 `String` in Room.
- **DailyGoal** is a singleton row (`id = 1`, `OnConflictStrategy.REPLACE`) — no per-day goal history.
- **Open Food Facts** base URL: `https://world.openfoodfacts.org/`. Two endpoints used:
  - `api/v2/search?search_terms=…` — text search
  - `api/v2/product/{barcode}` — barcode lookup (`status == 1` means found)
- **Barcode scanner** (`BarcodeScannerScreen`) uses CameraX + ML Kit. It passes the detected barcode back via `NavBackStackEntry.savedStateHandle["barcode"]` and `AddFoodViewModel.handleBarcode()` resolves it against the API. `BarcodeAnalyzer` uses an `AtomicBoolean` to fire the callback exactly once per scan session.
- **`@ExperimentalGetImage`** propagates from `BarcodeAnalyzer` → `BarcodeScannerScreen` → `AppNavigation` → `MainActivity`. This is expected and not a warning to suppress.
