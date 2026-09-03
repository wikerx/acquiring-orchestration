#!/usr/bin/env python3
"""Refine generated Java Javadocs into field- and module-aware descriptions."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

TYPE_RE = re.compile(
    r"^(?P<indent>\s*)(?P<mods>(?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\s+)*)"
    r"(?P<kind>@interface|class|interface|enum|record)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\b"
)
FIELD_RE = re.compile(
    r"^(?P<indent>\s*)(?P<ann>(?:@[A-Za-z_][A-Za-z0-9_$.]*(?:\([^)]*\))?\s*)*)"
    r"(?P<mods>(?:(?:public|protected|private|static|final|transient|volatile)\s+)*)"
    r"(?P<type>[A-Za-z_][A-Za-z0-9_$<>, ?.\[\]]+)\s+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)(?:\s*=[^;,]+)?(?:\s*,\s*[A-Za-z_][A-Za-z0-9_]*(?:\s*=[^;,]+)?)*\s*;"
)
METHOD_RE = re.compile(
    r"^(?P<indent>\s*)(?P<ann>(?:@[A-Za-z_][A-Za-z0-9_$.]*(?:\([^)]*\))?\s*)*)"
    r"(?P<mods>(?:(?:public|protected|private|default|static|final|abstract|synchronized|native|strictfp)\s+)*)"
    r"(?P<ret>[A-Za-z_][A-Za-z0-9_$<>, ?.\[\]]+\s+)?(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\((?P<params>[^;{}]*)\)"
    r"(?P<tail>\s*(?:throws\s+[^{;]+)?\s*[{;])"
)
ENUM_VALUE_RE = re.compile(r"^(?P<indent>\s*)(?P<name>[A-Z][A-Z0-9_]*)(?P<args>\s*(?:\([^;{}]*\))?)(?P<suffix>\s*[,;])\s*$")

FIELD_TEMPLATE_MARKERS = (
    "用于保存",
    "取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束",
    "与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障",
    "字段，表示当前模型在所属业务流程中的对应属性",
    "当前模型在所属业务流程中的对应属性",
    "由上游接口、数据库字段或枚举定义约束",
    "接口请求、数据库记录、配置文件或上游服务返回",
    "与同对象字段共同组成当前业务语义",
    "记录当前业务流程需要的结构化属性",
    "取值范围由所属 DTO、实体或配置类定义",
    "Spring 配置和构造器注入的内部客户端依赖",
    "表示当前记录在业务流程中的处理状态",
    "表示当前渠道、配置或接口是否支持对应能力",
    "表示当前记录所属的业务类型、交易类型或配置分类",
    "在当前场景中传递或保存",
    "用于关联当前记录所属的业务对象或配置对象",
    "中的标识、状态或时间字段共同描述当前记录",
    "在当前业务流程中传递结构化信息",
)
METHOD_TEMPLATE_MARKERS = (
    "前置条件：",
    "当前业务步骤需要",
    "按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用",
    "层级边界：",
    "状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准",
    "方法签名声明的返回值，具体结构由返回类型定义",
    "按当前领域规则完成校验、状态读取或数据写入",
    "的本地校验、字段转换或结果组装",
    "状态变化、事务边界、远程调用和敏感数据处理以当前实现为准",
    "服务于",
    "当前调用链需要的校验、转换或状态结果",
    "当前调用链需要的处理结果",
    "含义由调用方法名称和所属业务对象限定",
    "状态变化、事务边界、远程调用和敏感数据处理按当前实现执行",
    "完成对应业务判断、字段转换或状态协作",
    "用于当前方法完成",
    "完成当前类职责内的业务判断、字段转换或状态协作",
    "根据输入对象完成本地判断、字段整理或状态辅助",
    "为后续查询、校验、响应组装或审计记录生成标准值",
    "返回后续状态判断、金额处理或响应组装可直接使用的标准值",
    "本地协作不得扩大",
    "按当前方法契约生成的业务处理结果",
    "对应的本地处理，按所属类型职责完成校验、转换或结果组装",
    "按调用方提供的过滤条件返回对应业务视图",
    "，供当前方法按 ",
    "声明的业务动作，并沿用所属类型的权限、状态、事务和异常边界",
    "的公开契约返回当前类型所需结果；状态、副作用和异常语义以所属接口定义为准",
    "参数，其取值范围和可空性由当前方法与所属模型共同约束",
)
TYPE_TEMPLATE_MARKERS = (
    "输入输出边界由所在包和公开方法契约限定",
    "Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑",
    "接口传输模型，用于约束请求入参、响应字段和跨层数据边界",
    "封装当前包内的业务数据、协作能力或运行时支撑逻辑",
    "支撑类型，位于",
    "无状态支撑类型",
    "场景所需的数据结构、协作入口或运行时能力",
    "传输模型，位于",
    "持久化模型，位于",
    "协作组件，位于",
    "服务契约，位于",
    "服务实现，位于",
    "控制器，位于",
    "枚举，位于",
    "MPGS 请求报文的",
    "MPGS 响应报文的",
    "用于接口或跨层传递该业务数据，不承担状态写入职责",
    "请求模型，位于",
    "响应模型，位于",
    "嵌套数据模型，位于",
    "应用服务，位于",
    "配置类，位于",
    "导出行模型，位于",
)


@dataclass
class TypeContext:
    kind: str
    name: str
    depth: int


def iter_java_files(root: Path):
    for path in root.rglob("*.java"):
        if "target" not in path.parts:
            yield path


def javadoc(lines: list[str], indent: str) -> list[str]:
    return [indent + "/**\n"] + [indent + " * " + line + "\n" for line in lines] + [indent + " */\n"]


def javadoc_bounds_before(lines: list[str], index: int) -> tuple[int, int, str] | None:
    cursor = index - 1
    while cursor >= 0 and not lines[cursor].strip():
        cursor -= 1
    if cursor < 0 or not lines[cursor].strip().endswith("*/"):
        return None
    end = cursor
    while cursor >= 0 and "/**" not in lines[cursor]:
        cursor -= 1
    if cursor < 0:
        return None
    return cursor, end, "".join(lines[cursor:end + 1])


def javadoc_bounds_before_declaration(lines: list[str], index: int) -> tuple[int, int, str] | None:
    """Find Javadoc attached to a declaration, skipping annotation lines above it."""
    cursor = index - 1
    while cursor >= 0:
        while cursor >= 0 and not lines[cursor].strip():
            cursor -= 1
        if cursor >= 0 and lines[cursor].strip().startswith("@"):
            cursor -= 1
            continue
        break
    if cursor + 1 != index:
        return javadoc_bounds_before(lines, cursor + 1)
    return javadoc_bounds_before(lines, index)


def current_depth(line: str, in_block_comment: bool, in_string: bool) -> tuple[int, bool, bool]:
    depth = 0
    index = 0
    while index < len(line):
        ch = line[index]
        pair = line[index:index + 2]
        if in_block_comment:
            if pair == "*/":
                in_block_comment = False
                index += 2
                continue
            index += 1
            continue
        if in_string:
            if ch == "\\":
                index += 2
                continue
            if ch == '"':
                in_string = False
            index += 1
            continue
        if pair == "/*":
            in_block_comment = True
            index += 2
            continue
        if pair == "//":
            break
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        index += 1
    return depth, in_block_comment, in_string


def split_words(name: str) -> list[str]:
    name = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name).replace("_", " ")
    name = re.sub(r"([A-Za-z])([0-9])", r"\1 \2", name)
    name = re.sub(r"([0-9])([A-Za-z])", r"\1 \2", name)
    return [word for word in name.split() if word]


def lower_word_set(name: str) -> set[str]:
    return {word.lower() for word in split_words(name)}


def human_name(name: str) -> str:
    words = split_words(name)
    known = {
        "id": "ID",
        "dto": "DTO",
        "vo": "VO",
        "do": "DO",
        "url": "URL",
        "uri": "URI",
        "ip": "IP",
        "jwt": "JWT",
        "mq": "MQ",
        "api": "API",
        "mid": "MID",
        "mcc": "MCC",
        "dcc": "DCC",
        "edc": "EDC",
        "3ds": "3DS",
        "cavv": "CAVV",
        "cvv": "CVV",
        "cvc": "CVC",
        "rsa": "RSA",
        "aes": "AES",
        "stan": "STAN",
        "arn": "ARN",
        "iso": "ISO",
        "totp": "TOTP",
    }
    result = " ".join(known.get(word.lower(), word) for word in words)
    return result.replace("3 ds", "3DS").replace("3 Ds", "3DS")


def field_label(name: str) -> str:
    """Return a readable Chinese label, or preserve the exact Java identifier as code."""
    translated = business_subject(lower_words(name))
    if re.search(r"[a-z]{2,}", translated):
        return f"{{@code {name}}}"
    return translated


def lower_words(name: str) -> str:
    return " ".join(word.lower() for word in split_words(name))


def method_template_block(block: str) -> bool:
    """Detect generated method comments that still contain template wording."""
    if any(marker in block for marker in METHOD_TEMPLATE_MARKERS):
        return True
    if re.search(r"整理[a-zA-Z0-9 ]+，", block):
        return True
    if re.search(r"计算或解析[a-zA-Z0-9 ]*，", block):
        return True
    generated_lead = re.compile(
        r"(查询|创建|更新|删除或停用|处理|计算或解析|构造)\s*"
        r"(list|page|query|find|get|load|select|search|create|save|insert|add|register|"
        r"update|modify|change|edit|delete|remove|handle|process|execute|consume|clean|record|mask|sha|resolve|translate)\b",
        re.IGNORECASE,
    )
    english_operation = re.compile(r"(计算或解析|构造)\s+[a-z][a-z0-9 ]+\s+(值|对象)", re.IGNORECASE)
    mixed_operation = re.compile(r"(?:查询|构造|校验|解析|更新|处理|创建)[a-zA-Z]", re.IGNORECASE)
    return bool(generated_lead.search(block) or english_operation.search(block) or mixed_operation.search(block))


def field_template_block(block: str) -> bool:
    """Detect generated field comments that are too generic to keep."""
    if any(marker in block for marker in FIELD_TEMPLATE_MARKERS):
        return True
    if ("是否允许为空由接口校验、数据库约束或调用契约决定" in block or "不允许为空" in block) \
            and "数据来源：" in block:
        return True
    return bool(re.search(r"[A-Za-z]+\s+Type，表示当前记录所属", block))


def generated_field_block(block: str) -> bool:
    """Identify only comments produced by this repository's field generator."""
    return ("是否允许为空由接口校验、数据库约束或调用契约决定" in block or "不允许为空" in block) \
        and "数据来源：" in block


def simple_component_owner(owner: str) -> bool:
    """Return whether fields are implementation details rather than data-model fields."""
    lower = owner.lower()
    return lower.endswith((
        "service", "serviceimpl", "controller", "client", "restclient", "mapper",
        "registry", "resolver", "provider", "interceptor", "filter", "calculator",
        "coordinator", "scheduler", "publisher", "consumer", "listener", "handler",
        "configuration", "config", "support", "util", "utils", "factory",
    ))


def type_template_block(block: str) -> bool:
    """Detect generated type comments that describe shape instead of responsibility."""
    if any(marker in block for marker in TYPE_TEMPLATE_MARKERS):
        return True
    return bool(re.search(r"@description\s*:\s+.*支撑类型，位于", block))


