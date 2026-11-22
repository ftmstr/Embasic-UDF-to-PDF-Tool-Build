# TUR295 — Exact-run runtime and shaded NOTICE reconciliation (NO-GO)

Date: 2026-11-16 03:01 TRT. Scope: ONLY Public ftmstr/Embasic-UDF-to-PDF-Tool-Build, branch ci/secure-windows-bootstrap-20261018. This is evidence triage, not a release approval or legal opinion.

## Verified GitHub Actions evidence

Run [43771982669](https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43771982669), Windows job 132989688073 and Linux job 132989688389: both SUCCESS. The runtime Maven dependency-tree step succeeded on both OS. Both job logs print **PUBLIC_APP_EVIDENCE_COMMIT=acf9f3c1ee44257f5f87e515f8eea2ba3433424e**. This is different from the previously recorded TUR294 commit f75722ed749b0103308ae46b9ec0d122780df10b. Accordingly the observed job results must be attributed to the logged checkout SHA, not automatically asserted as exact-SHA f75722 validation.

Resolved runtime dependencies on both runners:
- org.apache.pdfbox:pdfbox:3.0.8 (direct)
- org.apache.pdfbox:pdfbox-io:3.0.8 (transitive)
- org.apache.pdfbox:fontbox:3.0.8 (transitive)
- commons-logging:commons-logging:1.4.0 (transitive)

The Maven Shade log includes these four JARs. Both runs report BUILD SUCCESS. The Windows app-image validation step SUCCESS, but the interactive Windows Robot step SKIPPED. This is hosted CI evidence, NOT an external physical Windows beta acceptance.

## Concrete redistribution blocker in actual shaded bytes

The runtime audit output on both OS identifies **six upstream legal-text members not preserved as exact bytes** in the shaded artifact:

| Component | Missing/transformed upstream members | Status |
| --- | --- | --- |
| org.apache.pdfbox:fontbox:3.0.8 | META-INF/DEPENDENCIES; META-INF/LICENSE; META-INF/NOTICE | 3 unresolved |
| org.apache.pdfbox:pdfbox-io:3.0.8 | META-INF/DEPENDENCIES; META-INF/LICENSE; META-INF/NOTICE | 3 unresolved |
| org.apache.pdfbox:pdfbox:3.0.8 | All three corresponding upstream legal texts present as exact bytes | Independent legal review still required |
| commons-logging:commons-logging:1.4.0 | LICENSE.txt and NOTICE.txt exact bytes present | Independent legal review still required |

The audit explicitly reports complete_shaded_byte_provenance=NOT_VERIFIED, independent_app_license_notice_clearance=NOT_VERIFIED, publication_allowed=false, and six unresolved upstream legal-text entries. The observed overlap of META-INF resources during Shade means blindly treating a single merged NOTICE as complete is unsafe. Do not silently replace or discard license/NOTICE texts. Next implementation priority: design a rights-reviewed, component-specific legal-text assembly mechanism for the independent Public app, verify hashes in the actual shaded artifact, and preserve fail-closed publication gates. This document does not itself satisfy that gate.

## Separate source publication blocker

Secure Bootstrap run [43771982805](https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43771982805) FAILED: hosted Windows toolchain probe SUCCESS, source-publication preflight FAILURE, subsequent Maven regression and packaging SKIPPED. Legacy src/main source gate BLOCKED; independent Public app/ ownership and license rights NOT_VERIFIED. PUBLIC_SOURCE_APPROVED_SHA alone is not approval.

## Acceptance state and next work

Native UYAP NOT_VERIFIED; physical exFAT NOT_RUN; hostile CAS NO-GO; physical external Windows beta NOT_VERIFIED; release NO-GO. No Private access, main merge, tag, release, ZIP, installer, or binary distribution. No percentage increase justified. No new synthetic smoke tests were added in this evidence-only turn.

Next: reconcile latest branch HEAD against logged CI checkout SHA before any code change; then implement only a reviewable, source-rights-clean NOTICE preservation fix if legally appropriate, verify on Windows/Linux exact commit, and separately complete rights-clean offline golden and physical Windows user acceptance.
