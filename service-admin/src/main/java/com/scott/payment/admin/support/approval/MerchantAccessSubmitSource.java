package com.scott.payment.admin.support.approval;

/**
 * 商户访问控制配置的提交来源，用于区分管理端直增与商户端待审申请。
 */
public enum MerchantAccessSubmitSource {

    /**
     * ADMIN 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    ADMIN,
    MERCHANT
}
