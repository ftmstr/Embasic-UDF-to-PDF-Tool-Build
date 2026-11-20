# TUR 241 — Synthetic UDF SHA-256 open-stream truncate/growth regression

**Date:** 13 November 2026. Scope: ONLY Public
`ftmstr/Embasic-UDF-to-PDF-Tool-Build`,
`ci/secure-windows-bootstrap-20261018`.

## Grounded baseline; never repeat a completed tour

- TUR 223 exact final `0bb0ccb112a04bd8c04579af4a559a6fd54fc300`
  and its Drive report were verified.
- The hourly automation continued through TUR 240 exact Public HEAD
  `bb7dbd521f39bdcdb2677854da03f06a52a61b78`.
  The canonical Drive report for TUR 227 includes actual TUR 228–240
  appended progress. No prior Tur 241 existed before these edits.
- The TUR 240 source-read safety fix made `sourceSha256` compare initial
  archive size, bytes consumed and the size at stream completion; its
  Public SHA Windows/Linux 203 Maven JUnit (202 PASS + one ordinary
  `GuiRobotSmokeTest` SKIP), packaged Windows app-image, PDF Engine and
  upstream dependency checks completed SUCCESS. Secure Bootstrap still
  FAILED because exact source approval was absent. No native UYAP golden.

## New nonduplicated test and testability work

The previous TUR 240 CI ran many tests but had no deterministic test
for a source **actually changing while its InputStream was open**.
TUR 241 introduces a package-private `FirstHashChunkBoundary` hook
into `FileImportPolicy.sourceSha256(Path, hook)`. The existing public
GUI/CLI `sourceSha256(Path)` keeps its signature and delegates to a
no-op. The hook is called **once after the first positive 64 KiB
hash chunk and while the actual `InputStream` is still open**. It
never adds production options, external input or hidden debug listeners.

Two new `FileImportPolicyTest` methods generate a rights-clean,
~300 KiB `format_id=1.0` ZIP whose `content.xml` contains only
original repeated synthetic characters, with ZIP compression disabled
to guarantee multiple disk reads:

1. **truncate while reading**: after the first chunk is digested,
   synchronously truncate the opened source to zero through an
   independent filesystem write handle. The immutable original-size
   check must reject the partial hash. The now-empty file must also
   fail at the ordinary import gate; never treat the empty stream as
   a valid fingerprint.
2. **grow while reading**: after the first chunk append 8 KiB of
   synthetic bytes using the filesystem while the read stream is
   open. The size/byte-count contract must reject the in-flight
   fingerprint; a later stable rehash may return a *new* digest.

Require exactly one callback invocation; no timer, filesystem polling,
network or real user UDF. Printed markers contain status only, no source
text or source file path.

These tests prove only that *observed* size changes cannot be accepted
as an unchanged source during hashing. Equal-size adversarial changes,
mutations after final size check, malicious file providers and
Windows handle-bound atomic snapshot semantics remain **NO-GO**.

## Safety and acceptance boundary

Check every final Public commit's exact SHA on Windows/Linux Maven,
JUnit result totals and two new markers, 160 synthetic UDF benchmark
with memory cap, separate physical Windows GUI Robot, independent
packaged jpackage EXE physical input/order/PDF safety, PDFBox/OpenPDF
probes, upstream SHA/NOTICE and Secure Bootstrap preflight.

Do not label a normal JUnit GUI skip as physical Robot PASS. The
packaged Windows EXE real cross-process **render** race remains
`PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN`: SHA stream mutation in a
test JVM is not an actual packaged UI render hook. Unsupported
hard-link volume physical acceptance remains separate.

Keep source gate closed, no Private access, main merge, tags, installers,
ZIP, release or external upload. No real native UYAP UDF, official
Editor CLI/API assumption, golden PDF parity, release licensing or
redistribution approval. `WINDOWS_ATOMIC_TOCTOU=NO_GO` for hostile
existing approved PDF replacement.

The weighted engineering planning score stays **44.3%**, not a native
UYAP compatibility or beta acceptance percentage.
