# Cluster 3 – SSH-Banner-Timeouts beim Sibyl-Installer

## Diagnose am 10.10.2026

Der `v0.1.0-rc.4`-Cluster-Installer bricht gelegentlich bereits in der **nur lesenden Vorprüfung 1/8** ab, mit:

```text
Connection timed out during banner exchange
Connection to 172.22.120.240 port 22 timed out
```

Der betroffene Zielhost variiert, beispielsweise `.240` und `.242`. Auf beiden VMs laufen SSH-Daemon und andere Netzwerkdienste. Public-Key-Anmeldungen mit dem auf dem Verwaltungsserver installierten Lab-Schlüssel funktionieren in der Regel. Der Fehler wurde bei wiederholten Verbindungen von `.118` reproduziert. ICMP-Ping war dabei stabil.

**Nicht nachgewiesen:** Eine definitive Ursache auf dem SSH-Server oder Router. Der VMware-Netzwerkadapter `ens34` auf `.118` zeigte viele RX-Drops, aber ein kausaler Zusammenhang ist nicht belegt. Keine voreilige Änderung an Linux-SSH-Firewall oder Routing durchführen.

## Clientseitige Abhilfe

Auf dem Verwaltungsserver `172.22.100.118` ist für **genau drei neue Labor-VMs** eine OpenSSH-Clientkonfiguration aktiv, die bereits authentifizierte Verbindungen wiederverwendet:

```sshconfig
Host 172.22.120.240 172.22.120.241 172.22.120.242
    ControlMaster auto
    ControlPath /home/fiona/.ssh/sibyl-cluster3-%C
    ControlPersist 120
    ConnectionAttempts 2
    ConnectTimeout 12
```

Pfad: `/home/fiona/.ssh/config` (Dateimodus 0600; Verzeichnis `~/.ssh` 0700). Im bestehenden Installer bleiben `StrictHostKeyChecking=yes`, dedizierter SSH-Key und fixierte `known_hosts_sibyl` **ausdrücklich aktiviert**.

Ergebnis: Direkt nach Konfigurationsänderung acht erfolgreiche Verbindungen je Host, insgesamt **24/24**. Ein längerer Belastungstest ist noch offen.

## Voraussetzungen und Grenzen

- Die SSH-Clientoptimierung löst nur das Problem **vor** der geheimnishaltigen Datenbankbereitstellung.
- `v0.1.0-rc.4` enthält **noch keine integrierte Preflight-Retry-Logik**. Eine entsprechende Codeaktualisierung wurde durch die Remote-/Repository-Sicherheitsprüfung verweigert und nicht freigegeben.
- Automatisierter Datenbank-Credential-Transfer wurde separat blockiert. Eine alternative Übertragungsmethode zum Umgehen dieser Zugriffssperre ist nicht zulässig. Für vollständiges Deployment muss der erlaubte Deployment-/Secrets-Kanal ausdrücklich autorisiert werden.
- `rc.4` auf der App-VM ist vorab geprüft und staged, aber **noch nicht aktiviert**. MySQL auf der DB-VM läuft; die Kern-Datenbanktabellen werden erst nach freigegebener erfolgreicher MySQL-Core-Verbindung migriert.
- Wer später eine neue Sibyl-Instanz installiert, darf sich **nicht** auf diese hart codierten Labor-IP-Adressen stützen. Die generische Standardinstallation benötigt eine eigenständige, getestete Automatisierung.

## Nächste Qualitätsanforderungen für eine neue Installer-Version

1. SSH-ControlMaster mit begrenzter Persistenz und Hostkey-Verifikation als Option.
2. Maximal drei Retry-Versuche **nur für idempotente, lesende Vorabprüfungen** bei verifizierten temporären SSH-Transportfehlern.
3. Keine Wiederholung mutierender Schritte bei unklarer Ausführung; vor Retry muss deren tatsächlicher Status geprüft werden.
4. Automatisches Backup, Fehlerabbruch vor Core-Umschaltung, Rollback und klarer Abschlussbericht.
5. End-to-End-Integrationstest inklusive berechtigtem Secrets-Transport auf separater frischer Laborumgebung.

**Verifizierter Release:** https://github.com/FionaAleksic/Sibyl_System/releases/tag/v0.1.0-rc.4
