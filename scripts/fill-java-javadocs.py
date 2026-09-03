#!/usr/bin/env python3
"""Fill missing Java Javadocs for the acquiring-orchestration project."""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

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
    r"(?P<type>[A-Za-z_][A-Za-z0-9_$<>, ?.\[\]]+)\s+(?P<names>[A-Za-z_][A-Za-z0-9_]*(?:\s*=[^;,]+)?(?:\s*,\s*[A-Za-z_][A-Za-z0-9_]*(?:\s*=[^;,]+)?)*)\s*;"
)
ENUM_VALUE_RE = re.compile(r"^(?P<indent>\s*)(?P<name>[A-Z][A-Z0-9_]*)(?P<args>\s*(?:\([^;{}]*\))?)(?P<suffix>\s*[,;])\s*$")


SKIP_METHOD_NAMES = {
    "if", "for", "while", "switch", "catch", "return", "new", "throw", "else", "do", "try"
}
LOGGER_NAMES = {"log", "logger", "LOGGER"}
TECHNICAL_STATIC_FIELDS = {"serialVersionUID"}
MAX_SIGNATURE_LINES = 20


@dataclass
class TypeContext:
    kind: str
    name: str
    depth: int


def iter_java_files(root: Path):
    for path in root.rglob("*.java"):
        if "target" not in path.parts:
            yield path


def has_javadoc_before(lines: list[str], index: int, declaration_indent: int) -> bool:
    """Return whether the declaration is already documented across annotations."""
    j = index - 1
    while j >= 0 and not lines[j].strip():
        j -= 1
    if j < 0:
        return False
    if lines[j].strip().endswith("*/"):
        return True

    # Find the nearest preceding Javadoc, then prove that every nonblank line
    # between it and the declaration belongs to a complete annotation block.
    # Forward parsing is required because a multiline annotation commonly ends
    # with a value or a closing parenthesis rather than a line beginning with @.
    doc_end = None
    for k in range(j, max(-1, j - 400), -1):
        if lines[k].strip().endswith("*/"):
            doc_end = k
            break
    if doc_end is None:
        return False

    cursor = doc_end + 1
    saw_annotation = False
    while cursor < index:
        while cursor < index and not lines[cursor].strip():
            cursor += 1
        if cursor >= index:
            break
        stripped = lines[cursor].lstrip()
        indent = len(lines[cursor]) - len(stripped)
        if not stripped.startswith("@") or indent != declaration_indent:
            return False
        saw_annotation = True
        annotation_end = annotation_block_end(lines, cursor)
        if annotation_end < cursor or annotation_end >= index:
            return False
        cursor = annotation_end + 1
    return saw_annotation


def generated_javadoc_start(lines: list[str], index: int) -> int | None:
    j = index - 1
    while j >= 0 and not lines[j].strip():
        j -= 1
    if j < 0 or not lines[j].strip().endswith("*/"):
        return None
    end = j
    while j >= 0 and "/**" not in lines[j]:
        j -= 1
    if j < 0:
        return None
    block = "".join(lines[j:end + 1])
    generated_markers = [
        "内部步骤，为当前类的公开能力提供参数校验、对象映射或状态计算",
        "前置条件、幂等规则、事务边界和外部系统调用由实现类、注解配置或调用方契约共同约束",
        "参数，来源于调用方输入、路径变量、请求体、配置或依赖注入",
        "方法执行后的领域对象、集合数据、统一响应或远程调用结果",
        "所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果",
        "涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束",
        "对象，携带当前业务动作的输入字段",
        "分支的校验或转换，返回值供当前调用链继续组装结果",
        "层级边界：",
        "状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准",
        "当前方法计算或转换后的业务结果",
        "接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致",
        "层级边界：",
        "输入值，参与",
        "方法执行后的业务结果、更新行数、转换对象或空结果",
        "查询得到的业务对象、分页结果或空结果",
        "构造、转换或解析后的业务值",
        "只读操作；实现必须沿用",
        "写操作；实现必须沿用",
        "本地协作不得扩大",
        "按当前方法契约生成的业务处理结果",
    ]
    return j if any(marker in block for marker in generated_markers) else None


