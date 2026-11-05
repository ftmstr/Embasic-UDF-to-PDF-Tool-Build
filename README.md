# Embasic UDF to PDF — Public-only Development

**As of 2026-10-27, this Public repository is the only active development destination.** The historical Private repository is no longer a source of truth, build dependency, or source-transfer target. Do not copy, reference, or publish its contents, history, artifacts, or genuine UYAP documents here.

## What is working in source

- `app/`: **new, independently authored Java 17 Swing / PDFBox 3.0.8 desktop MVP**. Opens a thumbnail workspace, adds multiple files from a dialog or Explorer drop, reorders selected file cards, supports basic light/dark colors, and saves a PDF in item order. File reading and previews happen off the Swing UI thread.
- Safe `.udf` ZIP scanning and fail-closed `content.xml` parser: limited `template` / `format_id` / `content` / `elements` / `paragraph` XML subset with UTF-16 offsets and named basic styles. Only the documented synthetic `format_id="1.0"` contract is accepted (other version identifiers fail closed). Controlled synthetic table, ZIP-local PNG reference, and page-layout subsets are implemented; unsupported elements, headers, signatures, exotic fonts, and unverified variants **fail with an explicit error**; no silent omission.
- PDFBox Unicode-font-backed A4 PDF output, same-directory temporary staging, PDF re-open validation, overwrite confirmation/guard, ordered multiple documents.
- `.github/workflows/public-mvp-ci.yml`: compile/JUnit/CLI-smoke on Ubuntu and Windows. **No binary publishing**.

This is **not** a verified UYAP-equivalent converter. No authentic UDF fixtures are stored or used here.

## Build and run locally

Requires Maven and Java 17 or newer. A suitable font must be installed on the local PC (Windows Arial/Segoe UI or Linux DejaVu Sans). Font files are not distributed here.

```powershell
mvn -B -ntp -f app/pom.xml clean verify
java -jar app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar
```

Command-line smoke mode:

```powershell
java -jar app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar --merge output.pdf one.udf two.udf
```

This initial app supports a **very limited UDF subset**. Its presence in GitHub does **not** mean a released Windows executable exists.

## Other directories and release controls

- `audit/`, `audit-content/`, `audit-pdfbox/`, `audit-openpdf/`: isolated synthetic fidelity, font, drawing geometry and PDF content tests. **Not** the active executable app.
- `docs/BUILD-GATES.md` and `.github/workflows/windows-ci.yml`: earlier import/release approval controls retained intact. They intentionally do not approve or publish the new binary.
- Publication of a downloadable Windows package will require public-only application acceptance tests, review of Apache PDFBox and transitive dependency notices/licenses, approval of exact release bytes/checksums, and a verified Windows launcher smoke. No proprietary resources or OS fonts may be redistributed without authorization.
- No automatic release, Git tag, installer, or ZIP is being asserted.

## Immediate product priorities

1. Windows CI for the new Public app; fix any build or UDF parsing failures.
2. Add and validate UDF table/image/page-layout subsets with rights-clean synthetic fixtures; do not silently accept unsupported elements.
3. Add more faithful document preview/reordering/selection; harden output rollback and logging.
4. Verify Windows portable app-image and redistribution notices **before** enabling artifact publishing.
5. Conduct strictly offline accuracy comparison against owner-authorized UYAP-native PDFs; never upload case files to Public GitHub.

**License note:** The new code is original in this Public repo; the presence of code does not grant blanket redistribution permission for third-party fonts, logos, legacy Private code or previously bundled libraries. Binary release authorization remains a separate decision.

## Public MVP — PowerShell release dashboard (2026-10-28)

Run the **color PowerShell roadmap** directly in the Public checkout:

```powershell
pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1
pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1 -Offline
pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1 -Offline -AsJson
```

