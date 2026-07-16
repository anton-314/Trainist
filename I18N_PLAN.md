# Mehrsprachigkeit (i18n) — Umsetzungsplan

Ziel: Trainist unterstützt zusätzlich zu Deutsch mindestens Englisch, mit einer
Sprachwahl in den Einstellungen (inkl. "Systemsprache folgen"). Architektur bleibt
offen für weitere Sprachen (neuer `values-xx/`-Ordner genügt).

Ausgangslage (siehe Analyse, nicht wiederholt): `strings.xml` ist praktisch leer,
~400 deutsche String-Literale liegen verteilt über ~30 Compose-Dateien, Enum-
`displayName()`-Funktionen geben rohe Strings zurück, Datumsformatierung ist auf
`Locale.GERMAN` fixiert, Notification-Texte sind hartcodiert. Der Übungskatalog
(`exercise_catalog.json`) ist bereits Englisch — hier fällt keine Übersetzung an.
`minSdk = 24`, kein `androidx.appcompat` in den Dependencies, kein `localeConfig`.

## Architektur-Entscheidung

- **Sprachumschaltung:** Android **Per-App Language** Mechanismus statt eigenem
  `attachBaseContext`/Locale-Context-Hack.
  - `androidx.appcompat:appcompat` als neue Dependency (Version über
    `gradle/libs.versions.toml`) — liefert `AppCompatDelegate.setApplicationLocales()`,
    das ab API 33 die native Systemfunktion nutzt und darunter (bis minSdk 24) selbst
    zurückfällt (persistiert intern, kein eigener DataStore/SharedPreferences-Code nötig).
  - `res/xml/locales_config.xml` mit `<locale android:name="de"/>` + `<locale android:name="en"/>`,
    referenziert über `android:localeConfig="@xml/locales_config"` im `<application>`-Tag
    des Manifests — macht die Sprachen zusätzlich in den System-Einstellungen (Android 13+)
    wählbar, nicht nur im App-internen Picker.
  - **Kein** Custom-`Context`-Wrapping, kein `Activity.recreate()`-Handling von Hand —
    `AppCompatDelegate` löst Activity-Recreate automatisch aus.
  - Vorbedingung: `MainActivity` muss von `AppCompatActivity` erben oder zumindest
    `AppCompatDelegate` funktioniert unabhängig davon mit `ComponentActivity`
    (zu verifizieren in Phase 0 — falls nicht, ggf. `androidx.activity` + reines
    `LocaleManagerCompat`-API prüfen als Fallback, um `ComponentActivity` nicht anfassen
    zu müssen).
- **String-Ressourcen:** Standard Android `values/strings.xml` (Default = Deutsch, da
  aktuelle Baseline) + `values-en/strings.xml` (Englisch). Kein drittes System
  (kein Compose-only i18n-Framework) — bleibt idiomatisch und funktioniert mit
  Android Studios eingebautem Translation-Editor.
- **Enum-Anzeigenamen:** `displayName()`-Funktionen, die aktuell `String` zurückgeben,
  werden zu `@Composable fun displayName(): String = stringResource(R.string....)`
  umgebaut (Aufrufer ist immer aus Compose-Kontext heraus). Enums selbst (in `domain/model`)
  bleiben unverändert — nur die UI-seitigen Format-Extensions (`FoodTagUi.kt`,
  `SetTypeUi.kt`, `ExerciseFormat.kt`, etc.) wandern von `String`-Rückgabe auf
  `@Composable`/Resource-Lookup.
- **Relative Datumstexte** ("Heute"/"Gestern"/"Vor N Tagen", Monats-/Wochentagsnamen):
  - Monats-/Wochentagsnamen: `DateTimeFormatter.ofPattern(pattern, Locale.getDefault())`
    statt hartem `Locale.GERMAN` — für Android per-app-locale reicht
    `Locale.getDefault()` nicht automatisch aus (JVM-Default ≠ App-Locale vor API 33
    ohne Weiteres); stattdessen `AppCompatDelegate.getApplicationLocales().get(0)`
    (mit Fallback auf `Locale.getDefault()`) zentral in einer neuen
    `ui/util/LocaleUtil.kt`-Helper-Funktion kapseln, die überall dort verwendet wird,
    wo aktuell `Locale.GERMAN`/`Locale.getDefault()` für Datumsformate vorkommt.
  - "Heute"/"Gestern"/"Vor N Tagen": als `plurals`/`string`-Ressourcen mit
    Platzhaltern (`R.string.days_ago` mit `%d`), aufgelöst über `stringResource(id, n)`.
- **Notifications:** Texte/Kanalnamen aus `strings.xml` via `context.getString(R.string...)`
  (kein `stringResource`, da kein Compose-Kontext) — Kanäle werden nur einmalig beim ersten
  Erstellen benannt; da Android Kanal-Metadaten cached, muss beim Sprachwechsel geprüft
  werden, ob bestehende Kanäle aktualisiert werden müssen (siehe Phase 6).

## Naming-Konvention für String-Keys

