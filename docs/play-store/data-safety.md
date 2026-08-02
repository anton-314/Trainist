# Play Store — Data-Safety-Formular (Entwurf)

Antwortvorlage für Play Console → **App-Content → Datensicherheit**. Basiert auf dem
tatsächlichen Verhalten der App (siehe `CLAUDE.md`) — kein Server, kein Konto, keine
Analytics/Ad-SDKs. Play definiert „Erfassung" als *Daten, die das Gerät verlassen* — rein
lokal gespeicherte Daten zählen nicht als erfasst.

## 1. Erfasst diese App Nutzerdaten oder gibt sie weiter?

**Ja** — beschränkt auf die Lebensmittelsuche gegen die offene Datenbank Open Food Facts:

- **Barcode-Suche**: die gescannte/eingegebene Barcode-Nummer.
- **Namenssuche**: der eingetippte Suchbegriff, die eingestellte App-Sprache und das Land des
  Geräts (Letzteres nur zur Eingrenzung der Treffer auf lokal verkaufte Produkte). Die Anfrage
  geht erst ab drei Zeichen und nach einer Tippause heraus.

Beides ist keine personenbezogene Information und nicht mit einer Nutzeridentität verknüpft;
es wird keine Kennung mitgesendet.

Alles andere (Ernährungs-, Gewichts-, Körpermaß- und Trainingsdaten, Ziele, die Angaben des
Kalorienbedarfs-Rechners) bleibt ausschließlich lokal auf dem Gerät und verlässt es nie — außer
der Nutzer exportiert es selbst aktiv (Backup-ZIP über die System-Teilen-Funktion). Das zählt
nach Play-Definition nicht als "Erfassung durch die App".

## 2. Datentypen — je Kategorie

Für jede Kategorie im Formular:

| Kategorie | Erfasst? | Geteilt? | Begründung |
|---|---|---|---|
| Standort | Nein | Nein | Keine Standortzugriffe im Code |
| Personenbezogene Daten (Name, E-Mail, ...) | Nein | Nein | Kein Konto, keine Registrierung |
| Finanzielle Informationen | Nein | Nein | Keine Zahlungsabwicklung in der App (PayPal-Link öffnet den externen Browser) |
| Gesundheit und Fitness | **Nein** (nicht erfasst i. S. der Definition) | Nein | Gewichts-, Körpermaß-, Ernährungs- und Trainingsdaten sowie das Profil des Kalorienbedarfs-Rechners bleiben lokal auf dem Gerät |
| Nachrichten | Nein | Nein | — |
| Fotos und Videos | Nein | Nein | Kamera wird nur für Live-Barcode-Erkennung genutzt, es werden keine Bilder gespeichert oder übertragen |
| Audiodateien | Nein | Nein | — |
| Dateien und Dokumente | Nein | Nein | Backup-Export ist eine reine Nutzeraktion (Teilen-Funktion), keine App-seitige Erfassung |
| Kalender | Nein | Nein | — |
| Kontakte | Nein | Nein | — |
| App-Aktivitäten | Nein | Nein | Kein Analytics-/Tracking-SDK enthalten |
| Web-Browsing-Verlauf | Nein | Nein | — |
| App-Infos und Leistung | Nein | Nein | Kein Crash-/Analytics-Reporting enthalten |
| Geräte- oder andere Kennungen | Nein | Nein | — |

Falls Play Console eine Angabe zur Suchanfrage/Netzwerkanfrage erzwingt: am ehesten unter
„App-Aktivitäten → sonstige Aktionen in der App" mit Zweck „App-Funktionalität", **nicht**
zu Werbe-, Analyse- oder Profilbildungszwecken, **nicht geteilt** mit Dritten außerhalb der
Zweckbindung der Produktsuche.

## 3. Sicherheitspraktiken

- **Daten werden während der Übertragung verschlüsselt**: n/a für Nutzerdaten (verlassen das
  Gerät nicht); die Open-Food-Facts-Abfrage läuft über HTTPS.
- **Nutzer können die Löschung ihrer Daten verlangen**: Ja — jeder Eintrag ist in der App
  löschbar; vollständige Löschung durch Deinstallation der App.
- **Unabhängige Sicherheitsüberprüfung**: Nein.

## 4. Berechtigungs-Deklaration (App-Content → Berechtigungen)

- **Kamera**: ausschließlich für die On-Device-Barcode-Erkennung (ML Kit), keine
  Bildspeicherung/-übertragung.
- **Benachrichtigungen**: tägliche Ernährungs-Erinnerung + Pausen-Timer-Alarm, beide lokal. Der
  Pausen-Timer nutzt bewusst **keine** genauen Alarme (`USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`
  sind nicht deklariert, `AlarmManager` kommt in der App gar nicht mehr vor) — den Countdown
  besitzt für seine gesamte Dauer ein Vordergrunddienst, der den Alarm selbst auslöst.
- **Vordergrunddienst (`specialUse`)**: der Pausen-Timer zwischen den Sätzen. Der Subtyp
  `rest_timer_between_sets` ist im Manifest deklariert und braucht in der Play Console eine
  Begründung im Formular zu Vordergrunddiensten.

## Referenz

Vollständige technische Details zu Netzwerkzugriffen, Berechtigungen und Datenhaltung stehen
in der Datenschutzerklärung ([Deutsch](../privacy-policy/index.html) ·
[English](../privacy-policy/en/index.html) · [Français](../privacy-policy/fr/index.html) ·
[Español](../privacy-policy/es/index.html)) und in `CLAUDE.md`.
