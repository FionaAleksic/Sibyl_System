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


## Fortschritt 13.10.2026 ab 19:07 – Rocky-Installation VM207

Diese Messungen überschreiben ältere Zwischenstände, wonach keine der Rocky-VMs installiert war:

### VM207 `srvedge` — Rocky 10.2 erfolgreich installiert und per SSH administrierbar

- `qm config 207` zeigte CPU `host`, IDE2 ursprünglich Rocky-10.2-Boot-ISO, Ceph `vm-207-disk-0` (32 GiB).
- VM207 wurde gestoppt. Das upstream-per-SHA256-verifizierte Rocky-10-GenericCloud-QCOW2 wurde mit `qm importdisk 207 ... Senfpool --format raw` als `vm-207-disk-1` importiert.
- Die ursprüngliche `scsi0` blieb **unverändert** als leere Platte erhalten. `scsi1` ist neue Bootplatte, auf **32 GiB** erweitert, `boot: order=scsi1`.
- Cloud-init wurde auf `ide2` (Ceph) gesetzt. `ciuser=fiona`, `sshkeys=/root/sibyl-lab-authorized.pub` (bereits existierender Lab-Key mit SHA256 `MHMEzZ/MNsWZp0i4UOTbJ/EHsOCdH50vyGAKdchg8nk`), IP `172.22.120.247/24`, Gateway `172.22.120.1`, DNS `172.22.100.202`, Domain `nl.nikoit.de`; serielle VM-Konsole und Guest Agent aktiviert.
- VM bootet und ist per SSH von Verwaltungsserver `.118` erreichbar: `ssh -i /home/fiona/.ssh/id_ed25519_sibyl_lab fiona@172.22.120.247`.
- Tatsächlich nachgewiesenes OS `Rocky Linux 10.2 (Red Quartz)`; Rootpartition `/dev/sdb4` rund **31 GiB**, SELinux **Enforcing**; `sudo -n` funktioniert, `sshd` aktiv, QEMU Guest Agent bereits installiert und aktiv.
- `sudo cloud-init status --long`: **`status: done`**, jedoch `extended_status: degraded done` **wegen DEPRECATED-Konfigurationswarnung**; `errors: []`. Proxmox `qm guest cmd 207 network-get-interfaces` sieht `172.22.120.247`.
- `sshd -T`: `passwordauthentication no`, `pubkeyauthentication yes`, `permitrootlogin without-password`. SSH-Root-Login mit Passwort damit deaktiviert; vollständige Härtung steht aus.
- `nginx` **noch nicht installiert**; Remote Desktop Commander blockierte den Befehl zur `dnf install` durch eine Fernzugriff-Sicherheitsprüfung.
- Rocky-Repositories `baseos`, `appstream`, `extras` vorhanden. GitHub API per IPv4 zuletzt HTTP 200. Ein früherer DNS-Timeout war transient.
- Der persönliche Windows-Schlüssel von `NL14313` wurde in VM207 noch **nicht** zusätzlich installiert; bislang ist der vorhandene Sibyl-Laborverwaltungsschlüssel freigegeben.

### VM208 `srvbackend` — ausgeschaltet, Rocky noch nicht installiert

- Bei `qm config 208` CPU `host`, Ceph `vm-208-disk-0` (32 GiB), ursprüngliches Rocky-10.2-Boot-ISO; Agent noch nicht aktiviert.
- VM208 wurde nach einer verzögerten ersten Stop-Anfrage zuletzt durch `qm stop 208` erfolgreich ausgeschaltet. `qm status 208` = **stopped**.
- Der Remote-Desktop-Commander-Aufruf `qm importdisk 208 ...` wurde durch Sicherheitschecks blockiert. **Keine neue Bootplatte installiert; VM208 nicht startfertig**.
- Nicht davon ausgehen, dass ein Zugriff über `172.22.120.248` bereits möglich ist.

### Fortsetzung direkt durch Cluster-Administrator auf `NL1337-PVE-maeb`

Das folgende Verfahren enthält **keine Löschung oder Überschreibung der bisherigen scsi0** und setzt voraus, dass VM208 weiterhin gestoppt ist. Administrator muss IP-Zuteilung (`.248`) und freien Ceph-Speicher bestätigen.

```bash
qm status 208
sha256sum /var/lib/vz/template/iso/sibyl-rocky10-genericcloud.qcow2
# Erwartet: 9fc9e9ff16888bb68ac39b0392e25c9c92684d50c85f1cce6ab549363bbc4b48
ssh-keygen -lf /root/sibyl-lab-authorized.pub
qm importdisk 208 /var/lib/vz/template/iso/sibyl-rocky10-genericcloud.qcow2 Senfpool --format raw
qm config 208 | grep unused
# Nur wenn unused0: Senfpool:vm-208-disk-1 angezeigt wird:
qm set 208 --scsi1 Senfpool:vm-208-disk-1,iothread=1,discard=on --boot order=scsi1
qm set 208 --ide2 Senfpool:cloudinit --ciuser fiona --sshkeys /root/sibyl-lab-authorized.pub --ipconfig0 ip=172.22.120.248/24,gw=172.22.120.1 --nameserver 172.22.100.202 --searchdomain nl.nikoit.de --serial0 socket --vga serial0 --agent enabled=1
qm resize 208 scsi1 32G
qm start 208
```

