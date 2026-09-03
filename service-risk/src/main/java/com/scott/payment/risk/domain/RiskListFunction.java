package com.scott.payment.risk.domain;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskListFunction
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 运行时可查询的风控名单或规则功能。
 * @status : create
 */
public enum RiskListFunction {

    /** 按商户号哈希匹配商户白名单。 */
    WHITE_MERCHANT(RiskModuleTypeEnum.WHITE, "merchant", "商户白名单", "risk_white_merchant", MatchKind.HASH),
    /** 按卡号不可逆哈希匹配卡号白名单。 */
    WHITE_CARD_NO(RiskModuleTypeEnum.WHITE, "cardNo", "卡号白名单", "risk_white_card_no", MatchKind.HASH),
    /** 按卡指纹哈希匹配卡片白名单。 */
    WHITE_CARD_FINGERPRINT(RiskModuleTypeEnum.WHITE, "cardFingerprint", "卡指纹白名单", "risk_white_card_fingerprint", MatchKind.HASH),
    /** 按规范化 IP 或网段匹配 IP 白名单。 */
    WHITE_IP(RiskModuleTypeEnum.WHITE, "ip", "IP地址白名单", "risk_white_ip", MatchKind.IP_RANGE),
    /** 按卡 BIN 起止区间匹配卡 BIN 白名单。 */
    WHITE_CARD_BIN(RiskModuleTypeEnum.WHITE, "cardBin", "卡BIN/区间白名单", "risk_white_card_bin", MatchKind.CARD_BIN_RANGE),
    /** 按 ISO 国家或地区代码匹配交易国家白名单。 */
    WHITE_TRADE_COUNTRY(RiskModuleTypeEnum.WHITE, "tradeCountry", "交易国家/地区白名单", "risk_white_trade_country", MatchKind.COUNTRY),
    /** 按 ISO 国家或地区代码匹配发卡行国家白名单。 */
    WHITE_ISSUER_COUNTRY(RiskModuleTypeEnum.WHITE, "issuerCountry", "发卡行国家/地区白名单", "risk_white_issuer_country", MatchKind.COUNTRY),
    /** 按规范化邮箱哈希匹配邮箱白名单。 */
    WHITE_EMAIL(RiskModuleTypeEnum.WHITE, "email", "邮箱地址白名单", "risk_white_email", MatchKind.HASH),
    /** 按规范化邮箱域名哈希匹配域名白名单。 */
    WHITE_EMAIL_DOMAIN(RiskModuleTypeEnum.WHITE, "emailDomain", "邮箱域名白名单", "risk_white_email_domain", MatchKind.HASH),
    /** 按规范化手机号哈希匹配手机号白名单。 */
    WHITE_PHONE(RiskModuleTypeEnum.WHITE, "phone", "手机号白名单", "risk_white_phone", MatchKind.HASH),
    /** 按商户侧客户标识哈希匹配客户白名单。 */
    WHITE_CUSTOMER_ID(RiskModuleTypeEnum.WHITE, "customerId", "Customer ID 白名单", "risk_white_customer_id", MatchKind.HASH),
    /** 按设备指纹哈希匹配设备白名单。 */
    WHITE_DEVICE_FINGERPRINT(RiskModuleTypeEnum.WHITE, "deviceFingerprint", "设备指纹白名单", "risk_white_device_fingerprint", MatchKind.HASH),

