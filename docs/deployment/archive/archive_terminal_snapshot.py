#!/usr/bin/env python3
"""按自然月只读导出终态运维记录，生成可核对的私有归档快照。"""

import argparse
import datetime as dt
import gzip
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys
import tempfile


TABLE_RULES = {
    "sys_mq_outbox": ("create_time", "event_status = 'SENT'"),
    "settlement_event_outbox": ("create_time", "event_status = 'SENT'"),
    "settlement_projection_task": ("create_time", "task_status = 'COMPLETED'"),
    "sys_oper_log": ("created_at", "1 = 1"),
    "sys_job_run_log": (
        "create_time",
        "run_status IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED')",
    ),
}
IDENTIFIER = re.compile(r"[a-z][a-z0-9_]*\Z")
CONTAINER_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_.-]*\Z")


def month_bounds(month: str) -> tuple[str, str]:
    """校验 YYYYMM 并计算半开月份边界。"""
    if not re.fullmatch(r"\d{6}", month):
        raise ValueError("month must be YYYYMM")
    year, number = int(month[:4]), int(month[4:])
    start = dt.datetime(year, number, 1)
    next_year, next_month = (year + 1, 1) if number == 12 else (year, number + 1)
    end = dt.datetime(next_year, next_month, 1)
    return start.strftime("%Y-%m-%d %H:%M:%S"), end.strftime("%Y-%m-%d %H:%M:%S")


def archive_where(table: str, month: str, before: str) -> str:
    """只接受固定表和日期，避免把操作参数拼成任意 SQL。"""
    if table not in TABLE_RULES:
        raise ValueError("table is not in the archive allowlist")
    start, end = month_bounds(month)
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", before):
        raise ValueError("before must be YYYY-MM-DD")
    try:
        cutoff = dt.datetime.strptime(before, "%Y-%m-%d")
    except ValueError as error:
        raise ValueError("before must be YYYY-MM-DD") from error
    if cutoff <= dt.datetime.strptime(start, "%Y-%m-%d %H:%M:%S"):
        raise ValueError("before must be later than the month start")
    column, terminal = TABLE_RULES[table]
    return (f"{terminal} AND `{column}` >= '{start}' "
            f"AND `{column}` < '{end}' AND `{column}` < '{before} 00:00:00'")


def docker_mysql(container: str, client: str, *arguments: str) -> list[str]:
    """密码仅在容器进程内部取用，不经命令行参数或归档清单输出。"""
    if not CONTAINER_NAME.fullmatch(container):
        raise ValueError("invalid Docker container name")
    if client not in ("mysql", "mysqldump"):
        raise ValueError("invalid MySQL client")
    return ["docker", "exec", container, "sh", "-c",
            f'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec {client} "$@"',
            "archive", *arguments]


def source_count(container: str, schema: str, table: str, where: str) -> int:
    """只读计数，导出前后均执行以检测源集合变化。"""
    sql = f"SELECT COUNT(*) FROM `{table}` WHERE {where}"
    output = subprocess.check_output(
        docker_mysql(container, "mysql", "-uroot", "--batch", "--skip-column-names",
                     schema, "-e", sql), text=True, stderr=subprocess.PIPE)
    return int(output.strip())


