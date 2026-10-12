# First-party Addon Bridge – Core RC.8

RC.7 downloaded ZIP archives but did not expose functions/navigation. RC.8 implements a **narrow, digest-pinned first-party compatibility bridge** (not a general untrusted-code plugin runtime).

## Enabled by existing verified downloads
- Sibyl.inventory v0.1.0-rc.1: actual `InventorySnapshot.addressList` from released JAR via limited-IPAM endpoint.
- Sibyl.planning v0.1.0-rc.1: actual `FairRotation.generate` from released JAR, independent per-slot planning preview.
- Sibyl.doc v0.1.0-rc.1: MySQL-persistent wiki-compatible simple editor, optimistic revision versioning and account-aware edits. Core adapter implements persistence; **the addon's in-memory Wiki JAR is not executed**.
- Sibyl.ad v0.1.0-rc.1: dependency present but no AD read/live login; no nav tab.
- Sibyl.report-book v0.1.0-rc.1: catalog entry, but no full adapter; no data writes until implemented.

## Security and lifecycle
- Core verifies local ZIP against hardcoded trusted SHA-256 and manifest ID/version on every use. Unknown changed releases are excluded. ZIPs only from approved GitHub repos; downloaded file is not an installed binary with arbitrary capabilities.
- Routes under /api/v1/modules require login. CSRF on modifying requests. All module forms escape output via textContent, not unsanitized HTML. No direct LDAP passwords, no old productive database joins.
- Core does not support arbitrary third-party JAR activation or full plugin runtime sandboxing. No implicit AD writes, no network scanning.
- Wiki Flyway V2 creates only `sibyl_wiki_pages`, does not drop or alter existing user tables.

## UI
- After login, Core fetches `/api/v1/modules` and appends nav items for verified installed first-party features.
- `/module.html?id=Sibyl.inventory`, `Sibyl.planning`, `Sibyl.doc`. The page loads actual module adapter operations.
- A runtime task remains to port legacy UI, full inventory/DHCP collectors, AD services, planning persistence, wiki folder/media and report-book workflows.

## Validation
- GitHub Actions Maven verification against MySQL 8 and JS syntax check.
- Staging deployment must verify downloaded ZIPs, health, anonymous auth, module listing and core rollback. Never install on production before acceptance.
