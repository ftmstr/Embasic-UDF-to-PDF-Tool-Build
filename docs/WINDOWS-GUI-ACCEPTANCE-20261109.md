# Windows GUI acceptance — evidence-gated checklist (2026-11-09)

Scope: Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`. This is a **manual acceptance protocol**, not a PASS claim. Use only rights-cleared synthetic UDF fixtures. Never import private repository source, native UYAP documents, or unlicensed fonts/images.

## Preconditions and evidence

1. Record the exact branch HEAD SHA, Windows edition/build, Java runtime, display scaling (100% and 150%), app-image SHA-256 and the CI run URL that built it. Do not use a release artifact; an internal CI-only app-image is permitted only if its source and license provenance is approved for that use.
2. Record each case as PASS/FAIL/NOT_RUN with a screenshot or screen recording, timestamp, fixture hash and observed behavior. An unexecuted case must remain NOT_RUN. Do not treat headless Swing JUnit or Windows jpackage build as physical GUI acceptance.
3. Run with a clean profile and again with a populated profile of 160 synthetic documents. Monitor UI hangs and exceptions; record measurements, do not invent thresholds or PASS outcomes.

## End-user acceptance cases

| ID | User action | Required observable result |
| --- | --- | --- |
| WGUI-01 | Import 1 and then 160 valid synthetic UDFs | All accepted documents appear once; no EDT freeze or silent omission. |
| WGUI-02 | Click, Ctrl-click, Shift-click, Ctrl+A; use keyboard navigation | Explorer-like selection and focus remain consistent, including after deletion. |
| WGUI-03 | Zoom thumbnails repeatedly to both extremes; resize window at 100%/150% DPI | One responsive frame, no clipped controls or negative/overflow columns. |
| WGUI-04 | Use the plus tile in the same grid at min/max zoom | Plus tile is reachable and visually scales with document cards; add opens expected file selection. |
| WGUI-05 | Drag a multi-selection before/after another tile, then export PDF | Visible order and exported PDF page order match exactly. |
| WGUI-06 | Switch light/dark themes during selection and preview | Selection, focus and readable contrast survive switching. |
| WGUI-07 | Scroll 160 documents while preview generation is slow | Input remains responsive; no preview from a previous selection replaces the current one. |
| WGUI-08 | Export synthetic mixed-font, image and multipage table fixtures | Page count, embedded fonts, image positioning, cell boundaries and page geometry match independently inspected expectations. |
| WGUI-09 | Replace source file between preview and export; supply unknown UDF element | SHA/source validation rejects modified content and unknown schema element fail-closed; no partial output is silently published. |
| WGUI-10 | Cancel conversion and retry after a recoverable preview error | No corrupt target overwrite; UI state and error messaging remain coherent. |

## Release boundaries

Current hosted source publication preflight is FAILING on the public branch; the downstream verified Windows ZIP/SHA job is SKIPPED. This checklist does **not** authorize bypassing source manifest, SHA, dependency or license gates, nor does it authorize a tag, release, ZIP or installer publication. Native UYAP equivalence is unverified. Any failure should become a minimal reproducible issue with fixture hash, logs and precise expected/actual behavior before a code change is made.
