# Sibyl V2 Rocky Linux 10 – Cluster-3-Rollout (13.10.2026)

## Status (nur belegte Ergebnisse)

- Cluster: ThrusterCluster (Cluster 3), Quorum 3 Nodes, `Ceph HEALTH_OK`.
- **Geplante**, nicht erstellte VMs: 207 (`sibyl-rocky-frontend`, `172.22.120.247/24`, 2 CPU/4 GiB/32 GiB auf NL0078-PVE-lath) und 208 (`sibyl-rocky-backend`, `172.22.120.248/24`, 4 CPU/8 GiB/50 GiB auf NL0080-PVE-shse).
- IPs antworteten vor dem Rollout nicht auf ICMP; **nicht** als IPAM-frei oder reserviert nachgewiesen. Für die Zuweisung ist die Netzwerkverwaltung zuständig.
- Offizielle Installationsquelle aus dem Rocky-Projekt auf Node `NL1337-PVE-maeb`:
  `/var/lib/vz/template/iso/sibyl-rocky10-genericcloud.qcow2`
- SHA-256 geprüft: `9fc9e9ff16888bb68ac39b0392e25c9c92684d50c85f1cce6ab549363bbc4b48` exakt wie Rocky-Upstream für `Rocky-10-GenericCloud-Base.latest.x86_64.qcow2`.
- `qemu-img check` **nach Abschluss des Downloads**: `No errors were found on the image.` Die frühere Fehlerausgabe während eines unvollständigen Downloads war daher nicht reproduzierbar.
- Rocky-spezifische Installer (nur Quellcode/CI-getestet, **noch nicht real auf Rocky-VM getestet**):
  `deploy/rocky10/install-backend.sh` und `deploy/rocky10/install-frontend.sh`.
- `Rocky Linux 10 installer syntax` GitHub Action: Syntaxprüfung. Erfolg ist **kein** Integrations-/Härtungsnachweis.
- VM-Erstellung über Remote Desktop Commander (`qm create 9501`) wurde vom OpenAI-Fernzugriff-Sicherheitscheck blockiert. **Nicht auf anderem Weg umgangen.** IDs 9501/207/208 existierten bei letzter Prüfung noch nicht. VMs 200–206, 9500 unverändert.

## Ausbilder-relevante Sicherheitsgrenzen

Das geteilte `vmbr0` / 172.22.120.0/24 ersetzt keine echte DMZ. Benutzer-/Netzfreigabe erst nach Segmentierung, IPAM-Prüfung, funktionalem Login-Test, Abschaffung/Rotation des initialen Admin-Passworts, Addon-Tests, TLS mit vertrauenswürdigen Zertifikaten und Freigabe. Bis dahin soll HTTPS nur vom Verwaltungsserver `172.22.100.118` zugelassen werden, Backend-TCP/8080 nur von der Frontend-VM.

## Operator-Freigabeschritt in Proxmox

Für die weitere Installation ist die Erstellung eines Rocky-10-Templates und der beiden neuen VMs durch einen **befugten Cluster-Administrator über Proxmox** erforderlich. Wegen des Sicherheitssperrstatus übernimmt die Remote-Sitzung diesen Schritt nicht. Aktuelle Image-Datei und Prüfsumme sind oben dokumentiert.

Vorgesehene Konfiguration:
- Template 9501: Rocky GenericCloud (QCOW2 in Ceph `Senfpool` importiert), Cloud-init-CD, VirtIO SCSI, serielles Konsolengerät, SSH-Public-Key für `fiona` (nicht private Schlüssel), QEMU Guest Agent.
- 207 auf Node `NL0078-PVE-lath`: statisch `172.22.120.247/24`.
- 208 auf Node `NL0080-PVE-shse`: statisch `172.22.120.248/24`.
- Gateway `172.22.120.1`, DNS `172.22.100.202`; keine Passwörter/Private Keys ins GitHub-Repo.
- Vor dem ersten Boot SSH-Key, statische IPs und Ceph-Diskzuordnung prüfen.

## Installation **nach** Freigabe und Boot beider VMs

1. Über den Verwaltungsserver .118 mit dem vorhandenen `id_ed25519_sibyl_lab` als `fiona` verbinden. `/etc/os-release`, SELinux, `cloud-init status`, Netz/DNS, Uhrzeit, Diskgröße prüfen.
2. Backend: Installer `deploy/rocky10/install-backend.sh` von einer versionierten GitHub-Quelle auf VM208 übertragen, auf Änderungsstand und Syntax prüfen und als Root ausführen mit:
   - `SIBYL_VERSION=0.1.0-rc.16`
   - `SIBYL_LISTEN_ADDRESS=172.22.120.248`
   - `SIBYL_FRONTEND_IP=172.22.120.247`
   - Es installiert MySQL 8.4, Java 21, prüft den veröffentlichten Core-Release SHA-256, startet `sibyl-core.service`.
   - Wenn die MySQL-RPM-Installation ein temporäres Root-Passwort erzeugt, darf dieses **nur auf einer bisher uninitialisierten Instanz** rotiert werden. Auf produktivem Datenbestand niemals Reset.
