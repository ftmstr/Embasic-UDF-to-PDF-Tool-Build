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
    output = folder / "result.pdf"
    p = subprocess.run([str(launcher), "--merge", str(output), str(udf)],
                       timeout=120, check=False, capture_output=True)
    if p.returncode != 0:
        raise RuntimeError(f"Packaged launcher exited with {p.returncode}: stdout={p.stdout[-2048:]!r}; stderr={p.stderr[-2048:]!r}")
    assert_complete_pdf(output, "Single-source packaged conversion")
    # Positive control: prove this same packaged launcher can merge two
    # DISTINCT physical UDF files before interpreting the hard-link rejection.
    second = folder / "independent.udf"
    with zipfile.ZipFile(second, "w", zipfile.ZIP_DEFLATED) as archive:
        second_content = "INDEPENDENT-UDF-SOURCE-CONTROL"
        second_xml = xml.replace(content, second_content).replace(
            f'length="{len(content)}"', f'length="{len(second_content)}"')
        archive.writestr("content.xml", second_xml.encode("utf-8"))
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
    # Physical Windows filesystem acceptance: two different paths to one NTFS
    # file must fail closed before replacing an already approved PDF.
    alias = folder / "synthetic-hardlink.udf"
    os.link(udf, alias)
    if not os.path.samefile(udf, alias):
        raise AssertionError("Hard-link fixture is not the same physical file")
    approved_sha = hashlib.sha256(distinct_output.read_bytes()).digest()
    source_sha = hashlib.sha256(udf.read_bytes()).digest()
    alias_sha = hashlib.sha256(alias.read_bytes()).digest()
    # Reject on a NEW target; otherwise non-overwrite policy alone could
    # produce a false positive without exercising duplicate-file detection.
    rejected_output = folder / "rejected-result.pdf"
    rejected = subprocess.run(
        [str(launcher), "--merge", str(rejected_output), str(udf), str(alias)],
        timeout=120, check=False, capture_output=True)
    if rejected.returncode == 0 or rejected_output.exists():
        raise AssertionError("Packaged launcher accepted or wrote duplicate physical UDF hard link")
    if (hashlib.sha256(udf.read_bytes()).digest() != source_sha or
            hashlib.sha256(alias.read_bytes()).digest() != alias_sha):
        raise AssertionError("Rejected hard-link merge modified a source UDF")
    if not distinct_output.is_file() or hashlib.sha256(distinct_output.read_bytes()).digest() != approved_sha:
        raise AssertionError("Rejected hard-link merge changed the approved PDF")
    if list(folder.glob("*.staging*")) or list(folder.glob("*.tmp")):
        raise AssertionError("Rejected hard-link merge left staging files")
    print("PASS Windows jpackage app-image launcher smoke")
    print("PASS distinct two-source packaged merge positive control")
    print("PASS physical Windows hard-link duplicate rejection and approved PDF SHA preservation")
