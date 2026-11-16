package com.scott.payment.risk.domain;

import java.util.List;

/**
 * 一次频控规则执行及其规则启用状态。
 *
 * <p>仓储先读取一次有序规则集合，再依次执行原有计数和成功名额预占，避免执行结束后
 * 为生成 PASS 审计节点再次读取同一规则集合。</p>
 *
 * @param details 与规则顺序一致的 PASS、HIT 或 ERROR 明细
 * @param activeRules 当前商户是否存在至少一条可执行频控规则
 */
public record FrequencyRuleEvaluation(List<RiskListMatch> details,
                                      boolean activeRules) {

    /**
     * 将频控明细复制为不可变集合，防止上层编排修改仓储执行结果。
     */
    public FrequencyRuleEvaluation {
        details = details == null || details.isEmpty() ? List.of() : List.copyOf(details);
    }

    /**
     * 创建没有可执行频控规则的空结果。
     *
     * @return 不产生计数、副作用或审计检查点的结果
     */
    public static FrequencyRuleEvaluation empty() {
        return new FrequencyRuleEvaluation(List.of(), false);
    }
}
