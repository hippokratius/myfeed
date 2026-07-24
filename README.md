# MyFeed

Ein scrollbarer RSS-Reader für Android – App mit Vollbild-Reader plus
scrollbarem Home-Screen-Widget
([Jetpack Glance](https://developer.android.com/jetpack/compose/glance)).
Das Widget zeigt nur Überschriften und vereinzelt Bilder; Artikel, die
vermutlich über dasselbe Thema berichten, werden zu einer Karte gruppiert.

## Features

- **Scrollbares Feed-Widget**: Überschriften mit Quelle und relativer Zeit,
  Thumbnails, wo der Feed Bilder liefert
- **Vollbild-Reader in der App**: Die App startet direkt im Feed – gleiche
  Inhalte wie das Widget, aber mit großen Bildern, Kategorie-Filter-Chips
  und Pull-to-Refresh. Verwandte Artikel einer Themen-Gruppe sind hier als
  **horizontal scrollbare Karten-Reihe** zu sehen
- **Themen-Gruppierung**: Artikel verschiedener Quellen zum selben Thema werden
  als Karte zusammengefasst – Hauptartikel groß (mit Bild), verwandte
  Überschriften kompakt darunter, jede einzeln antippbar. Für Artikel aus
  verschiedenen Quellen genügen zwei gemeinsame Schlüsselwörter, sofern
  eines davon im aktuellen Nachrichtenbestand selten ist (unterschiedliche
  Redaktionen formulieren dieselbe Story unterschiedlich).
  „+n weitere" öffnet die vollständige Gruppen-Ansicht in der App.
  (Horizontal scrollbare Listen sind in App-Widgets technisch nicht möglich,
  daher die vertikale Kompakt-Darstellung.)
- **Tap = Browser**: Jeder Artikel öffnet direkt im Browser
- **Gelesen-Status**: Der Reader zeigt alle Artikel (kein 40er-Limit mehr);
  Artikel, über die von oben nach unten hinweggescrollt wurde, gelten als
  gelesen, werden ausgegraut dargestellt und lassen sich per Augen-Symbol
  in der Titelzeile ausblenden
- **Archiv**: Geöffnete (angetippte) Artikel – auch aus dem Widget – werden
  automatisch archiviert und bleiben über die normale Aufbewahrungsdauer
  hinaus in der Archiv-Liste auffindbar
- **Lesezeichen**: Artikel lassen sich per Lesezeichen-Symbol in eine
  Extraliste speichern; die Aufbewahrungsdauern für Archiv und Lesezeichen
  sind länger als die des Feeds und in den Einstellungen jeweils separat
  wählbar
- **Feeds entdecken**: kuratierter Katalog bekannter deutsch- und
  englischsprachiger Feeds (Tagesschau, heise, BBC, …), nach Kategorien
  gruppiert, mit Ein-Tipp-Hinzufügen
- **Feed-Verwaltung**: Feeds manuell per URL hinzufügen oder per
  **OPML-Import** übernehmen (OPML-Ordner werden als Kategorien übernommen)
- **Kategorien**: Jeder Feed kann einer Kategorie zugeordnet werden
  (vordefiniert oder frei benannt); Filter-Chips in der Feed-Liste
- **Widget pro Kategorie**: Jede Widget-Instanz zeigt wahlweise alle Feeds
  oder nur eine Kategorie – z. B. ein Tech- und ein Sport-Widget auf
  verschiedenen Home-Screen-Seiten (Auswahl beim Platzieren, änderbar per
  Long-Press → Neu konfigurieren)
- **App-Shortcut „Feeds verwalten"**: per Long-Press auf das App-Icon direkt
  in die Feed-Verwaltung springen
- **Einstellungen**: Aktualisierungsintervall (15–180 min), Aufbewahrungsdauer
  (Feed, Archiv und Lesezeichen jeweils separat), Bilder an/aus,
  Gruppierung an/aus
- Unterstützt RSS 2.0, RSS 1.0 (RDF) und Atom; hell/dunkel folgt dem System

## Widget einrichten

1. App installieren und öffnen, Feeds hinzufügen (＋, **Feeds entdecken**
   oder OPML-Import)
2. Long-Press auf eine freie Stelle des Startbildschirms → **Widgets**
3. Unter **MyFeed** das Widget **RSS-Feed** platzieren
4. Beim Platzieren fragt das Widget, ob es alle Feeds oder nur eine
   Kategorie anzeigen soll
5. Über den Widget-Rahmen die Höhe nach Wunsch anpassen – die Liste
   scrollt innerhalb des Widgets

## Build

Voraussetzungen: JDK 17+ und ein Android SDK (`ANDROID_HOME` gesetzt oder
`local.properties` mit `sdk.dir`).

```bash
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # Unit-Tests (Parser, OPML, Gruppierung)
```

Ohne Android SDK wird nur das `:core`-Modul konfiguriert – die JVM-Tests
laufen dann trotzdem: `./gradlew :core:test`.

Bei jedem Push baut GitHub Actions die Debug-APK und hängt sie als
Artifact **myfeed-debug-apk** an den Workflow-Lauf an
(Actions-Tab → letzter Lauf → Artifacts).

Alle Debug-Builds werden mit dem eingecheckten Keystore
`signing/debug.keystore` signiert (Standard-Debug-Passwörter, kein
Geheimnis) — dadurch lassen sich neue APKs ohne Deinstallation über die
bestehende Installation aktualisieren.

Hinweis: Trotz des App-Namens **MyFeed** behält die App die applicationId
`de.hippokratius.kvaesitsorss` (und den Datenbank-Dateinamen) bei – eine
geänderte ID wäre für Android eine neue App, Updates über bestehende
Installationen würden abgelehnt und Feeds/Einstellungen gingen verloren.

## Architektur

```
core/  Reines Kotlin/JVM, ohne Android-Abhängigkeiten (JVM-getestet):
       ├── rss/       RSS-/Atom-Parser (DOM), Datums- und HTML-Helfer
       ├── opml/      OPML-Import
       └── grouping/  TopicClusterer: Tokenisierung (DE/EN-Stoppwörter),
                      Overlap-Ähnlichkeit, Union-Find, 48-h-Zeitfenster

app/   Android-App:
       ├── data/      Room (Feeds, Artikel)
       ├── settings/  DataStore-Einstellungen
       ├── fetch/     WorkManager-Sync, OkHttp, Thumbnail-Cache (≤400 px JPEG,
       │              nur für platzierte Widgets vorab; die App lädt Bilder
       │              lazy beim Scrollen über den Coil-Cache)
       ├── widget/    Glance-Widget (LazyColumn, Gruppen-Karten, Refresh)
       └── ui/        Compose: Vollbild-Reader, Feed-Verwaltung, OPML,
                      Einstellungen, Gruppen-Ansicht
```

## Konzepte (geplant)

- [Nextcloud News als optionales Backend](docs/konzept-nextcloud-news.md) –
  Feeds und Gelesen-/Stern-Status vom eigenen Nextcloud-Server,
  Anmeldung per Single Sign-On über die Nextcloud-Files-App
  (noch nicht implementiert)