The dashboard reports a manually weighted release-readiness estimate (**42.7% at the 2026-10-28 review**), milestone evidence and GitHub Actions status. The forecast is **conditional and low confidence**: an early Public beta in about 3–6 weeks; a broader final candidate in approximately 10–18 weeks *only if* owner-authorized test fixtures, redistribution rights and UYAP visual fidelity work are resolved. These dates are **not a promise** and all-UDF-version parity is not certified. The dashboard source and JSON schema run through the Windows CI job.

**Safety fixes:** PDFBox wrapped large-font lines now retain their proper line height; unknown page properties are rejected rather than quietly rendered on A4. The older source-publication, license and binary-release approval gates remain closed.

## Tur 39 (continued) — Bounded thumbnail zoom and coalesced previews (2026-10-29)

The single-grid Swing workspace now supports an adjustable thumbnail slider (132–272 logical pixels), `Ctrl + Plus/Minus`, `Ctrl + 0` to reset, and `Ctrl + mouse wheel` for zoom. The `+` add-file card scales using the same `ThumbnailSizing` arithmetic. Zoom only resizes already decoded thumbnail images; it **does not reopen UDF files**. The asynchronous preview pipeline creates higher-resolution thumbnails once and batches rapidly completed previews via a short, non-repeating Swing redraw timer. A separate redraw timer throttles slider movement.

`ThumbnailSizingTest` covers zoom limits, keyboard steps, card/image bounds and viewport column counts. No actual large-volume GUI benchmark or virtualization has been verified; the existing 160-card cap is retained. The original UYAP fidelity and binary distribution blockers remain unchanged.

## Tur 39 — Explorer multi-card drag and stable Shift-click anchor (2026-10-29)

When multiple UDF cards are selected (Ctrl+click), pressing an already selected card no longer discards the other selected cards before dragging. Dropping the selection on an unselected card retains the selected group and moves it in order. A simple click without dragging still collapses the selection to that card. Repeated Shift+click now keeps the original range anchor, allowing the range to shrink as well as grow. The pure pointer-selection contract has regression tests; this is **not** a supervised Windows mouse/drag acceptance test.

## Tur 38 — Explorer-style keyboard selection (2026-10-29)

The single-grid workspace now supports arrow-key focus movement (left/right/up/down), Shift+Arrow contiguous selection, Ctrl+Arrow focus navigation without clearing selected cards, Ctrl+Space toggling a focused card, Ctrl+A selection of all cards, and Delete to remove selected cards. The selection algorithm is independently tested through `GridKeyboardSelectionTest` and does not require real UDF documents. No claims of native UYAP visual equivalence or fully tested accessibility are made. Mouse multi-selection, thumbnail cards, and drag reorder are retained.

## Tur 37 — ZIP-local synthetic PNG references and bounded UI import (2026-10-29)

The independently authored Public-only application now accepts **exactly one more documented synthetic subset**, without claiming authentic UYAP archive compatibility:

```xml
<template format_id="1.0">
  <content>BEFORE AFTER</content>
  <properties pageSize="A5" orientation="landscape" margin="32"/>
  <elements>
    <paragraph><content startOffset="0" length="6"/></paragraph>
    <image ref="media/logo.png" mime="image/png" width="40" height="40"/>
    <paragraph><content startOffset="7" length="5"/></paragraph>
  </elements>
</template>
```

The ZIP must include `content.xml` and a literal `media/logo.png` entry. This is a **test contract invented for the Public MVP**, not a proven native UYAP `<image>` schema. The reader refuses external URLs, `../` traversal, ambiguous case-folded duplicates, unsupported file types, missing references, mixed inline+reference payloads, oversized PNG resources and decompression dimensions above four million pixels. Nothing is unpacked to disk or uploaded to a server. Synthetic page sizes are A4, A5, Letter and Legal; unknown layout metadata fails closed.

Desktop preview uses two bounded background workers, a card limit of `FileImportPolicy.MAX_CARDS`, native-aspect thumbnail scaling and cancellation checks when a file card is removed. This does **not** certify performance with authentic large UDFs or all file variations. Unit tests exercise 2,500 proposed paths, safe ZIP media resolution and ordered PDF text/image output; the Windows/Linux CI is authoritative for execution status.

