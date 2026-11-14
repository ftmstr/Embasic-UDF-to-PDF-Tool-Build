# TUR 223 — Separate-process post-check PDF name race and packaged EXE limits

**Date:** 13 November 2026 (Europe/Istanbul). **Repository:** Public
`ftmstr/Embasic-UDF-to-PDF-Tool-Build`, existing branch
`ci/secure-windows-bootstrap-20261018`. No Private access.

## Baseline, nonduplication

Tur220 final `5eb21bf7d27ce537c3b3aec55e1d212fd47e6bce`
was verified. During the hourly unattended runs, Tur221 and Tur222 were
completed, archived to Drive, and **not repeated**:

- Tur221 established that a newly appearing PDF target cannot be overwritten
  when it was absent at original overwrite consent; the original regression
  used a **same-JVM** test callback after final target verification.
- Tur222 replaced `Files.move(staging, target)` for a new PDF with
  `Files.createLink(target, staging)`. On supported filesystems, the file
  system either atomically allocates the new target name or rejects an
  existing one without replacement. No racy fallback on unsupported
  hard-link filesystems. The approved existing-target atomic replacement
  path is separate and remains subject to hostile check-to-move TOCTOU.

Tur222 exact SHA `b13ccca9ae696ee2305b1ba26b3d4758e1d3cac2`
had all 5 exact-SHA CI workflows completed: Public MVP Windows/Linux,
packaged Windows EXE, PDF Engine and upstream dependency audit SUCCESS;
Secure Bootstrap source publication gate FAILURE/BLOCKED. Windows/Linux
normal JUnit 200 total = 199 PASS, 1 normal Robot SKIP each; physical
packaged Windows EXE baseline PASS. This is **not** a license approval.

## New risk/verification

`PdfCrossProcessRenderBoundaryTest` now exercises an actual separate
OS/JVM process in the **exact post-check/pre-link window**, using the
existing package-private `PdfExporter.BeforeMoveBoundary` test seam.
No production test hook was introduced. A real child JVM, launched via
`ProcessBuilder` and bounded to 15 seconds, creates a valid synthetic
one-page PDF target after all SHA/target checks and before
`Files.createLink`:

- `overwrite=false`: a new target allocated by the child must win; the
  parent must reject without touching the child-created PDF.
- `overwrite=true` against an **initially absent** target: even though
  the user opted to overwrite, that consent never covers an external PDF
  appearing after the final check. This path also must reject.
- For both cases, compare full target bytes with the original child source,
  parse the retained PDF with PDFBox and check one page, and ensure
  `.embasic-pending-`/backup staging is fully removed. All fixtures are
  rights-clean original synthetic `format_id=1.0` ZIPs and self-generated PDFs.

The distinctive regression is a cross-process OS filesystem claim **after**
the final check, whereas Tur220 was cross-process **mid-render** and
Tur221's after-check test used a same-process copy. No races depend on
sleeping or filesystem polling. The child is a standalone JDK test class
and cannot be shipped in the shaded product or packaged EXE.

## Honest physical Windows EXE/JDWP feasibility

A physical packaged Windows app-image can verify ordinary file chooser,
keyboard reorder, save PDF, overwrite refusal and source-change rejection
with OS-level Robot. Those tests do **not** suspend the actual PDFBox
render thread between document boundaries.

Physical render timing is not made deterministic merely by running a
second process or noticing an in-progress dialog. A credible opt-in
experiment would require a test-only runtime with a JDWP-enabled JDK
module and a loopback-only JDI debugger attached **before** the physical
Ctrl+S action; set a source-line/thread-scoped breakpoint in the packaged
application at the first document render boundary, wait for the
breakpoint event, mutate a temp synthetic UDF with an independent process,
and resume the correct thread before checking the physical error modal,
original PDF SHA and cleanup. Before using it, CI must establish that
the ephemeral jpackage runtime actually contains `jdk.jdwp.agent`,
that the debugger never binds an external interface, that no untrusted
source text is printed to logs, and that test and release runtimes cannot
be confused. This Tur does **not** enable JDWP in the production launcher,
and does **not** claim to have run that physical scenario.

**PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN** until real event ordering and
Windows EXE observations are all evidenced. Hardlink-unsupported external
filesystem physical acceptance also remains **NOT_RUN**; a successful
NTFS CI run does not simulate exFAT/network restrictions.

## Gate and next-step contract

Retain `PUBLIC_SOURCE_APPROVED_SHA` strict fail-closed source gate,
upstream SHA/license evidence, LGPL/AGPL/NOTICE concerns, lack of native
UYAP golden PDFs, unknown native UDF schema versions and
`WINDOWS_ATOMIC_TOCTOU=NO_GO` for previously existing approved targets.
Do not claim an OS-level compare-and-swap implementation. Do not use
private source, official Editor binary, undocumented API/CLI, real
judicial cases or secret data.

Run exact-final-HEAD Windows/Linux Maven/JUnit, independent opt-in
physical GUI Robot, 160 synthetic UDF benchmark, packaged Windows
jpackage EXE, PDF Engine probes, upstream audit and Secure Bootstrap.
Never replace a pending/failed workflow with earlier-SHA evidence.
Keep weighted planning score **44.3%** until measurable user value can
justify changing the scoring framework. No main merge, tag, installer,
ZIP, deploy, release or Private access.
