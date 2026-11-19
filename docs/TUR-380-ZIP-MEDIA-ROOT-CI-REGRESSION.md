# TUR380 — Restore canonical media/images ZIP directory markers; fix failing CI

Date: 2026-11-19 Europe/Istanbul. Public repository ftmstr/Embasic-UDF-to-PDF-Tool-Build, branch ci/secure-windows-bootstrap-20261018. Start SHA f24cd8bad1186c9a9b3a0deaf8ca8fb686e5a19a (TUR379). Hourly TUR379 and cumulative Drive TUR370-379 report verified.

## Exact-SHA confirmed existing breakage
- Public Desktop MVP push run 44468700916, PR run 44468706533 FAILED on Windows and Linux. Separate Windows packaged app-image run 44468700914 FAILED at Maven, so it did not reach physical GUI acceptance.
- Existing ReferencedMediaRegressionTest.zippedImageReferenceAndPageFormatsRoundTrip ERROR: 'Desteklenmeyen UDF ZIP dizini: media' when reading a normal empty media/ marker with PNG.
- New StructuredUdfRegressionTest.zipFileDirectoryAliasFailsClosed FAIL: expected duplicate-path refusal but same incorrect media directory rejection occurs first.
- Each OS 210 Maven JUnit: 1 failure, 1 error, 1 normal Robot skip. This is a real product regression, unlike the intentionally blocked legacy source gate.

## Minimal product fix
- TUR379 normalized ZIP directory 'media/' to logical 'media' before seen.add() and namespace validation. Existing namespace validation accepted only media/ and images/ prefixes, incorrectly rejecting canonical top-level directory markers.
- Allow logical media and images directory roots, while retaining descendants and normalized duplicate path guard for media/ plus media file aliases.
- Existing ReferencedMediaRegressionTest covers valid media/ with PNG and existing StructuredUdfRegressionTest covers rejecting file/directory alias. Add focused valid images/ root marker regression to prevent recurrence.
- No change to ZIP member content rules, UI, PDF engine or export semantics; reject unknown elements and UYAP native schemas fail-closed.

## Current engineering constraints
Native UYAP equivalence NOT_VERIFIED, independent Public app source/LICENSE/NOTICE approval NOT_VERIFIED, physical exFAT NOT_RUN, external Windows beta NOT_RUN, hostile CAS NO-GO, legacy SOURCE_GATE BLOCKED and release NO-GO. Existing engineering completion gauge 44.3% not increased. Do not access Private repository or publish any binary, installer, ZIP, tag or release.

Append exact final commit and live Windows/Linux JUnit/physical Windows JDI results only after confirmed CI.