def collected_method_signature(lines: list[str], index: int) -> tuple[str, int] | None:
    first = lines[index].strip()
    if not first or first.startswith("*") or first.startswith("//") or first.startswith("@"):
        return None
    if "(" not in first and not re.match(r"(?:public|protected|private|static|final|abstract|synchronized|native|strictfp)\b", first):
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
    """Return the final line of an annotation without counting text-block contents."""
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


def package_name(text: str) -> str:
    match = re.search(r"^\s*package\s+([A-Za-z0-9_.]+)\s*;", text, re.MULTILINE)
    return match.group(1) if match else ""


def is_main_java(path: Path) -> bool:
    return "src/main/java" in str(path)


def file_layer(path: Path) -> str:
    rel = str(path.relative_to(ROOT))
    if rel.startswith("component-library/"):
        return "公共组件层"
    if rel.startswith("channel-library/"):
        return "渠道适配层"
    if rel.startswith("service-gateway/"):
        return "网关层"
    if rel.startswith("service-admin/"):
        return "运营后台服务层"
    if rel.startswith("service-merchant/"):
        return "商户后台服务层"
    if rel.startswith("service-openapi/"):
        return "商户开放接口服务层"
    if rel.startswith("service-payment/"):
        return "支付核心服务层"
    if rel.startswith("service-risk/"):
        return "风控服务层"
    if rel.startswith("service-payout/"):
        return "代付服务层"
    if rel.startswith("service-job/"):
        return "调度任务服务层"
    if rel.startswith("service-checkout/"):
        return "收银台服务层"
    return "项目服务层"


def type_role(name: str, kind: str, pkg: str, path: Path) -> str:
    n = name
    lower_pkg = pkg.lower()
    if kind == "@interface":
        return "注解类型，用于声明运行时元数据、拦截规则或框架扩展点"
    if kind == "enum":
        return "枚举类型，用于限定业务状态、配置选项或协议取值范围"
    if kind == "record":
        return "不可变数据载体，用于在模块内部传递结构化参数或结果"
    if n.endswith("Application"):
        return "Spring Boot 启动入口，用于装配当前服务的组件扫描、配置加载和运行时上下文"
    if n.endswith("Controller"):
        return "HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应"
    if n.endswith("ApplicationService"):
        return "应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖"
    if n.endswith("ServiceImpl"):
        return "服务实现，用于执行领域规则、数据读写编排和业务异常转换"
    if n.endswith("Service"):
        return "服务契约，用于声明业务能力、调用边界和返回结果约束"
    path_text = str(path).lower()
    if n.endswith("RequestMapper") and "channel-library" in path_text:
        return "渠道请求映射组件，用于把平台统一交易请求转换为渠道协议字段并保留金额、币种、卡数据和操作类型边界"
    if n.endswith("ResponseMapper") and "channel-library" in path_text:
        return "渠道响应映射组件，用于把渠道返回报文转换为平台统一结果并保留渠道状态、错误码和交易标识"
    if n.endswith("Mapper") and ".mapper" in lower_pkg:
        return "MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件"
    if n.endswith("Mapper"):
        return "对象映射组件，用于在内部模型、接口模型和外部协议字段之间转换数据"
    if n.endswith("DO") or ".entity" in lower_pkg:
        return "数据库实体，用于映射持久化表字段、审计字段和业务状态"
    if n.endswith("DTO") or "dto" in lower_pkg or n.endswith("Request") or n.endswith("Response") or n.endswith("VO"):
        return "接口传输模型，用于约束请求入参、响应字段和跨层数据边界"
    if n.endswith("Config") or n.endswith("Configuration"):
        return "Spring 配置类，用于注册当前模块所需 Bean、客户端和拦截器"
    if n.endswith("Properties"):
        return "配置属性模型，用于绑定 application 配置项并提供默认值"
    if n.endswith("Client") or n.endswith("RestClient"):
        return "内部或渠道客户端，用于封装远程调用、协议参数和异常转换"
    if n.endswith("Filter") or n.endswith("Interceptor"):
        return "请求拦截组件，用于处理鉴权、链路追踪、上下文绑定和安全边界"
    if n.endswith("Aspect"):
        return "切面组件，用于拦截注解声明的业务动作并记录审计信息"
    if n.endswith("Consumer") or n.endswith("Listener"):
        return "消息消费组件，用于解析 MQ 消息、绑定链路上下文并触发后续处理"
    if n.endswith("Producer") or n.endswith("Publisher"):
        return "消息投递组件，用于补齐消息元数据并发送 MQ 事件"
    if n.endswith("Job") or n.endswith("Handler") or n.endswith("Scanner"):
        return "调度任务组件，用于执行定时扫描、异步任务或后台补偿流程"
    if n.endswith("Tests") or n.endswith("Test"):
        return "自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景"
    if n.endswith("Utils") or n.endswith("Util"):
        return "通用能力封装，用于提供无状态的格式转换、校验或安全处理函数"
    if n.endswith("Exception"):
        return "异常类型，用于表达业务失败、系统失败或调用边界错误"
    if n.endswith("Factory"):
        return "工厂组件，用于创建领域对象、密钥材料、客户端参数或运行时配置"
    if n.endswith("Resolver"):
        return "解析组件，用于根据输入条件确定配置、路由、字典或上下文结果"
    if n.endswith("Converter"):
        return "转换组件，用于在实体、DTO、VO 和外部协议对象之间转换字段"
    return "Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑"


