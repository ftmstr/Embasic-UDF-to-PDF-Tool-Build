# Public app/ source, license and NOTICE release clearance — TUR 283 (2026-11-15)

**Decision: NO-GO.** This is a source-review worksheet, not a license grant, a release authorization, or a native UYAP compatibility certificate. It covers only the Public repository `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`.

## Verified source observations (starting HEAD `45872b8c2938bc9b8c57e8c7987bb29c899c67fa`)

- `app/pom.xml` identifies `com.embasic.publicapp:embasic-public-desktop:0.1.0-SNAPSHOT`, Java 17, direct `org.apache.pdfbox:pdfbox:3.0.8`, and Maven Shade 3.6.0 with classifier `all`. A Maven dependency declaration does not establish redistribution clearance.
- At this HEAD, GitHub Contents reads of `app/LICENSE`, `app/NOTICE`, repository-root `LICENSE`, and repository-root `NOTICE` returned **404**. These exact four paths are not present; this does **not** prove that no other legal files exist, nor that source ownership is invalid.
- `scripts/audit-public-shaded-runtime.py` inventories runtime components and upstream legal-text content digests, and intentionally reports `publication_allowed: false` and `independent_app_license_notice_clearance: NOT_VERIFIED`. It cannot issue legal approval.
- `scripts/verify-offline-golden.py` validates offline evidence records but does not itself prove visual equivalence or compatibility with native UYAP UDF documents.

## Separate mandatory sign-offs (none granted here)

| Gate | Required evidence before any distribution | Current state |
| --- | --- | --- |
| Legacy `src/main` provenance | File-by-file independent source ownership and redistribution authorization, exact reviewed commit | BLOCKED |
| Independent Public `app/` provenance | Authorship / contributor rights, copied assets and font rights, exact reviewed commit, human approval | NOT_VERIFIED |
| App licensing | Identified applicable license, copyright attribution, license text and redistribution terms approved by rights holder | NOT_VERIFIED |
| Shaded runtime | Actual built JAR and Maven/POM license inventory, NOTICE preservation review, transitive obligations approved | NOT_VERIFIED |
| Third-party artwork and fonts | Provenance, embedding/distribution permissions, attribution and NOTICE obligations | NOT_VERIFIED |
| Offline golden acceptance | Authorized clean test corpus, reference/output PDFs, page/table/font/geometry review and reproducible evidence | NOT_VERIFIED |
| Physical Windows acceptance | Real packaged GUI and export on Windows, actual output and error handling evidence | NOT_VERIFIED |
| Physical exFAT | Unsupported-hardlink filesystem experiment on actual media | NOT_RUN |
| Native UYAP compatibility | Authorized native acceptance and schema evidence; unknown elements fail closed | NOT_VERIFIED |

`PUBLIC_SOURCE_APPROVED_SHA` alone **never** authorizes publication. The legacy and independent app gates require **separate** evidence and approval. Source-free synthetic tests and CI success cannot substitute for either approval.

## Next high-value acceptance action

Produce a reviewed, file-level `app/` source/asset provenance inventory and a real shaded-JAR license/NOTICE discrepancy disposition. Record reviewer, date, source rights, exact commit, applicable license, any required notices, and explicit approved/blocked decision for every item. Do not infer approval from absence of a notice or from matching hashes. Then perform rights-clean offline golden PDF geometry/font/table acceptance and physical Windows beta testing.

No `main` merge, tag, release, installer, ZIP, binary redistribution, or progress-percentage increase is authorized by this document.
