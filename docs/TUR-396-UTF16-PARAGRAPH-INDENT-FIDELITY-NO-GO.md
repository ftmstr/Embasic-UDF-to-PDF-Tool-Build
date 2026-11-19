# TUR396 — Prevent silent PDF paragraph indentation loss from skipped UTF-16 ASCII spaces

Date: 2026-11-20 Europe/Istanbul. Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, `ci/secure-windows-bootstrap-20261018` ONLY. Starting exact-final TUR395 SHA `26804527d38f0957435a6d61fa391c243b774c5f`; real Drive TUR395 final report and hourly automation verified. The automated TUR396 check made no code commit, so this unique targeted P0 work does not repeat a prior implementation.

## Concrete defect

In `UdfReader.parse`, ranges are in raw `<content>` UTF-16 coordinates. The validated gap between a previous paragraph and the FIRST run in a new paragraph was accepted as structural whitespace, but the code copied that gap into the new run ONLY when `!runs.isEmpty()`. Example: raw `INDENT   INDENT`, first paragraph range (0,6), second paragraph range (9,6). The three ASCII spaces before the second paragraph vanished, moving its visible first glyph left in the PDF. Existing tests covered skipped spaces inside one paragraph and direct API indentation, but not this XML range boundary. This is visible page-geometry/content fidelity loss, not an authentic UYAP finding.

## Narrow fix and focused acceptance

`app/src/main/java/com/embasic/publicapp/UdfReader.java`: prepend a skipped gap to the current run when it is not the first run OR when the entire first-run gap contains only ASCII space characters. Thus visually meaningful indentation survives; other structural LF/CR/TAB cases keep their documented parsing behavior. No extra renderer, UI, font, UYAP schema assumptions, source gate or output overwrite changes.

`app/src/test/java/com/embasic/publicapp/StructuredUdfRegressionTest.java`: one targeted test verifies the second paragraph's run retains all three skipped ASCII spaces and the real PDF first glyph is horizontally displaced; also asserts a structural newline before a first run remains unchanged. No repeated broad synthetic sweep.

Source identity: `UdfReader.java` blob `ea0bfcd62f7b927574e1f7d7935a162df3e5dce2`, regression blob `d32475c247ab033b1f756fcf29015a81aa6af1d4` (Git SHA-1 object identity, NOT licenses). `app/RELEASE_SOURCE_REVIEW.md` updated with explicit NO-GO. This documentation file records scope; actual exact-commit Windows/Linux JUnit and separate packaged Windows Robot/JDI must be independently confirmed before a PASS claim.

## Unchanged release gates

Authentic native UYAP supported versions and official golden PDF fidelity NOT_VERIFIED/NOT_RUN; independently reviewed Public app/ source/LICENSE/NOTICE/font and distribution rights NOT_VERIFIED; legacy `src/main` Secure Bootstrap SOURCE_GATE BLOCKED; hostile target CAS NO-GO; physical exFAT unsupported-hardlink NOT_RUN; external Windows W01–W10 beta NOT_RUN. Engineering estimate 44.3% unchanged. RELEASE NO-GO. No Private access, main merge, tag, installer, ZIP or distributed binary.
