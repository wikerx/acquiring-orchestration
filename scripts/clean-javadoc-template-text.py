#!/usr/bin/env python3
"""Remove forbidden generated wording from Java Javadocs."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


REPLACEMENTS = [
    (
        re.compile(
            r"(@description\s*:\s*)(.*)，位于 [^，\n]+，"
            r"用于接口或跨层传递该业务数据，不承担状态写入职责。"
        ),
        lambda match: (
            f"{match.group(1)}{match.group(2)}，承载当前接口或跨层调用所需字段，"
            "不直接执行状态写入。"
        ),
    ),
    (
        re.compile(
            r"(@description\s*:\s*)(.*)，位于 [^，\n]+，"
            r"限定所属聚合内该对象的字段集合和传递边界。"
        ),
        lambda match: (
            f"{match.group(1)}{match.group(2)}，定义所属聚合内固定的字段集合和传递边界。"
        ),
    ),
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

# Dependency-injection constructors are self-explanatory. The old generator
# added long parameter lists that repeated field names and even guessed wrong
# business meanings; remove only blocks carrying the exact generated marker.
GENERATED_CONSTRUCTOR_JAVADOC = re.compile(
    r"(?ms)^[ \t]*/\*\*\n"
    r"(?:(?!^[ \t]*\*/$).)*?创建 .* 实例并校验构造参数。\n"
    r"(?:(?!^[ \t]*\*/$).)*?^[ \t]*\*/\n"
)

# This paragraph does not describe a real invariant. Keep the surrounding
# method-specific description, parameters and return contract.
GENERATED_LAYER_BOILERPLATE = re.compile(
    r"(?m)^[ \t]*\* <p>\n"
    r"^[ \t]*\* 实现必须保持 .* 已有权限、状态和异常语义。\n"
    r"^[ \t]*\* </p>\n"
)


def iter_java_files(root: Path):
    for path in root.rglob("*.java"):
        if "target" not in path.parts:
            yield path


def remove_duplicate_type_javadocs(text: str) -> str:
    """Keep the first of two adjacent class Javadocs for the same Java type."""
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    index = 0
    while index < len(lines):
        if "/**" not in lines[index]:
            out.append(lines[index])
            index += 1
            continue
        first_end = index
        while first_end < len(lines) and "*/" not in lines[first_end]:
            first_end += 1
        if first_end >= len(lines):
            out.extend(lines[index:])
            break
        first_block = "".join(lines[index:first_end + 1])
        first_name = re.search(r"@classname\s*:\s*(\w+)", first_block)
        second_start = first_end + 1
        while second_start < len(lines) and not lines[second_start].strip():
            second_start += 1
        if first_name and second_start < len(lines) and "/**" in lines[second_start]:
            second_end = second_start
            while second_end < len(lines) and "*/" not in lines[second_end]:
                second_end += 1
            second_block = "".join(lines[second_start:second_end + 1])
            second_name = re.search(r"@classname\s*:\s*(\w+)", second_block)
            if second_name and second_name.group(1) == first_name.group(1):
                out.extend(lines[index:first_end + 1])
                out.append("\n")
                index = second_end + 1
                continue
        out.extend(lines[index:first_end + 1])
        index = first_end + 1
    return "".join(out)


def annotation_block_end(lines: list[str], index: int) -> int:
    """Return the final line of a possibly multiline Java annotation."""
    depth = 0
    saw_parenthesis = False
    in_string = False
    in_text_block = False
    escaped = False
    for line_index in range(index, min(len(lines), index + 400)):
        line = lines[line_index]
        cursor = 0
        while cursor < len(line):
            if in_text_block:
                closing = line.find('"""', cursor)
                if closing < 0:
                    break
                in_text_block = False
                cursor = closing + 3
                continue
            if in_string:
                char = line[cursor]
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
                cursor += 1
                continue
            if line.startswith('"""', cursor):
                in_text_block = True
                cursor += 3
                continue
            char = line[cursor]
            if char == '"':
                in_string = True
            elif char == "(":
                depth += 1
                saw_parenthesis = True
            elif char == ")":
                depth -= 1
            cursor += 1
        if not saw_parenthesis or (depth <= 0 and not in_string and not in_text_block):
            return line_index
    return index


def line_indent(line: str) -> str:
    """Return the leading whitespace of one source line."""
    return line[:len(line) - len(line.lstrip())]


def move_javadocs_before_annotations(text: str) -> str:
    """Move declaration Javadocs in front of their annotation block.

    Only annotations using the same indentation as the following Javadoc are
    considered declaration annotations. This deliberately excludes annotated
    method parameters, whose indentation is deeper than the next member's
    Javadoc in Mapper interfaces.
    """
    lines = text.splitlines(keepends=True)
    index = 0
    while index < len(lines):
        if not lines[index].lstrip().startswith("@"):
            index += 1
            continue
        annotation_start = index
        declaration_indent = line_indent(lines[index])
        cursor = index
        while cursor < len(lines):
            stripped = lines[cursor].lstrip()
            if not stripped.startswith("@") or line_indent(lines[cursor]) != declaration_indent:
                break
            cursor = annotation_block_end(lines, cursor) + 1
            while cursor < len(lines) and not lines[cursor].strip():
                cursor += 1
        if (cursor >= len(lines)
                or not lines[cursor].lstrip().startswith("/**")
                or line_indent(lines[cursor]) != declaration_indent):
            index = max(index + 1, cursor)
            continue
        javadoc_start = cursor
        javadoc_end = javadoc_start
        while javadoc_end < len(lines) and "*/" not in lines[javadoc_end]:
            javadoc_end += 1
        if javadoc_end >= len(lines):
            break
        annotation_block = lines[annotation_start:javadoc_start]
        javadoc_block = lines[javadoc_start:javadoc_end + 1]
        lines[annotation_start:javadoc_end + 1] = javadoc_block + annotation_block
        index = javadoc_end + 1
    return "".join(lines)


def remove_duplicate_javadocs_across_annotations(text: str) -> str:
    """Keep the Javadoc before annotations when a second block documents the same declaration."""
    lines = text.splitlines(keepends=True)
    remove_lines: set[int] = set()
    index = 0
    while index < len(lines):
        if not lines[index].lstrip().startswith("/**"):
            index += 1
            continue
        first_end = index
        while first_end < len(lines) and "*/" not in lines[first_end]:
            first_end += 1
        cursor = first_end + 1
        while cursor < len(lines) and not lines[cursor].strip():
            cursor += 1
        saw_annotation = False
        while cursor < len(lines) and lines[cursor].lstrip().startswith("@"):
            saw_annotation = True
            cursor = annotation_block_end(lines, cursor) + 1
            while cursor < len(lines) and not lines[cursor].strip():
                cursor += 1
        if saw_annotation and cursor < len(lines) and lines[cursor].lstrip().startswith("/**"):
            second_end = cursor
            while second_end < len(lines) and "*/" not in lines[second_end]:
                second_end += 1
            remove_lines.update(range(cursor, min(second_end + 1, len(lines))))
            index = second_end + 1
            continue
        index = first_end + 1
    return "".join(line for line_index, line in enumerate(lines) if line_index not in remove_lines)


def clean_text(text: str) -> str:
    changed = remove_duplicate_type_javadocs(text)
    changed = remove_duplicate_javadocs_across_annotations(changed)
    changed = move_javadocs_before_annotations(changed)
    changed = GENERATED_CONSTRUCTOR_JAVADOC.sub("", changed)
    changed = GENERATED_LAYER_BOILERPLATE.sub("", changed)
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
