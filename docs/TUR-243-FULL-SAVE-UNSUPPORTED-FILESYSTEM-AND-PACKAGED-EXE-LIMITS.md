# TUR 243 — Complete PDF save fail-closed on unsupported filesystem operations

**13 November 2026 — Public only** `ftmstr/Embasic-UDF-to-PDF-Tool-Build`,
branch `ci/secure-windows-bootstrap-20261018`.

## Actual baseline, anti-duplication

TUR 242 exact final SHA
`a6fc0832d918ed88d8175d0c6490a443d061c0f7`
was verified directly on the only allowed Public branch (ahead=0,
behind=0). Its canonical Drive report
`13woUlCCFqPp9lZBUpVSQhV-BNXPYaOzv4y6lGmlT5xs`
was read back. The hourly task was still enabled with a next
23:00 TRT run; no TUR 243 or later report was present.
No earlier developments were duplicated.

TUR 242's Windows/Linux 207 JUnit = 206 PASS + 1 normal Robot SKIP
each, separate Windows physical Robot PASS, packaged jpackage EXE PASS,
PDF Engine/upstream SHA SUCCESS and Secure Bootstrap SOURCE_GATE
BLOCKED remain baseline facts, not TUR 243 results.

## Why a new regression?

Tur 226 exercised `PdfExporter.publishValidated` with test-only
`AtomicPdfLink` providers throwing
`UnsupportedOperationException` and `FileSystemException`.
That **unit** test correctly retained its manually constructed
staging file and rejected the destination. It did NOT prove that
a full GUI-equivalent `PdfExporter.save` call would close PDFBox,
clean the actual `.embasic-pending-*.pdf` file in its finally block,
and preserve a preapproved different PDF. Tur 219 similarly covered
a low-level unsupported `ATOMIC_MOVE` response.

Tur 243 adds two **distinct end-to-end save regression methods**
without shipping any debug runtime:

1. `app/src/main/java/com/embasic/publicapp/PdfExporter.java`:
   existing 6-argument test helper now delegates to a package-private
   8-argument helper accepting the already present `AtomicPdfReplace`
   and `AtomicPdfLink` interfaces. **Every real GUI and CLI path
   retains the actual `Files.move(...ATOMIC_MOVE...)` and
   `Files.createLink` implementations.** The hook accepts no
   runtime option, user-controlled path, environment variable, network
   listener, native UYAP API or debugger.
2. `app/src/test/java/com/embasic/publicapp/PdfOutputCommitTest.java`:
   create an original rights-clean `format_id=1.0` ZIP UDF, parse it
   with the real UDF reader, fingerprint through the current two-pass
   SHA gate, then **render, serialize and reopen a real one-page
   synthetic PDF with PDFBox**. At the final filesystem boundary,
   reject `Files.createLink` using the deterministic test-only
   provider with either:
   (a) `UnsupportedOperationException` or
   (b) `FileSystemException`.
   Run one with `overwrite=false`, another with overwrite-selected
   `true` while the destination was originally absent. Both must
   reject, avoid partial target publication, keep the separately
   approved PDF byte-for-byte unchanged, and leave **zero** pending
   or backup files. Marker `EMBASIC_TUR243_FULL_SAVE_LINK_UNSUPPORTED`.
3. The second JUnit exercises a previously approved existing PDF and
   synthetically throws `AtomicMoveNotSupportedException` only after
   a newly staged PDF was validated. Verify the approved PDF's bytes
   and PDFBox page structure remain intact, with no backup or staging
   leftovers. The test rejects any accidental use of the hard-link
   branch for an approved overwrite. Marker
   `EMBASIC_TUR243_FULL_SAVE_ATOMIC_UNSUPPORTED`.

Both test methods require real PDFBox serialization and reload before
invoking the fault injection; they verify an original PDF remains valid
as a PDF, not merely that an arbitrary string survives.

## Physical Windows EXE reality: NO fake evidence

The standard ephemeral packaged jpackage EXE acceptance already
exercises physical chooser, Ctrl+S, ordered two-page PDF, overwrite NO,
stale-source YES/error dialog and original PDF SHA/staging preservation.
It does NOT create an exFAT, FAT, WebDAV or network-backed volume known
to deny hard links. The new fault injection exists **inside JUnit**
and cannot be called from the normal packaged EXE. Therefore:

- `PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN`.
- `PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN`.
- The full-save synthetic regression PASS is **not** a physical
  unsupported-volume acceptance claim.

A potential future test-only physical race would need a separate
ephemeral `jpackage` app-image built with a debug-capable Java runtime
and `--java-options` restricted to a loopback JDWP listener. An
external JDI controller would have to suspend the **actual PDF render
thread**, observe the breakpoint event, mutate only temporary
rights-clean UDFs in another process, resume it and verify modal,
original SHA and staging. A generic delay/polling test cannot prove
ordering. The shipped production runtime must contain no debugger
and no diagnostic launch options. These JDK facilities are documented
at https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html
and https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/request/EventRequest.html.
TUR 243 does **not** execute such a physical breakpoint experiment.

## Release and test contract

Run the exact-final Public HEAD five workflows and inspect real job logs:
Windows/Linux Maven/JUnit, 160 original synthetic ZIP UDFs and 512MiB
heap, separate opt-in Windows GUI Robot, independent packaged Windows
EXE, PDFBox 3.0.8 + OpenPDF 2.0.5 probes, upstream hashes, and Secure
Bootstrap source gate. A normal JUnit Robot SKIP is not the separate
physical Robot PASS. Unfinished/failed work must never be labeled PASS;
correct failures within this tour.

Do not invent official native UYAP golden-PDF equivalence, all
`format_id` versions, Win32 atomic compare-and-swap for existing PDF
replacement or source/license/NOTICE redistribution approval.
`WINDOWS_ATOMIC_TOCTOU=NO_GO`; `SOURCE_GATE=BLOCKED` until reviewed
source approval is supplied. No Private access, main merge, release,
tag, ZIP, installer, publication or third-party sensitive UDF.

Weighted engineering planning progress remains **44.3%** (not a native
UYAP compatibility or distributable product readiness percentage).
