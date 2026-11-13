# TUR 244 — Physical packaged Windows EXE render race with isolated JDI

**Date:** 13 November 2026, Europe/Istanbul. **Only Public**
`ftmstr/Embasic-UDF-to-PDF-Tool-Build`,
`ci/secure-windows-bootstrap-20261018`.

## Nonduplication and baseline

TUR 243 exact-final `cb7bb704a0a0f1ef6eb402f32cb95e642c9d47f6`
verified at the Public branch and Drive report
`1_akN7It-wj2oweSmXL6Sd57QJIcvrf4yEsbjYO9x0E8` read.
The hourly task completed its 23:00 TRT run and recognized TUR 243
as already completed. No TUR 244+ report or commit existed before
this work. Prior TUR 243: Windows/Linux Maven 209 tests (208 PASS, 1
normal Robot SKIP each), separate physical Windows GUI Robot PASS,
packaged jpackage EXE SUCCESS; PDF Engine and upstream SHA SUCCESS;
Secure Bootstrap SOURCE_GATE BLOCKED. Physical packaged render-race
and unsupported hardlink volume remained NOT_RUN.

## New original CI-only physical test

Only the independently packaged, temporary **test image** receives
the `jdk.jdwp.agent` jlink module and
`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:51277`
launcher option. The ordinary app-image is created and tested
unchanged earlier in the workflow. The debug-enabled image is
`EmbasicJdiRace.exe`, under `RUNNER_TEMP/embasic-jdi-app-image`;
its image tree is always removed after the dedicated step and it is
never uploaded, installed, ZIP'd or released. The controlled port
must be free and is bound to **127.0.0.1 only**.

Files:

- `app/scripts/PackagedRenderBoundaryController.java` — an independent
  test-only JDK/JDI JVM with loopback-only SocketAttach. It verifies that
  the *actual packaged JVM* has loaded `PdfExporter`, locates the
  unique Java source line in `renderInternal`, and arms a JDI
  `BreakpointRequest.SUSPEND_EVENT_THREAD`. It accepts a breakpoint
  only when the suspended thread's stack includes a genuine
  `PdfExporter.save` call, excluding any background preview rendering.
  With that **real render thread paused** at the completed-document
  boundary, it replaces one temporary rights-clean synthetic UDF
  with a different synthetic UDF at the same pathname, restores
  last-modified time, emits only fixed status markers (no contents or
  filenames), then resumes the event thread. A missing/ambiguous
  breakpoint, timeout, remote endpoint, unauthorized source location,
  or failed mutation is NOT_VERIFIED, never PASS.
- `app/scripts/Assert-PackagedGuiInput.ps1` — the existing separate
  physical Robot script gains explicitly opt-in
  `-JdiRenderRace -ExePath`. Its default/regular packaged GUI
  acceptance remains unchanged. The JDI mode is permitted only for
  the isolated test-image pathname. It first uses physical Ctrl+S
  and PDFBox to produce and approve a real two-page synthetic PDF,
  then starts the independent JDI controller and requires its
  `READY` marker **before** a further physical Ctrl+S/overwrite YES.
  It requires the actual renderer `BOUNDARY_HIT` status, a physical
  `Embasic UDF hatası` dialog, changed UDF SHA after restoring
  mtime, byte-for-byte original approved PDF SHA, zero pending
  staging files, and correct GUI error dismissal. Only after all
  checks may `PACKAGED-JDWP-RENDER-RACE-PASS` be printed.
- `.github/workflows/public-mvp-windows-app-image-check.yml` — adds
  an isolated physical step after standard packaged Robot acceptance,
  building test-only app-image with JDWP and cleaning it in `finally`.
  The 35-minute hosted Windows workflow retains existing publisher
  prohibitions and `permissions: contents: read`.

No production Java, GUI, PDF engine, UDF parser, source validator,
normal launcher or release workflow is modified. Neither JDWP nor
the JDI controller is included in the published product.

## Evidence requirements

Only **real exact-final-SHA CI logs** can establish physical acceptance.
The normal packaged EXE acceptance alone is insufficient.
The log must show all:

1. Test-only debug app-image built under RUNNER_TEMP; normal image
   and physical keyboard acceptance remained successful.
2. A separate JDI JVM emitted `EMBASIC_TUR244_JDI_READY` while the
   real packaged renderer is loaded and breakpoint armed before
   save/overwrite.
3. A second physical Ctrl+S and overwrite YES led to
   `EMBASIC_TUR244_JDI_RENDER_BOUNDARY_HIT` from the real save
   thread and separate-process synthetic source mutation while
   suspended.
4. Physical `Embasic UDF hatası` modal; original PDF SHA unchanged,
   source SHA changed with same mtime and all staging removed.
5. A final `PACKAGED-JDWP-RENDER-RACE-PASS` after dismissing modal.
   If any check is missing, report
   `PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN/NOT_VERIFIED`, never PASS.

These tests do not establish an unchangeable filesystem snapshot,
native UYAP schema compatibility, all UDF versions or a true hostile
Windows compare-and-swap against an approved existing PDF. The
packaged debug image is isolated from users; no real judicial
document, private repository or copyrighted fixture is used.

## Security and distribution gates

The physical unsupported-hardlink filesystem scenario remains
`PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN` without a confirmed
volume that produces native Windows ERROR_NOT_SUPPORTED. In-memory
fault injection at the final link boundary remains a separate,
unit/integration-level proof only.

`WINDOWS_ATOMIC_TOCTOU=NO_GO` for hostile existing-target replacement;
native UYAP golden-PDF parity and all native UDF versions NOT_VERIFIED.
Source approval/NOTICE/third-party license distribution remains
`SOURCE_GATE=BLOCKED`; audit SHA success is not license approval.
No Private, main merge, tag, release, ZIP, installer or binary upload.

Keep weighted engineering progress **44.3%** until there is defensible
end-user evidence to change the milestones. This is not a UYAP
compatibility or product-release maturity score.
