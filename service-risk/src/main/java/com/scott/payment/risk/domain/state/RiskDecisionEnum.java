package com.scott.payment.risk.domain.state;

import lombok.Getter;

/**
 * 风控结论枚举，位于 风控服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
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

    /**
     * 定义风控结论及其是否允许交易继续执行的稳定语义。
     *
     * @param code 内部接口和审计消息使用的决策编码
     * @param allowProceed 是否允许支付流程继续进入路由和渠道链路
     */
    RiskDecisionEnum(String code, boolean allowProceed) {
        this.code = code;
        this.allowProceed = allowProceed;
    }
}
