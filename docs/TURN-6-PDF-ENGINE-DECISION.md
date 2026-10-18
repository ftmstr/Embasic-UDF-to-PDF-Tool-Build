# Turn 6 — iText 2.1.7 replacement decision and safely isolated probes

**Date:** 2026-10-18. **Status:** Architecture research and synthetic
candidate tests only; **no** private application code, real UDFs, logos or
binary release transferred to this Public Build repository.

## Objective and hard constraints

Original source remains Private. Preserve existing UDF XML/ZIP parser,
document model, Explorer UI and conversion behavior. A candidate PDF
library may replace only the PDF-writing/merging layer once a separate
Private branch and realistic quality comparison are authorized.
**Do not silently replace the dependency in the original POM.**

## Compared candidates (exact sampled versions)

| Candidate | License position | Java | Compatibility with old com.lowagie API | Principal risk |
|---|---|---|---|---|
| Apache PDFBox **3.0.8** | Core Apache License 2.0; additional dependencies and image plugins require individual notice/license review | Java 8 minimum; compatible with project's Java 17 | **Not API-compatible**; lower-level page/content stream primitives rather than automatic paragraphs/tables | Reimplement pagination, tables, images, Unicode layout and headers/footers to avoid UDF fidelity regression |
| OpenPDF **2.0.5** | Dual LGPL 2.1/MPL 2.0 metadata, not unrestricted/shade exempt | Java 17 for 2.0.x | Retains the `com.lowagie` package for 2.x; direct API parity must be separately proven | Source/notice/relink or chosen MPL terms and potential actual API behavior differences |
| Existing iText **2.1.7** | Historic MPL 1.1 / historically described GNU Library GPL alternative, exact private compliance unresolved | Currently compiled in old project | Existing baseline | Legacy security/IP exposure, historical dependencies and unresolved executable source/notice duties |

Apache PDFBox 3.0.8 was published July 2026; pin exact versions and inspect
effective dependency trees and upstream licenses before any future shipment.
OpenPDF 2.1.x requires Java 21; OpenPDF 3.x changes package names to
`org.openpdf`; neither is a drop-in replacement for this Java 17 product.
Do not conflate OpenPDF 2.0.5 licenses with the original iText 2.1.7 rights.

## The original application's dependency boundary (private review only)

The UDF ZIP/XML parser is independent of `com.lowagie` and should
not need modification. The existing document model can remain the
backend-neutral intermediate representation.

Main components in a staged migration:

| Existing responsibility | iText APIs currently used | PDFBox alternative / scope |
|---|---|---|
| Structured document rendering | `Document`, `PdfWriter`, `Paragraph`, `Chunk`, `Image` | `PDDocument`, `PDPage`, `PDPageContentStream`, `PDImageXObject`; **custom** line-breaking, styling, image anchoring and paragraph flow |
| Tables and merged cells | `PdfPTable`, `PdfPCell` with rowspan/colspan, borders and split rows | Explicit layout/pagination, rectangle borders and per-cell text. High risk; no native drop-in high-level table engine |
| Turkish/Unicode fonts | Embedded `BaseFont.IDENTITY_H` with Windows TrueType lookup | `PDType0Font` font embedding, real glyph coverage, shaping/metrics and multi-page subset verification |
| Headers and footers | `PdfPageEventHelper` with positioned tables | Multi-pass page layout and explicit fixed-position content streams |
| Ordered PDF merge | `PdfCopy`, `PdfReader` | `PDFMergerUtility` plus `Loader.loadPDF`, stream-cache management |
| Content smoke/inspection | `PdfReader`, `PdfTextExtractor` | `Loader.loadPDF`, `PDFTextStripper`, page MediaBoxes |

**The hardest task is document layout, not basic PDF merging.**
A naive POM edit would break PDF generation and likely lose page fidelity.

## Proposed stage gates (NO implementation changes yet)

1. Introduce a narrow `PdfBackend` or `PdfDocumentEngine` interface only
   in a **Private feature branch** after baseline tests exist.
   Keep existing iText engine as a comparison baseline without removing it.
2. Prove a source-free PDFBox 3.0.8 synthetic create/image/merge/text
   operation with real Maven 3.x transitive dependency tree. Current
   source-free test project: `audit-pdfbox/`.
3. Run an OpenPDF 2.0.5 **independent** paragraph/table/merge API
   compatibility probe in `audit-openpdf/`. Syntax and runtime PASS
   do not settle package/license obligations.
4. Prepare a *Private* golden UDF corpus with varied UYAP formats,
   corrupt cases, tables, images, list numbering, headers/footers,
   line breaks, overlapping/merged cells and unicode Turkish characters
   `İ ı Ğ ğ Ş ş Ü ü Ö ö Ç ç`. Store neither real files nor personal
   data in Public GitHub. Synthetic public samples may complement but
   cannot substitute for authorized real-document review.
5. Compare page count/order, pixel rendering, font embedding, Unicode
   extraction, margins, table geometry and page-break accuracy
   on both implementations. Run normal-size, many-file and large-file
   tests; check memory bounds and temporary file cleanup.
6. Separately verify actual shipped dependency JARs and attribution
   texts, source rights/brand permissions, security/vulnerability
   status, and executable artifact notices. Review selected legal route.
7. Only after evidence-based approval remove the old library and old
   historical transitive Bouncy Castle classes, rebuild, and test
   the final Windows portable ZIP/installer under secure CI.

## Independent synthetic tests

- `audit-pdfbox/pom.xml`: Apache PDFBox 3.0.8 on Java 17,
  standalone synthetic two-page PDF creation, image insertion,
  ordered merge, text extraction and page-size checks.
- `audit-openpdf/pom.xml`: OpenPDF 2.0.5 on Java 17,
  synthetic `com.lowagie` paragraph/table/PdfCopy/PdfReader checks.
- `.github/workflows/pdf-alternatives-probe.yml` runs them in
  separate hosted jobs, does **not** upload any binary artifacts.
- Neither is an authentic UDF conversion, a full method-for-method
  API compatibility test, a Unicode-font fidelity certification, a
  license approval, or an endorsed source migration.

Read the final CI job conclusions before marking either project PASS.

## Official references

- PDFBox 3.0.8 release/Maven setup:
  https://pdfbox.apache.org/3.0/getting-started.html
- PDFBox requirements and optional plugin/dependency licenses:
  https://pdfbox.apache.org/3.0/dependencies.html
- PDFBox 3.0 migration guide:
  https://pdfbox.apache.org/3.0/migration.html
- Apache License 2.0:
  https://www.apache.org/licenses/LICENSE-2.0
- OpenPDF Java requirements, package migration and dual licenses:
  https://github.com/LibrePDF/OpenPDF
- Exact sampled OpenPDF Maven artifact:
  https://central.sonatype.com/artifact/com.github.librepdf/openpdf/2.0.5

## Security and release decision

Original application copyright permission, PNG/ICO provenance and
redistribution rights, the historical iText legal route and a complete
confidential private-tree snapshot are still blocked or not performed.
**Do not merge this Draft PR, set approval SHA variables, upload actual
application source, or make a release based on the standalone probes.**
