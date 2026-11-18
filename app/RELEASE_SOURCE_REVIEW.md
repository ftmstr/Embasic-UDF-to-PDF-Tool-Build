# Public app/ source and rights release gate — TUR346 (2026-11-18)

Status: **NO-GO / evidence inventory only**. This is not a license grant, ownership attestation, UYAP certification, or publication approval. The legacy `src/main` source gate remains independent and BLOCKED. `PUBLIC_SOURCE_APPROVED_SHA` alone is insufficient.

## Observed public source inventory (branch HEAD 088dcf08ca10c850e82b9928737f92f342bce876)

The independent `app/src/main/java/com/embasic/publicapp/` directory contains these 15 Java files:

- DesktopApp.java
- DocumentElements.java
- FileImportPolicy.java
- GridDropIndicator.java
- GridKeyboardSelection.java
- GridPointerSelection.java
- GridReorder.java
- OfficialPdfReferenceAudit.java
- OfflinePdfGlyphAudit.java
- PdfBoxFontCacheDirectory.java
- PdfExporter.java
- TableLayout.java
- ThumbnailSizing.java
- UdfReader.java
- VirtualGridLayout.java

Other observed app entry: `app/pom.xml`. The repository root listing contains `.github`, `.gitignore`, `README.md`, `app`, `audit-content`, `audit-openpdf`, `audit-pdfbox`, `audit`, `docs`, `scripts`. **No root LICENSE or NOTICE was present in that listing**; this is a scoped observation, not a full rights audit. No `app/src/main/resources` directory was found.

## Blocking acceptance requirements

1. Produce a file-by-file origin and copyright/author rights attestation for all 15 Java files, `app/pom.xml`, workflows, scripts, fixtures, fonts, and any packaged assets. Do not infer ownership from public GitHub visibility.
2. Review every dependency and transitive dependency license, corresponding license texts and NOTICE obligations against the exact pinned resolved build. Audit embedded fonts/images separately. Preserve the existing dependency SHA and provenance gates.
3. Establish an explicit, independently reviewed release authorization for the **public app/** source tree at a specific exact SHA. Do not let a CI success, environment variable, or this document substitute for authorization.
4. Obtain authorized, offline, rights-clean native UDF golden fixtures with reference PDF outputs and compare text, fonts, page geometry, tables, image positioning and page order. Reject unknown schema elements fail-closed. Native UYAP equivalence remains NOT_VERIFIED until independently established.
5. Complete physical Windows GUI and packaging acceptance, including real end-user save/open flows and supported filesystem behavior; physical exFAT remains NOT_RUN and hostile CAS remains NO-GO.

## CI evidence at inventory HEAD

- Desktop MVP SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961997
- PDF Engine SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961955
- Upstream Audit SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961871
- Public legal byte-parity SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961951
- Windows app-image SUCCESS: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211955516
- Secure Bootstrap FAILURE: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/44211961877

These CI results apply **only** to the inventory HEAD, not to this document's new commit. A legal byte-parity workflow SUCCESS is not a source-license approval.

No main merge, tag, release, installer, ZIP or distribution is authorized. No synthetic smoke test added. No progress percentage changed.

## TUR347 — pinned Maven build and nine upstream attribution slots (2026-11-18 06:02 TRT)

Verified against public `app/pom.xml` at blob `bcd82d6ad6d32433ba39bbeb4d5d3bfa4505a7a8`. This is a concrete configuration-to-evidence matrix, **not** a completed binary provenance or legal review.

| Scope | Exact POM declaration | Evidence and outstanding gate |
|---|---|---|
| Java compiler | release 17 | Build compatibility declared; physical Windows acceptance NOT_RUN |
| PDF runtime direct | org.apache.pdfbox:pdfbox:3.0.8 | Exact resolved artifact hash, authorship and obligations must be independently checked |
| Test only | org.junit.jupiter:junit-jupiter:5.11.4 | Not a runtime distribution grant; transitive test graph unresolved |
| Build compiler | maven-compiler-plugin:3.13.0 | Build plugin provenance pending |
| Test runner | maven-surefire-plugin:3.5.2 | Build plugin provenance pending |
| Resource unpack | maven-dependency-plugin:3.7.1 | process-classes; legal resource presence and byte equality must be verified on packaged output |
| Fat JAR | maven-shade-plugin:3.6.0 | Shade attached classifier all; no explicit license/NOTICE aggregation approval |

The dependency plugin declares exactly **three** upstream artifact coordinates at 3.0.8: `pdfbox`, `fontbox`, and `pdfbox-io`. For **each** it requests `META-INF/DEPENDENCIES`, `META-INF/LICENSE`, `META-INF/NOTICE`, yielding **nine requested source-to-resource relationships**. Output directories are respectively `META-INF/embasic-upstream/pdfbox`, `.../fontbox`, `.../pdfbox-io` under the build output. This is an *unpack configuration*, not proof that all nine resources exist in the upstream JARs, survive packaging, retain byte parity, or satisfy legal attribution obligations. Verify each source JAR SHA-256, resource existence, byte SHA-256, final shaded JAR entry and final Windows app-image contents independently; any absent/mismatched item remains BLOCKED. Preserve the existing CI source gate; do not auto-authorize on successful parity alone.

The public POM has **no** declared application-specific license or distribution authorization. The root LICENSE/NOTICE observation remains scoped. Rights holder attestation for all public Java sources and any fixtures/fonts/images remains NOT_VERIFIED. Golden UDF/PDF native acceptance NOT_RUN; no native UYAP equivalence claimed. Legacy `src/main` BLOCKED and independent `app/` release authorization NOT_VERIFIED; `PUBLIC_SOURCE_APPROVED_SHA` is not an authorization. Physical exFAT NOT_RUN, hostile CAS NO-GO, physical Windows beta NOT_RUN. No release, installer, ZIP, tag, main merge or distribution authorized.
