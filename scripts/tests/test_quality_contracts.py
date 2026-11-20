"""检查器必须保留契约约束，同时接受规范明确允许的简洁注释。"""

import runpy
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1]
COMMENTS = runpy.run_path(str(SCRIPTS / 'verify-java-comments.py'))
LOGGING = runpy.run_path(str(SCRIPTS / 'verify-logging-rules.py'))


class JavaCommentContractTests(unittest.TestCase):
    def scan(self, source, relative='sample/src/main/java/example/internal/Lookup.java'):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / relative
            path.parent.mkdir(parents=True)
            path.write_text(source, encoding='utf-8')
            return COMMENTS['scan_file'](path)

    def test_meaningful_single_line_contract_is_allowed_outside_strict_modules(self):
        self.assertEqual(self.scan('''/** 读取已经授权的数据摘要，不修改来源事实。 */
public interface Lookup {
    /** 按业务标识读取摘要，不存在时返回空。 */
    String find(String id);
}
'''), [])

    def test_data_and_risk_keep_their_multiline_requirement(self):
        for module in ('service-data', 'service-risk'):
            with self.subTest(module=module):
                findings = self.scan('/** 读取已授权摘要。 */\npublic interface Lookup {}\n',
                                     module + '/src/main/java/example/Lookup.java')
                self.assertIn('one-line-javadoc', findings)

    def test_self_explanatory_package_private_internal_type_needs_no_filler(self):
        self.assertEqual(self.scan('class Lookup {\n}\n'), [])

    def test_public_contract_type_and_method_still_require_javadoc(self):
        findings = self.scan('public interface Lookup {\n    String find(String id);\n}\n')
        self.assertTrue(any(x.startswith('missing_type_javadoc:Lookup:') for x in findings))
        self.assertTrue(any(x.startswith('missing_method_javadoc:find:') for x in findings))

    def test_plain_internal_read_delegate_does_not_repeat_the_target_contract(self):
        self.assertEqual(self.scan('''/** 复用来源查询契约，不新增过滤或转换。 */
public class Lookup {
    public String find(String id) {
        return source.find(id);
    }
}
'''), [])

    def test_controller_delegate_remains_a_public_contract(self):
        findings = self.scan('''/** 受权限控制的查询入口。 */
public class Lookup {
    public String find(String id) {
        return source.find(id);
    }
}
''', 'service-admin/src/main/java/example/api/Lookup.java')
        self.assertTrue(any(x.startswith('missing_method_javadoc:find:') for x in findings))

    def test_annotated_or_transformed_delegate_is_not_exempt(self):
        for annotation, argument in (('@RequiresPermission("read")\n', 'id'), ('', 'id.trim()')):
            with self.subTest(annotation=annotation, argument=argument):
                findings = self.scan('''/** 查询边界。 */
public class Lookup {
    ''' + annotation + '''    public String find(String id) {
        return source.find(''' + argument + ''');
    }
}
''')
                self.assertTrue(any(x.startswith('missing_method_javadoc:find:') for x in findings))

    def test_side_effect_and_core_delegate_are_not_exempt(self):
        for method, path in (
                ('save', 'sample/src/main/java/example/internal/Lookup.java'),
                ('find', 'service-payment/src/main/java/example/service/Lookup.java')):
            with self.subTest(method=method):
                findings = self.scan('''/** 保持来源调用边界。 */
public class Lookup {
    public String ''' + method + '''(String id) {
        return source.''' + method + '''(id);
    }
}
''', path)
                self.assertTrue(any(x.startswith('missing_method_javadoc:' + method + ':') for x in findings))

    def test_multiline_annotation_cannot_hide_a_delegate_contract(self):
        findings = self.scan('''/** 读取来源摘要。 */
public class Lookup {
    @RequiresPermission(
        value = "read",
        scope = "merchant",
        audit = true
    )
    public String find(String id) {
        return source.find(id);
    }
}
''')
        self.assertTrue(any(x.startswith('missing_method_javadoc:find:') for x in findings))

    def test_private_money_method_still_requires_contract(self):
        findings = self.scan('''/** 金额单位转换。 */
public class Lookup {
    private long toMinorAmount(String amount) {
        return 1;
    }
}
''', 'service-payment/src/main/java/example/service/Lookup.java')
        self.assertTrue(any(x.startswith('missing_method_javadoc:toMinorAmount:') for x in findings))

    def test_empty_or_boilerplate_javadoc_does_not_satisfy_contract(self):
        self.assertIn('empty-javadoc', self.scan('/** */\npublic class Lookup {}\n'))
        self.assertTrue(any(x.startswith('template:') for x in self.scan(
            '/** 这是一个查询类 */\npublic class Lookup {}\n')))

    def test_annotation_and_multiline_signature_keep_their_javadoc(self):
        self.assertEqual(self.scan('''/** 分页读取可见记录，不修改来源事实。 */
public interface Lookup {
    /** 按当前授权条件分页读取，返回范围不超过请求上限。 */
    @Select("""
        SELECT id FROM sample WHERE id = #{id}
        """)
    String find(
        @Param("id") String id);
}
'''), [])

    def test_duplicate_and_misplaced_comments_remain_failures(self):
        findings = self.scan('''/** 读取来源摘要。 */
public interface Lookup {
    /** 已有契约。 */
    @Override
    /** 重复契约。 */
    String find(String id);
}
''')
        self.assertIn('duplicate-javadoc-across-annotations', findings)
        self.assertIn('javadoc-after-annotations', findings)

    def test_inline_annotated_declaration_does_not_capture_next_members_comment(self):
        self.assertEqual(self.scan('''/** 读取来源摘要。 */
public class Lookup {
    /** 返回固定任务类型。 */
    @Override public String taskType() { return "LOOKUP"; }
    /** 读取已经授权的记录。 */
    public String find(String id) { return id; }
}
'''), [])

    def test_annotation_span_ignores_quoted_parentheses_and_text_blocks(self):
        source = ['@Select("""\n', '  SELECT ")" FROM sample\n', '  """) String find();\n']
        self.assertEqual(COMMENTS['annotation_block_span'](source, 0), (2, 6))
        self.assertFalse(COMMENTS['is_annotation_only'](source, 0))
        self.assertTrue(COMMENTS['is_annotation_only'](['@One @Two("(")\n'], 0))
        self.assertTrue(COMMENTS['is_annotation_only'](['@One // 兼容说明\n'], 0))
        consecutive = ['@One @Two(\n', '  value = ")"\n', ') String find();\n']
        self.assertEqual(COMMENTS['annotation_block_end'](consecutive, 0), 2)
        self.assertFalse(COMMENTS['is_annotation_only'](consecutive, 0))


