# Embasic UDF to PDF — Public-only Development

**As of 2026-10-27, this Public repository is the only active development destination.** The historical Private repository is no longer a source of truth, build dependency, or source-transfer target. Do not copy, reference, or publish its contents, history, artifacts, or genuine UYAP documents here.

## What is working in source

- `app/`: **new, independently authored Java 17 Swing / PDFBox 3.0.8 desktop MVP**. Opens a thumbnail workspace, adds multiple files from a dialog or Explorer drop, reorders selected file cards, supports basic light/dark colors, and saves a PDF in item order. File reading and previews happen off the Swing UI thread.
- Safe `.udf` ZIP scanning and fail-closed `content.xml` parser: limited `template` / `format_id` / `content` / `elements` / `paragraph` XML subset with UTF-16 offsets and named basic styles. Unsupported tables, images, headers, signatures, exotic fonts, or unknown versions **fail with an explicit error**; no silent omission.
- PDFBox Unicode-font-backed A4 PDF output, same-directory temporary staging, PDF re-open validation, overwrite confirmation/guard, ordered multiple documents.
- `.github/workflows/public-mvp-ci.yml`: compile/JUnit/CLI-smoke on Ubuntu and Windows. **No binary publishing**.

This is **not** a verified UYAP-equivalent converter. No authentic UDF fixtures are stored or used here.

## Build and run locally

Requires Maven and Java 17 or newer. A suitable font must be installed on the local PC (Windows Arial/Segoe UI or Linux DejaVu Sans). Font files are not distributed here.

```powershell
mvn -B -ntp -f app/pom.xml clean verify
java -jar app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar
```

Command-line smoke mode:

```powershell
java -jar app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar --merge output.pdf one.udf two.udf
```

This initial app supports a **very limited UDF subset**. Its presence in GitHub does **not** mean a released Windows executable exists.

## Other directories and release controls

- `audit/`, `audit-content/`, `audit-pdfbox/`, `audit-openpdf/`: isolated synthetic fidelity, font, drawing geometry and PDF content tests. **Not** the active executable app.
- `docs/BUILD-GATES.md` and `.github/workflows/windows-ci.yml`: earlier import/release approval controls retained intact. They intentionally do not approve or publish the new binary.
- Publication of a downloadable Windows package will require public-only application acceptance tests, review of Apache PDFBox and transitive dependency notices/licenses, approval of exact release bytes/checksums, and a verified Windows launcher smoke. No proprietary resources or OS fonts may be redistributed without authorization.
- No automatic release, Git tag, installer, or ZIP is being asserted.

## Immediate product priorities

1. Windows CI for the new Public app; fix any build or UDF parsing failures.
2. Add and validate UDF table/image/page-layout subsets with rights-clean synthetic fixtures; do not silently accept unsupported elements.
3. Add more faithful document preview/reordering/selection; harden output rollback and logging.
4. Verify Windows portable app-image and redistribution notices **before** enabling artifact publishing.
5. Conduct strictly offline accuracy comparison against owner-authorized UYAP-native PDFs; never upload case files to Public GitHub.

**License note:** The new code is original in this Public repo; the presence of code does not grant blanket redistribution permission for third-party fonts, logos, legacy Private code or previously bundled libraries. Binary release authorization remains a separate decision.

## Public MVP — PowerShell release dashboard (2026-10-28)

Run the **color PowerShell roadmap** directly in the Public checkout:

```powershell
pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1
pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1 -Offline
pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1 -Offline -AsJson
```

The dashboard reports a manually weighted release-readiness estimate (**42.7% at the 2026-10-28 review**), milestone evidence and GitHub Actions status. The forecast is **conditional and low confidence**: an early Public beta in about 3–6 weeks; a broader final candidate in approximately 10–18 weeks *only if* owner-authorized test fixtures, redistribution rights and UYAP visual fidelity work are resolved. These dates are **not a promise** and all-UDF-version parity is not certified. The dashboard source and JSON schema run through the Windows CI job.

**Safety fixes:** PDFBox wrapped large-font lines now retain their proper line height; unknown page properties are rejected rather than quietly rendered on A4. The older source-publication, license and binary-release approval gates remain closed.

## Tur 37 — ZIP-local synthetic PNG references and bounded UI import (2026-10-29)

The independently authored Public-only application now accepts **exactly one more documented synthetic subset**, without claiming authentic UYAP archive compatibility:

```xml
<template format_id="1.0">
  <content>BEFORE AFTER</content>
  <properties pageSize="A5" orientation="landscape" margin="32"/>
  <elements>
    <paragraph><content startOffset="0" length="6"/></paragraph>
    <image ref="media/logo.png" mime="image/png" width="40" height="40"/>
    <paragraph><content startOffset="7" length="5"/></paragraph>
  </elements>
</template>
```

The ZIP must include `content.xml` and a literal `media/logo.png` entry. This is a **test contract invented for the Public MVP**, not a proven native UYAP `<image>` schema. The reader refuses external URLs, `../` traversal, ambiguous case-folded duplicates, unsupported file types, missing references, mixed inline+reference payloads, oversized PNG resources and decompression dimensions above four million pixels. Nothing is unpacked to disk or uploaded to a server. Synthetic page sizes are A4, A5, Letter and Legal; unknown layout metadata fails closed.

Desktop preview uses two bounded background workers, a card limit of `FileImportPolicy.MAX_CARDS`, native-aspect thumbnail scaling and cancellation checks when a file card is removed. This does **not** certify performance with authentic large UDFs or all file variations. Unit tests exercise 2,500 proposed paths, safe ZIP media resolution and ordered PDF text/image output; the Windows/Linux CI is authoritative for execution status.

**Remaining blockers:** no real native UYAP visual-parity evidence, incomplete image/metadata schema support, no supervised Windows GUI acceptance test, and unresolved third-party license/NOTICE and release-gate approvals. No downloadable Windows binary or final release has been authorized.