    /** 按卡号不可逆哈希匹配卡号黑名单。 */
    BLACK_CARD_NO(RiskModuleTypeEnum.BLACK, "cardNo", "卡号黑名单", "risk_black_card_no", MatchKind.HASH),
    /** 按卡指纹哈希匹配卡片黑名单。 */
    BLACK_CARD_FINGERPRINT(RiskModuleTypeEnum.BLACK, "cardFingerprint", "卡指纹黑名单", "risk_black_card_fingerprint", MatchKind.HASH),
    /** 按规范化 IP 或网段匹配 IP 黑名单。 */
    BLACK_IP(RiskModuleTypeEnum.BLACK, "ip", "IP地址/区间黑名单", "risk_black_ip", MatchKind.IP_RANGE),
    /** 按卡 BIN 起止区间匹配卡 BIN 黑名单。 */
    BLACK_CARD_BIN(RiskModuleTypeEnum.BLACK, "cardBin", "卡BIN/区间黑名单", "risk_black_card_bin", MatchKind.CARD_BIN_RANGE),
    /** 按规范化持卡人姓名哈希匹配姓名黑名单。 */
    BLACK_CARDHOLDER_NAME(RiskModuleTypeEnum.BLACK, "cardholderName", "持卡人姓名黑名单", "risk_black_cardholder_name", MatchKind.HASH),
    /** 按规范化邮箱哈希匹配邮箱黑名单。 */
    BLACK_EMAIL(RiskModuleTypeEnum.BLACK, "email", "邮箱地址黑名单", "risk_black_email", MatchKind.HASH),
    /** 按规范化邮箱域名哈希匹配域名黑名单。 */
    BLACK_EMAIL_DOMAIN(RiskModuleTypeEnum.BLACK, "emailDomain", "邮箱域名黑名单", "risk_black_email_domain", MatchKind.HASH),
    /** 按规范化邮箱用户名哈希匹配用户名黑名单。 */
    BLACK_EMAIL_USERNAME(RiskModuleTypeEnum.BLACK, "emailUsername", "邮箱用户名黑名单", "risk_black_email_username", MatchKind.HASH),
    /** 按规范化电话号码哈希匹配电话黑名单。 */
    BLACK_PHONE(RiskModuleTypeEnum.BLACK, "phone", "电话号码黑名单", "risk_black_phone", MatchKind.HASH),
    /** 按国家与行政区组合匹配高风险区域。 */
    BLACK_REGION(RiskModuleTypeEnum.BLACK, "region", "高风险区域黑名单", "risk_black_region", MatchKind.REGION),
    /** 按规范化账单地址哈希匹配地址黑名单。 */
    BLACK_BILLING_ADDRESS(RiskModuleTypeEnum.BLACK, "billingAddress", "账单地址黑名单", "risk_black_billing_address", MatchKind.HASH),
    /** 按规范化账单邮编哈希匹配邮编黑名单。 */
    BLACK_BILLING_ZIP(RiskModuleTypeEnum.BLACK, "billingZip", "账单邮编黑名单", "risk_black_billing_zip", MatchKind.HASH),
    /** 按 ISO 国家或地区代码匹配账单国家黑名单。 */
    BLACK_BILLING_COUNTRY(RiskModuleTypeEnum.BLACK, "billingCountry", "账单国家/地区黑名单", "risk_black_billing_country", MatchKind.COUNTRY),
    /** 按规范化收货地址哈希匹配地址黑名单。 */
    BLACK_SHIPPING_ADDRESS(RiskModuleTypeEnum.BLACK, "shippingAddress", "收货地址黑名单", "risk_black_shipping_address", MatchKind.HASH),
    /** 按规范化收货邮编哈希匹配邮编黑名单。 */
    BLACK_SHIPPING_ZIP(RiskModuleTypeEnum.BLACK, "shippingZip", "收货邮编黑名单", "risk_black_shipping_zip", MatchKind.HASH),
    /** 按 ISO 国家或地区代码匹配收货国家黑名单。 */
    BLACK_SHIPPING_COUNTRY(RiskModuleTypeEnum.BLACK, "shippingCountry", "收货国家/地区黑名单", "risk_black_shipping_country", MatchKind.COUNTRY),
    /** 按 ISO 国家或地区代码匹配发卡行国家黑名单。 */
    BLACK_ISSUER_COUNTRY(RiskModuleTypeEnum.BLACK, "issuerCountry", "发卡行国家/地区黑名单", "risk_black_issuer_country", MatchKind.COUNTRY),
    /** 按设备指纹哈希匹配设备黑名单。 */
    BLACK_DEVICE_FINGERPRINT(RiskModuleTypeEnum.BLACK, "deviceFingerprint", "设备指纹黑名单", "risk_black_device_fingerprint", MatchKind.HASH),

    /** 按卡号哈希或卡指纹哈希匹配 AML 卡片名单。 */
    AML_CARD(RiskModuleTypeEnum.AML, "card", "卡号/卡指纹AML", "risk_aml_card", MatchKind.HASH),
    /** 按规范化 IP 或网段匹配 AML IP 名单。 */
    AML_IP(RiskModuleTypeEnum.AML, "ip", "IP地址/区间AML", "risk_aml_ip", MatchKind.IP_RANGE),
    /** 按卡 BIN 起止区间匹配 AML 卡 BIN 名单。 */
    AML_CARD_BIN(RiskModuleTypeEnum.AML, "cardBin", "卡BIN/区间AML", "risk_aml_card_bin", MatchKind.CARD_BIN_RANGE),
    /** 按 ISO 国家或地区代码匹配 AML 国家名单。 */
    AML_COUNTRY(RiskModuleTypeEnum.AML, "country", "国家/地区AML", "risk_aml_country", MatchKind.COUNTRY),
    /** 按规范化邮箱或域名哈希匹配 AML 邮箱名单。 */
    AML_EMAIL(RiskModuleTypeEnum.AML, "email", "邮箱/域名AML", "risk_aml_email", MatchKind.HASH),
    /** 按规范化手机号哈希匹配 AML 电话名单。 */
    AML_PHONE(RiskModuleTypeEnum.AML, "phone", "手机号AML", "risk_aml_phone", MatchKind.HASH),
    /** 按规范化持卡人姓名哈希匹配 AML 姓名名单。 */
    AML_CARDHOLDER_NAME(RiskModuleTypeEnum.AML, "cardholderName", "持卡人姓名AML", "risk_aml_cardholder_name", MatchKind.HASH),
    /** 按规范化法人名称哈希匹配 AML 法人名单。 */
    AML_LEGAL_PERSON(RiskModuleTypeEnum.AML, "legalPerson", "AML法人", "risk_aml_legal_person", MatchKind.HASH),
    /** 按规范化企业名称哈希匹配 AML 企业名单。 */
    AML_ENTERPRISE(RiskModuleTypeEnum.AML, "enterprise", "AML企业", "risk_aml_enterprise", MatchKind.HASH),
    /** 按规范化商户账单地址哈希匹配 AML 地址名单。 */
    AML_MERCHANT_BILLING_ADDRESS(RiskModuleTypeEnum.AML, "merchantBillingAddress", "AML（商户）账单地址", "risk_aml_merchant_billing_address", MatchKind.HASH),
    /** 按规范化来源主机名匹配 AML 来源网址规则。 */
    AML_SOURCE_URL(RiskModuleTypeEnum.AML, "sourceUrl", "来源网址AML", "risk_aml_source_url", MatchKind.SOURCE_HOST);

