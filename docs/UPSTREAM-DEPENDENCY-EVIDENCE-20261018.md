# Upstream iText/Bouncy Castle evidence — 18 Oct 2026

Status: **actual Maven-resolved upstream byte inventory PASS; redistribution NOT APPROVED**.

## GitHub-hosted execution

Public Build workflow: https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/39127544140

This workflow ran a *source-free audit POM* with the exact historical direct dependency `com.lowagie:itext:2.1.7`. Maven resolved six JAR coordinates. The offline scanner read their actual cached POM/JAR bytes, verified declared POM license metadata, bounded archive integrity and inspected archive member names. Twelve synthetic regression tests passed. No real UDF, Embasic code, logo, shaded application JAR, portable ZIP, or distributable artifact was included.

| Maven-resolved coordinate | JAR SHA-256 | POM license metadata |
| --- | --- | --- |
| `com.lowagie:itext:2.1.7` | `7d82c6b097a31cdf5a6d49a327bf582fdec7304da69308f9f6abf54aa9fd9055` | Mozilla Public License |
| `bouncycastle:bcmail-jdk14:138` | `389f405cfa66b26004484733937a22624782a5d86456e0c35e007b60ebc8e359` | Bouncy Castle License |
| `bouncycastle:bcprov-jdk14:138` | `d60b88c5d1932de8d98edd5a3ae2d5d5647793de3eb6157015807ee523cd2bee` | Bouncy Castle License |
| `org.bouncycastle:bctsp-jdk14:1.38` | `ecbbe4d19c256e4dbfec3454b82b4d24b149f90f3f9c90e6aaef229463cd4da8` | Bouncy Castle Licence |
| `org.bouncycastle:bcprov-jdk14:1.38` | `d60b88c5d1932de8d98edd5a3ae2d5d5647793de3eb6157015807ee523cd2bee` | Bouncy Castle Licence |
| `org.bouncycastle:bcmail-jdk14:1.38` | `389f405cfa66b26004484733937a22624782a5d86456e0c35e007b60ebc8e359` | Bouncy Castle Licence |

### Key technical distinction

The iText POM declares three legacy `bouncycastle:*:138` dependencies, but Maven's **effective tree** relocates `bctsp-jdk14:138` to `org.bouncycastle:bctsp-jdk14:1.38`, which pulls in two additional `org.bouncycastle:*:1.38` coordinates. The old and new BC provider/mail JARs were *byte-identical* within this run, but Maven still resolved both coordinate families. A verifier that hardcodes `bctsp-jdk14-138.jar` as the runtime JAR can therefore report a false missing dependency. The eventual real application packaging gate must inspect the effective Maven dependency tree and actual shaded classes, not just four legacy filenames.

### What the JAR inventory does NOT prove

- iText's JAR contained no filename matching a plain `LICENSE`/`NOTICE` marker in this scan. The Bouncy Castle provider contained a `LICENSE.class`; a Java class is **not** the distribution of complete readable license terms. The scan also matched a technical class `NoticeReference.class`, which is not a legal notice.
- The POM's MPL record and alternate LGPL comment are metadata, not an automatically valid license-election, source-availability plan, or LGPL-compatible shaded packaging.
- A SHA-256 of a JAR fetched by Maven is a reproducible byte identifier, not independent publisher authenticity or a security/vulnerability certificate.
- No complete legal notices in final ZIP/installer were verified, and no source rights/brand permissions were approved.

**Distribution remains blocked** until the actual application binary dependency inventory, selected legal compliance route, complete notices and authorization are confirmed.

Primary references: https://central.sonatype.com/artifact/com.lowagie/itext/2.1.7 ; https://central.sonatype.com/artifact/bouncycastle/bcprov-jdk14/138 ; https://central.sonatype.com/artifact/org.bouncycastle/bcprov-jdk14/1.38 ; https://www.mozilla.org/MPL/1.1/ .
