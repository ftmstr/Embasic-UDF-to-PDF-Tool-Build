# TUR409 — Reject trailing or concatenated compressed PNG payload hidden inside CRC-correct IDAT

**Scope/date:** 2026-11-20 Europe/Istanbul. Only Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`. Prior TUR405 exact-final `75f359f5b082233923093aed9545cd41398ecf38` matched start HEAD. The active hourly automation completed TUR408 with zero code change and this exact HEAD. Verified separate TUR408 Drive report. TUR409 is the first missing unique code-development turn; TUR405 page allocation is not repeated.

## Independently reproduced P0

A valid original 1x1 PNG generated with Java/Python standard zlib contains a single `IDAT` zlib stream. Modify its `IDAT` to append seven arbitrary bytes after the finished zlib stream, recalculate PNG chunk length and CRC; Java 21 `ImageIO.read` still returns the original source pixel `ff010203` and ignores the hidden payload. Append a second valid zlib stream: same silent behavior. The Public synthetic PNG preflight previously checked image dimensions, chunk order/types, CRCs and IEND but not the zlib stream's exact end. That allowed source bytes to disappear silently when PDFBox embedded the decoded PNG. This is a source-integrity/fidelity bug in the supported synthetic 8-bit PNG subset, **not authentic native UYAP compatibility evidence**.

## Narrow correction

`app/src/main/java/com/embasic/publicapp/DocumentElements.java`: collect consecutive IDAT chunk payloads (the entire PNG is already limited to 1 MiB), check zlib integrity and Adler-32 with Java `Inflater`, bound decompressed byte count to exactly `height × (1 + width × channels)` for 8-bit noninterlaced color types 0/2/3/4/6, and reject any `Inflater.getRemaining()` bytes after the completed zlib stream. Fail before the raster is accepted; never silently ignore extra streams. Preserve accepted multi-IDAT input, RGB/RGBA colors and alpha, and the existing strict ancillary/color-metadata gates. This is not a general PNG standards verifier.

`app/src/test/java/com/embasic/publicapp/PdfImagePixelFidelityTest.java`: ONE focused synthetic fixture test demonstrates platform `ImageIO` acceptance of CRC-repacked hidden trailing bytes, the new explicit decoder rejection, the accepted split-IDAT stream's pixel parity, and byte-for-byte preservation of an already approved PDF on failed overwrite plus no leftover staging. No native fixtures, large test sweep or external dependencies.

New source Git blob SHA-1: `18988408a775757ece769cd20e74b9d11642f6b4`; new focused test blob: `d786be4920f41e6c9f984d0d181204edd491447a`. `app/RELEASE_SOURCE_REVIEW.md` records exact current source identities and retains NO-GO; this doc contains technical evidence, not release approval.

## Required exact SHA CI

Check real Windows/Linux Maven/JUnit, Windows normal ephemeral packaged EXE physical GUI Robot and separate test-only EXE JDI acceptance, PDFBox/OpenPDF, independent upstream, Windows/Linux legal byte parity. Check Secure Bootstrap `SOURCE_GATE BLOCKED` without overriding. Do not claim any test PASS before exact-commit GitHub logs confirm it. Physical exFAT hosted VHD failures remain NOT_RUN, not a successful physical acceptance. Native UYAP golden NOT_VERIFIED/NOT_RUN; independent app/LICENSE/NOTICE/font/provenance NOT_VERIFIED; hostile CAS NO-GO; external Windows W01–W10 beta NOT_RUN. RELEASE NO-GO, engineering estimate 44.3% unchanged. No Private, main merge/tag/release/installer/ZIP or binary publication.
