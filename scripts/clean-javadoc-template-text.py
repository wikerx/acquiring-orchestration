#!/usr/bin/env python3
"""Remove forbidden generated wording from Java Javadocs."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


REPLACEMENTS = [
    (
        re.compile(r"整理([a-zA-Z0-9 ]+)，返回当前业务步骤需要的规范化结果。"),
        lambda match: f"规范化{match.group(1).strip()}，返回当前业务步骤需要的业务值。",
    ),
    (
        re.compile(r"整理([a-zA-Z0-9 ]+)，返回调用链后续步骤可直接使用的规范化结果。"),
        lambda match: f"规范化{match.group(1).strip()}，返回调用链后续步骤可直接使用的业务值。",
    ),
    (
        re.compile(r"该方法根据所属类职责执行必要的校验、转换、查询、写入或协作调用。"),
        "该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。",
    ),
    (
        re.compile(r"摘要摘要"),
        "摘要",
    ),
]


def iter_java_files(root: Path):
    for path in root.rglob("*.java"):
        if "target" not in path.parts:
            yield path


def clean_text(text: str) -> str:
    changed = text
    for pattern, replacement in REPLACEMENTS:
        changed = pattern.sub(replacement, changed)
    return changed


def main() -> int:
    changed_files = 0
    for path in iter_java_files(ROOT):
        original = path.read_text(encoding="utf-8")
        cleaned = clean_text(original)
        if cleaned != original:
            path.write_text(cleaned, encoding="utf-8")
            changed_files += 1
    print(f"cleaned_files={changed_files}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
