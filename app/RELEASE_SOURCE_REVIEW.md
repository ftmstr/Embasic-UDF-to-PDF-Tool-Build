# Public app/ source and rights release gate — TUR346 (2026-11-18)

Status: **NO-GO / evidence inventory only**. This is not a license grant, ownership attestation, UYAP certification, or publication approval. The legacy `src/main` source gate remains independent and BLOCKED. `PUBLIC_SOURCE_APPROVED_SHA` alone is insufficient.

## Observed public source inventory (branch HEAD 088dcf08ca10c850e82b9928737f92f342bce876)

The independent `app/src/main/java/com/embasic/publicapp/` directory contains these 15 Java files:

- DesktopApp.java
- DocumentElements.java
- FileImportPolicy.java
- GridDropIndicator.java
- GridKeyboardSelection.java
- GridPointerSelection.java
- GridReorder.java
- OfficialPdfReferenceAudit.java
- OfflinePdfGlyphAudit.java
- PdfBoxFontCacheDirectory.java
- PdfExporter.java
- TableLayout.java
- ThumbnailSizing.java
- UdfReader.java
- VirtualGridLayout.java

Other observed app entry: `app/pom.xml`. The repository root listing contains `.github`, `.gitignore`, `README.md`, `app`, `audit-content`, `audit-openpdf`, `audit-pdfbox`, `audit`, `docs`, `scripts`. **No root LICENSE or NOTICE was present in that listing**; this is a scoped observation, not a full rights audit. No `app/src/main/resources` directory was found.

## Blocking acceptance requirements

1. Produce a file-by-file origin and copyright/author rights attestation for all 15 Java files, `app/pom.xml`, workflows, scripts, fixtures, fonts, and any packaged assets. Do not infer ownership from public GitHub visibility.
2. Review every dependency and transitive dependency license, corresponding license texts and NOTICE obligations against the exact pinned resolved build. Audit embedded fonts/images separately. Preserve the existing dependency SHA and provenance gates.
3. Establish an explicit, independently reviewed release authorization for the **public app/** source tree at a specific exact SHA. Do not let a CI success, environment variable, or this document substitute for authorization.
4. Obtain authorized, offline, rights-clean native UDF golden fixtures with reference PDF outputs and compare text, fonts, page geometry, tables, image positioning and page order. Reject unknown schema elements fail-closed. Native UYAP equivalence remains NOT_VERIFIED until independently established.
5. Complete physical Windows GUI and packaging acceptance, including real end-user save/open flows and supported filesystem behavior; physical exFAT remains NOT_RUN and hostile CAS remains NO-GO.

## CI evidence at inventory HEAD

- Desktop MVP SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961997
- PDF Engine SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961955
- Upstream Audit SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961871
- Public legal byte-parity SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961951
- Windows app-image SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211955516
- Secure Bootstrap FAILURE: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961877

These CI results apply **only** to the inventory HEAD, not to this document's new commit. A legal byte-parity workflow SUCCESS is not a source-license approval.

No main merge, tag, release, installer, ZIP or distribution is authorized. No synthetic smoke test added. No progress percentage changed.
