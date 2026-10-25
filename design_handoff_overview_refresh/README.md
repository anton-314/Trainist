# Handoff: Ernährungs-Übersicht Refresh + schwebende Navigation

## Overview
Gezielte Überarbeitung von Trainist. **Kein Komplett-Redesign:** Das „Ink & Paper“-Farb- und Typo-System, die Mahlzeiten-Liste, Swipe-Gesten, Dialoge und alle anderen Screens bleiben, wie sie sind. Übernommen werden nur diese Punkte:

1. **Schwebende Navigationsleiste** (Pill) mit 3 Tabs: Ernährung, Training, Statistik. Rechts daneben ein runder Plus-Button.
2. **Einstellungen nach oben:** kein Tab mehr, sondern ein runder Icon-Button im Kopf der Übersicht.
3. **Gewicht nach oben:** ein Chip im Kopf ersetzt die bisherige `WeightCard`.
4. **Wochentag groß + Datum** als Kopfzeile, ersetzt die TopAppBar mit Pfeilen.
5. **Tageswechsel über einen Wochenstreifen** (Mo–So) statt über die Pfeile.
6. **Zusammenfassung in zwei Kacheln:**
   - Kachel 1: Gesamtkalorien als **segmentierter Balken**. Kein Ring mehr, kein Satz „Noch x kcal …“.
   - Kachel 2: P / K / F als **kleine Ringe**. Darunter Zucker, Ballaststoffe und Salz als leise Zeile.

Referenz-Design: `Trainist Redesign.dc.html` → Abschnitt **Runde 4, Variante 4a** (dunkel). Die helle Variante zeigt **3b** (Runde 3, Aufbau identisch bis auf die Kachel-Teilung, Farben aus Paper).

## About the Design Files
Die Dateien in diesem Bundle sind **Design-Referenzen in HTML**: Prototypen, die Aussehen und Verhalten zeigen. Sie sind kein Produktionscode. Aufgabe ist, sie in der bestehenden App nachzubauen: **Kotlin + Jetpack Compose (Material3)**, mit den vorhandenen Patterns (`MaterialTheme.colorScheme`, `MaterialTheme.typography`, `tutorialAnchor`, `stringResource`, Hilt-ViewModels). Die HTML-Datei öffnet man im Browser. Die anderen Runden (1a–3b) darin sind verworfene Explorationen und dienen nur als Kontext.

## Fidelity
**High-fidelity.** Farben, Typo und Abstände kommen aus dem bestehenden Theme (`ui/theme/Color.kt`, `Type.kt`). Werte unten in dp/sp. Für alle Farben die vorhandenen Theme-Rollen verwenden, keine neuen Hex-Werte einführen.

---

## Screens / Views

### 1. App-Shell: schwebende Navigation (`ui/navigation/AppNavigation.kt`)

**Ersetzt:** `HorizontalDivider` + `NavigationBar` am Ende der `Column`.

