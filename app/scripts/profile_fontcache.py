#!/usr/bin/env python3
"""CI-only isolated PDFBox metadata probe: cold, repeated, corrupted."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
PATTERN = re.compile(
    r"EMBASIC_FONT_PROFILE cold_preview_ms=(\d+) "
    r"repeat_preview_ms=(\d+) pixel_parity=PASS"
)
CORRUPT = b"SYNTHETIC-INVALID-FONT-CACHE\n"


def execute(cache_dir: Path, label: str) -> tuple[int, int, int]:
    maven = shutil.which("mvn")
    if maven is None:
        raise RuntimeError("Missing Maven")
    argv = [
        maven, "-B", "-ntp", "-f", "app/pom.xml",
        "-Dtest=PdfFontFirstUseProfileTest,PdfFontSourceChangeFailClosedTest",
        f"-Dpdfbox.fontcache={cache_dir}",
        "-Dpdfbox.fontcache.skipchecksums=false",
        "-Dembasic.diagnostics.fontTiming=true", "test",
    ]
    start = time.monotonic()
    finished = subprocess.run(
        argv, cwd=ROOT, capture_output=True, text=True, errors="replace",
        timeout=240, env=os.environ.copy(), check=False,
    )
    elapsed = round((time.monotonic() - start) * 1000)
    log = finished.stdout + "\n" + finished.stderr
    result = PATTERN.search(log)
    if (finished.returncode != 0 or result is None
            or "EMBASIC_FONT_VALID_UPDATE embeddedFontChanged=PASS" not in log
            or "EMBASIC_FONT_SOURCE_CHANGE originalPdf=UNCHANGED" not in log):
        raise RuntimeError(
            f"{label}: JUnit failed with exit={finished.returncode}\n"
            + "\n".join(log.splitlines()[-16:])
        )
    return elapsed, int(result.group(1)), int(result.group(2))


def main() -> None:
    # An ephemeral runner's default home may already contain another cache.
    # An isolated, writable cache proves the first and repeat processes share
    # only the metadata from this test, never external PDF documents or fonts.
    with tempfile.TemporaryDirectory(prefix="embasic-fontcache-ci-") as folder:
        cache_dir = Path(folder).resolve()
        meta = cache_dir / ".pdfbox.cache"
        cold = execute(cache_dir, "cold")
        if not meta.is_file() or meta.stat().st_size < 100:
            raise RuntimeError("Cold PDFBox raster did not create metadata")
        warm = execute(cache_dir, "repeat")
        if not meta.is_file() or meta.stat().st_size < 100:
            raise RuntimeError("Repeated raster did not retain metadata")
        meta.write_bytes(CORRUPT)
        corrupted = execute(cache_dir, "corrupt")
        if not meta.is_file() or meta.stat().st_size < 100 or meta.read_bytes() == CORRUPT:
            raise RuntimeError("Corrupted metadata was not rejected and rebuilt")
        # No speed assertion: cold indexing and OS filesystem timings vary.
        print(
            "EMBASIC_FONT_CACHE_PROBE processes=3 "
            f"cold_wall_ms={cold[0]} warm_wall_ms={warm[0]} "
            f"corrupt_wall_ms={corrupted[0]} cold_preview_ms={cold[1]} "
            f"warm_preview_ms={warm[1]} corrupt_preview_ms={corrupted[1]} "
            "metadata_rebuild=PASS checksums=ON pixel_parity=PASS "
            "valid_font_update=PASS corrupted_font_fail_closed=PASS"
        )


if __name__ == "__main__":
    main()
