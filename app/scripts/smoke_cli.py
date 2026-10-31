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
    print("PASS public synthetic CLI smoke: 2 UDF inputs, PDF header, non-overwrite")
