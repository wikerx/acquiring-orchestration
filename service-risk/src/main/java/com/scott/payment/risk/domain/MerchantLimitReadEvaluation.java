package com.scott.payment.risk.domain;

import java.util.Optional;

/**
 * 商户金额限额的无副作用只读评估结果。
 *
 * <p>结果只包含单笔限额命中与规则启用状态，不执行累计金额预占，也不改变持久化状态。</p>
 *
 * @param singleLimitHit 本笔金额触发的单笔最低或最高限额
 * @param activeRule 当前商户和币种是否存在任意有效金额限额规则
 */
public record MerchantLimitReadEvaluation(Optional<RiskListMatch> singleLimitHit,
                                          boolean activeRule) {

    /**
     * 规范可选命中结果，避免仓储兼容实现返回空引用。
     */
    public MerchantLimitReadEvaluation {
        singleLimitHit = singleLimitHit == null ? Optional.empty() : singleLimitHit;
    }

    /**
     * 创建未配置金额限额规则的空结果。
     *
     * @return 不触发限额审计或预占的结果
     */
    public static MerchantLimitReadEvaluation empty() {
        return new MerchantLimitReadEvaluation(Optional.empty(), false);
    }
}
