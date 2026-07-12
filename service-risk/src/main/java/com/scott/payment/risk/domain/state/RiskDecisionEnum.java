package com.scott.payment.risk.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskDecisionEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控决策枚举，位于 service-risk 领域状态层，只表达风险处理建议，不与交易状态 transaction_status 混用。
 * @status : create
 */
@Getter
public enum RiskDecisionEnum {

    /**
     * 风控通过，交易可继续进入路由和渠道调用。
     */
    PASS("PASS", true),

    /**
     * 风控跳过，通常只用于降级或灰度，不建议生产常态使用。
     */
    SKIP("SKIP", true),

    /**
     * 风控拒绝，payment 应映射为 transaction_status=FAILED。
     */
    REJECT("REJECT", false),

    /**
     * 需要人工复核，payment 应映射为 transaction_status=PENDING。
     */
    REVIEW("REVIEW", false),

    /**
     * 需要付款人补充 3DS 认证，payment 应映射为 transaction_status=PENDING。
     */
    REQUIRE_3DS("REQUIRE_3DS", false);

    /**
     * 风控决策编码。
     */
    private final String code;

    /**
     * 是否允许交易继续进入渠道链路。
     */
    private final boolean allowProceed;

    RiskDecisionEnum(String code, boolean allowProceed) {
        this.code = code;
        this.allowProceed = allowProceed;
    }
}
