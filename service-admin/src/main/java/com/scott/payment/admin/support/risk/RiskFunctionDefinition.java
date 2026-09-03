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

    /**
     * AML CARD 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_CARD("AML", "card", "卡号/卡指纹AML", "risk_aml_card", "/risk/aml/card", "risk:aml:card", false, false),
    /**
     * AML CARD BIN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_CARD_BIN("AML", "cardBin", "卡BIN/区间AML", "risk_aml_card_bin", "/risk/aml/card-bin", "risk:aml:cardBin", false, false),
    /**
     * AML IP 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_IP("AML", "ip", "IP地址/区间AML", "risk_aml_ip", "/risk/aml/ip", "risk:aml:ip", false, false),
    /**
     * AML COUNTRY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_COUNTRY("AML", "country", "国家/地区AML", "risk_aml_country", "/risk/aml/country", "risk:aml:country", false, false),
    /**
     * AML EMAIL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_EMAIL("AML", "email", "邮箱/域名AML", "risk_aml_email", "/risk/aml/email", "risk:aml:email", false, false),
    /**
     * AML PHONE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_PHONE("AML", "phone", "手机号AML", "risk_aml_phone", "/risk/aml/phone", "risk:aml:phone", false, false),
    /**
     * AML CARDHOLDER NAME 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_CARDHOLDER_NAME("AML", "cardholderName", "持卡人姓名AML", "risk_aml_cardholder_name", "/risk/aml/cardholder-name", "risk:aml:cardholderName", false, false),
    /**
     * AML LEGAL PERSON 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_LEGAL_PERSON("AML", "legalPerson", "AML法人", "risk_aml_legal_person", "/risk/aml/legal-person", "risk:aml:legalPerson", false, false),
    /**
     * AML ENTERPRISE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_ENTERPRISE("AML", "enterprise", "AML企业", "risk_aml_enterprise", "/risk/aml/enterprise", "risk:aml:enterprise", false, false),
    /**
     * AML MERCHANT BILLING ADDRESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_MERCHANT_BILLING_ADDRESS("AML", "merchantBillingAddress", "AML（商户）账单地址", "risk_aml_merchant_billing_address", "/risk/aml/merchant-billing-address", "risk:aml:merchantBillingAddress", false, false),
    /**
     * AML SOURCE URL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AML_SOURCE_URL("AML", "sourceUrl", "来源网址AML", "risk_aml_source_url", "/risk/aml/source-url", "risk:aml:sourceUrl", false, false),

    /**
     * BLACK CARD NO 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_CARD_NO("BLACK", "cardNo", "卡号黑名单", "risk_black_card_no", "/risk/blacklist/card-no", "risk:blacklist:cardNo", false, false),
    /**
     * BLACK CARD FINGERPRINT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_CARD_FINGERPRINT("BLACK", "cardFingerprint", "卡指纹黑名单", "risk_black_card_fingerprint", "/risk/blacklist/card-fingerprint", "risk:blacklist:cardFingerprint", false, false),
    /**
     * BLACK CARD BIN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_CARD_BIN("BLACK", "cardBin", "卡BIN/区间黑名单", "risk_black_card_bin", "/risk/blacklist/card-bin", "risk:blacklist:cardBin", false, false),
    /**
     * BLACK CARDHOLDER NAME 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_CARDHOLDER_NAME("BLACK", "cardholderName", "持卡人姓名黑名单", "risk_black_cardholder_name", "/risk/blacklist/cardholder-name", "risk:blacklist:cardholderName", false, false),
    /**
     * BLACK PHONE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_PHONE("BLACK", "phone", "电话号码黑名单", "risk_black_phone", "/risk/blacklist/phone", "risk:blacklist:phone", false, false),
    /**
     * BLACK IP 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_IP("BLACK", "ip", "IP地址/区间黑名单", "risk_black_ip", "/risk/blacklist/ip", "risk:blacklist:ip", false, false),
    /**
     * BLACK REGION 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_REGION("BLACK", "region", "高风险区域黑名单", "risk_black_region", "/risk/blacklist/region", "risk:blacklist:region", true, false),
    /**
     * BLACK EMAIL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_EMAIL("BLACK", "email", "邮箱地址黑名单", "risk_black_email", "/risk/blacklist/email", "risk:blacklist:email", false, false),
    /**
     * BLACK EMAIL USERNAME 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_EMAIL_USERNAME("BLACK", "emailUsername", "邮箱用户名黑名单", "risk_black_email_username", "/risk/blacklist/email-username", "risk:blacklist:emailUsername", false, false),
    /**
     * BLACK EMAIL DOMAIN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_EMAIL_DOMAIN("BLACK", "emailDomain", "邮箱域名黑名单", "risk_black_email_domain", "/risk/blacklist/email-domain", "risk:blacklist:emailDomain", false, false),
    /**
     * BLACK BILLING ADDRESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_BILLING_ADDRESS("BLACK", "billingAddress", "账单地址黑名单", "risk_black_billing_address", "/risk/blacklist/billing-address", "risk:blacklist:billingAddress", false, false),
    /**
     * BLACK BILLING ZIP 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_BILLING_ZIP("BLACK", "billingZip", "账单邮编黑名单", "risk_black_billing_zip", "/risk/blacklist/billing-zip", "risk:blacklist:billingZip", false, false),
    /**
     * BLACK BILLING COUNTRY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_BILLING_COUNTRY("BLACK", "billingCountry", "账单国家/地区黑名单", "risk_black_billing_country", "/risk/blacklist/billing-country", "risk:blacklist:billingCountry", false, false),
    /**
     * BLACK SHIPPING ADDRESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_SHIPPING_ADDRESS("BLACK", "shippingAddress", "收货地址黑名单", "risk_black_shipping_address", "/risk/blacklist/shipping-address", "risk:blacklist:shippingAddress", false, false),
    /**
     * BLACK SHIPPING ZIP 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_SHIPPING_ZIP("BLACK", "shippingZip", "收货邮编黑名单", "risk_black_shipping_zip", "/risk/blacklist/shipping-zip", "risk:blacklist:shippingZip", false, false),
    /**
     * BLACK SHIPPING COUNTRY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_SHIPPING_COUNTRY("BLACK", "shippingCountry", "收货国家/地区黑名单", "risk_black_shipping_country", "/risk/blacklist/shipping-country", "risk:blacklist:shippingCountry", false, false),
    /**
     * BLACK ISSUER COUNTRY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_ISSUER_COUNTRY("BLACK", "issuerCountry", "发卡行国家/地区黑名单", "risk_black_issuer_country", "/risk/blacklist/issuer-country", "risk:blacklist:issuerCountry", false, false),
    /**
     * BLACK DEVICE FINGERPRINT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BLACK_DEVICE_FINGERPRINT("BLACK", "deviceFingerprint", "设备指纹黑名单", "risk_black_device_fingerprint", "/risk/blacklist/device-fingerprint", "risk:blacklist:deviceFingerprint", false, false),

    /**
     * WHITE MERCHANT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_MERCHANT("WHITE", "merchant", "商户白名单", "risk_white_merchant", "/risk/whitelist/merchant", "risk:whitelist:merchant", false, false),
    /**
     * WHITE CARD NO 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_CARD_NO("WHITE", "cardNo", "卡号白名单", "risk_white_card_no", "/risk/whitelist/card-no", "risk:whitelist:cardNo", false, false),
    /**
     * WHITE CARD FINGERPRINT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_CARD_FINGERPRINT("WHITE", "cardFingerprint", "卡指纹白名单", "risk_white_card_fingerprint", "/risk/whitelist/card-fingerprint", "risk:whitelist:cardFingerprint", false, false),
    /**
     * WHITE CARD BIN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_CARD_BIN("WHITE", "cardBin", "卡BIN/区间白名单", "risk_white_card_bin", "/risk/whitelist/card-bin", "risk:whitelist:cardBin", false, false),
    /**
     * WHITE IP 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_IP("WHITE", "ip", "IP地址白名单", "risk_white_ip", "/risk/whitelist/ip", "risk:whitelist:ip", false, false),
    /**
     * WHITE TRADE COUNTRY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_TRADE_COUNTRY("WHITE", "tradeCountry", "交易国家/地区白名单", "risk_white_trade_country", "/risk/whitelist/trade-country", "risk:whitelist:tradeCountry", false, false),
    /**
     * WHITE ISSUER COUNTRY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_ISSUER_COUNTRY("WHITE", "issuerCountry", "发卡行国家/地区白名单", "risk_white_issuer_country", "/risk/whitelist/issuer-country", "risk:whitelist:issuerCountry", false, false),
    /**
     * WHITE EMAIL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_EMAIL("WHITE", "email", "邮箱地址白名单", "risk_white_email", "/risk/whitelist/email", "risk:whitelist:email", false, false),
    /**
     * WHITE EMAIL DOMAIN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_EMAIL_DOMAIN("WHITE", "emailDomain", "邮箱域名白名单", "risk_white_email_domain", "/risk/whitelist/email-domain", "risk:whitelist:emailDomain", false, false),
    /**
     * WHITE PHONE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_PHONE("WHITE", "phone", "手机号白名单", "risk_white_phone", "/risk/whitelist/phone", "risk:whitelist:phone", false, false),
    /**
     * WHITE CUSTOMER ID 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_CUSTOMER_ID("WHITE", "customerId", "Customer ID 白名单", "risk_white_customer_id", "/risk/whitelist/customer-id", "risk:whitelist:customerId", false, false),
    /**
     * WHITE DEVICE FINGERPRINT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WHITE_DEVICE_FINGERPRINT("WHITE", "deviceFingerprint", "设备指纹白名单", "risk_white_device_fingerprint", "/risk/whitelist/device-fingerprint", "risk:whitelist:deviceFingerprint", false, false),

    /**
     * RULE SOURCE URL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RULE_SOURCE_URL("RULE", "sourceUrl", "商户来源网址限定", "risk_rule_source_url", "/risk/rule/source-url", "risk:rule:sourceUrl", false, true),
    /**
     * RULE MERCHANT LIMIT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RULE_MERCHANT_LIMIT("RULE", "merchantLimit", "商户交易限额管理", "risk_rule_merchant_limit", "/risk/rule/merchant-limit", "risk:rule:merchantLimit", false, true),
    /**
     * RULE FREQUENCY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RULE_FREQUENCY("RULE", "frequency", "交易频率限定", "risk_rule_frequency", "/risk/rule/frequency", "risk:rule:frequency", false, true),
    /**
     * RULE 3DS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RULE_3DS("RULE", "threeDs", "3DS规则管理", "risk_rule_3ds", "/risk/rule/3ds", "risk:rule:threeDs", false, true);

    /**
     * {@code moduleType}，用于区分 {@code RiskFunctionDefinition} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String moduleType;
    /**
     * {@code functionCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String functionCode;
    /**
     * {@code functionName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String functionName;
    /**
     * 表名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String tableName;
    /**
     * {@code routePath}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String routePath;
    /**
     * 权限前缀字段，保存 {@code RiskFunctionDefinition} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String permissionPrefix;
    /**
     * {@code regionFunction}，用于明确 {@code RiskFunctionDefinition} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final boolean regionFunction;
    /**
     * {@code ruleFunction}，用于明确 {@code RiskFunctionDefinition} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "风控功能不存在"));
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
