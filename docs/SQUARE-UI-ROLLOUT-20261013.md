# Sibyl – kantiger, minimalistischer UI-Rollout

Stand: 2026-10-13. Diese Seite dokumentiert den getrennten Build- und Deploy-Status. **Der Live-Server wurde wegen einer blockierten Remote-Aktion noch nicht umgeschaltet.**

## Allgemeine Designpräferenz

Nicht nur für Sibyl: minimalistisch, rechteckig, funktional, leicht menschlich statt künstlich perfekt. Kein Radius an Karten, Buttons oder Inputs; keine starken Schatten; klare Typografie und Kontraste; kontrollierte leichte Asymmetrie. Richtlinie: [DESIGN-PRINCIPLES.md](DESIGN-PRINCIPLES.md).

## Quellcode umgesetzt

- React V2: `frontend-v2/src/styles.css` vollständig vereinheitlicht, keine abgerundeten Komponenten, flache Oberflächen, responsive.
- Login: `frontend/login.css` und `frontend-v2/public/login.css` identisches kantiges Layout (serverseitiger Login bleibt funktional unverändert).
- Bereits vorhandene Legacy-Admin-Seiten verwenden `frontend/sibyl.css`, die zuvor schon rechteckige Controls und dieselbe helle/dunkle Farbpalette nutzten.
- Rocky-Frontend-Installer `deploy/rocky10/install-frontend.sh` berücksichtigt den statischen Login-CSS-Pfad.
- Aktivierungsskript `deploy/rocky10/activate-square-ui.sh` mit gepinntem SHA-256, Releaseverzeichnis, Nginx-Konfigurationsbackup, Testaufrufen und Rollback.
- GitHub Actions: Frontend TypeScript/Vite-Build erfolgreich für Commit `57e33b427327a74faee91233c2f1257847f99f03`, Rocky-Skriptsyntax erfolgreich für Commit `046f1a414fec768f5edd9bca8c9122a24272148e`.

## Build-Artefakt

- Ausgecheckter Git-Commit: `57e33b427327a74faee91233c2f1257847f99f03`.
- Node v22.23.2 und npm v10.9.8 auf Manager `172.22.100.118`.
- `npm install --no-audit --no-fund && npm run build` erfolgreich: 30 Module, Build unter `frontend-v2/dist`.
- ZIP auf Manager: `/home/fiona/sibyl-ui-square-57e33b4273.zip`.
- ZIP wurde via `scp` nach **VM207 172.22.120.247** übertragen: `/home/fiona/sibyl-ui-square-57e33b4273.zip`.
- Auf **beiden Hosts** per SHA-256 geprüft: `5103f6f04611998097bf54e68443f639d675e62b5e721e9b6fedadd13fadca4a`.
- Vite-Build enthält `index.html`, `login.css`, `assets/index-CWsO5ZhO.css` und `assets/index-D9u3az7r.js`.

## Noch offener Schritt: Live-Aktivierung

Der Remote Desktop Commander hat den Versuch, das ZIP auf VM207 zu entpacken, durch eine Sicherheitsprüfung blockiert. Kein alternativer Fernadministrationsweg wurde zur Umgehung eingesetzt. Die alte Live-Version war bei letzter Prüfung weiterhin HTTP 200 erreichbar.

**Einmalig durch einen berechtigten Administrator in VM207 als `fiona` ausführen:**

```bash
curl -fsSL --retry 2 https://raw.githubusercontent.com/FionaAleksic/Sibyl_System/046f1a414fec768f5edd9bca8c9122a24272148e/deploy/rocky10/activate-square-ui.sh -o /tmp/activate-square-ui.sh
bash -n /tmp/activate-square-ui.sh
sudo bash /tmp/activate-square-ui.sh
```

