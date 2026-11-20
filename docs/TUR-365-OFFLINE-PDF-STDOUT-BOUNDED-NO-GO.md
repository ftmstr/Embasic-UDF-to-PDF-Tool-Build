# TUR365 — Bound offline PDF comparator stdout before accepting local golden metrics

18 November 2026 (Europe/Istanbul); Public ftmstr/Embasic-UDF-to-PDF-Tool-Build only, branch ci/secure-windows-bootstrap-20261018. Initial HEAD dd840a516f6f11e26d814f2ced6831b257f5317f, TUR363/TUR364 already committed. TUR362 Drive report and TUR363–364 report verified. No native UYAP fixtures or human rights grants available.

## Verified concrete security defect
The rights-gated offline golden comparator used `subprocess.run(...stdout=PIPE, stderr=PIPE)`. Its MAX_JAVA_LOG check ran only AFTER the JVM finished: a corrupt/untrusted PDF runtime or accidentally verbose `--java` executable could emit unlimited stdout/stderr to host memory before the check. Stderr could contain protected input diagnostics though only retained in memory. This is a bounded-resource bug in the local review tool, not a claim of actual native UYAP equivalence.

## Fix
- Replace unbounded `subprocess.run` buffered capture with `Popen` and one daemon reader capped at MAX_JAVA_LOG+1 (64KiB + 1 byte). Over-budget stdout immediately kills the child, returns sanitized failure; no user PDF content, diagnostics or filenames printed/persisted.
- Redirect stderr directly to DEVNULL; protected exception text never enters application memory or local reports.
- Bound process runtime to 240 seconds; kill/reap on timeout, and enforce bounded reader thread completion. A process that refuses to finish or produce valid aggregate summary yields BLOCKED; only exact structured PDFBox metrics are parsed.
- Four rights-clean subprocess-specific regressions verify excessive output rejection, stderr privacy, timeout and zero-exit nonsense rejection. This is not another synthetic UDF converter smoke.
- PowerShell release dashboard remains fail-closed: OfflineGoldenVisualMetrics=LOCAL_PDFBOX_STDOUT_BOUNDED_GOLDEN_NOT_RUN, native UYAP NOT_VERIFIED and overall release NO-GO. CI asserts the explicit status.

## Open acceptance gates
This hardening does not certify input rights, establish signed binary provenance, perform an actual native golden visual review, certify every font/image/table, or satisfy physical Windows beta, exFAT, hostile CAS or Public app LICENSE/NOTICE approval. Never Private, main merge, tag, release, installer, ZIP or binary distribution. Exact-SHA Windows/Linux/GUI CI and Drive final results to be appended after real runs.
