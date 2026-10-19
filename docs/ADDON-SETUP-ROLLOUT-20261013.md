# Sibyl – Addon-Einrichtung und automatische Installation (13.10.2026)

## Stand

- **Core live:** VM208 `172.22.120.248`, Release `v0.1.0-rc.17-dev.1` (GitHub Pre-Release; keine Stable-Freigabe). Vorgänger `rc.16` unter `/opt/sibyl/` erhalten.
- **Frontend live:** VM207 `172.22.120.247`; React-Build weiterhin `7ec2529cdcee75142ccd595aff86cd77e3049fd6`. Neuer Build `60ddfb00e9d88a4b4efddd0d7ef80ec11e5817c2` ist auf Manager `.118` erstellt, als ZIP `/home/fiona/sibyl-frontend-60ddfb0.zip` auf VM207 übertragen, aber **nicht aktiviert**: die Remote-Sicherheitsprüfung hat den Aktivierungsaufruf blockiert. Nicht umgehen.
- **Nginx live:** Weiterleitung der Core-Stylesheets/Skripte sowie `/extensions/**`-Addon-Assets nach `.248:8080` eingerichtet; Syntaxprüfung und geschützter HTTP-302-Test bestanden.
- **Addonpakete:** Vier unveränderte Release-ZIPs unter `/var/lib/sibyl/addons`: `Sibyl.ad/v0.1.0-rc.4.zip`, `Sibyl.inventory/v0.1.0-rc.4.zip`, `Sibyl.doc/v0.1.0-rc.3.zip`, `Sibyl.report-book/v0.1.0-rc.4.zip`; Planung noch nicht heruntergeladen.
- **Aktive Addons:** Null. `sibyl_addon_installs`, `sibyl_addon_settings` und `sibyl_addon_install_dependencies` zeigten jeweils **0 Datensätze** nach dem Core-Upgrade.
- **Worker-Voraussetzung:** `bubblewrap-0.10.0-3.el10.x86_64` erfolgreich aus Rocky BaseOS auf Backend installiert. Der Versuch eines vollständigen isolierten Runtime-Tests wurde durch die Fernzugriffs-Sicherheitsprüfung blockiert. Worker-Funktion daher nicht als end-to-end nachgewiesen markieren.
- **Datenbank:** MySQL 8.4, Flyway-Version 4 unverändert. Das Core-Upgrade vergleicht die migrierten Flyway-Dateien im alten und neuen JAR bytegenau und lehnt Schemaabweichungen ab.
- **Health:** Backend `/actuator/health` = `UP`. Webseite `/` = 200; `/addons.html` ohne Session = 302; `/api/v1/admin/addon-settings/Sibyl.ad` ohne Session = 401; `/api/v1/auth/csrf` = 200.

## Neue generische Funktionen

1. **Ein Klick statt zwei:** Neue native Addon-Browser-JS (`frontend/addons.js`) bietet `Herunterladen & installieren`. Auf eine bestätigte Admin-Aktion folgen Backend-Download, Release-Checksum/Manifest-Verifikation, Planabruf, Abhängigkeitsbehandlung, Schema-Setup und Core-Install/Registrierung. Zuvor heruntergeladene Pakete haben `Jetzt installieren`; das bloße Anzeigen der Seite führt **keine** Installation aus.
2. **Eindeutige Versionen:** Die vorhandene Core-Installerprüfung verhindert mehrere aktive Versionen einer Addon-ID und verweigert fehlende Abhängigkeiten oder unbekannte Deploy-Operationen.
3. **Generische Einstellungen:** `AddonConfigTemplates` liest `config.schema.json` aus einem lokal heruntergeladenen Paket (max. 64 KiB), zeigt `properties`, `required`, `defaults` und valide Felder an. Leere Felder und Beispiele werden nicht automatisch als gültige Credentials übernommen.
4. **Geführter Setupdialog:** Vor Aktivierung werden fehlende Pflichtwerte in einem kantigen Dialog abgefragt; Standardwerte werden vorbelegt; Zahlen/Boolean/Enum/Array werden typgerecht dargestellt; Core validiert serverseitig Typen, Bereiche, Pflichtfelder und erlaubte Schlüssel.
5. **Admin-Einstellungen:** `admin.html` zeigt Beschreibungen und Standardwerte neben der weiterhin verfügbaren JSON-Expertenansicht.
6. **Keine geheimen Daten im Frontend:** Passwörter/Tokens bleiben aus dem Config-JSON ausgeschlossen und müssen über einen gesicherten serverseitigen Mechanismus eingerichtet werden.
7. **Gleiches Addon-Design:** `test`-Branches aller fünf Addon-Repositories enthalten jetzt Schema-Titel und Beispielwerte für zukünftige Releases. Die **bereits veröffentlichten** ZIPs enthalten weiterhin den ursprünglichen Schema-Stand; diese bleiben unveränderlich.

## Nachgewiesene Tests

- GitHub Core-Kandidat `v0.1.0-rc.17-dev.1` aus Commit `20976d0e08d5c9bc4cbabdd1b868bbe08cb2c3c1` wurde durch GitHub Actions nach Backend-/MySQL- und JS-Tests gebaut und als unveränderliches Pre-Release veröffentlicht.
- Release-Asset-JAR SHA-256: `ed97faae8f4f124775ff5c1dacee6a392c4c6fb1ca0b8c333c13164e3d8d63`.
- Atomares Backend-Deployment mit unveränderter Datenbank und Rollback-Option, HTTP-Health `UP`.
- Die generische Addon-Navigation im neuen React-Build ist von `/api/v1/modules` (alter RC1-Kompatibilitätsendpunkt) auf `/api/v1/extensions` (aktive Runtime) umgestellt und **lokal TypeScript-/Vite-geprüft**, jedoch **noch nicht live**.
- Backend-Worker-Start und ein vollständig authentifizierter Admin-E2E-Installationsablauf wurden noch **nicht** abschließend nachgewiesen; ohne bestehende Admin-Credentials keine automatische Service-Account-Umgehung.

## Offene Arbeiten

- Neues React-Release nach Fernzugriffsfreigabe auf VM207 aktivieren; derzeitige neue Datei `/home/fiona/sibyl-frontend-60ddfb0.zip`, SHA-256 `9c20759c28c7cd1edd53f97775c554a5ba4fe1b13f5421f0cf73727d982d4fd8`; vorherige React-Version für Rollback erhalten. Die blockierte Operation nicht durch andere Remote-Wege umgehen.
- Admin-E2E-Prüfung der bereits geladenen Addons mit echten Setupwerten; insbesondere AD benötigt gültigen LDAPS-Server, Bind-DN und getrennt hinterlegtes Kennwort. Keine Beispielwerte für echte AD-Daten verwenden.
- Das Report-Book-Java-Worker-Startverhalten unter `bubblewrap` auf dem Betriebssystem testen; bisher nur Paketinstallation bestätigt.
- Externes Datenbankbackup und Restore-Test sowie internes vertrauenswürdiges TLS-Zertifikat bleiben offene Voraussetzungen der regulären Produktionsfreigabe.

**Wichtig:** Pre-Release und ein grüner Unit-/CI-Test sind nicht gleichbedeutend mit einem vollständig produktiv abgenommenen Addon-Ökosystem.
