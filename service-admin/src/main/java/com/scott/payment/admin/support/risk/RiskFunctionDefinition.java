package com.scott.payment.admin.support.risk;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.util.Arrays;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskFunctionDefinition
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控管理功能白名单定义，固定功能码、物理表名、路由和权限前缀，禁止请求参数直接决定动态表名。
 * @status : create
 */
public enum RiskFunctionDefinition {

    AML_CARD("AML", "card", "卡号/卡指纹AML", "risk_aml_card", "/risk/aml/card", "risk:aml:card", false, false),
    AML_CARD_BIN("AML", "cardBin", "卡BIN/区间AML", "risk_aml_card_bin", "/risk/aml/card-bin", "risk:aml:cardBin", false, false),
    AML_IP("AML", "ip", "IP地址/区间AML", "risk_aml_ip", "/risk/aml/ip", "risk:aml:ip", false, false),
    AML_COUNTRY("AML", "country", "国家/地区AML", "risk_aml_country", "/risk/aml/country", "risk:aml:country", false, false),
    AML_EMAIL("AML", "email", "邮箱/域名AML", "risk_aml_email", "/risk/aml/email", "risk:aml:email", false, false),
    AML_PHONE("AML", "phone", "手机号AML", "risk_aml_phone", "/risk/aml/phone", "risk:aml:phone", false, false),
    AML_CARDHOLDER_NAME("AML", "cardholderName", "持卡人姓名AML", "risk_aml_cardholder_name", "/risk/aml/cardholder-name", "risk:aml:cardholderName", false, false),
    AML_SOURCE_URL("AML", "sourceUrl", "来源网址AML", "risk_aml_source_url", "/risk/aml/source-url", "risk:aml:sourceUrl", false, false),

    BLACK_CARD_NO("BLACK", "cardNo", "卡号黑名单", "risk_black_card_no", "/risk/blacklist/card-no", "risk:blacklist:cardNo", false, false),
    BLACK_CARD_FINGERPRINT("BLACK", "cardFingerprint", "卡指纹黑名单", "risk_black_card_fingerprint", "/risk/blacklist/card-fingerprint", "risk:blacklist:cardFingerprint", false, false),
    BLACK_CARD_BIN("BLACK", "cardBin", "卡BIN/区间黑名单", "risk_black_card_bin", "/risk/blacklist/card-bin", "risk:blacklist:cardBin", false, false),
    BLACK_CARDHOLDER_NAME("BLACK", "cardholderName", "持卡人姓名黑名单", "risk_black_cardholder_name", "/risk/blacklist/cardholder-name", "risk:blacklist:cardholderName", false, false),
    BLACK_PHONE("BLACK", "phone", "电话号码黑名单", "risk_black_phone", "/risk/blacklist/phone", "risk:blacklist:phone", false, false),
    BLACK_IP("BLACK", "ip", "IP地址/区间黑名单", "risk_black_ip", "/risk/blacklist/ip", "risk:blacklist:ip", false, false),
    BLACK_REGION("BLACK", "region", "高风险区域黑名单", "risk_black_region", "/risk/blacklist/region", "risk:blacklist:region", true, false),
    BLACK_EMAIL("BLACK", "email", "邮箱地址黑名单", "risk_black_email", "/risk/blacklist/email", "risk:blacklist:email", false, false),
    BLACK_EMAIL_USERNAME("BLACK", "emailUsername", "邮箱用户名黑名单", "risk_black_email_username", "/risk/blacklist/email-username", "risk:blacklist:emailUsername", false, false),
    BLACK_EMAIL_DOMAIN("BLACK", "emailDomain", "邮箱域名黑名单", "risk_black_email_domain", "/risk/blacklist/email-domain", "risk:blacklist:emailDomain", false, false),
    BLACK_BILLING_ADDRESS("BLACK", "billingAddress", "账单地址黑名单", "risk_black_billing_address", "/risk/blacklist/billing-address", "risk:blacklist:billingAddress", false, false),
    BLACK_BILLING_ZIP("BLACK", "billingZip", "账单邮编黑名单", "risk_black_billing_zip", "/risk/blacklist/billing-zip", "risk:blacklist:billingZip", false, false),
    BLACK_BILLING_COUNTRY("BLACK", "billingCountry", "账单国家/地区黑名单", "risk_black_billing_country", "/risk/blacklist/billing-country", "risk:blacklist:billingCountry", false, false),
    BLACK_SHIPPING_ADDRESS("BLACK", "shippingAddress", "收货地址黑名单", "risk_black_shipping_address", "/risk/blacklist/shipping-address", "risk:blacklist:shippingAddress", false, false),
    BLACK_SHIPPING_ZIP("BLACK", "shippingZip", "收货邮编黑名单", "risk_black_shipping_zip", "/risk/blacklist/shipping-zip", "risk:blacklist:shippingZip", false, false),
    BLACK_SHIPPING_COUNTRY("BLACK", "shippingCountry", "收货国家/地区黑名单", "risk_black_shipping_country", "/risk/blacklist/shipping-country", "risk:blacklist:shippingCountry", false, false),
    BLACK_ISSUER_COUNTRY("BLACK", "issuerCountry", "发卡行国家/地区黑名单", "risk_black_issuer_country", "/risk/blacklist/issuer-country", "risk:blacklist:issuerCountry", false, false),
    BLACK_DEVICE_FINGERPRINT("BLACK", "deviceFingerprint", "设备指纹黑名单", "risk_black_device_fingerprint", "/risk/blacklist/device-fingerprint", "risk:blacklist:deviceFingerprint", false, false),

