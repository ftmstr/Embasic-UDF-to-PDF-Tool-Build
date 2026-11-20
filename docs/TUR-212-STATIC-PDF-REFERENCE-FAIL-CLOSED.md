# TUR 212 — Fail-closed static-PDF boundary for offline reference audit (2026-11-12)

## Why this matters
TUR 211 compared page geometry, extracted text and 96 DPI RGB raster.
An invisible PDF link annotation, interactive form or document-level action
may leave all three unchanged, producing a misleading MATCH. A pixel-parity
verdict is not proof of identical PDF semantics or security.

## Production change
The optional `OfficialPdfReferenceAudit` now rejects, on **both** inputs,
PDF catalog keys `OpenAction`, `AA`, `AcroForm`, `Names`, `Outlines`,
`Perms`, `Collection`, `OCProperties` and page keys `Annots`, `AA`,
`PresSteps`. Presence is rejected even if the object is visually invisible
or appears empty. Rejection occurs before text extraction or rasterization.
The CLI reports a generic error and exits 3; it does not log document
content, extracted text, filenames or screenshots.

The list is a deliberately conservative **static subset gate**, not a
complete PDF malware scanner or proof of equivalent PDF semantics. PDFs
with legitimate links, bookmarks, form fields or other excluded features
are UNSUPPORTED by this optional comparator, not declared invalid by UYAP
or the PDF specification. The main UDF import/PDF export pipeline is unchanged.

## Rights-clean adversarial evidence
`OfficialPdfReferenceAuditTest` creates two visually identical original
synthetic PDFs, then adds (a) a tiny link annotation and (b) a catalog
OpenAction to separate synthetic candidates. Both directions must throw
`IOException` rather than returning MATCH. The input bytes must remain
unchanged. No real UYAP source, document, PDF or native binary is used.

## Boundaries retained
- Read-only offline diagnostic; no network, file staging, UYAP CLI or integration.
- Up to 64 pages, 64 MiB per file and 8 million raster pixels per page.
- Existing PDFBox 3.0.8, SHA, license, source-approval and release gates unchanged.
- Windows/Linux JUnit, Windows packaged GUI Robot, jpackage and PDF Engine
  acceptance must be checked at the exact final commit SHA before claiming PASS.
- Real UYAP reference PDF parity remains NOT_RUN without rights-cleared paired files.
- Public release/ZIP/installer/tag remains NO-GO while source approval is blocked.

## Next practical work
Extend packaged Windows EXE acceptance to synthetic UDF multi-selection and
reorder with observable UI state, then verify PDF order without private data.
Do not add tests merely to increase the count. Review official-reference
comparison limitations only when actual rights-cleared samples exist.
