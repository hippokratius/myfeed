# Konzept: Nextcloud News als optionales Backend

Stand: Juli 2026 · Status: **Konzept, nicht implementiert**

## 1. Ziel und Abgrenzung

MyFeed lädt Feeds heute direkt per HTTP und hält alle Daten ausschließlich lokal.
Dieses Konzept beschreibt, wie eine **Nextcloud-News-Instanz als optionales Backend**
integriert werden kann: Wer einen eigenen Nextcloud-Server mit der News-App betreibt,
bezieht Feeds und Artikel dann vom Server und bekommt Gelesen- und Stern-Status
**geräteübergreifend synchron** (Handy, Web-Oberfläche, andere News-Clients).

Die **Anmeldung läuft über die Nextcloud-Files-App** (Single Sign-On), wie man es von
Nextcloud Talk, Notes oder Deck kennt: MyFeed fragt die installierte Files-App nach
einem Konto, der Nutzer bestätigt dort den Zugriff – Zugangsdaten berühren MyFeed nie.

Optional kann MyFeed die News-Instanz zusätzlich **aktiv pflegen**: Eine
Opt-in-Option wendet die in MyFeed eingestellten Aufbewahrungsregeln auch auf den
Server-Bestand an, damit ungelesene bzw. gesternte Altartikel die News-Datenbank
nicht unbegrenzt anwachsen lassen (§4.3).

Nicht Teil dieses Konzepts:

- **Kein Parallelbetrieb** beider Quellen im selben Reader (siehe E1).
- **Kein eigener Server-Login** (Benutzername/Passwort oder Login Flow v2 im Browser) –
  nur als möglicher Ausbauschritt erwähnt (siehe Phase 3).
- **Kein Volltext-Reader**: Artikel öffnen weiterhin im Browser; der HTML-`body` der
  News-Items wird nur zur Bild-Extraktion genutzt.

## 2. Leitentscheidungen

| # | Entscheidung | Begründung / verworfene Alternative |
|---|---|---|
| E1 | **Exklusiver Modus-Schalter**: Entweder „Lokale Feeds“ oder „Nextcloud News“ ist aktiv. | Ein aktives Backend hält Reader-Queries, Widget, Aufbewahrung und Gruppierung eindeutig. *Alternative Parallelbetrieb*: mit der `origin`-Kennzeichnung (E3) technisch erreichbar, aber deutlicher Mehraufwand (Feed-Dedup über beide Welten, doppelte Artikel, gemischte Verwaltungs-UI, unklare Aufbewahrung) – bewusst verschoben. |
| E2 | **Pending-Deltas als Flag-Spalten** (`pendingReadSync`, `pendingStarSync`) statt einer Operations-Tabelle. | Es gibt nur zwei pushbare Änderungen: „gelesen“ ist einweg (kein Ungelesen-UI in MyFeed), „Stern“ ist ein Toggle. Dirty-Flag + aktueller Spaltenwert ergibt die Push-Richtung (Last-Writer-Wins), keine Op-Reihenfolge nötig. Aufbewahrungs-Löschungen verschonen Zeilen mit gesetztem Flag; gepusht wird vor dem Aufräumen. |
| E3 | **Beide Datenbestände bleiben in der DB**, getrennt über eine `origin`-Spalte; lesende Queries filtern nach dem aktiven Origin. | Umschalten ist damit verlustfrei und sofort reversibel – nichts wird kopiert oder gelöscht. Nextcloud-Daten werden erst bei explizitem „Konto trennen“ (mit Rückfrage) entfernt. |
| E4 | **JSON über kotlinx-serialization in `:core`** (`ignoreUnknownKeys = true`, `decodeFromStream`). | Rein JVM, damit im bestehenden `:core`-Testsetup testbar. *Alternative*: das mit der SSO-Bibliothek gebündelte Gson – funktioniert, ist aber reflexionsbasiert, nicht null-sicher und würde `:core` an eine `:app`-Abhängigkeit binden. Gson wird nur intern für den `NextcloudAPI`-Konstruktor verwendet. |
| E5 | **Pro-Feed-Aktiv-Schalter im Nextcloud-Modus ausblenden.** | Der Schalter steuert heute nur das Abrufen (`FeedDao.getEnabled()`); der Reader filtert nicht danach. Im Nextcloud-Modus liefert ein einziger API-Aufruf die Items aller Feeds – der Schalter wäre wirkungslos. Feeds stummschalten heißt dort: am Server löschen oder per Wortfilter arbeiten. |
| E6 | **Artikel öffnen setzt im Nextcloud-Modus zusätzlich „gelesen“** (Push an den Server); das Archiv selbst bleibt ein rein lokales Konzept. | Geöffnete Artikel sollen in der Nextcloud-Weboberfläche als gelesen erscheinen. Nextcloud News kennt kein „Archiv“ – `archivedAt` wird nie synchronisiert. |
| E7 | **Feed-Discovery bleibt clientseitig**: Die bestehende Auflösung (`FeedSyncer.resolveFeedInput()`) läuft vor dem `POST /feeds`. | Identische UX in beiden Modi (Website-URL eingeben → Vorschläge); der Server erhält immer eine konkrete Feed-URL, weniger Fehlversuche. |
| E8 | **Server-Lebenszyklus-Management als Opt-in** (§4.3): Auf Wunsch wendet MyFeed seine Aufbewahrungsregeln auch auf den Server-Bestand an – durch **Gelesen-Markieren** abgelaufener ungelesener Artikel und optional **Entsternen** abgelaufener Lesezeichen. | Die News-API kann Artikel nicht löschen; gelöscht wird nur durch den server-seitigen Auto-Purge, und der entfernt ausschließlich *gelesene, nicht gesternte* Artikel. Ungelesene/gesternte Altartikel sammeln sich sonst unbegrenzt an und können die News-Datenbank auffressen. Default: aus, weil die Aktion für alle Clients des Kontos sichtbar ist. |

