# TUR 260: disposable exFAT volume / physical packaged Windows GUI acceptance

**Date:** 14 November 2026. **Only Public**
`ftmstr/Embasic-UDF-to-PDF-Tool-Build`,
`ci/secure-windows-bootstrap-20261018`.

## Why this is different from TUR 245–259

TUR 250 onward demonstrated NTFS **input alias** rejection: two
hard links to one UDF are duplicates and must not be merged. TUR 259
additionally checked that both source UDF byte SHA-256 values were
unchanged after the rejected CLI merge. This is distinct from trying
to publish a new PDF on a file system that **does not support hard
links at all**.

Microsoft's official file-system comparison lists exFAT hard links
as **No**, while NTFS hard links are **Yes**:
https://learn.microsoft.com/en-us/windows/win32/fileio/filesystem-functionality-comparison/

The current PDF publisher uses `Files.createLink(target, staging)`
for a target initially absent, avoiding an unsafe check-to-move
replacement race. Unlike an ordinary POSIX rename, this is **not**
guaranteed to work on exFAT; the safe response is a visible failure,
not a silent fallback to overwriting an existing PDF.

## Optional physical CI-only experiment

`app/scripts/Run-EphemeralExfatHardlinkAcceptance.ps1` runs **only**
on Windows GitHub-hosted GitHub Actions, with `-AllowEphemeralDisk`,
admin and verified `RUNNER_TEMP`. Without those conditions:
`PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN`.

1. Create a fresh, GUID-named **256 MiB VHDX** file **under**
   `RUNNER_TEMP`, either via `New-VHD` (if installed) or
   `diskpart CREATE VDISK` **only**. Diskpart is never asked to
   SELECT, CLEAN, FORMAT, or mount any disk.
2. Use `Mount-DiskImage -ImagePath` on the exact new file and
   `Get-DiskImage -ImagePath | Get-Disk`. Reject unless exactly one
   disk is returned, not present among original disk numbers, RAW,
   size in 200–300 MiB and neither system nor boot.
3. **Only then**, initialize and format that identified disposable
   disk, and verify exFAT, `EMBASIC_T260` label and partition-to-disk
   mapping. This is the sole destructive operation, limited to the
   new test VHD. No user disk, production runner partition or native
   UYAP file may be formatted.
4. In an isolated directory on that exFAT volume, the existing
   `Test-UnsupportedHardLinkVolume.ps1` demands an actual failed
   Windows hard-link call with native `ERROR_NOT_SUPPORTED(50)`.
   If unavailable or ambiguous, explicitly **NOT_RUN**. Do not
   interpret unrelated access-denied/policy/path failures as proof.
5. If verified, launch the original, **non-JDWP** ephemeral
   `EmbasicPublicMvp.exe` and use the real physical Windows Robot to
   import two rights-clean synthetic `format_id=1.0` UDFs.
   Physically save a valid ordered two-page approved PDF on ordinary
   NTFS, record its byte SHA. Then initiate a separate physical
   Ctrl+S to a *new* exFAT PDF filename and require:
   - actual titled `Embasic UDF hatası` modal in the foreground,
     **not** a success dialog or a timed guess
   - the new exFAT target is absent
   - original approved **NTFS sentinel** PDF SHA unchanged
   - both synthetic source UDF SHA values unchanged
   - no `.embasic-pending-*.pdf` or backup remnants on either volume
   - physical modal dismissal and retained original PDF SHA
6. Emit `PACKAGED-UNSUPPORTED-HARDLINK-EXFAT-PASS` only after every
   check; wrapper emits `PHYSICAL_UNSUPPORTED_HARDLINK_EXE=PASS`
   only after this physical test succeeds.
7. Always dismount **the exact new GUID image pathname** and delete
   only that image's own GUID working directory. Failed cleanup must
   fail CI rather than imply an approved, reusable test image.

`app/scripts/Assert-PackagedGuiInput.ps1` has an opt-in
`-UnsupportedLinkVolumeDirectory` argument restricted to hosted
Windows, a real exFAT `EMBASIC_T260` volume and exactly the
`embasic-tur260-only` subdirectory. Normal/standard and JDI
modes use their original test paths and remain separate.
The new mode has independent temporary UDF and NTFS-approved
output folders. No production Java class, security gate, UDF
parser, GUI behavior, PDF exporter or release code is changed.

