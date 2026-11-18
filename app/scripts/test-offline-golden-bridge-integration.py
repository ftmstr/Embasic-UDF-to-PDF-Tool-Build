#!/usr/bin/env python3
"""End-to-end offline PDFBox reference *bridge* acceptance (synthetic only).

Exercises the existing rights custody gate and actual shaded PDFBox audit.
This is NOT a native UYAP golden, an external Windows beta, or a release grant.
No sensitive UDF, PDF, manifest or JSON is uploaded or printed.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def synthetic_udf(path: Path, content: str) -> None:
    # Rights-clean synthetic format_id=1.0 fixture; NEVER a native UYAP file.
    xml = (
        f'<template format_id="1.0"><content>{content}</content>'
        f'<elements><paragraph><content startOffset="0" length="{len(content)}"/>'
        '</paragraph></elements></template>'
    ).encode("utf-8")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("content.xml", xml)


def execute(args: list[str], *, success_code: int, description: str):
    # Local ephemeral synthetic input only. Never print private subprocess
    # stdout/stderr or filenames, even when the command reports an error.
    try:
        result = subprocess.run(args, stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=150,
                                check=False)
    except subprocess.TimeoutExpired as exc:
        raise AssertionError(description + " timed out") from exc
    if result.returncode != success_code:
        raise AssertionError(description + " exit status mismatch")


def assert_report(path: Path, expected_status: str, expected_jar: str,
                  expected_udf: str, expected_reference: str) -> dict:
    if not path.is_file():
        raise AssertionError("golden bridge local report missing")
    with path.open("r", encoding="utf-8") as stream:
        evidence = json.load(stream)
    if (
        evidence.get("schema") != "embasic-offline-golden-visual-v1"
        or evidence.get("status") != expected_status
        or evidence.get("release") != "NO_GO"
        or evidence.get("native_uyap") != "NOT_VERIFIED"
        or evidence.get("human_fidelity_review") != "NOT_VERIFIED"
        or evidence.get("external_windows_beta") != "NOT_RUN"
        or evidence.get("custody") != "BYTE_IDENTITY_VERIFIED_ONLY"
        or evidence.get("app_jar_sha256") != expected_jar
        or evidence.get("udf_sha256") != expected_udf
        or evidence.get("reference_pdf_sha256") != expected_reference
    ):
        raise AssertionError("golden bridge evidence is not fail-closed")
    comparison = evidence.get("comparison", {})
    if comparison.get("verdict") != expected_status:
        raise AssertionError("golden bridge metrics contradict status")
    if not comparison.get("pages") or comparison.get("dpi") != 96:
        raise AssertionError("golden bridge did not run actual page raster audit")
    if expected_status == "METRICS_MATCH_REVIEW_REQUIRED":
        if comparison.get("different_pixels") != 0 or any(
            page["geometry"] != "MATCH" or
            page["text"] != "MATCH" or
            page["rendered_pixels"] <= 0 or
            page["font_glyph_differences"] or
            page["text_position_differences"]
            for page in comparison["pages"]
        ):
            raise AssertionError("synthetic matching page metrics inconsistent")
    return evidence


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: test-offline-golden-bridge-integration.py shaded-app.jar",
              file=sys.stderr)
        return 2
    jar = Path(sys.argv[1]).resolve(strict=True)
    if not jar.is_file() or jar.suffix.lower() != ".jar":
        raise AssertionError("shaded public app JAR required")
    code = Path(__file__).with_name("Run-OfflineGoldenPdfMetrics.py")
    if not code.is_file():
        raise AssertionError("offline golden bridge script missing")
    expected_jar = sha256(jar)
    with tempfile.TemporaryDirectory(prefix="embasic-offline-golden-ci-") as temp:
        root = Path(temp)
        first, second = root / "original.udf", root / "different.udf"
        synthetic_udf(first, "EMBASIC-SYNTHETIC-A")
        synthetic_udf(second, "EMBASIC-SYNTHETIC-B")
        reference = root / "synthetic-reference.pdf"
        matching = root / "matching-copy.pdf"
        altered = root / "different-result.pdf"
        for source, output in ((first, reference), (second, altered)):
            execute(["java", "-Djava.awt.headless=true", "-jar", str(jar),
                     "--merge", str(output), str(source)],
                    success_code=0, description="synthetic public PDF conversion")
        # Two physically independent PDF files with identical synthetic bytes.
        # The actual tool must reject the same inode as source and reference.
        shutil.copyfile(reference, matching)
        manifest = root / "synthetic-rights-manifest.json"
        declaration = {
            "schema": "embasic-offline-golden-v1",
            "rights": {
                "owner_reviewed": True,
                "evidence_id": "RIGHTS_CLEAN_SYNTHETIC_CI_ONLY",
            },
            "reference": {
                "origin": "original-synthetic",
                "review_id": "RIGHTS_CLEAN_SYNTHETIC_CI_ONLY",
            },
            "udf_sha256": sha256(first),
            "pdf_sha256": sha256(reference),
        }
        manifest.write_text(json.dumps(declaration, sort_keys=True),
                            encoding="utf-8")
        def command(candidate: Path, report: Path, jar_digest: str) -> list[str]:
            return [
                sys.executable, str(code),
                "--manifest", str(manifest), "--udf", str(first),
                "--reference-pdf", str(reference),
                "--candidate-pdf", str(candidate),
                "--app-jar", str(jar),
                "--expected-app-jar-sha256", jar_digest,
                "--report", str(report),
            ]
        matched_report = root / "synthetic-match-only.json"
        execute(command(matching, matched_report, expected_jar),
                success_code=0, description="offline custody+PDFBox matching")
        assert_report(matched_report, "METRICS_MATCH_REVIEW_REQUIRED",
                      expected_jar, sha256(first), sha256(reference))
        changed_report = root / "synthetic-difference-only.json"
        execute(command(altered, changed_report, expected_jar),
                success_code=2, description="offline custody+PDFBox difference")
        difference = assert_report(
            changed_report, "DIFFERENCE_REVIEW_REQUIRED",
            expected_jar, sha256(first), sha256(reference)
        )
        if (
            difference["comparison"]["different_pixels"] == 0
            and all(page["text"] == "MATCH"
                    for page in difference["comparison"]["pages"])
        ):
            raise AssertionError("changed synthetic document produced no differences")
        refused_report = root / "forged-build-refused.json"
        execute(command(matching, refused_report, "0" * 64),
                success_code=3, description="mismatched application SHA gate")
        refused = json.loads(refused_report.read_text(encoding="utf-8"))
        if (
            refused.get("status") != "BLOCKED"
            or refused.get("native_uyap") != "NOT_VERIFIED"
            or refused.get("release") != "NO_GO"
            or "comparison" in refused
        ):
            raise AssertionError("untrusted JAR SHA produced visual acceptance")
        # No actual UYAP documents, output artifacts or sensitive paths escape
        # the rights-clean ephemeral runner directory.
    print("OFFLINE_GOLDEN_BRIDGE_SYNTHETIC_ONLY=PASS "
          "custody+real_PDFBox+exact_jar_SHA; native_UYAP=NOT_RUN; "
          "human_review=NOT_VERIFIED; release=NO_GO")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, AssertionError) as exc:
        # Never print real document content or operator paths.
        print("OFFLINE_GOLDEN_BRIDGE_SYNTHETIC_ONLY=FAIL "
              "native_UYAP=NOT_VERIFIED; release=NO_GO; type="
              + type(exc).__name__, file=sys.stderr)
        sys.exit(1)
