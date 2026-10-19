# Sibyl V2 – Cluster-3-Lab-Neubau, Ausführung 12.10.2026

**Geltungsbereich:** ausschließlich Cluster 3 / ThrusterCluster. Keine Änderungen an Cluster 1 (Produktion), Cluster 2 (Test), AD oder den existierenden VMs 200–202. 

## Nutzerauftrag und endgültige Methode

Nach einer ausdrücklich geänderten Anweisung werden zwei weitere VMs **nach dem Verfahren der bestehenden VMs 200–202** aus der Ubuntu-24.04-Template-VM 9500 geklont (vollständiger Ceph-Klon, nicht Linked-Clone). Die zunächst angelegten, gestoppten VMs 203/204 ließen sich wegen Remote-Sicherheitschecks nicht löschen und wurden daher nicht weiter verwendet.

Die IPs sind **vorläufige Laborzuweisungen** und benötigen eine verbindliche IPAM-/DHCP-Reservierungsprüfung. Ein fehlender ICMP-Reply vor VM-Start ist kein Beweis für freie IP.

| VM | Host | IPv4 | CPU | RAM | Systemdisk | Zustand |
| --- | --- | --- | ---: | ---: | ---: | --- |
| 205 `sibyl-frontend-v2-fresh` | NL0078-PVE-lath | 172.22.120.245/24 | 2 vCPU | 4 GiB | 32 GiB | running, SSH OK, Cloud-init abgeschlossen |
| 206 `sibyl-backend-v2-fresh` | NL0080-PVE-shse | 172.22.120.246/24 | 4 vCPU | 8 GiB | 50 GiB | running, SSH OK, Cloud-init abgeschlossen |

Netz: `vmbr0`, Gateway `172.22.120.1`, DNS `172.22.100.202`, SSH-Key des Managers `172.22.100.118`.

## Vollständig erledigt und verifiziert

- Proxmox-Template 9500 auf Ceph `Senfpool` auf Zielnodes voll geklont; VM205/206 mit statischen IPs konfiguriert, disk resize und Boot.
- Beide IPs per ICMP, beide VM-Hostnames per SSH mit Schlüssel bestätigt.
- Cluster `quorate`, Ceph `HEALTH_OK` nach dem Anlegen.
- Frontend-VM205: Ubuntu-Pakete Node.js 18 / npm 9 / Nginx / Git / QEMU Guest Agent installiert. Dienste Nginx und Guest Agent aktiv.
- GitHub Action `Frontend v2 build`: Build PASS, veröffentlichtes Workflow-Artefakt `sibyl-frontend-v2-dist` aus Commit `c1c126e66cd13427354da8355efb3c36731c746d`. SHA-256 ZIP: `ac06f47e085249848d858f5a78cfcc1eb9951a890610a8b32e26f7a1a4fb8deb`; nach Download auf Manager .118 und VM205 verifiziert.
- V2-React-Assets unter `/srv/sibyl/frontend-v2/releases/c1c126e`; aktueller Symlink `/srv/sibyl/frontend-v2/current`.
- HTTPS unter `https://172.22.120.245/` auf Port 443 mit **selbstsigniertem 30-Tage-Laborzertifikat** und IP-SAN. Zugriff per Nginx-ACL vorerst **nur von 172.22.100.118**. HTTP 200 für Homepage, JS-Bundle, /healthz bestätigt. Aktuelle API liefert **HTTP 502**, weil Backend fehlt.
- Backend-VM206: OpenJDK 21, Maven 3.8, MySQL 8.0.46 und Guest Agent installiert. MySQL läuft und bindet `127.0.0.1:3306` ausschließlich lokal. Core-JAR und Standardinstaller aus GitHub-Release `v0.1.0-rc.16` heruntergeladen, jeweils SHA-256 gegen GitHub-Asset-Metadaten verifiziert:
  - JAR `4a431b59567c58b0910b59ffa287a795031c167e015a56af71928990d69e4952`
  - Installer `381af1f32100c7f2d8e163ff48b1a31c96ef35fd64101e62c11e6cee058c1603`
  - Installer `bash -n` PASS, JAR nach `/opt/sibyl/` gestaged.

## Eindeutiger offener Blocker

Die **Ausführung** des geprüften Standardinstallers per Remote Desktop Commander auf VM206 wurde durch die OpenAI-Sicherheitsprüfung blockiert (Toolstatus: konnte Sicherheitsstatus nicht bestimmen). Nicht über andere unsichere oder verdeckte Wege umgangen. Deshalb gibt es auf VM206 **noch keine Sibyl-MySQL-Datenbank, keine Flyway-Tabellen, keinen gebootstrappten Adminkonto-Datensatz und keinen aktiven `sibyl-core.service`**. Ein tatsächlicher Login-/Addon-/API-End-to-End-Test ist nicht möglich.

Der Installer ist `/home/fiona/install-sibyl-ubuntu-2404.sh` auf VM206 und setzt auf einem frischen Ubuntu 24.04 mit root-Rechten und `SIBYL_VERSION=0.1.0-rc.16` die Datenbank, Benutzerrolle, zufälliges DB-Passwort, Flyway und systemd ein. Er bindet standardmäßig nur **127.0.0.1:8080**. Vor Freigabe für den Frontend-Reverse-Proxy muss deshalb die Backend-Bindadresse gezielt umgestellt, Firewall **nur** für VM205 freigegeben und die TLS-Proxy-Kette getestet werden. Zusätzlich **beim ersten Login `admin`/`friend` ändern**; vor diesem Schritt den Site-ACL nicht breiter öffnen.

## Abnahmereihenfolge ab hier

1. Autorisierte Installer-Ausführung auf VM206 ohne Umgehung der Fernzugriff-Sicherheitsprüfung und mit gesicherter MySQL-Backup-/Restore-Vorprüfung.
2. Verifizieren: `systemctl is-active sibyl-core`, localhost `/actuator/health` (200), Tabelle `flyway_schema_history` und Accounts existieren, Login/Passwortwechsel/403 Negativtests.
3. Nur `172.22.120.245` darf TCP 8080 auf `172.22.120.246` erreichen. Über HTTPS `/api/v1/modules` muss 401 ohne Login und 200 mit gültiger Session liefern; kein 502.
4. Aktuelle V2-Capability-API stammt vom **Featurebranch**, nicht vom alten veröffentlichten Core-RC.16. Einen **neuen Release aus erfolgreich getesteter Featurebranch-CI** erstellen und versioniert upgraden, damit `/api/v1/admin/capabilities` existiert. Keine Branch-ZIPs als Releases ausgeben.
5. Erst nach Passwortwechsel, Netzwerk-/DMZ-Freigabe, Ende-zu-Ende-Tests und Ausbilderabnahme Benutzerzugriff erweitern.

## Wichtige Grenzen

- **Keine echte DMZ**: `vmbr0` ist unverändert ein gemeinsames Netzwerk. Der Nginx-Quellfilter ist kein Ersatz für Firewall-Segmentierung.
- React-V2-Frontend live bedeutet **nicht**, dass AD, Planung, Inventar, Wiki, Suche, IPAM, Kurssystem oder Berichtsheft automatisch funktionsfähig sind. Addon-Lifecycle und Backend-V2-Deployment stehen aus.
- VMs 203/204 sind weiterhin **gestoppt**, aber **nicht gelöscht**. VMs 200–202 wurden nicht angefasst.
