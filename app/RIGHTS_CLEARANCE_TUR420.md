# TUR420 — Independent Public app/ rights clearance worksheet (2026-11-20)

**NOT_APPROVED / RELEASE NO-GO.** Scope: Public repository only, branch `ci/secure-windows-bootstrap-20261018`; baseline `fa7eb5177e38cd2b19213ef932f22487112b36cf`. This is an actionable reviewer worksheet, not a license, copyright determination, build artifact, or source approval. Never access or copy from the Private repository.

## Review protocol (per exact source blob)

For each of the 15 Java sources listed in `SOURCE_IDENTITY_TUR414.md`, independently obtain and retain **outside this Public repository**: (1) named copyright holder/author and dated origin evidence; (2) exact file/blob identity and change-history reconciliation; (3) affirmative rights to modify, build, distribute source and binaries; (4) exact applicable license text and required NOTICE/attribution; (5) third-party or generated-code origin and restrictions; (6) written reviewer approval and expiry/conditions. Missing, conflicting, or unverifiable evidence is **BLOCKED**, not assumed permissible. Repository visibility, Git authorship, a SHA or `PUBLIC_SOURCE_APPROVED_SHA` are insufficient.

## Reviewer decision ledger (no personal data or confidential proof in Git)

| Review unit | Current status | Required closure |
|---|---|---|
| 15 Java source blobs (see TUR414 manifest) | NOT_VERIFIED | Individual origin, redistribution grant, license and NOTICE reconciliation |
| Maven dependencies and transitive jars | NOT_VERIFIED | Exact resolved coordinates, checksums, licenses, NOTICE and binary redistribution rights |
| Fonts, icons, images, embedded resources | NOT_VERIFIED | File-by-file provenance and embedding/distribution permissions |
| Build scripts, workflows, generated assets | NOT_VERIFIED | Complete release-tree review at proposed exact SHA |
| Legacy `src/main` | BLOCKED | Independent existing legacy gate; never inherited from app approval |
| Independent Public `app/` | BLOCKED | Explicit separate source/license/NOTICE and packaged-byte approval |

## Closure and revalidation

Keep signed evidence in an access-controlled rights register; publish only a non-sensitive review identifier and categorical decision. Re-enumerate the full release tree at the **proposed exact SHA**; any source, dependency, resource, script or binary drift invalidates prior approvals until reviewed. Two independently scoped decisions are mandatory: legacy `src/main` and Public `app/`. Even complete rights approval does not substitute for offline authorized native UDF/PDF golden, physical Windows acceptance, hostile CAS or physical exFAT checks. Unknown UDF elements must fail closed.

**Current gates:** native UYAP NOT_VERIFIED; authorized native offline golden NOT_RUN; physical Windows beta NOT_RUN; physical external exFAT NOT_RUN; hostile CAS NO-GO; release NO-GO. No merge/tag/release/installer/ZIP/distribution. No synthetic smoke test or progress percentage change.
