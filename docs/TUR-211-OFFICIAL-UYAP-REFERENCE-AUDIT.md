# TUR 211 — Official UYAP Editor as an opt-in, offline PDF reference (2026-11-12)

## Verified official user-facing features and scope

The user supplied https://udf.adalet.gov.tr/ as a reference. That hostname was not
reachable from this execution environment; **no functionality is inferred from
its unverified pages**. The Ministry of Justice's official public UYAP sites
document the supported workflow:

- https://www.uyap.gov.tr/Uyap-Editor — official UYAP Editor download page.
- https://uyap.gov.tr/Uyap-Editor-Yardim — "Dosya > Farklı Kaydet" allows
  a PDF output; "Dosya > Tümünü Farklı Kaydet" lets a user select files/folders
  and save multiple UDFs as PDF, DOC, ODT or RTF into a chosen output folder.
- https://bigm.adalet.gov.tr/Home/SayfaDetay/uyap-dokuman-editoru-guncellendi01062026122201
  — official 31 May 2026 update notice instructing users to update the Editor.

The guide documents a **human-operated desktop workflow**, not an approved
headless PDF conversion API, unattended command-line entry point, service,
source-license, or binary redistribution permission. Never invoke a guessed
CLI, reverse-engineer/download/copy official binaries into this Public repo,
or replace the product's independent PDFBox renderer with an unapproved
proprietary dependency.

## Safe offline fidelity workflow (only rights-cleared documents)

1. Independently prepare/obtain a rights-cleared, non-confidential UDF.
   Avoid real investigations, personal data, signatures, or case documents.
2. In the official installed Editor, use **Dosya > Farklı Kaydet** and choose PDF.
   Or use **Tümünü Farklı Kaydet** to export multiple UDFs *as separate PDF
   files* into an owner-controlled folder. Do not assume the editor combines
   them in a chosen order.
3. Export the **same UDF** with Embasic into another, local PDF. If the
   synthetic parser rejects it, record it as UNSUPPORTED. Never silently
   simplify unsupported elements or call the result parity.
4. Run the original local comparator below. It only reads the two local
   PDFs; it never calls the UYAP Editor, never contacts a server, never
   saves a rendered image, and never publishes original content or its text.
5. Review differences: page count/order, MediaBox and CropBox/rotation,
   PDFTextStripper extraction, and full-page RGB raster at 96 DPI. Anti-
   aliasing varies by renderer and platform; a difference is diagnostic,
   not automatic evidence that one product is wrong. A matching comparison
   likewise does **not** establish support for all UDF versions or native
   UYAP PDF equivalence.

Windows PowerShell from the public repository root, after `mvn -f app/pom.xml
clean verify` (requires JDK 17+ and Maven):

```powershell
java -cp app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar `
  com.embasic.publicapp.OfficialPdfReferenceAudit `
  "C:\local-audit\official-reference.pdf" "C:\local-audit\embasic-candidate.pdf"
```

Exit code **0**: compared pages matched under this limited rule.
Exit code **2**: at least one discrepancy. Exit code **3**: missing, malformed,
identical physical path, oversized or otherwise invalid input. The result
contains only counts and status; no text, page images, paths, or document
content. The checker is a **separate optional diagnostic** and is not
on the product's automatic UDF import/export path.

### Audit boundaries

- No more than 64 PDF pages per input; file size 1..64 MiB each.
- PDFBox renders at 96 DPI in RGB; at most 8 million pixels per page.
- Geometry tolerance: 0.05 PDF points; per-channel raster tolerance: 8/255.
- Text extraction is compared exactly, without printing actual text.
- No cross-process, Windows handle-based TOCTOU guarantee or protection
  against an adversary swapping an input file while it is opened.
- Only two original rights-clean, **synthetic** PDFs are created and exercised
  by CI tests; there are no UYAP binaries, native PDF golden files or
  genuine case documents committed, and no native equivalence claim.
- Source/NOTICE/distribution gates remain fail-closed and separate.

## Acceptance and next steps

`OfficialPdfReferenceAuditTest` verifies: independently serialized identical
synthetic PDFs, changed drawn color despite identical text, changed extracted
text, changed page count/size, same physical path and malformed input rejection,
and huge-page pre-render budget rejection. Real official-reference comparison
remains **NOT_RUN** pending owner-supplied, explicitly authorized, non-sensitive
manual exports. Actual engine behavior must be confirmed by exact HEAD
Windows/Linux Maven CI before reporting PASS.

This TUR is a bounded diagnostic addition; it is not authorization to publish
any installer, ZIP or UYAP-related asset.
