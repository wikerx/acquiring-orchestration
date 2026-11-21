"""数据库中文注释门禁必须覆盖实际缺陷与动态迁移，不能仅检查是否存在 COMMENT 字样。"""

import runpy
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPTS = Path(__file__).resolve().parents[1]
CHECKER = runpy.run_path(str(SCRIPTS / 'verify-database-comments.py'))
AUDITOR = runpy.run_path(str(SCRIPTS / 'audit-database-comments.py'))


class DatabaseCommentContracts(unittest.TestCase):
    def check(self, sql):
        return CHECKER['inspect_sql'](sql)

    def test_missing_table_and_column_comments_are_rejected(self):
        errors = self.check('CREATE TABLE sample (id BIGINT NOT NULL, PRIMARY KEY(id));')
        self.assertEqual(len(errors), 2)

    def test_english_and_enum_only_comments_are_rejected(self):
        self.assertEqual(len(self.check("CREATE TABLE sample(id INT COMMENT 'Identifier') COMMENT='Business table';")), 2)
        self.assertIsNotNone(CHECKER['comment_error']('PENDING/SUCCESS/FAILED'))

    def test_mojibake_replacement_and_mixed_corruption_are_rejected(self):
        for value in ['æ±‡çŽ‡è¡¨', '汇率表ã€', '状态�', '名称\x81', '中文???', '锟斤拷', '姹囩巼']:
            with self.subTest(value=value):
                self.assertIsNotNone(CHECKER['comment_error'](value))

    def test_chinese_comments_can_retain_contract_codes(self):
        self.assertIsNone(CHECKER['comment_error']('退款状态：PENDING待审批，SUCCESS成功，FAILED失败'))

    def test_sql_line_comments_do_not_replace_database_comments(self):
        self.assertEqual(len(self.check("CREATE TABLE sample(\n -- 主键\n id INT\n) COMMENT='业务表';")), 1)

    def test_defaults_enum_quotes_generated_columns_and_checks_keep_boundaries(self):
        sql = """CREATE TABLE `sample` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '物理主键',
            `state` ENUM('A,B', 'C') NOT NULL DEFAULT 'A,B' COMMENT '业务状态，保留原始枚举',
            `active_id` BIGINT GENERATED ALWAYS AS (CASE WHEN state='C' THEN id ELSE NULL END) VIRTUAL COMMENT '活动记录唯一占位',
            `note` VARCHAR(60) DEFAULT 'COMMENT fake' COMMENT '操作说明；允许逗号,及引号''内容',
            PRIMARY KEY(id), CHECK(id>0)
        ) ENGINE=InnoDB COMMENT='业务审计表';"""
        self.assertEqual(self.check(sql), [])

    def test_add_modify_and_change_require_column_comments(self):
        for operation in ['ADD COLUMN value INT', 'MODIFY COLUMN value INT', 'CHANGE old_value value INT']:
            self.assertEqual(len(self.check('ALTER TABLE sample '+operation+';')), 1)
            self.assertEqual(self.check('ALTER TABLE sample '+operation+" COMMENT '业务数值';"), [])

    def test_index_and_default_operations_are_not_columns(self):
        self.assertEqual(self.check('ALTER TABLE sample ADD INDEX idx_value(value), ALTER COLUMN value SET DEFAULT 1;'), [])

    def test_like_inherits_source_definition_but_ctas_needs_table_comment(self):
        self.assertEqual(self.check('CREATE TABLE sample_copy LIKE sample;'), [])
        self.assertEqual(len(self.check('CREATE TABLE sample_copy AS SELECT * FROM sample;')), 1)

    def test_complete_dynamic_ddl_and_concat_are_checked(self):
        self.assertEqual(len(self.check("SET @sql='ALTER TABLE sample ADD COLUMN value INT';")), 1)
        self.assertEqual(len(self.check("SET @sql=CONCAT('ALTER TABLE `', @table_name, '` ADD value INT');")), 1)
        self.assertEqual(self.check("SET @sql=CONCAT('ALTER TABLE `', @table_name, '` ADD value INT COMMENT ''业务数值''');"), [])
        self.assertEqual(len(self.check("SET @sql=CONCAT('ALTER', ' TABLE sample ADD value INT');")), 1)

    def test_procedure_arguments_resolve_only_their_own_call_sites(self):
        sql = """DELIMITER $$
CREATE PROCEDURE add_field(IN target_table VARCHAR(64), IN field_definition TEXT)
BEGIN
 SET @sql=CONCAT('ALTER TABLE `',target_table,'` ADD ',field_definition);
END$$
DELIMITER ;
CALL add_field('sample','value INT COMMENT ''业务数值''');
"""
        self.assertEqual(self.check(sql), [])
        self.assertEqual(len(self.check(sql.replace(" COMMENT ''业务数值''", ''))), 1)

    def test_invalid_identifiers_and_placeholder_comments_fail(self):
        self.assertEqual(len(self.check("CREATE TABLE `乱码表` (`乱码字段` INT COMMENT '字段') COMMENT='表';")), 4)

    def test_metadata_empty_english_and_corrupt_fields_are_detected(self):
        self.assertTrue(CHECKER['inspect_metadata']([], []))
        errors = CHECKER['inspect_metadata']([{'table': 'sample', 'comment': '业务表'}], [
            {'table': 'sample', 'column': 'first', 'comment': ''},
            {'table': 'sample', 'column': 'second', 'comment': 'Amount'},
            {'table': 'sample', 'column': 'third', 'comment': '中文ã€'}])
        self.assertEqual(len(errors), 3)

    def test_damaged_ddl_is_not_silently_accepted(self):
        with self.assertRaises(ValueError):
            self.check("CREATE TABLE sample(id INT COMMENT '未闭合);")

    def test_read_only_auditor_rejects_schema_injection_without_connecting(self):
        with patch('subprocess.run') as execute:
            with self.assertRaises(ValueError):
                AUDITOR['collect']("payment_acquiring'; DROP DATABASE other;--")
            execute.assert_not_called()


if __name__ == '__main__':
    unittest.main()
