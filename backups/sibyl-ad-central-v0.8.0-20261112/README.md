# Sibyl.ad 0.8.0 — zentrale AD-Integration

**Stand:** 12.11.2026 — **Entwicklungs-Build, noch NICHT auf den produktiven Sibyl-VMs deployed.**

Dieser Branch enthält eine reine **Quellcode-Versionssicherung**. Für Deployment wird GitHub **nicht** verwendet. Gebaut und getestet wird auf `172.22.100.118:/home/fiona/sibyl-replatform`.

## Architektur

- `de.nikoit.sibyl.addons.ad`: Alle AD-Komponenten, darunter `SibylAdApi` als gemeinsame Schnittstelle, `AdAddonService` für LDAP-Bind, DNS-Controller, Gruppen- und Benutzerabfragen sowie `AdConnectionService` für Domänenprofile.
- `SibylAuthenticationProvider`: Kern-Komponente ohne LDAP-Code. Lokale Benutzer werden weiterhin lokal authentifiziert; AD-Anmeldung ausschließlich über `SibylAdApi`.
- `InventoryDiscoveryService`: Domain Controller ausschließlich über `SibylAdApi.controllers()`.
- `ReportBookDirectorySync`: AD-Mitglieder über `SibylAdApi.groupRoster(...)`. Tätigkeitsnachweise, Zuordnungen und Freigaben verbleiben im Berichtsheft.
- `ServiceCredentialVault`: AES-256-GCM-Verschlüsselung der Servicekonto-Passwörter, weiterhin Schlüssel aus geschützter Backend-Umgebung.

## Einstellungen — Administration → Addons → Active Directory

- Mehrere AD-Profile mit Domain, User Base DN, DNS-SRV oder statischem LDAPS-Server, NetBIOS-Namen.
- Verbindung an/aus, Benutzeranmeldung und servicekonto-basierte Synchronisierung **separat** ein-/ausschaltbar.
- Standarddomäne für unqualifizierte Benutzernamen optional.
- Administrator- und Benutzergruppen je Domäne (vollständige DNs).
- Servicekonto samt verschlüsseltem Kennwort.
- Zentrale Azubi-/Ausbilder-Gruppenzuordnungen für das Berichtsheft.

**Keine fest codierten Firmengruppen** in der neuen Gruppen-Rollenauflösung. Bei Überschneidungen haben die regulären Benutzergruppen Vorrang. Berechtigungen werden für jede AD-Anmeldung gegen das AD geprüft. Benutzerpasswörter werden nicht in der Sibyl-Datenbank gespeichert.

## Datenbankmigration V11

`V11__ad_addon_unified_directory_profiles.sql` fügt `login_enabled`, `sync_enabled`, `netbios_name` zur AD-Profil-Tabelle hinzu. Für bestehende Profile sind Login und Synchronisierung standardmäßig aktiviert (nur aktivierte Profile können genutzt werden).

Die bisherigen `traineeGroupDns`/`trainerGroupDns` aus dem Berichtsheft werden in `Sibyl.ad` als `reportBookTraineeGroups`/`reportBookTrainerGroups` übernommen. Die alten Werte bleiben aus Rollback-Gründen erhalten.

## Tests

- Maven/Java 21 `clean test package`: Build und 7 Gruppen-Rollen-Unit-Tests erfolgreich
- TypeScript/Vite `npm run build`: erfolgreich
- Isolierter MySQL-8.4-Test: lokale Anmeldung, Berichtsheft-Ablauf, Rechte, Konfiguration von AD-Profilen, separate Login- und Sync-Freigaben sowie Geheimnisschutz erfolgreich
- Getrennter Upgrade-Test von einem echten alten Sibyl-V9-JAR auf V11: bestehende AD-Domäne, Aktivierung und Gruppenkonfiguration erhalten, lokale Admin-Anmeldung weiterhin möglich
- Neue Umgebung ohne AD-Profil: kein impliziter/firmenfester AD-Login; lokale Administratoren bleiben nutzbar
- Reale AD-Benutzeranmeldung mit einem Endbenutzerpasswort **noch nicht auf der produktiven Version geprüft**

## Aktuelle produktive Situation

Die produktive DB auf `172.22.120.244` hat bisher Flyway V1–V10, ein **aktives Profil `nl.nikoit.de`**, aber **kein gespeichertes Servicekonto-Passwort**. Der AD-Login und die Gruppen-Synchronisierung sind getrennt; die fehlenden Dienstzugangsdaten verhindern ausschließlich die Hintergrundabfragen.

**Kein Live-Deployment:** Der Versuch, die nötige geschützte Datensicherung über den Remotezugriff zu starten, wurde durch Sicherheitsprüfungen blockiert. Diese Beschränkung wurde nicht umgangen. Die aktuelle Live-JAR, das Frontend und die DB wurden deshalb nicht modifiziert.

## Vorbereiteter direkter Build

- Entwicklungsserver: `/home/fiona/sibyl-staged-ad/sibyl-ad-addon-centralized-v0.8.0-20261112.tar.gz`
- Kopie auf NL14313: `C:\Users\devin.aleksic\Downloads\sibyl-ad-addon-centralized-v0.8.0-20261112.tar.gz`
- Release-SHA256: `12949c0cd3846145c60cd2e1a3af54ed5b74402acde03164e85ab452d26524b6`
- Backend-JAR-SHA256: `0c2d4a356cdeec648a4376c47d42438acb36aa6ee7a3c33c9739334b599abafb`

**Für ein autorisiertes Deployment:** Zuerst MySQL, Backend-JAR, Konfiguration und Frontend sichern; Flyway V11 gegen Datenbankkopie validieren; dann Backend/Frontend direkt aus diesem lokalen Build bereitstellen; danach lokalen Admin, AD-Login, Berichtsheft, Inventar und Gruppenabfrage prüfen. Für vollständigen Rollback nach V11 ist auch die passende Datenbanksicherung nötig.