## 3. Authentifizierung über die Nextcloud-Files-App (SSO)

### 3.1 Bibliothek und Abhängigkeiten

Verwendet wird die offizielle SSO-Bibliothek, die auch Talk, Notes und Deck einsetzen:

- `com.github.nextcloud:Android-SingleSignOn` (Stand des Konzepts: 1.3.4, via JitPack)
- `settings.gradle.kts`: `maven("https://jitpack.io")` unter
  `dependencyResolutionManagement.repositories` ergänzen (nötig, weil das Projekt
  `RepositoriesMode.FAIL_ON_PROJECT_REPOS` verwendet).
- `AndroidManifest.xml`: `<queries><package android:name="com.nextcloud.client" /></queries>`,
  damit die App die Files-App ab Android 11 sehen darf. Keine neuen Permissions.

Kernprinzip der Bibliothek: MyFeed erhält **nie Zugangsdaten**. Netzwerk-Requests an
den Server werden per IPC **durch die Files-App** ausgeführt
(`NextcloudAPI.performNetworkRequestV2(NextcloudRequest)` liefert einen `InputStream`).
Es gibt daher keinen zweiten OkHttp-Pfad mit Basic-Auth und nichts, das verschlüsselt
gespeichert werden müsste.

### 3.2 Anmelde-Ablauf

```
Nutzer                MyFeed                          Nextcloud-Files-App
  │  „Mit Nextcloud    │                                      │
  │   verbinden“       │                                      │
  ├───────────────────▶│ AccountImporter.pickNewAccount()     │
  │                    ├─────────────────────────────────────▶│
  │                    │            Kontoauswahl + „Zugriff erlauben“
  │                    │◀─────────────────────────────────────┤
  │                    │ onActivityResult → IAccountAccessGranted(account)
  │                    │ SingleAccountHelper.commitCurrentAccount()
  │                    │ GET /apps/news/api/v1-3/version  (Server-Check)
  │                    │   ├─ ok  → Zustand „Verbunden“, Umschalt-Dialog
  │                    │   └─ 404 → „News-App auf dem Server nicht installiert“
  │  Bestätigung       │                                      │
  ├───────────────────▶│ backend_mode = nextcloud, Cursor = 0, syncNow()
```

Konkret in MyFeed:

1. Einstellungen → Sektion „Nextcloud“ → **„Mit Nextcloud verbinden“** ruft
   `AccountImporter.pickNewAccount(activity)` auf.
2. `MainActivity.onActivityResult` leitet an `AccountImporter.onActivityResult(...)`
   weiter; der `IAccountAccessGranted`-Callback liefert das `SingleSignOnAccount`.
3. Ein neuer `SsoSessionManager` (siehe §5) persistiert die Kontowahl
   (`SingleAccountHelper.commitCurrentAccount`), baut die `NextcloudAPI`-Instanz auf
   und prüft per `GET …/version`, ob die News-App auf dem Server installiert ist.
4. Erst nach Bestätigungsdialog („Auf Nextcloud News umschalten? Deine lokalen Feeds
   bleiben erhalten.“) wird `backend_mode` umgestellt, der Sync-Cursor auf 0 gesetzt
   und ein Sofort-Sync angestoßen.

### 3.3 Fehlerfälle

| Fall | Erkennung | Verhalten |
|---|---|---|
| Files-App nicht installiert | `NextcloudFilesAppNotInstalledException` (bzw. `UiExceptionManager`) | Dialog mit Erklärung + Link `market://details?id=com.nextcloud.client` (Fallback: Browser/F-Droid). Modus bleibt „Lokal“. |
| Files-App zu alt | `VersionCheckHelper.verifyMinVersion()` | Hinweis, Files-App zu aktualisieren. |
| News-App fehlt auf dem Server | 404/403 auf `GET …/version` | Meldung in den Einstellungen; nicht umschalten. |
| Zugriff entzogen / Konto gelöscht | `NextcloudFilesAppAccountNotFoundException`, `TokenMismatchException` zur Laufzeit | Zustand „Anmeldung erforderlich“ in den Einstellungen (Button startet den Ablauf aus 3.2 neu). Sync schlägt fehl → WorkManager-Retry; die App bleibt aus dem lokalen Spiegel lesbar. |
| Server nicht erreichbar | `IOException` aus `performNetworkRequestV2` | Wie heute bei Feed-Fehlern: Sync-Fehler wird geloggt, WorkManager wiederholt (Netzwerk-Constraint besteht bereits). |
| Konto trennen | Nutzeraktion | Dialog mit Checkbox „Nextcloud-Daten vom Gerät löschen“ (Default: an) → `NextcloudAPI.stop()`, Kontowahl verwerfen, `backend_mode = local`, Cursor löschen; bei gesetzter Checkbox `DELETE FROM feeds WHERE origin='NEXTCLOUD'` (Artikel via CASCADE) + Thumbnail-Aufräumen. |

