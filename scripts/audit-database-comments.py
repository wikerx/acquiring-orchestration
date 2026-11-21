#!/usr/bin/env python3
"""只读检查指定 MySQL 库的中文表、字段注释，不执行 DDL 或读取业务记录。"""

from __future__ import annotations

import argparse
import json
import re
import runpy
import subprocess
from pathlib import Path


def collect(schema: str, container: str | None = None) -> tuple[list[dict], list[dict]]:
    if not re.fullmatch(r'[A-Za-z0-9_]+', schema):
        raise ValueError('数据库名只能包含英文、数字、下划线')
    command = ['mysql', '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names']
    if container:
        command = ['docker', 'exec', '-i', container, 'sh', '-c',
                   'MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?容器未配置数据库登录凭据}" exec mysql '
                   '--default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names']
    queries = [
        "SELECT JSON_OBJECT('table',TABLE_NAME,'comment',TABLE_COMMENT) FROM information_schema.TABLES "
        f"WHERE TABLE_SCHEMA='{schema}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;",
        "SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'comment',COLUMN_COMMENT) "
        f"FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{schema}' ORDER BY TABLE_NAME,ORDINAL_POSITION;"
    ]
    results = []
    for query in queries:
        process = subprocess.run(command, input=query, text=True, encoding='utf8', capture_output=True, check=True)
        results.append([json.loads(line) for line in process.stdout.splitlines() if line.strip()])
    if not results[0] or not results[1]:
        raise ValueError('目标数据库不存在、没有表或当前账号不可见，不能按零问题通过')
    return results[0], results[1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--schema', default='payment_acquiring')
    parser.add_argument('--container', help='可选的本地 MySQL Docker 容器；否则使用本机 mysql 客户端既有登录配置')
    parser.add_argument('--output-dir', type=Path, help='可选的元数据证据目录，只输出注释，不导出业务记录')
    args = parser.parse_args()
    try:
        tables, columns = collect(args.schema, args.container)
        checker = runpy.run_path(str(Path(__file__).with_name('verify-database-comments.py')))
        errors = checker['inspect_metadata'](tables, columns)
        if args.output_dir:
            args.output_dir.mkdir(parents=True, exist_ok=True)
            for name, rows in [('tables', tables), ('columns', columns)]:
                (args.output_dir / f'{name}.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf8')
        for error in errors:
            print(error)
        print(f'数据库 {args.schema}：{len(tables)} 张表、{len(columns)} 个字段，中文注释问题 {len(errors)} 个')
        return 1 if errors else 0
    except (ValueError, OSError, subprocess.CalledProcessError) as exception:
        # 不回显连接命令、凭据或异常中的潜在服务端连接细节。
        print(f'数据库注释审计未完成：{type(exception).__name__}，请检查本地连接和目标库权限')
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
