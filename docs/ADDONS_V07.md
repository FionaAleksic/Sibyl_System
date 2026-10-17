# Addon-Integration v0.7.0 – Test

Datum: 2026-10-17

## Zielstand

- `Sibyl.doc`: `documents`-Provider, gemeinsam genutzte Wiki-/Dokumentenseiten. Getrennte `maxBodyLength`-Konfiguration; Schreiben nur für Administratoren.
- `Sibyl.planning`: `planning`-Provider, Teilnehmerverwaltung und drei Rotationen. Rotationen werden unabhängig nach bisheriger Zuweisungszahl und dem ältesten letzten Einsatz gewählt. Bei deaktivierter Mehrfachbelegung dieselbe Person höchstens einmal pro Tag. Vorhandene Slots werden nicht überschrieben.
- `Sibyl.report-book`: `report-book`-Provider. Benutzer besitzen ihre Ausbildungsnachweise; Einreichen, Rückgabe, Freigabe durch Administratoren. Keine Selbstfreigabe.

## DB-Migration

`backend/src/main/resources/db/migration/V7__planning_wiki_reportbook.sql`: Registriert die drei Provider deaktiviert und erstellt `sibyl_planning_participants`, `sibyl_planning_slots`, `sibyl_report_entries`. Die FK-Abhängigkeiten `Sibyl.planning` → `Sibyl.ad`, `Sibyl.report-book` → `Sibyl.ad` werden serverseitig erzwungen. Auf Datenbank- und Jar-Backups achten. Flyway-Migrationen sind nicht automatisch rückrollbar.

## Admin-Einstellungen

| Addon | Einstellungen |
|---|---|
| Sibyl.ad | Bestehende Multi-AD-Profile mit SRV/LDAPS, Servicekonto und Gruppen |
| Sibyl.inventory | refreshMinutes, includeLocal, includeKnown |
| Sibyl.doc | maxBodyLength |
| Sibyl.planning | defaultDays, allowSameDayDoubleBooking, includeWeekends |
| Sibyl.report-book | minMinutes, maxMinutes, allowFutureEntries |

Die Backendvalidierung verhindert unbekannte Konfigurationsfelder; nur Administratoren dürfen `/api/v1/admin/**` aufrufen. Das React-Frontend zeigt settings automatisch an.

## Quellcode

Der kompiliert vorliegende Core-Entwicklungsstand ist auf der internen Arbeitsmaschine unter `/home/fiona/sibyl-replatform`. In diesem Repo ist bislang nur der neue v0.7.0-Integrationsausschnitt gespiegelt. Vollständige Source-Synchronisation und ein reproduzierbarer Installer sind offen; die Addon-Repos enthalten versionierte Provider und React-Seiten auf `test`.

## Verifikation

- TypeScript `tsc -b` und Vite Produktionsbuild: bestanden.
- Maven `test package` (Java 21, Spring Boot): bestanden.
- Isolierter Smoke-Test auf MySQL 8.4 bestanden: Spring-Backend startet, Flyway V7 registriert drei Addons, beide Abhängigkeiten und alle drei Tabellen sind vorhanden; anonyme API-Zugriffe wurden mit HTTP 401 verweigert.
- Authentifizierte CRUD-/RBAC-Tests wurden zusätzlich angestoßen; ihr Ergebnis konnte wegen des ausgeschöpften Remote-Werkzeugkontingents nicht abschließend ausgelesen werden. Sie gelten daher nicht als bestanden.
- Testdeploy ist ausstehend: SSH-Zugang zu den zugehörigen Sibyl-VMs fehlgeschlagen; Proxmox QEMU Guest Agent verweigert `guest-exec`. Keine Sicherheitsrichtlinien umgangen.

## Live-Freigabe-Kriterien

1. Vorherige Version, MySQL-Daten und `/etc/sibyl/backend.env` sicher sichern; Verschlüsselungsschlüssel erhalten.
2. Flyway V7 auf Klon testen, danach gesicherte Backend-JAR und Frontend-Assets einspielen.
3. Testen: `/actuator/health`, Login, AD-Login, Settings lesen/schreiben, Wiki-CRUD, Planung mit drei Typen, Einreichung und Review, RBAC/403.
4. Addons erst nach erfolgreichem Test über Admin → Addons aktivieren.
5. Im Fehlerfall Binärdateien und Datenbank gemeinsam aus dem Backup rückrollen; kein unkontrolliertes `DROP` auf produktiver DB.