## 4. Nextcloud-News-API und Sync-Strategie

Verwendet wird die **News-API v1.3** unter `/index.php/apps/news/api/v1-3/` (relative
Pfade, da die SSO-Bibliothek die Server-Basis beisteuert). Benötigte Endpunkte:

| Zweck | Endpunkt |
|---|---|
| Server-/News-Check | `GET /version` |
| Ordner lesen/anlegen | `GET /folders`, `POST /folders` |
| Feeds lesen/anlegen/löschen/verschieben | `GET /feeds`, `POST /feeds` (`url`, `folderId`), `DELETE /feeds/{id}`, `POST /feeds/{id}/move` |
| Initial-Sync Items | `GET /items?type=3&getRead=false&batchSize=-1` (alle ungelesenen) + `GET /items?type=2&getRead=true&batchSize=-1` (alle gesternten) |
| Inkrementeller Sync | `GET /items/updated?lastModified={cursor}&type=3` |
| Status-Push | `POST /items/read/multiple`, `/items/unread/multiple`, `/items/star/multiple`, `/items/unstar/multiple` (jeweils `itemIds`) |

### 4.1 Feld-Mapping

| Nextcloud News | MyFeed | Anmerkung |
|---|---|---|
| Folder (flach) | `FeedEntity.category` (String) | Ordnername = Kategoriename; passt 1:1, da beide flach sind. Kategorie ändern = Folder anlegen (falls neu) + `feeds/{id}/move`. |
| `item.unread == false` | `readAt` gesetzt | Zeitstempel aus `lastModified * 1000`. |
| `item.starred` | `bookmarkedAt` | Lesezeichen ↔ Stern, bidirektional. |
| – | `archivedAt` | Bleibt rein lokal (E6). |
| `item.mediaThumbnail` → Bild-Enclosure → erstes Bild aus `body` | `imageUrl` | Für die `body`-Extraktion wird die vorhandene First-Image-Logik aus `core/rss/HtmlText.kt` wiederverwendet. |
| `feed.faviconLink` | `iconUrl`/`iconPath` | Läuft durch die bestehende Icon-Pipeline (`ThumbnailStore.downloadIcon`). |
| `item.pubDate` (Sekunden) | `publishedAt` (Millis) | × 1000; fehlend → Sync-Zeitpunkt. |
| `item.guid` → `item.url` → `"nc-{id}"` | `guid` | Fallback-Kette; `remoteId` (= `item.id`) ist das primäre Dedup-Kriterium. |

### 4.2 Sync-Ablauf (`NextcloudNewsBackend.syncAll()`, Pseudocode)

```
syncMutex.withLock:
  api = ssoSessionManager.requireApi()          # wirft NotConnected / AuthLost

  # 0. Server-Lebenszyklus anwenden (nur wenn Option aktiv, §4.3)
  if settings.ncManageLifecycle:
    # abgelaufene ungelesene Artikel: lokal als gelesen markieren + Pending-Flag
    articleDao.expireUnread(origin=NC, minPublishedAt=feedCutoff, readAt=now)
    if settings.ncManageStars:
      # abgelaufene Lesezeichen: lokal entsternen + Pending-Flag
      articleDao.expireBookmarks(origin=NC, minBookmarkedAt=bookmarkCutoff)
    # der Push in Schritt 1 nimmt beides im selben Lauf mit

  # 1. Lokale Deltas ZUERST pushen (verhindert, dass der Pull sie zurückdreht)
  readIds   = articleDao.pendingReadRemoteIds()
  starIds   = articleDao.pendingStarRemoteIds()     # Flag + bookmarkedAt != null
  unstarIds = articleDao.pendingUnstarRemoteIds()   # Flag + bookmarkedAt == null
  api.markItemsRead(readIds);  clearPendingRead(readIds)     # in Blöcken (z. B. 500 IDs)
  api.starItems(starIds);      api.unstarItems(unstarIds);  clearPendingStar(...)
  # Fehler ⇒ Exception ⇒ WorkManager-Retry; Flags bleiben stehen, Push ist idempotent

  # 2. Folders + Feeds abgleichen (FeedReconciler, :core)
  changes = reconcile(lokaler NC-Bestand, api.feeds(), api.folders())
  #   neu     → insert FeedEntity(origin=NEXTCLOUD, remoteId, category=Ordnername, …)
  #   geändert→ Titel/Kategorie/Favicon aktualisieren
  #   weg     → Feed löschen (CASCADE räumt Artikel; Server ist Quelle der Wahrheit)

  # 3. Items ziehen
  if cursor == 0:  items = alle ungelesenen + alle gesternten   # Initial-Sync
  else:            items = api.updatedItems(lastModified=cursor, type=3)

  # 4. Mergen (Upsert über remoteId)
  für jedes Item:
    readAt/bookmarkedAt NUR übernehmen, wenn kein Pending-Flag gesetzt ist
    archivedAt und thumbPath NIE anfassen
    Inhaltsfelder (Titel, Link, Bild, …) immer aktualisieren

  # 5. Cursor fortschreiben
  cursor = max(alterCursor, max(items.lastModified))    # bewusst OHNE "+1":
  # /items/updated liefert lastModified >= X (inklusiv). Ein "+1" könnte bei
  # sekundengleichen Stempeln Items verlieren; die garantierte Überlappung ist
  # harmlos, weil der Upsert über remoteId idempotent ist.

  # 6. Gemeinsame Nachverarbeitung (SyncPostProcessor, origin-bezogen)
  Aufbewahrung + Max-Anzahl → Icons/Thumbnails → Themen-Gruppierung
  → lastSyncMillis → Widget-Update
```