class LoggingContractTests(unittest.TestCase):
    def test_existing_event_code_field_keeps_its_monitoring_contract(self):
        for field in ('event', 'eventCode'):
            with self.subTest(field=field):
                self.assertEqual(LOGGING['log_format_findings'](
                    'log.warn("文件安全检查失败, ' + field + ': UPLOAD_SCAN_REJECTED");'), [])

    def test_event_marker_must_be_a_real_nonempty_field(self):
        for text in ('前缀someevent: REJECTED', 'event: ', 'eventCode: , fileCount: {}'):
            with self.subTest(text=text):
                self.assertIn('log format lacks an event field', LOGGING['log_format_findings'](
                    'log.warn("检查失败, ' + text + '");'))

    def test_chinese_description_and_parameterized_format_remain_required(self):
        self.assertIn('log format lacks a Chinese event description',
                      LOGGING['log_format_findings']('log.warn("cleanup failed, event: CLEANUP_FAILED");'))
        self.assertIn('log format uses string concatenation', LOGGING['log_format_findings'](
            'log.warn("清理失败, event: CLEANUP_FAILED, id: " + id);'))
        self.assertEqual(LOGGING['unsafe_log_token']('log.info("值, event: UNSAFE, cardNo: {}", cardNo);'), 'cardNo')


if __name__ == '__main__':
    unittest.main()
