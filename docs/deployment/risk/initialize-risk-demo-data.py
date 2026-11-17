"""通过已登录管理端的正式 API 初始化停用演示配置；默认只读预演。"""

import argparse
import hashlib
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, build_opener, ProxyHandler, HTTPRedirectHandler


MARKER = "DEMO_RISK_V1"


class NoRedirect(HTTPRedirectHandler):
    """阻止认证 Cookie 随重定向离开指定管理端。"""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class AdminClient:
    """仅在回环地址使用已有会话；不接受账号密码，不输出认证材料。"""

    def __init__(self, origin, state_file):
        parsed = urlparse(origin)
        if parsed.scheme not in ("http", "https") or parsed.hostname not in ("127.0.0.1", "localhost"):
            raise ValueError("仅允许本机开发环境回环地址")
        if parsed.path not in ("", "/") or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("origin 必须是无凭据的本机源地址")
        self.origin = origin.rstrip("/")
        state_path = Path(state_file)
        if state_path.stat().st_mode & 0o077:
            raise ValueError("会话文件权限必须为 0600")
        state = json.loads(state_path.read_text())
        cookies = [c for c in state.get("cookies", []) if c["domain"].lstrip(".") == parsed.hostname]
        self.headers = {
            "Content-Type": "application/json",
            "Accept-Language": "zh-CN",
            "X-Auth-Mode": "cookie",
            "Origin": self.origin,
            "Referer": self.origin + "/",
            "Cookie": "; ".join(c["name"] + "=" + c["value"] for c in cookies),
        }
        csrf = next((c["value"] for c in cookies if c["name"] == "VEXRA_ADMIN_CSRF"), None)
        if not csrf:
            raise ValueError("缺少管理端 CSRF Cookie，请先登录独立验收浏览器")
        self.headers["X-CSRF-Token"] = csrf
        self.opener = build_opener(ProxyHandler({}), NoRedirect())

    def call(self, method, path, data=None):
        body = None if data is None else json.dumps(data).encode()
        request = Request(self.origin + path, data=body, headers=self.headers, method=method)
        try:
            with self.opener.open(request, timeout=30) as response:
                result = json.load(response)
        except HTTPError as error:
            raise RuntimeError(f"API 请求失败: {method} {path}, HTTP {error.code}") from None
        if str(result.get("code")) not in ("T200", "200", "0"):
            # 服务端错误正文可能包含录入值，不将其写到终端或报告。
            raise RuntimeError(f"API 拒绝: {method} {path}, code={result.get('code')}")
        return result.get("data")


def sample_value(code, serial):
    """生成虚构样本；卡值仅在调用栈中构造，不包含 CVV。"""
    if code in ("card", "cardNo"):
        return "4" + str(900000000000000 + serial)
    if code in ("cardFingerprint", "deviceFingerprint"):
        return hashlib.sha256(f"risk-demo-{code}-{serial}".encode()).hexdigest()
    if code == "phone":
        return "+1202555" + str(100 + serial % 100).zfill(4)
    if code == "email":
        return f"risk-demo-{serial}@example.invalid"
    if code == "emailDomain":
        return f"demo-{serial}.example.invalid"
    if code == "emailUsername":
        return f"risk.demo.{serial}"
    if code in ("billingZip", "shippingZip"):
        return str(90000 + serial)
    if code == "sourceUrl":
        return f"https://demo-{serial}.example.invalid"
    if code in ("billingAddress", "shippingAddress", "merchantBillingAddress"):
        return f"演示专用地址 {serial} 号（虚构）"
    if code in ("cardholderName", "legalPerson"):
        return f"演示姓名{serial}"
    if code == "enterprise":
        return f"演示企业{serial}（虚构）"
    return f"DEMO-{code.upper()}-{serial}"


