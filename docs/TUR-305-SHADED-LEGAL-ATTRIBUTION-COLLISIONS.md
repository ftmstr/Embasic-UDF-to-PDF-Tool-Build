# TUR305 — Four shaded runtime JAR LICENSE/NOTICE shared-namespace fail-closed attribution

Date: 2026-11-16, Europe/Istanbul. Authorized Public branch ci/secure-windows-bootstrap-20261018, initial HEAD 78b553d656d17ebe990167156c015f164e70526a (TUR304). TUR276–304 are already present; do not duplicate or alter their work.

## Actual baseline evidence
At TUR304 exact SHA, both Windows/Linux Public MVP workflows succeeded and printed `SHADED_LEGAL_RECONCILIATION=REVIEW_REQUIRED FINDINGS=6 STATUS_COUNTS={'DIFFERENT': 6}`: upstream FontBox and PDFBox-IO legal files do not match the same-named legal files in the shaded runtime. This is **not** legal clearance and no conclusion of actual infringement. The runtime SHA audit separately checks four JAR/POM sets: PDFBox, PDFBox IO, FontBox 3.0.8 and Commons Logging 1.4.0.

## New P0 scope
The runtime source inspector rejects an empty or missing LICENSE or NOTICE family in each of the four upstream JARs and in the final shaded JAR, without claiming that the existence of a file proves sufficient attribution. It hashes each upstream legal member; on identical META-INF paths shared across different upstream components, a new provenance collision table records every coordinate, all upstream digests, unique digest count, shaded path existence and whether the single shaded copy actually matches all source bytes. Identical source bytes still require attribution review; distinct bytes are a collision even if one matches the shaded file. Both conditions feed a fail-closed independent legal-review queue. No license text or original UYAP data is exported.

Four added rights-clean, synthetic archive tests cover distinct and same-byte shared names, missing upstream NOTICE, missing final shaded NOTICE, and empty source LICENSE; existing tests continue. Windows and Linux CI execute the actual Maven/JUnit+shade verification and new tests. The PowerShell dashboard and CI assert the separate shared-namespace NO-GO status. Full independent review of license/NOTICE text, holder, source and font/branding rights still missing.

## Safety and publication
No Private repository, main merge, tag, release, installer, ZIP, binary publishing or real UYAP data. `PUBLIC_SOURCE_APPROVED_SHA` is not independent source rights authorization. Native UYAP golden/all-version fidelity NOT_VERIFIED; exFAT physical NOT_RUN (hosted VHD mount issue); hostile existing target CAS NO-GO; external Windows user beta NOT_RUN. Source, LICENSE, NOTICE and distribution remain NO-GO. The manually weighted engineering score remains 44.3%, not shipping readiness.

Final exact commit and GitHub CI evidence shall be checked after the Public change and reported to the Drive /Uygulama/Embasic UDFto PDF/Rapor folder, not claimed ahead of actual successful runs.