**Remaining blockers:** no real native UYAP visual-parity evidence, incomplete image/metadata schema support, no supervised Windows GUI acceptance test, and unresolved third-party license/NOTICE and release-gate approvals. No downloadable Windows binary or final release has been authorized.

## Tur 40 — Viewport-only virtualized thumbnail grid (2026-10-29)

The Public Swing workspace now uses `VirtualGridLayout` to compute stable card bounds from file count, zoom level and viewport width. Only cards in the visible rows plus **one buffer row on each side** have Swing components. Other UDFs remain in the original ordered list, with selection and PDF export still based on that list. Keyboard navigation scrolls to the *logical position* of an offscreen card. The `+` add-file card remains at index `items.size()` and uses the same zoom/card geometry. Scroll callbacks use a short nonrepeating Swing timer, avoiding 160-card component rebuilds on every viewport event. Existing 160-item cap and two background preview workers remain.

`VirtualGridLayoutTest` checks boundary/clipping math, mixed zoom widths, all visible cards across a **2,500-card synthetic geometry**, and the 160+add-card use case. `GridReorder` extracts stable multi-card reorder independent of rendered components, and `GridReorderTest` covers moving selected cards with offscreen members and invalid targets. These are deterministic synthetic tests, **not a supervised Windows GUI benchmark**. Real UYAP document parity, redistribution rights, and the existing Windows release approval gates remain unresolved.


## Tur 41 — Safe virtual-grid drag capture and scroll/resize stress (2026-10-30)

The Public-only Swing editor now retains the pressed thumbnail JPanel while its virtual grid scrolls, preventing a gesture from losing its mouse-release handler when offscreen cards are recycled. During a drag, an EDT-based edge-scroll timer moves the viewport toward the pointer; targets are resolved by `VirtualGridLayout.indexAt(x,y)`, not by whether a destination JPanel exists. Left/right midpoint semantics use `insertAfter`; the `+` tile continues to move selected cards to the end. Existing Ctrl/Shift multi-selection, zoom, keyboard arrows, PDF export order and release gates are preserved.

`VirtualGridLayoutTest` exercises 160+1 logical drop targets across three zoom values and three viewport widths, outside-grid rejection and bounded edge-scroll deltas. `VirtualGridSwingLoadTest` simulates scroll/zoom/resizing on the real Swing EDT, mounts lightweight Swing panels and prints viewport samples, maximum mounted components, mean update milliseconds and observed heap delta. **It is a synthetic Swing load smoke, not an attended Windows GUI benchmark or a verified FPS/peak-memory acceptance test of authentic UDF documents.** No unsupported native UYAP parity, source publication permission or downloadable binary is implied.


## Tur 42 — Drag insertion indicator and viewport autoscroll feedback (2026-10-31)

The Public-only Swing grid now paints a blue, **noninteractive insertion marker** over file cards during drag operations. It indicates the position before/after the target card; dropping onto the trailing `+` tile moves the selection to the end. `GridDropIndicator` resolves marker rectangles from virtual document coordinates without requiring an onscreen card or a new event-catching Swing child. The indicator updates while autoscrolling or resizing and disappears on mouse release.

`GridDropIndicatorTest` adds three headless regression cases for target coordinates across 132/188/272-pixel zoom and 680/1000/1430-pixel viewport widths, marker painting and invalid/empty targets. Existing `VirtualGridLayoutTest`, `VirtualGridSwingLoadTest`, full Maven/JUnit, Windows packaged launcher and PDF engine/audit gates remain in the Public CI. The synthetic Swing load metrics **do not establish real Windows GUI FPS or native UYAP PDF fidelity**. The Windows release authorization gate remains closed; no downloadable executable was published.


## Tur 43 — Accessible drag hints, Escape cancellation and manual Windows Robot acceptance (2026-10-31)

