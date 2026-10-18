# Public build security gates

**Current status: BOOTSTRAP / APPLICATION SOURCE WITHHELD.**

## Source of truth and permitted flow

The historical private Java/Swing/Maven repository remains untouched and is authoritative. Only an independently inspected allowlist of approved current source files may be **newly added** to this public repository. Do not transfer commit history, branches, tags, binary artifacts, authentic UDF/PDF documents, private logs, or private Drive reports.

The exact release-candidate snapshot must be checked **outside** this public repository against a confidential owner identity dictionary and review manifest. Keep those inputs and forensic results in access-restricted storage. A CI scanner can reject obvious mistakes; it cannot prove legal ownership, lack of personal data in images, or source provenance.

## Required human sign-offs (not completed)

- Original code copyright/publication authorization and a corresponding root LICENSE text. Do not silently choose an irrevocable open-source grant.
- iText 2.1.7 license route (MPL 1.1 or its stated LGPL alternative) and actual shaded-JAR source/notice obligations.
- Historic Bouncy Castle 138 binary license/attribution checks and other transitive dependency inventory.
- PNG/ICO provenance, image-pixel and metadata audit, authorization to publicly redistribute graphics.
- Actual complete private-tree scan, private-identity dictionary review, binary scan and SHA-256 source manifest.
- A new clean Git history, inspected public tree, and a matching approved commit SHA set in the repository variable `PUBLIC_SOURCE_APPROVED_SHA`.
- For binary uploads, separate artifact rights review and exact commit SHA in `PUBLIC_BINARY_APPROVED_SHA`.

The `PUBLIC_SOURCE_APPROVED_SHA` variable is an **administrative release gate**, not a substitute for the preceding authorizations. For pull requests use the reviewed PR head commit. Never store confidential approvals in public GitHub variables or workflow logs.

## CI execution

`public-ci-gate.py` reports `ready=false` while the repository only holds bootstrap files. A Windows-hosted Java probe can still run. If application files appear without all essential files and the exact commit approval, validation fails closed. No application binary is uploaded from a pull request.

Once the source is approved, CI should run independent Java regression suites, synthetic-only UDF/PDF smoke checks and Maven clean verify. Publishing an artifact additionally requires `PUBLIC_BINARY_APPROVED_SHA` to match the exact committed sources. There is no GitHub Release publication or auto-deploy job.

## Verification boundaries

A successful runner probe is **not** proof of UDF parsing, Explorer interactions, real UYAP version fidelity, Windows app-image startup, installer behavior or legal compliance. These require separate Windows and authentic-reference (strictly private) acceptance work. CI must never echo original private runner details, source document contents or full forensic findings.
