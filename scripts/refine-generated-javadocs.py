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
)
METHOD_TEMPLATE_MARKERS = (
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
)
TYPE_TEMPLATE_MARKERS = (
    "输入输出边界由所在包和公开方法契约限定",
    "Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑",
    "接口传输模型，用于约束请求入参、响应字段和跨层数据边界",
    "封装当前包内的业务数据、协作能力或运行时支撑逻辑",
    "支撑类型，位于",
    "无状态支撑类型",
    "场景所需的数据结构、协作入口或运行时能力",
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
    return bool(generated_lead.search(block) or english_operation.search(block))


def field_template_block(block: str) -> bool:
    """Detect generated field comments that are too generic to keep."""
    if any(marker in block for marker in FIELD_TEMPLATE_MARKERS):
        return True
    return bool(re.search(r"[A-Za-z]+\s+Type，表示当前记录所属", block))


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
    if rel.startswith("channel-library/"):
        return "渠道适配库"
    if rel.startswith("component-library/"):
        return "公共组件库"
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


def relation_hint(field: str, owner: str) -> str:
    lower = field.lower()
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
    return "与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障"


def sensitivity(field: str, type_name: str) -> str:
    lower = field.lower()
    if any(token in lower for token in ["securitycode", "cvv", "cvc", "cavv", "privatekey", "aeskey", "apikey", "password", "secret", "authorization"]):
        return "高敏感字段，禁止明文打印日志，禁止写入异常消息"
    if any(token in lower for token in ["cardno", "cardnumber", "pan"]):
        return "银行卡敏感字段，只允许脱敏或摘要化使用"
    if any(token in lower for token in ["token", "jwt", "key", "cert", "signature"]):
        return "敏感安全字段，日志只允许记录长度、摘要或掩码"
    if any(token in lower for token in ["email", "phone", "mobile", "name", "address", "account", "iban", "card", "ip", "url"]):
        return "可识别字段，日志输出必须脱敏或截断"
    return "非敏感字段"


def unit_format(field: str, type_name: str) -> tuple[str, str, str]:
    lower = field.lower()
    words = lower_word_set(field)
    clean_type = type_name.replace("final", "").strip()
    if any(token in lower for token in ["amount", "fee", "balance", "limit"]):
        return "由关联 currency 字段决定", "decimal 金额字符串或 BigDecimal", "金额不得为负，交易金额通常必须大于 0"
    if "rate" in lower:
        return "比例值", "decimal，按费率或汇率精度保存", "取值范围由费率、汇率或预警配置定义"
    if "currency" in lower:
        return "无", "ISO 4217 三位大写币种代码", "取值必须来自平台支持币种"
    if "country" in lower or lower.endswith("countrycode"):
        return "无", "ISO 国家或地区代码", "取值必须来自平台支持国家地区"
    if lower.endswith("id") or lower.endswith("no"):
        return "无", "业务编号字符串", "长度、唯一性和可空性由接口校验或数据库唯一约束限制"
    if "status" in lower or "type" in lower or "method" in lower or "mode" in lower or "code" in lower:
        return "无", "枚举编码或受控字符串", "取值必须来自对应枚举、字典或渠道协议"
    if clean_type in {"Integer", "Long", "int", "long"} or any(token in lower for token in ["count", "num", "index", "total", "size", "limit", "sort", "retry"]):
        return "个或次", "整数", "取值范围由数据库字段、校验注解或任务参数限制"
    if clean_type in {"Boolean", "boolean"} or lower.startswith(("is", "enable", "support")) or lower.endswith("enabled"):
        return "无", "布尔值或 0/1 开关", "仅允许平台约定的启停取值"
    if words.intersection({"time", "date", "at", "created", "updated", "modified", "expire", "expiry"}) or clean_type in {"LocalDateTime", "LocalDate", "Date", "Instant"}:
        return "系统业务时区时间", "ISO 日期或日期时间", "时间范围由业务流程或查询条件限定"
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


def field_summary(field: str, type_name: str, owner: str) -> str:
    lower = field.lower()
    display = human_name(field)
    owner_name = human_name(owner)
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
    return f"{display}，用于保存 {owner_name} 中与 {business_subject(lower_words(field))} 相关的业务属性。"


def field_javadoc(field: str, type_name: str, owner: str, path: Path, indent: str, static_final: bool) -> list[str]:
    unit, fmt, scope = unit_format(field, type_name)
    nullable = "不允许为空" if static_final or re.search(r"@Not(?:Blank|Null|Empty)", "".join(indent)) else "是否允许为空由接口校验、数据库约束或调用契约决定"
    lines = [
        field_summary(field, type_name, owner),
        "<p>",
        f"单位：{unit}；格式：{fmt}；{nullable}；{sensitivity(field, type_name)}。",
        f"取值范围：{scope}；数据来源：{source_hint(path, owner, field, type_name)}。",
        f"字段关系：{relation_hint(field, owner)}。",
        "</p>",
    ]
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
    if kind == "enum":
        return f"{display} 枚举，位于 {module}，定义交易状态、配置类型或协议结果的受控取值，供状态机、接口返回和日志字段统一引用。"
    if kind == "@interface":
        return f"{display} 注解，位于 {module}，用于声明拦截、鉴权、审计或框架扩展元数据，由运行时组件读取并执行对应规则。"
    if kind == "record":
        return f"{display} 不可变数据结构，位于 {module}，用于在当前调用链中传递固定字段集合，不承担状态写入职责。"
    if "controller" in lower:
        return f"{display} 控制器，位于 {module}，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。"
    if lower.endswith("dtos") or lower.endswith("entities"):
        return f"{display} 聚合类型，位于 {module}，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。"
    if lower.endswith("query") or lower.endswith("queryrequest"):
        return f"{display} 查询条件模型，位于 {module}，承载筛选字段、时间范围、分页参数和列表查询边界。"
    if lower.endswith("saverequest") or lower.endswith("createrequest") or lower.endswith("updaterequest"):
        return f"{display} 写操作请求模型，位于 {module}，承载新增或编辑时需要校验并落入业务配置的字段。"
    if lower.endswith("deleterequest"):
        return f"{display} 删除请求模型，位于 {module}，承载批量删除、软删除或停用操作所需的记录标识。"
    if lower.endswith("statusrequest"):
        return f"{display} 状态变更请求模型，位于 {module}，承载启停、冻结、审核或处理状态更新所需字段。"
    if "applicationservice" in lower:
        return f"{display} 应用服务，位于 {module}，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。"
    if "serviceimpl" in lower:
        return f"{display} 服务实现，位于 {module}，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。"
    if lower.endswith("service"):
        return f"{display} 服务契约，位于 {module}，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。"
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
        return f"{display} 传输模型，位于 {module}，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。"
    if lower.endswith("do") or "entities" in lower:
        return f"{display} 持久化模型，位于 {module}，映射数据库记录字段，承载主键、业务标识、状态、时间和审计信息。"
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
        return f"{display} 自动化测试类，位于 {module}，验证当前模块的正常路径、异常边界和回归场景。"
    return f"{display} 协作组件，位于 {module}，封装 {business_subject(lower_words(name))} 相关的校验、转换、持久化访问或运行时协作入口。"


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
    subject = words
    for prefix in ("list ", "page ", "query ", "find ", "get ", "load ", "select ", "search ",
                   "create ", "save ", "insert ", "add ", "register ", "update ", "modify ",
                   "change ", "edit ", "delete ", "remove ", "validate ", "check ", "verify ",
                   "require ", "build ", "to ", "convert ", "map ", "fill ", "send ", "publish ",
                   "notify ", "dispatch ", "handle ", "process ", "execute ", "consume ",
                   "reset ", "assign ", "grant ", "replace ", "softdelete ", "flatten ", "safe ",
                   "count ", "enrich ", "merge ", "append ", "invoke ", "submit ", "reject ",
                   "copy ", "preview ", "resend ", "decrypt ", "generate ", "read "):
        if words.startswith(prefix):
            subject = words[len(prefix):]
            break
    subject_label = business_subject(subject)
    if lower.startswith(("list", "page", "query", "find", "get", "load", "select", "search")):
        return f"查询{subject_label}，按调用方提供的过滤条件返回对应业务视图。"
    if lower.startswith(("create", "save", "insert", "add", "register", "generate")):
        return f"创建{subject_label}，完成必要校验后写入或委托下游服务处理。"
    if lower.startswith(("update", "modify", "change", "edit", "reset", "assign", "grant", "replace", "softdelete")):
        return f"更新{subject_label}，保持业务状态、配置项或展示字段与请求意图一致。"
    if lower.startswith(("delete", "remove")):
        return f"删除或停用{subject_label}，调用方需保证权限和状态允许该操作。"
    if lower.startswith(("validate", "check", "verify", "require", "ensure", "assert")):
        return f"校验{subject_label}输入，发现缺失、越权或格式错误时中断当前流程。"
    if lower.startswith(("build", "to", "convert", "map", "fill", "copy", "enrich", "merge", "append", "flatten")):
        return f"构造{subject_label}对象，完成字段复制、格式标准化和敏感数据处理。"
    if lower.startswith(("send", "publish", "notify", "dispatch", "submit", "invoke", "resend")):
        return f"发送{subject_label}消息或请求，补齐目标地址、链路标识和业务载荷。"
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
        return f"解析{business_subject(words)}，将原始输入转换为当前调用链需要的规范化结果。"
    if lower.endswith("tree"):
        return f"构建{business_subject(words)}，按层级关系组装树形业务视图。"
    if lower.startswith(("set", "with")):
        return f"写入{business_subject(subject)}，保持配置属性或测试夹具中的字段值与调用方输入一致。"
    return f"规范化{business_subject(words)}，返回当前业务步骤需要的业务值。"


def param_desc(param: str) -> str:
    lower = param.lower()
    words = human_name(param)
    if lower in {"id", "ids"}:
        return "业务记录主键或主键集合，用于定位本次操作的目标记录"
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
    if lower in {"offset", "limit", "pagesize", "pageno"}:
        return "分页或扫描窗口参数，用于限制单次查询范围"
    if lower in {"value", "text", "message", "code"}:
        return "待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理"
    if lower in {"exception", "error", "throwable"}:
        return "下游调用、校验或持久化阶段捕获的异常对象"
    if lower in {"response", "result", "body"} or lower.endswith("response"):
        return "下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化"
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
    if "time" in lower or "date" in lower:
        return "时间值，使用系统约定时区或调用方传入的业务时区解释"
    if "card" in lower or "security" in lower or "key" in lower or "token" in lower:
        return "敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递"
    return f"{words} 输入值，参与 {business_subject(lower_words(param))} 的查询、校验、转换、写入或日志摘要"


def return_desc(name: str) -> str:
    lower = name.lower()
    if lower.startswith(("is", "has", "exists", "supports", "requires")):
        return "条件满足时返回 true，否则返回 false"
    if lower.startswith(("list", "page", "query", "find", "get", "load", "select", "search")):
        return "查询得到的业务对象、分页结果或空结果"
    if lower.startswith(("build", "to", "convert", "map", "parse", "normalize", "resolve")):
        return "构造、转换或解析后的业务值"
    if lower.startswith(("create", "save", "insert", "update", "delete")):
        return "写入、更新或删除后的处理结果"
    return "方法执行后的业务结果、更新行数、转换对象或空结果"


def method_contract(name: str, layer_name: str) -> list[str]:
    lower = name.lower()
    if lower.startswith(("list", "page", "query", "find", "get", "load", "select", "search")):
        return [
            f"前置条件：调用方已按 {layer_name} 的权限和数据范围传入查询条件。",
            "该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。",
            "异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。",
        ]
    if lower.startswith(("create", "save", "insert", "add", "register")):
        return [
            f"前置条件：调用方已完成 {layer_name} 的身份、权限、必填字段和业务唯一性准备。",
            "该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。",
            "异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。",
        ]
    if lower.startswith(("update", "modify", "change", "edit")):
        return [
            f"前置条件：调用方已确认 {layer_name} 中目标记录存在且当前状态允许变更。",
            "该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。",
            "异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。",
        ]
    if lower.startswith(("delete", "remove")):
        return [
            f"前置条件：调用方已确认 {layer_name} 中目标记录存在、权限满足且状态允许删除或停用。",
            "该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。",
            "异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。",
        ]
    if lower.startswith(("validate", "check", "verify", "require")):
        return [
            f"前置条件：调用方传入需要在 {layer_name} 内校验的参数、状态或安全材料。",
            "该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。",
            "异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。",
        ]
    if lower.startswith(("build", "to", "convert", "map", "fill")):
        return [
            f"前置条件：调用方已准备 {layer_name} 所需的源对象、配置或协议字段。",
            "该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。",
            "异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。",
        ]
    if lower.startswith(("send", "publish", "notify", "dispatch")):
        return [
            f"前置条件：调用方已确定 {layer_name} 的目标地址、消息主题、业务编号和重试策略。",
            "该方法可能调用外部系统、内部服务或 MQ；traceId 必须沿调用链透传，重试应保留原业务标识。",
            "异常边界：网络异常、超时或投递失败需转换为当前模块可识别的失败结果并记录脱敏摘要。",
        ]
    if lower.startswith(("handle", "process", "execute", "consume")):
        return [
            f"前置条件：调用方已把 {layer_name} 的请求、消息或任务参数解析为当前方法可识别的模型。",
            "该方法按业务分支串联校验、状态判断、数据读写、远程调用或消息投递，关键阶段应保留 traceId 日志。",
            "异常边界：幂等冲突、状态不允许、外部系统失败或持久化失败按当前流程返回明确结果。",
        ]
    if lower.startswith(("is", "has", "exists", "supports", "requires")):
        return [
            f"前置条件：调用方已准备 {layer_name} 判断所需的对象、枚举或配置。",
            "该方法不修改业务状态，只返回布尔判断结果供后续分支使用。",
            "异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。",
        ]
    if lower.startswith(("calculate", "sum", "resolve", "parse", "normalize")):
        return [
            f"前置条件：调用方已传入 {layer_name} 中需要标准化的原始值。",
            "该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。",
            "异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。",
        ]
    return [
        f"前置条件：调用方已准备 {layer_name} 当前步骤需要的输入对象和业务标识。",
        "该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。",
        "异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。",
    ]


def method_javadoc(name: str, owner: str, params: list[str], has_return: bool, indent: str, layer_name: str) -> list[str]:
    lines = [
        method_summary(name, owner),
        "<p>",
        *method_contract(name, layer_name),
        "</p>",
    ]
    for param in params:
        lines.append(f"@param {param} {param_desc(param)}")
    if has_return:
        lines.append(f"@return {return_desc(name)}")
    return javadoc(lines, indent)


def replace_previous_block(out: list[str], start: int, end: int, replacement: list[str]) -> None:
    out[start:end + 1] = replacement


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


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    changed = False
    depth = 0
    in_block = False
    in_string = False
    type_stack: list[TypeContext] = []
    index = 0

    while index < len(lines):
        line = lines[index]
        stripped = line.strip()
        while type_stack and depth <= type_stack[-1].depth:
            type_stack.pop()

        type_match = TYPE_RE.match(line)
        if type_match and not stripped.startswith("*") and not stripped.startswith("//"):
            kind = type_match.group("kind")
            name = type_match.group("name")
            bounds = javadoc_bounds_before(out, len(out))
            if bounds and type_template_block(bounds[2]):
                start, end, block = bounds
                replacement = replace_description(block, path, kind, name).splitlines(keepends=True)
                replace_previous_block(out, start, end, replacement)
                changed = True
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
                replace_previous_block(out, start, end, replacement)
                changed = True
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            index += 1
            continue

        field_match = FIELD_RE.match(line)
        if field_match and current_type and depth == current_type.depth + 1 and not stripped.startswith("return "):
            bounds = javadoc_bounds_before(out, len(out))
            if bounds and field_template_block(bounds[2]):
                start, end, _ = bounds
                mods = set((field_match.group("mods") or "").split())
                replacement = field_javadoc(
                    field_match.group("name"),
                    field_match.group("type").split()[-1].strip(),
                    current_type.name,
                    path,
                    field_match.group("indent"),
                    {"static", "final"}.issubset(mods),
                )
                replace_previous_block(out, start, end, replacement)
                changed = True
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
        if method_match and current_type and depth == current_type.depth + 1:
            bounds = javadoc_bounds_before_declaration(out, len(out))
            if bounds and method_template_block(bounds[2]):
                start, end, _ = bounds
                ret = method_match.group("ret")
                replacement = method_javadoc(
                    method_match.group("name"),
                    current_type.name,
                    params_from_signature(method_match.group("params")),
                    ret is not None and ret.strip() != "void",
                    method_match.group("indent"),
                    layer(path),
                )
                replace_previous_block(out, start, end, replacement)
                changed = True
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