The Public-only desktop UI now exposes a text and accessibility description of the active virtual-grid drop target (e.g. *3. kartın önüne taşı*), along with the `Esc` cancellation option. The status is updated only when the target changes. Escape stops edge autoscroll and releases the captured card **without moving UDFs**. Mouse release after cancellation cannot accidentally reorder a file. The visual blue insert marker remains paint-only.

For a **real interactive Windows desktop** (JDK 17 + Maven, public checkout), use:

```powershell
pwsh -NoProfile -File app/scripts/Run-Windows-GuiRobotSmoke.ps1 -AllowGui
```

The opt-in `GuiRobotSmokeTest` opens the actual DesktopApp, imports 40 synthetic local ZIP UDFs, drives a real mouse press/drag to the bottom edge, verifies the viewport scrolled, checks the insertion marker and accessible status text, then releases and verifies file order. It never uses real case files or publishes an EXE. Do not touch the mouse while the test runs. If the Windows session is locked, headless or lacks desktop input permission, the opt-in run **fails/inconclusive** rather than being reported as passing. The regular Maven/CI run intentionally skips physical GUI acceptance. A manual `workflow_dispatch` option `run_gui_robot` on the Public-only Desktop MVP Proof workflow enables this explicit additional test on a Windows runner. Its result must be checked separately.

`GridDropAccessibilityTest` runs under default CI and verifies Turkish before/after/end and invalid-target announcements. Physical Windows acceptance, full screen-reader compatibility, real UYAP PDF parity and redistribution rights remain **unverified** until explicitly tested and approved. Source approval/SHA/NOTICE gates remain intact.



## Tur 44 — GUI Robot wait reliability and PDF script-fidelity fail-closed (2026-11-02)

The opt-in `GuiRobotSmokeTest` no longer invokes `Robot.waitForIdle()` while the virtual-grid edge-autoscroll timer is continuously posting EDT events. It uses explicit Swing EDT barriers instead and rejects desktops that are too small for a meaningful interactive run. **The physical Windows Robot test has not been executed or accepted**: it remains explicit `-AllowGui` opt-in and does not count as passing when regular CI skips it.

`PdfExporter` rejects RTL, shaping-dependent script families and unpositioned combining marks before font loading or PDF staging. PDFBox's basic `showText` does not establish complex text shaping, visual order or mark placement. The new, original `PdfComplexScriptGateTest` covers synthetic Arabic, Hebrew, Devanagari, Thai and Myanmar text, combining accents and table cells, while confirming ordinary Turkish Latin glyphs still export. Rejected input fails closed instead of quietly producing a visually misleading PDF. **This narrows the accepted synthetic subset; it does not claim broad UYAP version support, enhanced rendering fidelity or native pixel equivalence.** Real rights-cleared golden documents and physical Windows UI/screen-reader acceptance remain outstanding.

The color PowerShell dashboard keeps the previous weighted **44.3% engineering estimate**, reports Robot GUI acceptance as `NOT_RUN` and retains the low-confidence forecast dates anchored to the 2026-10-31 evidence review (rather than shifting them on every hourly run). Release publication approval and source/license/SHA gates remain unchanged. No downloadable executable or binary release is published.


## Tur 45 — Stable opt-in GUI Robot checks and styled-word overflow guard (2026-11-03)

The manual, interactive Windows `GuiRobotSmokeTest` no longer assumes fixed mouse delays or a default thumbnail width. It uses the actual live `VirtualGridLayout` and a bounded EDT-observation helper (`GuiRobotWait`) to verify a visible frame, edge autoscroll, insertion target and final reorder, with timeouts that fail explicitly instead of claiming success. `GuiRobotWaitTest` has four normal, cross-platform unit regressions for polling, timeout and EDT access. This **does not certify** physical Windows GUI acceptance: the Robot test remains user-triggered only and reports `NOT_RUN` until it actually runs on an unlocked Windows desktop. Use the existing opt-in command:

```powershell
pwsh -NoProfile -File app/scripts/Run-Windows-GuiRobotSmoke.ps1 -AllowGui
```

