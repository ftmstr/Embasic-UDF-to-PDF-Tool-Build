# TUR284 — Public app file-level provenance baseline (2026-11-15)

**Decision: NO-GO / inventory only.** Source snapshot: Public ftmstr/Embasic-UDF-to-PDF-Tool-Build, branch `ci/secure-windows-bootstrap-20261018`, exact parent commit `782bc0e49e923415dc7abaa7d4e39714da8f2665`. This is not a grant of source rights or license approval. No private repository was accessed.

## Scope and audit disposition

This inventory identifies **30** production/build/support files (excluding test files) from the actual Git tree. The 40-hex identifiers below are **Git blob object IDs (SHA-1), not SHA-256 content digests**. A reviewer must independently establish author, provenance, contributor authorization, copied snippets, embedded resources, redistribution terms, and any copyright notices for **each** entry. Every entry is **RIGHTS_NOT_VERIFIED**; no inferred approval from public visibility or green CI.

| Path | Git blob SHA-1 | Bytes | Rights decision |
| --- | --- | ---: | --- |
| `app/pom.xml` | `3abdb70859b95f5346532c017ed905ea8d20959d` | 1905 | RIGHTS_NOT_VERIFIED |
| `app/scripts/Assert-PackagedGuiInput.ps1` | `230d4bbda9e61724dfe611d370754abf59581fb4` | 39070 | RIGHTS_NOT_VERIFIED |
| `app/scripts/PackagedInputRobot.java` | `bf1e8f7d704b89a85b48a19c33733cd28d757f8c` | 8607 | RIGHTS_NOT_VERIFIED |
| `app/scripts/PackagedPdfOrderProbe.java` | `ef3215936cdc5b72f2f2bdcb7df9a61846e0958c` | 2696 | RIGHTS_NOT_VERIFIED |
| `app/scripts/PackagedRenderBoundaryController.java` | `f4515ceafa39e473c0694ef44ba038f348db1a7c` | 7754 | RIGHTS_NOT_VERIFIED |
| `app/scripts/README-default-fontcache-replay.md` | `f9aa9fa43a9eb8c0d2c605c30a4ee5c59e053664` | 2097 | RIGHTS_NOT_VERIFIED |
| `app/scripts/Run-EphemeralExfatHardlinkAcceptance.ps1` | `d1d932e5c5b909770deda23ae33b014f310925e1` | 6781 | RIGHTS_NOT_VERIFIED |
| `app/scripts/Run-Windows-GuiRobotSmoke.ps1` | `c5ceb00f7cf53d9a8f3dd396d43d76ad07a8d2f2` | 3115 | RIGHTS_NOT_VERIFIED |
| `app/scripts/Show-EmbasicProgress.ps1` | `8f689a924ca3fa131b178e41c20009d1d2936893` | 10051 | RIGHTS_NOT_VERIFIED |
| `app/scripts/Test-UnsupportedHardLinkVolume.ps1` | `eb79c3cb6304513c6fbdc575a21ba180edf82cd0` | 3436 | RIGHTS_NOT_VERIFIED |
| `app/scripts/profile_160.py` | `93c4f67f2713cad479fc891a4e49171f44662bc9` | 7276 | RIGHTS_NOT_VERIFIED |
| `app/scripts/profile_fontcache.py` | `a1169c2a91f373081b03358cc3c973396b33933e` | 3219 | RIGHTS_NOT_VERIFIED |
| `app/scripts/profile_fontcache_default.py` | `79ccd00a94962f7398a80a6767f19ae4fe7ca8c1` | 3815 | RIGHTS_NOT_VERIFIED |
| `app/scripts/smoke_cli.py` | `908639f33ea9dd806dafbf98b031ee5ed151418a` | 5572 | RIGHTS_NOT_VERIFIED |
| `app/scripts/smoke_packaged.py` | `5b6e72c1679a65ee84036c989e1882382f54ab7f` | 7550 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/DesktopApp.java` | `f69537b3cb19eb97800b839addf60da9d6e235cf` | 90607 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/DocumentElements.java` | `739206ec97f1a522bb3c3c7ac73b0d3b969a0596` | 19518 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/FileImportPolicy.java` | `b903f69d311f28972ee3623583a4e94c6da40949` | 13054 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/GridDropIndicator.java` | `c8659cf92efd85ca03cfd35a628ebb28ffb3ca7d` | 1735 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/GridKeyboardSelection.java` | `729c774f5c8fc898ae26f7bfcd3b26a02947d067` | 8828 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/GridPointerSelection.java` | `c69931a6794d34c31ac730ee6acea6c20fd8032e` | 4925 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/GridReorder.java` | `d462e5dbed05688b6ff5e12ddb0914655dc04948` | 3728 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/OfficialPdfReferenceAudit.java` | `e01f1097c35d6bdc6970fffb52f76d914f9810b8` | 14542 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/OfflinePdfGlyphAudit.java` | `26430bcca931c600cbff0a8e77533334844b36c0` | 5224 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/PdfBoxFontCacheDirectory.java` | `15d9fb3b3c550071362268456d2085f531ef96ce` | 8312 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/PdfExporter.java` | `03aeb4b7bd4d0e751c54e4e81446ac43652c074d` | 71997 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/TableLayout.java` | `54dda2e34cac43ec17210ddeef2fbc64583d41dd` | 4811 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/ThumbnailSizing.java` | `fa7042d9e46252569f09d644b5fce339173f6f36` | 1606 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/UdfReader.java` | `b2628c5d781617d022dfff8f6a83afa9d49aecd2` | 19999 | RIGHTS_NOT_VERIFIED |
| `app/src/main/java/com/embasic/publicapp/VirtualGridLayout.java` | `f629e9e86857df7428226e2ef84d4a77f128b232` | 7965 | RIGHTS_NOT_VERIFIED |

## Release blockers and required evidence

1. **Independent Public app:** Obtain file-by-file author/contributor rights evidence, license grant, and reviewer/date/decision tied to this exact commit. Review generated/embedded resources, font embedding, and artwork separately. No license text at the conventional root or app LICENSE/NOTICE paths was established in TUR283.
2. **Legacy src/main:** Separate source provenance and human sign-off; `PUBLIC_SOURCE_APPROVED_SHA` is not an authorization and cannot approve either source tree alone.
3. **Shaded dependencies:** Compare actual built shaded JAR to direct/transitive Maven dependency license/NOTICE and copyright obligations, resolve every discrepancy with human sign-off. Technical checksum equality is not legal clearance.
4. **PDF acceptance:** Use rights-clean offline golden UDF fixtures; compare page count, tables, glyphs, geometry and media. Native UYAP remains NOT_VERIFIED and unknown elements must fail closed.
5. **Windows acceptance:** Physical packaged GUI and PDF output review remains required. exFAT physical experiment NOT_RUN.

**No main merge, tag, release, installer, ZIP, distribution or progress percentage change.** Next action: review this exact file list with provenance evidence, then inspect actual shaded legal inventory; prioritize a real Windows beta defect once release prerequisites can be cleared.
