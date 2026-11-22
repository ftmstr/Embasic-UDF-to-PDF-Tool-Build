# TUR414 — Public app source identity snapshot (2026-11-20 16:01 TRT)

**Evidence-only / NOT_APPROVED / NO-GO.** Source commit before this manifest: `3f956563af875506270dde7e167b3cfdbeb4a0b3`. This is a scoped GitHub directory enumeration, **not** a complete distributable source, artifact or dependency SBOM. Git blob IDs below are Git SHA-1 identities, **not** SHA-256 checksums or ownership proof. Never use this manifest as an approval token. Do not infer rights from repository visibility.

| Public Java source | Git blob SHA-1 | Rights and NOTICE |
|---|---|---|
| `app/src/main/java/com/embasic/publicapp/DesktopApp.java` | `c5619856c10306f9a79524cd0aa894dadfa60179` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/DocumentElements.java` | `372f047ebc422173352408c2f35c5014c6963c86` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/FileImportPolicy.java` | `b903f69d311f28972ee3623583a4e94c6da40949` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridDropIndicator.java` | `c8659cf92efd85ca03cfd35a628ebb28ffb3ca7d` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridKeyboardSelection.java` | `729c774f5c8fc898ae26f7bfcd3b26a02947d067` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridPointerSelection.java` | `c69931a6794d34c31ac730ee6acea6c20fd8032e` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/GridReorder.java` | `d462e5dbed05688b6ff5e12ddb0914655dc04948` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/OfficialPdfReferenceAudit.java` | `e01f1097c35d6bdc6970fffb52f76d914f9810b8` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/OfflinePdfGlyphAudit.java` | `26430bcca931c600cbff0a8e77533334844b36c0` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/PdfBoxFontCacheDirectory.java` | `15d9fb3b3c550071362268456d2085f531ef96ce` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/PdfExporter.java` | `43ee24c2b3bb749e037a203d9c193aa2beacb0da` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/TableLayout.java` | `54dda2e34cac43ec17210ddeef2fbc64583d41dd` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/ThumbnailSizing.java` | `fa7042d9e46252569f09d644b5fce339173f6f36` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/UdfReader.java` | `ea0bfcd62f7b927574e1f7d7935a162df3e5dce2` | NOT_VERIFIED / BLOCKED |
| `app/src/main/java/com/embasic/publicapp/VirtualGridLayout.java` | `f629e9e86857df7428226e2ef84d4a77f128b232` | NOT_VERIFIED / BLOCKED |

Compared with historical TUR375/TUR382/TUR392 ledger, source identities have drifted (including `DocumentElements.java`, `PdfExporter.java` and `UdfReader.java`); historical blobs must never be used to attest the current tree. A reviewer must re-enumerate at the **proposed** exact release commit and reconcile **all** files, workflows, scripts, generated resources, third-party dependencies, fonts, images, fixtures and packaged bytes, with written author/owner provenance, license and NOTICE texts, redistribution permissions and independent approval. No approval is recorded here.

Independent release gates remain separate: legacy `src/main` BLOCKED; Public `app/` source/license/NOTICE NOT_VERIFIED; `PUBLIC_SOURCE_APPROVED_SHA` alone never authorizes publication. Rights-clean offline native UDF/PDF golden NOT_RUN; native UYAP NOT_VERIFIED; physical Windows beta NOT_RUN; physical external exFAT NOT_RUN; hostile CAS NO-GO; release NO-GO. No merge, tag, release, installer, ZIP or distribution. No smoke test or progress percentage changed.
