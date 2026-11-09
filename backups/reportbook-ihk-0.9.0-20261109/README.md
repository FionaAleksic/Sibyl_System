# Sibyl Berichtsheft 0.9.0 – vertraute Wochen- und Jahresansicht

**Quellcode-Sicherung der direkt auf dem Sibyl-Cluster deployten Änderung vom 09.11.2026.**
Das Repository ist ausschließlich für Versionierung und Backup, **nicht die Deployment-Quelle**.

## Ziel
Die Bedienung des eingestellten IHK-Berichtshefts aus den von der Nutzerin bereitgestellten Screenshots möglichst beibehalten, in Sibyl integrieren, ohne den Reiter **Qualifikationen** fortzuführen.

Die sechs Addons bleiben fest im Sibyl-Core enthalten; Aktivierung und sämtliche Admin-Konfigurationen erfolgen weiterhin unter **Administration → Addons**. Diese Änderung betrifft die Benutzeroberfläche des Berichtshefts und die dafür notwendigen Datenbanktabellen, nicht die anderen Addons.

## Funktionen
- Wochenansicht mit sieben Tagesbereichen, Kalenderwoche und Pfeilnavigation
- Jahresansicht mit 12 Monatskalendern; Berichtsstatus farbig markiert, Tage anklickbar
- Anwesenheit, Ort (Betrieb, Schule, Unterweisung, Schule/Betrieb etc.)
- Pro Tag mehrere Freitext- oder Stichpunkteinträge, jeweils mit eigener Dauer
- Automatische Speicherung von Entwürfen mit Revisionskontrolle
- Tageseinträge leeren, eigene Entwurfswochen löschen, eingereichte Wochen zurückziehen
- Einreichen, begründete Zurückweisung, Korrektur, erneutes Einreichen und Freigabe
- Kommentare durch Azubi oder zuständigen Ausbilder; Anhänge in MySQL (maximal 3 MB/Datei und 40/Woche)
- Browser-Druckansicht: Über den Druckdialog als PDF speichern
- Keine Qualifikationen und keine wieder eingeführte Admin-Zuordnung in der Benutzeransicht

## Kompatibilität
Flyway-Migration `V9__reportbook_ihk_day_entries.sql` führt die neuen Tagesposten, Kommentare und Anhänge ein. Bestehende V8-Tätigkeiten werden in die neue Eintragsstruktur übertragen. Historische Berichtswochen, Nutzerzuweisungen und frühere Prüfschritte bleiben in der bestehenden Sibyl-Datenbank.

Der V2-Freigabeablauf bleibt vollständig eingebunden; V3 ergänzt die detaillierten Tagesdaten. Die Datenbankmigration ist **nicht** allein durch einen JAR-Rollback rückgängig zu machen.

## Build, Tests und Deployment
- TypeScript/Vite-Build erfolgreich.
- Java 21/Maven-Build und 7 Unit-Tests erfolgreich.
- 67 isolierte Integrationsprüfungen gegen MySQL 8.4 erfolgreich: Status-Workflow, Berechtigungen, Addon-Verwaltung, V3-Tage, mehrere Einträge, Dauer, Anhänge, Kommentare, Zurückziehen, Löschen.
- Live-Prüfung durch Nginx: 12 Prüfungen erfolgreich, inklusive aktueller JS-/CSS-Version, Admin-Konfiguration, Datenschutz und aktiver Module.
- Backend `172.22.120.244`, JAR SHA-256:
  `2b64e70c46f8e69779ece7b4acb209cd40d715c36aac64350f0aa8ddf1f24e48`.
- Frontend `172.22.120.250`, gebaute Assets: `index-BPVk0boj.js` und `index-C0ZApzG3.css`.
- Quellcode/Bundles direkt auf `172.22.100.118` gebaut und per SCP auf die autorisierten VMs übertragen.
- Getrennte Datenbank-/Backend-/Frontend-Sicherungen auf den entsprechenden VMs unter `/var/backups/sibyl/20261109-ihk090/`.
- Bestehende sechs Addons und Aktivierungszustände durch das Release unverändert.

## Bewusste Unterschiede / weitere Verfeinerungen
- Textformatierung im Entwurf nutzt eine leichte Markdown-Symbolleiste, noch keinen vollständigen IHK-WYSIWYG-Editor.
- PDF-Export erfolgt derzeit über den Browser-Druckdialog („Als PDF speichern“), nicht als direkt vom Server generierter PDF-Download.
- Individuelle IHK-Altdaten wurden **nicht** automatisch importiert, und eine Live-AD-Synchronisierung ist weiterhin von der Konfiguration des AD-Servicekontos abhängig.

Dieses Backup-Verzeichnis enthält nur die geänderten und neu erstellten Code-Dateien, **keinen vollständigen Core**. Der komplette Build liegt auf dem internen Entwicklungsserver.
