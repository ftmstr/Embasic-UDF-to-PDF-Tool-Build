# TUR321 — Public app shaded legal-resource disposition (NO-GO)

Date: 2026-11-17 05:xx TRT. Scope: **only** Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`, starting HEAD `074ca49aaf1ab7522eb51e607fafb3fb2cd19bd8`.

## Actual unresolved release blocker

The existing TUR295 audit of real Windows/Linux Maven Shade artifacts (logged checkout `acf9f3c1ee44257f5f87e515f8eea2ba3433424e`) found six component-specific upstream legal-resource members that were **not** retained byte-for-byte in the shaded application. This is historical evidence, **not** an audit of this document's commit.

| Component (3.0.8) | META-INF/DEPENDENCIES | META-INF/LICENSE | META-INF/NOTICE |
| --- | --- | --- | --- |
| pdfbox | Exact-byte observed at TUR295; legal sign-off pending | Exact-byte observed; sign-off pending | Exact-byte observed; sign-off pending |
| fontbox | Missing/transformed | Missing/transformed | Missing/transformed |
| pdfbox-io | Missing/transformed | Missing/transformed | Missing/transformed |

These are **nine independent component/member provenance relationships**, not a claim that one shared META-INF/LICENSE or NOTICE satisfies all components. `commons-logging:1.4.0` also requires separate independent legal review, although its `LICENSE.txt` and `NOTICE.txt` were observed exact-byte at TUR295.

## Required resolution, without publication

1. From **approved public upstream Maven artifacts** pinned by group/artifact/version and SHA-256, record the complete original bytes and SHA-256 of each of the nine members. Do not substitute a different module's same-named resource.
2. Define a deterministic **component-namespaced** inclusion strategy for the shaded jar, preserving all nine original byte streams under distinct names. Any Shade transformer that merges or overwrites them must be treated as insufficient without a component-by-component comparison.
3. On Windows **and** Linux, compare each shaded namespaced member's SHA-256 to the corresponding original JAR member. Record exact workflow run, checkout SHA, artifact SHA and per-member PASS/FAIL. If any member is absent, transformed or untraceable, fail closed.
4. Independently review Public `app/` source ownership, the app-level LICENSE/NOTICE and each dependency's redistribution terms. No automatic success based on `PUBLIC_SOURCE_APPROVED_SHA`, a Maven build, or a green CI run.
5. Do not mix the separate legacy `src/main` source gate with independent Public `app/` rights clearance.

## Current decision

**NO-GO.** No original upstream JAR bytes were retrieved or re-bundled in this change. No new legal text, package, executable or release is published. The nine SHA comparisons remain **NOT_VERIFIED at the current HEAD**, and source ownership/legal approval remains **NOT_VERIFIED**. Legacy `src/main` gate **BLOCKED**; native UYAP **NOT_VERIFIED**; physical exFAT **NOT_RUN**; hostile CAS **NO-GO**; physical Windows beta **NOT_RUN**.

The next useful engineering change is a rights-reviewed component-namespaced Shade resource assembly plus exact-byte artifact audit, **not** another synthetic smoke test. Separately obtain rights-clean offline golden UDF/PDF and W01–W10 physical Windows acceptance evidence. Beta/final dates remain low-confidence and conditional.

## Existing exact-HEAD CI evidence (before this document commit)

- Desktop MVP: SUCCESS — https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43976125223
- PDF Engine: SUCCESS — https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43976125183
- Upstream Audit: SUCCESS — https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43976125111
- Hosted Windows app-image: SUCCESS — https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43976114733
- Secure Bootstrap: FAILURE (source publication preflight); packaging skipped — https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43976125112

This document records a **release-blocking disposition** only. It does not fix the shaded bytes or claim a completed beta.
