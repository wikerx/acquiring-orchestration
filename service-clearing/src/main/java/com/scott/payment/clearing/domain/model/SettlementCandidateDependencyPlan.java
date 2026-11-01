package com.scott.payment.clearing.domain.model;

import com.scott.payment.clearing.domain.state.SettlementCandidateDependencyType;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 创建结算候选时必须同步持久化的源候选依赖计划。
 *
 * @param sourceFinanceStateId 源动作财务状态号
 * @param sourceRevision 源动作当前有效清分修订
 * @param sourceTransactionId 源动作平台交易号
 * @param merchantId 源动作所属平台商户号
 * @param dependencyTypes 必须满足的依赖类型集合
 */
public record SettlementCandidateDependencyPlan(String sourceFinanceStateId,
                                                int sourceRevision,
                                                String sourceTransactionId,
                                                String merchantId,
                                                Set<SettlementCandidateDependencyType> dependencyTypes) {

    /**
     * 复制依赖类型并校验有依赖计划时的完整源候选身份。
     */
    public SettlementCandidateDependencyPlan {
        dependencyTypes = dependencyTypes == null ? Set.of() : Set.copyOf(dependencyTypes);
        if (!dependencyTypes.isEmpty()
                && (!StringUtils.hasText(sourceFinanceStateId) || sourceRevision < 1
                || !StringUtils.hasText(sourceTransactionId) || !StringUtils.hasText(merchantId))) {
            throw new IllegalArgumentException("settlement dependency source identity is required");
        }
    }

    /**
     * 创建无需源候选约束的空计划。
     *
     * @return 不产生依赖记录的计划
     */
    public static SettlementCandidateDependencyPlan none() {
        return new SettlementCandidateDependencyPlan(null, 0, null, null, Set.of());
    }

    /**
     * 判断当前候选是否必须同步写入依赖。
     *
     * @return 存在至少一种依赖类型时返回 true
     */
    public boolean required() {
        return !dependencyTypes.isEmpty();
    }
}
