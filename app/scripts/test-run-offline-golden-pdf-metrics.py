#!/usr/bin/env python3
"""Rights-clean parser-only regression; does not claim native UYAP equivalence."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import unittest

PATH = Path(__file__).with_name("Run-OfflineGoldenPdfMetrics.py")
spec = importlib.util.spec_from_file_location("embasic_golden_metrics", PATH)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
GOOD = ("REFERENCE_AUDIT: MATCH referencePages=1 candidatePages=1 checkedPages=1 "
        "differingPixels=0 dpi=96 channelTolerance=8\n"
        "REFERENCE_AUDIT_PAGE: page=1 geometry=MATCH text=MATCH differentPixels=0 "
        "renderedPixels=100 maximumChannelDelta=0 glyphCount=3 fontGlyphDifferences=0 "
        "textPositionDifferences=0 changedRasterRows=0 changedRasterColumns=0\n")


class ParserRegression(unittest.TestCase):
    def test_exact_metrics_requires_manual_review(self):
        result = audit.parse_metrics(GOOD, 0)
        self.assertEqual(result["verdict"], "METRICS_MATCH_REVIEW_REQUIRED")
        self.assertEqual(result["pages"][0]["rendered_pixels"], 100)

    def test_difference_has_nonzero_exit_and_cannot_be_pass(self):
        altered = GOOD.replace("REFERENCE_AUDIT: MATCH", "REFERENCE_AUDIT: DIFFERENCE")
        altered = altered.replace("differingPixels=0", "differingPixels=1", 1)
        altered = altered.replace("differentPixels=0", "differentPixels=1")
        self.assertEqual(audit.parse_metrics(altered, 2)["verdict"], "DIFFERENCE_REVIEW_REQUIRED")
        with self.assertRaisesRegex(ValueError, "contradicts"):
            audit.parse_metrics(altered, 0)

    def test_missing_page_or_duplicate_summary_rejected(self):
        with self.assertRaises(ValueError):
            audit.parse_metrics(GOOD.split("REFERENCE_AUDIT_PAGE:")[0], 0)
        with self.assertRaises(ValueError):
            audit.parse_metrics(GOOD + GOOD, 0)

    def test_error_and_inconsistent_page_rejected(self):
        with self.assertRaises(ValueError):
            audit.parse_metrics(GOOD, 3)
        with self.assertRaises(ValueError):
            audit.parse_metrics(GOOD.replace("renderedPixels=100", "renderedPixels=0"), 0)

    def test_process_output_is_bounded_before_render_completion(self):
        payload = "import sys; sys.stdout.write('x'*100000); sys.stdout.flush()"
        with self.assertRaisesRegex(ValueError, "exceeded local memory budget"):
            audit.run_bounded_comparator([sys.executable, "-c", payload], timeout=5)

    def test_bounded_process_suppresses_stderr_and_keeps_metrics_only(self):
        payload = ("import sys; sys.stderr.write('sensitive internal UDF diagnostic'); "
                   "sys.stdout.write(" + repr(GOOD) + ")")
        result = audit.run_bounded_comparator([sys.executable, "-c", payload], timeout=5)
        self.assertEqual(result["verdict"], "METRICS_MATCH_REVIEW_REQUIRED")
        self.assertNotIn("sensitive", str(result))

    def test_bounded_process_timeout_is_fail_closed(self):
        with self.assertRaises(subprocess.TimeoutExpired):
            audit.run_bounded_comparator(
                [sys.executable, "-c", "import time; time.sleep(10)"], timeout=0.15
            )

    def test_bounded_process_rejects_nonsense_even_with_zero_exit(self):
        with self.assertRaisesRegex(ValueError, "missing/duplicate comparator summary"):
            audit.run_bounded_comparator(
                [sys.executable, "-c", "print('not a PDF comparator result')"], timeout=5
            )


if __name__ == "__main__":
    unittest.main()