def business_subject(subject: str) -> str:
    """Translate common camel-case method subjects into business-readable Chinese."""
    normalized = re.sub(r"\s+", " ", subject.strip().lower())
    phrase_mapping = {
        "": "当前业务",
        "dept": "部门",
        "depts": "部门",
        "post": "岗位",
        "posts": "岗位",
        "account": "账号",
        "accounts": "账号",
        "account base": "账号基础信息",
        "account status": "账号状态",
        "role": "角色",
        "roles": "角色",
        "role status": "角色状态",
        "merchant user": "商户用户",
        "merchant users": "商户用户",
        "granted menu ids": "已授权菜单 ID",
        "granted permission ids": "已授权权限 ID",
        "granted menu tree": "已授权菜单树",
        "payment": "支付交易",
        "payout": "代付交易",
        "capture transaction": "请款交易",
        "refund transaction": "退款交易",
        "void transaction": "撤销交易",
        "incremental authorization transaction": "增量授权交易",
        "callback": "渠道回调",
        "callback process result": "回调处理结果",
        "callback processed event": "回调处理事件",
        "operations": "交易动作",
        "all operations": "全部交易动作",
        "orders": "交易主单",
        "all orders": "全部交易主单",
        "operation by transaction id": "按交易号定位的动作单",
        "orders by operation id": "按操作号定位的交易主单",
        "operations by operation id": "按操作号定位的动作单",
        "amount summary": "金额汇总",
        "payment method summary": "支付方式汇总",
        "channel": "渠道",
        "channels": "渠道",
        "channel options": "渠道选项",
        "capability": "渠道能力",
        "capabilities": "渠道能力",
        "capability status": "渠道能力状态",
        "capability support": "渠道能力支持标识",
        "limit": "限额",
        "limits": "限额",
        "limit dimension": "限额维度",
        "mid": "渠道 MID",
        "mids": "渠道 MID",
        "mid binding": "渠道 MID 绑定",
        "mid bindings": "渠道 MID 绑定",
        "rule": "规则",
        "rules": "规则",
        "rule status": "规则状态",
        "rule dimension": "规则维度",
        "events": "事件",
        "event": "事件",
        "notify logs": "通知日志",
        "notification": "通知任务",
        "merchant notifications": "商户通知任务",
        "all merchant notifications": "全部商户通知任务",
        "template": "模板",
        "templates": "模板",
        "record": "记录",
        "records": "记录",
        "logs": "日志",
        "notice": "公告",
        "summary": "汇总数据",
        "business rate": "业务汇率",
        "matched rules": "命中的汇率规则",
        "enabled source": "启用的汇率来源",
        "fetch log": "汇率抓取日志",
        "source fetch status": "汇率来源抓取状态",
        "raw rate value": "原始汇率值",
        "dict": "字典数据",
        "dict labels": "字典标签",
        "active row": "生效记录",
        "level 1": "一级分类",
        "level 2": "二级分类",
        "child": "子节点",
        "code by id": "按 ID 定位的编码",
        "policy": "策略配置",
        "condition": "查询条件",
        "excel header row": "Excel 表头行",
        "data": "数据集合",
        "mode": "运行模式",
        "timezone": "时区配置",
        "sequence length": "序列长度",
        "max sequence": "最大序列值",
        "seq key prefix": "序列 Redis Key 前缀",
        "last millis key": "上一毫秒 Redis Key",
        "seq key expire seconds": "序列 Key 过期秒数",
        "max retry times": "最大重试次数",
        "retry sleep millis": "重试休眠毫秒数",
        "headers": "请求头",
        "text": "文本",
        "run logs": "任务运行日志",
        "blocked": "拦截事件",
        "cipher": "密文摘要",
        "cipher text": "密文文本",
        "sha 256 hex": "SHA-256 十六进制摘要",
        "http exception": "HTTP 异常",
        "result": "结果对象",
        "risk decision": "风控结论",
        "merchant response code": "商户响应码",
        "merchant response message": "商户响应说明",
        "payment brand": "支付品牌",
        "card bin": "卡 BIN",
        "callback url": "回调地址",
        "currency exponent": "币种小数位",
        "minor amount": "最小货币单位金额",
        "utc time": "UTC 时间",
        "no conversion": "无需换汇结果",
        "duplicate result": "重复请求结果",
        "initial totals": "初始累计金额",
        "result sub merchant info": "子商户响应信息",
        "visible failure message": "商户可见失败说明",
        "code and message": "编码和说明",
        "first text": "首个非空文本",
        "plain response": "响应明文摘要",
        "plain request": "请求明文摘要",
        "header summary": "请求头摘要",
        "authorization summary": "鉴权头摘要",
        "body summary": "报文体摘要",
        "opaque fields": "不透明敏感字段",
        "traffic": "HTTP 访问摘要",
        "mfa": "多因子认证",
        "password": "密码",
        "role tree": "角色授权树",
        "role menu": "角色菜单授权",
        "role permission": "角色权限授权",
        "user agent": "User-Agent 摘要",
        "safe user agent": "User-Agent 摘要",
        "account password": "账号密码",
        "account mfa": "账号 MFA",
        "account role": "账号角色",
        "account dept": "账号部门",
        "account post": "账号岗位",
        "role grants": "角色授权",
        "granted permission": "已授权权限",
        "payment info by transaction": "按交易号查询的支付工具信息",
        "payment info by order transaction": "按订单交易号查询的支付工具信息",
        "payment info by order operation": "按订单操作号查询的支付工具信息",
        "payment info table for operation table": "动作单对应的支付工具分表",
        "payment summary row mapper": "支付汇总行映射器",
        "payment info mapper": "支付工具信息映射器",
        "local date time": "本地日期时间",
        "nullable int": "可空整数",
        "append text filter": "文本筛选条件",
        "base params": "基础 SQL 参数",
        "current operator": "当前操作人",
        "current operator name": "当前操作人名称",
        "current merchant id": "当前商户号",
        "current account id": "当前账号 ID",
        "invoke channel safely": "安全调用渠道",
        "canonical followup request fingerprint": "后续交易幂等指纹",
        "canonical void request fingerprint": "撤销交易幂等指纹",
        "elapsed millis": "耗时毫秒数",
        "new digest": "SHA-256 摘要器",
        "put if text": "非空文本字段",
        "put if present": "非空摘要字段",
        "support channel code": "渠道编码支持判断",
        "submit payout": "代付提交请求",
        "submit refund": "退款提交请求",
        "do send": "邮件发送动作",
        "missing variables": "缺失模板变量",
        "extract variables": "模板变量提取结果",
        "decrypt secret": "解密后的密钥材料",
        "secret key": "密钥材料",
        "generate code": "生成编码",
        "read timeout": "读取超时时间",
        "extension value": "扩展字段值",
        "acquirer code": "收单机构结果码",
        "table": "物理表处理",
        "target quarters": "目标分表季度",
        "collect summary": "扫描汇总结果",
        "run status": "任务运行状态",
        "finish task run": "任务运行完成状态",
        "complete refund channel result": "退款渠道结果落库",
        "payment bucket": "支付方式汇总桶",
        "source of funds": "资金来源",
        "open api health": "OpenAPI 健康检查",
        "merchant risk level": "商户风险等级",
        "merchant status": "商户状态",
        "open api key export format": "OpenAPI 密钥导出格式",
        "open api key type": "OpenAPI 密钥类型",
        "api result": "统一 API 结果",
        "three ds": "3DS",
        "three ds 1": "3DS 1.x",
        "three ds 2": "3DS 2.x",
        "billing card holder info": "账单持卡人信息",
        "merchant template email": "商户模板邮件",
        "payment pending reason": "支付等待原因",
        "payout create client": "代付创建内部调用",
    }
    if normalized in phrase_mapping:
        return phrase_mapping[normalized]
    words = normalized.split()
    translated_words = [
        {
            "dept": "部门",
            "depts": "部门",
            "post": "岗位",
            "posts": "岗位",
            "account": "账号",
            "accounts": "账号",
            "role": "角色",
            "roles": "角色",
            "merchant": "商户",
            "user": "用户",
            "users": "用户",
            "transaction": "交易",
            "transactions": "交易",
            "operation": "动作",
            "operations": "动作",
            "order": "订单",
            "orders": "订单",
            "info": "信息",
            "by": "按",
            "for": "对应",
            "query": "查询",
            "queries": "查询",
            "filter": "筛选",
            "params": "参数",
            "mapper": "映射器",
            "row": "行",
            "rows": "行",
            "table": "表",
            "agent": "Agent",
            "safe": "安全",
            "support": "支持",
            "submit": "提交",
            "reset": "重置",
            "assign": "分配",
            "grant": "授权",
            "granted": "已授权",
            "replace": "替换",
            "softdelete": "软删除",
            "flatten": "展平",
            "valid": "有效",
            "current": "当前",
            "display": "展示",
            "login": "登录",
            "can": "可",
            "mfa": "MFA",
            "email": "邮件",
            "variables": "变量",
            "private": "私钥",
            "platform": "平台",
            "reject": "拒绝",
            "put": "写入",
            "encode": "编码",
            "decode": "解码",
            "read": "读取",
            "timeout": "超时",
            "extension": "扩展",
            "value": "值",
            "values": "值",
            "elapsed": "耗时",
            "millis": "毫秒数",
            "new": "新建",
            "digest": "摘要",
            "canonical": "规范化",
            "followup": "后续交易",
            "fingerprint": "指纹",
            "void": "撤销",
            "invoke": "调用",
            "safely": "安全",
            "bucket": "桶",
            "count": "计数",
            "append": "追加",
            "base": "基础",
            "enrich": "补充",
            "merge": "合并",
            "nullable": "可空",
            "int": "整数",
            "integer": "整数",
            "channel": "渠道",
            "channels": "渠道",
            "callback": "回调",
            "notification": "通知",
            "notifications": "通知",
            "notify": "通知",
            "log": "日志",
            "logs": "日志",
            "rule": "规则",
            "rules": "规则",
            "status": "状态",
            "state": "状态",
            "summary": "汇总",
            "amount": "金额",
            "rate": "汇率",
            "rates": "汇率",
            "source": "来源",
            "config": "配置",
            "key": "密钥",
            "keys": "密钥",
            "cipher": "密文",
            "plain": "明文",
            "header": "请求头",
            "headers": "请求头",
            "body": "报文体",
            "response": "响应",
            "request": "请求",
            "http": "HTTP",
            "exception": "异常",
            "error": "错误",
            "blocked": "拦截",
            "result": "结果",
            "results": "结果",
            "risk": "风控",
            "decision": "结论",
            "merchant": "商户",
            "visible": "可见",
            "failure": "失败",
            "message": "说明",
            "code": "编码",
            "brand": "品牌",
            "bin": "BIN",
            "currency": "币种",
            "exponent": "小数位",
            "minor": "最小单位",
            "utc": "UTC",
            "time": "时间",
            "default": "默认",
            "conversion": "换汇",
            "duplicate": "重复",
            "initial": "初始",
            "totals": "累计金额",
            "join": "拼接",
            "first": "首个",
            "text": "文本",
            "mask": "脱敏",
            "masked": "脱敏",
            "sanitize": "清理",
            "clean": "清理",
            "record": "记录",
            "translate": "转换",
            "unwrap": "解包",
            "apply": "应用",
            "ensure": "确保",
            "assert": "断言",
            "expire": "失效",
            "logout": "登出",
            "sessions": "会话",
            "physical": "物理",
            "tables": "表",
            "range": "范围",
            "permission": "权限",
            "permissions": "权限",
            "menu": "菜单",
            "menus": "菜单",
            "tree": "树",
            "ids": "ID",
            "id": "ID",
            "created": "创建",
            "updated": "更新",
            "effective": "生效",
            "start": "开始",
            "end": "结束",
            "at": "时刻",
            "date": "日期",
            "zone": "时区",
            "seconds": "秒数",
            "minutes": "分钟数",
            "hours": "小时数",
            "duration": "耗时",
            "cost": "耗时",
            "priority": "优先级",
            "level": "等级",
            "reason": "原因",
            "scene": "场景",
            "strategy": "策略",
            "mode": "模式",
            "method": "方式",
            "type": "类型",
            "category": "类别",
            "scope": "范围",
            "flag": "标识",
            "enabled": "启用标识",
            "allowed": "允许标识",
            "required": "必需标识",
            "concurrent": "并发执行",
            "expression": "表达式",
            "locale": "语言区域",
            "country": "国家或地区",
            "city": "城市",
            "street": "街道",
            "postal": "邮编",
            "phone": "电话",
            "mobile": "手机号",
            "subject": "主题",
            "content": "内容",
            "snapshot": "快照",
            "version": "版本",
            "number": "编号",
            "no": "编号",
            "batch": "批次",
            "group": "分组",
            "job": "任务",
            "scheduler": "调度器",
            "trigger": "触发",
            "execute": "执行",
            "executor": "执行器",
            "node": "节点",
            "children": "子节点",
            "checked": "已选",
            "icon": "图标",
            "redirect": "重定向地址",
            "external": "外部",
            "link": "链接",
            "logical": "逻辑",
            "sharding": "分表",
            "column": "字段",
            "quarter": "季度",
            "year": "年份",
            "auto": "自动",
            "increment": "自增",
            "max": "最大",
            "min": "最小",
            "current": "当前",
            "raw": "原始",
            "final": "最终",
            "original": "原始",
            "rounding": "舍入",
            "scale": "小数位",
            "precision": "精度",
            "algorithm": "算法",
            "transformation": "算法转换",
            "bytes": "字节数",
            "bits": "位数",
            "random": "安全随机数生成器",
            "pattern": "正则模式",
            "formatter": "格式化器",
            "resolver": "解析器",
            "registry": "注册表",
            "provider": "提供方",
            "handlers": "处理器集合",
            "system": "系统",
            "payout": "代付",
            "payment": "支付",
            "checkout": "收银台",
            "authorization": "授权",
            "capture": "请款",
            "refund": "退款",
            "reversal": "冲正",
            "settlement": "结算",
            "clearing": "清分",
            "reserve": "保证金",
            "fee": "费用",
            "exchange": "汇率",
            "alert": "告警",
            "monitor": "监控",
            "security": "安全",
            "authentication": "认证",
            "card": "卡",
            "expiry": "有效期",
            "acquirer": "收单机构",
            "chargeback": "拒付",
            "review": "审核",
            "payload": "报文",
            "source": "来源",
            "funds": "资金",
            "provided": "支付工具",
            "openapi": "OpenAPI",
            "api": "API",
            "template": "模板",
            "material": "材料",
            "factory": "工厂",
            "crypto": "加解密",
            "audit": "审计",
            "download": "下载",
            "file": "文件",
            "copy": "副本",
            "jwt": "JWT",
            "replay": "防重放",
            "protection": "保护",
            "context": "上下文",
            "pending": "等待",
            "enum": "枚举",
            "health": "健康检查",
            "iso": "ISO",
            "metadata": "元数据",
            "schema": "结构定义",
            "item": "明细",
            "option": "选项",
            "options": "选项",
            "preview": "预览",
            "holder": "持有人",
            "billing": "账单",
            "client": "内部调用",
            "page": "页",
            "size": "大小",
            "name": "名称",
            "plan": "方案",
            "approval": "审批",
            "label": "标签",
            "delay": "延迟",
            "frequency": "频率",
            "day": "日",
            "days": "天数",
            "remark": "备注",
            "description": "说明",
            "total": "合计",
            "success": "成功",
            "successful": "成功",
            "failed": "失败",
            "processing": "处理中",
            "change": "变更",
            "method": "方式",
            "resource": "资源",
            "profile": "资料",
            "url": "URL",
            "restriction": "限制",
            "miss": "未命中",
            "match": "匹配",
            "locator": "分片定位信息",
            "vo": "响应视图",
            "hmac": "HMAC",
            "sha": "SHA",
            "prefix": "前缀",
            "draft": "草稿",
            "outbox": "Outbox",
            "orderly": "顺序投递",
            "serialized": "序列化报文",
            "field": "字段",
            "exact": "精确值",
            "action": "动作",
            "policy": "策略",
            "nonce": "随机数",
        }.get(word, word)
        for word in words
    ]
    return "".join(translated_words) if translated_words else "当前业务"


