# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development workflow

Every feature or change follows this workflow — keep it in mind from the first line of code:

1. **Plan deliberately.** Design so the software stays *extensible* (new cases slot in without rewrites) and *well testable* (logic separable from Android/UI, behaviour reachable by a test). Prefer a reusable building block over a one-off when the same need will recur.
2. **Implement** the plan.
3. **Remove dead code.** Delete now-unused sections, wrappers, imports, and helpers so the codebase stays a clear picture of what's actually in use — no leftovers from the previous shape of the code.
4. **Validate** the change against the requirement (and edge cases) by reading it back.
5. **Write tests** covering the new behaviour (ViewModel logic → JVM unit tests under `app/src/test/`; UI behaviour → Compose tests under `app/src/androidTest/`).
6. **Compile and test from the command line** using the commands below — `assembleDebug` + `testDebugUnitTest` (and `connectedDebugAndroidTest` for instrumented tests when a device/emulator is attached). Don't consider a step done until it builds and tests pass.
7. **Update the docs** — keep this `CLAUDE.md` in sync with new architecture, design decisions, and conventions.
8. **Commit** with a clear, conventional message.

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
│   ├── components/          # Reusable UI building blocks (e.g. NumericTextField)
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

Compose UI behaviour is tested under `app/src/androidTest/` with `createComposeRule` (e.g. `ui/components/NumericTextFieldTest`). These are instrumented tests — run them with `connectedDebugAndroidTest` against an emulator/device, not `testDebugUnitTest`.

### Backup & data portability

- **Auto Backup**: `backup_rules.xml` (pre-12) and `data_extraction_rules.xml` (Android 12+) explicitly include `macrotrac.db` + WAL files. Android backs these up to Google Drive automatically when the user has backup enabled — no code required.
- **Full Backup Export** (`data/backup/BackupExporter`): reads all four data types (food entries, weight entries, daily goal, custom foods), writes a ZIP to `cacheDir` containing four named CSVs (`food_entries.csv`, `weight_entries.csv`, `daily_goal.csv`, `custom_foods.csv`), shares via `FileProvider` + `Intent.ACTION_SEND`.
- **Full Backup Import** (`data/backup/BackupImporter`): opens a user-picked URI, auto-detects ZIP vs single CSV by checking the PK magic bytes (`0x50 0x4B`). ZIP imports all four sections by entry name. A single CSV file is routed by type detection (`detectCsvType`) based on unique column presence — see table below. `Result` reports counts for each data type.
- **CSV type detection** (`detectCsvType` in `BackupImporter.kt`): identifies a CSV by its unique column:
  - `food_name` → `FOOD_ENTRIES` (parsed by `CsvFormat`)
  - `weight_kg` → `WEIGHT_ENTRIES` (parsed by `WeightCsvFormat`)
  - `kcal_per_100g` → `CUSTOM_FOODS` (parsed by `CustomFoodCsvFormat`)
  - `kcal` (without the above) → `DAILY_GOAL` (parsed by `GoalCsvFormat`)
- **CsvFormat** (`data/backup/CsvFormat.kt`): pure Kotlin — food entries. Columns identified by name; missing columns fall back to defaults; extra columns are ignored. `CsvFormat.parseHeaders` is the shared header-parsing function used by all format objects.
- **CustomFoodCsvFormat** (`data/backup/CustomFoodCsvFormat.kt`): pure Kotlin — custom foods. Columns: `name`, `brand`, `kcal_per_100g`, `protein_per_100g`, `carbs_per_100g`, `fat_per_100g`, `sugar_per_100g`, `fiber_per_100g`, `salt_per_100g`.
- **WeightCsvFormat** (`data/backup/WeightCsvFormat.kt`): pure Kotlin — weight entries. Columns: `date`, `weight_kg`, `timestamp_ms`.
- **GoalCsvFormat** (`data/backup/GoalCsvFormat.kt`): pure Kotlin — single-row daily goal. Columns: `kcal`, `protein_g`, `carbs_g`, `fat_g`.
- **FileProvider** authority: `dev.antonlammers.macrotrac.fileprovider`, paths configured in `res/xml/file_paths.xml` (cache dir).

### Backup schema evolution — backward compatibility contract

All CSV parsing is name-based (column order is irrelevant on import). This gives us forward and backward compatibility for free, as long as the following rules are followed:

| Change | Export (`toRow` / `HEADER`) | Import (`fromRow`) |
|---|---|---|
| **Add a column** | Add constant to `CsvColumns` / format object, add to `HEADER` and `toRow` | Read with a sensible default for missing values — old exports that lack the column still parse |
| **Remove a column** | Remove from `HEADER` and `toRow` | Remove the read; `fromRow` ignores unknown columns, so new exports with the column dropped still parse in old app versions |
| **Rename a column** | Write both old and new names in `toRow` for at least one release, then drop the old name | Read the new name with fallback to the old name until the transition period ends |
| **Add a new data type** | Add a new `*CsvFormat` object + ZIP entry name in `BackupExporter` | Add a new `CsvType` variant + detection rule in `detectCsvType`, add a `when` branch in `importZip` and `importSingleCsv` |
| **Change the ZIP container format** | — | Extend `isZip` / add a new container detection path; keep old paths for legacy files |

