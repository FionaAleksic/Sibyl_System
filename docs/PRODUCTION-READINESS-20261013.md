# Sibyl System – Betriebs- und Freigabeprüfung (13.10.2026)

> **Status: Funktionale Test-/Pilotumgebung. Noch keine uneingeschränkte Produktionsfreigabe.**
> Keine Addons wurden installiert, geladen oder aktiviert.

## Einsatzumfang

- Rocky Linux 10.2 auf zwei neuen VMs für Frontend und Backend.
- React-/Nginx-Frontend über HTTPS; Java-21-Core mit MySQL 8.4.
- HTTP-API und geschützte native Core-Seiten für Administration und Addon-Browser.
- Netzwerkfreigabe auf zwei explizit bekannte interne /24-Netze; keine pauschale DMZ-/Internetfreigabe.
- Veröffentlichte GitHub-Release-Metadaten aus fünf eigenständigen Addon-Repositories, ohne deren Pakete einzuspielen.

## Technisch nachgewiesen

| Prüfpunkte | Ergebnis |
| --- | --- |
| Rocky Linux 10.2 beider VMs, statisches Networking | OK |
| Nginx, firewalld, sshd, MySQL, Java/Core aktiv | OK |
| Autostart von Nginx, firewalld, SSH, MySQL und Core | OK |
| Core-Datenbank, Flyway-Schema Version 4 | OK |
| Admin-Konto vorhanden, Erstpasswortwechsel nicht mehr ausstehend | OK; keine Zugangsdaten gelesen |
| Geheimkonfiguration /etc/sibyl/core.env und mysql-root.cnf root:root 0600 | OK |
| Frontend-Startseite, Login, CSS, JS, healthz | HTTP 200 |
| Geschützte Admin-/Addon-HTML-Seiten ohne Session | HTTP 302 (Loginredirect) |
| Geschützte API ohne Session | HTTP 401 |
| Core-Assets (sibyl.css, sibyl.js, module.js, addon-browser.js) ohne Session | HTTP 302 nach Nginx-Korrektur |
| Backend direkt vom Frontend erreichbar | /actuator/health zuvor UP |
| Backend-Firewall Port 8080 nur für das Frontend | OK |
| Datenbank: Addon-Installationen | **0** |
| Datenbank: Addon-Abhängigkeiten | **0** |
| Core-Datenverzeichnis: Addon-Paketdateien | **0** |
| GitHub-Core CI: Frontend-Build, Backend-Tests, Rocky-Syntax | Erfolgreich für geprüften Commit `6f5d3cc57e52c989ccbd7f1d15248a2468f77d46` |
| Alle fünf Addon-Repositories und jeweilige Vorab-Release-Metadaten | Über GitHub-API bestätigt |
| Login/Admin-Addon-Browser *mit echter bestehender Admin-Sitzung* | **Noch nicht live E2E getestet** |

## In Betrieb verwendete Versionen

- Backend/Core: veröffentlichter unveränderter Release `v0.1.0-rc.16`; Java 21.
- Frontend: Git-Commit `7ec2529cdcee75142ccd595aff86cd77e3049fd6` mit neuem minimalistischem kantigem Design, Navigation zu Addon-Browser/Admin und Kompatibilität zum bereits veröffentlichten Core.
- Backend/Frontend werden unabhängig bereitgestellt: Neuere Backend-Codeänderungen im Featurebranch sind **noch nicht** als neuer Core-Release auf dem Server.
- Der vorige React-Build bleibt im separaten Releaseverzeichnis erhalten; Deployment per atomarem Symlink und Rollback.
- Nginx: die zuvor fehlenden geschützten Core-Stylesheets und JavaScript-Dateien werden jetzt serverseitig zum Core weitergeleitet. Login-/API-Autorisierung verbleibt im Spring Security Backend.

## Addon-Browser

- Für Admins erreichbar unter `/addons.html`; unautorisierte Besucher erhalten einen Loginredirect.
- Der Browser fragt GitHub über die Core-API ab. Die veröffentlichten Erweiterungen sind *Vorabversionen*. Der Standardkanal `stable` zeigt ohne Stable Releases keine installierbaren Pakete; durch Auswahl `prerelease` lassen sich vorhandene Vorabversionen auflisten.
- Addon-Repositories: `Sibyl.ad`, `Sibyl.inventory`, `Sibyl.planning`, `Sibyl.doc`, `Sibyl.report-book`.
- Quellcode `main`/`test` darf nicht mit installierbaren Release-Assets verwechselt werden.
- Die SHA-256-Digests dienen der Integritätsprüfung; sie sind **keine kryptographischen Signaturen**.
- Per Nutzeranweisung wurden **keine Addons heruntergeladen oder installiert**.

