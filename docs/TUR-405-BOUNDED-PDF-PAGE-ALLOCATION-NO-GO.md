# TUR405 — Bound merged synthetic PDF page allocation before resource exhaustion

**Date:** 2026-11-20 Europe/Istanbul. **Repository:** only Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`.

## Deduplicated baseline
TUR396 exact-final `5ff9aa0574e65ad4ea9f0c0f8514b9b809d8ccdd` verified. Hourly automation's latest TUR404 report at 09:02 TRT declared HEAD `b1ff9304fd12dd72b48ab8f4615cdf3af1514d2f`, zero code commits for that run. The live GitHub branch HEAD exactly matched `b1ff9304fd12dd72b48ab8f4615cdf3af1514d2f` before TUR405 changes; TUR398 was a source-identity report, not a code feature. TUR405 is the next unique improvement, not a repeat of TUR396 indentation.

## Reproducible P0 risk
`PdfExporter.Layout.newPage()` allocated a `PDPage` with no page-count budget. A bounded synthetic UDF with thousands of valid interior newlines (or multiple individually modest documents) could allocate unbounded PDF pages across a merge, exhausting heap during offline conversion and preventing controlled failure. The importer has archive byte and 160-document limits, but no limit on expanded page count. The final PDF page list was not bounded.

## Narrow correction
`PdfExporter.java` adds a single shared production `MAX_PDF_PAGES = 2048` bound. Every `Layout.newPage()` checks `pdf.getNumberOfPages() >= pageBudget` before allocating. If exceeded, throw `IllegalArgumentException`, preserve the existing `renderInternal` PDDocument close-on-error path and the `save()` staged-output cleanup. No partial PDF is returned or silently truncated. A package-private `renderWithPageBudgetForTest` validates the same actual allocation path using a tiny budget without high-volume synthetic CI spam. Preview's preexisting first-page early exit is preserved.

`PdfTrailingPageTest.java` adds ONE focused test: 2 merged one-page docs pass under a test budget of 2; the third is rejected rather than omitted; many interior newlines in one small document fail under budget of 2; its thumbnail still has one page; zero budget rejected. Source blobs: `PdfExporter.java` `43ee24c2b3bb749e037a203d9c193aa2beacb0da`, `PdfTrailingPageTest.java` `9e60edd3f07da4f4b57989b19575086d4dc06ca4`. The page limit is a conservative synthetic MVP policy, not a guarantee against every possible memory-exhaustion mechanism or authentic UYAP layout support.

## Required exact-SHA evidence and gates
Run real Windows/Linux Maven/JUnit and physically hosted Windows packaged EXE Robot/JDI on the exact final branch SHA; record independent PDF-engine, upstream and legal-byte parity jobs and expected Secure Bootstrap failure. Do not mark these PASS before GitHub logs confirm them. Public app/ rights, LICENSE/NOTICE and bundled font provenance NOT_VERIFIED, legacy `src/main` SOURCE_GATE BLOCKED, authentic native UYAP golden NOT_RUN, hostile CAS NO-GO, physical exFAT NOT_RUN, external Windows beta NOT_RUN. RELEASE NO-GO. No Private repository, main merge, tag, release, installer, ZIP or binary publication.
