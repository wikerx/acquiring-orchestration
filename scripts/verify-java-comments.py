#!/usr/bin/env python3
"""Verify Java comment governance quality for acquiring-orchestration."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


TEMPLATE_PATTERNS = [
    re.compile(r"@description\s*:\s*(TODO|todo|描述|说明|暂无|待补充|.*业务逻辑处理.*)", re.IGNORECASE),
    re.compile(r"这是一个.*类"),
    re.compile(r"该方法用于处理.*"),
    re.compile(r"返回结果对象。?$"),
    re.compile(r"设置.*属性。?$"),
    re.compile(r"获取.*属性。?$"),
    re.compile(r"@description\s*:\s*(?:工具类|业务类|.*处理相关业务|.*承载业务职责|.*负责数据流转|.*执行相关处理|.*保持职责边界|.*页面展示数据|.*请求参数或业务处理上下文)"),
    re.compile(r"@param\s+\w+\s+请求参数或业务处理上下文"),
    re.compile(r"@return\s+处理后的业务结果或页面展示数据"),
    re.compile(r"执行\s+.*内部步骤，为当前类的公开能力提供参数校验、对象映射或状态计算。"),
    re.compile(r"前置条件、幂等规则、事务边界和外部系统调用由实现类、注解配置或调用方契约共同约束"),
    re.compile(r"@param\s+\w+\s+.*参数，来源于调用方输入、路径变量、请求体、配置或依赖注入"),
    re.compile(r"@return\s+方法执行后的领域对象、集合数据、统一响应或远程调用结果"),
    re.compile(r"所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果"),
    re.compile(r"涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束"),
    re.compile(r"@param\s+\w+\s+.*对象，携带当前业务动作的输入字段"),
    re.compile(r"完成\s+.*分支的校验或转换，返回值供当前调用链继续组装结果。"),
    re.compile(r"字段，表示当前模型"),
    re.compile(r"当前模型在所属业务流程中的对应属性"),
    re.compile(r"由上游接口、数据库字段或枚举定义约束"),
    re.compile(r"接口请求、数据库记录、配置文件或上游服务返回"),
    re.compile(r"与同对象字段共同组成当前业务语义"),
    re.compile(r"层级边界："),
    re.compile(r"状态变更、事务提交、MQ 投递"),
    re.compile(r"方法签名声明的返回值"),
    re.compile(r"输入输出边界由所在包和公开方法契约限定"),
    re.compile(r"Java 类型，用于封装当前包内的领域数据"),
    re.compile(r"封装当前包内的业务数据、协作能力或运行时支撑逻辑"),
    re.compile(r"请求参数或业务处理上下文"),
    re.compile(r"页面展示数据"),
    re.compile(r"查询\s+(?:list|page|query|find|get|load|select|search)\s+", re.IGNORECASE),
    re.compile(r"创建\s+(?:create|save|insert|add|register)\s+", re.IGNORECASE),
    re.compile(r"更新\s+(?:update|modify|change|edit)\s+", re.IGNORECASE),
    re.compile(r"删除或停用\s+(?:delete|remove)\s+", re.IGNORECASE),
    re.compile(r"处理\s+(?:handle|process|execute|consume|clean)\s+", re.IGNORECASE),
    re.compile(r"完成对应业务判断、字段转换或状态协作"),
    re.compile(r"完成当前类职责内的业务判断、字段转换或状态协作"),
    re.compile(r"根据输入对象完成本地判断、字段整理或状态辅助"),
    re.compile(r"整理.*为后续查询、校验、响应组装或审计记录生成标准值"),
    re.compile(r"整理[a-zA-Z0-9 ]+，"),
    re.compile(r"计算或解析[a-zA-Z0-9 ]*"),
    re.compile(r"返回后续状态判断、金额处理或响应组装可直接使用的标准值"),
    re.compile(r"该方法根据所属类职责执行必要的校验、转换、查询、写入或协作调用"),
    re.compile(r"摘要摘要"),
    re.compile(r"用于当前方法完成"),
    re.compile(r"含义由调用方法名称和所属业务对象限定"),
    re.compile(r"无状态支撑类型"),
    re.compile(r"创建 .* 实例并校验构造参数"),
    re.compile(r"实现必须保持 .* 已有权限、状态和异常语义"),
    re.compile(r"输入值，参与"),
    re.compile(r"方法执行后的业务结果、更新行数、转换对象或空结果"),
    re.compile(r"校验[a-zA-Z0-9]+输入，发现缺失、越权或格式错误时中断当前流程"),
    re.compile(r"@description\s*:\s*.*，位于 .*，用于接口或跨层传递该业务数据"),
    re.compile(r"@description\s*:\s*.*，位于 .*，限定所属聚合内该对象的字段集合和传递边界"),
    re.compile(r"在当前业务流程中传递结构化信息"),
    re.compile(r"(?:查询|构造|校验|解析|更新|处理|创建)[a-zA-Z]", re.IGNORECASE),
    re.compile(r"对应的本地处理，按所属类型职责完成校验、转换或结果组装"),
    re.compile(r"按调用方提供的过滤条件返回对应业务视图"),
    re.compile(r"，供当前方法按 .* 语义完成校验、转换或协作调用"),
    re.compile(r"声明的业务动作，并沿用所属类型的权限、状态、事务和异常边界"),
    re.compile(r"按 \{@code .*\} 的公开契约返回当前类型所需结果"),
    re.compile(r"@param\s+\w+\s+\{@code \w+\} 参数，其取值范围和可空性由当前方法与所属模型共同约束"),
]

DUPLICATE_TYPE_JAVADOC = re.compile(
    r"(?s)/\*\*(?:(?!\*/).)*@classname\s*:\s*(\w+)(?:(?!\*/).)*\*/\s*"
    r"/\*\*(?:(?!\*/).)*@classname\s*:\s*\1\b"
)
JAVADOC_BLOCK_RE = re.compile(r"/\*\*.*?\*/", re.DOTALL)
ADJACENT_JAVADOC_RE = re.compile(r"/\*\*.*?\*/\s*/\*\*", re.DOTALL)
TYPE_RE = re.compile(
    r"^(?P<indent>\s*)(?P<mods>(?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\s+)*)"
    r"(?P<kind>@interface|class|interface|enum|record)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\b"
)
METHOD_RE = re.compile(
    r"^(?P<indent>\s*)(?P<ann>(?:@[A-Za-z_][A-Za-z0-9_$.]*(?:\([^)]*\))?\s*)*)"
    r"(?P<mods>(?:(?:public|protected|private|default|static|final|abstract|synchronized|native|strictfp)\s+)*)"
    r"(?P<ret>[A-Za-z_][A-Za-z0-9_$<>, ?.\[\]]+\s+)?(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\((?P<params>[^;{}]*)\)"
    r"(?P<tail>\s*(?:throws\s+[^{;]+)?\s*[{;])"
)
FIELD_RE = re.compile(
    r"^(?P<indent>\s*)(?P<ann>(?:@[A-Za-z_][A-Za-z0-9_$.]*(?:\([^)]*\))?\s*)*)"
    r"(?P<mods>(?:(?:public|protected|private|static|final|transient|volatile)\s+)*)"
    r"(?P<type>[A-Za-z_][A-Za-z0-9_$<>, ?.\[\]]+)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)(?:\s*=[^;,]+)?(?:\s*,\s*[A-Za-z_][A-Za-z0-9_]*(?:\s*=[^;,]+)?)*\s*;"
)
ENUM_VALUE_RE = re.compile(r"^(?P<indent>\s*)(?P<name>[A-Z][A-Z0-9_]*)(?P<args>\s*(?:\([^;{}]*\))?)(?P<suffix>\s*[,;])\s*$")
LOGGER_NAMES = {"log", "logger", "LOGGER"}
TECHNICAL_STATIC_FIELDS = {"serialVersionUID"}
SKIP_METHOD_NAMES = {
    "if", "for", "while", "switch", "catch", "return", "new", "throw", "else", "do", "try"
}
MAX_SIGNATURE_LINES = 20


def iter_java_files(root: Path):
    for path in root.rglob("*.java"):
        if "target" not in path.parts:
            yield path


def has_javadoc_before(lines: list[str], index: int) -> bool:
    j = index - 1
    while j >= 0 and not lines[j].strip():
        j -= 1
    if j < 0:
        return False
    if lines[j].strip().endswith("*/"):
        return True

    declaration_indent = len(lines[index]) - len(lines[index].lstrip())
    saw_annotation = False
    for k in range(j, max(-1, j - 120), -1):
        stripped = lines[k].strip()
        if not stripped:
            continue
        indent = len(lines[k]) - len(lines[k].lstrip())
        if stripped.endswith("*/"):
            return saw_annotation
        if indent < declaration_indent:
            break
        if indent > declaration_indent:
            # Multiline annotation bodies, including MyBatis text blocks, are
            # indented deeper than the declaration and belong to the same block.
            continue
        if stripped.startswith("@"):
            saw_annotation = True
            continue
        # A declaration or block boundary at the same indentation proves that
        # any earlier Javadoc belongs to a previous member, not this one.
        if (TYPE_RE.match(lines[k])
                or FIELD_RE.match(lines[k])
                or METHOD_RE.match(lines[k])
                or collected_method_signature(lines, k)
                or stripped in {"{", "}", "};"}
                or stripped.endswith(";")):
            break
    return False


def collected_method_signature(lines: list[str], index: int) -> tuple[str, int] | None:
    first = lines[index].strip()
    if not first or first.startswith("*") or first.startswith("//") or first.startswith("@"):
        return None
    if not re.match(r"(?:public|protected|private|static|final|abstract|synchronized|native|strictfp|[A-Za-z_][A-Za-z0-9_$<>, ?.\[\]]+\s+[A-Za-z_][A-Za-z0-9_]*\s*\()", first):
        return None
    parts: list[str] = []
    for offset in range(MAX_SIGNATURE_LINES):
        if index + offset >= len(lines):
            return None
        current = lines[index + offset].strip()
        if not current or current.startswith("//") or current.startswith("*"):
            return None
        parts.append(current)
        joined = " ".join(parts)
        if "{" in current or ";" in current:
            if "(" in joined and ")" in joined:
                return joined, offset + 1
            return None
    return None


def annotation_block_end(lines: list[str], index: int) -> int:
    """Return the final line of an annotation, excluding parentheses inside strings."""
    if not lines[index].lstrip().startswith("@"):
        return index
    parenthesis_depth = 0
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
                    cursor = len(line)
                    continue
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
                parenthesis_depth += 1
                saw_parenthesis = True
            elif char == ")":
                parenthesis_depth -= 1
            cursor += 1
        if not saw_parenthesis or (parenthesis_depth <= 0 and not in_string and not in_text_block):
            return line_index
    return index


def has_duplicate_javadocs_across_annotations(text: str) -> bool:
    """Detect two Javadocs attached to one declaration on opposite sides of annotations."""
    lines = text.splitlines(keepends=True)
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
            return True
        index = first_end + 1
    return False


def has_javadoc_after_annotations(text: str) -> bool:
    """Detect Javadocs placed after annotations instead of before the declaration."""
    lines = text.splitlines(keepends=True)
    index = 0
    while index < len(lines):
        if not lines[index].lstrip().startswith("@"):
            index += 1
            continue
        annotation_indent = len(lines[index]) - len(lines[index].lstrip())
        cursor = index
        while (cursor < len(lines)
               and lines[cursor].lstrip().startswith("@")
               and len(lines[cursor]) - len(lines[cursor].lstrip()) == annotation_indent):
            cursor = annotation_block_end(lines, cursor) + 1
            while cursor < len(lines) and not lines[cursor].strip():
                cursor += 1
        if (cursor < len(lines)
                and lines[cursor].lstrip().startswith("/**")
                and len(lines[cursor]) - len(lines[cursor].lstrip()) == annotation_indent):
            return True
        index = max(index + 1, cursor)
    return False


def is_main_java(path: Path) -> bool:
    return "src/main/java" in str(path)


def is_model_path(path: Path) -> bool:
    normalized = str(path).replace("\\", "/").lower()
    return any(segment in normalized for segment in (
        "/dto/", "/entity/", "/model/", "/vo/", "/request/", "/response/"
    )) or path.stem.lower().endswith(("dto", "do", "vo", "request", "response"))


def is_simple_accessor(path: Path, name: str, params: str) -> bool:
    if not is_model_path(path):
        return False
    parts = [part.strip() for part in params.split(",") if part.strip()]
    lower = name.lower()
    return ((lower.startswith("get") or lower.startswith("is")) and not parts
            or lower.startswith("set") and len(parts) == 1)


CORE_PRIVATE_ALWAYS = re.compile(
    r"(?:encrypt|decrypt|hmac|nonce|fingerprint|canonical.*fingerprint|completeidempotency|"
    r"resolveduplicate|locksource|verifyidentity|validatecurrency|tominoramount|save.*event|"
    r"record.*preparedfact|processcallback|updatecallbackprocess|requirelockedratecapacity|"
    r"validaterate|requiresamecurrency|constanttimeequals|stableid|deterministiceventno)",
    re.IGNORECASE,
)
CORE_PRIVATE_COMPLEX = re.compile(
    r"(?:amount|currency|rate|fee|reserve|settlement|clearing|outbox|callback|shard|ledger|"
    r"posting|reversal|approve|review|claim|lease|compensat|retry|projection|refund|capture|"
    r"void|authorization|state|complete|failure|recovery)",
    re.IGNORECASE,
)
CORE_PATH_MARKERS = (
    "service-payment/", "service-clearing/", "service-settlement/", "service-openapi/",
    "service-data/", "component-security/", "finance-library/", "component-db/",
    "channel-library/",
)


def method_body_metrics(lines: list[str], index: int) -> tuple[int, int]:
    depth = 0
    started = False
    body_lines = 0
    branches = 0
    in_block = False
    in_string = False
    for cursor in range(index, min(len(lines), index + 800)):
        line = lines[cursor]
        if not started and ";" in line and "{" not in line:
            return 0, 0
        delta, in_block, in_string = current_depth(line, in_block, in_string)
        if "{" in line or started:
            started = True
            body_lines += 1
            branches += len(re.findall(r"\b(?:if|else|switch|case|catch|for|while|try)\b", line))
        depth += delta
        if started and depth <= 0:
            break
    return body_lines, branches


def is_core_private_method(path: Path, name: str, lines: list[str], index: int) -> bool:
    if not is_main_java(path):
        return False
    normalized = str(path).replace("\\", "/")
    if not any(marker in normalized for marker in CORE_PATH_MARKERS):
        return False
    if CORE_PRIVATE_ALWAYS.search(name):
        return True
    if not CORE_PRIVATE_COMPLEX.search(name):
        return False
    body_lines, branches = method_body_metrics(lines, index)
    return body_lines >= 15 or branches >= 2


def is_empty_private_constructor(lines: list[str], index: int, type_name: str) -> bool:
    collected = collected_method_signature(lines, index)
    if not collected:
        return False
    signature = collected[0]
    if not re.match(rf"private\s+{re.escape(type_name)}\s*\(\s*\)\s*\{{", signature):
        return False
    if "}" in signature:
        return True
    for offset in range(1, 5):
        if index + offset >= len(lines):
            return False
        stripped = lines[index + offset].strip()
        if not stripped:
            continue
        return stripped == "}"
    return False


def current_depth(line: str, in_block_comment: bool, in_string: bool) -> tuple[int, bool, bool]:
    depth = 0
    i = 0
    while i < len(line):
        ch = line[i]
        nxt = line[i:i + 2]
        if in_block_comment:
            if nxt == "*/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_string:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_string = False
            i += 1
            continue
        if nxt == "/*":
            in_block_comment = True
            i += 2
            continue
        if nxt == "//":
            break
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        i += 1
    return depth, in_block_comment, in_string


def scan_structural_comments(path: Path, text: str) -> list[str]:
    lines = text.splitlines(keepends=True)
    findings: list[str] = []
    depth = 0
    in_block = False
    in_string = False
    method_signature_end = -1
    annotation_end = -1
    type_stack: list[tuple[str, str, int]] = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        if index <= method_signature_end or index <= annotation_end:
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            continue
        if stripped.startswith("@"):
            annotation_end = annotation_block_end(lines, index)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            continue
        while type_stack and depth <= type_stack[-1][2]:
            type_stack.pop()
        type_match = TYPE_RE.match(line)
        if type_match and not stripped.startswith("*") and not stripped.startswith("//"):
            kind = type_match.group("kind")
            name = type_match.group("name")
            mods = set((type_match.group("mods") or "").split())
            is_top = depth == 0
            is_public_static_nested = depth > 0 and {"public", "static"}.issubset(mods)
            if (is_main_java(path) and (is_top or is_public_static_nested)
                    and not has_javadoc_before(lines, index)):
                findings.append(f"missing_type_javadoc:{name}:{index + 1}")
            type_stack.append((kind, name, depth))
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            continue
        current_type = type_stack[-1] if type_stack else None
        if current_type and current_type[0] == "enum" and depth == current_type[2] + 1:
            enum_value = ENUM_VALUE_RE.match(line)
            if is_main_java(path) and enum_value and not has_javadoc_before(lines, index):
                findings.append(f"missing_enum_value_javadoc:{enum_value.group('name')}:{index + 1}")
                delta, in_block, in_string = current_depth(line, in_block, in_string)
                depth += delta
                continue
        field = FIELD_RE.match(line)
        if field and current_type and depth == current_type[2] + 1 and not stripped.startswith("return "):
            name = field.group("name")
            mods = set((field.group("mods") or "").split())
            required_field = (is_main_java(path) and current_type[0] != "record"
                              and (is_model_path(path) or {"static", "final"}.issubset(mods)))
            if (required_field and name not in LOGGER_NAMES and name not in TECHNICAL_STATIC_FIELDS
                    and not has_javadoc_before(lines, index)):
                findings.append(f"missing_field_javadoc:{name}:{index + 1}")
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            continue
        method = METHOD_RE.match(line)
        if not method and current_type and depth == current_type[2] + 1:
            collected_signature = collected_method_signature(lines, index)
            if collected_signature:
                method = METHOD_RE.match(collected_signature[0])
                if method:
                    # A declaration may span multiple annotated parameter lines. Once the
                    # complete signature is recognized, those continuation lines must not
                    # be scanned again as standalone methods.
                    method_signature_end = index + collected_signature[1] - 1
        if method and current_type and depth == current_type[2] + 1:
            name = method.group("name")
            mods = set((method.group("mods") or "").split())
            ret = method.group("ret")
            is_constructor = name == current_type[1] and ret is None
            is_private_core = "private" in mods and is_core_private_method(path, name, lines, index)
            is_required_method = (is_main_java(path)
                                  and ("public" in mods or "protected" in mods
                                       or current_type[0] == "interface" or is_private_core))
            if is_constructor or is_simple_accessor(path, name, method.group("params")):
                delta, in_block, in_string = current_depth(line, in_block, in_string)
                depth += delta
                continue
            if is_required_method and (is_constructor or name not in SKIP_METHOD_NAMES) and not has_javadoc_before(lines, index):
                findings.append(f"missing_method_javadoc:{name}:{index + 1}")
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            continue
        delta, in_block, in_string = current_depth(line, in_block, in_string)
        depth += delta
    return findings


def scan_file(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    findings: list[str] = []
    javadocs = JAVADOC_BLOCK_RE.findall(text)
    for pattern in TEMPLATE_PATTERNS:
        if any(pattern.search(block) for block in javadocs):
            findings.append(f"template:{pattern.pattern}")
    if DUPLICATE_TYPE_JAVADOC.search(text):
        findings.append("duplicate-type-javadoc")
    if ADJACENT_JAVADOC_RE.search(text):
        findings.append("orphan-or-adjacent-javadoc")
    if has_duplicate_javadocs_across_annotations(text):
        findings.append("duplicate-javadoc-across-annotations")
    if has_javadoc_after_annotations(text):
        findings.append("javadoc-after-annotations")
    findings.extend(scan_structural_comments(path, text))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Java comment cleanup residue.")
    parser.add_argument("--root", default=".", help="Project root")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    files = list(iter_java_files(root))
    findings: list[tuple[Path, list[str]]] = []
    for path in files:
        file_findings = scan_file(path)
        if file_findings:
            findings.append((path, file_findings))

    print(f"checked_java_files={len(files)}")
    print(f"remaining_files={len(findings)}")
    print(f"remaining_hits={sum(len(item[1]) for item in findings)}")
    if findings:
        for path, file_findings in findings[:100]:
            print(f"{path.relative_to(root)}: {', '.join(file_findings)}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
