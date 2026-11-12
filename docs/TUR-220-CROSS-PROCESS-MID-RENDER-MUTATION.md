# TUR 220 — Two-process, deterministic, offline PDF render mutation regression

**12 November 2026 — Public-only**
`ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch
`ci/secure-windows-bootstrap-20261018`.

## Starting point and anti-duplication

Tur218 exact final SHA:
`4673b3e8f8ac9bcf6e9cc61d8102f99fdde01dbf`.
The user-requested Tur219 was **already completed** before this turn.
Its final SHA `0287f27c9b51f762a7ce26af0c5deab684d63584`
and Drive report prove that, when `ATOMIC_MOVE` replacement is unavailable,
`PdfExporter.publishValidated` now fails closed rather than first moving
the existing approved PDF to an unprotected backup and risking replacement
of an independently updated target. We do not redo this work.

Tur218's `RenderBoundary` regression seam already supports injecting
a source or target mutation after document 1 has been rendered but before
document 2. However, that mutation ran **in the same JVM as the renderer**.
Tur220 makes a different, explicitly independent-process test.

## New original, synthetic-only test files

1. `app/src/test/java/com/embasic/publicapp/RenderMutationWorker.java`
   is a tiny, test-scope-only JDK subprocess entry point. It copies a
   pre-generated, rights-clean `.udf` or PDF inside one `@TempDir`,
   disallows symlinks and source=destination, and optionally restores a
   saved file modification time. It never runs within the packaged app.
2. `app/src/test/java/com/embasic/publicapp/PdfCrossProcessRenderBoundaryTest.java`
   starts a separate OS process via the CI JDK `java/java.exe`. The parent
   JVM synchronously waits (15-second bound), checks the process exit code
   and a fixed success marker, and only then allows the second document
   render to continue. No guessing millisecond windows, sleeping for a
   potentially unstable race, third-party binary, private UDF or GUI hook.

The three independent-process scenarios use two original synthetic
`format_id=1.0` UDF ZIPs and valid synthetic PDFs:

- **Source:** after document 1, the child replaces source UDF 2 with
  a new valid synthetic ZIP, restores its prior mtime, then exits.
  Rendering the second *previously parsed* document finishes; the
  mandatory prepublication source SHA gate rejects the export. The
  previously approved two-page PDF remains byte-identical, and staging
  and backup files do not remain.
- **Approved destination:** the child independently replaces the
  approved two-page PDF with a valid, newer one-page PDF during render.
  Optimistic `TargetSnapshot` verification requires renewed approval
  and does not overwrite or remove the external document.
- **No-overwrite consent:** the child creates a valid target PDF
  while a two-document merge is rendering. The publisher refuses to
  replace the now-present file, preserves the external PDF, and cleans
  staging.

Tests use real process isolation at the file system, not a mock of
`Files.copy`; they use one deterministic **in-process test-only
render-boundary callback as a synchronization barrier**. That distinction
is essential.

## Exact limits and claims

- These are **not physical packaged Windows EXE render-race tests**.
  `PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN` until an explicitly accepted,
  nonpublished Windows EXE test with separately verified ordering exists.
- Independent JVM process mutation demonstrates file-system observation
  across process boundaries on real Windows/Linux CI, not hostile
  byte-level atomic check-and-move guarantees. The vulnerable final
  check-to-`ATOMIC_MOVE` window is still not a Windows-native
  compare-and-swap. `WINDOWS_ATOMIC_TOCTOU=NO_GO`.
- No new timing hook or diagnostics are exposed in production UI or
  shipped `jpackage` EXE. The worker is under `src/test` only.
  Existing `PdfExporter`, UDF parser, fonts, PNG gates, GUI, source/NOTICE
  and Secure Bootstrap checks are unchanged.
- No official UYAP Editor binary, unsupported CLI, SDK, source, sensitive
  UDF or real native reference PDF was used. The Ministry's supported
  workflow remains manually exporting a rights-cleared PDF from the
  official Editor, independently checking it offline.
- Run **exact-final Public HEAD** Windows/Linux JUnit + 160 synthetic
  UDF profile, separately reported physical Robot, independent Windows
  app-image, PDFBox/OpenPDF probe, upstream SHA, and Secure Bootstrap.
  Keep source approval, third-party redistribution rights and publication
  NO-GO. No Private access, main merge, tag, release, installer or ZIP.

## Release readiness and next acceptance

Weighted engineering planning score remains **44.3%**, not a UYAP native
compatibility percentage. Future work may use a separately opt-in, ephemeral
packaged EXE with a debug/JDWP barrier to validate a real physical render
race, but never pretend that an approximate filesystem polling loop has
deterministic ordering. Any diagnostic-enabled app-image must remain
nonpublished, loopback-only, rights-clean, and disjoint from production.
