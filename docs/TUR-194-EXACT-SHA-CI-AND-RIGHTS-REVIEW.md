# Tur 194 — exact-SHA CI closure and rights-review blockers (2026-11-05 06:00 TRT)

Scope: ONLY public ftmstr/Embasic-UDF-to-PDF-Tool-Build, branch ci/secure-windows-bootstrap-20261018. No private repository access.

Baseline HEAD: `5c9a696a47ac1cde39438f00905f39d806424e33`. GitHub compare with branch: identical. This report is an evidence record, NOT an approval or an application-code fix.

## CI evidence at the exact baseline commit
- Windows/Linux MVP: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41952301219 — SUCCESS. Both Windows and Linux compile/JUnit, synthetic UDF-to-PDF, and 160-file synthetic profile steps succeeded. Windows CI-only portable app-image succeeded. Physical interactive Robot acceptance SKIPPED.
- PDF engine probes: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41952301109 — SUCCESS (OpenPDF and PDFBox probes).
- Dependency audit: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41952301170 — SUCCESS.
- Hosted source gate: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41952301217 — FAILURE. Synthetic gate unit tests SUCCESS, exact source preflight FAILURE. Approved Maven regression and approved Windows package SKIPPED.

## Confirmed blocker (source inspection)
`scripts/public-ci-gate.py` considers only root `pom.xml` or `src/main/` as application files. The public MVP lives under `app/pom.xml` and `app/src/main/`. The bootstrap-only allowlist therefore rejects unapproved tracked paths. The `REQUIRED` manifest also targets a different root layout. Exact reviewed SHA authorization is separate and not supplied. Changing only the path recognition is not a safe or sufficient repair.

## Source-review checklist, NOT an approval
Before changing source gate, independently review the complete tracked tree at a frozen SHA: source provenance and rights, build/test scripts, dependency licenses, binary icons/fonts, image provenance, byte-level SHA-256 inventory, and a complete fail-closed app/ manifest. Git blob IDs are not SHA-256 byte digests. No rights or redistribution conclusion can be inferred from public GitHub presence or successful CI. Preserve exact SHA approval, restricted extensions, secrets scan, symlink/path traversal checks, and release gates.

No application files or security gates were modified in this tour. No tags, releases, ZIPs, or installers published. Physical Windows GUI acceptance, real UYAP schema compatibility and visual PDF equivalence remain unverified. Progress stays 44.3% (not a UYAP compatibility score).

Next: secure independent rights approval and full byte-level manifest, then fix the source gate without weakening it; in parallel prioritize reproducible synthetic PDF font/geometry and Explorer grid defects. Beta 2026-11-21–2026-12-12; candidate 2027-01-09–2027-03-06: low-confidence conditional estimates.
