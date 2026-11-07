# Tur 4 — Private snapshot handoff and gated Windows release preparation

**Date:** 2026-10-18 · **Public Build PR #1 remains Draft**.

## Improvements implemented in the public, source-free bootstrap

1. The public source CI gate now has an explicit, reviewable `BOOTSTRAP_ALLOWED` inventory. Unrecognized documents, source fragments, or unapproved graphics in a public bootstrap fail the CI check rather than being silently accepted as a harmless empty repository. This is **detection**, not retroactive protection: an accidental public push is already publicly visible, so human review is mandatory *before* pushing.
2. `scripts/verify-snapshot-manifest.py` independently checks an **offline, already-prepared clean snapshot** against a SHA-256 manifest stored outside the snapshot. It refuses unlisted, missing, changed, unsafe, symlinked, or generated files. It detects forbidden UDF/PDF content and Git history. No copying, uploading, permission changes or approvals are performed.
3. Its synthetic regression suite tests normal byte matches and fail-closed tampering/path/archive/history cases. The public CI runs these tests **without** access to any original private files.
4. `scripts/audit-shaded-classes.py --mode application` is available for a future **explicitly approved** application snapshot. Unlike the isolated mode, it requires a main `com/embasic/udf2pdf/App.class` and forbids the synthetic audit probe. Every upstream .class still needs matching SHA-256 bytes; no unexpected third-party Java classes are allowed. Application-source-owned classes under the project package are permitted. All new acceptance/rejection cases are synthetic.
5. The separately gated Windows artifact pipeline uses effective six-coordinate Maven POM/JAR checks, full shaded .class verification, and two reviewed ZIP notice byte comparisons. This replaces the earlier four-filename verifier that was inconsistent with the actual Maven relocation to `org.bouncycastle:*:1.38`. The job is skipped while source and binary SHA approval gates are not met.

## Private operator handoff (not executed)

On a trusted machine with a complete Private checkout:

- Run the **existing private** `scripts/public-build-preflight.py` with the original confidential identity dictionary and separate hash-bound owner approval manifest, all held outside the project repository. Do not paste or upload its forensic report to Public GitHub.
- Resolve every project copyright, iText MPL 1.1/LGPL alternative license compliance, Bouncy Castle historical notice, and PNG/ICO provenance/redistribution question **before** authorizing public upload. A chosen license or file checksum is not itself proof of legal permission.
- If the source-owner authorizations are formally documented and private preflight passes, prepare only its allowlisted, no-history snapshot into an empty directory outside Private checkout.
- Independently hash every file from the prepared snapshot into an **external, access-restricted** JSON manifest, with shape:

```json
{
  "schema_version": 1,
  "files": [
    {"path": "src/main/java/example/Synthetic.java", "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
  ]
}
```

The JSON example uses a fictitious file and fictitious checksum. It is not an approved or valid manifest for this project. A human must verify that the manifest matches the *complete* intended reviewed export list.

- Run the separate verifier against the **prepared snapshot**, never by copying private data into a GitHub-hosted runner:

```sh
python scripts/verify-snapshot-manifest.py --snapshot-root /path/to/prepared-snapshot --manifest /private/review/source-manifest.json
```

A successful result is **only an immutable byte-integrity observation**, not a legal distribution approval. Inspect the complete source tree and actual assets before creating any clean-history Git commit.

## Outstanding approval gates

| Gate | Current evidence |
| --- | --- |
| Public CI and isolated Maven dependency/Java 17 Shade test | Passed on preceding, linked GitHub runs |
| Full synthetic snapshot manifest suite | Check associated final PR commit CI run |
| Real Private full-tree scan using confidential identity dictionary | **NOT RUN in this Public workspace** |
| Source owner-authenticated licensing choice and explicit public disclosure authorization | **BLOCKED** |
| iText shaded binary source/notice/relinkability legal route | **BLOCKED** |
| Historic Bouncy Castle readable notice/attribution inside real Windows ZIP | **BLOCKED** |
| PNG/ICO author, derivation, identity and public redistribution permission | **BLOCKED** |
| Actual application Java/Maven build, Windows UI, UDF conversion, installer | **NOT RUN** |

## Security boundary

The actual application and artwork are not present. Neither a passing class SHA comparison nor a private-checkout SHA manifest proves copyright ownership or a user's authority to publish. Do not automatically set `PUBLIC_SOURCE_APPROVED_SHA` or `PUBLIC_BINARY_APPROVED_SHA`; both must remain unset until the required rights reviews and an exact commit approval are documented in private. Do not merge the Draft PR or publish a release as a consequence of these synthetic PASS results.
