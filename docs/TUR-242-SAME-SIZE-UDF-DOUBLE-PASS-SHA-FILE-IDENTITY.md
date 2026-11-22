# TUR 242 — Same-size UDF mutation, two-pass SHA and file identity

**13 November 2026 (Türkiye)**. Public-only repository:
`ftmstr/Embasic-UDF-to-PDF-Tool-Build`, existing branch
`ci/secure-windows-bootstrap-20261018`.

## Deduplication and exact baseline

Tur 241 exact final HEAD `1bd13523f9466283abce722166ccfa09809baf10`
was verified from the Public branch, its canonical Drive report
`1MipbTeaPEDwjnU6ogqXoBUL55eZ4A2Ewux06ECUZ49M` read back, and
GitHub compare returned ahead=0/behind=0. The hourly automation's
22:00 TRT run was completed; it did not create a TUR 242, and the next
run was set to 23:00 TRT. There was no TUR 242+ report in the folder.
No earlier development was duplicated.

Tur 241 Windows/Linux Maven JUnit 205 = 204 PASS and ordinary GUI Robot
1 SKIP on each platform; separate physical Windows GUI Robot PASS,
independent jpackage app-image PASS, PDF Engine and upstream SHA
SUCCESS. Secure Bootstrap intentionally failed with
`SOURCE_GATE: BLOCKED (unapproved_bootstrap_file)`. A physical
packaged EXE render race remained NOT_RUN.

## Security issue

Before this change, `FileImportPolicy.sourceSha256(Path)` compared the
archive's start size, bytes read and final size. An adversary could
write a DIFFERENT but SAME-LENGTH ZIP/UDF into the same pathname while
the hashing input stream was open and restore the original mtime.
A single SHA pass could then fingerprint a hybrid read, or a replaced
file could look identical in size/mtime.

This tour strengthens *detection*, not cross-platform atomic snapshot
guarantees:

1. Preserve the existing GUI/CLI `sourceSha256(Path)` signature.
2. Read `BasicFileAttributes` before hashing: expected byte size,
   `lastModifiedTime` and filesystem `fileKey` where available.
3. Independently stream SHA-256 **twice** through separate actual
   filesystem input streams. Each pass enforces max 64 MiB input, positive
   byte count, exact initial size, final observed size, same mtime and
   same `fileKey` when the provider supplies one.
4. Reject if the two SHA-256 digests differ. All changed-file cases
   are fail-closed `IllegalArgumentException` with the established
   Turkish user-facing message.
5. Keep the previous first-positive-64KiB test-only callback and add
   a package-private between-pass callback. The production entrypoint
   supplies no-ops. No UI debug listener, environment variable,
   unsupported UYAP CLI or externally networked diagnostics.

The two independent passes can double source hashing I/O and CPU.
Benchmark the existing 160 rights-clean synthetic UDF workflow; do not
claim end-user performance parity with official UYAP files.

## New original, rights-clean JUnit tests

`FileImportPolicyTest.sameSizeSourceRewriteWhileHashStreamOpenIsRejected`
creates two generated `format_id=1.0` ZIP/UDFs with 300,000 synthetic
ASCII chars each, no compression, same ZIP length and different content.
After the **first real 64 KiB digest chunk while the stream is still
open**, it rewrites the first path with equal-length bytes without
changing its file identity, restores its mtime, and insists that the
hash is rejected despite equal final size/mtime. The stable version
after mutation remains hashable but has a different SHA.

`FileImportPolicyTest.sameBytesNewFileIdentityBetweenHashPassesIsRejected`
makes a byte-identical separate filesystem copy. After pass one, it
renames away the original and puts the other inode/file object at the
same path, restores the same modification time and size, and checks provider fileKey behavior. The two actual SHA passes would
be equal. **If the provider exposes a non-null original fileKey**,
the new object must be rejected, even with equal bytes, size and mtime.
If the provider exposes a null fileKey, this test records
`fileKey=UNAVAILABLE detection=NOT_VERIFIED` rather than falsely
claiming identity protection. A subsequent stable hash of the
replacement equals the original content hash on either platform.
Do not infer Windows NTFS object identity from Java's optional
`BasicFileAttributes.fileKey()`.

Markers must appear in real final SHA **both** Windows and Linux logs:
`EMBASIC_TUR242_SAME_SIZE_INFLIGHT` and
`EMBASIC_TUR242_FILE_IDENTITY_SWAP`.

No actual user document, copyrighted third-party UDF, private data
or official Editor binary is used. Real-world same-length ABA
replacements that occur and revert between observations, malicious
providers, file mutations after the final SHA check and hostile
Windows check-to-replace races still cannot be ruled out. This is
not a true snapshot, file lock, cryptographic attestation of an
immutable file handle, or an official UYAP PDF reference comparison.


## Initial Windows CI failure and same-tour repair

The first full test SHA `cb59014f2236fde6ca85ef0812356b76ef59b399`
FAILED the Windows Maven suite: JUnit
`sameBytesNewFileIdentityBetweenHashPassesIsRejected` wrongly assumed
Windows always exposes a non-null `BasicFileAttributes.fileKey()`.
The actual Windows GitHub Actions job `132117543500` reported
`expected: not <null>`. Linux job `132117544258` passed.
This is a **test assumption error**, not evidence that Windows
detects swapped file identity.

Fixed in the same tour:

- `FileImportPolicy` now rejects fileKey *availability transitions*
  too (null <-> non-null), while stable null remains an explicitly
  unsupported identity proof.
- `FileImportPolicyTest` makes provider evidence conditional:
  non-null fileKey -> deterministic identity swap must be rejected;
  null fileKey -> assert the byte-identical stable hash, label
  identity as NOT_VERIFIED, and never call it PASS.
- The original same-length/mtime-restored in-flight content mutation
  regression is unchanged and was already PASS on both platforms.

Require final Windows/Linux CI at the **new exact commit SHA** before
closing this tour. Normal JUnit PASS here means no false security
claim; the Windows file-identity guarantee remains **NO-GO**.

## Acceptance, remaining NO-GO and report contract

Verify **exact-final Public HEAD** Windows/Linux Maven JUnit, two
new test markers, opt-in Windows GUI Robot distinct from the ordinary
suite's SKIP, 160 synthetic UDF 512 MiB heap profile, ephemeral
packaged Windows EXE physical Ctrl+S/order/overwrite protection,
independent PDFBox 3.0.8 and OpenPDF 2.0.5 probes, upstream SHA and
Secure Bootstrap gate. No failed or pending CI may be shown as PASS.

**PHYSICAL_PACKAGED_RENDER_RACE=NOT_RUN** unless a real test-only
packaged EXE synchronization/mutation event and output SHA/staging
are evidenced. Hard-link-unsupported volume physical acceptance
remains NOT_RUN. Native UYAP PDF parity/all format versions
NOT_VERIFIED. `WINDOWS_ATOMIC_TOCTOU=NO_GO` for hostile approved
target replacement, source/license/NOTICE distribution approval
BLOCKED. No Private access, main merge, tag, release, ZIP or installer.
Weighted engineering progress stays **44.3%**; not a measure of
native UYAP compatibility or product launch readiness.
