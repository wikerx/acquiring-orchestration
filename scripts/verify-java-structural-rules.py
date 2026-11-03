#!/usr/bin/env python3
"""Verify structural Java rules that should fail before compilation or review."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


INJECTION_ANNOTATION_RE = re.compile(r"^\s*@(Autowired|Resource|Inject)\b")
ASSERT_STATEMENT_RE = re.compile(r"^\s*assert\s+", re.MULTILINE)
FORBIDDEN_OUTPUT_RE = re.compile(r"System\.(?:out|err)\.")
PRINT_STACK_TRACE_RE = re.compile(r"\.printStackTrace\s*\(")


def iter_production_java_files(root: Path):
    for path in root.rglob("*.java"):
        normalized = str(path).replace("\\", "/")
        if "/src/main/java/" in f"/{normalized}" and "target" not in path.parts:
            yield path


def annotation_block_end(lines: list[str], index: int) -> int:
    """Return the last line occupied by an annotation."""
    depth = 0
    saw_parenthesis = False
    for cursor in range(index, min(len(lines), index + 100)):
        depth += lines[cursor].count("(") - lines[cursor].count(")")
        saw_parenthesis = saw_parenthesis or "(" in lines[cursor]
        if not saw_parenthesis or depth <= 0:
            return cursor
    return index


def next_declaration(lines: list[str], start_index: int) -> str:
    """Collect the declaration following one or more annotations."""
    declaration: list[str] = []
    cursor = start_index
    while cursor < len(lines):
        stripped = lines[cursor].strip()
        if not declaration and stripped.startswith("@"):
            cursor = annotation_block_end(lines, cursor) + 1
            continue
        if not declaration and (not stripped or stripped.startswith(("//", "/*", "*"))):
            cursor += 1
            continue
        declaration.append(stripped)
        if ";" in lines[cursor] or "{" in lines[cursor]:
            break
        cursor += 1
    return " ".join(declaration)


def is_field_declaration(declaration: str) -> bool:
    if ";" not in declaration:
        return False
    prefix = declaration.split("=", 1)[0]
    return "(" not in prefix


def scan_file(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    findings: list[str] = []
    lines = text.splitlines()
    for index, line in enumerate(lines):
        match = INJECTION_ANNOTATION_RE.match(line)
        if not match:
            continue
        declaration = next_declaration(lines, index + 1)
        if is_field_declaration(declaration):
            findings.append(f"field-injection:{match.group(1)}:{index + 1}")
    if ASSERT_STATEMENT_RE.search(text):
        findings.append("production-assert-statement")
    if FORBIDDEN_OUTPUT_RE.search(text):
        findings.append("system-output")
    if PRINT_STACK_TRACE_RE.search(text):
        findings.append("print-stack-trace")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Java structural governance rules.")
    parser.add_argument("--root", default=".", help="Project root")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    files = list(iter_production_java_files(root))
    findings: list[tuple[Path, list[str]]] = []
    for path in files:
        file_findings = scan_file(path)
        if file_findings:
            findings.append((path, file_findings))

    print(f"checked_production_java_files={len(files)}")
    print(f"structural_findings={sum(len(item[1]) for item in findings)}")
    for path, file_findings in findings[:100]:
        print(f"{path.relative_to(root)}: {', '.join(file_findings)}")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
