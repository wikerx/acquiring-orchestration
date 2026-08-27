package com.scott.payment.clearing.domain.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingStateMachineTest
 * @date : 2026-08-26 08:25
 * @email : scott_x@163.com
 * @description : 验证清分权威状态的单向流转、终态不可逆和人工复核显式重试边界。
 * @status : create
 */
class ClearingStateMachineTest {

    @Test
    void shouldAllowNormalClaimAndCompletionTransitions() {
        assertThat(ClearingStateEnum.PENDING.canTransitionTo(
                ClearingStateEnum.PROCESSING, ClearingTransitionOrigin.AUTOMATIC)).isTrue();
        assertThat(ClearingStateEnum.PROCESSING.canTransitionTo(
                ClearingStateEnum.CLEARED, ClearingTransitionOrigin.AUTOMATIC)).isTrue();
        assertThat(ClearingStateEnum.PROCESSING.canTransitionTo(
                ClearingStateEnum.WAITING_SOURCE, ClearingTransitionOrigin.AUTOMATIC)).isTrue();
        assertThat(ClearingStateEnum.PROCESSING.canTransitionTo(
                ClearingStateEnum.MANUAL_REVIEW, ClearingTransitionOrigin.AUTOMATIC)).isTrue();
    }

    @Test
    void shouldKeepCompletedStatesImmutable() {
        assertThat(ClearingStateEnum.CLEARED.canTransitionTo(
                ClearingStateEnum.PROCESSING, ClearingTransitionOrigin.MANUAL_RETRY)).isFalse();
        assertThat(ClearingStateEnum.NOT_REQUIRED.canTransitionTo(
                ClearingStateEnum.PENDING, ClearingTransitionOrigin.AUTOMATIC)).isFalse();
    }

    @Test
    void shouldRequireExplicitManualRetryFromReview() {
        assertThat(ClearingStateEnum.MANUAL_REVIEW.canTransitionTo(
                ClearingStateEnum.PROCESSING, ClearingTransitionOrigin.AUTOMATIC)).isFalse();
        assertThat(ClearingStateEnum.MANUAL_REVIEW.canTransitionTo(
                ClearingStateEnum.PROCESSING, ClearingTransitionOrigin.MANUAL_RETRY)).isTrue();
    }
}