Eigenschaften:

- **Offline-first:** Gelesen-/Stern-Änderungen schreiben sofort in Room und setzen nur
  das Pending-Flag; die „Queue“ ist der nächste WorkManager-Lauf
  (Netzwerk-Constraint und Retry existieren bereits in `FeedFetchWorker`).
- **Server-Löschungen von Items** meldet die API nicht einzeln – wie im lokalen Modus
  räumt die zeitbasierte Aufbewahrung (`maxAgeDays`) alte Artikel weg.
- Der Initial-Sync folgt der offiziellen Client-Empfehlung („ungelesen + gesternt“);
  ältere gelesene Artikel fehlen anfangs, was die Aufbewahrungsdauer ohnehin kappen
  würde. Für sehr große Konten ist `offset`-Batching als Absicherung vorgesehen
  (Phase 3).

### 4.3 Server-Lebenszyklus-Management (Opt-in, E8)

**Motivation.** Nextcloud News löscht Artikel ausschließlich über seinen
server-seitigen Auto-Purge: Nach jedem Feed-Update werden pro Feed die *gelesenen,
nicht gesternten* Artikel über dem konfigurierten Limit entfernt (Admin-Einstellung
„Maximum read count per feed“, Default 200; `-1` deaktiviert das Aufräumen komplett).
**Ungelesene und gesternte Artikel werden nie gelöscht.** Wer Feeds abonniert, aber
nicht alles liest, sammelt damit unbegrenzt ungelesene Altartikel an – bis die
News-Datenbank kippt (genau dieses Szenario ist der Anlass für dieses Feature).
Die News-API bietet Clients **keinen Lösch-Endpunkt für Artikel**; der einzige Hebel,
den ein Client hat, ist der Artikel-*Status*: gelesen/ungelesen und Stern.

**Mechanik.** Ist die Option aktiv, wendet MyFeed seine in den Einstellungen
gewählten Aufbewahrungsregeln auch auf den Server-Bestand an – nicht durch Löschen
(unmöglich), sondern indem es Artikel **purge-fähig macht**:

| MyFeed-Regel | Server-Aktion bei Ablauf |
|---|---|
| Aufbewahrungsdauer Feed (`maxAgeDays`) | Ungelesene Artikel, deren `publishedAt` älter ist, werden als **gelesen** markiert (`items/read/multiple`). |
| Aufbewahrung Lesezeichen (`bookmarkMaxAgeDays`) | *Nur mit zusätzlicher Unteroption:* abgelaufene Lesezeichen werden **entsternt** (`items/unstar/multiple`). |
| Aufbewahrung Archiv | Keine Server-Aktion nötig – geöffnete Artikel sind bereits als gelesen gepusht (E6), das Archiv selbst ist lokal. |

Der Schritt läuft zu **Beginn** jedes Syncs (Schritt 0 in §4.2) rein lokal über die
bestehende Pending-Mechanik (E2): abgelaufene Zeilen erhalten `readAt` bzw. verlieren
`bookmarkedAt` und bekommen das Pending-Flag; der unmittelbar folgende Push-Schritt
überträgt die Änderung in ID-Blöcken (z. B. 500 pro Request). Scheitert der Push,
bleiben die Flags stehen – die Aufräum-Queries verschonen Pending-Zeilen, es geht
nichts verloren.

**Warum die lokale Spiegel-DB als Kandidatenquelle reicht:** Der Sync hält per
Definition *alle* ungelesenen und *alle* gesternten Artikel des Kontos lokal vor
(Initial-Sync §4.2). Genau diese beiden Zustände sind es, die den Server-Purge
blockieren – die Kandidatensuche ist also eine reine lokale Query, kein zusätzlicher
API-Scan. Auch ein jahrealter Rückstau ungelesener Artikel wird beim Aktivieren der
Option erfasst, weil er durch den Initial-Sync im Spiegel liegt. Wichtig ist nur die
Reihenfolge: Der Lebenszyklus-Schritt läuft **vor** dem lokalen Aufräumen, damit
abgelaufene ungelesene Artikel nicht aus dem Spiegel fallen, bevor ihre remoteId
gepusht wurde (durch das Pending-Flag zusätzlich abgesichert).

