# TUR362 — Local rights-gated native golden PDF visual metrics bridge

18 November 2026, Europe/Istanbul. Only Public ftmstr/Embasic-UDF-to-PDF-Tool-Build, branch ci/secure-windows-bootstrap-20261018. Baseline 26712990f4cf33d7e45db684d734c9172c3ff909. TUR338–361 already present; TUR359–361 Drive report verified. No sample UYAP documents, private repository, confidential content, installer, ZIP or released application used.

## Gap closed
The existing app/scripts/Verify-OfflineGoldenCustody.py validates rights claims, manifest integrity and exact source/reference hashes, while app/src/main/java/com/embasic/publicapp/OfficialPdfReferenceAudit.java compares two PDFs using PDFBox geometry, glyph positions and bounded 96-DPI raster differences. They were not linked in one operator-ready offline, fail-closed workflow. In particular a verified custody report could not itself support a claim that generated output resembles the authorized reference.

New app/scripts/Run-OfflineGoldenPdfMetrics.py accepts explicitly local --manifest, --udf, --reference-pdf, --candidate-pdf, --app-jar and independently supplied --expected-app-jar-sha256, --report path. It uses the existing custody gate and a bounded SHA-256 of the exact shaded JAR and candidate output, refuses aliasing/hardlinks and symlinked sources, invokes the existing PDFBox comparator in a headless capped JVM with a 240-second timeout and consumes only strictly parsed aggregate page metrics (no confidential text, images or filenames). It rehashes sources and build after comparison. It writes a single exclusive, local owner-only JSON (0600 on POSIX) containing custody hashes and page metrics. The report status is always METRICS_MATCH_REVIEW_REQUIRED, DIFFERENCE_REVIEW_REQUIRED or BLOCKED, and explicitly keeps native UYAP NOT_VERIFIED, human review NOT_VERIFIED, external Windows beta NOT_RUN and release NO_GO. No published/native fixture and no equivalence claim.

Companion parser-only regression validates fail-closed interpretation of Java summaries without adding another synthetic UDF converter smoke. Existing Java OfficialPdfReferenceAudit JUnit coverage remains unchanged; Windows/Linux CI runs the four parsing assertions.

## Remaining human gates
Rights declaration alone is not proof of ownership; an operator must provide independently authorized original UDF and PDF, build digest and offline physical Windows review. Independent app source rights/LICENSE/NOTICE review remains NO-GO; legacy source gate BLOCKED; physical exFAT NOT_RUN; hostile CAS NO-GO. This bridge measures only the existing PDFBox comparator criteria and does not independently audit all tables, Unicode semantics, font legal terms, annotations or inaccessible UYAP versions. Local output must stay offline, never upload to GitHub/Drive. No binary release, main merge, installer or ZIP.

Final exact-SHA CI and Drive evidence appended when completed.