def description_for_type(path: Path, name: str, kind: str, pkg: str) -> str:
    return f"{name} {type_role(name, kind, pkg, path)}，位于 {file_layer(path)}，输入输出边界由所在包和公开方法契约限定。"


def created_date(path: Path, existing_text: str) -> str:
    match = re.search(r"@date\s*:\s*([^\n\r*]+)", existing_text)
    if match:
        value = match.group(1).strip()
        if value:
            return value
    try:
        result = subprocess.run(
            ["git", "log", "--follow", "--diff-filter=A", "--format=%ad", "--date=format:%Y-%m-%d %H:%M", "--", str(path)],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=3,
            check=False,
        )
        dates = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        if dates:
            return dates[-1]
    except Exception:
        pass
    return "未确认"


def javadoc(lines: list[str], indent: str) -> list[str]:
    return [indent + "/**\n"] + [indent + " * " + line + "\n" for line in lines] + [indent + " */\n"]


def type_javadoc(path: Path, kind: str, name: str, pkg: str, date: str, indent: str) -> list[str]:
    return javadoc([
        "@author : scott",
        "@version : v1.0.0",
        f"@classname : {name}",
        f"@date : {date}",
        "@email : scott_x@163.com",
        f"@description : {description_for_type(path, name, kind, pkg)}",
        "@status : create",
    ], indent)


def split_words(name: str) -> str:
    if name.isupper():
        return name.replace("_", " ")
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name).replace("_", " ")
    return words.strip()


def sensitive_hint(name: str) -> str:
    lower = name.lower()
    if any(token in lower for token in ["password", "secret", "token", "privatekey", "apikey", "cvv", "cvc", "securitycode", "authorization"]):
        return "高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。"
    if any(token in lower for token in ["card", "pan", "email", "phone", "mobile", "account", "iban", "name", "address", "jwt", "key"]):
        return "敏感或可识别字段，日志输出必须脱敏。"
    return "非敏感字段，仍需按最小必要原则使用。"


def format_hint(name: str, type_name: str) -> str:
    lower = name.lower()
    if "amount" in lower or "fee" in lower or "rate" in lower:
        return "单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；"
    if "time" in lower or "date" in lower or type_name in {"LocalDateTime", "LocalDate", "Date"}:
        return "单位：系统时区时间；格式：ISO 日期或日期时间；"
    if "count" in lower or "num" in lower or type_name in {"Integer", "Long", "int", "long"}:
        return "单位：个；格式：整数；"
    if "currency" in lower:
        return "单位：无；格式：ISO 4217 三位币种代码；"
    if "country" in lower:
        return "单位：无；格式：ISO 国家或地区代码；"
    if "status" in lower or "type" in lower or "mode" in lower or "method" in lower:
        return "单位：无；格式：枚举编码或受控字符串；"
    if type_name == "Boolean" or type_name == "boolean" or lower.startswith("is"):
        return "单位：无；格式：布尔值；"
    return "单位：无；格式：由上游接口、数据库字段或枚举定义约束；"


