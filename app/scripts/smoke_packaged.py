#!/usr/bin/env python3
"""Synthetic packaged Windows launcher smoke; private UDF data must never be used."""
import hashlib
import os
import subprocess
import sys
import tempfile
from pathlib import Path
import zipfile

def assert_complete_pdf(path, label):
    """Reject truncated packaged output, not merely a PDF-looking header."""
    if not path.is_file():
        raise AssertionError(f"{label}: PDF missing")
    # Inspect only bounded header/trailer windows: a user PDF can be very large.
    # This is a structural smoke check, not a full PDF parser.
    with path.open("rb") as pdf:
        header = pdf.read(5)
        pdf.seek(0, os.SEEK_END)
        size = pdf.tell()
        pdf.seek(max(0, size - 4096))
        trailer = pdf.read()
    if header != b"%PDF-" or not trailer.rstrip(b" \t\r\n\x0c\x00").endswith(b"%%EOF"):
        raise AssertionError(f"{label}: incomplete PDF header/trailer")

def sha256_file(path):
    """Hash potentially large packaged outputs without loading them into RAM."""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.digest()

if len(sys.argv) != 2:
    raise SystemExit("Usage: smoke_packaged.py path/to/launcher.exe")
launcher = Path(sys.argv[1]).resolve()
if not launcher.is_file() or launcher.suffix.lower() != ".exe":
    raise SystemExit("Packaged Windows launcher missing")
with tempfile.TemporaryDirectory(prefix="embasic-packaged-") as directory:
    folder = Path(directory)
    udf = folder / "synthetic.udf"
    content = "WINDOWS-PACKAGED-SMOKE"
    xml = (f'<template format_id="1.0"><content>{content}</content>'
           f'<elements><paragraph><content startOffset="0" length="{len(content)}"/>'
           '</paragraph></elements></template>')
    with zipfile.ZipFile(udf, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("content.xml", xml.encode("utf-8"))
    original_udf_sha = sha256_file(udf)
    output = folder / "result.pdf"
    p = subprocess.run([str(launcher), "--merge", str(output), str(udf)],
                       timeout=120, check=False, capture_output=True)
    if p.returncode != 0:
        raise RuntimeError(f"Packaged launcher exited with {p.returncode}: stdout={p.stdout[-2048:]!r}; stderr={p.stderr[-2048:]!r}")
    assert_complete_pdf(output, "Single-source packaged conversion")
    if sha256_file(udf) != original_udf_sha:
        raise AssertionError("Successful single-source conversion modified input UDF")
    # End-user safety: an existing approved PDF must never be overwritten.
    approved_single_sha = sha256_file(output)
    overwrite = subprocess.run(
        [str(launcher), "--merge", str(output), str(udf)],
        timeout=120, check=False, capture_output=True)
    if overwrite.returncode == 0:
        raise AssertionError("Packaged launcher accepted overwrite of existing PDF")
    if not output.is_file() or sha256_file(output) != approved_single_sha:
        raise AssertionError("Rejected overwrite changed the approved single-source PDF")
    if sha256_file(udf) != original_udf_sha:
        raise AssertionError("Rejected overwrite modified the source UDF")
    # Positive control: prove this same packaged launcher can merge two
    # DISTINCT physical UDF files before interpreting the hard-link rejection.
    second = folder / "independent.udf"
    with zipfile.ZipFile(second, "w", zipfile.ZIP_DEFLATED) as archive:
        second_content = "INDEPENDENT-UDF-SOURCE-CONTROL"
        second_xml = xml.replace(content, second_content).replace(
            f'length="{len(content)}"', f'length="{len(second_content)}"')
        archive.writestr("content.xml", second_xml.encode("utf-8"))
    independent_udf_sha = sha256_file(second)
    if os.path.samefile(udf, second):
        raise AssertionError("Distinct-source control unexpectedly aliases the original")
    # The CLI deliberately rejects overwriting an existing PDF. Use a fresh
    # destination for the positive control, then preserve it for the rejection.
    distinct_output = folder / "distinct-result.pdf"
    distinct = subprocess.run(
        [str(launcher), "--merge", str(distinct_output), str(udf), str(second)],
        timeout=120, check=False, capture_output=True)
    if distinct.returncode != 0:
        raise AssertionError(f"Packaged launcher failed distinct two-source positive control (exit {distinct.returncode}): stdout={distinct.stdout[-2048:]!r}; stderr={distinct.stderr[-2048:]!r}")
    assert_complete_pdf(distinct_output, "Distinct two-source packaged merge")
    if sha256_file(udf) != original_udf_sha or sha256_file(second) != independent_udf_sha:
        raise AssertionError("Successful two-source merge modified an input UDF")
    # A merged, approved PDF must also resist accidental overwrite: a single-\n    # source rejection alone does not prove the multi-source CLI path is safe.\n    approved_distinct_sha = sha256_file(distinct_output)\n    merge_overwrite = subprocess.run(\n        [str(launcher), "--merge", str(distinct_output), str(udf), str(second)],\n        timeout=120, check=False, capture_output=True)\n    if merge_overwrite.returncode == 0:\n        raise AssertionError("Packaged launcher accepted overwrite of approved merged PDF")\n    if not distinct_output.is_file() or sha256_file(distinct_output) != approved_distinct_sha:\n        raise AssertionError("Rejected merge overwrite changed the approved merged PDF")\n    if sha256_file(udf) != original_udf_sha or sha256_file(second) != independent_udf_sha:\n        raise AssertionError("Rejected merge overwrite modified a source UDF")\n    # Physical Windows filesystem acceptance: two different paths to one NTFS
    # file must fail closed before replacing an already approved PDF.
    alias = folder / "synthetic-hardlink.udf"
    os.link(udf, alias)
    if not os.path.samefile(udf, alias):
        raise AssertionError("Hard-link fixture is not the same physical file")
    approved_sha = sha256_file(distinct_output)
    source_sha = sha256_file(udf)
    alias_sha = sha256_file(alias)
    # Reject on a NEW target; otherwise non-overwrite policy alone could
    # produce a false positive without exercising duplicate-file detection.
    rejected_output = folder / "rejected-result.pdf"
    rejected = subprocess.run(
        [str(launcher), "--merge", str(rejected_output), str(udf), str(alias)],
        timeout=120, check=False, capture_output=True)
    if rejected.returncode == 0 or rejected_output.exists():
        raise AssertionError("Packaged launcher accepted or wrote duplicate physical UDF hard link")
    if (sha256_file(udf) != source_sha or
            sha256_file(alias) != alias_sha):
        raise AssertionError("Rejected hard-link merge modified a source UDF")
    if not distinct_output.is_file() or sha256_file(distinct_output) != approved_sha:
        raise AssertionError("Rejected hard-link merge changed the approved PDF")
    if list(folder.glob("*.staging*")) or list(folder.glob("*.tmp")):
        raise AssertionError("Rejected hard-link merge left staging files")
    print("PASS single/two-source approved PDF overwrite rejected without changing PDF or UDF")
    print("PASS successful single/two-source conversions preserve source UDF SHA-256")
    print("PASS Windows jpackage app-image launcher smoke")
    print("PASS distinct two-source packaged merge positive control")
    print("PASS physical Windows hard-link duplicate rejection and approved PDF SHA preservation")
