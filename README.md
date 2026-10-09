# Sibyl System

Firmenneutrale, selbst gehostete modulare Plattform mit eigener **MySQL-8-Datenbank**, Administrationsoberfläche und einem GitHub-Release-basierten Addon-Browser.

## Standardinstallation

**MySQL ist die verbindliche Datenbank.** Die Standardinstallation für einen frischen Ubuntu-24.04-Server installiert **MySQL, Java 21 und Sibyl Core** gemeinsam. Die Datenbank `sibyl` wird mit einer eigenen eingeschränkten DB-Rolle und zufälligem Passwort eingerichtet. Flyway migriert das Schema bei jedem Update.

**Erstanmeldung** (nur bei vollständig leerer Installation): `admin` / `friend`. Das Passwort wird nur als BCrypt-Hash gespeichert. Vor dem Zugriff auf Admin-Einstellungen muss es verpflichtend geändert werden (mindestens 12 Zeichen). Weil das Standardpasswort allgemein bekannt ist, muss das System bis zur Änderung in einem beschränkten internen Netz bleiben und über vertrauenswürdiges HTTPS erreichbar sein.

Ein Installer wird mit dem GitHub-Core-Pre-Release **`v0.1.0-rc.4`** ausgeliefert. Die Dateien werden nur aus veröffentlichten GitHub-Releases bezogen und ihre SHA-256-Digests geprüft. Eine vollständige Anleitung inklusive des MySQL-Schemas gibt es unter [docs/STANDARD-INSTALLATION.md](docs/STANDARD-INSTALLATION.md).

## Administrationsbereiche

- `/admin.html`: Login über zentrale MySQL-Benutzerdatenbank; Passwortwechsel beim ersten Anmelden.
- Organisation und Design: Name, Sprache, Zeitzone und Akzentfarbe dauerhaft in der MySQL-Datenbank statt im Browser.
- Addons: je ID versionierungsunabhängige, nicht geheime JSON-Konfigurationen in MySQL; Secret-Felder werden abgelehnt.
- GitHub Addon-Browser: der **Sibyl-Server** fragt GitHub-Releases ohne Token ab, lädt nur veröffentlichte ZIP-Pakete und verifiziert Dateigröße, SHA-256, Version und Manifest. Der Download bedeutet derzeit `downloaded_not_activated`, noch nicht die Aktivierung des Moduls.
- Audit-Ereignisse und serverseitige Admin-Zugriffskontrolle sind als Core-Fundament vorgesehen.

Mehrbenutzerverwaltung mit differenziertem RBAC, Secret-Store, Addon-Loader/Aktivierung, AD-Synchronisierung und fertige Theme-Verwaltung sind noch offen.

## Architektur

- Java 21 / Spring Boot 3.5.x
- **MySQL 8**, InnoDB, utf8mb4, JDBC und Flyway
- Neutrale Startseite + geschützter Adminbereich
- Optionale Addons in eigenen Repositories mit SemVer-Version, ID und GitHub-Releases
- `Sibyl.ad` als **optionale** Verzeichnisanbindung; keine AD-Pflicht

Die anfänglichen Addon-Repositories heißen `Sibyl.ad`, `Sibyl.inventory`, `Sibyl.planning` und `Sibyl.doc`. Nur `Sibyl.ad v0.1.0-rc.1` ist derzeit als installierbares Addon-Pre-Release veröffentlicht. Weitere Erweiterungen werden nach und nach veröffentlicht.

## Unbeaufsichtigter Installer

Der veröffentlichte GitHub-Pre-Release [v0.1.0-rc.4](https://github.com/FionaAleksic/Sibyl_System/releases/tag/v0.1.0-rc.4) enthält einen **automatischen Einzelserver-Installer** und das **Cluster-3-Installer-Bundle** `sibyl-cluster3-installer-0.1.0-rc.4.tar.gz`. Beide sind auf einen einzelnen, freigegebenen Start ausgelegt und stellen während der Installation keine Passwortfragen.

Für einen einzelnen Ubuntu-24.04-Server werden MySQL-8-Datenbank, eingeschränkte SQL-Zugangsdaten, der Core aus GitHub, systemd und die Flyway-Migrationen automatisch eingerichtet. Im Clusterbetrieb werden die MySQL-Zugangsdaten einmal maschinell erzeugt, über einen **vorher autorisierten** SSH-Kanal provisioniert und anschließend der Core mit automatischem Rollback umgeschaltet. Auf Ablehnung durch die Remote-Sicherheitsrichtlinie bricht der Controller ausdrücklich ab; er besitzt keinen alternativen unsicheren Transferweg.

Das einmalige Passwort `admin` / `friend` ist nur für die Erstanmeldung zulässig und muss sofort geändert werden. Ein produktiver Einsatz benötigt ein vertrauenswürdiges HTTPS-Zertifikat, Backup-/Restore-Prüfungen und einen genehmigten Secrets-Provisionierungsweg.

**Stand der Firmen-VMs:** Die neue Cluster-Variante wurde im Release gebaut und ihr Shellcode geprüft, aber **nicht** auf .241 ausgeführt, da die Remote-Sicherheitsprüfung den Secret-Transport blockiert. Der funktionierende alte Core bleibt bis zu einem autorisierten Deployment unverändert aktiv.

## GitHub-Releases und Testbranch

Der `test`-Branch enthält die aktive Entwicklung, `main` ist der stabile Branch. **Installieren ausschließlich aus veröffentlichten GitHub-Releases**, niemals aus einem Branch-ZIP oder dem ungeprüften Stand eines Branches. Releases sind unveränderlich; für den Wechsel von PostgreSQL auf MySQL ist eine **neue** Core-Version `v0.1.0-rc.4` vorgesehen, statt den alten Release `v0.1.0-rc.2` zu überschreiben.

Die CI unter `.github/workflows/db-ci.yml` testet MySQL 8 mit frischem Schema, Passwort-Hash, Startpasswortwechsel, Adminrechten und zentraler Konfigurationsspeicherung.

## Cluster-3-Labor

Nur neue Labor-VMs im Proxmox-Spielwiesencluster:

- `172.22.120.240` – HTTPS-Edge / Reverse Proxy
- `172.22.120.241` – Sibyl-App
- `172.22.120.242` – **MySQL-8-Datenbank**

MySQL auf `.242` wurde installiert, mit TLS-Pflicht, eigener Datenbank und Benutzerkonto eingerichtet sowie per Firewall ausschließlich für `.241` geöffnet. Die neue App-Version ist **noch nicht umgeschaltet**, weil die sichere Weitergabe des neuen DB-Passworts an `.241` von einer Remote-Sicherheitsprüfung blockiert wurde. Auf `.241` läuft deshalb weiterhin der frühere Sibyl-Core `v0.1.0-rc.1`. Niemals eine erfolgreiche MySQL-Anmeldung oder Core-Umschaltung behaupten, bevor sie echt geprüft wurde.

Alte produktive und bestehende Testsysteme bleiben unverändert. Das aktuelle interne LAN ist **keine echte DMZ**. Self-signed Labor-HTTPS ist für eine echte Produktion durch ein vertrautes Zertifikat zu ersetzen.

Die originale Designreferenz ist in `design/legacy-reference.css` archiviert.
