# Sibyl Addon Deployment Contract v1

## Ziel
Der Core legt **keine installationsspezifischen Schritte** für Inventar, Planung, Wiki oder Drittanbieter fest. Jedes Addon liefert ein **`deployment.json`** (im Root des GitHub-Release-ZIPs), das seine Operationen und Abhängigkeiten beschreibt. Der Core kennt nur den Interpreter für versionierte, geprüfte Operationen. Jedes unbekannte Kommando wird standardmäßig abgelehnt.

## Phase 1 (RC.9): sichere UI-Registrierung

```json
{
  "schema": 1,
  "id": "Sibyl.example",
  "version": "0.1.0-rc.2",
  "kind": "feature",
  "dependencies": [{"id": "Sibyl.ad", "version": "0.1.0-rc.2"}],
  "operations": [
    {"type": "publish-ui", "path": "ui/index.html", "label": "Beispiel"}
  ]
}
```

Weitere Komponenten des Pakets unter `ui/` dürfen nur durch die geschützte, isolierte Frontend-Resource-Route aufgerufen werden. **Die `publish-ui`-Operation wird aus dem Addon-Paket gelesen, NICHT hartcodiert.**

Ein Dependency-Paket hat `kind: dependency`, `operations: []` und niemals einen eigenen Nav-Reiter. Die Installation bedeutet zunächst **nur Registrierung**, nicht dass LDAPS/andere Dienste bereits verbunden sind.

## Vorbereiteter API-Vertrag

- `GET /api/v1/admin/extensions/{id}/plan?version=...`: Operationen und benötigte Dependencies anzeigen.
- `POST /api/v1/admin/extensions/{id}/install`: einen verifizierten, heruntergeladenen Release mit dem Addon-Plan installieren.
- `POST /api/v1/admin/extensions/{id}/deactivate`: ohne Löschen von Daten deaktivieren.
- `GET /api/v1/extensions`: die wirklich registrierten/aktivierten Module für die Navigation abrufen.
- `GET /extensions/{id}/...`: streng isolierte, authentifizierte UI-Dateien aus freigegebenem Paket.

Das Backend legt nur **eine aktive Version je ID** ab. Abhängigkeiten werden überprüft; keine implizite Installation anderer Plugins. Bestands-ZIPs ohne `deployment.json` sind **Legacy und NICHT installierbar** über diesen Vertrag.

## Phase 2: Core SDK, Addon-eigene Worker
Spätere Version des SDK definiert weitere Operationen:

- `migrate-db`: vom Addon gelieferte Migrationen; durchgeführt von begrenztem Schema-Broker mit addoneigenen Rechten, Audit und Backup.
- `register-service`: vom Addon mitgelieferter **isolierter Worker** mit HTTP/gRPC-API, Ressourcenlimits, eigenem Benutzer/Secret-Scope und Healthchecks.
- `register-api`: Routen unter `/api/v1/ext/{id}/...` über eine authentifizierte Capability-Proxy-Schnittstelle.
- `register-scheduled-job`: zeitgesteuerter Job unter einem begrenzten Worker-Kontext.
- `register-settings`: eigenes Konfigurationsschema mit Secret-Referenzen.

**Wichtig:** Diese Operationen sind in v1 noch NICHT implementiert; der Interpreter lehnt sie ab. Ein Addon darf nicht beliebige Shell-Skripte mit Root-/MySQL-Core-Credentials ausführen. Die Ausführung erfolgt später über den isolierten Runner mit eng begrenzter delegierter Berechtigung, nicht im Sibyl-JVM-Classloader.

## Installationsreihenfolge

1. Repo/GitHub-Release finden und SHA-256 + Manifest prüfen.
2. `deployment.json` des Addons lesen, syntaktisch und semantisch gegen freigegebene SDK-Version validieren.
3. Abhängigkeiten und angeforderte Berechtigungen prüfen.
4. Vor Änderungen Installationsplan anzeigen; Admin autorisiert.
5. Operationen transaktional ausführen und versionierte Installation registrieren.
6. Healthcheck, dann Navigation anzeigen. Bei Fehler rollback.
7. Updates versionieren, nie zwei aktive Versionen mit gleicher ID.

**Kein produktiver Rollout ohne echte Datenbank-, Browser-, Auth- und Rollbacktests.**

## Implementierter generischer Phase-1-Interpreter (Core test)
Die `AddonDeploymentService`-Schnittstelle führt explizite `publish-ui`-Operationen aus Addon-ZIPs aus und speichert atomar eine aktive Version je ID in MySQL. Abhängigkeiten werden registriert und eine deaktivierte Dependency kann nicht bei aktiven abhängigen Modulen abgeschaltet werden. Keine addon-spezifischen `if/else` in diesem Installationspfad.

UI-Assets werden **nicht** mit privilegierten Shell-Befehlen kopiert, sondern aus der durch SHA-256 verifizierten Release-Datei über einen authentifizierten, CSP-sandboxed URL-Namespace ausgeliefert. Die installierte UI kann keine Core-Cookies lesen und kein API-Request mit Core-Rechten ausführen. Der Browser erhält die Navigationslinks aus `GET /api/v1/extensions`, nicht aus hardcodierten Produktlisten.

**Phase-1-Grenze:** Module mit eigenen SQL-Migrationen, API-Endpunkten oder Hintergrundservices bleiben bis zum isolierten Runner inaktiv bzw. müssen bestehende Core-Kompatibilitätsadapter nutzen. Für echte beliebige Addons ist dies ein sicherer Einstieg, noch keine vollständige allgemeine Runtime.

## Testversionen der Addons (Stand 12.10.2026)
- `Sibyl.ad v0.1.0-rc.3`: `kind=dependency`, keine Navigation
- `Sibyl.inventory v0.1.0-rc.2`: eigener UI-Deploy-Plan; benötigt AD RC.3
- `Sibyl.planning v0.1.0-rc.2`: eigener UI-Deploy-Plan; benötigt AD RC.3
- `Sibyl.doc v0.1.0-rc.2`: eigener UI-Deploy-Plan ohne AD
- `Sibyl.report-book v0.1.0-rc.2`: eigener UI-Deploy-Plan ohne AD

Aktuelle Releases sind eigenständige Beispiele für eine sichere statische UI-Installation. Alle Backend-Features, die echten AD-/MySQL-Zugriff benötigen, erfordern die folgende Worker-Phase. Insbesondere sind Inventar- und Planungsdaten vorerst Eingabedaten, und Dokumentation/Berichtsheft speichern noch nicht dauerhaft.

**Diese Core-Version wird als v0.1.0-rc.10 gebaut.** Ältere ZIP-Releases ohne `deployment.json` bleiben Downloads, nicht aktivierbare Deploy-Pakete nach Contract v1.