def field_javadoc(name: str, type_name: str, indent: str, static_final: bool = False) -> list[str]:
    display = split_words(name)
    if static_final:
        first = f"{display} 常量，用于在当前模块内统一引用固定配置、状态或协议字段。"
    else:
        first = f"{display} 字段，表示当前模型在所属业务流程中的对应属性。"
    return javadoc([
        first,
        "<p>",
        f"{format_hint(name, type_name)}是否允许为空由数据库约束、校验注解或调用契约决定；{sensitive_hint(name)}",
        "数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。",
        "</p>",
    ], indent)


def enum_value_javadoc(name: str, indent: str) -> list[str]:
    return javadoc([
        f"{split_words(name)} 枚举值，表示当前枚举定义中的一个受控业务取值。",
        "<p>",
        "单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。",
        "</p>",
    ], indent)


def params_from_signature(params: str) -> list[str]:
    clean = re.sub(r"@[A-Za-z_][A-Za-z0-9_$.]*(?:\([^)]*\))?", "", params)
    result: list[str] = []
    for part in clean.split(","):
        part = part.strip()
        if not part:
            continue
        part = re.sub(r"\bfinal\s+", "", part)
        tokens = part.replace("...", "[]").split()
        if not tokens:
            continue
        name = tokens[-1].replace("[]", "").strip()
        name = re.sub(r"[^A-Za-z0-9_].*", "", name)
        if name and name not in result:
            result.append(name)
    return result


def method_action(name: str) -> str:
    if name.startswith("get") or name.startswith("find") or name.startswith("query") or name.startswith("page") or name.startswith("list") or name.startswith("search"):
        return "查询"
    if name.startswith("create") or name.startswith("save") or name.startswith("add") or name.startswith("register"):
        return "创建或保存"
    if name.startswith("update") or name.startswith("modify") or name.startswith("change"):
        return "更新"
    if name.startswith("delete") or name.startswith("remove"):
        return "删除"
    if name.startswith("send") or name.startswith("publish"):
        return "发送"
    if name.startswith("handle") or name.startswith("process") or name.startswith("execute"):
        return "处理"
    if name.startswith("validate") or name.startswith("check") or name.startswith("verify"):
        return "校验"
    if name.startswith("convert") or name.startswith("to") or name.startswith("build"):
        return "构造或转换"
    return "执行"


