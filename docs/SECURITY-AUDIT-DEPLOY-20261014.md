# Sibyl – Security/Audit-Rollout, 14.10.2026

## Nachgewiesener Zustand

**Backend VM208** (`172.22.120.248`, Rocky Linux 10.2):

- Aktiviertes versioniertes Core-Pre-Release: `v0.1.0-rc.18-dev.1` (GitHub Release target commit `58a48913dd369042e8c196bdca01c1df005f204b`).
- Archiv-SHA-256 über GitHub Release metadata: `14d625d79e622101bcb04ac5e05f6f89282c3afaec80751be7f5f5f0fc11333b`.
- Deployment über `deploy/rocky10/activate-security-candidate.sh`; identische bisherige und neue Flyway-Migrationsdateien wurden vor Aktivierung erzwungen.
- Alter Kern bleibt erhalten unter `/opt/sibyl/sibyl-core-0.1.0-rc.17-dev.1.jar`.
- Nach erfolgreicher Aktivierung kontrolliert: `systemctl is-active sibyl-core = active`, `/actuator/health = HTTP 200`, anonymes `/api/v1/admin/audit = HTTP 401`, `/api/v1/auth/csrf = HTTP 200`.
- Über Frontendproxy anonymes `/api/v1/admin/audit = HTTP 401`, `/healthz = HTTP 200`.
- Vorab-CI mit MySQL: Backend erfolgreich; React-Typecheck/Build erfolgreich.

**Frontend VM207** (`172.22.120.247`, Rocky Linux 10.2):

- Bisheriger Stand ist **weiterhin** `/srv/sibyl/frontend-v2/releases/7ec2529cdcee75142ccd595aff86cd77e3049fd6`.
- Neues React-V2-Pre-Release `v0.1.0-rc.18-ui.1`, GitHub target commit `33a24a524f88113c9b6a576338daf39ba6382dda`.
- Asset `sibyl-frontend-v2-dist.zip`, SHA-256 `60db88dbc3dac3bf6784b918fdecc98f7cc19cf0329a3708f75b8d91e6d5ef3f`.
- ZIP und geprüfter Installer `activate-frontend-release.sh` unter `/home/fiona/` auf VM207 bereitgelegt; SHA-256 vor/nach Transfer identisch geprüft.
- **Nicht aktiviert**: Der Aufruf `sudo ... activate-frontend-release.sh` über den Fernzugriff wurde durch den OpenAI-Sicherheitscheck blockiert. Keine Umgehung des Checks versucht. Neues React-Audit-Menü ist daher noch NICHT live.

## Normaler Administrator-Rollout, wenn über freigegebenen Betreiberkanal zulässig

Auf VM207 im normalen autorisierten Betriebskanal als berechtigter Administrator:

```bash
sudo env \
  FRONTEND_ZIP=/home/fiona/sibyl-frontend-v2-dist-rc18-ui1.zip \
  FRONTEND_SHA256=60db88dbc3dac3bf6784b918fdecc98f7cc19cf0329a3708f75b8d91e6d5ef3f \
  FRONTEND_COMMIT=33a24a524f88113c9b6a576338daf39ba6382dda \
  bash /home/fiona/activate-frontend-release.sh
```

Der Installer prüft ZIP/Hash/Inhalt, Nginx-Konfiguration, HTTPS-Health und wechselt atomar per Symlink; bei Fehlschlag stellt er das vorherige Frontend wieder her. Nicht per Ausweichzugang eine Fernzugriffssperre umgehen.

## Abnahmeoffen

- Anmeldung über einen echten **berechtigten Testaccount**: `ADMIN` kann Audit lesen, `AUDITOR` kann ausschließlich Audit lesen, `USER` bekommt 403.
- Erste Login-Passwortrotation des Auditor-Kontos, alte Session/Logout, Pagination, Addon-Navigation und UI auf VM207 E2E.
- Audit-Aufbewahrung und externe manipulationsgeschützte Ablage; Rate-Limits für fehlgeschlagene Logins.
- MFA/OIDC/Secret Broker, AD-/Netz-/Endpoint-Datenquellen und Worker-Isolation aus der Roadmap.
- Externe DB-Backups samt Restoreprobe, vertrauenswürdiges TLS-Zertifikat und DMZ-Trennung sind vor allgemeiner Produktionsfreigabe weiterhin erforderlich.

**Ergebnis:** Backend-Sicherheitsbasis auf Rocky-Pilot aktiv; Frontend sicher bereitgestellt, Aktivierung aus Fernzugriff blockiert. Keine Produktivfreigabe behauptet.