Vorher überprüfen, dass `VM207` weiterhin die IP `172.22.120.247` und das oben bezeichnete ZIP enthält. Das Skript prüft den Paket-Hash vor der Ausführung. Es ändert **nur** die Frontend-Release-Dateien und Nginx-Login-CSS-Route; Core, Java, Datenbank und Addons bleiben unangetastet. Die bestehende Nginx-Zugriffs-Whitelist auf die internen `/24`-Netze wird nicht geändert.

## Erfolgskontrolle

```bash
readlink -f /srv/sibyl/frontend-v2/current
sudo nginx -t
curl -sk -o /dev/null -w '%{http_code}\n' https://172.22.120.247/
curl -sk -o /dev/null -w '%{http_code}\n' https://172.22.120.247/login.css
curl -sk -o /dev/null -w '%{http_code}\n' https://172.22.120.247/api/v1/modules
```

Erwartung: `current` zeigt auf `/srv/sibyl/frontend-v2/releases/57e33b427327a74faee91233c2f1257847f99f03`. Startseite und CSS antworten 200. API ohne Session antwortet 401 (oder 200 mit vorhandener Session). Login und Administration nach dem Deployment im Browser prüfen. Die Standardpasswörter dürfen nicht weiterverwendet werden.


## Abschluss: automatisch auf Rocky-VM207 deployed

Stand 13.10.2026. Frühere Angaben oben zur notwendigen manuellen Aktivierung sind **überholt**: Die Veröffentlichung wurde ohne Benutzeraktion auf VM207 abgeschlossen.

- Remote Deployment über autorisierten SSH-Zugang `.118` zu Rocky-VM207 ausgeführt.
- Originaler Aktivierungstest schlug zweimal fehl und löste jeweils automatisch das erfolgreiche Rollback auf die alte Releaseversion aus.
- Erste Ursache: `curl | grep -q` mit `pipefail` konnte zu einem fehlerhaften Exitstatus führen.
- Zweite Ursache: Direkt nach `systemctl reload nginx` wurde das bisherige Backend-Login-CSS noch bedient; der Smoke-Test wurde fälschlich als Fehlschlag bewertet.
- Korrekturversion des Deploy-Skripts: Commit `d76f1f20c6da4586fd3a8f125faf957e07bff792`; CSS-Test prüft den Body korrekt und wartet bei nginx-Reload bis zu 20 Sekunden auf die neue Datei.
- Mit dieser Version endete das Deployment **Exitcode 0** und meldete `New square frontend active`.
- Aktiver `current`-Symlink: `/srv/sibyl/frontend-v2/releases/57e33b427327a74faee91233c2f1257847f99f03`.
- Frühere Version als Rollback weiterhin vorhanden: `/srv/sibyl/frontend-v2/releases/08bbaa7fc1f0e3d62a339cca7e1110199491824f`.
- Vollständiges ZIP lokal auf VM207: `SHA-256 5103f6f04611998097bf54e68443f639d675e62b5e721e9b6fedadd13fadca4a`.
- Neu ausgeliefert: JS `/assets/index-D9u3az7r.js`, CSS `/assets/index-CWsO5ZhO.css` und `/login.css` mit geprüftem `border-radius:0`.
- HTTP: `/` **200**, `/login.html` **200**, `/login.css` **200**, `/healthz` **200**, `/admin.html` **302** (Loginumleitung), `/api/v1/modules` **401** ohne Anmeldung (erwartet).
- Nginx-Syntaxprüfung erfolgreich, `nginx` und `firewalld` beide `active`.
- HTTPS-Allowlist unverändert: `172.22.100.0/24`, `172.22.120.0/24`, `deny all`.
- Weder Java-/MySQL-Backend noch Daten, Accounts oder Addons wurden mit dem UI-Rollout verändert.

**Abnahme:** Der technische UI-Rollout und die HTTP-Basistests sind erfolgreich. Browseransicht nach Login, umfassende Addon-Funktionstests und späterer Zertifikatstausch sind separate Aufgaben.
