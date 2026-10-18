# Embasic UDF to PDF Tool — Public Build Workspace

This repository is the **clean-history, public CI workspace** for the Windows Java 17 / Swing / Maven Embasic UDF to PDF Tool project.

## Current state

**Bootstrap only — application sources have NOT been copied.** No production-ready JAR, ZIP, Windows executable, installer, or UDF compatibility certification is available here.

The original private repository is authoritative. It must remain private and retain its history and development branches. Never mirror, fork, cherry-pick historical commits, or publish its original CI logs or artifacts into this workspace.

## Publication gates

Before importing any application source, require an independently reviewed, zero-history snapshot and documented approvals for:

1. Ownership/publication terms for original project source code.
2. iText 2.1.7 MPL 1.1 / LGPL alternative, actual shaded-JAR compliance and third-party notices, including Bouncy Castle transitive packages.
3. Logo and icon origin, metadata, and public redistribution rights.
4. Complete source-tree private-identity, credential, UYAP document and binary scan; immutable snapshot checksums.
5. Human review of the exact bytes to be transferred. Keep private approval manifests and identity dictionaries **outside** this public repository.

**Fail closed** when any gate is incomplete. In particular, do not upload personal or authentic UYAP files, private documentation, security scans containing private data, credentials, private signing keys, historic Git data, CI logs, or previous build artifacts.

## Planned CI

The CI design uses GitHub-hosted `windows-latest`, minimum `contents: read` token access, and zero secrets on pull requests. A source-free runner probe may run at bootstrap; it is **not** an application build. Application compilation, regression tests, synthetic UDF smoke, and packaging are enabled only when the source snapshot and its approval gate are present.

Once reviewed source is imported:

```powershell
mvn -B -ntp clean verify
pwsh -NoProfile -File scripts/verify-explorer-grid.ps1
pwsh -NoProfile -File scripts/verify-udf-parser.ps1
mvn -B -ntp -DskipTests package
```

The scripts above belong to the approved application snapshot and are not present during bootstrap. Windows packaging requires JDK 17+ with `jpackage`. Binary artifacts and SHA-256 checks are permitted **only after separate redistribution approval**. Releases remain disabled.

See [BUILD-GATES.md](docs/BUILD-GATES.md) when available.

## Security

Submit synthetic test data only. Never attach real case files, signing material, sensitive names, identifiers, or private reports to public issues or pull requests.
