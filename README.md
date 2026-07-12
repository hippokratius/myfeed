# Kvaesitso RSS

Ein scrollbarer RSS-Reader als Android-App-Widget – gebaut für den
[Kvaesitso-Launcher](https://kvaesitso.mm20.de/). Das Widget zeigt nur
Überschriften und vereinzelt Bilder; Artikel, die vermutlich über dasselbe
Thema berichten, werden zu einer Karte gruppiert (ähnlich dem News-Feed des
Smart Launchers).

> **Warum ein Widget und kein "echtes" Kvaesitso-Plugin?**
> Die Kvaesitso-Plugin-API (SDK 2.x) unterstützt ausschließlich
> Provider-Plugins (Wetter, Kalender, Kontakt-/Datei-/Orte-Suche) – ein Plugin
> kann keine eigene scrollbare Seite im Launcher rendern. Kvaesitso bettet
> aber beliebige Android-App-Widgets auf seinen Seiten ein. Genau das nutzt
> diese App: ein scrollbares [Jetpack-Glance](https://developer.android.com/jetpack/compose/glance)-Widget.

## Features

- **Scrollbares Feed-Widget**: Überschriften mit Quelle und relativer Zeit,
  Thumbnails, wo der Feed Bilder liefert
- **Themen-Gruppierung**: Artikel verschiedener Quellen zum selben Thema werden
  als Karte zusammengefasst – Hauptartikel groß (mit Bild), verwandte
  Überschriften kompakt darunter, jede einzeln antippbar.
  „+n weitere" öffnet die vollständige Gruppen-Ansicht in der App.
  (Horizontal scrollbare Listen sind in App-Widgets technisch nicht möglich,
  daher die vertikale Kompakt-Darstellung.)
- **Tap = Browser**: Jeder Artikel öffnet direkt im Browser
- **Feeds entdecken**: kuratierter Katalog bekannter deutsch- und
  englischsprachiger Feeds (Tagesschau, heise, BBC, …), nach Kategorien
  gruppiert, mit Ein-Tipp-Hinzufügen
- **Feed-Verwaltung**: Feeds manuell per URL hinzufügen oder per
  **OPML-Import** übernehmen (OPML-Ordner werden als Kategorien übernommen)
- **Kategorien**: Jeder Feed kann einer Kategorie zugeordnet werden
  (vordefiniert oder frei benannt); Filter-Chips in der Feed-Liste
- **Widget pro Kategorie**: Jede Widget-Instanz zeigt wahlweise alle Feeds
  oder nur eine Kategorie – z. B. ein Tech- und ein Sport-Widget auf
  verschiedenen Launcher-Seiten (Auswahl beim Platzieren, änderbar per
  Long-Press → Neu konfigurieren)
- **Einstellungen**: Aktualisierungsintervall (15–180 min), Aufbewahrungsdauer,
  Bilder an/aus, Gruppierung an/aus
- Unterstützt RSS 2.0, RSS 1.0 (RDF) und Atom; hell/dunkel folgt dem System

## Widget in Kvaesitso einrichten

1. App installieren und öffnen, Feeds hinzufügen (＋, **Feeds entdecken**
   oder OPML-Import)
2. In Kvaesitso auf dem Startbildschirm nach unten scrollen → **Bearbeiten**
   (Stift-Symbol) → **Widget hinzufügen**
3. Unter **Kvaesitso RSS** das Widget **RSS-Feed** auswählen
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
Artifact **kvaesitso-rss-debug-apk** an den Workflow-Lauf an
(Actions-Tab → letzter Lauf → Artifacts).

Alle Debug-Builds werden mit dem eingecheckten Keystore
`signing/debug.keystore` signiert (Standard-Debug-Passwörter, kein
Geheimnis) — dadurch lassen sich neue APKs ohne Deinstallation über die
bestehende Installation aktualisieren.

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
       ├── fetch/     WorkManager-Sync, OkHttp, Thumbnail-Cache (≤400 px JPEG)
       ├── widget/    Glance-Widget (LazyColumn, Gruppen-Karten, Refresh)
       └── ui/        Compose: Feed-Verwaltung, OPML, Einstellungen, Gruppen-Ansicht
```
