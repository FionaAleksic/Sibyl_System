# Sibyl – Managed Assets / Security Pilot

**Stand: 14.10.2026 · Experimental / keine Produktionsfreigabe**

Dieser Branch ergänzt den bestehenden Audit-/RBAC-Core um ein begrenztes, ausdrücklich genehmigungspflichtiges Netzwerk- und Endpoint-Inventar. Er ersetzt weder `Sibyl.inventory` noch einen SIEM-/MDM-Anbieter.

## Funktionen

- Flyway `V5__security_inventory.sql`: freigegebene private CIDR-Netze, eindeutig einer Collector-Quelle zugeordnete Assets, Gerätehygiene-Meldungen und Nonce-Replay-Schutz.
- `SECURITY_OPERATOR`: nur Inventar lesen, Netzbereiche registrieren und Meldungen bestätigen. Konten- und Addon-Verwaltung bleibt `ADMIN` vorbehalten. `AUDITOR` behält ausschließlich Audit-Leserechte.
- `GET /api/v1/security/summary|assets|alerts|networks`: authentifiziert mit `inventory.read`.
- `POST /api/v1/security/networks`: `networks.manage`, nur ausgerichtete RFC1918-Netze mit Präfix /16 bis /30.
- `POST /api/v1/security/alerts/{id}/ack`: `alerts.manage`, Aktion im Core-Audit.
- `POST /api/v1/collector/snapshot`: einzige maschinelle Eingangsroute, ausschließlich signiert und ohne Session/Cookie; maximal 32 KiB, Zeitfenster ±120 Sekunden, UUID-Nonce einmalig, genau sechs erlaubte Felder. Standardmäßig deaktiviert, solange keine Schlüssel konfiguriert wurden.
- React V2: Geräte-/Netzwerkansicht und Sicherheitsmeldungen, nur für dazu berechtigte Rollen.

## Collector-Zulassung (ausdrückliche Einrichtung notwendig)

1. Zuständige Netzwerk-/Datenschutzverantwortliche genehmigen Messzweck, Gerätebereiche, betroffene Systeme, Transparenz und Aufbewahrung. Keine Überwachung von Privatverhalten, Bildschirmen, Tastatureingaben oder Inhalten.
2. Über den Admin-Zugang ein eigenes internes RFC1918-Netz registrieren. Keine Subnetze automatisch scannen. Geräte außerhalb dieser Bereiche werden abgewiesen.
3. Pro Collector-Identität einen mindestens 256-bit Zufallsschlüssel über Secret-Management erzeugen. Nicht ins Repo, nicht in Tickets, nicht in Browserlogs speichern.
4. Auf dem Core in der root-geschützten Umgebungsdatei `SIBYL_COLLECTOR_KEYS=standort-a:<64 HEX-Zeichen>` setzen. Mehrere IDs kommagetrennt; Schlüssel erst nach genehmigtem Deployment konfigurieren. Die Anwendung wird bei ungültiger Konfiguration nicht gestartet.
5. Auf dem zu inventarisierenden Linux-Gerät `collectors/linux/sibyl-collector.py` aus einem geprüften Release installieren. Betrieb nur mit vertrauenswürdigem HTTPS-Zertifikat und abgesicherter lokaler Umgebungsdatei (`SIBYL_COLLECTOR_URL`, `SIBYL_COLLECTOR_ID`, `SIBYL_COLLECTOR_KEY_HEX`, `SIBYL_DEVICE_IPV4`). Keine Klartext-Keys in CLI-Argumenten.
6. Zum Vorabcheck `python3 sibyl-collector.py --dry-run` ausführen. Die ersten Meldungen haben Patch-/Schutzstatus `unknown`, solange diese nicht durch freigegebene Adapter zuverlässig ermittelt werden. Keine Behauptung über automatisch geprüfte Patchstände.
7. Meldungen gehen nur per HTTPS an den Core. Keine Agent-Fernsteuerung, Shell-Ausführung, Bildschirmaufnahme oder heimliche Erfassung vorgesehen.

Die Meldungen sind **Selbstauskünfte von Geräten**, kein kryptografischer Nachweis, dass ein Endpoint sicher oder aktuell ist. Die Signatur schützt den Transport der Payload bei korrekter Geheimnisverwaltung, nicht vor kompromittierten Geräten.

## Datenbank-/Rollout-Sperre

**Nicht auf den bestehenden Rocky-Core/produktiv genutzten MySQL-Datenbestand deployen**, bevor ein verschlüsseltes Backup auf externem Medium vorhanden, vollständig wiederhergestellt und verifiziert ist. `V5` ist eine neue Flyway-Migration; das bisherige Core-Rollout-Skript verweigert zu Recht ein Update mit geändertem Schema. Kein stilles Umgehen dieser Sperre.

Weitere Voraussetzungen: erfolgreiche CI mit MySQL, React-Typprüfung, Negativ- und Positivtests des HMAC-Collectors, Rate-Limits, verifiziertes Zertifikat, echtes DMZ-/Firewallkonzept, Datenschutzfreigabe und getestetem Rollbackverfahren. Die bisherige Frontend-Aktivierung auf VM207 wurde durch den Fernzugriffs-Sicherheitscheck blockiert und bleibt unangetastet.

## Für vollständige Sibyl-Roadmap weiterhin offen

- MFA/OIDC und externes unveränderliches Audit-Forwarding
- AD-/DHCP-/DNS-Adapter und persistentes IPAM mit Konfliktauflösung
- Signierte Windows-/Linux-Collector-Pakete mit Patch-/Defender-Discovery und Agent-Key-Rotation
- Wazuh/Sysmon SIEM-Connector, Korrelation und Alarmzustellung
- Isolierte Addon-Capability-Broker und API-Versionierungs-/Rollbackvertrag
- Freigegebene Windows-Policy-Integration (USB, App Control, LAPS), mit Freigabe-/Dry-Run-Workflow

Dieser Branch ist ausdrücklich ein **erster testbarer Baustein**, keine Behauptung, dass die gesamte Netzverwaltung fertiggestellt wurde.