def sample_payload(definition, merchant_id, serial):
    """根据后端功能定义生成符合现有字段约束的停用配置。"""
    module, code = definition["moduleType"], definition["functionCode"]
    marker = f"{MARKER}:{module}:{code}:{merchant_id}"
    data = {
        "merchantScope": "GLOBAL" if module == "AML" else "MERCHANT",
        "merchantId": "" if module == "AML" else merchant_id,
        "status": 0,
        "riskLevel": "LOW" if module == "WHITE" else "HIGH",
        "decisionAction": "PASS" if module == "WHITE" else "REJECT",
        "validityType": "SUPER_LONG",
        "remark": f"{marker}；功能演示，默认停用；可编辑，不用于真实交易",
    }
    if module == "RULE":
        data.update(ruleName=f"演示-{definition['functionName']}-{merchant_id}", currency="USD")
        if code == "sourceUrl":
            data["sourceUrls"] = [f"https://risk-demo-{merchant_id}.example.invalid"]
        elif code == "merchantLimit":
            data.update(limitType="SINGLE_MAX", amountMax=str(20000 + serial))
        elif code == "frequency":
            data.update(timeWindowSeconds=300, thresholdCount=10, elementsJson=json.dumps({
                "elements": ["ip", "customerId"], "statisticDimension": "ELEMENT_COMBINATION",
                "windowUnit": "MINUTE", "windowValue": 5, "allowedCount": 10, "successCount": 5,
            }))
        elif code == "threeDs":
            data.update(ruleType="RISK_STRATEGY", channelCode="ALL", paymentMethod="BANK_CARD",
                        cardBrand="VISA", amountMatchType="GE", amountMin=str(100 + serial),
                        riskCondition="ANY", triggerAction="FORCE_3DS", priority=900)
        else:
            raise ValueError(f"尚未定义演示规则: {code}")
    elif code == "region":
        data.update(regionMatchLevel="COUNTRY", countryAlpha2="GB")
    elif code == "merchant":
        data["matchValuePlain"] = merchant_id
    elif code == "cardBin":
        data.update(matchValueStart="400000", matchValueEnd="400000", cardBrand="VISA")
    elif code == "ip":
        address = f"192.0.2.{1 + serial % 200}"
        data.update(matchValuePlain=address, matchValueStart=address, matchValueEnd=address)
    elif code in ("country", "tradeCountry", "billingCountry", "shippingCountry", "issuerCountry"):
        data.update(countryAlpha2="GB", countryAlpha3="GBR", countryNumeric="826")
    else:
        data["matchValuePlain"] = sample_value(code, serial)
    return marker, data


def existing_demo(client, definition, merchant_id, marker):
    """读取脱敏分页结果识别演示标记，不读取编辑态敏感明文。"""
    module, code = definition["moduleType"], definition["functionCode"]
    path = f"/admin/risk/rule/{code}/page" if module == "RULE" else f"/admin/risk/list/{module}/{code}/page"
    for page in range(1, 101):
        result = client.call("POST", path, {"pageNo": page, "pageSize": 100,
                                           "merchantId": "" if module == "AML" else merchant_id})
        rows = result.get("records", [])
        if any(str(row.get("remark", "")).startswith(marker + "；") for row in rows):
            return True
        if page * 100 >= result.get("total", 0):
            return False
    raise RuntimeError("分页超过安全上限，停止初始化")


def seed(client, merchants, apply=False):
    """通过正式业务写入保证校验、加密、缓存失效和操作审计正常执行。"""
    definitions = client.call("GET", "/admin/risk/functions")
    # 先验证全部目标商户，避免输入错误后才留下半批演示配置。
    for merchant in merchants:
        result = client.call("POST", "/admin/merchants/search", {"pageNo": 1, "pageSize": 100, "merchantId": merchant})
        if not any(row.get("merchantId") == merchant for row in result.get("records", [])):
            raise ValueError(f"无权访问目标商户或商户不存在: {merchant}")
    created = skipped = planned = 0
    for index, definition in enumerate(definitions):
        if definition["moduleType"] not in ("AML", "BLACK", "WHITE", "RULE"):
            continue
        targets = merchants[:1] if definition["moduleType"] == "AML" else merchants
        for merchant in targets:
            marker, data = sample_payload(definition, merchant, index + 1)
            if existing_demo(client, definition, merchant, marker):
                skipped += 1
                continue
            planned += 1
            if not apply:
                continue
            module, code = definition["moduleType"], definition["functionCode"]
            path = f"/admin/risk/rule/{code}" if module == "RULE" else (
                "/admin/risk/region" if code == "region" else f"/admin/risk/list/{module}/{code}")
            result = client.call("POST", path, data)
            records = result if isinstance(result, list) else [result]
            if not records or any(row.get("status") != 0 for row in records):
                raise RuntimeError(f"演示配置未按停用状态创建: {module}/{code}")
            created += len(records)
    return {"dryRun": not apply, "planned": planned, "created": created, "existing": skipped}


def main():
    """显式 apply 才写入；不在脚本中保存会话或登录凭据。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", default="http://127.0.0.1:5173")
    parser.add_argument("--state-file", required=True)
    parser.add_argument("--merchant", action="append", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    result = seed(AdminClient(args.origin, args.state_file), list(dict.fromkeys(args.merchant)), args.apply)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
