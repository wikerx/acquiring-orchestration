package com.scott.payment.settlement.exception;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementManualReviewProcessingExceptionTest {

    @Test
    void shouldExposeStableBusinessCodeThroughGlobalExceptionHandler() {
        SettlementManualReviewProcessingException exception =
                new SettlementManualReviewProcessingException(
                        "MANUAL_REVIEW_CANDIDATE_EMPTY", false,
                        "no matured transaction settlement candidates match the selected scope");

        CommonResult<Void> result = new GlobalExceptionHandler().handleServiceException(exception);

        assertThat(result.getCode()).isEqualTo("MANUAL_REVIEW_CANDIDATE_EMPTY");
        assertThat(result.getMessage())
                .isEqualTo("no matured transaction settlement candidates match the selected scope");
        assertThat(exception.getFailureCode()).isEqualTo(result.getCode());
        assertThat(exception.isRetryable()).isFalse();
    }
}