    private static final Set<String> TABLE_NAMES = Set.of(
            "risk_white_merchant",
            "risk_white_card_no",
            "risk_white_card_fingerprint",
            "risk_white_ip",
            "risk_white_card_bin",
            "risk_white_trade_country",
            "risk_white_issuer_country",
            "risk_white_email",
            "risk_white_email_domain",
            "risk_white_phone",
            "risk_white_customer_id",
            "risk_white_device_fingerprint",
            "risk_black_card_no",
            "risk_black_card_fingerprint",
            "risk_black_ip",
            "risk_black_card_bin",
            "risk_black_cardholder_name",
            "risk_black_email",
            "risk_black_email_domain",
            "risk_black_email_username",
            "risk_black_phone",
            "risk_black_region",
            "risk_black_billing_address",
            "risk_black_billing_zip",
            "risk_black_billing_country",
            "risk_black_shipping_address",
            "risk_black_shipping_zip",
            "risk_black_shipping_country",
            "risk_black_issuer_country",
            "risk_black_device_fingerprint",
            "risk_aml_card",
            "risk_aml_ip",
            "risk_aml_card_bin",
            "risk_aml_country",
            "risk_aml_email",
            "risk_aml_phone",
            "risk_aml_cardholder_name",
            "risk_aml_legal_person",
            "risk_aml_enterprise",
            "risk_aml_merchant_billing_address",
            "risk_aml_source_url"
    );

    /** 功能所属的白名单、黑名单或 AML 模块。 */
    private final RiskModuleTypeEnum moduleType;

    /** 对外稳定的规则功能编码。 */
    private final String functionCode;

    /** 用于审计和管理端展示的功能名称。 */
    private final String functionName;

    /** 存放该类规则的受控数据库表名。 */
    private final String tableName;

    /** 该功能查询值与规则记录之间的匹配方式。 */
    private final MatchKind matchKind;

    RiskListFunction(RiskModuleTypeEnum moduleType,
                     String functionCode,
                     String functionName,
                     String tableName,
                     MatchKind matchKind) {
        this.moduleType = moduleType;
        this.functionCode = functionCode;
        this.functionName = functionName;
        this.tableName = tableName;
        this.matchKind = matchKind;
    }

    /**
     * 返回规则功能所属模块。
     *
     * @return 白名单、黑名单或 AML 模块
     */
    public RiskModuleTypeEnum getModuleType() {
        return moduleType;
    }

    /**
     * 返回对外稳定的规则功能编码。
     *
     * @return 规则功能编码
     */
    public String getFunctionCode() {
        return functionCode;
    }

    /**
     * 返回用于审计展示的规则功能名称。
     *
     * @return 规则功能名称
     */
    public String getFunctionName() {
        return functionName;
    }

    /**
     * 返回该功能对应的受控数据库表名。
     *
     * @return 已列入白名单的规则表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 返回规则查询采用的匹配方式。
     *
     * @return 哈希、区间或地域等匹配类型
     */
    public MatchKind getMatchKind() {
        return matchKind;
    }

    /**
     * 校验动态 SQL 使用的规则表名是否在固定白名单内。
     *
     * @param tableName 待校验的物理表名
     * @throws IllegalArgumentException 表名未登记时抛出，避免动态表名注入
     */
    public static void requireKnownTable(String tableName) {
        if (!TABLE_NAMES.contains(tableName)) {
            throw new IllegalArgumentException("unsupported risk table: " + tableName);
        }
    }

    /**
     * 风控查询值与规则数据的匹配方式。
     */
    public enum MatchKind {
        /** 对规范化后的敏感值计算不可逆哈希并做等值匹配。 */
        HASH,
        /** 将 IP 解析为可比较形式后执行起止区间匹配。 */
        IP_RANGE,
        /** 将卡 BIN 规范化后执行数值起止区间匹配。 */
        CARD_BIN_RANGE,
        /** 按标准国家或地区代码做等值匹配。 */
        COUNTRY,
        /** 按国家与行政区域组合做范围匹配。 */
        REGION,
        /** 从来源 URL 提取规范化主机名后匹配。 */
        SOURCE_HOST
    }
}