**Grenzen (im Dokument und in der UI ehrlich benennen):**

- Das eigentliche **Löschen** erledigt weiterhin der Server-Purge. Hat der Admin
  „Maximum read count per feed“ auf `-1` gestellt, wächst die Datenbank trotz
  MyFeed weiter – die Einstellungs-UI verlinkt deshalb einen kurzen Hinweistext
  („MyFeed markiert abgelaufene Artikel als gelesen; entfernt werden sie durch das
  automatische Aufräumen von Nextcloud News – Limit in dessen Admin-Einstellungen
  prüfen“).
- Bereits *gelesene* Altartikel über dem Purge-Limit kann kein Client entfernen;
  das regelt der Server allein.
- Die Aktionen sind **kontoweit sichtbar**: Als-gelesen-Markierungen und entfernte
  Sterne gelten auch in der Weboberfläche und in allen anderen Clients. Deshalb
  Opt-in mit Bestätigungsdialog, und das Entsternen als separate, noch einmal
  bewusster zu aktivierende Unteroption (Sterne können von anderen Clients aktiv
  genutzt werden). Ein auf einem anderen Gerät frisch erneuerter Stern ist sicher:
  Der Pull aktualisiert `bookmarkedAt` auf den neuen `lastModified`-Zeitstempel,
  damit beginnt die Lesezeichen-Frist neu.
- Neuere News-Versionen bieten server-seitig „Purge unread items“ als Admin-Option –
  das löst das Rückstau-Problem global, erfordert aber Admin-Zugriff und kennt keine
  per-Nutzer-Fristen. Die MyFeed-Option ist das client-seitige Pendant mit den
  vertrauten MyFeed-Aufbewahrungsregeln; beide vertragen sich problemlos.

## 5. Architektur

### 5.1 Backend-Naht

Heute ist `FeedSyncer` hart mit OkHttp + RSS-Parser verdrahtet und die Screens rufen
DAOs/Syncer direkt über den handverdrahteten `AppGraph` auf. Es gibt **keinen Seam** –
der zentrale Umbau ist deshalb ein schmales Backend-Interface, hinter dem der
bestehende RSS-Pfad (reiner Refactor-Extract) und der neue Nextcloud-Pfad stehen:

```kotlin
enum class BackendMode { LOCAL_RSS, NEXTCLOUD_NEWS }
object Origin { const val LOCAL = "LOCAL"; const val NEXTCLOUD = "NEXTCLOUD" }

data class BackendCapabilities(
    val perFeedEnableSwitch: Boolean,      // nur lokal (E5)
    val feedManagementNeedsNetwork: Boolean,
)

interface FeedBackend {
    val mode: BackendMode
    val capabilities: BackendCapabilities
    suspend fun syncAll()
    suspend fun addFeed(url: String, title: String?, category: String?)
    suspend fun deleteFeed(feed: FeedEntity)
    suspend fun setCategory(feed: FeedEntity, category: String?)
    suspend fun importOpml(feeds: List<OpmlFeed>): Int
    suspend fun markRead(articleIds: List<Long>, readAt: Long)
    suspend fun markOpened(articleId: Long, timestamp: Long)   // Archiv + ggf. read-Push (E6)
    suspend fun setBookmarked(articleId: Long, bookmarkedAt: Long?)
}
```

- **Lesende Flows bleiben auf den DAOs** (mit neuem `origin`-Parameter) – die Screens
  behalten ihr Muster „Flow beobachten, Aktionen über den Graph“. Nur die
  *schreibenden* Aufrufe (`markRead`, `markArchived`, `setBookmarked`, Feed-CRUD)
  wandern vom DAO/Syncer auf das aktive Backend.
- `FeedSyncer.resolveFeedInput()` (Feed-Discovery) wird von beiden Backends genutzt (E7).
- `FeedFetchWorker.doWork()` ruft statt `feedSyncer.syncAll()` künftig
  `backendRegistry.current().syncAll()` auf; Work-Namen und Scheduling bleiben gleich.

### 5.2 Neue Komponenten

**`:core`** (rein JVM, unit-getestet):

| Datei | Inhalt |
|---|---|
| `core/…/nextcloud/NewsDtos.kt` | `@Serializable`-DTOs: `NewsFolder`, `NewsFeed`, `NewsItem`, Antwort-Hüllen, `NewsVersion` |
| `core/…/nextcloud/NewsJson.kt` | konfigurierte `Json`-Instanz + `parse…(InputStream)`-Helfer |
| `core/…/nextcloud/NewsApi.kt` | Interface der API-Aufrufe (Implementierung in `:app`), damit die Sync-Logik gegen einen Fake testbar ist |
| `core/…/nextcloud/NewsRoutes.kt` | Pfad-/Query-Aufbau als reine Strings/Maps |
| `core/…/nextcloud/NewsSyncLogic.kt` | Cursor-Fortschreibung, Merge-Regeln (Pending-Flag gewinnt) |
| `core/…/nextcloud/FeedReconciler.kt` | Diff Server-Feeds/-Folders ↔ lokaler Bestand → Insert/Update/Delete-ChangeSet |
| `core/…/nextcloud/NewsItemMapper.kt` | Bild-Priorität, Zeit- und GUID-Mapping (§4.1) |

