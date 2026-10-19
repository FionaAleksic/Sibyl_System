# Sibyl System v2 – erweiterbare Ausbildungsplattform

**Stand:** 12.10.2026 | **Scope:** GitHub-Featurebranch `feature/deck-inspired-v2` und Proxmox **Cluster 3** (Spielwiese). Kein Cutover der bisherigen RC.7/RC.16 und keine Änderung an Cluster 1/2.

## Designentscheidung

Deck (https://github.com/Nodvard/deck) dient **ausschließlich als Inspirationsquelle für Prinzipien**: Core-Extension-Grenze, Capability-Registry, deklarative Settings, Aktionsfreigaben, Provider für gemeinsame Ressourcen, robustes Addon-Fehlerverhalten. Kein Deck-Quellcode übernommen. Die Deck-Lizenz **PolyForm Noncommercial 1.0.0** ist keine freizügige Lizenz für kommerzielle Derivate.

**Verbindlicher Stack:** React + TypeScript (neue Shell), Java 21 + Spring Boot 3.5 (API), **MySQL 8** mit Flyway und getrennten Addonschemas. Firmenneutral, ohne AD-Pflicht; ein einziges aktives Release pro Addon-ID. Installiert werden nur GitHub-Release-Artefakte mit überprüften SHA-256-Digests, nie Branch-ZIPs.

## Zielumgebung und Ist-Stand

| Rolle | VMID | Netzwerk | Stand |
|---|---:|---|---|
| bisherige Edge | 200 | 172.22.120.240 | **Bestand**, nicht verändern |
| bisheriger Core | 201 | 172.22.120.241 | **Bestand**, nicht verändern |
| bisherige MySQL | 202 | 172.22.120.242 | **Bestand**, nicht verändern |
| v2 React Frontend | 203 | 172.22.120.243 | neue Labor-VM auf Node 1 |
| v2 Java Backend | 204 | 172.22.120.244 | vorgesehen; VM-Klon durch Remote-Sicherheitscheck blockiert |

Cluster 3: `NL0078-PVE-lath` (172.22.120.216), `NL0080-PVE-shse` (.217), `NL1337-PVE-maeb` (.218); vorhandenes Ubuntu 24.04 Cloud-Init-Template 9500 auf Ceph Senfpool. 172.22.100.118 ist Verwaltungs-Jump mit gesondertem SSH-Schlüssel. IP-Konflikte sind nicht nur anhand fehlenden ICMPs auszuschließen; .243/.244 dauerhaft im IPAM reservieren.

```text
Browser --> [echte DMZ: Edge/WAF/TLS] --/api--> Backend v2 --> eigene MySQL-v2-Datenbank
                             |
                             +--/--> React v2 Frontend (statische Assets)
Backend v2 --> Permission Broker --> Addon-Worker [isolierte Prozesse, ohne Core-Secrets]
                                  --> IdentityProvider / SearchProvider / InventoryProvider
                                  --> NetworkProvider / TrainingLogProvider / CourseProvider
```

**Die derzeitige Bridge vmbr0 ist KEINE DMZ.** Sie erfüllt die Ausbilderauflage nicht. Segmentierung/Firewall mit echten Zonen, Rückwegfilterung und ACLs ist eine gesonderte Netzwerkänderung. V2-Frontend und Backend bleiben bis dahin intern und in der Spielwiese. Die Bestands-DB wird nicht ohne gesicherte Migration für V2 geöffnet.

## Core- und Addon-Vertrag

1. **Core owns:** Accounts, Session/MFA/RBAC, Settings/Branding/Themes, Addon-Registry, Host/Device-IDs, Audit, Berechtigungen, Secret-Broker, Scheduler/Events/Outbox und API-Versionen. Kein Core-Import eines bestimmten Addons.
2. **Addon owns:** `module.json`, `deployment.json`, JSON-Schema für Admin-Settings, deklarative `provides` und `requires` Capabilities, UI-Bundle, Worker-Archiv und eigene Datenmigrationen. Aktive Versionen sind eindeutig nach Addon-ID.
3. **Capability:** stabile ID (z. B. `identity.users`), SemVer-API, Anbieter-ID und aufrufbare benannte Methoden. Provider-Runtime folgt dem manifestierten Vertrag und wird erst nach Release-Verifikation/Permission Approval registriert.
4. **Sicherheit:** Drittanbieter-JARs niemals im Core-JVM-Classloader. Worker sind ohne Root und Core-Secrets isoliert (bubblewrap/cgroup/seccomp oder vergleichbare VM-Isolation), kommunizieren ausschließlich über begrenzte IPC-/API-Aufrufe. Credentials bleiben beim Core-Broker; **ein Manifest-Recht ist keine Sandbox**.
5. **Activation:** Status `downloaded -> verified -> prepared -> migrating -> active`. Keine sichtbare Navigation vor aktiver UI/Backend-Health. Fehler führen zu `error`; Core und andere Addons bleiben erreichbar. Rollback braucht DB-Backup/Forward-Migration-Plan, nicht blindes Rückwärts-DDL.
6. **Nav & Settings:** React Shell generiert Navigation ausschließlich aus aktivierten Feature-Addons und berechtigtem Benutzerkontext. Dependency-Addons (z. B. AD) haben keine Benutzernavigation. Addon-Settings aus JSON Schema; Secret-Felder als Referenzen statt Klartext; keine doppelten `Planung`-Reiter.

Die neue `CapabilityRegistry` im Code ist zunächst **nur ein in-memory, read-only introspektierbarer Kernvertrag**, **noch kein** autorisierter Worker-Dispatcher oder persistenter Addon-Resolver. Providerregistrierung und Aktivierung sind absichtlich erst nach Security-/Lifecycle-Integration vorgesehen.

### Beispiel-Manifest-Erweiterung (v2, Spezifikation – noch nicht installierbar)
```json
{
  "id": "Sibyl.inventory",
  "version": "1.0.0",
  "apiVersion": "1.0.0",
  "type": "feature",
  "provides": [{"id":"inventory.devices","apiVersion":"1.0.0","methods":["listDevices"]}],
  "requires": [{"id":"identity.users","apiMajor":1,"optional":true}],
  "permissions": ["inventory.read", "inventory.write", "hosts.observe"],
  "settingsSchema": "settings.schema.json",
  "frontend": {"entry": "ui/index.html", "navigation": "Inventar"},
  "backend": {"worker": "backend/worker.jar", "sandbox": "required"}
}
```

## Ausbilder-Abnahmematrix

| Pflicht | v2-Fähigkeit / Abnahmetest | Aktueller Zustand |
|---|---|---|
| Suche lokal → Web | AuthZ-geprüfte Suche in Wiki/Kursen/Inventar; Web-Fallback **nur nach ausdrücklicher Freigabe**, keine stillen internen Query-Leaks | **offen** |
| Auto-Inventar | Agent/Collector für Software, Rechner, Peripherie, Status, Quelle und Beobachtungszeit; autorisierte Netzwerksegmente | **offen** |
| IP-/Netzverwaltung | Subnetze, IPAM, DHCP-Leases/Reservations, DNS, Adresskonflikte; fehlendes ICMP nie allein als frei werten | **offen** |
| Berichtsheft | Persistenz, Azubi/Ausbilder-Rollen, Genehmigung, Revisionen, PDF/Export und IHK-konformer Prozess | **teilweise in RC Addon**, Signatur offen |
| Kurssystem | Rollen, Kurse, Material, Aufgaben, Tests, Bewertung, Fortschritt | **offen** |
| AD-Kommunikation | Optionaler LDAPS-Provider über Identity Capability; servicegebunden, nur lesend bis freigegebene Write-Scopes vorliegen | **Release-CLI vorhanden**, Plattformintegration offen |
| DMZ-Webserver | Tatsächliche isolierte Firewall-Zone, nur TLS 443 zur Edge, gezielte Edge→API/Assets, DB niemals DMZ | **nicht erfüllt** |

**Weitere Bestands-Addons:** `Sibyl.planning`, `Sibyl.doc` (Wiki), `Sibyl.report-book` behalten Releases und Daten; Funktionsparität zur alten Niko-Seite separat durch E2E/Regressionstest nachweisen.

## Abnahme vor Umschaltung

- Isolierte VMs .243/.244 laufen mit gepinntem SSH-Key, Guest Agent und unabhängigem Backend-Service.
- Frontend-`dist` aus einem **veröffentlichten v2 GitHub-Release** mit SHA-256, HTTPS über Edge und Auth-Guard. Kein direktes Veröffentlichen von Branch-Code.
- MySQL-v2-Schema mit eigenen Credentials, Flyway, verifiziertem Backup+Restore und Schema-Migrations-Test.
- Negativtests 401/403, CSRF, Secret-Zugriff, fremde Addon-Daten, gesperrte Worker-Funktionen, SSRF, Pfad-Traversal, unsichere Downloads.
- Capability-Providerregistrierung an installierte, aktive Addon-ID gekoppelt; Deployment Rollback und Audit verifiziert; kein stilles Aktivieren defekter Erweiterungen.
- Funktions- und Ausbilder-Abnahme für alle sieben Pflichten oben. Bis dahin kein Produktionsrelease.

## Rollout-Reihenfolge

1. Cluster-3-VM203 testen/härten; VM204 erst nach explizit erlaubtem Provisioning erstellen und dieselben Preflights wiederholen.
2. React Shell auf GitHub-`feature/deck-inspired-v2`; Core-Capability-API und Tests ergänzen.
3. V2-Backend per CI bauen und als Pre-Release veröffentlichen; auf **neuer** App-VM installieren (nicht auf .241).
4. Separates V2-Schema/Secrets, Worker-/Capabilities-Broker, Dependency-Lifecycle implementieren und real testen.
5. Kurs-, Such-, Netzwerk-, Berichtsheft- und AD-Provider in kleinen einzeln testbaren Releases.
6. Vor Produktivfreigabe echte DMZ, Backup-Restore, E2E, Security-Gates und Ausbilderabnahme.



## Korrektur der Bereitstellungsart (12.10.2026, 15:54 UTC)

**Verbindliche Betreiberanweisung: Keine VMs klonen.** Beide Sibyl-V2-VMs sollen über `qm create` auf dem Cluster 3 **neu angelegt** und mit einer **frisch importierten Betriebssystem-Image-Systemplatte** initialisiert werden. Cloud-init darf für Netz, SSH-Public-Key und Benutzerzugang verwendet werden, **nicht** als VM-Klon.

**Nachgewiesener Ist-Stand:**
- VM203 (`sibyl-frontend-v2`, .243) wurde vor dieser Korrektur aus VM9500 geklont, gestartet und mit Nginx eingerichtet. **Entspricht nicht mehr der Vorgabe und ist zu ersetzen.** Nicht als fertig neu installierte VM deklarieren. Sie wurde danach kontrolliert heruntergefahren und `onboot=0` gesetzt; die VM-Konfiguration/Platte bleibt bis zur sicher verifizierten Ersetzung erhalten. Die IP .243 ist dadurch nicht mehr von dieser VM aktiv belegt.
- VM204 (`sibyl-backend-v2`, .244) wurde **neu mit `qm create`** auf `NL1337-PVE-maeb` angelegt, 4 vCPU, 8 GiB RAM, virtio-Netz auf vmbr0, Cloud-init-Netzadresse .244/24 und DNS .202, SSH-Public-Key. **Keine Festplatte, kein Betriebssystem, gestoppt**: der anschließende `qm importdisk` aus dem offiziellen Ubuntu 24.04 Cloud-Image wurde durch den Remote-Sicherheitscheck geblockt. Als Schutz steht `onboot=0`. Keine alternative Remote-Methode zum Umgehen der Sperre verwenden.
- Die vorhandene Installationsquelle auf dem Cluster-3-Node `NL1337-PVE-maeb` ist `/var/lib/vz/template/iso/sibyl-ubuntu-noble-cloud.img`, SHA-256 `6a81c37564db9b1ee84e141922625e1d7c5b389b99bb3c572e0243607d5bb4d2`, in früheren Arbeiten anhand Canonical SHA256SUMS verifiziert und heute erneut lokal gehasht. Wird als Image (nicht als VM-/Template-Datenträger) verwendet.
- Ceph HEALTH_OK nach Kontrolle, andere VM200–202 nicht verändert. Das Netzwerk ist weiterhin keine echte DMZ.

**Nächster administrativer Schritt:** Über einen ausdrücklich freigegebenen Proxmox-Administrationsweg die OS-Image-Systemplatte für VM204 bereitstellen, 50 GiB dimensionieren, Cloud-init-CD anlegen und danach booten. Erst wenn VM204 zugänglich ist, VM203 kontrolliert außer Betrieb setzen und vollständig durch eine ebenfalls via `qm create` und Image-Import neu erstellte VM gleichen Zwecks/IP ersetzen. Vorher aktuelle Konfiguration dokumentieren und sicherstellen, dass im Betrieb nur eine VM die IP .243 verwendet. Keine Datenmigration/Produktivumschaltung ohne eigene Tests.
