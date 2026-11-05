# Tur 193 — Public source-gate triage (2026-11-05 05:00 TRT)

Scope: **only** public ftmstr/Embasic-UDF-to-PDF-Tool-Build, branch ci/secure-windows-bootstrap-20261018. Baseline HEAD: `4119143a3432db5ddb772f9e8278a2dc313bd55b`. This is a diagnostic note, **not** a rights, source, license, or publication approval.

## Verified current code path

At baseline, `scripts/public-ci-gate.py` defines application files as tracked paths beginning `src/main/` or exactly `pom.xml`. The public MVP has `app/pom.xml` and `app/src/main/java/...`, but no root `pom.xml` or `src/main/`. Therefore the gate enters the bootstrap-only path and rejects paths outside `BOOTSTRAP_ALLOWED` as `unapproved_bootstrap_file`. Merely recognizing `app/` is **not** a safe repair: the gate's `REQUIRED` snapshot still refers to an older root `src/main/java/com/embasic/udf2pdf/...` layout and would produce `incomplete_application_snapshot`; the exact SHA approval variable is also empty. All three blockers require independent review. The existing gate must remain fail-closed.

## Evidence to obtain before any source-gate change

1. Freeze the exact reviewed commit and enumerate every tracked blob (path, Git blob ID, SHA-256 of bytes, type, origin, author/rights evidence, third-party license, decision). A Git blob SHA-1 is **not** a SHA-256 content digest.
2. Separately approve all `app/` Java sources, tests, build manifests, scripts, images/fonts, and third-party assets. Passing JUnit and public visibility do not establish ownership or redistribution rights.
3. Independently review a complete application snapshot manifest appropriate to `app/`; reject any missing or unknown entry. Preserve blocked suffixes, secrets scan, path traversal, symlink, size, binary/asset review and exact approved 40-hex SHA checks.
4. Only then revise gate recognition and the manifest with explicit positive and negative tests using rights-clean synthetic fixtures. Do not populate `PUBLIC_SOURCE_APPROVED_SHA` before the review is signed off.
5. Re-run Windows/Linux JUnit, PDF font/geometry probes, jpackage preflight and actual Windows GUI acceptance on the exact reviewed commit. No tag/release/ZIP/installer until all independent gates pass.

## Existing CI evidence for baseline SHA (not rerun in this note)

- Desktop MVP: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41921997926 — SUCCESS.
- PDF probes: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41921998045 — SUCCESS.
- Dependency audit: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41921997852 — SUCCESS.
- Windows Hosted: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/41921998012 — FAILURE at source preflight; approved packaging SKIPPED.
- Physical Windows GUI and real UYAP PDF equivalence: NOT VERIFIED.

Progress remains 44.3% (prior indicator, not UYAP compatibility). No release permission inferred. Next priority: exact-commit rights inventory, then a measurable PDF geometry/font or Explorer grid defect. Beta 2026-11-21 to 2026-12-12; release candidate 2027-01-09 to 2027-03-06 are low-confidence conditional estimates.