def layer(path: Path) -> str:
    rel = str(path.relative_to(ROOT))
    if rel.startswith("service-openapi/"):
        return "商户开放接口服务"
    if rel.startswith("service-payment/"):
        return "支付核心服务"
    if rel.startswith("service-risk/"):
        return "风控服务"
    if rel.startswith("service-gateway/"):
        return "网关服务"
    if rel.startswith("service-admin/"):
        return "运营后台服务"
    if rel.startswith("service-merchant/"):
        return "商户后台服务"
    if rel.startswith("service-job/"):
        return "调度任务服务"
    if rel.startswith("service-payout/"):
        return "代付服务"
    if rel.startswith("service-checkout/"):
        return "收银台服务"
    if rel.startswith("service-data/"):
        return "数据安全服务"
    if rel.startswith("service-clearing/"):
        return "清分服务"
    if rel.startswith("service-settlement/"):
        return "结算服务"
    if rel.startswith("channel-library/"):
        return "渠道适配库"
    if rel.startswith("component-library/"):
        return "公共组件库"
    if rel.startswith("finance-library/"):
        return "财务计算库"
    return "收单编排工程"


def source_hint(path: Path, owner: str, field: str, type_name: str) -> str:
    rel = str(path.relative_to(ROOT))
    lower_owner = owner.lower()
    lower_field = field.lower()
    lower_type = type_name.lower()
    if "request" in lower_owner or "dto" in lower_owner or "vo" in lower_owner or "response" in lower_owner:
        return "上游接口请求、内部服务调用或远程服务响应"
    if "controller" in lower_owner:
        return "构造器注入的应用服务或 HTTP 请求对象"
    if "client" in lower_owner or "restclient" in lower_owner:
        return "Spring 配置和构造器注入的内部客户端依赖"
    if "mapper" in lower_type or "service" in lower_type or "client" in lower_type or "template" in lower_type or "properties" in lower_type:
        return "Spring 容器构造器注入"
    if "entity" in rel or lower_owner.endswith("do") or lower_owner.endswith("entities"):
        return "数据库表记录或持久化写入对象"
    if "test" in rel:
        return "自动化测试夹具、Mock 对象或测试用例输入"
    if any(token in lower_field for token in ["trace", "request", "response", "callback"]):
        return "请求链路、回调链路或跨服务调用上下文"
    return "当前业务流程上游模型、配置项或数据库查询结果"


def relation_hint(field: str, owner: str, type_name: str) -> str:
    lower = field.lower()
    compact = lower.replace("_", "")
    lower_type = type_name.lower()
    if any(token in lower_type for token in ("list", "set", "collection", "map")):
        return "集合元素必须沿用所属模型的主键、币种、状态和数据范围口径"
    if lower == "merchantid":
        return "与 merchantOrderNo、transactionId 共同限定商户交易归属"
    if "merchantorder" in lower:
        return "与 merchantId、transactionId 共同支持幂等、查询和对账"
    if lower == "transactionid":
        return "与 operationId、merchantOrderNo 共同定位一笔平台交易"
    if lower == "operationid":
        return "与 transactionId、transactionType 共同定位一次交易动作"
    if "sourcetransactionid" in lower:
        return "与 transactionId 建立后续请款、退款、撤销和原交易之间的关联"
    if "amount" in lower:
        return "必须与 currency 或同名币种字段一起解释"
    if "currency" in lower:
        return "决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义"
    if "status" in lower:
        return "与时间字段、操作记录和状态历史共同描述当前处理阶段"
    if "channel" in lower and "id" in lower:
        return "与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录"
    if "callback" in lower:
        return "与 transactionId、operationId 和通知状态共同定位异步回调处理"
    if "trace" in lower:
        return "与日志 MDC 和 X-Trace-Id 请求头共同串联一次链路"
    if "page" in lower or "size" in lower or "limit" in lower:
        return "与查询条件和时间范围共同控制分页或扫描窗口"
    if compact in {"callchannel", "duplicate"}:
        return "与幂等结果和准备结果共同决定是否允许发起渠道调用"
    if compact == "currencyexponent":
        return "用于把主币种单位金额安全转换为该币种的最小货币单位"
    if compact == "idempotencykey":
        return "与商户号、交易类型和原交易共同限定重复请求的唯一范围"
    if compact in {"createtime", "updatetime", "createdat", "updatedat"}:
        return "与创建人、更新人和版本字段共同形成记录审计信息"
    return "与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障"


def sensitivity(field: str, type_name: str) -> str:
    lower = field.lower()
    words = lower_word_set(field)
    if words.intersection({"bytes", "bits", "length", "size", "algorithm", "transformation", "precision", "scale"}):
        return "非敏感字段"
    if any(token in lower for token in ["securitycode", "cvv", "cvc", "cavv", "privatekey", "aeskey", "apikey", "password", "secret", "authorization"]):
        return "高敏感字段，禁止明文打印日志，禁止写入异常消息"
    if lower in {"pan", "cardno", "cardnumber", "primaryaccountnumber"} or lower.endswith(("cardno", "cardnumber", "primaryaccountnumber")):
        return "银行卡敏感字段，只允许脱敏或摘要化使用"
    cryptographic_key = "key" in words and words.intersection({"private", "public", "aes", "api", "merchant", "encryption", "signing", "secret"})
    if words.intersection({"token", "jwt", "cert", "certificate", "signature", "ciphertext", "nonce", "iv"}) \
            or cryptographic_key or any(token in lower for token in ["encryptedkey", "ciphertext"]):
        return "敏感安全字段，日志只允许记录长度、摘要或掩码"
    identifiable_name = "name" in words and words.intersection({"payer", "holder", "cardholder", "customer", "first", "last", "operator"})
    if words.intersection({"email", "phone", "mobile", "address", "iban", "ip", "url", "street", "postal"}) or identifiable_name:
        return "可识别字段，日志输出必须脱敏或截断"
    return "非敏感字段"


def unit_format(field: str, type_name: str, static_final: bool = False) -> tuple[str, str, str]:
    lower = field.lower()
    compact = lower.replace("_", "")
    words = lower_word_set(field)
    clean_type = type_name.replace("final", "").strip()
    if static_final and clean_type in {"String", "char", "Character"}:
        return "无", "固定协议字面量或受控编码", "取值由当前类对接的协议、状态机或配置约定限定"
    if any(token in clean_type for token in ("List<", "Set<", "Collection<", "Map<")):
        return "无", "集合或键值映射", "元素类型和数量由所属请求、响应或聚合模型约束"
    if compact == "currencyexponent":
        return "位", "非负整数", "必须等于 ISO 4217 币种精度，禁止默认按 2 位处理"
    if "bytes" in words:
        return "字节", "正整数", "取值由算法协议或输入长度保护边界限定"
    if "bits" in words:
        return "位", "正整数", "取值由算法协议或数值精度边界限定"
    if "currency" in lower:
        return "无", "ISO 4217 三位大写币种代码", "取值必须来自平台支持币种"
    if words.intersection({"time", "date", "at", "created", "updated", "modified", "expire", "expiry"}) or clean_type in {"LocalDateTime", "LocalDate", "Date", "Instant"}:
        return "具体时刻使用系统约定业务时区，业务日期不附加时区", "ISO 日期或日期时间；持久化时刻保留毫秒精度", "时间范围由业务流程或查询条件限定"
    monetary_field = (
        bool(words.intersection({"amount", "balance"}))
        or lower in {"fee", "minimumfee", "maximumfee"}
        or lower.startswith(("percentagefee", "rawfee", "finalfee", "estimatednetsettlement"))
        or (clean_type == "BigDecimal" and "limit" in words)
    )
    if monetary_field:
        return "由关联 currency 字段决定", "decimal 金额字符串或 BigDecimal", "金额不得为负，交易金额通常必须大于 0"
    if "rate" in words or (clean_type == "BigDecimal" and lower.endswith("rate")):
        return "比例值", "decimal，按费率或汇率精度保存", "取值范围由费率、汇率或预警配置定义"
    if "country" in lower or lower.endswith("countrycode"):
        return "无", "ISO 国家或地区代码", "取值必须来自平台支持国家地区"
    if lower.endswith("id") or lower.endswith("no"):
        return "无", "业务编号字符串", "长度、唯一性和可空性由接口校验或数据库唯一约束限制"
    if "status" in lower or "type" in lower or "method" in lower or "mode" in lower or "code" in lower:
        return "无", "枚举编码或受控字符串", "取值必须来自对应枚举、字典或渠道协议"
    if compact in {"deleted", "enabled", "disabled", "active", "callchannel", "duplicate", "includedinfeetotal"}:
        return "无", "布尔值或 0/1 标识", "仅允许平台约定的真假取值"
    if clean_type in {"Integer", "Long", "int", "long"} or words.intersection({"count", "num", "index", "total", "size", "sort", "retry"}):
        return "个或次", "整数", "取值范围由数据库字段、校验注解或任务参数限制"
    if clean_type in {"Boolean", "boolean"} or lower.startswith(("is", "enable", "support")) or lower.endswith("enabled"):
        return "无", "布尔值或 0/1 开关", "仅允许平台约定的启停取值"
    if "url" in lower or "uri" in lower:
        return "无", "HTTP/HTTPS URL 或服务路径", "长度和协议由调用方校验"
    if "email" in lower:
        return "无", "邮箱地址或邮箱地址集合", "长度和格式由接口校验约束"
    if "phone" in lower or "mobile" in lower:
        return "无", "电话号码字符串", "长度和格式由接口校验约束"
    if "card" in lower and ("bin" in lower or "last4" in lower):
        return "无", "卡 BIN 或尾号字符串", "仅保存识别片段，不保存完整 PAN"
    if "json" in lower or clean_type in {"Map", "JSONObject"}:
        return "无", "JSON 字符串或结构化对象", "内容必须先脱敏再进入日志"
    return "无", "字符串、对象引用或集合结构", "取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束"


