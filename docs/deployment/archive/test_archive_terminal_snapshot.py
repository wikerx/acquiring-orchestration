"""归档快照的边界与发布校验，不访问真实数据库。"""

import argparse
import importlib.util
import io
import pathlib
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).with_name("archive_terminal_snapshot.py")
SPEC = importlib.util.spec_from_file_location("archive_terminal_snapshot", MODULE_PATH)
archive = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(archive)


class ArchiveTerminalSnapshotTests(unittest.TestCase):
    def test_only_allowlisted_table_and_valid_cutoff_are_accepted(self):
        where = archive.archive_where("sys_mq_outbox", "202608", "2026-09-01")
        self.assertIn("event_status = 'SENT'", where)
        self.assertIn("`create_time` < '2026-09-01 00:00:00'", where)
        with self.assertRaises(ValueError):
            archive.archive_where("transaction_order", "202608", "2026-09-01")
        with self.assertRaises(ValueError):
            archive.archive_where("sys_mq_outbox", "202608", "2026-08-01")
        with self.assertRaises(ValueError):
            archive.archive_where("sys_mq_outbox", "202613", "2027-01-01")
        with self.assertRaises(ValueError):
            archive.archive_where("sys_mq_outbox", "202608", "2026-9-01")

    def test_docker_command_accepts_local_container_without_exposing_password(self):
        command = archive.docker_mysql("MySQL8", "mysql", "-uroot")
        self.assertIn("MySQL8", command)
        self.assertIn('MYSQL_PWD="$MYSQL_ROOT_PASSWORD"', command[5])
        self.assertFalse(any(part.startswith("--password=") for part in command))
        with self.assertRaises(ValueError):
            archive.docker_mysql("MySQL8;echo unsafe", "mysql")

    def test_export_publishes_monthly_file_only_after_counts_match(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = pathlib.Path(directory)
            destination.chmod(0o700)
            args = self.args(destination)
            process = self.process_with_rows(2)
            with mock.patch.object(archive, "source_count", side_effect=[2, 2]) as count, \
                    mock.patch.object(archive.subprocess, "Popen", return_value=process):
                archive.export(args)
            self.assertEqual(2, count.call_count)
            manifests = list(destination.glob("*.manifest.json"))
            self.assertEqual(1, len(manifests))
            self.assertEqual(1, len(list(destination.glob("*.sql.gz"))))
            archive.verify(manifests[0])

    def test_export_does_not_publish_when_source_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = pathlib.Path(directory)
            destination.chmod(0o700)
            with mock.patch.object(archive, "source_count", side_effect=[2, 1]), \
                    mock.patch.object(archive.subprocess, "Popen", return_value=self.process_with_rows(2)):
                with self.assertRaisesRegex(RuntimeError, "row count changed"):
                    archive.export(self.args(destination))
            self.assertEqual([], list(destination.iterdir()))

    def test_export_removes_unverified_new_files(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = pathlib.Path(directory)
            destination.chmod(0o700)
            with mock.patch.object(archive, "source_count", side_effect=[2, 2]), \
                    mock.patch.object(archive.subprocess, "Popen", return_value=self.process_with_rows(2)), \
                    mock.patch.object(archive, "verify", side_effect=ValueError("bad hash")):
                with self.assertRaisesRegex(ValueError, "bad hash"):
                    archive.export(self.args(destination))
            self.assertEqual([], list(destination.iterdir()))

    @staticmethod
    def args(destination):
        return argparse.Namespace(container="MySQL8", schema="payment_acquiring",
                                  table="sys_mq_outbox", month="202608", before="2026-09-01",
                                  output_dir=str(destination), dry_run=False)

    @staticmethod
    def process_with_rows(count):
        process = mock.Mock()
        process.stdout = io.BytesIO(b"INSERT INTO `sys_mq_outbox` VALUES (1);\n" * count)
        process.stderr = io.BytesIO()
        process.wait.return_value = 0
        return process


if __name__ == "__main__":
    unittest.main()