## GitHub-Änderungen

- `Sibyl_System`: Featurebranch `feature/deck-inspired-v2` (React-Navigation, Core-Katalog-Response inklusive `repo`, Nginx-Core-Assets, Installerfixes, Deploy- und UI-Rollbackskripte, Designrichtlinie). Pull Request #1 zur Code-Review offen.
- Fünf Addon-Repositories: jeweils **nur im `test`-Branch** `docs/CORE-COMPATIBILITY.md` und README-Releasehinweis aktualisiert. `main`, vorhandene Tags und GitHub-Release-Assets unverändert.
- Die neue Core-API-Katalog-Eigenschaft `repo` ist bisher nur Quellcode: **nicht** in der aktuell laufenden Core-Version rc.16.
- Release-Freigabe bzw. Zusammenführung erst nach Test- und Sicherheitsabnahme.

## Offene Produktionstore – zwingend

### 1. Konsistente externe Backups + Restore-Test (**nicht bestätigt**)

Ein Remote-Schreibzugriff zum Erzeugen eines MySQL-Backups wurde durch die Fernzugriffs-Sicherheitsprüfung blockiert. Es liegt **kein durch diesen Durchlauf bestätigtes Datenbank-Backup** vor. Nicht durch einen alternativen Verwaltungsweg umgehen.

Anforderung: MySQL-Sicherung mit `--single-transaction` + Routinen/Trigger/Events, verschlüsselte Kopie **außerhalb** der Backend-VM, automatisierte Aufbewahrung/Rotation, definierte RPO/RTO, regelmäßiger Restore-Test in einer isolierten Staging-DB sowie VM-Level-Snapshot/Backup für Disaster Recovery. Ein Backup nur auf derselben VM ist kein ausreichender Schutz.

### 2. Vertrauenswürdiges HTTPS-Zertifikat (**nicht erfüllt**)

Aktuell: selbstsigniertes Laborzertifikat mit Laufzeit **13.10.2026–12.11.2026**; SAN enthält bisher die Labor-IP. Für langfristigen Betrieb: interne DNS-Domain offiziell vergeben und passenden Zertifikatsnamen festlegen; Zertifikat von einer auf allen Clients **vertrauenswürdigen Unternehmens-CA** (oder anderer vertrauenswürdiger CA) beziehen; vor Ablauf rotieren; Kette, SAN und Client-Vertrauen prüfen. Keine CA-Zertifikate/Privatschlüssel ins Git-Repository schreiben.

### 3. Echte Admin- und Browser-Abnahme (**nicht bestätigt**)

Mit dem **bereits vorhandenen, durch den Betreiber geänderten** Adminpasswort anmelden. Login, erneute Anmeldung, Cookie-Schutz, Logout, Startseite, Administration, Addon-Browser mit den Kanälen `stable` und `prerelease`, Suche und GitHub-Fehlerzustände prüfen. Keine Installations-/Download-Buttons betätigen. Ohne einen authentifizierten Test kann nicht behauptet werden, dass die gesamte Interaktion erfolgreich abgenommen wurde.

### 4. Upgrade- und Disaster-Recovery-Verfahren

Core-Release rc.16 ist eine Vorabversion. Vor einer stabilen Produktionsfreigabe: Release-Branchreview, alle CI-Tests, schema-kompatible Datenmigration, Backup/Restore, Staging-E2E, Notfallrollback, Wartungsfenster und Paket-SBOM/Abhängigkeitskontrolle. Derzeit neue Backend-Quelle **nicht** auf produktiver Datenbank aktualisiert.

### 5. Netzgrenze / DMZ

HTTPS ist auf die bekannten internen /24-Netze eingeschränkt. Prüfen, ob die DMZ über eigene Quellnetze verfügt; falls sie Quell-IP-Bereiche **innerhalb** dieser internen Netze verwendet, müssen zusätzliche Router-/Firewall-ACLs ergänzt werden. Keine Public-Internet-Freigabe.

## Entscheidung

**Technisch nutzbarer Pilotbetrieb: JA. Uneingeschränkter Produktivbetrieb: NO-GO**, bis Backup/Restore, Zertifikat und echter authentifizierter E2E-Test positiv bestätigt und dokumentiert sind.

Es wurden bei dieser Bearbeitung keine AD-Verzeichnisobjekte verändert, keine Addon-Pakete angefasst und keine vorhandenen produktiven Altserver umgestellt.