def field_summary(field: str, type_name: str, owner: str, static_final: bool = False) -> str:
    lower = field.lower()
    compact = lower.replace("_", "")
    display = field_label(field)
    owner_name = field_label(owner)
    exact_summaries = {
        "callchannel": "是否需要调用渠道；幂等命中、准备失败或本地终态结果均为 false。",
        "duplicate": "是否命中既有幂等结果；为 true 时必须复用原结果且禁止重复调用渠道。",
        "idempotencykey": "资金类请求幂等键，用于在同一商户和交易动作范围内识别重复提交。",
        "commanddto": "完成本地准备和字段归一后的支付命令，供渠道调用阶段使用。",
        "sourceorderdo": "后续交易关联的原交易主单快照，用于校验可操作状态、剩余金额和原渠道身份。",
        "routeresultdto": "本次交易锁定的渠道路由结果，后续渠道调用不得重新选择路由。",
        "preparedchannelrequestdto": "已完成金额、币种和渠道身份归一的渠道请求，仅用于本次渠道调用。",
        "resultdto": "无需调用渠道时直接返回的支付结果，例如幂等命中或准备阶段拒绝。",
        "currencyexponent": "交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。",
        "deleted": "逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。",
        "createtime": "记录创建时刻，持久化精度为毫秒。",
        "updatetime": "记录最后更新时间，持久化精度为毫秒。",
        "createby": "记录创建人账号标识，用于操作审计。",
        "updateby": "记录最后更新人账号标识，用于操作审计。",
        "operatorid": "执行本次管理操作的可信登录账号 ID，用于操作审计。",
        "operatorname": "执行本次管理操作时的账号显示名称快照，用于操作审计。",
        "sortno": "排序号，数值越小越优先展示或匹配。",
        "rulename": "费用规则名称，用于运营识别同一费用版本内的原子匹配规则。",
        "createdat": "记录创建时刻，持久化精度为毫秒。",
        "updatedat": "记录最后更新时间，持久化精度为毫秒。",
        "effectivetime": "业务配置或汇率开始生效的具体时刻。",
        "expiretime": "业务配置、令牌或缓存条目的失效时刻。",
        "transactiondatetime": "交易受理时刻，按交易业务时区解释并保留毫秒精度。",
        "transactionutctime": "交易受理时刻对应的 UTC 时间，用于跨时区排序和对账。",
        "transactiontimezone": "交易业务时区，使用 IANA 时区标识解释本地交易时间。",
        "channelorderno": "渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。",
        "batchno": "批次号，用于关联同一次导入、抓取、清分或结算处理的记录。",
        "versionno": "业务版本号，用于区分同一配置或方案的不可变版本。",
        "errormessage": "内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。",
        "cardbrand": "卡品牌编码，用于渠道能力匹配、路由和运营展示。",
        "paymentbrand": "支付品牌编码，用于区分银行卡、钱包或本地支付品牌。",
        "locale": "语言区域标识，用于选择国际化文案和导出标题。",
        "priority": "匹配优先级，数值越小越优先。",
        "timeoutseconds": "远程调用超时时间，单位为秒。",
        "risklevel": "风险等级，用于运营展示、审核和处置优先级判断。",
        "calculationcontext": "财务计算统一 MathContext，约束中间计算精度并避免过早舍入。",
        "onehundred": "百分比换算基数 100，用于把百分数转换为比例值。",
        "rateroundingmode": "汇率归一时的统一舍入模式，仅在锁定精度边界使用。",
        "lockedrateprecision": "结算锁定汇率的有效数字精度。",
        "lockedratescale": "结算锁定汇率保留的小数位数，至少满足结算汇率精度要求。",
        "ivbytes": "AES-GCM 随机 IV 字节数，每次加密必须重新生成。",
        "gcmivbytes": "AES-GCM 随机 IV 字节数，每次卡数据封装必须重新生成。",
        "tagbits": "AES-GCM 认证标签位数，用于同时校验密文完整性。",
        "gcmtagbits": "AES-GCM 认证标签位数，用于同时校验卡数据密文完整性。",
        "aeskeybytes": "一次性 AES 数据密钥字节数。",
        "maxciphertextbytes": "允许解密的最大密文长度，用于限制异常报文和内存占用。",
        "aestransformation": "卡数据对称加密算法标识，固定使用带认证的 AES-GCM。",
        "rsatransformation": "一次性 AES 密钥封装算法标识。",
        "algorithm": "卡数据混合加密协议标识，调用双方必须使用完全一致的算法组合。",
        "securerandom": "密码学安全随机数生成器，用于生成一次性 AES 密钥和 GCM IV。",
        "feecategory": "费用类别，用于区分交易手续费、退款费、风控费、争议费和结算换汇费。",
        "riskservicetype": "风控服务类型，用于区分内部风控、外部风控和 3DS 服务费用。",
        "chargetrigger": "计费触发点，明确费用在请求、成功、失败或其它受控事件发生时计提。",
        "feemode": "费用计算模式，决定当前规则采用标准费率还是阶梯费率。",
        "percentagerate": "百分比费率数值，例如 2.3 表示 2.3%；按标签币种和标签金额计提。",
        "fixedamountusd": "固定单笔费，币种恒为 USD，与百分比费用相加后再应用最低和最高限制。",
        "minimumamountusd": "单笔最低费用，币种恒为 USD；为空表示不设置最低限制。",
        "maximumamountusd": "单笔最高费用，币种恒为 USD；为空表示不设置最高限制。",
        "tiermetric": "阶梯累计指标，用于区分月累计交易笔数和 USD 归一交易金额。",
        "tierperiod": "阶梯累计周期，当前用于声明费用阶梯按哪个统计周期重置。",
        "matchedruleid": "本次费用计算命中的规则主键，用于审计和复现计算过程。",
        "matchedtierid": "本次费用计算命中的阶梯主键；标准费率或未命中阶梯时为空。",
        "percentagefeelabel": "按标签金额计算出的百分比费用，尚未换算为 USD，也未应用固定费和上下限。",
        "percentagefeecurrency": "百分比费用币种，与交易标签币种保持一致。",
        "rawfeeusd": "百分比费用换算为 USD 后与固定单笔费相加得到的原始费用，尚未应用最低和最高限制。",
        "finalfeeusd": "应用最低和最高限制后的最终费用，币种恒为 USD。",
        "appliedlimit": "费用上下限应用结果，用于标识未触发限制、命中最低费用或命中最高费用。",
        "formulasnapshot": "费用计算公式快照，用于运营展示和事后审计，不作为重新计算的输入。",
        "netsettlementformulasnapshot": "净结算金额公式快照，用于解释费用和保证金如何影响预计净入账金额。",
        "reserveamountusd": "试算保证金金额，按规则计算并换算为 USD，仅用于预览不产生资金流水。",
        "estimatednetsettlementusd": "预计净结算金额，币种为 USD，仅用于费用试算展示。",
        "includedinfeetotal": "是否计入费用合计；1 表示计入，0 表示仅展示该费用明细。",
        "settlementratesource": "结算试算汇率来源编码，用于追踪本次标签币种换算到 USD 的报价来源。",
        "sourcefingerprint": "冲正来源事实指纹，用于复核时确认原批次、净结果和资金流水未被替换。",
        "originalbatchversion": "申请冲正时读取的原结算批次版本号，用于复核阶段执行乐观一致性校验。",
        "originalnetresultitemid": "原批次净入账结果明细主键，用于把冲正申请绑定到唯一结算结果。",
        "originalfundledgerid": "原结算资金流水主键，用于防止同一入账流水被重复冲正。",
        "submittedrolesnapshot": "冲正申请人提交时的角色快照，用于 Maker-Checker 审计。",
        "decidedrolesnapshot": "冲正复核人决策时的角色快照，用于 Maker-Checker 审计。",
        "submitclientip": "冲正申请提交端 IP 快照，仅用于安全审计，展示和日志必须脱敏。",
        "decisionclientip": "冲正复核端 IP 快照，仅用于安全审计，展示和日志必须脱敏。",
        "submituseragent": "冲正申请提交端 User-Agent 快照，用于安全审计并限制长度。",
        "decisionuseragent": "冲正复核端 User-Agent 快照，用于安全审计并限制长度。",
        "originalbatchrequired": "是否必须关联原结算批次；冲正批次为 true，普通结算批次为 false。",
        "retryable": "失败是否允许重试；仅瞬时依赖故障可重试，业务校验和状态冲突不可重试。",
        "failurecode": "处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。",
        "stage": "结算失败阶段，用于确定补偿入口并防止跨阶段重复执行。",
        "expiresatnanos": "缓存条目的单调时钟过期点，仅用于进程内过期判断，不可解释为墙上时间。",
        "lastaccessorder": "缓存条目最近访问顺序，用于容量淘汰，不参与商户安全材料版本判断。",
        "pageno": "查询页码，从 1 开始。",
        "pagesize": "每页记录数，由接口上限约束避免无界查询。",
        "amounts": "按币种归集的交易金额汇总，用于列表统计展示。",
        "successamounts": "按币种归集的成功交易金额汇总。",
        "failedamounts": "按币种归集的失败交易金额汇总。",
        "amountsummaries": "按币种拆分的金额汇总集合，禁止直接跨币种相加。",
        "successamountsummaries": "按币种拆分的成功金额汇总集合，禁止直接跨币种相加。",
        "failedamountsummaries": "按币种拆分的失败金额汇总集合，禁止直接跨币种相加。",
        "currencyamounts": "按币种拆分的金额集合，禁止直接跨币种相加。",
    }
    if compact in exact_summaries:
        return exact_summaries[compact]
    if "merchantid" in lower:
        return "商户号，用于限定商户配置、交易数据、风控规则和权限归属。"
    if "merchantorderno" in lower:
        return "商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。"
    if "merchantorderid" in lower:
        return "商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。"
    if "transactionid" in lower and "source" not in lower:
        return "平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。"
    if "operationid" in lower:
        return "平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。"
    if "sourcetransactionid" in lower:
        return "原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。"
    if lower == "id":
        return f"{owner_name} 数据库主键，用于唯一标识当前记录。"
    if "unknownroute" in lower:
        return "未知路由占位值，用于网关未匹配到 Route 时保持日志字段稳定。"
    if "paymentinfoid" in lower:
        return "支付工具信息编号，用于关联交易支付工具摘要与对应交易主单、动作单和支付方式记录。"
    if "tokenid" in lower:
        return "支付令牌编号，用于关联渠道或钱包返回的 token 化支付凭据，不保存原始卡号。"
    if lower.endswith("id") and "transaction" not in lower and "operation" not in lower:
        return f"{display}，用于定位 {owner_name} 关联的上游配置、渠道、账号、角色或业务记录。"
    if "amount" in lower:
        return f"{display}，表示当前交易、费用、限额或统计口径下的金额值。"
    if "currency" in lower:
        return f"{display}，表示金额字段使用的币种。"
    if "status" in lower:
        return f"{display}，表示当前记录在业务流程中的处理状态。"
    if "transactiontype" in lower:
        return "交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。"
    if "businesstype" in lower:
        return "业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。"
    if "ruletype" in lower:
        return "规则类型，用于区分风控、限额、预警或通知规则的匹配逻辑。"
    if "notifytype" in lower:
        return "通知类型，用于区分交易成功、失败、处理中或补偿重试等商户通知场景。"
    if "callbacktype" in lower or "channeleventtype" in lower:
        return "回调事件类型，用于区分渠道授权、请款、退款、撤销和状态同步事件。"
    if "providertype" in lower:
        return "服务提供方类型，用于区分邮箱、短信、汇率或其它外部服务供应商。"
    if "encryptiontype" in lower:
        return "加密类型，用于声明密钥材料、邮件通道或开放接口报文采用的加密方式。"
    if "ratetype" in lower:
        return "汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。"
    if "sourcetype" in lower:
        return "数据来源类型，用于区分手工维护、文件导入、外部抓取或系统初始化数据。"
    if "scopetype" in lower:
        return "作用域类型，用于限定模板、配置、通知或权限记录适用的平台、商户或业务范围。"
    if "contenttype" in lower:
        return "内容类型，用于区分文本、HTML、附件、JSON 或导出文件等载荷格式。"
    if "triggertype" in lower:
        return "触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。"
    if "accesstype" in lower:
        return "访问类型，用于区分登录、查询、导出或配置变更等审计场景。"
    if lower.startswith("support") or lower.endswith("supported"):
        return f"{display}，表示当前渠道、配置或接口是否支持对应能力。"
    if lower.endswith("enabled") or lower.startswith("enable") or lower.startswith("disable"):
        return f"{display}，表示当前配置项或业务能力的启停开关。"
    if "type" in lower:
        return f"{display}，用于区分 {owner_name} 记录的处理类别、配置维度或外部协议枚举。"
    if "method" in lower:
        return f"{display}，表示支付方式、通知方式或调用方式。"
    if "channelcode" in lower:
        return "渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。"
    if "channelmid" in lower or "mid" in lower:
        return f"{display}，用于定位渠道商户号配置或渠道侧 MID。"
    if "request" in lower and "url" in lower:
        return f"{display}，表示当前内部调用、渠道调用或商户通知的目标地址。"
    if "url" in lower:
        return f"{display}，表示回调、通知、来源站点或远程接口地址。"
    if "traceid" in lower:
        return "链路追踪号，用于在网关、OpenAPI、支付核心、MQ 和任务日志之间关联一次请求。"
    if "retrycount" in lower:
        return "重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。"
    if "page" in lower or "limit" in lower or "size" in lower:
        return f"{display}，用于控制分页查询、批量扫描或任务单次处理规模。"
    if "keyword" in lower:
        return f"{display}，用于按名称、编码或说明文本进行模糊查询。"
    if "remark" in lower or "description" in lower:
        return f"{display}，用于保存人工备注、交易说明或配置补充说明。"
    if "sort" in lower:
        return f"{display}，用于控制列表展示或规则匹配时的排序优先级。"
    if "name" in lower:
        return f"{display}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。"
    if "code" in lower:
        return f"{display}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。"
    if "path" in lower:
        return f"{display}，表示接口路径、资源路径或路由匹配路径。"
    if "host" in lower or "domain" in lower:
        return f"{display}，表示远程服务主机、商户域名或渠道访问域名。"
    if "headers" in lower or "header" in lower:
        return f"{display}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。"
    if "body" in lower or "payload" in lower:
        return f"{display}，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。"
    if "digest" in lower or "hash" in lower or "fingerprint" in lower:
        return f"{display}，用于以不可逆摘要关联敏感原文或大报文。"
    if "country" in lower:
        return f"{display}，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。"
    if "email" in lower:
        return f"{display}，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。"
    if "phone" in lower or "mobile" in lower:
        return f"{display}，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。"
    if lower in {"firstname", "lastname", "fullname"}:
        return f"{display}，表示自然人姓名组成部分，展示和日志输出必须脱敏。"
    if lower in {"state", "province", "city", "street", "postal", "postalcode", "address"}:
        return f"{display}，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。"
    if "timezone" in lower:
        return f"{display}，使用 IANA 时区标识解释关联的本地日期时间。"
    if "version" in lower:
        return f"{display}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。"
    if "count" in lower_word_set(field) or "total" in lower_word_set(field):
        return f"{display}，表示当前统计、分页、扫描或重试场景中的数量。"
    if "cardno" in lower or "cardnumber" in lower:
        return f"{display}，表示银行卡号或脱敏卡号字段。"
    if "cardbin" in lower:
        return "卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。"
    if "last4" in lower:
        return "银行卡尾号，用于交易查询、客服核验和持卡人侧展示。"
    if "securitycode" in lower or lower in {"cvv", "cvc", "cavv"}:
        return f"{display}，表示卡组织或 3DS 认证链路使用的安全认证值。"
    if "expirymonth" in lower:
        return "银行卡有效期月份，用于渠道授权请求和卡片有效性校验，不应单独作为持卡人认证凭据。"
    if "expiryyear" in lower:
        return "银行卡有效期年份，用于渠道授权请求和卡片有效性校验，不应单独作为持卡人认证凭据。"
    if "threeds" in lower:
        return f"{display}，表示 3DS 认证状态、版本或结果标识，用于渠道授权和风控判断。"
    if lower.startswith("csc") or lower.startswith("avs"):
        return f"{display}，表示渠道返回的安全码或地址校验结果，用于交易风险判断和排障展示。"
    if "template" in lower:
        return f"{display}，用于定位邮件、通知或渠道参数模板。"
    if "mapper" in type_name.lower() or "service" in type_name.lower() or "client" in type_name.lower() or "template" in type_name.lower():
        return f"{display} 依赖，用于 {owner_name} 调用对应的数据访问、远程调用或领域服务能力。"
    subject = business_subject(lower_words(field))
    label = subject if not re.search(r"[a-z]{2,}", subject, re.IGNORECASE) else f"{{@code {field}}}"
    lower_type = type_name.lower()
    lower_owner = owner.lower()
    if static_final:
        return f"{label}常量，统一 {owner_name} 内部使用的配置值、状态码或协议字段。"
    if lower_type in {"boolean", "bool"} or lower_type.endswith("boolean"):
        return f"{label}，用于明确 {owner_name} 当前业务分支是否成立。"
    if any(token in lower_type for token in ["list", "set", "collection", "map"]):
        return f"{label}集合，承载 {owner_name} 当前请求或响应中的多值数据。"
    if lower_owner.endswith(("request", "query", "command")):
        return f"请求中的{label}，用于限定本次操作的输入和校验范围。"
    if lower_owner.endswith(("response", "vo", "summary", "detail")):
        return f"响应中的{label}，用于管理端或商户端展示当前处理结果。"
    if lower_owner.endswith(("do", "entity", "record")):
        return f"持久化的{label}，用于还原当前记录的业务事实。"
    return f"{label}字段，保存 {owner_name} 当前处理所需的业务取值。"


