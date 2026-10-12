# Addon Worker Protocol v1

The addon's `deployment.json` may declare `migrate-db` operations with files under `migrations/VNNN__name.sql` and one `register-worker` operation pointing to an executable Java 21 JAR under `backend/`. The Core has only **generic operation handlers**. Unknown operations fail closed; no arbitrary shell scripts.

**Database isolation:** Core never runs addon SQL with its own database credentials. DB migrations require a separate, operator-provisioned database user for each addon (`SIBYL_EXT_<ID>_DB_URL`, `_DB_USER`, `_DB_PASSWORD`). Migration connection must use `sibyl_ext_<id>` as its sole default schema and have no privileges on core or other extension schemas. Migration history/checksums are stored per schema. Prerequisite privileges must be provisioned through an administrator-approved installer, not silently escalated by add-on code. Automatic migration retries after MySQL DDL failure are forbidden until reconciled.

**Worker isolation:** Generic add-on JAR is run only in a Linux `bubblewrap` sandbox with user/PID/network namespace isolation, read-only system libraries, no inherited secrets/environment, no host home, no core.env, no DB credentials, a dedicated socket and bounded resources. Missing bubblewrap or restricted namespaces means installation or invocation fails closed. The only channel to Core is a Unix-domain socket JSON protocol, **not an exposed TCP listener**. Core forwards only authenticated, size-limited, scoped requests. A real production deployment needs AppArmor/seccomp/cgroup controls, socket and worker process tests and explicit administrator approval.

**Operation ordering:** First stage/verify archive, then run ordered migrations using the scoped DB identity, start the constrained worker, verify its health, and only then register the module/navigation. Registering a dependency creates no visible user tab. On worker failure, the Core must not show a module as running. MySQL DDL is not automatically reversible; backups and forward-fix migrations are required.

## Implementierungsstand Core RC.11

Core interpretiert Addon-operationen generisch (keine Addon-IDs im Handler):
- `publish-ui`: UI im Sandbox-iFrame veröffentlichen; Core vermittelt nur API-Aufrufe an den dazugehörigen Worker über `postMessage`, CSRF und Authentifizierung
- `migrate-db`: SQL aus `migrations/VNNN__description.sql`; separat provisionierte DB-Identität `sibyl_ext_<name>`; pro Datei genau ein Statement; Migrationshistorie mit `STARTED`/ `APPLIED` und SHA-256, MySQL-Lock schützt vor paralleler Ausführung
- `register-worker`: Java 21 JAR aus `backend/*.jar` mit Main-Class; getrennte JVM in zwingender `bubblewrap`-Sandbox mit `--unshare-all`; kein Core-Secret, kein eigenes Netzwerk, begrenzter RAM, AF_UNIX-Framing, 5s Deadline

Runtime muss **explizit mit einem sicheren Linux-Host getestet werden**. Es ist kein Produktionsversprechen: bwrap ist auf dem aktuellen Cluster-3-Appserver nicht installiert, deswegen startet ohne Provisioning kein Worker. Zudem fehlen noch cgroup/SECCOMP-Profil, Live-AD-Integration und scoped DB-Capability-Broker für Daten-Persistenz aus Worker-Code. SQL-DDL ist nicht generell rollbackfähig, deshalb require vorgängiges DB-Backup und operatorseitige Wiederherstellung von gescheiterten Migrationen.

## RC.12 Safety Gate

**Worker sandbox/health must pass before any addon MySQL DDL is attempted.** If `bubblewrap` is absent or a worker cannot respond to `ping`, the installation aborts without creating addon tables. The Core MySQL transaction also registers after-completion cleanup of a newly started worker if the installation record cannot be committed. Successfully applied DDL still requires explicit operator recovery if a later database action fails.
