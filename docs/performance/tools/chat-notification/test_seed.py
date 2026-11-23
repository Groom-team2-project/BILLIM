"""CSRF 확인 실패 후에도 테스트 세션 원문을 복구할 수 있는지 검증."""

import io
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

import seed


class SeedRecoveryTest(unittest.TestCase):
    def test_csrf_failure_keeps_fixture_for_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            compose = root / "compose.yaml"
            compose.write_text("name: billim-perf\n", encoding="utf-8")
            fixture = root / "local" / "fixtures.json"

            with (
                patch.object(seed, "COMPOSE", compose),
                patch.object(seed, "OUTPUT", fixture),
                patch.object(seed, "PAIRS", 1),
                patch.object(seed, "NOTIFICATIONS_PER_MEMBER", 1),
                patch.object(seed.subprocess, "run") as database_write,
                patch.object(seed.urllib.request, "urlopen", side_effect=urllib.error.URLError("backend unavailable")),
            ):
                with patch.object(seed.sys, "argv", ["seed.py"]):
                    with self.assertRaisesRegex(SystemExit, "--refresh-csrf"):
                        seed.main()

            saved = json.loads(fixture.read_text(encoding="utf-8"))
            self.assertEqual(len(saved["pairs"]), 1)
            self.assertTrue(saved["pairs"][0]["owner"]["token"])
            self.assertTrue(saved["pairs"][0]["requester"]["csrf"])
            database_write.assert_called_once()

            def csrf_response(_request, timeout):
                return io.BytesIO(b'{"csrfToken":"verified-csrf"}')

            with (
                patch.object(seed, "COMPOSE", compose),
                patch.object(seed, "OUTPUT", fixture),
                patch.object(seed.urllib.request, "urlopen", side_effect=csrf_response),
                patch.object(seed.sys, "argv", ["seed.py", "--refresh-csrf"]),
            ):
                seed.main()

            refreshed = json.loads(fixture.read_text(encoding="utf-8"))
            self.assertEqual(
                refreshed["pairs"][0]["owner"]["token"],
                saved["pairs"][0]["owner"]["token"],
            )
            self.assertEqual(refreshed["pairs"][0]["requester"]["csrf"], "verified-csrf")


if __name__ == "__main__":
    unittest.main()
