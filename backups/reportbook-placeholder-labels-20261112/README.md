# Sibyl Berichtsheft – Sichtbare Auswahlbeschriftungen (12.11.2026)

**Versionssicherung des direkt deployten Frontends; GitHub wurde NICHT für Deployment verwendet.**

## Änderung

Die Auswahlfelder „Anwesenheit“ und „Ort*“ besitzen jetzt dauerhaft sichtbare Beschriftungen **oberhalb** der Felder. Solange noch nichts ausgewählt ist, erscheint im Feld der entsprechende Hinweistext in Grau. Die leeren Placeholder sind `<option value="" disabled hidden>` und können im Dropdown nicht als reguläre Option ausgewählt werden.

Die bisherige CSS-Regel zum Verbergen der Labels (`.ihk-unset`) wurde entfernt. Die beiden Selects verwenden die CSS-Klasse `ihk-placeholder`, wenn der Wert leer ist. Alle vorhandenen Anwesenheits- und Ortsoptionen, der Tagesablauf, die Autospeicherung und „Eintrag leeren“ bleiben unverändert.

## Build und Deployment

- TypeScript + Vite: `npm run build` erfolgreich.
- Frontend-Release aus lokalem Entwicklungsprojekt `172.22.100.118:/home/fiona/sibyl-replatform` erstellt.
- TAR-Prüfsumme SHA-256: `b63d2d6757c7e921592c66e700bb556a9437291e443a61d4f0c48100eb21c956`.
- Direkte Bereitstellung per SCP/SSH auf `172.22.120.250`, ohne Repository-Download.
- Nginx-Konfiguration geprüft; die neue JavaScript-Datei `index-BrkRMKg1.js` und CSS-Datei `index-BlWSj7RF.css` sind über die Live-URL auslieferbar.
- Die Browser-URL liefert die neuen Asset-Referenzen und CSS-Regeln; der alte Label-Hide-Selektor ist nicht mehr enthalten.
- Backup des vorherigen Live-Frontends: `172.22.120.250:/var/backups/sibyl/20261112-select-labels/frontend-before.tar.gz`.
- Zusätzliches Verzeichnis mit vorherigem Frontend: `/srv/sibyl-ui.pre-select-labels-20261112`.

Es wurden ausschließlich `frontend/src/pages/ReportBookPage.tsx` und `frontend/src/styles/ihk-report.css` geändert, kein Backend und kein Datenbankschema.

Visuelle Abnahme durch einen angemeldeten Browser ist noch nicht erfolgt; der Build, die HTML-Auslieferung und die ausgelieferten CSS-Regeln sind bestätigt.
