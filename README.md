# Sibyl System

**Test-Branch: Addon-Integration v0.7.0 (17.10.2026)**

Sibyl ist eine interne modulare Plattform mit zentraler Benutzer- und Addon-Verwaltung. Der aktuelle ausführbare Entwicklungs-Build liegt auf dem internen Entwicklungsserver. Dieses öffentliche Repository enthält bislang **noch keinen vollständigen, aus GitHub allein reproduzierbaren Core-Checkout**. Der Test-Branch dokumentiert die aktuelle Integrationsarbeit; fehlende bestehende Core-Quelltexte müssen vor einem öffentlichen Installer nachgezogen und geprüft werden.

## Module der Testintegration

| Addon | Typ | Abhängigkeit |
|---|---|---|
| Sibyl.ad | ad-auth | – |
| Sibyl.inventory | inventory-discovery | – |
| Sibyl.doc | documents | – |
| Sibyl.planning | planning | Sibyl.ad |
| Sibyl.report-book | report-book | Sibyl.ad |
| Sibyl.notes | documents | – |

Die Backend-Provider werden im aktuellen Core kompiliert und durch die Datenbank registriert. Eine unkontrollierte Laufzeitinstallation fremder Java-Klassen ist nicht vorgesehen.

**Einstellungen:** Administration → Addon-Verwaltung. Das Formular wird vom `SibylAddonProvider.settings()`-Schema erzeugt; Werte liegen in `sibyl_addon_settings`, AD-Servicegeheimnisse separat verschlüsselt in `sibyl_ad_connections`.

**Stand:** Java/Maven-Build und TypeScript/Vite-Build erfolgreich. Live-Deployment nicht durchgeführt, da der direkte SSH-Zugang zu den Test-VMs derzeit nicht autorisiert ist und der Guest-Agent die Befehlsausführung sperrt. Die Sperre wurde nicht verändert.

Siehe [Integrationshinweise](docs/ADDONS_V07.md) und die `test`-Branches der jeweiligen Addon-Repositories.
