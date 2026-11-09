# Sibyl – Berichtsheft: AD-Synchronisierung meldet nur „Conflict“

**09.11.2026 · Backup/Versionierung, KEINE Deployment-Quelle**

## Reproduzierter Fehler
Auf `172.22.120.250` erzeugte `POST /api/v1/addons/Sibyl.report-book/v2/admin/sync` die HTTP-Antwort `409 Conflict` ohne fachliche Begründung. Die Gruppen `FIS`, `FIA`, `MA` waren bereits hinterlegt, aber die Admin-API lieferte **0 eingerichtete AD-Verbindungen**. Ohne AD-Verbindung mit aktiviertem, leseberechtigtem Servicekonto kann die Gruppenmitgliedschaft nicht synchronisiert werden.

## Behoben
- `ApiExceptionHandler.java`: liefert den bewusst formulierten Grund von `ResponseStatusException` im JSON-Feld `error` zurück, ohne Stacktrace, Passwort oder LDAP-Details auszugeben.
- `ReportBookAdminSettings.tsx`: prüft beim Laden die konfigurierten AD-Verbindungen. Wenn keine aktive Verbindung mit Servicekonto existiert, erscheint ein deutschsprachiger Hinweis. Der Synchronisieren-Button bleibt deaktiviert; „Active Directory konfigurieren“ führt direkt zum AD-Addon.
- `AdminAddonsPage.tsx`: übergibt den Konfigurations-Navigationscallback und den Status der gespeicherten Gruppen an die Berichtsheft-Komponente.

## Verifikation und Live-Deployment
- Spring Boot / Maven Build und 7 bestehende JUnit-Tests erfolgreich.
- Isolierter MySQL-8.4-Integrationstest inkl. fehlendem Servicekonto und Berichtsheft-Workflow erfolgreich.
- Backend auf `172.22.120.244` direkt vom Entwicklungsserver `172.22.100.118` bereitgestellt. Status: UP; JAR SHA-256 `85fce7cbbf36892cc1d362eb7df6f4be5f91f0ee28aa257d3ea3bd701d67deae`.
- Frontend auf `172.22.120.250` direkt bereitgestellt; `index-iEV9FKfV.js` per HTTP 200 abrufbar.
- Die Live-API liefert bei fehlender AD-Konfiguration weiterhin HTTP 409 (korrekter Status), nun aber mit `{"error":"No active directory profile with a read-only service account"}` statt nur `Conflict`.
- Die bisherigen Backend-/Frontend-Builds wurden für Rollback unter `/var/backups/sibyl/20261109-ad-sync-error-fix` gesichert. Keine DB-Migration und keine Änderungen an AD-Konten.

**Noch offene Voraussetzung:** Ein Administrator muss unter **Administration → Addons → Active Directory** eine aktive Domänenverbindung mit leseberechtigtem Servicekonto hinterlegen und erfolgreich testen. Erst danach können Azubi- und Ausbilderkonten aus dem AD synchronisiert werden. Bestehende Benutzer- und Berichtsheftdaten bleiben unangetastet.