## Interpreting CI outcomes

- A safe capability skip is **NOT_RUN** and can coexist with an
  ordinary successful Windows workflow. Read the exact log
  marker; workflow success alone is NOT proof of physical exFAT.
- Any failure after a real exFAT volume and native unsupported-hardlink
  preflight is a **FAIL**, not an acceptable silent skip. Debug in the
  same tour when safe.
- Do not claim a malicious existing-target atomic compare-and-swap:
  this experiment uses a **new** exFAT destination and separately
  protects an already approved NTFS PDF. Its SHA is a sentinel, not
  an approved PDF already present under the failing exFAT filename.
- The user has approved neither native UYAP golden PDF equivalence
  nor all `format_id` versions, source/license/NOTICE redistribution,
  Windows hostile existing-target CAS, or a public release.

`PHYSICAL_PACKAGED_RENDER_RACE=PASS` remains supported separately
by TUR 244's **isolated and unshipped** JDI-enabled app-image; this
exFAT test uses the **normal** package with **no JDWP**.
`SOURCE_GATE=BLOCKED` and publication `NO-GO` remain intact.

### Evidence checkpoint

The first three Public commits for this unique P0 are
`51dee287391c200eb548a6238898079c5e8b7351` (new VHD
harness), `2027662f3630fbc42bfcc37665c59b5b4f5f71d0` (real
physical GUI), and `d3a30665057d2d67bcf87ff2daea14b9f78bbdea`
(CI integration). Their outcomes require exact-SHA Windows log
verification; unobserved volume creation, GUI modal or test marker
must never be marked PASS.

The weighted development planning estimate remains **44.3%**,
not a UYAP compatibility, native visual fidelity or release
readiness percentage.

## Real Windows host capability outcome — 14 Nov 2026

The initial complete hosted Windows app-image acceptance on source
commit `d3a30665057d2d67bcf87ff2daea14b9f78bbdea` had a successful
normal physical GUI and test-only JDI acceptance, but its exFAT
capability result was **`PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN`**
due to `CimException` while preparing the disposable VHD.
It did **not** test a real exFAT save.

A second diagnostic commit
`98777fce88888e5b5d1f0e2c640a21aad451842a`
identified the phase without recording runner disk/device identifiers.
Its first Windows run `43617104968` failed the PowerShell parse
preflight; **the regression was fixed** with single-line PowerShell
diagnostics in
`f19e8cfcc1b5483a8304cf7a4fa0cf707c568831`.

**Verified CI** at `f19e8cfcc1b5483a8304cf7a4fa0cf707c568831`:
independent packaged Windows workflow `43617222833` SUCCESS;
ordinary physical GUI PASS; separate **test-only JDI**
`PACKAGED-JDWP-RENDER-RACE-PASS`; all scripts parse and Maven
passes. The specifically relevant real log is:

`PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN reason=hosted_vhd_environment_unavailable_stage_mount_disposable_vhd_exception_CimException`

The isolated VHD failed at the **official `Mount-DiskImage` step**
before the program reached `Get-DiskImage`, partition creation,
`Format-Volume`, the native hard-link probe or the physical EXE
save dialog on exFAT. Therefore **no host disk or VHD volume was
formatted** in this run. The temporary GUID VHDX file was
handled by the script's `finally` cleanup; disk identity and
cleanup are not replaced by runner filesystem guesses.

**Decision:** preserve `PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN`.
Do not treat workflow SUCCESS, normal NTFS hard-link alias rejection,
synthetic JUnit fault injection or a test-only JDWP render-race
as evidence for real unsupported-exFAT physical GUI acceptance.
Do not escalate privileges, format existing disks, or attempt
unreviewed alternate mounts to bypass the host's Storage error.

The safe runner-only VHD harness remains available for a future
verified environment that explicitly permits mounting ephemeral disk
images. All source/license/NOTICE and hostile Win32 CAS publication
gates remain NO-GO. Do not publish the debug app-image.