**`:app`**:

| Datei | Inhalt |
|---|---|
| `app/…/backend/FeedBackend.kt` | Interface, `BackendMode`, `BackendCapabilities`, `BackendException` (`NotConnected`, `AuthLost`, `NewsAppMissing`, `ServerUnreachable`, `FeedRejected`) |
| `app/…/backend/LocalRssBackend.kt` | Delegiert an `FeedSyncer` + DAOs; Verhalten 1:1 wie heute |
| `app/…/backend/BackendRegistry.kt` | Liefert das aktive Backend aus `AppSettings.backendMode`; `switchTo()` |
| `app/…/nextcloud/SsoSessionManager.kt` | `AccountImporter`/`SingleAccountHelper`-Kapselung, `NextcloudAPI`-Singleton, Konto-Zustand als `StateFlow`, Ausnahme-Mapping |
| `app/…/nextcloud/SsoNewsApi.kt` | `NewsApi`-Implementierung über `performNetworkRequestV2` → `InputStream` → `NewsJson` |
| `app/…/nextcloud/NextcloudNewsBackend.kt` | Sync-Orchestrierung (§4.2), Feed-CRUD über den Server, Delta-Push |
| `app/…/fetch/SyncPostProcessor.kt` | Aus `FeedSyncer.syncAll()` extrahierte Nachverarbeitung: Aufbewahrung, Max-Anzahl, Icons/Thumbnails, Gruppierung, `lastSyncMillis`, Widget-Update – von beiden Backends genutzt |
| `app/…/ui/NextcloudSettingsSection.kt` | Compose-Sektion für den Settings-Screen |

**Geänderte Dateien (Kern):** `MyFeedApp.kt` (AppGraph um `ssoSessionManager`,
`postProcessor`, beide Backends, `backendRegistry` erweitern), `data/Entities.kt` /
`Daos.kt` / `AppDatabase.kt` (§6), `fetch/FeedSyncer.kt` (Nachverarbeitung ausgelagert,
origin-bewusst), `fetch/FeedFetchWorker.kt`, die Screens (§7), `settings/
SettingsRepository.kt` (neue Keys), `settings.gradle.kts` (JitPack),
`gradle/libs.versions.toml` + Modul-Buildfiles (SSO-Bibliothek in `:app`,
`kotlinx-serialization-json` + Serialization-Plugin in `:core`), `AndroidManifest.xml`
(`<queries>`), Strings in `values/` **und** `values-en/`.

## 6. Datenmodell und Migration (Room v5 → v6)

**`feeds`** – Tabellen-Rebuild nötig, weil der Unique-Index von `(url)` auf
`(origin, url)` wechselt (derselbe Feed darf lokal *und* als Nextcloud-Spiegel
existieren):

- `+ origin TEXT NOT NULL DEFAULT 'LOCAL'`
- `+ remoteId INTEGER` (News-Feed-ID)
- Indizes: `UNIQUE(origin, url)`, `UNIQUE(origin, remoteId)`, `category` wie gehabt

**`articles`** – nur `ALTER TABLE ADD COLUMN`:

- `+ origin TEXT NOT NULL DEFAULT 'LOCAL'` (denormalisiert, damit Reader-Queries,
  Aufbewahrung und Max-Anzahl ohne Join origin-bezogen laufen)
- `+ remoteId INTEGER` + `UNIQUE(remoteId)` (primäres Dedup; der bestehende
  `(feedId, guid)`-Index bleibt als zweite Verteidigung, Insert weiter `IGNORE`)
- `+ pendingReadSync INTEGER NOT NULL DEFAULT 0`
- `+ pendingStarSync INTEGER NOT NULL DEFAULT 0`
- `+ INDEX(origin, publishedAt)`

Migrations-Hinweise: Rebuild nach dem etablierten Muster (`CREATE feeds_new` → `INSERT
… SELECT` → `DROP` → `RENAME`, danach `PRAGMA foreign_key_check`); neue Spalten in den
Entities mit `@ColumnInfo(defaultValue = …)` annotieren, sonst scheitert Rooms
Schema-Validierung; der DB-Dateiname `kvaesitso-rss.db` bleibt unverändert.

**DAO-Erweiterungen (Auszug):** origin-Parameter für alle lesenden und aufräumenden
Queries (`observeAllNewest`, `observeArchived/Bookmarked`, `deleteOlderThan`,
`enforceMaxCount`, …); neu: `byRemoteId`, `markReadPending(ids, readAt)`,
`setBookmarkedPending(id, ts)`, `pendingRead/Star/UnstarRemoteIds()`,
`clearPendingRead/Star(remoteIds)` sowie ein Merge-Update, das `readAt`/`bookmarkedAt`
nur bei nicht gesetztem Pending-Flag überschreibt. Aufräum-Queries erhalten zusätzlich
`AND pendingReadSync = 0 AND pendingStarSync = 0` (E2-Schutz). Für das
Lebenszyklus-Management (§4.3): `expireUnread(origin, minPublishedAt, readAt)`
(setzt `readAt` + `pendingReadSync` für ungelesene Artikel unterhalb des Cutoffs)
und `expireBookmarks(origin, minBookmarkedAt)` (löscht `bookmarkedAt`, setzt
`pendingStarSync`), jeweils nur für Zeilen mit `remoteId`.

