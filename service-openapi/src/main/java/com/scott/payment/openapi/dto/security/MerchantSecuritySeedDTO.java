package com.scott.payment.openapi.dto.security;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecuritySeedDTO
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户安全材料初始化入参
 * @status : create
 */
@Data
@NoArgsConstructor
public class MerchantSecuritySeedDTO {

    /**
     * 支付框架颁发的商户号，必须全局唯一。
     */
    private String merchantId;

    /**
     * 商户主体名称，建议与开户注册名称保持一致。
     */
    private String merchantName;

    /**
     * 商户简称，用于后台列表和日志摘要。
     */
    private String merchantShortName;

    /**
     * 商户类别码，外卡收单通常使用 MCC 四位数字。
     */
    private String merchantCategoryCode;

    /**
     * 商户所在国家三字码，使用 ISO 3166-1 alpha-3。
     */
    private String countryCode;

    /**
     * 商户所在州、省或区域代码。
     */
    private String regionCode;

    /**
     * 商户所在城市。
     */
    private String city;

    /**
     * 商户开户地址或经营地址。
     */
    private String addressLine;

    /**
     * 商户联系人邮箱。
     */
    private String contactEmail;

    /**
     * 商户联系人电话。
     */
    private String contactPhone;

    /**
     * 默认结算币种，使用 ISO 4217 三字码。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区。数据库交易时间统一 UTC+8，该字段用于展示和对账口径转换。
     */
    private String timezone;

    /**
     * 商户风险等级，例如 LOW、NORMAL、HIGH。
     */
    private String riskLevel;

    /**
     * 平台请求体 RSA 密钥编号，商户加密请求体 data 时写入 kid。
     */
    private String platformPayloadKeyId;

    /**
     * 商户响应公钥编号，平台响应加密增强模式写入 kid。
     * <p>
     * 该字段是可选增强项；默认 API 对接不要求商户提供响应解密密钥。
     */
    private String merchantResponseKeyId;
}
