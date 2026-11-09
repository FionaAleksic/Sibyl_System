# Sibyl System – Core-Addon-Verwaltung 0.8.0

**Backup des direkt ausgerollten Entwicklungsstands vom 09.11.2026.**
Dieses GitHub-Verzeichnis ist ausschließlich eine versionierte **Quellcode-Sicherung**. **GitHub ist keine Installations- oder Deployment-Quelle.**

## Architektur

Sechs bekannte Addons sind fest im Core enthalten:
`Sibyl.ad`, `Sibyl.inventory`, `Sibyl.notes`, `Sibyl.doc`, `Sibyl.planning`, `Sibyl.report-book`.

- Der neu eingeführte `BuiltinAddonCatalog` definiert die verfügbaren IDs und aktualisiert beim Start deren Metadaten in MySQL.
- Die Datenbank speichert Aktivierungszustände und Konfigurationen. Ein Neustart oder Update überschreibt **nicht** die Werte in `enabled`.
- Nicht im Core registrierte IDs können weder aktiviert noch über die Benutzer-Addon-API genutzt werden.
- Alle Addons sind über **Administration → Addons** aktivierbar, deaktivierbar und konfigurierbar. Keine frei installierbaren Addons und kein Download aus Repositories.
- **Berichtsheft**: Azubi-/Ausbilder-Gruppen, Kontenübersicht, Ausbilder-Zuweisungen und AD-Synchronisierung ausschließlich unter Addons. Im Benutzerteil verbleiben nur Schreiben, Einreichen und Prüfen.
- **Planung**: Teilnehmende ausschließlich unter Addons → Planung verwalten. Planung selbst enthält Planerstellung und Einteilungen.
- **AD**: mehrere Verbindungen, Servicekonten, Benutzer-/Admin-Gruppen im selben Addon-Tab. Lokale Login-Konten und die allgemeinen Benutzerrollen bleiben Core-Verwaltung.
- **Inventarerkennung**: Deaktivierung wird serverseitig durchgesetzt; Konfigurationsfelder gehören zum Addon.
- Auch deaktivierte Addons behalten ihre Einstellungen und können durch Administratoren vorkonfiguriert werden.

## Quellcode-Snapshot

Die Dateien unter `backend/` und `frontend/` enthalten die **geänderten Komponenten** der laufenden Core-0.8.0-Installation. Es handelt sich nicht um einen vollständigen, eigenständig baubaren Systemexport; die unveränderten Basiskomponenten befinden sich im Entwicklungsprojekt `/home/fiona/sibyl-replatform` auf `172.22.100.118`.

## Test und Deployment

Direkt aus `172.22.100.118` mit Java 21 / Maven und TypeScript / Vite gebaut, **nicht** aus GitHub:
- Maven-Build und 7 Unit-Tests erfolgreich
- MySQL-8.4-Integrationstest: 39 Prüfschritte erfolgreich (Addon-Lebenszyklus, RBAC, Konten, Berichtsheft)
- Auf `172.22.120.244` liegt die Java-Anwendung, SHA-256:
  `6f1e9a116b48800751ebaa44ae87e86deb2941586442f2ad42d59844490a5ab2`
- Auf `172.22.120.250` läuft der Frontend-Build mit `index-C8miZ6fk.js`; HTTP 200 auf HTML und JS
- Nginx → Backend-Proxy: authentifizierte Admin-API der sechs Addons erfolgreich geprüft
- Die sechs Aktivierungszustände blieben nach dem Backend-Update identisch (AD, Inventar, Notizen und Berichtsheft aktiv; Wiki und Planung deaktiviert).
- Geschützte Sicherung vor dem Update auf jeder VM unter `/var/backups/sibyl/20261109-addoncore080/` einschließlich Datenbankdump, Backend-JAR, Backend-Environment-Datei und Frontend-Bundle.

**Hinweis:** Die AD-Synchronisierung über ein echtes Servicekonto wurde nicht als Teil dieser Code-Umstellung neu geprüft, weil auf dem Ziel bislang kein entsprechendes Secret konfiguriert ist.