`<screen_or_module>_<element>_<variant>`, z. B.:
- `overview_calorie_ring_remaining` ("noch %1$d kcal")
- `workout_session_finish_button` ("Fertig")
- `settings_donation_cta` ("Kaffee spendieren")
- `common_delete` / `common_cancel` / `common_save` für app-weit wiederverwendete Labels
  (Sammlung in einem `common_*`-Block am Anfang von `strings.xml`, um Duplikate zu vermeiden —
  bei ~400 Strings ist Redundanz sonst ein reales Risiko).

## Phasenplan

### Phase 0 — Fundament (kein sichtbares Feature-Ergebnis)
1. `androidx.appcompat` zu `gradle/libs.versions.toml` + `app/build.gradle.kts` hinzufügen.
2. `res/xml/locales_config.xml` anlegen, im Manifest referenzieren.
3. `ui/util/LocaleUtil.kt`: zentrale Helper (`currentAppLocale()`, `localizedDateFormatter(pattern)`).
4. `SettingsRepository` um `getAppLanguage()`/`setAppLanguage(tag: String?)` erweitern
   (`null`/`"system"` = Systemsprache folgen) — Persistierung kann direkt an
   `AppCompatDelegate` delegieren (`AppCompatDelegate.getApplicationLocales()` ist bereits
   persistent), Repository-Methode ist dann ein dünner Wrapper für Testbarkeit
   (Fake-Repository für ViewModel-Tests, analog zu `FakeSettingsRepository`).
5. Leeres `values-en/strings.xml` anlegen (nur `app_name`, um die Struktur zu verifizieren).
6. Smoke-Test: Sprache manuell in den System-Einstellungen umschalten, prüfen dass
   `app_name` reagiert → bestätigt, dass der Mechanismus vor der großen Extraktion greift.

**Ergebnis:** Infrastruktur steht, noch keine Screens migriert.

### Phase 1 — Gemeinsame Bausteine zuerst
Reihenfolge bewusst so gewählt, dass wiederverwendete Komponenten zuerst sauber sind,
damit Folge-Phasen sie nur noch aufrufen statt selbst Strings zu duplizieren:
1. `ui/components/` (NumericTextField-Labels, FoodTagUi, DragReorderColumn falls Text
   enthalten, Bottom-Sheet-Bausteine).
2. `ui/workout/SetTypeUi.kt`, `ExerciseFormat.kt`, `WorkoutFormat.kt`, `PrBadge.kt`
   (Enum-Formatter-Umbau wie oben beschrieben).
3. `ui/goals/GoalFields.kt` (von Settings **und** Onboarding genutzt).
4. `notification/*.kt` (MealReminderNotifier, RestTimerNotifier) — unabhängig von Compose,
   kann parallel zu 1–3 laufen.

### Phase 2 — Screens nach Nutzungshäufigkeit/Größe
Absteigend nach String-Anzahl (größte Screens zuerst, um früh Muster/Probleme zu erkennen,
die sich auf kleinere Screens übertragen lassen):
1. `AddFoodScreen` (+ `BarcodeScannerScreen`)
2. `OverviewScreen`
3. `WorkoutSessionScreen`
4. `TemplatesScreen`
5. `SettingsScreen` (inkl. neuem Sprachwahl-Eintrag, siehe Phase 3)
6. `ExerciseCatalogScreen`
7. `TemplateEditorScreen`
8. `StatsScreen`
9. `OnboardingScreen`
10. `WorkoutHistoryScreen` (inkl. `Locale.GERMAN`-Fix in `MONTH_FORMATTER`/`DAY_FORMATTER`)
11. Restliche kleinere Dateien (`AppNavigation.kt`, `ExerciseDetailScreen`, `Type.kt` falls
    dort Text vorkommt statt nur Font-Definitionen)

Jeder Screen wird einzeln migriert und gebaut/getestet (kein Big-Bang-Commit), damit
Regressionen sofort einer Datei zuzuordnen sind.

### Phase 3 — Sprachwahl-UI
- Neue Sektion in `SettingsScreen` (analog zum bestehenden Reminder-Toggle-Muster):
  "Sprache" mit Optionen **Systemsprache / Deutsch / English** (Radio-Buttons oder
  `ModalBottomSheet`-Picker, konsistent mit dem bestehenden Bottom-Sheet-Pattern der App).
- `LanguageViewModel` (oder Erweiterung von `DataViewModel`, falls thematisch näher an
  "Einstellungen" als eigenständiges Modul nötig ist — Entscheidung in Phase 0 anhand
  der bestehenden VM-Aufteilung treffen) ruft `AppCompatDelegate.setApplicationLocales(...)`
  auf; Activity-Recreate übernimmt AppCompat automatisch.

### Phase 4 — Übersetzung
- Nach Abschluss der Extraktion (Phase 1+2): vollständige `values-en/strings.xml` befüllen.
- Deutscher Text bleibt in `values/strings.xml` (Default) — keine Strings "verloren",
  da Extraktion 1:1 aus bestehendem deutschen UI-Text erfolgt.