In `qm config 208` danach `scsi0` als alte, unbenutzte Originalplatte und `scsi1` als neue Bootplatte sowie `boot: order=scsi1` prüfen. Manuelle Aktion ist nötig, weil sich ein blockierter Fernzugriff nicht durch mehr Proxmox-Rootrechte freischalten lässt. Keine zusätzlichen SSH-Privatschlüssel kopieren.

### Nach vollständiger VM208-Installation

1. Über .118: `ssh -i /home/fiona/.ssh/id_ed25519_sibyl_lab fiona@172.22.120.248`; `cat /etc/os-release`, `sudo cloud-init status --long`, `ip -br addr`, `sudo dnf repolist` prüfen.
2. Auf VM208 den aus Repo `deploy/rocky10/install-backend.sh` **vorher durch Administrator geprüften** Installer ausführen; mit `SIBYL_VERSION=0.1.0-rc.16 SIBYL_LISTEN_ADDRESS=172.22.120.248 SIBYL_FRONTEND_IP=172.22.120.247`; auf MySQL 8.4, Root-Erstpasswort, SELinux und Ports achten.
3. Auf VM207 `deploy/rocky10/install-frontend.sh` mit verifiziertem Frontend-CI-Artefakt, korrektem `FRONTEND_SHA256`, IPs und HTTPS installieren.
4. Anschließend `/actuator/health`, `/healthz`, API Proxy, Addon Discovery, TLS und Admin-Login verifizieren. Vor breiter Freigabe Bootstrap-Adminpasswort ändern und separate Firewallsegmente sicherstellen.
5. Die Installer wurden via GitHub Actions auf **Bash-Syntax**, aber noch nicht auf echten Rocky-VMs ende-zu-ende ausgeführt.


## Abschlussstand 13.10.2026 – tatsächlicher Sibyl-Rocky-Deploy

**Dieser Abschnitt überschreibt die früheren Zwischenstände, in denen VM207/VM208 noch nicht installiert oder per SSH erreichbar waren.**

### Frontend-VM207 `srvedge.nl.nikoit.de`
- Rocky Linux 10.2, `172.22.120.247/24`, Guest Agent, SSH als `fiona` mit bestehendem Sibyl-Labor-Public-Key, `sudo`.
- Nginx 1.26.3 und firewalld aktiviert, SELinux **Enforcing**. TLS mit **30 Tage gültigem selbstsigniertem** Testzertifikat.
- HTTPS-Adresse: `https://172.22.120.247/`. Der Zugriff auf Port 443 ist absichtlich auf die Management-IP `172.22.100.118/32` begrenzt; Browser von anderen PCs werden derzeit NICHT freigeschaltet.
- Releasestand React: GitHub Actions Build des Featurebranches, Head-SHA `08bbaa7fc1f0e3d62a339cca7e1110199491824f`, Artifact-ID `11825837939`, geprüfter ZIP-SHA-256 `4c4c09755668186f18149df03d59256f1e75e0de4219be852137372ad196a14b`.
- Deployment `/srv/sibyl/frontend-v2/releases/08bbaa7fc1f0e3d62a339cca7e1110199491824f/`; aktiver Symlink `/srv/sibyl/frontend-v2/current`.
- Nach Installerfehler `403` für `index.html`: Archiv-Extraktion unter `umask 077` erzeugte root-only Dateien. Der Fix `chmod -R a+rX` wurde live angewandt und in `deploy/rocky10/install-frontend.sh` persistiert.
- Vom Manager `.118` verifiziert: `/` HTTP 200, `/healthz` HTTP 200, `/login.html` HTTP 200, React-JS und CSS jeweils HTTP 200. `/admin.html` und `/addons.html` leiten ohne Anmeldung weiter; `/api/v1/modules` antwortet ohne Login HTTP 401.
- firewalld gestattet HTTPS ausschließlich von `172.22.100.118/32`. Standardfreigabe `cockpit` in firewalld entfernt, SSH verbleibt.