    WHITE_MERCHANT("WHITE", "merchant", "商户白名单", "risk_white_merchant", "/risk/whitelist/merchant", "risk:whitelist:merchant", false, false),
    WHITE_CARD_NO("WHITE", "cardNo", "卡号白名单", "risk_white_card_no", "/risk/whitelist/card-no", "risk:whitelist:cardNo", false, false),
    WHITE_CARD_FINGERPRINT("WHITE", "cardFingerprint", "卡指纹白名单", "risk_white_card_fingerprint", "/risk/whitelist/card-fingerprint", "risk:whitelist:cardFingerprint", false, false),
    WHITE_CARD_BIN("WHITE", "cardBin", "卡BIN/区间白名单", "risk_white_card_bin", "/risk/whitelist/card-bin", "risk:whitelist:cardBin", false, false),
    WHITE_IP("WHITE", "ip", "IP地址白名单", "risk_white_ip", "/risk/whitelist/ip", "risk:whitelist:ip", false, false),
    WHITE_TRADE_COUNTRY("WHITE", "tradeCountry", "交易国家/地区白名单", "risk_white_trade_country", "/risk/whitelist/trade-country", "risk:whitelist:tradeCountry", false, false),
    WHITE_ISSUER_COUNTRY("WHITE", "issuerCountry", "发卡行国家/地区白名单", "risk_white_issuer_country", "/risk/whitelist/issuer-country", "risk:whitelist:issuerCountry", false, false),
    WHITE_EMAIL("WHITE", "email", "邮箱地址白名单", "risk_white_email", "/risk/whitelist/email", "risk:whitelist:email", false, false),
    WHITE_EMAIL_DOMAIN("WHITE", "emailDomain", "邮箱域名白名单", "risk_white_email_domain", "/risk/whitelist/email-domain", "risk:whitelist:emailDomain", false, false),
    WHITE_PHONE("WHITE", "phone", "手机号白名单", "risk_white_phone", "/risk/whitelist/phone", "risk:whitelist:phone", false, false),
    WHITE_CUSTOMER_ID("WHITE", "customerId", "Customer ID 白名单", "risk_white_customer_id", "/risk/whitelist/customer-id", "risk:whitelist:customerId", false, false),
    WHITE_DEVICE_FINGERPRINT("WHITE", "deviceFingerprint", "设备指纹白名单", "risk_white_device_fingerprint", "/risk/whitelist/device-fingerprint", "risk:whitelist:deviceFingerprint", false, false),