def field_javadoc(field: str, type_name: str, owner: str, path: Path, indent: str, static_final: bool) -> list[str]:
    unit, fmt, scope = unit_format(field, type_name, static_final)
    nullable = "不允许为空" if static_final or re.search(r"@Not(?:Blank|Null|Empty)", "".join(indent)) else "是否允许为空由接口校验、数据库约束或调用契约决定"
    relation = relation_hint(field, owner, type_name)
    lines = [
        field_summary(field, type_name, owner, static_final),
        "<p>",
        f"单位：{unit}；格式：{fmt}；{nullable}；{sensitivity(field, type_name)}。",
        f"取值范围：{scope}；数据来源：{source_hint(path, owner, field, type_name)}。",
    ]
    if relation != "与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障":
        lines.append(f"字段关系：{relation}。")
    lines.append("</p>")
    return javadoc(lines, indent)


def enum_value_javadoc(name: str, owner: str, indent: str) -> list[str]:
    words = human_name(name)
    return javadoc([
        f"{words} 枚举值，表示 {human_name(owner)} 中的受控取值。",
        "<p>",
        "单位：无；格式：枚举常量；非敏感字段；不允许使用未声明的状态或类型替代该值。",
        "</p>",
    ], indent)


def class_description(path: Path, kind: str, name: str) -> str:
    module = layer(path)
    lower = name.lower()
    display = human_name(name)
    words = lower_words(name)
    subject_words = re.sub(
        r"\b(application service|service impl|service|rest client|client|controller|"
        r"query request|save request|create request|update request|delete request|status request|"
        r"request|response|result|dto|vo|do|entity|entities|enum|export row|test|tests)\b",
        "",
        words,
    )
    subject = business_subject(re.sub(r"\s+", " ", subject_words).strip())
    if subject == "当前业务":
        subject = business_subject(words)
    if kind == "enum":
        return f"{subject}枚举，位于 {module}，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。"
    if kind == "@interface":
        return f"{display} 注解，位于 {module}，用于声明拦截、鉴权、审计或框架扩展元数据，由运行时组件读取并执行对应规则。"
    if kind == "record":
        return f"{subject}不可变数据结构，位于 {module}，在当前调用链中传递固定字段集合，不承担状态写入职责。"
    container = path.stem.lower()
    if "payment-channel-mpgs" in str(path) and name != path.stem:
        direction = "请求" if "request" in container else "响应"
        return f"MPGS {direction}报文的{subject}节点模型，位于渠道适配库，只映射渠道协议字段，不决定平台交易状态。"
    if "controller" in lower:
        return f"{subject} HTTP 控制器，位于 {module}，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。"
    if lower.endswith("dtos") or lower.endswith("entities"):
        return f"{display} 聚合类型，位于 {module}，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。"
    if lower.endswith("query") or lower.endswith("queryrequest"):
        return f"{subject}查询条件模型，位于 {module}，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。"
    if lower.endswith("saverequest") or lower.endswith("createrequest") or lower.endswith("updaterequest"):
        return f"{subject}写操作请求模型，位于 {module}，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。"
    if lower.endswith("deleterequest"):
        return f"{display} 删除请求模型，位于 {module}，承载批量删除、软删除或停用操作所需的记录标识。"
    if lower.endswith("statusrequest"):
        return f"{display} 状态变更请求模型，位于 {module}，承载启停、冻结、审核或处理状态更新所需字段。"
    if "applicationservice" in lower:
        return f"{subject}应用服务，位于 {module}，编排可信登录上下文、权限、领域服务调用和响应模型组装。"
    if "serviceimpl" in lower:
        return f"{subject}服务实现，位于 {module}，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。"
    if lower.endswith("service"):
        return f"{subject}服务契约，位于 {module}，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。"
    if "restclient" in lower or lower.endswith("client"):
        return f"{display} 客户端，位于 {module}，封装内部服务或渠道接口调用，统一处理请求构造、响应解析、超时和异常转换。"
    if "advice" in lower:
        return f"{display} MVC 扩展组件，位于 {module}，在请求体读取或响应写出阶段执行解密、加密、校验、摘要记录和上下文回填。"
    if "extractor" in lower:
        return f"{display} 提取组件，位于 {module}，从请求、响应或配置中读取关键字段，完成标准化、校验和脱敏日志准备。"
    if "decoder" in lower:
        return f"{display} 解码组件，位于 {module}，解析加密或外部协议报文，转换为内部 DTO 并保持异常边界清晰。"
    if "validator" in lower:
        return f"{display} 校验组件，位于 {module}，执行参数、状态、权限或配置规则校验，失败时返回统一异常。"
    if "converter" in lower:
        return f"{display} 转换组件，位于 {module}，在接口模型、领域对象、数据库记录或消息载荷之间复制并规范化字段。"
    if "resolver" in lower:
        return f"{display} 解析组件，位于 {module}，根据请求路径、配置、分表条件或协议字段解析后续处理需要的标准结果。"
    if lower.endswith("dto") or lower.endswith("request") or lower.endswith("response") or lower.endswith("vo"):
        if "request" in lower or lower.endswith("commanddto"):
            return f"{subject}请求模型，位于 {module}，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。"
        if "response" in lower or lower.endswith(("resultdto", "vo")):
            return f"{subject}响应模型，位于 {module}，向调用方展示处理结果和必要业务事实，不暴露持久化实体。"
        return f"{subject}传输模型，位于 {module}，用于接口或跨层传递该业务数据，不承担状态写入职责。"
    if lower.endswith("do") or "entities" in lower:
        return f"{subject}持久化模型，位于 {module}，映射数据库中的业务事实、状态、版本和审计字段，不作为外部接口模型。"
    if "mapper" in lower:
        return f"{display} 映射组件，位于 {module}，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。"
    if "filter" in lower or "interceptor" in lower:
        return f"{display} 拦截组件，位于 {module}，处理请求链路中的 traceId、鉴权、来源信息、上下文绑定或安全校验。"
    if "consumer" in lower or "listener" in lower:
        return f"{display} 消息消费组件，位于 {module}，解析 MQ 消息、绑定 traceId 和重试次数，并触发后续业务处理。"
    if "producer" in lower or "publisher" in lower:
        return f"{display} 消息投递组件，位于 {module}，补齐消息标识、traceId、重试次数和业务载荷后发送 MQ。"
    if "job" in lower or "handler" in lower:
        return f"{display} 任务组件，位于 {module}，执行定时扫描、分片调度、补偿处理或后台同步，并记录任务执行结果。"
    if "config" in lower or "configuration" in lower:
        return f"{display} 配置类，位于 {module}，注册当前模块运行所需 Bean、拦截器、客户端或配置属性。"
    if "properties" in lower:
        return f"{display} 配置属性模型，位于 {module}，绑定 application 配置项并提供运行时默认值。"
    if "utils" in lower or "util" in lower:
        return f"{display} 通用函数集合，位于 {module}，封装格式化、校验、脱敏、加密、编码或标准化逻辑，调用方以静态方法获取本地计算结果。"
    if "test" in lower:
        return f"{subject}自动化测试，验证对应生产能力的正常路径、异常边界和关键回归场景。"
    if lower.endswith("exportrow"):
        return f"{subject}导出行模型，位于 {module}，定义 Excel 列及运营可见值，不承载数据库写入规则。"
    if name != path.stem:
        return f"{subject}嵌套数据模型，位于 {module}，限定所属聚合内该对象的字段集合和传递边界。"
    return f"{subject}协作组件，位于 {module}，封装该业务的本地校验、转换或运行时协作入口。"


