# TUR391 — Bound XML depth, node count and lexical events before DOM construction

Date 2026-11-19, Europe/Istanbul. Only Public ftmstr/Embasic-UDF-to-PDF-Tool-Build / ci/secure-windows-bootstrap-20261018. Starting HEAD 99ecd9862eff67f32c6a837dfa3086b628452b40 (TUR382). Hourly automation most recent TUR390 checked, no forward commits since TUR382, TUR380 Google Drive report checked. No repeated old tour.

## Confirmed high-impact reliability risk
The independent Public UdfReader currently bounds XML byte length to 4 MiB, but it constructs a DOM and then recursively walks every XML element to reject unsupported metadata. At under 4 MiB, an XML source can contain tens of thousands of small tags, comments or hundreds of nested elements. Existing byte-size checks cannot bound DOM object allocations or the later Java call stack. A malformed local UDF therefore risks excessive heap/stack usage or denial of service before its unsupported schema is rejected. This is a code-based risk, not an observed real-case-file crash.

## Fix
Add a secure streaming SAX preflight directly ahead of DOM parse. Limits: XML node/event budget 65536, nesting depth 128, attributes per element 256. Count SAX start-element, nonempty text, ignorable whitespace, processing instruction and lexical comment events, so a comment-only flood cannot bypass the cap. Disable DTD and external entity resolution, external DTD/schema, and XInclude. Throw sanitized IllegalArgumentException for preflight refusal. This preserves existing strict synthetic format_id=1.0 rules, actual DOM metadata rejection, font/table logic, order and PDF renderer.

Three focused rights-clean synthetic Java JUnit regression tests: 70000 small elements (under 4 MiB), 140 nested elements, 70000 empty comments (under 4 MiB). Existing tests of text/PNG/table, source integrity, GUI and PDF outputs remain unchanged. Expected exact SHA CI Windows/Linux JUnit count baseline 211 + 3 = 214 (one normal Robot SKIP).

## Remaining release gates
No licensed native UYAP samples/reference PDFs are available; native all-version equivalence NOT_VERIFIED, real golden NOT_RUN. Public app/ source LICENSE/NOTICE ownership approval NOT_VERIFIED; src/main Secure Bootstrap BLOCKED; physical exFAT NOT_RUN, hostile existing-target CAS NO-GO, external Windows physical W01-W10 beta NOT_RUN. Release NO-GO, prior 44.3% engineering estimate unchanged; no private repo, main merge/tag/release/ZIP/installer/distribution. These SAX tests do not establish real native UYAP format acceptance.

Append observed exact SHA CI, physical Windows Robot/JDI, Drive readback at end.