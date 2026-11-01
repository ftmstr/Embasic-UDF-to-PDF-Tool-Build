#!/usr/bin/env python3
"""Isolated synthetic public-MVP CLI smoke. No authentic UYAP documents."""
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

jar = Path(sys.argv[1]).resolve()
if not jar.is_file():
    raise SystemExit("Public MVP JAR not found")
with tempfile.TemporaryDirectory(prefix="embasic-public-smoke-") as folder:
    root = Path(folder)
    for n, text in enumerate(("SMOKE-A", "SMOKE-B"), 1):
        data = (f'<template format_id="1.0"><content>{text}</content>'
                f'<elements><paragraph><content startOffset="0" length="{len(text)}"/>'
                '</paragraph></elements></template>')
        with zipfile.ZipFile(root / f"{n}.udf", "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("content.xml", data.encode("utf-8"))
    target = root / "ordered.pdf"
    cmd = ["java", "-Djava.awt.headless=true", "-jar", str(jar),
           "--merge", str(target), str(root / "1.udf"), str(root / "2.udf")]
    subprocess.run(cmd, check=True, timeout=90)
    pdf = target.read_bytes()
    if not pdf.startswith(b"%PDF-") or len(pdf) < 1000:
        raise AssertionError("Missing/corrupt public synthetic PDF")
    failed = subprocess.run(cmd, capture_output=True, text=True, timeout=90)
    if failed.returncode == 0 or target.read_bytes() != pdf:
        raise AssertionError("Existing PDF overwrite guard failed")
    # A 160-file merge must not parse even the first archive when its output
    # cannot be published. A deliberately malformed synthetic ZIP proves the
    # existing-target error wins before UDF parsing, with no output mutation.
    corrupt = root / "malformed.udf"
    corrupt.write_bytes(b"not a ZIP archive")
    early = subprocess.run(
        ["java", "-Djava.awt.headless=true", "-jar", str(jar),
         "--merge", str(target), str(corrupt)],
        capture_output=True, text=True, timeout=90)
    if (early.returncode == 0 or "PDF zaten var" not in early.stderr
            or target.read_bytes() != pdf):
        raise AssertionError("Existing target was not rejected before UDF parsing")
    invalid_folder = root / "missing-folder" / "new.pdf"
    early_folder = subprocess.run(
        ["java", "-Djava.awt.headless=true", "-jar", str(jar),
         "--merge", str(invalid_folder), str(corrupt)],
        capture_output=True, text=True, timeout=90)
    if (early_folder.returncode == 0 or "Çıktı klasörü bulunamadı" not in early_folder.stderr
            or invalid_folder.exists()):
        raise AssertionError("Missing output directory was not rejected before UDF parsing")
    # The CLI must reject an incomplete batch before parsing or publishing a
    # PDF. Otherwise duplicate arguments silently repeat pages in the merge.
    for invalid, reason in (
        ([str(root / "1.udf"), str(root / "1.udf")], "yinelenen"),
        ([str(root / "1.udf"), str(root / "notes.txt")], ".udf"),
    ):
        rejected = root / "rejected.pdf"
        attempt = subprocess.run(
            ["java", "-Djava.awt.headless=true", "-jar", str(jar),
             "--merge", str(rejected), *invalid],
            capture_output=True, text=True, timeout=90)
        if attempt.returncode == 0 or rejected.exists() or reason not in attempt.stderr:
            raise AssertionError(
                f"Invalid CLI batch was not rejected before output: {attempt.stderr}")
    print("PASS public synthetic CLI smoke: 2 UDF inputs, PDF header, "
          "early output preflight, non-overwrite, duplicate/unsupported batch rejection")