def replace_description(block: str, path: Path, kind: str, name: str) -> str:
    lines = block.splitlines()
    desc = class_description(path, kind, name)
    return "\n".join(
        re.sub(r"(@description\s*:\s*).*", rf"\1{desc}", line) if "@description" in line else line
        for line in lines
    ) + ("\n" if block.endswith("\n") else "")


def params_from_signature(params: str) -> list[str]:
    clean = re.sub(r"@[A-Za-z_][A-Za-z0-9_$.]*(?:\([^)]*\))?", "", params)
    result: list[str] = []
    for part in clean.split(","):
        part = part.strip()
        if not part:
            continue
        part = re.sub(r"\bfinal\s+", "", part)
        tokens = part.replace("...", "[]").split()
        if tokens:
            name = re.sub(r"[^A-Za-z0-9_].*", "", tokens[-1].replace("[]", ""))
            if name and name not in result:
                result.append(name)
    return result


def method_summary(name: str, owner: str) -> str:
    words = lower_words(name)
    owner_display = human_name(owner)
    lower = name.lower()
    exact_summaries = {
        "all": "创建可访问全部商户的管理端数据范围。",
        "limited": "创建仅允许访问指定商户集合的管理端数据范围。",
        "empty": "判断当前数据范围是否不允许访问任何商户。",
        "searchcandidates": "分页查询当前操作人数据范围内的结算候选。",
        "candidatedetail": "查询指定结算候选详情，并校验来源类型和商户数据范围。",
        "searchreviews": "分页查询当前操作人数据范围内的结算预审单。",
        "reviewdetail": "查询指定结算预审单详情，并校验商户数据范围。",
        "requirecandidateaccess": "校验当前操作人是否有权访问指定结算候选。",
        "requirereviewaccess": "校验当前操作人是否有权访问指定结算预审单。",
        "searchresultitems": "分页查询正式结算批次的交易结果明细。",
        "searchreserveitems": "分页查询正式结算批次的保证金结算明细。",
        "searchpostings": "分页查询正式结算批次对应的资金入账流水。",
        "recordchannelresult": "记录渠道返回的 3DS 或其它支付认证结果。",
        "recordchannelfailure": "记录渠道认证失败事实，保留可审计的失败码和脱敏摘要。",
        "recordtimeout": "记录渠道认证超时事实，供状态机和运营排障使用。",
        "markthreedsindicator": "在交易认证信息中记录 3DS 指示值，不改变交易金额和币种。",
        "encrypt": "使用版本化密文格式加密敏感字段，并为每次加密生成独立随机 IV。",
        "decrypt": "校验密文版本和结构后解密敏感字段；非法密文直接失败，不返回部分明文。",
        "normalize": "将输入归一为当前类型接受的标准格式。",
        "from": "将支付核心内部结果转换为 OpenAPI 响应模型，并保持商户可见状态语义一致。",
        "miss": "创建未命中卡 BIN 缓存的占位结果，避免把空值写入缓存。",
        "detail": "查询指定业务单据详情，并执行调用方数据范围校验。",
        "requireaccess": "校验当前操作人是否有权访问指定业务单据。",
        "rolegranttreetemplate": "构建角色授权树空白模板，供新增角色时展示全部可授权节点。",
        "rolemenus": "查询指定角色已授权的菜单集合。",
        "rolepermissions": "查询指定角色已授权的权限集合。",
        "writerequest": "将 Worldpay 请求对象序列化为符合 WPGXML 协议的 XML 文本。",
        "duplicate": "创建命中幂等结果的准备结果，明确禁止再次调用渠道。",
        "completeincrementalauthorizationchannelresult": "将增量授权渠道结果写入交易主单、动作单和认证事实，并按状态机推进交易状态。",
        "ruleoptions": "查询当前管理页面可选择的渠道告警规则选项。",
        "acknowledgeevent": "确认指定渠道告警事件并记录可信操作人审计信息。",
        "direct": "构造用于固定地址访问的无代理内部 HTTP 客户端。",
        "loadbalanced": "构造通过服务发现解析目标实例的负载均衡内部 HTTP 客户端。",
        "channelcodes": "返回当前渠道适配器或验签器明确支持的渠道编码集合。",
        "registeredverifiers": "返回按规范化渠道编码注册的回调验签器只读视图。",
        "fresh": "读取当前缓存代际对应的最新业务值。",
        "stableid": "根据稳定业务身份生成可重复计算的幂等标识。",
        "tompgsrequest": "将平台统一支付请求映射为 MPGS 协议请求，统一校验金额、币种、卡信息和 3DS 字段。",
        "selectactivemerchantscopes": "查询账号生效角色对应的商户数据范围，用于构建可信管理端访问边界。",
        "selecthashmatch": "按不可逆摘要查询当前生效且商户范围优先的风控名单命中记录。",
        "selectiprangematch": "按 IP 版本和数值区间查询当前生效的风控名单命中记录。",
        "selectamlsourcehostmatch": "按来源主机精确查询当前生效的 AML 风控名单命中记录。",
        "selectissuercountrybycardbin": "按卡 BIN 最长区间优先规则解析发卡国家或地区。",
        "findfresh": "绕过方法级缓存读取当前数据源中的最新业务值。",
        "tosubmerchantinfovo": "将内部或 OpenAPI 子商户信息转换为对外响应视图，并限制敏感字段集合。",
        "tooffsetdatetime": "按指定 IANA 时区把本地日期时间转换为带 UTC 偏移的时间。",
        "normalizerate": "将非空汇率统一保留八位小数，使用 HALF_UP 舍入。",
        "findsourceurlrule": "查询当前商户可用的来源主机白名单规则，命中时返回受控放行结论。",
        "findsourceurlrestrictionmiss": "判断来源主机是否未命中商户白名单，并返回对应风控处置规则。",
        "selectcardbinrangematch": "按卡 BIN 数值区间查询当前生效且商户范围优先的风控名单记录。",
        "cardno": "规范化卡号并生成只包含哈希或脱敏片段的风控查询值。",
        "ip": "规范化 IP 地址并生成数值区间或摘要形式的风控查询值。",
        "email": "规范化邮箱后生成不可逆摘要形式的风控查询值。",
        "sourcehost": "规范化来源 URL 主机名，生成只用于白名单匹配的风控查询值。",
        "reservesnapshothash": "对冻结的保证金配置快照计算 SHA-256 摘要，用于校验清分事实未被替换。",
        "clearingstatus": "把清分状态规范化为受控指标标签，未知值统一归入 OTHER，避免指标基数失控。",
        "currentpolicycode": "返回当前生效的退款审批策略编码；空配置按 NONE 处理。",
        "merchantvisiblefailuremessage": "根据交易状态和受控失败码生成商户可见失败说明，不暴露渠道或内部异常细节。",
        "constanttimeequals": "使用常量时间摘要比较校验费用快照哈希，避免普通字符串比较泄露时序差异。",
        "key": "按系统、环境和业务片段构造规范化 Redis Key，忽略空片段并使用安全默认值。",
        "monthkey": "把年份和月份转换为 ISO YearMonth 缓存键。",
        "noncekey": "对收银台会话号和一次性随机数取摘要，构造防重放 Redis Key。",
        "newnonce": "使用密码学安全随机数生成 URL-safe 的一次性 nonce。",
        "overridesettlementcurrency": "把可信商户配置中的结算币种写入 OpenAPI 账单响应，不使用请求参数覆盖。",
        "shouldusedefaultrejectedmessage": "判断渠道或内部失败说明是否缺失或仍为通用 Rejected，以决定是否回退统一商户文案。",
        "normalizecreateactiontotals": "按初始支付、授权或预授权终态归一累计授权、请款和退款金额。",
        "isinitialcreateaction": "判断交易类型是否属于支付、授权或预授权等初始创建动作。",
        "cleanuplocalnonces": "清理进程内已经过期的一次性 nonce，避免降级防重放集合无界增长。",
        "defaultzero": "将可空金额归一为零值，不改变非空金额的精度和符号。",
    }
    if lower in exact_summaries:
        return exact_summaries[lower]
    if lower == owner.lower():
        return f"创建 {owner_display} 实例并校验构造参数。"
    subject = words
    for prefix in ("list ", "page ", "query ", "find ", "get ", "load ", "select ", "search ",
                   "create ", "save ", "insert ", "add ", "register ", "update ", "modify ",
                   "change ", "edit ", "delete ", "remove ", "validate ", "check ", "verify ",
                   "require ", "build ", "to ", "convert ", "map ", "fill ", "send ", "publish ",
                   "notify ", "dispatch ", "handle ", "process ", "execute ", "consume ",
                   "reset ", "assign ", "grant ", "replace ", "softdelete ", "flatten ", "safe ",
                   "count ", "enrich ", "merge ", "append ", "invoke ", "submit ", "reject ",
                   "copy ", "preview ", "resend ", "decrypt ", "generate ", "read ",
                   "resolve ", "parse ", "normalize ", "calculate ", "override ",
                   "cleanup ", "default ", "set ", "with ", "prepare ", "resume ",
                   "fail ", "complete ", "freeze ", "continue ", "refresh ", "encrypt ",
                   "sign ", "canonical ", "current ", "new ", "on "):
        if words.startswith(prefix):
            subject = words[len(prefix):]
            break
    subject_label = business_subject(subject)
    if not subject.strip() or subject_label == "当前业务":
        subject_label = "符合条件的业务记录"
    if re.search(r"[a-z]{2,}", subject_label, re.IGNORECASE):
        subject_label = f"{{@code {name}}}"
    if lower.startswith(("list", "page", "query", "find", "get", "load", "select", "search")):
        return f"查询{subject_label}；筛选条件、分页上限和数据范围由方法参数共同限定。"
    if lower.startswith(("create", "save", "insert", "add", "register", "generate")):
        return f"创建{subject_label}，完成必要校验后写入或委托下游服务处理。"
    if lower.startswith(("update", "modify", "change", "edit", "reset", "assign", "grant", "replace", "softdelete")):
        return f"更新{subject_label}，保持业务状态、配置项或展示字段与请求意图一致。"
    if lower.startswith("exempt"):
        return f"为{subject_label}设置受控豁免，并保留操作原因和审计上下文。"
    if lower.startswith("disable"):
        return f"停用{subject_label}，使后续请求不再使用该能力。"
    if lower.startswith("unlock"):
        return f"解除{subject_label}的锁定状态，恢复符合条件的后续操作。"
    if lower.startswith(("delete", "remove")):
        return f"删除或停用{subject_label}，调用方需保证权限和状态允许该操作。"
    if lower.startswith(("validate", "check", "verify", "require", "ensure", "assert")):
        return f"校验{subject_label}输入，发现缺失、越权或格式错误时中断当前流程。"
    if lower.startswith(("build", "to", "convert", "map", "fill", "copy", "enrich", "merge", "append", "flatten")):
        return f"构造{subject_label}对象，完成字段复制、格式标准化和敏感数据处理。"
    if lower.startswith(("send", "publish", "notify", "dispatch", "submit", "invoke", "resend")):
        return f"发送{subject_label}消息或请求，补齐目标地址、链路标识和业务载荷。"
    if lower.startswith(("encrypt", "decrypt", "sign", "hmac")):
        return f"处理{subject_label}安全计算，严格沿用当前算法、密钥边界和敏感日志约束。"
    if lower.startswith(("prepare", "freeze")):
        return f"准备{subject_label}，在执行外部动作前冻结必要事实并完成幂等与状态校验。"
    if lower.startswith(("resume", "continue")):
        return f"恢复{subject_label}处理，复用既有幂等身份并从已持久化阶段继续执行。"
    if lower.startswith(("fail", "complete")):
        return f"完成{subject_label}状态收口，通过既有状态机和版本条件写入终态或失败事实。"
    if lower.startswith("refresh"):
        return f"刷新{subject_label}，使后续读取切换到最新且已验证的数据版本。"
    if lower.startswith(("canonical", "fingerprint")):
        return f"构造{subject_label}的规范化指纹，用于稳定识别重复请求且不暴露敏感原文。"
    if lower.startswith("on"):
        return f"响应{subject_label}事件，按所属服务的事务提交顺序执行后续联动。"
    if lower.startswith(("handle", "process", "execute", "consume")):
        return f"处理{subject_label}流程，串联校验、状态判断和后续业务动作。"
    if lower.startswith(("record", "log", "audit")):
        return f"记录{business_subject(words[len(split_words(name)[0]):] if len(split_words(name)) > 1 else words)}，写入安全、审计或链路排障所需的脱敏上下文。"
    if lower.startswith(("mask", "sanitize", "redact")):
        return f"脱敏{business_subject(words[len(split_words(name)[0]):] if len(split_words(name)) > 1 else words)}，返回可安全写入日志或展示的摘要文本。"
    if lower.startswith(("sha", "digest", "fingerprint")):
        return f"计算{subject_label if subject_label.endswith('摘要') else subject_label + '摘要'}，用不可逆指纹关联原始内容而不暴露明文。"
    if lower.startswith(("translate", "unwrap")):
        return f"转换{subject_label}，把下游响应、异常或包装结果映射为当前模块统一语义。"
    if lower.startswith(("apply", "fill")):
        return f"应用{subject_label}，把校验后的配置、金额、状态或字段值写入目标对象。"
    if lower.startswith(("expire", "logout", "trim", "clean", "join", "first", "zero", "default", "safe", "read", "preview", "decrypt")):
        return f"规范化{subject_label}，返回调用链后续步骤可直接使用的业务值。"
    if lower.startswith(("count", "sum")):
        return f"统计{subject_label}，返回分页、扫描或报表汇总所需的数量结果。"
    if lower.startswith(("is", "has", "exists", "supports", "requires")):
        return f"判断 {words} 条件是否成立，用于控制 {owner_display} 的后续分支。"
    if lower.startswith(("calculate", "sum", "resolve", "parse", "normalize")):
        return f"解析{subject_label}，将原始输入转换为当前调用链需要的规范化结果。"
    if lower.endswith("tree"):
        return f"构建{business_subject(words)}，按层级关系组装树形业务视图。"
    if lower.startswith(("set", "with")):
        return f"写入{business_subject(subject)}，保持配置属性或测试夹具中的字段值与调用方输入一致。"
    return f"按 {{@code {name}}} 的公开契约返回当前类型所需结果；状态、副作用和异常语义以所属接口定义为准。"


