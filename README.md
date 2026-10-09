# Sibyl System

Firmenneutrale, selbst gehostete und modulare Webplattform mit eigenem **PostgreSQL-Core**, Administrationsoberfläche und einem ausschließlich über **GitHub-Releases** versorgten Addon-Browser.

**Status (09.10.2026):** Entwicklungsstand, `test`-Branch. Core `v0.1.0-rc.1` ist auf der internen Labor-VM lauffähig; die **neue datenbankgestützte Version `v0.1.0-rc.2`** ist noch separat zu veröffentlichen und auf Cluster 3 zu deployen. Keine Produktions-/DMZ-Freigabe.

## Standardinstallation mit PostgreSQL

Die Standardinstallation für eine frische **Ubuntu-24.04-VM** besteht aus einem einzigen Release-Installer:

```text
GitHub Release v0.1.0-rc.2
├── sibyl-core-0.1.0-rc.2.jar
├── install-sibyl-ubuntu-2404.sh
├── STANDARD-INSTALLATION.md
├── RELEASE-POLICY.md
└── SHA256SUMS.txt
```

Nach Prüfung der Release-Assets das Script `sudo bash install-sibyl-ubuntu-2404.sh` ausführen. Es installiert **PostgreSQL und Java 21**, erstellt die Datenbank `sibyl` samt zufällig generiertem Datenbankkennwort, lädt den Core aus dem **öffentlichen GitHub-Release** mit SHA-256-Validierung und startet ihn als eingeschränkten Systemdienst.

**Erstanmeldung:** Benutzer `admin`, Passwort `friend`. Das bekannte Standardpasswort wird nur beim ersten Start einer leeren Datenbank als BCrypt-Hash gespeichert. **Vor jedem administrativen Eingriff muss es geändert werden** (neues Passwort mindestens 12 Zeichen). Bei regulären Neustarts und Updates bleiben Konten und Einstellungen unverändert. Den Erststart ausschließlich im geschützten Netz und mit einem vertrauenswürdigen HTTPS-Reverse-Proxy freigeben.

Der Installer bindet den Core absichtlich nur auf `127.0.0.1:8080`; er erzeugt keine öffentliche HTTP-Anmeldung. Die detaillierte Anleitung, Datenbankstruktur und Wartungshinweise stehen unter [docs/STANDARD-INSTALLATION.md](docs/STANDARD-INSTALLATION.md).

## Administration

- `/admin.html`: Anmelden mit Datenbankkonto, verpflichtender Passwortwechsel bei Erstnutzung.
- Organisation: Name, Sprache, Zeitzone, Design-Akzent zentral in PostgreSQL.
- Addons: pro Addon eine versionierungsunabhängige, nicht geheime JSON-Konfiguration in `sibyl_addon_settings`. Zugangsdaten werden nicht in diesem Feld gespeichert.
- GitHub Addon-Browser: serverseitige Prüfung veröffentlichter Releases und Pre-Releases, geschützter Download mit Digest/Manifest-Prüfung. **Download ist noch keine Aktivierung**.
- Sicherheit: BCrypt-Passwort-Hashing, Admin-Berechtigung, CSRF-Prüfung, erstes Passwort zwingend ändern und Audit-Log.
- Nicht fertig: Mehrbenutzer-RBAC, verschlüsselter Secret-Store, echtes Plugin-Laden/Aktivieren, automatische Formulare für Addon-Schemata.

Die Datenbankmigrationsdateien liegen in `backend/src/main/resources/db/migration/` (Flyway). Die Datenbank besitzt Benutzer, Organisation, Addon-Settings und Audit-Ereignisse.

## Architektur und Addons

Sibyl Core (Java 21/Spring Boot 3.5.x), PostgreSQL, unabhängige optionale Addons und firmeneigene Theme-/Konfigurationswerte. Ein Active Directory ist **nicht erforderlich**: AD wird über `Sibyl.ad` als optionaler Verzeichnisprovider angebunden.

Bekannte Repositories: `Sibyl.ad`, `Sibyl.inventory`, `Sibyl.planning`, `Sibyl.doc`. Weitere optionale Addons sind geplant (Suche, Network/IPAM, Berichtsheft, Kurse). Alle Addons besitzen eigene IDs und Versionen, eine Sibyl-Installation führt pro Modul-ID höchstens eine Version.

Der **Sibyl-Server** fragt die öffentliche GitHub Releases API ohne Token ab und lädt veröffentlichte ZIP-Assets direkt von GitHub. Keine ZIP-Downloads aus `main`, `test` oder Entwicklerverzeichnissen. Für private Addon-Repositories wäre ein separat konfigurierter, serverseitiger GitHub-Zugang erforderlich. Siehe [docs/ADDON-GITHUB-RELEASE-FLOW.md](docs/ADDON-GITHUB-RELEASE-FLOW.md) und [docs/RELEASE-POLICY.md](docs/RELEASE-POLICY.md).

Das Grunddesign bleibt an `design/legacy-reference.css` angelehnt. Die Marke ist unabhängig vom Betreiber, die Oberfläche wird mit Organisationseinstellungen angepasst. Eine vollständige optische 1:1-Parität mit dem früheren System steht noch aus.

## Entwicklung und Tests

```bash
cd backend
mvn clean verify
```

Die Tests benötigen eine PostgreSQL-Datenbank, z. B. den Postgres-16-Dienst des GitHub-CI-Workflows `.github/workflows/db-ci.yml`. Dort werden unter anderem Flyway-Migration, `admin`/`friend`-Bootstrap, Passwortwechselpflicht, Einstellungen und Sicherheitsendpunkte geprüft.

**Lab-Deployment:** ausschließlich Proxmox Cluster 3 mit Edge `172.22.120.240`, App `172.22.120.241`, Datenbank-VM `172.22.120.242`. Die derzeitige App `.241` läuft noch mit dem früheren Core-Release `v0.1.0-rc.1`, und auf der separaten Datenbank-VM wurde PostgreSQL noch nicht ausgerollt. Die neue Standardinstallation ist für **einen Host**; die bestehende Drei-VM-Laborumgebung benötigt eine gesonderte, kontrollierte Migration und Rückfallstrategie.

**Produktion (Cluster 1) und bestehendes Testcluster (Cluster 2) bleiben unangetastet.**
