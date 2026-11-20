# TUR 222 — Atomic no-clobber creation of new PDFs (Public-only)

Date: 2026-11-12 (Europe/Istanbul). Scope: **only** `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`.

## Preflight
- Verified starting Public HEAD: `d6da8efcd618bc5b26e2d9ca33ec09f855136f70`.
- Verified prior TUR221 Google Drive report in folder `1Ct_jMTUDPU-hrBBxjcMl8MEk-NpVlRY8` and checked the Public tree for existing work. No TUR222 was present.
- Prior exact-SHA Windows/Linux MVP, app-image, PDF engine and dependency audit succeeded. Secure Bootstrap remained FAILURE due to `unapproved_bootstrap_file`. Never reuse those green results as proof for TUR222.

## Concrete risk and change
A new PDF must never clobber a PDF created by another process between the final optimistic target check and publication. The old `Files.move(staging, target)` without `ATOMIC_MOVE` does not provide a portable atomic *no-replace* contract; it may depend on the provider's check-then-move implementation.

`app/src/main/java/com/embasic/publicapp/PdfExporter.java` now publishes **new** PDFs by `Files.createLink(target, staging)` followed by deleting the temporary name. Because staging is created in the destination directory, this creates a second name for the already validated complete bytes, and hard-link creation fails if the destination name already exists. No partial copy and no racy overwrite fallback are allowed. If a filesystem cannot create hard links (for example certain removable/exFAT stores), the save fails closed; the app must communicate the failure rather than claim success. The *previously existing and approved* target path is unchanged and still uses atomic replacement after an optimistic SHA/fileKey check. This does **not** solve hostile CAS for existing targets.

Existing focused JUnit regressions cover: normal uncontended new PDF, destination already present, and a late-created independent synthetic PDF after the final check. A standalone JDK 21 local Linux hard-link probe additionally verified: same file identity while both names exist, target bytes after deleting staging, and rejection of an existing target. This standalone probe is **not** Windows, Maven, PDFBox, or GitHub CI proof.

## Safety and remaining acceptance
- Rights-clean synthetic format_id=1.0 only. No official UYAP schema, native PDF fidelity, or real-file equivalence claim.
- Do not touch the private repository. Do not merge main, publish tags/releases/ZIP/installers, or bypass license/SHA/NOTICE gates.
- `WINDOWS_ATOMIC_TOCTOU=NO_GO` for hostile existing-approved-target CAS; `PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN` until an actual packaged EXE test proves it.
- On Windows, confirm the actual packaged EXE can save to an NTFS folder with this hard-link path; unsupported filesystems should fail safely and visibly. Confirm exact-final-SHA Windows/Linux JUnit, jpackage physical Robot, PDF engine, upstream audit and secure bootstrap separately.
- The progress percentage remains 44.3% (weighted engineering planning only); no UYAP compatibility percentage is inferred. Beta 2026-11-21..2026-12-12 and final candidate 2027-01-09..2027-03-06 are low-confidence conditional estimates.

Next: close exact-SHA CI and test the new no-clobber publishing behavior physically on packaged Windows EXE, without adding tests solely to increase counts. Investigate a provider-backed existing-target CAS only if a genuine atomic conditional replacement primitive can be demonstrated; otherwise keep NO-GO.