- Terminologie-Konsistenz vorab festlegen (z. B. "Satz" → "set", "Übung" → "exercise",
  "Einheit" → "session/workout") in einem kurzen Glossar-Abschnitt am Ende dieser Datei,
  damit nicht jede Datei eigene Wortwahl trifft.

### Phase 5 — Relative Zeit- und Datumslogik
- `lastUsedText()` (TemplatesScreen), `formatRelative()` (AddFoodScreen),
  `DateGroupHeader`/`MONTH_FORMATTER`/`DAY_FORMATTER` (WorkoutHistoryScreen) umbauen:
  Pattern-Strings + `LocaleUtil.localizedDateFormatter()` statt hartem `Locale.GERMAN`;
  "Heute"/"Gestern"/"Vor N Tagen" als Ressourcen mit Pluralformen
  (Englisch braucht "day"/"days" - "Vor 1 Tag" vs. "Vor 2 Tagen" hat im Deutschen ohnehin
  schon eine ähnliche Unterscheidung, `<plurals>` deckt beides ab).

### Phase 6 — Notifications & Rand-Fälle
- Kanal-Namen/-Beschreibungen: prüfen, ob ein Sprachwechsel zur Laufzeit bereits
  erstellte `NotificationChannel`s aktualisieren muss (`NotificationManager.createNotificationChannel`
  mit gleicher ID aktualisiert Name/Description bereits automatisch bei erneutem Aufruf —
  verifizieren, dass die Notifier-Klassen das ohnehin bei jedem Start erneut aufrufen,
  sonst gezielt nachziehen).
- Dezimaltrennzeichen (`NumericTextField`/`normalizeDecimal`): bereits Komma/Punkt-tolerant,
  aber Anzeige-Formatierung (z. B. `String.format("%.1f", ...)`) auf potenzielle
  Locale-Abhängigkeit prüfen (`String.format` nutzt ohne explizite Locale die
  JVM-Default-Locale, was von der App-Locale abweichen kann) — überall wo Zahlen
  angezeigt werden, explizit `Locale.getDefault()`/App-Locale übergeben statt implizit.

### Phase 7 — Tests
- Neue/angepasste Unit-Tests dort, wo reine Formatter-Logik existiert
  (z. B. falls `lastUsedText`/`formatRelative` in eine testbare Pure-Funktion mit
  injizierter Locale extrahiert werden — bevorzugt gegenüber Testen direkt in Compose).
- Bestehende `androidTest`-Compose-Tests, die auf deutschen Anzeigetext prüfen
  (`onNodeWithText("...")` mit deutschen Strings): durchsuchen und ggf. auf
  `onNodeWithText(activity.getString(R.string....))` umstellen, damit sie
  locale-unabhängig bleiben.
- Manuelle Prüfung: App auf Englisch stellen, kompletten Kernablauf (Essen loggen,
  Workout starten/beenden, Ziel setzen, Backup exportieren) durchklicken.

## Aufwandsschätzung pro Phase (grobe Richtwerte, kein Commitment)

| Phase | Inhalt | Umfang |
|---|---|---|
| 0 | Infrastruktur | klein, aber blockierend für alles Weitere |
| 1 | Gemeinsame Bausteine | mittel |
| 2 | 11 Screens einzeln migrieren | größter Block, gut parallelisierbar/inkrementell |
| 3 | Sprachwahl-UI | klein |
| 4 | Übersetzung EN | mittel (mechanisch, aber Sorgfalt nötig) |
| 5 | Datums-/Zeitlogik | klein-mittel |
| 6 | Notifications & Rand-Fälle | klein |
| 7 | Tests | mittel |

Empfehlung: Phasen 0–1 in einem PR, danach **pro migriertem Screen ein eigener PR**
(Phase 2), damit Reviews klein bleiben und ein Screen jederzeit isoliert getestet werden
kann, bevor der nächste beginnt.

## Glossar (Deutsch → Englisch, verbindlich für Phase 4)

| Deutsch | Englisch |
|---|---|
| Satz | Set |
| Übung | Exercise |
| Einheit / Workout | Workout / Session |
| Vorlage | Template |
| Aufwärmen | Warm-up |
| Wiederholungen | Reps |
| Ziel | Goal |
| Mahlzeit | Meal |
| Frühstück / Mittagessen / Abendessen / Snack | Breakfast / Lunch / Dinner / Snack |
| Ruhe-Timer / Satzpause | Rest timer |
| Aufzeichnung / Verlauf | Log / History |

## Offene Entscheidungen (vor Start zu klären)

1. Soll die Sprachwahl **nur** In-App (Settings-Picker) oder zusätzlich über die
   System-Einstellungen (Android 13+ "App-Sprachen") erreichbar sein? → beeinflusst,
   ob `locales_config.xml` zwingend ist (Empfehlung: ja, minimal zusätzlicher Aufwand).
2. Startet Deutsch als expliziter `values-de/`-Ordner (symmetrisch zu `values-en/`) oder
   bleibt Deutsch der Ressourcen-Default in `values/` (aktueller Plan) — Default-Variante
   spart einen Migrationsschritt, ist aber weniger symmetrisch, falls später eine dritte
   Sprache dazukommt, die nicht Englisch ist.
