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

## TUR375 — exact Public Java source provenance ledger (2026-11-19 05:00 TRT)

Evidence-only inventory at branch HEAD `f918d6552a6639a245adeb5540b79f06ccf99890`. Each identifier below is a **Git blob SHA-1**, not a SHA-256 content digest, copyright claim, or release approval. File presence and blob identity were retrieved from the Public branch directory listing. Rights holder, acquisition route, original author, third-party attribution, redistribution grant, and independent reviewer evidence are **NOT_VERIFIED for every row**. The table is a review queue, not a grant.

| Public source file | Git blob SHA-1 | Rights/NOTICE decision |
|---|---|---|
| `app/src/main/java/com/embasic/publicapp/DesktopApp.java` | `c5619856c10306f9a79524cd0aa894dadfa60179` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/DocumentElements.java` | `739206ec97f1a522bb3c3c7ac73b0d3b969a0596` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/FileImportPolicy.java` | `b903f69d311f28972ee3623583a4e94c6da40949` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridDropIndicator.java` | `c8659cf92efd85ca03cfd35a628ebb28ffb3ca7d` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridKeyboardSelection.java` | `729c774f5c8fc898ae26f7bfcd3b26a02947d067` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridPointerSelection.java` | `c69931a6794d34c31ac730ee6acea6c20fd8032e` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridReorder.java` | `d462e5dbed05688b6ff5e12ddb0914655dc04948` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/OfficialPdfReferenceAudit.java` | `e01f1097c35d6bdc6970fffb52f76d914f9810b8` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/OfflinePdfGlyphAudit.java` | `26430bcca931c600cbff0a8e77533334844b36c0` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/PdfBoxFontCacheDirectory.java` | `15d9fb3b3c550071362268456d2085f531ef96ce` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/PdfExporter.java` | `6e99ab9e6df518e2debe5e7e12929b6d85aec0e8` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/TableLayout.java` | `54dda2e34cac43ec17210ddeef2fbc64583d41dd` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/ThumbnailSizing.java` | `fa7042d9e46252569f09d644b5fce339173f6f36` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/UdfReader.java` | `b2628c5d781617d022dfff8f6a83afa9d49aecd2` | NOT_VERIFIED — BLOCKED |
| `app/src/main/java/com/embasic/publicapp/VirtualGridLayout.java` | `f629e9e86857df7428226e2ef84d4a77f128b232` | NOT_VERIFIED — BLOCKED |

Review completion requires an independent evidence reference and affirmative per-file disposition for **each** row, plus scripts, workflows, fixtures, fonts, images, `app/pom.xml`, and transitive dependencies; none may be inferred from GitHub visibility. The present 15-row inventory does not establish completeness of the distributable app-image. Before approval, re-enumerate at the exact proposed source SHA, resolve added/removed files, verify provenance and final packaged resources, and record an independently authorized decision. Keep legacy `src/main` BLOCKED separately. `PUBLIC_SOURCE_APPROVED_SHA` is not itself permission. Secure Bootstrap remains FAILURE at the inventory SHA; native UYAP NOT_VERIFIED, real golden NOT_RUN, physical exFAT NOT_RUN, hostile CAS NO-GO, physical Windows beta NOT_RUN. Release remains NO-GO; no installer, ZIP, tag, merge, or distribution.

## TUR382 — source identity reconciliation (2026-11-19 12:00 TRT)

At exact Public branch HEAD `da7af41c0edae256d8ee615aee99ba0d34eb7778`, the 15-file directory was re-enumerated. Fourteen Git blob identifiers in the TUR375 ledger still match. `UdfReader.java` has changed from Git blob `b2628c5d781617d022dfff8f6a83afa9d49aecd2` to `af9ada5b04a16cd37c5d49e169723274c77ca6e0` following TUR379/TUR380. The earlier ledger is historical and must not be used as a current release attestation. No new Java filenames were observed in that scoped directory listing. This reconciliation does **not** cover other sources, resources, dependencies or generated app-image contents. All fifteen source rights and independent app release approval remain NOT_VERIFIED/BLOCKED; legacy `src/main` separately BLOCKED. No native golden or physical Windows acceptance was performed. No approval is implied by `PUBLIC_SOURCE_APPROVED_SHA` or CI success.

## TUR392 — XML parser source identity drift and rights hold (2026-11-19 22:04 TRT)

Exact pre-change Public branch HEAD: `068abf9b50e8a77253c1dcf494658f10713b64f7`. Following TUR391's SAX complexity preflight, `app/src/main/java/com/embasic/publicapp/UdfReader.java` now has Git blob SHA-1 `f288596a23dcebc94c9633c67e416c05a15f3922` (read directly from the allowed Public branch). This supersedes the TUR382 observed `af9ada5b04a16cd37c5d49e169723274c77ca6e0`; earlier 15-file ledgers are historical, not current source approval. No rights holder, author, origin, redistribution grant, independent license/NOTICE reviewer, or authorized native fixture evidence was obtained by this reconciliation. Other fourteen entries were **not reverified in this turn**. Re-enumerate all files at any future proposed release SHA and audit all packaged bytes separately.

Existing CI at the pre-change HEAD: Desktop MVP SUCCESS; PDF Engine SUCCESS; Upstream Audit SUCCESS; legal byte parity SUCCESS; Secure Bootstrap FAILURE (source preflight). These results **must not** be represented as tests of this documentation commit. Legacy `src/main` BLOCKED and independent `app/` source/license/NOTICE NOT_VERIFIED; `PUBLIC_SOURCE_APPROVED_SHA` alone is not permission. Native UYAP NOT_VERIFIED; rights-authorized offline golden NOT_RUN; physical Windows beta NOT_RUN; exFAT physical NOT_RUN; hostile CAS NO-GO; release NO-GO. No synthetic smoke tests, progress percentage changes, merge, tag, release, installer, ZIP or distribution.

## TUR394 — local/central ZIP provenance refresh (2026-11-19 23:xx TRT)

At exact Public branch pre-change HEAD `98873ae38256d0db4d74f824751925177151ad0b`, `app/src/main/java/com/embasic/publicapp/UdfReader.java` has the Git **blob SHA-1** `65cc192c4c9ae782fa0e025c979efc5a26eb3a9c` (read back from the permitted Public repository). This version adds strict LOCAL versus CENTRAL ZIP member-name, decoded-byte-size and CRC integrity preflight, preserving the distinct CRC and size diagnostic categories. It supersedes TUR392's prior `f288596a23dcebc94c9633c67e416c05a15f3922` entry. The existing 15-file ledger is HISTORICAL, not current source release clearance. The other fourteen Public Java sources and all runtime assets, scripts, fonts, dependencies and license notices were **not independently reverified in this TUR394 provenance note**.

The independent Public `app/` authorship, copyright, distribution rights and LICENSE/NOTICE review remain NOT_VERIFIED; legacy `src/main` Secure Bootstrap SOURCE_GATE remains BLOCKED. Source SHA identity, JUnit PASS, shaded byte-parity, public visibility and `PUBLIC_SOURCE_APPROVED_SHA` are NOT legal authorization. Native UYAP golden NOT_RUN, physical exFAT NOT_RUN, external Windows beta NOT_RUN, hostile existing-target CAS NO-GO and release NO-GO. No merge, release, installer, ZIP, tagged binary or unapproved native UYAP upload. This documentation-only `app/` update also makes the hosted Windows packaged EXE acceptance workflow eligible at the new exact SHA; the test must actually run before claiming same-SHA physical Robot/JDI PASS.

## TUR395 — bounded LOCAL/CENTRAL decoded-data SHA identity (2026-11-19 23:49 TRT)

At exact Public branch pre-note HEAD `7150bba786afd51cbedad5015271c1f9eda4b69b`, `UdfReader.java` has Git blob SHA-1 `a86868e5323ab04fb5c19f933a738ee83de40870`, and synthetic regression `ReferencedMediaRegressionTest.java` blob SHA-1 `c56fef7be693ae44b63409a7f0238a3e656672f3`. This newly observed code identity supersedes TUR394's UdfReader blob `65cc192c4c9ae782fa0e025c979efc5a26eb3a9c`. The Java reader now compares bounded LOCAL vs CENTRAL decompressed payload SHA-256 in addition to name/size/method/CRC. Original synthetic XML with a genuine equal-size CRC-32 collision reproduced distinct Java ZIP reader outputs. Neither the test nor a Git blob fingerprint proves origin or legal distribution rights.

All fifteen Public app/ Java source ownership and redistribution permissions remain NOT_VERIFIED; LICENSE/NOTICE/font/asset/dependency release review remains NOT_VERIFIED; legacy `src/main` SOURCE_GATE BLOCKED; native UYAP official PDF golden NOT_RUN; physical exFAT NOT_RUN; hostile existing-target CAS NO-GO; external Windows user beta NOT_RUN; overall release NO-GO. This app/ documentation change requests a new exact-SHA packaged Windows EXE Robot/JDI CI via the existing path filter; it is not an authorization or a test result. No main merge, tag, release, installer, ZIP, private access or distribution.

## TUR396 — paragraph UTF-16 leading-space geometry provenance (2026-11-20 01.xx TRT)

At exact Public pre-change HEAD `26804527d38f0957435a6d61fa391c243b774c5f`, the UDF synthetic parser dropped ASCII-only whitespace before the first referenced run of each new paragraph, so visible second-paragraph indentation was lost in PDF output. The implementation now preserves only ASCII-space-only first-run gaps; structural LF/CR/TAB first-run gaps retain their prior documented handling. `UdfReader.java` new Git blob SHA-1 `ea0bfcd62f7b927574e1f7d7935a162df3e5dce2`, and `StructuredUdfRegressionTest.java` new Git blob SHA-1 `d32475c247ab033b1f756fcf29015a81aa6af1d4`. The regression compares the real PDF first-glyph X coordinates and preserves a pretty-printed XML case. Git blob identities and JUnit success do NOT establish copyright provenance or publishing authorization.

All 15 Public app/ Java source origins and redistribution permissions, fonts, dependencies, full LICENSE/NOTICE inventory and independent source authorization remain NOT_VERIFIED; legacy `src/main` SOURCE_GATE BLOCKED. Actual native UYAP + authorized official PDF golden NOT_RUN, hostile target CAS NO-GO, physical exFAT NOT_RUN, outside Windows beta NOT_RUN. Release remains NO-GO. Do not treat this document or `PUBLIC_SOURCE_APPROVED_SHA` as a permission grant. No Private, main merge, tag, release, installer, ZIP or binary publication.
