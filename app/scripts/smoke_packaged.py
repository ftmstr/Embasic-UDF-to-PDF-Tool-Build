#!/usr/bin/env python3
"""Synthetic packaged Windows launcher smoke; private UDF data must never be used."""
import subprocess
import sys
import tempfile
from pathlib import Path
import zipfile

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
        raise RuntimeError(f"Packaged launcher exited with {p.returncode}")
    if not output.is_file() or output.read_bytes()[:5] != b"%PDF-":
        raise AssertionError("Packaged launcher failed to produce PDF")
    print("PASS Windows jpackage app-image launcher smoke")
