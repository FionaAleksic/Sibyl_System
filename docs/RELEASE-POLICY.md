# Sibyl System – verbindliche Release-Regeln

- `main` ist der stabile Entwicklungszweig. `test` ist die Vorab- und Integrationsentwicklung.
- **Ausgeliefert und installiert werden ausschließlich GitHub-Releases.** Kein Download/Installation aus Branch-ZIP, Branch-HEAD oder ungetaggtem Commit.
- Stabil: `releases/latest` (nur veröffentlichte nicht-prerelease Releases); Test: nur veröffentlichte Pre-Releases aus `releases`. Draft-Releases sind nie installierbar.
- Jedes Release/Pre-Release erhält ein unveränderliches SemVer-Tag (`vMAJOR.MINOR.PATCH[-rc.N]`) und enthält ein paketiertes, getestetes Release-Asset.
- Build-Artefakte enthalten mindestens `module.json`, `id`, `version`, `apiCompatibility`, Abhängigkeiten und Dateiprüfsummen. Der Addon-Manager lädt zunächst nur verifizierte ZIPs herunter; die Code-Aktivierung braucht eine separat geprüfte Module-Runtime.
- Release-Digest SHA-256 muss für jede Datei überprüft werden. Veröffentlichungen werden nicht unbemerkt überschrieben; ein Fehler erzeugt ein neues Release.
- GitHub-App-Installation oder eingeschränkter PAT für private Repositories **nur serverseitig**. Nie im Browser, JavaScript-Bundle oder Quellcode.
- Pro Modul-ID nur eine installierte Version in einer Installation. Upgrade mit kontrolliertem Staging, Migration, Healthcheck und Rollback.
- Quellen müssen vor Aufnahme ins Organisations-Repository/Allowlist geprüft werden; keine vom Nutzer frei eingegebenen ZIP-URLs herunterladen.
- Fehlender Release ist ein sichtbarer Zustand `KEIN_RELEASE`. Niemals automatisch auf `main` oder `test` zurückfallen.
- Backend-JARs/JS aus unbekannten ZIPs dürfen ohne Isolierung/Signaturprüfung nicht gestartet werden.
- Repositories (Stand 09.10.2026): `Sibyl_System` (Core), `Sibyl.ad`, `Sibyl.inventory`, `Sibyl.planning`, `Sibyl.doc`. Veröffentlicht: **`Sibyl.ad v0.1.0-rc.1`**, **`Sibyl_System v0.1.0-rc.1`**. Weitere Pre-Releases werden erst nach bestandenen Tests freigegeben.
- Zu klären/neue Repositories: `Sibyl.search`, `Sibyl.network`, `Sibyl.training-log`, `Sibyl.courses`, optional `Sibyl.wiki`. Diese existieren in der geprüften Repo-Liste bislang nicht.
- Cloud/Lab auf Cluster 3 benötigt vor produktiver Nutzung eine echte DMZ. Ein normaler LAN-VM-Adapter ist keine DMZ.

Design-Vertrag: Das frühere CSS aus `Niko_Web-System:Test/frontend-original-snapshot/styles.css` wurde unverändert unter `design/legacy-reference.css` archiviert. Sichtbare Designs werden anhand dieser Referenz geprüft (inklusive Hell/Dunkel, Topbar, Abstände, Typografie und eckige Bedienelemente); firmenspezifische Logos/Texte dürfen nicht festkodiert werden.
