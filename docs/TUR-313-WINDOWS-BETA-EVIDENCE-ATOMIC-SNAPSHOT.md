# TUR313 — Windows beta evidence: no pre-validation directories, stable EXE hash, no overwrite

Date: 2026-11-16 Europe/Istanbul. Only Public ftmstr/Embasic-UDF-to-PDF-Tool-Build / ci/secure-windows-bootstrap-20261018. Baseline TUR312 HEAD 64c8d5b6e476177d81f7d29531fb2a164b226ade. TUR306–TUR312 already exist; no rework.

## Concrete defect from TUR312's verified Drive report
scripts/Collect-PublicWindowsBetaEvidence.ps1 previously called New-Item -Force on the requested OutputDirectory BEFORE testing whether it lies inside the application image. A rejected malicious or mistaken path could thus create an empty directory in the app image, despite the collector claiming to write evidence only outside the app.

## TUR313 change
- Examine every existing path ancestor of the unresolved executable and requested output *before* any directory creation. Reject junctions/symlinks/reparse points, including the executable file itself; avoid reliance on Resolve-Path alone.
- Compare the intended output directory to the executable directory before New-Item. Recheck path components after creation to reject an intervening reparse-point change.
- Hash a read-only FileStream once and record its length from that SAME stream with FileShare.Read. Reject zero-byte launchers. This narrows inconsistent executable SHA/size evidence; it does not certify the entire JRE/app-image or prevent all adversarial filesystem races.
- Write evidence JSON via exclusive FileMode.CreateNew, not a prior Test-Path followed by a clobber-capable WriteAllText. No existing evidence can be silently overwritten in a same-second collision.
- On an actual Windows hosted runner, use the normal existing jpackage app-image EXE from the Public MVP workflow to test positive local evidence capture, exact executable SHA/size, NOT_RUN/NO-GO state, and a forbidden inside-app-image destination that must be rejected without creating a directory. This is a beta-tool acceptance, NOT physical external USER beta or any real UYAP golden.
- PowerShell progress dashboard release readiness explicitly marks this local proof as UNVERIFIED for external user acceptance and retains 44.3% weighted engineering estimate.

## Independent blockers not affected
The four Maven runtime JAR/NOTICE sources and three shared META-INF DEPENDENCIES/LICENSE/NOTICE paths still need independent legal human review; source ownership, LICENSE and THIRD-PARTY-NOTICES approval NO-GO. Native UYAP golden and all-version parity NOT_VERIFIED; unsupported-hardlink physical exFAT NOT_RUN; hostile existing-target CAS NO-GO; external real Windows beta NOT_RUN. Secure Bootstrap SOURCE_GATE remains intentionally BLOCKED. No Private repo, main merge, tag, release, installer, ZIP, binary/real UYAP files, or external upload.

After the code commit, record exact-SHA real Windows/Linux/Robot/jpackage/CI and Google Drive readback, including any failures and repairs.
