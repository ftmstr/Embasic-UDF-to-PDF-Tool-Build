# TUR268 — Independent Public app exact-commit source, dependency and NOTICE inventory

Date: 2026-11-15, Europe/Istanbul. Baseline Public HEAD: 854d158543f8faae60d69c5ca3a44cd36c636092.
Scope: only ftmstr/Embasic-UDF-to-PDF-Tool-Build, ci/secure-windows-bootstrap-20261018.

The new audit-public-app-readiness.py executable gathers immutable HEAD Git blob SHA-1 and SHA-256 for every tracked regular app file. It checks path, size, symlink and prohibited artifact constraints, parses direct Maven group/artifact/version/scope from app/pom.xml, then calculates an exact-commit manifest SHA-256. No local UYAP PDF, UDF, private repo, original logo or proprietary font is ever opened.

The output explicitly lists source LICENSE and THIRD-PARTY-NOTICES status as MISSING or PRESENT_UNREVIEWED. Direct POM dependencies are NOT a complete shaded/transitive SBOM. Presence of text or a matching SHA is NOT a redistribution authorization; source rights, font/brand permissions and native UYAP golden remain NOT_VERIFIED. publication_allowed stays false and status BLOCKED.

Optional unuploaded --manifest-out writes an exclusive local file outside checkout; no CI artifact upload. Synthetic unit tests cover exact-commit binding, mutable working tree, missing license, presence without approval, symlink and binary rejection, missing pom, unresolved dependency, and forbidden in-repo output.

Public Windows/Linux MVP workflow runs both synthetic unit tests and the exact commit inventory --expect-blocked with no ZIP, installer, tag, release or license gate bypass. The old Secure Bootstrap gate is unchanged. Windows hosted exFAT remains PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN (Mount-DiskImage CimException). Hostile existing target atomic CAS and official native UYAP golden/all-version fidelity are NO-GO; real UYAP data never reaches Public CI.

Final SHA and CI must be verified after the commit; an inventory CI SUCCESS means only that evidence was measured and publication remains BLOCKED. Progress stays 44.3 percent weighted engineering estimate, not user release readiness.
