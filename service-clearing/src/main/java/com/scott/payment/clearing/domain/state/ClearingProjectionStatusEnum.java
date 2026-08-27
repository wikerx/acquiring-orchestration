package com.scott.payment.clearing.domain.state;

import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingProjectionStatusEnum
 * @date : 2026-08-26 18:30
 * @email : scott_x@163.com
 * @description : 定义动作查询投影和生命周期聚合状态；不替代 transaction_finance_state 的权威清分状态机。
 * @status : create
 */
public enum ClearingProjectionStatusEnum {
    /** 尚无清分触发记录。 */
    NOT_CLEARED,
    /** 已触发但尚未形成清分终态。 */
    PENDING,
    /** 生命周期中已完成动作与待处理动作并存。 */
    PARTIALLY_CLEARED,
    /** 生命周期或动作已经产生至少一项有效清分事实。 */
    CLEARED,
    /** 动作失败或生命周期中存在失败动作。 */
    FAILED,
    /** 动作或生命周期内全部动作均无需清分。 */
    NOT_REQUIRED;

    /**
     * 把动作权威状态映射为对查询稳定的动作投影，不暴露短暂 PROCESSING 或内部等待状态。
     *
     * @param authoritativeStatus transaction_finance_state 权威状态
     * @return transaction_operation 可持久化投影
     */
    public static ClearingProjectionStatusEnum fromAuthoritative(ClearingStateEnum authoritativeStatus) {
        Objects.requireNonNull(authoritativeStatus, "authoritative clearing status is required");
        return switch (authoritativeStatus) {
            case NOT_CLEARED -> NOT_CLEARED;
            case PENDING, PROCESSING, WAITING_SOURCE -> PENDING;
            case FAILED, MANUAL_REVIEW -> FAILED;
            case CLEARED -> CLEARED;
            case NOT_REQUIRED -> NOT_REQUIRED;
        };
    }

    /**
     * 按生命周期内全部动作投影计算主单聚合状态，失败优先于完成和等待。
     *
     * @param operationStatuses transaction_operation 动作投影列表
     * @return transaction_order 生命周期聚合投影
     */
    public static ClearingProjectionStatusEnum aggregate(List<String> operationStatuses) {
        if (operationStatuses == null || operationStatuses.isEmpty()) {
            throw new IllegalArgumentException("operation clearing projections are required");
        }
        List<ClearingProjectionStatusEnum> statuses = operationStatuses.stream()
                .map(ClearingProjectionStatusEnum::parseOperationStatus)
                .toList();
        if (statuses.contains(FAILED)) {
            return FAILED;
        }
        if (statuses.stream().allMatch(status -> status == NOT_REQUIRED)) {
            return NOT_REQUIRED;
        }
        boolean hasCompleted = statuses.stream().anyMatch(ClearingProjectionStatusEnum::isCompleted);
        boolean allCompleted = statuses.stream().allMatch(ClearingProjectionStatusEnum::isCompleted);
        if (allCompleted) {
            return CLEARED;
        }
        if (hasCompleted) {
            return PARTIALLY_CLEARED;
        }
        return statuses.stream().allMatch(status -> status == NOT_CLEARED) ? NOT_CLEARED : PENDING;
    }

    /** @return 是否为动作清分完成投影 */
    public boolean isCompleted() {
        return this == CLEARED || this == NOT_REQUIRED;
    }

    private static ClearingProjectionStatusEnum parseOperationStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("operation clearing projection is blank");
        }
        ClearingProjectionStatusEnum parsed;
        try {
            parsed = valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported operation clearing projection", exception);
        }
        if (parsed == PARTIALLY_CLEARED) {
            throw new IllegalArgumentException("partial clearing is only valid for lifecycle projection");
        }
        return parsed;
    }
}
