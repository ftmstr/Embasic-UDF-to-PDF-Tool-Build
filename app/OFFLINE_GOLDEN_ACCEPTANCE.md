# Offline native UDF/PDF golden acceptance contract — TUR348 (2026-11-18)

**Status: NOT_RUN / NO-GO.** This document is an acceptance specification, not a test result, source-rights approval, UYAP certification, or authorization to distribute. It adds no synthetic smoke tests and changes no production converter behavior.

## Input custody and rights gate

1. Accept a candidate only from an authorized rights holder, with written permission for offline conversion, comparison, and storage in the specifically approved test environment. Record owner, permission scope, permitted retention, expiry and reviewer. Public repository, GitHub Actions artifacts and logs must never contain native UYAP documents, sensitive case data, personal identifiers or reference outputs.
2. Maintain an **offline, access-controlled** evidence register with fixture identifier, source SHA-256, source byte length, schema/version claim (if supported), expected output PDF SHA-256, authoritative reference tool/version, acquisition timestamp, custodian and reviewer. Do not publish the register or its document hashes if that would expose protected metadata.
3. A native document is **NOT_ELIGIBLE** until permissions and handling restrictions are verified. A synthetic file cannot be substituted for native acceptance or described as UYAP-compatible.

## Executable acceptance sequence (physical, authorized offline workstation)

1. Record the exact **Public branch commit SHA**, application JAR/app-image SHA-256, Java runtime, OS build, display scaling and PDF renderer version. Keep legacy `src/main` and independent `app/` source/license gates separate.
2. On a clean Windows workstation without network access, validate the source UDF against the supported documented schema subset. Unknown tags, relationships, embedded resources, external references or ambiguous decoding must **fail closed**; log only sanitized error class and fixture ID.
3. Convert each eligible fixture once with the exact recorded build. Capture output SHA-256, page count, page dimensions (MediaBox/CropBox), rotation, page ordering, embedded font names and glyph coverage, selectable text extraction and image/resource inventory. Preserve original files read-only.
4. Independently compare with a rights-authorized reference PDF using a fixed PDF renderer and deterministic page raster settings. Review every page, including table borders, cell coordinates, line wrapping, Turkish characters, page breaks, image positions, alpha, rotation and footers. Record measured coordinate tolerances **before** acceptance; no default unapproved tolerance is implied.
5. Classify each fixture **PASS**, **FAIL**, **BLOCKED_RIGHTS**, **UNSUPPORTED_SCHEMA**, or **NOT_RUN**. PASS requires *all* mandatory geometry, text, font, image and order checks, independent reviewer sign-off, and absence of unexplained deviations. A single material mismatch is FAIL; missing reference or rights is BLOCKED, not PASS.
6. Verify physical Windows user flow: import, multi/keyboard selection, thumbnail scale, grid add, drag reorder, save/cancel/overwrite, reopen and visual output. Record each feature independently; CI or synthetic robot evidence cannot substitute for physical acceptance.
7. Keep physical exFAT **NOT_RUN** until tested on a real exFAT volume. Hostile CAS remains **NO-GO** unless independently resolved.

## Evidence record fields

`fixture_id | rights_review_id | custody_reference | public_source_sha | binary_sha256 | source_sha256 | reference_pdf_sha256 | actual_pdf_sha256 | schema_scope | page_count_match | page_box_match | page_order_match | text_and_glyph_match | font_match | table_geometry_match | images_match | visual_review | physical_windows_review | reviewer | timestamp | verdict | deviations`

Store only anonymized verdicts and approved summaries outside the offline evidence environment. Never include fixture content or identifying metadata in GitHub/Drive reports.

## Release decision

This contract does **not** relax the legacy source gate, independent Public `app/` ownership/license/NOTICE gate, nine upstream attribution provenance checks, or the exact SHA controls. `PUBLIC_SOURCE_APPROVED_SHA` alone grants nothing. Native UYAP remains **NOT_VERIFIED**, golden acceptance **NOT_RUN**, physical Windows beta **NOT_RUN**, physical exFAT **NOT_RUN**, hostile CAS **NO-GO**, and release **NO-GO**. No main merge, tag, release, installer, ZIP or distribution.