### Backend-VM208 `srvbackend.nl.nikoit.de`
- Rocky Linux 10.2, `172.22.120.248/24`, SSH als `fiona`, `sudo`, Guest Agent, SELinux **Enforcing**.
- Java 21, MySQL 8.4.11, `sibyl-core.service`, firewalld aktiviert und als Systemdienste für Boot aktiviert.
- Sibyl Core JAR aus veröffentlichtem GitHub Prerelease `v0.1.0-rc.16`; veröffentlichter GitHub-JAR-SHA-256 `4a431b59567c58b0910b59ffa287a795031c167e015a56af71928990d69e4952`. Die Release-Download-Prüfung erfolgt im Rocky-Installer.
- MySQL 8.4-Schema `sibyl` wurde erstellt. Flyway-Migrationen v1 bis v4 erfolgreich angewandt und nach MySQL-Neustart erneut validiert. Live `/actuator/health` auf `172.22.120.248:8080` meldet JSON `{"status":"UP"}`.
- `sibyl_users`: genau `admin`, Rolle `ADMIN`, `enabled=1`, `must_change_password=1`. **Initialpasswort unverändert; Passwortwechsel durch Betreiber noch nötig.**
- `sibyl_addon_installs`: 0 Einträge. Die modulare Addon-Installation ist deshalb **nicht** als abgeschlossen zu bezeichnen.
- Datenbankports MySQL `3306` und MySQL-X `33060` lauschen ausschließlich auf `127.0.0.1` nach Installation von `/etc/my.cnf.d/sibyl-loopback.cnf`. SQL-Zugangsdaten nur in root-geschützten Dateien `/etc/sibyl/`, nicht ins Repo kopieren.
- Backend HTTP `8080` ist nur von der Frontend-IP `172.22.120.247/32` in firewalld zugelassen. Über Reverse Proxy `https://172.22.120.247/api/v1/modules`: ohne Login HTTP 401 (vor Firewall-Fix HTTP 502). Unnötige Cockpit-Firewall-Freigabe entfernt.
- Erster `cloud-init`-Paketupdateversuch schlug wegen `python-unversioned-command` mit alter, hart gepinnter Python-3-Version fehl. Dry-Run zeigte nur eine optionale Abhängigkeit, das Paket wurde entfernt. Danach installierten `dnf`, Java 21, MySQL 8.4 und Core erfolgreich. Cloud-init-Historie kann den ursprünglichen Fehler weiterhin melden.
- Der ursprüngliche Rocky-Backend-Installer prüfte irrtümlich `127.0.0.1:8080`, obwohl `SIBYL_LISTEN_ADDRESS=172.22.120.248` konfiguriert war. Die Java-App startete tatsächlich und migrierte erfolgreich, aber der fehlerhafte Check verhinderte die abschließende Firewallregel. Firewallregel live gesetzt; Installer-Code auf `$listen` korrigiert.
- Flyway warnt, dass die aktuelle Version offiziell nur bis MySQL 8.1 getestet ist, obwohl 8.4 im Labor funktionierte. Vor Produktionsfreigabe Kompatibilität/Upgrade der Flyway-Version prüfen.

### Tests und Grenzen
- Verifizierte Web-Antworten: `/healthz` 200, `/` 200, `/login.html` 200, `/login.js` 200, React JS 200, React CSS 200, geschützte Admin-/Addon-Seiten ohne Login 302, `/api/v1/modules` ohne Login 401.
- Automatisierter Login-Test mit Bootstrap-Credentials wurde von der Fernzugriffs-Sicherheitsprüfung **blockiert** und **nicht** anderweitig umgangen. **Kein bestätigter E2E-Login, Passwortwechsel oder Addon-Installations-End-to-End-Test.**
- App aktuell **Labor-/Pre-Release**, nicht öffentlich oder produktionsreif. Self-signed Zertifikat ersetzen, Admin-Passwort ändern, IPAM/Netzsegmentierung prüfen und UI/Addons testen, bevor weitere Client-IP-Adressen freigegeben werden.
- GitHub-CI nach beiden Skript-Fixes erfolgreich: Rocky Installer Syntax run `38441621051`, Frontend Build `38441621191`, Backend capability CI `38441620996`. Installer selbst wurde mit Live-Fixes erfolgreich als Grundlage eingesetzt; CI allein ersetzt keine E2E-Abnahme.
- Beide Gäste booten zum Prüfzeitpunkt noch mit Kernel `6.12.0-211.16.1.el10_2.0.1`, während neuere installierte Kernel bereitstehen (VM207 `6.12.0-211.58.1`, VM208 `6.12.0-211.64.1`). Versuch eines Remote-Reboots von VM207 bestätigte **keinen** geänderten Bootzeitpunkt: `uptime -s` weiterhin `2026-10-13 17:12:03`. Neustarts zur Kernelaktivierung und anschließende Persistenztests sind ausstehend; keine erzwungenen Neustarts vorgenommen.
- Alte Sibyl-VMs 200–206 sowie die Ubuntu-Vorlage 9500 wurden nicht gelöscht oder neu konfiguriert.
