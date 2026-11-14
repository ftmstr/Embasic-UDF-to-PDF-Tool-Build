# TUR 221 — Late-created PDF after final overwrite-consent check

**12 November 2026 — Public-only**  
Repository: `ftmstr/Embasic-UDF-to-PDF-Tool-Build`  
Branch: `ci/secure-windows-bootstrap-20261018`  
Start: `5eb21bf7d27ce537c3b3aec55e1d212fd47e6bce` (TUR220).

## Specific defect and production fix

The existing optimistic `TargetSnapshot.verifyUnchanged` rejects a PDF
that appears during rendering. A narrower but real gap remained: if the
user chose overwrite while the destination was **absent at capture time**,
another process could create a PDF **after the final existence check but
before** `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`. The latter would
replace the new file without its owner's consent.

`PdfExporter.save` now calls `publishValidated(staging, target,
overwrite && originalTarget != null)`. Thus an initially absent
destination always uses the existing no-replace move path, even if the
save dialog supplied overwrite=true. The initially existing and explicitly
approved overwrite path still uses atomic replacement. This is a narrow
data-loss prevention fix, not a universal Windows compare-and-swap.

## Deterministic, rights-clean regression

`PdfOutputCommitTest.overwriteConsentForAbsentTargetCannotClobberLateExternalPdf`
creates an independent valid one-page synthetic PDF in JUnit `@TempDir`.
A package-private regression-only `BeforeMoveBoundary` runs **after**
`TargetSnapshot.verifyUnchanged` and **before** the move. It copies the
external PDF into the previously empty destination, then resumes publication.

Expected: `FileAlreadyExistsException` is mapped to a consent error;
external PDF bytes and page count are unchanged; staging is removed.
A second, uncontended overwrite=true save to a missing path still succeeds.
No sleeps, polling, real user documents, official UYAP UDF, or packaged EXE
debug hooks are required. Regression marker:
`EMBASIC_TUR221_LATE_CREATE_NO_CLOBBER`.

## Limits and gates

The JUnit seam is package-private and invoked with a no-op by normal
GUI/CLI callers; it does not ship a new user-facing feature. The test
is not a physical packaged Windows EXE race and does not prove hostile
check-to-atomic-replace CAS for **previously existing** PDFs:
`WINDOWS_ATOMIC_TOCTOU=NO_GO`. Preserve
`PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN` until directly evidenced.

Do not alter private repository, source/NOTICE approval, release gates,
official UYAP compatibility claims, progress score (44.3% engineering
planning only), main, tags, releases, ZIP or installers. Verify exact-final
SHA Windows/Linux JUnit, physical Windows app-image Robot, PDF engine,
upstream dependency audit and secure bootstrap; distinguish SUCCESS,
FAILURE, SKIPPED, QUEUED, and NOT_RUN.

Next: investigate existing-approved-PDF check-to-atomic-move race with
a provider-supported conditional operation, if available, or keep explicit
NO-GO rather than claim a nonexistent CAS guarantee.
