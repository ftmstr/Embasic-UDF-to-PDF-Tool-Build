# TUR 218 — Deterministic mid-render source/target mutation regression

Date: **12 November 2026**. Scope: **only Public** `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, existing branch `ci/secure-windows-bootstrap-20261018`.

## Previously completed work

TUR216 (final `96484805742e8262240a374038288bd5e261b533`) already verified **physical packaged Windows EXE** two synthetic UDF imports, keyboard reorder, Ctrl+S, PDFBox page order `BRAVO-TWO` then `ALPHA-ONE`, A4 geometry, and overwrite **NO** preservation. TUR217 (final `b1e89e79ea8f58a8c85b8c59fb5e9998bc7f05bb`) already verified an **on-disk source changed before starting a second save**, even with original mtime restored and physical overwrite **YES**: old PDF SHA unchanged and no staging leak. Do not replay those tests as a new invention.

**Unproven boundary:** a source or user-approved target changing *while* PDFBox renders multiple documents. Prior `PdfOutputCommitTest` already injected mutations at `BeforePublishCheck`, *after* staging/re-open, not within PDFBox's document render loop. Running ad hoc background filesystem races cannot reliably establish the exact order of events.

## New deterministic test seam, preserving public product behavior

An original, package-private `PdfExporter.RenderBoundary` callback executes after each fully laid-out synthetic `DocumentSpec`, **before the next document is processed**. Existing GUI, CLI and preview paths supply a no-op callback through existing signatures; the page layout, save/overwrite UI, PDFBox bytes and final pre-publication SHA checks are not changed. The callback is injection for deterministic tests, **not** a supported external plugin API or official UYAP integration.

`PdfRenderMutationBoundaryTest` creates only rights-clean temporary `format_id=1.0` ZIPs and self-generated PDF documents, with two distinct source files:
1. Produce an existing valid two-page approved PDF. After document 1 is laid out, replace document 2's source UDF ZIP with different same-length text, restore the original mtime, continue the second document's PDFBox render. The existing GUI source-fingerprint verification at the mandatory before-publish gate rejects the stale parsed snapshot. The original target bytes and PDF page count remain unchanged; no `.embasic-pending-` or `.embasic-backup-` remains.
2. While rendering, an independent valid one-page PDF replaces the previously user-approved two-page output. Final `TargetSnapshot` validation requires explicit reapproval and preserves the *newer* external file, never overwriting it with the internally staged result.
3. Starting without overwrite consent, a valid external target appears after document 1. Final non-replacing publication rejects the race; the newer external bytes remain untouched and all temporary stages are cleaned.

Every mutation occurs synchronously **inside the render loop** and records ordered boundaries 1,2; this is a deterministic engine-level regression, not a stochastic or physical Windows EXE race. Source and target byte contents are checked only inside CI tests; no document text, real user data, case artifacts or native UYAP files are uploaded to GitHub/Drive.

## Security / semantics

The final check-before-move for an approved overwrite remains an **optimistic** guard, not Windows handle-bound atomic compare-and-swap. A hostile, sub-millisecond mutation after the final SHA validation may still race with the move. **Do not claim atomic TOCTOU safety** from these tests.

No proprietary UYAP Editor binary or native conversion CLI/API is used; the Ministry's documented **manual** PDF export remains an optional local fidelity reference. Publication remains **NO-GO** without exact source approval and redistribution/NOTICE rights. No Private repo access, tag, main merge, ZIP, installer, release or deploy is authorized.

For acceptance, inspect **exact-final-SHA** Windows/Linux Maven and JUnit counts, Windows physical Robot separately from normal-suite SKIP, 160 synthetic UDF profile, independent jpackage/app-image, PDF Engine probes, upstream SHA audit, and Secure Bootstrap gate. Any failed new tests must be repaired on the Public branch before declaring completion.
