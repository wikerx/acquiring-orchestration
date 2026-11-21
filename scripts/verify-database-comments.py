#!/usr/bin/env python3
"""校验 MySQL 表、字段的中文注释；可检查 SQL 文件或 information_schema 导出的元数据。"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HAN = re.compile(r"[\u3400-\u9fff]")
MOJIBAKE = re.compile(r"[\u0080-\u009f\ufffdÃÂâãäåæçèé]|锟斤拷|烫烫烫|\?{3,}")
IDENTIFIER = re.compile(r"[a-z_][a-z0-9_]*\Z")
CONSTRAINTS = {"PRIMARY", "UNIQUE", "KEY", "INDEX", "CONSTRAINT", "FOREIGN", "CHECK", "FULLTEXT", "SPATIAL"}


@dataclass(frozen=True)
class Token:
    value: str
    kind: str
    start: int
    end: int

    @property
    def upper(self) -> str:
        return self.value.upper() if self.kind == "word" else self.value


@dataclass(frozen=True)
class Definition:
    kind: str
    table: str
    column: str | None
    start: int
    end: int
    comment: Token | None
    insert_at: int


@dataclass(frozen=True)
class DynamicSql:
    sql: str
    # 展开区间到原字符串 token 的映射；变量只用标识符占位，不推测运行时字段定义。
    pieces: list[tuple[int, int, Token | None]]
    start: int


def tokens(sql: str, stop_after_statement: bool = False) -> list[Token]:
    """保留字符串及标识符边界，避免将枚举逗号、SQL 行注释当成字段定义。"""
    result = []
    i = 0
    while i < len(sql):
        if sql[i].isspace() or sql[i] == '\ufeff':
            i += 1
            continue
        if sql[i] == '#' or sql.startswith('--', i) and (i + 2 == len(sql) or sql[i + 2].isspace()):
            end = sql.find('\n', i)
            i = len(sql) if end < 0 else end + 1
            continue
        if sql.startswith('/*', i):
            end = sql.find('*/', i + 2)
            if end < 0:
                raise ValueError('SQL 块注释未闭合')
            # 版本注释中的 DDL 是可执行 SQL，不能直接忽略。
            if sql.startswith('/*!', i):
                begin = i + 3
                while begin < end and sql[begin].isdigit():
                    begin += 1
                result.extend(Token(t.value, t.kind, t.start + begin, t.end + begin) for t in tokens(sql[begin:end]))
            i = end + 2
            continue
        start = i
        if sql[i] in "'\"`":
            quote = sql[i]
            i += 1
            value = []
            while i < len(sql):
                if sql[i] == quote:
                    if i + 1 < len(sql) and sql[i + 1] == quote:
                        value.append(quote)
                        i += 2
                    else:
                        i += 1
                        break
                elif sql[i] == '\\' and quote != '`' and i + 1 < len(sql):
                    i += 1
                    value.append({'n': '\n', 'r': '\r', 't': '\t', '0': '\0'}.get(sql[i], sql[i]))
                    i += 1
                else:
                    value.append(sql[i])
                    i += 1
            else:
                raise ValueError(f'SQL 引号未闭合，起始行 {sql.count(chr(10), 0, start) + 1}')
            result.append(Token(''.join(value), 'identifier' if quote == '`' else 'string', start, i))
        elif sql[i].isalnum() or sql[i] in '_$':
            i += 1
            while i < len(sql) and (sql[i].isalnum() or sql[i] in '_$'):
                i += 1
            result.append(Token(sql[start:i], 'word', start, i))
        else:
            i += 1
            result.append(Token(sql[start:i], 'symbol', start, i))
            if stop_after_statement and sql[start:i] == ';':
                break
    return result


def ddl_tokens(sql: str) -> list[Token]:
    try:
        return tokens(sql)
    except ValueError:
        # 部分历史数据快照末尾 DML 不完整。仍检查其中每条完整 DDL，不能为此跳过表结构；
        # 业务数据正文和 DML 修复不属于注释校验的职责。DDL 自身引号损坏仍会失败。
        result = []
        for match in re.finditer(r'(?im)^\s*(?:CREATE\s+(?:TEMPORARY\s+)?TABLE|ALTER\s+TABLE)\b', sql):
            offset = match.start()
            result.extend(Token(t.value, t.kind, t.start + offset, t.end + offset)
                          for t in tokens(sql[offset:], stop_after_statement=True))
        if not result:
            raise
        return result


def closing(items: list[Token], index: int) -> int:
    depth = 0
    for cursor in range(index, len(items)):
        if items[cursor].kind == 'symbol':
            if items[cursor].value == '(':
                depth += 1
            elif items[cursor].value == ')':
                depth -= 1
                if depth == 0:
                    return cursor
    raise ValueError('SQL 表或字段括号未闭合')


def groups(items: list[Token]) -> list[list[Token]]:
    result, current, depth = [], [], 0
    for item in items:
        if item.kind == 'symbol':
            if item.value == '(':
                depth += 1
            elif item.value == ')':
                depth -= 1
            elif item.value == ',' and depth == 0:
                if current:
                    result.append(current)
                current = []
                continue
        current.append(item)
    if current:
        result.append(current)
    return result


def comment_token(items: list[Token]) -> Token | None:
    for i, item in enumerate(items):
        if item.upper == 'COMMENT' and item.kind == 'word':
            j = i + 1
            if j < len(items) and items[j].value == '=':
                j += 1
            if j < len(items) and items[j].kind == 'string':
                return items[j]
    return None


def column_definition(table: str, items: list[Token]) -> Definition | None:
    if not items or items[0].upper in CONSTRAINTS or items[0].kind not in {'word', 'identifier'}:
        return None
    if items[0].value == 'generated_identifier' and len(items) > 1 and items[1].upper in CONSTRAINTS:
        return None
    insert_at = items[-1].end
    # MODIFY 的位置子句不属于字段类型，COMMENT 必须写在它前面。
    for item in items[1:]:
        if item.kind == 'word' and item.upper in {'FIRST', 'AFTER'}:
            insert_at = item.start
            break
    return Definition('column', table, items[0].value, items[0].start, items[-1].end, comment_token(items[1:]), insert_at)


def definitions(sql: str) -> list[Definition]:
    items = ddl_tokens(sql)
    result = []
    i = 0
    while i < len(items):
        command = items[i].upper
        if command not in {'CREATE', 'ALTER'} or items[i].kind != 'word':
            i += 1
            continue
        j = i + 1
        if j < len(items) and items[j].upper == 'TEMPORARY':
            j += 1
        if j >= len(items) or items[j].upper != 'TABLE':
            i += 1
            continue
        j += 1
        if j < len(items) and items[j].upper == 'IF':
            j += 3
        if j >= len(items):
            raise ValueError('SQL 表名缺失')
        table = items[j].value
        table_start = items[j].start
        j += 1
        if j + 1 < len(items) and items[j].value == '.':
            table = items[j + 1].value
            table_start = items[j + 1].start
            j += 2
        if command == 'CREATE' and j < len(items) and items[j].value == '(':
            end = closing(items, j)
            for group in groups(items[j + 1:end]):
                definition = column_definition(table, group)
                if definition:
                    result.append(definition)
            stop = end + 1
            while stop < len(items) and items[stop].value != ';':
                stop += 1
            option_end = next((k for k in range(end + 1, stop)
                               if items[k].kind == 'word' and items[k].upper in {'AS', 'SELECT'}), stop)
            options = items[end + 1:option_end]
            result.append(Definition('table', table, None, table_start, items[stop - 1].end,
                                     comment_token(options), items[option_end].start if option_end < stop else items[stop - 1].end))
            i = stop
        elif command == 'CREATE':
            # LIKE 继承字段、表注释；运行库仍须全量后检，CTAS 不允许默默丢失注释。
            if j < len(items) and items[j].upper == 'LIKE':
                i = j + 1
                continue
            stop = j
            while stop < len(items) and items[stop].value != ';':
                stop += 1
            option_end = next((k for k in range(j, stop)
                               if items[k].kind == 'word' and items[k].upper in {'AS', 'SELECT'}), stop)
            result.append(Definition('table', table, None, table_start, items[stop - 1].end,
                                     comment_token(items[j:option_end]), items[option_end].start if option_end < stop else items[stop - 1].end))
            i = stop
        else:
            stop = j
            while stop < len(items) and items[stop].value != ';':
                stop += 1
            for group in groups(items[j:stop]):
                if not group:
                    continue
                if group[0].upper in {'ADD', 'MODIFY', 'CHANGE'}:
                    k = 1
                    if k < len(group) and group[k].upper == 'COLUMN':
                        k += 1
                    if k < len(group) and group[k].upper == 'IF':
                        k += 3
                    if group[0].upper == 'CHANGE':
                        k += 1
                    if k < len(group) and group[k].value == '(':
                        for nested in groups(group[k + 1:closing(group, k)]):
                            definition = column_definition(table, nested)
                            if definition:
                                result.append(definition)
                    else:
                        definition = column_definition(table, group[k:])
                        if definition:
                            result.append(definition)
                elif group[0].upper == 'COMMENT':
                    result.append(Definition('table', table, None, table_start, group[-1].end,
                                             comment_token(group), group[-1].end))
            i = stop
    return result


def comment_error(value: str | None) -> str | None:
    if not value or not value.strip():
        return '缺少注释'
    if MOJIBAKE.search(value):
        return '注释含乱码或替换字符'
    try:
        restored = value.encode('gb18030').decode('utf8')
        if restored != value and HAN.search(restored):
            return '注释疑似包含中文编码误解码产生的乱码'
    except UnicodeError:
        pass
    if not HAN.search(value):
        return '注释缺少中文业务说明'
    if value.strip() in {'表', '字段', '注释', '中文', '说明', '待补充', '测试'}:
        return '注释没有具体业务含义'
    return None


def dynamic_sql(sql: str) -> list[DynamicSql]:
    """检查 PREPARE 常用的完整字面量及 CONCAT，不执行动态 SQL。"""
    items = ddl_tokens(sql)
    result = []
    covered = set()
    procedures = {}
    procedure_ranges = []
    for i, item in enumerate(items[:-3]):
        if item.upper == 'CREATE' and items[i + 1].upper == 'PROCEDURE' and items[i + 3].value == '(':
            end = closing(items, i + 3)
            parameters = []
            for group in groups(items[i + 4:end]):
                offset = 1 if group[0].upper in {'IN', 'OUT', 'INOUT'} else 0
                parameters.append(group[offset].value)
            procedures[items[i + 2].value] = parameters
            boundary = re.search(r'(?m)^\s*END[ \t]*(?:\$\$|//)', sql[item.end:])
            if boundary:
                procedure_ranges.append((item.start, item.end + boundary.end(), items[i + 2].value))
    contexts = []
    for i, item in enumerate(items[:-2]):
        if item.upper == 'CALL' and items[i + 1].value in procedures and items[i + 2].value == '(':
            end = closing(items, i + 2)
            arguments = groups(items[i + 3:end])
            contexts.append((items[i + 1].value,
                             {name: group[0] for name, group in zip(procedures[items[i + 1].value], arguments)
                              if len(group) == 1 and group[0].kind == 'string'}))
    for i, item in enumerate(items):
        if item.upper != 'CONCAT' or item.kind != 'word' or i + 1 >= len(items) or items[i + 1].value != '(':
            continue
        end = closing(items, i + 1)
        arguments = groups(items[i + 2:end])
        variables = {arg[0].value for arg in arguments if len(arg) == 1 and arg[0].kind in {'word', 'identifier'}}
        owner = next((name for start, stop, name in procedure_ranges if start <= item.start < stop), None)
        matching = [context for name, context in contexts if owner == name and variables.intersection(context)] or [{}]
        for context in matching:
            expanded = ''
            pieces = []
            for argument in arguments:
                literal = argument[0] if len(argument) == 1 and argument[0].kind == 'string' else None
                if len(argument) == 1 and argument[0].value in context:
                    literal = context[argument[0].value]
                text = literal.value if literal else 'generated_identifier '
                if len(argument) == 4 and argument[0].upper == 'QUOTE' and argument[1].value == '(':
                    value = argument[2]
                    if value.kind == 'string':
                        text = "'" + value.value.replace("'", "''") + "'"
                    elif re.search(r'IF\s+' + re.escape(value.value) + r"\s+IS\s+NULL\s+OR\s+" + re.escape(value.value)
                                   + r"\s+NOT\s+REGEXP\s+'\[一-龥\]'", sql, re.IGNORECASE):
                        text = "'运行时校验后继承的中文注释'"
                pieces.append((len(expanded), len(expanded) + len(text), literal))
                expanded += text
            if re.match(r'\s*(?:CREATE\s+(?:TEMPORARY\s+)?TABLE|ALTER\s+TABLE)\b', expanded, re.IGNORECASE):
                result.append(DynamicSql(expanded, pieces, item.start))
                covered.update(t.start for t in items[i + 2:end] if t.kind == 'string')
    for item in items:
        if item.kind == 'string' and item.start not in covered and re.match(
                r'\s*(?:CREATE\s+(?:TEMPORARY\s+)?TABLE|ALTER\s+TABLE)\b', item.value, re.IGNORECASE):
            result.append(DynamicSql(item.value, [(0, len(item.value), item)], item.start))
    return result


def inspect_sql(sql: str, source: str = '<sql>') -> list[str]:
    # 纯数据脚本不定义结构，其业务正文不属于本校验器的 SQL 解析和改写范围。
    if not re.search(r'\b(?:CREATE\s+(?:TEMPORARY\s+)?TABLE|ALTER\s+TABLE|CONCAT|PREPARE)\b', sql, re.IGNORECASE):
        return []
    errors = []
    for definition in definitions(sql):
        name = definition.column or definition.table
        line = sql.count('\n', 0, definition.start) + 1
        location = f'{source}:{line}: {definition.table}' + (f'.{definition.column}' if definition.column else '')
        if not IDENTIFIER.fullmatch(name):
            errors.append(f'{location}: 标识符须使用小写英文、数字、下划线，禁止乱码')
        error = comment_error(definition.comment.value if definition.comment else None)
        if error:
            errors.append(f'{location}: {error}')
    for dynamic in dynamic_sql(sql):
        line = sql.count('\n', 0, dynamic.start) + 1
        try:
            for definition in definitions(dynamic.sql):
                error = comment_error(definition.comment.value if definition.comment else None)
                if error:
                    errors.append(f'{source}:{line}: 动态 DDL {definition.table}.{definition.column or "表"}: {error}')
        except ValueError as exception:
            errors.append(f'{source}:{line}: 动态 DDL 必须先展开后校验：{exception}')
    return errors


def inspect_metadata(tables: list[dict], columns: list[dict]) -> list[str]:
    if not tables or not columns:
        return ['元数据为空或目标库不可见，不能按零问题验收通过']
    errors = []
    for row in tables + columns:
        name = row.get('column', row['table'])
        location = row['table'] + ('.' + row['column'] if 'column' in row else '')
        if not IDENTIFIER.fullmatch(name):
            errors.append(f'{location}: 标识符须使用小写英文、数字、下划线，禁止乱码')
        error = comment_error(row.get('comment'))
        if error:
            errors.append(f'{location}: {error}')
    return errors


def sql_paths(root: Path) -> list[Path]:
    paths = list((root / 'docs/sql').rglob('*.sql'))
    for service in root.glob('service-*'):
        paths.extend((service / 'src/main/resources').rglob('*.sql'))
    return sorted(paths)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('paths', nargs='*', type=Path)
    parser.add_argument('--metadata-dir', type=Path, help='读取 tables.json、columns.json；不连接数据库或执行 DDL')
    args = parser.parse_args()
    errors = []
    if args.metadata_dir:
        tables = json.loads((args.metadata_dir / 'tables.json').read_text(encoding='utf8'))
        columns = json.loads((args.metadata_dir / 'columns.json').read_text(encoding='utf8'))
        errors.extend(inspect_metadata(tables, columns))
        count = len(tables) + len(columns)
    else:
        paths = args.paths or sql_paths(ROOT)
        count = len(paths)
        for path in paths:
            try:
                errors.extend(inspect_sql(path.read_text(encoding='utf8'), str(path)))
            except (UnicodeError, ValueError) as exception:
                errors.append(f'{path}: SQL 必须为有效 UTF-8 且语法边界完整：{exception}')
    for error in errors:
        print(error)
    print(f'数据库中文注释校验：检查 {count} 项，发现 {len(errors)} 个问题')
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
