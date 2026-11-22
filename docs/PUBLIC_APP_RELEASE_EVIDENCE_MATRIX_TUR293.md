# TUR293 — Public app/ release evidence matrix (NO-GO)

Evidence baseline: `0a1f4e981f2c12d87765e4a6887caa9a538e1d57` (2026-11-16 Europe/Istanbul). Scope: **only** Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, branch `ci/secure-windows-bootstrap-20261018`. This is a review checklist, **not** a license grant or release approval.

## Confirmed from `app/pom.xml` (blob `3abdb70859b95f5346532c017ed905ea8d20959d`)

| Coordinate / build item | Declared version | What is established | Release evidence still required |
| --- | --- | --- | --- |
| `org.apache.pdfbox:pdfbox` | 3.0.8 | Direct runtime dependency | Actual resolved transitive graph, artifact POM/LICENSE/NOTICE and bundled notices, hashes, font/image provenance |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 | Test-scope declaration | Verify excluded from distributed shaded artifact and record test dependency notices separately |
| `maven-shade-plugin` | 3.6.0 | Shade attached artifact classifier `all`, manifest main class `com.embasic.publicapp.DesktopApp` | Inspect actual shaded JAR entries, merged META-INF/LICENSE/NOTICE, duplicate/conflicting notices and reproducible hash |
| `maven-compiler-plugin` | 3.13.0 | Java release 17 | Capture actual toolchain/JDK distribution and its distribution terms |
| `maven-surefire-plugin` | 3.5.2 | Unit-test runner | Preserve exact-SHA CI logs; passing tests are not publication permission |

**Important:** Maven coordinates alone do not establish the complete transitive dependency list, bundled binary contents, legal attribution obligations or source ownership. Do not infer those as PASS.

## Two independent source-rights gates

1. **Legacy `src/main`**: source-publication preflight remains BLOCKED. `PUBLIC_SOURCE_APPROVED_SHA` is not independently sufficient.
2. **Independent Public `app/`**: file-level ownership, contributors, license grant, embedded assets/fonts and NOTICE obligations must be independently evidenced. Existing file/blob inventory is identification, **not** ownership approval.

Neither gate can be overridden by a successful PDF/desktop workflow. Never move code or binaries from any Private repository.

## Acceptance artifacts required before any distributable

- Exact app commit and reproducible `mvn -f app/pom.xml dependency:tree` output, including scope and SHA-256 of resolved artifacts; identify every runtime transitive dependency.
- Exact shaded JAR listing and per-artifact origin map, complete licenses and NOTICE text where required; reviewer sign-off for every third-party and first-party source and embedded font/image.
- Rights-clean offline golden inputs and reference PDFs with permission record and hashes; evidence for page count, Unicode, fonts, images, table cells and page geometry. A verifier reporting PASS alone is **not** UYAP compatibility evidence.
- Real physical Windows GUI and jpackage acceptance: Explorer-style keyboard/multiselect, virtual grid/zoom/+ tile, reorder, PDF save/overwrite and crash recovery. exFAT physical experiment must remain NOT_RUN until performed.
- Explicit separate written source and binary publication authorization. Do not create tag, release, ZIP, installer, merge to main, or distribute without it.

## Current status and next highest-value action

| Gate | Status |
| --- | --- |
| Legacy source | BLOCKED |
| Public app source ownership | RIGHTS_NOT_VERIFIED |
| Public app transitive license / NOTICE | NOT_VERIFIED |
| Native UYAP equivalence | NOT_VERIFIED |
| Physical exFAT | NOT_RUN |
| Release / distribution | NO-GO |

Next: generate a **real** dependency tree and shaded-JAR contents from the permitted Public branch on CI; compare each resolved artifact against license/NOTICE evidence and separately perform physical Windows beta acceptance. Do not add synthetic smoke tests just to increase counts.