The independently authored PDFBox subset previously treated each styled run fragment as a separate word. If two adjacent, uninterrupted fragments crossed the page margin, it silently wrapped at the style boundary and changed the word layout. `PdfExporter` now **fails closed** on such unverified styled-word line/page breaks rather than publishing a misleading PDF. `PdfStyledRunWrapTest` checks a rejected 48-point split word, a genuine space-delimited wrap, a short Turkish styled word, and preservation of an existing validated PDF with no pending files after rejection. This does **not** implement full UYAP word wrapping, native font shaping, table equivalence or page-position parity.

The color PowerShell dashboard remains at **44.3% manually weighted engineering maturity**; the physical Windows Robot acceptance field is still `NOT_RUN`. The conditional beta and final forecast are low confidence and should be revised if rights-cleared native fixtures, license/NOTICE approval or Windows UI acceptance remain unavailable. The old publication/SHA approval gates are unchanged, and no executable is released.


## Tur 46 — PDF color fidelity and reproducible synthetic visual geometry (2026-11-05)

The Public-only PDF exporter now **rejects translucent text styles** (alpha below 255) constructed through the direct API. The strictly documented synthetic XML only specifies opaque `#RRGGBB`; PDFBox's RGB drawing API cannot faithfully preserve that unexpected alpha. The fail-closed style check runs before issuing PDF output rather than silently turning translucent text opaque.

`PdfVisualGeometryRegressionTest` uses **rights-clean synthetic documents and actual rendered PDF output**: it checks that 14pt red regular and 18pt blue bold runs produce distinguishable colored pixels with embedded fonts, that a two-row merged table has its correct external and internal border geometry, and that the parser/exporter preserves A4, A5 landscape, Letter and Legal media-box sizes and ordered text. It also verifies that alpha 0/35/160/254 text styles fail without publishing any PDF. Pixel assertions use measured independent expected points and ink colors, **not** a source-authorized native UYAP gold PDF. A test fixture was corrected after a false font-size assertion picked a shared glyph from the wrong word; the converter was not modified to hide the test failure.

The monthly/scheduled engineering score remains **44.3% (manual estimate)**. The real physical Windows `GuiRobotSmokeTest` remains `NOT_RUN` until deliberately executed on an unlocked interactive Windows desktop. All existing source rights, SHA/NOTICE and Windows release preflight gates remain mandatory; this work does **not** establish all-version UYAP PDF visual parity or grant permission to publish installable binaries.


## Tur 47 — Conservative mixed-font page-boundary guard (2026-11-06)

The Public-only synthetic PDF layout must not silently split an **already drawn single line** when a later, larger font no longer fits the remaining height of the current page. Previously, `Layout.word()` could move **only the large-font suffix** onto page two, changing the apparent line/baseline geometry without informing the user. Complete export now fails closed if the next word would require this unsafe midline page break. A word that legitimately begins at the left margin may still move to the next page. The existing `renderPreview` contract remains first-page-only and does not inspect later page fonts.

The original, rights-clean `PdfMixedFontPageBoundaryTest` verifies three cases against real PDFBox output: (1) an A5 6pt+48pt midline boundary rejects rendering and does **not** overwrite a previously saved validated PDF or leave staged files; (2) a 48pt word starting at the beginning of a line safely moves to page two while the first-page thumbnail stays one page; and (3) 11pt regular and 36pt bold glyphs share a baseline, while a following line advances by the largest run's 1.4× line-height and returns to the left margin. Existing Tur 46 raster checks continue to cover opaque red/blue colors, embedded fonts, merged-cell borders and A4/A5/Letter/Legal media boxes. These are **synthetic geometric regression checks**, not comparisons against native UYAP PDF reference pages.

`GuiRobotSmokeTest` still requires a separately authorized, interactive Windows session and has **NOT_RUN** physical acceptance status. Source publication preflight, SHA/NOTICE licensing and rights gates remain unchanged. The PowerShell progress score stays at its conservative 44.3% weighted engineering estimate; beta/final calendar windows remain conditional and low-confidence, not delivery promises.
