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
