#!/usr/bin/env python3
"""Offline, rights-gated reference-PDF visual metrics. NEVER native-UYAP GO.

Uses the existing public custody gate and PDFBox comparison. This utility
reads only explicit local inputs and writes exactly one private local JSON.
It never sends evidence, PDF text or screenshots anywhere.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import threading

MAX_PDF = 64 * 1024 * 1024
MAX_JAR = 128 * 1024 * 1024
MAX_JAVA_LOG = 64 * 1024
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
SUMMARY = re.compile(
    r"REFERENCE_AUDIT: (MATCH|DIFFERENCE) referencePages=(\d+) candidatePages=(\d+) "
    r"checkedPages=(\d+) differingPixels=(\d+) dpi=(\d+) channelTolerance=(\d+)"
)
PAGE = re.compile(
    r"REFERENCE_AUDIT_PAGE: page=(\d+) geometry=(MATCH|DIFFERENCE) "
    r"text=(MATCH|DIFFERENCE) differentPixels=(\d+) renderedPixels=(\d+) "
    r"maximumChannelDelta=(\d+) glyphCount=(\d+) fontGlyphDifferences=(\d+) "
    r"textPositionDifferences=(\d+) changedRasterRows=(\d+) changedRasterColumns=(\d+)"
)


def custody_module():
    source = Path(__file__).with_name("Verify-OfflineGoldenCustody.py")
    spec = importlib.util.spec_from_file_location("embasic_offline_custody", source)
    if spec is None or spec.loader is None:
        raise ValueError("custody gate unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_metrics(output: str, exit_code: int) -> dict:
    """Strictly recognize local PDFBox aggregates; ignore unrelated JVM logs."""
    if len(output) > MAX_JAVA_LOG or exit_code not in (0, 2):
        raise ValueError("unrecognized bounded comparator result")
    summaries, pages = [], []
    for line in output.splitlines():
        if line.startswith("REFERENCE_AUDIT:"):
            result = SUMMARY.fullmatch(line)
            if result is None:
                raise ValueError("unexpected comparator summary")
            summaries.append(result)
        elif line.startswith("REFERENCE_AUDIT_PAGE:"):
            result = PAGE.fullmatch(line)
            if result is None:
                raise ValueError("unexpected comparator page")
            pages.append(result)
    if len(summaries) != 1:
        raise ValueError("missing/duplicate comparator summary")
    summary = summaries[0]
    ref_count, actual_count, checked_count, differences, dpi, tolerance = (
        int(summary.group(i)) for i in range(2, 8)
    )
    if not (1 <= ref_count <= 64 and 1 <= actual_count <= 64
            and checked_count == min(ref_count, actual_count)
            and len(pages) == checked_count and dpi == 96 and tolerance == 8):
        raise ValueError("unsupported comparator counts or raster settings")
    records = []
    total_different = 0
    for position, match in enumerate(pages, 1):
        index = int(match.group(1))
        geo, content = match.group(2), match.group(3)
        values = [int(match.group(i)) for i in range(4, 12)]
        if index != position or any(number < 0 for number in values):
            raise ValueError("invalid comparator page order or metrics")
        pixels, rendered, maximum, glyphs, font_diff, position_diff, rows, columns = values
        if maximum > 255 or font_diff > glyphs or position_diff > glyphs or (
            rendered == 0 and (pixels != 0 or rows != 0 or columns != 0)
        ) or (rendered > 0 and pixels > rendered):
            raise ValueError("inconsistent per-page PDF metrics")
        records.append({
            "page": index, "geometry": geo, "text": content,
            "different_pixels": pixels, "rendered_pixels": rendered,
            "maximum_channel_delta": maximum, "glyph_count": glyphs,
            "font_glyph_differences": font_diff, "text_position_differences": position_diff,
            "changed_raster_rows": rows, "changed_raster_columns": columns,
        })
        total_different += pixels
    matched = (
        ref_count == actual_count and
        all(p["geometry"] == "MATCH" and p["text"] == "MATCH"
            and p["different_pixels"] == 0 and p["rendered_pixels"] > 0
            and p["font_glyph_differences"] == 0
            and p["text_position_differences"] == 0 for p in records)
    )
    declared_match = summary.group(1) == "MATCH"
    if differences != total_different or declared_match != matched or (
        exit_code == 0 and not matched
    ) or (exit_code == 2 and matched):
        raise ValueError("comparator exit status contradicts page evidence")
    return {
        "verdict": "METRICS_MATCH_REVIEW_REQUIRED" if matched else "DIFFERENCE_REVIEW_REQUIRED",
        "reference_pages": ref_count, "candidate_pages": actual_count,
        "different_pixels": total_different, "dpi": dpi, "channel_tolerance": tolerance,
        "pages": records,
    }


def run_bounded_comparator(command: list[str], timeout: float = 240) -> dict:
    """Bound JVM stdout while it runs, discard private stderr, enforce a timeout.

    subprocess.run(capture_output=True) buffers arbitrarily much output in
    process memory before a later size check. A malfunctioning renderer/JVM
    must not exhaust the host just by emitting diagnostic text.
    """
    chunks: list[bytes] = []
    reader_failed = []
    with subprocess.Popen(command, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL) as process:
        def read_capped():
            try:
                data = process.stdout.read(MAX_JAVA_LOG + 1)
                chunks.append(data)
                if len(data) > MAX_JAVA_LOG:
                    # Stop a process that is filling stdout. No raw content is
                    # written to logs, disk, exceptions, or the JSON report.
                    try:
                        process.kill()
                    except OSError:
                        pass
            except (OSError, ValueError):
                reader_failed.append(True)
                try:
                    process.kill()
                except OSError:
                    pass

        reader = threading.Thread(target=read_capped, daemon=True,
                                  name="embasic-offline-pdf-stdout-cap")
        reader.start()
        try:
            exit_code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise
        finally:
            # The process has exited (or was killed); the pipe must close
            # promptly. Never join forever when output is malformed.
            reader.join(timeout=5)
        if reader.is_alive() or reader_failed or not chunks:
            raise ValueError("comparator output stream was not safely captured")
        if len(chunks[0]) > MAX_JAVA_LOG:
            raise ValueError("comparator stdout exceeded local memory budget")
        output = chunks[0].decode("utf-8", errors="replace")
    return parse_metrics(output, exit_code)


def ensure_distinct(inputs: list[Path], output: Path):
    resolved = [path.resolve(strict=True) for path in inputs]
    # Reject redirected ancestors too: a final regular file may still be reached
    # through a symlinked directory outside the operator-approved offline tree.
    if any(path.is_symlink() or not path.is_file() or
           any(parent.is_symlink() for parent in (path.absolute().parent, *path.absolute().parents))
           for path in inputs):
        raise ValueError("unsafe evidence input or redirected ancestor")
    file_ids = [(os.stat(path, follow_symlinks=False).st_dev,
                 os.stat(path, follow_symlinks=False).st_ino) for path in inputs]
    if len(set(resolved)) != len(inputs) or len(set(file_ids)) != len(inputs):
        raise ValueError("evidence inputs must be distinct physical files")
    if output.is_symlink() or output.resolve() in set(resolved):
        raise ValueError("report must not alias an input")
    # Reports must remain in a pre-existing offline owner-approved directory.
    parent = output.parent.absolute()
    if not parent.is_dir() or any(p.is_symlink() for p in (parent, *parent.parents)):
        raise ValueError("report directory absent or redirected")


def private_report(path: Path, record: dict):
    # Never silently replace a rights/custody report. No auto-created directories.
    data = (json.dumps(record, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("utf-8")
    if len(data) > 128 * 1024:
        raise ValueError("aggregate report exceeds local evidence limit")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags, 0o600)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
            f.flush()
            os.fsync(f.fileno())
    except BaseException:
        # Partial reports cannot become an accepted custody record.
        try:
            path.unlink()
        except OSError:
            pass
        raise


def run(args) -> int:
    report = {
        "schema": "embasic-offline-golden-visual-v1",
        "status": "BLOCKED",
        "native_uyap": "NOT_VERIFIED",
        "release": "NO_GO",
        "external_windows_beta": "NOT_RUN",
        "human_fidelity_review": "NOT_VERIFIED",
    }
    status = 3
    try:
        inputs = [args.manifest, args.udf, args.reference_pdf, args.candidate_pdf, args.app_jar]
        ensure_distinct(inputs, args.report)
        if not SHA256.fullmatch(args.expected_app_jar_sha256):
            raise ValueError("independent expected application JAR digest required")
        if args.app_jar.suffix.lower() != ".jar":
            raise ValueError("expected a local shaded application JAR")
        custody = custody_module()
        manifest_sha = custody.digest_file(args.manifest, custody.MAX_MANIFEST)[0]
        manifest = custody.read_manifest(args.manifest)
        source = custody.verify(manifest, args.udf, args.reference_pdf)
        if custody.digest_file(args.manifest, custody.MAX_MANIFEST)[0] != manifest_sha:
            raise ValueError("rights manifest changed during custody verification")
        jar_sha, jar_bytes, jar_id = custody.digest_file(args.app_jar, MAX_JAR)
        if jar_sha != args.expected_app_jar_sha256:
            raise ValueError("application JAR not the independently expected build")
        cand_sha, cand_bytes, cand_id = custody.digest_file(
            args.candidate_pdf, MAX_PDF, required_prefix=b"%PDF-"
        )
        source_id = custody.digest_file(args.udf, custody.MAX_UDF)[2]
        reference_id = custody.digest_file(
            args.reference_pdf, MAX_PDF, required_prefix=b"%PDF-"
        )[2]
        if len({jar_id, cand_id, source_id, reference_id}) != 4:
            raise ValueError("candidate, source, reference, and build files must differ")
        metrics = run_bounded_comparator(
            [args.java, "-Xmx512m", "-Djava.awt.headless=true",
             "-cp", str(args.app_jar.resolve()),
             "com.embasic.publicapp.OfficialPdfReferenceAudit",
             str(args.reference_pdf.resolve()), str(args.candidate_pdf.resolve())]
        )
        # Reconfirm source bytes before publishing a result; an immutable
        # filesystem snapshot and a trusted signed artifact are still needed.
        if custody.digest_file(args.manifest, custody.MAX_MANIFEST)[0] != manifest_sha or (
            custody.digest_file(args.app_jar, MAX_JAR)[0] != jar_sha
        ) or (
            custody.digest_file(args.candidate_pdf, MAX_PDF, required_prefix=b"%PDF-")[0] != cand_sha
        ) or (
            custody.digest_file(args.reference_pdf, MAX_PDF, required_prefix=b"%PDF-")[0]
            != source["pdf_sha256"]
        ) or (
            custody.digest_file(args.udf, custody.MAX_UDF)[0] != source["udf_sha256"]
        ):
            raise ValueError("evidence changed during local PDF visual comparison")
        report.update({
            "status": metrics["verdict"],
            "custody": "BYTE_IDENTITY_VERIFIED_ONLY",
            "rights_manifest_sha256": manifest_sha,
            "udf_sha256": source["udf_sha256"],
            "reference_pdf_sha256": source["pdf_sha256"],
            "candidate_pdf_sha256": cand_sha,
            "candidate_pdf_bytes": cand_bytes,
            "app_jar_sha256": jar_sha, "app_jar_bytes": jar_bytes,
            "comparison": metrics,
        })
        status = 0 if metrics["verdict"] == "METRICS_MATCH_REVIEW_REQUIRED" else 2
    except (OSError, ValueError, TypeError, UnicodeError, subprocess.TimeoutExpired) as ex:
        # Never copy untrusted PDF/JVM exception text, filenames or UYAP data.
        report["error_type"] = type(ex).__name__
    try:
        ensure_report_parent(args.report)
        private_report(args.report, report)
    except (OSError, ValueError) as ex:
        print("OFFLINE_GOLDEN_VISUAL=BLOCKED_REPORT_WRITE", file=sys.stderr)
        return 3
    print("OFFLINE_GOLDEN_VISUAL=" + report["status"])
    return status


def ensure_report_parent(path: Path):
    parent = path.parent.absolute()
    if not parent.is_dir() or any(p.is_symlink() for p in (parent, *parent.parents)):
        raise ValueError("unsafe local evidence output directory")
    if path.is_symlink():
        raise ValueError("report target must not be a symlink")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest", type=Path, required=True)
    p.add_argument("--udf", type=Path, required=True)
    p.add_argument("--reference-pdf", type=Path, required=True)
    p.add_argument("--candidate-pdf", type=Path, required=True)
    p.add_argument("--app-jar", type=Path, required=True)
    p.add_argument("--expected-app-jar-sha256", required=True)
    p.add_argument("--report", type=Path, required=True)
    p.add_argument("--java", default="java")
    return run(p.parse_args())


if __name__ == "__main__":
    sys.exit(main())
