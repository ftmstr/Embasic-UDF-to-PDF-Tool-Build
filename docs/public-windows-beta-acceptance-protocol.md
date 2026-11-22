# Public Windows beta acceptance protocol (NO-GO until evidenced)

Scope: **only** the independent Public `app/` implementation on `ci/secure-windows-bootstrap-20261018`. This is a test protocol, not authorization to publish an installer or to claim UYAP equivalence.

## Prerequisites and evidence

- Record exact Git commit, GitHub Actions run URLs and conclusions, Windows build, Java runtime, and SHA-256 of the tested app-image. Never label an unexecuted test PASS.
- Keep the legacy `src/main` source gate BLOCKED. Review Public `app/` source rights and third-party LICENSE/NOTICE obligations independently. `PUBLIC_SOURCE_APPROVED_SHA` alone does not grant permission.
- Use only rights-cleared synthetic UDF fixtures or separately documented, authorized offline golden records. Unknown UDF constructs must fail closed. Do not import actual case files into CI or upload them.
- Test a locally built **unpublished** app-image on a physical Windows machine. A hosted Windows build is not a physical end-user acceptance test.

## Operator walkthrough (record actual outcome and evidence per step)

| ID | Acceptance operation | Objective evidence | Status until performed |
| --- | --- | --- | --- |
| W01 | Launch the independent Public app-image; open and close it twice | Windows build, app-image hash, launch log and screenshots | NOT_RUN |
| W02 | Add multiple rights-cleared UDF files from the in-grid add tile and file chooser | Visible grid entries, source count and filename order | NOT_RUN |
| W03 | Exercise Ctrl-click, Shift-click, Ctrl+A, keyboard focus and removal | Selection state before/after each action; no unintended removals | NOT_RUN |
| W04 | Zoom thumbnails in/out and resize the window between narrow and wide layouts | Screenshots with no clipped controls or overlapping tiles | NOT_RUN |
| W05 | Reorder items by drag/drop and verify order survives preview and export | Before/after order plus output PDF page order | NOT_RUN |
| W06 | Switch light/dark theme, preview a document and return to the grid | Screenshots; no lost selection or stale preview | NOT_RUN |
| W07 | Convert a rights-cleared golden fixture with Unicode, table borders and images | Output SHA-256, page count, embedded fonts, geometry and visual comparison against authorized reference | NOT_RUN |
| W08 | Supply malformed or unsupported UDF input | Explicit rejection; no partial output promoted as success | NOT_RUN |
| W09 | Cancel/interrupt conversion, then retry | No corrupted final PDF or stale temporary file mistaken for success | NOT_RUN |
| W10 | Inspect independent app source rights, shaded dependencies and LICENSE/NOTICE attribution | Signed review notes and exact manifest/hash evidence | NOT_VERIFIED |

Record each outcome as PASS, FAIL, BLOCKED or NOT_RUN, with exact file hashes, steps, and screenshots/log paths. No screenshot or log means NOT_VERIFIED, not PASS. A Windows-hosted CI job may establish buildability, not the physical GUI outcomes above.

## Explicit exclusions / release gates

- Native UYAP compatibility: **NOT_VERIFIED** until separately authorized and independently accepted.
- Physical exFAT: **NOT_RUN** until performed on actual exFAT media.
- Hostile CAS: **NO-GO** until its specific physical evidence is accepted.
- Legacy source: **BLOCKED**; independent Public app source/license/NOTICE: **NOT_VERIFIED**.
- Main merge, tag, release, ZIP, installer publication and distribution: **PROHIBITED** absent explicit user approval and separate source/license approval.

## Beta decision

A beta recommendation requires W01–W10 evidence, resolution of material failures, a documented source/license review, and a separately recorded release decision. If any gate is missing, outcome remains **NO-GO**. This protocol never changes progress percentages or converts synthetic acceptance into real UYAP equivalence.
