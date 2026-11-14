#!/usr/bin/env python3
"""Measure a real 160-file public synthetic CLI merge on Windows and Linux.

No authentic UYAP data, external assets, publication, or benchmark claims.
Numbers are CI-runner baselines, not physical end-user Windows measurements.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import zipfile


def windows_counters(pid):
    """Return a sampler and a final CPU reader for one Windows Java process."""
    import ctypes
    from ctypes import wintypes

    class MemoryCounters(ctypes.Structure):
        _fields_ = [
            ("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD),
            ("PeakWorkingSetSize", ctypes.c_size_t), ("WorkingSetSize", ctypes.c_size_t),
            ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
            ("QuotaPagedPoolUsage", ctypes.c_size_t),
            ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
            ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
            ("PagefileUsage", ctypes.c_size_t), ("PeakPagefileUsage", ctypes.c_size_t),
        ]

    class FileTime(ctypes.Structure):
        _fields_ = [("low", wintypes.DWORD), ("high", wintypes.DWORD)]

    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    psapi = ctypes.WinDLL("psapi", use_last_error=True)
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(FileTime)] * 4
    kernel.GetProcessTimes.restype = wintypes.BOOL
    psapi.GetProcessMemoryInfo.argtypes = [
        wintypes.HANDLE, ctypes.POINTER(MemoryCounters), wintypes.DWORD]
    psapi.GetProcessMemoryInfo.restype = wintypes.BOOL
    handle = kernel.OpenProcess(0x0400 | 0x0010, False, pid)
    if not handle:
        raise OSError(ctypes.get_last_error(), "Cannot sample Java process")

    def sample():
        counters = MemoryCounters()
        counters.cb = ctypes.sizeof(counters)
        if not psapi.GetProcessMemoryInfo(handle, ctypes.byref(counters), counters.cb):
            return None
        return int(counters.PeakWorkingSetSize)

    def cpu_seconds():
        created, exited, kernel_time, user_time = (
            FileTime(), FileTime(), FileTime(), FileTime())
        if not kernel.GetProcessTimes(handle, ctypes.byref(created),
                                      ctypes.byref(exited), ctypes.byref(kernel_time),
                                      ctypes.byref(user_time)):
            return None

        def seconds(value):
            return ((value.high << 32) | value.low) / 10_000_000

        return seconds(kernel_time) + seconds(user_time)

    return sample, cpu_seconds, lambda: kernel.CloseHandle(handle)


def run_profile(command):
    before = None
    if os.name != "nt":
        import resource
        before = resource.getrusage(resource.RUSAGE_CHILDREN)
    started = time.perf_counter()
    process = subprocess.Popen(command, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True,
                               encoding="utf-8", errors="replace")
    sampler = cpu_reader = closer = None
    peak_bytes = None
    try:
        if os.name == "nt":
            sampler, cpu_reader, closer = windows_counters(process.pid)
        while process.poll() is None:
            if time.perf_counter() - started > 240:
                process.kill()
                raise TimeoutError("160-file synthetic merge exceeded 240 seconds")
            if sampler is not None:
                value = sampler()
                if value is not None:
                    peak_bytes = max(peak_bytes or 0, value)
            time.sleep(0.2)
        output, errors = process.communicate(timeout=10)
        wall = time.perf_counter() - started
        if os.name == "nt":
            cpu = cpu_reader()
            if peak_bytes is None:
                raise RuntimeError("Windows Java peak working set was not measurable")
            peak_mib = peak_bytes / 1024**2
        else:
            import resource
            after = resource.getrusage(resource.RUSAGE_CHILDREN)
            cpu = (after.ru_utime + after.ru_stime
                   - before.ru_utime - before.ru_stime)
            # Linux ru_maxrss is KiB (Windows uses a separate WinAPI sampler).
            peak_mib = after.ru_maxrss / 1024
        return process.returncode, output, errors, wall, cpu, peak_mib
    finally:
        if process.poll() is None:
            process.kill()
            process.communicate()
        if closer is not None:
            closer()


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: profile_160.py path/to/public-mvp-all.jar")
    jar = Path(sys.argv[1]).resolve()
    if not jar.is_file():
        raise SystemExit("Public MVP JAR not found")
    with tempfile.TemporaryDirectory(prefix="embasic-public-profile-") as folder:
        root = Path(folder)
        inputs = []
        for number in range(160):
            segments = [
                f"PUBLIC-PERF-{number:03d} PAR-{part}: "
                + "Synthetic content for bounded PDF layout measurement. "
                for part in range(8)
            ]
            xml_text = "".join(segments)
            offset = 0
            paragraphs = []
            for segment in segments:
                paragraphs.append(
                    f'<paragraph><content startOffset="{offset}" '
                    f'length="{len(segment)}"/></paragraph>')
                offset += len(segment)
            xml = ('<template format_id="1.0"><content>' + xml_text
                   + '</content><elements>' + "".join(paragraphs)
                   + '</elements></template>')
            path = root / f"synthetic-{number:03d}.udf"
            with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("content.xml", xml.encode("utf-8"))
            inputs.append(str(path))
        target = root / "merged-160.pdf"
        command = ["java", "-Xmx512m", "-Djava.awt.headless=true",
                   "-jar", str(jar), "--merge", str(target), *inputs]
        code, output, errors, wall, cpu, rss = run_profile(command)
        if code != 0 or "OK 160 page(s):" not in output:
            raise AssertionError(
                f"160-file public synthetic merge failed: exit={code}; "
                f"stdout={output[-600:]!r}; stderr={errors[-1200:]!r}")
        pdf = target.read_bytes()
        if not pdf.startswith(b"%PDF-") or b"%%EOF" not in pdf[-1024:]:
            raise AssertionError("160-file output PDF missing header or EOF")
        metrics = {
            "fixture": "160 distinct public synthetic format_id=1.0 UDFs; 8 paragraphs each",
            "runner": sys.platform,
            "java_heap_limit_mib": 512,
            "pages": 160,
            "output_bytes": len(pdf),
            "wall_seconds": round(wall, 2),
            "java_cpu_seconds": None if cpu is None else round(cpu, 2),
            "java_peak_rss_mib": round(rss, 2),
        }
        print("PROFILE_160_JSON: " + json.dumps(metrics, ensure_ascii=True, sort_keys=True))


if __name__ == "__main__":
    main()
