# TUR395 — ZIP local/central decoded payload SHA-256 parity (NO-GO)

Date: 2026-11-19 Europe/Istanbul. Restricted to Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build` branch `ci/secure-windows-bootstrap-20261018`. TUR394 exact-final SHA `4306053f714a5cd9e4d79bd0eb8a004a5b658820`, Drive TUR394 final report and hourly automation checked. Do not duplicate TUR394 work.

## Reproduced P0 silent-content ambiguity

A ZIP's CENTRAL entry may reference a different LOCAL file header from the one a sequential `ZipInputStream` reads. TUR394 compared member name, method, decoded size and CRC-32; those metadata can match even when the decoded bytes differ, since CRC-32 is not collision resistant. Independent OpenJDK 21 reproduction uses an original rights-clean synthetic `content.xml` containing `ILYZRZIDGILQ` in the sequential LOCAL record and `XJDQSZRLQIDU` in the CENTRAL-referenced record, both 150 bytes and CRC-32 `0x81106fec`. The first record is followed by a non-member marker and a second hidden LOCAL record before the valid CENTRAL directory; `ZipInputStream` stops at the marker while `ZipFile` follows the second header. Java 21 confirmed distinct plaintext content, equal CRC/size/name, one entry on each reader. A standalone preflight matching TUR394 logic accepted the metadata and count but SHA-256 differed. No native UYAP or third-party document was used.

## Targeted correction

`app/src/main/java/com/embasic/publicapp/UdfReader.java`: while streaming each LOCAL entry, compute SHA-256 over the bounded decoded bytes. For the corresponding CENTRAL entry, independently stream `ZipFile.getInputStream(entry)`, enforce the declared-size bound, and compare SHA-256 via constant-time `MessageDigest.isEqual`. Reject disagreement before XML/PNG parsing or PDF creation. Keep prior member-count, path, CRC, declared-size, compression-method, 32 MiB aggregate decoded LOCAL limit, 2048 entry limit, and sanitized messages. No unsafe fallback or release gate change. Central streaming adds at most one further 32 MiB bounded decompression across an archive.

`app/src/test/java/com/embasic/publicapp/ReferencedMediaRegressionTest.java`: one focused deterministic in-memory crafted ZIP fixture. Verify the CRC32 collision is genuine, that Java's actual local/central readers return different valid synthetic XML bytes, and that `UdfReader.read` fails closed with a SHA-256 mismatch. No randomized search, no real UYAP files, no broad smoke duplication.

Public implementation commit `62ea6db2f6a5cce10657375aa16a447c82ce1591`; focused regression commit `c42cfbb89f86208f6cf3108d7392752f2f82e895`. Exact-final SHA and CI evidence to be appended after real checks.

## Release gate

Native UYAP official PDF golden: NOT_RUN/NOT_VERIFIED. Independent `app/` source/LICENSE/NOTICE rights approval: NOT_VERIFIED. Legacy `src/main` Secure Bootstrap: SOURCE_GATE BLOCKED. Hostile existing-target CAS: NO-GO; physical exFAT unsupported-hardlink: NOT_RUN; independent physical Windows beta: NOT_RUN. Release: **NO-GO**. No Private repo access, main merge, tag, release, installer, ZIP or binary publication. Engineering indicator 44.3% unchanged.
