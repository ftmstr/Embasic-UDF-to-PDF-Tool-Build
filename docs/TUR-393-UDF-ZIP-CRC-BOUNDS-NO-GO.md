# TUR393 — Verify ZIP CRC-32 and declared size before UDF content reaches PDF

2026-11-19 Europe/Istanbul. Public ftmstr/Embasic-UDF-to-PDF-Tool-Build branch ci/secure-windows-bootstrap-20261018 only; start exact HEAD 2047be943f1bd6bc43475fc0b319f74fc72c3ee9 (TUR392 source ledger). TUR391 Drive exact-final and hourly TUR392 Drive report checked. No completed work repeated.

## Concrete reproducible corruption risk
Java ZipFile.getInputStream() does not compare uncompressed member bytes with the ZIP central directory CRC. This was reproduced outside the application with a synthetic ZIP whose content.xml central CRC byte was modified while the XML payload was unchanged: Java 21 ZipFile returned 37 bytes and the actual CRC differed from declared CRC. Source UdfReader.java reads content.xml and ZIP PNG through ZipFile streams and checks only size ceilings. Thus malformed/corrupted metadata can be ignored and a PDF could be produced without a ZIP integrity warning. This is a confirmed API-level blind spot, not a claim of observed real native UYAP corruption.

## Minimal fix
New bounded readVerifiedZipMember(ZipFile, ZipEntry, int) enforces declared uncompressed size >=0 and within supported max, known CRC, physical decoded byte count exact, then CRC32 over the full bounded read. Fail closed before parsing UDF XML or referenced PNG. PNG total count and prior restrictions preserved; directory markers also require declared zero size and zero CRC in addition to physically empty input. Error text is generic, without any actual case data. CRC32 checks archive metadata consistency only; it is not cryptographic authentication or license/ownership evidence.

Three rights-clean synthetic tests in ReferencedMediaRegressionTest create valid ZIP XML/PNG, modify one central-directory field without changing payload and assert fail-closed: (1) XML CRC mismatch, (2) referenced PNG CRC mismatch, (3) XML declared uncompressed byte count mismatch. Existing valid media/ and images/ directories, valid references, document ordering, GUI and PDF tests remain intact. Run Windows/Linux exact-SHA CI and hosted packaged physical Windows Robot/JDI; no legitimate UYAP data uploaded.

## Release gate
Independent Public app ownership/LICENSE/NOTICE NOT_VERIFIED, legacy src/main Secure Bootstrap BLOCKED, authentic native UYAP all-version golden NOT_VERIFIED/NOT_RUN, physical exFAT NOT_RUN, hostile existing-target CAS NO-GO, independent physical Windows W01-W10 beta NOT_RUN, release NO-GO. No merge/tag/release/installer/ZIP/binary. Engineering progress 44.3% gauge remains unchanged.

Append exact-final SHA, actual CI, physical GUI and Drive report readback only when verified.