# TUR366 — End-to-end exact-JAR offline golden bridge test (synthetic only)

2026-11-18 Europe/Istanbul. Public ftmstr/Embasic-UDF-to-PDF-Tool-Build branch ci/secure-windows-bootstrap-20261018. Base exact SHA 5ae42ffe41946858184d13b7970d0c72589de67c (TUR365). No next scheduled automation tour was present at check time. Prior Drive TUR365 and actual CI verified.

## Concrete proof gap
TUR362–365 created and secured an offline custodian-to-PDFBox reference comparison pipeline but tested only a Python stdout parser and bounded process primitives in both OS CI. A successful JUnit in OfficialPdfReferenceAudit did not verify that a real shaded application JAR + rights-declaration manifest + physically separate reference and candidate PDFs can be orchestrated by the Python gate end-to-end. That omission could hide integration defects until actual permitted offline golden acceptance.

## Targeted new acceptance
app/scripts/test-offline-golden-bridge-integration.py assembles a rights-clean synthetic format_id=1.0 UDF in a temporary CI directory; invokes the exact freshly shaded Java JAR to emit one original synthetic PDF plus a separate changed-content PDF; copies the original bytes into a separate physical PDF as a matching control; writes an original-synthetic explicit owner-reviewed *CI-only* manifest with SHA-256 custody. It runs actual app/scripts/Run-OfflineGoldenPdfMetrics.py and compiled com.embasic.publicapp.OfficialPdfReferenceAudit via the real shaded JAR to check: MATCH returns 0 but METRICS_MATCH_REVIEW_REQUIRED and NO_GO; distinct output returns 2 and DIFFERENCE_REVIEW_REQUIRED; independently mismatched JAR digest returns BLOCKED without a comparison. It checks real per-page raster/text aggregates and absence of a false release/physical-beta/native-acceptance verdict. No actual UYAP, private file, or document text is uploaded, persisted or printed. Temporary directory is destroyed.

CI adds this after existing shaded JAR build and standard synthetic CLI path, on both Windows and Linux. No general UDF smoke re-run was added; this checks a previously unexercised operator evidence chain.
PowerShell dashboard updates only OfflineGoldenVisualMetrics to SYNTHETIC_BRIDGE_INTEGRATION_ONLY_NATIVE_GOLDEN_NOT_RUN, with CI NO-GO assertion. Existing weighted engineering percentage unchanged.

## Safety gates
CI synthetic custody evidence cannot establish permission for real UYAP case data. External independent owner rights, Public app/LICENSE/NOTICE, legacy source gate, actual native UYAP golden, physical end-user Windows beta, hostile CAS and real exFAT remain UNVERIFIED/BLOCKED/NO-GO. No Private repo, main merge, tag, release, installer, ZIP, binary or case data publication.

Record exact commit, Windows/Linux Python and JUnit results, packaged physical Robot/JDI, exFAT exact NOT_RUN and Google Drive report readback when observed.