**Neue DataStore-Keys** (`SettingsRepository`): `backend_mode` (Default `"local"`),
`nc_last_modified` (Sekunden-Cursor, 0 = Initial-Sync ausstehend), `nc_account_name`
(nur Anzeige – die Wahrheit liegt bei `SingleAccountHelper`), optional
`nc_last_sync_error`; für §4.3 `nc_manage_lifecycle` (Bool, Default aus) und
`nc_manage_stars` (Bool, Default aus, nur wirksam mit `nc_manage_lifecycle`).

## 7. UI-Änderungen und Feature-Matrix

### 7.1 Änderungen pro Screen

| Screen | Änderung |
|---|---|
| `SettingsScreen` | Neue Sektion „Nextcloud“: nicht verbunden → Button „Mit Nextcloud verbinden“ + Hinweis auf die Files-App; verbunden → Kontoname/Server, Modus-Auswahl *Lokale Feeds* / *Nextcloud News* (Nextcloud nur mit Konto wählbar, Wechsel mit Bestätigungsdialog), „Konto trennen“, Fehlerzeile bei `AuthLost`/`NewsAppMissing`. Dazu Schalter **„Aufbewahrung auch auf dem Server anwenden“** (§4.3, mit Bestätigungsdialog: erklärt Gelesen-Markierung, kontoweite Sichtbarkeit und dass das Löschen der Server-Purge übernimmt) und – nur wenn dieser aktiv ist – Unter-Schalter **„Abgelaufene Lesezeichen auch entsternen“**; beide nur im Nextcloud-Modus sichtbar. |
| `MainActivity` | `onActivityResult` an `AccountImporter.onActivityResult` weiterleiten. |
| `ReaderScreen` | Queries mit `origin = aktives Origin`; Scroll-Tracking ruft `backend.markRead`, Öffnen `backend.markOpened`, Lesezeichen `backend.setBookmarked`. Sonst unverändert. |
| `SavedScreen` / `GroupScreen` | Nur origin-bezogene Queries. |
| `FeedsScreen` | Hinzufügen/Löschen/Kategorie über das aktive Backend (Nextcloud: Server-Aufrufe mit Fortschritt und Fehler-Feedback, Ordner werden bei Bedarf angelegt); OPML-Import → `backend.importOpml` (Nextcloud: je Eintrag `POST /feeds`, Ergebnis „X hinzugefügt, Y fehlgeschlagen“); Aktiv-Schalter nur bei `capabilities.perFeedEnableSwitch` (E5). |
| `DiscoverScreen` | One-Tap-Add → `backend.addFeed` (Katalog-Kategorie wird zum Ordner). |
| Widget (`WidgetEntries`, `RssWidget`, `WidgetConfigActivity`) | Queries und Kategorienliste origin-bezogen; sonst unverändert. |
| `OpenArticleActivity` | Archivieren über `backend.markOpened` (E6). |

Neue Strings (Deutsch als Default in `values/`, Übersetzung in `values-en/`):
Verbinden/Trennen, Kontoanzeige, Modus-Labels, Bestätigungs- und Fehlertexte
(Files-App fehlt, News-App fehlt, Anmeldung erforderlich, Feed abgelehnt, offline)
sowie Label, Erklärungs- und Bestätigungstexte der Lebenszyklus-Option (§4.3)
inkl. Hinweis auf den Server-Purge.

### 7.2 Feature-Matrix

| Funktion | Lokale Feeds | Nextcloud News |
|---|---|---|
| Feed hinzufügen (URL/Discovery) | lokal, sofort | Discovery lokal, dann `POST /feeds` – braucht Netz |
| Katalog „Entdecken“ / OPML-Import | ja | ja (an den Server, Kategorie→Ordner automatisch) |
| Feed löschen | ja | ja (`DELETE /feeds/{id}`, braucht Netz) |
| Kategorien | freier String | = News-Ordner (beide flach, 1:1) |
| Pro-Feed aktiv/inaktiv | ja | entfällt (E5) |
| Gelesen (Scroll-Tracking) | lokal | lokal + Push an den Server |
| Lesezeichen | lokal | = Stern, bidirektional synchron |
| Archiv (geöffnete Artikel) | lokal | lokal; Öffnen pusht zusätzlich „gelesen“ (E6) |
| Themen-Gruppierung, Wortfilter, Widget, Bilder | ja | identisch (gemeinsame Nachverarbeitung) |
| Aufbewahrung/Aufräumen | ja | identisch, origin-bezogen; Server-Bestand bleibt unberührt – **außer** die Opt-in-Option §4.3 ist aktiv: dann werden abgelaufene Artikel am Server als gelesen markiert (optional entsternt) und damit für den Server-Purge freigegeben |
| Lesestatus anderer Geräte/Clients | – | ja (über `/items/updated`) |
| Offline lesen | ja | ja (lokaler Spiegel) |