def method_purpose(name: str, has_return: bool, owner_name: str = "") -> str:
    words = split_words(name)
    lower = name.lower()
    owner_lower = owner_name.lower()
    if owner_name.endswith("Controller"):
        return f"接收 {words} 接口调用，完成 Web 层参数承接并委托应用服务返回统一响应。"
    if owner_name.endswith("ApplicationService"):
        return f"编排 {words} 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。"
    if owner_name.endswith("ServiceImpl") or owner_name.endswith("Service"):
        return f"执行 {words} 服务能力，按当前领域规则完成校验、状态读取或数据写入。"
    if owner_name.endswith("Mapper") and "requestmapper" not in owner_lower and "responsemapper" not in owner_lower:
        return f"定义 {words} 数据访问或对象转换入口，返回调用方需要的持久化记录或映射结果。"
    if owner_name.endswith("Client") or owner_name.endswith("RestClient"):
        return f"发起 {words} 远程调用，封装请求参数、响应解析和调用失败边界。"
    if owner_name.endswith("Consumer") or owner_name.endswith("Listener"):
        return f"消费 {words} 消息或事件，解析消息载荷并触发后续领域处理。"
    if owner_name.endswith("Producer") or owner_name.endswith("Publisher"):
        return f"发布 {words} 消息或事件，补齐消息键、链路标识和业务载荷。"
    if lower.startswith("validate"):
        return f"校验 {words} 相关输入，发现不满足业务约束时抛出明确异常。"
    if lower.startswith("require"):
        return f"强制校验 {words} 必填值，缺失时中断当前业务流程。"
    if lower.startswith("normalize"):
        return f"标准化 {words} 输入值，统一大小写、空白字符或协议格式。"
    if lower.startswith("resolve"):
        return f"解析 {words} 对应的业务值，按优先级从上下文、请求或配置中取值。"
    if lower.startswith("build"):
        return f"构建 {words} 对应的领域对象、请求对象或日志对象。"
    if lower.startswith("to"):
        return f"转换生成 {words} 对应的传输对象、导出行或协议字段。"
    if lower.startswith("fill"):
        return f"填充 {words} 相关字段，保持来源对象与目标对象的业务含义一致。"
    if lower.startswith("parse"):
        return f"解析 {words} 输入文本并转换为内部可校验的数据结构。"
    if lower.startswith("is") or lower.startswith("has") or lower.startswith("exists"):
        return f"判断 {words} 条件是否成立，用于控制后续业务分支。"
    if lower.startswith("find") or lower.startswith("load") or lower.startswith("select"):
        return f"查询 {words} 所需数据，未命中时按调用场景返回空值或抛出异常。"
    if lower.startswith("save") or lower.startswith("insert") or lower.startswith("update") or lower.startswith("record"):
        return f"写入或更新 {words} 相关数据，保持数据库记录与当前业务处理结果一致。"
    if lower.startswith("expire") or lower.startswith("mark") or lower.startswith("complete"):
        return f"推进 {words} 对应的状态或处理结果，并保留后续查询所需信息。"
    if lower.startswith("send") or lower.startswith("notify") or lower.startswith("publish"):
        return f"发送 {words} 对应的外部通知、内部消息或远程请求。"
    if lower.startswith("calculate") or lower.startswith("sum") or lower.startswith("add"):
        return f"计算 {words} 对应的数值结果，调用方负责保证金额和币种上下文一致。"
    return f"完成 {words} 的本地校验、字段转换或结果组装，供当前调用链继续使用。" if has_return else f"完成 {words} 的本地校验、字段转换或状态更新。"


def param_description(param: str) -> str:
    lower = param.lower()
    words = split_words(param)
    if lower == "request" or lower.endswith("request"):
        return f"{words} 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义"
    if lower == "requestdto":
        return "内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段"
    if "transactiontype" in lower:
        return "交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型"
    if "transactionid" in lower:
        return "平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果"
    if "operationid" in lower:
        return "平台交易操作号，用于定位一次授权、请款、退款或撤销操作"
    if "merchantid" in lower:
        return "商户号，用于限定数据归属、幂等范围和权限边界"
    if "merchantorderno" in lower:
        return "商户订单号，用于商户侧幂等校验和订单查询"
    if "amount" in lower:
        return "金额值，单位由关联币种决定，调用前必须完成币种精度校验"
    if "currency" in lower:
        return "币种代码，格式为 ISO 4217 三位大写字母"
    if "card" in lower or "pan" in lower:
        return "卡相关输入，属于敏感或可识别数据，禁止直接写入日志"
    if "securitycode" in lower or "cvv" in lower or "cvc" in lower:
        return "银行卡安全码，高敏感认证数据，仅允许在渠道请求内短暂使用"
    if "message" in lower:
        return "错误提示或消息内容，供异常转换、日志摘要或返回结果使用"
    if "status" in lower:
        return "状态编码，取值必须来自对应枚举或数据库受控字典"
    if "time" in lower or "date" in lower:
        return "时间值，使用系统约定时区或调用方传入的业务时区解释"
    if lower == "value":
        return "待校验或转换的原始值"
    return f"{words} 输入值，含义由调用方法名称和所属业务对象限定"


