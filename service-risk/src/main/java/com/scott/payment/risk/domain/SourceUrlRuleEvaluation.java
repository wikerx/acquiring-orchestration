package com.scott.payment.risk.domain;

import java.util.Optional;

/**
 * 来源网址限制的一次性只读评估结果。
 *
 * <p>同一规则快照同时表达允许命中和限制未命中，避免一次交易重复读取规则代际与快照。
 * 限制未命中优先于允许命中参与上层短路决策。</p>
 *
 * @param allowedRule 当前来源主机命中的允许规则
 * @param restrictionMiss 已配置来源限制但当前主机未命中的拒绝明细
 */
public record SourceUrlRuleEvaluation(Optional<RiskListMatch> allowedRule,
                                      Optional<RiskListMatch> restrictionMiss) {

    /**
     * 规范可选结果，防止仓储兼容实现返回空引用。
     */
    public SourceUrlRuleEvaluation {
        allowedRule = allowedRule == null ? Optional.empty() : allowedRule;
        restrictionMiss = restrictionMiss == null ? Optional.empty() : restrictionMiss;
    }

    /**
     * 创建未配置或未命中来源网址限制的空结果。
     *
     * @return 不触发允许或拒绝决策的结果
     */
    public static SourceUrlRuleEvaluation empty() {
        return new SourceUrlRuleEvaluation(Optional.empty(), Optional.empty());
    }
}
