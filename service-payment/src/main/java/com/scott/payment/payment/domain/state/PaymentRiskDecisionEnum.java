package com.scott.payment.payment.domain.state;

import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRiskDecisionEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 支付风控结论枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 * @status : create
 */
@Getter
public enum PaymentRiskDecisionEnum {

    /**
     * 风控通过，交易可继续处理。
     */
    PASS("PASS", true),

    /**
     * 风控跳过，交易可继续处理；生产环境不应常态依赖该状态。
     */
    SKIP("SKIP", true),

    /**
     * 风控拒绝，交易应进入 FAILED。
     */
    REJECT("REJECT", false),

    /**
     * 风控人工复核，交易应进入 PENDING。
     */
    REVIEW("REVIEW", false),

    /**
     * 风控要求 3DS，交易应进入 PENDING。
     */
    REQUIRE_3DS("REQUIRE_3DS", false),

    /**
     * 未知风控决策，支付侧按拒绝处理以避免风险放行。
     */
    UNKNOWN("UNKNOWN", false);

    /**
     * 风控决策编码。
     */
    private final String code;

    /**
     * 是否允许交易继续进入路由和渠道调用。
     */
    private final boolean allowProceed;

    PaymentRiskDecisionEnum(String code, boolean allowProceed) {
        this.code = code;
        this.allowProceed = allowProceed;
    }

    /**
     * 按风控决策编码解析枚举，未知编码按 UNKNOWN 处理。
     *
     * @param code 风控决策编码
     * @return 风控决策枚举
     */
    public static PaymentRiskDecisionEnum of(String code) {
        if (!StringUtils.hasText(code)) {
            return UNKNOWN;
        }
        for (PaymentRiskDecisionEnum decisionEnum : values()) {
            if (decisionEnum.getCode().equalsIgnoreCase(code)) {
                return decisionEnum;
            }
        }
        return UNKNOWN;
    }
}