**Layout**
- Die `NavHost` füllt jetzt den ganzen Screen. Die Nav-Leiste liegt als Overlay in einer `Box`, ausgerichtet `BottomCenter`.
- Overlay-Row: `padding(start = 16.dp, end = 16.dp, bottom = 20.dp)` + `navigationBarsPadding()`, `horizontalArrangement = spacedBy(10.dp)`, vertikal zentriert.
  - **Pill** (`weight(1f)`, Höhe **60dp**, `RoundedCornerShape(50)`):
    - Ink: Hintergrund `surfaceVariant` (#282826), Border 1dp `outlineVariant` (rgba 255,255,255,.08).
    - Paper: Hintergrund **`onSurface` (#26211A)**, kein Border. Die Pill ist also in beiden Modi dunkel.
    - Schatten: weich, ca. y=12dp, blur 30dp, Schwarz 50–70 %. In Compose: `Modifier.shadow(12.dp, RoundedCornerShape(50), ambientColor/spotColor = Color.Black)`.
    - Innen: `padding(horizontal = 6.dp)`, `Arrangement.SpaceAround`.
  - **Plus-Button**: 60×60dp, `CircleShape`, `primary` (#B79FCB), Icon `Icons.Rounded.Add` 28dp in `onPrimary` (#1B1409). Gleicher Schatten wie die Pill.

**Tab-Items** (Reihenfolge: Ernährung, Training, Statistik)

| Zustand | Darstellung |
|---|---|
| aktiv | Chip, Höhe 46dp, `RoundedCornerShape(50)`, `padding(horizontal = 14.dp)`, Icon 20dp (Rounded/gefüllt) + 6dp Abstand + Label. **Ink:** Chip-Hintergrund `onSurface` (#EDECE8), Inhalt `background` (#161615). **Paper:** Chip-Hintergrund `background` (#F5F0E4), Inhalt `onSurface` (#26211A). Label: Hanken Grotesk SemiBold 13sp. |
| inaktiv | Nur Icon (Outlined) 22dp. Ink: `onSurfaceVariant` (#9C9A94). Paper: `outline` (#9A8F7C). |

- Icons wie bisher: `Restaurant`, `FitnessCenter`, `BarChart` (Rounded = aktiv, Outlined = inaktiv).
- Chip-Wechsel animieren: `animateContentSize()` + Farb-Crossfade, ca. 200ms `FastOutSlowInEasing`.
- Labels: `nav_overview_label` („Ernährung“), `templates_title` („Training“), `stats_title` („Statistik“).
- **Settings-Tab entfällt** in `bottomNavItems()`. Die Route `Screen.Settings` bleibt bestehen, wird aber ohne Nav-Leiste angezeigt (wie Goals/Help) und bekommt dafür einen Zurück-Pfeil in der TopAppBar.

**Plus-Button je Tab**
- Ernährung: `navController.navigate(Screen.AddFood.withDate(selectedDate))`, das bisherige FAB-Verhalten.
- Training: startet ein leeres Training (`Screen.WorkoutSession.start(0)`), das bisherige ExtendedFAB-Verhalten. Icon bleibt `Add` (oder `PlayArrow`, Entscheidung offen, siehe unten).
- Statistik: Plus ausblenden. Die Pill nimmt dann die volle Breite ein.
- Die bisherigen FABs in `OverviewScreen` und `TemplatesScreen` **entfernen**. Die Aktion wird über einen Callback/State an die Shell gehoben, z. B. ein `onPrimaryAction`-Lambda pro Route oder ein kleiner `PrimaryActionController` als CompositionLocal. Wichtig: Die Shell muss das **aktuell gewählte Datum** der Übersicht kennen.

**Abstände unten**
- Alle Tab-Screens: `contentPadding.bottom` auf **110dp** erhöhen (Pill 60 + 20 Rand + Luft), damit der letzte Eintrag nicht unter der Pill verschwindet. Bei Overview war es bisher 88dp.
- `SnackbarHost` über die Pill heben: `padding(bottom = 90.dp)`.

**Tutorial**
- `TutorialTarget.SETTINGS_TAB` → Anchor auf den neuen Settings-Button im Overview-Kopf (Target umbenennen oder Text in `TutorialStep` anpassen: „oben rechts“ statt „unten“).
- `TutorialTarget.STATS_TAB` → Anchor auf das Statistik-Item in der Pill.
- `TutorialTarget.ADD_FOOD` → Anchor auf den Plus-Button der Shell.

---

### 2. Ernährungs-Übersicht (`ui/overview/OverviewScreen.kt`)

**Struktur (LazyColumn, `contentPadding = PaddingValues(start = 16, end = 16, top = 12, bottom = 110)`, `spacedBy(8.dp)`)**
1. `OverviewHeader` (neu)
2. `WeekStrip` (neu)
3. `CalorieCard` (neu, ersetzt `CalorieRing` + `CleanEatingSummary` in `MacroSummaryCard`)
4. `MacroCard` (neu, ersetzt die `MacroBar`s + `SecondaryMacro`-Zeile)
5. Mahlzeiten-Sektionen: **unverändert** (`MealSectionHeader`, `FoodEntryRow`, `SwipeToDismissBox`, `CopyMealButton`, Empty State)

Die `TopAppBar` im `Scaffold` entfällt (`statusBarsPadding()` stattdessen auf der LazyColumn). `WeightCard` entfällt als Listeneintrag. Der Dialog `overview_weight_dialog_title` bleibt und wird vom Chip geöffnet.

#### 2.1 OverviewHeader
- Row, `padding(start = 4, end = 4, bottom = 8)`, `SpaceBetween`, `Alignment.Top`.
- **Links** (Column):
  - Wochentag, voll ausgeschrieben: `localizedDateFormatter("EEEE")` → „Mittwoch“. Newsreader Medium **30sp / 36sp** Zeilenhöhe, `onBackground`. Das entspricht `displaySmall`.
  - Datum: `localizedDateFormatter("d. MMMM")` → „21. Oktober“. `bodyMedium` 14/20, `onSurfaceVariant`.
- **Rechts** (Row, `spacedBy(8.dp)`, `padding(top = 2.dp)`):
  - **Gewichts-Chip**: Höhe 36dp, `RoundedCornerShape(50)`, Hintergrund `surface`, Border 1dp `outlineVariant`, `padding(horizontal = 12.dp)`, `spacedBy(6.dp)`.
    - Icon: Material Symbol `monitor_weight` (bzw. `Icons.Outlined.MonitorWeight` aus material-icons-extended), 16dp, `outline`.
    - Text: „78.4 kg“ wie bisher formatiert (`"${weightKg} kg"`, bzw. „– kg“ ohne Eintrag). Hanken SemiBold 13sp, `onSurface`, `maxLines = 1`.
    - Klick → bestehender Gewichtsdialog. Bekommt `tutorialAnchor(TutorialTarget.WEIGHT_CARD)`.
  - **Settings-Button**: 36×36dp, `CircleShape`, Hintergrund `surface`, Border 1dp `outlineVariant`, Icon `Icons.Outlined.Settings` 18dp, `onSurface`. Klick → `Screen.Settings`.
- „Heute“-Shortcut: Ist das gewählte Datum nicht heute, erscheint im Header links neben dem Gewichts-Chip ein `TextButton(common_today)` in `primary`. Das ersetzt die bisherige TopAppBar-Action.

#### 2.2 WeekStrip
- 7 Spalten, gleich breit (`Row` mit `weight(1f)` je Zelle), `spacedBy(4.dp)`, `padding(bottom = 8.dp)`. Bekommt `tutorialAnchor(TutorialTarget.DAY_SWITCH)`.
- Zeigt die **ISO-Woche (Mo–So)** des gewählten Datums.
- **Zelle** (Column, zentriert, `padding(vertical = 8.dp)`, `spacedBy(6.dp)`, `RoundedCornerShape(14.dp)`):
  - Wochentag-Kürzel, 2 Buchstaben in Großbuchstaben („MO“, „DI“ …, lokalisiert über `DayOfWeek.getDisplayName(TextStyle.SHORT)` gekürzt): JetBrains Mono 10sp, letterSpacing 1sp.
  - Tageszahl: Hanken SemiBold 15sp.
  - Mini-Fortschrittsbalken: 18×3dp, Radius 2dp, Füllung = `kcal des Tages / kcal-Ziel`, auf 1 gedeckelt.

| Zustand | Hintergrund | Kürzel | Zahl | Balken-Track | Balken-Füllung |
|---|---|---|---|---|---|
| gewählt | `onBackground` (Ink #EDECE8 / Paper #26211A) | `background` | `background` | `onSurfaceVariant` | `background` |
| Vergangenheit/heute | transparent | `onSurfaceVariant` | `onBackground` | `surfaceVariant` | `onSurfaceVariant` |
| Zukunft | transparent | `onSurfaceVariant` | `outline` | `surfaceVariant` | (leer) |
| über Ziel (> 100 %) | wie oben | | | | `error` (#E07670) |

- Tippen auf eine Zelle → `viewModel.selectDate(date)`.
- Horizontaler Swipe auf dem Streifen → vorherige/nächste Woche. Der Wochentag bleibt gewählt: Mi → Mi der Nachbarwoche. Umsetzung z. B. mit `HorizontalPager` (eine Seite pro Woche) oder `detectHorizontalDragGestures` + `AnimatedContent(slideInHorizontally)`.
- `previousDay()`/`nextDay()` im ViewModel können bleiben, werden aber nicht mehr von UI-Pfeilen benutzt.

#### 2.3 CalorieCard (Kachel 1)
- Card: `RoundedCornerShape(24.dp)`, `surface`, Border 1dp `outlineVariant`, Elevation 0, `padding(20.dp)`, Column `spacedBy(10.dp)`. Bekommt `tutorialAnchor(TutorialTarget.CALORIE_RING)` (Target ggf. in `CALORIES` umbenennen). Klick → Replay der Animation (bisheriges `onReplay`).
- **Zeile 1** (Row, `SpaceBetween`, `Alignment.Bottom`):
  - Links (Row, Baseline-aligned, `spacedBy(6.dp)`): kcal-Wert gezählt (Newsreader Medium **38sp / 44sp**, letterSpacing −0.25sp = `displayMedium`, `onSurface`) + „/ 2400 KCAL“ (`labelMedium` Mono 11sp, tracking 1.6sp, `outline`). Text: `"/ ${goal.kcal.toInt()} " + stringResource(overview_kcal_label)`.
  - Rechts: `overview_clean_percent` („58 % CLEAN“), `labelMedium`, `onSurfaceVariant`, `padding(bottom = 8.dp)`. Nur wenn `cleanPercent != null`.
- **Zeile 2, segmentierter Balken**: Höhe **12dp**, `RoundedCornerShape(50)` mit `clip`, Track `surfaceVariant`. Segmente in Ring-Reihenfolge Gesund → Neutral → Ungesund → ungetaggt (`outline`), jeweils Breite = `kcalForTag / goal.kcal` (gesamt auf 100 % gedeckelt; bei Überschreitung proportional skalieren). **2dp Lücke** zwischen den Segmenten (`Arrangement.spacedBy(2.dp)`), keine eigenen Rundungen (der Clip rundet die Enden).
  - Über Ziel: Der Balken ist dann komplett gefüllt. Zusätzlich die kcal-Zahl in `error` einfärben.
- **Zeile 3, Legende** (nur wenn mindestens ein getaggter Eintrag existiert): wie bisher in `CleanEatingSummary`, aber linksbündig. Row `spacedBy(14.dp)`, je Punkt 7dp + 4dp + `labelSmall` (Mono 10sp, tracking 1.5sp), `onSurfaceVariant`.
- **Entfällt:** Ring, die Caption „noch X“ / „+X zuviel“ / „Ziel erreicht“ (`overview_kcal_remaining`, `overview_kcal_over`, `overview_kcal_goal_reached` werden ungenutzt und können raus).

#### 2.4 MacroCard (Kachel 2)
- Card: wie 2.3 (24dp, `surface`, Border), `padding(start = 12, end = 12, top = 18, bottom = 16)`, Column `spacedBy(14.dp)`.
- **Ring-Reihe**: Row mit 3 × `weight(1f)`, je Column zentriert, `spacedBy(8.dp)`:
  - Ring 64×64dp, Stroke **6dp**, `StrokeCap.Round` für die Füllung. Track = Makrofarbe mit Alpha 0.2 (wie bisher bei `MacroBar`). Füllung = Makrofarbe, Sweep = `current/goal * 360°` (gedeckelt), Start −90°.
  - Ring-Mitte: Ist-Wert ohne Einheit („113“), Newsreader Medium 18sp, `onSurface`.
  - Darunter Label (`overview_macro_protein` / `_carbs` / `_fat`), Hanken Medium 13sp, `onSurface`, plus Ziel „/ 160 G“ in `labelSmall` Mono 10sp, tracking 1.2sp, `outline`.
  - Farben: `ProteinColor` #82C8F5, `CarbsColor` #82D48A, `FatColor` #E8A4D4.
  - „Kohlenhydrate“ passt bei 13sp in ein Drittel von 328dp. Bei FR/ES-Strings `maxLines = 1` + `TextOverflow.Ellipsis` setzen.
- **Trenner**: `HorizontalDivider(outlineVariant)`, `padding(horizontal = 8.dp)`.
- **Sekundärzeile**: Row zentriert, `spacedBy(18.dp)`, je „Zucker **41 g**“: Label `bodySmall` 12sp `outline` + Wert `bodySmall` Medium `onSurfaceVariant`. Strings: `overview_sugar_label`, `overview_fiber_label`, `overview_salt_label`.

---

## Interactions & Behavior
- **Entrance-/Replay-Animation**: Die bestehende Master-Clock (`AnimTotalSeconds = 3.2f`, `windowProgress`) bleibt. Neue Zeitfenster:
  - kcal-Zahl Count-up: 0.4–2.0s `EaseOutExpo` (wie bisher).
  - Balken-Segmente: wachsen nacheinander von links, 0.5–2.05s, Fenster proportional zum kcal-Anteil, `EaseOutCubic` (gleiche Logik wie bisher beim Ring, nur auf Breite statt Sweep).
  - Clean-%/Legende: Fade + 16dp translateY, 2.1–2.6s.
  - Makro-Ringe: Sweep + Count-up in der Mitte, Fenster wie die bisherigen Balken (P 0.4–1.3, K 0.55–1.45, F 0.7–1.6), `EaseOutCubic`.
  - Trigger wie bisher: Screen öffnen, Datum ändern, Tap auf die CalorieCard.
- **Wochenstreifen**: Auswahlwechsel animiert den Zell-Hintergrund (`animateColorAsState`, 150ms). Wochen-Swipe mit Slide-Übergang.
- **Gewichts-Chip**: öffnet den bestehenden `AlertDialog`. Nach dem Speichern aktualisiert sich der Chip.
- **Pill-Navigation**: Tab-Wechsel mit der gleichen Nav-Semantik wie bisher (`popUpTo(startDestination){saveState}`, `launchSingleTop`, `restoreState`).
- Swipe-to-edit/delete, Undo-Snackbar und „Von Gestern kopieren“: **unverändert**.

## State Management
- `OverviewViewModel`:
  - neu `selectDate(date: LocalDate)`.
  - neu `weekSummary: StateFlow<List<DaySummary>>` mit `DaySummary(date, kcal)` für die 7 Tage der angezeigten Woche. Quelle: Room-Query mit Summe der kcal je Tag zwischen Mo und So, analog zur bestehenden Tages-Query.
  - neu `shownWeekStart: LocalDate`, Wochen-Navigation unabhängig vom gewählten Tag oder daran gekoppelt (siehe oben).
- Shell: aktuelle Route + Primär-Aktion je Route (für den Plus-Button). Overview muss `state.date` an die Shell melden.

## Design Tokens (alle bereits im Theme vorhanden)
| Rolle | Ink (dunkel) | Paper (hell) |
|---|---|---|
| background | #161615 | #F5F0E4 |
| surface | #1E1E1C | #FCF8EF |
| surfaceVariant | #282826 | #EDE4D2 |
| outlineVariant (Hairline) | rgba(255,255,255,.08) | rgba(40,32,18,.12) |
| onSurface | #EDECE8 | #26211A |
| onSurfaceVariant | #9C9A94 | #6E6455 |
| outline | #68665F | #9A8F7C |
| primary / onPrimary | #B79FCB / #1B1409 | #B79FCB / #1B1409 |
| error | #E07670 | #E07670 |

Daten: Protein #82C8F5, Kohlenhydrate #82D48A, Fett #E8A4D4. Tags: Gesund #7CC47F, Neutral #E3A85A, Ungesund #E07670.

**Radien**: Kacheln 24dp, Wochen-Zelle 14dp, Food-Row 18dp (unverändert), Pill/Chips/Buttons 50 %.
**Abstände**: Screen-Rand 16dp, Listen-Gap 8dp, Kachel-Padding 20dp, Nav-Rand 16dp seitlich und 20dp unten.
**Typo** (aus `Type.kt`): Wochentag 30sp Newsreader Medium · kcal 38sp `displayMedium` · Ring-Wert 18sp Newsreader Medium · Mono-Labels `labelMedium`/`labelSmall` · Nav-Label 13sp Hanken SemiBold.

## Assets
- Icons: Material Icons (Rounded/Outlined), wie bereits im Projekt. Neu: `MonitorWeight` (Outlined) aus `material-icons-extended`. Ist die Dependency nicht vorhanden, alternativ `Icons.Rounded.Edit` beibehalten.
- Keine Bilder, keine neuen Fonts.

## Neue/geänderte Strings (DE/EN/FR/ES)
- Keine neuen Pflicht-Strings: Wochentag/Datum kommen aus `localizedDateFormatter`, alle Labels existieren schon.
- Optional entfernen: `overview_kcal_remaining`, `overview_kcal_over`, `overview_kcal_goal_reached`, `overview_previous_day_content_description`, `overview_next_day_content_description`.
- Content-Descriptions: Settings-Button = `settings_title`, Gewichts-Chip = `overview_weight_dialog_title`, Wochenzelle = vollständiges Datum.

## Offene Entscheidungen (Default gesetzt)
- Plus im Training-Tab: Icon `Add` (Default) oder `PlayArrow`.
- Kein Gewichtstrend (z. B. „−0,3“) im Chip.
- Settings-Screen: zeigt die Pill **nicht** mehr, bekommt einen Zurück-Pfeil.

## Files
- `Trainist Redesign.dc.html`: Design-Referenz (im Browser öffnen). Maßgeblich: **4a** (Runde 4) für Ink; **3b** (Runde 3) als Referenz für Paper-Farben von Kopf, Wochenstreifen und Pill.
- `support.js`: Laufzeit für die HTML-Referenz, nicht für die App.
- Betroffene Repo-Dateien: `ui/navigation/AppNavigation.kt`, `ui/overview/OverviewScreen.kt`, `ui/overview/OverviewViewModel.kt`, `ui/workout/TemplatesScreen.kt` (FAB entfernen, Bottom-Padding), `ui/stats/StatsScreen.kt` (Bottom-Padding), `ui/settings/SettingsScreen.kt` (Zurück-Pfeil), `ui/tutorial/TutorialStep.kt` / `TutorialAnchors.kt` (Anchors), Room-DAO für die Wochensummen.
