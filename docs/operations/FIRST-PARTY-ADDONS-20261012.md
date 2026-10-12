# Sibyl Core – First-party Addon Rollout (12.10.2026)

## Released, checked by CI
| Addon | Current prerelease | Capability | GitHub |
|---|---|---|---|
| Sibyl.ad | v0.1.0-rc.2 | Read-only LDAPS: users, groups, computers | https://github.com/FionaAleksic/Sibyl.ad/releases |
| Sibyl.inventory | v0.1.0-rc.1 | Full IPv4 subnet inventory + ICMP evidence state | https://github.com/FionaAleksic/Sibyl.inventory/releases |
| Sibyl.planning | v0.1.0-rc.1 | Fair three-slot rotation, per-slot histories | https://github.com/FionaAleksic/Sibyl.planning/releases |
| Sibyl.doc | v0.1.0-rc.1 | Wiki versioning, optimistic locking, archive | https://github.com/FionaAleksic/Sibyl.doc/releases |
| Sibyl.report-book | v0.1.0-rc.1 | Weekly training-report model, time validation, Markdown export | https://github.com/FionaAleksic/Sibyl.report-book/releases |

All five repositories use `test` branches and independently published, immutable, checksum-verified release ZIPs. Every archive contains `module.json` and a compiled Java 21 JAR. Inventory and Planning require the AD provider in their manifest; the Doc and Report Book packages do not.

## Critical limitation of deployed Core v0.1.0-rc.7
The deployed Core is a GitHub **release downloader**, NOT an executable Java addon runtime. POST `/api/v1/admin/addons/{id}/download` returns `downloaded_not_activated`. `GitHubReleaseService` verifies archive digest and module ID/version but does not load an addon JAR, resolve dependencies, expose module routes, run DB migrations or register navigation.

This commit adds `Sibyl.report-book` to the catalog in the **test branch only**, for inclusion in a future Core release. The live `rc.7` JAR has its catalog embedded, so this change **does not** make it appear on the currently deployed live website.

## Next Core platform implementation, before addon activation
1. Define versioned `sibyl-addon-sdk` (capability interfaces, lifecycle `install/activate/deactivate/uninstall`, data-directory and config-schema interfaces).
2. Trusted release verification + single-version inventory, dependency graph validation, no arbitrary classpath execution. Isolate classloaders or use separate sandboxed processes and explicitly scoped permissions.
3. Add per-addon Flyway migrations, migration lock, backup/restore and per-addon schema boundaries in MySQL.
4. Implement server-side scoped AD capability with a single protected service credential. Only validated providers may access directory reads; consumers never receive the password.
5. Use role-checked, CSRF-protected API routing and register navigation only when activation succeeds; no anonymous access to private module assets.
6. Port original interfaces pixel-/behavior-faithfully using the pinned Niko Test and production source snapshots in the addon repos; do not run legacy `app.webplatform.*` directly in the new Core.
7. Add high-coverage tests: ZIP digest, zip-slip, manifest ID/version, dependency conflicts, unauthorized access, module migration rollback, stateful reload, AD mock, inventory collectors, roster fairness, UI parity and Playwright/browser.
8. Activate one addon at a time only on Cluster-3 staging (`172.22.120.240/241/242`). Preserve existing MySQL accounts and rollback. Never change production without a separate approval.

## Source provenance
Legacy Niko references: https://github.com/FionaAleksic/Niko_Web-System/tree/Test/Addons, and existing production-source branch `main`. Historical sources are stored under `reference/` and are not included in published runtime archives.

## Release discipline
Always use a new SemVer prerelease tag for subsequent changes; do not overwrite already published ZIPs. New Core release must carry the refreshed catalog before Berichtsheft can be discovered in the Core UI.