## 8. Umschalt-Semantik

- **Lokal → Nextcloud:** Lokale Feeds/Artikel bleiben unangetastet in der DB
  (`origin = LOCAL`); Reader, Widget und Verwaltung zeigen nur noch
  `origin = NEXTCLOUD`. Erster Sync = Initial-Sync (§4.2).
- **Nextcloud → Lokal:** Sofort wieder der alte lokale Bestand; Nextcloud-Daten
  bleiben (für schnelles Zurückschalten) liegen, bis das Konto getrennt wird.
- **Konto trennen:** Siehe §3.3 – optional mit Löschung der Nextcloud-Daten.
- Periodischer Sync (`FeedFetchWorker`) läuft unverändert und bedient immer das
  gerade aktive Backend.

## 9. Teststrategie

**Schwerpunkt `:core` (reine JVM-Tests, wie bestehende Parser-Tests):**

- `NewsJsonTest`: Fixture-JSONs der API v1.3 – vollständige Objekte, fehlende
  optionale Felder, unbekannte Schlüssel, `folderId` 0/null, leere Listen.
- `NewsRoutesTest`: Pfade/Query-Parameter inkl. `batchSize=-1`.
- `NewsSyncLogicTest`: Cursor (max-`lastModified`, leere Antwort ⇒ unverändert,
  Überlappungs-Idempotenz); Merge (Pending-Flag gewinnt, `archivedAt` unangetastet).
- Lebenszyklus (§4.3): Cutoff-Auswahl der Kandidaten (nur ungelesen bzw. nur
  abgelaufene Sterne, nur mit `remoteId`), ID-Blockbildung für den Push,
  Frist-Neustart nach Re-Star durch anderen Client (Pull aktualisiert
  `bookmarkedAt`), Reihenfolge „Lebenszyklus vor lokalem Aufräumen“.
- `FeedReconcilerTest`: neu/gelöscht/umbenannt/Ordner-Wechsel/Favicon-Änderung.
- `NewsItemMapperTest`: Bild-Priorität (inkl. Tracker-Pixel-Skip über `HtmlText`),
  Sekunden→Millis, GUID-Fallbacks.

**`:app`:** Migration v5→v6 mit Bestandsdaten einer v5-Installation manuell prüfen
(+ `PRAGMA foreign_key_check`); Rooms `MigrationTestHelper` ist empfohlen, aber kein
Blocker (bisher existiert kein `androidTest`-Setup). Manueller E2E-Plan: Verbinden ·
Files-App fehlt · News-App fehlt · großer Initial-Sync · offline lesen/sternen →
Push beim nächsten Sync · Moduswechsel hin und zurück (Datenerhalt) · Trennen mit und
ohne Löschen · Zugriff in der Files-App entziehen.

## 10. Rollout-Phasen

1. **Phase 1 – MVP:** Gradle/JitPack, Schema v6, Backend-Naht + `LocalRssBackend`
   (reiner Refactor, Verhalten unverändert), SSO-Anbindung, `NewsApi` + `:core`-Logik,
   `NextcloudNewsBackend` (Pull-Sync + Gelesen-/Stern-Push), Settings-Sektion,
   Moduswechsel, origin-Filter in Queries/Widget, Fehlerbehandlung.
2. **Phase 2 – Feed-Verwaltung & Server-Lebenszyklus:** Feed-CRUD über den Server,
   OPML-Import und „Entdecken“ im Nextcloud-Modus, optionaler Migrationsassistent
   („lokale Feeds zum Server exportieren“ per `POST /feeds`; Lesestatus ist nicht
   übertragbar – API-Grenze), **Server-Lebenszyklus-Management (§4.3)** – baut nur
   auf der Pending-Mechanik aus Phase 1 auf und ist bewusst klein gehalten.
3. **Phase 3 – Ausbau:** Offset-Batching für sehr große Konten, Sync-Fehleranzeige im
   Reader, ggf. **Login Flow v2** als Fallback für Geräte ohne Files-App (erfordert
   eigene Credential-Haltung + zweiten HTTP-Pfad – bewusst nicht Teil dieses
   Konzepts).

## 11. Offene Punkte

1. Default der Checkbox „Nextcloud-Daten beim Trennen löschen“ (Vorschlag: an).
2. Bestätigung von E6 (Öffnen = am Server „gelesen“; Vorschlag: ja).
3. Ob der Migrationsassistent (Phase 2) den lokalen Bestand nach dem Export
   deaktivieren oder unverändert lassen soll.
4. Server-Lebenszyklus (§4.3): Soll das Gelesen-Markieren wirklich an
   `maxAgeDays` (Feed-Aufbewahrung, Default 7 Tage) hängen oder an einer eigenen,
   großzügigeren Frist (z. B. Default 30 Tage)? Vorschlag: eigene Frist mit
   eigenem Default, da „aus meinem Reader gefallen“ und „kontoweit als gelesen
   markiert“ unterschiedlich schwer wiegen.
