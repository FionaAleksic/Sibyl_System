# Sibyl System – Berichtsheft 0.10.0

**Stand 16.11.2026.** Versionssicherung der direkt deployten Änderungen. **GitHub ist ausschließlich Backup und Versionierung, kein Installationsweg.**

## Ergebnis

- Nur aktive Benutzer werden in der neuen Ausbilderverwaltung geladen; Checkbox „Aktiv“ entfällt.
- „Quelle“ wurde entfernt. Für Azubis erscheint ein bearbeitbares **Lehrjahr (1–4)**, für reine Ausbilder „—“.
- Nicht gepflegte Lehrjahre erscheinen als „—“. Es wurde im AD kein verlässlich gepflegtes Lehrjahrattribut gefunden; keine Schätzung.
- Zusätzlich wird die **AD-Gruppe (FIS/FIA)** aus dem Verzeichnis synchronisiert. Mehrfachgruppen sind als Mitgliedschaften in einer neuen DB-Tabelle modelliert.
- Der Suchfilter aktualisiert sich sofort bei Eingaben; separate Filter für LJ und Gruppe.
- Pro Azubi können mehrere Ausbilder hinzugefügt und einzeln wieder entfernt werden.
- **Sammelzuweisung** nach Lehrjahr, AD-Gruppe oder beiden Kriterien; mehrere Ausbilder auswählbar; idempotente additive Zuweisungen ohne Ersetzen vorhandener Ausbilder.
- Lehrjahr kann pro Azubi oder gruppenweise eingetragen werden.
- Backend-Rechteprüfung berücksichtigt alle zugewiesenen Ausbilder. Nicht zugewiesene oder entfernte Ausbilder erhalten keinen Zugriff auf vertrauliche Berichte.
- Sicherheitsvorkehrungen: nur Administratoren dürfen verwalten, serverseitige Filtervalidierung, kein ungefilterter Bulk-Eingriff, keine Selbstprüfung, Transaktionen und Audit-Einträge.

## Datenbank

Migration `V11__reportbook_bulk_trainers_and_year.sql`:
- `sibyl_report_profiles.learning_year` (optional 1–4).
- Primärschlüssel `sibyl_report_assignments(trainee_username, trainer_username)` statt nur `trainee_username`.
- Neue Tabelle `sibyl_report_profile_groups` mit Mitgliedschaften und Herkunft AD/MANUAL.

Die bestehenden Flyway-Migrationen V1–V10 wurden vor dem Deployment mit der laufenden JAR **bytegenau verglichen**, keine veränderten Checksums. Es wurden keine bestehenden Backend-Klassen gelöscht. Die einzige neue Migration ist V11. **Achtung beim späteren Sibyl.ad-Refactor:** Dessen noch nicht deployte Migration muss vor einem künftigen Rollout auf eine neue Versionsnummer über V11 gehoben und neu getestet werden; die vorhandene DB-Migration V11 darf nicht überschrieben werden.

## Build, Test, Live-Deployment

- Quelle: separate V10-kompatible Entwicklungs-Checkout-Kopie auf `172.22.100.118:/home/fiona/sibyl-reportbook-bulk-v10-20261116`.
- Maven / Java 21: Build und bestehende Unit-Tests erfolgreich.
- TypeScript / Vite: Build erfolgreich.
- Isolierte MySQL-8.4-Integration: bestehende Berichtsheft-Abläufe und neue Tests für Filterfälle, Mehrfachausbilder, Bulk nach LJ/Gruppe/beiden, Wiederholung, Rechteprüfung, Inaktive, ungültige Lehrjahre sowie Migration erfolgreich.
- Direktes SCP/SSH-Deployment **ohne GitHub** auf `172.22.120.244` (Backend) und `172.22.120.250` (Frontend).
- Backend-Flyway V11 erfolgreich angewendet; Spring Boot und Tomcat HTTPS 8081 gestartet.
- Live mit authentifizierter Admin-API geprüft: 112 aktive Profile, AD-Sync 91 Azubis und 22 Ausbilder, Gruppenzugehörigkeiten FIA/FIS in neuer Tabelle.
- Legacy-Roster-API weiter kompatibel. Vor dem Rollout existierten null Ausbilderzuweisungen; nach Rollout und AD-Sync weiterhin null, es wurden keine ungewollt erzeugt.
- Frontend-HTML referenziert neues JS `index-Bew7k4DL.js`; CSS `index-opYD-c2s.css` wird ausgeliefert.
- Kein Servicekonto-Passwort verändert oder exportiert.

## Backup und Rollback

Vor Deployment erstellt:
- Backend `172.22.120.244:/var/backups/sibyl/20261116-reportbook-multi-trainer/`: MySQL-Dump, alte JAR, Environment-Datei und systemd-Konfiguration.
- Frontend `172.22.120.250:/var/backups/sibyl/20261116-reportbook-multi-trainer/`: vorherige HTML/JS/CSS-Dateien.
- Zusätzliche installierte Vorgängerversionen: `/opt/sibyl/backend/sibyl-core.pre-reportbook-multi-20261116.jar` und `/srv/sibyl-ui.pre-multi-trainer-20261116`.

Bei einem vollständigen Rollback auf V10 muss **auch die vorherige Datenbank** wiederhergestellt werden, weil Flyway V11 das Schema verändert hat. Kein blindes Zurückkopieren der alten JAR gegen das neue DB-Schema.

## Offene fachliche Entscheidung

Das Lehrjahr lässt sich im AD für die vorhandenen FIA-Konten nicht zuverlässig aus einem einheitlichen Attribut ablesen. Es wird daher bewusst leer gelassen, bis es in Sibyl einzeln oder gruppenweise konfiguriert wird. Eine spätere automatische Erkennung aus einer verbindlichen Stammdatenquelle kann nachgerüstet werden.
