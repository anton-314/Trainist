# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build debug APK
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug

# Install on connected emulator and launch
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug
/home/anon/Android/Sdk/platform-tools/adb shell am start -n dev.antonlammers.macrotrac/.MainActivity

# Run all unit tests (debug only — release build has a pre-existing Hilt issue from the rename)
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest

# Run a specific test class
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests "dev.antonlammers.macrotrac.ui.overview.OverviewViewModelTest"

# Lint
JAVA_HOME=/opt/android-studio/jbr ./gradlew lint
```

## Architecture

MVVM, single `app` module, Jetpack Compose + Material3. All dependency versions live in `gradle/libs.versions.toml`.

```
app/src/main/java/dev/antonlammers/macrotrac/
├── domain/                  # Pure Kotlin — no Android deps
│   ├── model/               # Food, FoodEntry, DailyGoal, WeightEntry
│   └── repository/          # FoodSearchRepository, FoodEntryRepository, GoalRepository,
│                            #   WeightRepository (interfaces)
├── data/
│   ├── local/               # Room: AppDatabase (v3), DAOs, entities (date stored as ISO String)
│   ├── remote/              # Retrofit: OpenFoodFactsApi + DTOs (Moshi @JsonClass)
│   └── repository/          # Implementations of domain interfaces, entity↔model mapping
├── di/                      # Hilt modules: DatabaseModule, NetworkModule, RepositoryModule
├── ui/
│   ├── theme/               # Color.kt (incl. pastel macro colors), Theme.kt
│   ├── navigation/          # AppNavigation with sealed Screen routes
│   ├── overview/            # Daily summary (OverviewScreen + OverviewViewModel)
│   ├── addfood/             # Food search + barcode scanner (AddFoodScreen, AddFoodViewModel,
│   │                        #   BarcodeScannerScreen, BarcodeAnalyzer)
│   ├── goals/               # Goal editor (GoalsScreen + GoalsViewModel)
│   └── stats/               # Stats screen: calorie + weight charts (StatsScreen + StatsViewModel)
├── MacroTracApp.kt          # @HiltAndroidApp
└── MainActivity.kt          # @AndroidEntryPoint, hosts AppNavigation inside MacroTracTheme
```

### Tests

Unit tests live in `app/src/test/`. Fakes (no mocking library) are in `.../fake/`:
- `FakeFoodEntryRepository`, `FakeGoalRepository`, `FakeFoodSearchRepository`, `FakeWeightRepository`

Tests use `kotlinx-coroutines-test` + `turbine`. All ViewModels have full test coverage.

### Backup & data portability

- **Auto Backup**: `backup_rules.xml` (pre-12) and `data_extraction_rules.xml` (Android 12+) explicitly include `macrotrac.db` + WAL files. Android backs these up to Google Drive automatically when the user has backup enabled — no code required.
- **Full Backup Export** (`data/backup/BackupExporter`): reads all three data types (food entries, weight entries, daily goal), writes a ZIP to `cacheDir` containing three named CSVs (`food_entries.csv`, `weight_entries.csv`, `daily_goal.csv`), shares via `FileProvider` + `Intent.ACTION_SEND`.
- **Full Backup Import** (`data/backup/BackupImporter`): opens a user-picked URI, auto-detects ZIP vs legacy CSV by checking the PK magic bytes. ZIP imports all three sections; legacy CSV imports only food entries (backward-compatible).
- **CsvFormat** (`data/backup/CsvFormat.kt`): pure Kotlin — food entries. Columns identified by name; missing columns fall back to defaults; extra columns are ignored.
- **WeightCsvFormat** (`data/backup/WeightCsvFormat.kt`): pure Kotlin — weight entries. Columns: `date`, `weight_kg`, `timestamp_ms`.
- **GoalCsvFormat** (`data/backup/GoalCsvFormat.kt`): pure Kotlin — single-row daily goal. Columns: `kcal`, `protein_g`, `carbs_g`, `fat_g`.
- **FileProvider** authority: `dev.antonlammers.macrotrac.fileprovider`, paths configured in `res/xml/file_paths.xml` (cache dir).

### Key design decisions

- **Domain layer is Android-free.** ViewModels depend only on `domain/repository` interfaces. Swapping the data source touches only `data/` and `di/`.
- **Theme isolation.** Replacing `ui/theme/Color.kt` and `Theme.kt` is sufficient to restyle the entire app. Pastel macro colors (`CalorieColor`, `ProteinColor`, `CarbsColor`, `FatColor`) are defined in `Color.kt`.
- **Local dates.** `FoodEntry.date` and `WeightEntry.date` are `LocalDate` in the domain; stored as ISO-8601 `String` in Room.
- **DailyGoal** is a singleton row (`id = 1`, `OnConflictStrategy.REPLACE`) — no per-day goal history.
- **WeightEntry** is one per day, enforced via a `UNIQUE` index on the `date` column + `OnConflictStrategy.REPLACE` on insert.
- **Day navigation** in `OverviewViewModel` is driven by a `MutableStateFlow<LocalDate>`. The `uiState` uses `flatMapLatest` to re-subscribe to food entries and today's weight whenever the date changes.
- **Recently eaten** in `AddFoodScreen`: shown when the search field is empty. `FoodEntryRepository.recentFoods()` fetches the last 100 entries, deduplicates by `foodName`, and returns the 15 most recent distinct foods. Tapping one back-calculates per-100g values and opens the amount dialog with the previous portion pre-filled.
- **Swipe gestures on food entries** (OverviewScreen): EndToStart (right-to-left) shows a red delete background and deletes the entry on full swipe. StartToEnd (left-to-right) shows a green edit background and opens the edit dialog (swipe snaps back via `confirmValueChange` returning `false`). `FoodEntryRow` has an explicit `surface` background so the swipe background only shows when actively swiping. Edit can also be triggered by the pencil IconButton in the row.
- **Stats screen** (`ui/stats/`) uses Canvas-based charts (no chart library dependency). Calorie data is aggregated daily for 7-day/30-day views and monthly for the 1-year view. The time range drives a `flatMapLatest` to re-subscribe to both food and weight repositories.
- **Open Food Facts** base URL: `https://world.openfoodfacts.org/`. Two endpoints used:
  - `api/v2/search?search_terms=…` — text search
  - `api/v2/product/{barcode}` — barcode lookup (`status == 1` means found)
- **Barcode scanner** (`BarcodeScannerScreen`) uses CameraX + ML Kit. It passes the detected barcode back via `NavBackStackEntry.savedStateHandle["barcode"]` and `AddFoodViewModel.handleBarcode()` resolves it against the API. `BarcodeAnalyzer` uses an `AtomicBoolean` to fire the callback exactly once per scan session.
- **`@ExperimentalGetImage`** propagates from `BarcodeAnalyzer` → `BarcodeScannerScreen` → `AppNavigation` → `MainActivity`. This is expected and not a warning to suppress.
- **DB schema**: version 3. Migrations: 1→2 adds sugar/fiber/mealCategory columns; 2→3 adds the `weight_entries` table.
