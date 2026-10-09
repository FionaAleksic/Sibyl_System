# Sibyl System

Firmenneutrale modulare Webplattform. Dieses Repository ist **die Standardinstallation / Core**. Addons haben eigene Repositories und veröffentlichen unabhängig versionierte GitHub-Releases.

> **Entwicklungsstand (09.10.2026):** Architekturprototyp, nicht produktiv freigegeben. Der Branch `test` enthält eine erste Core-API und die im Legacy-Design gestaltete Addon-Browser-Oberfläche. Backend-Build, Authentifizierung, Addon-Aktivierung, DB-Migrationen und End-to-End-Tests sind noch zu vervollständigen. Der Core wurde noch **nicht** aus einem veröffentlichten Release auf eine VM deployt.

## Installation / Release-Policy

- Nur Assets eines **veröffentlichten GitHub-Releases** installieren, niemals den Inhalt von `main` oder `test` direkt.
- `main`: stabile Releases `v1.0.0`, `test`: Pre-Releases `v0.1.0-rc.1`.
- Manuelle GitHub-Actions-Ausführung `Build & Publish Sibyl Core Release` erstellt nur nach erfolgreichem Maven-/Frontend-Test das Paket und den Release. Ein GitHub Actions Run ist nötig; das Vorhandensein der YAML-Datei bedeutet nicht, dass bereits ein Release existiert.
- Im Lab fehlt bislang ein Release; deshalb steht dort noch keine laufende neue WebApp.

## Ausbilderfunktionen / Ziele

Suche lokal -> optional Web; vollautomatische Inventarisierung; Network Mapping/IPAM; IHK-ähnliches Berichtsheft; Kurssystem; optionaler AD-Provider. Das alles baut auf einer eigenen zentralen **Sibyl-Datenbank und Core API** auf. Sibyl funktioniert auch ohne AD. Die finale WebApp benötigt einen tatsächlich getrennten **DMZ-Server**. Auf dem derzeit unsegmentierten Cluster-3-LAN ist nur ein Entwicklungs-Deployment zulässig, kein DMZ-Abnahmenachweis.

## Addon-Browser

Das Standardfrontend unter `frontend/` hat einen integrierten **Addon-Browser**. Sein serverseitiges Backend:
- listet nur geprüfte GitHub-Repositories in `catalog/default.json`;
- liest den neuesten stabilen Release oder neuesten passenden veröffentlichten Pre-Release;
- fällt **niemals auf einen Git-Branch** zurück;
- fordert für private Repositories einen nur serverseitig gespeicherten `SIBYL_GITHUB_TOKEN` an;
- validiert Release-Tag, ZIP-Dateigröße, offizielle SHA-256-Digest-Angabe, ZIP-Einträge und `module.json`;
- speichert das verifizierte Release als **downloaded_not_activated**; fremden Backendcode automatisch auszuführen ist bewusst nicht implementiert.

Aktuell deklarierte Repos: `Sibyl.ad`, `Sibyl.inventory`, `Sibyl.planning`, `Sibyl.doc`. Weitere benötigte Repositories für `Sibyl.search`, `Sibyl.network`, `Sibyl.training-log` und `Sibyl.courses` müssen noch angelegt werden.

## Visuelles Design

Das historische CSS ist unverändert unter `design/legacy-reference.css` aufbewahrt. Das neue `frontend/styles.css` verwendet das Originalfarbschema, Darkmode, Topbar, Segoe UI und eckige Bedienelemente. Eine vollständige optische 1:1-Abnahme aller früheren Seiten steht noch aus.

## Basis-Entwicklung

- Java 21, Spring Boot 3.5.x (Backend), Webfrontend für frühen Browser-Prototyp.
- Für lokale Versuche muss `SIBYL_BOOTSTRAP_ADMIN_USERNAME` und `SIBYL_BOOTSTRAP_ADMIN_BCRYPT` (gesicherter BCrypt-Hash) gesetzt sein. Ohne beide verweigert die API bewusst den Start.
- `SIBYL_GITHUB_TOKEN` ist ausschließlich im Backend zur Abfrage privater Releases erlaubt.
- `SIBYL_ADDON_DATA` konfiguriert einen geschützten lokalen Addon-Staging-Pfad.
- Core API: `GET /api/v1/admin/addons/catalog?channel=stable|prerelease`, `GET /api/v1/admin/csrf`, `POST /api/v1/admin/addons/{id}/download`.
- Siehe `docs/RELEASE-POLICY.md`.

## Cluster-3-Labor

Vorbereitet auf dem Proxmox-Spielwiesencluster `ThrusterCluster`: Ubuntu-Vorlage 9500, erste VM 200 `sibyl-edge-lab`. Weitere VMs und Release-Deployment sind noch offen. Dokumentation auf Verwaltungsserver .118 in `/home/fiona/Documentation-Sibyl/`.

**Produktion (Cluster 1) und Test (Cluster 2) werden nicht verändert.**
