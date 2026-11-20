#!/usr/bin/env python3
"""Four fresh JVMs exercise PDFBox's *default*, not explicitly configured cache.

Disposable fixture only. Do not override Maven's real user.home and do not
write, inspect or repair any actual user's font metadata.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
INVALID_METADATA = b"EMBASIC-SYNTHETIC-INVALID-DEFAULT-CACHE\n"
PHASES = ("cold", "updated", "metadata-rebuild", "bad-font")


def run_phase(home: Path, phase: str) -> int:
    maven = shutil.which("mvn")
    if maven is None:
        raise RuntimeError("Maven executable not found")
    argv = [
        maven, "-B", "-ntp", "-f", "app/pom.xml",
        "-Dtest=PdfFontDefaultCacheReplayProbe",
        f"-Dembasic.defaultcache.fixture={home}",
        f"-Dembasic.defaultcache.phase={phase}",
        "-Dpdfbox.fontcache.skipchecksums=false",
        "test",
    ]
    started = time.monotonic()
    finished = subprocess.run(
        argv, cwd=ROOT, capture_output=True, text=True, errors="replace",
        env=os.environ.copy(), check=False, timeout=240,
    )
    elapsed = round((time.monotonic() - started) * 1000)
    output = finished.stdout + "\n" + finished.stderr
    expected = (
        f"EMBASIC_DEFAULT_FONTCACHE phase={phase} fallback=UNSET "
    )
    if (finished.returncode or expected not in output or not re.search(
        r"Tests run: 1, Failures: 0, Errors: 0, Skipped: 0", output
    )):
        raise RuntimeError(
            f"Isolated default-cache phase {phase} failed "
            f"(exit={finished.returncode}); no PASS receipt issued.\n"
            + "\n".join(output.splitlines()[-16:])
        )
    return elapsed


def check_metadata(path: Path, stage: str) -> None:
    if path.is_symlink() or not path.is_file() or path.stat().st_size < 100:
        raise RuntimeError(f"{stage}: default PDFBox metadata missing/invalid")
    if path.read_bytes() == INVALID_METADATA:
        raise RuntimeError(f"{stage}: default PDFBox did not rebuild bad cache")


def main() -> None:
    # Temp root is passed only to a JUnit JVM property. The real Maven home,
    # Windows LOCALAPPDATA, installed OS fonts and documents are left intact.
    with tempfile.TemporaryDirectory(prefix="embasic-default-fontcache-") as temp:
        home = Path(temp).resolve()
        cache = home / ".pdfbox.cache"
        approved = home / "validated.pdf"
        timing = {}
        timing["cold"] = run_phase(home, "cold")
        check_metadata(cache, "cold")
        if not approved.is_file() or approved.stat().st_size < 1000:
            raise RuntimeError("Cold phase did not publish a valid synthetic PDF")
        timing["updated"] = run_phase(home, "updated")
        check_metadata(cache, "updated")
        cache.write_bytes(INVALID_METADATA)
        timing["metadata-rebuild"] = run_phase(home, "metadata-rebuild")
        check_metadata(cache, "metadata-rebuild")
        baseline_pdf = approved.read_bytes()
        timing["bad-font"] = run_phase(home, "bad-font")
        if approved.read_bytes() != baseline_pdf:
            raise RuntimeError("Invalid replacement font overwrote approved PDF")
        check_metadata(cache, "after-invalid-source")
        print(
            "EMBASIC_DEFAULT_CACHE_REPLAY processes=4 "
            f"cold_wall_ms={timing['cold']} updated_wall_ms={timing['updated']} "
            f"metadata_rebuild_wall_ms={timing['metadata-rebuild']} "
            f"invalid_font_wall_ms={timing['bad-font']} "
            "default_property=UNSET metadata_rebuild=PASS "
            "embedded_font_source_changed=PASS "
            "invalid_source_existing_pdf=UNCHANGED "
            "checksums=ON user_profile_writes=NONE"
        )


if __name__ == "__main__":
    main()
