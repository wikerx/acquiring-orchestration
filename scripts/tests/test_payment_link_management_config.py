"""配置合并和发布冲突保护；不连接 Nacos、不处理真实凭据。"""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('link_config', Path(__file__).resolve().parents[1] / 'sync-payment-link-management-config.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

SOURCE = '''payment:
  link:
    enabled: true
    storage-ready: true
    frontend-base-url: https://pay.example.invalid
    active-key-version: v1
    signing-keys:
      v1: isolated-test-signing-material-00001
      old: isolated-test-signing-material-00002
'''
TARGET = '''# existing comments
payment:
  retained-setting: preserved
internal-service:
  auth:
    enabled: true
'''

class PaymentLinkConfigurationTests(unittest.TestCase):
    def test_merge_only_adds_link_preserving_other_settings_and_comments(self):
        link = module.link_config(SOURCE)
        merged = module.merge_target(TARGET, link)
        self.assertTrue(merged.startswith('# existing comments'))
        parsed = module.parsed(merged)
        self.assertEqual(parsed['internal-service'], module.parsed(TARGET)['internal-service'])
        self.assertEqual(parsed['payment']['retained-setting'], 'preserved')
        self.assertEqual(parsed['payment']['link']['signing-keys'], link['signing-keys'])
        self.assertEqual(module.merge_target(merged, link), merged)

    def test_different_existing_target_key_is_never_overwritten(self):
        different = SOURCE.replace('active-key-version: v1', 'active-key-version: old')
        with self.assertRaises(module.ConfigurationError):
            module.merge_target(different, module.link_config(SOURCE))

    def test_missing_payment_root_is_added_without_touching_existing_nodes(self):
        self.assertEqual(module.parsed(module.merge_target('server:\n  port: 8001\n', module.link_config(SOURCE)))['server']['port'], 8001)

    def test_duplicate_keys_and_unresolved_secrets_are_rejected(self):
        for text in [SOURCE + '\npayment: {}\n', SOURCE.replace('isolated-test-signing-material-00001', '${UNRESOLVED_SECRET_WITH_LONG_NAME}')]:
            with self.assertRaises(module.ConfigurationError):
                module.link_config(text)

    def test_publish_uses_compare_and_set_and_rejects_false_response(self):
        nacos = module.Nacos.__new__(module.Nacos)
        nacos.group, nacos.namespace = 'test', 'namespace'
        captured = []
        nacos.request = lambda path, params, post: captured.append((path, params, post)) or 'false'
        with self.assertRaises(module.ConfigurationError):
            nacos.publish('target', TARGET, SOURCE)
        self.assertEqual(captured[0][1]['casMd5'], module.hashlib.md5(TARGET.encode()).hexdigest())
        self.assertTrue(captured[0][2])

    def test_read_cache_delay_is_verified_before_success(self):
        reads = iter([TARGET, TARGET, SOURCE])
        class FakeNacos:
            def get(self, name):
                return next(reads)
        with patch.object(module.time, 'sleep') as sleep:
            module.wait_for_config(FakeNacos(), 'target', SOURCE)
            self.assertEqual(sleep.call_count, 2)

    def test_partial_publication_rolls_back_only_own_completed_write(self):
        calls = []
        class FakeNacos:
            def __init__(self, *args):
                self.values = {'service-payment-dev.yaml': SOURCE, 'service-admin-dev.yaml': TARGET, 'service-merchant-dev.yaml': TARGET}
            def get(self, name):
                return self.values[name]
            def publish(self, name, before, after):
                calls.append((name, before, after))
                if name == 'service-merchant-dev.yaml':
                    raise module.ConfigurationError('concurrent update')
                self.values[name] = after
        with tempfile.TemporaryDirectory() as directory, patch.object(module, 'Nacos', FakeNacos), patch.object(module.sys, 'argv', ['test', 'apply', '--environment', 'dev', '--namespace', 'isolated', '--backup-dir', directory + '/new']):
            with self.assertRaises(module.ConfigurationError):
                module.main()
            self.assertEqual(calls[0][0], 'service-admin-dev.yaml')
            self.assertEqual(calls[-1][0], 'service-admin-dev.yaml')
            self.assertEqual(calls[-1][1], calls[0][2])
            self.assertEqual(calls[-1][2], TARGET)
            self.assertEqual((Path(directory) / 'new/service-admin-dev.yaml.before').stat().st_mode & 0o777, 0o600)

if __name__ == '__main__':
    unittest.main()