    RULE_SOURCE_URL("RULE", "sourceUrl", "商户来源网址限定", "risk_rule_source_url", "/risk/rule/source-url", "risk:rule:sourceUrl", false, true),
    RULE_MERCHANT_LIMIT("RULE", "merchantLimit", "商户交易限额管理", "risk_rule_merchant_limit", "/risk/rule/merchant-limit", "risk:rule:merchantLimit", false, true),
    RULE_FREQUENCY("RULE", "frequency", "交易频率限定", "risk_rule_frequency", "/risk/rule/frequency", "risk:rule:frequency", false, true),
    RULE_TRADE_COUNTRY("RULE", "tradeCountry", "商户交易国家限定", "risk_rule_trade_country", "/risk/rule/trade-country", "risk:rule:tradeCountry", false, true),
    RULE_ISSUER_COUNTRY("RULE", "issuerCountry", "发卡行国家限定", "risk_rule_issuer_country", "/risk/rule/issuer-country", "risk:rule:issuerCountry", false, true),
    RULE_CARD_BIN("RULE", "cardBin", "卡BIN交易规则", "risk_rule_card_bin", "/risk/rule/card-bin", "risk:rule:cardBin", false, true),
    RULE_3DS("RULE", "threeDs", "3DS规则管理", "risk_rule_3ds", "/risk/rule/3ds", "risk:rule:threeDs", false, true);

    private final String moduleType;
    private final String functionCode;
    private final String functionName;
    private final String tableName;
    private final String routePath;
    private final String permissionPrefix;
    private final boolean regionFunction;
    private final boolean ruleFunction;

    RiskFunctionDefinition(String moduleType,
                           String functionCode,
                           String functionName,
                           String tableName,
                           String routePath,
                           String permissionPrefix,
                           boolean regionFunction,
                           boolean ruleFunction) {
        this.moduleType = moduleType;
        this.functionCode = functionCode;
        this.functionName = functionName;
        this.tableName = tableName;
        this.routePath = routePath;
        this.permissionPrefix = permissionPrefix;
        this.regionFunction = regionFunction;
        this.ruleFunction = ruleFunction;
    }

    /**
     * 按模块和功能码解析定义，不允许任意表名进入 Mapper。
     *
     * @param moduleType   模块类型
     * @param functionCode 功能编码
     * @return 功能定义
     */
    public static RiskFunctionDefinition require(String moduleType, String functionCode) {
        return Arrays.stream(values())
                .filter(item -> item.moduleType.equalsIgnoreCase(moduleType) && item.functionCode.equals(functionCode))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "invalid risk function"));
    }

    /**
     * 获取全部功能定义。
     *
     * @return 功能定义列表
     */
    public static List<RiskFunctionDefinition> all() {
        return Arrays.asList(values());
    }

    /**
     * 获取模块类型，管理端用于区分 AML、黑名单、白名单和规则模块。
     *
     * @return 模块类型
     */
    public String getModuleType() {
        return moduleType;
    }

    /**
     * 获取功能编码，管理端路由和数据库表白名单共同使用。
     *
     * @return 功能编码
     */
    public String getFunctionCode() {
        return functionCode;
    }

    /**
     * 获取功能名称，用于页面标题和配置变更日志。
     *
     * @return 功能中文名称
     */
    public String getFunctionName() {
        return functionName;
    }

    /**
     * 获取物理表名；该值只能来自枚举白名单，禁止由请求参数直接传入。
     *
     * @return 物理表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 获取前端路由路径。
     *
     * @return 管理端前端路由
     */
    public String getRoutePath() {
        return routePath;
    }

    /**
     * 获取前端按钮权限前缀。
     *
     * @return 权限前缀
     */
    public String getPermissionPrefix() {
        return permissionPrefix;
    }

    /**
     * 判断当前功能是否使用高风险区域专用表结构。
     *
     * @return true 表示高风险区域功能
     */
    public boolean isRegionFunction() {
        return regionFunction;
    }

    /**
     * 判断当前功能是否使用内风控规则表结构。
     *
     * @return true 表示规则功能
     */
    public boolean isRuleFunction() {
        return ruleFunction;
    }
}
