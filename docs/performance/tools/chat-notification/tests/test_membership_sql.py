"""시드의 신·구 소속 스키마 호환 및 확인 실패 시 중단 검증."""

import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from membership_sql import membership_slot_fields


class MembershipSqlTest(unittest.TestCase):
    @patch("membership_sql.subprocess.run")
    def test_v23_includes_saved_community_slot(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "COUNT(*)\n1\n")
        self.assertEqual((",active_slot", ",1"), membership_slot_fields(["mysql-test"]))

    @patch("membership_sql.subprocess.run")
    def test_legacy_schema_preserves_original_insert(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "COUNT(*)\n0\n")
        self.assertEqual(("", ""), membership_slot_fields(["mysql-test"]))

    @patch("membership_sql.subprocess.run")
    def test_missing_metadata_stops_before_seed(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "")
        with self.assertRaises(RuntimeError):
            membership_slot_fields(["mysql-test"])

    @patch("membership_sql.subprocess.run")
    def test_database_failure_is_not_treated_as_legacy(self, run):
        run.side_effect = subprocess.CalledProcessError(1, ["mysql-test"])
        with self.assertRaises(subprocess.CalledProcessError):
            membership_slot_fields(["mysql-test"])


if __name__ == "__main__":
    unittest.main()
