#!/usr/bin/env python3
"""Verify logging rules for payment trace and sensitive data hygiene."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


SENSITIVE_TOKENS = [
    "cardNo",
    "securityCode",
    "cvv",
    "cvc",
    "Authorization",
    "privateKey",
    "apiPassword",
    "merchantKey",
    "rawBody",
    "requestBody",
]

SAFE_LOG_HINTS = [
    "SensitiveDataMaskUtils",
    "mask",
    "masked",
    "bodyLength",
    ".length()",
    ".length",
    "fingerprint",
    "指纹",
    "摘要",
    "长度",
]

FORBIDDEN_PATTERNS = [
    re.compile(r"System\.out\."),
    re.compile(r"printStackTrace\s*\("),
]

REQUIRED_EVENT_PATTERNS = [
    "PAYMENT_TRANSACTION_START",
    "PAYMENT_TRANSACTION_END",
    "PAYMENT_IDENTIFIERS_GENERATED",
    "PAYMENT_IDEMPOTENCY_HIT",
    "PAYMENT_IDEMPOTENCY_COMPLETE",
    "PAYMENT_LOCAL_PREPARE_COMMIT",
    "PAYMENT_ROUTE_START",
    "PAYMENT_ROUTE_END",
    "PAYMENT_ROUTE_DECISION",
    "PAYMENT_RISK_REQUEST_START",
    "PAYMENT_RISK_REQUEST_END",
    "PAYMENT_CHANNEL_REQUEST_START",
    "PAYMENT_CHANNEL_REQUEST_END",
    "PAYMENT_STATUS_MAPPED",
    "PAYMENT_MERCHANT_RESPONSE_BUILT",
    "PAYMENT_AMOUNT_CHANGED",
    "PAYMENT_CHANNEL_CALLBACK_START",
    "PAYMENT_CHANNEL_CALLBACK_END",
    "PAYMENT_CHANNEL_CALLBACK_DUPLICATE",
    "PAYMENT_CHANNEL_CALLBACK_PROCESS_UPDATE",
    "PAYMENT_MERCHANT_NOTIFY_CREATED",
    "DATA_MERCHANT_NOTIFY_ATTEMPT_START",
    "DATA_MERCHANT_NOTIFY_ATTEMPT_END",
    "GATEWAY_REQUEST_START",
    "GATEWAY_ROUTE_COMPLETE",
    "GATEWAY_REQUEST_END",
    "OPENAPI_REQUEST_ENTER",
    "OPENAPI_SECURITY_CHECK_END",
    "OPENAPI_REQUEST_DECRYPT_END",
    "OPENAPI_PAYMENT_CALL_START",
    "OPENAPI_PAYMENT_CALL_END",
    "OPENAPI_RESPONSE_ENCRYPT_END",
    "RISK_EVALUATION_START",
    "RISK_EVALUATION_END",
    "CHANNEL_REQUEST_START",
    "CHANNEL_RESPONSE_END",
    "ADMIN_WRITE_OPERATION",
    "ADMIN_QUERY_ACCESS",
    "JOB_HANDLER_SCAN_START",
    "JOB_HANDLER_SCAN_END",
]

REQUIRED_TRACE_PATTERNS = [
    "TraceIdRestTemplateInterceptor",
    "TraceIdRestTemplateCustomizer",
    "request.header(TraceContext.TRACE_ID_HEADER",
    "message.setTraceId(TraceContext.getOrCreateTraceId())",
    "TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()))",
    "TraceContext.clear()",
    "maskJsonSafely",
    "MASK_FAILED_PLACEHOLDER",
]


def iter_java_files(root: Path):
    for path in root.rglob("*.java"):
        if "target" not in path.parts:
            yield path


def extract_log_statements(text: str) -> list[str]:
    statements: list[str] = []
    for match in re.finditer(r"\blog\.(?:trace|debug|info|warn|error)\s*\(", text):
        start = match.start()
        index = match.end()
        depth = 1
        in_string = False
        escaped = False
        while index < len(text):
            ch = text[index]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
            else:
                if ch == '"':
                    in_string = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        statements.append(text[start:index + 1])
                        break
            index += 1
    return statements


def unsafe_log_token(statement: str) -> str | None:
    lower_statement = statement.lower()
    if any(hint.lower() in lower_statement for hint in SAFE_LOG_HINTS):
        return None
    for token in SENSITIVE_TOKENS:
        token_pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(token) + r"(?![A-Za-z0-9_])", re.IGNORECASE)
        if token_pattern.search(statement):
            return token
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify logging hygiene rules.")
    parser.add_argument("--root", default=".", help="Project root")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    java_files = list(iter_java_files(root))
    all_text = []
    findings: list[str] = []
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        all_text.append(text)
        for pattern in FORBIDDEN_PATTERNS:
            if pattern.search(text):
                findings.append(f"{path.relative_to(root)}: {pattern.pattern}")
        for statement in extract_log_statements(text):
            token = unsafe_log_token(statement)
            if token:
                findings.append(f"{path.relative_to(root)}: log statement contains {token}")

    joined = "\n".join(all_text)
    missing_events = [event for event in REQUIRED_EVENT_PATTERNS if event not in joined]
    missing_trace_rules = [pattern for pattern in REQUIRED_TRACE_PATTERNS if pattern not in joined]

    print(f"checked_java_files={len(java_files)}")
    print(f"sensitive_log_findings={len(findings)}")
    print(f"missing_required_events={len(missing_events)}")
    print(f"missing_trace_rules={len(missing_trace_rules)}")
    for finding in findings[:100]:
        print(finding)
    for event in missing_events:
        print(f"missing_event:{event}")
    for pattern in missing_trace_rules:
        print(f"missing_trace_rule:{pattern}")
    return 1 if findings or missing_events or missing_trace_rules else 0


if __name__ == "__main__":
    raise SystemExit(main())
