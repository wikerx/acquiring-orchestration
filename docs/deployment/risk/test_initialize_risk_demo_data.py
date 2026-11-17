"""验证演示初始化的停用、预演和重复执行边界，不连接业务数据库。"""

import importlib.util
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location("risk_demo", Path(__file__).with_name("initialize-risk-demo-data.py"))
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


class FakeAdminClient:
    """用正式 API 的分页和创建形态观察初始化行为。"""

    def __init__(self):
        self.records = []
        self.writes = []

    def call(self, method, path, data=None):
        if path == "/admin/risk/functions":
            return [
                {"moduleType": "BLACK", "functionCode": "ip", "functionName": "IP黑名单"},
                {"moduleType": "WHITE", "functionCode": "email", "functionName": "邮箱白名单"},
                {"moduleType": "RULE", "functionCode": "frequency", "functionName": "频率规则"},
            ]
        if path == "/admin/merchants/search":
            return {"records": [{"merchantId": "200045"}]}
        if path.endswith("/page"):
            return {"records": self.records, "total": len(self.records)}
        self.writes.append((path, data))
        self.records.append(data.copy())
        return data.copy()


class RiskDemoInitializationTests(unittest.TestCase):
    def test_dry_run_only_queries(self):
        client = FakeAdminClient()
        result = demo.seed(client, ["200045"])
        self.assertEqual(result["planned"], 3)
        self.assertEqual(client.writes, [])

    def test_second_execution_does_not_create_duplicates(self):
        client = FakeAdminClient()
        self.assertEqual(demo.seed(client, ["200045"], True)["created"], 3)
        self.assertEqual(demo.seed(client, ["200045"], True)["created"], 0)
        self.assertEqual(len(client.writes), 3)
        self.assertTrue(all(data["status"] == 0 for _, data in client.writes))

    def test_all_rule_examples_are_disabled_and_do_not_contain_cvv(self):
        for code in ("sourceUrl", "merchantLimit", "frequency", "threeDs"):
            _, data = demo.sample_payload({"moduleType": "RULE", "functionCode": code,
                                           "functionName": code}, "200045", 7)
            self.assertEqual(data["status"], 0)
            self.assertNotEqual(data.get("decisionAction"), "REVIEW")
            self.assertNotIn("cvv", str(data).lower())
            self.assertTrue(data["remark"].startswith(demo.MARKER))

    def test_unknown_merchant_is_rejected_before_any_write(self):
        client = FakeAdminClient()
        with self.assertRaises(ValueError):
            demo.seed(client, ["not-a-merchant"], True)
        self.assertEqual(client.writes, [])

    def test_nonlocal_origin_is_rejected_before_reading_session(self):
        for origin in ("https://example.com", "http://127.0.0.1@evil.invalid", "file:///tmp/demo"):
            with self.assertRaises(ValueError):
                demo.AdminClient(origin, "/does-not-exist")


if __name__ == "__main__":
    unittest.main()
