package com.scott.payment.component.db.auth.model;

import lombok.Data;

/**
 * 商户交易和登录链路使用的最小运行时资料。
 *
 * <p>该对象可以进入 Redis，但不得扩展联系人、地址、密钥或其他非运行时敏感字段。</p>
 */
@Data
public class MerchantRuntimeProfile {

    /**
     * 商户主表主键。
     */
    private Long id;

    /**
     * 平台商户号。
     */
    private String merchantId;

    /**
     * 商户状态：1 正常，2 冻结，3 关闭。
     */
    private Integer merchantStatus;

    /**
     * 商户类别码。
     */
    private String merchantCategoryCode;

    /**
     * 商户所在国家三字码。
     */
    private String countryCode;

    /**
     * 默认结算币种。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区。
     */
    private String timezone;

    /**
     * 商户风险等级。
     */
    private Integer riskLevel;
}