def param_desc(param: str) -> str:
    lower = param.lower()
    words_set = lower_word_set(param)
    words = human_name(param)
    if lower in {"id", "ids"}:
        return "业务记录主键或主键集合，用于定位本次操作的目标记录"
    if lower == "appid":
        return "应用主键，用于限定角色、账号和数据范围所属的管理应用"
    if lower == "accountid":
        return "登录账号主键，用于查询该账号当前生效的角色授权"
    if lower == "tablename":
        return "经白名单选择的风控物理表名，禁止由外部请求直接拼接"
    if lower == "moduletype":
        return "风控模块类型，用于标识命中记录所属的规则域"
    if lower == "functioncode":
        return "风控功能编码，用于标识本次名单匹配能力"
    if lower == "functionname":
        return "风控功能名称，用于生成运营可读的命中原因"
    if lower == "hitelement":
        return "命中要素编码，用于说明本次匹配基于邮箱、IP、卡 BIN 或其它维度"
    if lower == "matchvaluehash":
        return "待匹配值的不可逆摘要，禁止传入或记录原始敏感值"
    if lower == "ipversion":
        return "IP 协议版本，只允许平台支持的 IPv4 或 IPv6 编码"
    if lower == "numericvalue":
        return "已规范化的数值边界值，用于执行 BIN 或 IP 区间包含判断"
    if lower == "sourcehost":
        return "已规范化的来源主机名，用于 AML 来源地址精确匹配"
    if lower == "lookupvalue":
        return "已完成哈希、脱敏或区间归一化的风控查询值，禁止携带可直接识别的敏感原文"
    if lower.endswith("do"):
        return "已从数据库读取或准备持久化的记录对象，状态、版本和审计字段必须保持一致"
    if lower.endswith("vo"):
        return "待组装或返回的接口视图对象，只允许写入调用方有权查看的字段"
    if lower.endswith("id") or lower.endswith("ids"):
        return "业务记录主键或主键集合，用于精确定位当前操作对象"
    if lower in {"failurecode", "failreasoncode", "failreason", "failuremessage"}:
        return "受控失败码或失败说明，用于状态机、商户文案映射和审计排障"
    if lower in {"transactiontype", "transactiontypeenum"}:
        return "交易类型，取值来自平台交易类型枚举并决定状态机和渠道能力"
    if lower in {"topic", "tag", "messagegroup", "eventtype"}:
        return "MQ 主题、标签、顺序分组或事件类型，必须符合既有消息契约"
    if lower in {"payloadjson", "content", "json"}:
        return "序列化业务载荷，持久化或记录日志前必须完成敏感字段检查"
    if lower in {"secret", "aad", "plaintext", "pan", "envelope", "authorization"}:
        return "敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显"
    if lower in {"traceid", "requestid"}:
        return "链路或请求标识，用于跨服务日志关联，不作为业务幂等键"
    if lower == "retrycount":
        return "当前重试次数，用于执行有界重试和人工介入判断"
    if lower in {"enabled", "approval"}:
        return "受控开关或审批结论，不得绕过对应权限和状态校验"
    if lower in {"dicttype", "dictvalue"}:
        return "字典类型或字典值编码，用于限定配置项所属的受控字典"
    if lower in {"locale", "year", "month"}:
        return "语言区域、年份或月份值，用于格式化、分区或缓存窗口计算"
    if lower in {"values", "parameters", "map", "segments"}:
        return "有界参数集合或键值片段，空元素按当前方法约定忽略或拒绝"
    if lower in {"rate", "exponent"}:
        return "汇率、费率或币种小数位，必须使用明确精度且禁止隐式浮点换算"
    if lower in {"sourceurl", "returnurl"}:
        return "来源或回跳地址，必须经过协议、主机和长度校验"
    if lower in {"operator", "operatorid", "operatorname", "operatortype"}:
        return "可信认证上下文中的操作人身份或类型，用于权限校验和操作审计"
    if lower in {"ip", "ipvalue", "email"}:
        return "待规范化的可识别信息，仅允许以脱敏、哈希或数值区间形式参与匹配"
    if lower in {"expected", "actual"}:
        return "待比较的期望值和实际值；敏感摘要必须使用常量时间比较"
    if lower == "snapshot":
        return "动作受理时冻结的不可变配置快照，用于计算复现和完整性校验"
    if lower in {"checkoutsessionid", "nonce"}:
        return "收银台会话标识或一次性随机数，用于构造防重放身份"
    if lower == "merchantresponsemessage":
        return "候选商户可见响应说明，内部错误和渠道敏感细节不得直接透传"
    if lower == "operation":
        return "当前交易动作事实，包含交易身份、状态、版本和分片定位信息"
    if lower == "currentlocator":
        return "当前已解析的交易分片定位信息，用于避免跨季度广播查询"
    if lower == "revision":
        return "清分修订号，用于区分同一动作的不可变清分版本"
    if lower == "anomalytype":
        return "清分异常类型编码，用于聚合告警和人工处置"
    if lower == "summary":
        return "不含敏感数据的异常或处理摘要，用于运营排障"
    if lower == "expectedoperationversion":
        return "交易动作预期版本，用于数据库 CAS 防止并发终态覆盖"
    if lower == "settlementprofileno":
        return "结算档案编号，用于定位商户、资金账户、币种和日切规则"
    if lower == "merchant":
        return "已读取的商户资料记录，状态变更前必须校验当前版本和目标状态"
    if lower == "account":
        return "可信登录账号上下文，用于解析角色授权和商户数据范围"
    if lower == "channelcallbackresult":
        return "已完成渠道验签和协议解析的回调结果，不包含可直接记录的敏感原文"
    if lower == "context":
        return "当前交易或请求上下文，用于透传交易身份、分片时间和审计信息"
    if lower == "channelorderno":
        return "渠道订单号，用于回调关联、渠道查询和对账"
    if lower == "matchresult":
        return "渠道查询或状态匹配结果，用于受控推进平台交易状态"
    if lower == "platformresultcode":
        return "平台统一结果码，用于把渠道状态映射为稳定业务语义"
    if lower == "routeresult":
        return "已锁定的渠道路由结果，后续调用不得重新选择渠道"
    if lower in {"methodparameter", "targettype", "convertertype"}:
        return "Spring Web 方法参数、目标类型或消息转换器类型，用于判断当前 Advice 是否适用"
    if lower == "phase":
        return "3DS 认证阶段编码，用于限定当前可执行的渠道认证动作"
    if lower == "step":
        return "TOTP 时间步长序号，用于计算当前或相邻窗口的一次性验证码"
    if lower in {"method", "httpmethod"}:
        return "HTTP 方法或内部调用方法名，用于构造请求、签名或异常摘要"
    if lower in {"uri", "url", "path"}:
        return "请求地址或路径，用于定位内部服务、渠道接口或商户回调目标"
    if lower in {"table", "physicaltable", "physicaltablename"}:
        return "经分表规则解析后的物理表名，只允许来自受控分表解析器"
    if lower == "logicaltable":
        return "逻辑表名，用于按交易时间解析真实物理分表"
    if lower in {"query", "condition"} or lower.endswith("query"):
        return "查询条件对象，包含筛选字段、时间范围、分页参数和数据范围"
    if lower == "datascope":
        return "可信登录上下文解析出的商户数据范围，查询不得越过该范围"
    if lower == "sourcetypes":
        return "允许查询的结算候选来源类型集合"
    if lower == "candidateno":
        return "结算候选编号，用于定位唯一候选记录"
    if lower == "reviewno":
        return "结算预审单号，用于定位唯一预审记录"
    if lower in {"revieworderno", "reversalorderno"}:
        return "结算预审单号或冲正单号，用于定位唯一业务单据"
    if lower == "batchno":
        return "正式结算批次号，用于限定明细和资金流水归属"
    if lower in {"offset", "limit", "pagesize", "pageno"}:
        return "分页或扫描窗口参数，用于限制单次查询范围"
    if lower in {"value", "text", "message", "code"}:
        return "待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理"
    if lower in {"exception", "error", "throwable"}:
        return "下游调用、校验或持久化阶段捕获的异常对象"
    if lower in {"response", "result", "body"} or lower.endswith("response"):
        return "下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化"
    if lower.endswith("builder"):
        return "框架构建器，用于按当前配置创建客户端、请求对象或运行时组件"
    if lower.endswith("customizer"):
        return "框架定制器，用于为客户端补充 traceId、超时或其它统一调用约束"
    if lower.endswith("interceptor"):
        return "请求拦截器，用于透传链路标识或执行当前调用边界的统一处理"
    if lower.endswith("properties") or lower == "properties":
        return "已绑定并校验的运行时配置，提供服务地址、调用身份和有界超时"
    if lower == "consumergroup":
        return "MQ 消费组，用于限定消息消费幂等记录的处理方范围"
    if lower == "messageid":
        return "MQ 消息唯一标识，与消费组共同构成消费幂等键"
    if lower == "topic":
        return "MQ 主题，用于记录消息来源并辅助审计排障"
    if lower in {"now", "createdat", "updatedat"}:
        return "当前处理时刻，用于写入业务记录或审计记录的时间字段"
    if lower in {"source", "target", "row", "rows"}:
        return "源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计"
    if "merchantid" in lower:
        return "商户号，用于限定数据归属、权限范围和配置读取范围"
    if "transactionid" in lower:
        return "平台交易号，用于定位主单、动作单、渠道请求和回调记录"
    if "operationid" in lower:
        return "平台操作号，用于定位单次授权、请款、退款、撤销或通知动作"
    if "amount" in lower:
        return "金额值，单位必须结合 currency 或同名币种字段解释"
    if "currency" in lower:
        return "币种代码，格式为 ISO 4217 三位大写字母"
    if lower.endswith("request") or lower.endswith("dto") or lower == "request":
        return f"{words}，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义"
    if "status" in lower:
        return "状态编码，取值必须来自对应枚举、字典或渠道协议"
    if words_set.intersection({"time", "date", "at", "created", "updated", "expired"}):
        return "时间值，使用系统约定时区或调用方传入的业务时区解释"
    if "card" in lower or "security" in lower or "key" in lower or "token" in lower:
        return "敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递"
    return f"{{@code {param}}} 参数，其取值范围和可空性由当前方法与所属模型共同约束"


