# Original code and brand asset rights — release blockers

**Status: HUMAN REVIEW REQUIRED.** This document records a safe approval process, not permission to publish.

## 1. Source ownership and publication

The private application's current development branch has no root project-owned LICENSE authorizing public reuse. The public Build repository's bootstrap documentation is not an authorization to publish private application code.

Before export, an authorized owner must privately confirm: legal authority over every contributed source file, third-party code provenance, whether the complete source may become publicly viewable/forkable, any open-source licensing terms chosen, and any source notice obligations.

Do not silently apply MIT, Apache, MPL, GPL or an all-rights-reserved declaration to the private application's source. No new copyright grant should be presumed. In particular, changing a repository back to Private later cannot recall third-party clones or extinguish already-granted rights.

GitHub guidance: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository

## 2. Brand and logo evidence

The private project includes Windows ICO and three PNG branding derivatives. The separate access-restricted Drive logo folder contains source/reference images. The existing private audit established limited PNG/ICO structural metadata checks, **not** identical source provenance, copyright ownership, pixel-level inspection or redistribution permission.

Before an approved public snapshot, review the private reference images and each actual GitHub binary separately; compare image content and derivation, check embedded metadata and authorship, and record verified SHA-256 values. Explicitly confirm public hosting and distribution rights for each individual image.

A PNG without text/EXIF metadata is not automatically rights-cleared. **Do not publish or replace the original artwork** solely to make a CI job pass. If authorization is not available, withhold the artwork and application build rather than inventing a new logo or silently removing approved branding.

## 3. Third-party binaries and legal notices

For `com.lowagie:itext:2.1.7`, the Maven Central POM lists Mozilla Public License and mentions LGPL as an alternative. MPL 1.1 requirements for modifications/source availability and executable notices must be reviewed. The LGPL alternative requires analysis of the actual packaging and library-replacement/relinkability obligations. The correct route cannot be selected by an automated test.

Bouncy Castle 138/1.38 Maven transitive resolution includes six coordinate entries and byte-identical provider/mail pairs. The exact embedded binary set and distribution notices of the **actual** shaded application JAR, ZIP and any installer still require review. See `UPSTREAM-DEPENDENCY-EVIDENCE-20261018.md`.

- https://www.mozilla.org/MPL/1.1/
- https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
- https://www.bouncycastle.org/license/

## 4. What stays private

Any signed copyright/brand decision; list of real identities, creator correspondence, license-election analysis with operational data, reference logo originals, SHA-bound approval manifest, full binary/pixel findings, authentic UYAP files and private code audit report must remain **outside** this public repository.

The exact approved source snapshot and target commit SHA must be verified manually against the private record *before* adding source to a public PR. The existing GitHub Actions gate is an additional automated control, **not** a replacement for those approvals.

**Current decision: NO-GO for application source transfer, Windows binary artifact upload or GitHub Release.**