**No explicit version field is needed.** Structural detection (column presence, ZIP magic bytes, entry names) is sufficient and avoids the overhead of managing version numbers. Only introduce a version field if two formats become structurally ambiguous (same unique columns, different semantics).

### Key design decisions

- **Domain layer is Android-free.** ViewModels depend only on `domain/repository` interfaces. Swapping the data source touches only `data/` and `di/`.
- **Theme isolation.** Replacing `ui/theme/Color.kt` and `Theme.kt` is sufficient to restyle the entire app. Pastel macro colors (`CalorieColor`, `ProteinColor`, `CarbsColor`, `FatColor`) are defined in `Color.kt`.
- **Numeric input fields — `ui/components/NumericTextField`.** UI design language: *every* field that holds a number uses `NumericTextField`, never a bare `OutlinedTextField`. When such a field gains focus it **selects its whole content**, so a pre-filled value (e.g. the last used amount, the current goal) can be overwritten by simply typing — without deleting the old value first. The composable keeps a `String`-based `value`/`onValueChange` API (callers own the raw text and parse it themselves, accepting comma or period as decimal separators via `normalizeDecimal`); the internal `TextFieldValue` that carries the selection never leaks out. `decimal = true` (default) gives a decimal keyboard, `decimal = false` a whole-number keyboard. Behaviour is covered by `NumericTextFieldTest` (Compose UI test in `app/src/androidTest/`). When adding a new numeric field, reach for `NumericTextField` — do not reintroduce raw text fields for numbers.
- **Local dates.** `FoodEntry.date` and `WeightEntry.date` are `LocalDate` in the domain; stored as ISO-8601 `String` in Room.
- **DailyGoal** is a singleton row (`id = 1`, `OnConflictStrategy.REPLACE`) — no per-day goal history.
- **WeightEntry** is one per day, enforced via a `UNIQUE` index on the `date` column + `OnConflictStrategy.REPLACE` on insert.
- **Day navigation** in `OverviewViewModel` is driven by a `MutableStateFlow<LocalDate>`. The `uiState` uses `flatMapLatest` to re-subscribe to food entries and today's weight whenever the date changes.
- **AddFoodScreen lists**: when the search field is empty, two sections are shown — "Meine Lebensmittel" (custom foods) and "Verlauf" (last 500 entries grouped by date). When a query is typed, a flat filtered list replaces both sections: matching custom foods first, then deduplicated history entries (one per food name, most recent). Tapping any item opens the amount dialog (add to log). Custom foods support swipe gestures: StartToEnd (left-to-right) opens the edit dialog; EndToStart (right-to-left) triggers deferred delete with undo snackbar. History entries support EndToStart swipe to delete (also with undo).
- **Swipe gestures on food entries** (OverviewScreen): EndToStart (right-to-left) shows a red delete background and deletes the entry on full swipe. StartToEnd (left-to-right) shows a green edit background and opens the edit dialog (swipe snaps back via `confirmValueChange` returning `false`). `FoodEntryRow` has an explicit `surface` background so the swipe background only shows when actively swiping. There is no separate edit button — swiping is the only way to edit or delete.
- **Stats screen** (`ui/stats/`) uses Canvas-based charts (no chart library dependency). Calorie data is aggregated daily for 7-day/30-day views and monthly for the 1-year view. The time range drives a `flatMapLatest` to re-subscribe to both food and weight repositories.
- **Open Food Facts** base URL: `https://world.openfoodfacts.org/`. Used only for barcode lookup:
  - `api/v2/product/{barcode}` — barcode lookup (`status == 1` means found)
  - Text search endpoint is no longer used; search is local-only.
- **Barcode scanner** (`BarcodeScannerScreen`) uses CameraX + ML Kit. It passes the detected barcode back via `NavBackStackEntry.savedStateHandle["barcode"]` and `AddFoodViewModel.handleBarcode()` resolves it against the API. `BarcodeAnalyzer` uses an `AtomicBoolean` to fire the callback exactly once per scan session.
- **`@ExperimentalGetImage`** propagates from `BarcodeAnalyzer` → `BarcodeScannerScreen` → `AppNavigation` → `MainActivity`. This is expected and not a warning to suppress.
- **DB schema**: version 5. Migrations: 1→2 adds sugar/fiber/mealCategory columns; 2→3 adds the `weight_entries` table; 3→4 adds the `custom_foods` table; 4→5 adds `saltG` to `food_entries` and `saltPer100g` to `custom_foods`.
