# Sibyl System – Audit & minimale Rollenrechte (14.10.2026)

## Technisch enthalten (Featurebranch, keine Produktivbehauptung)

- Serverseitige Berechtigungen `audit.read`, `accounts.manage`, `settings.manage`, `addons.manage`.
- Bestehende `ADMIN`-Rechte bleiben für den Core erhalten, `USER` hat keine dieser Rechte.
- `AUDITOR` ist ein neues eigenständiges, lesendes Konto mit ausschließlich `audit.read`; keine Addon-Installation, keine Benutzeranlage, keine Admin-Settings.
- Geschütztes, **nur lesendes** `GET /api/v1/admin/audit?limit=50&before=<ID>`, serverseitig auf 100 begrenzt, nach absteigenden IDs paginiert, 401/403 für Unberechtigte.
- Erfolgreiche und gescheiterte Formular-Anmeldungen protokollieren nur normierten Kontonamen + Ereignistyp; niemals Passwörter, Session-Cookies, Suchabfragen oder private Kommunikationsinhalte.
- React-V2-Shell: Sichtbarer Audit-Reiter nur für `ADMIN` / `AUDITOR`. Backend erzwingt die Rechte separat.
- Bestehende First-Login-Passwortänderung gilt auch für Auditoren.

## Grenzen

Dies ist **kein vollständiges** Security Information and Event Management (SIEM), kein manipulationssicherer Audit-Speicher und keine echte MFA-Implementierung. Die Ereignisse befinden sich in der bestehenden MySQL-Datenbank, können von DB-Administratoren manipuliert werden und benötigen externe, eingeschränkt zugreifbare Aufbewahrung. Login-Fehlversuche müssen über Edge/Nginx zusätzlich limitiert werden. Die UI enthält keine versteckte Überwachung von Mitarbeitenden.

**Noch nicht umgesetzt**: OIDC/MFA, Secret-Broker, feingliedrige Organisation/Objekt-Rechte, Wazuh/SIEM, AD-Schreibscopes, Windows-Collector, DHCP-/DNS-Pull, IPAM-Persistenz, USB-/App-Control-Richtlinien, getrennte Sandbox für beliebige Addons, manipulationssichere Audit-Weiterleitung.

## Deploy-Gates

1. CI mit MySQL und Browser-Build muss erfolgreich sein.
2. Eigenständige Test-DB/Isolation **oder** externes, verifiziertes Backup plus Restore-Probe vor jedem DB-Migrationsrelease.
3. Backend-Artefakt aus unveränderlichem Release mit geprüftem SHA-256 und dokumentiertem Commit. **Kein** `git pull && java -jar` auf aktiver Core-Instanz.
4. Negativtests: ungeauthentifiziert 401, USER 403, AUDITOR Audit 200, andere Admin-Endpunkte 403; Erstpasswortwechsel 423.
5. Rollback-Artefakt behalten, Nginx- und Backend-Health kontrollieren; kein automatischer Rollout auf alte Produktionsumgebung.
6. Für Betriebs-/Mitarbeiterdaten: Rechtsgrundlage und Zweckbindung prüfen, Betriebsrat/Datenschutz einbeziehen, Zugriff und Aufbewahrung minimieren, Mitarbeiter transparent informieren.

## Vorschlag für nächste Release-Schritte

- Sichere Audit-Event-Weiterleitung an externe unveränderliche Speicherschicht + MFA.
- Gesondertes `Sibyl.ad`: LDAPS-Read-Sync, explizit freigegebene Write-Scopes, kein freies LDAP.
- `Sibyl.inventory`: Windows-Collector (Computername, Patch- und Defender-Status, Software), DHCP-/DNS-Connectoren, ICMP/Service-Probes für freigegebene Subnetze, Eigentümer/Quelle/Beobachtungszeit.
- `Sibyl.security`: Wazuh/Sysmon-Events, Richtlinien für App Control/USB erst in freigegebener Pilotgruppe, LAPS-Integration.

## Versionshinweis

Der zur Erstellung verwendete Featurebranch basiert auf `feature/deck-inspired-v2`. Der Rocky-Pilot auf `.247/.248` hatte zuletzt Core `0.1.0-rc.17-dev.1`. Ein React-Build allein aktiviert **keine** neue Backend-Funktion.