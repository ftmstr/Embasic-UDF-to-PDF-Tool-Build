# Public MVP source approval inventory — 2026-11-04 (Tur 176)

Scope: **only** the public repository `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`. This document is an inventory and review plan, **not** source, asset, license, or release approval.

## Observed gate mismatch
`scripts/public-ci-gate.py` detects application sources only when tracked paths include root `pom.xml` or `src/main/`. The current public desktop MVP resides under `app/` (including `app/pom.xml`). With no root application entrypoint, `evaluate()` takes its bootstrap-only path and rejects tracked files outside `BOOTSTRAP_ALLOWED` with `unapproved_bootstrap_file`. The hosted workflow's source approval variable is also unset. These are independent blockers.

## Mandatory review before changing any publication gate
1. Enumerate **every tracked file at the exact reviewed commit**, including `app/`, workflows, scripts, images, fonts, documentation, and dependency manifests. Record each path, SHA-256, provenance, author/rights evidence, and disposition. Do not assume a public repository grants redistribution rights.
2. Classify each path explicitly as application source, generated/synthetic test fixture, third-party dependency, artwork/font, build tooling, or prohibited/unverified material. Reject unknown paths, symlinks, secrets, real case data, and unapproved binary assets.
3. Require a reviewed **exact 40-hex commit SHA**, an independently approved source inventory, and separate license/artwork/font approvals. PR merge SHA and branch HEAD are not interchangeable; never infer approval from passing tests.
4. Only after these reviews, design a gate that recognizes `app/` while preserving all existing restricted-extension, secret-scanning, size, path traversal, complete-snapshot, license, and release checks. Any missing evidence must remain **BLOCKED**.
5. Exercise positive and negative cases on **rights-clean synthetic data only**, then validate Windows/Linux JUnit, PDF geometry/font smoke, jpackage preflight, and physical Windows GUI tests on the same approved SHA. A jpackage app-image CI success is not permission to publish an installer.
6. No tag, release, ZIP, or installer before all source/license/SHA gates and real Windows acceptance are independently satisfied.

## Evidence baseline (not a new CI run)
Commit `3aa351e80f192299989dc1fc7a1cd9a45698dcb9`:
- Public Desktop MVP: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41789418019 — reported SUCCESS.
- PDF probes: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41789418042 — reported SUCCESS.
- Dependency audit: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41789418160 — reported SUCCESS.
- Windows Hosted: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41789418104 — reported FAILURE, `SOURCE_GATE: BLOCKED (unapproved_bootstrap_file)`.
- Physical Windows GUI acceptance and real UYAP PDF equivalence: **NOT VERIFIED**.

## Next engineering work
Prepare the exact-commit inventory with independent rights evidence; separately prioritize a reproducible PDF font/geometry or Explorer grid usability defect. Keep `app/scripts/Show-EmbasicProgress.ps1` unchanged absent measurable evidence. The previous 44.3% progress indicator is **not** a measure of UYAP compatibility.