def return_desc(name: str, return_type: str | None) -> str:
    lower = name.lower()
    exact_returns = {
        "all": "允许访问全部商户的数据范围",
        "limited": "仅允许访问指定商户集合的数据范围",
        "empty": "不允许访问任何商户时返回 true，否则返回 false",
        "candidatedetail": "结算候选详情",
        "reviewdetail": "结算预审单详情",
        "from": "符合 OpenAPI 契约的支付响应",
        "miss": "卡 BIN 未命中占位结果",
        "encrypt": "包含版本、IV 和认证标签的密文",
        "decrypt": "通过完整性校验的敏感字段明文",
    }
    if lower in exact_returns:
        return exact_returns[lower]
    if lower.startswith(("is", "has", "exists", "supports", "requires")):
        return "条件满足时返回 true，否则返回 false"
    if lower.startswith(("list", "page", "query", "find", "get", "load", "select", "search")):
        return "查询得到的业务对象、分页结果或空结果"
    if lower.startswith(("build", "to", "convert", "map", "parse", "normalize", "resolve")):
        return "构造、转换或解析后的业务值"
    if lower.startswith(("create", "save", "insert", "update", "delete")):
        return "写入、更新或删除后的处理结果"
    normalized_type = (return_type or "").strip()
    if normalized_type in {"boolean", "Boolean"}:
        return "当前业务条件成立时返回 true，否则返回 false"
    if normalized_type in {"int", "Integer", "long", "Long", "short", "Short"}:
        return "当前方法计算的数量、版本或状态数值"
    if normalized_type in {"String", "CharSequence"}:
        return "当前方法生成或规范化后的文本值"
    if any(token in normalized_type for token in ("List<", "Set<", "Collection<", "Map<")):
        return "符合当前条件的只读集合或映射结果"
    if normalized_type:
        return f"当前方法生成的 {{@code {normalized_type}}} 结果"
    return "当前方法的业务处理结果"


def method_contract(name: str, layer_name: str) -> list[str]:
    lower = name.lower()
    if lower == "registeredverifiers":
        return ["返回不可变注册表视图，不修改验签器注册状态。"]
    if lower == "loadbalanced":
        return ["仅创建客户端 Bean，不发起远程调用或读取业务数据。"]
    if lower.startswith(("list", "page", "query", "find", "get", "load", "select", "search")):
        return [f"只读操作；实现必须沿用 {layer_name} 既有权限、数据范围和空结果约定。"]
    if lower.startswith(("create", "save", "insert", "add", "register")):
        return [f"写操作；实现必须沿用 {layer_name} 既有权限、幂等键、唯一约束和事务边界。"]
    if lower.startswith(("update", "modify", "change", "edit")):
        return [f"状态或配置变更必须通过 {layer_name} 既有权限、版本和状态流转校验。"]
    if lower.startswith(("delete", "remove")):
        return [f"删除或停用必须通过 {layer_name} 既有权限和状态校验，并沿用软删除约定。"]
    if lower.startswith(("validate", "check", "verify", "require")):
        return [f"校验失败时按 {layer_name} 统一异常语义中断流程，不返回部分校验结果。"]
    if lower.startswith(("build", "to", "convert", "map", "fill")):
        return ["转换过程不改变来源对象的业务状态；敏感字段仅保留目标模型所需的最小集合。"]
    if lower.startswith(("send", "publish", "notify", "dispatch")):
        return [f"跨进程调用必须透传 traceId 和原业务标识，并按 {layer_name} 规则转换超时与失败结果。"]
    if lower.startswith(("handle", "process", "execute", "consume")):
        return [f"处理过程沿用 {layer_name} 既有幂等、状态机、事务和异常边界。"]
    if lower.startswith(("is", "has", "exists", "supports", "requires")):
        return ["纯判断操作，不修改业务状态。"]
    if lower.startswith(("calculate", "sum", "resolve", "parse", "normalize")):
        return ["仅返回规范化或计算结果，不直接提交交易状态。"]
    return []


def method_javadoc(name: str,
                   owner: str,
                   params: list[str],
                   return_type: str | None,
                   indent: str,
                   layer_name: str) -> list[str]:
    lines = [method_summary(name, owner)]
    contract = method_contract(name, layer_name)
    if contract:
        lines.extend(["<p>", *contract, "</p>"])
    for param in params:
        lines.append(f"@param {param} {param_desc(param)}")
    if return_type is not None and return_type.strip() != "void":
        lines.append(f"@return {return_desc(name, return_type)}")
    return javadoc(lines, indent)


def remove_generated_private_method_javadocs(lines: list[str]) -> tuple[list[str], bool]:
    """Drop the old blanket comments from private helpers before focused review."""
    out: list[str] = []
    changed = False
    index = 0
    while index < len(lines):
        if not re.match(r"^\s*/\*\*", lines[index]):
            out.append(lines[index])
            index += 1
            continue
        end = index
        while end < len(lines) and "*/" not in lines[end]:
            end += 1
        if end >= len(lines):
            out.append(lines[index])
            index += 1
            continue
        block = "".join(lines[index:end + 1])
        cursor = end + 1
        while cursor < len(lines) and cursor <= end + 24:
            stripped = lines[cursor].strip()
            if not stripped or stripped.startswith("@") or stripped.startswith("."):
                cursor += 1
                continue
            break
        signature = collected_method_signature(lines, cursor) if cursor < len(lines) else None
        method_match = METHOD_RE.match(signature[0]) if signature else None
        if method_match and "private" in set((method_match.group("mods") or "").split()) and "前置条件：" in block:
            changed = True
            index = end + 1
            continue
        out.extend(lines[index:end + 1])
        index = end + 1
    return out, changed


def move_type_javadocs_before_annotations(lines: list[str]) -> tuple[list[str], bool]:
    """Place generated type documentation before contiguous one-line annotations."""
    out = list(lines)
    changed = False
    index = 0
    while index < len(out):
        if not re.match(r"^\s*/\*\*", out[index]):
            index += 1
            continue
        end = index
        while end < len(out) and "*/" not in out[end]:
            end += 1
        if end >= len(out) or "@author" not in "".join(out[index:end + 1]):
            index = end + 1
            continue
        annotation_start = index
        cursor = index - 1
        while cursor >= 0 and out[cursor].strip().startswith("@"):
            annotation_start = cursor
            cursor -= 1
        if annotation_start == index:
            index = end + 1
            continue
        block = out[index:end + 1]
        del out[index:end + 1]
        out[annotation_start:annotation_start] = block
        changed = True
        index = annotation_start + len(block) + (index - annotation_start)
    return out, changed


def replace_previous_block(out: list[str], start: int, end: int, replacement: list[str]) -> bool:
    """Replace a Javadoc block only when the generated text actually differs."""
    if out[start:end + 1] == replacement:
        return False
    out[start:end + 1] = replacement
    return True


def collected_method_signature(lines: list[str], index: int) -> tuple[str, int] | None:
    first = lines[index].strip()
    if not first or first.startswith("*") or first.startswith("//") or first.startswith("@"):
        return None
    if "(" not in first and not re.match(r"(?:public|protected|private|static|final|abstract|synchronized|native|strictfp)\b", first):
        return None
    parts: list[str] = []
    for offset in range(20):
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
    """Return the final line of an annotation without interpreting text-block SQL."""
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


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    lines, type_placement_changed = move_type_javadocs_before_annotations(lines)
    lines, private_cleanup_changed = remove_generated_private_method_javadocs(lines)
    out: list[str] = []
    changed = type_placement_changed or private_cleanup_changed
    depth = 0
    in_block = False
    in_string = False
    type_stack: list[TypeContext] = []
    method_signature_end = -1
    annotation_end = -1
    index = 0

    while index < len(lines):
        line = lines[index]
        stripped = line.strip()
        while type_stack and depth <= type_stack[-1].depth:
            type_stack.pop()
        if index <= method_signature_end:
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            index += 1
            continue
        if index <= annotation_end:
            out.append(line)
            index += 1
            continue
        if stripped.startswith("@") and not TYPE_RE.match(line):
            annotation_end = annotation_block_end(lines, index)
            out.append(line)
            index += 1
            continue

        type_match = TYPE_RE.match(line)
        if type_match and not stripped.startswith("*") and not stripped.startswith("//"):
            kind = type_match.group("kind")
            name = type_match.group("name")
            bounds = javadoc_bounds_before_declaration(out, len(out))
            if bounds and type_template_block(bounds[2]):
                start, end, block = bounds
                replacement = replace_description(block, path, kind, name).splitlines(keepends=True)
                changed = replace_previous_block(out, start, end, replacement) or changed
            type_stack.append(TypeContext(kind, name, depth))
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            index += 1
            continue

        current_type = type_stack[-1] if type_stack else None
        enum_match = ENUM_VALUE_RE.match(line)
        if current_type and current_type.kind == "enum" and depth == current_type.depth + 1 and enum_match:
            bounds = javadoc_bounds_before(out, len(out))
            if bounds and field_template_block(bounds[2]):
                start, end, _ = bounds
                replacement = enum_value_javadoc(enum_match.group("name"), current_type.name, enum_match.group("indent"))
                changed = replace_previous_block(out, start, end, replacement) or changed
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            index += 1
            continue

        field_match = FIELD_RE.match(line)
        if field_match and current_type and depth == current_type.depth + 1 and not stripped.startswith("return "):
            bounds = javadoc_bounds_before_declaration(out, len(out))
            mods = set((field_match.group("mods") or "").split())
            remove_generated = bounds and generated_field_block(bounds[2]) and (
                "src/test/java" in str(path)
                or (simple_component_owner(current_type.name) and not {"static", "final"}.issubset(mods))
            )
            if remove_generated:
                start, end, _ = bounds
                del out[start:end + 1]
                changed = True
            elif bounds and field_template_block(bounds[2]):
                start, end, _ = bounds
                replacement = field_javadoc(
                    field_match.group("name"),
                    field_match.group("type").split()[-1].strip(),
                    current_type.name,
                    path,
                    field_match.group("indent"),
                    {"static", "final"}.issubset(mods),
                )
                changed = replace_previous_block(out, start, end, replacement) or changed
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            index += 1
            continue

        method_match = METHOD_RE.match(line)
        if not method_match and current_type and depth == current_type.depth + 1:
            collected = collected_method_signature(lines, index)
            if collected:
                method_match = METHOD_RE.match(collected[0])
                if method_match:
                    method_signature_end = index + collected[1] - 1
        if method_match and current_type and depth == current_type.depth + 1:
            bounds = javadoc_bounds_before_declaration(out, len(out))
            if bounds and method_template_block(bounds[2]):
                start, end, _ = bounds
                ret = method_match.group("ret")
                is_constructor = method_match.group("name") == current_type.name and ret is None
                if is_constructor:
                    del out[start:end + 1]
                    changed = True
                    out.append(line)
                    delta, in_block, in_string = current_depth(line, in_block, in_string)
                    depth += delta
                    index += 1
                    continue
                declaration_indent = re.match(r"^\s*", line).group(0)
                replacement = method_javadoc(
                    method_match.group("name"),
                    current_type.name,
                    params_from_signature(method_match.group("params")),
                    ret,
                    declaration_indent,
                    layer(path),
                )
                changed = replace_previous_block(out, start, end, replacement) or changed
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            index += 1
            continue

        out.append(line)
        delta, in_block, in_string = current_depth(line, in_block, in_string)
        depth += delta
        index += 1

    if changed:
        path.write_text("".join(out), encoding="utf-8")
    return changed


def main() -> int:
    changed_files = 0
    for path in iter_java_files(ROOT):
        if process_file(path):
            changed_files += 1
    print(f"refined_files={changed_files}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