def return_description(name: str, return_type: str | None) -> str:
    lower = name.lower()
    ret = (return_type or "").strip()
    if ret == "boolean" or ret == "Boolean" or lower.startswith(("is", "has", "exists", "requires")):
        return "满足当前业务条件时返回 true，否则返回 false"
    if "amount" in lower:
        return "按渠道协议格式化后的金额字符串或金额计算结果"
    if "currency" in lower:
        return "标准化后的 ISO 4217 币种代码"
    if "operation" in lower:
        return "渠道 API 操作类型或平台操作映射结果"
    if lower.startswith(("to", "build")):
        return "转换或构建后的目标对象"
    if lower.startswith(("resolve", "find", "load", "select")):
        return "解析或查询得到的业务值"
    if lower.startswith("normalize"):
        return "标准化后的业务字段值"
    if lower.startswith("parse"):
        return "解析后的内部数据结构或业务值"
    return "方法签名声明的返回值，具体结构由返回类型定义"


def method_javadoc(name: str,
                   params: list[str],
                   has_return: bool,
                   is_interface: bool,
                   indent: str,
                   owner_name: str = "",
                   layer: str = "",
                   constructor: bool = False,
                   private_method: bool = False) -> list[str]:
    if constructor:
        first = f"创建 {name} 实例并注入其运行所需依赖。"
    else:
        first = method_purpose(name, has_return, owner_name) if private_method else method_purpose(name, has_return, owner_name)
    boundary = layer or "当前模块"
    lines = [
        first,
        "<p>",
        f"层级边界：{boundary}；输入来源、输出结构和异常语义由 {owner_name or '当前类型'} 的方法签名及调用链约束。",
        "状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。",
        "</p>",
    ]
    for param in params:
        lines.append(f"@param {param} {param_description(param)}")
    if has_return:
        lines.append(f"@return {return_description(name, None)}")
    if is_interface:
        lines[1:1] = ["接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。"]
    return javadoc(lines, indent)


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


def is_model_path(path: Path) -> bool:
    normalized = str(path).replace("\\", "/").lower()
    return any(segment in normalized for segment in (
        "/dto/", "/entity/", "/model/", "/vo/", "/request/", "/response/"
    )) or path.stem.lower().endswith(("dto", "do", "vo", "request", "response"))


def is_simple_accessor(path: Path, name: str, params: list[str]) -> bool:
    if not is_model_path(path):
        return False
    lower = name.lower()
    return ((lower.startswith("get") or lower.startswith("is")) and not params
            or lower.startswith("set") and len(params) == 1)


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


def remove_generated_duplicate_blocks(lines: list[str]) -> tuple[list[str], bool]:
    """Remove generated Javadocs inserted between annotations and a method that already had Javadoc."""
    changed = False
    out: list[str] = []
    i = 0
    while i < len(lines):
        if "/**" not in lines[i]:
            out.append(lines[i])
            i += 1
            continue
        start = i
        end = i
        while end < len(lines) and "*/" not in lines[end]:
            end += 1
        if end >= len(lines):
            out.append(lines[i])
            i += 1
            continue
        block = "".join(lines[start:end + 1])
        is_generated = generated_javadoc_start(lines[:end + 1], end + 1) == start
        prev_doc_end = None
        for j in range(start - 1, max(-1, start - 120), -1):
            stripped = lines[j].strip()
            if stripped.endswith("*/"):
                prev_doc_end = j
                break
        cursor = prev_doc_end + 1 if prev_doc_end is not None else start
        saw_annotation = False
        valid_annotation_chain = prev_doc_end is not None
        while valid_annotation_chain and cursor < start:
            while cursor < start and not lines[cursor].strip():
                cursor += 1
            if cursor >= start:
                break
            if not lines[cursor].lstrip().startswith("@"):
                valid_annotation_chain = False
                break
            saw_annotation = True
            annotation_end = annotation_block_end(lines, cursor)
            if annotation_end < cursor or annotation_end >= start:
                valid_annotation_chain = False
                break
            cursor = annotation_end + 1
        if is_generated and valid_annotation_chain and saw_annotation and cursor == start:
            changed = True
            i = end + 1
            continue
        out.extend(lines[start:end + 1])
        i = end + 1
    return out, changed


