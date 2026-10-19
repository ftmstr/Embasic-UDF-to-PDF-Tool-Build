#!/usr/bin/env python3
"""Public CI fail-closed gate; no network access or sensitive findings in output."""
from __future__ import annotations
import os
import re
import subprocess
import sys
from pathlib import Path

REQUIRED = {
    "LICENSE", "pom.xml", "THIRD-PARTY-iText.txt", "THIRD-PARTY-NOTICES.txt",
    "src/main/java/com/embasic/udf2pdf/App.java",
    "src/main/java/com/embasic/udf2pdf/UdfParser.java",
    "src/test/java/com/embasic/udf2pdf/ExplorerGridLogicTest.java",
    "src/test/java/com/embasic/udf2pdf/UdfParserRegressionTest.java",
    "scripts/verify-explorer-grid.ps1", "scripts/verify-udf-parser.ps1",
    "scripts/create-smoke-udf.py", "scripts/PdfSmokeVerifier.java",
    "scripts/verify-release-license-jars.py",
    "scripts/test-verify-release-license-jars.py",
    "src/main/resources/com/embasic/udf2pdf/brand/app-icon.png",
    "src/main/resources/com/embasic/udf2pdf/brand/logo-light.png",
    "src/main/resources/com/embasic/udf2pdf/brand/logo-dark.png",
}
BOOTSTRAP_ALLOWED = {
    "audit-pdfbox/src/main/java/com/embasic/audit/pdfbox/PdfQualityOracle.java",
    "docs/TURN-7-PDF-QUALITY-ORACLE.md",
    "audit-pdfbox/pom.xml",
    "audit-pdfbox/src/main/java/com/embasic/audit/pdfbox/PdfBoxProbe.java",
    "audit-openpdf/pom.xml",
    "audit-openpdf/src/main/java/com/embasic/audit/openpdf/OpenPdfProbe.java",
    ".github/workflows/pdf-alternatives-probe.yml",
    "docs/TURN-6-PDF-ENGINE-DECISION.md",
    ".github/workflows/upstream-dependency-audit.yml",
    ".github/workflows/windows-ci.yml",
    ".gitignore",
    "README.md",
    "audit/pom.xml",
    "audit/src/main/java/com/embasic/audit/IsolatedProbe.java",
    "docs/BUILD-GATES.md",
    "docs/DEPENDENCY-EVIDENCE-PLAN.md",
    "docs/RIGHTS-AND-ARTWORK-REVIEW.md",
    "docs/TURN-3-SHADE-AND-LEGAL-GATES.md",
    "docs/TURN-4-SNAPSHOT-EVIDENCE.md",
    "docs/UPSTREAM-DEPENDENCY-EVIDENCE-20261018.md",
    "scripts/HostedRunnerProbe.java",
    "scripts/audit-cached-maven.py",
    "scripts/audit-isolated-shade.py",
    "scripts/audit-shaded-classes.py",
    "scripts/check-portable-notices.py",
    "scripts/public-ci-gate.py",
    "scripts/test-audit-cached-maven.py",
    "scripts/test-audit-isolated-shade.py",
    "scripts/test-audit-shaded-classes.py",
    "scripts/test-check-portable-notices.py",
    "scripts/test-public-ci-gate.py",
    "scripts/test-verify-snapshot-manifest.py",
    "scripts/verify-private-rights-evidence.py",
    "scripts/test-verify-private-rights-evidence.py",
    "docs/TURN-5-LICENSE-ASSET-REVIEW.md",
    "scripts/verify-snapshot-manifest.py",
}

BLOCKED_SUFFIX = {".udf", ".pdf", ".p12", ".pfx", ".jks", ".keystore",
                  ".pem", ".key", ".sqlite", ".db", ".log"}
BLOCKED_NAMES = {".env", "id_rsa", "id_ed25519"}
PATTERNS = {
    "contact_address": re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I),
    "windows_user_path": re.compile(r"[A-Za-z]:[\\/]+Users[\\/]+[^\\/\s]+", re.I),
    "private_key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "credential": re.compile(r"(?i)\b(?:api[_-]?key|access[_-]?token|password|client[_-]?secret)\s*[:=]\s*['\"][^'\"\r\n]{8,}['\"]"),
}
BINARY = {".png", ".ico"}
class GateError(Exception):
    pass

def evaluate(root: Path, tracked: list[str], approved: str, sha: str) -> bool:
    names = set(tracked)
    if not names or len(names) != len(tracked):
        raise GateError("tracked_file_index_invalid")
    for name in tracked:
        p = Path(name)
        if p.is_absolute() or ".." in p.parts or "\\" in name:
            raise GateError("unsafe_path")
        if p.suffix.lower() in BLOCKED_SUFFIX or p.name.lower() in BLOCKED_NAMES:
            raise GateError("restricted_artifact")
        item = root / p
        if item.is_symlink() or not item.is_file() or item.stat().st_size > 2 * 1024 * 1024:
            raise GateError("unsafe_or_oversized_file")
        if p.suffix.lower() in BINARY:
            continue  # Human rights and pixel audit is mandatory before the SHA gate.
        try:
            data = item.read_text(encoding="utf-8")
        except UnicodeError as exc:
            raise GateError("unexpected_binary") from exc
        for category, pattern in PATTERNS.items():
            if name == "THIRD-PARTY-iText.txt" and category == "contact_address":
                continue  # Historic license attributions must stay intact and approved.
            if pattern.search(data):
                raise GateError("potential_sensitive_content_" + category)
    application_files = [n for n in names if n.startswith("src/main/") or n == "pom.xml"]
    if not application_files:
        if names - BOOTSTRAP_ALLOWED:
            raise GateError("unapproved_bootstrap_file")
        return False
    if REQUIRED - names:
        raise GateError("incomplete_application_snapshot")
    if not re.fullmatch(r"[0-9a-f]{40}", approved or "") or approved != sha:
        raise GateError("exact_commit_source_approval_missing")
    return True

def main() -> int:
    try:
        root = Path(__file__).resolve().parents[1]
        raw = subprocess.check_output(["git", "-C", str(root), "ls-files", "-z"])
        tracked = [x.decode("utf-8") for x in raw.split(b"\0") if x]
        approved = os.environ.get("PUBLIC_SOURCE_APPROVED_SHA", "").strip()
        sha = os.environ.get("REVIEWED_HEAD_SHA", "").strip()
        ready = evaluate(root, tracked, approved, sha)
        outfile = os.environ.get("GITHUB_OUTPUT")
        if outfile:
            with open(outfile, "a", encoding="utf-8") as f:
                f.write("ready=" + str(ready).lower() + "\n")
        print("SOURCE_GATE: " + ("APPROVED_EXACT_COMMIT" if ready else "BOOTSTRAP_NO_APP_SOURCE"))
        return 0
    except (GateError, OSError, subprocess.CalledProcessError, UnicodeError) as exc:
        category = str(exc) if isinstance(exc, GateError) else "filesystem_or_git_error"
        print("SOURCE_GATE: BLOCKED (" + category + ")", file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
