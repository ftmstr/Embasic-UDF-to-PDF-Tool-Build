# TUR367 — Offline native golden preflight: bind candidate PDF to the verified renderer

2026-11-18 Europe/Istanbul. Only Public ftmstr/Embasic-UDF-to-PDF-Tool-Build, ci/secure-windows-bootstrap-20261018; starting TUR366 exact SHA b5e159519d4344380fa68ba052fc9d12c9f6bdd0. No later committed tour at preflight; hourly automation remains enabled.

## Material acceptance gap
TUR362–366 verified an offline rights manifest, immutable source hashes, the shaded application JAR digest and PDFBox numeric comparison. However its --candidate-pdf was entirely operator-supplied; a copied reference document could produce MATCH without any evidence that this candidate came from the inspected renderer. The TUR366 synthetic positive fixture deliberately copied reference bytes, making that provenance limitation visible. Report status remained human-review-only, so no prior release grant is revoked, but treating that match as a renderer conversion proof would be incorrect.

## New optional local render mode
- Existing --candidate-pdf remains supported and its report is explicitly labeled EXTERNAL_PDF_RENDER_PROVENANCE_UNVERIFIED.
- New mutually exclusive --generate-candidate creates a private temporary directory inside the pre-existing operator-chosen offline report directory, AFTER checking rights, custody, original UDF and reference PDF bytes and independently supplied exact JAR SHA.
- Invoke that exact verified JAR once via Java headless --merge to create the candidate PDF, with 150-second timeout, 512MiB heap, discarded stdin/stdout/stderr and no shell. Only the generated candidate is given to the existing bounded PDFBox reference comparator.
- Rehash source, manifest, JAR, reference and candidate after comparison. The generated PDF is removed on successful comparison OR exception; report includes only aggregate metrics and SHA-256, marks candidate_provenance=LOCAL_TEMP_RENDER_EXACT_JAR_SHA_REVIEW_REQUIRED and generated_candidate_retained=false. This does NOT produce an inspectable native golden PDF for a human reviewer; external candidate mode remains for manual workflows.
- TUR366's real-JAR Windows/Linux CI integration now asserts the new generated-candidate MATCH, changed-source DIFFERENCE and wrong-JAR-hash BLOCKED results plus temporary directory cleanup; existing external-candidate checks remain.
- Dashboard switches to EXACT_SHA_LOCAL_RENDER_PROVENANCE_SYNTHETIC_ONLY_NATIVE_GOLDEN_NOT_RUN without modifying weighted progress or source/release gates.

## Limits and NO-GO
The generated PDF is created by a Java process identified by a previously independently supplied application JAR SHA, not by a signed full runtime chain; races and JDK injection/environment are outside this proof. A rights declaration is not a legal grant. The reference used by CI is synthetic, not UYAP native. Actual authorized native UDF golden NOT_VERIFIED; external physical beta NOT_RUN, hostile existing-target CAS NO-GO, exFAT physical NOT_RUN; independent Public app owner/source/LICENSE/NOTICE approval NOT_VERIFIED; legacy Secure Bootstrap SOURCE_GATE BLOCKED. Never Private repo, main merge, tag, release, installer, ZIP or actual UYAP data upload. Final exact SHA CI/logs and Drive readback pending real execution.