def remove_generated_parameter_blocks(lines: list[str]) -> tuple[list[str], bool]:
    """Remove generated Javadocs that were accidentally inserted inside method parameter lists."""
    changed = False
    out: list[str] = []
    i = 0
    while i < len(lines):
        if "/**" not in lines[i]:
            out.append(lines[i])
            i += 1
            continue
        start = i
        end = i
        while end < len(lines) and "*/" not in lines[end]:
            end += 1
        if end >= len(lines):
            out.append(lines[i])
            i += 1
            continue
        block = "".join(lines[start:end + 1])
        is_generated = generated_javadoc_start(lines[:end + 1], end + 1) == start
        next_nonblank = end + 1
        while next_nonblank < len(lines) and not lines[next_nonblank].strip():
            next_nonblank += 1
        prev_nonblank = start - 1
        while prev_nonblank >= 0 and not lines[prev_nonblank].strip():
            prev_nonblank -= 1
        next_is_param = next_nonblank < len(lines) and lines[next_nonblank].strip().startswith("@Param(")
        prev_continues_signature = prev_nonblank >= 0 and not lines[prev_nonblank].strip().endswith(";")
        if is_generated and next_is_param and prev_continues_signature:
            changed = True
            i = end + 1
            continue
        out.extend(lines[start:end + 1])
        i = end + 1
    return out, changed


