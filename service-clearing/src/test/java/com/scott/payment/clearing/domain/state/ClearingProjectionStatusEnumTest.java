package com.scott.payment.clearing.domain.state;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingProjectionStatusEnumTest
 * @date : 2026-08-26 18:45
 * @email : scott_x@163.com
 * @description : 验证权威清分状态到动作查询投影的固定映射，以及生命周期失败优先的完整聚合规则。
 * @status : create
 */
class ClearingProjectionStatusEnumTest {

    @Test
    void authoritativeStatesShouldMapToStableOperationProjection() {
        assertThat(ClearingProjectionStatusEnum.fromAuthoritative(ClearingStateEnum.NOT_CLEARED))
                .isEqualTo(ClearingProjectionStatusEnum.NOT_CLEARED);
        assertThat(List.of(ClearingStateEnum.PENDING, ClearingStateEnum.PROCESSING,
                ClearingStateEnum.WAITING_SOURCE))
                .allSatisfy(status -> assertThat(ClearingProjectionStatusEnum.fromAuthoritative(status))
                        .isEqualTo(ClearingProjectionStatusEnum.PENDING));
        assertThat(List.of(ClearingStateEnum.FAILED, ClearingStateEnum.MANUAL_REVIEW))
                .allSatisfy(status -> assertThat(ClearingProjectionStatusEnum.fromAuthoritative(status))
                        .isEqualTo(ClearingProjectionStatusEnum.FAILED));
        assertThat(ClearingProjectionStatusEnum.fromAuthoritative(ClearingStateEnum.CLEARED))
                .isEqualTo(ClearingProjectionStatusEnum.CLEARED);
        assertThat(ClearingProjectionStatusEnum.fromAuthoritative(ClearingStateEnum.NOT_REQUIRED))
                .isEqualTo(ClearingProjectionStatusEnum.NOT_REQUIRED);
    }

    @Test
    void lifecycleAggregationShouldCoverFailurePartialPendingAndTerminalStates() {
        assertThat(ClearingProjectionStatusEnum.aggregate(List.of("CLEARED", "FAILED", "PENDING")))
                .isEqualTo(ClearingProjectionStatusEnum.FAILED);
        assertThat(ClearingProjectionStatusEnum.aggregate(List.of("CLEARED", "PENDING")))
                .isEqualTo(ClearingProjectionStatusEnum.PARTIALLY_CLEARED);
        assertThat(ClearingProjectionStatusEnum.aggregate(List.of("NOT_REQUIRED", "NOT_REQUIRED")))
                .isEqualTo(ClearingProjectionStatusEnum.NOT_REQUIRED);
        assertThat(ClearingProjectionStatusEnum.aggregate(List.of("CLEARED", "NOT_REQUIRED")))
                .isEqualTo(ClearingProjectionStatusEnum.CLEARED);
        assertThat(ClearingProjectionStatusEnum.aggregate(List.of("NOT_CLEARED", "PENDING")))
                .isEqualTo(ClearingProjectionStatusEnum.PENDING);
        assertThat(ClearingProjectionStatusEnum.aggregate(List.of("NOT_CLEARED", "NOT_CLEARED")))
                .isEqualTo(ClearingProjectionStatusEnum.NOT_CLEARED);
    }

    @Test
    void operationAggregationShouldRejectLifecycleOnlyOrUnknownStatus() {
        assertThatThrownBy(() -> ClearingProjectionStatusEnum.aggregate(List.of("PARTIALLY_CLEARED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lifecycle");
        assertThatThrownBy(() -> ClearingProjectionStatusEnum.aggregate(List.of("UNKNOWN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }
}
