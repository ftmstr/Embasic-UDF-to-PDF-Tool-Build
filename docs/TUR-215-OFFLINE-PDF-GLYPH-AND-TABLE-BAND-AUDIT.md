# TUR 215 — Offline PDF font, text-placement and table-line diagnostic

**Date:** 2026-11-12 (Türkiye). **Scope:** Public-only
`ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch
`ci/secure-windows-bootstrap-20261018`.

## Why a new diagnostic is needed

The official Ministry of Justice [UYAP Editor help](https://uyap.gov.tr/Uyap-Editor-Yardim)
documents manual single-file **Dosya → Farklı Kaydet → PDF** and batch
**Dosya → Tümünü Farklı Kaydet**. No published unattended conversion API,
command-line interface or redistribution grant is inferred.

The independently authored `OfficialPdfReferenceAudit` (TUR211–213) checks
page count, MediaBox, CropBox, page rotation, UserUnit, Trim/Bleed/Art print
boxes, extracted text and whole-page 96 DPI raster. Its static-PDF gate rejects
potentially interactive content before comparison. These checks do **not**
distinguish invisible font substitutions or explain where table-grid raster
mismatches occur.

## New optional local comparator measurements

- `OfflinePdfGlyphAudit`: inspect bounded `PDFTextStripper` glyph positions
  with `TextPosition`, comparing normalized font-family names (strip only a
  standards-conforming six-uppercase-character subset prefix), size,
  glyph x/y coordinates, width, height and direction in points.
- Glyph limit: **50,000 per PDF page**, with invalid, nonfinite or absent font
  geometry rejected. Tolerances: **0.20 PDF points** on coordinates/extent,
  **0.05 point** on type size, **0.01 degree** on direction.
- `OfficialPdfReferenceAudit.PageCheck` records count-only
  `glyphCount`, `differentFontGlyphs` and `differentTextPositions`.
  Font or glyph-position mismatches prevent MATCH even if text and raster
  happen to agree. Source words, fonts files and PDF byte content never appear
  in public results.
- The same full raster scan now counts `changedRasterRows` and
  `changedRasterColumns` above the existing per-channel tolerance of 8.
  These are **pixel-band localization metrics**, not semantic table-cell,
  row-span or vector-grid reconstruction. A glyph that moves 0.2 pt or less
  may be regarded as equivalent under this specific diagnostic; a matching
  result is never an assertion of native UYAP fidelity.
- Refuses interactive PDF catalogs/annotations from TUR212 and preserves
  TUR213 physical page geometry checks. Works entirely offline with
  user-supplied rights-cleared PDFs; creates no output files or cloud uploads.
- Uses an ordinary private in-memory class for source-text snapshots, not a
  Java `record` with automatically generated `toString()` exposing text.
  Only aggregate mismatch counts are returned.

## Synthetic CI acceptance

`OfflinePdfGlyphAuditRegressionTest` creates separate, original, rights-clean
PDF documents with visible two-row/two-column *vector* table borders and a
separate white-on-white text run:

1. Same font, text, placements and table rules => MATCH with zero counters.
2. Invisible Helvetica -> Courier substitution, otherwise identical RGB
   pixels and extracted text => DIFFERENCE with `differentFontGlyphs > 0`.
3. White-on-white glyph x shift => changed text position with unchanged
   raster; independent table-divider y shift => changed raster rows/columns
   with unchanged glyph positions. Original source bytes remain unchanged.

No genuine UYAP UDF or native-editor-produced PDF fixture is present, so
native UYAP parity must remain **NOT_RUN**.

## Safety and publication controls

No changes to UDF import, PNG fail-closed parsing, PDF generation, user save,
the Swing UI, or Secure Windows Bootstrap. Source approval
`PUBLIC_SOURCE_APPROVED_SHA`, upstream SHA/license and Windows atomic
TOCTOU remain fail-closed **NO-GO**. No Private repo access, release,
installer, ZIP, tag or main merge. Existing numeric progress weighting stays
**44.3%** — a planning score, not a UYAP compatibility or release-readiness
certificate.

## Deferred next acceptance

Packaged Windows EXE synthetic document **Ctrl+S** output and physical
two-page PDF order verification (TUR214 backlog), plus approved real/native
reference trials only if non-sensitive, owner-authorized local golden documents
become available. CI success for this new Tur must be separately verified by
exact final Public SHA, not inferred from earlier commits.