3. Nach Serverhealth und DB-Flyway-Checks einen geprüften React-`dist`-ZIP-Build aus erfolgreich gelaufener GitHub-CI auf VM207 übertragen. Für den Build aus Commit `c1c126e` wurde `ac06f47e085249848d858f5a78cfcc1eb9951a890610a8b32e26f7a1a4fb8deb` als ZIP-SHA-256 ermittelt. Für jeden **anderen** Build neu hashverifizieren!
4. Frontend: `FRONTEND_ZIP=/root/sibyl-frontend-v2-dist.zip FRONTEND_SHA256=<exakter_sha256> SIBYL_FRONTEND_IP=172.22.120.247 SIBYL_BACKEND_IP=172.22.120.248` und `deploy/rocky10/install-frontend.sh` als Root. HTTPS wird vorerst nur vom Verwaltungsserver erlaubt, mit temporärem selbstsigniertem Laborzertifikat.
5. Tests auf VM207 und über .118: `https://172.22.120.247/healthz` 200, React HTML+JS 200, `/api/v1/modules` darf **nicht** 502 sein. `/actuator/health` vom Backend, gültige Session/403-Negativtests, **Admin-Passwort sofort ändern**.
6. Beachten: Core-Release rc.16 enthält möglicherweise noch nicht die neuen V2-Capability-APIs des Featurebranches. Dafür braucht es einen gesonderten getesteten, versionierten Core-Release.

## Unverändert

VMs 200–202 bleiben bestehende Sibyl-Umgebung; 203/204 gestoppte Prototypen; 205/206 Ubuntu-V2-Labor; VM9500 Ubuntu-Vorlage. Keine dieser Instanzen wird ohne getrennte Freigabe gelöscht oder überschrieben.

## Nachtrag 13.10.2026 – Betreiber hat VM207 und VM208 selbst angelegt

Die ursprüngliche Planung (207/208 noch nicht vorhanden) ist durch neue Betreiberaktion überholt. Die folgende **neue Zustandsprüfung** ist maßgeblich:

- `qm list` auf `NL1337-PVE-maeb` weist **VM207 `srvedge`** und **VM208 `srvbackend`** mit je **8.000 MB RAM und 32-GB-Bootdisk** aus. **Beide liegen auf demselben Node .218**, nicht auf den früher vorgesehenen unterschiedlichen Nodes.
- VM207 war bereits `running`; VM208 wurde im Rahmen dieser Fortsetzung durch `qm start 208` erfolgreich gestartet und der Status anschließend als `running` bestätigt.
- Die ursprünglich geplanten IPs **172.22.120.247** und **172.22.120.248** antworten auch nach dem Start nicht auf ICMP. Keine erfolgreiche SSH-Verbindung zu diesen IPs nachgewiesen. Deshalb keine Deploy-Aktionen in den Gast-VMS möglich.
- `qm guest cmd 208 network-get-interfaces` meldete **`No QEMU guest agent configured`**.
- `qm terminal 207` und `qm terminal 208` meldeten jeweils **`unable to find a serial interface`**. Beide VMs benötigen die Proxmox-Grafikkonsole zur Überprüfung von Boot-/Installerzustand und Netz.
- Read-only-`qm config`-Abfragen für 207/208 über Remote Desktop Commander wurden durch den Fernzugriff-Sicherheitscheck geblockt. Nicht durch andere Verwaltungswege umgangen.
- **Keine Aussage möglich**, ob Rocky Linux in den beiden Gästen schon fertig installiert wurde. Zuletzt keine erfolgreiche Web-/API-Bereitstellung, keine Berücksichtigung als produktionsbereit.
- Alte Sibyl-VMs 200–206 und Ubuntu-Vorlage 9500 bleiben unverändert.

**Operator-Schritt, Proxmox GUI:** VM207 und VM208 einzeln über `Konsole` öffnen, prüfen ob Installer oder installiertes Rocky 10 läuft; falls Installer, die Rocky-10-Installation fertigstellen. Nach Login im Gast die Netzwerkkonfiguration mit `ip -br addr`, `nmcli con show --active` prüfen und die **von IPAM bestätigten** vorgesehenen Adressen `.247/24` (Frontend) und `.248/24` (Backend) mit Gateway `172.22.120.1` und DNS `172.22.100.202` setzen; Passwortlogin nach sicherem Einspielen des SSH-Public-Keys einschränken, ohne sich auszusperren. `sudo dnf install -y qemu-guest-agent && sudo systemctl enable --now qemu-guest-agent`. Ein Proxmox-VM-Agent muss außerdem in den **Optionen der VM** aktiviert sein. Sobald SSH auf beiden VMs als `fiona` funktioniert, erst Installer aus `deploy/rocky10/` ausführen. Beim Backend sind vor Freigabe die neuen Rocky-MySQL-8.4-Details durch echten Integrationstest zu validieren.