def move_generated_blocks_before_annotations(lines: list[str]) -> tuple[list[str], bool]:
    """Move generated method Javadocs from after annotations to before the annotation block."""
    changed = False
    out = list(lines)
    i = 0
    while i < len(out):
        if "/**" not in out[i]:
            i += 1
            continue
        start = i
        end = i
        while end < len(out) and "*/" not in out[end]:
            end += 1
        if end >= len(out):
            i += 1
            continue
        is_generated = generated_javadoc_start(out[:end + 1], end + 1) == start
        if not is_generated:
            i = end + 1
            continue
        prev = start - 1
        while prev >= 0 and not out[prev].strip():
            prev -= 1
        if prev < 0:
            i = end + 1
            continue
        segment_start = prev
        while segment_start > 0 and out[segment_start - 1].strip():
            segment_start -= 1
        segment = out[segment_start:start]
        if not any(line.strip().startswith("@") for line in segment):
            i = end + 1
            continue
        if any(line.strip().endswith("*/") for line in segment):
            i = end + 1
            continue
        block = out[start:end + 1]
        del out[start:end + 1]
        out[segment_start:segment_start] = block
        changed = True
        i = segment_start + len(block) + len(segment)
    return out, changed


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    lines, duplicate_changed = remove_generated_duplicate_blocks(lines)
    lines, parameter_changed = remove_generated_parameter_blocks(lines)
    # Javadoc must precede the annotation block to document the declaration.
    # Keeping it between annotations and a method leaves an orphan comment and
    # also prevents architecture scanners from recognizing the annotated method.
    lines, placement_changed = move_generated_blocks_before_annotations(lines)
    pkg = package_name(text)
    date = created_date(path, text)
    out: list[str] = []
    changed = duplicate_changed or parameter_changed or placement_changed
    depth = 0
    in_block = False
    in_string = False
    type_stack: list[TypeContext] = []
    method_signature_end = -1
    annotation_end = -1

    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        while type_stack and depth <= type_stack[-1].depth:
            type_stack.pop()
        if i <= method_signature_end:
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            i += 1
            continue
        if i <= annotation_end:
            out.append(line)
            i += 1
            continue
        if stripped.startswith("@") and not TYPE_RE.match(line):
            annotation_end = annotation_block_end(lines, i)
            out.append(line)
            i += 1
            continue

        match_type = TYPE_RE.match(line)
        if match_type and not stripped.startswith("*") and not stripped.startswith("//"):
            indent = match_type.group("indent")
            kind = match_type.group("kind")
            name = match_type.group("name")
            mods = match_type.group("mods") or ""
            is_top = depth == 0
            is_public_static_nested = depth > 0 and "public" in mods.split() and "static" in mods.split()
            if (is_main_java(path) and (is_top or is_public_static_nested)
                    and not has_javadoc_before(out, len(out), len(indent))):
                out.extend(type_javadoc(path, kind, name, pkg, date, indent))
                changed = True
            type_stack.append(TypeContext(kind, name, depth))
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            i += 1
            continue

        current_type = type_stack[-1] if type_stack else None
        enum_value = ENUM_VALUE_RE.match(line)
        if (is_main_java(path) and current_type and current_type.kind == "enum"
                and depth == current_type.depth + 1 and enum_value
                and not has_javadoc_before(out, len(out), len(enum_value.group("indent")))):
            out.extend(enum_value_javadoc(enum_value.group("name"), enum_value.group("indent")))
            changed = True
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            i += 1
            continue

        field = FIELD_RE.match(line)
        if field and current_type and depth == current_type.depth + 1 and not stripped.startswith("return "):
            names_raw = field.group("names")
            first_name = re.split(r"\s*=|,", names_raw, maxsplit=1)[0].strip()
            mods = set((field.group("mods") or "").split())
            type_name = field.group("type").split()[-1].strip()
            required_field = (is_main_java(path) and current_type.kind != "record"
                              and (is_model_path(path) or {"static", "final"}.issubset(mods)))
            if (required_field and first_name not in LOGGER_NAMES
                    and first_name not in TECHNICAL_STATIC_FIELDS
                    and not has_javadoc_before(out, len(out), len(field.group("indent")))):
                out.extend(field_javadoc(first_name, type_name, field.group("indent"), {"static", "final"}.issubset(mods)))
                changed = True
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            i += 1
            continue

        method = METHOD_RE.match(line)
        collected_signature = None
        if not method and current_type and depth == current_type.depth + 1:
            collected_signature = collected_method_signature(lines, i)
            if collected_signature:
                method = METHOD_RE.match(collected_signature[0])
                if method:
                    method_signature_end = i + collected_signature[1] - 1
        if method and current_type and depth == current_type.depth + 1:
            name = method.group("name")
            mods = set((method.group("mods") or "").split())
            ret = method.group("ret")
            declaration_indent = line[:len(line) - len(line.lstrip())]
            is_constructor = name == current_type.name and ret is None
            method_params = params_from_signature(method.group("params"))
            # Simple dependency-injection constructors and model accessors are
            # self-explanatory and explicitly excluded by AGENTS.md.
            if is_constructor or is_simple_accessor(path, name, method_params):
                out.append(line)
                delta, in_block, in_string = current_depth(line, in_block, in_string)
                depth += delta
                i += 1
                continue
            # Private methods require comments only when a reviewer has identified a
            # non-obvious business rule. Set the opt-in flag for that focused pass;
            # the default gate must not annotate every local helper mechanically.
            is_private_core = ("private" in mods
                               and is_core_private_method(path, name, lines, i))
            is_required_method = (is_main_java(path)
                                  and ("public" in mods or "protected" in mods
                                       or current_type.kind == "interface" or is_private_core))
            if is_constructor and is_private_core and is_empty_private_constructor(lines, i, current_type.name):
                out.append(line)
                delta, in_block, in_string = current_depth(line, in_block, in_string)
                depth += delta
                i += 1
                continue
            has_existing_javadoc = has_javadoc_before(out, len(out), len(declaration_indent))
            if is_required_method and name not in SKIP_METHOD_NAMES and not has_existing_javadoc:
                has_return = ret is not None and ret.strip() != "void"
                out.extend(method_javadoc(
                    name,
                    method_params,
                    has_return,
                    current_type.kind == "interface",
                    declaration_indent,
                    owner_name=current_type.name,
                    layer=file_layer(path),
                    constructor=is_constructor,
                    private_method=is_private_core,
                ))
                changed = True
            out.append(line)
            delta, in_block, in_string = current_depth(line, in_block, in_string)
            depth += delta
            i += 1
            continue

        out.append(line)
        delta, in_block, in_string = current_depth(line, in_block, in_string)
        depth += delta
        i += 1

    rendered = "".join(out)
    if rendered != text:
        path.write_text(rendered, encoding="utf-8")
        return True
    return False


def main() -> int:
    changed_count = 0
    for path in iter_java_files(ROOT):
        if process_file(path):
            changed_count += 1
    print(f"changed_files={changed_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