def file_sha256(path: pathlib.Path) -> str:
    """流式计算归档文件摘要。"""
    digest = hashlib.sha256()
    with path.open("rb") as archive:
        for chunk in iter(lambda: archive.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def insert_count(path: pathlib.Path) -> int:
    """逐条 INSERT 用于发现截断或空文件。"""
    with gzip.open(path, "rb") as archive:
        return sum(1 for line in archive if line.startswith(b"INSERT INTO "))


def verify(manifest_path: pathlib.Path) -> None:
    """不连接数据库，复核文件摘要和记录数。"""
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    archive = manifest_path.parent / manifest["file"]
    if archive.name != manifest["file"] or not archive.is_file():
        raise ValueError("archive file is missing or invalid")
    if file_sha256(archive) != manifest["sha256"]:
        raise ValueError("archive SHA-256 mismatch")
    if insert_count(archive) != manifest["rowCount"]:
        raise ValueError("archive row count mismatch")
    print(f"verified: {archive.name}, rows={manifest['rowCount']}, sha256={manifest['sha256']}")


def export(args: argparse.Namespace) -> None:
    """只复制终态行到按月命名的快照文件，不更新或删除任何业务表。"""
    if not IDENTIFIER.fullmatch(args.schema):
        raise ValueError("invalid schema name")
    where = archive_where(args.table, args.month, args.before)
    before_count = source_count(args.container, args.schema, args.table, where)
    if args.dry_run:
        print(f"dry-run: table={args.table}, month={args.month}, eligibleRows={before_count}")
        return
    destination = pathlib.Path(args.output_dir).expanduser().resolve(strict=True)
    if not destination.is_dir() or destination.stat().st_mode & 0o077:
        raise ValueError("output directory must exist and have mode 0700")
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    stem = f"{args.table}_{args.month}_{stamp}"
    archive_path = destination / f"{stem}.sql.gz"
    manifest_path = destination / f"{stem}.manifest.json"
    if archive_path.exists() or manifest_path.exists():
        raise FileExistsError("archive name already exists")
    fd, temporary_name = tempfile.mkstemp(prefix=f".{stem}.", dir=destination)
    os.close(fd)
    temporary_path = pathlib.Path(temporary_name)
    temporary_manifest = None
    published = False
    try:
        command = docker_mysql(
            args.container, "mysqldump", "-uroot", "--single-transaction", "--quick",
            "--skip-lock-tables", "--no-create-info", "--skip-extended-insert",
            "--skip-add-locks", "--complete-insert", "--default-character-set=utf8mb4",
            "--set-gtid-purged=OFF", f"--where={where}", args.schema, args.table)
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        with temporary_path.open("wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb") as compressed:
                for chunk in iter(lambda: process.stdout.read(1024 * 1024), b""):
                    compressed.write(chunk)
            raw.flush()
            os.fsync(raw.fileno())
        error_output = process.stderr.read()
        if process.wait() != 0:
            raise RuntimeError("mysqldump failed: " + error_output.decode("utf-8", "replace")[:300])
        after_count = source_count(args.container, args.schema, args.table, where)
        actual_count = insert_count(temporary_path)
        if before_count != after_count or actual_count != before_count:
            raise RuntimeError("source or dump row count changed; archive was not published")
        digest = file_sha256(temporary_path)
        os.replace(temporary_path, archive_path)
        published = True
        manifest = {"schema": args.schema, "table": args.table, "month": args.month,
                    "before": args.before, "terminalFilter": TABLE_RULES[args.table][1],
                    "rowCount": actual_count, "file": archive_path.name,
                    "sha256": digest, "createdAtUtc": stamp}
        manifest_fd, manifest_name = tempfile.mkstemp(prefix=f".{stem}.manifest.", dir=destination)
        temporary_manifest = pathlib.Path(manifest_name)
        with os.fdopen(manifest_fd, "w", encoding="utf-8") as manifest_output:
            manifest_output.write(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
            manifest_output.flush()
            os.fsync(manifest_output.fileno())
        os.replace(temporary_manifest, manifest_path)
        verify(manifest_path)
    except BaseException:
        if published:
            archive_path.unlink(missing_ok=True)
            manifest_path.unlink(missing_ok=True)
        raise
    finally:
        temporary_path.unlink(missing_ok=True)
        if temporary_manifest is not None:
            temporary_manifest.unlink(missing_ok=True)


def main() -> None:
    """解析命令行并确保错误不会发布看似完整的快照。"""
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", type=pathlib.Path, help="verify an existing manifest only")
    parser.add_argument("--container", default="MySQL8")
    parser.add_argument("--schema", default="payment_acquiring")
    parser.add_argument("--table", choices=TABLE_RULES)
    parser.add_argument("--month")
    parser.add_argument("--before")
    parser.add_argument("--output-dir")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        if args.verify:
            verify(args.verify)
        else:
            if not all((args.table, args.month, args.before)) or (not args.dry_run and not args.output_dir):
                parser.error("--table, --month, --before and --output-dir are required for export")
            export(args)
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"archive failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error


if __name__ == "__main__":
    main()
