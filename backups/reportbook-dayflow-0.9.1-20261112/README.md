# Sibyl Berichtsheft 0.9.1 – Tagesauswahl (12.11.2026)

**Quellcode-Backup eines bereits direkt deployten Builds.** GitHub dient ausschließlich der Versionierung, **nicht** als Installations- oder Deployment-Quelle.

## Tagesablauf

1. Ein neuer Tag startet ohne vorbelegte Anwesenheit oder vorbelegten Ort.
2. Nach Auswahl von **anwesend** erscheint die Ortsauswahl mit dem Platzhalter **Ort***.
3. Nach Auswahl des Ortes (beispielsweise **Betrieb**) erscheint der Kategoriename und der breite Button **Eintrag hinzufügen ***.
4. Erst danach werden Tätigkeits-/Unterrichtseinträge (Text oder Stichpunkte) mit Zeitangaben angelegt. Bei vorhandenen Einträgen erscheint die Funktion **Weiteren Eintrag hinzufügen**.
5. **Eintrag leeren** setzt Anwesenheit, Ort und Tätigkeiten wieder vollständig auf die ursprüngliche, nicht ausgewählte Ansicht zurück.
6. Abwesenheitsstatus wie Urlaub und Krank benötigen keinen Ort.

Die Zustände werden im MySQL-Backend als leere Zeichenfolgen für noch nicht gewählte Optionen gespeichert. Ein vollständig zurückgesetzter Tag erzeugt keinen Datensatz; ein ausgewählter Tag ohne Tätigkeit kann dagegen gespeichert und korrekt erneut angezeigt werden.

## Datenbankmigration

`V10__reportbook_clear_legacy_empty_days.sql` bereinigt ausschließlich alte, automatisch vorbefüllte **ANWESEND/BETRIEB**-Datensätze in bearbeitbaren Wochen, wenn weder Zeiten/Tätigkeiten noch Anhänge oder Kommentare vorhanden sind. Freigegebene und eingereichte Wochen bleiben von dieser Bereinigung ausgenommen.

**Produktionsdaten vor dem Update:** 1 Berichtswoche, 7 Tagesdatensätze, 1 Tätigkeitseintrag.

**Produktionsdaten nach dem Update:** 1 Berichtswoche, 1 Tagesdatensatz, 1 Tätigkeitseintrag. Es wurden nur sechs leere automatisch generierte Tagesdatensätze entfernt.

Die bestehenden Migrationen V1–V9 sind bytegleich mit der Live-Anwendung vor dem Update. Nur V10 ist neu. Außer `ReportBookIhkController.class` und der Versionsangabe im `BuiltinAddonCatalog.class` wurden keine bestehenden Backend-Klassen verändert.

## Prüfung

- TypeScript / Vite `npm run build`: erfolgreich
- Java 21 / Maven `mvn test package`: erfolgreich, 7 bestehende Tests bestanden
- Isolierte MySQL-8.4-Integration: bestehender Berichtsheft-/Rechte-Workflow plus neue Zustands-/Speicher-/Zurücksetzen-Tests bestanden
- Nach dem direkten Live-Deployment: Backend `.120.244` gesund, Flyway V10 erfolgreich, ReportBook v0.9.1 über Nginx `.120.250` aktiv und authentifiziert erreichbar
- HTML und zugehörige JavaScript-/CSS-Dateien werden über HTTPS ausgeliefert

## Sicherungen und Artefakte

Live-Sicherungen vor Installation:
- Backend-VM `172.22.120.244:/var/backups/sibyl/20261112-dayflow`: Datenbankdump, vorherige JAR, Environment-Konfiguration, systemd Service
- Frontend-VM `172.22.120.250:/var/backups/sibyl/20261112-dayflow`: gesicherte Frontend-Dateien
- Zusätzliche Kopien der vorherigen JAR und des alten Frontends verbleiben in den jeweiligen Installationsverzeichnissen.

Artefakt-Prüfsummen:
- Backend-JAR: `51312e1425dc91c712d09548a54e8fe695d15e5ee10cdbedc3f7dd22daf70d8a`
- Frontend-TAR: `7d00137d5a6f7bce47b2a8f402190aff1e016d6d04052d57fc181e2c06c5a986`

Quellbasis: `/home/fiona/sibyl-replatform` auf `172.22.100.118`. Deployment direkt per SCP/SSH auf die Ziel-VMs, ohne GitHub.

## Einschränkung

Die automatische Integration wurde mit Testkonten und einer isolierten Datenbank sowie mit read-only Live-Checks geprüft. Eine individuelle visuelle Abnahme der gesamten Oberfläche im angemeldeten Browser durch eine Benutzerin wurde nicht durchgeführt. Die Bilder der Nutzerin dienten als Vorgabe für das UI-Verhalten.
