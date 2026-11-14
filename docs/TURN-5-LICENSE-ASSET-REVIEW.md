# Tur 5 — Rights, 2009-era iText/Bouncy Castle, and brand provenance

**Date:** 2026-10-18. **Safety state:** Public infrastructure only. Original
private Java code, artwork, complete license notices and owner records
withheld. THIS DOCUMENT DOES NOT AUTHORIZE PUBLICATION.

## 1. What was verified about the original private project

- The private repository remains private. The development branch's root
  `LICENSE` retrieval returned **404**. This is not a right to choose
  an open-source license on behalf of the authors.
- Its Java 17 Maven POM depends on `com.lowagie:itext:2.1.7` and
  uses Maven Shade 3.6.0. An independent, synthetic audit earlier resolved
  six effective JAR coordinates due to Maven relocation, with 1,923
  upstream Java classes preserved byte-for-byte in the isolated shade.
- The private `THIRD-PARTY-iText.txt` contains historical upstream
  copyright/debianization and MPL/LGPL notice material. The private
  `THIRD-PARTY-NOTICES.txt` contains the Bouncy Castle historical
  copyright and permission notice. Neither file by itself proves
  the actual shipped ZIP includes ALL legally required terms or
  the relevant library source/relinking mechanism.
- Two **separate, private** Google Drive logo references exist:
  `Logo2.png` and `Embasic Logo-1.png`. The original app references
  3 PNG derivatives and an ICO. Derivation from these two references,
  author identity, copyright ownership, pixel/metadata verification and
  permission for **public repository distribution** remain unverified.
  Nothing from either source has been copied into this repository.

## 2. Third-party distribution obligations to resolve

### iText 2.1.7

Sonatype's pinned 2.1.7 POM lists Mozilla Public License and comments
that historical LGPL terms were available as an alternative. Do not
incorrectly apply the license of modern iText releases automatically.

If the project selects **MPL 1.1**, its Sections 3.1–3.6 must be
evaluated for the actual covered files and executable: source availability,
covered-code modifications, original notices, and clear executable
instructions telling recipients where the corresponding source is
available. The absence of modifications does not establish that no
distribution obligations apply.

If the project instead selects the **LGPL alternative**, a qualified
review must address the exact applicable historical terms and static
shaded-JAR library replacement/relinking requirements. A hash match and a
copy of the original terms are not a replacement mechanism.

The private iText notice uses the literal historical term "GNU LIBRARY GENERAL PUBLIC LICENSE" but does not by itself establish which exact numbered LGPL text governs each relevant file. A separately archived iText 2.1.7 license example contains the **GNU Library GPL version 2 (1991)**; this is research evidence, not a definitive provenance determination for the current exact artifact. Thus the attestation accepts `LGPL-historical-reviewed` only after an authorized reviewer verifies the exact version and full applicable terms privately. It explicitly refuses an assumed `LGPL-2.1` route.

The iText vendor's historical guidance also flags iText 2.1.7 as end-of-life, with potential security and historical third-party intellectual property concerns. This is a distinct technical/legal risk to assess or remediate before release; complete license notices alone do not eliminate it.
- iText vendor historical warning: https://kb.itextpdf.com/it5kb/can-itext-2-1-7-itextsharp-4-1-6-or-earlier-be-use
- Example historic 2.1.7 GNU Library GPL v2 archival text: https://gitlab.siu.edu.ar/siu/jasper/-/blob/master/JasperReports/iText-2.1.7-LICENSE.txt

No license route is selected here. An owner-authorized legal/compliance
review must record one of the valid routes **in a private evidence file**
and review the actual packaging/source offer.

Official references:
- Maven Central exact artifact: https://central.sonatype.com/artifact/com.lowagie/itext/2.1.7
- MPL 1.1 full text: https://www.mozilla.org/MPL/1.1/
- LGPL 2.1 text: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

### Bouncy Castle 138 / 1.38

Maven's actual effective dependency tree includes 6 entries in total
including iText, and old/new Bouncy Castle coordinates that refer to
byte-identical old/new provider and mail JARs. The readable historical
BC permission terms and attributions must be preserved in the actual
portable ZIP in accordance with the applicable released version.
The binary's `LICENSE.class` is not a readable text notice.

- Current project licensing reference: https://www.bouncycastle.org/license/

## 3. New machine-checkable *private-only* sign-offs

Public code file `scripts/verify-private-rights-evidence.py` is designed
to run **offline on a trusted machine** with three independent inputs:

1. The separately prepared, rights-reviewed clean snapshot;
2. Its *external* SHA-256 file inventory manifest;
3. Its *external* human-reviewed rights record, plus the exact private
   source commit SHA to compare.

It refuses missing/inconsistent review fields, SHA changes, wrong
private Git commit, absent project LICENSE, altered historical notices,
missing branding files, unreviewed provenance, denied public redistribution,
or missing independent owner/license/branding review confirmations.

Review is required for the project license, iText's explicit historical
MPL-1.1 or reviewed historical LGPL route, library source/relinkability plan,
Bouncy Castle six-coordinate inventory and notice, and each of the
four actual brand assets. A checksum by itself **never** changes the
authorisation Boolean. It does not create, publish, upload or sign an
approval document.

Example invocation on **private** files only:

```sh
python scripts/verify-private-rights-evidence.py \
  --snapshot-root /private/reviewed-export \
  --snapshot-manifest /private/manifest.json \
  --rights-approval /private/rights-reviewed.json \
  --private-commit <40-HEX-PRIVATE-SOURCE-COMMIT>
```

Never commit these evidence inputs or paste their contents into issue
comments. The script's synthetic regression suite tests missing
licensing, brand authorization, changes, invalid SHA and approved-source
identity without using the project's actual assets or signing for anyone.

**A successful script is evidence consistency, not independent proof that
the attestor was authorized or that all laws were complied with.**
An actual attestation by the rights holder/reviewer remains mandatory.

## 4. GitHub artifact barrier

The Windows `approved-package` job now requires a third manually-set
exact-SHA variable, `PUBLIC_RIGHTS_APPROVED_SHA`, **in addition** to
the existing separate `PUBLIC_SOURCE_APPROVED_SHA` and
`PUBLIC_BINARY_APPROVED_SHA`. Approval variables must remain absent
until the relevant legal, owner and private source checks are genuinely
completed. The pipeline still creates no release and should not
automatically assert rights.

## 5. Status

- Original code's public distribution authority — **BLOCKED**
- Logo provenance/rights for 4 app graphics — **BLOCKED**
- iText MPL/LGPL route, required source/notice/relinking in app package —
  **BLOCKED**
- Bouncy Castle actual release ZIP terms and checksum review — **BLOCKED**
- Actual complete private full-tree clean-source audit — **NOT RUN**
- Actual Embasic Maven/Windows/GUI/UDF-conversion build — **NOT RUN**
- Public source-free CI gates and synthetic tests — **see current GitHub
  Actions evidence, not an assertion of production fitness**

The Private original repository is the source of truth; keep it Private.
Do not merge the Public Draft PR or upload application binaries as
a consequence of synthetic green tests.
