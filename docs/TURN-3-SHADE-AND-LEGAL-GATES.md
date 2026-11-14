# Tur 3 — Zero-private-source Shade and legal release gates

**Date:** 2026-10-18 (Türkiye)  
**Status:** Technical independent shaded audit PASS; application release and source publication still NO-GO.

## Bounded scope

The **separate synthetic** Java 17 audit module, with only the public Maven coordinates
`com.lowagie:itext:2.1.7`, builds an ephemeral test JAR through Maven Shade Plugin 3.6.0.
**No private Embasic Java sources, logos, historical Git objects or real UYAP documents
were published or included in this compilation.** The workflow does not upload artifacts.

This proves the historical dependency resolution and verifies selected and full
`.class` bytes. It is **not** a build of Embasic UDF to PDF Tool.

## Run evidence

- Initial isolated shaded artifact PASS:
  https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/39133562987
- Full-class audit PASS:
  https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/39133780651

The actual isolated Maven Shade log reports six included dependency coordinate
entries and two significant overlap warnings:

- 146 overlapping class/resource entries in legacy/new `bcmail` JAR names
- 1,294 overlapping class/resource entries in legacy/new `bcprov` JAR names

These warning counts include resources, not just `.class` files. A separate
bounded exhaustive hash comparison across upstream `.class` members found:

| Criterion | Observed |
| --- | ---: |
| Dependency coordinate entries inspected | 6 |
| Upstream class occurrences | 3,359 |
| Unique upstream classes reproduced byte-for-byte in isolated shaded JAR | 1,923 |
| Identical duplicate class occurrences | 1,436 |
| Divergent duplicate class bytes | 0 |
| Regressions for upstream POM/JAR checks | 13 PASS |
| Regressions for selected shaded class checks | 9 PASS |
| Regressions for full shaded class checks | 8 PASS |

This does **not** prove that every non-class resource is preserved; Maven manifest,
service resources and third-party legal notices require their own checks.
The checker fails if any source class is missing/changed, class duplicates differ
in byte content, or the independent probe unexpectedly embeds the original
Embasic app entry class.

## Portable ZIP legal-notice gate

`scripts/check-portable-notices.py` verifies the exact expected reviewed source
bytes of `THIRD-PARTY-iText.txt` and `THIRD-PARTY-NOTICES.txt` are present
**once each** inside an optional portable ZIP. It rejects missing, duplicated,
ambiguous, altered or unsafe archive entries. Its public CI tests use strictly
**synthetic** notice bytes. No authentic private legal documentation or app
binary is present here.

**Important:** a byte-equal notice is not proof of legal sufficiency. A human
still needs to select and document the applicable iText MPL 1.1 or LGPL 2.1
route, source-availability/relinking implementation and exact Bouncy Castle
historical notice content against a *real* app package before distribution.
See official license text:
https://www.mozilla.org/MPL/1.1/ and
https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html.

## Source-code and artwork rights are not automated

The original application's project-owned `LICENSE` and an owner's public
distribution authorization are still absent. The privately stored logo references
have not been proven hash-identical to all four derived app binaries, and exact
public redistribution permission for each graphic has not been established.

Human owner sign-offs, an independently approved full-source snapshot,
private identity scans and a source SHA-256 manifest remain prerequisites.
Public-to-Private visibility reversals cannot undo already downloaded material
or irrevocable open-source license grants.

## Release decision

- Public GitHub-hosted runner, isolated Maven dependency tree, isolated Shade
  compilation and class integrity: **PASS** on the cited commits
- Synthetic ZIP notice byte-integrity gate: **test results per current workflow**
- Actual private application Maven/GUI/UDF conversion and Windows ZIP/installer:
  **NOT RUN**
- Third-party executable distribution legal obligations, project source rights,
  asset publication rights: **BLOCKED**
- Real application source import, production JAR artifact and GitHub Release:
  **NOT PERFORMED**

This PR must remain **Draft** and the original repository **Private**.
