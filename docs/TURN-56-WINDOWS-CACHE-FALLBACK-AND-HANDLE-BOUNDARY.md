# Tur 56 — Windows PDFBox cache fallback and handle-security boundary

Date: 2026-11-10. Public-only development; no native UYAP document, Windows release, or license clearance implied.

## Scope and evidence discipline

- A persistent cache below Windows LOCALAPPDATA is an optional performance path. The Java implementation rejects noncanonical, redirected, differently owned, or ACL-untrusted profile locations. A rejected profile must not set the pdfbox.fontcache JVM property.
- The intended fallback is PDFBox's existing default selection, not a newly trusted or verified application cache. This fallback is not automatically hardened by the application's own ACL filter. Never present fallback as a security boundary for hostile per-user caches.
- PDFBox metadata integrity checks including pdfbox.fontcache.skipchecksums must not be bypassed. Content PDF export remains independent of an optional cache selection.
- PASS requires actual per-SHA Windows/Linux GitHub Actions logs, not just a successful source edit. Physical Windows GUI Robot acceptance remains NOT_RUN unless run explicitly in an interactive Windows desktop.

## New fixture-based regression coverage

1. PdfBoxFontCacheFallbackTest intentionally supplies a rights-clean disposable home and an invalid outside-local path. The test checks that the JVM does not receive an unsafe custom cache value and does not mutate checksum configuration. It then saves a two-page synthetic UDF-derived PDF and checks embedded TrueType font, RGB PNG sample, colored-styled text, vector table text, page count, and leftover staging files.
2. PdfBoxFontCacheFallbackTest preserves an explicit operator-managed JVM override even if profile input is invalid; an override is not security-approved by this test.
3. PdfBoxFontCacheFallbackTest reads Windows hosted runner's existing real home and LOCALAPPDATA ownership/ACL metadata. It calls the configuration entry point only if an unsafe profile precondition is already proved; it asserts no new application cache directory and unchanged JVM cache property. It never mutates the real profile or uses actual user documents.
4. FontCacheHandleFeasibilityTest uses only disposable directories to inspect Java 17 SecureDirectoryStream support. Where supported, a rename and same-name replacement verify that reading relative to the originally open directory still accesses original synthetic content. Where unsupported, log UNAVAILABLE and make no handle-security claim; a Java proof does not integrate PDFBox.

## What path checks cannot guarantee

The existing implementation returns a Path after checking real paths, owner and ACL. Between that return and PDFBox's subsequent path-based file open, another actor with directory mutation rights may swap a junction, hard link, ACL, or reparse point. A second prepare() will detect some changes but is not an atomic open/write guarantee. Merely checking a file ID before and after a path-based open is also insufficient to make those separate operations atomic.

Potential Windows design requiring a separate, reviewed implementation:

1. Use CreateFileW with FILE_FLAG_BACKUP_SEMANTICS for directory handles and FILE_FLAG_OPEN_REPARSE_POINT where inspecting the reparse object rather than following it is required.
2. Inspect reparse tags with FSCTL_GET_REPARSE_POINT; inspect stable identity for an open handle with GetFileInformationByHandleEx(FileIdInfo), volume serial and 128-bit file ID.
3. Design handle-relative opening and a file write path that stays tied to validated directory handle, with explicit share/access masks, ACL/owner checks, interruption and cleanup tests. A handle ID check alone does not prevent ACL mutation or make PDFBox's own path-based metadata writer handle-relative.
4. Before any native bridge, prove build reproducibility, compatible licensing and Windows-specific adversarial tests (junction/ACL mutation across processes, handle close/reopen, interruption, hardlink and reparse variants). Until PDFBox's actual read/write path is integrated, classify atomic TOCTOU protection as NO-GO / NOT_PROVEN.

Authoritative API references:
- [Java SE 17 SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): requires OS/provider support; directory-relative operations can prevent some rename races.
- [Microsoft CreateFile flags](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilew): directory and reparse open behavior.
- [Microsoft reparse point operations](https://learn.microsoft.com/en-us/windows/win32/fileio/reparse-point-operations): tag and FSCTL_GET_REPARSE_POINT.
- [Microsoft FILE_ID_INFO](https://learn.microsoft.com/en-us/windows/win32/api/winbase/ns-winbase-file_id_info): volume serial and 128-bit identity of an open file handle.

## Release and acceptance gates

- Native UYAP visual parity across UDF versions: NO-GO, rights-clean authoritative golden comparisons unavailable.
- PDFBox upstream class SHA consistency does not constitute redistribution permission: license and NOTICE approvals remain NO-GO.
- Windows secure-source bootstrap may remain BLOCKED due to empty PUBLIC_SOURCE_APPROVED_SHA. Do not bypass or publish any installer/release/ZIP/tag.
- Interactive physical Robot Windows UI test: NOT_RUN; CI launcher smoke and headless Swing tests are not substitutes.
- Per-run durations from the 160-file synthetic format_id=1.0 batch are measurements, not production performance guarantees.
